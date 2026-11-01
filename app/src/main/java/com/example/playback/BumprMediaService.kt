package com.example.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.MainActivity
import com.example.data.BumprDatabase
import com.example.data.DemoData
import com.example.data.BumprPreferences
import com.example.data.MediaStoreScanner
import com.example.lyrics.LrcParser
import com.example.model.LyricLine
import com.example.model.Track
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
class BumprMediaService : MediaLibraryService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var player: ExoPlayer
    private lateinit var lyricPlayer: LyricForwardingPlayer
    private var mediaLibrarySession: MediaLibrarySession? = null

    // Audio Effects
    private var hardwareEqualizer: Equalizer? = null
    private var hardwareBassBoost: BassBoost? = null

    // Lyrics tracking for Android Auto subtitle
    private var currentLyricsLines: List<LyricLine> = emptyList()
    private var lastSubtitleText: String = ""
    private var showLyricsInCar: Boolean = true
    private var cachedTracks: List<Track> = emptyList()

    companion object {
        const val TAG = "BumprMediaService"
        const val ROOT_ID = "bumpr_root"
        const val CATEGORY_SONGS = "cat_songs"
        const val CATEGORY_ALBUMS = "cat_albums"
        const val CATEGORY_ARTISTS = "cat_artists"
        const val CATEGORY_RECENT = "cat_recent"

        const val ACTION_SET_EQ_BAND = "action_set_eq_band"
        const val ACTION_SET_BASS_BOOST = "action_set_bass_boost"
        const val ACTION_SET_EQ_ENABLED = "action_set_eq_enabled"
        const val ACTION_UPDATE_LYRICS_SYNC = "action_update_lyrics_sync"
    }

    override fun onCreate() {
        super.onCreate()
        initializePlayer()
        initializeSession()
        observeSettings()
        startLyricsMetadataSyncLoop()
    }

    private fun initializePlayer() {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true) // auto handle audio focus
            .setHandleAudioBecomingNoisy(true) // auto pause on unplug
            .build()

        lyricPlayer = LyricForwardingPlayer(
            player = player,
            getLyricText = { lastSubtitleText },
            isLyricsEnabled = { showLyricsInCar }
        )

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateLyricsForCurrentMediaItem(mediaItem)
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                setupAudioEffects(audioSessionId)
            }
        })
    }

    private fun setupAudioEffects(sessionId: Int) {
        if (sessionId == C.AUDIO_SESSION_ID_UNSET) return
        try {
            hardwareEqualizer?.release()
            hardwareBassBoost?.release()

            hardwareEqualizer = Equalizer(0, sessionId).apply {
                enabled = true
            }
            hardwareBassBoost = BassBoost(0, sessionId).apply {
                enabled = true
            }
            Log.d(TAG, "Audio effects initialized on audioSessionId: $sessionId")
        } catch (e: Exception) {
            Log.w(TAG, "Hardware audio effects unavailable or failed: ${e.message}")
        }
    }

    private fun initializeSession() {
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val callback = object : MediaLibrarySession.Callback {
            override fun onGetLibraryRoot(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<MediaItem>> {
                val rootItem = MediaItem.Builder()
                    .setMediaId(ROOT_ID)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle("Bumpr")
                            .setIsBrowsable(true)
                            .setIsPlayable(false)
                            .build()
                    )
                    .build()
                return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
            }

            override fun onGetChildren(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                parentId: String,
                page: Int,
                pageSize: Int,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                val items = mutableListOf<MediaItem>()

                when (parentId) {
                    ROOT_ID -> {
                        // Top level categories for Android Auto
                        items.add(createCategoryItem(CATEGORY_RECENT, "Recently Played"))
                        items.add(createCategoryItem(CATEGORY_SONGS, "All Songs"))
                        items.add(createCategoryItem(CATEGORY_ALBUMS, "Albums"))
                        items.add(createCategoryItem(CATEGORY_ARTISTS, "Artists"))
                    }
                    CATEGORY_SONGS, CATEGORY_RECENT -> {
                        // Provide tracks strictly from user's scanned/filtered library (zero demo tracks)
                        for (track in cachedTracks) {
                            items.add(createTrackMediaItem(track.id, track.title, track.artist, track.album, track.contentUri))
                        }
                    }
                    CATEGORY_ALBUMS -> {
                        val albums = cachedTracks.map { it.album }.filter { it.isNotBlank() }.distinct()
                        for (album in albums) {
                            items.add(createCategoryItem("album_$album", album))
                        }
                    }
                    CATEGORY_ARTISTS -> {
                        val artists = cachedTracks.map { it.artist }.filter { it.isNotBlank() }.distinct()
                        for (artist in artists) {
                            items.add(createCategoryItem("artist_$artist", artist))
                        }
                    }
                    else -> {
                        if (parentId.startsWith("album_")) {
                            val albumName = parentId.removePrefix("album_")
                            val matched = cachedTracks.filter { it.album == albumName }
                            for (t in matched) {
                                items.add(createTrackMediaItem(t.id, t.title, t.artist, t.album, t.contentUri))
                            }
                        } else if (parentId.startsWith("artist_")) {
                            val artistName = parentId.removePrefix("artist_")
                            val matched = cachedTracks.filter { it.artist == artistName }
                            for (t in matched) {
                                items.add(createTrackMediaItem(t.id, t.title, t.artist, t.album, t.contentUri))
                            }
                        }
                    }
                }

                return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
            }

            override fun onAddMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: MutableList<MediaItem>
            ): ListenableFuture<MutableList<MediaItem>> {
                val updatedItems = mediaItems.map { item ->
                    if (item.localConfiguration?.uri != null) {
                        item
                    } else {
                        val track = cachedTracks.find { it.id == item.mediaId }
                        if (track != null) {
                            createTrackMediaItem(track.id, track.title, track.artist, track.album, track.contentUri)
                        } else {
                            item
                        }
                    }
                }.toMutableList()
                return Futures.immediateFuture(updatedItems)
            }

            override fun onSetMediaItems(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: MutableList<MediaItem>,
                startIndex: Int,
                startPositionMs: Long
            ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                // If Android Auto requests playing a single track, expand it into the entire playlist queue
                // so Next and Previous track controls are enabled on the vehicle head unit!
                if (mediaItems.size == 1 && cachedTracks.isNotEmpty()) {
                    val clickedId = mediaItems[0].mediaId
                    val clickedIndex = cachedTracks.indexOfFirst { it.id == clickedId }
                    if (clickedIndex >= 0) {
                        val fullPlaylist = cachedTracks.map { track ->
                            createTrackMediaItem(track.id, track.title, track.artist, track.album, track.contentUri)
                        }
                        return Futures.immediateFuture(
                            MediaSession.MediaItemsWithStartPosition(fullPlaylist, clickedIndex, startPositionMs)
                        )
                    }
                }

                val resolvedItems = mediaItems.map { item ->
                    if (item.localConfiguration?.uri != null) {
                        item
                    } else {
                        val track = cachedTracks.find { it.id == item.mediaId }
                        if (track != null) {
                            createTrackMediaItem(track.id, track.title, track.artist, track.album, track.contentUri)
                        } else {
                            item
                        }
                    }
                }.toMutableList()
                return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(resolvedItems, startIndex, startPositionMs)
                )
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                when (customCommand.customAction) {
                    ACTION_SET_EQ_BAND -> {
                        val band = args.getInt("band", 0)
                        val level = args.getInt("level", 0) // millibels
                        try {
                            hardwareEqualizer?.setBandLevel(band.toShort(), level.toShort())
                        } catch (e: Exception) {
                            Log.e(TAG, "Error setting EQ band", e)
                        }
                    }
                    ACTION_SET_BASS_BOOST -> {
                        val strength = args.getInt("strength", 0) // 0-1000
                        try {
                            hardwareBassBoost?.setStrength(strength.toShort())
                        } catch (e: Exception) {
                            Log.e(TAG, "Error setting bass boost", e)
                        }
                    }
                    ACTION_SET_EQ_ENABLED -> {
                        val enabled = args.getBoolean("enabled", true)
                        try {
                            hardwareEqualizer?.enabled = enabled
                            hardwareBassBoost?.enabled = enabled
                        } catch (e: Exception) {
                            Log.e(TAG, "Error toggling EQ", e)
                        }
                    }
                    ACTION_UPDATE_LYRICS_SYNC -> {
                        val trackId = args.getString("track_id", "")
                        val rawLrc = args.getString("raw_lrc", "")
                        if (rawLrc.isNotBlank()) {
                            val parsed = LrcParser.parse(rawLrc)
                            currentLyricsLines = parsed.lines
                            lastSubtitleText = ""
                            lyricPlayer.notifyLyricChanged()
                        }
                    }
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }

        mediaLibrarySession = MediaLibrarySession.Builder(this, lyricPlayer, callback)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()
    }

    private fun createCategoryItem(id: String, title: String): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()
    }

    private fun createTrackMediaItem(
        id: String,
        title: String,
        artist: String,
        album: String,
        uri: String
    ): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(Uri.parse(uri))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .build()
            )
            .build()
    }

    private fun updateLyricsForCurrentMediaItem(mediaItem: MediaItem?) {
        val mediaId = mediaItem?.mediaId ?: return
        serviceScope.launch {
            val lines: List<LyricLine> = withContext(Dispatchers.IO) {
                // Priority 1: User-edited/saved lyrics in local Room DB
                val db = BumprDatabase.getDatabase(this@BumprMediaService)
                val savedEntity = db.lyricDao().getLyricForTrack(mediaId)
                if (savedEntity != null && savedEntity.lrcContent.isNotBlank()) {
                    LrcParser.parse(savedEntity.lrcContent).lines
                } else {
                    // Priority 2: Sidecar .lrc file in folder
                    val track = cachedTracks.find { it.id == mediaId }
                    val sidecarText = if (track != null) MediaStoreScanner.readSidecarLrc(this@BumprMediaService, track) else null
                    if (!sidecarText.isNullOrBlank()) {
                        LrcParser.parse(sidecarText).lines
                    } else {
                        // Priority 3: Fallback demo lyrics
                        val demoText = DemoData.demoLyrics[mediaId]
                        if (!demoText.isNullOrBlank()) {
                            LrcParser.parse(demoText).lines
                        } else {
                            emptyList()
                        }
                    }
                }
            }

            currentLyricsLines = lines
            lastSubtitleText = ""
            lyricPlayer.notifyLyricChanged()
            val currentItem = player.currentMediaItem
            if (currentItem != null) {
                player.playlistMetadata = if (lines.isEmpty()) {
                    currentItem.mediaMetadata.buildUpon()
                        .setSubtitle(null)
                        .build()
                } else {
                    currentItem.mediaMetadata
                }
            }
        }
    }

    private fun observeSettings() {
        val prefs = BumprPreferences(this)
        serviceScope.launch {
            prefs.showLyricsInCarFlow.collect { enabled ->
                showLyricsInCar = enabled
                lastSubtitleText = ""
                lyricPlayer.notifyLyricChanged()
                if (!enabled) {
                    player.playlistMetadata = player.currentMediaItem?.mediaMetadata?.buildUpon()
                        ?.setSubtitle(null)
                        ?.build() ?: MediaMetadata.EMPTY
                }
            }
        }

        // Keep cached tracks in sync with user's selected folders strictly
        serviceScope.launch {
            prefs.selectedFoldersFlow.collect { folders ->
                val scanned = withContext(Dispatchers.IO) {
                    MediaStoreScanner.scanLocalAudio(this@BumprMediaService, folders)
                }
                cachedTracks = scanned
                mediaLibrarySession?.notifyChildrenChanged(ROOT_ID, 0, null)
                mediaLibrarySession?.notifyChildrenChanged(CATEGORY_SONGS, 0, null)
                mediaLibrarySession?.notifyChildrenChanged(CATEGORY_ALBUMS, 0, null)
                mediaLibrarySession?.notifyChildrenChanged(CATEGORY_ARTISTS, 0, null)
                mediaLibrarySession?.notifyChildrenChanged(CATEGORY_RECENT, 0, null)
            }
        }
    }

    /**
     * Periodically updates Android Auto media metadata with the current lyric line
     * when showLyricsInCar is enabled.
     * Projects clean lyric line text directly to the car head unit display Line 2 (Artist/Subtitle).
     */
    private fun startLyricsMetadataSyncLoop() {
        serviceScope.launch {
            while (isActive) {
                if (player.isPlaying && currentLyricsLines.isNotEmpty() && showLyricsInCar) {
                    val currentPos = player.currentPosition
                    val activeIndex = LrcParser.findActiveLineIndex(currentLyricsLines, currentPos)
                    if (activeIndex in currentLyricsLines.indices) {
                        val lineText = currentLyricsLines[activeIndex].text
                        if (lineText != lastSubtitleText && lineText.isNotBlank()) {
                            lastSubtitleText = lineText
                            lyricPlayer.notifyLyricChanged()
                            val currentItem = player.currentMediaItem
                            if (currentItem != null) {
                                val updatedMetadata = currentItem.mediaMetadata.buildUpon()
                                    .setArtist(lineText)
                                    .setSubtitle(lineText)
                                    .build()
                                player.playlistMetadata = updatedMetadata
                            }
                        }
                    }
                } else if (!player.isPlaying && lastSubtitleText.isNotEmpty()) {
                    lastSubtitleText = ""
                    lyricPlayer.notifyLyricChanged()
                }
                delay(250) // Update every 250ms for low battery drain
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onDestroy() {
        serviceScope.cancel()
        try {
            hardwareEqualizer?.release()
            hardwareBassBoost?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing audio effects", e)
        }
        mediaLibrarySession?.run {
            player.release()
            release()
            mediaLibrarySession = null
        }
        super.onDestroy()
    }
}

/**
 * Custom ForwardingPlayer that intercepts mediaMetadata to dynamically project the active lyric line
 * to Android Auto car displays without interrupting audio playback or buffering.
 */
@UnstableApi
class LyricForwardingPlayer(
    player: Player,
    private val getLyricText: () -> String?,
    private val isLyricsEnabled: () -> Boolean
) : ForwardingPlayer(player) {

    private val sessionListeners = mutableListOf<Player.Listener>()

    override fun addListener(listener: Player.Listener) {
        super.addListener(listener)
        sessionListeners.add(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        super.removeListener(listener)
        sessionListeners.remove(listener)
    }

    override fun getMediaMetadata(): MediaMetadata {
        val base = super.getMediaMetadata()
        val lyric = getLyricText()
        val enabled = isLyricsEnabled()
        return if (enabled && !lyric.isNullOrBlank()) {
            base.buildUpon()
                .setArtist(lyric) // Subtitle Line 2 on Android Auto
                .setSubtitle(lyric)
                .build()
        } else {
            base
        }
    }

    fun notifyLyricChanged() {
        val current = mediaMetadata
        sessionListeners.forEach { listener ->
            try {
                listener.onMediaMetadataChanged(current)
            } catch (e: Exception) {
                Log.w("LyricForwardingPlayer", "Listener error on metadata change", e)
            }
        }
    }
}
