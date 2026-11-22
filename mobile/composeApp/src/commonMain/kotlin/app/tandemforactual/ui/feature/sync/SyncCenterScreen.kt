@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sync
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.ui.core.format.formatAmount
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.format.formatMomentAtEpochMilliseconds
import app.tandemforactual.ui.core.format.formatMonth
import app.tandemforactual.ui.core.format.formatSignedCurrency
import app.tandemforactual.ui.core.session.BudgetReorderKind
import app.tandemforactual.ui.core.session.BudgetSyncOverview
import app.tandemforactual.ui.core.session.QueuedBudgetReorder
import app.tandemforactual.ui.core.session.SyncRecovery
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.budgetMutationReviewsFor
import app.tandemforactual.ui.core.session.budget.deviceWriteHistory
import app.tandemforactual.ui.core.session.budget.pendingBudgetMutationsFor
import app.tandemforactual.ui.core.session.budgetMutationFailureMessage
import app.tandemforactual.ui.core.session.budgetMutationStatusLabel
import app.tandemforactual.ui.core.session.budgetSyncOverview
import app.tandemforactual.ui.core.session.decodeBudgetReorderQueue
import app.tandemforactual.ui.core.session.downloadBudgetAgain
import app.tandemforactual.ui.core.session.isRejectedBudgetOrderRequest
import app.tandemforactual.ui.core.session.orderBatchScopes
import app.tandemforactual.ui.core.session.retryFailedWork
import app.tandemforactual.ui.core.session.syncRecovery
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.core.state.LiveScheduleSummary
import app.tandemforactual.ui.core.state.PlanningSyncStatus
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.core.state.booleanValue
import app.tandemforactual.ui.core.state.budgetDisplayNameForHeader
import app.tandemforactual.ui.core.state.deviceWriteActionLabel
import app.tandemforactual.ui.core.state.deviceWriteStatusLabel
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.formatLiveDate
import app.tandemforactual.ui.core.state.formatSignedCurrency
import app.tandemforactual.ui.core.state.longValue
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.core.state.stringValue
import app.tandemforactual.ui.core.state.writeStatusLabel
import app.tandemforactual.ui.design.buttons.PrimaryButton
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.feedback.LabelText
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.DisplayHeadline
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.templates.PageLazyColumn
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemAlloFamily
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import app.tandemforactual.ui.feature.auth.LocalEncryptedBudgetVault
import app.tandemforactual.ui.feature.transactions.SyncNoticeCard
import com.composables.icons.lucide.ArrowLeftRight
import com.composables.icons.lucide.CalendarClock
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Cloud
import com.composables.icons.lucide.CloudAlert
import com.composables.icons.lucide.CloudCheck
import com.composables.icons.lucide.CloudOff
import com.composables.icons.lucide.CloudSync
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Receipt
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.TriangleAlert
import com.composables.icons.lucide.WalletCards
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

internal fun openNeedsReview(
    state: AppState,
    navigation: AppNavigation,
    transactionReview: OutboxOperationEntity?,
    scheduleReview: OutboxOperationEntity?,
    budgetMutationReview: OutboxOperationEntity?,
): Boolean {
    transactionReview?.let { operation ->
        state.selectedConflictOperationId = operation.operationId
        navigation.open(if (operation.status == "conflict") AppRoute.CONFLICT_REVIEW else AppRoute.UNCERTAIN_WRITE)
        return true
    }
    scheduleReview?.let { operation ->
        state.pendingScheduleToOpenId = operation.recordId
        navigation.open(AppRoute.SCHEDULES)
        return true
    }
    budgetMutationReview?.let { operation ->
        state.selectedConflictOperationId = operation.operationId
        navigation.open(AppRoute.UNCERTAIN_WRITE)
        return true
    }
    return false
}

