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

    /** Set right before our own [ExoPlayer.seekToNextMediaItem] so the transition listener
     *  can tell the crossfade's own queue advance apart from a user skip. */
    private var selfCrossfadeTransition = false

    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val browseExecutor = Executors.newSingleThreadExecutor()

    private val slideshow = GallerySlideshow()

    /** The last node the user browsed on the car — used to scope Auto's global search. */
    @Volatile private var lastBrowsedParent: String = ID_ROOT

    private var lastSearchQuery: String = ""
    private var lastSearchItems: List<MediaItem> = emptyList()

    /** Mirrors the player's shuffle mode so browse tiles (built off the main thread) can read it. */
    @Volatile private var shuffleOn: Boolean = false

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
                    // ran) must not leave P1 half-faded or P2 playing a stale tail.
                    cancelCrossfade()
                }
            }

            // Keep the outgoing tail in lock-step with the user pausing/resuming mid-crossfade.
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (crossfading) crossfadePlayer.playWhenReady = isPlaying
            }

            // Shuffle can be toggled from the phone, this service's browse tile, or Auto's
            // now-playing template. Mirror it and re-list the song views so their "Shuffle:
            // On/Off" tile reflects the shared state wherever it was changed.
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                shuffleOn = shuffleModeEnabled
                session.notifyChildrenChanged(TAB_PLAYLISTS, Int.MAX_VALUE, null)
                MusicLibrary.browsableFolderIds().forEach {
                    session.notifyChildrenChanged("folder:$it", Int.MAX_VALUE, null)
                }
            }
        })

        // Live Android Auto refresh when the sort changes on the phone.
        mainScope.launch {
            MusicLibrary.sortMode.drop(1).collect {
                // Re-fetch every browsable song list so a connected car reflects the new order.
                session.notifyChildrenChanged(TAB_PLAYLISTS, Int.MAX_VALUE, null)
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
        if (crossfading || !player.isPlaying || !player.hasNextMediaItem()) return
        // Songs only. Radio is live (no real duration) and must not crossfade.
        val current = player.currentMediaItem ?: return
        if (current.mediaId.startsWith("radio:")) return
        val nextIndex = player.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET || player.getMediaItemAt(nextIndex).mediaId.startsWith("radio:")) return

        val duration = player.duration
        if (duration == C.TIME_UNSET || duration <= 0) return
        val remaining = duration - player.currentPosition
        // A song shorter than the crossfade window still crossfades once, right at its start's tail.
        if (remaining in 1..CROSSFADE_MS) startCrossfade(current)
    }

    private fun startCrossfade(outgoing: MediaItem) {
        val uri = outgoing.localConfiguration?.uri ?: return
        val outgoingPosition = player.currentPosition
        crossfading = true

        // P2 picks the outgoing song up where P1 leaves it, at full volume, and will fade down.
        crossfadePlayer.setMediaItem(MediaItem.fromUri(uri))
        crossfadePlayer.prepare()
        crossfadePlayer.seekTo(outgoingPosition)
        crossfadePlayer.volume = 1f
        crossfadePlayer.playWhenReady = true

        // P1 jumps to the next song now (up to 5s early), silent, and fades up. The queue and
        // now-playing metadata flip here — that's the leading edge of the crossfade.
        selfCrossfadeTransition = true
        player.volume = 0f
        player.seekToNextMediaItem()

        crossfadeJob = mainScope.launch {
            val steps = (CROSSFADE_MS / CROSSFADE_TICK_MS).toInt()
            for (i in 1..steps) {
                delay(CROSSFADE_TICK_MS)
                val fraction = i.toFloat() / steps
                player.volume = fraction            // incoming fades in
                crossfadePlayer.volume = 1f - fraction  // outgoing fades out
            }
            finishCrossfade()
        }
    }

    /** Restore full volume on P1 and tear down P2. Safe to call whether or not a fade is running. */
    private fun finishCrossfade() {
        crossfadeJob = null
        crossfading = false
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
                // playableGrid=true so the root-level letter tiles render as a row of tiles
                // alongside the Playlists / All Songs / Radio tabs.
                browsableItem(ID_ROOT, "Phoenix", null, styleExtras(browsableGrid = true, playableGrid = true)),
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
                parentId == ID_ROOT -> rootTabs()
                parentId == TAB_PLAYLISTS -> { lastBrowsedParent = TAB_PLAYLISTS; playlistsTabChildren() }
                parentId == TAB_RADIO -> { lastBrowsedParent = TAB_RADIO; radioTabChildren() }
                parentId.startsWith(CMD_SORT_PREFIX) -> {
                    val (mode, target) = parseSortCommand(parentId)
                    MusicLibrary.setSortMode(mode)
                    lastBrowsedParent = target
                    childrenForTarget(target)
                }
                parentId.startsWith(CMD_SHUFFLE_PREFIX) -> {
                    // Flip the shared shuffle mode on the main thread (player is main-only). The
                    // shuffle listener re-lists this node, so the returned tile updates to match.
                    val target = parentId.substringAfter('|')
                    mainScope.launch { player.shuffleModeEnabled = !player.shuffleModeEnabled }
                    lastBrowsedParent = target
                    childrenForTarget(target)
                }
                parentId.startsWith("folder:") -> {
                    lastBrowsedParent = parentId
                    val fid = parentId.removePrefix("folder:")
                    songListWithSortTiles(parentId, MusicLibrary.tracksInFolder(fid))
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
            resolveForPlayback(mediaItems).first.toMutableList()
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = supply {
            val (items, index) = resolveForPlayback(mediaItems)
            MediaSession.MediaItemsWithStartPosition(items, index, C.TIME_UNSET)
        }
    }

    // ---- Child builders -------------------------------------------------------

    private fun rootTabs(): List<MediaItem> = buildList {
        // The browsable tabs form Android Auto's root tab strip...
        add(browsableItem(TAB_PLAYLISTS, "Playlists", null, styleExtras(true, true)))
        add(browsableItem(TAB_RADIO, "Radio", null, styleExtras(true, true)))
        // ...and the L/A/H/Au shortcuts sit at the same root level (as text tiles),
        // reachable without first opening Playlists. Same shuffle-play behavior as elsewhere.
        addAll(letterTiles())
    }

    /**
     * The shared shortcut row shown at the top of the Playlists and All Songs tabs:
     * L / A / H / Au (shuffle-play the folder) plus a Radio tile (jumps to the Radio list).
     * Mirrors the phone's LetterShortcutBar. The Radio tab omits its own Radio tile.
     */
    private fun shortcutRow(): List<MediaItem> = buildList {
        addAll(letterTiles())
        // Text tile (no icon) so "Radio" renders in the same template font as the tabs.
        add(browsableTextTile(TAB_RADIO, "Radio", styleExtras(true, true)))
    }

    private fun playlistsTabChildren(): List<MediaItem> = buildList {
        addAll(shortcutRow())
        add(shuffleAllItem())
        MusicLibrary.browseFolders().forEach { folder ->
            add(browsableItem("folder:${folder.id}", folder.name, null, styleExtras(true, false)))
        }
    }

    private fun radioTabChildren(): List<MediaItem> = buildList {
        // No Radio tile here — you're already in Radio (matches the phone Radio bar).
        addAll(letterTiles())
        val favs = RadioFavorites.favorites.value
        RadioBrowser.browseStations(favs).forEach { add(radioItem(it)) }
    }

    /** Song list preceded by the row of four sort tiles + the shuffle toggle (car-only controls). */
    private fun songListWithSortTiles(parentBrowseId: String, tracks: List<Track>): List<MediaItem> =
        buildList {
            if (tracks.isNotEmpty()) {
                add(sortTile(MusicLibrary.SortMode.DATE_ASC, parentBrowseId))
                add(sortTile(MusicLibrary.SortMode.DATE_DESC, parentBrowseId))
                add(sortTile(MusicLibrary.SortMode.NAME_ASC, parentBrowseId))
                add(sortTile(MusicLibrary.SortMode.NAME_DESC, parentBrowseId))
                add(shuffleTile(parentBrowseId))
            }
            addAll(tracks.map { songItem(it) })
        }

    private fun childrenForTarget(target: String): List<MediaItem> = when {
        target.startsWith("folder:") ->
            songListWithSortTiles(target, MusicLibrary.tracksInFolder(target.removePrefix("folder:")))
        else -> emptyList()
    }

    private fun letterTiles(): List<MediaItem> = carShortcuts.mapNotNull { s ->
        // A letter whose folder isn't on the device is hidden.
        // The letter is the TITLE text (not a drawn glyph) so it renders in the same
        // template font as the Playlists / Radio tab labels. Folder name is the subtitle.
        if (MusicLibrary.folderIdByName(s.folderName) == null) null
        else playableTextTile("$CMD_SHORTCUT_PREFIX|${s.letter}", s.letter, s.label)
    }

    // Text-only, matching the L/A/H/Au and sort tiles' template font/size.
    private fun shuffleAllItem(): MediaItem = playableTextTile(ID_SHUFFLE_ALL, "Shuffle all", null)

    private fun sortTile(mode: MusicLibrary.SortMode, parentBrowseId: String): MediaItem {
        val label = when (mode) {
            MusicLibrary.SortMode.DATE_ASC -> "Date ↑ oldest"
            MusicLibrary.SortMode.DATE_DESC -> "Date ↓ newest"
            MusicLibrary.SortMode.NAME_ASC -> "Name A–Z"
            MusicLibrary.SortMode.NAME_DESC -> "Name Z–A"
        }
        // Text-only browsable tile — same render path (and template font/size) as the
        // L/A/H/Au and Radio tiles. Browsable so tapping re-enters onGetChildren, applies
        // the sort, and re-lists the current folder.
        return browsableTextTile("$CMD_SORT_PREFIX|${mode.name}|$parentBrowseId", label, styleExtras(true, false))
    }

    /**
     * The car's shuffle toggle, shown atop each folder's song list. Browsable so tapping
     * re-enters onGetChildren, flips the shared shuffle mode, and re-lists the folder with the
     * label updated. When off the list plays in the current sort order; when on, ExoPlayer
     * shuffles playback of the same queue.
     */
    private fun shuffleTile(parentBrowseId: String): MediaItem {
        val label = if (shuffleOn) "Shuffle: On" else "Shuffle: Off"
        return browsableTextTile("$CMD_SHUFFLE_PREFIX|$parentBrowseId", label, styleExtras(true, false))
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
     * Android Auto has one global search box and never tells us the folder, so search is
     * scoped to the last-browsed node: radio stations in the Radio tab, otherwise only the
     * current folder's songs (All Songs = everything). Never a mixed song+radio dump.
     */
    private fun contextSearchItems(query: String): List<MediaItem> {
        MusicLibrary.ensureLoaded(this)
        return if (lastBrowsedParent == TAB_RADIO) {
            RadioBrowser.search(query).map { radioItem(it) }
        } else {
            val folderId = if (lastBrowsedParent.startsWith("folder:"))
                lastBrowsedParent.removePrefix("folder:") else null
            MusicLibrary.search(query, folderId).map { songItem(it) }
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

    /** Browsable tile with no artwork — title renders in the head unit's template font. */
    private fun browsableTextTile(id: String, title: String, extras: Bundle): MediaItem {
        val meta = MediaMetadata.Builder()
            .setTitle(title)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setExtras(extras)
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

    private fun styleExtras(browsableGrid: Boolean, playableGrid: Boolean): Bundle = Bundle().apply {
        putInt(
            MusicLibrary.CONTENT_STYLE_BROWSABLE_HINT,
            if (browsableGrid) MusicLibrary.CONTENT_STYLE_GRID else MusicLibrary.CONTENT_STYLE_LIST,
        )
        putInt(
            MusicLibrary.CONTENT_STYLE_PLAYABLE_HINT,
            if (playableGrid) MusicLibrary.CONTENT_STYLE_GRID else MusicLibrary.CONTENT_STYLE_LIST,
        )
    }

    private fun resourceUri(resId: Int): Uri =
        Uri.parse("android.resource://$packageName/$resId")

    private fun parseSortCommand(id: String): Pair<MusicLibrary.SortMode, String> {
        // cmd:sort|MODE|target  (target may itself be "folder:Some/Path")
        val parts = id.split("|")
        val mode = runCatching { MusicLibrary.SortMode.valueOf(parts[1]) }.getOrDefault(MusicLibrary.SortMode.NAME_ASC)
        val target = parts.drop(2).joinToString("|")
        return mode to target
    }

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
        const val TAB_SONGS = "tab_songs"
        const val TAB_RADIO = "tab_radio"
        const val ID_SHUFFLE_ALL = "cmd:shuffle_all"
        const val CMD_SORT_PREFIX = "cmd:sort"
        const val CMD_SHUFFLE_PREFIX = "cmd:shuffle_toggle"
        const val CMD_SHORTCUT_PREFIX = "cmd:shortcut"

        const val ACTION_SHORTCUT_PREFIX = "com.phoenix.SHORTCUT."
        const val ACTION_TOGGLE_FAVORITE = "com.phoenix.TOGGLE_FAVORITE"
        const val ACTION_PLAY_RADIO = "com.phoenix.PLAY_RADIO"

        const val SLIDESHOW_INTERVAL_MS = 12_000L

        /** Crossfade length between songs, and the fade/poll granularity. */
        const val CROSSFADE_MS = 5_000L
        const val CROSSFADE_TICK_MS = 50L
        const val CROSSFADE_POLL_MS = 200L
    }
}
