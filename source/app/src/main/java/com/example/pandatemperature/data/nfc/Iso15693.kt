package com.example.pandatemperature.data.nfc

import android.nfc.Tag
import android.nfc.tech.NfcV
import java.io.IOException

/**
 * ISO15693(NfcV) 基础命令与辅助封装。
 *
 * 说明：
 * - 这里采用 addressed 模式（带 UID），避免同场景多标签时误写。
 * - ST25DV16K 常见 block size 为 4 字节；若解析系统信息失败，默认回退到 4。
 */
internal object Iso15693 {
    private const val FLAGS_ADDRESSED: Byte = 0x60

    private const val CMD_GET_SYSTEM_INFO: Byte = 0x2B
    private const val CMD_READ_SINGLE_BLOCK: Byte = 0x20
    private const val CMD_WRITE_SINGLE_BLOCK: Byte = 0x21

    // ST25DV 自定义命令（IC Mfg Code = 0x02）
    private const val ST_MFG_CODE: Byte = 0x02
    private const val CMD_READ_DYN_CFG: Byte = 0xAD.toByte()
    /** MB_CTRL_Dyn 寄存器指针，参考 ST25DV 数据手册 */
    private const val REG_MB_CTRL_DYN: Byte = 0x0D

    fun buildGetSystemInfo(uid: ByteArray): ByteArray {
        val cmd = ByteArray(2 + 8)
        cmd[0] = FLAGS_ADDRESSED
        cmd[1] = CMD_GET_SYSTEM_INFO
        System.arraycopy(uid, 0, cmd, 2, minOf(8, uid.size))
        return cmd
    }

    fun buildReadSingleBlock(uid: ByteArray, blockNumber: Int): ByteArray {
        val cmd = ByteArray(2 + 8 + 1)
        cmd[0] = FLAGS_ADDRESSED
        cmd[1] = CMD_READ_SINGLE_BLOCK
        System.arraycopy(uid, 0, cmd, 2, minOf(8, uid.size))
        cmd[10] = blockNumber.toByte()
        return cmd
    }

    fun buildWriteSingleBlock(uid: ByteArray, blockNumber: Int, data: ByteArray): ByteArray {
        val cmd = ByteArray(2 + 8 + 1 + data.size)
        cmd[0] = FLAGS_ADDRESSED
        cmd[1] = CMD_WRITE_SINGLE_BLOCK
        System.arraycopy(uid, 0, cmd, 2, minOf(8, uid.size))
        cmd[10] = blockNumber.toByte()
        System.arraycopy(data, 0, cmd, 11, data.size)
        return cmd
    }

    /**
     * 读取 ST25DV MB_CTRL_Dyn 寄存器，判断 Mailbox 中上一帧是否已被 I2C 端（STM32）读走。
     *
     * 返回值 bit 含义（参考 ST25DV 数据手册 §6.6）：
     * - bit0: MB_EN（Mailbox 使能）
     * - bit1: HOST_PUT_MSG（I2C 端写了消息，RF 端未读）
     * - bit2: RF_PUT_MSG（RF 端写了消息，I2C 端未读）
     * - bit3: HOST_MISS_MSG
     * - bit4: RF_MISS_MSG
     * - bit5: HOST_CURRENT_MSG（I2C 正在读/写）
     * - bit6: RF_CURRENT_MSG（RF 正在读/写）
     *
     * 当 RF_PUT_MSG(bit2)=1 时，表示 Android 上次写入的消息尚未被 STM32 读走，
     * 此时不应写入新帧，否则会被拒绝或覆盖导致数据丢失。
     *
     * @return 寄存器原始字节值；调用方通过 (value and 0x04) != 0 判断 RF_PUT_MSG
     * @throws IOException 通讯失败
     */
    fun readMbCtrlDyn(nfcv: NfcV, uid: ByteArray): Int {
        val uidLen = minOf(8, uid.size)
        val cmd = ByteArray(2 + 1 + uidLen + 1)
        var idx = 0
        cmd[idx++] = 0x20 // Flags: Addressed
        cmd[idx++] = CMD_READ_DYN_CFG
        cmd[idx++] = ST_MFG_CODE
        System.arraycopy(uid, 0, cmd, idx, uidLen); idx += uidLen
        cmd[idx] = REG_MB_CTRL_DYN

        val resp = nfcv.transceive(cmd)
        if (resp.isEmpty() || (resp[0].toInt() and 0x01) != 0) {
            throw IOException("Read MB_CTRL_Dyn failed, resp=${resp.contentToString()}")
        }
        if (resp.size < 2) throw IOException("MB_CTRL_Dyn response too short: ${resp.size}")
        return resp[1].toInt() and 0xFF
    }

    /** RF_PUT_MSG 位掩码：该位为 1 说明上一帧 RF 写入尚未被 I2C 端消费 */
    const val MB_CTRL_RF_PUT_MSG: Int = 0x04

    data class SystemInfo(
        val blockSizeBytes: Int,
        val numberOfBlocks: Int
    )

