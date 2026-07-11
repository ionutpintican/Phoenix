package com.phoenix.playback

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The single source of truth for local audio. Scans [MediaStore] into a flat set of
 * "leaf folders" (each directory that directly contains audio) and exposes sorting,
 * folder lookup and search. Shared by the phone Compose UI and the car [PlaybackService].
 *
 * Only files MediaStore has indexed are visible; a `.nomedia` file hides a folder.
 * The [rescan] call forces a re-read after new files are copied in.
 */
object MusicLibrary {

    enum class SortMode { NAME_ASC, NAME_DESC, DATE_ASC, DATE_DESC }

    /** A directory that directly contains audio (a "playlist" in the browse UI). */
    data class Folder(
        val id: String,          // normalized relative path, e.g. "Music/Leni"
        val name: String,        // last path segment, e.g. "Leni"
        val path: String,        // id + "/" — the prefix used to match tracks
        val volume: String,      // MediaStore volume name (SD card differs from primary)
    )

    private val _sortMode = MutableStateFlow(SortMode.NAME_ASC)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()
    fun setSortMode(mode: SortMode) { _sortMode.value = mode }

    /** Bumped whenever the library is (re)scanned so observers can refresh. */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    @Volatile private var tracksById: Map<Long, Track> = emptyMap()
    @Volatile private var folders: List<Folder> = emptyList()
    @Volatile var isLoaded: Boolean = false
        private set

    // ---- Scanning -------------------------------------------------------------

    fun ensureLoaded(context: Context) {
        if (!isLoaded) load(context)
    }

    fun rescan(context: Context) = load(context)

    @Synchronized
    fun load(context: Context) {
        val tracks = queryTracks(context)
        tracksById = tracks.associateBy { it.id }

        // Build the leaf-folder set: one entry per directory that directly holds audio.
        folders = tracks
            .groupBy { it.relativePath.trimEnd('/') }
            .filterKeys { it.isNotBlank() }
            .map { (path, ts) ->
                val name = path.substringAfterLast('/')
                Folder(id = path, name = name, path = "$path/", volume = ts.first().volume)
            }
            .sortedBy { it.name.lowercase() }

        isLoaded = true
        _revision.value = _revision.value + 1
    }

