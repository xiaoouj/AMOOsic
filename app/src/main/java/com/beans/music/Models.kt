package com.beans.music

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

enum class SongSource(val label: String) {
    LOCAL("本机音乐"),
    NETEASE("网易云"),
    QQ("QQ音乐")
}

data class BeansSong(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val sizeBytes: Long = 0L,
    val source: SongSource = SongSource.LOCAL,
    val localPath: String? = null,
    val contentUri: Uri? = null,
    val folderPath: String? = null,
    val folderName: String? = null,
    val coverUrl: String? = null,
    val albumId: Long? = null,
    val neteaseId: Long? = null,
    val qqMid: String? = null,
    val isVip: Boolean = false
) {
    val formattedDuration: String
        get() {
            val totalSec = (durationMs / 1000L).coerceAtLeast(0L)
            val min = totalSec / 60L
            val sec = totalSec % 60L
            return "%d:%02d".format(min, sec)
        }

    val formattedSize: String
        get() {
            if (sizeBytes <= 0L) return ""
            val mb = sizeBytes.toDouble() / (1024.0 * 1024.0)
            return if (mb >= 1.0) "%.1f MB".format(mb) else "%d KB".format((sizeBytes / 1024L).coerceAtLeast(1L))
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("artist", artist)
        put("album", album)
        put("durationMs", durationMs)
        put("sizeBytes", sizeBytes)
        put("source", source.name)
        put("localPath", localPath ?: "")
        put("contentUri", contentUri?.toString() ?: "")
        put("folderPath", folderPath ?: "")
        put("folderName", folderName ?: "")
        put("coverUrl", coverUrl ?: "")
        if (neteaseId != null) put("neteaseId", neteaseId)
        if (qqMid != null) put("qqMid", qqMid)
        put("isVip", isVip)
    }

    companion object {
        fun fromJson(obj: JSONObject): BeansSong {
            val uriStr = obj.optString("contentUri", "")
            val pathStr = obj.optString("localPath", "")
            val folderStr = obj.optString("folderPath", "")
            val folderNameStr = obj.optString("folderName", "")
            val coverStr = obj.optString("coverUrl", "")
            val srcName = obj.optString("source", SongSource.LOCAL.name)
            val src = runCatching { SongSource.valueOf(srcName) }.getOrDefault(SongSource.LOCAL)
            return BeansSong(
                id = obj.optString("id"),
                title = obj.optString("title", "未知歌曲"),
                artist = obj.optString("artist", "未知歌手"),
                album = obj.optString("album", ""),
                durationMs = obj.optLong("durationMs", 0L),
                sizeBytes = obj.optLong("sizeBytes", 0L),
                source = src,
                localPath = pathStr.ifEmpty { null },
                contentUri = if (uriStr.isNotEmpty()) Uri.parse(uriStr) else null,
                folderPath = folderStr.ifEmpty { null },
                folderName = folderNameStr.ifEmpty { null },
                coverUrl = coverStr.ifEmpty { null },
                neteaseId = if (obj.has("neteaseId")) obj.optLong("neteaseId") else null,
                qqMid = obj.optString("qqMid", "").ifEmpty { null },
                isVip = obj.optBoolean("isVip", false)
            )
        }
    }
}

data class LocalScanFilter(
    val minDurationSec: Int = 60,
    val maxDurationMin: Int = 20, // 0 means unlimited
    val minSizeKb: Int = 500,
    val autoExcludeRecordings: Boolean = true,
    val excludedFolders: Set<String> = emptySet(),
    val selectedFolderOnly: String? = null // null means all enabled folders
)

data class FolderBucket(
    val path: String,
    val name: String,
    val totalAudioCount: Int,
    val passedMusicCount: Int,
    val recordingFilteredCount: Int,
    val isExcludedByUser: Boolean,
    val isAutoRecordingFolder: Boolean
)

data class LocalScanResult(
    val songs: List<BeansSong>,
    val folders: List<FolderBucket>,
    val totalRawCount: Int,
    val excludedRecordingCount: Int,
    val excludedByDurationOrSizeCount: Int,
    val excludedByFolderCount: Int,
    val excludedItemsPreview: List<ExcludedAudioItem>
)

data class ExcludedAudioItem(
    val title: String,
    val path: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val reason: String
)

data class LyricWord(
    val text: String,
    val startMs: Long,
    val durationMs: Long
)

data class LyricLine(
    val timeMs: Long,
    val text: String,
    val translation: String? = null,
    val words: List<LyricWord> = emptyList()
)

data class CloudTopList(
    val id: Long,
    val name: String,
    val coverUrl: String,
    val updateFrequency: String,
    val trackPreview: List<String> = emptyList()
)

data class CloudPlaylist(
    val id: Long,
    val name: String,
    val coverUrl: String,
    val trackCount: Int,
    val description: String = ""
)

data class LocalCustomPlaylist(
    val id: String,
    val name: String,
    val songs: List<BeansSong>
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        val arr = JSONArray()
        songs.forEach { arr.put(it.toJson()) }
        put("songs", arr)
    }

    companion object {
        fun fromJson(obj: JSONObject): LocalCustomPlaylist {
            val arr = obj.optJSONArray("songs") ?: JSONArray()
            val list = mutableListOf<BeansSong>()
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { list.add(BeansSong.fromJson(it)) }
            }
            return LocalCustomPlaylist(
                id = obj.optString("id"),
                name = obj.optString("name", "未命名歌单"),
                songs = list
            )
        }
    }
}

enum class RepeatMode {
    SEQUENCE,
    ONE,
    SHUFFLE
}
