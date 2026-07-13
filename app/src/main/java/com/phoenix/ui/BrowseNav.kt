package com.phoenix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.phoenix.playback.MusicLibrary

/** A car letter shortcut → the folder name it jumps to. Mirrors the car's shortcuts. */
data class LetterShortcut(val label: String, val folderName: String)

val LETTER_SHORTCUTS = listOf(
    LetterShortcut("L", "Leni"),
    LetterShortcut("A", "Action"),
    LetterShortcut("H", "Hideout"),
    LetterShortcut("Au", "Audiobooks"),
)

/**
 * The row of jump-to-folder buttons (L / A / H / Au), optionally with a Radio button.
 * A letter whose folder isn't on the device is disabled. [revision] forces a recompute
 * of enablement after the library (re)scans.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LetterShortcutBar(
    onGoToFolder: (String) -> Unit,
    modifier: Modifier = Modifier,
    showRadioButton: Boolean = false,
    onOpenRadio: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") revision: Int = 0,
) {
    // A wrapping row: on narrow screens (e.g. the now-playing screen's extra padding) the
    // Radio button would otherwise overflow off-screen instead of dropping to a second line.
    FlowRow(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LETTER_SHORTCUTS.forEach { shortcut ->
            val enabled = MusicLibrary.folderIdByName(shortcut.folderName) != null
            FilledTonalButton(
                onClick = { onGoToFolder(shortcut.folderName) },
                enabled = enabled,
            ) {
                Text(shortcut.label)
            }
        }
        if (showRadioButton) {
            FilledTonalButton(onClick = onOpenRadio) {
                Icon(Icons.Filled.Radio, contentDescription = "Radio")
            }
        }
    }
}
