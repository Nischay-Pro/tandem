@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sync
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.ui.core.session.QueuedBudgetReorder
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.clearBudgetMutationCorrectionMessage
import app.tandemforactual.ui.core.session.budget.correctRejectedCategoryRename
import app.tandemforactual.ui.core.session.budget.retryBudgetMutation
import app.tandemforactual.ui.core.session.budgetMutationCanRetry
import app.tandemforactual.ui.core.session.budgetMutationNeedsRefresh
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.buttons.PrimaryButton
import app.tandemforactual.ui.design.buttons.SecondaryButton
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Retry, refresh, correct or discard actions for the budget change, depending on its status. */
@Composable
internal fun BudgetMutationActions(
    operation: OutboxOperationEntity,
    mutation: QueuedBudgetReorder?,
    itemName: String,
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    scope: CoroutineScope,
    onDiscard: () -> Unit,
) {
    val retryEnabled = !state.readOnlyMode && !(mutation?.requiresEncryptionPassword == true && state.encryptedPassword.isBlank())
    when (operation.status) {
        "uncertain", "conflict" -> {
            PrimaryButton(
                if (budgetMutationNeedsRefresh(operation.lastErrorCode)) {
                    "Refresh budget"
                } else if (budgetMutationCanRetry(operation.lastErrorCode)) {
                    "Try again"
                } else {
                    "Check Actual"
                },
                enabled = retryEnabled,
            ) { mobileSession.budgetQueue.retryBudgetMutation(operation.operationId) }
            if (!budgetMutationNeedsRefresh(operation.lastErrorCode) && !budgetMutationCanRetry(operation.lastErrorCode)) {
                SecondaryButton("Discard change", enabled = !state.readOnlyMode, onClick = onDiscard)
            }
            if (budgetMutationCanRetry(operation.lastErrorCode)) {
                SecondaryButton("Discard change", enabled = !state.readOnlyMode, onClick = onDiscard)
            }
        }
        "rejected" -> {
            val isRejectedCategoryRename = operation.action == "RENAME_CATEGORY"
            if (isRejectedCategoryRename && mutation != null) {
                CategoryRenameCorrection(operation, mutation, itemName, state, mobileSession, navigation, scope, onDiscard)
            } else if (budgetMutationCanRetry(operation.lastErrorCode)) {
                PrimaryButton(
                    "Try again",
                    enabled = retryEnabled,
                ) { mobileSession.budgetQueue.retryBudgetMutation(operation.operationId) }
                SecondaryButton("Discard change", enabled = !state.readOnlyMode, onClick = onDiscard)
            } else {
                PrimaryButton(
                    if (budgetMutationNeedsRefresh(operation.lastErrorCode)) "Refresh budget" else "Check Actual",
                    enabled = retryEnabled,
                ) { mobileSession.budgetQueue.retryBudgetMutation(operation.operationId) }
                SecondaryButton("Discard change", enabled = !state.readOnlyMode, onClick = onDiscard)
            }
        }
        else -> Spacer(Modifier.height(72.dp))
    }
}

/** Correction flow for a rejected category rename: edit the name and save, or discard the change. */
@Composable
private fun CategoryRenameCorrection(
    operation: OutboxOperationEntity,
    mutation: QueuedBudgetReorder,
    itemName: String,
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    scope: CoroutineScope,
    onDiscard: () -> Unit,
) {
    var showRenameCorrection by rememberSaveable(operation.operationId) { mutableStateOf(false) }
    var correctedName by rememberSaveable(operation.operationId) { mutableStateOf(mutation.name.orEmpty()) }
    if (showRenameCorrection) {
        TextFieldCard(
            "Category name",
            correctedName,
            { correctedName = it },
            TextFieldOptions(placeholder = itemName),
        )
        mobileSession.budgetQueue.budgetMutationCorrectionMessage?.let {
            InlineMessage(it, InlineMessageKind.ERROR)
        }
        PrimaryButton(
            if (mobileSession.budgetQueue.isCorrectingBudgetMutation) "Saving correction" else "Save correction",
            enabled =
                !state.readOnlyMode &&
                    !mobileSession.budgetQueue.isCorrectingBudgetMutation &&
                    correctedName.trim().isNotBlank() &&
                    correctedName.trim() != mutation.name &&
                    (mutation.requiresEncryptionPassword.not() || state.encryptedPassword.isNotBlank()),
            loading = mobileSession.budgetQueue.isCorrectingBudgetMutation,
        ) {
            scope.launch {
                val saved =
                    mobileSession.budgetQueue.correctRejectedCategoryRename(
                        budgetId = operation.budgetId,
                        operationId = operation.operationId,
                        correctedName = correctedName,
                        encryptionPassword = state.encryptedPassword.takeIf(String::isNotBlank),
                    )
                if (saved) navigation.back()
            }
        }
        SecondaryButton(
            "Cancel correction",
            enabled = !mobileSession.budgetQueue.isCorrectingBudgetMutation,
            onClick = {
                showRenameCorrection = false
                mobileSession.budgetQueue.clearBudgetMutationCorrectionMessage()
            },
        )
    } else {
        PrimaryButton(
            "Correct category name",
            enabled =
                !state.readOnlyMode &&
                    (!mutation.requiresEncryptionPassword || state.encryptedPassword.isNotBlank()),
        ) {
            correctedName = mutation.name.orEmpty()
            mobileSession.budgetQueue.clearBudgetMutationCorrectionMessage()
            showRenameCorrection = true
        }
        SecondaryButton("Discard change", enabled = !state.readOnlyMode, onClick = onDiscard)
    }
}
