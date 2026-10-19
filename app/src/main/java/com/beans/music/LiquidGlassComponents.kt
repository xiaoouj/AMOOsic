package com.beans.music

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode as AnimRepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.compositionLocalOf
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import coil.compose.AsyncImage
import kotlin.math.cos
import kotlin.math.sin

val LocalBackdrop = compositionLocalOf<Backdrop?> { null }

data class BeansAccentPalette(
    val name: String,
    val primary: Color,
    val secondary: Color
)

val BEANS_ACCENT_THEMES = listOf(
    BeansAccentPalette("琥珀暖金", Color(0xFFF2A33C), Color(0xFFC07A0A)),
    BeansAccentPalette("青碧湖绿", Color(0xFF6BC79E), Color(0xFF298C6B)),
    BeansAccentPalette("樱粉", Color(0xFFF58FB2), Color(0xFFD15E8F)),
    BeansAccentPalette("星蓝", Color(0xFF63B5F5), Color(0xFF3B7AD6)),
    BeansAccentPalette("罗兰紫", Color(0xFFB39EF2), Color(0xFF7D57C2)),
    BeansAccentPalette("赛博青", Color(0xFF40E6D9), Color(0xFF0D8C9E)),
    BeansAccentPalette("蜜桃粉", Color(0xFFFF8577), Color(0xFFD94A3D)),
    BeansAccentPalette("鎏金黑", Color(0xFFD4AF37), Color(0xFF8A6D1F)),
    BeansAccentPalette("翡翠绿", Color(0xFF2ECC71), Color(0xFF1F8B4C))
)

/**
 * 自然融合的底层流体光晕（Apple Music / iOS 原生风格）
 * 彻底抛弃硬勾勒和突兀光斑，采用超大半径高斯级漫射渐变，作为纯粹的氛围底色
 */
@Composable
fun FluidMeshBackground(
    colors: DynamicCoverColors,
    isPlaying: Boolean,
    intensity: Float = 0.85f,
    modifier: Modifier = Modifier
) {
    val c1 by animateColorAsState(targetValue = colors.primary, animationSpec = tween(1400), label = "c1")
    val c2 by animateColorAsState(targetValue = colors.secondary, animationSpec = tween(1400), label = "c2")
    val c3 by animateColorAsState(targetValue = colors.tertiary, animationSpec = tween(1400), label = "c3")
    val cBg by animateColorAsState(targetValue = colors.darkSurface, animationSpec = tween(1400), label = "cBg")

    val infiniteTransition = rememberInfiniteTransition(label = "fluidMesh")
    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isPlaying) 18000 else 36000, easing = LinearEasing),
            repeatMode = AnimRepeatMode.Restart
        ),
        label = "phase1"
    )
    val phase2 by infiniteTransition.animateFloat(
        initialValue = (Math.PI / 2).toFloat(),
        targetValue = (2.5 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isPlaying) 24000 else 46000, easing = LinearEasing),
            repeatMode = AnimRepeatMode.Restart
        ),
        label = "phase2"
    )

    Canvas(modifier = modifier.fillMaxSize().background(Color(0xFF0A0A0C))) {
        val w = size.width
        val h = size.height
        val maxDim = maxOf(w, h)

        // 1. 底层沉稳的深色暗场渐变（确保文字可读性）
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    cBg.copy(alpha = 0.80f),
                    Color(0xFF0F1118),
                    Color(0xFF0A0B10)
                )
            )
        )

        // 2. 超广角漫射柔和光晕 1（大面积无锐利边缘）
        val x1 = w * (0.35f + 0.18f * cos(phase1))
        val y1 = h * (0.26f + 0.12f * sin(phase1))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    c1.copy(alpha = 0.58f * intensity),
                    c1.copy(alpha = 0.22f * intensity),
                    Color.Transparent
                ),
                center = Offset(x1, y1),
                radius = maxDim * 0.95f
            ),
            center = Offset(x1, y1),
            radius = maxDim * 0.95f
        )

        // 3. 超广角漫射柔和光晕 2
        val x2 = w * (0.68f + 0.16f * sin(phase2))
        val y2 = h * (0.58f + 0.14f * cos(phase2))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    c2.copy(alpha = 0.52f * intensity),
                    c2.copy(alpha = 0.18f * intensity),
                    Color.Transparent
                ),
                center = Offset(x2, y2),
                radius = maxDim * 0.90f
            ),
            center = Offset(x2, y2),
            radius = maxDim * 0.90f
        )

        // 4. 底部柔和环境底光（直接照亮底栏液态玻璃棱镜折射）
        val x3 = w * 0.5f
        val y3 = h * 0.88f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    c3.copy(alpha = 0.62f * intensity),
                    c3.copy(alpha = 0.25f * intensity),
                    Color.Transparent
                ),
                center = Offset(x3, y3),
                radius = maxDim * 0.85f
            ),
            center = Offset(x3, y3),
            radius = maxDim * 0.85f
        )

        // 5. 磨砂质感极轻遮罩层（增强文字与内容可读性同时保持高通透）
        drawRect(color = Color.Black.copy(alpha = 0.10f))
    }
}

