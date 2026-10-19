package com.beans.music

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {

    private val viewModel: BeansViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Enable 100% immersive transparent edge-to-edge
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        setContent {
            val accentThemeIndex by viewModel.accentThemeIndex.collectAsState()
            val toastMsg by viewModel.toastMessage.collectAsState()
            val accent = BEANS_ACCENT_THEMES.getOrElse(accentThemeIndex) { BEANS_ACCENT_THEMES[0] }.primary

            // Permission check for storage & audio
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { perms ->
                val granted = perms.values.any { it }
                if (granted) {
                    viewModel.refreshLocalMusic()
                }
            }

            LaunchedEffect(Unit) {
                val needed = mutableListOf<String>()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        needed.add(Manifest.permission.READ_MEDIA_AUDIO)
                    }
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        needed.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                }
                if (needed.isNotEmpty()) {
                    permissionLauncher.launch(needed.toTypedArray())
                } else {
                    viewModel.refreshLocalMusic()
                }
            }

            LaunchedEffect(toastMsg) {
                toastMsg?.let {
                    Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show()
                }
            }

            BeansMainApp(viewModel = viewModel, accentColor = accent)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeansMainApp(
    viewModel: BeansViewModel,
    accentColor: Color
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showPlayerSheet by remember { mutableStateOf(false) }
    var addToPlaylistSong by remember { mutableStateOf<BeansSong?>(null) }

    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val progressMs by viewModel.progressMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    val customPlaylists by viewModel.customPlaylists.collectAsState()
    val coverBitmap by viewModel.currentCoverBitmap.collectAsState()
    val colors by viewModel.currentColors.collectAsState()

    val bgBackdrop = rememberLayerBackdrop()
    val dockBackdrop = rememberLayerBackdrop()

    CompositionLocalProvider(LocalBackdrop provides bgBackdrop) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Screen content and background captured into dockBackdrop for floating bottom dock
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(dockBackdrop)
            ) {
                // 1. Dynamic Fluid Mesh Background covering 100% full screen
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(bgBackdrop)
                ) {
                    FluidMeshBackground(
                        colors = colors,
                        isPlaying = isPlaying,
                        intensity = 1.0f
                    )
                }

                // 2. Active Screen Content with smooth directional slide & fade transitions
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally(
                                animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
                                initialOffsetX = { fullWidth -> (fullWidth * 0.35f).toInt() }
                            ) + fadeIn(animationSpec = tween(240)))
                                .togetherWith(
                                    slideOutHorizontally(
                                        animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
                                        targetOffsetX = { fullWidth -> (-fullWidth * 0.35f).toInt() }
                                    ) + fadeOut(animationSpec = tween(180))
                                )
                        } else {
                            (slideInHorizontally(
                                animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
                                initialOffsetX = { fullWidth -> (-fullWidth * 0.35f).toInt() }
                            ) + fadeIn(animationSpec = tween(240)))
                                .togetherWith(
                                    slideOutHorizontally(
                                        animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
                                        targetOffsetX = { fullWidth -> (fullWidth * 0.35f).toInt() }
                                    ) + fadeOut(animationSpec = tween(180))
                                )
                        }
                    },
                    label = "tabTransition",
                    modifier = Modifier.fillMaxSize()
                ) { tab ->
                    when (tab) {
                        0 -> LocalMusicScreen(viewModel = viewModel, onOpenSongMenu = { addToPlaylistSong = it })
                        1 -> CloudDiscoverScreen(viewModel = viewModel, onOpenSongMenu = { addToPlaylistSong = it })
                        2 -> LibraryAndSettingsScreen(viewModel = viewModel, onOpenSongMenu = { addToPlaylistSong = it })
                    }
                }
            }

            // 3. Floating Bottom Dock (Mini Player + iOS 26 KumoneGlassTabBar)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 10.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Floating Mini Player (visible when a song is loaded)
                if (currentSong != null) {
                    LiquidMiniPlayer(
                        song = currentSong!!,
                        isPlaying = isPlaying,
                        progressMs = progressMs,
                        durationMs = durationMs,
                        coverBitmap = coverBitmap,
                        accentColor = accentColor,
                        backdrop = dockBackdrop,
                        onClick = { showPlayerSheet = true },
                        onTogglePlayPause = { viewModel.togglePlayPause() },
                        onSkipNext = { viewModel.skipNext() },
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 5.dp)
                    )
                }

                // Floating Pill Liquid Glass TabBar
                KumoneGlassTabBar(
                    items = listOf(
                        TabBarItem(0, "本机音乐", Icons.Rounded.Folder),
                        TabBarItem(1, "发现/云端", Icons.Rounded.Explore),
                        TabBarItem(2, "我的曲库", Icons.Rounded.LibraryMusic)
                    ),
                    selectedIndex = selectedTab,
                    accentColor = accentColor,
                    backdrop = dockBackdrop,
                    onSelectTab = { selectedTab = it },
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 3.dp)
                )
            }

        // 4. Full Screen Liquid Glass Player Overlay (with system back handler to prevent app exit)
        BackHandler(enabled = showPlayerSheet) {
            showPlayerSheet = false
        }

        if (showPlayerSheet) {
            FullPlayerSheet(
                viewModel = viewModel,
                onDismiss = { showPlayerSheet = false }
            )
        }

        // 5. Add to Custom Playlist Sheet
        if (addToPlaylistSong != null) {
            val song = addToPlaylistSong!!
            ModalBottomSheet(
                onDismissRequest = { addToPlaylistSong = null },
                containerColor = Color(0xFF141722),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp)
                        .padding(bottom = 36.dp)
                ) {
                    Text("添加到本地歌单", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(song.title, color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(14.dp))
                    if (customPlaylists.isEmpty()) {
                        Text("暂无自建歌单，可先在「我的曲库」新建", color = Color.White.copy(alpha = 0.45f))
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().height(260.dp)) {
                            items(customPlaylists) { pl ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            viewModel.addSongToCustomPlaylist(pl.id, song)
                                            addToPlaylistSong = null
                                        }
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.Album, contentDescription = null, tint = accentColor)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(pl.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}
