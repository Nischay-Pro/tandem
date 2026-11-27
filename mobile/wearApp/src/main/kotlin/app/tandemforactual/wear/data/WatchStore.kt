package app.tandemforactual.wear.data

import android.content.Context
import android.content.SharedPreferences
import app.tandemforactual.watch.QuickAddQueue
import app.tandemforactual.watch.WatchCodec
import app.tandemforactual.watch.WatchSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The last summary from the phone and the quick adds it hasn't confirmed, kept on the watch across restarts. */
object WatchStore {
    private const val FILE = "tandem_watch"
    private const val SUMMARY = "summary"
    private const val QUEUE = "queue"
    private const val COMPLICATION = "complication-"

    private var preferences: SharedPreferences? = null
    private val summaryState = MutableStateFlow<WatchSummary?>(null)
    private val queueState = MutableStateFlow(QuickAddQueue())

    val summary: StateFlow<WatchSummary?> = summaryState.asStateFlow()
    val queue: StateFlow<QuickAddQueue> = queueState.asStateFlow()

    @Synchronized
    fun load(context: Context): WatchStore {
        if (preferences == null) {
            val stored = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            preferences = stored
            summaryState.value = stored.getString(SUMMARY, null)?.let { WatchCodec.decodeSummary(it.encodeToByteArray()) }
            queueState.value = QuickAddQueue(WatchCodec.decodeQueue(stored.getString(QUEUE, null)))
        }
        return this
    }

    /** Keeps the phone's latest summary, or forgets it when the phone cleared it; true when it changed. */
    @Synchronized
    fun saveSummary(bytes: ByteArray?): Boolean {
        val summary = bytes?.let(WatchCodec::decodeSummary)
        if (bytes != null && summary == null) return false
        preferences
            ?.edit()
            ?.apply {
                if (bytes == null) remove(SUMMARY) else putString(SUMMARY, bytes.decodeToString())
            }?.apply()
        val changed = summaryState.value != summary
        summaryState.value = summary
        return changed
    }

    @Synchronized
    fun updateQueue(change: (QuickAddQueue) -> QuickAddQueue): QuickAddQueue {
        val updated = change(queueState.value)
        queueState.value = updated
        preferences?.edit()?.putString(QUEUE, WatchCodec.encodeQueue(updated.items))?.apply()
        return updated
    }

    fun complicationCategory(complicationId: Int): String? = preferences?.getString(COMPLICATION + complicationId, null)

    fun setComplicationCategory(
        complicationId: Int,
        categoryId: String?,
    ) {
        preferences
            ?.edit()
            ?.apply {
                if (categoryId == null) remove(COMPLICATION + complicationId) else putString(COMPLICATION + complicationId, categoryId)
            }?.apply()
    }
}
