package com.example.pandatemperature.data.nfc

import android.graphics.Bitmap
import android.graphics.Color

/**
 * 三色量化（黑/白/红）：先黑白有序抖动，再用红色掩膜把点阵“染红”。
 *
 * 目标：
 * - 黑白部分用 Ordered Dithering 保留层次，避免硬阈值块状。
 * - 红色只作为点缀：在判为红的区域，把本来应是“黑点”的位置替换为“红点”，避免黑红混杂脏。
 *
 * 约定：
 * - 输出每个像素严格属于 黑/白/红 三者之一。
 * - 位序与像素顺序在上层编码决定；这里仅给出 per-pixel 分类与预览渲染。
 */
internal object EInkTriColorQuantizer {
    enum class PixelClass { BLACK, WHITE, RED }

    // 4x4 Bayer matrix (0..15)
    private val BAYER4 = intArrayOf(
        0, 8, 2, 10,
        12, 4, 14, 6,
        3, 11, 1, 9,
        15, 7, 13, 5
    )

    data class Result(
        val width: Int,
        val height: Int,
        val classes: ByteArray // 0=WHITE,1=BLACK,2=RED
    )

    fun quantize(src: Bitmap, width: Int, height: Int): Result {
        val safeSrc = if (src.config == Bitmap.Config.HARDWARE) {
            src.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            src
        }
        val scaled = if (safeSrc.width == width && safeSrc.height == height) safeSrc
        else Bitmap.createScaledBitmap(safeSrc, width, height, true)

        val out = ByteArray(width * height)
        val yNormArr = DoubleArray(width * height)
        val hsv = FloatArray(3)
        try {
            // 亮度范围（简单全局归一化，配合抖动已足够好看）
            var minY = 255.0
            var maxY = 0.0
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val c = scaled.getPixel(x, y)
                    val r = Color.red(c)
                    val g = Color.green(c)
                    val b = Color.blue(c)
                    val yL = 0.299 * r + 0.587 * g + 0.114 * b
                    if (yL < minY) minY = yL
                    if (yL > maxY) maxY = yL
                }
            }
            val range = (maxY - minY).coerceAtLeast(1.0)

            // 阈值（经验值，后续联调可调）
            val veryBlack = 55.0          // 极暗直接黑
            val redSatMin = 0.35f
            val redValMin = 0.25f
            val redHueLow = 20f
            val redHueHigh = 340f

            var i = 0
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val c = scaled.getPixel(x, y)
                    val r = Color.red(c)
                    val g = Color.green(c)
                    val b = Color.blue(c)
                    val yL = 0.299 * r + 0.587 * g + 0.114 * b
                    val yNorm = ((yL - minY) * 255.0 / range).coerceIn(0.0, 255.0)
                    yNormArr[i] = yNorm

                    // Ordered dithering 阈值：0..255
                    val m = BAYER4[(y and 3) * 4 + (x and 3)]
                    val ditherT = (m + 0.5) * (255.0 / 16.0)
                    val ditherBlackDot = yNorm < ditherT

                    // 红色掩膜（HSV）
                    Color.RGBToHSV(r, g, b, hsv)
                    val h = hsv[0]
                    val s = hsv[1]
                    val v = hsv[2]
                    val isRed = s >= redSatMin && v >= redValMin && (h <= redHueLow || h >= redHueHigh)

                    val cls = when {
                        yNorm < veryBlack -> PixelClass.BLACK
                        isRed && ditherBlackDot -> PixelClass.RED   // 在红色区域把“黑点”染红
                        isRed -> PixelClass.WHITE                   // 红色区域其余保持白，干净
                        ditherBlackDot -> PixelClass.BLACK
                        else -> PixelClass.WHITE
                    }
                    out[i] = when (cls) {
                        PixelClass.WHITE -> 0
                        PixelClass.BLACK -> 1
                        PixelClass.RED -> 2
                    }.toByte()
                    i++
                }
            }

            // 第二阶段：在“重要区域”给一部分中等偏亮的黑点加红，形成红色点阵效果。
            // 区域：垂直方向中间 40%（height * 0.3f .. 0.7f）
            // 条件：当前分类为黑，亮度在 [90, 210] 之间。
            // 比例：使用简单的坐标哈希，约 25% 的点被提升为红。
            val yStart = (height * 0.3f).toInt().coerceAtLeast(0)
            val yEnd = (height * 0.7f).toInt().coerceAtMost(height)
            for (y in yStart until yEnd) {
                for (x in 0 until width) {
                    val idx = y * width + x
                    if ((out[idx].toInt() and 0xFF) != 1) continue // 仅处理黑点
                    val yNorm = yNormArr[idx]
                    if (yNorm < 90.0 || yNorm > 210.0) continue
                    // 均匀分布的伪随机：基于坐标的哈希
                    val h = (x * 73856093) xor (y * 19349663)
                    val ratio = (h and 0xFF) / 255.0
                    if (ratio < 0.25) {
                        out[idx] = 2 // 提升为红点
                    }
                }
            }
        } finally {
            if (scaled !== safeSrc && !scaled.isRecycled) scaled.recycle()
            if (safeSrc !== src && safeSrc !== scaled && !safeSrc.isRecycled) safeSrc.recycle()
        }

        return Result(width = width, height = height, classes = out)
    }

    fun renderPreview(result: Result): Bitmap {
        val bmp = Bitmap.createBitmap(result.width, result.height, Bitmap.Config.ARGB_8888)
        var i = 0
        for (y in 0 until result.height) {
            for (x in 0 until result.width) {
                val v = result.classes[i].toInt() and 0xFF
                val c = when (v) {
                    1 -> Color.rgb(0, 0, 0)
                    2 -> Color.rgb(200, 0, 0)
                    else -> Color.rgb(255, 255, 255)
                }
                bmp.setPixel(x, y, c)
                i++
            }
        }
        return bmp
    }
}

