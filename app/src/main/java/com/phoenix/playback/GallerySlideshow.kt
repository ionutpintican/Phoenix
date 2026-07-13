package com.phoenix.playback

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

/**
 * Feeds the car now-playing artwork slideshow. Reads all gallery image URIs from
 * MediaStore once, shuffles them, and hands them out one at a time — reshuffling each
 * time it wraps so every pass is a fresh random order. Car-only; the phone UI never
 * uses this.
 */
class GallerySlideshow {

    private var photos: List<Uri> = emptyList()
    private var order: List<Int> = emptyList()
    private var cursor = 0

    val hasPhotos: Boolean get() = photos.isNotEmpty()

    /** Load on a background thread — touches the content resolver. */
    fun load(context: Context) {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val out = ArrayList<Uri>()
        // Tolerate a missing/partial Images grant: the query throws SecurityException when the
        // permission isn't held. Swallow it and leave the pool empty — a later grant triggers a
        // reload (see PlaybackService), rather than crashing the loader coroutine.
        runCatching {
            context.contentResolver.query(collection, projection, null, null, null)?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (c.moveToNext()) {
                    out += ContentUris.withAppendedId(collection, c.getLong(idCol))
                }
            }
        }
        // Swap in the new pool atomically: load() runs on IO while next() reads on Main, so
        // publish photos + order together to avoid handing out a stale index into an old list.
        synchronized(this) {
            photos = out
            reshuffle()
        }
    }

    /** The next random photo, or null if there are none. */
    @Synchronized
    fun next(): Uri? {
        if (photos.isEmpty()) return null
        if (cursor >= order.size) reshuffle()
        return photos[order[cursor++]]
    }

    private fun reshuffle() {
        order = photos.indices.shuffled()
        cursor = 0
    }
}
