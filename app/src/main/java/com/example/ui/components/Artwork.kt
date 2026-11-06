package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads the cover picture embedded in an audio file (ID3 / MP4 / FLAC tags). */
object ArtworkLoader {
    private const val MAX_PX = 800
    private val cache = LruCache<String, Bitmap>(16)
    private val missing = HashSet<String>()

    suspend fun load(context: Context, uriString: String): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(uriString)?.let { return@withContext it }
        if (synchronized(missing) { uriString in missing }) return@withContext null

        val uri = Uri.parse(uriString)
        val bitmap = runCatching { fromEmbeddedTag(context, uri) }.getOrNull()
            ?: if (Build.VERSION.SDK_INT >= 29) {
                runCatching { context.contentResolver.loadThumbnail(uri, Size(MAX_PX, MAX_PX), null) }.getOrNull()
            } else null

        if (bitmap != null) cache.put(uriString, bitmap) else synchronized(missing) { missing.add(uriString) }
        bitmap
    }

    private fun fromEmbeddedTag(context: Context, uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val bytes = retriever.embeddedPicture ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / sample > MAX_PX * 2 || bounds.outHeight / sample > MAX_PX * 2) sample *= 2
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        } finally {
            retriever.release()
        }
    }
}

/** Returns the embedded cover art for a track, or null while loading / if it has none. */
@Composable
fun rememberTrackArtwork(contentUri: String?): ImageBitmap? {
    val appContext = LocalContext.current.applicationContext
    val art by produceState<ImageBitmap?>(initialValue = null, contentUri) {
        value = null
        value = contentUri?.let { ArtworkLoader.load(appContext, it)?.asImageBitmap() }
    }
    return art
}