@Composable
internal fun SyncCenterScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    liveTransactions: List<TransactionItem>,
    liveBudgetMonth: LiveBudgetMonthSummary?,
    liveSchedules: List<LiveScheduleSummary>,
    onSyncNow: () -> Unit,
) {
    if (state.selectedBudgetId.isBlank()) {
        PageColumn {
            EmptyState(
                "Choose a budget",
                "Open an Actual budget to see changes waiting to sync.",
                actionLabel = "Choose budget",
                onAction = { navigation.open(AppRoute.SWITCH_BUDGET) },
            )
        }
        return
    }

    val transactionsNeedingReview =
        mobileSession.pendingWrites.filter {
            it.budgetId == state.selectedBudgetId &&
                it.recordType == "transaction" &&
                it.status in setOf("conflict", "uncertain", "rejected")
        }
    val transactionsWaiting =
        mobileSession.pendingWrites.filter {
            it.budgetId == state.selectedBudgetId && it.recordType == "transaction" && it.status in setOf("queued", "sending")
        }
    val budgetMutations = mobileSession.budgetQueue.pendingBudgetMutationsFor(state.selectedBudgetId)
    val budgetMutationReviews = mobileSession.budgetQueue.budgetMutationReviewsFor(state.selectedBudgetId)
    val scheduleActions =
        mobileSession.pendingWrites.filter {
            it.budgetId == state.selectedBudgetId &&
                it.recordType == "schedule-action" &&
                it.status in setOf("queued", "sending", "uncertain", "conflict", "rejected")
        }
    val schedulesNeedingReview = scheduleActions.filter { it.status in setOf("uncertain", "conflict", "rejected") }
    val schedulesWaiting = scheduleActions.filter { it.status in setOf("queued", "sending") }
    val attentionCount = transactionsNeedingReview.size + budgetMutationReviews.size + schedulesNeedingReview.size
    val waitingCount = transactionsWaiting.size + budgetMutations.size + schedulesWaiting.size

    val scope = rememberCoroutineScope()
    val vault = LocalEncryptedBudgetVault.current
    val retryable =
        transactionsNeedingReview.count { it.status == "uncertain" } + mobileSession.banking.bankLinks.count { it.failure != null } +
            (if (mobileSession.planning.syncStatus == PlanningSyncStatus.WAITING) 1 else 0)
    val conflictCount = transactionsNeedingReview.count { it.status == "conflict" }
    var retrying by remember { mutableStateOf(false) }
    val budgetOverview by produceState(
        emptyList<BudgetSyncOverview>(),
        mobileSession.budgets,
        mobileSession.pendingWrites,
        mobileSession.localSyncState,
        state.selectedBudgetId,
    ) { value = mobileSession.budgetSyncOverview(state.selectedBudgetId) }
    LaunchedEffect(state.selectedBudgetId, mobileSession.isAuthenticated) {
        if (mobileSession.isAuthenticated) {
            runCatching {
                mobileSession.banking.loadLinkedAccounts(
                    state.selectedBudgetId,
                    state.encryptedPassword.takeIf { it.isNotBlank() },
                )
            }
        }
    }
    PageLazyColumn {
        val capabilities = mobileSession.serverCapabilities
        if (capabilities?.clientUpdateRequired == true) {
            item(key = "sync-center-app-update") {
                SyncNoticeCard(
                    Lucide.TriangleAlert,
                    tandemPalette.warning,
                    "Update the Tandem app",
                    "Your Tandem server needs a newer version of this app to sync every change.",
                    onClick = null,
                )
            }
        } else if (capabilities?.isLegacy == true) {
            item(key = "sync-center-server-update") {
                SyncNoticeCard(
                    Lucide.TriangleAlert,
                    tandemPalette.warning,
                    "Update your Tandem server",
                    "Some changes wait on this device until your server is updated. They sync automatically after that.",
                    onClick = null,
                )
            }
        }
        item(key = "sync-center-overview") {
            SyncOverviewCard(
                budgetName = budgetDisplayNameForHeader(state.selectedBudget),
                attentionCount = attentionCount,
                waitingCount = waitingCount,
                isSyncing = mobileSession.isSyncing,
                isServerReachable = mobileSession.isServerReachable,
                syncError = mobileSession.syncError,
                syncRecovery = mobileSession.syncRecovery,
                onRecover = {
                    when (mobileSession.syncRecovery) {
                        SyncRecovery.SIGN_IN -> navigation.open(AppRoute.SIGN_IN)
                        SyncRecovery.CHECK_SERVER -> navigation.open(AppRoute.SERVER_ADDRESS)
                        SyncRecovery.CHOOSE_BUDGET -> navigation.open(AppRoute.SWITCH_BUDGET)
                        SyncRecovery.RETRY -> onSyncNow()
                        SyncRecovery.DOWNLOAD_AGAIN ->
                            scope.launch {
                                mobileSession.downloadBudgetAgain(state.selectedBudgetId, vault)
                                navigation.reset(AppRoute.SWITCH_BUDGET)
                            }
                    }
                },
                lastSyncedAtEpochMs = mobileSession.localSyncState?.lastSyncedAtEpochMs,
                nowEpochMilliseconds = mobileSession.nowEpochMillis(),
                onSyncNow = onSyncNow.takeIf { !mobileSession.isSyncing },
                onPendingClick = { navigation.open(AppRoute.PENDING_CHANGES) },
                onReviewClick = {
                    if (!openNeedsReview(
                            state,
                            navigation,
                            transactionsNeedingReview.firstOrNull(),
                            schedulesNeedingReview.firstOrNull(),
                            budgetMutationReviews.firstOrNull(),
                        )
                    ) {
                        navigation.open(AppRoute.CONFLICT_INBOX)
                    }
                },
            )
        }

        if (attentionCount > 0) {
            item(key = "sync-center-review-section") {
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
        }

        if (retryable > 0 || conflictCount > 0) {
            item(key = "sync-center-actions") {
                SyncCenterActions(
                    retryableCount = retryable,
                    conflictCount = conflictCount,
                    busy = retrying || mobileSession.isSyncing,
                    onRetry = {
                        retrying = true
                        scope.launch {
                            try {
                                mobileSession.retryFailedWork(state.selectedBudgetId, state.encryptedPassword.takeIf { it.isNotBlank() })
                            } finally {
                                retrying = false
                            }
                        }
                    },
                    onResolve = { navigation.open(AppRoute.CONFLICT_INBOX) },
                )
            }
        }
        if (budgetOverview.size > 1) {
            item(key = "sync-center-budgets") {
                SyncCenterBudgets(budgetOverview, mobileSession) { navigation.open(AppRoute.SWITCH_BUDGET) }
            }
        }
        if (mobileSession.banking.bankLinks.isNotEmpty()) {
            item(key = "sync-center-bank-links") {
                SyncCenterBankLinks(mobileSession) { navigation.open(AppRoute.BANK_CONNECTIONS) }
            }
        }
        item(key = "sync-center-services") {
            SyncCenterServices(mobileSession) { navigation.open(AppRoute.SETTINGS) }
        }
        item(key = "sync-center-history") { DeviceWriteHistory(state, mobileSession) }
        item(key = "sync-center-bottom-spacer") { Spacer(Modifier.height(72.dp)) }
    }
}

private const val DEVICE_HISTORY_PREVIEW = 8

@Composable
private fun DeviceWriteHistory(
    state: AppState,
    mobileSession: TandemMobileSession,
) {
    val budgetId = state.selectedBudgetId
    val history by produceState(emptyList<OutboxOperationEntity>(), budgetId, mobileSession.pendingWrites, mobileSession.localSyncState) {
        value = mobileSession.budgetEdits.deviceWriteHistory(budgetId)
    }
    var showAll by rememberSaveable(budgetId) { mutableStateOf(false) }
    if (history.isEmpty()) return
    val payeeNames = remember(mobileSession.payeeRecords) { mobileSession.payeeRecords.associate { it.recordId to it.recordName() } }
    val now = mobileSession.nowEpochMillis()
    SectionCard("Sent from this device", "Your transaction changes from the last 90 days") {
        val shown = if (showAll) history else history.take(DEVICE_HISTORY_PREVIEW)
        shown.forEachIndexed { index, write ->
            if (index > 0) DividerLine()
            val body =
                runCatching {
                    Json
                        .parseToJsonElement(
                            write.optimisticJson ?: write.baseSnapshotJson ?: "{}",
                        ).jsonObject
                }.getOrNull()
            val payee = (body?.get("payee") as? JsonPrimitive)?.contentOrNull?.let { payeeNames[it] }
            val amount = (body?.get("amount") as? JsonPrimitive)?.longOrNull
            ListRow(
                "${deviceWriteActionLabel(write.action)} ${payee ?: "a transaction"}",
                "${deviceWriteStatusLabel(
                    write.status,
                    write.lastErrorCode,
                )} · ${formatMomentAtEpochMilliseconds(write.updatedAtEpochMs, now)}",
                amount?.let { state.formatCurrency(it) },
                leading = RowLeading.None,
                options = ListRowOptions(dense = true, trailingColor = tandemPalette.muted),
            )
        }
        if (history.size > DEVICE_HISTORY_PREVIEW) {
            DividerLine()
            ListRow(
                if (showAll) "Show fewer" else "Show all ${history.size}",
                leading = RowLeading.None,
                options = ListRowOptions(dense = true),
                onClick = { showAll = !showAll },
            )
        }
    }
}

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
            EmptyState(
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
        if (pendingCount > 0) {
            item(key = "pending-changes-footer") {
                Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.large)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        Icon(Lucide.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = tandemPalette.positive)
                        Text("All edits are saved on this device.", color = tandemPalette.muted, fontSize = TandemTypography.compactBody)
                    }
                    if (onSyncNow != null) {
                        PrimaryButton(
                            if (mobileSession.isSyncing) {
                                "Syncing…"
                            } else {
                                "Sync $pendingCount ${if (pendingCount == 1) "change" else "changes"}"
                            },
                            enabled = !mobileSession.isSyncing,
                            loading = mobileSession.isSyncing,
                            onClick = onSyncNow,
                        )
                    }
                }
            }
        }
        item(key = "pending-changes-bottom-spacer") { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
internal fun SyncCountChip(
    label: String,
    color: androidx.compose.ui.graphics.Color,
) {
    Text(
        label.uppercase(),
        Modifier
            .clip(RoundedCornerShape(TandemShapes.pill))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = TandemSpacing.medium, vertical = TandemSpacing.extraSmall),
        color = color,
        fontSize = TandemTypography.caption,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = TandemTypography.captionLetterSpacing,
    )
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

@Composable
internal fun AmountTransitionLine(change: BudgetAmountChange) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
    ) {
        Text(
            change.label,
            modifier = Modifier.weight(1f),
            color = tandemPalette.text,
            fontSize = TandemTypography.caption,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(change.before, color = tandemPalette.muted, fontSize = TandemTypography.caption, maxLines = 1)
        Text("→", color = tandemPalette.accent, fontSize = TandemTypography.caption, fontWeight = FontWeight.Bold)
        Text(change.after, color = tandemPalette.text, fontSize = TandemTypography.caption, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun SyncOverviewCard(
    budgetName: String,
    attentionCount: Int,
    waitingCount: Int,
    isSyncing: Boolean,
    isServerReachable: Boolean,
    syncError: String?,
    syncRecovery: SyncRecovery,
    onRecover: () -> Unit,
    lastSyncedAtEpochMs: Long?,
    nowEpochMilliseconds: Long,
    onSyncNow: (() -> Unit)?,
    onPendingClick: () -> Unit,
    onReviewClick: () -> Unit,
) {
    val palette = tandemPalette
    var showErrorDetails by rememberSaveable(syncError) { mutableStateOf(false) }
    val status =
        when {
            isSyncing ->
                SyncStatusPresentation(
                    title = "Syncing now",
                    detail = "Getting the latest from Actual and sending your changes.",
                    icon = Lucide.CloudSync,
                    color = palette.syncing,
                )
            !isServerReachable ->
                SyncStatusPresentation(
                    title = "You’re offline",
                    detail =
                        when (waitingCount) {
                            0 -> "Showing the budget saved on this device. The latest from Actual loads when you reconnect."
                            1 -> "1 change is saved on this device. It syncs when you reconnect."
                            else -> "$waitingCount changes are saved on this device. They sync when you reconnect."
                        },
                    icon = Lucide.CloudOff,
                    color = palette.warning,
                )
            syncError != null ->
                SyncStatusPresentation(
                    title = "Couldn’t finish syncing",
                    detail =
                        when (waitingCount) {
                            0 -> "Couldn’t load the latest from Actual. Your saved copy is still here."
                            1 -> "Your change is saved on this device."
                            else -> "Your $waitingCount changes are saved on this device."
                        },
                    icon = Lucide.CloudAlert,
                    color = palette.negative,
                )
            attentionCount > 0 ->
                SyncStatusPresentation(
                    title = "Needs review",
                    detail = "$attentionCount ${if (attentionCount == 1) "change needs" else "changes need"} a decision.",
                    icon = Lucide.CircleAlert,
                    color = palette.warning,
                )
            waitingCount > 0 ->
                SyncStatusPresentation(
                    title = "Ready to sync",
                    detail = "$waitingCount ${if (waitingCount == 1) "change is" else "changes are"} waiting.",
                    icon = Lucide.CloudSync,
                    color = palette.syncing,
                )
            lastSyncedAtEpochMs != null ->
                SyncStatusPresentation(
                    title = "Up to date",
                    detail = "Your budget is in sync.",
                    icon = Lucide.CloudCheck,
                    color = palette.positive,
                )
            else ->
                SyncStatusPresentation(
                    title = "Ready to sync",
                    detail = "Your budget is saved on this device.",
                    icon = Lucide.Cloud,
                    color = palette.accent,
                )
        }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TandemSpacing.large)) {
        SyncPageHeading(
            eyebrow = budgetName,
            title = "${status.title}.",
            detail = status.detail,
        )
        SyncStatusRing(
            icon = status.icon,
            color = status.color,
            active = isSyncing,
            caption =
                lastSyncedAtEpochMs?.let { "Last synced ${formatMomentAtEpochMilliseconds(it, nowEpochMilliseconds)}" }
                    ?: "Not synced yet",
        )
        if (syncError != null) {
            val chevronRotation by animateFloatAsState(
                if (showErrorDetails) 90f else 0f,
                tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTROL_MILLIS),
                label = "sync-error-chevron",
            )
            SurfaceCard {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(
                            role = Role.Button,
                            onClickLabel = if (showErrorDetails) "Hide error details" else "Show error details",
                        ) { showErrorDetails = !showErrorDetails }
                        .animateContentSize(tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTENT_SIZE_MILLIS))
                        .padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                ) {
                    Icon(Lucide.CircleAlert, contentDescription = null, modifier = Modifier.size(22.dp), tint = status.color)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall)) {
                        Text("What went wrong", color = palette.text, fontSize = TandemTypography.body, fontWeight = FontWeight.SemiBold)
                        Text(
                            syncError,
                            color = palette.muted,
                            fontSize = TandemTypography.caption,
                            lineHeight = TandemTypography.compactLineHeight,
                            maxLines = if (showErrorDetails) Int.MAX_VALUE else 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        Lucide.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = chevronRotation },
                        tint = palette.muted,
                    )
                }
            }
        }
        SurfaceCard {
            SyncSummaryRow(
                icon = Lucide.RefreshCw,
                color = palette.syncing,
                title = "Pending changes",
                detail =
                    when (waitingCount) {
                        0 -> "No changes waiting to upload"
                        1 -> "1 change saved on this device"
                        else -> "$waitingCount changes saved on this device"
                    },
                count = waitingCount,
                onClick = onPendingClick,
            )
            DividerLine()
            SyncSummaryRow(
                icon = Lucide.TriangleAlert,
                color = palette.warning,
                title = "Needs review",
                detail =
                    when (attentionCount) {
                        0 -> "Nothing needs a decision"
                        1 -> "1 change needs a decision"
                        else -> "$attentionCount changes need a decision"
                    },
                count = attentionCount,
                onClick = onReviewClick.takeIf { attentionCount > 0 },
            )
        }
        val recovers = syncError != null && !isSyncing && syncRecovery != SyncRecovery.RETRY
        PrimaryButton(
            when {
                isSyncing -> "Syncing…"
                recovers -> syncRecovery.label
                !isServerReachable -> "Try connecting again"
                syncError != null -> "Try again"
                waitingCount > 0 -> "Sync $waitingCount ${if (waitingCount == 1) "change" else "changes"}"
                else -> "Sync now"
            },
            enabled = recovers || onSyncNow != null,
            loading = isSyncing,
            onClick = { if (recovers) onRecover() else onSyncNow?.invoke() },
        )
    }
}

