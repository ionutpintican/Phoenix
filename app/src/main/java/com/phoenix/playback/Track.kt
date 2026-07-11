package com.phoenix.playback

import android.net.Uri

/**
 * One playable local audio file, as read from [android.provider.MediaStore].
 *
 * [bucketId] / [bucketName] are the MediaStore "bucket" (the immediate parent folder).
 * [relativePath] is the full relative path (e.g. `Music/Leni/`) — used to resolve
 * folders-of-subfolders such as Audiobooks, and to prefer the SD-card copy of a folder.
 */
data class Track(
    val id: Long,
    val title: String,
    val artist: String?,
    val album: String?,
    val uri: Uri,
    val albumArtUri: Uri?,
    val bucketId: String,
    val bucketName: String,
    val relativePath: String,
    val dateModified: Long,
    val duration: Long,
) {
    val mediaId: String get() = "song:$id"
}
