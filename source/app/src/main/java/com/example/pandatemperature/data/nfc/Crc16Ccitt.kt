package com.example.pandatemperature.data.nfc

/**
 * CRC16-CCITT (poly=0x1021, init=0xFFFF)。
 *
 * STM32 侧实现成本低，可用于整包校验；APP 侧也同步计算写入控制区。
 */
internal object Crc16Ccitt {
    fun compute(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Int {
        var crc = 0xFFFF
        val end = offset + length
        for (i in offset until end) {
            crc = crc xor ((data[i].toInt() and 0xFF) shl 8)
            for (_j in 0 until 8) {
                crc = if ((crc and 0x8000) != 0) {
                    ((crc shl 1) xor 0x1021) and 0xFFFF
                } else {
                    (crc shl 1) and 0xFFFF
                }
            }
        }
        return crc and 0xFFFF
    }
}