// Kyant0 AndroidLiquidGlass 核心思想：基于 AGSL RuntimeShader 与 GPU 片段着色器实现真实透镜折射、色散与边缘微光
private const val LIQUID_GLASS_AGSL = """
    uniform shader content;
    uniform float2 resolution;
    uniform float cornerRadius;
    uniform float refractionHeight;
    uniform float refractionAmount;
    uniform float chromaticAberration;

    // Signed Distance Field (SDF) of rounded box
    float sdRoundedBox(float2 p, float2 b, float r) {
        float2 q = abs(p) - b + r;
        return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
    }

    float circleMap(float x) {
        return 1.0 - sqrt(1.0 - x * x);
    }

    half4 main(float2 fragCoord) {
        float2 halfSize = resolution * 0.5;
        float2 centeredCoord = fragCoord - halfSize;
        float radius = min(cornerRadius, min(halfSize.x, halfSize.y));

        float sd = sdRoundedBox(centeredCoord, halfSize, radius);
        if (-sd >= refractionHeight) {
            return content.eval(fragCoord);
        }
        sd = min(sd, 0.0);

        // Calculate SDF gradient for realistic lens surface normal
        float eps = 1.0;
        float dx = sdRoundedBox(centeredCoord + float2(eps, 0.0), halfSize, radius) - sd;
        float dy = sdRoundedBox(centeredCoord + float2(0.0, eps), halfSize, radius) - sd;
        float2 normal = normalize(float2(dx, dy) + 0.0001);

        // Lens refraction displacement based on circle profile
        float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
        float2 refractedCoord = fragCoord + d * normal;

        // Chromatic aberration (RGB wavelength dispersion split)
        float dispersionIntensity = chromaticAberration * ((centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y));
        float2 dispersedOffset = d * normal * dispersionIntensity;

        half4 color = half4(0.0);
        half4 red = content.eval(refractedCoord + dispersedOffset);
        half4 green = content.eval(refractedCoord);
        half4 blue = content.eval(refractedCoord - dispersedOffset);

        color.r = red.r;
        color.g = green.g;
        color.b = blue.b;
        color.a = green.a;

        // Specular highlight rim reflection (45-degree angle falloff)
        float highlight = pow(clamp(dot(normal, float2(-0.7071, -0.7071)), 0.0, 1.0), 3.0) * 0.38;
        color.rgb += half3(highlight);

        return color;
    }
"""

fun Modifier.liquidGlassEffect(
    cornerRadius: Float,
    refractionHeight: Float = 36f,
    refractionAmount: Float = 26f,
    blurRadius: Float = 14f,
    chromaticAberration: Float = 1.2f
): Modifier = this.graphicsLayer {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && size.width > 0 && size.height > 0) {
        val blurEffect = RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
        val shader = RuntimeShader(LIQUID_GLASS_AGSL)
        shader.setFloatUniform("resolution", size.width, size.height)
        shader.setFloatUniform("cornerRadius", cornerRadius)
        shader.setFloatUniform("refractionHeight", refractionHeight)
        shader.setFloatUniform("refractionAmount", refractionAmount)
        shader.setFloatUniform("chromaticAberration", chromaticAberration)
        val lensEffect = RenderEffect.createRuntimeShaderEffect(shader, "content")
        val chain = RenderEffect.createChainEffect(lensEffect, blurEffect)
        renderEffect = chain.asComposeRenderEffect()
    }
}

/**
 * 真实 Liquid Glass 卡片（参考 Kyant0 AndroidLiquidGlass 算法与 skill-liquid-glass 规范）
 * - 结合 GPU AGSL 透镜折射 + 色散 + 硬件级模糊
 * - 超高透光微润基底（透明度 0.12f ~ 0.20f），让底层多彩流体光晕自然透射
 * - 0.5dp 极细微高光边缘与镜面菲涅尔反射
 */
