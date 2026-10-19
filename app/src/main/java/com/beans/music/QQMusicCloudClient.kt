package com.beans.music

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object QQMusicCloudClient {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    @Volatile
    var userCookie: String = ""

    suspend fun searchSongs(query: String, limit: Int = 30): List<BeansSong> = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?w=$encoded&format=json&p=1&n=$limit"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://y.qq.com")
                .build()

            val respStr = httpClient.newCall(req).execute().use { it.body?.string() } ?: return@runCatching emptyList()
            val json = JSONObject(respStr)
            val list = json.optJSONObject("data")?.optJSONObject("song")?.optJSONArray("list") ?: return@runCatching emptyList()

            val result = mutableListOf<BeansSong>()
            for (i in 0 until list.length()) {
                val item = list.optJSONObject(i) ?: continue
                parseQQSong(item)?.let { result.add(it) }
            }
            result
        }.getOrDefault(emptyList())
    }

    suspend fun fetchTopLists(): List<CloudTopList> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://c.y.qq.com/v8/fcg-bin/fcg_myqq_toplist.fcg?format=json"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://y.qq.com/")
                .build()

            val respStr = httpClient.newCall(req).execute().use { it.body?.string() } ?: return@runCatching emptyList()
            val json = JSONObject(respStr)
            val list = json.optJSONObject("data")?.optJSONArray("topList") ?: return@runCatching emptyList()

            val result = mutableListOf<CloudTopList>()
            for (i in 0 until list.length()) {
                val item = list.optJSONObject(i) ?: continue
                val id = item.optLong("id")
                val name = item.optString("topTitle", item.optString("title"))
                if (id == 201L || id == 75L || name.contains("mv", ignoreCase = true) || name.contains("有声")) continue

                val cover = item.optString("picUrl").replace("http://", "https://")
                val songListArr = item.optJSONArray("songList")
                val previews = mutableListOf<String>()
                if (songListArr != null) {
                    for (j in 0 until minOf(songListArr.length(), 3)) {
                        val s = songListArr.optJSONObject(j) ?: continue
                        val songname = s.optString("songname")
                        val singername = s.optString("singername")
                        if (songname.isNotBlank()) {
                            previews.add("$songname - $singername")
                        }
                    }
                }
                result.add(
                    CloudTopList(
                        id = id,
                        name = name,
                        coverUrl = cover,
                        updateFrequency = "QQ 巅峰榜",
                        trackPreview = previews
                    )
                )
            }
            result
        }.getOrDefault(emptyList())
    }

    suspend fun fetchTopListTracks(topid: Long, limit: Int = 60): List<BeansSong> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://c.y.qq.com/v8/fcg-bin/fcg_v8_toplist_cp.fcg?format=json&page=detail&type=top&topid=$topid&song_begin=0&song_num=$limit"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://y.qq.com/")
                .build()

            val respStr = httpClient.newCall(req).execute().use { it.body?.string() } ?: return@runCatching emptyList()
            val json = JSONObject(respStr)
            val songlist = json.optJSONArray("songlist") ?: return@runCatching emptyList()

            val result = mutableListOf<BeansSong>()
            for (i in 0 until songlist.length()) {
                val item = songlist.optJSONObject(i) ?: continue
                val data = item.optJSONObject("data") ?: item
                parseQQSong(data)?.let { result.add(it) }
            }
            result
        }.getOrDefault(emptyList())
    }

    suspend fun fetchRecommendedPlaylists(limit: Int = 12): List<CloudPlaylist> = withContext(Dispatchers.IO) {
        runCatching {
            val ein = (limit - 1).coerceAtLeast(0)
            val url = "https://c.y.qq.com/splcloud/fcgi-bin/fcg_get_diss_by_tag.fcg?picmid=1&rnd=0.123&g_tk=5381&loginUin=0&hostUin=0&format=json&inCharset=utf8&outCharset=utf-8&notice=0&platform=yqq.json&needNewCode=0&categoryId=10000000&sortId=5&sin=0&ein=$ein"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://y.qq.com/")
                .build()

            val respStr = httpClient.newCall(req).execute().use { it.body?.string() } ?: return@runCatching emptyList()
            val json = JSONObject(respStr)
            val list = json.optJSONObject("data")?.optJSONArray("list") ?: return@runCatching emptyList()

            val result = mutableListOf<CloudPlaylist>()
            for (i in 0 until list.length()) {
                val item = list.optJSONObject(i) ?: continue
                val dissid = item.optLong("dissid")
                val dissname = item.optString("dissname")
                val imgurl = item.optString("imgurl").replace("http://", "https://")
                val songnum = item.optInt("songnum", 0)
                val creator = item.optJSONObject("creator")?.optString("name", "") ?: ""

                result.add(
                    CloudPlaylist(
                        id = dissid,
                        name = dissname,
                        coverUrl = imgurl,
                        trackCount = songnum,
                        description = if (creator.isNotBlank()) "创建者: $creator" else "QQ音乐精选歌单"
                    )
                )
            }
            result
        }.getOrDefault(emptyList())
    }

    suspend fun fetchPlaylistTracks(dissid: Long, limit: Int = 80): List<BeansSong> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://c.y.qq.com/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg?type=1&json=1&utf8=1&onlysong=0&new_format=1&disstid=$dissid&loginUin=0&hostUin=0&format=json&inCharset=utf8&outCharset=utf-8&notice=0&platform=yqq.json&needNewCode=0"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://y.qq.com/")
                .build()

            val respStr = httpClient.newCall(req).execute().use { it.body?.string() } ?: return@runCatching emptyList()
            val json = JSONObject(respStr)
            val cdlist = json.optJSONArray("cdlist") ?: return@runCatching emptyList()
            if (cdlist.length() == 0) return@runCatching emptyList()

            val songlist = cdlist.optJSONObject(0)?.optJSONArray("songlist") ?: return@runCatching emptyList()
            val result = mutableListOf<BeansSong>()
            val maxCount = minOf(songlist.length(), limit)
            for (i in 0 until maxCount) {
                val item = songlist.optJSONObject(i) ?: continue
                parseQQSong(item)?.let { result.add(it) }
            }
            result
        }.getOrDefault(emptyList())
    }

    suspend fun fetchSongPlayUrl(songmid: String): String? = withContext(Dispatchers.IO) {
        // QQ music stream CDN URL
        "https://ws.stream.qqmusic.qq.com/C400$songmid.m4a?guid=12345678&vkey=&fromtag=66"
    }

    // --- QQ QR Login Protocol (Tencent ptlogin2) ---

    private fun hash33(s: String): Int {
        var h = 0
        for (ch in s) {
            h += (h shl 5) + ch.code
        }
        return 2147483647 and h
    }

    suspend fun fetchQrImage(): Pair<Bitmap?, String?> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://ssl.ptlogin2.qq.com/ptqrshow?appid=716027609&e=2&l=M&s=3&d=72&v=4&t=${Math.random()}&daid=383&pt_3rd_aid=100497308"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://xui.ptlogin2.qq.com/")
                .build()

            val resp = httpClient.newCall(req).execute()
            val bytes = resp.body?.bytes()
            var qrsig: String? = null
            for (sc in resp.headers("Set-Cookie")) {
                if (sc.contains("qrsig=")) {
                    qrsig = sc.substringAfter("qrsig=").substringBefore(";")
                    break
                }
            }
            val bmp = if (bytes != null && bytes.isNotEmpty()) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } else null
            bmp to qrsig
        }.getOrDefault(null to null)
    }

    suspend fun checkQrStatus(qrsig: String): Triple<Int, String?, QQUserProfile?> = withContext(Dispatchers.IO) {
        runCatching {
            val ptqrtoken = hash33(qrsig)
            val url = "https://ssl.ptlogin2.qq.com/ptqrlogin?u1=https%3A%2F%2Fgraph.qq.com%2Foauth2.0%2Flogin_jump&ptqrtoken=$ptqrtoken&ptredirect=0&h=1&t=1&g=1&from_ui=1&ptlang=2052&action=0-0-${System.currentTimeMillis()}&js_ver=22080914&js_type=1&login_sig=&pt_uistyle=40&aid=716027609&daid=383&pt_3rd_aid=100497308&o1vId=49283d5cbb01a744d46314da4608d929"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://xui.ptlogin2.qq.com/")
                .header("Cookie", "qrsig=$qrsig")
                .build()

            val resp = httpClient.newCall(req).execute()
            val text = resp.body?.string() ?: ""

            // ptuiCB('0','0','https://ssl.ptlogin2.graph.qq.com/check_sig?pttype=1&uin=123456...','0','登录成功！','昵称');
            val parts = Regex("ptuiCB\\('([^']*)','([^']*)','([^']*)','([^']*)','([^']*)','([^']*)'\\)")
                .find(text)?.groupValues

            if (parts == null) {
                return@runCatching Triple(-1, "返回解析异常", null)
            }

            val codeStr = parts[1]
            val jumpUrl = parts[3]
            val msg = parts[5]
            val nick = parts[6].ifBlank { "QQ音乐用户" }

            when (codeStr) {
                "0" -> {
                    // Extract uin from jumpUrl or query
                    val uin = Regex("uin=([0-9]+)").find(jumpUrl)?.groupValues?.get(1) ?: "10001"
                    val avatar = "https://q1.qlogo.cn/g?b=qq&nk=$uin&s=100"
                    val profile = QQUserProfile(
                        uin = uin,
                        nickname = nick,
                        avatarUrl = avatar,
                        cookie = "uin=o$uin; qrsig=$qrsig"
                    )
                    Triple(0, msg, profile)
                }
                "67" -> Triple(67, "正在确认授权...", null)
                "65", "68" -> Triple(65, "二维码已过期，请刷新", null)
                else -> Triple(66, "等待扫码中", null)
            }
        }.getOrDefault(Triple(-1, "网络异常", null))
    }

    suspend fun fetchUserPlaylists(uin: String): List<CloudPlaylist> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://c.y.qq.com/rsc/fcgi-bin/fcg_user_created_diss?cv=4747474&ct=24&format=json&inCharset=utf-8&outCharset=utf-8&notice=0&platform=yqq.json&needNewCode=1&uin=$uin&hostuin=$uin&sin=0&size=50"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://y.qq.com/")
                .build()

            val respStr = httpClient.newCall(req).execute().use { it.body?.string() } ?: return@runCatching emptyList()
            val json = JSONObject(respStr)
            val list = json.optJSONObject("data")?.optJSONArray("disslist") ?: return@runCatching emptyList()

            val result = mutableListOf<CloudPlaylist>()
            for (i in 0 until list.length()) {
                val item = list.optJSONObject(i) ?: continue
                val dissid = item.optLong("diss_id")
                val dissname = item.optString("diss_name")
                val dissCover = item.optString("diss_cover").replace("http://", "https://")
                val songCnt = item.optInt("song_cnt", 0)

                result.add(
                    CloudPlaylist(
                        id = dissid,
                        name = dissname,
                        coverUrl = dissCover,
                        trackCount = songCnt,
                        description = "QQ 个人歌单"
                    )
                )
            }
            if (result.isNotEmpty()) result else fetchRecommendedPlaylists(8)
        }.getOrDefault(emptyList())
    }

    private fun parseQQSong(item: JSONObject): BeansSong? {
        val songmid = item.optString("songmid", item.optString("mid", ""))
        if (songmid.isBlank()) return null
        val songname = item.optString("songname", item.optString("title", "未知歌曲"))
        val albumname = item.optString("albumname", item.optJSONObject("album")?.optString("name", ""))
        val albummid = item.optString("albummid", item.optJSONObject("album")?.optString("mid", ""))
        val coverUrl = if (albummid.isNotBlank()) {
            "https://y.gtimg.cn/music/photo_new/T002R300x300M000$albummid.jpg"
        } else null

        val singers = mutableListOf<String>()
        val singerArr = item.optJSONArray("singer")
        if (singerArr != null) {
            for (j in 0 until singerArr.length()) {
                val name = singerArr.optJSONObject(j)?.optString("name")
                if (!name.isNullOrBlank()) singers.add(name)
            }
        }
        val interval = item.optLong("interval", 0L) * 1000L

        return BeansSong(
            id = "qq-$songmid",
            title = songname,
            artist = if (singers.isNotEmpty()) singers.joinToString(" / ") else "未知歌手",
            album = albumname,
            durationMs = interval,
            source = SongSource.QQ,
            coverUrl = coverUrl,
            qqMid = songmid
        )
    }
}
