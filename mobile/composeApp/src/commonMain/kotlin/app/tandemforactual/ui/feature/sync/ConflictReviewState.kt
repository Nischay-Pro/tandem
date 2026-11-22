@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.conflicts.conflictReviewRevision
import app.tandemforactual.ui.core.state.AppState

/** Saved review progress for the conflict being reviewed: the reviewed record and revision, and the chosen side. */
internal class ConflictReviewState(
    reviewedRecordId: MutableState<String?>,
    reviewedRevision: MutableState<String?>,
    redirectedToLatestConflict: MutableState<Boolean>,
    serverCheckAttempt: MutableState<Int>,
    keepThisDevice: MutableState<Boolean>,
) {
    var reviewedRecordId by reviewedRecordId
    var reviewedRevision by reviewedRevision
    var redirectedToLatestConflict by redirectedToLatestConflict
    var serverCheckAttempt by serverCheckAttempt
    var keepThisDevice by keepThisDevice
}

@Composable
internal fun rememberConflictReviewState(
    state: AppState,
    operation: OutboxOperationEntity?,
): ConflictReviewState {
    val reviewedRecordId = rememberSaveable(state.selectedConflictOperationId) { mutableStateOf<String?>(null) }
    val reviewedRevision = rememberSaveable(state.selectedConflictOperationId) { mutableStateOf<String?>(null) }
    val redirectedToLatestConflict = remember { mutableStateOf(false) }
    val serverCheckAttempt = rememberSaveable(operation?.operationId) { mutableStateOf(0) }
    val keepThisDevice = rememberSaveable(operation?.operationId) { mutableStateOf(true) }
    return ConflictReviewState(reviewedRecordId, reviewedRevision, redirectedToLatestConflict, serverCheckAttempt, keepThisDevice)
}

/** Tracks the reviewed record, follows a replacement conflict, and confirms the conflict with the server. */
@Composable
internal fun ConflictReviewEffects(
    review: ConflictReviewState,
    operation: OutboxOperationEntity?,
    record: BudgetRecordEntity?,
    serverConfirmed: Boolean,
    state: AppState,
    mobileSession: TandemMobileSession,
) {
    LaunchedEffect(operation?.recordId) {
        if (review.reviewedRecordId == null) review.reviewedRecordId = operation?.recordId
    }
    LaunchedEffect(operation?.operationId, state.selectedConflictOperationId, mobileSession.pendingWrites) {
        if (operation == null && !review.reviewedRecordId.isNullOrBlank()) {
            val latestConflict =
                conflictReplacementForRecord(
                    mobileSession.pendingWrites,
                    state.selectedConflictOperationId,
                    review.reviewedRecordId,
                )
            if (latestConflict != null) {
                review.redirectedToLatestConflict = true
                state.selectedConflictOperationId = latestConflict.operationId
            }
        }
    }
    LaunchedEffect(operation?.operationId, review.serverCheckAttempt) {
        operation?.let {
            mobileSession.conflicts.confirmConflictWithServer(
                state.selectedBudgetId,
                it.operationId,
                state.encryptedPassword.takeIf(String::isNotBlank),
            )
        }
    }
    LaunchedEffect(operation?.operationId, serverConfirmed, record?.remoteRevision, record?.remoteDeleted) {
        if (review.reviewedRevision == null) review.reviewedRevision = record?.let(::conflictReviewRevision)
    }
}
