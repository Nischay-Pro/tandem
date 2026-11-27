package app.tandemforactual.wear.link

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.tandemforactual.watch.WatchQuickAdd
import app.tandemforactual.watch.WatchQuickAddResult
import app.tandemforactual.watch.WatchState
import app.tandemforactual.wear.data.WatchStore
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/** Quick adds go into the watch's queue first and leave it only when the phone answers. */
object QuickAdds {
    private const val WORK = "tandem-quick-add-send"

    /** Queues a new entry and tries to send it now; true when a phone took it. */
    suspend fun add(
        context: Context,
        amountMinor: Long,
        categoryId: String?,
        payee: String?,
    ): Boolean {
        val entry = WatchQuickAdd(UUID.randomUUID().toString(), amountMinor, categoryId, payee, System.currentTimeMillis())
        WatchStore.load(context).updateQueue { it.enqueue(entry) }
        return send(context) > 0
    }

    /** Sends every entry that's due, and plans the next attempt for the rest; returns how many a phone took. */
    suspend fun send(context: Context): Int {
        val store = WatchStore.load(context)
        val link = PhoneLink(context)
        var delivered = 0
        for (entry in store.queue.value.due(System.currentTimeMillis())) {
            val sent =
                try {
                    link.send(entry)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    false
                }
            store.updateQueue { it.sent(entry.entryId, System.currentTimeMillis()) }
            if (sent) delivered++ else break
        }
        schedule(context)
        return delivered
    }

    fun answered(
        context: Context,
        result: WatchQuickAddResult,
    ) {
        WatchStore.load(context).updateQueue { it.resolved(result) }
    }

    /** A new summary that isn't locked frees entries held for an unlock, and sends them. */
    suspend fun summaryArrived(context: Context) {
        val store = WatchStore.load(context)
        val state = store.summary.value?.state ?: return
        if (state != WatchState.LOCKED &&
            store.queue.value.items
                .any { it.waitingForUnlock }
        ) {
            store.updateQueue { it.unlocked() }
        }
        send(context)
    }

    private fun schedule(context: Context) {
        val next =
            WatchStore
                .load(context)
                .queue.value
                .nextAttemptAt() ?: return
        val delay = (next - System.currentTimeMillis()).coerceAtLeast(0)
        val request =
            OneTimeWorkRequestBuilder<QuickAddWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.NONE)
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, request)
    }
}

class QuickAddWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        QuickAdds.send(applicationContext)
        return Result.success()
    }
}
