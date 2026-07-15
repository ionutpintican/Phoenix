package com.phoenix.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector
import com.phoenix.R

/**
 * A built-in shortcut icon: a stable [key] persisted in settings, a [drawableRes] for the car's
 * media-session command buttons (Android Auto resolves the icon from a resource URI), and the
 * matching Compose [vector] for the phone UI so both surfaces look identical.
 */
data class CuratedIcon(
    val key: String,
    val label: String,
    val drawableRes: Int,
    val vector: ImageVector,
)

/**
 * Registry mapping a [ShortcutSetting]'s appearance to concrete assets. Letters resolve to the
 * bundled A–Z vector drawables ([letterDrawable]); icons resolve through [curated]. Add a new
 * entry to [curated] (plus its `ic_appearance_*` drawable) to offer more icons.
 */
object ShortcutIcons {

    val curated: List<CuratedIcon> = listOf(
        CuratedIcon("music_note", "Music note", R.drawable.ic_appearance_music_note, Icons.Filled.MusicNote),
        CuratedIcon("headphones", "Headphones", R.drawable.ic_appearance_headphones, Icons.Filled.Headphones),
        CuratedIcon("star", "Star", R.drawable.ic_appearance_star, Icons.Filled.Star),
        CuratedIcon("heart", "Heart", R.drawable.ic_appearance_heart, Icons.Filled.Favorite),
        CuratedIcon("radio", "Radio", R.drawable.ic_radio, Icons.Filled.Radio),
        CuratedIcon("album", "Album", R.drawable.ic_appearance_album, Icons.Filled.Album),
        CuratedIcon("book", "Book", R.drawable.ic_appearance_book, Icons.Filled.MenuBook),
    )

    fun curatedByKey(key: String?): CuratedIcon? = key?.let { k -> curated.firstOrNull { it.key == k } }

    /** The Compose icon for an icon-mode shortcut, or null when it uses a letter. */
    fun vectorFor(s: ShortcutSetting): ImageVector? = curatedByKey(s.iconKey)?.vector

    /** The drawable the car command button uses — the letter glyph, or the curated icon. */
    fun drawableFor(s: ShortcutSetting): Int =
        s.letter?.let { letterDrawable(it) }
            ?: curatedByKey(s.iconKey)?.drawableRes
            ?: R.drawable.ic_appearance_music_note

    fun letterDrawable(letter: String): Int = when (letter.uppercase()) {
        "A" -> R.drawable.ic_letter_a
        "B" -> R.drawable.ic_letter_b
        "C" -> R.drawable.ic_letter_c
        "D" -> R.drawable.ic_letter_d
        "E" -> R.drawable.ic_letter_e
        "F" -> R.drawable.ic_letter_f
        "G" -> R.drawable.ic_letter_g
        "H" -> R.drawable.ic_letter_h
        "I" -> R.drawable.ic_letter_i
        "J" -> R.drawable.ic_letter_j
        "K" -> R.drawable.ic_letter_k
        "L" -> R.drawable.ic_letter_l
        "M" -> R.drawable.ic_letter_m
        "N" -> R.drawable.ic_letter_n
        "O" -> R.drawable.ic_letter_o
        "P" -> R.drawable.ic_letter_p
        "Q" -> R.drawable.ic_letter_q
        "R" -> R.drawable.ic_letter_r
        "S" -> R.drawable.ic_letter_s
        "T" -> R.drawable.ic_letter_t
        "U" -> R.drawable.ic_letter_u
        "V" -> R.drawable.ic_letter_v
        "W" -> R.drawable.ic_letter_w
        "X" -> R.drawable.ic_letter_x
        "Y" -> R.drawable.ic_letter_y
        "Z" -> R.drawable.ic_letter_z
        else -> R.drawable.ic_appearance_music_note
    }
}
