package app.tandemforactual.wear.link

import android.content.Context
import app.tandemforactual.watch.WatchCodec
import app.tandemforactual.watch.WatchPaths
import app.tandemforactual.wear.WatchSurfaces
import app.tandemforactual.wear.data.WatchStore
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/** Takes the phone's summaries and answers in the background, and sends waiting quick adds once the phone is back. */
class WatchDataService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        val summaries =
            events
                .filter { it.dataItem.uri.path == WatchPaths.SUMMARY }
                .map { event ->
                    if (event.type == DataEvent.TYPE_DELETED) {
                        null
                    } else {
                        DataMapItem.fromDataItem(event.dataItem).dataMap.getByteArray(WatchPaths.SUMMARY_KEY)
                    }
                }
        if (summaries.isEmpty()) return
        receive(this, summaries.last())
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WatchPaths.QUICK_ADD_RESULT) return
        WatchCodec.decodeResult(event.data)?.let { QuickAdds.answered(this, it) }
    }

    override fun onCapabilityChanged(info: CapabilityInfo) {
        if (info.nodes.isEmpty()) return
        runBlocking { withTimeoutOrNull(SEND_MS) { QuickAdds.send(this@WatchDataService) } }
    }

    companion object {
        private const val SEND_MS = 20_000L

        /** Stores a summary, or clears it when [bytes] is null, then redraws tiles and complications. */
        fun receive(
            context: Context,
            bytes: ByteArray?,
        ) {
            val store = WatchStore.load(context)
            if (store.saveSummary(bytes)) WatchSurfaces.refresh(context)
            runBlocking { withTimeoutOrNull(SEND_MS) { QuickAdds.summaryArrived(context) } }
        }
    }
}
