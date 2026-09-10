package com.example.pandatemperature.data.nfc

import android.nfc.tech.NfcV

/**
 * ST25DV16K FTM（Fast Transfer Mode）相关封装。
 *
 * 当前协议约定：
 * - 所有控制字段与数据块均通过 FTM Mailbox RAM 传输，不再使用用户 EEPROM。
 * - 每次交互均为一帧 256B Mailbox：
 *
 *   [0..1]   magic = 0x45 0x49 ("EI")
 *   [2]     version
 *   [3]     sessionId
 *   [4]     txState
 *   [5]     rxState
 *   [6]     errorCode
 *   [7]     预留
 *   [8..11] totalBytes
 *   [12..13] chunkSize
 *   [14..15] totalChunks
 *   [16..17] txChunkIndex
 *   [18..19] txChunkLen
 *   [20..21] rxConsumedIndex
 *   [22..23] crc16Ccitt
 *   [24..31] 预留
 *   [32..255] 本块数据（最多 224B 或者你按需调整）
 *
 * 注意：
 * - 本文件只提供 Mailbox 读/写的外壳，真正的 ISO15693 FTM 命令需要参照 ST25DV16K 数据手册补全。
 */
internal object St25dvFtm {

    /** Mailbox RAM 总长度（字节）。 */
    const val MAILBOX_LEN: Int = 256

    /** 头部占用长度（字节）。 */
    const val HEADER_LEN: Int = 32

    /** 单帧可用负载最大长度。 */
    const val MAX_PAYLOAD_PER_FRAME: Int = MAILBOX_LEN - HEADER_LEN

    /**
     * 读取当前 Mailbox 内容（通常为最近一次写入的 256B 帧）。
     *
     * @throws UnsupportedOperationException 需要根据 ST25DV16K 手册补全实现。
     */
    fun readMailbox(nfcv: NfcV, uid: ByteArray): ByteArray {
        // TODO: 根据 ST25DV16K FTM 读命令实现实际的 transceive。
        throw UnsupportedOperationException(
            "St25dvFtm.readMailbox 尚未实现底层 FTM 读命令，请根据 ST25DV16K 手册补全。"
        )
    }

    /**
     * 将一帧 256B 数据写入 Mailbox RAM。
     *
     * 调用方负责确保 data.size == [MAILBOX_LEN]。
     *
     * @throws UnsupportedOperationException 需要根据 ST25DV16K 手册补全实现。
     */
    fun writeMailbox(nfcv: NfcV, uid: ByteArray, data: ByteArray) {
        require(data.size == MAILBOX_LEN) {
            "FTM mailbox frame must be exactly $MAILBOX_LEN bytes, actual=${data.size}"
        }

        // TODO: 根据 ST25DV16K FTM 写命令实现实际的 transceive。
        throw UnsupportedOperationException(
            "St25dvFtm.writeMailbox 尚未实现底层 FTM 写命令，请根据 ST25DV16K 手册补全。"
        )
    }
}