    private fun queryTracks(context: Context): List<Track> {
        val hasRelPath = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DATE_MODIFIED)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.DATA)
            if (hasRelPath) {
                add(MediaStore.Audio.Media.RELATIVE_PATH)
                add(MediaStore.Audio.Media.VOLUME_NAME)
            }
        }.toTypedArray()

        // Include audiobook-flagged files: Android's scanner marks files under an
        // "Audiobooks" folder as IS_AUDIOBOOK=1 / IS_MUSIC=0, which would otherwise
        // exclude them entirely. The audiobook clause is API 29+, version-guarded.
        val selection = if (hasRelPath) {
            "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.IS_AUDIOBOOK} != 0)"
        } else {
            "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        }

        val collection = if (hasRelPath) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val out = ArrayList<Track>()
        context.contentResolver.query(collection, projection, selection, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val relCol = if (hasRelPath) c.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH) else -1
            val volCol = if (hasRelPath) c.getColumnIndex(MediaStore.Audio.Media.VOLUME_NAME) else -1

            val artBase = Uri.parse("content://media/external/audio/albumart")
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val albumId = c.getLong(albumIdCol)
                val relPath = if (relCol >= 0) c.getString(relCol) ?: "" else relativePathFromData(c.getString(dataCol))
                out += Track(
                    id = id,
                    title = c.getString(titleCol) ?: "Unknown",
                    artist = c.getString(artistCol),
                    album = c.getString(albumCol),
                    uri = ContentUris.withAppendedId(collection, id),
                    albumArtUri = ContentUris.withAppendedId(artBase, albumId),
                    bucketId = relPath.trimEnd('/'),
                    bucketName = relPath.trimEnd('/').substringAfterLast('/'),
                    relativePath = relPath,
                    dateModified = c.getLong(dateCol),
                    duration = c.getLong(durCol),
                ).let { it.copyVolume(if (volCol >= 0) c.getString(volCol) ?: "external_primary" else "external_primary") }
            }
        }
        return out
    }

    private fun relativePathFromData(data: String?): String {
        if (data.isNullOrBlank()) return ""
        // .../storage/emulated/0/Music/Leni/song.mp3 -> "Music/Leni/"
        val parent = data.substringBeforeLast('/')
        val idx = parent.indexOf("/0/").let { if (it >= 0) it + 3 else parent.lastIndexOf('/') + 1 }
        return parent.substring(idx.coerceIn(0, parent.length)) + "/"
    }

    // ---- Sorting --------------------------------------------------------------

    fun sorted(tracks: List<Track>, mode: SortMode = _sortMode.value): List<Track> = when (mode) {
        SortMode.NAME_ASC -> tracks.sortedBy { it.title.lowercase() }
        SortMode.NAME_DESC -> tracks.sortedByDescending { it.title.lowercase() }
        SortMode.DATE_ASC -> tracks.sortedBy { it.dateModified }
        SortMode.DATE_DESC -> tracks.sortedByDescending { it.dateModified }
    }

    // ---- Accessors ------------------------------------------------------------

    fun allTracks(): List<Track> = sorted(tracksById.values.toList())

    fun getTrack(id: Long): Track? = tracksById[id]

    fun getTrackByMediaId(mediaId: String): Track? =
        mediaId.removePrefix("song:").toLongOrNull()?.let { tracksById[it] }

    fun browseFolders(): List<Folder> = folders

    fun browsableFolderIds(): List<String> = folders.map { it.id }

    /** Direct tracks of [folderId], sorted. */
    fun tracksInFolder(folderId: String): List<Track> =
        sorted(tracksById.values.filter { it.relativePath.trimEnd('/') == folderId })

    /**
     * Everything playable for a folder: its direct tracks, or — if it has none (a folder
     * made only of sub-folders, e.g. Audiobooks) — every track beneath it. Sorted.
     */
    fun playableTracksFor(folderId: String): List<Track> {
        val direct = tracksInFolder(folderId)
        if (direct.isNotEmpty()) return direct
        val prefix = "$folderId/"
        return sorted(tracksById.values.filter { it.relativePath.startsWith(prefix) })
    }

    /**
     * Resolve a folder by name, preferring the copy under Music/ and on the SD card.
     * Falls back to any ancestor path segment so a folder-of-subfolders still resolves.
     * Returns null when nothing on the device matches.
     */
    fun folderIdByName(name: String): String? {
        val target = name.lowercase()
        val leaf = folders.filter { it.name.lowercase() == target }
            .maxByOrNull { folderPreferenceScore(it.path, it.volume) }
        if (leaf != null) return leaf.id

        // No leaf folder — look for the name as an ancestor segment (subfolder container).
        return tracksById.values
            .mapNotNull { t ->
                val segs = t.relativePath.trim('/').split('/')
                val idx = segs.indexOfFirst { it.lowercase() == target }
                if (idx >= 0) segs.subList(0, idx + 1).joinToString("/") to t.volume else null
            }
            .maxByOrNull { folderPreferenceScore("${it.first}/", it.second) }
            ?.first
    }

    private fun folderPreferenceScore(path: String, volume: String): Int {
        var score = 0
        if (path.contains("Music/", ignoreCase = true)) score += 2
        if (volume != "external_primary") score += 1  // prefer the SD-card copy
        return score
    }

    /**
     * Context-scoped search. [parentFolderId] null searches all songs; otherwise only
     * tracks beneath that folder. Matches title or artist, case-insensitively.
     */
    fun search(query: String, parentFolderId: String?): List<Track> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return emptyList()
        val pool = if (parentFolderId == null) tracksById.values
        else tracksById.values.filter {
            it.relativePath.trimEnd('/') == parentFolderId || it.relativePath.startsWith("$parentFolderId/")
        }
        return sorted(pool.filter {
            it.title.lowercase().contains(q) || (it.artist?.lowercase()?.contains(q) == true)
        })
    }

    // ---- Android Auto content-style hints -------------------------------------

    /** Grid tiles for browsable nodes (folders), a list for songs. Cascades down the tree. */
    fun contentStyleExtras(): Bundle = Bundle().apply {
        putInt(CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_GRID)
        putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST)
    }

    const val CONTENT_STYLE_BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
    const val CONTENT_STYLE_PLAYABLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
    const val CONTENT_STYLE_SINGLE_ITEM_HINT = "android.media.browse.CONTENT_STYLE_SINGLE_ITEM_HINT"
    const val CONTENT_STYLE_LIST = 1
    const val CONTENT_STYLE_GRID = 2
}

/** Small helper so [Track] can stay a clean data class while we thread the volume name in. */
private fun Track.copyVolume(volume: String): Track = this.also { TrackVolumes.map[it.id] = volume }

/** Sidecar map for the (API-29+) MediaStore volume of each track. */
internal object TrackVolumes { val map = HashMap<Long, String>() }

internal val Track.volume: String get() = TrackVolumes.map[id] ?: "external_primary"
