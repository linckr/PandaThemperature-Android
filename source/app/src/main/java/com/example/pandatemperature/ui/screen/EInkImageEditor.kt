package com.example.pandatemperature.ui.screen

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.min
import kotlin.math.roundToInt

/** 墨水屏裁剪比例 128 x 250 */
const val EINK_CROP_WIDTH = 128
const val EINK_CROP_HEIGHT = 250

/**
 * 图片裁剪编辑弹层：原图可拖动、双指缩放，中央 128:250 选框内区域为裁剪结果。
 * 完成裁剪后回调 onCropComplete(128x250 Bitmap)。
 */
@Composable
fun EInkImageEditorDialog(
    bitmap: Bitmap,
    onDismiss: () -> Unit,
    onCropComplete: (Bitmap) -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val bitmapPainter = remember(bitmap) {
        androidx.compose.ui.graphics.painter.BitmapPainter(bitmap.asImageBitmap())
    }
    val strokeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    val fillColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = "拖动、双指缩放图片，框内为 128×250 显示区域",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .onSizeChanged { containerSize = it }
                ) {
                    if (containerSize.width > 0 && containerSize.height > 0) {
                        val boxW = containerSize.width.toFloat()
                        val boxH = containerSize.height.toFloat()
                        val aspect = EINK_CROP_HEIGHT.toFloat() / EINK_CROP_WIDTH
                        val overlayW = minOf(boxW, boxH / aspect)
                        val overlayH = overlayW * aspect
                        val overlayLeft = (boxW - overlayW) / 2f
                        val overlayTop = (boxH - overlayH) / 2f
                        val srcW = bitmap.width.toFloat()
                        val srcH = bitmap.height.toFloat()
                        val drawScale = min(boxW / srcW, boxH / srcH)
                        val drawW = srcW * drawScale
                        val drawH = srcH * drawScale
                        val imgLeft0 = (boxW - drawW) / 2f
                        val imgTop0 = (boxH - drawH) / 2f

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        scale = (scale * zoom).coerceIn(0.5f, 5f)
                                        offsetX += pan.x
                                        offsetY += pan.y
                                    }
                                }
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offsetX,
                                    translationY = offsetY
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = bitmapPainter,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawRect(
                                color = strokeColor,
                                topLeft = Offset(overlayLeft, overlayTop),
                                size = Size(overlayW, overlayH),
                                style = Stroke(3f)
                            )
                            drawRect(
                                color = fillColor,
                                topLeft = Offset(overlayLeft, overlayTop),
                                size = Size(overlayW, overlayH)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Button(onClick = {
                        if (containerSize.width > 0 && containerSize.height > 0) {
                            val boxW = containerSize.width.toFloat()
                            val boxH = containerSize.height.toFloat()
                            val aspect = EINK_CROP_HEIGHT.toFloat() / EINK_CROP_WIDTH
                            val overlayW = minOf(boxW, boxH / aspect)
                            val overlayH = overlayW * aspect
                            val overlayLeft = (boxW - overlayW) / 2f
                            val overlayTop = (boxH - overlayH) / 2f
                            val srcW = bitmap.width.toFloat()
                            val srcH = bitmap.height.toFloat()
                            val drawScale = min(boxW / srcW, boxH / srcH)
                            val drawW = srcW * drawScale
                            val drawH = srcH * drawScale
                            val imgLeft0 = (boxW - drawW) / 2f
                            val imgTop0 = (boxH - drawH) / 2f
                            val imgLeft = imgLeft0 + offsetX
                            val imgTop = imgTop0 + offsetY
                            val srcPerPxX = bitmap.width / (drawW * scale)
                            val srcPerPxY = bitmap.height / (drawH * scale)
                            val srcX = ((overlayLeft - imgLeft) / scale * srcPerPxX).roundToInt().coerceIn(0, bitmap.width - 1)
                            val srcY = ((overlayTop - imgTop) / scale * srcPerPxY).roundToInt().coerceIn(0, bitmap.height - 1)
                            val srcCropW = (overlayW / scale * srcPerPxX).roundToInt().coerceIn(1, bitmap.width - srcX)
                            val srcCropH = (overlayH / scale * srcPerPxY).roundToInt().coerceIn(1, bitmap.height - srcY)
                            val cropped = Bitmap.createBitmap(bitmap, srcX, srcY, srcCropW, srcCropH)
                            val result = Bitmap.createScaledBitmap(cropped, EINK_CROP_WIDTH, EINK_CROP_HEIGHT, true)
                            onCropComplete(result)
                            onDismiss()
                        }
                    }) { Text("完成裁剪") }
                }
            }
        }
    }
}

