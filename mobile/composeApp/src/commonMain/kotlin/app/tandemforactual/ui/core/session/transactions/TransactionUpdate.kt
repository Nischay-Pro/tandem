package app.tandemforactual.ui.core.session.transactions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.LocalOperationConflictException
import app.tandemforactual.data.store.LocalWriteAction
import app.tandemforactual.data.store.LocalWriteRequest
import app.tandemforactual.data.store.discardWrite
import app.tandemforactual.data.store.enqueueWrite
import app.tandemforactual.data.store.findRecord
import app.tandemforactual.data.store.readPendingWrites
import app.tandemforactual.ui.core.format.TransactionDateValidationStatus
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.format.parseAmountMinorUnits
import app.tandemforactual.ui.core.format.signedAmount
import app.tandemforactual.ui.core.format.transactionAmountInputError
import app.tandemforactual.ui.core.format.transactionDateValidationMessage
import app.tandemforactual.ui.core.format.validateTransactionDate
import app.tandemforactual.ui.core.session.TransactionEditDetails
import app.tandemforactual.ui.core.session.blockedTransactionEditMessage
import app.tandemforactual.ui.core.session.buildTransactionEditPatch
import app.tandemforactual.ui.core.session.localStorageFailureMessage
import app.tandemforactual.ui.core.session.pendingTransactionEditMessage
import app.tandemforactual.ui.core.session.queuedTransactionCreateToEdit
import app.tandemforactual.ui.core.session.queuedTransactionEditToDiscardForNoOp
import app.tandemforactual.ui.core.session.stringValue
import app.tandemforactual.ui.core.session.toProjection
import app.tandemforactual.ui.core.session.transactions.TransactionController
import app.tandemforactual.ui.core.state.booleanValue
import app.tandemforactual.ui.core.state.isOffBudgetAccount
import app.tandemforactual.ui.core.state.recordBody
import app.tandemforactual.ui.design.inputs.MAX_TRANSACTION_AMOUNT_MINOR_UNITS
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

internal suspend fun TransactionController.updateTransaction(
    budgetId: String,
    transactionId: String,
    amountText: String,
    notes: String,
    originalAmountMinor: Long,
    transactionType: String,
    displaySettings: BudgetDisplaySettings? = null,
    accountId: String? = null,
    payeeId: String? = null,
    categoryId: String? = null,
    date: String? = null,
    updateDetails: Boolean = false,
    confirmedFutureDate: String? = null,
): Boolean {
    host.reportTransactionWriteMessage(null)
    if (host.refuseWriteInReadOnlyMode()) return false
    if (!host.isCurrentIdentityCacheOwner()) {
        host.reportTransactionWriteMessage("Sign in to the server and account that own this budget before editing a transaction.")
        return false
    }
    val enteredAmount = parseEditedTransactionAmount(amountText, displaySettings) ?: return false
    val dateValidation =
        if (updateDetails) {
            validateTransactionDate(date.orEmpty(), host.currentDateIso(), confirmedFutureDate)
        } else {
            null
        }
    if (dateValidation != null && dateValidation.status != TransactionDateValidationStatus.VALID) {
        host.reportTransactionWriteMessage(transactionDateValidationMessage(dateValidation))
        return false
    }
    val requestedDate = dateValidation?.date
    val localRepository = host.repository
    if (localRepository == null) {
        host.reportTransactionWriteMessage("Budget data isn’t available on this device. Reopen the budget and try again.")
        return false
    }
    val pendingForRecord =
        localRepository
            .readPendingWrites(budgetId)
            .filter { it.recordType == "transaction" && it.recordId == transactionId }
    val blockingWrite = pendingForRecord.firstOrNull { it.status in setOf("sending", "uncertain", "conflict") }
    if (blockingWrite != null) {
        host.reportTransactionWriteMessage(blockedTransactionEditMessage(blockingWrite.status))
        return false
    }
    val current = localRepository.findRecord(budgetId, "transaction", transactionId)
    val queuedCreate = queuedTransactionCreateToEdit(pendingForRecord)
    val editingLocalCreate = queuedCreate != null
    val baseJson = if (editingLocalCreate) current?.localJson else current?.remoteJson
    val revision = if (editingLocalCreate) null else current?.remoteRevision
    if (current == null || current.remoteDeleted || baseJson == null || (!editingLocalCreate && revision.isNullOrBlank())) {
        host.reportTransactionWriteMessage("This transaction isn’t up to date. Sync the budget before editing.")
        return false
    }
    val base = runCatching { Json.parseToJsonElement(baseJson).jsonObject }.getOrNull()
    if (base == null) {
        host.reportTransactionWriteMessage("Tandem couldn’t open this transaction. Sync the budget before editing.")
        return false
    }
    val requestedAccountId = if (updateDetails) accountId?.takeIf(String::isNotBlank) else null
    if (!validateEditedTransactionAccount(updateDetails, requestedAccountId)) return false
    if (!validateEditedTransactionPayee(updateDetails, payeeId, transactionType, requestedAccountId)) return false
    val requestedCategoryId = categoryId.takeUnless { transactionType == "Transfer" || isOffBudget(requestedAccountId) }
    if (updateDetails && requestedCategoryId != null && host.categoryRecords.none { it.recordId == requestedCategoryId }) {
        host.reportTransactionWriteMessage("Choose a category from this budget before saving.")
        return false
    }
    val wasTransfer = (base["transferId"] as? JsonPrimitive)?.contentOrNull != null
    val signedAmount = signedEditAmount(transactionType, enteredAmount, originalAmountMinor, wasTransfer)
    val details = editDetails(updateDetails, requestedAccountId, payeeId, requestedCategoryId, requestedDate)
    val patch = buildTransactionEditPatch(base, signedAmount, current.amountMinor, notes, details)
    if (patch.isEmpty()) {
        return resolveNoOpTransactionEdit(localRepository, budgetId, pendingForRecord, editingLocalCreate)
    }
    return saveTransactionEdit(localRepository, budgetId, transactionId, base, patch, editingLocalCreate, revision, baseJson)
}

