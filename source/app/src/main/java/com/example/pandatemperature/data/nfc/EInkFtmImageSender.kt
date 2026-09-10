package com.example.pandatemperature.data.nfc

import android.nfc.Tag
import android.nfc.tech.NfcV
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * 使用 ST25DV FTM/Mailbox 发送整张 128x250 三色图片。
 *
 * 协议与 docs 中的「FTM 版整体协议设计」一致：
 * - 先发 Header Frame（CMD=0x01, SEQ=0）
 * - 再按 240B 切片发 Data Frame（CMD=0x02, SEQ>=1）
 *
 * 底层 Mailbox 读写由 [St25dvFtm] 实现，需先补全其 ISO15693 FTM 命令。
 */
internal class EInkFtmImageSender {

    companion object {
        private const val CMD_HEADER: Byte = 0x01
        private const val CMD_DATA: Byte = 0x02
        private const val FLAG_LAST_FRAME: Byte = 0x01
        private const val FTM_CHUNK_DATA_SIZE: Int = 240
    }

    sealed class Progress {
        data class Sending(val frameIndex: Int, val totalFrames: Int) : Progress()
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
        require(payload.isNotEmpty()) { "payload is empty" }
        val totalBytes = payload.size
        val crc16 = Crc16Ccitt.compute(payload)

        Iso15693.withNfcV(tag) { nfcv, uid ->
            val totalChunks = (totalBytes + FTM_CHUNK_DATA_SIZE - 1) / FTM_CHUNK_DATA_SIZE
            val totalFrames = 1 + totalChunks // 1 header + N data

            // 1) 发送头帧
            sendHeaderFrame(
                nfcv = nfcv,
                uid = uid,
                sessionId = sessionId,
                width = width,
                height = height,
                format = format,
                totalBytes = totalBytes,
                chunkSize = FTM_CHUNK_DATA_SIZE,
                totalChunks = totalChunks,
                crc16 = crc16
            )
            onProgress(Progress.Sending(frameIndex = 0, totalFrames = totalFrames))

            // 2) 发送数据帧
            for (chunkIndex in 0 until totalChunks) {
                coroutineContext.ensureActiveOrThrow()
                val offset = chunkIndex * FTM_CHUNK_DATA_SIZE
                val len = minOf(FTM_CHUNK_DATA_SIZE, totalBytes - offset)
                val last = (chunkIndex == totalChunks - 1)
                sendDataFrame(
                    nfcv = nfcv,
                    uid = uid,
                    seq = chunkIndex + 1,
                    isLast = last,
                    chunk = payload,
                    chunkOffset = offset,
                    chunkLen = len
                )
                onProgress(
                    Progress.Sending(
                        frameIndex = 1 + chunkIndex,
                        totalFrames = totalFrames
                    )
                )
                // 简单节流，避免连续占满 RF 通道
                delay(5)
            }

            onProgress(Progress.Done("发送完成（FTM）"))
        }
    }

    private fun sendHeaderFrame(
        nfcv: NfcV,
        uid: ByteArray,
        sessionId: Int,
        width: Int,
        height: Int,
        format: Int,
        totalBytes: Int,
        chunkSize: Int,
        totalChunks: Int,
        crc16: Int
    ) {
        val frame = ByteArray(St25dvFtm.MAILBOX_LEN)

        // userPayload header
        frame[0] = CMD_HEADER
        frame[1] = 0       // SEQ_L
        frame[2] = 0       // SEQ_H
        frame[3] = 0       // FLAGS

        // DATA 区从 Byte4 开始，按 docs 7.3
        var p = 4
        frame[p++] = 0x45 // Magic "E"
        frame[p++] = 0x49 // Magic "I"
        frame[p++] = 0x01 // Version
        frame[p++] = (width and 0xFF).toByte()
        frame[p++] = ((width ushr 8) and 0xFF).toByte()
        frame[p++] = (height and 0xFF).toByte()
        frame[p++] = ((height ushr 8) and 0xFF).toByte()
        frame[p++] = (totalBytes and 0xFF).toByte()
        frame[p++] = ((totalBytes ushr 8) and 0xFF).toByte()
        frame[p++] = ((totalBytes ushr 16) and 0xFF).toByte()
        frame[p++] = ((totalBytes ushr 24) and 0xFF).toByte()
        frame[p++] = (chunkSize and 0xFF).toByte()
        frame[p++] = ((chunkSize ushr 8) and 0xFF).toByte()
        frame[p++] = (totalChunks and 0xFF).toByte()
        frame[p++] = ((totalChunks ushr 8) and 0xFF).toByte()
        frame[p++] = (crc16 and 0xFF).toByte()
        frame[p++] = ((crc16 ushr 8) and 0xFF).toByte()
        frame[p++] = (format and 0xFF).toByte()
        // 剩余保留区清 0（frame 默认已是 0）

        // sessionId 暂不放入头帧 DATA，可根据需要扩展

        // CRC8 覆盖 CMD..DATA
        val crc8 = crc8(frame, 0, St25dvFtm.HEADER_LEN - 1)
        frame[St25dvFtm.HEADER_LEN - 1] = crc8

        St25dvFtm.writeMailbox(nfcv, uid, frame)
    }

    private fun sendDataFrame(
        nfcv: NfcV,
        uid: ByteArray,
        seq: Int,
        isLast: Boolean,
        chunk: ByteArray,
        chunkOffset: Int,
        chunkLen: Int
    ) {
        val frame = ByteArray(St25dvFtm.MAILBOX_LEN)

        frame[0] = CMD_DATA
        frame[1] = (seq and 0xFF).toByte()
        frame[2] = ((seq ushr 8) and 0xFF).toByte()
        frame[3] = if (isLast) FLAG_LAST_FRAME else 0

        // DATA: 从 Byte4 开始复制 chunkLen 字节
        System.arraycopy(chunk, chunkOffset, frame, 4, chunkLen)

        val crc8 = crc8(frame, 0, St25dvFtm.HEADER_LEN - 1)
        frame[St25dvFtm.HEADER_LEN - 1] = crc8

        St25dvFtm.writeMailbox(nfcv, uid, frame)
    }

    private suspend fun kotlin.coroutines.CoroutineContext.ensureActiveOrThrow() {
        if (!isActive) throw CancellationException("cancelled")
    }

    /** 简单 CRC8（多项式 0x07，初始 0x00，不反转）。 */
    private fun crc8(data: ByteArray, offset: Int, length: Int): Byte {
        var crc = 0
        for (i in 0 until length) {
            var b = data[offset + i].toInt() and 0xFF
            crc = crc xor b
            repeat(8) {
                crc = if ((crc and 0x80) != 0) {
                    ((crc shl 1) xor 0x07) and 0xFF
                } else {
                    (crc shl 1) and 0xFF
                }
            }
        }
        return crc.toByte()
    }
}

