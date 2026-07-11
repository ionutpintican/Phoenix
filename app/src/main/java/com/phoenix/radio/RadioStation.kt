package com.phoenix.radio

import org.json.JSONObject

/**
 * An internet radio station from the radio-browser.info directory.
 * The full station (not just an id) is persisted for favorites so it stays replayable
 * even when it isn't in the current top/search list and even offline.
 */
data class RadioStation(
    val uuid: String,
    val name: String,
    val streamUrl: String,
    val favicon: String?,
    val votes: Int,
    val country: String?,
) {
    val mediaId: String get() = "radio:$uuid"

    fun toJson(): JSONObject = JSONObject().apply {
        put("uuid", uuid)
        put("name", name)
        put("url", streamUrl)
        put("favicon", favicon ?: "")
        put("votes", votes)
        put("country", country ?: "")
    }

    companion object {
        fun fromJson(o: JSONObject): RadioStation = RadioStation(
            uuid = o.optString("uuid"),
            name = o.optString("name"),
            streamUrl = o.optString("url"),
            favicon = o.optString("favicon").ifBlank { null },
            votes = o.optInt("votes"),
            country = o.optString("country").ifBlank { null },
        )

        /** Parse a radio-browser API station object (its field names differ from ours). */
        fun fromApi(o: JSONObject): RadioStation? {
            val url = o.optString("url_resolved").ifBlank { o.optString("url") }
            if (url.isBlank()) return null
            val uuid = o.optString("stationuuid").ifBlank { url }
            return RadioStation(
                uuid = uuid,
                name = o.optString("name").ifBlank { "Unknown station" }.trim(),
                streamUrl = url,
                favicon = o.optString("favicon").ifBlank { null },
                votes = o.optInt("votes"),
                country = o.optString("country").ifBlank { null },
            )
        }
    }
}
