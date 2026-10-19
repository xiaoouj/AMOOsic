package com.beans.music

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

data class DynamicCoverColors(
    val primary: Color = Color(0xFFF2A33C),
    val secondary: Color = Color(0xFF3E6B89),
    val tertiary: Color = Color(0xFF1F2438),
    val darkSurface: Color = Color(0xFF0E1017)
)

object LocalMediaHelper {

    private val bitmapCache = ConcurrentHashMap<String, Bitmap>()
    private val paletteCache = ConcurrentHashMap<String, DynamicCoverColors>()
    private val lyricsCache = ConcurrentHashMap<String, List<LyricLine>>()

    suspend fun loadCoverBitmap(context: Context, song: BeansSong): Bitmap? = withContext(Dispatchers.IO) {
        bitmapCache[song.id]?.let { return@withContext it }

        var bmp: Bitmap? = null

        // 1. Android ContentResolver Thumbnail (Android 10+ standard contentUri thumbnail)
        if (bmp == null && song.contentUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                bmp = context.contentResolver.loadThumbnail(song.contentUri, Size(640, 640), null)
            }
        }

        // 2. MediaStore album art URI (content://media/external/audio/albumart/<albumId>)
        if (bmp == null && song.albumId != null && song.albumId > 0) {
            runCatching {
                val albumArtUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    song.albumId
                )
                context.contentResolver.openInputStream(albumArtUri)?.use { input ->
                    bmp = BitmapFactory.decodeStream(input)
                }
            }
        }

        // 3. MediaMetadataRetriever via openFileDescriptor on song.contentUri
        if (bmp == null && song.contentUri != null) {
            val retriever = MediaMetadataRetriever()
            try {
                context.contentResolver.openFileDescriptor(song.contentUri, "r")?.use { pfd ->
                    retriever.setDataSource(pfd.fileDescriptor)
                    val embedded = retriever.embeddedPicture
                    if (embedded != null && embedded.isNotEmpty()) {
                        bmp = BitmapFactory.decodeByteArray(embedded, 0, embedded.size)
                    }
                }
            } catch (_: Exception) {
            } finally {
                runCatching { retriever.release() }
            }
        }

        // 4. MediaMetadataRetriever via local file path if accessible
        if (bmp == null && !song.localPath.isNullOrBlank()) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(song.localPath)
                val embedded = retriever.embeddedPicture
                if (embedded != null && embedded.isNotEmpty()) {
                    bmp = BitmapFactory.decodeByteArray(embedded, 0, embedded.size)
                }
            } catch (_: Exception) {
            } finally {
                runCatching { retriever.release() }
            }
        }

        // 5. Binary Stream parser fallback for ID3 APIC & FLAC PICTURE blocks
        if (bmp == null && song.contentUri != null) {
            runCatching {
                context.contentResolver.openInputStream(song.contentUri)?.use { input ->
                    bmp = extractPictureFromStream(input)
                }
            }
        }
        if (bmp == null && !song.localPath.isNullOrBlank()) {
            runCatching {
                val file = File(song.localPath)
                if (file.exists() && file.canRead()) {
                    file.inputStream().use { input ->
                        bmp = extractPictureFromStream(input)
                    }
                }
            }
        }

        // 6. Sidecar folder artwork files (cover.jpg, folder.jpg, etc.)
        if (bmp == null && !song.localPath.isNullOrBlank()) {
            runCatching {
                val parent = File(song.localPath).parentFile
                if (parent != null && parent.exists()) {
                    val candidates = listOf("cover.jpg", "cover.png", "cover.jpeg", "folder.jpg", "front.jpg")
                    for (name in candidates) {
                        val f = File(parent, name)
                        if (f.exists() && f.length() > 0) {
                            bmp = BitmapFactory.decodeFile(f.absolutePath)
                            if (bmp != null) break
                        }
                    }
                }
            }
        }

        // 7. Check remote coverUrl if present
        if (bmp == null && !song.coverUrl.isNullOrBlank()) {
            runCatching {
                val conn = URL(song.coverUrl).openConnection()
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                conn.getInputStream().use { input ->
                    bmp = BitmapFactory.decodeStream(input)
                }
            }
        }

        if (bmp != null) {
            val scaled = if (bmp!!.width > 600 || bmp!!.height > 600) {
                Bitmap.createScaledBitmap(bmp!!, 512, 512, true)
            } else {
                bmp!!
            }
            bitmapCache[song.id] = scaled
            return@withContext scaled
        }
        null
    }

    /**
     * Binary parser extracting embedded artwork directly from audio streams
     * Supports ID3v2 APIC frame (MP3) and FLAC METADATA_BLOCK_PICTURE (FLAC)
     */
    private fun extractPictureFromStream(input: InputStream): Bitmap? {
        val buffer = ByteArray(2 * 1024 * 1024)
        var totalRead = 0
        while (totalRead < buffer.size) {
            val r = input.read(buffer, totalRead, buffer.size - totalRead)
            if (r <= 0) break
            totalRead += r
        }
        if (totalRead < 32) return null

        // Check FLAC: starts with "fLaC"
        if (buffer[0] == 'f'.code.toByte() && buffer[1] == 'L'.code.toByte() &&
            buffer[2] == 'a'.code.toByte() && buffer[3] == 'C'.code.toByte()
        ) {
            var offset = 4
            while (offset + 4 <= totalRead) {
                val hdr0 = buffer[offset].toInt() and 0xFF
                val isLast = (hdr0 and 0x80) != 0
                val blockType = hdr0 and 0x7F
                val blockLen = ((buffer[offset + 1].toInt() and 0xFF) shl 16) or
                    ((buffer[offset + 2].toInt() and 0xFF) shl 8) or
                    (buffer[offset + 3].toInt() and 0xFF)
                offset += 4
                if (blockType == 6 && offset + blockLen <= totalRead) { // PICTURE
                    var picOffset = offset
                    // skip type (4)
                    picOffset += 4
                    val mimeLen = readInt32(buffer, picOffset)
                    picOffset += 4 + mimeLen
                    val descLen = readInt32(buffer, picOffset)
                    picOffset += 4 + descLen + 16 // skip desc + width, height, depth, colors (4*4)
                    val dataLen = readInt32(buffer, picOffset)
                    picOffset += 4
                    if (picOffset + dataLen <= totalRead) {
                        return BitmapFactory.decodeByteArray(buffer, picOffset, dataLen)
                    }
                }
                offset += blockLen
                if (isLast) break
            }
        }

        // Check ID3v2: starts with "ID3"
        if (buffer[0] == 'I'.code.toByte() && buffer[1] == 'D'.code.toByte() && buffer[2] == '3'.code.toByte()) {
            // Find "APIC" frame marker
            val apicBytes = "APIC".toByteArray()
            val maxSearch = (totalRead - 20).coerceAtLeast(0)
            for (i in 10 until maxSearch) {
                if (buffer[i] == apicBytes[0] && buffer[i + 1] == apicBytes[1] &&
                    buffer[i + 2] == apicBytes[2] && buffer[i + 3] == apicBytes[3]
                ) {
                    val frameSize = readInt32(buffer, i + 4)
                    if (frameSize > 0 && i + 10 + frameSize <= totalRead) {
                        // Scan for image headers: JPEG (0xFF, 0xD8, 0xFF) or PNG (0x89, 0x50, 0x4E, 0x47)
                        val bodyStart = i + 10
                        val bodyEnd = (bodyStart + frameSize).coerceAtMost(totalRead)
                        for (j in bodyStart until bodyEnd - 4) {
                            if ((buffer[j] == 0xFF.toByte() && buffer[j + 1] == 0xD8.toByte() && buffer[j + 2] == 0xFF.toByte()) ||
                                (buffer[j] == 0x89.toByte() && buffer[j + 1] == 0x50.toByte() && buffer[j + 2] == 0x4E.toByte() && buffer[j + 3] == 0x47.toByte())
                            ) {
                                val imgLen = bodyEnd - j
                                return BitmapFactory.decodeByteArray(buffer, j, imgLen)
                            }
                        }
                    }
                }
            }
        }

        // Generic signature scan for embedded JPEG/PNG in the entire read buffer
        for (i in 0 until totalRead - 4) {
            if (buffer[i] == 0x89.toByte() && buffer[i + 1] == 0x50.toByte() && buffer[i + 2] == 0x4E.toByte() && buffer[i + 3] == 0x47.toByte()) {
                val bmp = BitmapFactory.decodeByteArray(buffer, i, totalRead - i)
                if (bmp != null) return bmp
            }
            if (buffer[i] == 0xFF.toByte() && buffer[i + 1] == 0xD8.toByte() && buffer[i + 2] == 0xFF.toByte()) {
                val bmp = BitmapFactory.decodeByteArray(buffer, i, totalRead - i)
                if (bmp != null) return bmp
            }
        }

        return null
    }

    private fun readInt32(buf: ByteArray, offset: Int): Int {
        if (offset + 4 > buf.size) return 0
        return ((buf[offset].toInt() and 0xFF) shl 24) or
            ((buf[offset + 1].toInt() and 0xFF) shl 16) or
            ((buf[offset + 2].toInt() and 0xFF) shl 8) or
            (buf[offset + 3].toInt() and 0xFF)
    }

    suspend fun extractColors(context: Context, song: BeansSong): DynamicCoverColors = withContext(Dispatchers.Default) {
        paletteCache[song.id]?.let { return@withContext it }

        val bmp = loadCoverBitmap(context, song)
        if (bmp != null) {
            val palette = Palette.from(bmp).maximumColorCount(24).generate()
            val vibrant = palette.getVibrantColor(0xFFF2A33C.toInt())
            val muted = palette.getMutedColor(0xFF3E6B89.toInt())
            val darkVibrant = palette.getDarkVibrantColor(0xFF1F2438.toInt())
            val darkMuted = palette.getDarkMutedColor(0xFF0E1017.toInt())

            val colors = DynamicCoverColors(
                primary = Color(vibrant),
                secondary = Color(muted),
                tertiary = Color(darkVibrant),
                darkSurface = Color(darkMuted)
            )
            paletteCache[song.id] = colors
            return@withContext colors
        }

        // Deterministic rich palette based on song title hash when no cover image is embedded
        val palettes = listOf(
            DynamicCoverColors(Color(0xFFF2A33C), Color(0xFF9C4146), Color(0xFF28192E), Color(0xFF0E0B14)),
            DynamicCoverColors(Color(0xFF42C79A), Color(0xFF1E6E78), Color(0xFF122B38), Color(0xFF08131A)),
            DynamicCoverColors(Color(0xFF63B4F5), Color(0xFF4353A3), Color(0xFF171E40), Color(0xFF0A0D1C)),
            DynamicCoverColors(Color(0xFFF582AE), Color(0xFF7B4288), Color(0xFF2B1638), Color(0xFF120A18)),
            DynamicCoverColors(Color(0xFFB39EF2), Color(0xFF4E4396), Color(0xFF1E193D), Color(0xFF0C0A1A))
        )
        val picked = palettes[(song.title.hashCode() and Int.MAX_VALUE) % palettes.size]
        paletteCache[song.id] = picked
        picked
    }

    suspend fun loadLyricsForSong(context: Context, song: BeansSong): List<LyricLine> = withContext(Dispatchers.IO) {
        lyricsCache[song.id]?.let { return@withContext it }

        // 1. Check sidecar lyric files (.lrc, .ttml, .vtt) in the same folder
        if (!song.localPath.isNullOrBlank()) {
            val audioFile = File(song.localPath)
            val parent = audioFile.parentFile
            val baseName = audioFile.nameWithoutExtension
            if (parent != null && parent.exists()) {
                val lrcCandidates = listOf(
                    File(parent, "$baseName.lrc"),
                    File(parent, "$baseName.ttml"),
                    File(parent, "${song.title}.lrc"),
                    File(parent, "${song.artist} - ${song.title}.lrc")
                )
                for (f in lrcCandidates) {
                    if (f.exists() && f.length() > 0) {
                        runCatching {
                            val lines = parseLrc(f.readText(Charsets.UTF_8))
                            if (lines.isNotEmpty()) {
                                lyricsCache[song.id] = lines
                                return@withContext lines
                            }
                        }
                    }
                }
            }
        }

        // 2. Fallback to Cloud lyrics search by title + artist
        runCatching {
            val searchResults = NetEaseCloudClient.searchSongs("${song.title} ${song.artist}", limit = 1)
            val neteaseSong = searchResults.firstOrNull()
            if (neteaseSong?.neteaseId != null) {
                val (lrc, tlyric) = NetEaseCloudClient.fetchLyrics(neteaseSong.neteaseId)
                if (!lrc.isNullOrBlank()) {
                    val parsed = parseLrc(lrc)
                    if (parsed.isNotEmpty()) {
                        lyricsCache[song.id] = parsed
                        return@withContext parsed
                    }
                }
            }
        }

        emptyList()
    }

    fun parseLrc(lrcText: String): List<LyricLine> {
        val result = mutableListOf<LyricLine>()
        val timeRegex = Regex("""\[(\d{1,2}):(\d{1,2})(?:[\.:](\d{1,3}))?]""")
        val translationMap = mutableMapOf<Long, String>()
        val rawLines = lrcText.lines()

        for (line in rawLines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[ti:") || trimmed.startsWith("[ar:") ||
                trimmed.startsWith("[al:") || trimmed.startsWith("[by:") ||
                trimmed.startsWith("[offset:")
            ) continue

            val match = timeRegex.find(trimmed) ?: continue
            val min = match.groupValues[1].toLongOrNull() ?: 0L
            val sec = match.groupValues[2].toLongOrNull() ?: 0L
            val msStr = match.groupValues[3]
            val ms = when (msStr.length) {
                1 -> msStr.toLong() * 100
                2 -> msStr.toLong() * 10
                3 -> msStr.toLong()
                else -> 0L
            }
            val totalMs = min * 60_000L + sec * 1000L + ms
            val text = trimmed.substring(match.range.last + 1).trim()

            if (text.startsWith("(") && text.endsWith(")") || text.startsWith("（") && text.endsWith("）")) {
                translationMap[totalMs] = text.removeSurrounding("(", ")").removeSurrounding("（", "）")
            } else if (text.isNotBlank()) {
                result.add(
                    LyricLine(
                        timeMs = totalMs,
                        text = text
                    )
                )
            }
        }

        result.sortBy { it.timeMs }
        return result.map { item ->
            val tr = translationMap[item.timeMs]
            if (tr != null) item.copy(translation = tr) else item
        }
    }
}
