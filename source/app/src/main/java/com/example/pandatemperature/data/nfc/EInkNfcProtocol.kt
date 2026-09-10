package com.example.pandatemperature.data.nfc

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil

/**
 * 墨水屏挂件 NFC 传输控制区结构（MVP：Stop-and-Wait + session 覆盖）。
 *
 * 控制区是一个定长字节数组（默认 128B），由 APP 写 header/tx 字段，STM32 写回 rx 字段。
 */
internal object EInkNfcProtocol {
    const val MAGIC_0: Byte = 0x45 // 'E'
    const val MAGIC_1: Byte = 0x49 // 'I'
    const val VERSION: Byte = 0x01

    const val TX_STATE_IDLE: Byte = 0
    const val TX_STATE_SENDING: Byte = 1
    const val TX_STATE_DONE: Byte = 2
    const val TX_STATE_ABORT: Byte = 3

    const val RX_STATE_IDLE: Byte = 0
    const val RX_STATE_RECEIVING: Byte = 1
    const val RX_STATE_APPLYING: Byte = 2
    const val RX_STATE_OK: Byte = 3
    const val RX_STATE_ERROR: Byte = 4

    data class Control(
        val sessionId: Int,
        val width: Int,
        val height: Int,
        val format: Int,
        val totalBytes: Int,
        val chunkSize: Int,
        val totalChunks: Int,
        val crc16: Int,
        val txState: Int,
        val txChunkIndex: Int,
        val txChunkLen: Int,
        val rxConsumedIndex: Int,
        val rxState: Int,
        val errorCode: Int
    ) {
        companion object {
            fun initial(
                sessionId: Int,
                width: Int,
                height: Int,
                format: Int,
                totalBytes: Int,
                chunkSize: Int,
                crc16: Int
            ): Control {
                val totalChunks = ceil(totalBytes.toDouble() / chunkSize.toDouble()).toInt()
                return Control(
                    sessionId = sessionId,
                    width = width,
                    height = height,
                    format = format,
                    totalBytes = totalBytes,
                    chunkSize = chunkSize,
                    totalChunks = totalChunks,
                    crc16 = crc16,
                    txState = TX_STATE_SENDING.toInt(),
                    txChunkIndex = 0,
                    txChunkLen = chunkSize,
                    rxConsumedIndex = 0,
                    rxState = RX_STATE_IDLE.toInt(),
                    errorCode = 0
                )
            }
        }
    }

    // 控制区布局（小端）
    // 0..1 magic
    // 2 version
    // 3 sessionId (u8)
    // 4..5 width (u16)
    // 6..7 height (u16)
    // 8 format (u8)
    // 9 txState (u8)
    // 10 rxState (u8)
    // 11 errorCode (u8)
    // 12..15 totalBytes (u32)
    // 16..17 chunkSize (u16)
    // 18..19 totalChunks (u16)
    // 20..21 crc16 (u16)
    // 22..23 txChunkIndex (u16)
    // 24..25 txChunkLen (u16)
    // 26..27 rxConsumedIndex (u16)
    private const val MIN_SIZE = 28

    fun encode(control: Control, size: Int = 128): ByteArray {
        val out = ByteArray(size.coerceAtLeast(MIN_SIZE))
        val bb = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        bb.put(MAGIC_0)
        bb.put(MAGIC_1)
        bb.put(VERSION)
        bb.put((control.sessionId and 0xFF).toByte())
        bb.putShort(control.width.toShort())
        bb.putShort(control.height.toShort())
        bb.put((control.format and 0xFF).toByte())
        bb.put((control.txState and 0xFF).toByte())
        bb.put((control.rxState and 0xFF).toByte())
        bb.put((control.errorCode and 0xFF).toByte())
        bb.putInt(control.totalBytes)
        bb.putShort(control.chunkSize.toShort())
        bb.putShort(control.totalChunks.toShort())
        bb.putShort(control.crc16.toShort())
        bb.putShort(control.txChunkIndex.toShort())
        bb.putShort(control.txChunkLen.toShort())
        bb.putShort(control.rxConsumedIndex.toShort())
        return out
    }

    fun decodeOrNull(bytes: ByteArray): Control? {
        if (bytes.size < MIN_SIZE) return null
        if (bytes[0] != MAGIC_0 || bytes[1] != MAGIC_1) return null
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        bb.position(2)
        val version = bb.get().toInt() and 0xFF
        if (version != (VERSION.toInt() and 0xFF)) return null
        val sessionId = bb.get().toInt() and 0xFF
        val width = bb.short.toInt() and 0xFFFF
        val height = bb.short.toInt() and 0xFFFF
        val format = bb.get().toInt() and 0xFF
        val txState = bb.get().toInt() and 0xFF
        val rxState = bb.get().toInt() and 0xFF
        val errorCode = bb.get().toInt() and 0xFF
        val totalBytes = bb.int
        val chunkSize = bb.short.toInt() and 0xFFFF
        val totalChunks = bb.short.toInt() and 0xFFFF
        val crc16 = bb.short.toInt() and 0xFFFF
        val txChunkIndex = bb.short.toInt() and 0xFFFF
        val txChunkLen = bb.short.toInt() and 0xFFFF
        val rxConsumedIndex = bb.short.toInt() and 0xFFFF
        return Control(
            sessionId = sessionId,
            width = width,
            height = height,
            format = format,
            totalBytes = totalBytes,
            chunkSize = chunkSize,
            totalChunks = totalChunks,
            crc16 = crc16,
            txState = txState,
            txChunkIndex = txChunkIndex,
            txChunkLen = txChunkLen,
            rxConsumedIndex = rxConsumedIndex,
            rxState = rxState,
            errorCode = errorCode
        )
    }
}

