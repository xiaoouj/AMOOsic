package com.beans.music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PauseCircleFilled
import androidx.compose.material.icons.rounded.PlayCircleFilled
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerSheet(
    viewModel: BeansViewModel,
    onDismiss: () -> Unit
) {
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val progressMs by viewModel.progressMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val speed by viewModel.playbackSpeed.collectAsState()
    val colors by viewModel.currentColors.collectAsState()
    val coverBitmap by viewModel.currentCoverBitmap.collectAsState()
    val lyrics by viewModel.currentLyrics.collectAsState()
    val favorites by viewModel.favoriteIds.collectAsState()
    val queue by viewModel.queue.collectAsState()
    val accentThemeIndex by viewModel.accentThemeIndex.collectAsState()

    val accent = BEANS_ACCENT_THEMES.getOrElse(accentThemeIndex) { BEANS_ACCENT_THEMES[0] }.primary
    val isFavorite = currentSong != null && favorites.contains(currentSong!!.id)

    var showQueueSheet by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showLyricsView by remember { mutableStateOf(false) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderProgress by remember { mutableFloatStateOf(0f) }

    val activeProgress = if (isDraggingSlider) sliderProgress else progressMs.toFloat()
    val durSafe = durationMs.coerceAtLeast(1L).toFloat()

    // Intercept back action: if queue sheet open -> close queue; else if lyrics view open -> return to artwork; else dismiss player to previous page
    androidx.activity.compose.BackHandler {
        when {
            showQueueSheet -> showQueueSheet = false
            showLyricsView -> showLyricsView = false
            else -> onDismiss()
        }
    }

    // Swipe down to dismiss
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    if (delta > 0 || dragOffsetY > 0) {
                        dragOffsetY = (dragOffsetY + delta).coerceAtLeast(0f)
                    }
                },
                onDragStopped = {
                    if (dragOffsetY > 220f) {
                        onDismiss()
                    } else {
                        dragOffsetY = 0f
                    }
                }
            )
    ) {
        // Dynamic fluid mesh blur background (Apple Music style)
        FluidMeshBackground(
            colors = colors,
            isPlaying = isPlaying,
            intensity = 1.15f
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Switch between Artwork mode & Lyrics mode
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(horizontal = 4.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (!showLyricsView) accent.copy(alpha = 0.88f) else Color.Transparent)
                            .clickable { showLyricsView = false }
                            .padding(horizontal = 14.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "歌曲",
                            color = if (!showLyricsView) Color(0xFF101218) else Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (showLyricsView) accent.copy(alpha = 0.88f) else Color.Transparent)
                            .clickable { showLyricsView = true }
                            .padding(horizontal = 14.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "歌词",
                            color = if (showLyricsView) Color(0xFF101218) else Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                IconButton(
                    onClick = { showQueueSheet = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.QueueMusic,
                        contentDescription = "Queue",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Center Area: Vinyl Turntable Disk OR Interactive 3D Karaoke Lyrics
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (!showLyricsView) {
                    // Realistic Vinyl Record with Micro-Grooves & Continuous Turntable Rotation
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        BeansVinylDisk(
                            song = currentSong,
                            coverBitmap = coverBitmap,
                            isPlaying = isPlaying,
                            size = 296.dp,
                            accentColor = accent,
                            onClick = { showLyricsView = true }
                        )
                    }
                } else {
                    // Apple Music style 3D floating karaoke lyrics view with dynamic tilt & straighten
                    KaraokeLyricsView(
                        lyrics = lyrics,
                        currentProgressMs = progressMs,
                        accentColor = accent,
                        onSeekTo = { viewModel.seekTo(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Song Title & Artist + Favorite Heart
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentSong?.title ?: "未在播放",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${currentSong?.artist ?: "未知歌手"} · ${currentSong?.album ?: ""}",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = { currentSong?.let { viewModel.toggleFavorite(it) } },
                    modifier = Modifier.size(46.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) Color(0xFFFF5C77) else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress Slider
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = activeProgress,
                    onValueChange = {
                        isDraggingSlider = true
                        sliderProgress = it
                    },
                    onValueChangeFinished = {
                        viewModel.seekTo(sliderProgress.toLong())
                        isDraggingSlider = false
                    },
                    valueRange = 0f..durSafe,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White.copy(alpha = 0.95f),
                        inactiveTrackColor = Color.White.copy(alpha = 0.22f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(activeProgress.toLong()),
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 11.sp
                    )
                    Text(
                        text = formatTime(durationMs),
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Playback Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Repeat Mode Cycle
                IconButton(onClick = { viewModel.cycleRepeatMode() }) {
                    Icon(
                        imageVector = when (repeatMode) {
                            RepeatMode.SEQUENCE -> Icons.Rounded.Repeat
                            RepeatMode.ONE -> Icons.Rounded.RepeatOne
                            RepeatMode.SHUFFLE -> Icons.Rounded.Shuffle
                        },
                        contentDescription = "Repeat",
                        tint = if (repeatMode != RepeatMode.SEQUENCE) accent else Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Previous
                IconButton(onClick = { viewModel.skipPrevious() }, modifier = Modifier.size(54.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Play / Pause
                IconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier.size(76.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.PauseCircleFilled else Icons.Rounded.PlayCircleFilled,
                        contentDescription = "PlayPause",
                        tint = Color.White,
                        modifier = Modifier.size(72.dp)
                    )
                }

                // Next
                IconButton(onClick = { viewModel.skipNext() }, modifier = Modifier.size(54.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Playback Speed
                IconButton(onClick = {
                    val nextSpeed = when {
                        speed < 0.9f -> 1.0f
                        speed < 1.2f -> 1.25f
                        speed < 1.4f -> 1.5f
                        speed < 1.7f -> 2.0f
                        else -> 0.75f
                    }
                    viewModel.setPlaybackSpeed(nextSpeed)
                }) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${speed}x",
                            color = if (speed != 1.0f) accent else Color.White.copy(alpha = 0.65f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        // Bottom Sheet: Queue List
        if (showQueueSheet) {
            ModalBottomSheet(
                onDismissRequest = { showQueueSheet = false },
                containerColor = Color(0xFF141722),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "当前播放队列 (${queue.size}首)",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { showQueueSheet = false }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(360.dp)) {
                        itemsIndexed(queue) { _, s ->
                            SongRowItem(
                                song = s,
                                isCurrent = s.id == currentSong?.id,
                                isPlaying = isPlaying,
                                isFavorite = favorites.contains(s.id),
                                accentColor = accent,
                                onClick = {
                                    viewModel.playSong(s)
                                    showQueueSheet = false
                                },
                                onToggleFavorite = { viewModel.toggleFavorite(s) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KaraokeLyricsView(
    lyrics: List<LyricLine>,
    currentProgressMs: Long,
    accentColor: Color,
    onSeekTo: (Long) -> Unit
) {
    if (lyrics.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "纯音乐 / 暂无歌词",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 16.sp
            )
        }
        return
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Find current active line index
    val activeIndex = remember(currentProgressMs, lyrics) {
        var idx = 0
        for (i in lyrics.indices) {
            if (lyrics[i].timeMs <= currentProgressMs) {
                idx = i
            } else break
        }
        idx
    }

    LaunchedEffect(activeIndex) {
        if (activeIndex in lyrics.indices) {
            scope.launch {
                listState.animateScrollToItem(
                    index = (activeIndex - 2).coerceAtLeast(0)
                )
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 120.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        itemsIndexed(lyrics) { idx, line ->
            val isActive = idx == activeIndex
            val distance = abs(idx - activeIndex)
            val lineAlpha by animateFloatAsState(
                targetValue = when (distance) {
                    0 -> 1.0f
                    1 -> 0.58f
                    2 -> 0.35f
                    else -> 0.18f
                },
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "lyricAlpha_$idx"
            )
            val lineScale by animateFloatAsState(
                targetValue = if (isActive) 1.06f else 0.94f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy),
                label = "lyricScale_$idx"
            )

            // Dynamic 3D tilt: upcoming and past lines tilt on X axis, straightening upright (0 deg) when active
            val targetTiltX = when {
                isActive -> 0f
                idx > activeIndex -> 24f.coerceAtMost(16f + distance * 3f) // Upcoming lines tilt forward/backward
                else -> (-20f).coerceAtLeast(-14f - distance * 3f) // Past lines tilt away
            }
            val tiltRotationX by animateFloatAsState(
                targetValue = targetTiltX,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "lyricTilt_$idx"
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        this.rotationX = tiltRotationX
                        this.cameraDistance = 16f * density
                        this.scaleX = lineScale
                        this.scaleY = lineScale
                        this.alpha = lineAlpha
                    }
                    .clickable { onSeekTo(line.timeMs) }
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.Start
            ) {
                if (isActive) {
                    // Enhanced Karaoke word-by-word flow
                    KaraokeTextRow(
                        line = line,
                        currentProgressMs = currentProgressMs,
                        accentColor = accentColor
                    )
                } else {
                    Text(
                        text = line.text,
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 28.sp
                    )
                }

                if (!line.translation.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = line.translation,
                        color = if (isActive) accentColor.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.4f),
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

@Composable
fun KaraokeTextRow(
    line: LyricLine,
    currentProgressMs: Long,
    accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (line.words.isNotEmpty()) {
            for (w in line.words) {
                val isSang = currentProgressMs >= (w.startMs + w.durationMs)
                val isCurrentWord = currentProgressMs >= w.startMs && !isSang
                val wordColor = when {
                    isSang -> accentColor
                    isCurrentWord -> Color.White
                    else -> Color.White.copy(alpha = 0.6f)
                }
                Text(
                    text = w.text,
                    color = wordColor,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 32.sp
                )
            }
        } else {
            Text(
                text = line.text,
                color = accentColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 32.sp
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val sec = (ms / 1000L).coerceAtLeast(0L)
    val min = sec / 60L
    val s = sec % 60L
    return "%d:%02d".format(min, s)
}