/**
 * 真实 Liquid Glass 卡片（参考 Kyant0 AndroidLiquidGlass 算法与 skill-liquid-glass 规范）
 * - 支持 Kyant0 drawBackdrop 真实背景图层捕获 + 透镜折射 + 色散 + 硬件模糊
 * - 独立玻璃底板渲染，前景文字与图标保持 100% 锐利清晰
 * - 0.6dp 极细微高光边缘与镜面菲涅尔反射
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    fillAlpha: Float = 0.15f,
    borderAlpha: Float = 0.22f,
    tintColor: Color = Color.White,
    backdrop: Backdrop? = LocalBackdrop.current,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .shadow(
                elevation = 16.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.40f),
                spotColor = Color.Black.copy(alpha = 0.40f)
            )
            .clip(shape)
    ) {
        // 1. 真实背景捕获与透镜折射底板
        if (backdrop != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { shape },
                        effects = {
                            vibrancy()
                            blur(8f.dp.toPx())
                            lens(20f.dp.toPx(), 24f.dp.toPx(), chromaticAberration = true)
                        },
                        highlight = { Highlight.Default.copy(alpha = 0.40f) },
                        shadow = { Shadow.Default.copy(alpha = 0.30f) },
                        onDrawSurface = {
                            drawRect(Color(0xFF141724).copy(alpha = fillAlpha))
                        }
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .liquidGlassEffect(
                        cornerRadius = 20f * 3f,
                        refractionHeight = 32f,
                        refractionAmount = 22f,
                        blurRadius = 12f
                    )
                    .background(Color(0xFF141724).copy(alpha = fillAlpha))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.15f),
                                Color.White.copy(alpha = 0.03f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.14f)
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(400f, 400f)
                        )
                    )
            )
        }

        // 2. 0.6dp 极细微物理折射边框
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(
                    width = 0.6.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = borderAlpha + 0.12f),
                            Color.White.copy(alpha = borderAlpha * 0.4f),
                            Color.White.copy(alpha = borderAlpha * 0.8f)
                        )
                    ),
                    shape = shape
                )
        )

        // 3. 前景内容层（100% 锐利清晰）
        content()
    }
}

data class TabBarItem(
    val index: Int,
    val title: String,
    val icon: ImageVector
)

/**
 * 严格还原 Beans Music 源码并结合 Kyant0 AndroidLiquidGlass 真实高透流体玻璃：
 * - 结合 GPU AGSL 透镜折射、色散、RenderEffect 硬件模糊
 * - 0.20f 高通透清润基底，底层流动色彩直接透射
 * - 顶部 45° 弱反光，底部微阴影，中心完全透出底层网格流体动态
 * - 选中胶囊块：柔和高光微晶白
 */
@Composable
fun KumoneGlassTabBar(
    items: List<TabBarItem>,
    selectedIndex: Int,
    accentColor: Color,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = LocalBackdrop.current
) {
    val count = items.size.coerceAtLeast(1)
    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "tabSelection"
    )

    val shape = CircleShape

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .shadow(
                elevation = 22.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.50f),
                spotColor = Color.Black.copy(alpha = 0.50f)
            )
            .clip(shape)
    ) {
        // 1. 真实背景捕获透镜折射底板
        if (backdrop != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { shape },
                        effects = {
                            vibrancy()
                            blur(10f.dp.toPx())
                            lens(28f.dp.toPx(), 36f.dp.toPx(), chromaticAberration = true)
                        },
                        highlight = { Highlight.Default.copy(alpha = 0.55f) },
                        shadow = { Shadow.Default.copy(alpha = 0.35f) },
                        onDrawSurface = {
                            drawRect(Color.White.copy(alpha = 0.08f))
                            drawRect(Color(0xFF101320).copy(alpha = 0.10f))
                        }
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .liquidGlassEffect(
                        cornerRadius = 29f * 3f,
                        refractionHeight = 42f,
                        refractionAmount = 30f,
                        blurRadius = 16f,
                        chromaticAberration = 1.4f
                    )
                    .background(Color(0xFF141724).copy(alpha = 0.20f))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.20f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.18f)
                            )
                        )
                    )
            )
        }

        // 2. 0.8dp 极细微高光边缘与镜面反射
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(
                    width = 0.8.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.65f),
                            Color.White.copy(alpha = 0.15f),
                            Color.White.copy(alpha = 0.35f)
                        )
                    ),
                    shape = shape
                )
        )

        val totalWidth = maxWidth
        val cellWidth = totalWidth / count

        // 3. 还原源码 selectionPill：.fill(Color.white.opacity(0.14))
        Box(
            modifier = Modifier
                .width(cellWidth)
                .fillMaxHeight()
                .offset(x = cellWidth * animatedIndex)
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f))
        )

        // 4. Tab 项文字与图标（前景层，100% 锐利清晰）
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isSelected = item.index == selectedIndex
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) accentColor else Color.White.copy(alpha = 0.70f),
                    animationSpec = tween(180),
                    label = "tabColor"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable { onSelectTab(item.index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title,
                        tint = contentColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.title,
                        color = contentColor,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * 原生规范悬浮 Mini 播放器（Beans Music Dock Player 风格，Kyant0 高透光学液态玻璃）
 */
@Composable
fun LiquidMiniPlayer(
    song: BeansSong,
    isPlaying: Boolean,
    progressMs: Long,
    durationMs: Long,
    coverBitmap: Bitmap?,
    accentColor: Color,
    onClick: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = LocalBackdrop.current
) {
    val progressFraction = remember(progressMs, durationMs) {
        if (durationMs > 0) (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    }
    val shape = RoundedCornerShape(20.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 18.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.50f),
                spotColor = Color.Black.copy(alpha = 0.50f)
            )
            .clip(shape)
            .clickable { onClick() }
    ) {
        // 1. 真实背景捕获透镜折射底板
        if (backdrop != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { shape },
                        effects = {
                            vibrancy()
                            blur(10f.dp.toPx())
                            lens(24f.dp.toPx(), 30f.dp.toPx(), chromaticAberration = true)
                        },
                        highlight = { Highlight.Default.copy(alpha = 0.50f) },
                        shadow = { Shadow.Default.copy(alpha = 0.35f) },
                        onDrawSurface = {
                            drawRect(Color.White.copy(alpha = 0.08f))
                            drawRect(Color(0xFF131522).copy(alpha = 0.10f))
                        }
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .liquidGlassEffect(
                        cornerRadius = 20f * 3f,
                        refractionHeight = 36f,
                        refractionAmount = 26f,
                        blurRadius = 14f,
                        chromaticAberration = 1.2f
                    )
                    .background(Color(0xFF131522).copy(alpha = 0.22f))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.18f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.18f)
                            )
                        )
                    )
            )
        }

        // 2. 0.8dp 极细微高光边缘
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(
                    width = 0.8.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.60f),
                            Color.White.copy(alpha = 0.15f),
                            Color.White.copy(alpha = 0.30f)
                        )
                    ),
                    shape = shape
                )
        )

        // 3. 前景播放器内容（100% 锐利清晰）
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SongArtwork(
                    song = song,
                    localBitmap = coverBitmap,
                    size = 42.dp,
                    cornerRadius = 11.dp,
                    accentColor = accentColor
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Toggle",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // 底部平滑细微进度指示
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = progressFraction)
                        .fillMaxHeight()
                        .background(accentColor)
                )
            }
        }
    }
}

