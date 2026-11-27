package app.tandemforactual.ui.core.session.insights

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.tandemforactual.api.client.BudgetChangeHistory
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.api.client.budgetAccountMovements
import app.tandemforactual.api.client.budgetChangeHistory
import app.tandemforactual.api.client.budgetDataHealth
import app.tandemforactual.api.client.budgetRuleDiagnostics
import app.tandemforactual.api.client.budgetTaggedPayments
import app.tandemforactual.api.client.exportBudgetTransactions
import app.tandemforactual.api.client.previewBudgetRule
import app.tandemforactual.api.client.undoBudgetChange
import app.tandemforactual.data.actual.HealthIssue
import app.tandemforactual.data.actual.HealthIssueKind
import app.tandemforactual.data.actual.HealthRow
import app.tandemforactual.data.actual.RuleFinding
import app.tandemforactual.data.actual.UndoResult
import app.tandemforactual.data.planning.AccountMovement
import app.tandemforactual.data.planning.TaggedPayment
import app.tandemforactual.ui.core.session.insights.InsightsController
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject

/** Read-only views of the budget: checkup, change history with undo, rule preview, CSV export and account inflows. */
internal class InsightsController(
    private val apiClient: TandemApiClient,
    private val performBudgetAction: suspend (String, String?, String, suspend () -> Unit) -> Boolean,
) {
    /** The last budget checkup, kept so returning to the screen shows it at once while it checks again. */
    var dataHealthCache by mutableStateOf<Pair<String, List<HealthIssue>>?>(null)
        private set

    /** The last rule checks, kept alongside [dataHealthCache]. */
    var ruleFindingsCache by mutableStateOf<Pair<String, List<RuleFinding>>?>(null)
        private set

    /** Takes fixed rule findings out of the cache right away, before the checks run again. */
    fun dropRuleFindings(keys: Set<String>) {
        ruleFindingsCache = ruleFindingsCache?.let { (budget, list) -> budget to list.filterNot { it.key in keys } }
    }

    /** Takes fixed findings out of the cached checkup right away, before it runs again. */
    fun dropHealthRows(
        kind: HealthIssueKind,
        ids: Set<String>,
    ) {
        dataHealthCache =
            dataHealthCache?.let { (budget, list) ->
                budget to list.map { if (it.kind == kind) HealthIssue(kind, it.rows.filterNot { row -> row.id in ids }) else it }
            }
    }

    suspend fun loadAccountMovements(
        budgetId: String,
        accountIds: List<String>,
        encryptionPassword: String?,
    ): Map<String, List<AccountMovement>> =
        try {
            apiClient.budgetAccountMovements(budgetId, accountIds, encryptionPassword)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyMap()
        }

    suspend fun loadTaggedPayments(
        budgetId: String,
        tag: String,
        fromDate: String,
        encryptionPassword: String?,
    ): List<TaggedPayment>? =
        try {
            apiClient.budgetTaggedPayments(budgetId, tag, fromDate, encryptionPassword)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }

    suspend fun loadChangeHistory(
        budgetId: String,
        encryptionPassword: String?,
    ): BudgetChangeHistory? =
        try {
            apiClient.budgetChangeHistory(budgetId, encryptionPassword)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }

    suspend fun exportTransactions(
        budgetId: String,
        from: String,
        to: String,
        encryptionPassword: String?,
    ): String? =
        try {
            apiClient.exportBudgetTransactions(budgetId, from, to, encryptionPassword)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }

    suspend fun previewRule(
        budgetId: String,
        rule: kotlinx.serialization.json.JsonObject,
        encryptionPassword: String?,
    ): List<HealthRow>? =
        try {
            apiClient.previewBudgetRule(budgetId, rule, encryptionPassword)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }

    suspend fun loadDataHealth(
        budgetId: String,
        encryptionPassword: String?,
    ): List<HealthIssue>? =
        try {
            apiClient.budgetDataHealth(budgetId, encryptionPassword).also { dataHealthCache = budgetId to it }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }

    suspend fun loadRuleFindings(
        budgetId: String,
        encryptionPassword: String?,
    ): List<RuleFinding>? =
        try {
            apiClient.budgetRuleDiagnostics(budgetId, encryptionPassword).also { ruleFindingsCache = budgetId to it }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }

    suspend fun undoChange(
        budgetId: String,
        changeId: String,
        encryptionPassword: String?,
    ): UndoResult? {
        var result: UndoResult? = null
        val done =
            performBudgetAction(budgetId, encryptionPassword, "Couldn’t undo this change. Try again.") {
                result = apiClient.undoBudgetChange(budgetId, changeId, encryptionPassword)
            }
        return result.takeIf { done }
    }
}
