package com.beans.music

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryAndSettingsScreen(
    viewModel: BeansViewModel,
    onOpenSongMenu: (BeansSong) -> Unit
) {
    val favorites by viewModel.favoriteIds.collectAsState()
    val scanResult by viewModel.scanResult.collectAsState()
    val customPlaylists by viewModel.customPlaylists.collectAsState()
    val recentSongs by viewModel.recentSongs.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val accentThemeIndex by viewModel.accentThemeIndex.collectAsState()

    val netEaseUser by viewModel.netEaseUser.collectAsState()
    val netEasePlaylists by viewModel.netEaseUserPlaylists.collectAsState()

    val qqUser by viewModel.qqUser.collectAsState()
    val qqPlaylists by viewModel.qqUserPlaylists.collectAsState()

    val cloudPlatform by viewModel.cloudPlatform.collectAsState()
    val audioQuality by viewModel.audioQuality.collectAsState()
    val scanFilter by viewModel.scanFilter.collectAsState()

    val playlistSongs by viewModel.selectedCloudPlaylistSongs.collectAsState()
    val isLoadingDetail by viewModel.isLoadingPlaylistDetail.collectAsState()

    val backScreenEnabled by viewModel.backScreenEnabled.collectAsState()
    val backScreenMode by viewModel.backScreenMode.collectAsState()
    val backScreenBrightness by viewModel.backScreenBrightness.collectAsState()
    val backScreenVolumeOverlay by viewModel.backScreenVolumeOverlay.collectAsState()
    val isBackScreenHardwareAvailable = viewModel.isBackScreenHardwareAvailable

    val accent = BEANS_ACCENT_THEMES.getOrElse(accentThemeIndex) { BEANS_ACCENT_THEMES[0] }.primary
    val qqColor = Color(0xFF1296DB)

    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var viewingPlaylist by remember { mutableStateOf<LocalCustomPlaylist?>(null) }
    var viewingFavorites by remember { mutableStateOf(false) }
    var viewingHistory by remember { mutableStateOf(false) }
    var viewingCloudPlaylistTitle by remember { mutableStateOf<String?>(null) }
    var showNetEaseLoginDialog by remember { mutableStateOf(false) }
    var showQQLoginDialog by remember { mutableStateOf(false) }

    val favoriteSongs = remember(favorites, scanResult.songs, recentSongs) {
        val pool = (scanResult.songs + recentSongs).distinctBy { it.id }
        pool.filter { favorites.contains(it.id) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "我的曲库与设置",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "网易云/QQ音乐双核切换 · 双端云歌单同步 · 音频与个性化系统设置",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (viewingFavorites) {
            // Viewing Favorites List
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { viewingFavorites = false }) {
                    Text("← 返回", color = accent, fontWeight = FontWeight.Bold)
                }
                Text("我喜欢的音乐 (${favoriteSongs.size}首)", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 150.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(favoriteSongs) { s ->
                    SongRowItem(
                        song = s,
                        isCurrent = s.id == currentSong?.id,
                        isPlaying = isPlaying,
                        isFavorite = true,
                        accentColor = accent,
                        onClick = { viewModel.playSong(s, favoriteSongs) },
                        onToggleFavorite = { viewModel.toggleFavorite(s) },
                        onAddToPlaylist = { onOpenSongMenu(s) }
                    )
                }
            }
        } else if (viewingHistory) {
            // Viewing History List
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { viewingHistory = false }) {
                        Text("← 返回", color = accent, fontWeight = FontWeight.Bold)
                    }
                    Text("最近播放记录 (${recentSongs.size}首)", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                if (recentSongs.isNotEmpty()) {
                    TextButton(onClick = { viewModel.clearRecentSongs() }) {
                        Text("清空", color = Color.Red.copy(alpha = 0.8f), fontSize = 13.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 150.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(recentSongs) { s ->
                    SongRowItem(
                        song = s,
                        isCurrent = s.id == currentSong?.id,
                        isPlaying = isPlaying,
                        isFavorite = favorites.contains(s.id),
                        accentColor = accent,
                        onClick = { viewModel.playSong(s, recentSongs) },
                        onToggleFavorite = { viewModel.toggleFavorite(s) },
                        onAddToPlaylist = { onOpenSongMenu(s) }
                    )
                }
            }
        } else if (viewingPlaylist != null) {
            // Viewing a custom playlist
            val pl = viewingPlaylist!!
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { viewingPlaylist = null }) {
                    Text("← 返回", color = accent, fontWeight = FontWeight.Bold)
                }
                Text(pl.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = {
                    viewModel.deleteCustomPlaylist(pl.id)
                    viewingPlaylist = null
                }) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.8f))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (pl.songs.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("歌单为空，可在曲库或搜索中点击「+」添加歌曲", color = Color.White.copy(alpha = 0.45f))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 150.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(pl.songs) { s ->
                        SongRowItem(
                            song = s,
                            isCurrent = s.id == currentSong?.id,
                            isPlaying = isPlaying,
                            isFavorite = favorites.contains(s.id),
                            accentColor = accent,
                            onClick = { viewModel.playSong(s, pl.songs) },
                            onToggleFavorite = { viewModel.toggleFavorite(s) },
                            onAddToPlaylist = { onOpenSongMenu(s) }
                        )
                    }
                }
            }
        } else {
            // Main Library & Comprehensive Settings
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 150.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Cloud Accounts Cards (NetEase & QQ Music)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // NetEase Account Card
                        if (netEaseUser == null) {
                            LiquidGlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 18.dp,
                                fillAlpha = 0.10f,
                                borderAlpha = 0.20f
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFE60026)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Rounded.Cloud, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text("网易云音乐账号", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                            Text("扫码登录自动同步网易云个人歌单", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(accent)
                                            .clickable { showNetEaseLoginDialog = true }
                                            .padding(horizontal = 13.dp, vertical = 7.dp)
                                    ) {
                                        Text("扫码登录", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            val user = netEaseUser!!
                            LiquidGlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 18.dp,
                                fillAlpha = 0.12f,
                                borderAlpha = 0.24f
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        AsyncImage(
                                            model = user.avatarUrl,
                                            contentDescription = "Avatar",
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .border(1.5.dp, accent, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(user.nickname, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                                if (user.vipType > 0) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(Color(0xFFFFB300))
                                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                                    ) {
                                                        Text("VIP", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                            Text("网易云已连接 (${netEasePlaylists.size}个歌单)", color = accent, fontSize = 11.sp)
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        IconButton(
                                            onClick = { viewModel.refreshNetEaseUserPlaylists() },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = accent, modifier = Modifier.size(20.dp))
                                        }
                                        TextButton(onClick = { viewModel.logoutNetEase() }) {
                                            Text("退出", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // QQ Music Account Card
                        if (qqUser == null) {
                            LiquidGlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 18.dp,
                                fillAlpha = 0.10f,
                                borderAlpha = 0.20f
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(CircleShape)
                                                .background(qqColor),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("QQ", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text("QQ 音乐账号", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                            Text("扫码登录自动同步 QQ 个人歌单", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(qqColor)
                                            .clickable { showQQLoginDialog = true }
                                            .padding(horizontal = 13.dp, vertical = 7.dp)
                                    ) {
                                        Text("扫码登录", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            val user = qqUser!!
                            LiquidGlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 18.dp,
                                fillAlpha = 0.12f,
                                borderAlpha = 0.24f
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        AsyncImage(
                                            model = user.avatarUrl,
                                            contentDescription = "Avatar",
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .border(1.5.dp, qqColor, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(user.nickname, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                            Text("QQ 音乐已连接 (${qqPlaylists.size}个歌单)", color = qqColor, fontSize = 11.sp)
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        IconButton(
                                            onClick = { viewModel.refreshQQUserPlaylists() },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = qqColor, modifier = Modifier.size(20.dp))
                                        }
                                        TextButton(onClick = { viewModel.logoutQQ() }) {
                                            Text("退出", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. QQ Music vs NetEase Cloud Platform Switcher
                item {
                    LiquidGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 18.dp,
                        fillAlpha = 0.10f,
                        borderAlpha = 0.18f
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.CloudDownload, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("当前云音乐源切换", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "全网搜索、每日发现与在线排行榜所采用的解析平台",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    SongSource.NETEASE to "网易云音乐",
                                    SongSource.QQ to "QQ音乐"
                                ).forEach { (src, label) ->
                                    val isSelected = cloudPlatform == src
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSelected) accent else Color.Transparent)
                                            .clickable { viewModel.setCloudPlatform(src) }
                                            .padding(vertical = 9.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (isSelected) {
                                                Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                            }
                                            Text(
                                                text = label,
                                                color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.7f),
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Synced NetEase Cloud Playlists (When Logged in)
                if (netEaseUser != null && netEasePlaylists.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Cloud, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("网易云同步歌单 (${netEasePlaylists.size})", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            }
                            Text("点击查看曲目", color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(netEasePlaylists) { pl ->
                                LiquidGlassCard(
                                    modifier = Modifier
                                        .width(136.dp)
                                        .clickable {
                                            viewingCloudPlaylistTitle = pl.name
                                            viewModel.openCloudPlaylistOrTopList(pl.id)
                                        },
                                    cornerRadius = 16.dp,
                                    fillAlpha = 0.12f,
                                    borderAlpha = 0.18f
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        AsyncImage(
                                            model = pl.coverUrl,
                                            contentDescription = pl.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(116.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0xFF232634))
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = pl.name,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${pl.trackCount} 首",
                                            color = Color.White.copy(alpha = 0.45f),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3.2 Synced QQ Cloud Playlists (When Logged in)
                if (qqUser != null && qqPlaylists.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Cloud, contentDescription = null, tint = qqColor, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("QQ 音乐同步歌单 (${qqPlaylists.size})", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            }
                            Text("点击查看曲目", color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(qqPlaylists) { pl ->
                                LiquidGlassCard(
                                    modifier = Modifier
                                        .width(136.dp)
                                        .clickable {
                                            viewingCloudPlaylistTitle = pl.name
                                            viewModel.openCloudPlaylistOrTopList(pl.id)
                                        },
                                    cornerRadius = 16.dp,
                                    fillAlpha = 0.12f,
                                    borderAlpha = 0.18f
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        AsyncImage(
                                            model = pl.coverUrl,
                                            contentDescription = pl.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(116.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0xFF232634))
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = pl.name,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${pl.trackCount} 首",
                                            color = Color.White.copy(alpha = 0.45f),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Favorites & History Cards
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        LiquidGlassCard(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewingFavorites = true },
                            cornerRadius = 20.dp,
                            fillAlpha = 0.12f,
                            borderAlpha = 0.22f
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Icon(
                                    imageVector = Icons.Rounded.Favorite,
                                    contentDescription = null,
                                    tint = Color(0xFFFF5C77),
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("我喜欢的音乐", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text("${favorites.size} 首歌曲", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                            }
                        }

                        LiquidGlassCard(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewingHistory = true },
                            cornerRadius = 20.dp,
                            fillAlpha = 0.12f,
                            borderAlpha = 0.22f
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Icon(
                                    imageVector = Icons.Rounded.History,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("最近播放历史", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text("${recentSongs.size} 首记录", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                            }
                        }
                    }
                }

                // 5. Custom Local Playlists Section
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("本地自建歌单", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { showCreatePlaylistDialog = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.Add, contentDescription = "New Playlist", tint = accent, modifier = Modifier.size(22.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    if (customPlaylists.isEmpty()) {
                        Text(
                            text = "暂无自建歌单，点击右上角「+」新建一个吧",
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 13.sp
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            customPlaylists.forEach { pl ->
                                LiquidGlassCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewingPlaylist = pl },
                                    cornerRadius = 16.dp,
                                    fillAlpha = 0.08f,
                                    borderAlpha = 0.16f
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(accent.copy(alpha = 0.22f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = accent)
                                        }
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(pl.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                            Text("${pl.songs.size} 首歌曲", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                                        }
                                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Color.White.copy(alpha = 0.45f))
                                    }
                                }
                            }
                        }
                    }
                }

                // 6. Comprehensive App Settings Section
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Settings, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("应用与播放设置", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // 6.0 STEPX Neo BackScreen Music Matrix Visualizer Card
                item {
                    LiquidGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 20.dp,
                        fillAlpha = 0.12f,
                        borderAlpha = 0.24f
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(accent.copy(alpha = 0.22f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.GraphicEq,
                                            contentDescription = null,
                                            tint = accent,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "STEPX Neo 17×23 背屏联动",
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isBackScreenHardwareAvailable) Color(0xFF4ADE80) else Color(0xFFF87171))
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (isBackScreenHardwareAvailable) "MiniLED 点阵接口已连接" else "未检测到 STEPX Neo 硬件",
                                                color = if (isBackScreenHardwareAvailable) Color(0xFF4ADE80) else Color(0xFFF87171),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }

                                Switch(
                                    checked = backScreenEnabled,
                                    onCheckedChange = { viewModel.setBackScreenEnabled(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = accent,
                                        checkedTrackColor = accent.copy(alpha = 0.35f)
                                    )
                                )
                            }

                            if (backScreenEnabled) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "背屏动效模式",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    BackScreenVisualMode.values().forEach { mode ->
                                        val isSel = backScreenMode == mode
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isSel) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f))
                                                .border(
                                                    width = if (isSel) 1.5.dp else 0.5.dp,
                                                    color = if (isSel) accent else Color.White.copy(alpha = 0.12f),
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .clickable { viewModel.setBackScreenMode(mode) }
                                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = mode.title,
                                                        color = if (isSel) Color.White else Color.White.copy(alpha = 0.85f),
                                                        fontSize = 13.sp,
                                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                                    )
                                                    Text(
                                                        text = mode.desc,
                                                        color = Color.White.copy(alpha = 0.45f),
                                                        fontSize = 11.sp
                                                    )
                                                }
                                                if (isSel) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Check,
                                                        contentDescription = null,
                                                        tint = accent,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Volume Overlay Toggle
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "调整音量时显示音量进度条",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "按音量键即刻在背屏弹出 🔊 图标与 17x23 竖向电平条",
                                            color = Color.White.copy(alpha = 0.45f),
                                            fontSize = 11.sp
                                        )
                                    }
                                    Switch(
                                        checked = backScreenVolumeOverlay,
                                        onCheckedChange = { viewModel.setBackScreenVolumeOverlay(it) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = accent,
                                            checkedTrackColor = accent.copy(alpha = 0.35f)
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Brightness Slider
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "背屏亮度",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${(backScreenBrightness * 100).toInt()}%",
                                            color = accent,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Slider(
                                        value = backScreenBrightness,
                                        onValueChange = { viewModel.setBackScreenBrightness(it) },
                                        valueRange = 0.1f..1.0f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = accent,
                                            activeTrackColor = accent,
                                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // 6.1 Audio Quality Settings
                item {
                    LiquidGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 18.dp,
                        fillAlpha = 0.08f,
                        borderAlpha = 0.16f
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("音频流播放品质偏好", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(
                                    "standard" to "标准 128k",
                                    "exhigh" to "极高 320k",
                                    "lossless" to "无损 FLAC"
                                ).forEach { (q, label) ->
                                    val isSel = audioQuality == q
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSel) accent else Color.Transparent)
                                            .clickable { viewModel.setAudioQuality(q) }
                                            .padding(vertical = 7.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isSel) Color.Black else Color.White.copy(alpha = 0.7f),
                                            fontSize = 12.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 6.2 Local Music Scan Filter
                item {
                    LiquidGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 18.dp,
                        fillAlpha = 0.08f,
                        borderAlpha = 0.16f
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Tune, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("屏蔽录音与语音文件", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        Text("过滤通话录音、微信语音等非音乐音频", color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp)
                                    }
                                }
                                Switch(
                                    checked = scanFilter.autoExcludeRecordings,
                                    onCheckedChange = { viewModel.updateFilter(autoExcludeRecordings = it) },
                                    colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.35f))
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text("最小扫描时长过滤", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(
                                    0 to "全部扫描",
                                    30 to "> 30 秒",
                                    60 to "> 60 秒"
                                ).forEach { (sec, label) ->
                                    val isSel = scanFilter.minDurationSec == sec
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSel) accent else Color.Transparent)
                                            .clickable { viewModel.updateFilter(minDurationSec = sec) }
                                            .padding(vertical = 7.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isSel) Color.Black else Color.White.copy(alpha = 0.7f),
                                            fontSize = 12.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "已过滤录音: ${scanResult.excludedRecordingCount} 首 · 屏蔽目录: ${scanFilter.excludedFolders.size} 个",
                                    color = Color.White.copy(alpha = 0.45f),
                                    fontSize = 11.sp
                                )
                                TextButton(onClick = { viewModel.refreshLocalMusic() }) {
                                    Text("重新扫描本机", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // 6.3 Liquid Glass Theme Accent Colors
                item {
                    LiquidGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 18.dp,
                        fillAlpha = 0.08f,
                        borderAlpha = 0.16f
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Palette, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("iOS 26 液态玻璃主题色", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                itemsIndexed(BEANS_ACCENT_THEMES) { idx, t ->
                                    val isSel = idx == accentThemeIndex
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color.White.copy(alpha = 0.08f))
                                            .border(
                                                width = if (isSel) 2.dp else 0.8.dp,
                                                color = if (isSel) t.primary else Color.White.copy(alpha = 0.18f),
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                            .clickable { viewModel.setAccentTheme(idx) }
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clip(CircleShape)
                                                    .background(t.primary)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = t.name,
                                                color = if (isSel) Color.White else Color.White.copy(alpha = 0.7f),
                                                fontSize = 13.sp,
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 6.4 Cache & Storage Management
                item {
                    LiquidGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 18.dp,
                        fillAlpha = 0.08f,
                        borderAlpha = 0.16f
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Storage, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("存储与缓存管理", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .clickable { viewModel.clearAppCache() }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("清理临时缓存", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .clickable { viewModel.clearRecentSongs() }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("清空播放历史", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }

                // 6.5 About Beans Music
                item {
                    LiquidGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 18.dp,
                        fillAlpha = 0.08f,
                        borderAlpha = 0.16f
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Info, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("关于 AMOOsic", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("AMOOsic Android · v1.0.0", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("双核云端 (NetEase WeAPI & QQ音乐) + Media3 ExoPlayer", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                            Text("iOS 26 Liquid Glass 动态高斯折射 & 流体背景渲染系统", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }

    // Modal Sheet for Cloud Playlist Tracks
    if (viewingCloudPlaylistTitle != null) {
        ModalBottomSheet(
            onDismissRequest = { viewingCloudPlaylistTitle = null },
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = viewingCloudPlaylistTitle ?: "歌单歌曲",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "共收录 ${playlistSongs.size} 首歌曲",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    }
                    IconButton(onClick = { viewingCloudPlaylistTitle = null }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (isLoadingDetail) {
                    Box(modifier = Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = accent, strokeWidth = 2.dp)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(420.dp)) {
                        items(playlistSongs) { song ->
                            SongRowItem(
                                song = song,
                                isCurrent = song.id == currentSong?.id,
                                isPlaying = isPlaying,
                                isFavorite = favorites.contains(song.id),
                                accentColor = accent,
                                onClick = { viewModel.playSong(song, playlistSongs) },
                                onToggleFavorite = { viewModel.toggleFavorite(song) },
                                onAddToPlaylist = { onOpenSongMenu(song) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNetEaseLoginDialog) {
        NetEaseLoginDialog(
            viewModel = viewModel,
            accentColor = accent,
            onDismiss = { showNetEaseLoginDialog = false }
        )
    }

    if (showQQLoginDialog) {
        QQMusicLoginDialog(
            viewModel = viewModel,
            accentColor = accent,
            onDismiss = { showQQLoginDialog = false }
        )
    }

    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("新建本地歌单", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                TextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = { Text("请输入歌单名称") },
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = accent
                    ),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.createCustomPlaylist(newPlaylistName)
                    newPlaylistName = ""
                    showCreatePlaylistDialog = false
                }) {
                    Text("创建", color = accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("取消", color = Color.White.copy(alpha = 0.6f))
                }
            },
            containerColor = Color(0xFF181B26)
        )
    }
}
