package app.tandemforactual.ui.design.icons

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.rememberResourceEnvironment

/** Keeps the [limit] most recently used values, dropping the least recently used beyond that. Used from one thread. */
internal class RecentCache<K, V>(
    private val limit: Int,
) {
    private val values = LinkedHashMap<K, V>()

    fun get(key: K): V? {
        val value = values.remove(key) ?: return null
        values[key] = value
        return value
    }

    fun put(
        key: K,
        value: V,
    ) {
        values.remove(key)
        values[key] = value
        if (values.size > limit) values.remove(values.keys.first())
    }

    val size: Int get() = values.size
}

/** The most recently drawn merchant and bank logos, so scrolling back doesn't decode them again. Main thread only. */
internal val MerchantArtworkCache = RecentCache<DrawableResource, LogoArtwork>(256)

/**
 * A logo from the icon pack, read and decoded off the main thread the first time it is drawn, so lists of
 * transactions scroll without stopping to decode each new logo. Null until it is ready, so callers show their
 * fallback icon meanwhile; cached logos show straight away.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
internal fun rememberMerchantArtwork(resource: DrawableResource?): LogoPainter? {
    val environment = rememberResourceEnvironment()
    val cached = remember(resource) { resource?.let(MerchantArtworkCache::get) }
    val decoded by produceState<Pair<DrawableResource, LogoArtwork>?>(null, resource, environment) {
        if (resource == null || cached != null) return@produceState
        val artwork =
            withContext(Dispatchers.Default) {
                runCatching { getDrawableResourceBytes(environment, resource).decodeToImageBitmap().logoArtwork() }.getOrNull()
            } ?: return@produceState
        MerchantArtworkCache.put(resource, artwork)
        value = resource to artwork
    }
    val artwork = cached ?: decoded?.takeIf { it.first == resource }?.second
    return artwork?.let { remember(it) { LogoPainter(it) } }
}
