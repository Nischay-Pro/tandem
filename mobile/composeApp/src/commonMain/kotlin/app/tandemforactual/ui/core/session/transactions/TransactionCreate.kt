package app.tandemforactual.ui.core.session.transactions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.data.store.BudgetDisplaySettings
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
import app.tandemforactual.ui.core.state.recordBody
import app.tandemforactual.ui.design.inputs.MAX_TRANSACTION_AMOUNT_MINOR_UNITS
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.uuid.Uuid

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
): Boolean {
    host.reportTransactionWriteMessage(null)
    if (host.refuseWriteInReadOnlyMode()) return false
    if (!host.isCurrentIdentityCacheOwner()) {
        host.reportTransactionWriteMessage("Sign in to the server and account that own this budget before adding a transaction.")
        return false
    }
    val amount = parseAmountMinorUnits(amountText, displaySettings)
    if (amount == null) {
        host.reportTransactionWriteMessage(transactionAmountInputError(amountText, displaySettings))
        return false
    }
    if (amount == 0L) {
        host.reportTransactionWriteMessage("Enter an amount greater than zero.")
        return false
    }
    if (amount == Long.MIN_VALUE || kotlin.math.abs(amount) > MAX_TRANSACTION_AMOUNT_MINOR_UNITS) {
        host.reportTransactionWriteMessage("Largest amount is ${formatCurrency(MAX_TRANSACTION_AMOUNT_MINOR_UNITS, displaySettings)}.")
        return false
    }
    val dateValidation = validateTransactionDate(dateText, host.currentDateIso(), confirmedFutureDate)
    if (dateValidation.status != TransactionDateValidationStatus.VALID) {
        host.reportTransactionWriteMessage(transactionDateValidationMessage(dateValidation))
        return false
    }
    val date = checkNotNull(dateValidation.date)
    val localRepository = host.repository
    val coordinator = host.syncCoordinator
    if (localRepository == null || coordinator == null) {
        host.reportTransactionWriteMessage("Budget data isn’t available on this device. Reopen the budget and try again.")
        return false
    }
    val account = host.accountRecords.firstOrNull { it.recordId == accountId }
    if (account == null) {
        host.reportTransactionWriteMessage("Choose an account from this budget before saving.")
        return false
    }
    val accountBody = account.recordBody()
    if (accountBody?.booleanValue("offbudget") == true || accountBody?.booleanValue("offBudget") == true) {
        host.reportTransactionWriteMessage("Choose an on-budget account for this transaction.")
        return false
    }
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
    val transactionId = "tandem:${Uuid.random()}"
    val operationId = Uuid.random().toString()
    val requestedCategoryId = categoryId.takeUnless { isTransfer }
    val patch =
        buildJsonObject {
            put("account", account.recordId)
            put("amount", amount)
            put("date", date)
            put("payee", payeeId?.let(::JsonPrimitive) ?: JsonNull)
            put("category", requestedCategoryId?.let(::JsonPrimitive) ?: JsonNull)
            put("notes", notes)
            put("cleared", false)
        }
    val optimistic =
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
    host.markSavingTransaction(true)
    try {
        localRepository.enqueueWrite(
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
            ),
        )
        val summary =
            if (host.budgetQueue.hasPendingTransactionDependency(budgetId)) {
                null
            } else {
                host.synchronize(budgetId, encryptionPassword)
            }
        val savedWrite = localRepository.findWrite(operationId)
        host.reportTransactionWriteMessage(
            when (savedWrite?.status) {
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
            },
        )
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
