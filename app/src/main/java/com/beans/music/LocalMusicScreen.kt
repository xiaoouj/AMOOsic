package com.beans.music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalMusicScreen(
    viewModel: BeansViewModel,
    onOpenSongMenu: (BeansSong) -> Unit
) {
    val scanResult by viewModel.scanResult.collectAsState()
    val scanFilter by viewModel.scanFilter.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val searchQuery by viewModel.localSearchQuery.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val favorites by viewModel.favoriteIds.collectAsState()
    val accentThemeIndex by viewModel.accentThemeIndex.collectAsState()

    val accent = BEANS_ACCENT_THEMES.getOrElse(accentThemeIndex) { BEANS_ACCENT_THEMES[0] }.primary

    var showFilterSheet by remember { mutableStateOf(false) }
    var showFoldersSheet by remember { mutableStateOf(false) }
    var showExcludedDialog by remember { mutableStateOf(false) }
    // 顶部筛选项默认隐藏，用户点击右上角筛选按钮时再展开
    var showFilterBar by remember { mutableStateOf(false) }

    // Filter displayed songs by search keyword
    val filteredSongs = remember(scanResult.songs, searchQuery) {
        if (searchQuery.isBlank()) {
            scanResult.songs
        } else {
            val q = searchQuery.trim().lowercase()
            scanResult.songs.filter {
                it.title.lowercase().contains(q) ||
                    it.artist.lowercase().contains(q) ||
                    it.album.lowercase().contains(q)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Header Title & Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "本机音乐库",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "已收录 ${scanResult.songs.size} 首 · 智能过滤 ${scanResult.excludedRecordingCount + scanResult.excludedByDurationOrSizeCount + scanResult.excludedByFolderCount} 个干扰项",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp
                )
            }
            Row {
                IconButton(
                    onClick = { viewModel.refreshLocalMusic() },
                    modifier = Modifier.size(38.dp)
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = accent, strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                IconButton(
                    onClick = { showFilterBar = !showFilterBar },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = "Filter",
                        tint = if (showFilterBar) accent else Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Search Bar (Beans liquid glass style)
        LiquidGlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 16.dp,
            fillAlpha = 0.08f,
            borderAlpha = 0.16f
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Search",
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(19.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setLocalSearchQuery(it) },
                    placeholder = {
                        Text(
                            text = "搜索本地歌曲、歌手、专辑…",
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 13.sp
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = accent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.setLocalSearchQuery("") },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Quick Filter Pills Row (默认隐藏，点击右上角筛选按钮展开/收起)
        AnimatedVisibility(visible = showFilterBar) {
            Column {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlassChip(
                        text = if (scanFilter.autoExcludeRecordings) "录音剔除: 开 (${scanResult.excludedRecordingCount})" else "录音剔除: 关",
                        selected = scanFilter.autoExcludeRecordings,
                        accentColor = accent,
                        onClick = {
                            viewModel.updateFilter(autoExcludeRecordings = !scanFilter.autoExcludeRecordings)
                        }
                    )

                    GlassChip(
                        text = if (scanFilter.selectedFolderOnly != null) {
                            "文件夹: ${File(scanFilter.selectedFolderOnly!!).name}"
                        } else {
                            "全盘文件夹 (${scanResult.folders.size})"
                        },
                        selected = scanFilter.selectedFolderOnly != null,
                        accentColor = accent,
                        onClick = { showFoldersSheet = true }
                    )

                    GlassChip(
                        text = "时长/大小规则",
                        selected = false,
                        accentColor = accent,
                        onClick = { showFilterSheet = true }
                    )

                    if (scanResult.excludedItemsPreview.isNotEmpty()) {
                        GlassChip(
                            text = "已过滤明细 (${scanResult.excludedRecordingCount + scanResult.excludedByDurationOrSizeCount})",
                            selected = false,
                            accentColor = accent,
                            onClick = { showExcludedDialog = true }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Song List
        if (filteredSongs.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOff,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "没有找到匹配的歌曲" else "暂无符合条件的本地音乐",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "可尝试放宽时长与容量过滤条件，或切换扫描文件夹",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 150.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredSongs, key = { it.id }) { song ->
                    SongRowItem(
                        song = song,
                        isCurrent = song.id == currentSong?.id,
                        isPlaying = isPlaying,
                        isFavorite = favorites.contains(song.id),
                        accentColor = accent,
                        onClick = { viewModel.playSong(song, filteredSongs) },
                        onToggleFavorite = { viewModel.toggleFavorite(song) },
                        onAddToPlaylist = { onOpenSongMenu(song) }
                    )
                }
            }
        }
    }

    // Modal Sheet: Detailed Local Scan Filter Dialog
    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            containerColor = Color(0xFF141722),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 36.dp)
            ) {
                Text(
                    text = "音乐扫描与过滤设置",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(18.dp))

                // 1. Auto Exclude Recordings Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "智能剔除录音文件",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "自动识别并屏蔽 Sounds、CallRecord、语音备忘录及时间戳录音文件",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = scanFilter.autoExcludeRecordings,
                        onCheckedChange = {
                            viewModel.updateFilter(autoExcludeRecordings = it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.35f))
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 2. Minimum Duration Filter
                var tempMinDur by remember { mutableFloatStateOf(scanFilter.minDurationSec.toFloat()) }
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "过滤短音频（最小播放时长）",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "${tempMinDur.toInt()} 秒",
                            color = accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Slider(
                        value = tempMinDur,
                        onValueChange = { tempMinDur = it },
                        onValueChangeFinished = {
                            viewModel.updateFilter(minDurationSec = tempMinDur.toInt())
                        },
                        valueRange = 0f..180f,
                        steps = 17,
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 3. Minimum File Size Filter
                var tempMinSize by remember { mutableFloatStateOf(scanFilter.minSizeKb.toFloat()) }
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "过滤小文件（最小文件容量）",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (tempMinSize >= 1024) "%.1f MB".format(tempMinSize / 1024) else "${tempMinSize.toInt()} KB",
                            color = accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Slider(
                        value = tempMinSize,
                        onValueChange = { tempMinSize = it },
                        onValueChangeFinished = {
                            viewModel.updateFilter(minSizeKb = tempMinSize.toInt())
                        },
                        valueRange = 0f..5120f,
                        steps = 19,
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        // Reset defaults
                        viewModel.updateFilter(
                            minDurationSec = 60,
                            maxDurationMin = 20,
                            minSizeKb = 500,
                            autoExcludeRecordings = true,
                            excludedFolders = emptySet()
                        )
                    }) {
                        Text("恢复默认规则", color = Color.White.copy(alpha = 0.6f))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    TextButton(onClick = { showFilterSheet = false }) {
                        Text("完成", color = accent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Modal Sheet: Folder Filtering & Management
    if (showFoldersSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFoldersSheet = false },
            containerColor = Color(0xFF141722),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 36.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "文件夹筛选与管理",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "可单独选定某个目录播放，或屏蔽特定文件夹",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 12.sp
                        )
                    }
                    if (scanFilter.selectedFolderOnly != null) {
                        TextButton(onClick = { viewModel.selectSingleFolderFilter(null) }) {
                            Text("恢复全盘", color = accent)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(modifier = Modifier.fillMaxWidth().height(380.dp)) {
                    items(scanResult.folders) { f ->
                        val isSelectedOnly = scanFilter.selectedFolderOnly == f.path
                        val isExcluded = f.isExcludedByUser

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    when {
                                        isSelectedOnly -> accent.copy(alpha = 0.18f)
                                        isExcluded -> Color.Red.copy(alpha = 0.08f)
                                        else -> Color.White.copy(alpha = 0.04f)
                                    }
                                )
                                .clickable {
                                    if (isSelectedOnly) {
                                        viewModel.selectSingleFolderFilter(null)
                                    } else {
                                        viewModel.selectSingleFolderFilter(f.path)
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (f.isAutoRecordingFolder) Icons.Rounded.MicOff else Icons.Rounded.Folder,
                                contentDescription = null,
                                tint = if (f.isAutoRecordingFolder) Color(0xFFFF7A85) else accent,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = f.name,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (f.isAutoRecordingFolder) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "录音目录",
                                            color = Color(0xFFFF7A85),
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                                Text(
                                    text = "${f.path} (${f.passedMusicCount}首音乐 / ${f.totalAudioCount}项音频)",
                                    color = Color.White.copy(alpha = 0.45f),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { viewModel.toggleFolderExclusion(f.path) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (isExcluded) Icons.Rounded.FolderOff else Icons.Rounded.Check,
                                    contentDescription = "Toggle exclude",
                                    tint = if (isExcluded) Color(0xFFFF5C77) else Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }
    }

    // Excluded items list dialog
    if (showExcludedDialog) {
        AlertDialog(
            onDismissRequest = { showExcludedDialog = false },
            title = {
                Text(
                    text = "自动过滤与剔除清单",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().height(320.dp)) {
                    items(scanResult.excludedItemsPreview) { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Text(
                                text = item.title,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = item.reason,
                                    color = Color(0xFFFF8B94),
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = formatTime(item.durationMs),
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExcludedDialog = false }) {
                    Text("知道了", color = accent)
                }
            },
            containerColor = Color(0xFF181B26)
        )
    }
}

private fun formatTime(ms: Long): String {
    val sec = (ms / 1000L).coerceAtLeast(0L)
    val min = sec / 60L
    val s = sec % 60L
    return "%d:%02d".format(min, s)
}