    fun readSystemInfoOrNull(nfcv: NfcV, uid: ByteArray): SystemInfo? {
        val resp = nfcv.transceive(buildGetSystemInfo(uid))
        if (resp.isEmpty()) return null
        // resp[0] = flags, resp[1] = info flags
        if (resp.size < 2) return null
        val infoFlags = resp[1].toInt() and 0xFF
        var idx = 2
        // UID present when addressed; spec indicates UID always returned in system info response.
        if (resp.size < idx + 8) return null
        idx += 8
        if ((infoFlags and 0x01) != 0) idx += 1 // DSFID
        if ((infoFlags and 0x02) != 0) idx += 1 // AFI
        if ((infoFlags and 0x04) == 0) return null // memory size not present
        if (resp.size < idx + 2) return null
        val numberOfBlocksMinus1 = resp[idx].toInt() and 0xFF
        val blockSizeMinus1 = resp[idx + 1].toInt() and 0xFF
        val numberOfBlocks = numberOfBlocksMinus1 + 1
        val blockSize = blockSizeMinus1 + 1
        return SystemInfo(blockSizeBytes = blockSize, numberOfBlocks = numberOfBlocks)
    }

    /** ST25DV16K 常见 block 大小 4 字节；Get System Info 失败时回退值 */
    const val DEFAULT_BLOCK_SIZE: Int = 4

    /**
     * 按字节范围读取 EEPROM（内部按块多次 Read Single Block 0x20）。
     *
     * @param startByteAddr 起始字节地址，必须按 blockSizeBytes 对齐
     * @param length 读取字节数，必须为 blockSizeBytes 的整数倍
     * @return 读取到的数据，长度为 length
     */
    fun readEepromBytes(
        nfcv: NfcV,
        uid: ByteArray,
        startByteAddr: Int,
        length: Int,
        blockSizeBytes: Int = DEFAULT_BLOCK_SIZE
    ): ByteArray {
        require(blockSizeBytes > 0)
        require(startByteAddr >= 0 && startByteAddr % blockSizeBytes == 0) {
            "startByteAddr must be block-aligned (blockSize=$blockSizeBytes)"
        }
        require(length > 0 && length % blockSizeBytes == 0) {
            "length must be multiple of blockSize ($blockSizeBytes)"
        }
        val startBlock = startByteAddr / blockSizeBytes
        val blockCount = length / blockSizeBytes
        val out = ByteArray(length)
        for (i in 0 until blockCount) {
            val blockNumber = startBlock + i
            val cmd = buildReadSingleBlock(uid, blockNumber)
            val resp = nfcv.transceive(cmd)
            if (resp.isEmpty() || (resp[0].toInt() and 0x01) != 0) {
                throw IOException("Read block $blockNumber failed, resp=${resp.contentToString()}")
            }
            if (resp.size < 1 + blockSizeBytes) {
                throw IOException("Read block $blockNumber response too short: ${resp.size}")
            }
            System.arraycopy(resp, 1, out, i * blockSizeBytes, blockSizeBytes)
        }
        return out
    }

    /**
     * 按字节范围写入 EEPROM（内部按块多次 Write Single Block 0x21）。
     *
     * @param startByteAddr 起始字节地址，必须按 blockSizeBytes 对齐
     * @param data 要写入的数据，长度必须为 blockSizeBytes 的整数倍
     */
    fun writeEepromBytes(
        nfcv: NfcV,
        uid: ByteArray,
        startByteAddr: Int,
        data: ByteArray,
        blockSizeBytes: Int = DEFAULT_BLOCK_SIZE
    ) {
        require(blockSizeBytes > 0)
        require(startByteAddr >= 0 && startByteAddr % blockSizeBytes == 0) {
            "startByteAddr must be block-aligned (blockSize=$blockSizeBytes)"
        }
        require(data.isNotEmpty() && data.size % blockSizeBytes == 0) {
            "data.size must be multiple of blockSize ($blockSizeBytes)"
        }
        val startBlock = startByteAddr / blockSizeBytes
        val blockCount = data.size / blockSizeBytes
        for (i in 0 until blockCount) {
            val blockNumber = startBlock + i
            val offset = i * blockSizeBytes
            val blockData = data.copyOfRange(offset, offset + blockSizeBytes)
            val cmd = buildWriteSingleBlock(uid, blockNumber, blockData)
            val resp = nfcv.transceive(cmd)
            if (resp.isEmpty() || (resp[0].toInt() and 0x01) != 0) {
                throw IOException("Write block $blockNumber failed, resp=${resp.contentToString()}")
            }
        }
    }

    inline fun <T> withNfcV(tag: Tag, block: (NfcV, ByteArray) -> T): T {
        val nfcv = NfcV.get(tag) ?: throw IOException("NfcV not supported by this tag")
        val uid = tag.id ?: throw IOException("Tag uid is null")
        try {
            nfcv.connect()
            return block(nfcv, uid)
        } finally {
            try {
                nfcv.close()
            } catch (_: Exception) {
            }
        }
    }
}

