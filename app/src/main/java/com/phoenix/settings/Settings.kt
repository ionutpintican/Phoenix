package com.phoenix.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * One editable letter/icon shortcut. Its appearance is *either* a single capital [letter]
 * (A–Z) *or* a built-in [iconKey] from [ShortcutIcons] — never both. [folderName] is the
 * playlist folder it jumps to (matched by [com.phoenix.playback.MusicLibrary.folderIdByName]).
 */
data class ShortcutSetting(
    val folderName: String,
    val letter: String?,
    val iconKey: String?,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("folder", folderName)
        put("letter", letter ?: "")
        put("icon", iconKey ?: "")
    }

    companion object {
        fun fromJson(o: JSONObject) = ShortcutSetting(
            folderName = o.optString("folder", ""),
            letter = o.optString("letter").takeIf { it.isNotBlank() },
            iconKey = o.optString("icon").takeIf { it.isNotBlank() },
        )
    }
}

/**
 * Persisted user settings (SharedPreferences), exposed as [StateFlow]s so the phone Compose
 * UI and the car [com.phoenix.playback.PlaybackService] both react live — the same pattern as
 * [com.phoenix.radio.RadioFavorites]. Editable only from the phone Settings screen; the car
 * just reads the current values.
 *
 * Grows over time: add a key + a flow + accessors here and the two consumers pick it up.
 */
object Settings {

    private const val PREFS = "phoenix_settings"
    private const val KEY_SHORTCUTS = "shortcuts"
    private const val KEY_CROSSFADE = "crossfade_seconds"

    /** The letter/icon shortcut bar is a fixed set of four editable rows. */
    const val SHORTCUT_COUNT = 4

    const val CROSSFADE_MIN = 0
    const val CROSSFADE_MAX = 12
    const val CROSSFADE_DEFAULT = 7

    /**
     * Defaults reproduce the app's original hardcoded shortcuts exactly: L / A / H as letters,
     * and Audiobooks as the headphones icon (matching the old ic_letter_au glyph). Declared before
     * the flows below so it's initialized first (object properties init top-to-bottom).
     */
    val DEFAULT_SHORTCUTS = listOf(
        ShortcutSetting("Leni", "L", null),
        ShortcutSetting("Action", "A", null),
        ShortcutSetting("Hideout", "H", null),
        ShortcutSetting("Audiobooks", null, "headphones"),
    )

    private lateinit var prefs: SharedPreferences

    private val _shortcuts = MutableStateFlow(DEFAULT_SHORTCUTS)
    val shortcuts: StateFlow<List<ShortcutSetting>> = _shortcuts.asStateFlow()

    private val _crossfadeSeconds = MutableStateFlow(CROSSFADE_DEFAULT)
    val crossfadeSeconds: StateFlow<Int> = _crossfadeSeconds.asStateFlow()

    /** Crossfade length in ms; 0 means the user turned crossfade off. */
    val crossfadeMs: Long get() = _crossfadeSeconds.value * 1000L

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _shortcuts.value = readShortcuts()
        _crossfadeSeconds.value = prefs.getInt(KEY_CROSSFADE, CROSSFADE_DEFAULT)
    }

    fun setShortcut(index: Int, shortcut: ShortcutSetting) {
        val next = _shortcuts.value.toMutableList()
        if (index !in next.indices) return
        next[index] = shortcut
        _shortcuts.value = next
        writeShortcuts(next)
    }

    fun setCrossfadeSeconds(seconds: Int) {
        val v = seconds.coerceIn(CROSSFADE_MIN, CROSSFADE_MAX)
        _crossfadeSeconds.value = v
        prefs.edit().putInt(KEY_CROSSFADE, v).apply()
    }

    private fun readShortcuts(): List<ShortcutSetting> = try {
        val raw = prefs.getString(KEY_SHORTCUTS, null) ?: return DEFAULT_SHORTCUTS
        val arr = JSONArray(raw)
        val list = (0 until arr.length()).map { ShortcutSetting.fromJson(arr.getJSONObject(it)) }
        // Guard against a stored list from a different SHORTCUT_COUNT (forward/back compat).
        if (list.size == SHORTCUT_COUNT) list else DEFAULT_SHORTCUTS
    } catch (_: Exception) {
        DEFAULT_SHORTCUTS
    }

    private fun writeShortcuts(list: List<ShortcutSetting>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_SHORTCUTS, arr.toString()).apply()
    }
}
