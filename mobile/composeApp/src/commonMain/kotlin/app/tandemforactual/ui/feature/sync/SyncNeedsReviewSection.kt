@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sync
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.format.formatSignedCurrency
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budgetMutationStatusLabel
import app.tandemforactual.ui.core.session.decodeBudgetReorderQueue
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.core.state.LiveScheduleSummary
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.core.state.booleanValue
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.formatLiveDate
import app.tandemforactual.ui.core.state.formatSignedCurrency
import app.tandemforactual.ui.core.state.longValue
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.core.state.stringValue
import app.tandemforactual.ui.core.state.writeStatusLabel
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Cloud
import com.composables.icons.lucide.CloudAlert
import com.composables.icons.lucide.Lucide
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Rows for every transaction, schedule action and budget edit that needs a decision. */
@Composable
internal fun SyncNeedsReviewSection(
    work: SyncCenterWork,
    attentionCount: Int,
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    liveTransactions: List<TransactionItem>,
    liveBudgetMonth: LiveBudgetMonthSummary?,
    liveSchedules: List<LiveScheduleSummary>,
) {
    val transactionsNeedingReview = work.transactionsNeedingReview
    val schedulesNeedingReview = work.schedulesNeedingReview
    val budgetMutationReviews = work.budgetMutationReviews
    SyncChangeSection(
        title = "Needs review",
        detail = "$attentionCount ${if (attentionCount == 1) "change" else "changes"}",
    ) {
        transactionsNeedingReview.forEachIndexed { index, operation ->
            val transaction = liveTransactions.firstOrNull { it.recordId == operation.recordId }
            SyncChangeRow(
                title = transaction?.payee ?: "Transaction",
                summary = transactionChangeSummary(operation, transaction, state, mobileSession),
                status = writeStatusLabel(operation.status),
                attention = true,
                showDivider = index > 0,
                onClick = {
                    mobileSession.clearTransactionWriteMessage()
                    state.selectedConflictOperationId = operation.operationId
                    navigation.open(
                        if (operation.status == "conflict") AppRoute.CONFLICT_REVIEW else AppRoute.UNCERTAIN_WRITE,
                    )
                },
            )
        }
        schedulesNeedingReview.forEachIndexed { index, operation ->
            val schedule = liveSchedules.firstOrNull { it.id == operation.recordId }
            SyncChangeRow(
                title = scheduleActionTitle(operation.action, schedule?.name),
                summary = scheduleActionSummary(operation.action, schedule, state),
                status = writeStatusLabel(operation.status),
                attention = true,
                showDivider = transactionsNeedingReview.isNotEmpty() || index > 0,
                onClick = {
                    state.pendingScheduleToOpenId = operation.recordId
                    navigation.open(AppRoute.SCHEDULES)
                },
            )
        }
        budgetMutationReviews.forEachIndexed { index, operation ->
            val mutation = decodeBudgetReorderQueue(operation.patchJson).moves.singleOrNull()
            val itemName = budgetMutationItemName(mutation, operation, mobileSession, liveBudgetMonth)
            SyncChangeRow(
                title = itemName,
                summary =
                    mutation?.let { budgetMutationPendingSummary(it, state, mobileSession) }
                        ?: budgetMutationTitle(operation.action, itemName),
                status = budgetMutationStatusLabel(operation.status),
                attention = true,
                showDivider =
                    transactionsNeedingReview.isNotEmpty() ||
                        schedulesNeedingReview.isNotEmpty() ||
                        index > 0,
                onClick = {
                    state.selectedConflictOperationId = operation.operationId
                    navigation.open(AppRoute.UNCERTAIN_WRITE)
                },
            )
        }
    }
}

@Composable
private fun SyncChangeRow(
    title: String,
    summary: String,
    status: String,
    attention: Boolean,
    showDivider: Boolean,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    val statusColor =
        when {
            status == "Rejected" -> palette.negative
            attention -> palette.warning
            else -> palette.syncing
        }
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = "Review $title", onClick = onClick)
                .padding(horizontal = TandemSpacing.medium, vertical = TandemSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(TandemShapes.medium))
                    .background(statusColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (attention) Lucide.CloudAlert else Lucide.Cloud,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = statusColor,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall)) {
                Text(
                    title,
                    color = palette.text,
                    fontSize = TandemTypography.compactBody,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    summary,
                    color = palette.muted,
                    fontSize = TandemTypography.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall)) {
                Text(status, color = statusColor, fontSize = TandemTypography.micro, fontWeight = FontWeight.SemiBold)
                Icon(Lucide.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp), tint = palette.muted)
            }
        }
        if (showDivider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 64.dp, end = TandemSpacing.medium)
                    .height(1.dp)
                    .background(palette.border),
            )
        }
    }
}

private fun transactionChangeSummary(
    operation: OutboxOperationEntity,
    transaction: TransactionItem?,
    state: AppState,
    mobileSession: TandemMobileSession,
): String {
    val patch = runCatching { Json.parseToJsonElement(operation.patchJson) as? JsonObject }.getOrNull()
    val changes =
        buildList {
            patch?.longValue("amount")?.let { add("Amount ${state.formatSignedCurrency(it)}") }
            val payeeId = patch?.stringValue("payee") ?: patch?.stringValue("payeeId")
            payeeId?.let { id ->
                val value = mobileSession.payeeRecords.firstOrNull { it.recordId == id }?.recordName() ?: id
                add("Payee $value")
            }
            val categoryId = patch?.stringValue("category") ?: patch?.stringValue("categoryId")
            categoryId?.let { id ->
                val value = mobileSession.categoryRecords.firstOrNull { it.recordId == id }?.recordName() ?: "Uncategorized"
                add("Category $value")
            }
            patch?.stringValue("date")?.let { add("Date ${formatLiveDate(it)}") }
            if (patch?.containsKey("notes") == true) add("Notes updated")
            patch?.booleanValue("cleared")?.let { add(if (it) "Marked cleared" else "Marked uncleared") }
            patch?.booleanValue("reconciled")?.let { add(if (it) "Marked reconciled" else "Marked unreconciled") }
        }
    val change =
        changes.take(2).joinToString(" · ").ifBlank {
            if (operation.action == "DELETE") "Delete this transaction" else "Transaction edit"
        }
    return listOfNotNull(change, transactionIdentity(operation, transaction, state, mobileSession)).joinToString(" · ")
}

private fun scheduleActionTitle(
    action: String,
    scheduleName: String?,
): String {
    val name = scheduleName ?: "scheduled transaction"
    return when (action) {
        "skip" -> "Skip $name"
        "post" -> "Post $name"
        "edit" -> "Edit $name"
        "delete" -> "Delete $name"
        else -> "Scheduled transaction"
    }
}

private fun scheduleActionSummary(
    action: String,
    schedule: LiveScheduleSummary?,
    state: AppState,
): String {
    val amount = schedule?.amountMinor?.let(state::formatCurrency)
    val date = schedule?.nextDate?.let(::formatLiveDate)
    return listOfNotNull(
        when (action) {
            "skip" -> "Skip the next payment"
            "post" -> "Add the scheduled payment to Actual"
            "delete" -> "Remove this scheduled item"
            "edit" -> "Update the scheduled item"
            else -> null
        },
        amount,
        date,
    ).joinToString(" · ").ifBlank { "Review this scheduled change" }
}
