@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sync
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.design.buttons.PrimaryButton
import app.tandemforactual.ui.design.feedback.BodyText
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette

/** Resolution feedback and the button that keeps the chosen version, confirming before discarding the edit. */
@Composable
internal fun ConflictResolveActions(
    operation: OutboxOperationEntity,
    review: ConflictReviewState,
    keepThisDeviceEnabled: Boolean,
    keepActualEnabled: Boolean,
    mobileSession: TandemMobileSession,
    onResolveConflict: (String, Boolean, String) -> Unit,
) {
    var confirmKeepActual by rememberSaveable(operation.operationId) { mutableStateOf(false) }
    mobileSession.conflicts.conflictResolutionFeedback
        ?.takeIf { it.operationId == operation.operationId }
        ?.let { feedback -> BodyText(feedback.message) }
    Text(
        "Only this transaction will be replaced.",
        color = tandemPalette.muted,
        fontSize = TandemTypography.caption,
    )
    PrimaryButton(
        "Keep this version",
        enabled = if (review.keepThisDevice) keepThisDeviceEnabled else keepActualEnabled,
    ) {
        if (review.keepThisDevice) {
            onResolveConflict(operation.operationId, true, review.reviewedRevision.orEmpty())
        } else {
            confirmKeepActual = true
        }
    }
    if (confirmKeepActual) {
        DiscardConflictEditDialog(
            enabled = keepActualEnabled,
            onDismiss = { confirmKeepActual = false },
            onConfirm = {
                confirmKeepActual = false
                onResolveConflict(operation.operationId, false, review.reviewedRevision.orEmpty())
            },
        )
    }
}
