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
    /** Whether the directory's last reachability check found the stream playable. null = unknown
     *  (e.g. a favorite persisted before this field existed, or a seeded default). */
    val lastCheckOk: Boolean? = null,
) {
    val mediaId: String get() = "radio:$uuid"

    fun toJson(): JSONObject = JSONObject().apply {
        put("uuid", uuid)
        put("name", name)
        put("url", streamUrl)
        put("favicon", favicon ?: "")
        put("votes", votes)
        put("country", country ?: "")
        // Only written when known, so absence round-trips back to null (unknown) on read.
        lastCheckOk?.let { put("lastcheckok", if (it) 1 else 0) }
    }

    companion object {
        fun fromJson(o: JSONObject): RadioStation = RadioStation(
            uuid = o.optString("uuid"),
            name = o.optString("name"),
            streamUrl = o.optString("url"),
            favicon = o.optString("favicon").ifBlank { null },
            votes = o.optInt("votes"),
            country = o.optString("country").ifBlank { null },
            lastCheckOk = if (o.has("lastcheckok")) o.optInt("lastcheckok") == 1 else null,
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
                lastCheckOk = if (o.has("lastcheckok")) o.optInt("lastcheckok") == 1 else null,
            )
        }
    }
}

/** Compact vote count for list rows: 950 → "950", 1234 → "1.2k", 15300 → "15k". */
fun formatVotes(votes: Int): String = when {
    votes < 1000 -> votes.toString()
    votes < 10_000 -> {
        val k = "%.1f".format(votes / 1000.0)
        (if (k.endsWith(".0")) k.dropLast(2) else k) + "k"
    }
    else -> "${votes / 1000}k"
}

/** One-line subtitle for the car's radio rows (no styling available there): country, an OK when
 *  the stream was reachable at last check, and the vote count — joined with " · ". */
fun RadioStation.stationSubtitle(): String = buildList {
    country?.let { add(it) }
    if (lastCheckOk == true) add("OK")
    if (votes > 0) add("▲ ${formatVotes(votes)}")
}.joinToString(" · ")
