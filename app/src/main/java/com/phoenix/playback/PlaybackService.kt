package com.phoenix.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.phoenix.MainActivity
import com.phoenix.R
import com.phoenix.radio.RadioBrowser
import com.phoenix.radio.RadioFavorites
import com.phoenix.radio.RadioStation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * The Media3 [MediaLibraryService] — the single playback engine and the Android Auto
 * browse tree. The phone Compose UI connects to the same session via a MediaController.
 *
 * On Android Auto no app draws its own screens; it feeds this browse tree into Auto's
 * standard media template. Everything car-specific (tabs, sort tiles, letter tiles, the
 * heart button, the artwork slideshow) is built here.
 */
class PlaybackService : MediaLibraryService() {

    private lateinit var player: ExoPlayer
    private lateinit var session: MediaLibrarySession

    /**
     * A second engine used only to play the *outgoing* song's tail during a crossfade, so its
     * fade-out can overlap the next song's fade-in. It never owns the queue or audio focus —
     * [player] stays the single source of truth the phone and car both observe.
     */
    private lateinit var crossfadePlayer: ExoPlayer

    // ---- Crossfade state (only ever touched on the main thread) ----
    private var crossfading = false
    private var crossfadeJob: Job? = null

    /** True once [crossfadePlayer] has been pre-buffered with the current song's tail, so the
     *  fade can start instantly instead of stalling ~1s while the file loads and seeks. */
    private var crossfadeArmed = false

    /** Set right before our own [ExoPlayer.seekToNextMediaItem] so the transition listener
     *  can tell the crossfade's own queue advance apart from a user skip. */
    private var selfCrossfadeTransition = false

    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val browseExecutor = Executors.newSingleThreadExecutor()

    private val slideshow = GallerySlideshow()

    /** The list the user is currently viewing on the car — the Radio tab vs. a music view — used
     *  to scope Auto's one global search box. The *displayed* list decides the corpus, not what
     *  happens to be playing. */
    @Volatile private var lastBrowsedParent: String = ID_ROOT

    private var lastSearchQuery: String = ""
    private var lastSearchItems: List<MediaItem> = emptyList()

    override fun onCreate() {
        super.onCreate()
        RadioFavorites.init(this)
        ioScope.launch {
            MusicLibrary.ensureLoaded(this@PlaybackService)
            slideshow.load(this@PlaybackService)
        }

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        // The crossfade engine mirrors the main player's audio output but must NOT handle audio
        // focus (that's [player]'s job) or the becoming-noisy event, or the two would fight.
        crossfadePlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ false,
            )
            .build()

        val sessionActivity = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        session = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(sessionActivity)
            .build()

