package com.beans.music

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Beans Music 原生黑胶唱片圆盘组件 (Vinyl Turntable Disk)
 * 具备逼真的黑胶细密同心微槽纹理、高光折射环、金属中轴与自转动画
 */
@Composable
fun BeansVinylDisk(
    song: BeansSong?,
    coverBitmap: Bitmap?,
    isPlaying: Boolean,
    size: Dp = 290.dp,
    accentColor: Color = Color(0xFFF2A33C),
    onClick: () -> Unit = {}
) {
    // 持续平滑自转动画
    val rotationAnim = remember { Animatable(0f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                rotationAnim.animateTo(
                    targetValue = rotationAnim.value + 360f,
                    animationSpec = tween(durationMillis = 20000, easing = LinearEasing)
                )
            }
        }
    }

    val scaleAnim by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.94f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "vinylScale"
    )

    Box(
        modifier = Modifier
            .size(size)
            .scale(scaleAnim)
            .shadow(
                elevation = 28.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.70f),
                spotColor = Color.Black.copy(alpha = 0.70f)
            )
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // 1. 黑胶唱片本体与逼真的精密同心圆微槽（Vinyl Grooves）
        Canvas(modifier = Modifier.fillMaxSize().rotate(rotationAnim.value)) {
            val radius = this.size.minDimension / 2f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)

            // 基础黑胶外层放射渐变
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF20222A),
                        Color(0xFF14151B),
                        Color(0xFF0C0D11),
                        Color(0xFF050608)
                    ),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )

            // 18 道高仿真黑胶密纹圈（微槽声轨）
            for (i in 0 until 18) {
                val factor = 0.64f + (i / 17f) * 0.32f
                val isMajor = (i % 4 == 0)
                drawCircle(
                    color = if (isMajor) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f),
                    radius = radius * factor,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = if (isMajor) 1.2f else 0.6f
                    )
                )
            }

            // 唱片外圈光洁边缘线
            drawCircle(
                color = Color.White.copy(alpha = 0.20f),
                radius = radius - 1.5f,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.0f)
            )
            // 贴纸外沿倒角防滑环
            drawCircle(
                color = Color.White.copy(alpha = 0.16f),
                radius = radius * 0.61f,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
            )
        }

        // 2. 中心圆形专辑封面（占据盘面 58% 直径）
        val labelSize = size * 0.58f
        Box(
            modifier = Modifier
                .size(labelSize)
                .rotate(rotationAnim.value)
                .shadow(elevation = 6.dp, shape = CircleShape)
                .clip(CircleShape)
                .border(width = 2.dp, color = Color(0xFF0F1118), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            when {
                coverBitmap != null -> {
                    Image(
                        bitmap = coverBitmap.asImageBitmap(),
                        contentDescription = song?.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                !song?.coverUrl.isNullOrBlank() -> {
                    AsyncImage(
                        model = song?.coverUrl,
                        contentDescription = song?.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        accentColor.copy(alpha = 0.6f),
                                        Color(0xFF222533)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(labelSize * 0.38f)
                        )
                    }
                }
            }

            // 封面贴纸内圈细装饰线
            Box(
                modifier = Modifier
                    .fillMaxSize(0.92f)
                    .border(width = 0.6.dp, color = Color.White.copy(alpha = 0.25f), shape = CircleShape)
            )

            // 3. 唱机中轴孔（Spindle Hole）与高光金属环
            Box(
                modifier = Modifier
                    .size(labelSize * 0.16f)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFE2E4EC),
                                Color(0xFF888B98),
                                Color(0xFFE2E4EC),
                                Color(0xFF555866)
                            )
                        )
                    )
                    .border(width = 0.8.dp, color = Color.Black.copy(alpha = 0.5f), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // 中轴实心黑孔
                Box(
                    modifier = Modifier
                        .size(labelSize * 0.08f)
                        .clip(CircleShape)
                        .background(Color(0xFF08090C))
                )
            }
        }
    }
}
