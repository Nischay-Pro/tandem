package app.tandemforactual.android.watch

import app.tandemforactual.watch.WatchQuickAdd
import app.tandemforactual.watch.WatchQuickAddResult
import app.tandemforactual.wearable.WatchListenerService

/** Wakes for the watch's quick adds and summary requests, and when a watch with Tandem is paired or removed. */
class PhoneWatchService : WatchListenerService() {
    override suspend fun quickAdd(entry: WatchQuickAdd): WatchQuickAddResult = PhoneWatch.quickAdd(applicationContext, entry)

    override suspend fun summaryRequested() = PhoneWatch.publish(applicationContext, afterUnlock = false)

    override fun pairingChanged() = PhoneWatch.checkPairing(applicationContext)
}
