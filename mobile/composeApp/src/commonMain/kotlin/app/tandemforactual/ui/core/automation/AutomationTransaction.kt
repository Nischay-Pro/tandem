package app.tandemforactual.ui.core.automation

import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.LocalRecordProjection
import app.tandemforactual.data.store.LocalWriteAction
import app.tandemforactual.data.store.LocalWriteRequest
import app.tandemforactual.data.store.cleanMerchantText
import app.tandemforactual.data.store.enqueueWrite
import app.tandemforactual.data.store.findRecords
import app.tandemforactual.data.store.learnPayeeBoilerplate
import app.tandemforactual.data.store.readBudgetDisplaySettings
import app.tandemforactual.ui.core.format.TransactionDateValidationStatus
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.format.parseAmountMinorUnits
import app.tandemforactual.ui.core.format.transactionAmountInputError
import app.tandemforactual.ui.core.format.validateTransactionDate
import app.tandemforactual.ui.core.state.booleanValue
import app.tandemforactual.ui.core.state.isOffBudgetAccount
import app.tandemforactual.ui.core.state.recordBody
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.core.state.stringValue
import app.tandemforactual.ui.design.inputs.MAX_TRANSACTION_AMOUNT_MINOR_UNITS
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** What an automation app sends to add a transaction; every field is text as the app typed it or filled it from variables. */
data class AutomationTransactionInput(
    val amount: String? = null,
    val payee: String? = null,
    val category: String? = null,
    val account: String? = null,
    val date: String? = null,
    val notes: String? = null,
    val cleared: String? = null,
    val externalId: String? = null,
    val amountFormat: AutomationAmountFormat = AutomationAmountFormat.BUDGET,
)

enum class AutomationAmountFormat {
    /** Written the way the budget shows amounts, read with the keypad's parser. */
    BUDGET,

    /** A dot as the decimal mark and no grouping, the way Shortcuts and Siri hand over numbers. */
    PLAIN,
}

data class AutomatedTransaction(
    val transactionId: String,
    val operationId: String,
    val amountMinor: Long,
    val payeeName: String?,
    val categoryName: String?,
    val repeated: Boolean,
    val displaySettings: BudgetDisplaySettings?,
    val sealed: Boolean = false,
) {
    /** The notification line, such as "Added −€12.40 · Corner Café · Coffee"; a sealed entry says only when it's added. */
    val summary: String
        get() =
            if (sealed) {
                SEALED_SUMMARY
            } else {
                listOfNotNull("Added ${formatCurrency(amountMinor, displaySettings)}", payeeName, categoryName).joinToString(" · ")
            }

    companion object {
        const val SEALED_SUMMARY = "Saved. It will be added when you next open Tandem."
    }
}

private class ResolvedTransaction(
    val account: BudgetRecordEntity,
    val amountMinor: Long,
    val date: String,
    val payee: BudgetRecordEntity?,
    val newPayeeName: String?,
    val importedPayee: String?,
    val category: BudgetRecordEntity?,
    val notes: String,
    val cleared: Boolean,
)

/**
 * Adds a transaction to [budgetId] through the outbox, exactly as manual entry does, so the next sync sends it and
 * Actual's rules run on it. A repeat of an [AutomationTransactionInput.externalId] within 30 days adds nothing and
 * returns the first transaction. Names resolve to the budget's own accounts, categories and payees: an unknown
 * category leaves it uncategorized, an unknown account fails, and an unknown payee is created when it syncs.
 */
suspend fun addAutomatedTransaction(
    database: TandemDatabase,
    budgetId: String,
    input: AutomationTransactionInput,
    source: String,
    todayIso: String,
    nowEpochMs: Long,
    transactionId: String = "tandem:${Uuid.random()}",
): AutomatedTransaction {
    val repository = LocalBudgetRepository(database) { nowEpochMs }
    val log = AutomationLog(database, budgetId)
    val settings = repository.readBudgetDisplaySettings(budgetId)
    val externalId = automationInput(input.externalId)
    externalId?.let { log.repeatOf(it, nowEpochMs) }?.let { earlier ->
        return AutomatedTransaction(earlier.transactionId, earlier.operationId, 0, null, null, repeated = true, settings)
    }
    val resolved = resolveTransaction(repository, budgetId, input, settings, todayIso)
    val operationId = Uuid.random().toString()
    try {
        repository.enqueueWrite(writeRequest(operationId, budgetId, transactionId, resolved, nowEpochMs))
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        throw AutomationException(
            AutomationErrorCode.SAVE_FAILED,
            "Tandem couldn’t save the transaction on this device. Open Tandem and try again.",
        )
    }
    log.add(AutomatedWrite(transactionId, operationId, externalId, source, nowEpochMs))
    return AutomatedTransaction(
        transactionId,
        operationId,
        resolved.amountMinor,
        resolved.payee?.recordName() ?: resolved.newPayeeName,
        resolved.category?.recordName(),
        repeated = false,
        displaySettings = settings,
    )
}

