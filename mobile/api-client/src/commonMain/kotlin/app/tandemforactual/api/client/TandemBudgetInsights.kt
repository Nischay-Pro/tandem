package app.tandemforactual.api.client

import app.tandemforactual.data.actual.HealthIssue
import app.tandemforactual.data.actual.HealthRow
import app.tandemforactual.data.actual.UndoResult
import app.tandemforactual.data.planning.TaggedPayment
import kotlinx.serialization.json.JsonObject

suspend fun TandemApiClient.budgetChangeHistory(
    budgetId: String,
    encryptionPassword: String?,
    limit: Int = 60,
): BudgetChangeHistory =
    call {
        val budget = budget(budgetId, encryptionPassword)
        BudgetChangeHistory(budget.deviceNode, budget.changeHistory(limit))
    }

/** Deposits and transfers into each account by calendar year, keyed by account id; the first year shows when saving began. */
suspend fun TandemApiClient.budgetAccountInflows(
    budgetId: String,
    accountIds: List<String>,
    encryptionPassword: String?,
): Map<String, Map<Int, Long>> =
    call {
        val budget = budget(budgetId, encryptionPassword)
        accountIds.associateWith { budget.inflowsByYear(it) }
    }

/** Money-in transactions since [fromDate] tagged `#`[tag] in their notes, newest first. */
suspend fun TandemApiClient.budgetTaggedPayments(
    budgetId: String,
    tag: String,
    fromDate: String,
    encryptionPassword: String?,
): List<TaggedPayment> = call { budget(budgetId, encryptionPassword).taggedPayments(tag, fromDate) }

suspend fun TandemApiClient.exportBudgetTransactions(
    budgetId: String,
    from: String,
    to: String,
    encryptionPassword: String?,
): String = call { budget(budgetId, encryptionPassword).exportCsv(from, to) }

suspend fun TandemApiClient.previewBudgetRule(
    budgetId: String,
    rule: JsonObject,
    encryptionPassword: String?,
): List<HealthRow> = call { budget(budgetId, encryptionPassword).previewRule(rule) }

suspend fun TandemApiClient.budgetDataHealth(
    budgetId: String,
    encryptionPassword: String?,
): List<HealthIssue> = call { budget(budgetId, encryptionPassword).dataHealth(today()) }

suspend fun TandemApiClient.undoBudgetChange(
    budgetId: String,
    changeId: String,
    encryptionPassword: String?,
): UndoResult = call { budget(budgetId, encryptionPassword).undoChange(changeId) }
