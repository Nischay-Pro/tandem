@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sync
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.format.formatSignedCurrency
import app.tandemforactual.ui.core.session.BudgetReorderKind
import app.tandemforactual.ui.core.session.QueuedBudgetReorder
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.pendingBudgetMutationsFor
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.core.state.LiveScheduleSummary
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.formatLiveDate
import app.tandemforactual.ui.core.state.formatSignedCurrency
import app.tandemforactual.ui.core.state.longValue
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.core.state.stringValue
import app.tandemforactual.ui.design.buttons.PrimaryButton
import app.tandemforactual.ui.design.feedback.EmptyStateCard
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.templates.PageLazyColumn
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.ArrowLeftRight
import com.composables.icons.lucide.CalendarClock
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Receipt
import com.composables.icons.lucide.WalletCards
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

@Composable
internal fun PendingChangesScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    liveTransactions: List<TransactionItem>,
    liveBudgetMonth: LiveBudgetMonthSummary?,
    liveSchedules: List<LiveScheduleSummary>,
    onSyncNow: (() -> Unit)?,
) {
    if (state.selectedBudgetId.isBlank()) {
        PageColumn {
            EmptyStateCard(
                "Choose a budget",
                "Open an Actual budget to see changes saved on this device.",
                actionLabel = "Choose budget",
                onAction = { navigation.open(AppRoute.SWITCH_BUDGET) },
            )
        }
        return
    }

    val transactions =
        mobileSession.pendingWrites.filter {
            it.budgetId == state.selectedBudgetId &&
                it.recordType == "transaction" &&
                it.status in setOf("queued", "sending")
        }
    val schedules =
        mobileSession.pendingWrites.filter {
            it.budgetId == state.selectedBudgetId &&
                it.recordType == "schedule-action" &&
                it.status in setOf("queued", "sending")
        }
    val budgetMutations = mobileSession.budgetQueue.pendingBudgetMutationsFor(state.selectedBudgetId)
    val pendingCount = transactions.size + schedules.size + budgetMutations.size

    PageLazyColumn {
        item(key = "pending-changes-heading") {
            Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                SyncPageHeading(
                    eyebrow = "Sync",
                    title = if (pendingCount > 0) "Pending changes." else "All caught up.",
                    detail =
                        when (pendingCount) {
                            0 -> "Every change on this device is in Actual."
                            1 -> "One change is saved on this device. It syncs within a minute, or now."
                            else -> "$pendingCount changes are saved on this device. They sync within a minute, or now."
                        },
                )
                if (pendingCount > 0) SyncCountChip("$pendingCount waiting to sync", tandemPalette.syncing)
            }
        }

        pendingTransactionItems(transactions, liveTransactions, state, mobileSession, navigation)

        pendingScheduleItems(schedules, liveSchedules, state, navigation)

        pendingBudgetItems(budgetMutations, liveBudgetMonth, state, mobileSession, navigation)
        if (pendingCount > 0) {
            item(key = "pending-changes-footer") { PendingChangesFooter(pendingCount, mobileSession.isSyncing, onSyncNow) }
        }
        item(key = "pending-changes-bottom-spacer") { Spacer(Modifier.height(72.dp)) }
    }
}

private fun LazyListScope.pendingTransactionItems(
    transactions: List<OutboxOperationEntity>,
    liveTransactions: List<TransactionItem>,
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
) {
    if (transactions.isNotEmpty()) {
        item(key = "pending-transactions-section") {
            SyncChangeSection("Activity", transactions.size.toString()) {
                transactions.forEachIndexed { index, operation ->
                    val transaction = liveTransactions.firstOrNull { it.recordId == operation.recordId }
                    PendingChangeRow(
                        title = transaction?.payee ?: "Transaction",
                        summary = pendingTransactionSummary(operation, transaction, state, mobileSession),
                        icon = Lucide.Receipt,
                        showDivider = index > 0,
                        onClick = {
                            mobileSession.clearTransactionWriteMessage()
                            state.selectedConflictOperationId = operation.operationId
                            navigation.open(AppRoute.UNCERTAIN_WRITE)
                        },
                    )
                }
            }
        }
    }
}

private fun LazyListScope.pendingScheduleItems(
    schedules: List<OutboxOperationEntity>,
    liveSchedules: List<LiveScheduleSummary>,
    state: AppState,
    navigation: AppNavigation,
) {
    if (schedules.isNotEmpty()) {
        item(key = "pending-schedules-section") {
            SyncChangeSection("Scheduled", schedules.size.toString()) {
                schedules.forEachIndexed { index, operation ->
                    val schedule = liveSchedules.firstOrNull { it.id == operation.recordId }
                    PendingChangeRow(
                        title = schedule?.name ?: "Scheduled payment",
                        summary = pendingScheduleSummary(operation.action, schedule, state),
                        icon = Lucide.CalendarClock,
                        showDivider = index > 0,
                        onClick = {
                            state.pendingScheduleToOpenId = operation.recordId
                            navigation.open(AppRoute.SCHEDULES)
                        },
                    )
                }
            }
        }
    }
}

