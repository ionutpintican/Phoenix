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

    /** A titled group of stations in the browse list (Favorites / Recently played / All stations). */
    data class RadioSection(val title: String, val stations: List<RadioStation>)

    /**
     * The browse list split into sections: favorites, then recently-played (that aren't favorites),
     * then the rest of the top stations. De-duped by stream URL (not uuid) so a seeded favorite and
     * its top-list twin — which carry different ids — collapse to a single entry, kept in the
     * highest section it appears in. Favorites and recents are enriched from their fresh top-list
     * twin so they can show an up-to-date reachability badge and vote count.
     */
    fun browseSections(
        favorites: List<RadioStation>,
        recents: List<RadioStation>,
    ): List<RadioSection> {
        val top = topStations()
        val fresh = top.associateBy { it.streamUrl.ifBlank { it.uuid } }
        fun key(s: RadioStation) = s.streamUrl.ifBlank { s.uuid }
        // Copy the directory's fresh check result / votes onto a stored favorite or recent.
        fun enrich(s: RadioStation): RadioStation = fresh[key(s)]?.let {
            s.copy(votes = it.votes, lastCheckOk = it.lastCheckOk)
        } ?: s

        val seen = mutableSetOf<String>()
        fun take(list: List<RadioStation>): List<RadioStation> = list.mapNotNull { s ->
            if (seen.add(key(s))) enrich(s) else null
        }

        val favSection = take(favorites)
        val recentSection = take(recents)
        val allSection = take(top)
        val sections = buildList {
            if (favSection.isNotEmpty()) add(RadioSection("Favorites", favSection))
            if (recentSection.isNotEmpty()) add(RadioSection("Recently played", recentSection))
            if (allSection.isNotEmpty()) add(RadioSection("All stations", allSection))
        }
        // Remember the flattened list so a played station — including a recent that isn't in the
        // favorites or top lists — can be resolved back from its media id.
        lastBrowse = sections.flatMap { it.stations }
        return sections
    }

    /** The browse list flattened — favorites, then recents, then the rest — for the car's plain
     *  vertical list and for building a play queue. */
    fun browseStations(
        favorites: List<RadioStation>,
        recents: List<RadioStation>,
    ): List<RadioStation> = browseSections(favorites, recents).flatMap { it.stations }

    /** The list a station should queue with: whichever remembered list contains it. */
    fun queueContaining(station: RadioStation, favorites: List<RadioStation>): List<RadioStation> {
        for (list in listOf(lastSearch, lastBrowse, lastTop, favorites, RadioRecents.recents.value)) {
            if (list.any { it.uuid == station.uuid }) return list
        }
        return listOf(station)
    }

    /** Resolve a "radio:uuid" media id back to its full station (the stream URL lives here, not in
     *  the id). Persistent stores — favorites and recents — are searched alongside the volatile
     *  browse/search/top caches so a played station stays resolvable even after the process is
     *  trimmed and those caches are empty; otherwise playback would silently no-op. */
    fun stationByMediaId(mediaId: String, favorites: List<RadioStation>): RadioStation? {
        val uuid = mediaId.removePrefix("radio:")
        return (favorites + RadioRecents.recents.value + lastBrowse + lastSearch + lastTop)
            .firstOrNull { it.uuid == uuid }
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
