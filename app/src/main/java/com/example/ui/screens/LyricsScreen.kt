package com.example.ui.screens

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import com.example.ui.BumprViewModel
import com.example.ui.components.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun LyricsScreen(
    viewModel: BumprViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSyncEditor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentTrack by viewModel.currentTrack.collectAsState()
    val lyricsData by viewModel.currentLyrics.collectAsState()
    val activeIndex by viewModel.activeLyricIndex.collectAsState()
    val offsetMs by viewModel.lyricsOffsetMs.collectAsState()
    val fontSizeSp by viewModel.lyricsFontSize.collectAsState()
    val keepAwake by viewModel.keepScreenAwake.collectAsState()

    val isPlaying by viewModel.musicService.isPlaying.collectAsState()
    val currentPos by viewModel.musicService.currentPosition.collectAsState()
    val duration by viewModel.musicService.duration.collectAsState()

    val listState = rememberLazyListState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showOffsetControl by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    val exportLrcLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val lrcContent = viewModel.getFormattedCurrentLrc()
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(lrcContent.toByteArray(Charsets.UTF_8))
                }
                scope.launch { snackbarHostState.showSnackbar("Exported .lrc file to storage successfully!") }
            } catch (e: Exception) {
                scope.launch { snackbarHostState.showSnackbar("Export failed: ${e.message}") }
            }
        }
    }



    // Auto-scroll to active lyric line smoothly
    LaunchedEffect(activeIndex) {
        if (lyricsData.lines.isNotEmpty() && activeIndex in lyricsData.lines.indices) {
            val targetIndex = (activeIndex - 2).coerceAtLeast(0)
            listState.animateScrollToItem(targetIndex)
        }
    }

    val progress = if (duration > 0) (currentPos.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("lyrics_back_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = currentTrack?.title ?: "Lyrics",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text = if (offsetMs != 0L) "Sync Offset: ${if (offsetMs > 0) "+$offsetMs" else offsetMs} ms"
                               else "Live Line-by-Line Sync",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Header actions: Offset & Menu
                Row {
                    IconButton(
                        onClick = { showOffsetControl = !showOffsetControl },
                        modifier = Modifier.testTag("offset_toggle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "Timing Offset",
                            tint = if (showOffsetControl || offsetMs != 0L) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Open Sync Editor") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onNavigateToSyncEditor()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export .lrc to Storage") },
                                leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    val safeTitle = (currentTrack?.title ?: "lyrics").replace(Regex("[^a-zA-Z0-9.-]"), "_")
                                    exportLrcLauncher.launch("$safeTitle.lrc")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear Lyric", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    showClearConfirmDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Increase Font Size") },
                                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    viewModel.setLyricsFontSize((fontSizeSp + 2f).coerceAtMost(32f))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Decrease Font Size") },
                                leadingIcon = { Icon(Icons.Default.Remove, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    viewModel.setLyricsFontSize((fontSizeSp - 2f).coerceAtLeast(14f))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (keepAwake) "Screen Awake: On" else "Screen Awake: Off") },
                                onClick = {
                                    showMenu = false
                                    viewModel.setKeepScreenAwake(!keepAwake)
                                }
                            )
                        }
                    }
                }
            }

            // Offset Adjustment Bar (collapsible)
            if (showOffsetControl) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Offset: ${if (offsetMs > 0) "+$offsetMs" else offsetMs} ms",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = { viewModel.adjustLyricsOffset(-100L) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("-100ms")
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            OutlinedButton(
                                onClick = { viewModel.adjustLyricsOffset(100L) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("+100ms")
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            if (offsetMs != 0L) {
                                OutlinedButton(
                                    onClick = { viewModel.adjustLyricsOffset(-offsetMs) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("Reset")
                                }
                            }
                        }
                    }
                }
            }

            // Main Lyrics View
            if (lyricsData.lines.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "No synced lyrics available",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "You can create or sync lyrics using the built-in manual sync editor",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = onNavigateToSyncEditor,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Sync Editor")
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("lyrics_list"),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(lyricsData.lines) { index, line ->
                        val isCurrent = index == activeIndex
                        val containerBg by animateColorAsState(
                            targetValue = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                          else Color.Transparent,
                            animationSpec = androidx.compose.animation.core.tween(350, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                            label = "lyric_bg"
                        )
                        val borderColor by animateColorAsState(
                            targetValue = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                          else Color.Transparent,
                            animationSpec = androidx.compose.animation.core.tween(350),
                            label = "lyric_border"
                        )
                        val textColor by animateColorAsState(
                            targetValue = if (isCurrent) MaterialTheme.colorScheme.primary
                                          else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            animationSpec = androidx.compose.animation.core.tween(400),
                            label = "lyric_color"
                        )
                        val textScale by animateFloatAsState(
                            targetValue = if (isCurrent) 1.04f else 1.0f,
                            animationSpec = androidx.compose.animation.core.tween(350),
                            label = "lyric_scale"
                        )

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.seekTo(line.timestampMs + offsetMs)
                                }
                                .scale(textScale)
                                .testTag("lyric_line_$index"),
                            color = containerBg,
                            shape = RoundedCornerShape(12.dp),
                            border = if (isCurrent) androidx.compose.foundation.BorderStroke(1.dp, borderColor) else null
                        ) {
                            Text(
                                text = line.text,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontSize = fontSizeSp.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    lineHeight = (fontSizeSp * 1.35f).sp
                                ),
                                color = textColor,
                                textAlign = TextAlign.Start,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }

            // Compact Bottom Player Controls
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                tonalElevation = 8.dp
            ) {
                Column {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = formatDuration(currentPos),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IconButton(
                                onClick = { viewModel.skipToPrevious() },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Previous",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            FilledIconButton(
                                onClick = { viewModel.togglePlayPause() },
                                modifier = Modifier.size(52.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play / Pause",
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            IconButton(
                                onClick = { viewModel.skipToNext() },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Next",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        IconButton(
                            onClick = onNavigateToSyncEditor,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit in Sync Editor",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        if (showClearConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showClearConfirmDialog = false },
                title = { Text("Clear Lyric") },
                text = { Text("Are you sure you want to clear the lyrics for \"${currentTrack?.title ?: "this song"}\"?") },
                confirmButton = {
                    Button(
                        onClick = {
                            showClearConfirmDialog = false
                            viewModel.clearLyricsForCurrentTrack()
                            scope.launch { snackbarHostState.showSnackbar("Lyrics cleared") }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Clear")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