        // Rebuild the now-playing custom layout (heart visibility depends on radio vs. song).
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                session.setCustomLayout(buildCustomLayout())
                if (selfCrossfadeTransition) {
                    // Our own 5s-early advance — leave the running crossfade alone.
                    selfCrossfadeTransition = false
                } else {
                    // Any other transition (user skip, or a natural advance when no crossfade
                    // ran) must not leave P1 half-faded or P2 playing/holding a stale tail.
                    cancelCrossfade()
                    if (crossfadeArmed) disarmCrossfade()
                }
            }

            // Keep the outgoing tail in lock-step with the user pausing/resuming mid-crossfade.
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (crossfading) crossfadePlayer.playWhenReady = isPlaying
            }
        })

        // Live Android Auto refresh when the sort changes on the phone: re-fetch every folder's
        // song list so a connected car reflects the new order.
        mainScope.launch {
            MusicLibrary.sortMode.drop(1).collect {
                MusicLibrary.browsableFolderIds().forEach {
                    session.notifyChildrenChanged("folder:$it", Int.MAX_VALUE, null)
                }
            }
        }

        // Favorites toggled anywhere → re-sort the Radio tab and flip the heart button.
        mainScope.launch {
            RadioFavorites.favorites.drop(1).collect {
                session.notifyChildrenChanged(TAB_RADIO, Int.MAX_VALUE, null)
                session.setCustomLayout(buildCustomLayout())
            }
        }

        // Reload the car slideshow whenever the library is (re)scanned. The onCreate load
        // above runs at service start, which races the phone's Images permission dialog and
        // usually loses (empty pool). The phone re-runs MusicLibrary.load() right after the
        // grant (and on manual rescans), bumping revision — reload the gallery photos then so
        // a late-granted permission actually populates the slideshow instead of leaving it
        // empty for the life of the process.
        mainScope.launch {
            MusicLibrary.revision.drop(1).collect {
                ioScope.launch { slideshow.load(this@PlaybackService) }
            }
        }

        startSlideshowLoop()
        startCrossfadeMonitor()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = session

    override fun onDestroy() {
        mainScope.cancel()
        ioScope.cancel()
        browseExecutor.shutdown()
        session.release()
        player.release()
        crossfadePlayer.release()
        super.onDestroy()
    }

    // ---- Crossfade ------------------------------------------------------------

    /**
     * Polls the main player near the end of each song and kicks off a crossfade. Runs on the
     * main thread (all player access must). Cheap: a duration check every 200ms.
     */
    private fun startCrossfadeMonitor() {
        mainScope.launch {
            while (isActive) {
                delay(CROSSFADE_POLL_MS)
                maybeStartCrossfade()
            }
        }
    }

    private fun maybeStartCrossfade() {
        if (crossfading || !player.isPlaying || !player.hasNextMediaItem()) {
            // Not eligible right now — drop any preload we armed for a song we've since left.
            if (crossfadeArmed && !crossfading) disarmCrossfade()
            return
        }
        // Songs only. Radio is live (no real duration) and must not crossfade.
        val current = player.currentMediaItem ?: return
        if (current.mediaId.startsWith("radio:")) return
        val nextIndex = player.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET || player.getMediaItemAt(nextIndex).mediaId.startsWith("radio:")) return

        val duration = player.duration
        if (duration == C.TIME_UNSET || duration <= 0) return
        val remaining = duration - player.currentPosition

        // Pre-buffer the outgoing tail into P2 a few seconds ahead of the fade so firing is
        // instant (no ~1s load/seek stall). Disarm again if the user seeks back out of range.
        when {
            !crossfadeArmed && remaining in (CROSSFADE_MS + 1)..(CROSSFADE_MS + CROSSFADE_PRELOAD_MS) ->
                armCrossfade(current, duration)
            crossfadeArmed && remaining > CROSSFADE_MS + CROSSFADE_PRELOAD_MS ->
                disarmCrossfade()
        }

        // A song shorter than the crossfade window still crossfades once, right at its start's tail.
        if (remaining in 1..CROSSFADE_MS) startCrossfade(current)
    }

    /** Load the outgoing song into P2 and park it (paused, silent) a little *before* the fade-start
     *  position, so the region straddling the fade start is fully decoded ahead of time. Parking
     *  exactly on the fade start left the fire-time alignment seek landing at the edge of the
     *  buffer, which is what made the hand-off audible; the lead-in keeps that seek mid-buffer. */
    private fun armCrossfade(outgoing: MediaItem, duration: Long) {
        val uri = outgoing.localConfiguration?.uri ?: return
        crossfadeArmed = true
        crossfadePlayer.setMediaItem(MediaItem.fromUri(uri))
        crossfadePlayer.prepare()
        crossfadePlayer.seekTo((duration - CROSSFADE_MS - CROSSFADE_LEADIN_MS).coerceAtLeast(0))
        crossfadePlayer.volume = 0f
        crossfadePlayer.playWhenReady = false
    }

    /** Throw away an armed-but-unused preload (user seeked away, skipped, or paused out of range). */
    private fun disarmCrossfade() {
        crossfadeArmed = false
        crossfadePlayer.playWhenReady = false
        crossfadePlayer.stop()
        crossfadePlayer.clearMediaItems()
    }

    private fun startCrossfade(outgoing: MediaItem) {
        val uri = outgoing.localConfiguration?.uri ?: return
        crossfading = true

        if (!crossfadeArmed) {
            // Not pre-buffered (e.g. a song shorter than the preload window) — load P2 now.
            crossfadePlayer.setMediaItem(MediaItem.fromUri(uri))
            crossfadePlayer.prepare()
        }
        // P2 picks the outgoing song up exactly where P1 is (a fast in-buffer seek when armed),
        // at full volume, and will fade down.
        crossfadePlayer.seekTo(player.currentPosition)
        crossfadePlayer.volume = 1f
        crossfadePlayer.playWhenReady = true
        crossfadeArmed = false

        // P1 jumps to the next song now (a fraction of a second early), silent, and fades up.
        // ExoPlayer keeps the next window pre-buffered, so this advance is gapless. The queue
        // and now-playing metadata flip here — that's the leading edge of the crossfade.
        selfCrossfadeTransition = true
        player.volume = 0f
        player.seekToNextMediaItem()

        crossfadeJob = mainScope.launch {
            val steps = (CROSSFADE_MS / CROSSFADE_TICK_MS).toInt()
            for (i in 1..steps) {
                delay(CROSSFADE_TICK_MS)
                val fraction = i.toFloat() / steps
                // Equal-power (constant-loudness) curve: sin²+cos²=1, so the two songs sum to a
                // steady perceived level through the dissolve instead of the mid-point dip a
                // linear fade gives.
                val angle = fraction * (Math.PI.toFloat() / 2f)
                player.volume = kotlin.math.sin(angle)             // incoming fades in
                crossfadePlayer.volume = kotlin.math.cos(angle)    // outgoing fades out
            }
            finishCrossfade()
        }
    }

    /** Restore full volume on P1 and tear down P2. Safe to call whether or not a fade is running. */
    private fun finishCrossfade() {
        crossfadeJob = null
        crossfading = false
        crossfadeArmed = false
        player.volume = 1f
        crossfadePlayer.volume = 0f
        crossfadePlayer.stop()
        crossfadePlayer.clearMediaItems()
    }

    /** Abort an in-flight crossfade (a manual skip landed mid-fade). */
    private fun cancelCrossfade() {
        if (!crossfading) return
        crossfadeJob?.cancel()
        finishCrossfade()
    }

    // ---- Car now-playing artwork slideshow -----------------------------------

    /** The media item the slideshow is currently cycling for — detects track changes. */
    @Volatile private var slideshowMediaId: String? = null

    /**
     * The now-playing artwork slideshow. Each track *leads* with its own artwork — the album
     * pic for a local song, the station favicon for radio — and only then rotates into random
     * gallery photos. When the track changes we restore its real artwork first (a prior photo
     * may have overwritten this queue slot) and hold it for one interval before the photos
     * resume. With no gallery photos the real artwork simply stays up.
     */
    private fun startSlideshowLoop() {
        mainScope.launch {
            while (isActive) {
                delay(SLIDESHOW_INTERVAL_MS)
                if (!player.isPlaying) continue
                val idx = player.currentMediaItemIndex
                val item = player.currentMediaItem ?: continue

                if (item.mediaId != slideshowMediaId) {
                    // New track: show its own album art / station artwork before any photo.
                    slideshowMediaId = item.mediaId
                    val original = originalArtworkFor(item)
                    if (item.mediaMetadata.artworkUri != original) {
                        setArtwork(idx, item, original)
                    }
                    continue
                }

                val photo = slideshow.next() ?: continue
                setArtwork(idx, item, photo)
            }
        }
    }

    /** The track's own artwork (album pic or station favicon), independent of any slideshow photo. */
    private fun originalArtworkFor(item: MediaItem): Uri? {
        val id = item.mediaId
        return when {
            id.startsWith("song:") -> MusicLibrary.getTrackByMediaId(id)?.albumArtUri
            id.startsWith("radio:") ->
                RadioBrowser.stationByMediaId(id, RadioFavorites.favorites.value)
                    ?.favicon?.let { runCatching { Uri.parse(it) }.getOrNull() }
            else -> null
        }
    }

    /** Swap only the artwork of the item at [idx] — same media URI, no audio interruption. */
    private fun setArtwork(idx: Int, item: MediaItem, artwork: Uri?) {
        val updated = item.buildUpon()
            .setMediaMetadata(item.mediaMetadata.buildUpon().setArtworkUri(artwork).build())
            .build()
        player.replaceMediaItem(idx, updated)
    }

    // ---- Browse tree ----------------------------------------------------------

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val available = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .apply {
                    carShortcuts.forEach { add(SessionCommand(ACTION_SHORTCUT_PREFIX + it.letter, Bundle.EMPTY)) }
                    add(SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY))
                    add(SessionCommand(ACTION_PLAY_RADIO, Bundle.EMPTY))
                }
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(available)
                .setCustomLayout(buildCustomLayout())
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            val action = customCommand.customAction
            when {
                action.startsWith(ACTION_SHORTCUT_PREFIX) -> {
                    val letter = action.removePrefix(ACTION_SHORTCUT_PREFIX)
                    mainScope.launch { playTracks(shortcutTracks(letter)) }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                action == ACTION_TOGGLE_FAVORITE -> {
                    toggleCurrentFavorite()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                action == ACTION_PLAY_RADIO -> {
                    // Switch playback to the radio list (favorites first). browseStations touches
                    // the network, so resolve on IO and hop back to Main to drive the player.
                    ioScope.launch {
                        val stations = RadioBrowser.browseStations(RadioFavorites.favorites.value)
                        mainScope.launch { playRadio(stations) }
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(
            LibraryResult.ofItem(
                browsableItem(ID_ROOT, "Phoenix", null, styleExtras()),
                params,
            )
        )

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = supply {
            MusicLibrary.ensureLoaded(this@PlaybackService)
            val item = when {
                mediaId == ID_ROOT -> browsableItem(ID_ROOT, "Phoenix", null, styleExtras(true, true))
                mediaId == ID_SHUFFLE_ALL -> playableTextTile(ID_SHUFFLE_ALL, "Shuffle all", null)
                mediaId.startsWith("$CMD_SHORTCUT_PREFIX|") -> {
                    val letter = mediaId.substringAfterLast('|')
                    val s = carShortcuts.firstOrNull { it.letter == letter }
                    if (s != null) playableTextTile(mediaId, s.letter, s.label) else null
                }
                mediaId == TAB_PLAYLISTS -> browsableItem(TAB_PLAYLISTS, "Playlists", null, styleExtras(true, true))
                mediaId == TAB_RADIO -> browsableItem(TAB_RADIO, "Radio", null, styleExtras(true, true))
                mediaId.startsWith("song:") -> MusicLibrary.getTrackByMediaId(mediaId)?.let { songItem(it) }
                mediaId.startsWith("radio:") ->
                    RadioBrowser.stationByMediaId(mediaId, RadioFavorites.favorites.value)?.let { radioItem(it) }
                mediaId.startsWith("folder:") -> {
                    val fid = mediaId.removePrefix("folder:")
                    browsableItem(mediaId, fid.substringAfterLast('/'), null, styleExtras(true, false))
                }
                else -> null
            }
            // Graceful fallback instead of a crashing/empty node for an unknown id.
            val resolved = item ?: browsableItem(mediaId, "Unavailable", null, styleExtras(false, false))
            LibraryResult.ofItem(resolved, null)
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = supply {
            MusicLibrary.ensureLoaded(this@PlaybackService)
            val children: List<MediaItem> = when {
                parentId == ID_ROOT -> {
                    // Invalidate both tab subtrees whenever the tab strip is (re)shown, so opening
                    // a tab always re-fires its onGetChildren and re-registers the search context.
                    // Auto can otherwise serve a cached tab without re-calling us, leaving
                    // lastBrowsedParent pointing at a stale view and search hitting the wrong corpus.
                    mainScope.launch {
                        session.notifyChildrenChanged(TAB_PLAYLISTS, Int.MAX_VALUE, null)
                        session.notifyChildrenChanged(TAB_RADIO, Int.MAX_VALUE, null)
                    }
                    rootTabs()
                }
                parentId == TAB_PLAYLISTS -> { lastBrowsedParent = TAB_PLAYLISTS; playlistsTabChildren() }
                parentId == TAB_RADIO -> { lastBrowsedParent = TAB_RADIO; radioTabChildren() }
                parentId.startsWith("folder:") -> {
                    lastBrowsedParent = parentId
                    val fid = parentId.removePrefix("folder:")
                    folderSongList(MusicLibrary.tracksInFolder(fid))
                }
                else -> emptyList()
            }
            LibraryResult.ofItemList(ImmutableList.copyOf(children), params)
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> = supply {
            val items = contextSearchItems(query)
            lastSearchQuery = query
            lastSearchItems = items
            session.notifySearchResultChanged(browser, query, items.size, params)
            LibraryResult.ofVoid()
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = supply {
            val items = if (query == lastSearchQuery) lastSearchItems else contextSearchItems(query)
            val from = (page * pageSize).coerceIn(0, items.size)
            val to = (from + pageSize).coerceIn(from, items.size)
            LibraryResult.ofItemList(ImmutableList.copyOf(items.subList(from, to)), params)
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = supply {
            MusicLibrary.ensureLoaded(this@PlaybackService)
            if (mediaItems.size > 1) mediaItems.map { resolvePlayableItem(it) }.toMutableList()
            else resolveForPlayback(mediaItems).first.toMutableList()
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = supply {
            MusicLibrary.ensureLoaded(this@PlaybackService)
            if (mediaItems.size > 1) {
                // A fully-built queue from the phone UI. A controller can't carry local URIs to
                // the session, so re-attach each item's URI from its media id — but keep the
                // caller's exact order and start index instead of collapsing to the first item's
                // folder (which is what made a tapped search/browse result play the wrong song).
                val resolved = mediaItems.map { resolvePlayableItem(it) }
                MediaSession.MediaItemsWithStartPosition(
                    resolved,
                    startIndex.coerceIn(0, resolved.lastIndex),
                    startPositionMs.coerceAtLeast(0),
                )
            } else {
                // A single tapped browse item (the car) — expand it into its full queue at the
                // right index (e.g. a folder, a letter shortcut, or one song within its folder).
                val (items, index) = resolveForPlayback(mediaItems)
                MediaSession.MediaItemsWithStartPosition(items, index, C.TIME_UNSET)
            }
        }
    }

    /** Re-attach a playable URI to a controller-sent item by resolving its media id (a controller
     *  can't send local content URIs across the session, only ids + metadata). */
    private fun resolvePlayableItem(item: MediaItem): MediaItem {
        val id = item.mediaId
        return when {
            id.startsWith("song:") -> MusicLibrary.getTrackByMediaId(id)?.let { songItem(it) } ?: item
            id.startsWith("radio:") ->
                RadioBrowser.stationByMediaId(id, RadioFavorites.favorites.value)?.let { radioItem(it) } ?: item
            else -> item
        }
    }

    // ---- Child builders -------------------------------------------------------

    /** Android Auto's root tab strip: just Playlists and Radio. No letter tiles here — keeping
     *  them out is what removes Auto's overflow "More" tab (the letters now live inside each
     *  folder's song list instead). */
    private fun rootTabs(): List<MediaItem> = buildList {
        add(browsableItem(TAB_PLAYLISTS, "Playlists", null, styleExtras()))
        add(browsableItem(TAB_RADIO, "Radio", null, styleExtras()))
    }

    /**
     * The Playlists tab: the flat list of music folders, exactly the set the phone shows in its
     * root "Folders" list. Tapping a folder browses into its song list.
     */
    private fun playlistsTabChildren(): List<MediaItem> =
        MusicLibrary.browseFolders().map { folder ->
            browsableItem("folder:${folder.id}", folder.name, null, styleExtras())
        }

    /** The Radio tab: favorites first, then the top stations — a plain vertical list. */
    private fun radioTabChildren(): List<MediaItem> {
        val favs = RadioFavorites.favorites.value
        return RadioBrowser.browseStations(favs).map { radioItem(it) }
    }

    /**
     * A folder's song view (the back-target from now-playing): the favorite-playlist letter
     * shortcuts (L / A / H / Au) first, then this folder's songs in the current sort order.
     * Tapping a song plays this folder as a queue starting at it; tapping a letter shuffle-plays
     * that shortcut's folder — same behavior as the phone's letter bar.
     */
    private fun folderSongList(tracks: List<Track>): List<MediaItem> = buildList {
        addAll(letterTiles())
        addAll(tracks.map { songItem(it) })
    }

    private fun letterTiles(): List<MediaItem> = carShortcuts.mapNotNull { s ->
        // A letter whose folder isn't on the device is hidden.
        // The letter is the TITLE text (not a drawn glyph) so it renders in the same
        // template font as the song rows. Folder name is the subtitle.
        if (MusicLibrary.folderIdByName(s.folderName) == null) null
        else playableTextTile("$CMD_SHORTCUT_PREFIX|${s.letter}", s.letter, s.label)
    }

    // ---- Playback resolution --------------------------------------------------

    /** Expand a tapped browse item (virtual or real) into a ready-to-play queue + start index. */
    private fun resolveForPlayback(items: List<MediaItem>): Pair<List<MediaItem>, Int> {
        MusicLibrary.ensureLoaded(this)
        val id = items.firstOrNull()?.mediaId ?: return emptyList<MediaItem>() to 0
        return when {
            id == ID_SHUFFLE_ALL ->
                MusicLibrary.allTracks().shuffled().map { songItem(it) } to 0
            id.startsWith("$CMD_SHORTCUT_PREFIX|") ->
                shortcutTracks(id.substringAfterLast('|')).map { songItem(it) } to 0
            id.startsWith("folder:") ->
                MusicLibrary.playableTracksFor(id.removePrefix("folder:")).map { songItem(it) } to 0
            id.startsWith("song:") -> {
                val track = MusicLibrary.getTrackByMediaId(id)
                if (track == null) items to 0
                else {
                    val queue = MusicLibrary.tracksInFolder(track.relativePath.trimEnd('/'))
                    val start = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                    queue.map { songItem(it) } to start
                }
            }
            id.startsWith("radio:") -> {
                val favs = RadioFavorites.favorites.value
                val station = RadioBrowser.stationByMediaId(id, favs)
                    ?: return items to 0
                val queue = RadioBrowser.queueContaining(station, favs)
                val start = queue.indexOfFirst { it.uuid == station.uuid }.coerceAtLeast(0)
                queue.map { radioItem(it) } to start
            }
            else -> items to 0
        }
    }

    /** Tracks for a letter shortcut, shuffled — every press starts a fresh random order. */
    private fun shortcutTracks(letter: String): List<Track> {
        val shortcut = carShortcuts.firstOrNull { it.letter == letter } ?: return emptyList()
        val folderId = MusicLibrary.folderIdByName(shortcut.folderName) ?: return emptyList()
        return MusicLibrary.playableTracksFor(folderId).shuffled()
    }

    private fun playTracks(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        player.setMediaItems(tracks.map { songItem(it) })
        player.prepare()
        player.play()
    }

    private fun playRadio(stations: List<RadioStation>) {
        if (stations.isEmpty()) return
        player.setMediaItems(stations.map { radioItem(it) })
        player.prepare()
        player.play()
    }

    private fun toggleCurrentFavorite() {
        val id = player.currentMediaItem?.mediaId ?: return
        if (!id.startsWith("radio:")) return
        val station = RadioBrowser.stationByMediaId(id, RadioFavorites.favorites.value) ?: return
        RadioFavorites.toggle(station)
    }

    // ---- Context-scoped search ------------------------------------------------

    /**
     * Android Auto has one global search box and never tells us the folder, so the corpus is
     * decided by the list currently on screen — never by what's playing. In the Radio list a
     * search hits the online station directory; anywhere in music it searches the whole phone
     * library (matching the phone, where song search spans every folder rather than being pinned
     * to the one you last opened). So a search while viewing the Radio list finds stations even
     * if a song is playing, and a search while viewing a playlist finds songs even if a station
     * is playing. Never a mixed song+radio dump.
     */
    private fun contextSearchItems(query: String): List<MediaItem> {
        MusicLibrary.ensureLoaded(this)
        return if (lastBrowsedParent == TAB_RADIO) {
            RadioBrowser.search(query).map { radioItem(it) }
        } else {
            MusicLibrary.search(query, null).map { songItem(it) }
        }
    }

    // ---- Now-playing custom layout (letter buttons + radio heart) -------------

    private fun buildCustomLayout(): ImmutableList<CommandButton> {
        val buttons = ArrayList<CommandButton>()
        val current = player.currentMediaItem?.mediaId
        if (current != null && current.startsWith("radio:")) {
            val fav = RadioFavorites.isFavorite(current.removePrefix("radio:"))
            buttons += CommandButton.Builder()
                .setDisplayName(if (fav) "Unfavorite" else "Favorite")
                .setIconResId(if (fav) R.drawable.ic_favorite else R.drawable.ic_favorite_border)
                .setSessionCommand(SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY))
                .build()
        }
        carShortcuts.forEach { s ->
            buttons += CommandButton.Builder()
                .setDisplayName(s.letter)
                .setIconResId(s.iconRes)
                // Also hand Auto an android.resource:// URI for the same drawable. The head
                // unit resolves the icon itself from this URI (no cross-process resId lookup),
                // which is what keeps the letter glyph from degrading to the "!" placeholder.
                .setIconUri(resourceUri(s.iconRes))
                .setSessionCommand(SessionCommand(ACTION_SHORTCUT_PREFIX + s.letter, Bundle.EMPTY))
                .build()
        }
        // Radio always available in the now-playing controls, mirroring the phone's Radio
        // button. Tapping switches playback to the radio list (favorites first).
        buttons += CommandButton.Builder()
            .setDisplayName("Radio")
            .setIconResId(R.drawable.ic_radio)
            .setIconUri(resourceUri(R.drawable.ic_radio))
            .setSessionCommand(SessionCommand(ACTION_PLAY_RADIO, Bundle.EMPTY))
            .build()
        return ImmutableList.copyOf(buttons)
    }

    // ---- MediaItem builders ---------------------------------------------------

    private fun browsableItem(id: String, title: String, iconRes: Int?, extras: Bundle): MediaItem {
        val meta = MediaMetadata.Builder()
            .setTitle(title)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setExtras(extras)
            .apply { iconRes?.let { setArtworkUri(resourceUri(it)) } }
            .build()
        return MediaItem.Builder().setMediaId(id).setMediaMetadata(meta).build()
    }

    /** Playable tile with no artwork — title/subtitle render in the head unit's template font. */
    private fun playableTextTile(id: String, title: String, subtitle: String?): MediaItem {
        val meta = MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setArtist(subtitle)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .build()
        return MediaItem.Builder().setMediaId(id).setMediaMetadata(meta).build()
    }

    private fun songItem(track: Track): MediaItem {
        val meta = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setArtworkUri(track.albumArtUri)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .build()
        return MediaItem.Builder()
            .setMediaId(track.mediaId)
            .setUri(track.uri)
            .setMediaMetadata(meta)
            .build()
    }

    private fun radioItem(station: RadioStation): MediaItem {
        val meta = MediaMetadata.Builder()
            .setTitle(station.name)
            .setArtist(station.country)
            .setArtworkUri(station.favicon?.let { runCatching { Uri.parse(it) }.getOrNull() })
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .build()
        return MediaItem.Builder()
            .setMediaId(station.mediaId)
            .setUri(station.streamUrl)
            .setMediaMetadata(meta)
            .build()
    }

    /**
     * The whole car app is a vertical list — no large grid tiles anywhere. Besides matching the
     * phone's list layout, list rows don't render the big broken-artwork "!" placeholder that
     * grid tiles show for a song/station/shortcut without loadable album art. The old
     * browsable/playable-grid flags are kept for call-site compatibility but ignored.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun styleExtras(browsableGrid: Boolean = false, playableGrid: Boolean = false): Bundle =
        Bundle().apply {
            putInt(MusicLibrary.CONTENT_STYLE_BROWSABLE_HINT, MusicLibrary.CONTENT_STYLE_LIST)
            putInt(MusicLibrary.CONTENT_STYLE_PLAYABLE_HINT, MusicLibrary.CONTENT_STYLE_LIST)
        }

    private fun resourceUri(resId: Int): Uri =
        Uri.parse("android.resource://$packageName/$resId")

    private fun <T> supply(block: () -> T): ListenableFuture<T> =
        Futures.submit(Callable { block() }, browseExecutor)

    // ---- Constants ------------------------------------------------------------

    /** A car letter shortcut → the folder it jumps to. */
    private data class CarShortcut(val letter: String, val label: String, val folderName: String, val iconRes: Int)

    private val carShortcuts = listOf(
        CarShortcut("L", "Leni", "Leni", R.drawable.ic_letter_l),
        CarShortcut("A", "Action", "Action", R.drawable.ic_letter_a),
        CarShortcut("H", "Hideout", "Hideout", R.drawable.ic_letter_h),
        CarShortcut("Au", "Audiobooks", "Audiobooks", R.drawable.ic_letter_au),
    )

    companion object {
        const val ID_ROOT = "root"
        const val TAB_PLAYLISTS = "tab_playlists"
        const val TAB_RADIO = "tab_radio"
        const val ID_SHUFFLE_ALL = "cmd:shuffle_all"
        const val CMD_SHORTCUT_PREFIX = "cmd:shortcut"

        const val ACTION_SHORTCUT_PREFIX = "com.phoenix.SHORTCUT."
        const val ACTION_TOGGLE_FAVORITE = "com.phoenix.TOGGLE_FAVORITE"
        const val ACTION_PLAY_RADIO = "com.phoenix.PLAY_RADIO"

        const val SLIDESHOW_INTERVAL_MS = 12_000L

        /** Crossfade length between songs, and the fade/poll granularity. A long 7s dissolve; the
         *  100ms poll keeps the fire point close to the exact 7s-from-end mark (so P2's alignment
         *  seek is sub-100ms and stays inside its pre-buffered region), and the 50ms tick gives a
         *  ~140-step ramp for a smooth blend. */
        const val CROSSFADE_MS = 7_000L
        const val CROSSFADE_TICK_MS = 50L
        const val CROSSFADE_POLL_MS = 100L

        /** How far ahead of the fade to pre-buffer the outgoing tail into P2 (removes the stall).
         *  Generous lead so P2 is fully buffered and parked well before the fade fires. */
        const val CROSSFADE_PRELOAD_MS = 4_000L

        /** How far *before* the fade-start to park P2, so the region around the fade start is
         *  already decoded and the fire-time alignment seek lands mid-buffer (click-free start). */
        const val CROSSFADE_LEADIN_MS = 700L
    }
}
