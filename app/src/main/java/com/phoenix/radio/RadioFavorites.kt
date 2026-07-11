package com.phoenix.radio

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * Persisted radio favorites. The full station is stored (as JSON in SharedPreferences),
 * not just an id, so a favorite stays playable when it isn't in the current top/search
 * list and shows even offline. Exposed as a [StateFlow] so both the Compose phone UI and
 * the car [com.phoenix.playback.PlaybackService] react to changes live — toggling in one
 * place updates everywhere.
 */
object RadioFavorites {

    private const val PREFS = "radio_favorites"
    private const val KEY = "stations"

    private lateinit var prefs: SharedPreferences

    private val _favorites = MutableStateFlow<List<RadioStation>>(emptyList())
    val favorites: StateFlow<List<RadioStation>> = _favorites.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _favorites.value = read()
    }

    fun isFavorite(uuid: String): Boolean = _favorites.value.any { it.uuid == uuid }

    fun toggle(station: RadioStation) {
        val current = _favorites.value
        val next = if (current.any { it.uuid == station.uuid }) {
            current.filterNot { it.uuid == station.uuid }
        } else {
            current + station
        }
        _favorites.value = next
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