@Composable
internal fun SyncPageHeading(
    eyebrow: String,
    title: String,
    detail: String?,
) {
    val palette = tandemPalette
    Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
        Text(
            eyebrow.uppercase(),
            color = palette.muted,
            fontSize = TandemTypography.caption,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = TandemTypography.captionLetterSpacing,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        DisplayHeadline(title)
        detail?.let {
            Text(
                it,
                color = palette.muted,
                fontSize = TandemTypography.body,
                lineHeight = TandemTypography.bodyLineHeight,
            )
        }
    }
}

@Composable
internal fun SyncStatusRing(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
    active: Boolean,
    caption: String?,
) {
    val palette = tandemPalette
    val ringColor by animateColorAsState(color, tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS), label = "sync-ring")
    val breath by
        rememberInfiniteTransition(label = "sync-ring-breath").animateFloat(
            initialValue = 1f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(tween(900, easing = TandemMotion.movementEasing), RepeatMode.Reverse),
            label = "sync-ring-phase",
        )
    val emphasis by animateFloatAsState(
        if (active && !tandemReduceMotion) breath else 1f,
        spring(stiffness = Spring.StiffnessMediumLow),
        label = "sync-ring-emphasis",
    )
    Column(
        Modifier.fillMaxWidth().padding(vertical = TandemSpacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
    ) {
        Box(
            Modifier
                .size(104.dp)
                .graphicsLayer {
                    scaleX = 0.94f + 0.06f * emphasis
                    scaleY = 0.94f + 0.06f * emphasis
                }.clip(CircleShape)
                .background(ringColor.copy(alpha = 0.08f + 0.06f * (1f - emphasis)))
                .border(2.dp, ringColor.copy(alpha = 0.35f + 0.4f * (1f - emphasis)), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp), tint = ringColor)
        }
        caption?.let { Text(it, color = palette.muted, fontSize = TandemTypography.compactBody) }
    }
}

