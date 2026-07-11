package com.phoenix.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.phoenix.radio.RadioStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.LaunchedEffect

@Composable
fun RadioScreen(
    vm: PlayerViewModel,
    onOpenNowPlaying: () -> Unit,
    onBack: () -> Unit,
    onGoToFolder: (String) -> Unit,
) {
    val revision by MusicLibrary.revision.collectAsState()
    val favorites by RadioFavorites.favorites.collectAsState()

    var query by remember { mutableStateOf("") }
    var stations by remember { mutableStateOf<List<RadioStation>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    // Favorites-first when browsing; scoped to radio when searching. Re-runs on query/favorites.
    LaunchedEffect(query, favorites) {
        loading = true
        stations = withContext(Dispatchers.IO) {
            if (query.isBlank()) {
                RadioBrowser.browseStations(favorites)
            } else {
                val results = RadioBrowser.search(query)
                (favorites.filter { it.name.contains(query, ignoreCase = true) } + results)
                    .distinctBy { it.uuid }
            }
        }
        loading = false
    }

    Scaffold(
        bottomBar = { NowPlayingBar(vm, onOpenNowPlaying) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

            // Phone Radio bar: letters only (you're already on the radio view).
            LetterShortcutBar(onGoToFolder = onGoToFolder, revision = revision)

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search stations") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )

            if (loading) {
                CircularProgressIndicator(Modifier.padding(16.dp))
            }

            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(stations, key = { _, s -> s.uuid }) { index, station ->
                    val isFav = favorites.any { it.uuid == station.uuid }
                    ListItem(
                        headlineContent = { Text(station.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { station.country?.let { Text(it, maxLines = 1) } },
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
                            vm.playStation(stations, index)
                            onOpenNowPlaying()
                        },
                    )
                }
            }
        }
    }
}
