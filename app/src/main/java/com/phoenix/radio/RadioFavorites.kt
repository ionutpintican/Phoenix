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
        if (!prefs.contains(KEY)) {
            // First launch: seed a few reliable stations so the Radio list opens with favorites
            // (phone and car) instead of an empty favorites row. Persisted like any favorite, so
            // once the user edits the list this never runs again — removed defaults stay removed.
            write(DEFAULT_FAVORITES)
            _favorites.value = DEFAULT_FAVORITES
        } else {
            _favorites.value = read()
        }
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

    /**
     * Default favorites seeded on first launch. Chosen for reliable, no-auth, non-geoblocked
     * streams (SomaFM / Radio Paradise / Radio Swiss Jazz). Favicons are left null so the list
     * shows a clean placeholder rather than risking a broken-image tile on the car. The uuids are
     * stable local slugs; browse de-dupes by stream URL so these won't double up with the API's
     * top list.
     */
    private val DEFAULT_FAVORITES = listOf(
        RadioStation("phoenix-soma-groovesalad", "SomaFM: Groove Salad",
            "https://ice1.somafm.com/groovesalad-256-mp3", null, 0, "USA"),
        RadioStation("phoenix-soma-dronezone", "SomaFM: Drone Zone",
            "https://ice1.somafm.com/dronezone-256-mp3", null, 0, "USA"),
        RadioStation("phoenix-soma-indiepop", "SomaFM: Indie Pop Rocks",
            "https://ice1.somafm.com/indiepop-128-mp3", null, 0, "USA"),
        RadioStation("phoenix-radioparadise-main", "Radio Paradise: Main Mix",
            "https://stream.radioparadise.com/mp3-192", null, 0, "USA"),
        RadioStation("phoenix-radioparadise-mellow", "Radio Paradise: Mellow Mix",
            "https://stream.radioparadise.com/mellow-192", null, 0, "USA"),
        RadioStation("phoenix-radioswissjazz", "Radio Swiss Jazz",
            "https://stream.srg-ssr.ch/m/rsj/mp3_128", null, 0, "Switzerland"),
    )
}
