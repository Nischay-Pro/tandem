package app.tandemforactual.ui.core.session.transactions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.data.store.BudgetSyncSummary
import app.tandemforactual.data.store.LocalRecordProjection
import app.tandemforactual.data.store.LocalWriteAction
import app.tandemforactual.data.store.LocalWriteRequest
import app.tandemforactual.data.store.enqueueWrite
import app.tandemforactual.data.store.findWrite
import app.tandemforactual.ui.core.format.TransactionDateValidationStatus
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.format.parseAmountMinorUnits
import app.tandemforactual.ui.core.format.transactionAmountInputError
import app.tandemforactual.ui.core.format.transactionDateValidationMessage
import app.tandemforactual.ui.core.format.validateTransactionDate
import app.tandemforactual.ui.core.session.BudgetReorderKind
import app.tandemforactual.ui.core.session.budget.hasPendingTransactionDependency
import app.tandemforactual.ui.core.session.localStorageFailureMessage
import app.tandemforactual.ui.core.session.stringValue
import app.tandemforactual.ui.core.session.transactions.TransactionController
import app.tandemforactual.ui.core.state.booleanValue
import app.tandemforactual.ui.core.state.isOffBudgetAccount
import app.tandemforactual.ui.core.state.recordBody
import app.tandemforactual.ui.design.inputs.MAX_TRANSACTION_AMOUNT_MINOR_UNITS
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal suspend fun TransactionController.createTransaction(
    budgetId: String,
    accountId: String,
    payeeId: String?,
    categoryId: String?,
    amountText: String,
    dateText: String,
    notes: String,
    encryptionPassword: String?,
    displaySettings: BudgetDisplaySettings? = null,
    isTransfer: Boolean = false,
    confirmedFutureDate: String? = null,
    splitLines: List<Pair<Long, String>>? = null,
): Boolean {
    host.reportTransactionWriteMessage(null)
    if (host.refuseWriteInReadOnlyMode()) return false
    if (!host.isCurrentIdentityCacheOwner()) {
        host.reportTransactionWriteMessage("Sign in to the server and account that own this budget before adding a transaction.")
        return false
    }
    val amount = parseNewTransactionAmount(amountText, displaySettings) ?: return false
    val date = validNewTransactionDate(dateText, confirmedFutureDate) ?: return false
    val localRepository = host.repository
    val coordinator = host.syncCoordinator
    if (localRepository == null || coordinator == null) {
        host.reportTransactionWriteMessage("Budget data isn’t available on this device. Reopen the budget and try again.")
        return false
    }
    val account = validNewTransactionTarget(budgetId, accountId, payeeId, categoryId, isTransfer, splitLines) ?: return false
    val transactionId = "tandem:${Uuid.random()}"
    val operationId = Uuid.random().toString()
    val requestedCategoryId = categoryId.takeUnless { isTransfer || account.recordBody().isOffBudgetAccount() }
    val split = splitLines?.takeUnless { isTransfer || account.recordBody().isOffBudgetAccount() }?.let(::splitLinesJson)
    val patch =
        newTransactionPatch(account, amount, date, payeeId, requestedCategoryId.takeIf { split == null }, notes).withSplit(split)
    val optimistic =
        newTransactionOptimistic(transactionId, account, amount, date, payeeId, categoryId.takeIf { split == null }, notes).withSplit(split)
    host.markSavingTransaction(true)
    try {
        localRepository.enqueueWrite(
            newTransactionWriteRequest(
                operationId,
                budgetId,
                transactionId,
                account,
                amount,
                date,
                payeeId,
                requestedCategoryId.takeIf { split == null },
                patch,
                optimistic,
            ),
        )
        val summary =
            if (host.budgetQueue.hasPendingTransactionDependency(budgetId)) {
                null
            } else {
                host.synchronize(budgetId, encryptionPassword)
            }
        val savedWrite = localRepository.findWrite(operationId)
        host.reportTransactionWriteMessage(newTransactionSaveMessage(savedWrite?.status, summary))
        host.loadLocalRecords(budgetId)
        return savedWrite?.status in setOf("committed", "queued")
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        host.reportTransactionWriteMessage(
            localStorageFailureMessage(
                error,
                "Tandem couldn’t confirm the save. Check Activity before adding it again.",
            ),
        )
        host.loadLocalRecords(budgetId)
        return false
    } finally {
        host.markSavingTransaction(false)
    }
}

