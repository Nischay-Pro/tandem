package app.tandemforactual.wear.link

import android.content.Context
import android.net.Uri
import app.tandemforactual.watch.WatchCodec
import app.tandemforactual.watch.WatchPaths
import app.tandemforactual.watch.WatchQuickAdd
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/** The watch's end of the Wearable Data Layer: the phone's summary in, quick adds and summary requests out. */
internal class PhoneLink(
    context: Context,
) {
    private val appContext = context.applicationContext

    /** The summary the phone last put in the Data Layer, or null when there is none. */
    suspend fun storedSummary(): ByteArray? {
        val uri =
            Uri
                .Builder()
                .scheme(PutDataRequest.WEAR_URI_SCHEME)
                .path(WatchPaths.SUMMARY)
                .build()
        val items = Wearable.getDataClient(appContext).getDataItems(uri).await()
        return try {
            items.firstOrNull()?.let { DataMapItem.fromDataItem(it).dataMap.getByteArray(WatchPaths.SUMMARY_KEY) }
        } finally {
            items.release()
        }
    }

    suspend fun requestSummary(): Boolean = sendToPhone(WatchPaths.SUMMARY_REQUEST, ByteArray(0))

    /** Sends [entry] to the phone; true once a phone took the message, which doesn't yet mean it was added. */
    suspend fun send(entry: WatchQuickAdd): Boolean = sendToPhone(WatchPaths.QUICK_ADD, WatchCodec.encodeQuickAdd(entry))

    private suspend fun sendToPhone(
        path: String,
        data: ByteArray,
    ): Boolean {
        val node = phone() ?: return false
        Wearable
            .getMessageClient(appContext)
            .sendMessage(node.id, path, data)
            .await()
        return true
    }

    private suspend fun phone(): Node? =
        Wearable
            .getCapabilityClient(appContext)
            .getCapability(WatchPaths.PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
            .await()
            .nodes
            .sortedByDescending { it.isNearby }
            .firstOrNull()
}
