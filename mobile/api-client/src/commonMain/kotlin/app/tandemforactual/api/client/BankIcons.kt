package app.tandemforactual.api.client

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Where downloaded bank icons, and domains that had none, are kept between launches. */
interface BankIconStore {
    suspend fun icon(host: String): BankIcon?

    suspend fun saveIcon(
        host: String,
        icon: BankIcon,
    )

    suspend fun missingSince(host: String): Long?

    suspend fun saveMissing(
        host: String,
        at: Long,
    )

    suspend fun clear()
}

/**
 * Bank icons by domain: each is fetched once, kept for offline use, and a domain without an icon isn't asked again
 * for a week (six hours when the site couldn't be reached), so a broken site never causes a stream of retries.
 */
class BankIcons(
    private val store: BankIconStore,
    private val fetcher: BankIconFetcher,
    private val now: () -> Long,
) {
    private val lock = Mutex()

    /** The cached icon for [domain], without touching the network. */
    suspend fun cached(domain: String): BankIcon? = bankIconHost(domain)?.let { store.icon(it) }

    /** The icon for [domain], downloading it when it isn't cached and the last attempt wasn't recent. */
    suspend fun icon(domain: String): BankIcon? {
        val host = bankIconHost(domain) ?: return null
        return lock.withLock {
            store.icon(host)?.let { return@withLock it }
            val missing = store.missingSince(host)
            if (missing != null && now() - missing < RETRY_AFTER_MILLIS) return@withLock null
            val icon =
                try {
                    fetcher.fetch(host)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    store.saveMissing(host, now() - RETRY_AFTER_MILLIS + UNREACHABLE_RETRY_MILLIS)
                    return@withLock null
                }
            if (icon == null) store.saveMissing(host, now()) else store.saveIcon(host, icon)
            icon
        }
    }

    /** Deletes every downloaded icon and every remembered miss. */
    suspend fun clear() = lock.withLock { store.clear() }

    companion object {
        const val RETRY_AFTER_MILLIS = 7L * 24 * 60 * 60 * 1000
        const val UNREACHABLE_RETRY_MILLIS = 6L * 60 * 60 * 1000
    }
}
