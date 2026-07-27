package com.phoenix.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phoenix.R
import com.phoenix.playback.MusicLibrary
import com.phoenix.playback.PlayerViewModel
import com.phoenix.radio.RadioBrowser
import com.phoenix.radio.RadioFavorites
import com.phoenix.radio.RadioRecents
import com.phoenix.radio.RadioStation
import com.phoenix.radio.formatVotes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.LaunchedEffect

@Composable
fun RadioScreen(
    vm: PlayerViewModel,
    onOpenNowPlaying: () -> Unit,
    onBack: () -> Unit,
    onGoToFolder: (String) -> Unit,
    onOpenSearch: (Boolean) -> Unit,
) {
    val revision by MusicLibrary.revision.collectAsState()
    val favorites by RadioFavorites.favorites.collectAsState()
    val recents by RadioRecents.recents.collectAsState()

    var sections by remember { mutableStateOf<List<RadioBrowser.RadioSection>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    // The browse list, grouped: favorites, then recently played, then all stations. Searching is
    // done through the search icon (which opens the shared search screen in radio mode).
    LaunchedEffect(favorites, recents) {
        loading = true
        sections = withContext(Dispatchers.IO) { RadioBrowser.browseSections(favorites, recents) }
        loading = false
    }

    // Flat queue so tapping any row plays the full list starting at that station.
    val queue = remember(sections) { sections.flatMap { it.stations } }

    Scaffold(
        bottomBar = { NowPlayingBar(vm, onOpenNowPlaying) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Radio", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { onOpenSearch(true) }) {
                    Icon(Icons.Filled.Search, contentDescription = "Search stations")
                }
            }

            // Phone Radio bar: keep the Radio button visible for parity with the other screens.
            LetterShortcutBar(
                onGoToFolder = onGoToFolder,
                showRadioButton = true,
                revision = revision,
            )

            if (loading) {
                CircularProgressIndicator(Modifier.padding(16.dp))
            }

            LazyColumn(Modifier.fillMaxSize()) {
                sections.forEach { section ->
                    item(key = "header:${section.title}") {
                        Text(
                            section.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(section.stations, key = { it.uuid }) { station ->
                        val isFav = favorites.any { it.uuid == station.uuid }
                        ListItem(
                            headlineContent = { Text(station.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { StationSubtitle(station) },
                            leadingContent = { Icon(Icons.Filled.Radio, contentDescription = null) },
                            trailingContent = {
                                IconButton(onClick = { RadioFavorites.toggle(station) }) {
                                    Icon(
                                        painter = painterResource(
                                            if (isFav) R.drawable.ic_favorite else R.drawable.ic_favorite_border
                                        ),
                                        contentDescription = if (isFav) "Unfavorite" else "Favorite",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth().clickableRow {
                                vm.playStation(queue, queue.indexOfFirst { it.uuid == station.uuid }.coerceAtLeast(0))
                                onOpenNowPlaying()
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Row subtitle: country, an OK badge when the stream was reachable at last check, and the
 *  station's popularity (vote count). Any piece that's missing is simply left out. Shared by the
 *  radio browse list and the radio search results so both rows read the same. */
@Composable
internal fun StationSubtitle(station: RadioStation) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        station.country?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        if (station.lastCheckOk == true) {
            Text(
                "OK",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (station.votes > 0) {
            Text(
                "▲ ${formatVotes(station.votes)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