@Composable
fun SongArtwork(
    song: BeansSong?,
    localBitmap: Bitmap? = null,
    size: Dp = 52.dp,
    cornerRadius: Dp = 12.dp,
    accentColor: Color = Color(0xFFF2A33C),
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var autoLoadedBitmap by remember(song?.id) { androidx.compose.runtime.mutableStateOf<Bitmap?>(null) }

    androidx.compose.runtime.LaunchedEffect(song?.id, localBitmap) {
        if (localBitmap == null && song != null && autoLoadedBitmap == null) {
            autoLoadedBitmap = LocalMediaHelper.loadCoverBitmap(context, song)
        }
    }

    val displayBitmap = localBitmap ?: autoLoadedBitmap
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.45f),
                        Color(0xFF232736)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            displayBitmap != null -> {
                Image(
                    bitmap = displayBitmap.asImageBitmap(),
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
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(size * 0.46f)
                )
            }
        }
    }
}

@Composable
fun SongRowItem(
    song: BeansSong,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isCurrent) accentColor.copy(alpha = 0.16f) else Color.Transparent
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SongArtwork(
            song = song,
            size = 48.dp,
            cornerRadius = 10.dp,
            accentColor = accentColor
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = song.title,
                    color = if (isCurrent) accentColor else Color.White,
                    fontSize = 15.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (song.source == SongSource.LOCAL) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(accentColor.copy(alpha = 0.22f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = if (song.localPath?.endsWith(".flac", true) == true) "FLAC" else "本机",
                            color = accentColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (song.isVip) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFEC4141).copy(alpha = 0.85f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "VIP",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            val subtitle = buildString {
                append(song.artist)
                if (song.album.isNotBlank()) {
                    append(" · ")
                    append(song.album)
                }
                if (song.formattedDuration.isNotBlank() && song.durationMs > 0) {
                    append(" · ")
                    append(song.formattedDuration)
                }
                if (song.formattedSize.isNotBlank()) {
                    append(" (")
                    append(song.formattedSize)
                    append(")")
                }
            }
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.60f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isCurrent && isPlaying) {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = "Playing",
                tint = accentColor,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(20.dp)
            )
        }
        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (isFavorite) Color(0xFFFF5C77) else Color.White.copy(alpha = 0.55f),
                modifier = Modifier.size(19.dp)
            )
        }
        if (onAddToPlaylist != null) {
            IconButton(
                onClick = onAddToPlaylist,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "加入歌单",
                    tint = Color.White.copy(alpha = 0.65f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun GlassChip(
    text: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                if (selected) accentColor.copy(alpha = 0.88f)
                else Color.White.copy(alpha = 0.08f)
            )
            .border(
                width = 0.5.dp,
                color = if (selected) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.12f),
                shape = CircleShape
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) Color(0xFF101218) else Color.White.copy(alpha = 0.88f),
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
