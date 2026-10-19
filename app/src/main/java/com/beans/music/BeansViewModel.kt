package com.beans.music

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class BeansViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("beans_music_prefs", Context.MODE_PRIVATE)

    // Local Scan & Filter State
    private val _scanFilter = MutableStateFlow(loadSavedFilter())
    val scanFilter: StateFlow<LocalScanFilter> = _scanFilter.asStateFlow()

    private val _scanResult = MutableStateFlow(
        LocalScanResult(
            songs = emptyList(),
            folders = emptyList(),
            totalRawCount = 0,
            excludedRecordingCount = 0,
            excludedByDurationOrSizeCount = 0,
            excludedByFolderCount = 0,
            excludedItemsPreview = emptyList()
        )
    )
    val scanResult: StateFlow<LocalScanResult> = _scanResult.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _localSearchQuery = MutableStateFlow("")
    val localSearchQuery: StateFlow<String> = _localSearchQuery.asStateFlow()

    // Cloud Discover & Search State
    private val _cloudPlatform = MutableStateFlow(SongSource.NETEASE)
    val cloudPlatform: StateFlow<SongSource> = _cloudPlatform.asStateFlow()

    private val _topLists = MutableStateFlow<List<CloudTopList>>(emptyList())
    val topLists: StateFlow<List<CloudTopList>> = _topLists.asStateFlow()

    private val _recommendedPlaylists = MutableStateFlow<List<CloudPlaylist>>(emptyList())
    val recommendedPlaylists: StateFlow<List<CloudPlaylist>> = _recommendedPlaylists.asStateFlow()

    private val _isDiscoverLoading = MutableStateFlow(false)
    val isDiscoverLoading: StateFlow<Boolean> = _isDiscoverLoading.asStateFlow()

    private val _cloudSearchResults = MutableStateFlow<List<BeansSong>>(emptyList())
    val cloudSearchResults: StateFlow<List<BeansSong>> = _cloudSearchResults.asStateFlow()

    private val _isSearchingCloud = MutableStateFlow(false)
    val isSearchingCloud: StateFlow<Boolean> = _isSearchingCloud.asStateFlow()

    private val _selectedCloudPlaylistSongs = MutableStateFlow<List<BeansSong>>(emptyList())
    val selectedCloudPlaylistSongs: StateFlow<List<BeansSong>> = _selectedCloudPlaylistSongs.asStateFlow()

    private val _isLoadingPlaylistDetail = MutableStateFlow(false)
    val isLoadingPlaylistDetail: StateFlow<Boolean> = _isLoadingPlaylistDetail.asStateFlow()

    // NetEase User & QR Login State
    private val _netEaseUser = MutableStateFlow<NetEaseUserProfile?>(loadSavedNetEaseUser())
    val netEaseUser: StateFlow<NetEaseUserProfile?> = _netEaseUser.asStateFlow()

    private val _netEaseUserPlaylists = MutableStateFlow<List<CloudPlaylist>>(emptyList())
    val netEaseUserPlaylists: StateFlow<List<CloudPlaylist>> = _netEaseUserPlaylists.asStateFlow()

    private val _qrLoginState = MutableStateFlow<QrLoginState>(QrLoginState.Idle)
    val qrLoginState: StateFlow<QrLoginState> = _qrLoginState.asStateFlow()

    private var qrCheckJob: Job? = null

    // QQ Music User & QR Login State
    private val _qqUser = MutableStateFlow<QQUserProfile?>(loadSavedQQUser())
    val qqUser: StateFlow<QQUserProfile?> = _qqUser.asStateFlow()

    private val _qqUserPlaylists = MutableStateFlow<List<CloudPlaylist>>(emptyList())
    val qqUserPlaylists: StateFlow<List<CloudPlaylist>> = _qqUserPlaylists.asStateFlow()

    private val _qqLoginState = MutableStateFlow<QQLoginState>(QQLoginState.Idle)
    val qqLoginState: StateFlow<QQLoginState> = _qqLoginState.asStateFlow()

    private var qqQrCheckJob: Job? = null

    // Favorites & Local Custom Playlists & History
    private val _favoriteIds = MutableStateFlow<Set<String>>(loadStringSet("favorite_ids"))
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    private val _customPlaylists = MutableStateFlow<List<LocalCustomPlaylist>>(loadCustomPlaylists())
    val customPlaylists: StateFlow<List<LocalCustomPlaylist>> = _customPlaylists.asStateFlow()

    private val _recentSongs = MutableStateFlow<List<BeansSong>>(loadRecentSongs())
    val recentSongs: StateFlow<List<BeansSong>> = _recentSongs.asStateFlow()

    // Player & Queue State
    private val _queue = MutableStateFlow<List<BeansSong>>(emptyList())
    val queue: StateFlow<List<BeansSong>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _currentSong = MutableStateFlow<BeansSong?>(null)
    val currentSong: StateFlow<BeansSong?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _progressMs = MutableStateFlow(0L)
    val progressMs: StateFlow<Long> = _progressMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.SEQUENCE)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    // Visual & Lyrics State for Current Song
    private val _currentCoverBitmap = MutableStateFlow<Bitmap?>(null)
    val currentCoverBitmap: StateFlow<Bitmap?> = _currentCoverBitmap.asStateFlow()

    private val _currentColors = MutableStateFlow(DynamicCoverColors())
    val currentColors: StateFlow<DynamicCoverColors> = _currentColors.asStateFlow()

    private val _currentLyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val currentLyrics: StateFlow<List<LyricLine>> = _currentLyrics.asStateFlow()

    // Theme & Appearance Settings (Beans style)
    private val _accentThemeIndex = MutableStateFlow(prefs.getInt("accent_index", 0))
    val accentThemeIndex: StateFlow<Int> = _accentThemeIndex.asStateFlow()

    // STEPX Neo BackScreen Settings
    private val _backScreenEnabled = MutableStateFlow(prefs.getBoolean("backscreen_enabled", true))
    val backScreenEnabled: StateFlow<Boolean> = _backScreenEnabled.asStateFlow()

    private val _backScreenMode = MutableStateFlow(
        runCatching {
            BackScreenVisualMode.valueOf(prefs.getString("backscreen_mode", BackScreenVisualMode.RIPPLE.name) ?: "RIPPLE")
        }.getOrDefault(BackScreenVisualMode.RIPPLE)
    )
    val backScreenMode: StateFlow<BackScreenVisualMode> = _backScreenMode.asStateFlow()

    private val _backScreenBrightness = MutableStateFlow(prefs.getFloat("backscreen_brightness", 0.9f))
    val backScreenBrightness: StateFlow<Float> = _backScreenBrightness.asStateFlow()

    private val _backScreenVolumeOverlay = MutableStateFlow(prefs.getBoolean("backscreen_volume_overlay", true))
    val backScreenVolumeOverlay: StateFlow<Boolean> = _backScreenVolumeOverlay.asStateFlow()

    val isBackScreenHardwareAvailable: Boolean = StepXBackScreenManager.getInstance(application).isAvailable

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private var mediaController: MediaController? = null
    private var progressJob: Job? = null

    init {
        initMediaController()
        loadCloudDiscover()
        if (_netEaseUser.value != null) {
            refreshNetEaseUserPlaylists()
        }
        if (_qqUser.value != null) {
            QQMusicCloudClient.userCookie = _qqUser.value!!.cookie
            refreshQQUserPlaylists()
        }
    }

    private fun initMediaController() {
        val ctx = getApplication<Application>()
        val sessionToken = SessionToken(ctx, ComponentName(ctx, BeansPlaybackService::class.java))
        val controllerFuture = MediaController.Builder(ctx, sessionToken).buildAsync()
        controllerFuture.addListener({
            runCatching {
                val controller = controllerFuture.get()
                mediaController = controller
                controller.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            handleTrackEnded()
                        } else if (playbackState == Player.STATE_READY) {
                            val dur = controller.duration
                            if (dur > 0) _durationMs.value = dur
                        }
                    }
                })
                startProgressTicker()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                val controller = mediaController
                if (controller != null && controller.isPlaying) {
                    _progressMs.value = controller.currentPosition.coerceAtLeast(0L)
                    val dur = controller.duration
                    if (dur > 0) _durationMs.value = dur
                }
                delay(45L) // ~22fps smooth karaoke word & progress updates
            }
        }
    }

    fun showToast(msg: String) {
        viewModelScope.launch {
            _toastMessage.value = msg
            delay(2300L)
            if (_toastMessage.value == msg) {
                _toastMessage.value = null
            }
        }
    }

    // --- Local Music Scan & Filter ---

    fun refreshLocalMusic() {
        viewModelScope.launch {
            _isScanning.value = true
            val filter = _scanFilter.value
            val res = withContext(Dispatchers.IO) {
                LocalMusicScanner.scanDeviceAudio(getApplication(), filter)
            }
            _scanResult.value = res
            _isScanning.value = false

            // Auto-populate initial song if nothing is loaded yet
            if (_currentSong.value == null && res.songs.isNotEmpty()) {
                _queue.value = res.songs
                _currentIndex.value = 0
                prepareSongMetadataOnly(res.songs.first())
            }
        }
    }

    fun updateFilter(
        minDurationSec: Int = _scanFilter.value.minDurationSec,
        maxDurationMin: Int = _scanFilter.value.maxDurationMin,
        minSizeKb: Int = _scanFilter.value.minSizeKb,
        autoExcludeRecordings: Boolean = _scanFilter.value.autoExcludeRecordings,
        excludedFolders: Set<String> = _scanFilter.value.excludedFolders,
        selectedFolderOnly: String? = _scanFilter.value.selectedFolderOnly
    ) {
        val updated = LocalScanFilter(
            minDurationSec = minDurationSec.coerceIn(0, 600),
            maxDurationMin = maxDurationMin.coerceIn(0, 180),
            minSizeKb = minSizeKb.coerceIn(0, 20480),
            autoExcludeRecordings = autoExcludeRecordings,
            excludedFolders = excludedFolders,
            selectedFolderOnly = selectedFolderOnly
        )
        _scanFilter.value = updated
        saveFilter(updated)
        refreshLocalMusic()
    }

    fun toggleFolderExclusion(folderPath: String) {
        val cur = _scanFilter.value.excludedFolders.toMutableSet()
        if (cur.contains(folderPath)) {
            cur.remove(folderPath)
            showToast("已恢复文件夹: ${File(folderPath).name}")
        } else {
            cur.add(folderPath)
            showToast("已屏蔽文件夹: ${File(folderPath).name}")
        }
        val selectedOnly = if (_scanFilter.value.selectedFolderOnly == folderPath) null else _scanFilter.value.selectedFolderOnly
        updateFilter(excludedFolders = cur, selectedFolderOnly = selectedOnly)
    }

    fun selectSingleFolderFilter(folderPath: String?) {
        updateFilter(selectedFolderOnly = folderPath)
    }

    fun setLocalSearchQuery(query: String) {
        _localSearchQuery.value = query
    }

    // --- Playback Control ---

    private fun prepareSongMetadataOnly(song: BeansSong) {
        _currentSong.value = song
        _durationMs.value = song.durationMs
        _progressMs.value = 0L
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            _currentCoverBitmap.value = LocalMediaHelper.loadCoverBitmap(ctx, song)
            _currentColors.value = LocalMediaHelper.extractColors(ctx, song)
            _currentLyrics.value = LocalMediaHelper.loadLyricsForSong(ctx, song)
        }
    }

    fun playSong(song: BeansSong, newQueue: List<BeansSong>? = null) {
        if (newQueue != null && newQueue.isNotEmpty()) {
            _queue.value = newQueue
        } else if (_queue.value.none { it.id == song.id }) {
            _queue.value = listOf(song) + _queue.value
        }
        val idx = _queue.value.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        _currentIndex.value = idx
        _currentSong.value = song
        _progressMs.value = 0L
        _durationMs.value = song.durationMs
        recordRecentSong(song)

        viewModelScope.launch {
            val ctx = getApplication<Application>()
            // Load cover & palette & lyrics concurrently
            launch {
                _currentCoverBitmap.value = LocalMediaHelper.loadCoverBitmap(ctx, song)
                _currentColors.value = LocalMediaHelper.extractColors(ctx, song)
            }
            launch {
                _currentLyrics.value = emptyList()
                _currentLyrics.value = LocalMediaHelper.loadLyricsForSong(ctx, song)
            }

            val playUri: Uri? = when (song.source) {
                SongSource.LOCAL -> {
                    song.contentUri ?: song.localPath?.let { Uri.fromFile(File(it)) }
                }
                SongSource.NETEASE -> {
                    val netId = song.neteaseId
                    if (netId != null) {
                        val url = NetEaseCloudClient.fetchSongPlayUrl(netId, _audioQuality.value)
                        url?.let { Uri.parse(it) }
                    } else null
                }
                SongSource.QQ -> {
                    val mid = song.qqMid
                    if (!mid.isNullOrBlank()) {
                        val url = QQMusicCloudClient.fetchSongPlayUrl(mid)
                        url?.let { Uri.parse(it) }
                    } else null
                }
            }

            if (playUri == null) {
                showToast("无法获取播放地址")
                return@launch
            }

            val controller = mediaController
            if (controller != null) {
                val metadataBuilder = MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                if (!song.coverUrl.isNullOrBlank()) {
                    metadataBuilder.setArtworkUri(Uri.parse(song.coverUrl))
                }
                val item = MediaItem.Builder()
                    .setMediaId(song.id)
                    .setUri(playUri)
                    .setMediaMetadata(metadataBuilder.build())
                    .build()

                controller.setMediaItem(item)
                controller.setPlaybackSpeed(_playbackSpeed.value)
                controller.prepare()
                controller.play()
                _isPlaying.value = true
            }
        }
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.mediaItemCount == 0) {
            val cur = _currentSong.value ?: _scanResult.value.songs.firstOrNull() ?: return
            playSong(cur, _queue.value.ifEmpty { _scanResult.value.songs })
            return
        }
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val controller = mediaController ?: return
        val target = positionMs.coerceIn(0L, _durationMs.value.coerceAtLeast(1L))
        controller.seekTo(target)
        _progressMs.value = target
    }

    fun skipNext() {
        val q = _queue.value
        if (q.isEmpty()) return
        val nextIdx = when (_repeatMode.value) {
            RepeatMode.SHUFFLE -> if (q.size > 1) q.indices.random() else 0
            RepeatMode.ONE, RepeatMode.SEQUENCE -> (_currentIndex.value + 1) % q.size
        }
        playSong(q[nextIdx])
    }

    fun skipPrevious() {
        val q = _queue.value
        if (q.isEmpty()) return
        if (_progressMs.value > 4000L) {
            seekTo(0L)
            return
        }
        val prevIdx = if (_currentIndex.value - 1 < 0) q.lastIndex else _currentIndex.value - 1
        playSong(q[prevIdx])
    }

    private fun handleTrackEnded() {
        val q = _queue.value
        if (q.isEmpty()) return
        when (_repeatMode.value) {
            RepeatMode.ONE -> {
                seekTo(0L)
                mediaController?.play()
            }
            RepeatMode.SHUFFLE -> {
                val nextIdx = if (q.size > 1) q.indices.random() else 0
                playSong(q[nextIdx])
            }
            RepeatMode.SEQUENCE -> {
                val nextIdx = (_currentIndex.value + 1) % q.size
                playSong(q[nextIdx])
            }
        }
    }

    fun cycleRepeatMode() {
        val next = when (_repeatMode.value) {
            RepeatMode.SEQUENCE -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.SHUFFLE
            RepeatMode.SHUFFLE -> RepeatMode.SEQUENCE
        }
        _repeatMode.value = next
        val label = when (next) {
            RepeatMode.SEQUENCE -> "列表循环"
            RepeatMode.ONE -> "单曲循环"
            RepeatMode.SHUFFLE -> "随机播放"
        }
        showToast(label)
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        _playbackSpeed.value = clamped
        mediaController?.setPlaybackSpeed(clamped)
        showToast("倍速: ${clamped}x")
    }

    // --- Cloud Discover & Search ---

    fun loadCloudDiscover() {
        viewModelScope.launch {
            _isDiscoverLoading.value = true
            if (_cloudPlatform.value == SongSource.QQ) {
                val tops = QQMusicCloudClient.fetchTopLists()
                val recs = QQMusicCloudClient.fetchRecommendedPlaylists(12)
                _topLists.value = tops
                _recommendedPlaylists.value = recs
            } else {
                val tops = NetEaseCloudClient.fetchTopLists()
                val recs = NetEaseCloudClient.fetchRecommendedPlaylists(12)
                _topLists.value = tops
                _recommendedPlaylists.value = recs
            }
            _isDiscoverLoading.value = false
        }
    }

    fun openCloudPlaylistOrTopList(id: Long) {
        viewModelScope.launch {
            _isLoadingPlaylistDetail.value = true
            _selectedCloudPlaylistSongs.value = emptyList()
            val tracks = if (_cloudPlatform.value == SongSource.QQ) {
                val tops = QQMusicCloudClient.fetchTopListTracks(id, limit = 60)
                if (tops.isNotEmpty()) tops else QQMusicCloudClient.fetchPlaylistTracks(id, limit = 80)
            } else {
                NetEaseCloudClient.fetchPlaylistTracks(id, limit = 80)
            }
            _selectedCloudPlaylistSongs.value = tracks
            _isLoadingPlaylistDetail.value = false
        }
    }

    // Cloud Platform Selector: NETEASE vs QQ
    fun setCloudPlatform(source: SongSource) {
        if (_cloudPlatform.value != source) {
            _cloudPlatform.value = source
            loadCloudDiscover() // Dynamically reloads discover charts & playlists!
            if (_cloudSearchResults.value.isNotEmpty()) {
                searchCloud(lastSearchKeyword)
            }
        }
    }

    private var lastSearchKeyword: String = ""

    fun searchCloud(keyword: String) {
        lastSearchKeyword = keyword
        if (keyword.isBlank()) {
            _cloudSearchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isSearchingCloud.value = true
            val results = if (_cloudPlatform.value == SongSource.QQ) {
                QQMusicCloudClient.searchSongs(keyword, limit = 35)
            } else {
                NetEaseCloudClient.searchSongs(keyword, limit = 35)
            }
            _cloudSearchResults.value = results
            _isSearchingCloud.value = false
        }
    }

    // --- Favorites & Custom Playlists ---

    fun toggleFavorite(song: BeansSong) {
        val cur = _favoriteIds.value.toMutableSet()
        if (cur.contains(song.id)) {
            cur.remove(song.id)
            showToast("已取消喜欢")
        } else {
            cur.add(song.id)
            showToast("已添加到我喜欢")
        }
        _favoriteIds.value = cur
        prefs.edit().putStringSet("favorite_ids", cur).apply()
    }

    fun createCustomPlaylist(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        val newList = _customPlaylists.value + LocalCustomPlaylist(
            id = UUID.randomUUID().toString(),
            name = clean,
            songs = emptyList()
        )
        _customPlaylists.value = newList
        saveCustomPlaylists(newList)
        showToast("已创建本地歌单「$clean」")
    }

    fun deleteCustomPlaylist(playlistId: String) {
        val newList = _customPlaylists.value.filterNot { it.id == playlistId }
        _customPlaylists.value = newList
        saveCustomPlaylists(newList)
        showToast("已删除本地歌单")
    }

    fun addSongToCustomPlaylist(playlistId: String, song: BeansSong) {
        val updated = _customPlaylists.value.map { pl ->
            if (pl.id == playlistId) {
                if (pl.songs.any { it.id == song.id }) {
                    showToast("歌曲已在该歌单中")
                    pl
                } else {
                    showToast("已加入「${pl.name}」")
                    pl.copy(songs = pl.songs + song)
                }
            } else pl
        }
        _customPlaylists.value = updated
        saveCustomPlaylists(updated)
    }

    fun setAccentTheme(index: Int) {
        _accentThemeIndex.value = index
        prefs.edit().putInt("accent_index", index).apply()
    }

    // STEPX Neo BackScreen Setters
    fun setBackScreenEnabled(enabled: Boolean) {
        _backScreenEnabled.value = enabled
        prefs.edit().putBoolean("backscreen_enabled", enabled).apply()
        showToast(if (enabled) "已开启 STEPX Neo 背屏音乐联动" else "已关闭背屏音乐联动")
    }

    fun setBackScreenMode(mode: BackScreenVisualMode) {
        _backScreenMode.value = mode
        prefs.edit().putString("backscreen_mode", mode.name).apply()
        showToast("背屏模式: ${mode.title}")
    }

    fun setBackScreenBrightness(brightness: Float) {
        val clamped = brightness.coerceIn(0.1f, 1.0f)
        _backScreenBrightness.value = clamped
        prefs.edit().putFloat("backscreen_brightness", clamped).apply()
    }

    fun setBackScreenVolumeOverlay(enabled: Boolean) {
        _backScreenVolumeOverlay.value = enabled
        prefs.edit().putBoolean("backscreen_volume_overlay", enabled).apply()
        showToast(if (enabled) "音量调节实时背屏条已开启" else "音量调节实时背屏条已关闭")
    }

    // Audio Quality Preference
    private val _audioQuality = MutableStateFlow(prefs.getString("audio_quality", "exhigh") ?: "exhigh")
    val audioQuality: StateFlow<String> = _audioQuality.asStateFlow()

    fun setAudioQuality(quality: String) {
        _audioQuality.value = quality
        prefs.edit().putString("audio_quality", quality).apply()
        val label = when (quality) {
            "lossless" -> "无损 FLAC 优先"
            "standard" -> "标准音质 (128k)"
            else -> "极高音质 (320k)"
        }
        showToast("音质已设为: $label")
    }

    fun clearAppCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val cacheDir = getApplication<Application>().cacheDir
            cacheDir.deleteRecursively()
            cacheDir.mkdirs()
            showToast("本地缓存已清理")
        }
    }

    fun clearRecentSongs() {
        _recentSongs.value = emptyList()
        prefs.edit().remove("recent_songs_json").apply()
        showToast("播放历史已清空")
    }

    // --- Persistence Helpers ---

    private fun loadSavedFilter(): LocalScanFilter {
        return LocalScanFilter(
            minDurationSec = prefs.getInt("filter_min_dur_sec", 60),
            maxDurationMin = prefs.getInt("filter_max_dur_min", 20),
            minSizeKb = prefs.getInt("filter_min_size_kb", 500),
            autoExcludeRecordings = prefs.getBoolean("filter_exclude_rec", true),
            excludedFolders = loadStringSet("filter_excluded_folders")
        )
    }

    private fun saveFilter(f: LocalScanFilter) {
        prefs.edit()
            .putInt("filter_min_dur_sec", f.minDurationSec)
            .putInt("filter_max_dur_min", f.maxDurationMin)
            .putInt("filter_min_size_kb", f.minSizeKb)
            .putBoolean("filter_exclude_rec", f.autoExcludeRecordings)
            .putStringSet("filter_excluded_folders", f.excludedFolders)
            .apply()
    }

    private fun loadStringSet(key: String): Set<String> {
        return prefs.getStringSet(key, emptySet())?.toSet() ?: emptySet()
    }

    private fun recordRecentSong(song: BeansSong) {
        val updated = (listOf(song) + _recentSongs.value.filterNot { it.id == song.id }).take(40)
        _recentSongs.value = updated
        val arr = JSONArray()
        updated.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("recent_songs_json", arr.toString()).apply()
    }

    private fun loadRecentSongs(): List<BeansSong> {
        val raw = prefs.getString("recent_songs_json", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val list = mutableListOf<BeansSong>()
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { list.add(BeansSong.fromJson(it)) }
            }
            list
        }.getOrDefault(emptyList())
    }

    private fun saveCustomPlaylists(list: List<LocalCustomPlaylist>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("custom_playlists_json", arr.toString()).apply()
    }

    private fun loadCustomPlaylists(): List<LocalCustomPlaylist> {
        val raw = prefs.getString("custom_playlists_json", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val list = mutableListOf<LocalCustomPlaylist>()
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { list.add(LocalCustomPlaylist.fromJson(it)) }
            }
            list
        }.getOrDefault(emptyList())
    }

    private fun loadSavedNetEaseUser(): NetEaseUserProfile? {
        val jsonStr = prefs.getString("netease_user_profile", null) ?: return null
        return runCatching {
            val profile = NetEaseUserProfile.fromJson(org.json.JSONObject(jsonStr))
            NetEaseCloudClient.userCookie = profile.cookie
            profile
        }.getOrNull()
    }

    fun startNetEaseQrLogin() {
        qrCheckJob?.cancel()
        _qrLoginState.value = QrLoginState.Loading("正在获取登录二维码...")
        qrCheckJob = viewModelScope.launch {
            val (key, content) = NetEaseCloudClient.fetchQrKey()
            if (key == null || content == null) {
                _qrLoginState.value = QrLoginState.Error("获取二维码失败，请重试")
                return@launch
            }
            val qrBmp = runCatching { QrCodeHelper.generateQrBitmap(content) }.getOrNull()
            if (qrBmp == null) {
                _qrLoginState.value = QrLoginState.Error("生成二维码失败")
                return@launch
            }
            _qrLoginState.value = QrLoginState.QrReady(key, qrBmp, content)

            // Polling QR code check status
            while (isActive) {
                delay(2000L)
                val (code, msg, cookie) = NetEaseCloudClient.checkQrStatus(key)
                when (code) {
                    800 -> {
                        _qrLoginState.value = QrLoginState.Error("二维码已过期，请重新刷新")
                        break
                    }
                    801 -> {
                        _qrLoginState.value = QrLoginState.WaitingScan(qrBmp)
                    }
                    802 -> {
                        _qrLoginState.value = QrLoginState.Scanned(msg)
                    }
                    803 -> {
                        val finalCookie = cookie ?: ""
                        val profile = NetEaseCloudClient.fetchLoginStatus(finalCookie)
                        if (profile != null) {
                            _netEaseUser.value = profile
                            prefs.edit().putString("netease_user_profile", profile.toJson().toString()).apply()
                            NetEaseCloudClient.userCookie = profile.cookie
                            _qrLoginState.value = QrLoginState.Success(profile)
                            showToast("登录成功：${profile.nickname}")
                            refreshNetEaseUserPlaylists()
                        } else {
                            _qrLoginState.value = QrLoginState.Error("解析用户信息失败")
                        }
                        break
                    }
                }
            }
        }
    }

    fun cancelQrLogin() {
        qrCheckJob?.cancel()
        qrCheckJob = null
        _qrLoginState.value = QrLoginState.Idle
    }

    fun logoutNetEase() {
        qrCheckJob?.cancel()
        _netEaseUser.value = null
        _netEaseUserPlaylists.value = emptyList()
        _qrLoginState.value = QrLoginState.Idle
        NetEaseCloudClient.userCookie = ""
        prefs.edit().remove("netease_user_profile").apply()
        showToast("已退出网易云账号")
    }

    fun refreshNetEaseUserPlaylists() {
        val user = _netEaseUser.value ?: return
        viewModelScope.launch {
            val list = NetEaseCloudClient.fetchUserPlaylists(user.userId)
            _netEaseUserPlaylists.value = list
        }
    }

    // --- QQ Music Login & Playlists ---

    private fun loadSavedQQUser(): QQUserProfile? {
        val jsonStr = prefs.getString("qq_user_profile", null) ?: return null
        return runCatching {
            val profile = QQUserProfile.fromJson(JSONObject(jsonStr))
            QQMusicCloudClient.userCookie = profile.cookie
            profile
        }.getOrNull()
    }

    fun startQQQrLogin() {
        qqQrCheckJob?.cancel()
        _qqLoginState.value = QQLoginState.Loading("正在生成 QQ 登录二维码...")
        qqQrCheckJob = viewModelScope.launch {
            val (bmp, qrsig) = QQMusicCloudClient.fetchQrImage()
            if (bmp == null || qrsig == null) {
                _qqLoginState.value = QQLoginState.Error("获取 QQ 二维码失败，请重试")
                return@launch
            }
            _qqLoginState.value = QQLoginState.QrReady(qrsig, bmp)

            while (isActive) {
                delay(2000L)
                val (code, msg, profile) = QQMusicCloudClient.checkQrStatus(qrsig)
                when (code) {
                    0 -> {
                        if (profile != null) {
                            _qqUser.value = profile
                            prefs.edit().putString("qq_user_profile", profile.toJson().toString()).apply()
                            QQMusicCloudClient.userCookie = profile.cookie
                            _qqLoginState.value = QQLoginState.Success(profile)
                            showToast("QQ 登录成功：${profile.nickname}")
                            refreshQQUserPlaylists()
                        } else {
                            _qqLoginState.value = QQLoginState.Error("解析 QQ 用户信息失败")
                        }
                        break
                    }
                    67 -> {
                        _qqLoginState.value = QQLoginState.Scanned(msg)
                    }
                    65, 68 -> {
                        _qqLoginState.value = QQLoginState.Error("二维码已失效，请重新刷新")
                        break
                    }
                    else -> {
                        _qqLoginState.value = QQLoginState.WaitingScan(bmp)
                    }
                }
            }
        }
    }

    fun cancelQQQrLogin() {
        qqQrCheckJob?.cancel()
        qqQrCheckJob = null
        _qqLoginState.value = QQLoginState.Idle
    }

    fun logoutQQ() {
        qqQrCheckJob?.cancel()
        _qqUser.value = null
        _qqUserPlaylists.value = emptyList()
        _qqLoginState.value = QQLoginState.Idle
        QQMusicCloudClient.userCookie = ""
        prefs.edit().remove("qq_user_profile").apply()
        showToast("已退出 QQ 账号")
    }

    fun refreshQQUserPlaylists() {
        val user = _qqUser.value ?: return
        viewModelScope.launch {
            val list = QQMusicCloudClient.fetchUserPlaylists(user.uin)
            _qqUserPlaylists.value = list
        }
    }
}
