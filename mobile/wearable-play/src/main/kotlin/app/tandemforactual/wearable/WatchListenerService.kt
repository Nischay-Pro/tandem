package app.tandemforactual.wearable

import app.tandemforactual.watch.WatchCodec
import app.tandemforactual.watch.WatchPaths
import app.tandemforactual.watch.WatchQuickAdd
import app.tandemforactual.watch.WatchQuickAddResult
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Receives the watch's messages on Play services' background thread and answers each quick add, so the app only
 * decides what to do with them.
 */
abstract class WatchListenerService : WearableListenerService() {
    protected abstract suspend fun quickAdd(entry: WatchQuickAdd): WatchQuickAddResult

    protected abstract suspend fun summaryRequested()

    protected abstract fun pairingChanged()

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            WatchPaths.QUICK_ADD -> {
                val entry = WatchCodec.decodeQuickAdd(event.data) ?: return
                runBlocking {
                    withTimeoutOrNull(HANDLE_MS) {
                        val result = quickAdd(entry)
                        runCatching { PlayWatchLink(this@WatchListenerService).reply(event.sourceNodeId, result) }
                    }
                }
            }

            WatchPaths.SUMMARY_REQUEST -> {
                runBlocking { withTimeoutOrNull(HANDLE_MS) { summaryRequested() } }
            }
        }
    }

    override fun onCapabilityChanged(info: CapabilityInfo) {
        pairingChanged()
    }

    private companion object {
        const val HANDLE_MS = 20_000L
    }
}
