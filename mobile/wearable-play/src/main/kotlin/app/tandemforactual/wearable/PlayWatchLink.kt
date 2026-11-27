package app.tandemforactual.wearable

import android.content.Context
import android.net.Uri
import app.tandemforactual.watch.WatchCodec
import app.tandemforactual.watch.WatchLink
import app.tandemforactual.watch.WatchPaths
import app.tandemforactual.watch.WatchQuickAddResult
import app.tandemforactual.watch.WatchSummary
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/** The watch connection through Google Play services' Wearable Data Layer. */
class PlayWatchLink(
    context: Context,
) : WatchLink {
    private val appContext = context.applicationContext

    override suspend fun publish(summary: WatchSummary?) {
        val client = Wearable.getDataClient(appContext)
        if (summary == null) {
            val uri =
                Uri
                    .Builder()
                    .scheme(PutDataRequest.WEAR_URI_SCHEME)
                    .path(WatchPaths.SUMMARY)
                    .build()
            client.deleteDataItems(uri).await()
            return
        }
        val request = PutDataMapRequest.create(WatchPaths.SUMMARY)
        request.dataMap.putByteArray(WatchPaths.SUMMARY_KEY, WatchCodec.encode(summary))
        client.putDataItem(request.asPutDataRequest().setUrgent()).await()
    }

    override suspend fun hasWatch(): Boolean =
        Wearable
            .getCapabilityClient(appContext)
            .getCapability(WatchPaths.WATCH_CAPABILITY, CapabilityClient.FILTER_ALL)
            .await()
            .nodes
            .isNotEmpty()

    suspend fun reply(
        nodeId: String,
        result: WatchQuickAddResult,
    ) {
        Wearable
            .getMessageClient(appContext)
            .sendMessage(nodeId, WatchPaths.QUICK_ADD_RESULT, WatchCodec.encodeResult(result))
            .await()
    }
}
