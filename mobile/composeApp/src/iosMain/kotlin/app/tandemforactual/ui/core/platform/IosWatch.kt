package app.tandemforactual.ui.core.platform

import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.watchSummaryJson
import app.tandemforactual.ui.core.automation.WatchQuickAddEntry
import app.tandemforactual.ui.core.automation.WatchQuickAddReply
import app.tandemforactual.ui.core.automation.addWatchQuickAdd
import app.tandemforactual.ui.core.format.formatBudgetCurrency
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The Apple Watch as Tandem sees it. Swift reports whether one is paired with Tandem on it and passes each summary on
 * through WatchConnectivity; the watch's quick adds come back through [addQuickAdd], one at a time.
 */
object IosWatch : WatchLink {
    private val pairedState = MutableStateFlow(false)
    override val paired: StateFlow<Boolean> = pairedState

    private var sender: ((String) -> Unit)? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val quickAdds = Mutex()

    fun connect(send: (String) -> Unit) {
        sender = send
    }

    fun updatePaired(paired: Boolean) {
        pairedState.value = paired
    }

    override fun sendSummary(json: String) {
        sender?.invoke(json)
    }

    /** The summary from this device's copy while Tandem isn't on screen, so a protected budget reads as locked. */
    fun readSummary(completion: (String?) -> Unit) {
        scope.launch {
            val json =
                runCatching {
                    val database = iosTandemDatabase
                    watchSummaryJson(
                        LocalBudgetRepository(database) { Clock.System.now().toEpochMilliseconds() },
                        AppPreferencesStore(database).read(),
                        platformTodayIsoDate(),
                        Clock.System.now().toEpochMilliseconds(),
                        opened = false,
                        format = ::formatBudgetCurrency,
                    )
                }.getOrNull()
            runCatching { completion(json) }
        }
    }

    /** Adds one watch entry; entries are taken one at a time so a resent entry is always seen as a repeat. */
    fun addQuickAdd(
        entryId: String,
        amount: String,
        categoryId: String?,
        payee: String?,
        accountId: String?,
        completion: (WatchQuickAddReply) -> Unit,
    ) {
        scope.launch {
            val entry = WatchQuickAddEntry(entryId, amount, categoryId, payee, accountId)
            val reply =
                runCatching {
                    quickAdds.withLock {
                        val database = iosTandemDatabase
                        addWatchQuickAdd(
                            database,
                            AppPreferencesStore(database).read(),
                            entry,
                            platformTodayIsoDate(),
                            Clock.System.now().toEpochMilliseconds(),
                        )
                    }
                }.getOrElse { WatchQuickAddReply(entryId, added = false, message = "Tandem couldn’t save it. Open Tandem and try again.") }
            runCatching { completion(reply) }
        }
    }
}
