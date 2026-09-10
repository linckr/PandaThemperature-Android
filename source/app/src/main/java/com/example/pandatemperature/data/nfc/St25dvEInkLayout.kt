package com.example.pandatemperature.data.nfc

import kotlin.math.ceil

/**
 * ST25DV16K 上用于墨水屏挂件传输的 EEPROM 布局常量。
 *
 * 约定（与 docs/20260313-NFC图片发送-EEPROM方案-plan.md 一致）：
 * - 总容量 2KB：Control 区 128B（0x0000～0x007F），DataBuffer 区 1920B（0x0080～0x07FF）。
 * - 单块最大 1920 字节，减少往返次数；8KB 图片约 5 块。
 *
 * NfcV 读写以 block 为单位（常见 blockSize=4B），块号由 [toBlockIndex] 计算。
 */
internal object St25dvEInkLayout {
    const val CONTROL_ADDR: Int = 0x0000
    const val CONTROL_LEN: Int = 128

    const val DATA_ADDR: Int = 0x0080
    /** 数据区长度：2KB 总容量减去控制区 128B = 1920B */
    const val DATA_LEN: Int = 1920

    init {
        check(CONTROL_ADDR == 0x0000)
        check(CONTROL_LEN == 128)
        check(DATA_ADDR == CONTROL_ADDR + CONTROL_LEN)
        check(DATA_LEN == 1920)
        check(CONTROL_LEN + DATA_LEN <= 2048) // ST25DV16K 2KB
    }

    fun controlStartBlock(blockSizeBytes: Int): Int = toBlockIndex(CONTROL_ADDR, blockSizeBytes)

    fun dataStartBlock(blockSizeBytes: Int): Int = toBlockIndex(DATA_ADDR, blockSizeBytes)

    fun controlBlockCount(blockSizeBytes: Int): Int = blocksForBytes(CONTROL_LEN, blockSizeBytes)

    fun dataBlockCount(blockSizeBytes: Int): Int = blocksForBytes(DATA_LEN, blockSizeBytes)

    private fun toBlockIndex(byteAddr: Int, blockSizeBytes: Int): Int {
        require(blockSizeBytes > 0)
        require(byteAddr % blockSizeBytes == 0) {
            "byte address 0x${byteAddr.toString(16)} must align to blockSize=$blockSizeBytes"
        }
        return byteAddr / blockSizeBytes
    }

    private fun blocksForBytes(byteCount: Int, blockSizeBytes: Int): Int {
        require(blockSizeBytes > 0)
        return ceil(byteCount.toDouble() / blockSizeBytes.toDouble()).toInt()
    }
}

