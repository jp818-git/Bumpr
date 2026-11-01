package com.example.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.example.model.Track
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@UnstableApi
class MusicServiceConnection(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    var mediaController: MediaController? = null
        private set

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentMediaId = MutableStateFlow<String?>(null)
    val currentMediaId: StateFlow<String?> = _currentMediaId.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private var positionTickerJob: Job? = null

    init {
        connectToService()
    }

    private fun connectToService() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, BumprMediaService::class.java)
        )
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                val controller = controllerFuture?.get()
                mediaController = controller
                _isConnected.value = true
                setupControllerListener(controller)
                updateStateFromController()
            } catch (e: Exception) {
                Log.e("MusicServiceConn", "Failed to connect to MediaService", e)
            }
        }, MoreExecutors.directExecutor())
    }

    private fun setupControllerListener(controller: MediaController?) {
        controller?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) {
                    startPositionTicker()
                } else {
                    stopPositionTicker()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                _currentMediaId.value = mediaItem?.mediaId
                _duration.value = controller.duration.coerceAtLeast(0L)
                _currentPosition.value = controller.currentPosition.coerceAtLeast(0L)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _duration.value = controller.duration.coerceAtLeast(0L)
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _isShuffle.value = shuffleModeEnabled
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _repeatMode.value = repeatMode
            }
        })
    }

    private fun updateStateFromController() {
        val controller = mediaController ?: return
        _isPlaying.value = controller.isPlaying
        _currentMediaId.value = controller.currentMediaItem?.mediaId
        _duration.value = controller.duration.coerceAtLeast(0L)
        _currentPosition.value = controller.currentPosition.coerceAtLeast(0L)
        _isShuffle.value = controller.shuffleModeEnabled
        _repeatMode.value = controller.repeatMode
        if (controller.isPlaying) {
            startPositionTicker()
        }
    }

    private fun startPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = scope.launch {
            while (isActive) {
                mediaController?.let { controller ->
                    if (controller.isPlaying) {
                        _currentPosition.value = controller.currentPosition
                        if (_duration.value <= 0L && controller.duration > 0L) {
                            _duration.value = controller.duration
                        }
                    }
                }
                delay(40) // 40ms updates for silky <50ms lyric sync responsiveness
            }
        }
    }

    private fun stopPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = null
        mediaController?.let {
            _currentPosition.value = it.currentPosition
        }
    }

    fun playTrack(track: Track, playlist: List<Track> = listOf(track)) {
        val controller = mediaController ?: return
        val mediaItems = playlist.map { item ->
            MediaItem.Builder()
                .setMediaId(item.id)
                .setUri(item.contentUri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(item.title)
                        .setArtist(item.artist)
                        .setAlbumTitle(item.album)
                        .setIsPlayable(true)
                        .build()
                )
                .build()
        }
        val targetIndex = playlist.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        controller.setMediaItems(mediaItems, targetIndex, 0L)
        controller.prepare()
        controller.play()
    }

    fun play() {
        mediaController?.play()
    }

    fun pause() {
        mediaController?.pause()
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _currentPosition.value = positionMs
    }

    fun skipToNext() {
        mediaController?.seekToNextMediaItem()
    }

    fun skipToPrevious() {
        val controller = mediaController ?: return
        if (controller.currentPosition > 3000L) {
            controller.seekTo(0L)
        } else {
            controller.seekToPreviousMediaItem()
        }
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        val next = !controller.shuffleModeEnabled
        controller.shuffleModeEnabled = next
        _isShuffle.value = next
    }

    fun cycleRepeatMode() {
        val controller = mediaController ?: return
        val nextMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        controller.repeatMode = nextMode
        _repeatMode.value = nextMode
    }

    fun setEqBand(bandIndex: Int, levelMilliBels: Int) {
        val controller = mediaController ?: return
        val command = SessionCommand(BumprMediaService.ACTION_SET_EQ_BAND, Bundle.EMPTY)
        val args = Bundle().apply {
            putInt("band", bandIndex)
            putInt("level", levelMilliBels)
        }
        controller.sendCustomCommand(command, args)
    }

    fun setBassBoost(strength: Int) {
        val controller = mediaController ?: return
        val command = SessionCommand(BumprMediaService.ACTION_SET_BASS_BOOST, Bundle.EMPTY)
        val args = Bundle().apply {
            putInt("strength", strength)
        }
        controller.sendCustomCommand(command, args)
    }

    fun setEqEnabled(enabled: Boolean) {
        val controller = mediaController ?: return
        val command = SessionCommand(BumprMediaService.ACTION_SET_EQ_ENABLED, Bundle.EMPTY)
        val args = Bundle().apply {
            putBoolean("enabled", enabled)
        }
        controller.sendCustomCommand(command, args)
    }

    fun release() {
        stopPositionTicker()
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }
}
