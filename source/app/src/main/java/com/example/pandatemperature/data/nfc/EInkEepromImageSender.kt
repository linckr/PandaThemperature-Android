package com.example.pandatemperature.data.nfc

import android.nfc.Tag
import android.nfc.tech.NfcV
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil
import kotlin.math.min
import java.io.IOException

/**
 * 基于 ST25DV16K EEPROM 的 NFC 图片发送器（主路径）。
 *
 * 使用 ISO15693 Read/Write Single Block（0x20/0x21），兼容性好。
 * 流程：清理（ABORT + 新 sessionId）→ 写头部 → 块循环（等 rxConsumedIndex → 写数据区 → 写控制区）→ tx_state=DONE。
 *
 * 与 [EInkNfcImageSender]（FTM）对外 API 一致，便于 ViewModel 切换。
 */
class EInkEepromImageSender(
    private val config: Config = Config()
) {
    data class Config(
        /** 清理后等待 rx_state=IDLE 的超时（ms） */
        val clearWaitRxIdleTimeoutMs: Long = 2000L,
        /** 每块等待 rxConsumedIndex 的轮询间隔（ms） */
        val chunkPollIntervalMs: Long = 80L,
        /** 每块等待 rxConsumedIndex 的超时（ms） */
        val chunkWaitConsumedTimeoutMs: Long = 8000L
    )

    sealed class Progress {
        data class WaitingTag(val message: String) : Progress()
        data class Sending(val chunkIndex: Int, val totalChunks: Int) : Progress()
        data class Done(val message: String) : Progress()
    }

    suspend fun sendImage(
        tag: Tag,
        sessionId: Int,
        width: Int,
        height: Int,
        format: Int,
        payload: ByteArray,
        onProgress: (Progress) -> Unit = {}
    ) {
        if (payload.isEmpty()) error("payload is empty")
        val chunkSize = St25dvEInkLayout.DATA_LEN
        val totalChunks = ceil(payload.size.toDouble() / chunkSize.toDouble()).toInt()
        val crc16 = Crc16Ccitt.compute(payload)

        Iso15693.withNfcV(tag) { nfcv, uid ->
            val blockSize = Iso15693.readSystemInfoOrNull(nfcv, uid)?.blockSizeBytes
                ?: Iso15693.DEFAULT_BLOCK_SIZE

            // 1. 清理：写控制区 ABORT + 新 sessionId
            onProgress(Progress.WaitingTag("清理缓冲区…"))
            writeControlAbort(nfcv, uid, blockSize, sessionId)
            waitRxStateIdle(nfcv, uid, blockSize)

            // 2. 写头部：完整控制区，tx_state=SENDING，txChunkIndex=0，txChunkLen=0
            val headerControl = EInkNfcProtocol.Control.initial(
                sessionId = sessionId,
                width = width,
                height = height,
                format = format,
                totalBytes = payload.size,
                chunkSize = chunkSize,
                crc16 = crc16
            ).let { it.copy(txChunkLen = 0) }
            writeControl(nfcv, uid, blockSize, headerControl)

            // 3. 块循环：等 rxConsumedIndex → 写数据区 → 写控制区
            for (k in 0 until totalChunks) {
                coroutineContext.ensureActiveOrThrow()
                onProgress(Progress.Sending(k, totalChunks))

                if (k > 0) {
                    waitRxConsumedIndex(nfcv, uid, blockSize, k)
                }

                val offset = k * chunkSize
                val len = min(chunkSize, payload.size - offset)
                val chunkData = ByteArray(chunkSize)
                payload.copyInto(chunkData, 0, offset, offset + len)

                Iso15693.writeEepromBytes(
                    nfcv, uid,
                    St25dvEInkLayout.DATA_ADDR,
                    chunkData,
                    blockSize
                )

                val current = readControl(nfcv, uid, blockSize)
                    ?: throw IOException("读控制区失败")
                val updated = current.copy(
                    txChunkIndex = k,
                    txChunkLen = len
                )
                writeControl(nfcv, uid, blockSize, updated)
            }

            // 4. 结束：tx_state=DONE
            val last = readControl(nfcv, uid, blockSize)
                ?: throw IOException("读控制区失败")
            writeControl(nfcv, uid, blockSize, last.copy(txState = EInkNfcProtocol.TX_STATE_DONE.toInt()))
        }

        onProgress(Progress.Done("发送完成"))
    }

    /** 写控制区：仅用于清理的 ABORT + sessionId（前 28 字节有效即可） */
    private fun writeControlAbort(nfcv: NfcV, uid: ByteArray, blockSize: Int, sessionId: Int) {
        val control = EInkNfcProtocol.Control(
            sessionId = sessionId,
            width = 0,
            height = 0,
            format = 0,
            totalBytes = 0,
            chunkSize = 0,
            totalChunks = 0,
            crc16 = 0,
            txState = EInkNfcProtocol.TX_STATE_ABORT.toInt(),
            txChunkIndex = 0,
            txChunkLen = 0,
            rxConsumedIndex = 0,
            rxState = EInkNfcProtocol.RX_STATE_IDLE.toInt(),
            errorCode = 0
        )
        writeControl(nfcv, uid, blockSize, control)
    }

    private fun writeControl(nfcv: NfcV, uid: ByteArray, blockSize: Int, control: EInkNfcProtocol.Control) {
        val raw = EInkNfcProtocol.encode(control, St25dvEInkLayout.CONTROL_LEN)
        Iso15693.writeEepromBytes(
            nfcv, uid,
            St25dvEInkLayout.CONTROL_ADDR,
            raw,
            blockSize
        )
    }

    private fun readControl(nfcv: NfcV, uid: ByteArray, blockSize: Int): EInkNfcProtocol.Control? {
        val raw = Iso15693.readEepromBytes(
            nfcv, uid,
            St25dvEInkLayout.CONTROL_ADDR,
            St25dvEInkLayout.CONTROL_LEN,
            blockSize
        )
        return EInkNfcProtocol.decodeOrNull(raw)
    }

    private suspend fun waitRxStateIdle(nfcv: NfcV, uid: ByteArray, blockSize: Int) {
        val deadline = System.currentTimeMillis() + config.clearWaitRxIdleTimeoutMs
        while (System.currentTimeMillis() < deadline) {
            coroutineContext.ensureActiveOrThrow()
            val c = readControl(nfcv, uid, blockSize) ?: break
            if (c.rxState == EInkNfcProtocol.RX_STATE_IDLE.toInt()) return
            delay(config.chunkPollIntervalMs)
        }
    }

    private suspend fun waitRxConsumedIndex(nfcv: NfcV, uid: ByteArray, blockSize: Int, expectedMin: Int) {
        val deadline = System.currentTimeMillis() + config.chunkWaitConsumedTimeoutMs
        while (System.currentTimeMillis() < deadline) {
            coroutineContext.ensureActiveOrThrow()
            val c = readControl(nfcv, uid, blockSize)
                ?: throw IOException("轮询控制区失败")
            if (c.rxConsumedIndex >= expectedMin) return
            delay(config.chunkPollIntervalMs)
        }
        throw IOException("等待 STM32 确认超时（${config.chunkWaitConsumedTimeoutMs}ms），请重试或检查设备")
    }

    private fun kotlin.coroutines.CoroutineContext.ensureActiveOrThrow() {
        if (!isActive) throw CancellationException("cancelled")
    }

    companion object {
        /**
         * 在 Tag 丢失或发送失败时尽量写一次 ABORT，便于 STM32 回到 IDLE。
         * 若已无法写入则忽略。
         */
        fun tryAbort(tag: Tag?, sessionId: Int) {
            if (tag == null) return
            try {
                Iso15693.withNfcV(tag) { nfcv, uid ->
                    val blockSize = Iso15693.readSystemInfoOrNull(nfcv, uid)?.blockSizeBytes
                        ?: Iso15693.DEFAULT_BLOCK_SIZE
                    val control = EInkNfcProtocol.Control(
                        sessionId = sessionId,
                        width = 0,
                        height = 0,
                        format = 0,
                        totalBytes = 0,
                        chunkSize = 0,
                        totalChunks = 0,
                        crc16 = 0,
                        txState = EInkNfcProtocol.TX_STATE_ABORT.toInt(),
                        txChunkIndex = 0,
                        txChunkLen = 0,
                        rxConsumedIndex = 0,
                        rxState = EInkNfcProtocol.RX_STATE_IDLE.toInt(),
                        errorCode = 0
                    )
                    val raw = EInkNfcProtocol.encode(control, St25dvEInkLayout.CONTROL_LEN)
                    Iso15693.writeEepromBytes(
                        nfcv, uid,
                        St25dvEInkLayout.CONTROL_ADDR,
                        raw,
                        blockSize
                    )
                }
            } catch (_: Exception) {
                // 尽最大努力，失败不抛
            }
        }
    }
}
