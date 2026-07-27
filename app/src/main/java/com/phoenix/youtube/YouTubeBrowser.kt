package com.phoenix.youtube

import android.net.Uri
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * Thin client over NewPipeExtractor for the two things this app needs from YouTube: listing a
 * playlist's tracks, and turning a videoId into a playable audio-stream URL. Everything here does
 * blocking network I/O and MUST be called off the main thread (the playback service's IO scope or
 * the ResolvingDataSource's load thread).
 *
 * Caveats worth remembering (they're the reason this isn't just "the YouTube API"):
 *  - It talks to YouTube's private InnerTube API, which violates YouTube's Terms of Service. Fine
 *    for a personal device; not distributable.
 *  - It's inherently fragile: YouTube periodically changes its internal API / signature logic,
 *    which breaks extraction until NewPipeExtractor is bumped. Treat a version bump as routine
 *    maintenance, not a red flag.
 *
 * Stream URLs are short-lived and IP-bound, so they're resolved just-in-time (never persisted) and
 * cached only briefly. Playlist track lists and per-video metadata are cached so a played track
 * stays resolvable by its media id after the browse caches would otherwise be gone.
 */
object YouTubeBrowser {

    /** Audio URLs YouTube hands out are valid ~6h; refresh well before that. */
    private const val STREAM_URL_TTL_MS = 4L * 60 * 60 * 1000

    /** Safety cap so a pathological playlist can't page forever. */
    private const val MAX_TRACKS = 400

    @Volatile private var initialized = false

    // videoId -> track metadata, for resolving a media id back to a displayable/playable item.
    private val trackCache = HashMap<String, YouTubeTrack>()
    // playlistId -> its ordered tracks, so a tapped track can queue with its siblings.
    private val playlistCache = LinkedHashMap<String, List<YouTubeTrack>>()
    // videoId -> (resolved url, expiry). Guards against re-resolving on every buffer.
    private val streamUrlCache = HashMap<String, Pair<String, Long>>()

    @Synchronized
    private fun ensureInit() {
        if (initialized) return
        NewPipe.init(YouTubeDownloader.build())
        initialized = true
    }

    // ---- Playlists ------------------------------------------------------------

    /**
     * Fetch a playlist's tracks (paged, capped at [MAX_TRACKS]). Cached by playlist id; pass
     * [forceRefresh] to re-fetch. Returns an empty list on any failure so the UI just shows an
     * empty playlist instead of crashing.
     */
    fun playlistTracks(playlistId: String, forceRefresh: Boolean = false): List<YouTubeTrack> {
        if (!forceRefresh) playlistCache[playlistId]?.let { return it }
        return try {
            ensureInit()
            val extractor = ServiceList.YouTube.getPlaylistExtractor(playlistUrl(playlistId))
            extractor.fetchPage()

            val tracks = ArrayList<YouTubeTrack>()
            var page = extractor.initialPage
            while (true) {
                page.items.forEach { item -> toTrack(item)?.let { tracks.add(it) } }
                if (tracks.size >= MAX_TRACKS || !page.hasNextPage()) break
                page = extractor.getPage(page.nextPage)
            }
            val result = tracks.take(MAX_TRACKS)
            playlistCache[playlistId] = result
            result.forEach { trackCache[it.videoId] = it }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** The playlist's display name (its own network call). Null when it can't be fetched. */
    fun playlistTitle(playlistId: String): String? = try {
        ensureInit()
        val extractor = ServiceList.YouTube.getPlaylistExtractor(playlistUrl(playlistId))
        extractor.fetchPage()
        extractor.name?.ifBlank { null }
    } catch (_: Exception) {
        null
    }

    /** The cached list a track belongs to (for building a play queue). Just the track itself when
     *  no cached playlist contains it. */
    fun queueContaining(videoId: String): List<YouTubeTrack> {
        playlistCache.values.firstOrNull { list -> list.any { it.videoId == videoId } }
            ?.let { return it }
        return trackCache[videoId]?.let { listOf(it) } ?: emptyList()
    }

    /** Resolve a "yt:videoId" media id back to its cached track metadata, if known. */
    fun trackByMediaId(mediaId: String): YouTubeTrack? =
        trackCache[mediaId.removePrefix("yt:")]

    // ---- Stream resolution ----------------------------------------------------

    /**
     * Resolve a videoId to a directly-playable audio-stream URL, cached until it nears expiry.
     * Called from the ResolvingDataSource on a playback load thread, so blocking is expected.
     * Returns null when extraction fails; the data source then leaves the yt:// URI in place,
     * which surfaces as a normal player error for that item rather than a crash.
     */
    fun resolveStreamUrl(videoId: String): String? {
        val now = System.currentTimeMillis()
        synchronized(streamUrlCache) {
            streamUrlCache[videoId]?.let { (url, expiry) -> if (now < expiry) return url }
        }
        return try {
            ensureInit()
            val extractor = ServiceList.YouTube.getStreamExtractor(watchUrl(videoId))
            extractor.fetchPage()
            val url = pickAudioStream(extractor.audioStreams)?.content ?: return null
            synchronized(streamUrlCache) {
                streamUrlCache[videoId] = url to (now + STREAM_URL_TTL_MS)
            }
            url
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Prefer m4a (widest ExoPlayer progressive compatibility) at the highest bitrate; otherwise
     * just the highest-bitrate audio stream available. Only direct-URL streams are usable here.
     */
    private fun pickAudioStream(streams: List<AudioStream>?): AudioStream? {
        val usable = streams?.filter { it.isUrl && !it.content.isNullOrBlank() } ?: return null
        if (usable.isEmpty()) return null
        val m4a = usable.filter { it.format == MediaFormat.M4A }
        return (m4a.ifEmpty { usable }).maxByOrNull { it.averageBitrate }
    }

    // ---- Input parsing --------------------------------------------------------

    /**
     * Pull a playlist id out of whatever the user pasted: a full watch/playlist URL (`list=...`),
     * a bare id, or a `music.youtube.com` link. Returns null if nothing playlist-shaped is found.
     */
    fun extractPlaylistId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        // A bare id (no scheme, no slash) — accept as-is.
        if (!trimmed.contains('/') && !trimmed.contains('?') && !trimmed.contains(' ')) {
            return trimmed
        }
        return runCatching { Uri.parse(trimmed).getQueryParameter("list") }.getOrNull()
            ?.ifBlank { null }
    }

    private fun toTrack(item: StreamInfoItem): YouTubeTrack? {
        val videoId = extractVideoId(item.url) ?: return null
        return YouTubeTrack(
            videoId = videoId,
            title = item.name?.ifBlank { null } ?: "Unknown track",
            artist = item.uploaderName?.ifBlank { null },
            durationSec = item.duration.coerceAtLeast(0),
            thumbnailUrl = bestThumbnail(item),
        )
    }

    private fun bestThumbnail(item: StreamInfoItem): String? =
        item.thumbnails.lastOrNull()?.url?.ifBlank { null }

    /** videoId from a watch URL (`?v=`) or a youtu.be short link (last path segment). */
    private fun extractVideoId(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return runCatching {
            val uri = Uri.parse(url)
            uri.getQueryParameter("v")?.ifBlank { null } ?: uri.lastPathSegment?.ifBlank { null }
        }.getOrNull()
    }

    private fun playlistUrl(playlistId: String) =
        "https://www.youtube.com/playlist?list=$playlistId"

    private fun watchUrl(videoId: String) =
        "https://www.youtube.com/watch?v=$videoId"
}