private suspend fun resolveTransaction(
    repository: LocalBudgetRepository,
    budgetId: String,
    input: AutomationTransactionInput,
    settings: BudgetDisplaySettings?,
    todayIso: String,
): ResolvedTransaction {
    val amount =
        when (input.amountFormat) {
            AutomationAmountFormat.BUDGET -> automationAmount(input.amount, settings)
            AutomationAmountFormat.PLAIN -> automationPlainAmount(input.amount, settings)
        }
    val account = automationAccount(repository.findRecords(budgetId, "account"), automationInput(input.account))
    val payeeChoice = automationInput(input.payee)?.let { automationPayeeChoice(repository.findRecords(budgetId, "payee"), it) }
    val category =
        automationInput(input.category)
            ?.takeUnless { account.recordBody().isOffBudgetAccount() }
            ?.let { text -> repository.findRecords(budgetId, "category").matching(text) }
    return ResolvedTransaction(
        account = account,
        amountMinor = amount,
        date = automationDate(input.date, todayIso),
        payee = payeeChoice?.payee,
        newPayeeName = payeeChoice?.newName,
        importedPayee = payeeChoice?.importedText,
        category = category,
        notes = automationInput(input.notes).orEmpty(),
        cleared = automationCleared(input.cleared),
    )
}

/** The amount in minor units, read with the keypad's strict parser; the sign must be given, `-` for money out. */
internal fun automationAmount(
    text: String?,
    settings: BudgetDisplaySettings?,
): Long {
    val value = automationInput(text) ?: throw AutomationException(AutomationErrorCode.AMOUNT, "Enter an amount, such as -12.40.")
    if (value.first() !in "+-−") {
        throw AutomationException(AutomationErrorCode.AMOUNT, "Start the amount with - for money out or + for money in, such as -12.40.")
    }
    val amount =
        parseAmountMinorUnits(value, settings)
            ?: throw AutomationException(AutomationErrorCode.AMOUNT, transactionAmountInputError(value, settings))
    return checkedAmount(amount, settings)
}

private val PLAIN_AMOUNT = Regex("""^([+-])(\d{1,12})(?:\.(\d{1,6}))?$""")

/** A signed amount written with a dot, such as "-12.4"; more decimals than the budget's currency has are refused. */
internal fun automationPlainAmount(
    text: String?,
    settings: BudgetDisplaySettings?,
): Long {
    val value =
        automationInput(text)?.replace('−', '-')
            ?: throw AutomationException(AutomationErrorCode.AMOUNT, "Enter an amount, such as -12.40.")
    val match =
        PLAIN_AMOUNT.matchEntire(value)
            ?: throw AutomationException(
                AutomationErrorCode.AMOUNT,
                "Start the amount with - for money out or + for money in, such as -12.40.",
            )
    val (sign, whole, fraction) = match.destructured
    val digits = (settings?.currencyFractionDigits ?: 2).coerceIn(0, 2)
    val kept = fraction.trimEnd('0')
    if (kept.length > digits) {
        throw AutomationException(
            AutomationErrorCode.AMOUNT,
            if (digits == 0) "Use a whole amount, without decimals." else "Use at most $digits decimal places.",
        )
    }
    val magnitude = whole.toLong() * 100 + kept.padEnd(2, '0').toLong()
    return checkedAmount(if (sign == "-") -magnitude else magnitude, settings)
}

private fun checkedAmount(
    amount: Long,
    settings: BudgetDisplaySettings?,
): Long {
    if (amount == 0L) throw AutomationException(AutomationErrorCode.AMOUNT, "Enter an amount other than zero.")
    if (amount == Long.MIN_VALUE || kotlin.math.abs(amount) > MAX_TRANSACTION_AMOUNT_MINOR_UNITS) {
        throw AutomationException(
            AutomationErrorCode.AMOUNT,
            "Largest amount is ${formatCurrency(MAX_TRANSACTION_AMOUNT_MINOR_UNITS, settings)}.",
        )
    }
    return amount
}

internal fun automationAccount(
    accounts: List<BudgetRecordEntity>,
    text: String?,
): BudgetRecordEntity {
    val name = text ?: throw AutomationException(AutomationErrorCode.ACCOUNT, "Choose an account to add the transaction to.")
    val account =
        accounts.matching(name) ?: throw AutomationException(AutomationErrorCode.ACCOUNT, "No account named “$name” in this budget.")
    if (account.recordBody()?.booleanValue("closed") == true) {
        throw AutomationException(AutomationErrorCode.ACCOUNT, "“${account.recordName() ?: name}” is closed. Choose an open account.")
    }
    return account
}

/** An existing payee by id or name, or null to create one with that name; transfer payees are refused. */
/** Which payee automation text means, the name for a new one, and the text as it came when that was cleaned up. */
internal data class AutomationPayeeChoice(
    val payee: BudgetRecordEntity?,
    val newName: String?,
    val importedText: String?,
)

