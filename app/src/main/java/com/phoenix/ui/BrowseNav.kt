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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.phoenix.playback.MusicLibrary
import com.phoenix.settings.Settings
import com.phoenix.settings.ShortcutIcons

/**
 * The row of jump-to-folder shortcut buttons, optionally with a Radio button. Each button's
 * appearance (a single capital letter or a built-in icon) and target folder come from the
 * user-editable [Settings.shortcuts], so the phone bar and the car mirror the same config.
 * A shortcut whose folder isn't on the device is disabled. [revision] forces a recompute of
 * enablement after the library (re)scans.
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
    val shortcuts by Settings.shortcuts.collectAsState()

    // A wrapping row: on narrow screens (e.g. the now-playing screen's extra padding) the
    // Radio button would otherwise overflow off-screen instead of dropping to a second line.
    FlowRow(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        shortcuts.forEach { shortcut ->
            val enabled = MusicLibrary.folderIdByName(shortcut.folderName) != null
            FilledTonalButton(
                onClick = { onGoToFolder(shortcut.folderName) },
                enabled = enabled,
            ) {
                val letter = shortcut.letter
                val icon = ShortcutIcons.vectorFor(shortcut)
                when {
                    letter != null -> Text(letter)
                    icon != null -> Icon(icon, contentDescription = shortcut.folderName)
                    else -> Text(shortcut.folderName.take(1).uppercase())
                }
            }
        }
        if (showRadioButton) {
            FilledTonalButton(onClick = onOpenRadio) {
                Icon(Icons.Filled.Radio, contentDescription = "Radio")
            }
        }
    }
}
