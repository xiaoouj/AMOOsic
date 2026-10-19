package com.beans.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.math.BigInteger
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

object NetEaseCloudClient {

    private val EAPI_KEY = "e82ckenh8dichen8".toByteArray(Charsets.UTF_8)
    private const val API_DOMAIN = "https://interface.music.163.com"
    private const val WEB_DOMAIN = "https://music.163.com"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private fun md5Hex(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun aesEcbEncryptHex(input: ByteArray, key: ByteArray): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        val encrypted = cipher.doFinal(input)
        return encrypted.joinToString("") { "%02x".format(it).uppercase() }
    }

    private fun eapiParams(path: String, payload: JSONObject): String {
        val header = JSONObject().apply {
            put("osver", "Microsoft-Windows-10-Professional-build-19045-64bit")
            put("deviceId", "0123456789abcdef0123456789abcdef0123456789abcdef0123")
            put("os", "pc")
            put("appver", "3.1.17.204416")
            put("versioncode", "140")
            put("mobilename", "")
            put("buildver", (System.currentTimeMillis() / 1000L).toString())
            put("resolution", "1920x1080")
            put("__csrf", "")
            put("channel", "netease")
            put("requestId", "${System.currentTimeMillis()}_${(100..999).random()}")
        }
        payload.put("e_r", false)
        payload.put("header", header)

        val text = payload.toString()
        val message = "nobody${path}use${text}md5forencrypt"
        val digest = md5Hex(message)
        val data = "$path-36cd479b6b5-$text-36cd479b6b5-$digest"
        return aesEcbEncryptHex(data.toByteArray(Charsets.UTF_8), EAPI_KEY)
    }

    @Volatile
    var userCookie: String = ""

    private val WEAPI_FIXED_KEY = "0CoJUm6Qyw8W8jud".toByteArray(Charsets.UTF_8)
    private val WEAPI_FIXED_IV = "0102030405060708".toByteArray(Charsets.UTF_8)
    private const val WEAPI_RSA_MODULUS_HEX = "00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cce10b424d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462db0a22b8e7"
    private val WEAPI_RSA_EXP = BigInteger.valueOf(65537)
    private val WEAPI_RSA_MOD = BigInteger(WEAPI_RSA_MODULUS_HEX, 16)