/** The account to add to, once the payee, category and any split categories check out; null after reporting why not. */
private fun TransactionController.validNewTransactionTarget(
    budgetId: String,
    accountId: String,
    payeeId: String?,
    categoryId: String?,
    isTransfer: Boolean,
    splitLines: List<Pair<Long, String>>?,
): BudgetRecordEntity? {
    val account = newTransactionAccount(accountId) ?: return null
    if (!validateNewTransactionPayee(accountId, payeeId, isTransfer)) return null
    if (!validateNewTransactionCategory(budgetId, categoryId)) return null
    if (splitLines?.any { (_, lineCategory) -> !validateNewTransactionCategory(budgetId, lineCategory) } == true) return null
    return account
}

private fun TransactionController.parseNewTransactionAmount(
    amountText: String,
    displaySettings: BudgetDisplaySettings?,
): Long? {
    val amount = parseAmountMinorUnits(amountText, displaySettings)
    if (amount == null) {
        host.reportTransactionWriteMessage(transactionAmountInputError(amountText, displaySettings))
        return null
    }
    if (amount == 0L) {
        host.reportTransactionWriteMessage("Enter an amount greater than zero.")
        return null
    }
    if (amount == Long.MIN_VALUE || kotlin.math.abs(amount) > MAX_TRANSACTION_AMOUNT_MINOR_UNITS) {
        host.reportTransactionWriteMessage("Largest amount is ${formatCurrency(MAX_TRANSACTION_AMOUNT_MINOR_UNITS, displaySettings)}.")
        return null
    }
    return amount
}

private fun TransactionController.validNewTransactionDate(
    dateText: String,
    confirmedFutureDate: String?,
): String? {
    val dateValidation = validateTransactionDate(dateText, host.currentDateIso(), confirmedFutureDate)
    if (dateValidation.status != TransactionDateValidationStatus.VALID) {
        host.reportTransactionWriteMessage(transactionDateValidationMessage(dateValidation))
        return null
    }
    return checkNotNull(dateValidation.date)
}

private fun TransactionController.newTransactionAccount(accountId: String): BudgetRecordEntity? {
    val account = host.accountRecords.firstOrNull { it.recordId == accountId }
    if (account == null) {
        host.reportTransactionWriteMessage("Choose an account from this budget before saving.")
        return null
    }
    if (account.recordBody()?.booleanValue("closed") == true) {
        host.reportTransactionWriteMessage("Choose an open account from this budget.")
        return null
    }
    return account
}

private fun TransactionController.validateNewTransactionPayee(
    accountId: String,
    payeeId: String?,
    isTransfer: Boolean,
): Boolean {
    if (payeeId != null && host.payeeRecords.none { it.recordId == payeeId }) {
        host.reportTransactionWriteMessage("Choose an existing payee or clear the payee field.")
        return false
    }
    val transferDestinationId =
        payeeId
            ?.let { id -> host.payeeRecords.firstOrNull { it.recordId == id } }
            ?.recordBody()
            ?.stringValue("transfer_acct")
    if (isTransfer && transferDestinationId.isNullOrBlank()) {
        host.reportTransactionWriteMessage("Choose a destination account for this transfer.")
        return false
    }
    if (isTransfer && transferDestinationId == accountId) {
        host.reportTransactionWriteMessage("Choose a different destination account.")
        return false
    }
    if (!isTransfer && !transferDestinationId.isNullOrBlank()) {
        host.reportTransactionWriteMessage("Choose Transfer as the transaction type for this payee.")
        return false
    }
    return true
}

