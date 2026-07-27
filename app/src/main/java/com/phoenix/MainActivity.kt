package com.phoenix

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.phoenix.playback.MusicLibrary
import com.phoenix.playback.PlayerViewModel
import com.phoenix.playback.Track
import com.phoenix.ui.BrowseScreen
import com.phoenix.ui.NowPlayingScreen
import com.phoenix.ui.RadioScreen
import com.phoenix.ui.SearchScreen
import com.phoenix.ui.SettingsScreen
import com.phoenix.ui.YouTubeScreen
import com.phoenix.ui.theme.PhoenixTheme
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {

    private val vm: PlayerViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // App access is never gated on any single grant; load whatever we can read.
        thread { MusicLibrary.load(applicationContext) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestPermissions()

        setContent {
            PhoenixTheme {
                PhoenixAppUi(vm)
            }
        }
    }

    private fun requestPermissions() {
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_AUDIO)
                add(Manifest.permission.READ_MEDIA_IMAGES) // car slideshow (grant happens on phone)
                add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
        permissionLauncher.launch(perms)
    }
}

private enum class Screen { Browse, Radio, YouTube, NowPlaying, Search, Settings }

@Composable
private fun PhoenixAppUi(vm: PlayerViewModel) {
    var screen by remember { mutableStateOf(Screen.Browse) }
    // null = the root folder list; otherwise the folder id currently open.
    var openFolder by remember { mutableStateOf<String?>(null) }
    // Search context + where to return when it closes.
    var searchRadio by remember { mutableStateOf(false) }
    var searchOrigin by remember { mutableStateOf(Screen.Browse) }

    // Jump-to-folder from a letter shortcut: navigate into it and start playing (shuffled).
    val goToFolder: (String) -> Unit = { folderName ->
        val folderId = MusicLibrary.folderIdByName(folderName)
        if (folderId != null) {
            openFolder = folderId
            screen = Screen.Browse
            vm.playFolderShuffled(folderId)
        }
    }

    // Open the shared search screen. [radio] picks the corpus; remember the current screen so
    // the back button returns there.
    val openSearch: (Boolean) -> Unit = { radio ->
        searchRadio = radio
        searchOrigin = screen
        screen = Screen.Search
    }

    // A song chosen from search: switch the browsed folder + the playback queue to the playlist
    // that song lives in, positioned at it, so next/shuffle-next stay within that playlist.
    val playLibrarySong: (Track) -> Unit = { track ->
        val folderId = track.relativePath.trimEnd('/')
        openFolder = folderId
        val queue = MusicLibrary.tracksInFolder(folderId)
        val index = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        vm.playTracks(queue, index)
        screen = Screen.NowPlaying
    }

    when (screen) {
        Screen.Browse -> BrowseScreen(
            vm = vm,
            openFolder = openFolder,
            onOpenFolder = { openFolder = it },
            onBackToRoot = { openFolder = null },
            onOpenRadio = { screen = Screen.Radio },
            onOpenYouTube = { screen = Screen.YouTube },
            onOpenNowPlaying = { screen = Screen.NowPlaying },
            onGoToFolder = goToFolder,
            onOpenSearch = openSearch,
            onOpenSettings = { screen = Screen.Settings },
        )
        Screen.Radio -> RadioScreen(
            vm = vm,
            onOpenNowPlaying = { screen = Screen.NowPlaying },
            onBack = { screen = Screen.Browse },
            onGoToFolder = goToFolder,
            onOpenYouTube = { screen = Screen.YouTube },
            onOpenSearch = openSearch,
        )
        Screen.YouTube -> YouTubeScreen(
            vm = vm,
            onOpenNowPlaying = { screen = Screen.NowPlaying },
            onBack = { screen = Screen.Browse },
        )
        Screen.NowPlaying -> NowPlayingScreen(
            vm = vm,
            onBack = { screen = Screen.Browse },
            // On the player, a letter jump-plays its folder but stays on the now-playing screen.
            onGoToFolder = { folderName ->
                MusicLibrary.folderIdByName(folderName)?.let { vm.playFolderShuffled(it) }
            },
            onOpenRadio = { screen = Screen.Radio },
            onOpenYouTube = { screen = Screen.YouTube },
            onOpenSearch = openSearch,
        )
        Screen.Search -> SearchScreen(
            vm = vm,
            searchRadio = searchRadio,
            onBack = { screen = searchOrigin },
            onOpenNowPlaying = { screen = Screen.NowPlaying },
            onPlayLibrarySong = playLibrarySong,
        )
        Screen.Settings -> SettingsScreen(
            onBack = { screen = Screen.Browse },
        )
    }
}
