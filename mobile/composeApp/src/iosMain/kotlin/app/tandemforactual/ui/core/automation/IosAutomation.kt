package app.tandemforactual.ui.core.automation

import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.platform.IosEncryptedBudgetVault
import app.tandemforactual.ui.core.platform.IosInboxFile
import app.tandemforactual.ui.core.platform.iosReadablePassword
import app.tandemforactual.ui.core.platform.iosTandemDatabase
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** A transaction Shortcuts or Siri added; [repeated] when its external ID was already used, so nothing new was added. */
data class IosAutomationAdded(
    val budgetId: String,
    val transactionId: String,
    val summary: String,
    val repeated: Boolean,
    val sealed: Boolean,
)

data class IosAutomationFailure(
    val code: Int,
    val message: String,
)

/** What undoing an automated transaction did; [syncNeeded] when its deletion still has to reach Actual. */
data class IosAutomationUndone(
    val message: String,
    val syncNeeded: Boolean,
)

/**
 * Shortcuts and Siri reach the open budget only through here, under the same Settings → Automation rules as Tasker.
 * Every call answers exactly once through its callback and never throws into Swift.
 */
object IosAutomation {
    const val SOURCE = "Shortcuts"

    private const val UNEXPECTED_FAILURE = "Tandem couldn’t open this budget. Open Tandem and try again."
    const val LOCKED = "Open Tandem to unlock this budget."

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Adds a transaction through the outbox; [amount] is signed plain decimal text, such as "-12.4". */
    fun add(
        amount: String,
        payee: String?,
        category: String?,
        account: String?,
        date: String?,
        notes: String?,
        cleared: Boolean,
        externalId: String?,
        completion: (IosAutomationAdded?, IosAutomationFailure?) -> Unit,
    ) = save(
        AutomationTransactionInput(
            amount = amount,
            payee = payee,
            category = category,
            account = account,
            date = date,
            notes = notes,
            cleared = cleared.toString(),
            externalId = externalId,
            amountFormat = AutomationAmountFormat.PLAIN,
        ),
        completion,
    )

    /** Moves money from [fromAccount] to [toAccount] through the outbox; [amount] is plain decimal text, such as "250". */
    fun transfer(
        amount: String,
        fromAccount: String,
        toAccount: String,
        date: String?,
        notes: String?,
        externalId: String?,
        completion: (IosAutomationAdded?, IosAutomationFailure?) -> Unit,
    ) = save(
        AutomationTransactionInput(
            amount = amount,
            account = fromAccount,
            date = date,
            notes = notes,
            externalId = externalId,
            amountFormat = AutomationAmountFormat.PLAIN,
            transferTo = toAccount,
        ),
        completion,
    )

    private fun save(
        input: AutomationTransactionInput,
        completion: (IosAutomationAdded?, IosAutomationFailure?) -> Unit,
    ) = answer(completion) {
        val target = automationTarget(AppPreferencesStore(iosTandemDatabase).read(), AutomationKind.WRITE)
        val now = Clock.System.now().toEpochMilliseconds()
        val added =
            if (target.encrypted) {
                val inbox = IosInboxFile.forBudget(target.budgetId)
                sealAutomatedTransaction(iosTandemDatabase, inbox, target.budgetId, input, SOURCE, platformTodayIsoDate(), now)
            } else {
                addAutomatedTransaction(iosTandemDatabase, target.budgetId, input, SOURCE, platformTodayIsoDate(), now)
            }
        IosAutomationAdded(target.budgetId, added.transactionId, added.summary, added.repeated, added.sealed)
    }

    /** This month's left to spend from this device's copy, for [categoryId] or the whole budget. */
    fun leftToSpend(
        categoryId: String?,
        categoryName: String?,
        completion: (AutomationLeftToSpend?, IosAutomationFailure?) -> Unit,
    ) = answer(completion) {
        val target = automationTarget(AppPreferencesStore(iosTandemDatabase).read(), AutomationKind.READ)
        withReadableBudget(target) { database ->
            readAutomationBudget(database, target.budgetId, AutomationReadInput(category = categoryId), platformTodayIsoDate())
                .leftToSpend(categoryName?.takeIf { categoryId != null })
        }
    }