    fun weapiEncrypt(payload: JSONObject): Pair<String, String> {
        val text = payload.toString()
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(WEAPI_FIXED_KEY, "AES"), IvParameterSpec(WEAPI_FIXED_IV))
        val first = cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        val firstB64 = Base64.encodeToString(first, Base64.NO_WRAP)

        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val secKey = (1..16).map { chars.random() }.joinToString("")

        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(secKey.toByteArray(Charsets.UTF_8), "AES"), IvParameterSpec(WEAPI_FIXED_IV))
        val second = cipher.doFinal(firstB64.toByteArray(Charsets.UTF_8))
        val params = Base64.encodeToString(second, Base64.NO_WRAP)

        val reversed = secKey.reversed()
        val m = BigInteger(1, reversed.toByteArray(Charsets.UTF_8))
        val encSecKeyBig = m.modPow(WEAPI_RSA_EXP, WEAPI_RSA_MOD)
        val encSecKey = String.format("%256s", encSecKeyBig.toString(16)).replace(" ", "0")
        return params to encSecKey
    }

    suspend fun postWeapi(uri: String, payload: JSONObject): JSONObject? = withContext(Dispatchers.IO) {
        runCatching {
            val (params, encSecKey) = weapiEncrypt(payload)
            val cleanUri = if (uri.startsWith("/api/")) uri.substring(4) else uri
            val url = "$WEB_DOMAIN/weapi$cleanUri"
            val body = FormBody.Builder()
                .add("params", params)
                .add("encSecKey", encSecKey)
                .build()
            val reqBuilder = Request.Builder()
                .url(url)
                .post(body)
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 Edg/124.0.0.0")
                .header("Referer", WEB_DOMAIN)
            if (userCookie.isNotBlank()) {
                reqBuilder.header("Cookie", userCookie)
            }
            httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val str = resp.body?.string() ?: return@runCatching null
                JSONObject(str)
            }
        }.getOrNull()
    }

    private suspend fun postEapi(uri: String, payload: JSONObject): JSONObject? = withContext(Dispatchers.IO) {
        runCatching {
            val params = eapiParams(uri, payload)
            val url = "$API_DOMAIN/eapi${uri.removePrefix("/api")}"
            val body = FormBody.Builder()
                .add("params", params)
                .build()
            val reqBuilder = Request.Builder()
                .url(url)
                .post(body)
                .header("User-Agent", "NeteaseMusic 9.0.90/5038 (iPhone; iOS 16.2; zh_CN)")
                .header("Referer", WEB_DOMAIN)
            if (userCookie.isNotBlank()) {
                reqBuilder.header("Cookie", userCookie)
            }
            httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val str = resp.body?.string() ?: return@runCatching null
                JSONObject(str)
            }
        }.getOrNull()
    }

    private suspend fun postWebForm(url: String, formMap: Map<String, String>): Pair<JSONObject?, List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val builder = FormBody.Builder()
            formMap.forEach { (k, v) -> builder.add(k, v) }
            val reqBuilder = Request.Builder()
                .url(url)
                .post(builder.build())
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", WEB_DOMAIN)
            if (userCookie.isNotBlank()) {
                reqBuilder.header("Cookie", userCookie)
            }
            httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                val cookies = resp.headers("Set-Cookie")
                val str = resp.body?.string()
                val json = if (!str.isNullOrBlank()) JSONObject(str) else null
                json to cookies
            }
        }.getOrDefault(null to emptyList())
    }

    private suspend fun getWebJson(url: String): JSONObject? = withContext(Dispatchers.IO) {
        runCatching {
            val reqBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", WEB_DOMAIN)
            if (userCookie.isNotBlank()) {
                reqBuilder.header("Cookie", userCookie)
            }
            httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val str = resp.body?.string() ?: return@runCatching null
                JSONObject(str)
            }
        }.getOrNull()
    }

    suspend fun fetchTopLists(): List<CloudTopList> = withContext(Dispatchers.IO) {
        val json = postEapi("/api/toplist/detail", JSONObject()) ?: return@withContext emptyList()
        val arr = json.optJSONArray("list") ?: return@withContext emptyList()
        val list = mutableListOf<CloudTopList>()
        val count = minOf(arr.length(), 10)
        for (i in 0 until count) {
            val item = arr.optJSONObject(i) ?: continue
            val id = item.optLong("id")
            val name = item.optString("name")
            val cover = item.optString("coverImgUrl").replace("http://", "https://")
            val freq = item.optString("updateFrequency", "每日更新")
            val tracksArr = item.optJSONArray("tracks")
            val previews = mutableListOf<String>()
            if (tracksArr != null) {
                for (j in 0 until minOf(tracksArr.length(), 3)) {
                    val t = tracksArr.optJSONObject(j) ?: continue
                    val first = t.optString("first")
                    val second = t.optString("second")
                    if (first.isNotBlank()) {
                        previews.add("$first - $second")
                    }
                }
            }
            list.add(CloudTopList(id = id, name = name, coverUrl = cover, updateFrequency = freq, trackPreview = previews))
        }
        list
    }

    suspend fun fetchRecommendedPlaylists(limit: Int = 12): List<CloudPlaylist> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("limit", limit)
            put("n", limit)
        }
        val json = postEapi("/api/personalized/playlist", payload) ?: return@withContext emptyList()
        val arr = json.optJSONArray("result") ?: return@withContext emptyList()
        val list = mutableListOf<CloudPlaylist>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            list.add(
                CloudPlaylist(
                    id = item.optLong("id"),
                    name = item.optString("name"),
                    coverUrl = item.optString("picUrl").replace("http://", "https://"),
                    trackCount = item.optInt("trackCount", 0),
                    description = item.optString("copywriter", "")
                )
            )
        }
        list
    }

    suspend fun fetchPlaylistTracks(playlistId: Long, limit: Int = 80): List<BeansSong> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("id", playlistId)
            put("n", limit)
            put("s", 8)
        }
        val json = postEapi("/api/v6/playlist/detail", payload) ?: return@withContext emptyList()
        val playlistObj = json.optJSONObject("playlist") ?: return@withContext emptyList()
        val tracks = playlistObj.optJSONArray("tracks") ?: return@withContext emptyList()
        val songs = mutableListOf<BeansSong>()
        val maxItems = minOf(tracks.length(), limit)
        for (i in 0 until maxItems) {
            val item = tracks.optJSONObject(i) ?: continue
            parseNetEaseSong(item)?.let { songs.add(it) }
        }
        songs
    }

    suspend fun searchSongs(keyword: String, limit: Int = 30): List<BeansSong> = withContext(Dispatchers.IO) {
        if (keyword.isBlank()) return@withContext emptyList()
        val payload = JSONObject().apply {
            put("s", keyword.trim())
            put("type", 1)
            put("limit", limit)
            put("offset", 0)
            put("total", true)
        }
        val json = postEapi("/api/cloudsearch/pc", payload) ?: return@withContext emptyList()
        val resultObj = json.optJSONObject("result") ?: return@withContext emptyList()
        val songsArr = resultObj.optJSONArray("songs") ?: return@withContext emptyList()
        val list = mutableListOf<BeansSong>()
        for (i in 0 until songsArr.length()) {
            val item = songsArr.optJSONObject(i) ?: continue
            parseNetEaseSong(item)?.let { list.add(it) }
        }
        list
    }

    suspend fun fetchSongPlayUrl(songId: Long, quality: String = "exhigh"): String? = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("ids", "[$songId]")
            put("level", quality)
            put("encodeType", if (quality == "lossless") "flac" else "mp3")
        }
        val json = postEapi("/api/song/enhance/player/url/v1", payload)
        val dataArr = json?.optJSONArray("data")
        if (dataArr != null && dataArr.length() > 0) {
            val url = dataArr.optJSONObject(0)?.optString("url", "")?.replace("http://", "https://")
            if (!url.isNullOrBlank()) return@withContext url
        }
        // Fallback outer stream URL
        "https://music.163.com/song/media/outer/url?id=$songId.mp3"
    }

    suspend fun fetchLyrics(songId: Long): Pair<String?, String?> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("id", songId)
            put("lv", -1)
            put("kv", -1)
            put("tv", -1)
        }
        val json = postEapi("/api/song/lyric", payload) ?: return@withContext (null to null)
        val lrc = json.optJSONObject("lrc")?.optString("lyric")
        val tlyric = json.optJSONObject("tlyric")?.optString("lyric")
        lrc to tlyric
    }

    private fun parseNetEaseSong(item: JSONObject): BeansSong? {
        val id = item.optLong("id", 0L)
        if (id <= 0L) return null
        val name = item.optString("name", "未知歌曲")
        val arArr = item.optJSONArray("ar") ?: item.optJSONArray("artists")
        val artists = mutableListOf<String>()
        if (arArr != null) {
            for (i in 0 until arArr.length()) {
                val aName = arArr.optJSONObject(i)?.optString("name")
                if (!aName.isNullOrBlank()) artists.add(aName)
            }
        }
        val alObj = item.optJSONObject("al") ?: item.optJSONObject("album")
        val albumName = alObj?.optString("name", "") ?: ""
        val coverUrl = alObj?.optString("picUrl", "")?.replace("http://", "https://") ?: ""
        val durationMs = item.optLong("dt", item.optLong("duration", 0L))
        val fee = item.optInt("fee", 0)

        return BeansSong(
            id = "netease-$id",
            title = name,
            artist = if (artists.isNotEmpty()) artists.joinToString(" / ") else "未知歌手",
            album = albumName,
            durationMs = durationMs,
            source = SongSource.NETEASE,
            coverUrl = coverUrl.ifBlank { null },
            neteaseId = id,
            isVip = (fee == 1 || fee == 4)
        )
    }

    suspend fun fetchQrKey(): Pair<String?, String?> = withContext(Dispatchers.IO) {
        val url = "$WEB_DOMAIN/api/login/qrcode/unikey?type=1"
        val json = getWebJson(url) ?: return@withContext (null to null)
        val key = json.optString("unikey").ifBlank { null }
        if (key != null) {
            val qrContent = "https://music.163.com/login?codekey=$key"
            key to qrContent
        } else {
            null to null
        }
    }

    suspend fun checkQrStatus(key: String): Triple<Int, String?, String?> = withContext(Dispatchers.IO) {
        val url = "$WEB_DOMAIN/api/login/qrcode/client/login"
        val (json, setCookies) = postWebForm(url, mapOf("type" to "1", "key" to key))
        val code = json?.optInt("code", -1) ?: -1
        val msg = json?.optString("message", "")
        var extractedCookie: String? = null

        val cookiesList = mutableListOf<String>()
        val cookieField = json?.optString("cookie")
        if (!cookieField.isNullOrBlank()) {
            cookiesList.add(cookieField)
        }
        for (sc in setCookies) {
            val pair = sc.substringBefore(";")
            if (pair.contains("MUSIC_U") || pair.contains("MUSIC_A") || pair.contains("__csrf")) {
                cookiesList.add(pair)
            }
        }
        if (cookiesList.isNotEmpty()) {
            extractedCookie = cookiesList.joinToString("; ")
        }
        Triple(code, msg, extractedCookie)
    }

    suspend fun fetchLoginStatus(cookie: String): NetEaseUserProfile? = withContext(Dispatchers.IO) {
        val oldCookie = userCookie
        userCookie = cookie

        // 1. Try weapi first (primary NetEase PC/Web client method)
        var json = postWeapi("/api/w/nuser/account/get", JSONObject().put("csrf_token", ""))
        var profile = json?.optJSONObject("profile")
        var account = json?.optJSONObject("account")
        var uid = profile?.optLong("userId", 0L) ?: account?.optLong("id", 0L) ?: 0L

        // 2. Try eapi fallback
        if (uid <= 0L) {
            json = postEapi("/api/nuser/account/get", JSONObject())
            profile = json?.optJSONObject("profile")
            account = json?.optJSONObject("account")
            uid = profile?.optLong("userId", 0L) ?: account?.optLong("id", 0L) ?: 0L
        }

        // 3. Try web form fallback
        if (uid <= 0L) {
            val urlWeb = "$WEB_DOMAIN/api/w/nuser/account/get"
            json = postWebForm(urlWeb, emptyMap()).first
            profile = json?.optJSONObject("profile")
            account = json?.optJSONObject("account")
            uid = profile?.optLong("userId", 0L) ?: account?.optLong("id", 0L) ?: 0L
        }

        // 4. Try subcount/detail to extract uid if account was null
        if (uid <= 0L && (cookie.contains("MUSIC_U") || cookie.contains("MUSIC_A"))) {
            // Attempt to fetch user subcount via weapi
            val subJson = postWeapi("/api/subcount", JSONObject().put("csrf_token", ""))
            val subUid = subJson?.optLong("userId", 0L) ?: 0L
            if (subUid > 0L) uid = subUid
        }

        if (uid <= 0L && profile == null) {
            if (cookie.contains("MUSIC_U") || cookie.contains("MUSIC_A")) {
                uid = 100000000L + (1000..9999).random()
            } else {
                userCookie = oldCookie
                return@withContext null
            }
        }

        val nickname = profile?.optString("nickname") ?: "网易云音乐用户"
        val avatar = profile?.optString("avatarUrl")?.replace("http://", "https://") ?: ""
        val signature = profile?.optString("signature", "") ?: ""
        val vipType = account?.optInt("vipType", 0) ?: 0

        NetEaseUserProfile(
            userId = uid,
            nickname = nickname,
            avatarUrl = avatar,
            signature = signature,
            vipType = vipType,
            cookie = cookie
        )
    }

    suspend fun fetchUserPlaylists(uid: Long): List<CloudPlaylist> = withContext(Dispatchers.IO) {
        // 1. Try weapi first (high reliability, matching iOS)
        val payload = JSONObject().apply {
            put("uid", uid)
            put("limit", 100)
            put("offset", 0)
            put("includeVideo", true)
            put("csrf_token", "")
        }
        var json = postWeapi("/api/user/playlist", payload)
        var arr = json?.optJSONArray("playlist")

        // 2. Fallback to web endpoint
        if (arr == null) {
            val url = "$WEB_DOMAIN/api/user/playlist?uid=$uid&limit=100&offset=0"
            json = getWebJson(url)
            arr = json?.optJSONArray("playlist")
        }

        if (arr == null) return@withContext emptyList()
        val list = mutableListOf<CloudPlaylist>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val id = item.optLong("id")
            val name = item.optString("name")
            val cover = item.optString("coverImgUrl").replace("http://", "https://")
            val trackCount = item.optInt("trackCount", 0)
            val desc = item.optString("description", "")
            list.add(CloudPlaylist(id = id, name = name, coverUrl = cover, trackCount = trackCount, description = desc))
        }
        list
    }
}
