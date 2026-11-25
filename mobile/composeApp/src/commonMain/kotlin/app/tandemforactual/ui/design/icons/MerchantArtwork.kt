package app.tandemforactual.ui.design.icons

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.rememberResourceEnvironment

/** Keeps the [limit] most recently used values, dropping the least recently used beyond that. */
internal class RecentCache<K, V>(
    private val limit: Int,
) {
    private val values = LinkedHashMap<K, V>(limit, 0.75f, true)

    fun get(key: K): V? = synchronized(this) { values[key] }

    fun put(
        key: K,
        value: V,
    ) = synchronized(this) {
        values[key] = value
        if (values.size > limit) values.remove(values.keys.first())
    }

    val size: Int get() = synchronized(this) { values.size }
}

/** The most recently drawn merchant and bank logos, so scrolling back doesn't decode them again. */
internal val MerchantArtworkCache = RecentCache<DrawableResource, ImageBitmap>(256)

/**
 * A logo from the icon pack, read and decoded off the main thread the first time it is drawn, so lists of
 * transactions scroll without stopping to decode each new logo. Null until it is ready, so callers show their
 * fallback icon meanwhile; cached logos show straight away.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
internal fun rememberMerchantArtwork(resource: DrawableResource?): Painter? {
    val environment = rememberResourceEnvironment()
    val bitmap by produceState(resource?.let(MerchantArtworkCache::get), resource, environment) {
        if (resource == null || value != null) return@produceState
        value =
            withContext(Dispatchers.Default) {
                runCatching { getDrawableResourceBytes(environment, resource).decodeToImageBitmap() }
                    .getOrNull()
                    ?.also { MerchantArtworkCache.put(resource, it) }
            }
    }
    return bitmap?.let { remember(it) { BitmapPainter(it) } }
}
