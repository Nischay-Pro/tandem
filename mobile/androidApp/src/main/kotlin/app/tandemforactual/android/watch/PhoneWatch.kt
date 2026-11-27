package app.tandemforactual.android.watch

import android.content.Context
import app.tandemforactual.android.TandemProcess
import app.tandemforactual.android.tasker.AutomationCalls
import app.tandemforactual.android.tasker.AutomationNotifications
import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.ui.core.watch.WATCH_SOURCE
import app.tandemforactual.ui.core.watch.WatchPairing
import app.tandemforactual.ui.core.watch.WatchSettings
import app.tandemforactual.ui.core.watch.addFromWatch
import app.tandemforactual.ui.core.watch.watchSummary
import app.tandemforactual.watch.WatchLink
import app.tandemforactual.watch.WatchQuickAdd
import app.tandemforactual.watch.WatchQuickAddResult
import app.tandemforactual.watch.WatchQuickAddStatus
import app.tandemforactual.wearable.PlayWatchLink
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Keeps a paired watch's summary current and adds what it sends; nothing here uses the network. */
internal object PhoneWatch {
    private const val PUBLISH_MS = 15_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun link(context: Context): WatchLink = PlayWatchLink(context)

    /** Lets Settings ask for a fresh summary, and finds out whether a watch with Tandem is paired. */
    fun start(context: Context) {
        val appContext = context.applicationContext
        WatchPairing.refresh = { scope.launch { publish(appContext, afterUnlock = true) } }
        checkPairing(context)
    }

    fun checkPairing(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            WatchPairing.available.value = runCatching { withTimeoutOrNull(PUBLISH_MS) { link(appContext).hasWatch() } }.getOrNull() == true
        }
    }

    fun refresh(context: Context) {
        val appContext = context.applicationContext
        scope.launch { publish(appContext, afterUnlock = true) }
    }

    /**
     * Sends the open budget's summary, or clears the watch when none is open. Unless [afterUnlock], a phone set to
     * update the watch only after an unlock leaves it as it was.
     */
    suspend fun publish(
        context: Context,
        afterUnlock: Boolean,
    ) {
        try {
            withTimeoutOrNull(PUBLISH_MS) {
                val database = TandemProcess.database(context)
                val preferences = AppPreferencesStore(database).read()
                if (afterUnlock || !WatchSettings.from(preferences).summaryAfterUnlock) {
                    val summary =
                        watchSummary(database, preferences, LocalDate.now().toString(), System.currentTimeMillis()) {
                            AutomationCalls.open(context, it)
                        }
                    link(context).publish(summary)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return
        }
    }

    suspend fun quickAdd(
        context: Context,
        entry: WatchQuickAdd,
    ): WatchQuickAddResult {
        val database = TandemProcess.database(context)
        val preferences = AppPreferencesStore(database).read()
        val date =
            Instant
                .ofEpochMilli(entry.createdAtEpochMs)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .toString()
        val outcome =
            addFromWatch(
                database,
                preferences,
                entry,
                date,
                System.currentTimeMillis(),
                openBudget = { AutomationCalls.open(context, it) },
            )
        val added = outcome.added
        val target = outcome.target
        if (outcome.result.status == WatchQuickAddStatus.ADDED && added != null && target != null) {
            AutomationNotifications.added(context, target, added, WATCH_SOURCE)
            AutomationCalls.requestSync(context)
        }
        publish(context, afterUnlock = false)
        return outcome.result
    }
}
