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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.phoenix.playback.MusicLibrary
import com.phoenix.settings.Settings
import com.phoenix.settings.ShortcutIcons
import com.phoenix.settings.ShortcutSetting
import kotlin.math.roundToInt

/**
 * An editable copy of one shortcut. [isLetter] is an explicit mode (independent of whether
 * [letter] is currently filled) so the letter field can be cleared and retyped without the row
 * flipping to icon mode. Held only in screen state until the user taps Save.
 */
private data class DraftShortcut(
    val folderName: String,
    val isLetter: Boolean,
    val letter: String,
    val iconKey: String,
)

private fun ShortcutSetting.toDraft() = DraftShortcut(
    folderName = folderName,
    isLetter = letter != null,
    letter = letter ?: "",
    iconKey = iconKey ?: ShortcutIcons.curated.first().key,
)

private fun DraftShortcut.toSetting() = ShortcutSetting(
    folderName = folderName,
    letter = if (isLetter) letter.uppercase().takeIf { it.isNotBlank() } ?: "A" else null,
    iconKey = if (isLetter) null else iconKey,
)

/** A letter-mode row is incomplete until it actually has a letter. */
private val DraftShortcut.isValid: Boolean get() = !isLetter || letter.isNotBlank()

/**
 * The phone-only settings screen (never shown in the car). Edits are staged in local state and
 * written to [Settings] only when the user taps Save — at which point the phone bar and the car
 * [com.phoenix.playback.PlaybackService] both pick up the change (they observe its flows). Leaving
 * without saving discards the edits.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val savedShortcuts by Settings.shortcuts.collectAsState()
    val savedCrossfade by Settings.crossfadeSeconds.collectAsState()
    val revision by MusicLibrary.revision.collectAsState()

    // Local editable drafts. Keyed on the saved values so they resync after a Save (or an
    // external change) but persist across recompositions while editing.
    var drafts by remember(savedShortcuts) { mutableStateOf(savedShortcuts.map { it.toDraft() }) }
    var crossfadeDraft by remember(savedCrossfade) { mutableStateOf(savedCrossfade) }

    val dirty = drafts != savedShortcuts.map { it.toDraft() } || crossfadeDraft != savedCrossfade
    val valid = drafts.all { it.isValid }

    // The folders the app has scanned, offered as the shortcut targets (no typos).
    val folderOptions = remember(revision) {
        MusicLibrary.browseFolders().map { it.name }.distinct().sortedBy { it.lowercase() }
    }

    fun updateDraft(index: Int, value: DraftShortcut) {
        drafts = drafts.toMutableList().also { it[index] = value }
    }

    fun save() {
        drafts.forEachIndexed { i, d -> Settings.setShortcut(i, d.toSetting()) }
        Settings.setCrossfadeSeconds(crossfadeDraft)
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
                actions = {
                    TextButton(onClick = { save() }, enabled = dirty && valid) {
                        Text("Save")
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

            drafts.forEachIndexed { index, draft ->
                ShortcutEditor(
                    index = index,
                    draft = draft,
                    folderOptions = folderOptions,
                    onChange = { updateDraft(index, it) },
                )
                if (index < drafts.lastIndex) {
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionHeader("Crossfade")
            Text(
                if (crossfadeDraft == 0) "Off — songs change instantly."
                else "$crossfadeDraft s dissolve between songs.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = crossfadeDraft.toFloat(),
                onValueChange = { crossfadeDraft = it.roundToInt() },
                valueRange = Settings.CROSSFADE_MIN.toFloat()..Settings.CROSSFADE_MAX.toFloat(),
                steps = (Settings.CROSSFADE_MAX - Settings.CROSSFADE_MIN) - 1,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Off", style = MaterialTheme.typography.labelSmall)
                Text("${Settings.CROSSFADE_MAX} s", style = MaterialTheme.typography.labelSmall)
            }

            Spacer(Modifier.height(24.dp))
            Text(
                if (dirty) "Unsaved changes — tap Save to apply."
                else "All changes saved.",
                style = MaterialTheme.typography.labelMedium,
                color = if (dirty) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    draft: DraftShortcut,
    folderOptions: List<String>,
    onChange: (DraftShortcut) -> Unit,
) {
    Text(
        "Shortcut ${index + 1}",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(bottom = 8.dp),
    )

    // --- Folder picker (from scanned folders; keep the current value even if not scanned) ---
    var expanded by remember { mutableStateOf(false) }
    val options = remember(folderOptions, draft.folderName) {
        (folderOptions + draft.folderName).filter { it.isNotBlank() }.distinct()
    }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = draft.folderName,
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
                        onChange(draft.copy(folderName = name))
                        expanded = false
                    },
                )
            }
        }
    }

    Spacer(Modifier.height(8.dp))

    // --- Appearance: letter vs icon (single choice) ---
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = draft.isLetter,
            onClick = { onChange(draft.copy(isLetter = true)) },
            label = { Text("Letter") },
        )
        FilterChip(
            selected = !draft.isLetter,
            onClick = { onChange(draft.copy(isLetter = false)) },
            label = { Text("Icon") },
        )
    }

    Spacer(Modifier.height(8.dp))

    if (draft.isLetter) {
        OutlinedTextField(
            value = draft.letter,
            onValueChange = { input ->
                // Single capital letter; keep the last letter typed, and allow clearing to empty
                // so it can be deleted and retyped.
                val ch = input.lastOrNull { it.isLetter() }?.uppercaseChar()?.toString().orEmpty()
                onChange(draft.copy(letter = ch))
            },
            singleLine = true,
            isError = draft.letter.isBlank(),
            supportingText = { Text(if (draft.letter.isBlank()) "Enter a letter A–Z" else "A–Z") },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Done,
            ),
            label = { Text("Letter") },
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShortcutIcons.curated.forEach { ci ->
                FilterChip(
                    selected = draft.iconKey == ci.key,
                    onClick = { onChange(draft.copy(iconKey = ci.key)) },
                    leadingIcon = { Icon(ci.vector, contentDescription = null) },
                    label = { Text(ci.label) },
                )
            }
        }
    }
}
