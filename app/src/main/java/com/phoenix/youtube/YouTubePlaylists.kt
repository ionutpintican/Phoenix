package com.phoenix.youtube

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** A YouTube playlist the user has added: its id plus a display title (falls back to the id until
 *  the real name is fetched). */
data class YouTubePlaylistRef(val id: String, val title: String) {
    val mediaId: String get() = "ytpl:$id"

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
    }

    companion object {
        fun fromJson(o: JSONObject) = YouTubePlaylistRef(
            id = o.optString("id"),
            title = o.optString("title").ifBlank { o.optString("id") },
        )
    }
}

/**
 * The user's saved YouTube playlists, persisted as JSON in SharedPreferences and exposed as a
 * [StateFlow] so the phone UI and the car [com.phoenix.playback.PlaybackService] react live when
 * one is added or removed — the same shape as [com.phoenix.radio.RadioFavorites]. Only the id and
 * title are stored; the tracks themselves are (re)fetched on demand by [YouTubeBrowser].
 */
object YouTubePlaylists {

    private const val PREFS = "youtube_playlists"
    private const val KEY = "playlists"

    private lateinit var prefs: SharedPreferences

    private val _playlists = MutableStateFlow<List<YouTubePlaylistRef>>(emptyList())
    val playlists: StateFlow<List<YouTubePlaylistRef>> = _playlists.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _playlists.value = read()
    }

    fun contains(id: String): Boolean = _playlists.value.any { it.id == id }

    /** Add (or move to the end of the list) a playlist by id. No-op if [id] is already present. */
    fun add(ref: YouTubePlaylistRef) {
        if (!::prefs.isInitialized || contains(ref.id)) return
        val next = _playlists.value + ref
        _playlists.value = next
        write(next)
    }

    /** Replace a playlist's title (once its real name is fetched), keeping its position. */
    fun updateTitle(id: String, title: String) {
        if (!::prefs.isInitialized) return
        val next = _playlists.value.map { if (it.id == id) it.copy(title = title) else it }
        if (next == _playlists.value) return
        _playlists.value = next
        write(next)
    }

    fun remove(id: String) {
        if (!::prefs.isInitialized) return
        val next = _playlists.value.filterNot { it.id == id }
        _playlists.value = next
        write(next)
    }

    private fun read(): List<YouTubePlaylistRef> = try {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        val arr = JSONArray(raw)
        (0 until arr.length()).map { YouTubePlaylistRef.fromJson(arr.getJSONObject(it)) }
    } catch (_: Exception) {
        emptyList()
    }

    private fun write(list: List<YouTubePlaylistRef>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }
}
