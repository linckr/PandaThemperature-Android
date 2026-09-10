package com.example.pandatemperature.ui.screen

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pandatemperature.data.bluetooth.BleManager
import com.example.pandatemperature.data.model.TaskStatus
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.min
import kotlin.math.roundToInt

/** 传输用分辨率 */
private const val TRANSFER_WIDTH = 128
private const val TRANSFER_HEIGHT = 250
/** 界面高亮框像素 */
private const val FRAME_W_PX = 256f
private const val FRAME_H_PX = 500f

/**
 * 墨水屏挂件界面：选图后直接在图片区拖拽缩放选定区域，确认时按 256×500 框裁剪为 128×250 并回调。
 */
@Composable
fun EInkPendantScreen(
    modifier: Modifier = Modifier,
    connectionState: BleManager.ConnectionState,
    deviceName: String?,
    taskStatus: TaskStatus?,
    batteryVoltage: Float?,
    batteryPercent: Float?,
    firmwareVersion: Int?,
    imageBitmap: Bitmap?,
    nfcTagInRange: Boolean = false,
    onDeviceBarClick: () -> Unit,
    onConfigClick: () -> Unit,
    onBackToHome: () -> Unit,
    onSelectImageClick: () -> Unit,
    onConfirmProcessClick: (Bitmap) -> Unit
) {
    val hasSelectedImage = imageBitmap != null
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var sidePx by remember { mutableStateOf(0f) }
    val systemBars = WindowInsets.systemBars
    val topPadding = systemBars.asPaddingValues().calculateTopPadding()
    val density = LocalDensity.current

    Surface(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .padding(top = topPadding + 2.dp, bottom = 8.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.04f),
                shape = MaterialTheme.shapes.medium
            ),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.04f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 左上角返回（放进卡片头部，减少额外占位）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(onClick = onBackToHome, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "返回"
                    )
                }
                TextButton(onClick = onBackToHome, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                    Text("返回温湿度主界面", fontSize = 12.sp)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(9.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "墨水屏挂件",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 9.sp
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Nfc,
                        contentDescription = null,
                        modifier = Modifier.size(11.dp),
                        tint = if (nfcTagInRange) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                    )
                    Text(
                        text = if (nfcTagInRange) "贴片已贴近" else "请将挂件贴近手机背面",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp,
                        color = if (nfcTagInRange) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            // 图片区域：固定 1:1，放大到屏幕宽度，避免被 weight 拉伸产生空白
            val borderColor = MaterialTheme.colorScheme.primary
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                // 用约束稳定计算像素宽度，避免 onSizeChanged 未触发导致 sidePx=0 点击无反应
                sidePx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
                if (imageBitmap != null) {
                    Image(
                        bitmap = imageBitmap.asImageBitmap(),
                        contentDescription = "传输图片",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY
                            )
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(0.5f, 5f)
                                    offsetX += pan.x
                                    offsetY += pan.y
                                }
                            },
                        contentScale = ContentScale.Fit
                    )
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val left = (size.width - FRAME_W_PX) / 2f
                        val top = (size.height - FRAME_H_PX) / 2f
                        drawRect(
                            color = borderColor,
                            topLeft = Offset(left, top),
                            size = Size(FRAME_W_PX, FRAME_H_PX),
                            style = Stroke(width = 3f)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "请选择图片",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // 轻量状态提示：用于确认按钮是否解锁（仅一行，便于联调）
            val confirmEnabled = hasSelectedImage && sidePx > 1f
            Text(
                text = if (confirmEnabled) "已选图，可确认" else "未选图或布局未就绪",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onSelectImageClick,
                    modifier = Modifier.weight(1f).height(36.dp),
                    colors = ButtonDefaults.buttonColors()
                ) {
                    Text("选择图片", fontSize = 12.sp)
                }
                Button(
                    onClick = {
                        val bmp = imageBitmap ?: return@Button
                        if (sidePx <= 0f) return@Button
                        val boxW = sidePx
                        val boxH = sidePx
                        val srcW = bmp.width.toFloat()
                        val srcH = bmp.height.toFloat()
                        val drawScale = min(boxW / srcW, boxH / srcH)
                        val drawW = srcW * drawScale
                        val drawH = srcH * drawScale
                        val imgLeft0 = (boxW - drawW) / 2f
                        val imgTop0 = (boxH - drawH) / 2f
                        val imgLeft = imgLeft0 + offsetX
                        val imgTop = imgTop0 + offsetY
                        val overlayLeft = (boxW - FRAME_W_PX) / 2f
                        val overlayTop = (boxH - FRAME_H_PX) / 2f
                        val overlayW = FRAME_W_PX
                        val overlayH = FRAME_H_PX
                        val srcPerPxX = bmp.width / (drawW * scale)
                        val srcPerPxY = bmp.height / (drawH * scale)
                        val srcX = ((overlayLeft - imgLeft) / scale * srcPerPxX).roundToInt().coerceIn(0, bmp.width - 1)
                        val srcY = ((overlayTop - imgTop) / scale * srcPerPxY).roundToInt().coerceIn(0, bmp.height - 1)
                        val srcCropW = (overlayW / scale * srcPerPxX).roundToInt().coerceIn(1, bmp.width - srcX)
                        val srcCropH = (overlayH / scale * srcPerPxY).roundToInt().coerceIn(1, bmp.height - srcY)
                        val cropped = Bitmap.createBitmap(bmp, srcX, srcY, srcCropW, srcCropH)
                        val result = Bitmap.createScaledBitmap(cropped, TRANSFER_WIDTH, TRANSFER_HEIGHT, true)
                        if (cropped != result) cropped.recycle()
                        onConfirmProcessClick(result)
                    },
                    enabled = confirmEnabled,
                    modifier = Modifier.weight(1f).height(36.dp),
                    colors = ButtonDefaults.buttonColors()
                ) {
                    Text("确认图片", fontSize = 12.sp)
                }
            }
        }
    }
}
