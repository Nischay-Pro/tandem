package app.tandemforactual.ui.core.automation

import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.inbox.InboxFile
import app.tandemforactual.data.inbox.InboxKeyring
import app.tandemforactual.data.inbox.InboxPublicKeys
import app.tandemforactual.data.inbox.OpenedEntry
import app.tandemforactual.data.inbox.SealedInbox
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.findRecord
import app.tandemforactual.data.store.readPendingWrites
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** What opening a budget's inbox did; [failures] are the reasons entries couldn't be added. */
data class SealedInboxResult(
    val added: Int,
    val failures: List<String>,
    val unreadable: Int,
) {
    /** One line for the person who just unlocked, or null when everything went in. */
    val message: String?
        get() =
            when {
                failures.isNotEmpty() ->
                    "Tandem couldn’t add ${count(failures.size)} saved while this budget was locked. ${failures.first()}"
                unreadable > 0 -> "Tandem couldn’t read ${count(unreadable)} saved while this budget was locked. Add them again."
                else -> null
            }

    private fun count(value: Int) = if (value == 1) "1 transaction" else "$value transactions"
}

/**
 * Seals a transaction for a locked encrypted budget instead of adding it, using only what the device keeps in the
 * clear: the budget's inbox public key. Checks that need the budget, such as the account's name, wait for the unlock.
 */
suspend fun sealAutomatedTransaction(
    mainDatabase: TandemDatabase,
    inbox: InboxFile,
    budgetId: String,
    input: AutomationTransactionInput,
    source: String,
    todayIso: String,
    nowEpochMs: Long,
): AutomatedTransaction {
    val publicKey =
        InboxPublicKeys.read(mainDatabase, budgetId)
            ?: throw AutomationException(
                AutomationErrorCode.BUDGET_LOCKED,
                "This encrypted budget is locked. Open Tandem and unlock it once so automation apps can save to it.",
            )
    val payload = sealedPayload(checkedForSealing(input, todayIso), source, nowEpochMs)
    val entryId =
        try {
            SealedInbox(inbox, budgetId).seal(publicKey, payload.toString().encodeToByteArray())
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            throw AutomationException(AutomationErrorCode.SAVE_FAILED, "Tandem couldn’t save the transaction on this device. Try again.")
        }
    return AutomatedTransaction(sealedTransactionId(entryId), entryId, 0, null, null, repeated = false, null, sealed = true)
}

/** Takes back a sealed transaction before it's added; false when it's no longer waiting. */
suspend fun withdrawSealedTransaction(
    inbox: InboxFile,
    budgetId: String,
    transactionId: String,
): Boolean {
    val entryId = entryIdOf(transactionId) ?: return false
    return SealedInbox(inbox, budgetId).withdraw(entryId)
}

/**
 * Adds everything sealed while [budgetId] was locked, then publishes the key automation seals with next. Entries
 * sealed with an earlier key go in first; a password change makes a new key pair. Each entry becomes the transaction
 * `tandem:<entry id>`, so one added before a crash is recognised and not added twice.
 */
suspend fun openSealedInbox(
    vaultDatabase: TandemDatabase,
    mainDatabase: TandemDatabase,
    inbox: InboxFile,
    budgetId: String,
    password: String,
    todayIso: String,
): SealedInboxResult {
    val keyring = InboxKeyring(vaultDatabase, budgetId)
    val failures = mutableListOf<String>()
    var added = 0
    val drain =
        SealedInbox(inbox, budgetId).drain(keyring) { entry ->
            try {
                if (applySealedEntry(vaultDatabase, budgetId, entry, todayIso)) added++
            } catch (error: AutomationException) {
                failures += error.message.orEmpty()
            }
        }
    InboxPublicKeys.publish(mainDatabase, budgetId, keyring.ensure(password))
    return SealedInboxResult(added, failures, drain.unreadable)
}

private suspend fun applySealedEntry(
    database: TandemDatabase,
    budgetId: String,
    entry: OpenedEntry,
    todayIso: String,
): Boolean {
    val transactionId = sealedTransactionId(entry.entryId)
    val repository = LocalBudgetRepository(database) { 0L }
    val exists =
        repository.findRecord(budgetId, "transaction", transactionId) != null ||
            repository.readPendingWrites(budgetId).any { it.recordId == transactionId }
    if (exists) return false
    val payload = runCatching { Json.parseToJsonElement(entry.payload.decodeToString()) as? JsonObject }.getOrNull() ?: return false

    fun text(name: String) = (payload[name] as? JsonPrimitive)?.contentOrNull
    val input =
        AutomationTransactionInput(
            amount = text("amount"),
            payee = text("payee"),
            category = text("category"),
            account = text("account"),
            date = text("date"),
            notes = text("notes"),
            cleared = text("cleared"),
            externalId = text("external"),
            amountFormat = AutomationAmountFormat.entries.firstOrNull { it.name == text("format") } ?: AutomationAmountFormat.BUDGET,
        )
    val at = (payload["at"] as? JsonPrimitive)?.longOrNull ?: 0L
    val source = text("source") ?: "Automation"
    return !addAutomatedTransaction(database, budgetId, input, source, todayIso, at, transactionId).repeated
}

/** What can be checked without the budget: an amount with its sign, an account, the date and cleared. */
internal fun checkedForSealing(
    input: AutomationTransactionInput,
    todayIso: String,
): AutomationTransactionInput {
    val amount = automationInput(input.amount) ?: throw AutomationException(AutomationErrorCode.AMOUNT, "Enter an amount, such as -12.40.")
    when (input.amountFormat) {
        AutomationAmountFormat.PLAIN -> automationPlainAmount(amount, null)
        AutomationAmountFormat.BUDGET ->
            if (amount.first() !in "+-−") {
                throw AutomationException(
                    AutomationErrorCode.AMOUNT,
                    "Start the amount with - for money out or + for money in, such as -12.40.",
                )
            }
    }
    automationInput(input.account) ?: throw AutomationException(AutomationErrorCode.ACCOUNT, "Choose an account to add the transaction to.")
    automationCleared(input.cleared)
    return input.copy(date = automationDate(input.date, todayIso))
}

private fun sealedPayload(
    input: AutomationTransactionInput,
    source: String,
    nowEpochMs: Long,
): JsonObject =
    buildJsonObject {
        put("v", 1)
        automationInput(input.amount)?.let { put("amount", it) }
        automationInput(input.payee)?.let { put("payee", it) }
        automationInput(input.category)?.let { put("category", it) }
        automationInput(input.account)?.let { put("account", it) }
        input.date?.let { put("date", it) }
        automationInput(input.notes)?.let { put("notes", it) }
        automationInput(input.cleared)?.let { put("cleared", it) }
        automationInput(input.externalId)?.let { put("external", it) }
        put("format", input.amountFormat.name)
        put("source", source)
        put("at", nowEpochMs)
    }

@OptIn(ExperimentalUuidApi::class)
internal fun sealedTransactionId(entryId: String): String = "tandem:${Uuid.parseHex(entryId)}"

@OptIn(ExperimentalUuidApi::class)
private fun entryIdOf(transactionId: String): String? =
    runCatching { Uuid.parse(transactionId.removePrefix("tandem:")).toHexString() }.getOrNull()
