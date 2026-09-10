package com.example.pandatemperature.data.nfc

import android.graphics.Bitmap

internal object EInkImageEncoder {
    data class Planes(
        val width: Int,
        val height: Int,
        val blackPlane: ByteArray,
        val redPlane: ByteArray
    ) {
        fun toPayload(): ByteArray {
            val out = ByteArray(blackPlane.size + redPlane.size)
            System.arraycopy(blackPlane, 0, out, 0, blackPlane.size)
            System.arraycopy(redPlane, 0, out, blackPlane.size, redPlane.size)
            return out
        }
    }

    /**
     * 将 Bitmap 三色化并编码为黑/红双 1bpp 平面。
     *
     * 量化规则与预览侧 `toSimpleEInkPreview` 保持一致：
     * - 先在 128x250 尺寸上统计亮度范围并做一次归一化；
     * - 使用 HSV 色相/饱和度/亮度判定红色；
     * - 其余根据亮度阈值分到黑/白。
     *
     * 位序：每字节 MSB 优先，即 x%8==0 对应 bit7。
     */
    fun encodeBlackRedPlanes(src: Bitmap, expectedWidth: Int, expectedHeight: Int): Planes {
        val width = expectedWidth
        val height = expectedHeight
        val bytesPerPlane = (width * height + 7) / 8
        val black = ByteArray(bytesPerPlane)
        val red = ByteArray(bytesPerPlane)

        val result = EInkTriColorQuantizer.quantize(src = src, width = width, height = height)
        // classes: 0=WHITE,1=BLACK,2=RED
        for (i in 0 until width * height) {
            val cls = result.classes[i].toInt() and 0xFF
            val byteIndex = i ushr 3
            val bit = 7 - (i and 7)
            when (cls) {
                1 -> black[byteIndex] = (black[byteIndex].toInt() or (1 shl bit)).toByte()
                2 -> red[byteIndex] = (red[byteIndex].toInt() or (1 shl bit)).toByte()
            }
        }

        return Planes(width = width, height = height, blackPlane = black, redPlane = red)
    }
}

