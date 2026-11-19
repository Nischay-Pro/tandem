@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.ui.components.EmptyState
import app.tandemforactual.ui.components.LabelText
import app.tandemforactual.ui.components.PrimaryButton
import app.tandemforactual.ui.components.SurfaceCard
import app.tandemforactual.ui.components.TandemActionButton
import app.tandemforactual.ui.components.TandemActionButtonTone
import app.tandemforactual.ui.model.LiveBudgetMonthSummary
import app.tandemforactual.ui.model.LiveScheduleSummary
import app.tandemforactual.ui.model.booleanValue
import app.tandemforactual.ui.model.longValue
import app.tandemforactual.ui.model.recordName
import app.tandemforactual.ui.model.stringValue
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Cloud
import com.composables.icons.lucide.CloudAlert
import com.composables.icons.lucide.CloudCheck
import com.composables.icons.lucide.CloudOff
import com.composables.icons.lucide.CloudSync
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

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
                onAction = { navigation.open(AppRoute.BUDGET_CHOOSER) },
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
    val budgetMutations = mobileSession.pendingBudgetMutationsFor(state.selectedBudgetId)
    val budgetMutationReviews = mobileSession.budgetMutationReviewsFor(state.selectedBudgetId)
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
    val attentionItemCount =
        transactionsNeedingReview.size + schedulesNeedingReview.size + budgetMutationReviews.size
    val listState = LocalRouteLazyListState.current
    val scope = rememberCoroutineScope()
    val waitingSectionIndex = if (attentionItemCount > 0) 2 + attentionItemCount else 1

    fun scrollToSection(index: Int) {
        scope.launch { listState?.animateScrollToItem(index) }
    }

    PageLazyColumn {
        item(key = "sync-center-overview") {
            SyncOverviewCard(
                budgetName = budgetDisplayNameForHeader(state.selectedBudget),
                attentionCount = attentionCount,
                waitingCount = waitingCount,
                isSyncing = mobileSession.isSyncing,
                isServerReachable = mobileSession.isServerReachable,
                syncError = mobileSession.syncError,
                lastSyncedAtEpochMs = mobileSession.localSyncState?.lastSyncedAtEpochMs,
                onSyncNow = onSyncNow.takeIf { !mobileSession.isSyncing },
                onPendingClick = { scrollToSection(waitingSectionIndex) },
                onReviewClick = {
                    if (!openNeedsReview(
                            state,
                            navigation,
                            transactionsNeedingReview.firstOrNull(),
                            schedulesNeedingReview.firstOrNull(),
                            budgetMutationReviews.firstOrNull(),
                        )
                    ) {
                        scrollToSection(1)
                    }
                },
            )
        }

        if (attentionCount > 0) {
            item(key = "sync-center-review-heading") {
                ChangeSectionHeading(
                    title = "Needs your attention",
                    detail = "$attentionCount ${if (attentionCount == 1) "change" else "changes"}",
                )
            }
            items(transactionsNeedingReview, key = { "transaction-review:${it.operationId}" }) { operation ->
                val transaction = liveTransactions.firstOrNull { it.recordId == operation.recordId }
                val status = writeStatusLabel(operation.status)
                ChangeSummaryCard(
                    title = transaction?.payee ?: "Transaction",
                    summary = transactionChangeSummary(operation, transaction, state, mobileSession),
                    status = status,
                    explanation = transactionStatusExplanation(operation.status),
                    needsAttention = true,
                    actionLabel = "View details",
                    onClick = {
                        mobileSession.clearTransactionWriteMessage()
                        state.selectedConflictOperationId = operation.operationId
                        navigation.open(
                            if (operation.status == "conflict") AppRoute.CONFLICT_REVIEW else AppRoute.UNCERTAIN_WRITE,
                        )
                    },
                )
            }
            items(schedulesNeedingReview, key = { "schedule-review:${it.operationId}" }) { operation ->
                val schedule = liveSchedules.firstOrNull { it.id == operation.recordId }
                val actionLabel = mobileSession.scheduleActionStatusActionLabel(operation)
                ChangeSummaryCard(
                    title = scheduleActionTitle(operation.action, schedule?.name),
                    summary = scheduleActionSummary(operation.action, schedule, state),
                    status = writeStatusLabel(operation.status),
                    explanation = scheduleStatusExplanation(operation.status),
                    needsAttention = true,
                    actionLabel = actionLabel,
                    onClick = if (actionLabel != null) ({ mobileSession.performScheduleActionStatusAction(operation) }) else null,
                )
            }
            items(budgetMutationReviews, key = { "budget-review:${it.operationId}" }) { operation ->
                val mutation = decodeBudgetReorderQueue(operation.patchJson).moves.singleOrNull()
                val itemName = budgetMutationItemName(mutation, operation, mobileSession, liveBudgetMonth)
                ChangeSummaryCard(
                    title = itemName,
                    summary =
                        mutation?.let { budgetMutationChangeTitle(it, itemName, state, mobileSession) }
                            ?: budgetMutationTitle(operation.action, itemName),
                    status = budgetMutationStatusLabel(operation.status),
                    explanation = budgetMutationStatusSummary(operation, mutation),
                    needsAttention = true,
                    actionLabel = "View details",
                    onClick = {
                        state.selectedConflictOperationId = operation.operationId
                        navigation.open(AppRoute.UNCERTAIN_WRITE)
                    },
                )
            }
        }

        if (waitingCount > 0) {
            item(key = "sync-center-waiting-heading") {
                ChangeSectionHeading(
                    title = "Waiting to sync",
                    detail = "$waitingCount ${if (waitingCount == 1) "change" else "changes"}",
                )
            }
            items(transactionsWaiting, key = { "transaction-waiting:${it.operationId}" }) { operation ->
                val transaction = liveTransactions.firstOrNull { it.recordId == operation.recordId }
                ChangeSummaryCard(
                    title = transaction?.payee ?: "Transaction",
                    summary = transactionChangeSummary(operation, transaction, state, mobileSession),
                    status = if (operation.status == "sending") "Syncing" else "On this device",
                    explanation = "This edit is saved on this device. Actual has not confirmed it yet.",
                    needsAttention = false,
                )
            }
            items(schedulesWaiting, key = { "schedule-waiting:${it.operationId}" }) { operation ->
                val schedule = liveSchedules.firstOrNull { it.id == operation.recordId }
                ChangeSummaryCard(
                    title = scheduleActionTitle(operation.action, schedule?.name),
                    summary = scheduleActionSummary(operation.action, schedule, state),
                    status = if (operation.status == "sending") "Syncing" else "On this device",
                    explanation = "Saved on this device. It will reach Actual when sync completes.",
                    needsAttention = false,
                )
            }
            items(budgetMutations, key = { "budget-waiting:${it.operationId}" }) { mutation ->
                val itemName = budgetMutationItemName(mutation, null, mobileSession, liveBudgetMonth)
                ChangeSummaryCard(
                    title = itemName,
                    summary = budgetMutationChangeTitle(mutation, itemName, state, mobileSession),
                    status = "On this device",
                    explanation = "Saved here. It has not been applied in Actual yet.",
                    needsAttention = false,
                )
            }
        }
        item(key = "sync-center-bottom-spacer") { Spacer(Modifier.height(72.dp)) }
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
    lastSyncedAtEpochMs: Long?,
    onSyncNow: (() -> Unit)?,
    onPendingClick: () -> Unit,
    onReviewClick: () -> Unit,
) {
    val palette = tandemPalette
    val statusTitle =
        when {
            syncError != null -> "Sync failed"
            isSyncing -> "Syncing with Actual"
            isServerReachable -> "Connected to Actual"
            else -> "You’re offline"
        }
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
    ) {
        if (syncError != null) {
            SyncFailureCard(
                error = syncError,
                isServerReachable = isServerReachable,
                isSyncing = isSyncing,
                onRetry = onSyncNow,
            )
        } else if (!isServerReachable) {
            val warningColor = palette.warning
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(TandemShapes.medium))
                    .background(warningColor.copy(alpha = 0.14f))
                    .border(1.dp, warningColor.copy(alpha = 0.55f), RoundedCornerShape(TandemShapes.medium))
                    .padding(TandemSpacing.medium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
            ) {
                Icon(
                    Lucide.CloudOff,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = warningColor,
                )
                Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall)) {
                    Text(
                        "You’re offline",
                        color = warningColor,
                        fontSize = TandemTypography.compactBody,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Changes will be saved locally and synced when you’re back online.",
                        color = palette.text,
                        fontSize = TandemTypography.caption,
                        lineHeight = TandemTypography.compactLineHeight,
                    )
                }
            }
        }

        SurfaceCard {
            Row(
                Modifier.fillMaxWidth().padding(TandemSpacing.medium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
            ) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(TandemShapes.medium))
                        .background(palette.raised),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Lucide.CloudCheck, contentDescription = null, modifier = Modifier.size(24.dp), tint = palette.accent)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall)) {
                    Text(
                        "Saved on this device",
                        color = palette.text,
                        fontSize = TandemTypography.compactBody,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("All changes are stored locally.", color = palette.muted, fontSize = TandemTypography.caption)
                }
            }
        }

        if (waitingCount > 0) {
            SyncSummaryRow(
                title = "$waitingCount Pending ${if (waitingCount == 1) "Change" else "Changes"}",
                subtitle = if (isServerReachable) "Will sync when you sync" else "Will sync when you’re back online",
                icon = Lucide.RefreshCw,
                color = palette.syncing,
                onClick = onPendingClick,
            )
        }
        if (attentionCount > 0) {
            SyncSummaryRow(
                title = "Needs review",
                subtitle = "$attentionCount ${if (attentionCount == 1) "change" else "changes"} need your attention",
                icon = Lucide.CircleAlert,
                color = palette.warning,
                onClick = onReviewClick,
            )
        }

        if (syncError == null) {
            PrimaryButton("Sync now", enabled = onSyncNow != null, onClick = { onSyncNow?.invoke() })
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.small, vertical = TandemSpacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                if (isServerReachable && syncError == null) Lucide.CloudCheck else Lucide.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isServerReachable && syncError == null) palette.positive else palette.muted,
            )
            Spacer(Modifier.size(TandemSpacing.small))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(statusTitle, color = palette.muted, fontSize = TandemTypography.caption, fontWeight = FontWeight.SemiBold)
                Text(
                    lastSyncedAtEpochMs?.let { "Last successful sync ${formatDateAtEpochMilliseconds(it)}" }
                        ?: "No successful sync yet · $budgetName",
                    color = palette.muted,
                    fontSize = TandemTypography.micro,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SyncFailureCard(
    error: String,
    isServerReachable: Boolean,
    isSyncing: Boolean,
    onRetry: (() -> Unit)?,
) {
    val palette = tandemPalette
    var showDetails by rememberSaveable(error) { mutableStateOf(false) }
    val errorReason =
        error.ifBlank {
            if (isServerReachable) "The sync request did not complete." else "Tandem could not reach the server."
        }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TandemShapes.medium))
            .background(palette.negative.copy(alpha = 0.09f))
            .border(1.dp, palette.negative.copy(alpha = 0.4f), RoundedCornerShape(TandemShapes.medium))
            .padding(TandemSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
            Icon(Lucide.CloudAlert, contentDescription = null, modifier = Modifier.size(22.dp), tint = palette.negative)
            Text("Sync failed", color = palette.negative, fontSize = TandemTypography.compactBody, fontWeight = FontWeight.SemiBold)
        }
        Text(
            errorReason,
            color = palette.text,
            fontSize = TandemTypography.caption,
            lineHeight = TandemTypography.compactLineHeight,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (isServerReachable) {
                "Check the Actual budget and try again. Your changes are still saved on this device."
            } else {
                "Check your network and the Tandem server, then try again. Your changes are still saved on this device."
            },
            color = palette.muted,
            fontSize = TandemTypography.caption,
            lineHeight = TandemTypography.compactLineHeight,
        )
        if (showDetails) {
            Text(
                "Full server response:\n$errorReason",
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(TandemShapes.small))
                    .background(palette.surface.copy(alpha = 0.78f))
                    .padding(TandemSpacing.small),
                color = palette.text,
                fontSize = TandemTypography.micro,
                lineHeight = TandemTypography.compactLineHeight,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
            TandemActionButton(
                if (isSyncing) "Syncing…" else "Try again",
                TandemActionButtonTone.PRIMARY,
                Modifier.weight(1f),
                icon = Lucide.RefreshCw,
                enabled = onRetry != null && !isSyncing,
                loading = isSyncing,
                onClick = { onRetry?.invoke() },
            )
            TandemActionButton(
                if (showDetails) "Hide details" else "View details",
                TandemActionButtonTone.NEUTRAL,
                Modifier.weight(1f),
                enabled = !isSyncing,
                onClick = { showDetails = !showDetails },
            )
        }
    }
}

