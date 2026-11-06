package com.example.ui

import android.app.Application
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.example.data.DemoData
import com.example.data.FavoriteTrackEntity
import com.example.data.LyricEntity
import com.example.data.MediaStoreScanner
import com.example.data.BumprDatabase
import com.example.data.BumprPreferences
import com.example.lyrics.LrcParser
import com.example.model.AppTheme
import com.example.model.NoLyricsDisplay
import com.example.model.BackgroundStyle
import com.example.model.EqPreset
import com.example.model.EqState
import com.example.model.LyricLine
import com.example.model.LyricsData
import com.example.model.ThemeMode
import com.example.model.Track
import com.example.playback.MusicServiceConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LibraryTab {
    SONGS,
    ALBUMS,
    ARTISTS,
    FOLDERS,
    FAVORITES
}

@UnstableApi
class BumprViewModel(application: Application) : AndroidViewModel(application) {

    private val db = BumprDatabase.getDatabase(application)
    private val prefs = BumprPreferences(application)
    val musicService = MusicServiceConnection(application)

    // Library State
    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    // Folder selection & filtering
    val selectedFolders: StateFlow<Set<String>> = prefs.selectedFoldersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _discoveredDeviceFolders = MutableStateFlow<List<String>>(emptyList())

    val allDiscoveredFolders: StateFlow<List<String>> = combine(_discoveredDeviceFolders, _tracks) { devFolders, trackList ->
        (devFolders + trackList.map { it.folderPath }).filter { it.isNotBlank() }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Tracks available strictly from selected folders
    val availableTracks: StateFlow<List<Track>> = combine(_tracks, selectedFolders) { allList, folders ->
        if (folders.isEmpty()) emptyList() else allList.filter { folders.contains(it.folderPath) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _currentTab = MutableStateFlow(LibraryTab.SONGS)
    val currentTab: StateFlow<LibraryTab> = _currentTab.asStateFlow()

    val favoriteIds: StateFlow<List<String>> = db.favoriteDao().getAllFavoriteIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredTracks: StateFlow<List<Track>> = combine(availableTracks, _searchQuery, _currentTab, favoriteIds) { avTracks, query, tab, favs ->
        var list = avTracks
        if (tab == LibraryTab.FAVORITES) {
            list = list.filter { favs.contains(it.id) }
        }
        if (query.isNotBlank()) {
            val q = query.lowercase().trim()
            list = list.filter {
                it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q)
            }
        }
        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Current Playing Track
    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    // Lyrics State
    private val _currentLyrics = MutableStateFlow<LyricsData>(LyricsData())
    val currentLyrics: StateFlow<LyricsData> = _currentLyrics.asStateFlow()

    private val _lyricsOffsetMs = MutableStateFlow(0L)
    val lyricsOffsetMs: StateFlow<Long> = _lyricsOffsetMs.asStateFlow()

    private val _activeLyricIndex = MutableStateFlow(0)
    val activeLyricIndex: StateFlow<Int> = _activeLyricIndex.asStateFlow()

    // Settings
    val appTheme: StateFlow<AppTheme> = prefs.appThemeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppTheme.NEON_CYAN)

    val backgroundStyle: StateFlow<BackgroundStyle> = prefs.backgroundStyleFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BackgroundStyle.MUSICAL_STAVE)

    val noLyricsDisplay: StateFlow<NoLyricsDisplay> = prefs.noLyricsDisplayFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NoLyricsDisplay.ALBUM_ART)