    /** [accountId]'s balance from this device's copy, with edits not yet synced. */
    fun accountBalance(
        accountId: String,
        accountName: String,
        completion: (AutomationAccountBalance?, IosAutomationFailure?) -> Unit,
    ) = answer(completion) {
        val target = automationTarget(AppPreferencesStore(iosTandemDatabase).read(), AutomationKind.READ)
        withReadableBudget(target) { database ->
            readAutomationBudget(database, target.budgetId, AutomationReadInput(account = accountId), platformTodayIsoDate())
                .accountBalance(accountName)
        }
    }

    /** Accounts and categories to offer; empty while both opt-ins are off or an encrypted budget may not be read locked. */
    fun records(completion: (AutomationRecords) -> Unit) {
        scope.launch {
            val records =
                runCatching {
                    val preferences = AppPreferencesStore(iosTandemDatabase).read()
                    val readable = preferences["selectedBudgetId"]?.let { iosReadablePassword(it) } != null
                    val target = automationNamesTarget(preferences, encryptedReadable = readable)
                    target?.let { withReadableBudget(it) { database -> automationRecords(database, it.budgetId) } }
                }.getOrNull()
            completion(records ?: AutomationRecords(emptyList(), emptyList()))
        }
    }

    /**
     * Runs [block] on [target]'s data. An encrypted budget opens only with the password the person allowed widgets,
     * Siri and Shortcuts to use while it's locked; otherwise the call asks them to open Tandem.
     */
    private suspend fun <T> withReadableBudget(
        target: AutomationTarget,
        block: suspend (TandemDatabase) -> T,
    ): T {
        if (!target.encrypted) return block(iosTandemDatabase)
        val database =
            iosReadablePassword(target.budgetId)?.let { IosEncryptedBudgetVault().open(target.budgetId, it) }
                ?: throw AutomationException(AutomationErrorCode.BUDGET_LOCKED, LOCKED)
        return try {
            block(database)
        } finally {
            database.close()
        }
    }

    fun undo(
        budgetId: String,
        transactionId: String,
        completion: (IosAutomationUndone) -> Unit,
    ) {
        scope.launch {
            val result =
                runCatching {
                    val now = Clock.System.now().toEpochMilliseconds()
                    if (withdrawSealedTransaction(IosInboxFile.forBudget(budgetId), budgetId, transactionId)) {
                        AutomationUndo.REMOVED
                    } else {
                        undoAutomatedTransaction(iosTandemDatabase, budgetId, transactionId, now)
                    }
                }.getOrNull()
            completion(
                when (result) {
                    AutomationUndo.REMOVED -> {
                        IosAutomationUndone("Transaction removed.", syncNeeded = false)
                    }

                    AutomationUndo.DELETING -> {
                        IosAutomationUndone("Transaction removed. Tandem deletes it in Actual on the next sync.", syncNeeded = true)
                    }

                    else -> {
                        IosAutomationUndone("Open Tandem to remove this transaction. It changed after it was added.", syncNeeded = false)
                    }
                },
            )
        }
    }

    private fun <T : Any> answer(
        completion: (T?, IosAutomationFailure?) -> Unit,
        block: suspend () -> T,
    ) {
        scope.launch {
            val outcome = runCatching { block() }
            val failure =
                outcome.exceptionOrNull()?.let { error ->
                    (error as? AutomationException)?.let { IosAutomationFailure(it.code, it.message.orEmpty()) }
                        ?: IosAutomationFailure(AutomationErrorCode.SAVE_FAILED, UNEXPECTED_FAILURE)
                }
            completion(outcome.getOrNull(), failure)
        }
    }
}