@Composable
internal fun SyncSummaryRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = "Open $title", onClick = onClick)
                .padding(TandemSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(TandemShapes.medium))
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = color)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall)) {
                Text(title, color = palette.text, fontSize = TandemTypography.compactBody, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = palette.muted, fontSize = TandemTypography.caption)
            }
            Icon(Lucide.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp), tint = palette.muted)
        }
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
private fun ChangeSummaryCard(
    title: String,
    summary: String,
    status: String,
    explanation: String,
    needsAttention: Boolean,
    actionLabel: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val statusColor = if (needsAttention) tandemPalette.warning else tandemPalette.syncing
    SurfaceCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(
            Modifier.fillMaxWidth().padding(TandemSpacing.large),
            verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
        ) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(TandemShapes.medium))
                        .background(statusColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (needsAttention) Lucide.CloudAlert else Lucide.Cloud,
                        contentDescription = null,
                        modifier = Modifier.size(21.dp),
                        tint = statusColor,
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
                    Text(
                        title,
                        color = tandemPalette.text,
                        fontSize = TandemTypography.cardTitle,
                        lineHeight = TandemTypography.actionLineHeight,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        summary,
                        color = tandemPalette.accent,
                        fontSize = TandemTypography.action,
                        lineHeight = TandemTypography.bodyLineHeight,
                    )
                }
            }
            Text(
                explanation,
                color = tandemPalette.muted,
                fontSize = TandemTypography.label,
                lineHeight = TandemTypography.compactLineHeight,
            )
            if (actionLabel != null) {
                Row(
                    Modifier.fillMaxWidth().padding(top = TandemSpacing.extraSmall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        status,
                        modifier = Modifier.weight(1f),
                        color = statusColor,
                        fontSize = TandemTypography.label,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(actionLabel, color = tandemPalette.accent, fontSize = TandemTypography.label, fontWeight = FontWeight.SemiBold)
                    Icon(Lucide.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp), tint = tandemPalette.accent)
                }
            } else {
                Text(status, color = statusColor, fontSize = TandemTypography.label, fontWeight = FontWeight.SemiBold)
            }
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
        BudgetReorderKind.REFRESH_CATEGORY_CREATE, BudgetReorderKind.REFRESH_CATEGORY_GROUP_CREATE -> "Finish adding $itemName"
    }
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

