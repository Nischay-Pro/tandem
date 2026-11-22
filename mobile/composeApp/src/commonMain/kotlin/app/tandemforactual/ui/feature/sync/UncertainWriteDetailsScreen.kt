@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sync
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.ui.core.format.formatSignedCurrency
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.core.state.formatSignedCurrency
import app.tandemforactual.ui.design.buttons.PrimaryButton
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.sheets.DestructiveConfirmationDialog
import app.tandemforactual.ui.design.templates.Heading
import app.tandemforactual.ui.design.templates.InfoBubble
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette

@Composable
internal fun UncertainWriteDetailsScreen(
    state: AppState,
    operation: OutboxOperationEntity?,
    transaction: TransactionItem?,
    isChecked: Boolean,
    isNotFound: Boolean,
    checkError: String?,
    isSyncing: Boolean,
    onCheckStatus: () -> Unit,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
    onReviewConflict: () -> Unit,
    onReviewRejected: () -> Unit,
    onDone: () -> Unit,
) {
    val operationPayee = transaction?.payee ?: "this transaction"
    val operationAmount = operation?.optimisticAmountMinor ?: transaction?.amountMinorUnits
    val operationAmountText = operationAmount?.let(state::formatSignedCurrency) ?: "Unavailable"
    var confirmDiscard by rememberSaveable(operation?.operationId) { mutableStateOf(false) }
    val checkErrorForOperation = checkError.takeIf { operation != null }
    val statusCheckFinished = isChecked && !isSyncing
    val canRetryOrRemove =
        operation != null &&
            canOfferUncertainWriteRecoveryActions(
                operation.status,
                statusCheckFinished,
            )
    PageColumn {
        if (operation == null) {
            Heading("Nothing to check")
            InfoBubble("This edit no longer needs a status check.")
        } else {
            val heading =
                when {
                    checkErrorForOperation != null -> "Couldn’t check this change"
                    operation.status == "committed" -> "Saved to Actual"
                    operation.status == "conflict" -> "Needs review"
                    operation.status == "rejected" -> "Rejected"
                    operation.status == "discarded" -> "Change discarded"
                    operation.status == "queued" -> "Waiting to sync"
                    operation.status == "sending" -> "Syncing"
                    isNotFound -> "Status not found"
                    isChecked -> "Status unknown"
                    operation.status == "uncertain" -> "Status unknown"
                    isSyncing -> "Checking status"
                    else -> "Status unknown"
                }
            val message =
                when {
                    checkErrorForOperation != null -> checkErrorForOperation
                    operation.status == "committed" -> "Actual saved this edit."
                    operation.status == "conflict" -> "Actual has a different version. Compare before choosing."
                    operation.status == "rejected" -> "Actual rejected this edit. The previous value is unchanged."
                    operation.status == "discarded" -> "This edit was removed from this device."
                    operation.status == "queued" && isChecked -> "Actual did not save this edit. It is still on this device."
                    operation.status == "queued" -> "Saved on this device. Waiting to sync."
                    operation.status == "sending" && isSyncing -> "Sending this edit to Actual."
                    operation.status == "sending" -> "Tandem couldn’t confirm the save. Check Actual before retrying."
                    isNotFound -> "Actual has no record of this edit. Keep it here or remove it."
                    isChecked -> "Actual couldn’t confirm whether this was saved. Check Actual before retrying."
                    isSyncing -> "Checking Actual for this edit."
                    else -> "Tandem couldn’t confirm whether Actual saved this edit. Check before retrying."
                }
            ReviewOutcomeCard(
                title = heading,
                explanation = message,
                isRejected = operation.status == "rejected" || checkErrorForOperation != null,
            )
            SectionCard(
                transaction?.payee ?: "Transaction",
                transaction?.dateLabel ?: "Saved on this device",
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.large),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Amount", color = tandemPalette.muted, fontSize = TandemTypography.body)
                    Text(
                        operation.optimisticAmountMinor?.let { state.formatSignedCurrency(it) } ?: "Unavailable",
                        color = tandemPalette.text,
                        fontSize = TandemTypography.cardTitle,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            when {
                checkErrorForOperation != null -> {
                    UncertainWriteRecoveryActions(
                        showCheckAgain = true,
                        showRetryAndRemove = canRetryOrRemove,
                        isSyncing = isSyncing,
                        readOnly = state.readOnlyMode,
                        onCheckAgain = onCheckStatus,
                        onRetry = onRetry,
                        onRemove = { confirmDiscard = true },
                    )
                }
                canRetryOrRemove -> {
                    UncertainWriteRecoveryActions(
                        showCheckAgain = isNotFound,
                        showRetryAndRemove = true,
                        isSyncing = isSyncing,
                        readOnly = state.readOnlyMode,
                        onCheckAgain = onCheckStatus,
                        onRetry = onRetry,
                        onRemove = { confirmDiscard = true },
                    )
                }
                operation.status == "uncertain" -> {
                    PrimaryButton(
                        if (isChecked) "Check again" else "Check status",
                        enabled = !isSyncing,
                        loading = isSyncing,
                    ) { onCheckStatus() }
                }
                operation.status == "queued" -> Spacer(Modifier.height(72.dp))
                operation.status == "sending" ->
                    PrimaryButton(
                        if (isSyncing) "Syncing" else "Check status",
                        enabled = !isSyncing,
                        loading = isSyncing,
                    ) {
                        if (!isSyncing) onCheckStatus()
                    }
                operation.status == "conflict" -> PrimaryButton("Review", onClick = onReviewConflict)
                operation.status == "rejected" -> PrimaryButton("Review change", onClick = onReviewRejected)
                else -> PrimaryButton("Done", onClick = onDone)
            }
        }
    }
    if (confirmDiscard && operation != null) {
        DestructiveConfirmationDialog(
            title = "Remove this edit?",
            message = uncertainWriteRemovalWarning(operationPayee, operationAmountText),
            confirmLabel = "Remove edit",
            dismissLabel = "Keep edit",
            enabled = !state.readOnlyMode && !isSyncing,
            onDismissRequest = { if (!isSyncing) confirmDiscard = false },
            onConfirm = {
                confirmDiscard = false
                onDiscard()
            },
        )
    }
}
