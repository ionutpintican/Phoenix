package com.phoenix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phoenix.playback.MusicLibrary
import com.phoenix.settings.Settings
import com.phoenix.settings.ShortcutIcons
import com.phoenix.settings.ShortcutSetting
import kotlin.math.roundToInt

/**
 * The phone-only settings screen (never shown in the car). Edits persist immediately via
 * [Settings], whose flows the phone bar and the car [com.phoenix.playback.PlaybackService]
 * both observe, so a change here shows up everywhere without a restart.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val shortcuts by Settings.shortcuts.collectAsState()
    val crossfade by Settings.crossfadeSeconds.collectAsState()
    val revision by MusicLibrary.revision.collectAsState()

    // The folders the app has scanned, offered as the shortcut targets (no typos).
    val folderOptions = remember(revision) {
        MusicLibrary.browseFolders().map { it.name }.distinct().sortedBy { it.lowercase() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SectionHeader("Shortcuts")
            Text(
                "Each shortcut plays a folder shuffled. Pick its folder and show it as a single " +
                    "capital letter or an icon — used on the phone and in the car.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            shortcuts.forEachIndexed { index, shortcut ->
                ShortcutEditor(index, shortcut, folderOptions)
                if (index < shortcuts.lastIndex) {
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionHeader("Crossfade")
            Text(
                if (crossfade == 0) "Off — songs change instantly."
                else "$crossfade s dissolve between songs.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = crossfade.toFloat(),
                onValueChange = { Settings.setCrossfadeSeconds(it.roundToInt()) },
                valueRange = Settings.CROSSFADE_MIN.toFloat()..Settings.CROSSFADE_MAX.toFloat(),
                steps = (Settings.CROSSFADE_MAX - Settings.CROSSFADE_MIN) - 1,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Off", style = MaterialTheme.typography.labelSmall)
                Text("${Settings.CROSSFADE_MAX} s", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ShortcutEditor(
    index: Int,
    shortcut: ShortcutSetting,
    folderOptions: List<String>,
) {
    Text(
        "Shortcut ${index + 1}",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(bottom = 8.dp),
    )

    // --- Folder picker (from scanned folders; keep the current value even if not scanned) ---
    var expanded by remember { mutableStateOf(false) }
    val options = remember(folderOptions, shortcut.folderName) {
        (folderOptions + shortcut.folderName).filter { it.isNotBlank() }.distinct()
    }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = shortcut.folderName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Folder") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        Settings.setShortcut(index, shortcut.copy(folderName = name))
                        expanded = false
                    },
                )
            }
        }
    }

    Spacer(Modifier.height(8.dp))

    // --- Appearance: letter vs icon (single choice) ---
    val letterMode = shortcut.letter != null
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = letterMode,
            onClick = {
                if (!letterMode) {
                    val default = shortcut.folderName.firstOrNull { it.isLetter() }?.uppercase() ?: "A"
                    Settings.setShortcut(index, shortcut.copy(letter = default, iconKey = null))
                }
            },
            label = { Text("Letter") },
        )
        FilterChip(
            selected = !letterMode,
            onClick = {
                if (letterMode) {
                    val key = shortcut.iconKey ?: ShortcutIcons.curated.first().key
                    Settings.setShortcut(index, shortcut.copy(letter = null, iconKey = key))
                }
            },
            label = { Text("Icon") },
        )
    }

    Spacer(Modifier.height(8.dp))

    if (letterMode) {
        OutlinedTextField(
            value = shortcut.letter.orEmpty(),
            onValueChange = { input ->
                input.lastOrNull { it.isLetter() }?.let { ch ->
                    Settings.setShortcut(index, shortcut.copy(letter = ch.uppercase(), iconKey = null))
                }
            },
            singleLine = true,
            label = { Text("Letter (A–Z)") },
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShortcutIcons.curated.forEach { ci ->
                FilterChip(
                    selected = shortcut.iconKey == ci.key,
                    onClick = {
                        Settings.setShortcut(index, shortcut.copy(letter = null, iconKey = ci.key))
                    },
                    leadingIcon = { Icon(ci.vector, contentDescription = null) },
                    label = { Text(ci.label) },
                )
            }
        }
    }
}