internal fun budgetMutationStatusSummary(
    operation: OutboxOperationEntity,
    mutation: QueuedBudgetReorder?,
): String =
    when (operation.status) {
        "rejected" ->
            if (operation.lastErrorCode == "invalid_request" && mutation?.kind == BudgetReorderKind.SET_CATEGORY_AMOUNT) {
                "Tandem stopped this before it reached Actual. Open this change to see the exact reason."
            } else {
                "This change was rejected. Open it to see what happened and what to do next."
            }
        "uncertain" -> "Tandem could not confirm whether Actual saved this change. Check before trying again."
        "conflict" -> "The budget changed while Tandem was saving this. Review it before trying again."
        else -> "This change still needs attention."
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
    val summary = changes.take(2).joinToString(" · ")
    if (summary.isNotBlank()) return summary
    if (operation.action == "DELETE") return "Delete this transaction"
    return transaction?.let { "${it.category} · ${it.dateLabel} · ${state.formatSignedCurrency(it.amountMinorUnits)}" }
        ?: "Review the transaction edit"
}

private fun transactionStatusExplanation(status: String): String =
    when (status) {
        "rejected" -> "This edit was rejected. Open it to see the reason and next step."
        "conflict" -> "Actual changed this transaction too. Compare both versions before choosing."
        else -> "Tandem could not confirm whether Actual saved this edit. Check its status before retrying."
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

private fun scheduleStatusExplanation(status: String): String =
    when (status) {
        "rejected" -> "Actual did not accept this scheduled change. Open it to see the next step."
        "conflict" -> "The schedule changed before Tandem could finish this action. Review it first."
        "uncertain" -> "Tandem could not confirm whether this action reached Actual. Check before retrying."
        else -> "Saved on this device. It has not been confirmed in Actual yet."
    }

private fun mutationPayload(mutation: QueuedBudgetReorder): JsonObject? =
    mutation.payloadJson?.let { raw -> runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull() }

@Composable
internal fun ReviewOutcomeCard(
    title: String,
    explanation: String,
    isRejected: Boolean,
) {
    val color = if (isRejected) tandemPalette.warning else tandemPalette.syncing
    SurfaceCard {
        Row(
            Modifier.fillMaxWidth().padding(TandemSpacing.large),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(TandemShapes.medium)).background(color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (isRejected) Lucide.CloudAlert else Lucide.CloudSync, contentDescription = null, tint = color)
            }
            Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
                Text(
                    title,
                    color = tandemPalette.text,
                    fontSize = TandemTypography.sectionTitle,
                    fontFamily = tandemAlloFamily(),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    explanation,
                    color = tandemPalette.muted,
                    fontSize = TandemTypography.body,
                    lineHeight = TandemTypography.bodyLineHeight,
                )
            }
        }
    }
}

