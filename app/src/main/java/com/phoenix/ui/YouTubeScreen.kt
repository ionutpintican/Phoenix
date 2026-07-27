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
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phoenix.playback.PlayerViewModel
import com.phoenix.youtube.YouTubeBrowser
import com.phoenix.youtube.YouTubePlaylistRef
import com.phoenix.youtube.YouTubePlaylists
import com.phoenix.youtube.YouTubeTrack
import com.phoenix.youtube.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The phone YouTube screen. The single text box does double duty: the **+** button adds it as a
 * playlist (parsing a playlist link/id), the **🔍** button runs a plain YouTube video search on
 * it. Below sits either the saved-playlist list or the current search results; tapping a playlist
 * browses into its tracks. Everything plays through the same session, so the car's YouTube tab and
 * the now-playing bar stay in sync. The car browse tree is unchanged.
 */
@Composable
fun YouTubeScreen(
    vm: PlayerViewModel,
    onOpenNowPlaying: () -> Unit,
    onBack: () -> Unit,
) {
    val playlists by YouTubePlaylists.playlists.collectAsState()
    val scope = rememberCoroutineScope()

    var openPlaylist by remember { mutableStateOf<YouTubePlaylistRef?>(null) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var searchResults by remember { mutableStateOf<List<YouTubeTrack>?>(null) }
    var searching by remember { mutableStateOf(false) }

    BackHandler {
        when {
            openPlaylist != null -> openPlaylist = null
            searchResults != null -> searchResults = null
            else -> onBack()
        }
    }

    Scaffold(
        bottomBar = { NowPlayingBar(vm, onOpenNowPlaying) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            val open = openPlaylist
            if (open != null) {
                PlaylistTracks(vm, open, onOpenNowPlaying)
                return@Column
            }

            Text(
                "YouTube",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            // Shared box: + adds a playlist (link/id), magnifier searches YouTube for videos.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it; error = null },
                    label = { Text("Playlist link, or search text") },
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    val id = YouTubeBrowser.extractPlaylistId(input)
                    when {
                        id == null -> error = "Not a playlist link"
                        YouTubePlaylists.contains(id) -> { error = "Already added"; input = "" }
                        else -> {
                            YouTubePlaylists.add(YouTubePlaylistRef(id, id))
                            input = ""
                            searchResults = null
                            scope.launch {
                                val title = withContext(Dispatchers.IO) { YouTubeBrowser.playlistTitle(id) }
                                if (title != null) YouTubePlaylists.updateTitle(id, title)
                                withContext(Dispatchers.IO) { YouTubeBrowser.playlistTracks(id) }
                            }
                        }
                    }
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add playlist")
                }
                IconButton(onClick = {
                    val q = input.trim()
                    if (q.isEmpty()) {
                        error = "Enter search text"
                    } else {
                        error = null
                        searching = true
                        scope.launch {
                            val res = withContext(Dispatchers.IO) { YouTubeBrowser.search(q) }
                            searchResults = res
                            searching = false
                        }
                    }
                }) {
                    Icon(Icons.Filled.Search, contentDescription = "Search YouTube")
                }
            }
            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            if (searching) CircularProgressIndicator(Modifier.padding(16.dp))

            val results = searchResults
            if (results != null) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Search results",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { searchResults = null }) { Text("Clear") }
                }
                if (results.isEmpty() && !searching) {
                    Text(
                        "No results.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                TrackList(results) { index ->
                    vm.playYouTube(results, index)
                    onOpenNowPlaying()
                }
            } else {
                PlaylistList(playlists, onOpen = { openPlaylist = it })
            }
        }
    }
}

/** The saved-playlist list, each removable, tapping to browse its tracks. */
@Composable
private fun PlaylistList(
    playlists: List<YouTubePlaylistRef>,
    onOpen: (YouTubePlaylistRef) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(playlists, key = { it.id }) { pl ->
            ListItem(
                headlineContent = { Text(pl.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingContent = {
                    Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null)
                },
                trailingContent = {
                    IconButton(onClick = { YouTubePlaylists.remove(pl.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove playlist")
                    }
                },
                modifier = Modifier.fillMaxWidth().clickableRow { onOpen(pl) },
            )
        }
    }
}

/** One playlist's tracks, loaded on demand. Tapping a track plays the whole playlist from it. */
@Composable
private fun PlaylistTracks(
    vm: PlayerViewModel,
    playlist: YouTubePlaylistRef,
    onOpenNowPlaying: () -> Unit,
) {
    var tracks by remember(playlist.id) { mutableStateOf<List<YouTubeTrack>>(emptyList()) }
    var loading by remember(playlist.id) { mutableStateOf(true) }

    LaunchedEffect(playlist.id) {
        loading = true
        tracks = withContext(Dispatchers.IO) { YouTubeBrowser.playlistTracks(playlist.id) }
        loading = false
    }

    Text(
        playlist.title,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )

    if (loading) {
        CircularProgressIndicator(Modifier.padding(16.dp))
    } else if (tracks.isEmpty()) {
        Text(
            "Couldn't load this playlist. It may be private, or YouTube extraction may need an update.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }

    TrackList(tracks) { index ->
        vm.playYouTube(tracks, index)
        onOpenNowPlaying()
    }
}

/** Shared vertical list of YouTube tracks (playlist tracks or search hits). */
@Composable
private fun TrackList(tracks: List<YouTubeTrack>, onPlay: (Int) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(tracks, key = { _, t -> t.videoId }) { index, track ->
            ListItem(
                headlineContent = { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    val subtitle = listOfNotNull(
                        track.artist,
                        formatDuration(track.durationSec).ifBlank { null },
                    ).joinToString(" · ")
                    if (subtitle.isNotBlank()) Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                leadingContent = { Icon(Icons.Filled.MusicNote, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().clickableRow { onPlay(index) },
            )
        }
    }
}
