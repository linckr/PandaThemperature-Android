package com.example.pandatemperature.data.nfc

import android.nfc.Tag
import android.nfc.tech.NfcV
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil
import java.io.IOException

/**
 * NFC 发送端（Android）：基于 ST25DV FTM（0xCA Fast Write Message）推送图片。
 *
 * 核心改进（解决 "Tag was lost"）：
 * 1. 每帧发送前轮询 MB_CTRL_Dyn.RF_PUT_MSG，等待 STM32 消费上一帧后再写入
 * 2. 单帧级别失败重试（而非整个流程重来），容忍短暂 RF 场中断
 * 3. 合理的帧间延迟，避免 Android 写入速度超过 STM32 I2C 读取速度
 */
class EInkNfcImageSender(
    private val config: Config = Config()
) {
    data class Config(
        /** 每帧数据区最大长度（不含头部 CMD/SEQ/FLAGS/CRC8） */
        val dataChunkSize: Int = 240,
        /** 帧与帧之间的基础延迟（ms），给 STM32 留出 I2C 读取时间 */
        val interFrameDelayMs: Long = 50L,
        /** 等待 Mailbox 空闲的单次轮询间隔（ms） */
        val mailboxPollIntervalMs: Long = 30L,
        /** 等待 Mailbox 空闲的最大超时（ms） */
        val mailboxTimeoutMs: Long = 3000L,
        /** 单帧 transceive 失败时的最大重试次数 */
        val frameRetryCount: Int = 3,
        /** 帧重试之间的等待时间（ms） */
        val frameRetryDelayMs: Long = 100L
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
        val chunkSize = config.dataChunkSize.coerceAtMost(240)
        val totalChunks = ceil(payload.size.toDouble() / chunkSize.toDouble()).toInt()
        val crc16 = Crc16Ccitt.compute(payload)

        Iso15693.withNfcV(tag) { nfcv, uid ->
            // 1. 发送头帧（CMD=0x01, SEQ=0）
            val headerPayload = buildHeaderUserPayload(
                sessionId = sessionId,
                width = width,
                height = height,
                format = format,
                totalBytes = payload.size,
                chunkSize = chunkSize,
                totalChunks = totalChunks,
                crc16 = crc16
            )
            sendFtmFrameWithRetry(nfcv, uid, headerPayload)

            // 2. 发送数据帧（CMD=0x02, SEQ>=1）
            for (chunkIndex in 0 until totalChunks) {
                coroutineContext.ensureActiveOrThrow()
                onProgress(Progress.Sending(chunkIndex, totalChunks))

                waitMailboxReady(nfcv, uid)

                val from = chunkIndex * chunkSize
                val len = minOf(chunkSize, payload.size - from)
                val isLast = (chunkIndex == totalChunks - 1)

                val dataPayload = buildDataUserPayload(
                    seq = chunkIndex + 1,
                    isLast = isLast,
                    source = payload,
                    offset = from,
                    length = len
                )

                sendFtmFrameWithRetry(nfcv, uid, dataPayload)

                if (config.interFrameDelayMs > 0) {
                    delay(config.interFrameDelayMs)
                }
            }
        }

        onProgress(Progress.Done("发送完成"))
    }

    /**
     * 轮询 MB_CTRL_Dyn 寄存器，等待 RF_PUT_MSG 位清零（表示 STM32 已读走上一帧）。
     * 超时则抛出 IOException，由上层决定是否重试整个流程。
     */
    private suspend fun waitMailboxReady(nfcv: NfcV, uid: ByteArray) {
        val deadline = System.currentTimeMillis() + config.mailboxTimeoutMs
        while (System.currentTimeMillis() < deadline) {
            coroutineContext.ensureActiveOrThrow()
            try {
                val mbCtrl = Iso15693.readMbCtrlDyn(nfcv, uid)
                if ((mbCtrl and Iso15693.MB_CTRL_RF_PUT_MSG) == 0) {
                    return
                }
            } catch (_: IOException) {
                // 读寄存器本身也可能因 RF 抖动失败，短暂等待后重试
            }
            delay(config.mailboxPollIntervalMs)
        }
        throw IOException("Mailbox busy timeout: STM32 未在 ${config.mailboxTimeoutMs}ms 内消费上一帧")
    }

    /**
     * 带帧级重试的 FTM 写入：单帧 transceive 失败后短暂等待重试，
     * 避免因瞬间 RF 场抖动导致整个发送流程中断。
     */
    private suspend fun sendFtmFrameWithRetry(nfcv: NfcV, uid: ByteArray, userPayload: ByteArray) {
        var lastException: IOException? = null
        repeat(config.frameRetryCount) { attempt ->
            coroutineContext.ensureActiveOrThrow()
            try {
                sendFtmFrame(nfcv, uid, userPayload)
                return
            } catch (e: IOException) {
                lastException = e
                if (attempt < config.frameRetryCount - 1) {
                    delay(config.frameRetryDelayMs)
                }
            }
        }
        throw lastException ?: IOException("FTM write failed after ${config.frameRetryCount} retries")
    }

    private fun kotlin.coroutines.CoroutineContext.ensureActiveOrThrow() {
        if (!isActive) throw CancellationException("cancelled")
    }

    /**
     * 构造头帧 userPayload：
     * [CMD=0x01][SEQ_L][SEQ_H][FLAGS][DATA...][CRC8]
     */
    private fun buildHeaderUserPayload(
        sessionId: Int,
        width: Int,
        height: Int,
        format: Int,
        totalBytes: Int,
        chunkSize: Int,
        totalChunks: Int,
        crc16: Int
    ): ByteArray {
        val data = ByteArray(32)
        var idx = 0
        data[idx++] = 0x45 // Magic "EI"
        data[idx++] = 0x49
        data[idx++] = 0x01 // Version
        writeU16Le(data, idx, width); idx += 2
        writeU16Le(data, idx, height); idx += 2
        writeU32Le(data, idx, totalBytes); idx += 4
        writeU16Le(data, idx, chunkSize); idx += 2
        writeU16Le(data, idx, totalChunks); idx += 2
        writeU16Le(data, idx, crc16); idx += 2
        data[idx++] = format.toByte()
        while (idx < data.size) {
            data[idx++] = 0
        }

        val payloadLen = 1 + 2 + 1 + data.size + 1
        val payload = ByteArray(payloadLen)
        var p = 0
        payload[p++] = 0x01 // CMD=Header
        payload[p++] = 0x00 // SEQ=0
        payload[p++] = 0x00
        payload[p++] = 0x00 // FLAGS=0
        System.arraycopy(data, 0, payload, p, data.size)
        p += data.size
        val crc8 = crc8(payload, 0, p)
        payload[p] = crc8.toByte()
        return payload
    }

    /**
     * 构造数据帧 userPayload：
     * [CMD=0x02][SEQ_L][SEQ_H][FLAGS][DATA...][CRC8]
     */
    private fun buildDataUserPayload(
        seq: Int,
        isLast: Boolean,
        source: ByteArray,
        offset: Int,
        length: Int
    ): ByteArray {
        val payloadLen = 1 + 2 + 1 + length + 1
        val payload = ByteArray(payloadLen)
        var p = 0
        payload[p++] = 0x02 // CMD=Data
        payload[p++] = (seq and 0xFF).toByte()
        payload[p++] = ((seq ushr 8) and 0xFF).toByte()
        var flags = 0
        if (isLast) flags = flags or 0x01
        payload[p++] = flags.toByte()
        System.arraycopy(source, offset, payload, p, length)
        p += length
        val crc8 = crc8(payload, 0, p)
        payload[p] = crc8.toByte()
        return payload
    }

    /**
     * 将一帧 userPayload 通过 0xCA Fast Write Message 写入 Mailbox RAM。
     *
     * 采用 Addressed 模式：FLAGS=0x20, CMD=0xCA, MfgCode=0x02, UID(8B), LEN-1, DATA...
     */
    private fun sendFtmFrame(nfcv: NfcV, uid: ByteArray, userPayload: ByteArray) {
        if (userPayload.isEmpty()) throw IllegalArgumentException("userPayload is empty")
        if (userPayload.size > 0xFF) {
            throw IllegalArgumentException("userPayload too large: ${userPayload.size}")
        }
        val uidLen = minOf(8, uid.size)
        val frame = ByteArray(2 + 1 + uidLen + 1 + userPayload.size)
        var idx = 0
        frame[idx++] = 0x20 // Flags: Addressed
        frame[idx++] = 0xCA.toByte() // Fast Write Message
        frame[idx++] = 0x02 // ST Manufacturer Code
        System.arraycopy(uid, 0, frame, idx, uidLen)
        idx += uidLen
        frame[idx++] = (userPayload.size - 1).toByte() // MB_LEN (length-1)
        System.arraycopy(userPayload, 0, frame, idx, userPayload.size)

        val resp = nfcv.transceive(frame)
        if (resp.isEmpty() || resp[0] != 0x00.toByte()) {
            throw IOException("FTM write failed, resp[0]=${resp.getOrNull(0)?.toInt()}")
        }
    }

    private fun writeU16Le(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and 0xFF).toByte()
        buffer[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    }

    private fun writeU32Le(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and 0xFF).toByte()
        buffer[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        buffer[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        buffer[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }

    private fun crc8(data: ByteArray, offset: Int, length: Int): Int {
        var crc = 0
        for (i in 0 until length) {
            val b = data[offset + i].toInt() and 0xFF
            crc = crc xor b
            repeat(8) {
                crc = if ((crc and 0x80) != 0) {
                    ((crc shl 1) xor 0x07) and 0xFF
                } else {
                    (crc shl 1) and 0xFF
                }
            }
        }
        return crc and 0xFF
    }
}
