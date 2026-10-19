package com.beans.music

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 17x23 点阵图形与文字渲染器。
 * 内置专为 17x23 尺寸定制的 3x5 像素微型字体、几何图形抗锯齿绘制与光晕算法。
 */
class BackScreenRenderer {

    val buffer = IntArray(StepXBackScreenManager.N)

    fun clear() {
        buffer.fill(0)
    }

    fun setPixel(x: Int, y: Int, brightness: Int) {
        if (x in 0 until StepXBackScreenManager.W && y in 0 until StepXBackScreenManager.H) {
            val idx = y * StepXBackScreenManager.W + x
            val clamped = brightness.coerceIn(0, 255)
            // 采用最大亮度混合，避免覆盖叠加时闪烁
            if (clamped > buffer[idx]) {
                buffer[idx] = clamped
            }
        }
    }

    fun setPixelExact(x: Int, y: Int, brightness: Int) {
        if (x in 0 until StepXBackScreenManager.W && y in 0 until StepXBackScreenManager.H) {
            buffer[y * StepXBackScreenManager.W + x] = brightness.coerceIn(0, 255)
        }
    }

    fun getPixel(x: Int, y: Int): Int {
        if (x in 0 until StepXBackScreenManager.W && y in 0 until StepXBackScreenManager.H) {
            return buffer[y * StepXBackScreenManager.W + x]
        }
        return 0
    }

    // 绘制直线（Bresenham + 光晕）
    fun drawLine(x0: Int, y0: Int, x1: Int, y1: Int, brightness: Int = 255, withGlow: Boolean = true) {
        var cx = x0
        var cy = y0
        val dx = abs(x1 - x0)
        val dy = abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx - dy

        while (true) {
            setPixel(cx, cy, brightness)
            if (withGlow) {
                setPixel(cx, cy - 1, (brightness * 0.45f).toInt())
                setPixel(cx, cy + 1, (brightness * 0.45f).toInt())
                setPixel(cx - 1, cy, (brightness * 0.35f).toInt())
                setPixel(cx + 1, cy, (brightness * 0.35f).toInt())
            }
            if (cx == x1 && cy == y1) break
            val e2 = 2 * err
            if (e2 > -dy) {
                err -= dy
                cx += sx
            }
            if (e2 < dx) {
                err += dx
                cy += sy
            }
        }
    }

    // 绘制实心或空心矩形
    fun drawRect(x: Int, y: Int, w: Int, h: Int, brightness: Int = 255, fill: Boolean = false) {
        for (row in y until y + h) {
            for (col in x until x + w) {
                if (fill || row == y || row == y + h - 1 || col == x || col == x + w - 1) {
                    setPixel(col, row, brightness)
                }
            }
        }
    }

    // 绘制垂直进度条 (从底部向上填充)
    fun drawVerticalBar(x: Int, bottomY: Int, maxHeight: Int, ratio: Float, brightness: Int = 255) {
        val filledH = (ratio.coerceIn(0f, 1f) * maxHeight).roundToInt()
        for (i in 0 until filledH) {
            val y = bottomY - i
            val grad = ((150 + (105f * i / maxHeight))).toInt().coerceIn(100, 255)
            val b = (brightness * (grad / 255f)).toInt()
            setPixel(x, y, b)
        }
    }

    // 绘制水平进度条 (从左到右填充)
    fun drawHorizontalBar(startX: Int, y: Int, maxWidth: Int, ratio: Float, activeBrightness: Int = 230, bgBrightness: Int = 25) {
        val filledW = (ratio.coerceIn(0f, 1f) * maxWidth).roundToInt()
        for (col in 0 until maxWidth) {
            val x = startX + col
            if (col < filledW) {
                setPixel(x, y, activeBrightness)
            } else {
                setPixel(x, y, bgBrightness)
            }
        }
    }

    // 3x5 像素微型字体编码 (每一行 3 个点，高 5 行)
    private val FONT_3X5: Map<Char, IntArray> = mapOf(
        '0' to intArrayOf(0b111, 0b101, 0b101, 0b101, 0b111),
        '1' to intArrayOf(0b010, 0b110, 0b010, 0b010, 0b111),
        '2' to intArrayOf(0b111, 0b001, 0b111, 0b100, 0b111),
        '3' to intArrayOf(0b111, 0b001, 0b111, 0b001, 0b111),
        '4' to intArrayOf(0b101, 0b101, 0b111, 0b001, 0b001),
        '5' to intArrayOf(0b111, 0b100, 0b111, 0b001, 0b111),
        '6' to intArrayOf(0b111, 0b100, 0b111, 0b101, 0b111),
        '7' to intArrayOf(0b111, 0b001, 0b010, 0b010, 0b010),
        '8' to intArrayOf(0b111, 0b101, 0b111, 0b101, 0b111),
        '9' to intArrayOf(0b111, 0b101, 0b111, 0b001, 0b111),
        ':' to intArrayOf(0b000, 0b010, 0b000, 0b010, 0b000),
        '-' to intArrayOf(0b000, 0b000, 0b111, 0b000, 0b000),
        '%' to intArrayOf(0b101, 0b001, 0b010, 0b100, 0b101),
        'V' to intArrayOf(0b101, 0b101, 0b101, 0b101, 0b010),
        'O' to intArrayOf(0b010, 0b101, 0b101, 0b101, 0b010),
        'L' to intArrayOf(0b100, 0b100, 0b100, 0b100, 0b111),
        ' ' to intArrayOf(0b000, 0b000, 0b000, 0b000, 0b000)
    )

    fun measureStringWidth(text: String): Int {
        var w = 0
        for (i in text.indices) {
            val c = text[i]
            w += if (c == ':' || c == ' ') 2 else 3
            if (i < text.length - 1) w += 1 // 字符间距 1 点
        }
        return w
    }

    fun drawChar(c: Char, startX: Int, startY: Int, brightness: Int = 255): Int {
        val pattern = FONT_3X5[c] ?: FONT_3X5[' ']!!
        val charWidth = if (c == ':' || c == ' ') 2 else 3

        for (row in 0 until 5) {
            val bits = pattern[row]
            for (col in 0 until charWidth) {
                // bits 从高位到低位 (第 2 位是 col=0, 第 1 位是 col=1, 第 0 位是 col=2)
                val mask = 1 shl (2 - col)
                if ((bits and mask) != 0) {
                    setPixel(startX + col, startY + row, brightness)
                }
            }
        }
        return charWidth
    }

    fun drawString(text: String, startX: Int, startY: Int, brightness: Int = 255) {
        var curX = startX
        for (i in text.indices) {
            val c = text[i]
            val cw = drawChar(c, curX, startY, brightness)
            curX += cw + 1 // 间距 1 点
        }
    }

    // 居中绘制字符串
    fun drawCenteredString(text: String, y: Int, brightness: Int = 255) {
        val totalW = measureStringWidth(text)
        val startX = max(0, (StepXBackScreenManager.W - totalW) / 2)
        drawString(text, startX, y, brightness)
    }

    // 格式化时间为 MM:SS 或 M:SS
    companion object {
        fun formatDuration(totalSeconds: Long): String {
            val s = max(0L, totalSeconds)
            val m = s / 60
            val sec = s % 60
            return "%d:%02d".format(m, sec)
        }
    }
}
