package io.kazutoiris.pure.shot.ui.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Asynchronously loads and caches application icons.
 *
 * Icons are only loaded when a list row asks for them, rendered at a fixed size and kept in a small
 * LRU cache so scrolling a 300+ app list does not retain full-resolution bitmaps.
 */
class AppIconLoader(context: Context) {

    private val packageManager = (context.applicationContext ?: context).packageManager
    private val cache = LruCache<String, ImageBitmap>(MAX_CACHED_ICONS)

    suspend fun load(packageName: String): ImageBitmap? = withContext(Dispatchers.IO) {
        cache.get(packageName)?.let { return@withContext it }
        val icon = runCatching {
            packageManager.getApplicationIcon(packageName).toImageBitmap()
        }.getOrNull()
        if (icon != null) {
            cache.put(packageName, icon)
        }
        icon
    }

    fun clear() {
        cache.evictAll()
    }

    private fun Drawable.toImageBitmap(): ImageBitmap {
        val bitmap = Bitmap.createBitmap(ICON_SIZE_PX, ICON_SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, ICON_SIZE_PX, ICON_SIZE_PX)
        draw(canvas)
        return bitmap.asImageBitmap()
    }

    private companion object {
        const val MAX_CACHED_ICONS = 64
        const val ICON_SIZE_PX = 128
    }
}
