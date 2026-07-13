package com.phoenix.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phoenix.playback.MusicLibrary
import com.phoenix.playback.PlayerViewModel
import kotlin.concurrent.thread

@Composable
fun BrowseScreen(
    vm: PlayerViewModel,
    openFolder: String?,
    onOpenFolder: (String) -> Unit,
    onBackToRoot: () -> Unit,
    onOpenRadio: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onGoToFolder: (String) -> Unit,
    onOpenSearch: (Boolean) -> Unit,
) {
    val revision by MusicLibrary.revision.collectAsState()
    // The folder song list is sorted by the global sort mode, so recompute it when that
    // changes — otherwise tapping a sort button only re-highlights the rail while the visible
    // list keeps its stale order.
    val sortMode by MusicLibrary.sortMode.collectAsState()
    // Shuffle is a single shared player state (same toggle the now-playing screen and the car
    // use). Surfacing it here lets the user pick order before playing from the browsed list.
    val shuffle by vm.shuffle.collectAsState()
    val context = LocalContext.current

    BackHandler(enabled = openFolder != null) { onBackToRoot() }

    Scaffold(
        bottomBar = { NowPlayingBar(vm, onOpenNowPlaying) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

            LetterShortcutBar(
                onGoToFolder = onGoToFolder,
                showRadioButton = true,
                onOpenRadio = onOpenRadio,
                revision = revision,
            )

            SortRail(vm)

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (openFolder == null) "Folders" else openFolder.substringAfterLast('/'),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onOpenSearch(false) }) {
                    Icon(Icons.Filled.Search, contentDescription = "Search")
                }
                IconButton(onClick = { vm.toggleShuffle() }) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = if (shuffle) "Shuffle on" else "Shuffle off",
                        tint = if (shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = { thread { MusicLibrary.rescan(context) } }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Rescan")
                }
            }

            if (openFolder == null) {
                val folders = remember(revision) { MusicLibrary.browseFolders() }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(folders, key = { it.id }) { folder ->
                        ListItem(
                            headlineContent = { Text(folder.name) },
                            leadingContent = { Icon(Icons.Filled.Folder, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth().clickableRow { onOpenFolder(folder.id) },
                        )
                    }
                }
            } else {
                val tracks = remember(revision, openFolder, sortMode) { MusicLibrary.tracksInFolder(openFolder) }
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                        ListItem(
                            headlineContent = { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { track.artist?.let { Text(it, maxLines = 1) } },
                            leadingContent = { TrackArtwork(track.albumArtUri) },
                            modifier = Modifier.fillMaxWidth().clickableRow {
                                vm.playTracks(tracks, index)
                                onOpenNowPlaying()
                            },
                        )
                    }
                }
            }
        }
    }
}

/** A track's album art thumbnail, falling back to the music-note icon when it can't load. */
@Composable
fun TrackArtwork(uri: Uri?) {
    val art = rememberArtwork(uri)
    if (art != null) {
        Image(
            bitmap = art,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)),
        )
    } else {
        Icon(Icons.Filled.MusicNote, contentDescription = null)
    }
}

/** Four sort icons that set the global sort mode (shared with the car). */
@Composable
private fun SortRail(vm: PlayerViewModel) {
    val current by vm.sortMode.collectAsState()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SortButton("📅↑", MusicLibrary.SortMode.DATE_ASC, current, vm)
        SortButton("📅↓", MusicLibrary.SortMode.DATE_DESC, current, vm)
        SortButton("A→Z", MusicLibrary.SortMode.NAME_ASC, current, vm)
        SortButton("Z→A", MusicLibrary.SortMode.NAME_DESC, current, vm)
    }
}

@Composable
private fun SortButton(
    label: String,
    mode: MusicLibrary.SortMode,
    current: MusicLibrary.SortMode,
    vm: PlayerViewModel,
) {
    val selected = mode == current
    IconButton(onClick = { vm.setSortMode(mode) }) {
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/**
 * The shared mini now-playing bar. Its content is pushed above the system nav buttons with
 * [navigationBarsPadding] (the edge-to-edge inset fix) while the colored background still
 * fills to the screen edge. The car never uses this composable.
 */
@Composable
fun NowPlayingBar(vm: PlayerViewModel, onOpen: () -> Unit) {
    val mediaId by vm.currentMediaId.collectAsState()
    if (mediaId == null) return

    val title by vm.title.collectAsState()
    val artist by vm.artist.collectAsState()
    val playing by vm.isPlaying.collectAsState()

    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clickableRow { onOpen() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                artist?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall) }
            }
            IconButton(onClick = { vm.togglePlayPause() }) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                )
            }
        }
    }
}