@Composable
private fun SyncSummaryRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
    title: String,
    detail: String,
    count: Int,
    onClick: (() -> Unit)?,
) {
    val palette = tandemPalette
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = if (count > 0) color else palette.muted)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall)) {
            Text(title, color = palette.text, fontSize = TandemTypography.body, fontWeight = FontWeight.SemiBold)
            Text(detail, color = palette.muted, fontSize = TandemTypography.caption)
        }
        Text(
            count.toString(),
            color =
                if (count >
                    0
                ) {
                    color
                } else {
                    palette.muted
                },
            fontSize = TandemTypography.body,
            fontWeight = FontWeight.SemiBold,
        )
        if (onClick != null) {
            Icon(Lucide.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp), tint = palette.muted)
        }
    }
}

private data class SyncStatusPresentation(
    val title: String,
    val detail: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: androidx.compose.ui.graphics.Color,
)

@Composable
internal fun SyncCountTile(
    label: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(TandemShapes.medium)
    Row(
        modifier
            .clip(shape)
            .background(color.copy(alpha = 0.09f))
            .border(1.dp, color.copy(alpha = 0.28f), shape)
            .clickable(role = Role.Button, onClickLabel = "Open $label", onClick = onClick)
            .padding(horizontal = TandemSpacing.small, vertical = TandemSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = color)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall)) {
            Text(count.toString(), color = tandemPalette.text, fontSize = TandemTypography.cardTitle, fontWeight = FontWeight.Bold)
            Text(label, color = tandemPalette.muted, fontSize = TandemTypography.micro, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Lucide.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp), tint = color)
    }
}