private fun LazyListScope.pendingBudgetItems(
    budgetMutations: List<QueuedBudgetReorder>,
    liveBudgetMonth: LiveBudgetMonthSummary?,
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
) {
    if (budgetMutations.isNotEmpty()) {
        item(key = "pending-budget-section") {
            SyncChangeSection("Budget", budgetMutations.size.toString()) {
                budgetMutations.forEachIndexed { index, mutation ->
                    val operation = mobileSession.pendingWrites.firstOrNull { it.operationId == mutation.operationId }
                    val itemName = budgetMutationItemName(mutation, operation, mobileSession, liveBudgetMonth)
                    PendingChangeRow(
                        title = pendingBudgetMutationTitle(itemName),
                        summary = budgetMutationPendingSummary(mutation, state, mobileSession),
                        icon = if (mutation.kind == BudgetReorderKind.MOVE_BUDGET_MONEY) Lucide.ArrowLeftRight else Lucide.WalletCards,
                        amountChanges = budgetMutationAmountChanges(mutation, state, mobileSession),
                        showDivider = index > 0,
                        onClick = {
                            state.selectedConflictOperationId = mutation.operationId
                            navigation.open(AppRoute.UNCERTAIN_WRITE)
                        },
                    )
                }
            }
        }
    }
}

/** The saved-on-device note and the sync button under the pending changes. */
@Composable
private fun PendingChangesFooter(
    pendingCount: Int,
    isSyncing: Boolean,
    onSyncNow: (() -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.large)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
            Icon(Lucide.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = tandemPalette.positive)
            Text("All edits are saved on this device.", color = tandemPalette.muted, fontSize = TandemTypography.compactBody)
        }
        if (onSyncNow != null) {
            PrimaryButton(
                if (isSyncing) {
                    "Syncing…"
                } else {
                    "Sync $pendingCount ${if (pendingCount == 1) "change" else "changes"}"
                },
                enabled = !isSyncing,
                loading = isSyncing,
                onClick = onSyncNow,
            )
        }
    }
}

@Composable
private fun PendingChangeRow(
    title: String,
    summary: String,
    icon: ImageVector,
    amountChanges: List<BudgetAmountChange> = emptyList(),
    showDivider: Boolean,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = "Review $title", onClick = onClick)
                .padding(horizontal = TandemSpacing.medium, vertical = TandemSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(TandemShapes.medium))
                    .background(palette.accent.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = palette.accent)
            }
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall),
            ) {
                if (amountChanges.isNotEmpty()) {
                    amountChanges.forEach { change ->
                        AmountTransitionLine(change)
                    }
                } else {
                    Text(
                        title,
                        color = palette.text,
                        fontSize = TandemTypography.compactBody,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (summary.isNotBlank()) {
                        Text(
                            summary,
                            color = palette.muted,
                            fontSize = TandemTypography.caption,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Icon(Lucide.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp), tint = palette.muted)
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

private fun pendingTransactionSummary(
    operation: OutboxOperationEntity,
    transaction: TransactionItem?,
    state: AppState,
    mobileSession: TandemMobileSession,
): String {
    val patch = runCatching { Json.parseToJsonElement(operation.patchJson) as? JsonObject }.getOrNull()
    val details =
        buildList {
            patch?.longValue("amount")?.let { add(state.formatSignedCurrency(it)) }
            val categoryId = patch?.stringValue("category") ?: patch?.stringValue("categoryId")
            categoryId?.let { id ->
                add(mobileSession.categoryRecords.firstOrNull { it.recordId == id }?.recordName() ?: "Uncategorized")
            }
            val payeeId = patch?.stringValue("payee") ?: patch?.stringValue("payeeId")
            payeeId?.let { id -> add(mobileSession.payeeRecords.firstOrNull { it.recordId == id }?.recordName() ?: id) }
            patch?.stringValue("date")?.let { add(formatLiveDate(it)) }
            if (patch?.containsKey("notes") == true) add("Note changed")
        }
    return details.take(2).joinToString(" · ").ifBlank {
        if (operation.action == "DELETE") "Delete" else transaction?.let { state.formatSignedCurrency(it.amountMinorUnits) }.orEmpty()
    }
}

private fun pendingScheduleSummary(
    action: String,
    schedule: LiveScheduleSummary?,
    state: AppState,
): String =
    listOfNotNull(
        when (action) {
            "skip" -> "Skip"
            "post" -> "Post"
            "edit" -> "Edit"
            "delete" -> "Delete"
            else -> null
        },
        schedule?.amountMinor?.let(state::formatCurrency),
        schedule?.nextDate?.let(::formatLiveDate),
    ).joinToString(" · ")
