package com.phoenix.radio

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * Persisted "recently played" radio stations, most-recent first. Stored the same way as
 * [RadioFavorites] (full station as JSON in SharedPreferences) so a recent station stays
 * replayable even when it isn't in the current top/search list. Exposed as a [StateFlow] so the
 * phone UI and the car [com.phoenix.playback.PlaybackService] both react live when a play is
 * recorded. De-duplicated by stream URL so re-playing a station just moves it to the top.
 */
object RadioRecents {

    private const val PREFS = "radio_recents"
    private const val KEY = "stations"
    private const val MAX = 10

    private lateinit var prefs: SharedPreferences

    private val _recents = MutableStateFlow<List<RadioStation>>(emptyList())
    val recents: StateFlow<List<RadioStation>> = _recents.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _recents.value = read()
    }

    /** Move [station] to the front of the recents list (de-duped by stream URL), capped at [MAX]. */
    fun record(station: RadioStation) {
        if (!::prefs.isInitialized) return
        val key = station.streamUrl.ifBlank { station.uuid }
        val next = (listOf(station) + _recents.value.filterNot {
            it.streamUrl.ifBlank { it.uuid } == key
        }).take(MAX)
        if (next == _recents.value) return
        _recents.value = next
        write(next)
    }

    private fun read(): List<RadioStation> = try {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        val arr = JSONArray(raw)
        (0 until arr.length()).map { RadioStation.fromJson(arr.getJSONObject(it)) }
    } catch (_: Exception) {
        emptyList()
    }

    private fun write(list: List<RadioStation>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }
}