@Composable
private fun ChangeSectionHeading(
    title: String,
    detail: String,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = TandemSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            color = tandemPalette.text,
            fontSize = TandemTypography.sectionTitle,
            fontFamily = tandemAlloFamily(),
            fontWeight = FontWeight.Bold,
        )
        Text(detail, color = tandemPalette.muted, fontSize = TandemTypography.label)
    }
}

@Composable
private fun SyncChangeSection(
    title: String,
    detail: String,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
    ) {
        ChangeSectionHeading(title, detail)
        SurfaceCard {
            Column(Modifier.fillMaxWidth()) { content() }
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

internal fun budgetMutationItemName(
    mutation: QueuedBudgetReorder?,
    operation: OutboxOperationEntity?,
    mobileSession: TandemMobileSession,
    liveBudgetMonth: LiveBudgetMonthSummary? = null,
): String {
    val itemId = mutation?.itemId ?: operation?.recordId
    val records =
        mobileSession.categoryRecords + mobileSession.categoryGroupRecords +
            mobileSession.accountRecords + mobileSession.payeeRecords
    val storedName = itemId?.let { id -> records.firstOrNull { it.recordId == id }?.recordName() }
    val monthName =
        itemId?.let { id ->
            liveBudgetMonth?.groups?.firstNotNullOfOrNull { group ->
                group.categories.firstOrNull { it.id == id }?.name
            }
        }
    return storedName ?: monthName ?: mutation?.name ?: "Budget change"
}

internal fun budgetMutationChangeTitle(
    mutation: QueuedBudgetReorder,
    itemName: String,
    state: AppState,
    mobileSession: TandemMobileSession,
): String {
    val payload = mutationPayload(mutation)
    val month = budgetMutationMonthLabel(payload?.stringValue("month"))
    val categoryName: (String?) -> String = { id ->
        id?.let { key -> mobileSession.categoryRecords.firstOrNull { it.recordId == key }?.recordName() } ?: "category"
    }
    val groupName: (String?) -> String = { id ->
        id?.let { key -> mobileSession.categoryGroupRecords.firstOrNull { it.recordId == key }?.recordName() } ?: "group"
    }
    val target = payload?.longValue("amountMinor")?.let(state::formatCurrency)
    return when (mutation.kind) {
        BudgetReorderKind.SET_CATEGORY_AMOUNT -> "Assign ${target ?: "money"} to $itemName${month?.let { " · $it" }.orEmpty()}"
        BudgetReorderKind.SET_CATEGORY_CARRYOVER ->
            if (payload?.booleanValue("enabled") == true) {
                "Carry unused money over · $itemName${month?.let { " · $it" }.orEmpty()}"
            } else {
                "Stop carrying unused money over · $itemName${month?.let { " · $it" }.orEmpty()}"
            }
        BudgetReorderKind.MOVE_BUDGET_MONEY ->
            "Move ${target ?: "money"} from ${categoryName(payload?.stringValue("fromCategoryId"))} to " +
                "${categoryName(payload?.stringValue("toCategoryId"))}${month?.let { " · $it" }.orEmpty()}"
        BudgetReorderKind.COPY_BUDGET_MONTH -> "Copy last month’s assignments${month?.let { " into $it" }.orEmpty()}"
        BudgetReorderKind.APPLY_BUDGET_TEMPLATE -> {
            val count = (payload?.get("assignments") as? kotlinx.serialization.json.JsonArray)?.size
            "Apply budget template${count?.let { " to $it ${if (it == 1) "category" else "categories"}" }.orEmpty()}" +
                month?.let { " · $it" }.orEmpty()
        }
        BudgetReorderKind.HOLD_BUDGET_NEXT_MONTH -> "Hold ${target ?: "money"} for next month${month?.let { " · from $it" }.orEmpty()}"
        BudgetReorderKind.RESET_BUDGET_HOLD -> "Release money held for next month${month?.let { " · $it" }.orEmpty()}"
        BudgetReorderKind.RENAME_CATEGORY, BudgetReorderKind.REFRESH_CATEGORY_RENAME ->
            "Rename $itemName to ${payload?.stringValue("name") ?: mutation.name ?: "a new name"}"
        BudgetReorderKind.RENAME_CATEGORY_GROUP, BudgetReorderKind.REFRESH_CATEGORY_GROUP_RENAME ->
            "Rename $itemName to ${payload?.stringValue("name") ?: mutation.name ?: "a new name"}"
        BudgetReorderKind.CREATE_CATEGORY -> "Add category $itemName${mutation.parentGroupId?.let { " to ${groupName(it)}" }.orEmpty()}"
        BudgetReorderKind.CREATE_CATEGORY_GROUP -> "Add category group $itemName"
        BudgetReorderKind.HIDE_CATEGORY, BudgetReorderKind.HIDE_CATEGORY_GROUP -> "Hide $itemName from the budget"
        BudgetReorderKind.SHOW_CATEGORY, BudgetReorderKind.SHOW_CATEGORY_GROUP -> "Show $itemName in the budget"
        BudgetReorderKind.MOVE_CATEGORY_TO_GROUP -> "Move $itemName to ${groupName(mutation.targetId ?: mutation.parentGroupId)}"
        BudgetReorderKind.DELETE_CATEGORY -> "Delete category $itemName"
        BudgetReorderKind.DELETE_CATEGORY_GROUP -> "Delete category group $itemName"
        BudgetReorderKind.CREATE_PAYEE -> "Add payee $itemName"
        BudgetReorderKind.CREATE_ACCOUNT -> "Add account $itemName"
        BudgetReorderKind.CLOSE_ACCOUNT -> "Close account $itemName"
        BudgetReorderKind.REOPEN_ACCOUNT -> "Reopen account $itemName"
        BudgetReorderKind.RENAME_ACCOUNT -> "Rename account to ${payload?.stringValue("name") ?: mutation.name ?: itemName}"
        BudgetReorderKind.MOVE_ACCOUNT -> "Change the order of $itemName"
        BudgetReorderKind.RENAME_PAYEE -> "Rename payee to ${payload?.stringValue("name") ?: mutation.name ?: itemName}"
        BudgetReorderKind.MERGE_PAYEE -> "Merge $itemName into ${payload?.stringValue("targetPayeeId") ?: "another payee"}"
        BudgetReorderKind.DELETE_PAYEE -> "Delete payee $itemName"
        BudgetReorderKind.SET_PAYEE_DEFAULT_CATEGORY -> "Set $itemName’s default category"
        BudgetReorderKind.IMPORT_STATEMENT -> "Import ${itemName.ifBlank { "transactions" }}"
        BudgetReorderKind.CATEGORY, BudgetReorderKind.CATEGORY_GROUP -> "Change the order of $itemName"
        BudgetReorderKind.SET_ORDER -> {
            val lists = mutation.orderBatchScopes().size
            "Save the new order of ${if (lists == 1) "1 list" else "$lists lists"}"
        }
        BudgetReorderKind.REFRESH_CATEGORY_CREATE, BudgetReorderKind.REFRESH_CATEGORY_GROUP_CREATE -> "Finish adding $itemName"
    }
}

internal fun pendingBudgetMutationTitle(itemName: String): String = itemName

internal fun budgetMutationPendingSummary(
    mutation: QueuedBudgetReorder,
    state: AppState,
    mobileSession: TandemMobileSession,
): String {
    val payload = mutationPayload(mutation)
    val categoryName: (String?) -> String = { id ->
        id?.let { key -> mobileSession.categoryRecords.firstOrNull { it.recordId == key }?.recordName() } ?: "category"
    }
    return when (mutation.kind) {
        BudgetReorderKind.MOVE_BUDGET_MONEY -> {
            val amount = payload?.longValue("amountMinor")?.let(state::formatCurrency) ?: "Money"
            val from = categoryName(payload?.stringValue("fromCategoryId"))
            val to = categoryName(payload?.stringValue("toCategoryId"))
            "$amount · $from → $to"
        }
        BudgetReorderKind.SET_CATEGORY_AMOUNT -> {
            val target = payload?.longValue("amountMinor")
            val before =
                payload?.stringValue("month")?.let { monthId ->
                    budgetCategoryAssignedMinorFromLastSync(mobileSession, mutation.itemId, monthId)
                }
            val change =
                if (before != null && target != null) {
                    "${state.formatCurrency(before)} → ${state.formatCurrency(target)}"
                } else {
                    "→ ${target?.let(state::formatCurrency) ?: "money"}"
                }
            change
        }
        BudgetReorderKind.SET_CATEGORY_CARRYOVER -> {
            val before =
                payload?.stringValue("month")?.let { monthId ->
                    budgetCategoryCarryoverFromLastSync(mobileSession, mutation.itemId, monthId)
                }
            val after = payload?.booleanValue("enabled")
            if (before != null && after != null) {
                "${if (before) "On" else "Off"} → ${if (after) "On" else "Off"}"
            } else {
                if (after == true) "Carryover on" else "Carryover off"
            }
        }
        BudgetReorderKind.CATEGORY,
        BudgetReorderKind.CATEGORY_GROUP,
        BudgetReorderKind.MOVE_ACCOUNT,
        BudgetReorderKind.MOVE_CATEGORY_TO_GROUP,
        BudgetReorderKind.SET_ORDER,
        -> "Reorder"
        BudgetReorderKind.RENAME_CATEGORY,
        BudgetReorderKind.RENAME_CATEGORY_GROUP,
        BudgetReorderKind.RENAME_ACCOUNT,
        BudgetReorderKind.RENAME_PAYEE,
        BudgetReorderKind.REFRESH_CATEGORY_RENAME,
        BudgetReorderKind.REFRESH_CATEGORY_GROUP_RENAME,
        -> "Rename → ${payload?.stringValue("name") ?: mutation.name ?: "new name"}"
        BudgetReorderKind.CREATE_CATEGORY, BudgetReorderKind.REFRESH_CATEGORY_CREATE -> "Add category"
        BudgetReorderKind.CREATE_CATEGORY_GROUP, BudgetReorderKind.REFRESH_CATEGORY_GROUP_CREATE -> "Add group"
        BudgetReorderKind.CREATE_ACCOUNT -> "Add account"
        BudgetReorderKind.CREATE_PAYEE -> "Add payee"
        BudgetReorderKind.HIDE_CATEGORY, BudgetReorderKind.HIDE_CATEGORY_GROUP -> "Hide"
        BudgetReorderKind.SHOW_CATEGORY, BudgetReorderKind.SHOW_CATEGORY_GROUP -> "Show"
        BudgetReorderKind.DELETE_CATEGORY, BudgetReorderKind.DELETE_CATEGORY_GROUP,
        BudgetReorderKind.DELETE_PAYEE,
        -> "Delete"
        BudgetReorderKind.COPY_BUDGET_MONTH -> "Copy last month"
        BudgetReorderKind.APPLY_BUDGET_TEMPLATE -> "Apply template"
        BudgetReorderKind.HOLD_BUDGET_NEXT_MONTH -> "Hold ${payload?.longValue("amountMinor")?.let(state::formatCurrency) ?: "money"}"
        BudgetReorderKind.RESET_BUDGET_HOLD -> "Release hold"
        BudgetReorderKind.CLOSE_ACCOUNT -> "Close account"
        BudgetReorderKind.REOPEN_ACCOUNT -> "Reopen account"
        BudgetReorderKind.MERGE_PAYEE -> "Merge payee"
        BudgetReorderKind.SET_PAYEE_DEFAULT_CATEGORY -> "Set default category"
        BudgetReorderKind.IMPORT_STATEMENT -> "Import transactions"
    }
}

internal data class BudgetAmountChange(
    val label: String,
    val before: String,
    val after: String,
)

internal fun budgetMoneyMoveTransitions(
    fromName: String,
    fromBefore: Long,
    toName: String,
    toBefore: Long,
    amount: Long,
    formatAmount: (Long) -> String,
): List<BudgetAmountChange> {
    if (amount <= 0L) return emptyList()
    return listOf(
        BudgetAmountChange(fromName, formatAmount(fromBefore), formatAmount(fromBefore - amount)),
        BudgetAmountChange(toName, formatAmount(toBefore), formatAmount(toBefore + amount)),
    )
}

internal fun budgetMutationAmountChanges(
    mutation: QueuedBudgetReorder?,
    state: AppState,
    mobileSession: TandemMobileSession,
): List<BudgetAmountChange> {
    mutation ?: return emptyList()
    val payload = mutationPayload(mutation) ?: return emptyList()
    val monthId = payload.stringValue("month") ?: return emptyList()
    val formatAmount: (Long) -> String = state::formatCurrency
    return when (mutation.kind) {
        BudgetReorderKind.SET_CATEGORY_AMOUNT -> {
            val before = budgetCategoryAssignedMinorFromLastSync(mobileSession, mutation.itemId, monthId) ?: return emptyList()
            val after = payload.longValue("amountMinor") ?: return emptyList()
            listOf(
                BudgetAmountChange(
                    mobileSession.categoryRecords.firstOrNull { it.recordId == mutation.itemId }?.recordName() ?: "Assigned",
                    formatAmount(before),
                    formatAmount(after),
                ),
            )
        }
        BudgetReorderKind.MOVE_BUDGET_MONEY -> {
            val fromId = payload.stringValue("fromCategoryId") ?: return emptyList()
            val toId = payload.stringValue("toCategoryId") ?: return emptyList()
            val amount = payload.longValue("amountMinor") ?: return emptyList()
            val fromBefore = budgetCategoryAssignedMinorFromLastSync(mobileSession, fromId, monthId) ?: return emptyList()
            val toBefore = budgetCategoryAssignedMinorFromLastSync(mobileSession, toId, monthId) ?: return emptyList()
            budgetMoneyMoveTransitions(
                mobileSession.categoryRecords.firstOrNull { it.recordId == fromId }?.recordName() ?: "From category",
                fromBefore,
                mobileSession.categoryRecords.firstOrNull { it.recordId == toId }?.recordName() ?: "To category",
                toBefore,
                amount,
                formatAmount,
            )
        }
        else -> emptyList()
    }
}

internal fun budgetCategoryAssignedMinorFromLastSync(
    mobileSession: TandemMobileSession,
    categoryId: String,
    monthId: String,
): Long? {
    val record =
        mobileSession.selectedBudgetMonthRecord?.takeIf {
            it.recordId == monthId
        } ?: return null
    val month =
        record.remoteJson?.let { raw ->
            runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
        } ?: return null
    val groups = month["categoryGroups"] as? JsonArray ?: return null
    val category =
        groups.firstNotNullOfOrNull { groupElement ->
            (groupElement as? JsonObject)
                ?.get("categories")
                ?.let { it as? JsonArray }
                ?.firstNotNullOfOrNull { categoryElement ->
                    (categoryElement as? JsonObject)?.takeIf { it.stringValue("id") == categoryId }
                }
        } ?: return null
    return category.longValue("budgeted") ?: category.longValue("assigned")
}

internal fun budgetCategoryCarryoverFromLastSync(
    mobileSession: TandemMobileSession,
    categoryId: String,
    monthId: String,
): Boolean? {
    val record =
        mobileSession.selectedBudgetMonthRecord?.takeIf {
            it.recordId == monthId
        } ?: return null
    val month =
        record.remoteJson?.let { raw ->
            runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
        } ?: return null
    val groups = month["categoryGroups"] as? JsonArray ?: return null
    val category =
        groups.firstNotNullOfOrNull { groupElement ->
            (groupElement as? JsonObject)
                ?.get("categories")
                ?.let { it as? JsonArray }
                ?.firstNotNullOfOrNull { categoryElement ->
                    (categoryElement as? JsonObject)?.takeIf { it.stringValue("id") == categoryId }
                }
        } ?: return null
    return category.booleanValue("carryover") ?: category.booleanValue("rollover")
}

internal fun budgetMutationTitle(
    action: String,
    itemName: String,
): String =
    when (action) {
        "CREATE_CATEGORY_GROUP" -> "Add category group · $itemName"
        "RENAME_CATEGORY_GROUP", "REFRESH_CATEGORY_GROUP_RENAME" -> "Rename category group · $itemName"
        "CREATE_CATEGORY", "REFRESH_CATEGORY_CREATE" -> "Add category · $itemName"
        "CREATE_PAYEE" -> "Add payee · $itemName"
        "CREATE_ACCOUNT" -> "Add account · $itemName"
        "CLOSE_ACCOUNT" -> "Close account · $itemName"
        "RENAME_ACCOUNT" -> "Rename account · $itemName"
        "REOPEN_ACCOUNT" -> "Reopen account · $itemName"
        "MOVE_ACCOUNT" -> "Reorder account · $itemName"
        "SET_ORDER" -> "Reorder · categories and accounts"
        "SET_CATEGORY_AMOUNT" -> "Assign money · $itemName"
        "SET_CATEGORY_CARRYOVER" -> "Update carryover · $itemName"
        "COPY_BUDGET_MONTH" -> "Copy assignments · $itemName"
        "APPLY_BUDGET_TEMPLATE" -> "Apply template · $itemName"
        "HOLD_BUDGET_NEXT_MONTH" -> "Hold money · $itemName"
        "RESET_BUDGET_HOLD" -> "Reset hold · $itemName"
        "MOVE_BUDGET_MONEY" -> "Move money · $itemName"
        "RENAME_PAYEE" -> "Rename payee · $itemName"
        "MERGE_PAYEE" -> "Merge payee · $itemName"
        "DELETE_PAYEE" -> "Delete payee · $itemName"
        "SET_PAYEE_DEFAULT_CATEGORY" -> "Update payee · $itemName"
        "IMPORT_STATEMENT" -> "Import transactions · $itemName"
        "RENAME_CATEGORY", "REFRESH_CATEGORY_RENAME" -> "Rename category · $itemName"
        "HIDE_CATEGORY", "HIDE_CATEGORY_GROUP" -> "Hide · $itemName"
        "SHOW_CATEGORY", "SHOW_CATEGORY_GROUP" -> "Show · $itemName"
        "MOVE_CATEGORY_TO_GROUP" -> "Move · $itemName"
        "DELETE_CATEGORY" -> "Delete category · $itemName"
        "DELETE_CATEGORY_GROUP" -> "Delete category group · $itemName"
        "CATEGORY", "CATEGORY_GROUP" -> "Reorder · $itemName"
        else -> itemName
    }

internal fun budgetMutationMonthLabel(monthId: String?): String? {
    val parts = monthId?.split('-') ?: return null
    val year = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val month = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..12 } ?: return null
    return formatMonth(year, month)
}

internal fun budgetMutationLastSyncedAssignment(
    mutation: QueuedBudgetReorder?,
    operation: OutboxOperationEntity?,
    state: AppState,
    mobileSession: TandemMobileSession,
): String? {
    if (
        mutation?.kind != BudgetReorderKind.SET_CATEGORY_AMOUNT ||
        operation?.status != "rejected" ||
        operation.lastErrorCode != "invalid_request"
    ) {
        return null
    }
    val monthId = mutationPayload(mutation)?.stringValue("month") ?: return null
    val monthRecord =
        mobileSession.selectedBudgetMonthRecord?.takeIf {
            it.budgetId == operation.budgetId && it.recordId == monthId
        } ?: return null
    val month =
        monthRecord.remoteJson?.let { raw ->
            runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
        } ?: return null
    val groups = month["categoryGroups"] as? kotlinx.serialization.json.JsonArray ?: return null
    val category =
        groups.firstNotNullOfOrNull { groupElement ->
            val categories = (groupElement as? JsonObject)?.get("categories") as? kotlinx.serialization.json.JsonArray
            categories?.firstNotNullOfOrNull { categoryElement ->
                (categoryElement as? JsonObject)?.takeIf { it.stringValue("id") == mutation.itemId }
            }
        } ?: return null
    val amount = category.longValue("budgeted") ?: category.longValue("assigned") ?: 0L
    val period = budgetMutationMonthLabel(monthId)
    return "Last synced in Actual${period?.let { " · $it" }.orEmpty()}: ${state.formatCurrency(amount)} assigned."
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

internal fun transactionIdentity(
    operation: OutboxOperationEntity,
    transaction: TransactionItem?,
    state: AppState,
    mobileSession: TandemMobileSession,
): String? {
    val version =
        (operation.optimisticJson ?: operation.baseSnapshotJson)
            ?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
    val date = transaction?.dateLabel ?: version?.stringValue("date")?.let(::formatLiveDate)
    val amount = (transaction?.amountMinorUnits ?: version?.longValue("amount"))?.let(state::formatSignedCurrency)
    val account =
        version?.stringValue("account")?.let { id ->
            mobileSession.accountRecords.firstOrNull { it.recordId == id }?.recordName()
        }
    return listOfNotNull(date, amount, account).joinToString(" · ").ifBlank { null }
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

private fun mutationPayload(mutation: QueuedBudgetReorder): JsonObject? =
    mutation.payloadJson?.let { raw -> runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull() }

@Composable
internal fun ReviewOutcomeCard(
    title: String,
    explanation: String,
    isRejected: Boolean,
) {
    val unknown = !isRejected && (title.startsWith("Status") || title.startsWith("Couldn"))
    Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
        SyncPageHeading(
            eyebrow =
                when {
                    isRejected -> "Action required"
                    unknown -> "Verify sync status"
                    else -> "Sync"
                },
            title = "${if (title == "Rejected") "Change rejected" else title}.",
            detail = explanation,
        )
        if (unknown) {
            SyncStatusRing(icon = Lucide.CloudAlert, color = tandemPalette.syncing, active = false, caption = null)
        }
    }
}

@Composable
internal fun BudgetMutationChangeDetails(
    title: String,
    detail: String?,
    amountChanges: List<BudgetAmountChange> = emptyList(),
) {
    SurfaceCard {
        Column(
            Modifier.fillMaxWidth().padding(TandemSpacing.large),
            verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
        ) {
            if (amountChanges.isNotEmpty()) {
                amountChanges.forEach { change ->
                    AmountTransitionLine(change)
                }
            } else {
                Text(title, color = tandemPalette.text, fontSize = TandemTypography.cardTitle, fontWeight = FontWeight.SemiBold)
                detail?.let { Text(it, color = tandemPalette.muted, fontSize = TandemTypography.body) }
            }
        }
    }
}

@Composable
internal fun BudgetMutationReasonCard(
    title: String,
    explanation: String,
) {
    SurfaceCard {
        Column(
            Modifier.fillMaxWidth().padding(TandemSpacing.large),
            verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
        ) {
            LabelText(title)
            Text(explanation, color = tandemPalette.text, fontSize = TandemTypography.body, lineHeight = TandemTypography.bodyLineHeight)
        }
    }
}

internal fun budgetMutationReason(
    operation: OutboxOperationEntity,
    mutation: QueuedBudgetReorder?,
    itemName: String,
): String =
    when {
        operation.status == "rejected" && isRejectedBudgetOrderRequest(operation.lastErrorCode, mutation?.kind) ->
            budgetMutationFailureMessage(operation.lastErrorCode, mutation?.kind, itemName)
        operation.status == "rejected" &&
            operation.lastErrorCode == "invalid_request" &&
            mutation?.kind == BudgetReorderKind.SET_CATEGORY_AMOUNT ->
            "Tandem searched hidden categories only, so it could not find $itemName. It stopped the request before sending it to Actual; your assignment did not change."
        operation.status == "rejected" -> budgetMutationFailureMessage(operation.lastErrorCode, mutation?.kind, itemName)
        operation.status == "uncertain" ->
            "Tandem sent this change but did not receive confirmation. It may already be saved in Actual, so check your budget before trying again."
        operation.status == "conflict" ->
            "The budget changed while this request was being processed. Refresh the budget and review the latest values before choosing what to do."
        operation.status == "queued" -> "This change is saved on this device and is waiting to be sent to Actual."
        operation.status == "sending" -> "Tandem is sending this change to Actual."
        else -> "Tandem could not confirm the result of this change. Check Actual before trying again."
    }