    val themeMode: StateFlow<ThemeMode> = prefs.themeModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.DARK)

    val showLyricsInCar: StateFlow<Boolean> = prefs.showLyricsInCarFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val keepScreenAwake: StateFlow<Boolean> = prefs.keepScreenAwakeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val lyricsFontSize: StateFlow<Float> = prefs.lyricsFontSizeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 20f)

    // Equalizer State
    private val _eqState = MutableStateFlow(EqState())
    val eqState: StateFlow<EqState> = _eqState.asStateFlow()

    // Manual Sync Editor State
    private val _editorLines = MutableStateFlow<List<LyricLine>>(emptyList())
    val editorLines: StateFlow<List<LyricLine>> = _editorLines.asStateFlow()

    private val _editorCursorIndex = MutableStateFlow(0)
    val editorCursorIndex: StateFlow<Int> = _editorCursorIndex.asStateFlow()

    private val undoStack = mutableListOf<List<LyricLine>>()

    init {
        // Observe media player track transitions
        viewModelScope.launch {
            musicService.currentMediaId.collect { mediaId ->
                if (mediaId != null) {
                    val track = _tracks.value.find { it.id == mediaId }
                    if (track != null) {
                        _currentTrack.value = track
                        loadLyricsForTrack(track)
                    }
                }
            }
        }

        // Calculate active lyric line on the fly with <50ms response
        viewModelScope.launch {
            musicService.currentPosition.collect { pos ->
                val lyrics = _currentLyrics.value
                if (lyrics.lines.isNotEmpty() && lyrics.isSynced) {
                    val idx = LrcParser.findActiveLineIndex(lyrics.lines, pos, _lyricsOffsetMs.value)
                    if (idx != _activeLyricIndex.value) {
                        _activeLyricIndex.value = idx
                    }
                }
            }
        }

        // Auto-switch track if current playing track is excluded by folder filtering
        viewModelScope.launch {
            availableTracks.collect { avList ->
                val curr = _currentTrack.value
                if (curr != null && avList.isNotEmpty() && avList.none { it.id == curr.id }) {
                    _currentTrack.value = avList.first()
                    loadLyricsForTrack(avList.first())
                }
            }
        }

        // Observe selected folders changes to auto rescan
        viewModelScope.launch {
            selectedFolders.collect {
                rescanMediaLibrary()
            }
        }

        // Scan local device media
        rescanMediaLibrary()
    }

    fun rescanMediaLibrary() {
        viewModelScope.launch(Dispatchers.IO) {
            val devFolders = MediaStoreScanner.getDiscoveredAudioFolders(getApplication())
            _discoveredDeviceFolders.value = devFolders

            val currentSelected = selectedFolders.value
            val localTracks = MediaStoreScanner.scanLocalAudio(getApplication(), currentSelected)
            _tracks.value = localTracks

            if (_currentTrack.value == null && localTracks.isNotEmpty()) {
                _currentTrack.value = localTracks.first()
                loadLyricsForTrack(localTracks.first())
            }
        }
    }

    fun setSelectedFolders(folders: Set<String>) {
        viewModelScope.launch {
            prefs.setSelectedFolders(folders)
            rescanMediaLibrary()
        }
    }

    fun clearFolderFilter() {
        viewModelScope.launch {
            prefs.setSelectedFolders(emptySet())
            rescanMediaLibrary()
        }
    }

    fun addCustomFolder(folderName: String, uriString: String? = null) {
        viewModelScope.launch {
            val current = selectedFolders.value.toMutableSet()
            current.add(folderName)
            prefs.setSelectedFolders(current)
            if (uriString != null) {
                withContext(Dispatchers.IO) {
                    val scanned = MediaStoreScanner.scanFolderUri(getApplication(), android.net.Uri.parse(uriString), folderName)
                    if (scanned.isNotEmpty()) {
                        val combined = (_tracks.value + scanned).distinctBy { it.id }
                        _tracks.value = combined
                    }
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setTab(tab: LibraryTab) {
        _currentTab.value = tab
    }

    fun playTrack(track: Track) {
        _currentTrack.value = track
        loadLyricsForTrack(track)
        val playlist = availableTracks.value.ifEmpty { _tracks.value }
        musicService.playTrack(track, playlist)
    }

    fun togglePlayPause() {
        val controller = musicService.mediaController
        val currentMediaId = musicService.currentMediaId.value
        // If player is not initialized or queue not set, play current track directly
        if (controller == null || controller.currentMediaItem == null || currentMediaId == null) {
            val trackToPlay = _currentTrack.value ?: availableTracks.value.firstOrNull() ?: _tracks.value.firstOrNull()
            if (trackToPlay != null) {
                playTrack(trackToPlay)
            }
        } else {
            musicService.togglePlayPause()
        }
    }

    fun seekTo(positionMs: Long) {
        musicService.seekTo(positionMs)
    }

    fun skipToNext() {
        val controller = musicService.mediaController
        val currentMediaId = musicService.currentMediaId.value
        if (controller == null || controller.currentMediaItem == null || currentMediaId == null) {
            val list = availableTracks.value.ifEmpty { _tracks.value }
            val current = _currentTrack.value ?: list.firstOrNull()
            if (current != null && list.isNotEmpty()) {
                val idx = list.indexOfFirst { it.id == current.id }
                val nextTrack = if (idx in 0 until list.size - 1) list[idx + 1] else list.first()
                playTrack(nextTrack)
            }
        } else {
            musicService.skipToNext()
        }
    }

    fun skipToPrevious() {
        val controller = musicService.mediaController
        val currentMediaId = musicService.currentMediaId.value
        if (controller == null || controller.currentMediaItem == null || currentMediaId == null) {
            val list = availableTracks.value.ifEmpty { _tracks.value }
            val current = _currentTrack.value ?: list.firstOrNull()
            if (current != null && list.isNotEmpty()) {
                val idx = list.indexOfFirst { it.id == current.id }
                val prevTrack = if (idx > 0) list[idx - 1] else list.last()
                playTrack(prevTrack)
            }
        } else {
            musicService.skipToPrevious()
        }
    }

    fun toggleShuffle() {
        musicService.toggleShuffle()
    }

    fun cycleRepeatMode() {
        musicService.cycleRepeatMode()
    }

    fun toggleFavorite(trackId: String) {
        viewModelScope.launch {
            if (db.favoriteDao().isFavorite(trackId)) {
                db.favoriteDao().removeFavorite(trackId)
            } else {
                db.favoriteDao().addFavorite(FavoriteTrackEntity(trackId))
            }
        }
    }

    // Lyrics Loading & Management
    fun loadLyricsForTrack(track: Track) {
        viewModelScope.launch {
            // Priority 1: User-edited/saved lyrics in local Room DB
            val savedEntity = withContext(Dispatchers.IO) {
                db.lyricDao().getLyricForTrack(track.id)
            }
            if (savedEntity != null) {
                if (savedEntity.lrcContent.isBlank()) {
                    val emptyData = LyricsData(
                        title = track.title,
                        artist = track.artist,
                        lines = emptyList(),
                        isSynced = false,
                        rawText = ""
                    )
                    _currentLyrics.value = emptyData
                    _lyricsOffsetMs.value = 0L
                    prepareEditorWithLyrics(emptyData)
                    return@launch
                }
                val parsed = LrcParser.parse(savedEntity.lrcContent)
                _currentLyrics.value = parsed
                _lyricsOffsetMs.value = savedEntity.offsetMs
                prepareEditorWithLyrics(parsed)
                return@launch
            }

            // Priority 2: Demo lyrics map
            val demoText = DemoData.demoLyrics[track.id]
            if (!demoText.isNullOrBlank()) {
                val parsed = LrcParser.parse(demoText)
                _currentLyrics.value = parsed
                _lyricsOffsetMs.value = parsed.offsetMs
                prepareEditorWithLyrics(parsed)
                return@launch
            }

            // Priority 3: Sidecar .lrc file in folder
            val sidecarText = withContext(Dispatchers.IO) {
                MediaStoreScanner.readSidecarLrc(getApplication(), track)
            }
            if (!sidecarText.isNullOrBlank()) {
                val parsed = LrcParser.parse(sidecarText)
                _currentLyrics.value = parsed
                _lyricsOffsetMs.value = parsed.offsetMs
                prepareEditorWithLyrics(parsed)
                return@launch
            }

            // Fallback: No lyrics found
            val emptyData = LyricsData(
                title = track.title,
                artist = track.artist,
                lines = emptyList(),
                isSynced = false,
                rawText = ""
            )
            _currentLyrics.value = emptyData
            _lyricsOffsetMs.value = 0L
            prepareEditorWithLyrics(emptyData)
        }
    }

    fun adjustLyricsOffset(deltaMs: Long) {
        _lyricsOffsetMs.value += deltaMs
    }

    fun setLyricsFontSize(size: Float) {
        viewModelScope.launch {
            prefs.setLyricsFontSize(size)
        }
    }

    fun setKeepScreenAwake(awake: Boolean) {
        viewModelScope.launch {
            prefs.setKeepScreenAwake(awake)
        }
    }

    fun setAppTheme(theme: AppTheme) {
        viewModelScope.launch {
            prefs.setAppTheme(theme)
        }
    }

    fun setNoLyricsDisplay(mode: NoLyricsDisplay) {
        viewModelScope.launch {
            prefs.setNoLyricsDisplay(mode)
        }
    }

    fun setBackgroundStyle(style: BackgroundStyle) {
        viewModelScope.launch {
            prefs.setBackgroundStyle(style)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            prefs.setThemeMode(mode)
        }
    }

    fun setShowLyricsInCar(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setShowLyricsInCar(enabled)
        }
    }

    // Equalizer Controls
    fun setEqEnabled(enabled: Boolean) {
        _eqState.value = _eqState.value.copy(isEnabled = enabled)
        musicService.setEqEnabled(enabled)
        saveEqState()
    }

    fun applyEqPreset(preset: EqPreset) {
        _eqState.value = _eqState.value.copy(
            currentPreset = preset.name,
            bandLevels = preset.bandLevels,
            bassPercent = preset.bassPercent,
            trebleDb = preset.trebleDb
        )
        preset.bandLevels.forEachIndexed { index, level ->
            musicService.setEqBand(index, level * 100) // Convert to millibels
        }
        musicService.setBassBoost(preset.bassPercent * 10) // 0-1000 range
        saveEqState()
    }

    fun setEqBandLevel(bandIndex: Int, levelDb: Int) {
        val currentLevels = _eqState.value.bandLevels.toMutableList()
        if (bandIndex in currentLevels.indices) {
            currentLevels[bandIndex] = levelDb
            _eqState.value = _eqState.value.copy(
                currentPreset = "Custom",
                bandLevels = currentLevels
            )
            musicService.setEqBand(bandIndex, levelDb * 100)
            saveEqState()
        }
    }

    fun setBassBoost(percent: Int) {
        _eqState.value = _eqState.value.copy(
            currentPreset = "Custom",
            bassPercent = percent
        )
        musicService.setBassBoost(percent * 10)
        saveEqState()
    }

    fun setTreble(db: Int) {
        _eqState.value = _eqState.value.copy(
            currentPreset = "Custom",
            trebleDb = db
        )
        // High-shelf boost on highest band
        val currentLevels = _eqState.value.bandLevels.toMutableList()
        if (currentLevels.isNotEmpty()) {
            currentLevels[currentLevels.lastIndex] = db
            musicService.setEqBand(currentLevels.lastIndex, db * 100)
        }
        saveEqState()
    }

    private fun saveEqState() {
        val state = _eqState.value
        viewModelScope.launch {
            prefs.setEqSettings(state.isEnabled, state.currentPreset, state.bassPercent, state.trebleDb)
        }
    }

    // Manual Sync Editor Functions
    private fun prepareEditorWithLyrics(lyrics: LyricsData) {
        if (lyrics.lines.isNotEmpty()) {
            _editorLines.value = lyrics.lines
            _editorCursorIndex.value = 0
        } else {
            _editorLines.value = emptyList()
            _editorCursorIndex.value = 0
        }
        undoStack.clear()
    }

    fun loadRawLyricsIntoEditor(rawText: String) {
        val lines = rawText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line ->
                val cleaned = line.replace(Regex("^\\[\\d+:\\d+(\\.\\d+)?\\]"), "").trim()
                LyricLine(0L, cleaned)
            }
        _editorLines.value = lines
        _editorCursorIndex.value = 0
        undoStack.clear()
    }

    fun markCurrentLineTimestamp() {
        val cursor = _editorCursorIndex.value
        val list = _editorLines.value.toMutableList()
        if (cursor in list.indices) {
            undoStack.add(list.toList())
            val currentPos = musicService.currentPosition.value
            list[cursor] = list[cursor].copy(timestampMs = currentPos)
            _editorLines.value = list
            if (cursor < list.lastIndex) {
                _editorCursorIndex.value = cursor + 1
            }
        }
    }

    fun setEditorCursor(index: Int) {
        if (index in _editorLines.value.indices) {
            _editorCursorIndex.value = index
        }
    }

    fun nudgeLineTimestamp(index: Int, deltaMs: Long) {
        val list = _editorLines.value.toMutableList()
        if (index in list.indices) {
            undoStack.add(list.toList())
            val newTime = (list[index].timestampMs + deltaMs).coerceAtLeast(0L)
            list[index] = list[index].copy(timestampMs = newTime)
            _editorLines.value = list
        }
    }

    fun undoEditorAction() {
        if (undoStack.isNotEmpty()) {
            val previous = undoStack.removeAt(undoStack.lastIndex)
            _editorLines.value = previous
            if (_editorCursorIndex.value > 0) {
                _editorCursorIndex.value -= 1
            }
        }
    }

    fun loadLrcFileContent(content: String) {
        val parsed = LrcParser.parse(content)
        if (parsed.lines.isNotEmpty() && parsed.lines.any { it.timestampMs > 0L }) {
            _editorLines.value = parsed.lines
            _editorCursorIndex.value = 0
            _lyricsOffsetMs.value = parsed.offsetMs
            _currentLyrics.value = parsed
            undoStack.clear()
        } else {
            loadRawLyricsIntoEditor(content)
        }
    }

    fun getFormattedCurrentLrc(): String {
        val track = _currentTrack.value ?: return ""
        val lines = if (_editorLines.value.isNotEmpty()) _editorLines.value else _currentLyrics.value.lines
        return LrcParser.formatToLrc(
            lines = lines,
            title = track.title,
            artist = track.artist,
            offsetMs = _lyricsOffsetMs.value
        )
    }

    fun clearLyricsForCurrentTrack() {
        val track = _currentTrack.value ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // Save empty lrc entry so demo or sidecar won't reload
                db.lyricDao().saveLyric(
                    LyricEntity(
                        trackId = track.id,
                        title = track.title,
                        artist = track.artist,
                        lrcContent = "",
                        offsetMs = 0L
                    )
                )
            }
            val emptyData = LyricsData(
                title = track.title,
                artist = track.artist,
                lines = emptyList(),
                isSynced = false,
                rawText = ""
            )
            _currentLyrics.value = emptyData
            _lyricsOffsetMs.value = 0L
            _editorLines.value = emptyList()
            _editorCursorIndex.value = 0
            undoStack.clear()
        }
    }

    fun saveEditorLyrics() {
        val track = _currentTrack.value ?: return
        val lines = _editorLines.value
        val lrcString = LrcParser.formatToLrc(
            lines = lines,
            title = track.title,
            artist = track.artist,
            offsetMs = _lyricsOffsetMs.value
        )

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.lyricDao().saveLyric(
                    LyricEntity(
                        trackId = track.id,
                        title = track.title,
                        artist = track.artist,
                        lrcContent = lrcString,
                        offsetMs = _lyricsOffsetMs.value
                    )
                )
            }
            val parsed = LrcParser.parse(lrcString)
            _currentLyrics.value = parsed
        }
    }

    override fun onCleared() {
        super.onCleared()
        musicService.release()
    }
}
