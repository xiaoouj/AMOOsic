package com.beans.music

import android.content.SharedPreferences
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

enum class BackScreenVisualMode(val title: String, val desc: String) {
    RIPPLE("波浪涟漪", "同心水波随低音节奏扩散反射"),
    LINE("折线波形", "示波器动感折线与光晕跳跃"),
    SPECTRUM("柱状频谱", "17 段专业柱状均衡器与峰值驻留"),
    METEOR("流星律动", "高低频触发多道彗尾下落流星"),
    INFO_PROGRESS("播放进度与时间", "清晰显示播放进度、当前与剩余时间")
}

/**
 * STEPX Neo 背屏音乐联动引擎。
 * 驱动 17x23 点阵背屏实现波浪涟漪、折线、柱状频谱、流星雨、播放进度时钟及音量实时进度条。
 */
class BackScreenMusicEngine(private val context: Context) {

    companion object {
        private const val TAG = "BackScreenMusicEngine"
        private const val FRAME_DELAY_MS = 50L // ~20fps 平滑流畅且兼顾功耗
        private const val VOLUME_OVERLAY_DURATION_MS = 2400L
    }

    private val backScreen = StepXBackScreenManager.getInstance(context)
    private val renderer = BackScreenRenderer()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val prefs = context.getSharedPreferences("beans_music_prefs", Context.MODE_PRIVATE)

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "backscreen_enabled", "backscreen_mode", "backscreen_brightness", "backscreen_volume_overlay" -> {
                loadPrefs()
            }
        }
    }

    private fun loadPrefs() {
        isEnabled = prefs.getBoolean("backscreen_enabled", true)
        val modeName = prefs.getString("backscreen_mode", BackScreenVisualMode.RIPPLE.name)
        currentMode = runCatching { BackScreenVisualMode.valueOf(modeName ?: "RIPPLE") }.getOrDefault(BackScreenVisualMode.RIPPLE)
        brightnessScale = prefs.getFloat("backscreen_brightness", 0.9f)
        showVolumeOverlayEnabled = prefs.getBoolean("backscreen_volume_overlay", true)
    }

    // 状态配置
    var isEnabled: Boolean = true
        set(value) {
            field = value
            if (!value) {
                stopLoop()
                backScreen.clearFrame()
            } else if (isPlaying) {
                startLoop()
            }
        }

    var currentMode: BackScreenVisualMode = BackScreenVisualMode.RIPPLE
        set(value) {
            field = value
            renderer.clear()
        }

    var brightnessScale: Float = 0.9f // 0.1 .. 1.0
    var showVolumeOverlayEnabled: Boolean = true

    // 播放状态监听
    private var isPlaying: Boolean = false
    private var currentPositionMs: Long = 0L
    private var durationMs: Long = 0L
    private var currentAudioSessionId: Int = 0

    // 音量浮层状态
    private var volumeOverlayUntilMs: Long = 0L
    private var currentVolumeRatio: Float = 0.5f

    // 音频采集 Visualizer
    private var visualizer: Visualizer? = null
    private val fftBuffer = ByteArray(512)
    private val waveformBuffer = ByteArray(512)

    // 17 个频段能量 (平滑后，范围 0.0f .. 1.0f)
    private val bands = FloatArray(17) { 0f }
    // 柱状频谱峰值驻留高度 (0f .. 22f)
    private val peakHeights = FloatArray(17) { 0f }

    // 涟漪水波系统状态
    private data class RippleRing(var radius: Float, var speed: Float, var intensity: Float, var maxRadius: Float = 16f)
    private val activeRipples = ArrayList<RippleRing>()

    // 流星系统状态
    private data class Meteor(var col: Int, var y: Float, var speed: Float, var length: Int, var brightness: Int)
    private val activeMeteors = ArrayList<Meteor>()

    // 节拍仿真器（当 Visualizer 权限未授予或某些音源无直接 FFT 时提供平滑自然的动效）
    private var simPhase: Float = 0f

    // 音量广播监听器
    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                val streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
                if (streamType == AudioManager.STREAM_MUSIC || streamType == -1) {
                    val maxV = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                    val curV = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    triggerVolumeOverlay(curV.toFloat() / maxV)
                }
            }
        }
    }

    private var isVolumeReceiverRegistered = false

    private val frameRunnable = object : Runnable {
        override fun run() {
            if (!isEnabled) return
            renderNextFrame()
            mainHandler.postDelayed(this, FRAME_DELAY_MS)
        }
    }

    init {
        loadPrefs()
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        registerVolumeReceiver()
    }

    private fun registerVolumeReceiver() {
        if (!isVolumeReceiverRegistered) {
            val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
            runCatching {
                context.registerReceiver(volumeReceiver, filter)
                isVolumeReceiverRegistered = true
            }
        }
    }

    fun unregisterVolumeReceiver() {
        if (isVolumeReceiverRegistered) {
            runCatching { context.unregisterReceiver(volumeReceiver) }
            isVolumeReceiverRegistered = false
        }
    }

    fun updatePlaybackState(playing: Boolean, positionMs: Long, totalDurationMs: Long, audioSessionId: Int = 0) {
        val wasPlaying = isPlaying
        isPlaying = playing
        currentPositionMs = positionMs
        durationMs = totalDurationMs

        if (audioSessionId > 0 && audioSessionId != currentAudioSessionId) {
            currentAudioSessionId = audioSessionId
            setupVisualizer(audioSessionId)
        }

        if (playing && isEnabled) {
            startLoop()
        } else if (!playing && wasPlaying) {
            // 暂停/停止时：立刻完全释放背屏，清除画面并复位 agent 为 idle，让背屏工坊立即无缝恢复显示！
            stopLoop()
        }
    }

    fun triggerVolumeOverlay(ratio: Float) {
        if (!showVolumeOverlayEnabled) return
        currentVolumeRatio = ratio.coerceIn(0f, 1f)
        volumeOverlayUntilMs = System.currentTimeMillis() + VOLUME_OVERLAY_DURATION_MS
        if (isEnabled) {
            startLoop()
        }
    }

    private fun startLoop() {
        mainHandler.removeCallbacks(frameRunnable)
        backScreen.setEnabled(true)
        backScreen.setDisplayMode(1) // 播放动画时开启背屏常显
        backScreen.setAgentState("task_running") // 最高优先级：设为 task_running，背屏工坊 HandoffPolicy 识别 busy 并主动避让
        mainHandler.post(frameRunnable)
    }

    private fun stopLoop() {
        mainHandler.removeCallbacks(frameRunnable)
        backScreen.clearFrame() // 彻底清除自定义帧
        backScreen.resetAgent() // 复位 Agent 为 idle，让背屏工坊安全恢复
        backScreen.setDisplayMode(0) // 恢复系统默认自动显示模式
    }

    private fun setupVisualizer(sessionId: Int) {
        releaseVisualizer()
        try {
            val v = Visualizer(sessionId).apply {
                captureSize = 256
                scalingMode = Visualizer.SCALING_MODE_NORMALIZED
                enabled = true
            }
            visualizer = v
            Log.i(TAG, "Visualizer attached to session $sessionId successfully")
        } catch (t: Throwable) {
            Log.w(TAG, "Cannot init Visualizer with session $sessionId: ${t.message}. Falling back to dynamic energy model.")
            visualizer = null
        }
    }

    private fun releaseVisualizer() {
        runCatching {
            visualizer?.enabled = false
            visualizer?.release()
        }
        visualizer = null
    }

    private fun captureAudioData() {
        simPhase += 0.12f
        var hasRealFft = false

        visualizer?.let { v ->
            if (runCatching { v.getFft(fftBuffer) == Visualizer.SUCCESS }.getOrDefault(false)) {
                hasRealFft = true
                val n = fftBuffer.size / 2
                // 17 个对数间隔频段映射
                val bandEdges = intArrayOf(
                    1, 2, 3, 5, 7, 10, 14, 19, 26, 35, 47, 63, 85, 114, 150, 195, 240, 255
                )
                for (b in 0 until 17) {
                    var sum = 0f
                    val start = bandEdges[b]
                    val end = min(bandEdges[b + 1], n)
                    var count = 0
                    for (k in start until end) {
                        val r = fftBuffer[2 * k].toInt()
                        val i = fftBuffer[2 * k + 1].toInt()
                        sum += sqrt((r * r + i * i).toFloat())
                        count++
                    }
                    val avg = if (count > 0) (sum / count) / 128f else 0f
                    // 平滑衰减
                    bands[b] = max(avg.coerceIn(0f, 1f), bands[b] * 0.72f)
                }
            }
        }

        // 模拟节奏生成器（当无真实麦克风权限或静音时提供真实自然的脉冲律动）
        if (!hasRealFft) {
            val bassPulse = max(0f, sin(simPhase * 1.5f)).let { it * it }
            val midPulse = max(0f, cos(simPhase * 2.2f + 1.2f))
            val treblePulse = max(0f, sin(simPhase * 3.7f + 2.5f))

            for (b in 0 until 17) {
                val weight = when {
                    b < 4 -> bassPulse * 0.9f + 0.1f * sin(simPhase + b)
                    b < 11 -> midPulse * 0.75f + 0.2f * cos(simPhase * 1.8f + b)
                    else -> treblePulse * 0.65f + 0.15f * sin(simPhase * 2.5f + b)
                }.coerceIn(0f, 1f)
                bands[b] = max(weight, bands[b] * 0.75f)
            }
        }
    }

    private fun renderNextFrame() {
        val now = System.currentTimeMillis()
        val isVolumeActive = showVolumeOverlayEnabled && (now < volumeOverlayUntilMs)

        if (!isPlaying && !isVolumeActive) {
            stopLoop()
            return
        }

        captureAudioData()
        renderer.clear()

        if (isVolumeActive) {
            renderVolumeOverlay(currentVolumeRatio)
        } else {
            when (currentMode) {
                BackScreenVisualMode.RIPPLE -> renderRippleMode()
                BackScreenVisualMode.LINE -> renderLineMode()
                BackScreenVisualMode.SPECTRUM -> renderSpectrumMode()
                BackScreenVisualMode.METEOR -> renderMeteorMode()
                BackScreenVisualMode.INFO_PROGRESS -> renderInfoProgress(isPaused = false)
            }
        }

        backScreen.sendFrame(renderer.buffer, brightnessScale)
    }

    // ==========================================
    // 模式 1: 波浪涟漪 (RIPPLE)
    // ==========================================
    private fun renderRippleMode() {
        val cx = 8.0f
        val cy = 11.0f
        val bassEnergy = (bands[0] + bands[1] + bands[2] + bands[3]) / 4f

        // 当低音冲击产生时，从中心生成新涟漪水环
        if (bassEnergy > 0.45f && (activeRipples.isEmpty() || activeRipples.last().radius > 3.0f)) {
            activeRipples.add(RippleRing(radius = 0.5f, speed = 0.65f + bassEnergy * 0.35f, intensity = bassEnergy))
        }

        // 更新与绘制水波
        val it = activeRipples.iterator()
        while (it.hasNext()) {
            val r = it.next()
            r.radius += r.speed
            r.intensity *= 0.88f // 衰减
            if (r.radius > r.maxRadius || r.intensity < 0.04f) {
                it.remove()
                continue
            }

            for (y in 0 until StepXBackScreenManager.H) {
                for (x in 0 until StepXBackScreenManager.W) {
                    val dist = sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy))
                    val diff = abs(dist - r.radius)
                    // 高斯扩散水波边缘
                    if (diff < 2.0f) {
                        val factor = exp(-((diff * diff) / 0.85f))
                        val b = (255f * r.intensity * factor).toInt()
                        renderer.setPixel(x, y, b)
                    }
                }
            }
        }

        // 中心水珠脉冲
        val centerB = (140 + 115 * bassEnergy).toInt().coerceIn(0, 255)
        renderer.setPixel(8, 11, centerB)
        renderer.setPixel(7, 11, (centerB * 0.6f).toInt())
        renderer.setPixel(9, 11, (centerB * 0.6f).toInt())
        renderer.setPixel(8, 10, (centerB * 0.6f).toInt())
        renderer.setPixel(8, 12, (centerB * 0.6f).toInt())
    }

    // ==========================================
    // 模式 2: 折线波形 (LINE)
    // ==========================================
    private fun renderLineMode() {
        val baseY = 11
        val pointsY = IntArray(17)

        for (x in 0 until 17) {
            val b = bands[x]
            val offset = ((b - 0.25f) * 16f * if (x % 2 == 0) 1 else -1).toInt().coerceIn(-9, 9)
            pointsY[x] = (baseY + offset).coerceIn(1, 21)
        }

        // 绘制折线连接
        for (x in 0 until 16) {
            renderer.drawLine(x, pointsY[x], x + 1, pointsY[x + 1], brightness = 255, withGlow = true)
        }

        // 水平基线微微透出暗光
        for (x in 0 until 17) {
            renderer.setPixel(x, baseY, 40)
        }
    }

    // ==========================================
    // 模式 3: 柱状频谱 (SPECTRUM)
    // ==========================================
    private fun renderSpectrumMode() {
        val bottomY = 22
        val maxHeight = 22

        for (x in 0 until 17) {
            val ratio = bands[x].coerceIn(0f, 1f)
            val curHeight = ratio * maxHeight

            // 绘制主柱状能量条
            renderer.drawVerticalBar(x, bottomY, maxHeight, ratio, brightness = 240)

            // 峰值驻留点计算与绘制
            if (curHeight >= peakHeights[x]) {
                peakHeights[x] = curHeight
            } else {
                peakHeights[x] = max(0f, peakHeights[x] - 0.45f) // 重力缓慢下落
            }

            val peakY = (bottomY - peakHeights[x].toInt()).coerceIn(0, 22)
            renderer.setPixel(x, peakY, 255) // 最顶端高亮停驻点
        }
    }

    // ==========================================
    // 模式 4: 流星律动 (METEOR)
    // ==========================================
    private fun renderMeteorMode() {
        // 音频中高频触发新流星
        for (col in 0 until 17) {
            if (bands[col] > 0.48f && Math.random() < 0.28) {
                activeMeteors.add(
                    Meteor(
                        col = col,
                        y = 0f,
                        speed = 0.5f + bands[col] * 0.9f,
                        length = (3 + bands[col] * 3).toInt().coerceIn(3, 6),
                        brightness = (180 + bands[col] * 75).toInt().coerceIn(150, 255)
                    )
                )
            }
        }

        // 更新与绘制流星
        val it = activeMeteors.iterator()
        while (it.hasNext()) {
            val m = it.next()
            m.y += m.speed
            val headY = m.y.toInt()

            // 头部
            if (headY in 0 until StepXBackScreenManager.H) {
                renderer.setPixel(m.col, headY, m.brightness)
            }

            // 彗尾
            for (t in 1..m.length) {
                val tailY = headY - t
                if (tailY in 0 until StepXBackScreenManager.H) {
                    val tailFade = 1.0f - (t.toFloat() / (m.length + 1))
                    val b = (m.brightness * tailFade * 0.65f).toInt()
                    renderer.setPixel(m.col, tailY, b)
                }
            }

            // 超出底部清除
            if (headY - m.length >= StepXBackScreenManager.H) {
                it.remove()
            }
        }

        // 底部微光地平线
        for (x in 0 until 17) {
            renderer.setPixel(x, 22, 35)
        }
    }

    // ==========================================
    // 模式 5: 播放信息与进度 (无频谱模式)
    // ==========================================
    private fun renderInfoProgress(isPaused: Boolean) {
        val curSec = currentPositionMs / 1000
        val totalSec = max(1L, durationMs / 1000)
        val remainSec = max(0L, totalSec - curSec)
        val progressRatio = (currentPositionMs.toFloat() / max(1L, durationMs)).coerceIn(0f, 1f)

        // 第 0..1 行：顶部全宽播放进度条 (17 列)
        renderer.drawHorizontalBar(startX = 0, y = 0, maxWidth = 17, ratio = progressRatio, activeBrightness = 250, bgBrightness = 25)
        renderer.drawHorizontalBar(startX = 0, y = 1, maxWidth = 17, ratio = progressRatio, activeBrightness = 250, bgBrightness = 25)

        // 第 3 行：居中播放/暂停状态小指示符
        if (isPaused) {
            // 暂停符号 ❚❚ (两道竖线)
            renderer.setPixel(7, 3, 220)
            renderer.setPixel(9, 3, 220)
        } else {
            // 播放三角符号 ▶
            renderer.setPixel(7, 3, 160)
            renderer.setPixel(8, 3, 255)
            renderer.setPixel(9, 3, 160)
        }

        // 第 5..9 行：当前播放时间 (例如 1:25)
        val curTimeStr = BackScreenRenderer.formatDuration(curSec)
        renderer.drawCenteredString(curTimeStr, y = 5, brightness = 255)

        // 第 11 行：微光分割线
        for (x in 2 until 15) {
            renderer.setPixel(x, 11, if (x % 2 == 0) 50 else 20)
        }

        // 第 13..17 行：剩余时长 (例如 -2:40)
        val remainTimeStr = "-" + BackScreenRenderer.formatDuration(remainSec)
        renderer.drawCenteredString(remainTimeStr, y = 13, brightness = 210)

        // 第 19..22 行：音符律动标志
        val beatPhase = (System.currentTimeMillis() / 200) % 4
        val noteY = 20
        renderer.setPixel(8, noteY, 120)
        renderer.setPixel(7, noteY + 1, 180)
        renderer.setPixel(9, noteY + 1, 180)
        renderer.setPixel(8 + (beatPhase.toInt() - 2).coerceIn(-1, 1), noteY - 1, 240)
    }

    // ==========================================
    // 音量调节实时覆盖图层 (VOLUME_OVERLAY)
    // ==========================================
    private fun renderVolumeOverlay(volumeRatio: Float) {
        val pct = (volumeRatio * 100).toInt().coerceIn(0, 100)

        // 第 1..3 行：微型扬声器图标 🔊
        renderer.setPixel(3, 2, 255)
        renderer.setPixel(4, 1, 255)
        renderer.setPixel(4, 2, 255)
        renderer.setPixel(4, 3, 255)
        if (pct > 0) {
            renderer.setPixel(6, 2, 180)
            renderer.setPixel(7, 1, 220)
            renderer.setPixel(7, 3, 220)
        }
        if (pct > 50) {
            renderer.setPixel(9, 0, 200)
            renderer.setPixel(9, 2, 240)
            renderer.setPixel(9, 4, 200)
        }

        // 第 5..9 行：音量百分比 (例如 75% 或 100)
        val pctStr = if (pct >= 100) "100" else "$pct%"
        renderer.drawCenteredString(pctStr, y = 6, brightness = 255)

        // 第 12..21 行：大尺寸垂直音量条 (高 10 格，宽 7 格)
        val barLeft = 5
        val barWidth = 7
        val barTop = 12
        val barHeight = 9
        val filledSteps = (volumeRatio * barHeight).toInt().coerceIn(0, barHeight)

        // 外框
        renderer.drawRect(barLeft, barTop, barWidth, barHeight + 1, brightness = 50, fill = false)

        // 填充进度
        for (step in 0 until filledSteps) {
            val y = (barTop + barHeight) - step
            val grad = 160 + (95 * step / barHeight)
            for (x in (barLeft + 1) until (barLeft + barWidth - 1)) {
                renderer.setPixel(x, y, grad)
            }
        }
    }

    fun release() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        stopLoop()
        unregisterVolumeReceiver()
        releaseVisualizer()
        backScreen.clearFrame()
    }
}
