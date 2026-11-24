package app.tandemforactual.ui.core.session.budget

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.api.client.StatementImportItem
import app.tandemforactual.api.client.StatementImportPreview
import app.tandemforactual.api.client.TandemApiException
import app.tandemforactual.api.client.previewBudgetAccountStatementImport
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.data.store.budgetWrites
import app.tandemforactual.ui.core.format.formatInteger
import app.tandemforactual.ui.core.session.BudgetReorderKind
import app.tandemforactual.ui.core.session.budget.BudgetEditQueue
import app.tandemforactual.ui.core.session.budget.BudgetEdits
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal suspend fun BudgetEdits.previewStatementImportTransactions(
    budgetId: String,
    accountId: String,
    importedIds: List<String>,
    encryptionPassword: String?,
): StatementImportPreview {
    if (budgetId.isBlank() || !host.apiClient.isAuthenticated) throw TandemApiException("sign_in_required")
    return try {
        host.apiClient.withExclusiveSession {
            host.apiClient.previewBudgetAccountStatementImport(budgetId, accountId, importedIds, encryptionPassword)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: TandemApiException) {
        if (error.httpStatus != null) throw error
        host.markServerReachable(false)
        StatementImportPreview(emptySet(), serverChecked = false)
    }
}

internal suspend fun BudgetEdits.importStatementTransactions(
    budgetId: String,
    accountId: String,
    transactions: List<StatementImportItem>,
    encryptionPassword: String?,
): Boolean {
    if (transactions.isEmpty() || transactions.size > 1_000) {
        host.feedback.budgetActionMessage = "Choose a statement with 1 to 1,000 transactions."
        return false
    }
    val payload =
        buildJsonObject {
            put("accountId", accountId)
            put(
                "transactions",
                JsonArray(
                    transactions.map { transaction ->
                        buildJsonObject {
                            put("date", transaction.date)
                            put("amountMinor", transaction.amountMinor)
                            put("payeeName", transaction.payeeName)
                            transaction.notes?.let { put("notes", it) }
                            put("importedId", transaction.importedId)
                        }
                    },
                ),
            )
        }
    return queue.enqueueOfflineBudgetMutation(
        budgetId,
        BudgetReorderKind.IMPORT_STATEMENT,
        "statement-${Uuid.random()}",
        "imports",
        payload,
        encryptionPassword,
        "${formatInteger(transactions.size)} transactions",
    )
}

/** Transaction changes this device made in the last 90 days, newest first, including ones already sent. */
internal suspend fun BudgetEdits.deviceWriteHistory(budgetId: String): List<OutboxOperationEntity> =
    host.repository
        ?.budgetWrites(budgetId)
        ?.filter { it.recordType == "transaction" }
        ?.sortedByDescending { it.updatedAtEpochMs }
        .orEmpty()
