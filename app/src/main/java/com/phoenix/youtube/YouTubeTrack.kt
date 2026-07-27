package com.phoenix.youtube

import org.json.JSONObject

/**
 * One track from a YouTube (Music) playlist. Only the [videoId] and light metadata are stored —
 * never a stream URL, because YouTube's audio URLs are short-lived and IP-bound. The actual
 * playable URL is resolved just-in-time by [YouTubeBrowser.resolveStreamUrl] via the
 * ResolvingDataSource in the playback service, so a persisted/queued track stays playable long
 * after its original URL would have expired.
 *
 * Persisted (as JSON) in the playlist cache the same way a [com.phoenix.radio.RadioStation] is,
 * so a played track stays resolvable by its media id even after the in-memory caches are trimmed.
 */
data class YouTubeTrack(
    val videoId: String,
    val title: String,
    val artist: String?,
    /** Track length in seconds; 0 when unknown (e.g. a live item). */
    val durationSec: Long,
    val thumbnailUrl: String?,
) {
    /** Stable id used across the browse tree and the phone controller. */
    val mediaId: String get() = "yt:$videoId"

    /**
     * The custom scheme URI put on the [androidx.media3.common.MediaItem]. It carries only the
     * video id — the ResolvingDataSource rewrites it to the real googlevideo URL at read time.
     * Video ids are `[A-Za-z0-9_-]`, all valid in a path segment, so the id round-trips cleanly
     * as [android.net.Uri.getLastPathSegment].
     */
    val playbackUri: String get() = "yt://i/$videoId"

    fun toJson(): JSONObject = JSONObject().apply {
        put("videoId", videoId)
        put("title", title)
        put("artist", artist ?: "")
        put("duration", durationSec)
        put("thumb", thumbnailUrl ?: "")
    }

    companion object {
        fun fromJson(o: JSONObject): YouTubeTrack = YouTubeTrack(
            videoId = o.optString("videoId"),
            title = o.optString("title").ifBlank { "Unknown track" },
            artist = o.optString("artist").ifBlank { null },
            durationSec = o.optLong("duration"),
            thumbnailUrl = o.optString("thumb").ifBlank { null },
        )
    }
}

/** `1:04:09`, `4:09`, or `0:37` for a track duration. Blank when the length is unknown. */
fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return ""
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
