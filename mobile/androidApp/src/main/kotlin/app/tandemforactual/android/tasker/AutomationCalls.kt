package app.tandemforactual.android.tasker

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import app.tandemforactual.android.AndroidDeviceCredentialStore
import app.tandemforactual.android.AndroidEncryptedBudgetVault
import app.tandemforactual.android.BACKGROUND_KEY_STORE
import app.tandemforactual.android.BackgroundBudgetSyncScheduler
import app.tandemforactual.android.TandemProcess
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.ui.core.automation.AutomatedTransaction
import app.tandemforactual.ui.core.automation.AutomationBudgetNumbers
import app.tandemforactual.ui.core.automation.AutomationChoices
import app.tandemforactual.ui.core.automation.AutomationEvent
import app.tandemforactual.ui.core.automation.AutomationEventKind
import app.tandemforactual.ui.core.automation.AutomationEventStore
import app.tandemforactual.ui.core.automation.AutomationKind
import app.tandemforactual.ui.core.automation.AutomationReadInput
import app.tandemforactual.ui.core.automation.AutomationTarget
import app.tandemforactual.ui.core.automation.AutomationTransactionInput
import app.tandemforactual.ui.core.automation.AutomationUndo
import app.tandemforactual.ui.core.automation.addAutomatedTransaction
import app.tandemforactual.ui.core.automation.automationChoices
import app.tandemforactual.ui.core.automation.automationDatabase
import app.tandemforactual.ui.core.automation.automationTarget
import app.tandemforactual.ui.core.automation.categoriesOverBudget
import app.tandemforactual.ui.core.automation.overBudgetValues
import app.tandemforactual.ui.core.automation.readAutomationBudget
import app.tandemforactual.ui.core.automation.syncEventsAllowed
import app.tandemforactual.ui.core.automation.undoAutomatedTransaction
import app.tandemforactual.ui.core.session.SyncNetwork
import java.time.LocalDate

/** Every automation call opens the budget the same way: Settings → Automation first, then the budget, never a prompt. */
internal object AutomationCalls {
    private const val SYNC_WORK = "tandem-automation-sync"

    suspend fun preferences(context: Context): Map<String, String> = AppPreferencesStore(TandemProcess.database(context)).read()

    suspend fun add(
        context: Context,
        input: AutomationTransactionInput,
        source: String,
    ): AutomatedTransaction {
        val target = automationTarget(preferences(context), AutomationKind.WRITE)
        val added =
            withBudget(context, target) { database ->
                addAutomatedTransaction(database, target.budgetId, input, source, LocalDate.now().toString(), System.currentTimeMillis())
            }
        if (!added.repeated) {
            AutomationNotifications.added(context, target, added, source)
            requestSync(context)
        }
        return added
    }

    suspend fun read(
        context: Context,
        input: AutomationReadInput,
    ): AutomationBudgetNumbers {
        val target = automationTarget(preferences(context), AutomationKind.READ)
        return withBudget(context, target) { readAutomationBudget(it, target.budgetId, input, LocalDate.now().toString()) }
    }

    /** A stored event for Tasker, once reads are allowed for it; null when it's gone, closed or the budget is locked. */
    suspend fun event(
        context: Context,
        id: String,
        kind: AutomationEventKind,
    ): AutomationEvent? =
        runCatching {
            val preferences = preferences(context)
            val now = System.currentTimeMillis()
            val event =
                if (kind == AutomationEventKind.SYNC_FAILED) {
                    if (!syncEventsAllowed(preferences)) return null
                    AutomationEventStore(TandemProcess.database(context)).find(id, now)
                } else {
                    withBudget(context, automationTarget(preferences, AutomationKind.READ)) { AutomationEventStore(it).find(id, now) }
                }
            event?.takeIf { it.kind == kind }
        }.getOrNull()

    /** The "Category over budget" variables while [category], or any category, is over budget; null otherwise. */
    suspend fun overBudget(
        context: Context,
        category: String?,
    ): Map<String, String>? =
        runCatching {
            val target = automationTarget(preferences(context), AutomationKind.READ)
            withBudget(context, target) { database ->
                categoriesOverBudget(database, target.budgetId, category, LocalDate.now().toString())
                    .takeIf { it.isNotEmpty() }
                    ?.let { overBudgetValues(database, target.budgetId, it) }
            }
        }.getOrNull()

    /** Names for an action's settings page; null while no budget is open or it's locked. */
    suspend fun choices(context: Context): AutomationChoices? =
        runCatching {
            val preferences = preferences(context)
            val budgetId = preferences["selectedBudgetId"]?.takeIf(String::isNotBlank) ?: return null
            val target = AutomationTarget(budgetId, preferences["selectedBudgetEncrypted"] == "true")
            withBudget(context, target) { automationChoices(it, budgetId) }
        }.getOrNull()

    suspend fun undo(
        context: Context,
        target: AutomationTarget,
        transactionId: String,
    ): AutomationUndo {
        val result =
            withBudget(context, target) { undoAutomatedTransaction(it, target.budgetId, transactionId, System.currentTimeMillis()) }
        if (result == AutomationUndo.DELETING) requestSync(context)
        return result
    }

    private suspend fun <T> withBudget(
        context: Context,
        target: AutomationTarget,
        block: suspend (TandemDatabase) -> T,
    ): T {
        val main = TandemProcess.database(context)
        val database = open(context, target)
        return try {
            block(database)
        } finally {
            if (database !== main) database.close()
        }
    }

    /** [target]'s database without asking for anything; the caller closes it unless it's the main one. */
    suspend fun open(
        context: Context,
        target: AutomationTarget,
    ): TandemDatabase {
        val key = runCatching { AndroidDeviceCredentialStore(context, BACKGROUND_KEY_STORE).read() }.getOrNull()
        return automationDatabase(target, TandemProcess.database(context), key) { id, password ->
            AndroidEncryptedBudgetVault(context).open(id, password)
        }
    }

    /** The same sync Tandem schedules, run once over the user's chosen network; the worker holds the sync lock. */
    suspend fun requestSync(context: Context) {
        val preferences = preferences(context)
        val network = SyncNetwork.restore(preferences["syncNetwork"], preferences["syncOnWifi"]?.toBooleanStrictOrNull()) ?: SyncNetwork.ANY
        WorkManager
            .getInstance(context.applicationContext)
            .enqueueUniqueWork(
                SYNC_WORK,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                BackgroundBudgetSyncScheduler.afterAutomationRequest(network),
            )
    }
}
