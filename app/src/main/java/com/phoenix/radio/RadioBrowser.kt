package com.phoenix.radio

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Thin client over the radio-browser.info directory: top stations + name search.
 *
 * Android Auto calls search twice per query (once for the count, once for the results),
 * so a small per-query cache avoids a double network round-trip. Recently seen lists are
 * remembered so a played station can be queued alongside the list it came from.
 */
object RadioBrowser {

    private const val BASE = "https://de1.api.radio-browser.info/json"
    private const val UA = "Phoenix/1.0 (+local Android Auto music app)"

    private val searchCache = object : LinkedHashMap<String, List<RadioStation>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<RadioStation>>?) = size > 12
    }

    @Volatile private var lastTop: List<RadioStation> = emptyList()
    @Volatile private var lastSearch: List<RadioStation> = emptyList()
    @Volatile private var lastBrowse: List<RadioStation> = emptyList()

    fun topStations(limit: Int = 50): List<RadioStation> {
        if (lastTop.isNotEmpty()) return lastTop
        val stations = fetch("$BASE/stations/topvote/$limit")
        if (stations.isNotEmpty()) lastTop = stations
        return stations
    }

    fun search(query: String): List<RadioStation> {
        val key = query.trim().lowercase()
        if (key.isBlank()) return emptyList()
        searchCache[key]?.let { return it }
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val stations = fetch("$BASE/stations/byname/$encoded?limit=50&order=votes&reverse=true")
        searchCache[key] = stations
        lastSearch = stations
        return stations
    }

    /** Browse ordering: favorites first, then the top stations, de-duplicated. De-dupe by stream
     *  URL (not uuid) so a seeded favorite and its top-list twin — which carry different ids —
     *  collapse to the single favorite entry. */
    fun browseStations(favorites: List<RadioStation>): List<RadioStation> {
        val rest = topStations()
        val merged = (favorites + rest).distinctBy { it.streamUrl.ifBlank { it.uuid } }
        lastBrowse = merged
        return merged
    }

    /** The list a station should queue with: whichever remembered list contains it. */
    fun queueContaining(station: RadioStation, favorites: List<RadioStation>): List<RadioStation> {
        for (list in listOf(lastSearch, lastBrowse, lastTop, favorites)) {
            if (list.any { it.uuid == station.uuid }) return list
        }
        return listOf(station)
    }

    fun stationByMediaId(mediaId: String, favorites: List<RadioStation>): RadioStation? {
        val uuid = mediaId.removePrefix("radio:")
        return (favorites + lastBrowse + lastSearch + lastTop).firstOrNull { it.uuid == uuid }
    }

    private fun fetch(urlStr: String): List<RadioStation> = try {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", UA)
            connectTimeout = 8000
            readTimeout = 8000
        }
        conn.inputStream.bufferedReader().use { reader ->
            val arr = JSONArray(reader.readText())
            (0 until arr.length()).mapNotNull { RadioStation.fromApi(arr.getJSONObject(it)) }
        }
    } catch (_: Exception) {
        emptyList()
    }
}