/**
 * An exact payee first; otherwise the text with a card processor's tag and bank detail taken off, matched again or
 * used as the new payee's name. The original text stays as the imported payee so rules still see it.
 */
internal fun automationPayeeChoice(
    payees: List<BudgetRecordEntity>,
    text: String,
): AutomationPayeeChoice {
    automationPayee(payees, text)?.let { return AutomationPayeeChoice(it, null, null) }
    val cleaned = cleanMerchantText(text, learnPayeeBoilerplate(payees.mapNotNull { it.recordName() }))
    if (cleaned == text) return AutomationPayeeChoice(null, text, null)
    val matched = automationPayee(payees, cleaned)
    return AutomationPayeeChoice(matched, cleaned.takeIf { matched == null }, text)
}

internal fun automationPayee(
    payees: List<BudgetRecordEntity>,
    text: String,
): BudgetRecordEntity? {
    val payee = payees.matching(text) ?: return null
    if (!payee.recordBody()?.stringValue("transfer_acct").isNullOrBlank()) {
        throw AutomationException(AutomationErrorCode.PAYEE, "Automation apps can’t add transfers. Use a payee that isn’t an account.")
    }
    return payee
}

internal fun automationDate(
    text: String?,
    todayIso: String,
): String {
    val value = automationInput(text) ?: return todayIso
    val validation = validateTransactionDate(value, todayIso, confirmedFutureDate = null)
    return when (validation.status) {
        TransactionDateValidationStatus.VALID -> checkNotNull(validation.date)
        TransactionDateValidationStatus.CONFIRMATION_REQUIRED ->
            throw AutomationException(AutomationErrorCode.DATE, "That date is too far ahead. Use a date in the next few years.")
        else -> throw AutomationException(AutomationErrorCode.DATE, "Use a date written as year-month-day, such as $todayIso.")
    }
}

internal fun automationCleared(text: String?): Boolean =
    when (automationInput(text)?.lowercase()) {
        null, "false", "no", "0", "off" -> false
        "true", "yes", "1", "on" -> true
        else -> throw AutomationException(AutomationErrorCode.CLEARED, "Set cleared to true or false.")
    }

/** The record with this id, or else the first with this name, ignoring case. */
internal fun List<BudgetRecordEntity>.matching(text: String): BudgetRecordEntity? =
    firstOrNull { it.recordId == text } ?: firstOrNull { it.recordName()?.trim().equals(text, ignoreCase = true) }

private fun writeRequest(
    operationId: String,
    budgetId: String,
    transactionId: String,
    resolved: ResolvedTransaction,
    nowEpochMs: Long,
): LocalWriteRequest {
    val payeeId = resolved.payee?.recordId
    val categoryId = resolved.category?.recordId
    val patch =
        buildJsonObject {
            put("account", resolved.account.recordId)
            put("amount", resolved.amountMinor)
            put("date", resolved.date)
            put("payee", payeeId?.let(::JsonPrimitive) ?: JsonNull)
            resolved.newPayeeName?.let { put("payee_name", it) }
            resolved.importedPayee?.let { put("imported_payee", it) }
            put("category", categoryId?.let(::JsonPrimitive) ?: JsonNull)
            put("notes", resolved.notes)
            put("cleared", resolved.cleared)
        }
    return LocalWriteRequest(
        operationId = operationId,
        idempotencyKey = operationId,
        budgetId = budgetId,
        recordType = "transaction",
        recordId = transactionId,
        action = LocalWriteAction.CREATE,
        baseRevision = null,
        baseSnapshotJson = null,
        patchJson = patch.toString(),
        optimisticJson = optimisticTransaction(transactionId, patch, resolved.importedPayee ?: resolved.newPayeeName).toString(),
        createdAtEpochMs = nowEpochMs,
        optimisticProjection =
            LocalRecordProjection(
                recordDate = resolved.date,
                accountId = resolved.account.recordId,
                categoryId = categoryId,
                payeeId = payeeId,
                amountMinor = resolved.amountMinor,
                budgetMonth = resolved.date.take(7),
            ),
    )
}

private fun optimisticTransaction(
    transactionId: String,
    patch: JsonObject,
    importedPayee: String?,
): JsonObject =
    JsonObject(
        patch - "payee_name" - "imported_payee" +
            mapOf(
                "id" to JsonPrimitive(transactionId),
                "reconciled" to JsonNull,
                "importedId" to JsonNull,
                "importedPayee" to (importedPayee?.let(::JsonPrimitive) ?: JsonNull),
                "startingBalance" to JsonNull,
                "schedule" to JsonNull,
                "transferId" to JsonNull,
                "isParent" to JsonPrimitive(false),
                "isChild" to JsonPrimitive(false),
                "parentId" to JsonNull,
                "subtransactions" to JsonArray(emptyList()),
            ),
    )
