package com.beans.music

import android.graphics.Bitmap
import org.json.JSONObject

data class NetEaseUserProfile(
    val userId: Long,
    val nickname: String,
    val avatarUrl: String,
    val signature: String = "",
    val vipType: Int = 0,
    val cookie: String = ""
) {
    val isVip: Boolean get() = vipType > 0

    fun toJson(): JSONObject = JSONObject().apply {
        put("userId", userId)
        put("nickname", nickname)
        put("avatarUrl", avatarUrl)
        put("signature", signature)
        put("vipType", vipType)
        put("cookie", cookie)
    }

    companion object {
        fun fromJson(json: JSONObject): NetEaseUserProfile {
            return NetEaseUserProfile(
                userId = json.optLong("userId"),
                nickname = json.optString("nickname", "网易云用户"),
                avatarUrl = json.optString("avatarUrl", ""),
                signature = json.optString("signature", ""),
                vipType = json.optInt("vipType", 0),
                cookie = json.optString("cookie", "")
            )
        }
    }
}

sealed class QrLoginState {
    data object Idle : QrLoginState()
    data class Loading(val msg: String = "正在生成登录二维码...") : QrLoginState()
    data class QrReady(val qrKey: String, val qrBitmap: Bitmap, val qrUrl: String) : QrLoginState()
    data class WaitingScan(val qrBitmap: Bitmap) : QrLoginState()
    data class Scanned(val nickname: String? = null) : QrLoginState()
    data class Success(val profile: NetEaseUserProfile) : QrLoginState()
    data class Error(val message: String) : QrLoginState()
}

data class QQUserProfile(
    val uin: String,
    val nickname: String,
    val avatarUrl: String,
    val cookie: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("uin", uin)
        put("nickname", nickname)
        put("avatarUrl", avatarUrl)
        put("cookie", cookie)
    }

    companion object {
        fun fromJson(json: JSONObject): QQUserProfile {
            return QQUserProfile(
                uin = json.optString("uin"),
                nickname = json.optString("nickname", "QQ音乐用户"),
                avatarUrl = json.optString("avatarUrl", ""),
                cookie = json.optString("cookie", "")
            )
        }
    }
}

sealed class QQLoginState {
    data object Idle : QQLoginState()
    data class Loading(val msg: String = "正在生成 QQ 登录二维码...") : QQLoginState()
    data class QrReady(val qrsig: String, val qrBitmap: Bitmap) : QQLoginState()
    data class WaitingScan(val qrBitmap: Bitmap) : QQLoginState()
    data class Scanned(val msg: String? = null) : QQLoginState()
    data class Success(val profile: QQUserProfile) : QQLoginState()
    data class Error(val message: String) : QQLoginState()
}
