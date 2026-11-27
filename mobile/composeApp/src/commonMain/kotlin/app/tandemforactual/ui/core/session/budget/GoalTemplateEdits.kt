package app.tandemforactual.ui.core.session.budget

import app.tandemforactual.api.client.budgetGoalTemplateStatus
import app.tandemforactual.api.client.checkBudgetGoalTemplates
import app.tandemforactual.api.client.previewBudgetGoalTemplates
import app.tandemforactual.data.actual.GoalTemplateOutcome
import app.tandemforactual.data.actual.GoalTemplateStatus
import app.tandemforactual.ui.core.session.BudgetReorderKind
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Queues Actual's "Apply budget templates" for [month], or with [force] "Overwrite with budget templates". The device's
 * own [preview] is shown until Actual has the change; the templates are worked out again when the edit is sent.
 */
internal suspend fun BudgetEdits.applyGoalTemplates(
    budgetId: String,
    month: String,
    force: Boolean,
    categoryIds: List<String>?,
    preview: GoalTemplateOutcome,
    encryptionPassword: String?,
): Boolean =
    queue.enqueueOfflineBudgetMutation(
        budgetId,
        BudgetReorderKind.APPLY_GOAL_TEMPLATES,
        month,
        "budget-$month",
        buildJsonObject {
            put("month", month)
            put("force", force)
            categoryIds?.let { ids -> put("categoryIds", JsonArray(ids.map(::JsonPrimitive))) }
            put(
                "assignments",
                JsonArray(
                    preview.values.map { value ->
                        buildJsonObject {
                            put("categoryId", value.categoryId)
                            put("amountMinor", value.budgeted)
                        }
                    },
                ),
            )
        },
        encryptionPassword,
    )

/** What applying templates to [month] would budget, from the device's copy; null when the budget can't be read. */
internal suspend fun BudgetEdits.previewGoalTemplates(
    budgetId: String,
    month: String,
    force: Boolean,
    categoryIds: List<String>?,
    encryptionPassword: String?,
): GoalTemplateOutcome? =
    try {
        host.apiClient.previewBudgetGoalTemplates(budgetId, month, force, categoryIds, encryptionPassword)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

/** Template lines Actual would flag, from the device's copy. */
internal suspend fun BudgetEdits.checkGoalTemplates(
    budgetId: String,
    encryptionPassword: String?,
): List<String> =
    try {
        host.apiClient.checkBudgetGoalTemplates(budgetId, encryptionPassword)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        emptyList()
    }

/** A category's templates and how it stands against them, from the device's copy; null when it can't be read. */
internal suspend fun BudgetEdits.goalTemplateStatus(
    budgetId: String,
    month: String,
    categoryId: String,
    encryptionPassword: String?,
): GoalTemplateStatus? =
    try {
        host.apiClient.budgetGoalTemplateStatus(budgetId, month, categoryId, encryptionPassword)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }
