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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
fun CloudDiscoverScreen(
    viewModel: BeansViewModel,
    onOpenSongMenu: (BeansSong) -> Unit
) {
    val cloudPlatform by viewModel.cloudPlatform.collectAsState()
    val topLists by viewModel.topLists.collectAsState()
    val recPlaylists by viewModel.recommendedPlaylists.collectAsState()
    val isDiscoverLoading by viewModel.isDiscoverLoading.collectAsState()
    val searchResults by viewModel.cloudSearchResults.collectAsState()
    val isSearchingCloud by viewModel.isSearchingCloud.collectAsState()
    val playlistSongs by viewModel.selectedCloudPlaylistSongs.collectAsState()
    val isLoadingDetail by viewModel.isLoadingPlaylistDetail.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val favorites by viewModel.favoriteIds.collectAsState()
    val accentThemeIndex by viewModel.accentThemeIndex.collectAsState()

    val accent = BEANS_ACCENT_THEMES.getOrElse(accentThemeIndex) { BEANS_ACCENT_THEMES[0] }.primary

    var cloudSearchText by remember { mutableStateOf("") }
    var viewingPlaylistTitle by remember { mutableStateOf<String?>(null) }

    val netEaseUser by viewModel.netEaseUser.collectAsState()
    val netEasePlaylists by viewModel.netEaseUserPlaylists.collectAsState()

    val qqUser by viewModel.qqUser.collectAsState()
    val qqPlaylists by viewModel.qqUserPlaylists.collectAsState()

    var showNetEaseLoginDialog by remember { mutableStateOf(false) }
    var showQQLoginDialog by remember { mutableStateOf(false) }

    val isQQ = cloudPlatform == SongSource.QQ

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "发现与云播放",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isQQ) "QQ音乐官方巅峰榜 · 精选歌单 · 全网搜索" else "网易云官方排行榜 · 精选歌单 · 全网搜索",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp
                )
            }

            // User Avatar or Login Button based on active platform
            if (isQQ) {
                if (qqUser == null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF1296DB).copy(alpha = 0.2f))
                            .border(0.8.dp, Color(0xFF1296DB).copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                            .clickable { showQQLoginDialog = true }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text("登录 QQ 音乐", color = Color(0xFF1296DB), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable { showQQLoginDialog = true }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        AsyncImage(
                            model = qqUser!!.avatarUrl,
                            contentDescription = "QQ Avatar",
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(qqUser!!.nickname, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                }
            } else {
                if (netEaseUser == null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(accent.copy(alpha = 0.15f))
                            .border(0.8.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                            .clickable { showNetEaseLoginDialog = true }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text("登录网易云", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable { showNetEaseLoginDialog = true }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        AsyncImage(
                            model = netEaseUser!!.avatarUrl,
                            contentDescription = "NetEase Avatar",
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(netEaseUser!!.nickname, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Cloud Platform Switcher: 网易云音乐 vs QQ音乐
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                        .background(if (isSelected) accent.copy(alpha = 0.88f) else Color.Transparent)
                        .clickable { viewModel.setCloudPlatform(src) }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color(0xFF10121A) else Color.White.copy(alpha = 0.65f),
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Cloud Search Bar
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
                    value = cloudSearchText,
                    onValueChange = {
                        cloudSearchText = it
                        if (it.length >= 2) viewModel.searchCloud(it)
                    },
                    placeholder = {
                        Text(
                            text = if (isQQ) "在 QQ 音乐中搜索单曲、歌手、专辑…" else "在网易云中搜索单曲、歌手、专辑…",
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
                if (cloudSearchText.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            cloudSearchText = ""
                            viewModel.searchCloud("")
                        },
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

        Spacer(modifier = Modifier.height(12.dp))

        if (cloudSearchText.isNotBlank()) {
            // Search Mode Results
            if (isSearchingCloud) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = accent, strokeWidth = 2.dp)
                }
            } else if (searchResults.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("未找到相关云端歌曲", color = Color.White.copy(alpha = 0.5f))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 150.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(searchResults) { s ->
                        SongRowItem(
                            song = s,
                            isCurrent = s.id == currentSong?.id,
                            isPlaying = isPlaying,
                            isFavorite = favorites.contains(s.id),
                            accentColor = accent,
                            onClick = { viewModel.playSong(s, searchResults) },
                            onToggleFavorite = { viewModel.toggleFavorite(s) },
                            onAddToPlaylist = { onOpenSongMenu(s) }
                        )
                    }
                }
            }
        } else {
            // Discover Mode
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 150.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // User's Synced Playlists Section
                val activeUser = if (isQQ) qqUser else netEaseUser
                val activePlaylists = if (isQQ) qqPlaylists else netEasePlaylists
                if (activeUser != null && activePlaylists.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Cloud, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isQQ) "我的 QQ 音乐歌单" else "我的网易云歌单", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = "退出登录",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 12.sp,
                                modifier = Modifier.clickable {
                                    if (isQQ) viewModel.logoutQQ() else viewModel.logoutNetEase()
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(activePlaylists) { pl ->
                                LiquidGlassCard(
                                    modifier = Modifier
                                        .width(136.dp)
                                        .clickable {
                                            viewingPlaylistTitle = pl.name
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

                // Official Rankings Row (QQ 巅峰榜 or NetEase 官方排行榜)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Leaderboard, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isQQ) "QQ 音乐巅峰榜" else "官方音乐排行榜",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    if (isDiscoverLoading && topLists.isEmpty()) {
                        CircularProgressIndicator(color = accent, modifier = Modifier.size(24.dp))
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(topLists) { top ->
                                LiquidGlassCard(
                                    modifier = Modifier
                                        .width(136.dp)
                                        .clickable {
                                            viewingPlaylistTitle = top.name
                                            viewModel.openCloudPlaylistOrTopList(top.id)
                                        },
                                    cornerRadius = 16.dp,
                                    fillAlpha = 0.12f,
                                    borderAlpha = 0.18f
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        AsyncImage(
                                            model = top.coverUrl,
                                            contentDescription = top.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(116.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0xFF232634))
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = top.name,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = top.updateFrequency,
                                            color = Color.White.copy(alpha = 0.45f),
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Recommended Playlists (QQ or NetEase)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Cloud, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isQQ) "QQ 音乐热门精选歌单" else "精选云端歌单",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        recPlaylists.forEach { pl ->
                            LiquidGlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewingPlaylistTitle = pl.name
                                        viewModel.openCloudPlaylistOrTopList(pl.id)
                                    },
                                cornerRadius = 16.dp,
                                fillAlpha = 0.08f,
                                borderAlpha = 0.16f
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = pl.coverUrl,
                                        contentDescription = pl.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFF232634))
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = pl.name,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = "${pl.trackCount} 首歌曲 · ${pl.description.ifBlank { "优质精选歌单" }}",
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.45f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Sheet: Cloud Playlist Track Details
    if (viewingPlaylistTitle != null) {
        ModalBottomSheet(
            onDismissRequest = { viewingPlaylistTitle = null },
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
                            text = viewingPlaylistTitle ?: "歌单歌曲",
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
                    IconButton(onClick = { viewingPlaylistTitle = null }) {
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
}