private fun TransactionController.validateNewTransactionCategory(
    budgetId: String,
    categoryId: String?,
): Boolean {
    val categoryIsPending =
        categoryId != null &&
            host.budgetQueue.budgetReorderQueue.moves.any {
                it.budgetId == budgetId &&
                    it.itemId == categoryId &&
                    it.kind in setOf(BudgetReorderKind.CREATE_CATEGORY, BudgetReorderKind.REFRESH_CATEGORY_CREATE)
            }
    if (categoryId != null && host.categoryRecords.none { it.recordId == categoryId } && !categoryIsPending) {
        host.reportTransactionWriteMessage("Choose a category from this budget before saving.")
        return false
    }
    return true
}

private fun newTransactionPatch(
    account: BudgetRecordEntity,
    amount: Long,
    date: String,
    payeeId: String?,
    requestedCategoryId: String?,
    notes: String,
): JsonObject =
    buildJsonObject {
        put("account", account.recordId)
        put("amount", amount)
        put("date", date)
        put("payee", payeeId?.let(::JsonPrimitive) ?: JsonNull)
        put("category", requestedCategoryId?.let(::JsonPrimitive) ?: JsonNull)
        put("notes", notes)
        put("cleared", false)
    }

private fun newTransactionOptimistic(
    transactionId: String,
    account: BudgetRecordEntity,
    amount: Long,
    date: String,
    payeeId: String?,
    categoryId: String?,
    notes: String,
): JsonObject =
    buildJsonObject {
        put("id", transactionId)
        put("account", account.recordId)
        put("amount", amount)
        put("date", date)
        put("payee", payeeId?.let(::JsonPrimitive) ?: JsonNull)
        put("category", categoryId?.let(::JsonPrimitive) ?: JsonNull)
        put("notes", notes)
        put("cleared", false)
        put("reconciled", JsonNull)
        put("importedId", JsonNull)
        put("importedPayee", JsonNull)
        put("startingBalance", JsonNull)
        put("schedule", JsonNull)
        put("transferId", JsonNull)
        put("isParent", false)
        put("isChild", false)
        put("parentId", JsonNull)
        put("subtransactions", JsonArray(emptyList()))
    }

private fun newTransactionSaveMessage(
    status: String?,
    summary: BudgetSyncSummary?,
): String =
    when (status) {
        "committed" -> "Transaction added to Actual."
        "queued" -> "Saved on this device and waiting to sync."
        "conflict" -> "This transaction is already in Actual. Sync the budget to check it."
        "uncertain", "sending" -> "Tandem couldn’t confirm the save. Check Activity before adding it again."
        "rejected" -> "Actual didn’t add this transaction. Review the details and try again."
        else ->
            if (summary == null) {
                "Tandem couldn’t confirm the save. Check Activity before adding it again."
            } else {
                "The transaction is saved on this device and is waiting to sync."
            }
    }

private fun TransactionController.newTransactionWriteRequest(
    operationId: String,
    budgetId: String,
    transactionId: String,
    account: BudgetRecordEntity,
    amount: Long,
    date: String,
    payeeId: String?,
    requestedCategoryId: String?,
    patch: JsonObject,
    optimistic: JsonObject,
): LocalWriteRequest =
    LocalWriteRequest(
        operationId = operationId,
        idempotencyKey = operationId,
        budgetId = budgetId,
        recordType = "transaction",
        recordId = transactionId,
        action = LocalWriteAction.CREATE,
        baseRevision = null,
        baseSnapshotJson = null,
        patchJson = patch.toString(),
        optimisticJson = optimistic.toString(),
        createdAtEpochMs = host.nowEpochMillis(),
        optimisticProjection =
            LocalRecordProjection(
                recordDate = date,
                accountId = account.recordId,
                categoryId = requestedCategoryId,
                payeeId = payeeId,
                amountMinor = amount,
                budgetMonth = date.take(7),
            ),
    )

/** A split's lines as the outbox sends them: each an amount and a category. */
private fun splitLinesJson(lines: List<Pair<Long, String>>): JsonArray =
    JsonArray(
        lines.map { (amount, category) ->
            buildJsonObject {
                put("amount", amount)
                put("category", category)
            }
        },
    )

private fun JsonObject.withSplit(split: JsonArray?): JsonObject =
    if (split ==
        null
    ) {
        this
    } else {
        JsonObject(this + ("subtransactions" to split))
    }
