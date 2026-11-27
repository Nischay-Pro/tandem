package app.tandemforactual.api.client

import app.tandemforactual.data.actual.GoalTemplateOutcome
import app.tandemforactual.data.actual.applyGoalTemplates

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

/** Actual's "Apply budget templates" for [month]; [force] overwrites amounts, [categoryIds] limits it to those categories. */
@Suppress("UNUSED_PARAMETER")
suspend fun TandemApiClient.applyBudgetGoalTemplates(
    budgetId: String,
    month: String,
    force: Boolean,
    categoryIds: List<String>?,
    encryptionPassword: String?,
    idempotencyKeyOverride: String? = null,
): GoalTemplateOutcome = edit(budgetId, encryptionPassword) { it.applyGoalTemplates(month, force, categoryIds) }
