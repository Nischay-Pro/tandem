@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.ui.core.session.BudgetReorderKind
import app.tandemforactual.ui.core.session.QueuedBudgetReorder
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budgetMutationStatusLabel
import app.tandemforactual.ui.core.session.decodeBudgetReorderQueue
import app.tandemforactual.ui.core.session.isRejectedBudgetOrderRequest
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.templates.Heading
import app.tandemforactual.ui.design.templates.InfoBubble
import app.tandemforactual.ui.design.templates.PageColumn

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
            BudgetMutationReviewContent(operation, mutation, itemName, changeTitle, statusLabel, state, mobileSession)
            BudgetMutationActions(operation, mutation, itemName, state, mobileSession, navigation, scope) { confirmDiscard = true }
        }
    }

    if (confirmDiscard && operation != null) {
        DiscardBudgetMutationDialog(
            operation = operation,
            itemName = itemName,
            enabled = !state.readOnlyMode,
            onDismiss = { confirmDiscard = false },
            onConfirm = {
                confirmDiscard = false
                requestedDiscardOperationId = operation.operationId
                onDiscard(operation.operationId)
            },
        )
    }
}

/** Outcome, change details, reason and next-step cards for the budget change under review. */
@Composable
private fun BudgetMutationReviewContent(
    operation: OutboxOperationEntity,
    mutation: QueuedBudgetReorder?,
    itemName: String,
    changeTitle: String,
    statusLabel: String,
    state: AppState,
    mobileSession: TandemMobileSession,
) {
    ReviewOutcomeCard(
        budgetMutationOutcome(operation, statusLabel),
        budgetMutationOutcomeExplanation(operation, mutation),
        isRejected = operation.status == "rejected",
    )
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
}

private fun budgetMutationOutcome(
    operation: OutboxOperationEntity,
    statusLabel: String,
): String =
    when (operation.status) {
        "rejected" -> "Rejected"
        "uncertain" -> "Status unknown"
        "conflict" -> "Needs review"
        "queued" -> "Waiting to sync"
        "sending" -> "Syncing"
        else -> statusLabel
    }

private fun budgetMutationOutcomeExplanation(
    operation: OutboxOperationEntity,
    mutation: QueuedBudgetReorder?,
): String =
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
