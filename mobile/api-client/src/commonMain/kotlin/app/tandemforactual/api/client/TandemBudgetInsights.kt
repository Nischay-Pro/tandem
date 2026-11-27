package app.tandemforactual.api.client

import app.tandemforactual.data.actual.GoalTemplateOutcome
import app.tandemforactual.data.actual.HealthIssue
import app.tandemforactual.data.actual.HealthRow
import app.tandemforactual.data.actual.UndoResult
import app.tandemforactual.data.planning.AccountMovement
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

/** Each account's transactions, keyed by account id, for sorting contributions from growth and withdrawals. */
suspend fun TandemApiClient.budgetAccountMovements(
    budgetId: String,
    accountIds: List<String>,
    encryptionPassword: String?,
): Map<String, List<AccountMovement>> =
    call {
        val budget = budget(budgetId, encryptionPassword)
        accountIds.associateWith { budget.accountMovements(it) }
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

/** What "Apply templates" would budget for [month], from the device's copy, without changing it. */
suspend fun TandemApiClient.previewBudgetGoalTemplates(
    budgetId: String,
    month: String,
    force: Boolean,
    categoryIds: List<String>?,
    encryptionPassword: String?,
): GoalTemplateOutcome = call { budget(budgetId, encryptionPassword).goalTemplates(today()) { it.preview(month, force, categoryIds) } }

/** Template lines that don't parse and schedules they name that don't exist, worded as Actual's check words them. */
suspend fun TandemApiClient.checkBudgetGoalTemplates(
    budgetId: String,
    encryptionPassword: String?,
): List<String> = call { budget(budgetId, encryptionPassword).goalTemplates(today()) { it.check() } }
