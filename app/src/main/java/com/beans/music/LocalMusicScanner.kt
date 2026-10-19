package com.beans.music

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import java.io.File

object LocalMusicScanner {

    // Default recording/voice memo folder names or path keywords (case-insensitive)
    private val RECORDING_PATH_KEYWORDS = listOf(
        "/sounds",
        "/callrecord",
        "/recordings",
        "/sound_recorder",
        "/voice_recorder",
        "/voicerecorder",
        "/recorder",
        "/miui/sound_recorder",
        "/audiorecord",
        "/call_rec",
        "/phonerecord",
        "/wechat/arm",
        "/micromsg",
        "/qqfile_recv/voice",
        "/ringtones",
        "/alarms",
        "/notifications"
    )

    // Filename patterns typical of voice recordings & call recordings:
    // e.g. "20230523_142326", "20211027_212426_1_1", "138 0970 1485_20230608170941", "1688服务热线@400 800 1688_20240401101302", "新录音", "录音_"
    private val TIMESTAMP_RECORDING_REGEX = Regex("""^\d{8}[_-]\d{6}([_-]\d+)*$""")
    private val CALL_RECORD_REGEX = Regex(""".*[_@]\d{14}$""")
    private val PHONE_NUMBER_RECORD_REGEX = Regex("""^[\d\s+-]{5,20}_\d{8,14}.*$""")
    private val VOICE_MEMO_TITLE_REGEX = Regex("""^(新录音|录音|语音备忘录|通话录音|Voice|Record|REC)[_\s\d-]*.*$""", RegexOption.IGNORE_CASE)

    fun isLikelyRecording(
        path: String,
        title: String,
        artist: String,
        album: String,
        isRecordingFlag: Boolean,
        isRingtoneFlag: Boolean,
        isAlarmFlag: Boolean,
        isNotificationFlag: Boolean
    ): Pair<Boolean, String> {
        if (isRecordingFlag) return true to "系统标记为录音 (is_recording)"
        if (isRingtoneFlag || isAlarmFlag || isNotificationFlag) return true to "系统铃声/闹钟/通知音"

        val lowerPath = path.lowercase()
        for (kw in RECORDING_PATH_KEYWORDS) {
            if (lowerPath.contains("$kw/") || lowerPath.endsWith(kw)) {
                return true to "录音目录 ($kw)"
            }
        }

        val lowerAlbum = album.lowercase()
        if (lowerAlbum == "callrecord" || lowerAlbum == "recordings" || lowerAlbum == "sounds" || lowerAlbum == "sound_recorder") {
            return true to "录音专辑分类 ($album)"
        }

        val cleanTitle = title.trim()
        val isUnknownArtist = artist.isBlank() || artist.equals("<unknown>", ignoreCase = true) || artist.equals("未知歌手", ignoreCase = true)

        if (TIMESTAMP_RECORDING_REGEX.matches(cleanTitle)) {
            return true to "时间戳录音命名 ($cleanTitle)"
        }
        if (CALL_RECORD_REGEX.matches(cleanTitle) || PHONE_NUMBER_RECORD_REGEX.matches(cleanTitle)) {
            return true to "通话录音命名 ($cleanTitle)"
        }
        if (isUnknownArtist && VOICE_MEMO_TITLE_REGEX.matches(cleanTitle)) {
            return true to "录音标题特征 ($cleanTitle)"
        }

        return false to ""
    }

    fun scanDeviceAudio(context: Context, filter: LocalScanFilter): LocalScanResult {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val columns = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.IS_MUSIC,
            MediaStore.Audio.Media.IS_RINGTONE,
            MediaStore.Audio.Media.IS_ALARM,
            MediaStore.Audio.Media.IS_NOTIFICATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            columns.add(MediaStore.Audio.Media.IS_RECORDING)
        }

        data class RawTrack(
            val id: Long,
            val title: String,
            val artist: String,
            val album: String,
            val albumId: Long?,
            val durationMs: Long,
            val sizeBytes: Long,
            val path: String,
            val folderPath: String,
            val folderName: String,
            val isRecording: Boolean,
            val isRingtone: Boolean,
            val isAlarm: Boolean,
            val isNotification: Boolean
        )

        val rawTracks = mutableListOf<RawTrack>()