@Composable
internal fun BudgetMutationChangeDetails(
    title: String,
    detail: String?,
) {
    SurfaceCard {
        Column(
            Modifier.fillMaxWidth().padding(TandemSpacing.large),
            verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
        ) {
            LabelText("THE CHANGE")
            Text(title, color = tandemPalette.text, fontSize = TandemTypography.cardTitle, fontWeight = FontWeight.SemiBold)
            detail?.let { Text(it, color = tandemPalette.muted, fontSize = TandemTypography.body) }
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
        operation.status == "rejected" &&
            operation.lastErrorCode == "invalid_request" &&
            mutation?.kind == BudgetReorderKind.SET_CATEGORY_AMOUNT ->
            "Tandem checked only hidden categories, so it failed to find $itemName. The category is in your budget, but Tandem treated it as missing and stopped the request before sending it to Actual. Actual did not receive or save this assignment."
        operation.status == "rejected" -> budgetMutationFailureMessage(operation.lastErrorCode, mutation?.kind, itemName)
        operation.status == "uncertain" ->
            "Tandem sent this change but did not receive confirmation. It may already be saved in Actual, so check your budget before trying again."
        operation.status == "conflict" ->
            "The budget changed while this request was being processed. Refresh the budget and review the latest values before choosing what to do."
        operation.status == "queued" -> "This change is saved on this device and is waiting to be sent to Actual."
        operation.status == "sending" -> "Tandem is sending this change to Actual."
        else -> "Tandem could not confirm the result of this change. Check Actual before trying again."
    }
