package com.phoenix.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phoenix.R
import com.phoenix.playback.MusicLibrary
import com.phoenix.playback.PlayerViewModel
import com.phoenix.radio.RadioBrowser
import com.phoenix.radio.RadioFavorites

@Composable
fun NowPlayingScreen(
    vm: PlayerViewModel,
    onBack: () -> Unit,
    onGoToFolder: (String) -> Unit,
    onOpenRadio: () -> Unit,
    onOpenYouTube: () -> Unit,
    onOpenSearch: (Boolean) -> Unit,
) {
    BackHandler { onBack() }

    val title by vm.title.collectAsState()
    val artist by vm.artist.collectAsState()
    val playing by vm.isPlaying.collectAsState()
    val shuffle by vm.shuffle.collectAsState()
    val mediaId by vm.currentMediaId.collectAsState()
    val artworkUri by vm.artwork.collectAsState()
    val position by vm.position.collectAsState()
    val duration by vm.duration.collectAsState()
    val favorites by RadioFavorites.favorites.collectAsState()

    val isRadio = mediaId?.startsWith("radio:") == true
    val isFav = isRadio && favorites.any { "radio:${it.uuid}" == mediaId }
    val revision by MusicLibrary.revision.collectAsState()

    Scaffold { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // L / A / H / Au + Radio row on the now-playing screen, same as the browse bar.
            LetterShortcutBar(
                onGoToFolder = onGoToFolder,
                showRadioButton = true,
                onOpenRadio = onOpenRadio,
                showYouTubeButton = true,
                onOpenYouTube = onOpenYouTube,
                revision = revision,
            )

            // Album art / radio favicon for the current track. The playback service rotates
            // this URI — the track's own artwork first, then gallery photos — so this view
            // follows that slideshow. Falls back to a music-note placeholder while nothing has
            // loaded (or when the image can't be decoded).
            val artwork = rememberArtwork(artworkUri)
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(0.8f).aspectRatio(1f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (artwork != null) {
                        Image(
                            bitmap = artwork,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            Icons.Filled.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(96.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Text(
                title ?: "Nothing playing",
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 24.dp),
            )
            artist?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            // Seek bar — songs only (radio has no length, so duration is 0). While the thumb is
            // held we show the dragged position and only commit the seek on release, so the
            // 500ms position poll doesn't yank the thumb back mid-drag.
            if (duration > 0) {
                var scrubbing by remember { mutableStateOf(false) }
                var scrubFraction by remember { mutableStateOf(0f) }
                val fraction =
                    if (scrubbing) scrubFraction else (position.toFloat() / duration).coerceIn(0f, 1f)
                Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Slider(
                        value = fraction,
                        onValueChange = { scrubbing = true; scrubFraction = it },
                        onValueChangeFinished = {
                            vm.seekTo((scrubFraction * duration).toLong())
                            scrubbing = false
                        },
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(formatTime((fraction * duration).toLong()), style = MaterialTheme.typography.labelMedium)
                        Text(formatTime(duration), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onOpenSearch(isRadio) }) {
                    Icon(Icons.Filled.Search, contentDescription = "Search")
                }
                IconButton(onClick = { vm.toggleShuffle() }) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = { vm.previous() }) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(40.dp))
                }
                IconButton(onClick = { vm.togglePlayPause() }) {
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                        modifier = Modifier.size(56.dp),
                    )
                }
                IconButton(onClick = { vm.next() }) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next", modifier = Modifier.size(40.dp))
                }
                // Heart shown only while a radio station is playing.
                if (isRadio) {
                    IconButton(onClick = {
                        val id = mediaId ?: return@IconButton
                        RadioBrowser.stationByMediaId(id, favorites)?.let { RadioFavorites.toggle(it) }
                    }) {
                        Icon(
                            painter = painterResource(if (isFav) R.drawable.ic_favorite else R.drawable.ic_favorite_border),
                            contentDescription = if (isFav) "Unfavorite" else "Favorite",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/** ms → "m:ss" for the seek-bar time labels. */
private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0L)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
