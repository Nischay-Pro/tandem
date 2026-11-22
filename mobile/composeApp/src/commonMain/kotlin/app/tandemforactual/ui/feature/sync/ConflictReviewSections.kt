@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sync
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.ui.core.format.formatAmount
import app.tandemforactual.ui.core.format.formatSignedCurrency
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.core.state.formatSignedCurrency
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.core.ui.SyncStatusRing
import app.tandemforactual.ui.design.buttons.PrimaryButton
import app.tandemforactual.ui.design.buttons.SecondaryButton
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.BodyText
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.TriangleAlert
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Confirmation shown after a conflict is resolved, with a link to the next open conflict. */
@Composable
internal fun ConflictChoiceSaved(
    openConflicts: List<OutboxOperationEntity>,
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    liveTransactions: List<TransactionItem>,
) {
    val nextConflict = openConflicts.firstOrNull()
    val feedback =
        mobileSession.conflicts.conflictResolutionFeedback
            ?.takeIf { it.operationId == state.selectedConflictOperationId }
            ?.message
    SyncPageHeading(eyebrow = "Sync", title = "Choice saved.", detail = null)
    SyncStatusRing(icon = Lucide.Check, color = tandemPalette.positive, active = false, caption = null)
    BodyText(
        feedback
            ?: if (nextConflict != null) {
                "Your choice has been kept. ${if (openConflicts.size == 1) "One more change needs" else "${openConflicts.size} more changes need"} your review."
            } else {
                "Your choice has been kept."
            },
    )
    if (nextConflict != null) {
        val nextTitle = conflictPayeeTitle(nextConflict, liveTransactions, mobileSession) ?: "Transaction"
        SurfaceCard {
            ListRow("Next up", nextTitle, onClick = { state.selectedConflictOperationId = nextConflict.operationId })
        }
        PrimaryButton("Review next change") { state.selectedConflictOperationId = nextConflict.operationId }
    } else {
        PrimaryButton("Back to Sync Center") { navigation.open(AppRoute.SYNC_CENTER) }
    }
}

/** Heading with the conflict's position and the transaction details as edited on this device. */
@Composable
internal fun ConflictReviewSummary(
    operation: OutboxOperationEntity,
    deviceVersion: JsonObject?,
    userEditedFields: Set<String>?,
    actualDeleted: Boolean,
    openConflicts: List<OutboxOperationEntity>,
    state: AppState,
    mobileSession: TandemMobileSession,
) {
    val position = openConflicts.indexOfFirst { it.operationId == operation.operationId } + 1
    SyncPageHeading(
        eyebrow = if (position > 0 && openConflicts.size > 1) "Change $position of ${openConflicts.size}" else "Needs review",
        title = "Choose what to keep.",
        detail =
            if (actualDeleted) {
                "You edited this transaction on this device, but it was deleted in Actual."
            } else {
                "This transaction changed on this device and in Actual."
            },
    )
    TransactionDetailsCard(
        rows =
            transactionDetailRows(
                current = deviceVersion,
                previous =
                    operation.baseSnapshotJson?.let {
                        runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull()
                    },
                editedFields = userEditedFields.orEmpty(),
                payeeName = { id -> mobileSession.payeeRecords.firstOrNull { it.recordId == id }?.recordName() },
                accountName = { id -> mobileSession.accountRecords.firstOrNull { it.recordId == id }?.recordName() },
                categoryName = { id -> mobileSession.categoryRecords.firstOrNull { it.recordId == id }?.recordName() },
                formatAmount = state::formatSignedCurrency,
            ),
    )
}

/** Whether the Actual version is confirmed, cached or missing, and a note when Actual changed again. */
@Composable
internal fun ConflictReviewStatus(
    serverConfirmed: Boolean,
    hasActualVersion: Boolean,
    showRevisionNote: Boolean,
    reviewRevisionChanged: Boolean,
    latestRevision: String?,
    mobileSession: TandemMobileSession,
    onCheckAgain: () -> Unit,
    onReviewLatest: () -> Unit,
) {
    when {
        mobileSession.isSyncing -> BodyText("Checking the latest transaction with Tandem…")
        serverConfirmed -> Unit
        hasActualVersion ->
            BodyText("Showing the last synced Actual version. Tandem checks it again before applying your choice.")
        else -> {
            BodyText("No Actual version is saved on this device yet. Connect to Tandem to load it before choosing.")
            SecondaryButton("Check again", enabled = !mobileSession.isSavingTransaction, onClick = onCheckAgain)
        }
    }
    if (showRevisionNote) {
        ConflictNote(
            icon = Lucide.TriangleAlert,
            title = "Actual changed again",
            detail = "Review the latest version before choosing.",
        )
        if (reviewRevisionChanged) {
            SecondaryButton("Review latest version", enabled = !latestRevision.isNullOrBlank()) {
                mobileSession.conflicts.clearConflictResolutionFeedback()
                onReviewLatest()
            }
        }
    }
}

/** "What changed?" toggle that expands the field-by-field comparison table. */
@Composable
internal fun ConflictComparisonToggle(
    operationId: String,
    comparisons: List<ConflictFieldComparison>,
    actualLabel: String,
) {
    var showComparison by rememberSaveable(operationId) { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        if (showComparison) 90f else 0f,
        tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTROL_MILLIS),
        label = "conflict-compare-chevron",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TandemShapes.medium))
            .clickable(role = Role.Button) { showComparison = !showComparison }
            .padding(vertical = TandemSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "What changed?",
            Modifier.weight(1f),
            color = tandemPalette.accent,
            fontSize = TandemTypography.body,
            fontWeight = FontWeight.SemiBold,
        )
        Icon(
            Lucide.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = chevronRotation },
            tint = tandemPalette.accent,
        )
    }
    AnimatedVisibility(visible = showComparison) {
        ConflictCompareTable(comparisons, actualLabel)
    }
}
