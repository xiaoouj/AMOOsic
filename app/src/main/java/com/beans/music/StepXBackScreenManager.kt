package com.beans.music

import android.content.Context
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import java.lang.reflect.Method
import java.util.Arrays

/**
 * STEPX Neo (PZ261A) 17x23 MiniLED 背屏控制驱动。
 *
 * 通过反射系统隐藏服务 display_ext (StepOSDisplayManagerService) 实现点阵渲染。
 * 分辨率: 17 列 x 23 行，共 391 个点。
 * 格式: int[391]，行优先 (y * 17 + x)，每个元素亮度 0..255。
 */
class StepXBackScreenManager private constructor(context: Context) {

    companion object {
        const val W = 17
        const val H = 23
        const val N = W * H // 391

        private const val TAG = "StepXBackScreen"
        private const val SERVICE_NAME = "display_ext"

        // 物理死区掩膜：四角斜切深度约 3 点，死区 LED 硬件不存在，必须强制为 0
        val DOT_OK: BooleanArray = BooleanArray(N) { idx ->
            val x = idx % W
            val y = idx / W
            val depth = 3
            val ct = maxOf(0, depth - y)
            val cb = maxOf(0, depth - (H - 1 - y))
            x >= ct && x <= W - 1 - ct && x >= cb && x <= W - 1 - cb
        }

        @Volatile
        private var instance: StepXBackScreenManager? = null

        fun getInstance(context: Context): StepXBackScreenManager {
            return instance ?: synchronized(this) {
                instance ?: StepXBackScreenManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private var mProxy: Any? = null
    private var mManager: Any? = null
    var isAvailable: Boolean = false
        private set
    var detailInfo: String = ""
        private set

    init {
        initService(context)
    }

    private fun initService(ctx: Context) {
        var ok = false
        var det = ""
        var err = ""

        // 路径 1: Context.getSystemService("display_ext")
        try {
            val svc = ctx.getSystemService(SERVICE_NAME)
            if (svc != null && svc.javaClass.name.contains("StepOSDisplayManager")) {
                mManager = svc
                ok = true
                det = "client"
            }
        } catch (t: Throwable) {
            err += "gss:${t.javaClass.simpleName}; "
        }

        // 路径 2: ServiceManager.getService("display_ext") -> Stub.asInterface
        if (!ok) {
            try {
                val sm = Class.forName("android.os.ServiceManager")
                val binder = sm.getMethod("getService", String::class.java).invoke(null, SERVICE_NAME) as? IBinder
                if (binder != null) {
                    val stub = Class.forName("android.hardware.display.IStepOSDisplayManager\$Stub")
                    mProxy = stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
                    ok = true
                    det = "binder"
                } else {
                    err += "sm:binder_null; "
                }
            } catch (t: Throwable) {
                err += "sm:$t; "
            }
        }

        isAvailable = ok
        detailInfo = if (ok) det else err
        Log.i(TAG, "StepXBackScreenManager init: available=$isAvailable, detail=$detailInfo")
    }

    private fun call(method: String, vararg args: Any?): Any? {
        if (mProxy != null) {
            val m = findMethod(mProxy!!.javaClass, method, args.size)
                ?: findMethod(Class.forName("android.hardware.display.IStepOSDisplayManager"), method, args.size)
                ?: throw NoSuchMethodException("$method/${args.size}")
            m.isAccessible = true
            return m.invoke(mProxy, *args)
        }
        if (mManager != null) {
            val m = findMethod(mManager!!.javaClass, method, args.size)
            if (m != null) {
                m.isAccessible = true
                return m.invoke(mManager, *args)
            }
        }
        throw NoSuchMethodException(method)
    }

    private fun tryCall(method: String, vararg args: Any?): Boolean {
        return try {
            call(method, *args)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "$method failed: $t")
            false
        }
    }

    private fun findMethod(cls: Class<*>, name: String, argc: Int): Method? {
        var c: Class<*>? = cls
        while (c != null && c != Any::class.java) {
            for (m in c.declaredMethods) {
                if (m.name == name && m.parameterTypes.size == argc) {
                    runCatching { m.isAccessible = true }
                    return m
                }
            }
            c = c.superclass
        }
        for (ifc in cls.interfaces) {
            for (m in ifc.declaredMethods) {
                if (m.name == name && m.parameterTypes.size == argc) {
                    runCatching { m.isAccessible = true }
                    return m
                }
            }
        }
        return null
    }

    /**
     * 推送一帧 17x23 点阵画面到背屏。
     * @param frame 长度必须为 391 (W * H)，各点亮度 0..255。
     * @param brightnessScale 整体亮度缩放 (0.1f .. 1.0f)
     */
    fun sendFrame(frame: IntArray, brightnessScale: Float = 1.0f): Boolean {
        if (!isAvailable || frame.size != N) return false
        val copy = Arrays.copyOf(frame, N)
        val scale = brightnessScale.coerceIn(0.05f, 1.0f)

        for (i in 0 until N) {
            if (!DOT_OK[i]) {
                copy[i] = 0
            } else if (scale < 0.99f) {
                copy[i] = (copy[i] * scale).toInt().coerceIn(0, 255)
            }
        }

        return tryCall("setBackScreenAgentFrame", copy, Bundle())
    }

    /**
     * 清空自定义画面，恢复系统默认状态/空闲。
     */
    fun clearFrame(): Boolean {
        if (!isAvailable) return false
        var ok = tryCall("clearBackScreenAgentFrame")
        if (!ok) ok = tryCall("clearBackScreenAgentBitmap")
        return ok
    }

    /**
     * 设置 Agent 状态。
     * 播放时设为 "task_running"，告知背屏工坊（HandoffPolicy）进入繁忙避让态；
     * 暂停时设为 "idle" 释放给背屏工坊恢复。
     */
    fun setAgentState(state: String): Boolean {
        if (!isAvailable) return false
        if (mProxy != null) return tryCall("setBackScreenAgentState", state, Bundle())
        return tryCall("setBackScreenAgentState", state)
    }

    /**
     * 复位 Agent 状态为 idle，完全释放控制权。
     */
    fun resetAgent(): Boolean {
        return setAgentState("idle")
    }

    /**
     * 设置背屏启用状态
     */
    fun setEnabled(enabled: Boolean): Boolean {
        return tryCall("setBackScreenEnabled", enabled)
    }

    /**
     * 设置背屏显示策略: 0=自动 (翻转/抬起显示), 1=常显, 2=自定义, 3=仅翻转
     */
    fun setDisplayMode(mode: Int): Boolean {
        return tryCall("setBackScreenDisplayMode", mode)
    }

    /**
     * 设置自动亮度
     */
    fun setBrightnessMode(auto: Boolean): Boolean {
        return tryCall("setBackScreenBrightnessMode", auto)
    }

    /**
     * 设置背屏整体亮度 (0..255)
     */
    fun setBrightness(brightness: Int): Boolean {
        return tryCall("setBackScreenBrightness", brightness.coerceIn(0, 255))
    }
}
