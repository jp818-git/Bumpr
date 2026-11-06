package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import com.example.ui.BumprViewModel
import com.example.ui.components.AppBackground
import com.example.ui.screens.EqualizerDialog
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.LyricsScreen
import com.example.ui.screens.NowPlayingScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SyncEditorScreen
import com.example.ui.theme.BumprTheme

enum class ScreenDestination {
    LIBRARY,
    NOW_PLAYING,
    SETTINGS,
    LYRICS,
    SYNC_EDITOR
}

@UnstableApi
class MainActivity : ComponentActivity() {

    private val viewModel: BumprViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val appTheme by viewModel.appTheme.collectAsState()
            val themeMode by viewModel.themeMode.collectAsState()
            val backgroundStyle by viewModel.backgroundStyle.collectAsState()
            val keepAwake by viewModel.keepScreenAwake.collectAsState()

            LaunchedEffect(keepAwake) {
                if (keepAwake) {
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            BumprTheme(
                appTheme = appTheme,
                themeMode = themeMode
            ) {
                // Permission Request on Launch
                RequestAppPermissions()

                AppBackground(backgroundStyle = backgroundStyle, appTheme = appTheme) {
                    BumprAppContent(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.keepScreenAwake.value) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

@Composable
fun RequestAppPermissions() {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Gracefully handle permissions */ }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        launcher.launch(permissionsToRequest.toTypedArray())
    }
}

@UnstableApi
@Composable
fun BumprAppContent(
    viewModel: BumprViewModel
) {
    var currentScreen by rememberSaveable { mutableStateOf(ScreenDestination.LIBRARY) }
    var showEqualizerDialog by rememberSaveable { mutableStateOf(false) }

    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.musicService.isPlaying.collectAsState()
    val currentPositionMs by viewModel.musicService.currentPosition.collectAsState()
    val durationMs by viewModel.musicService.duration.collectAsState()

    // Handle Back Navigation cleanly
    BackHandler(enabled = currentScreen != ScreenDestination.LIBRARY) {
        currentScreen = when (currentScreen) {
            ScreenDestination.LYRICS, ScreenDestination.SYNC_EDITOR -> ScreenDestination.NOW_PLAYING
            ScreenDestination.NOW_PLAYING, ScreenDestination.SETTINGS -> ScreenDestination.LIBRARY
            else -> ScreenDestination.LIBRARY
        }
    }

    val isTopLevelScreen = currentScreen in listOf(
        ScreenDestination.LIBRARY,
        ScreenDestination.NOW_PLAYING,
        ScreenDestination.SETTINGS
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        bottomBar = {
            if (isTopLevelScreen) {
                NavigationBar(
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .testTag("bottom_navigation_bar"),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = currentScreen == ScreenDestination.LIBRARY,
                        onClick = { currentScreen = ScreenDestination.LIBRARY },
                        icon = {
                            Icon(
                                imageVector = if (currentScreen == ScreenDestination.LIBRARY) Icons.Default.LibraryMusic
                                               else Icons.Outlined.LibraryMusic,
                                contentDescription = "Library"
                            )
                        },
                        label = { Text("Library") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("nav_item_library")
                    )

                    NavigationBarItem(
                        selected = currentScreen == ScreenDestination.NOW_PLAYING,
                        onClick = { currentScreen = ScreenDestination.NOW_PLAYING },
                        icon = {
                            Icon(
                                imageVector = if (currentScreen == ScreenDestination.NOW_PLAYING) Icons.Default.PlayCircle
                                               else Icons.Outlined.PlayCircleOutline,
                                contentDescription = "Now Playing"
                            )
                        },
                        label = { Text("Playing") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("nav_item_now_playing")
                    )

                    NavigationBarItem(
                        selected = currentScreen == ScreenDestination.SETTINGS,
                        onClick = { currentScreen = ScreenDestination.SETTINGS },
                        icon = {
                            Icon(
                                imageVector = if (currentScreen == ScreenDestination.SETTINGS) Icons.Default.Settings
                                               else Icons.Outlined.Settings,
                                contentDescription = "Settings"
                            )
                        },
                        label = { Text("Settings") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("nav_item_settings")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main Screen Content
            when (currentScreen) {
                ScreenDestination.LIBRARY -> {
                    LibraryScreen(
                        viewModel = viewModel,
                        onNavigateToNowPlaying = { currentScreen = ScreenDestination.NOW_PLAYING }
                    )
                }
                ScreenDestination.NOW_PLAYING -> {
                    NowPlayingScreen(
                        viewModel = viewModel,
                        onNavigateBack = { currentScreen = ScreenDestination.LIBRARY },
                        onNavigateToLyrics = { currentScreen = ScreenDestination.LYRICS },
                        onNavigateToSyncEditor = { currentScreen = ScreenDestination.SYNC_EDITOR },
                        onOpenEqualizer = { showEqualizerDialog = true }
                    )
                }
                ScreenDestination.SETTINGS -> {
                    SettingsScreen(viewModel = viewModel)
                }
                ScreenDestination.LYRICS -> {
                    LyricsScreen(
                        viewModel = viewModel,
                        onNavigateBack = { currentScreen = ScreenDestination.NOW_PLAYING },
                        onNavigateToSyncEditor = { currentScreen = ScreenDestination.SYNC_EDITOR }
                    )
                }
                ScreenDestination.SYNC_EDITOR -> {
                    SyncEditorScreen(
                        viewModel = viewModel,
                        onNavigateBack = { currentScreen = ScreenDestination.NOW_PLAYING }
                    )
                }
            }
        }
    }

    // Audio Equalizer Dialog
    if (showEqualizerDialog) {
        EqualizerDialog(
            viewModel = viewModel,
            onDismissRequest = { showEqualizerDialog = false }
        )
    }
}