private fun TransactionController.parseEditedTransactionAmount(
    amountText: String,
    displaySettings: BudgetDisplaySettings?,
): Long? {
    val enteredAmount = parseAmountMinorUnits(amountText, displaySettings)
    if (enteredAmount == null) {
        host.reportTransactionWriteMessage(transactionAmountInputError(amountText, displaySettings))
        return null
    }
    if (enteredAmount < 0) {
        host.reportTransactionWriteMessage("Enter a positive amount.")
        return null
    }
    if (enteredAmount > MAX_TRANSACTION_AMOUNT_MINOR_UNITS) {
        host.reportTransactionWriteMessage("Largest amount is ${formatCurrency(MAX_TRANSACTION_AMOUNT_MINOR_UNITS, displaySettings)}.")
        return null
    }
    return enteredAmount
}

private fun TransactionController.isOffBudget(accountId: String?): Boolean =
    host.accountRecords
        .firstOrNull { it.recordId == accountId }
        ?.recordBody()
        .isOffBudgetAccount()

private fun TransactionController.validateEditedTransactionAccount(
    updateDetails: Boolean,
    requestedAccountId: String?,
): Boolean {
    if (updateDetails && requestedAccountId == null) {
        host.reportTransactionWriteMessage("Choose an account from this budget before saving.")
        return false
    }
    if (requestedAccountId != null) {
        val account = host.accountRecords.firstOrNull { it.recordId == requestedAccountId }
        val accountBody = account?.recordBody()
        if (account == null || accountBody?.booleanValue("closed") == true) {
            host.reportTransactionWriteMessage("Choose an open account from this budget.")
            return false
        }
    }
    return true
}

private fun TransactionController.validateEditedTransactionPayee(
    updateDetails: Boolean,
    payeeId: String?,
    transactionType: String,
    requestedAccountId: String?,
): Boolean {
    if (updateDetails && payeeId != null && host.payeeRecords.none { it.recordId == payeeId }) {
        host.reportTransactionWriteMessage("Choose an existing payee or clear the payee field.")
        return false
    }
    val transferDestinationId =
        payeeId
            ?.let { id -> host.payeeRecords.firstOrNull { it.recordId == id } }
            ?.recordBody()
            ?.stringValue("transfer_acct")
    if (updateDetails && transactionType == "Transfer" && transferDestinationId.isNullOrBlank()) {
        host.reportTransactionWriteMessage("Choose a destination account for this transfer.")
        return false
    }
    if (updateDetails && transactionType == "Transfer" && transferDestinationId == requestedAccountId) {
        host.reportTransactionWriteMessage("Choose a different destination account.")
        return false
    }
    if (updateDetails && transactionType != "Transfer" && !transferDestinationId.isNullOrBlank()) {
        host.reportTransactionWriteMessage("Choose Transfer as the transaction type for this payee.")
        return false
    }
    return true
}

