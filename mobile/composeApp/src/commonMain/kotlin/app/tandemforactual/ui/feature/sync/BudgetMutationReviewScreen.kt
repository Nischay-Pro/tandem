@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sync
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.session.BudgetReorderKind
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.clearBudgetMutationCorrectionMessage
import app.tandemforactual.ui.core.session.budget.correctRejectedCategoryRename
import app.tandemforactual.ui.core.session.budget.retryBudgetMutation
import app.tandemforactual.ui.core.session.budgetMutationCanRetry
import app.tandemforactual.ui.core.session.budgetMutationNeedsRefresh
import app.tandemforactual.ui.core.session.budgetMutationStatusLabel
import app.tandemforactual.ui.core.session.decodeBudgetReorderQueue
import app.tandemforactual.ui.core.session.isRejectedBudgetOrderRequest
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.buttons.PrimaryButton
import app.tandemforactual.ui.design.buttons.SecondaryButton
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.sheets.DestructiveConfirmationDialog
import app.tandemforactual.ui.design.templates.Heading
import app.tandemforactual.ui.design.templates.InfoBubble
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import kotlinx.coroutines.launch

@Composable
internal fun BudgetMutationReviewScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    onDiscard: (String) -> Unit,
) {
    val operation =
        mobileSession.pendingWrites.firstOrNull {
            it.operationId == state.selectedConflictOperationId &&
                it.recordType == "budget-mutation" &&
                isBudgetMutationReviewable(it.status)
        }
    val mutation = operation?.let { decodeBudgetReorderQueue(it.patchJson).moves.singleOrNull() }
    val itemName = budgetMutationItemName(mutation, operation, mobileSession)
    val changeTitle =
        mutation?.let { budgetMutationChangeTitle(it, itemName, state, mobileSession) }
            ?: operation?.let { budgetMutationTitle(it.action, itemName) }
            ?: "Budget change"
    val statusLabel = operation?.let { budgetMutationStatusLabel(it.status) } ?: "Pending"
    val scope = rememberCoroutineScope()
    var showRenameCorrection by rememberSaveable(operation?.operationId) { mutableStateOf(false) }
    var correctedName by rememberSaveable(operation?.operationId) { mutableStateOf(mutation?.name.orEmpty()) }
    val isRejectedCategoryRename = operation?.status == "rejected" && operation.action == "RENAME_CATEGORY"
    var confirmDiscard by rememberSaveable(operation?.operationId) { mutableStateOf(false) }
    var requestedDiscardOperationId by rememberSaveable(state.selectedConflictOperationId) { mutableStateOf<String?>(null) }

    LaunchedEffect(state.selectedConflictOperationId) {
        mobileSession.clearTransactionWriteMessage()
    }
    LaunchedEffect(requestedDiscardOperationId, operation?.operationId) {
        if (requestedDiscardOperationId != null && operation?.operationId != requestedDiscardOperationId) {
            navigation.back()
        }
    }

    PageColumn {
        if (operation == null) {
            Heading("Change no longer needs review")
            InfoBubble(mobileSession.transactionWriteMessage ?: "This budget change has already been cleared.")
        } else {
            val outcome =
                when (operation.status) {
                    "rejected" -> "Rejected"
                    "uncertain" -> "Status unknown"
                    "conflict" -> "Needs review"
                    "queued" -> "Waiting to sync"
                    "sending" -> "Syncing"
                    else -> statusLabel
                }
            val outcomeExplanation =
                when {
                    operation.status == "rejected" &&
                        operation.lastErrorCode == "invalid_request" &&
                        mutation?.kind == BudgetReorderKind.SET_CATEGORY_AMOUNT ->
                        "Actual did not receive this assignment. The saved value is unchanged."
                    operation.status == "rejected" && isRejectedBudgetOrderRequest(operation.lastErrorCode, mutation?.kind) ->
                        "Tandem rejected this reorder. Actual’s saved order did not change."
                    operation.status == "rejected" -> "Actual rejected this change. The previous value is unchanged."
                    operation.status == "uncertain" -> "Actual has not confirmed whether this change was saved."
                    operation.status == "conflict" -> "The budget changed since this edit. Review the latest value first."
                    operation.status == "queued" -> "Saved on this device. Waiting to sync."
                    operation.status == "sending" -> "Sending this change to Actual."
                    else -> "Review the change and its current status."
                }
            ReviewOutcomeCard(outcome, outcomeExplanation, isRejected = operation.status == "rejected")
            BudgetMutationChangeDetails(
                changeTitle,
                budgetMutationLastSyncedAssignment(mutation, operation, state, mobileSession),
                budgetMutationAmountChanges(mutation, state, mobileSession),
            )
            BudgetMutationReasonCard(
                title = if (operation.status == "rejected") "WHY IT WAS REJECTED" else "WHAT HAPPENED",
                explanation = budgetMutationReason(operation, mutation, itemName),
            )
            if (operation.status == "rejected" &&
                operation.lastErrorCode == "invalid_request" &&
                mutation?.kind == BudgetReorderKind.SET_CATEGORY_AMOUNT
            ) {
                BudgetMutationReasonCard(
                    title = "WHAT TO DO NEXT",
                    explanation =
                        "Refresh the budget. After the server is updated, assign this category again from Budget.",
                )
            }
            if (operation.status == "rejected" && isRejectedBudgetOrderRequest(operation.lastErrorCode, mutation?.kind)) {
                BudgetMutationReasonCard(
                    title = "WHAT TO DO NEXT",
                    explanation =
                        "Check Actual’s order. Confirm the Tandem server is updated, then make this reorder again.",
                )
            }
            if (mutation?.requiresEncryptionPassword == true && state.encryptedPassword.isBlank()) {
                BudgetMutationReasonCard(
                    title = "ENCRYPTED BUDGET",
                    explanation = "Unlock this budget before Tandem can refresh or try again.",
                )
            }
            mobileSession.transactionWriteMessage?.let { InlineMessage(it, InlineMessageKind.ERROR) }

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
                        enabled =
                            !state.readOnlyMode && !(mutation?.requiresEncryptionPassword == true && state.encryptedPassword.isBlank()),
                    ) { mobileSession.budgetQueue.retryBudgetMutation(operation.operationId) }
                    if (!budgetMutationNeedsRefresh(operation.lastErrorCode) && !budgetMutationCanRetry(operation.lastErrorCode)) {
                        SecondaryButton("Discard change", enabled = !state.readOnlyMode) { confirmDiscard = true }
                    }
                    if (budgetMutationCanRetry(operation.lastErrorCode)) {
                        SecondaryButton("Discard change", enabled = !state.readOnlyMode) { confirmDiscard = true }
                    }
                }
                "rejected" -> {
                    if (isRejectedCategoryRename && mutation != null) {
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
                            SecondaryButton("Discard change", enabled = !state.readOnlyMode) { confirmDiscard = true }
                        }
                    } else if (budgetMutationCanRetry(operation.lastErrorCode)) {
                        PrimaryButton(
                            "Try again",
                            enabled =
                                !state.readOnlyMode && !(mutation?.requiresEncryptionPassword == true && state.encryptedPassword.isBlank()),
                        ) { mobileSession.budgetQueue.retryBudgetMutation(operation.operationId) }
                        SecondaryButton("Discard change", enabled = !state.readOnlyMode) { confirmDiscard = true }
                    } else {
                        PrimaryButton(
                            if (budgetMutationNeedsRefresh(operation.lastErrorCode)) "Refresh budget" else "Check Actual",
                            enabled =
                                !state.readOnlyMode && !(mutation?.requiresEncryptionPassword == true && state.encryptedPassword.isBlank()),
                        ) { mobileSession.budgetQueue.retryBudgetMutation(operation.operationId) }
                        SecondaryButton("Discard change", enabled = !state.readOnlyMode) { confirmDiscard = true }
                    }
                }
                else -> Spacer(Modifier.height(72.dp))
            }
        }
    }

    if (confirmDiscard && operation != null) {
        DestructiveConfirmationDialog(
            title = "Discard this change?",
            message =
                if (operation.status == "uncertain") {
                    "Remove the pending change for $itemName from this device? Actual may already have saved it; this won’t undo a change already stored there."
                } else {
                    "Remove this budget change from this device?"
                },
            confirmLabel = "Discard change",
            dismissLabel = "Keep change",
            enabled = !state.readOnlyMode,
            onDismissRequest = { confirmDiscard = false },
            onConfirm = {
                confirmDiscard = false
                requestedDiscardOperationId = operation.operationId
                onDiscard(operation.operationId)
            },
        )
    }
}
