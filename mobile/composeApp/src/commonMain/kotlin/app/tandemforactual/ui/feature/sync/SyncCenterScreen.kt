@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sync
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
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
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.format.formatMomentAtEpochMilliseconds
import app.tandemforactual.ui.core.format.formatSignedCurrency
import app.tandemforactual.ui.core.session.BudgetSyncOverview
import app.tandemforactual.ui.core.session.SyncRecovery
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.budgetMutationReviewsFor
import app.tandemforactual.ui.core.session.budget.deviceWriteHistory
import app.tandemforactual.ui.core.session.budget.pendingBudgetMutationsFor
import app.tandemforactual.ui.core.session.budgetMutationStatusLabel
import app.tandemforactual.ui.core.session.budgetSyncOverview
import app.tandemforactual.ui.core.session.decodeBudgetReorderQueue
import app.tandemforactual.ui.core.session.downloadBudgetAgain
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
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.templates.PageLazyColumn
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import app.tandemforactual.ui.feature.auth.LocalEncryptedBudgetVault
import app.tandemforactual.ui.feature.transactions.SyncNoticeCard
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Cloud
import com.composables.icons.lucide.CloudAlert
import com.composables.icons.lucide.CloudCheck
import com.composables.icons.lucide.CloudOff
import com.composables.icons.lucide.CloudSync
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.TriangleAlert
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

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
