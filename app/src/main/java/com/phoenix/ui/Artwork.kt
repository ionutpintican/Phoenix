package com.phoenix.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal artwork loader for the phone UI. This build deliberately ships no image-loading
 * library, so album art (content:// MediaStore URIs), radio favicons (http/https) and the
 * car slideshow photos (content:// gallery URIs) are decoded here by hand on a background
 * thread and cached in memory.
 *
 * The car (Android Auto) never uses this — Media3's own BitmapLoader renders artwork there.
 */
private val artCache = LruCache<String, ImageBitmap>(64)

/**
 * Load [uri] as an [ImageBitmap], or null while loading / on failure (callers fall back to a
 * placeholder). Re-decodes only on a URI change and reuses the cache, so scrolling the browse
 * list — or the now-playing slideshow flipping back to a repeated photo — doesn't reload.
 */
@Composable
fun rememberArtwork(uri: Uri?): ImageBitmap? {
    val context = LocalContext.current
    val key = uri?.toString()
    var image by remember(key) { mutableStateOf(key?.let { artCache.get(it) }) }
    LaunchedEffect(key) {
        if (image != null || uri == null || key == null) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) { loadArtwork(context, uri) }
        if (loaded != null) {
            artCache.put(key, loaded)
            image = loaded
        }
    }
    return image
}

private fun loadArtwork(context: Context, uri: Uri): ImageBitmap? = runCatching {
    val bytes = when (uri.scheme) {
        "http", "https" -> {
            val conn = (URL(uri.toString()).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                instanceFollowRedirects = true
            }
            conn.inputStream.use { it.readBytes() }
        }
        // content:// (album art, gallery photos) and file:// — read through the resolver.
        else -> context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } ?: return null

    // Two-pass decode: measure first, then downsample so full-res photos / album art don't
    // blow the bitmap budget on a list of thumbnails.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val opts = BitmapFactory.Options().apply {
        inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, target = 1024)
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
}.getOrNull()

private fun sampleSize(width: Int, height: Int, target: Int): Int {
    if (width <= 0 || height <= 0) return 1
    var sample = 1
    var w = width
    var h = height
    while (w / 2 >= target && h / 2 >= target) {
        w /= 2
        h /= 2
        sample *= 2
    }
    return sample
}
