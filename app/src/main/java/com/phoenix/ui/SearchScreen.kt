package com.phoenix.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phoenix.playback.MusicLibrary
import com.phoenix.playback.PlayerViewModel
import com.phoenix.playback.Track
import com.phoenix.radio.RadioBrowser
import com.phoenix.radio.RadioFavorites
import com.phoenix.radio.RadioStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shared search screen. [searchRadio] decides the corpus: all radio stations (opened from the
 * radio browse list, or from now-playing while a station plays) versus the phone's music
 * library (everywhere else). Radio results are a network fetch; library results are an
 * in-memory filter. Tapping a library song hands it back via [onPlayLibrarySong] so the caller
 * can switch the browsed folder and queue to the playlist that song lives in.
 */
@Composable
fun SearchScreen(
    vm: PlayerViewModel,
    searchRadio: Boolean,
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onPlayLibrarySong: (Track) -> Unit,
) {
    BackHandler { onBack() }

    var query by remember { mutableStateOf("") }
    val revision by MusicLibrary.revision.collectAsState()
    val favorites by RadioFavorites.favorites.collectAsState()

    var radioResults by remember { mutableStateOf<List<RadioStation>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    // Radio search hits the network — re-run whenever the query changes.
    LaunchedEffect(query, searchRadio) {
        if (!searchRadio) return@LaunchedEffect
        if (query.isBlank()) {
            radioResults = emptyList()
            loading = false
            return@LaunchedEffect
        }
        loading = true
        radioResults = withContext(Dispatchers.IO) {
            val results = RadioBrowser.search(query)
            (favorites.filter { it.name.contains(query, ignoreCase = true) } + results)
                .distinctBy { it.uuid }
        }
        loading = false
    }

    // Library search is a cheap in-memory filter across every song (null = no folder scope).
    val libraryResults = remember(query, revision, searchRadio) {
        if (searchRadio || query.isBlank()) emptyList()
        else MusicLibrary.search(query, null)
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Scaffold(
        bottomBar = { NowPlayingBar(vm, onOpenNowPlaying) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(if (searchRadio) "Search radio stations" else "Search songs") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                )
            }

            if (loading) CircularProgressIndicator(Modifier.padding(16.dp))

            LazyColumn(Modifier.fillMaxSize()) {
                if (searchRadio) {
                    itemsIndexed(radioResults, key = { _, s -> s.uuid }) { index, station ->
                        ListItem(
                            headlineContent = { Text(station.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { station.country?.let { Text(it, maxLines = 1) } },
                            leadingContent = { Icon(Icons.Filled.Radio, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth().clickableRow {
                                vm.playStation(radioResults, index)
                                onOpenNowPlaying()
                            },
                        )
                    }
                } else {
                    items(libraryResults, key = { it.id }) { track ->
                        ListItem(
                            headlineContent = { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { track.artist?.let { Text(it, maxLines = 1) } },
                            leadingContent = { TrackArtwork(track.albumArtUri) },
                            modifier = Modifier.fillMaxWidth().clickableRow {
                                onPlayLibrarySong(track)
                            },
                        )
                    }
                }
            }
        }
    }
}
