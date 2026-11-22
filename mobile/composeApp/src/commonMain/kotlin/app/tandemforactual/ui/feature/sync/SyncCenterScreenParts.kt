@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sync
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import app.tandemforactual.ui.core.format.formatAmount
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.format.formatMonth
import app.tandemforactual.ui.core.format.formatSignedCurrency
import app.tandemforactual.ui.core.session.BudgetReorderKind
import app.tandemforactual.ui.core.session.QueuedBudgetReorder
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budgetMutationFailureMessage
import app.tandemforactual.ui.core.session.isRejectedBudgetOrderRequest
import app.tandemforactual.ui.core.session.orderBatchScopes
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.core.state.booleanValue
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.formatLiveDate
import app.tandemforactual.ui.core.state.formatSignedCurrency
import app.tandemforactual.ui.core.state.longValue
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.core.state.stringValue
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.LabelText
import app.tandemforactual.ui.design.templates.DisplayHeadline
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemAlloFamily
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CloudAlert
import com.composables.icons.lucide.Lucide
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
internal fun ChangeSectionHeading(
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
internal fun SyncChangeSection(
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
