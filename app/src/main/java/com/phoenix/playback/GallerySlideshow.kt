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
        context.contentResolver.query(collection, projection, null, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (c.moveToNext()) {
                out += ContentUris.withAppendedId(collection, c.getLong(idCol))
            }
        }
        photos = out
        reshuffle()
    }

    /** The next random photo, or null if there are none. */
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
