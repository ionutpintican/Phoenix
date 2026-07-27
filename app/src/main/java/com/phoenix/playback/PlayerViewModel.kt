package com.phoenix.playback

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.phoenix.radio.RadioStation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Bridges the phone Compose UI to the shared [PlaybackService] session via a
 * [MediaController]. Building full [MediaItem]s (with URIs) here means phone playback works
 * directly without round-tripping through the service's browse-resolution callbacks.
 */
class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private var controller: MediaController? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentMediaId = MutableStateFlow<String?>(null)
    val currentMediaId: StateFlow<String?> = _currentMediaId.asStateFlow()

    private val _title = MutableStateFlow<String?>(null)
    val title: StateFlow<String?> = _title.asStateFlow()

    private val _artist = MutableStateFlow<String?>(null)
    val artist: StateFlow<String?> = _artist.asStateFlow()

    private val _artwork = MutableStateFlow<Uri?>(null)
    val artwork: StateFlow<Uri?> = _artwork.asStateFlow()

    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()

    /** Current playback position and track length, in ms. [duration] is 0 when unknown (radio). */
    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    val sortMode: StateFlow<MusicLibrary.SortMode> = MusicLibrary.sortMode

    init {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            controller = future.get().also { c ->
                c.addListener(PlayerListener())
                syncFrom(c)
            }
        }, MoreExecutors.directExecutor())

        // The controller doesn't push position continuously, so poll it while a track plays to
        // drive the seek bar. Dragging still reads live position between updates.
        viewModelScope.launch {
            while (isActive) {
                controller?.let { c ->
                    _position.value = c.currentPosition.coerceAtLeast(0L)
                    _duration.value = c.duration.let { if (it == C.TIME_UNSET || it < 0) 0L else it }
                }
                delay(500)
            }
        }
    }

    private inner class PlayerListener : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) { _isPlaying.value = isPlaying }
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) { controller?.let(::syncFrom) }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { controller?.let(::syncFrom) }
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { _shuffle.value = shuffleModeEnabled }
    }

    private fun syncFrom(c: MediaController) {
        _isPlaying.value = c.isPlaying
        _shuffle.value = c.shuffleModeEnabled
        _currentMediaId.value = c.currentMediaItem?.mediaId
        val m = c.mediaMetadata
        _title.value = m.title?.toString()
        _artist.value = m.artist?.toString()
        _artwork.value = m.artworkUri
        _position.value = c.currentPosition.coerceAtLeast(0L)
        _duration.value = c.duration.let { if (it == C.TIME_UNSET || it < 0) 0L else it }
    }

    // ---- Commands -------------------------------------------------------------

    /** Play a specific track list starting at [startIndex]. */
    fun playTracks(tracks: List<Track>, startIndex: Int = 0) {
        val c = controller ?: return
        if (tracks.isEmpty()) return
        c.setMediaItems(tracks.map { it.toMediaItem() }, startIndex.coerceIn(0, tracks.lastIndex), 0)
        c.prepare()
        c.play()
    }

    /** Jump-play a folder in a fresh random order — the letter-shortcut behavior. */
    fun playFolderShuffled(folderId: String) {
        val tracks = MusicLibrary.playableTracksFor(folderId).shuffled()
        playTracks(tracks, 0)
    }

    fun playStation(queue: List<RadioStation>, startIndex: Int) {
        val c = controller ?: return
        if (queue.isEmpty()) return
        c.setMediaItems(queue.map { it.toMediaItem() }, startIndex.coerceIn(0, queue.lastIndex), 0)
        c.prepare()
        c.play()
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPreviousMediaItem() }

    /** Scrub within the current track. */
    fun seekTo(positionMs: Long) {
        val c = controller ?: return
        _position.value = positionMs.coerceAtLeast(0L)
        c.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun setSortMode(mode: MusicLibrary.SortMode) = MusicLibrary.setSortMode(mode)

    override fun onCleared() {
        controller?.release()
        controller = null
    }

    private fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(mediaId)
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(albumArtUri)
                .setIsPlayable(true)
                .build()
        )
        .build()

    private fun RadioStation.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(mediaId)
        .setUri(streamUrl)
        // A controller's local URI is dropped crossing to the session, but requestMetadata.mediaUri
        // survives — carry the stream URL there so the session can still play the station if it
        // can't re-resolve the id against its in-memory lists (e.g. after a process trim).
        .setRequestMetadata(
            MediaItem.RequestMetadata.Builder()
                .setMediaUri(runCatching { Uri.parse(streamUrl) }.getOrNull())
                .build()
        )
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(name)
                .setArtist(country)
                .setArtworkUri(favicon?.let { runCatching { Uri.parse(it) }.getOrNull() })
                .setIsPlayable(true)
                .build()
        )
        .build()
}