        runCatching {
            context.contentResolver.query(
                collection,
                columns.toTypedArray(),
                null,
                null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val ringtoneCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_RINGTONE)
                val alarmCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_ALARM)
                val notifCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_NOTIFICATION)
                val recCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.IS_RECORDING)
                } else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val rawTitle = cursor.getString(titleCol) ?: ""
                    val rawArtist = cursor.getString(artistCol) ?: ""
                    val rawAlbum = cursor.getString(albumCol) ?: ""
                    val rawAlbumId = if (albumIdCol >= 0) cursor.getLong(albumIdCol).takeIf { it > 0 } else null
                    val durationMs = cursor.getLong(durationCol)
                    val sizeBytes = cursor.getLong(sizeCol)
                    val path = cursor.getString(dataCol) ?: ""
                    if (path.isBlank()) continue

                    val parentFile = File(path).parentFile
                    val folderPath = parentFile?.absolutePath ?: "/storage/emulated/0"
                    val folderName = parentFile?.name?.ifBlank { "根目录" } ?: "根目录"

                    val isRec = if (recCol >= 0) cursor.getInt(recCol) != 0 else false
                    val isRing = cursor.getInt(ringtoneCol) != 0
                    val isAlarm = cursor.getInt(alarmCol) != 0
                    val isNotif = cursor.getInt(notifCol) != 0

                    rawTracks.add(
                        RawTrack(
                            id = id,
                            title = rawTitle,
                            artist = rawArtist,
                            album = rawAlbum,
                            albumId = rawAlbumId,
                            durationMs = durationMs,
                            sizeBytes = sizeBytes,
                            path = path,
                            folderPath = folderPath,
                            folderName = folderName,
                            isRecording = isRec,
                            isRingtone = isRing,
                            isAlarm = isAlarm,
                            isNotification = isNotif
                        )
                    )
                }
            }
        }

        val folderTotalMap = mutableMapOf<String, Int>()
        val folderPassedMap = mutableMapOf<String, Int>()
        val folderRecordingMap = mutableMapOf<String, Int>()
        val folderNameMap = mutableMapOf<String, String>()

        var excludedRecordingCount = 0
        var excludedByDurationOrSizeCount = 0
        var excludedByFolderCount = 0
        val excludedPreview = mutableListOf<ExcludedAudioItem>()
        val passedSongs = mutableListOf<BeansSong>()

        val minDurationMs = filter.minDurationSec * 1000L
        val maxDurationMs = if (filter.maxDurationMin > 0) filter.maxDurationMin * 60_000L else Long.MAX_VALUE
        val minSizeBytes = filter.minSizeKb * 1024L

        for (raw in rawTracks) {
            folderTotalMap[raw.folderPath] = (folderTotalMap[raw.folderPath] ?: 0) + 1
            folderNameMap[raw.folderPath] = raw.folderName

            // 1. Check recording heuristic
            val (isRec, recReason) = isLikelyRecording(
                path = raw.path,
                title = raw.title,
                artist = raw.artist,
                album = raw.album,
                isRecordingFlag = raw.isRecording,
                isRingtoneFlag = raw.isRingtone,
                isAlarmFlag = raw.isAlarm,
                isNotificationFlag = raw.isNotification
            )
            if (isRec) {
                folderRecordingMap[raw.folderPath] = (folderRecordingMap[raw.folderPath] ?: 0) + 1
            }

            if (filter.autoExcludeRecordings && isRec) {
                excludedRecordingCount++
                if (excludedPreview.size < 60) {
                    excludedPreview.add(
                        ExcludedAudioItem(
                            title = raw.title.ifBlank { File(raw.path).name },
                            path = raw.path,
                            durationMs = raw.durationMs,
                            sizeBytes = raw.sizeBytes,
                            reason = "录音剔除: $recReason"
                        )
                    )
                }
                continue
            }

            // 2. Check duration & size constraints
            if (raw.durationMs < minDurationMs) {
                excludedByDurationOrSizeCount++
                if (excludedPreview.size < 60) {
                    excludedPreview.add(
                        ExcludedAudioItem(
                            title = raw.title.ifBlank { File(raw.path).name },
                            path = raw.path,
                            durationMs = raw.durationMs,
                            sizeBytes = raw.sizeBytes,
                            reason = "时长过短 (< ${filter.minDurationSec}s)"
                        )
                    )
                }
                continue
            }
            if (raw.durationMs > maxDurationMs) {
                excludedByDurationOrSizeCount++
                if (excludedPreview.size < 60) {
                    excludedPreview.add(
                        ExcludedAudioItem(
                            title = raw.title.ifBlank { File(raw.path).name },
                            path = raw.path,
                            durationMs = raw.durationMs,
                            sizeBytes = raw.sizeBytes,
                            reason = "时长超限 (> ${filter.maxDurationMin}min)"
                        )
                    )
                }
                continue
            }
            if (raw.sizeBytes < minSizeBytes) {
                excludedByDurationOrSizeCount++
                if (excludedPreview.size < 60) {
                    excludedPreview.add(
                        ExcludedAudioItem(
                            title = raw.title.ifBlank { File(raw.path).name },
                            path = raw.path,
                            durationMs = raw.durationMs,
                            sizeBytes = raw.sizeBytes,
                            reason = "容量过小 (< ${filter.minSizeKb}KB)"
                        )
                    )
                }
                continue
            }

            // 3. Check user folder exclusion or single folder filter
            if (filter.excludedFolders.contains(raw.folderPath)) {
                excludedByFolderCount++
                continue
            }
            if (filter.selectedFolderOnly != null && raw.folderPath != filter.selectedFolderOnly) {
                continue
            }

            folderPassedMap[raw.folderPath] = (folderPassedMap[raw.folderPath] ?: 0) + 1

            // Parse cleaner title & artist if artist is "<unknown>" and filename has "Artist - Title"
            val (cleanTitle, cleanArtist) = normalizeTitleAndArtist(raw.title, raw.artist, raw.path)
            val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, raw.id)

            passedSongs.add(
                BeansSong(
                    id = "local-${raw.id}",
                    title = cleanTitle,
                    artist = cleanArtist,
                    album = raw.album.takeIf { it.isNotBlank() && !it.equals("<unknown>", true) } ?: raw.folderName,
                    durationMs = raw.durationMs,
                    sizeBytes = raw.sizeBytes,
                    source = SongSource.LOCAL,
                    localPath = raw.path,
                    contentUri = contentUri,
                    folderPath = raw.folderPath,
                    folderName = raw.folderName,
                    albumId = raw.albumId
                )
            )
        }

        val folderBuckets = folderTotalMap.keys.map { fPath ->
            val total = folderTotalMap[fPath] ?: 0
            val recCount = folderRecordingMap[fPath] ?: 0
            val passed = folderPassedMap[fPath] ?: 0
            val lower = fPath.lowercase()
            val isAutoRecFolder = RECORDING_PATH_KEYWORDS.any { lower.contains("$it/") || lower.endsWith(it) } ||
                (total > 0 && recCount == total)
            FolderBucket(
                path = fPath,
                name = folderNameMap[fPath] ?: File(fPath).name,
                totalAudioCount = total,
                passedMusicCount = passed,
                recordingFilteredCount = recCount,
                isExcludedByUser = filter.excludedFolders.contains(fPath),
                isAutoRecordingFolder = isAutoRecFolder
            )
        }.sortedWith(
            compareBy<FolderBucket> { it.isAutoRecordingFolder }
                .thenByDescending { it.passedMusicCount }
                .thenByDescending { it.totalAudioCount }
        )

        return LocalScanResult(
            songs = passedSongs.sortedBy { it.title.lowercase() },
            folders = folderBuckets,
            totalRawCount = rawTracks.size,
            excludedRecordingCount = excludedRecordingCount,
            excludedByDurationOrSizeCount = excludedByDurationOrSizeCount,
            excludedByFolderCount = excludedByFolderCount,
            excludedItemsPreview = excludedPreview
        )
    }

    private fun normalizeTitleAndArtist(rawTitle: String, rawArtist: String, path: String): Pair<String, String> {
        val fileBaseName = File(path).nameWithoutExtension
        val baseTitle = rawTitle.ifBlank { fileBaseName }
        val isUnknownArtist = rawArtist.isBlank() || rawArtist.equals("<unknown>", ignoreCase = true)

        if (isUnknownArtist) {
            // Try splitting "Artist - Title" from baseTitle or fileBaseName
            val candidate = if (baseTitle.contains(" - ")) baseTitle else fileBaseName
            val idx = candidate.indexOf(" - ")
            if (idx > 0 && idx < candidate.length - 3) {
                val parsedArtist = candidate.substring(0, idx).trim()
                val parsedTitle = candidate.substring(idx + 3).trim()
                if (parsedArtist.isNotEmpty() && parsedTitle.isNotEmpty()) {
                    return parsedTitle to parsedArtist
                }
            }
            return baseTitle to "未知歌手"
        }

        // Even if artist is known, sometimes rawTitle still prefixes "Artist - Title"
        val prefix = "$rawArtist - "
        val cleanedTitle = if (baseTitle.startsWith(prefix, ignoreCase = true)) {
            baseTitle.removePrefix(prefix).trim()
        } else {
            baseTitle
        }
        return cleanedTitle to rawArtist
    }
}