/**
 * The signed amount to save. An existing transfer keeps its direction; anything changed into a transfer sends money
 * from this account to the chosen one, as a new transfer does.
 */
internal fun signedEditAmount(
    transactionType: String,
    enteredAmount: Long,
    originalAmountMinor: Long,
    wasTransfer: Boolean,
): Long =
    when (transactionType) {
        "Income" -> enteredAmount
        "Transfer" -> if (wasTransfer && originalAmountMinor >= 0L) enteredAmount else -enteredAmount
        else -> -enteredAmount
    }

private suspend fun TransactionController.resolveNoOpTransactionEdit(
    localRepository: LocalBudgetRepository,
    budgetId: String,
    pendingForRecord: List<OutboxOperationEntity>,
    editingLocalCreate: Boolean,
): Boolean {
    val queuedEditId = queuedTransactionEditToDiscardForNoOp(pendingForRecord, editingLocalCreate)
    if (queuedEditId != null) {
        try {
            localRepository.discardWrite(queuedEditId)
            host.loadLocalRecords(budgetId)
            host.reportTransactionWriteMessage("Your transaction matches Actual. Removed the waiting edit.")
            return true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            host.reportTransactionWriteMessage("This transaction started syncing. Check Pending Changes before changing it.")
            host.loadLocalRecords(budgetId)
            return false
        }
    }
    host.reportTransactionWriteMessage("There are no transaction changes to save.")
    return false
}

private suspend fun TransactionController.saveTransactionEdit(
    localRepository: LocalBudgetRepository,
    budgetId: String,
    transactionId: String,
    base: JsonObject,
    patch: JsonObject,
    editingLocalCreate: Boolean,
    revision: String?,
    baseJson: String,
): Boolean {
    host.markSavingTransaction(true)
    try {
        val patchJson = JsonObject(patch)
        val optimistic = JsonObject(base + patch)
        val operationId = Uuid.random().toString()
        localRepository.enqueueWrite(
            LocalWriteRequest(
                operationId = operationId,
                idempotencyKey = operationId,
                budgetId = budgetId,
                recordType = "transaction",
                recordId = transactionId,
                action = if (editingLocalCreate) LocalWriteAction.CREATE else LocalWriteAction.UPDATE,
                baseRevision = revision,
                baseSnapshotJson = if (editingLocalCreate) null else baseJson,
                patchJson = if (editingLocalCreate) optimistic.toString() else patchJson.toString(),
                optimisticJson = optimistic.toString(),
                createdAtEpochMs = host.nowEpochMillis(),
                optimisticProjection = optimistic.toProjection(),
            ),
        )
        host.reportTransactionWriteMessage(null)
        host.loadLocalRecords(budgetId)
        return true
    } catch (error: CancellationException) {
        throw error
    } catch (_: LocalOperationConflictException) {
        host.reportTransactionWriteMessage(pendingTransactionEditMessage())
        host.loadLocalRecords(budgetId)
        return false
    } catch (error: Exception) {
        host.reportTransactionWriteMessage(
            localStorageFailureMessage(
                error,
                "Tandem couldn’t save your edit. Check the budget and try again.",
            ),
        )
        host.loadLocalRecords(budgetId)
        return false
    } finally {
        host.markSavingTransaction(false)
    }
}

private fun editDetails(
    updateDetails: Boolean,
    requestedAccountId: String?,
    payeeId: String?,
    requestedCategoryId: String?,
    requestedDate: String?,
): TransactionEditDetails? =
    if (updateDetails) {
        TransactionEditDetails(
            accountId = checkNotNull(requestedAccountId),
            payeeId = payeeId,
            categoryId = requestedCategoryId,
            date = checkNotNull(requestedDate),
        )
    } else {
        null
    }
