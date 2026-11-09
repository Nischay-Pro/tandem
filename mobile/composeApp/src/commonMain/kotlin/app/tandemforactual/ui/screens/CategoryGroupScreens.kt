@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import app.tandemforactual.ui.components.EmptyState
import app.tandemforactual.ui.components.InlineMessage
import app.tandemforactual.ui.components.InlineMessageKind
import app.tandemforactual.ui.components.ListRow
import app.tandemforactual.ui.components.ListRowOptions
import app.tandemforactual.ui.components.ReorderDragHandle
import app.tandemforactual.ui.components.RowLeading
import app.tandemforactual.ui.components.SecondaryButton
import app.tandemforactual.ui.components.SurfaceCard
import app.tandemforactual.ui.components.TandemActionButton
import app.tandemforactual.ui.components.TandemActionButtonTone
import app.tandemforactual.ui.components.TandemIconButton
import app.tandemforactual.ui.model.LiveBudgetMonthSummary
import app.tandemforactual.ui.model.booleanValue
import app.tandemforactual.ui.model.recordBody
import app.tandemforactual.ui.model.recordName
import app.tandemforactual.ui.model.stringValue
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private data class CategoryMoveRequest(
    val categoryId: String,
    val targetId: String?,
)

internal fun reorderItemToIndex(
    itemIds: List<String>,
    itemId: String,
    destinationIndex: Int,
): List<String> {
    val currentIndex = itemIds.indexOf(itemId)
    if (currentIndex < 0 || itemIds.isEmpty()) return itemIds
    val destination = destinationIndex.coerceIn(0, itemIds.lastIndex)
    if (destination == currentIndex) return itemIds
    return itemIds.toMutableList().apply {
        removeAt(currentIndex)
        add(destination, itemId)
    }
}

internal fun itemTargetIdAfterMove(
    itemIds: List<String>,
    itemId: String,
): String? {
    val index = itemIds.indexOf(itemId)
    if (index < 0) return null
    return itemIds.getOrNull(index + 1)
}

@Composable
internal fun CategoryGroupScreen(
    state: AppState,
    navigation: AppNavigation,
    liveBudgetMonth: LiveBudgetMonthSummary?,
    mobileSession: TandemMobileSession,
) {
    val scope = rememberCoroutineScope()
    val palette = tandemPalette
    val isLiveBudget = state.selectedBudgetId.isNotBlank()
    val isTrackingBudget = isLiveBudget && state.budgetDisplaySettings?.budgetType == "tracking"
    val groups = if (isLiveBudget) liveBudgetMonth?.groups.orEmpty() else previewCategoryGroups
    val group = groups.firstOrNull { it.id == state.selectedCategoryGroupId } ?: groups.firstOrNull()
    var showHidden by rememberSaveable(state.selectedBudgetId) { mutableStateOf(false) }
    var selectedCategoryOrderId by rememberSaveable(state.selectedBudgetId, group?.id) { mutableStateOf<String?>(null) }
    var showGroupDialog by rememberSaveable(state.selectedBudgetId, group?.id) { mutableStateOf(false) }
    var showCreateCategoryDialog by rememberSaveable(state.selectedBudgetId, group?.id) { mutableStateOf(false) }
    var groupNameDraft by rememberSaveable(state.selectedBudgetId, group?.id) { mutableStateOf(group?.name.orEmpty()) }
    var newCategoryName by rememberSaveable(state.selectedBudgetId, group?.id) { mutableStateOf("") }
    var categoryOrderIds by remember(state.selectedBudgetId, group?.id) { mutableStateOf<List<String>?>(null) }
    var pendingCategoryMoves by remember(state.selectedBudgetId, group?.id) { mutableIntStateOf(0) }
    var draggedCategoryId by remember(state.selectedBudgetId, group?.id) { mutableStateOf<String?>(null) }
    var dragStartingOrder by remember(state.selectedBudgetId, group?.id) { mutableStateOf<List<String>>(emptyList()) }
    var draggedCategoryOffsetPx by remember(state.selectedBudgetId, group?.id) { mutableFloatStateOf(0f) }
    var categoryOrderMessage by rememberSaveable(state.selectedBudgetId, group?.id) { mutableStateOf<String?>(null) }
    var categoryOrderMessageKind by remember(state.selectedBudgetId, group?.id) { mutableStateOf(InlineMessageKind.ERROR) }
    val categoryRowCenters = remember(group?.id) { mutableStateMapOf<String, Float>() }
    var categoryMoveRequests by remember(state.selectedBudgetId, group?.id) { mutableStateOf<List<CategoryMoveRequest>>(emptyList()) }
    val orderedCategories =
        remember(group?.categories, categoryOrderIds) {
            val categories = group?.categories.orEmpty()
            val ids = categoryOrderIds ?: categories.map { it.id }
            val categoriesById = categories.associateBy { it.id }
            ids.mapNotNull(categoriesById::get) + categories.filterNot { it.id in ids }
        }

    fun currentCategoryOrder(): List<String> = categoryOrderIds ?: group?.categories.orEmpty().map { it.id }

    fun enqueueCategoryMove(
        itemId: String,
        nextIndex: Int,
    ) {
        val oldOrder = currentCategoryOrder()
        val nextOrder = reorderItemToIndex(oldOrder, itemId, nextIndex)
        if (oldOrder == nextOrder) return
        categoryOrderMessage = null
        categoryOrderMessageKind = InlineMessageKind.ERROR
        mobileSession.clearBudgetActionMessage()
        categoryOrderIds = nextOrder
        pendingCategoryMoves += 1
        categoryMoveRequests = categoryMoveRequests + CategoryMoveRequest(itemId, itemTargetIdAfterMove(nextOrder, itemId))
    }

    LaunchedEffect(group?.id, state.selectedBudgetId) {
        while (true) {
            snapshotFlow { categoryMoveRequests.firstOrNull() }.first { it != null }
            snapshotFlow { mobileSession.isPerformingBudgetAction }.first { !it }
            val request = categoryMoveRequests.firstOrNull() ?: continue
            val moved =
                mobileSession.manageBudgetCategory(
                    state.selectedBudgetId,
                    request.categoryId,
                    "move",
                    request.targetId,
                    state.encryptedPassword.takeIf { it.isNotBlank() },
                )
            if (moved || mobileSession.budgetActionWasAppliedButRefreshFailed) {
                val refreshFailed = mobileSession.budgetActionWasAppliedButRefreshFailed
                categoryMoveRequests = categoryMoveRequests.drop(1)
                pendingCategoryMoves = (pendingCategoryMoves - 1).coerceAtLeast(0)
                if (refreshFailed) {
                    categoryOrderMessage =
                        mobileSession.budgetActionMessage
                            ?: "Actual was updated, but this device couldn’t refresh the budget."
                    categoryOrderMessageKind = InlineMessageKind.WARNING
                } else {
                    categoryOrderMessage = null
                    if (pendingCategoryMoves == 0) categoryOrderIds = null
                }
            } else {
                categoryMoveRequests = emptyList()
                categoryOrderIds = null
                pendingCategoryMoves = 0
                categoryOrderMessage = mobileSession.budgetActionMessage ?: "Couldn’t reorder this category. The order was restored."
                categoryOrderMessageKind = InlineMessageKind.ERROR
            }
        }
    }
    LaunchedEffect(group?.categories, categoryOrderIds, pendingCategoryMoves) {
        val optimisticOrder = categoryOrderIds ?: return@LaunchedEffect
        if (pendingCategoryMoves == 0 && group?.categories?.map { it.id } == optimisticOrder) {
            categoryOrderIds = null
            categoryOrderMessage = null
        }
    }
    val hiddenGroupRecords =
        remember(mobileSession.categoryGroupRecords) {
            mobileSession.categoryGroupRecords.filter { it.recordBody()?.booleanValue("hidden") == true }
        }
    val hiddenCategoryRecords =
        remember(mobileSession.categoryRecords, group?.id) {
            mobileSession.categoryRecords.filter { record ->
                val body = record.recordBody()
                body?.booleanValue("hidden") == true &&
                    (body.stringValue("group") ?: body.stringValue("group_id")) == group?.id
            }
        }
    Box(Modifier.fillMaxSize()) {
        PageLazyColumn {
            if (!isLiveBudget) {
                item(key = "category-group-sample-banner") { SampleBanner() }
            }
            if (group == null) {
                item(key = "category-group-empty-message") {
                    EmptyState(
                        "No category group",
                        "No category group is available for this month.",
                        modifier = Modifier.padding(horizontal = TandemSpacing.large),
                    )
                }
            } else {
                item(key = "category-group-title:${group.id}") {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                    ) {
                        Box(Modifier.weight(1f)) {
                            Heading(group.name, "${state.selectedMonth} · ${categoryCountLabel(group.categories.size)}")
                        }
                        if (isLiveBudget) {
                            TandemIconButton(
                                Lucide.Pencil,
                                "Edit category group",
                                enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
                            ) {
                                groupNameDraft = group.name
                                mobileSession.clearBudgetActionMessage()
                                showGroupDialog = true
                            }
                        }
                    }
                }
                categoryOrderMessage?.let { message ->
                    item(key = "category-group-reorder-message:${group.id}") {
                        InlineMessage(
                            message,
                            categoryOrderMessageKind,
                            Modifier.padding(horizontal = TandemSpacing.large),
                        )
                    }
                }
                item(key = "category-group-summary:${group.id}") {
                    AmountHero(
                        if (isTrackingBudget) "Activity this month" else "Assigned this month",
                        state.formatCurrency(if (isTrackingBudget) group.activityMinor else group.assignedMinor),
                        if (isTrackingBudget) {
                            null
                        } else {
                            "${state.formatCurrency(group.balanceMinor)} available"
                        },
                    )
                }
                items(orderedCategories, key = { "category-group:${group.id}:${it.id}" }) { category ->
                    val isReordering = selectedCategoryOrderId == category.id
                    val isDragging = draggedCategoryId == category.id
                    val categoryIndex = orderedCategories.indexOfFirst { it.id == category.id }
                    val canMoveUp = categoryIndex > 0
                    val canMoveDown = categoryIndex >= 0 && categoryIndex < orderedCategories.lastIndex
                    val moveCategory: (Int) -> Unit = { direction ->
                        enqueueCategoryMove(category.id, categoryIndex + direction)
                    }
                    val categoryCardModifier =
                        (if (isDragging) Modifier else tandemListItemModifier(tandemReduceMotion))
                            .onGloballyPositioned { coordinates ->
                                categoryRowCenters[category.id] = coordinates.boundsInRoot().center.y
                            }.graphicsLayer {
                                translationY = if (isDragging) draggedCategoryOffsetPx else 0f
                            }.zIndex(if (isDragging) 1f else 0f)
                    SurfaceCard(modifier = categoryCardModifier) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ListRow(
                                category.name,
                                if (isReordering) {
                                    null
                                } else if (isTrackingBudget) {
                                    "Activity ${state.formatCurrency(category.activityMinor)}"
                                } else {
                                    "Spent ${state.formatCurrency(
                                        kotlin.math.abs(category.activityMinor),
                                    )} · ${state.formatCurrency(category.balanceMinor)} available"
                                },
                                if (isReordering || isTrackingBudget) null else state.formatCurrency(category.assignedMinor),
                                leading = RowLeading.Icon(state.categoryIconFor(category.id, category.name)),
                                options =
                                    ListRowOptions(
                                        trailingColor = if (category.balanceMinor >= 0) palette.positive else palette.warning,
                                        showChevron = !isReordering,
                                    ),
                                onLongClick =
                                    if (isLiveBudget && !state.readOnlyMode && !mobileSession.isPerformingBudgetAction && !isReordering) {
                                        { selectedCategoryOrderId = category.id }
                                    } else {
                                        null
                                    },
                                onClick =
                                    {
                                        if (isReordering) {
                                            selectedCategoryOrderId = null
                                        } else {
                                            state.selectedCategoryGroupId = group.id
                                            state.selectedCategoryId = category.id
                                            state.categoryEditRequested = false
                                            navigation.open(AppRoute.CATEGORY_DETAIL)
                                        }
                                    },
                                modifier = Modifier.weight(1f),
                            )
                            if (isReordering && isLiveBudget) {
                                BudgetItemReorderActions(
                                    itemName = category.name,
                                    isHidden = false,
                                    canMoveUp = canMoveUp,
                                    canMoveDown = canMoveDown,
                                    enabled =
                                        !state.readOnlyMode &&
                                            !mobileSession.isPerformingBudgetAction &&
                                            pendingCategoryMoves == 0,
                                    dragEnabled = !state.readOnlyMode,
                                    onToggleHidden = {
                                        scope.launch {
                                            if (mobileSession.manageBudgetCategory(
                                                    state.selectedBudgetId,
                                                    category.id,
                                                    "hide",
                                                    null,
                                                    state.encryptedPassword.takeIf { it.isNotBlank() },
                                                )
                                            ) {
                                                selectedCategoryOrderId = null
                                            }
                                        }
                                    },
                                    onMoveUp = { moveCategory(-1) },
                                    onMoveDown = { moveCategory(1) },
                                    onDragStarted = {
                                        categoryOrderMessage = null
                                        categoryOrderMessageKind = InlineMessageKind.ERROR
                                        mobileSession.clearBudgetActionMessage()
                                        dragStartingOrder = currentCategoryOrder()
                                        categoryOrderIds = dragStartingOrder
                                        draggedCategoryId = category.id
                                        draggedCategoryOffsetPx = 0f
                                    },
                                    onDragBy = { delta ->
                                        if (draggedCategoryId == category.id) {
                                            draggedCategoryOffsetPx += delta
                                            val centers = categoryRowCenters.toMap()
                                            var baseCenter = centers[category.id]
                                            while (baseCenter != null) {
                                                val currentOrder = currentCategoryOrder()
                                                val currentIndex = currentOrder.indexOf(category.id)
                                                if (currentIndex < 0) break
                                                val draggedCenter = baseCenter + draggedCategoryOffsetPx
                                                val nextId = currentOrder.getOrNull(currentIndex + 1)
                                                val nextCenter = nextId?.let(centers::get)
                                                if (nextCenter != null && draggedCenter > nextCenter) {
                                                    val gap = nextCenter - baseCenter
                                                    categoryOrderIds = reorderItemToIndex(currentOrder, category.id, currentIndex + 1)
                                                    draggedCategoryOffsetPx -= gap
                                                    baseCenter = nextCenter
                                                    continue
                                                }
                                                val previousId = currentOrder.getOrNull(currentIndex - 1)
                                                val previousCenter = previousId?.let(centers::get)
                                                if (previousCenter != null && draggedCenter < previousCenter) {
                                                    val gap = baseCenter - previousCenter
                                                    categoryOrderIds = reorderItemToIndex(currentOrder, category.id, currentIndex - 1)
                                                    draggedCategoryOffsetPx += gap
                                                    baseCenter = previousCenter
                                                    continue
                                                }
                                                break
                                            }
                                        }
                                    },
                                    onDragStopped = {
                                        if (draggedCategoryId == category.id) {
                                            val finalOrder = currentCategoryOrder()
                                            if (finalOrder != dragStartingOrder) {
                                                pendingCategoryMoves += 1
                                                categoryMoveRequests =
                                                    categoryMoveRequests +
                                                    CategoryMoveRequest(
                                                        category.id,
                                                        itemTargetIdAfterMove(finalOrder, category.id),
                                                    )
                                            }
                                            draggedCategoryId = null
                                            draggedCategoryOffsetPx = 0f
                                            dragStartingOrder = emptyList()
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                if (isLiveBudget && (hiddenCategoryRecords.isNotEmpty() || hiddenGroupRecords.isNotEmpty())) {
                    item(key = "category-group-hidden-toggle:${group.id}") {
                        SecondaryButton(if (showHidden) "Hide hidden items" else "Show hidden items") {
                            showHidden = !showHidden
                        }
                    }
                    if (showHidden) {
                        items(hiddenCategoryRecords, key = { "hidden-category:${it.recordId}" }) { hidden ->
                            val isReordering = selectedCategoryOrderId == hidden.recordId
                            val hiddenIndex = hiddenCategoryRecords.indexOfFirst { it.recordId == hidden.recordId }
                            val moveHiddenCategory: (Int) -> Unit = { direction ->
                                val targetId =
                                    if (direction < 0) {
                                        hiddenCategoryRecords.getOrNull(hiddenIndex - 1)?.recordId
                                    } else {
                                        hiddenCategoryRecords.getOrNull(hiddenIndex + 2)?.recordId
                                    }
                                scope.launch {
                                    mobileSession.manageBudgetCategory(
                                        state.selectedBudgetId,
                                        hidden.recordId,
                                        "move",
                                        targetId,
                                        state.encryptedPassword.takeIf { it.isNotBlank() },
                                    )
                                }
                            }
                            SurfaceCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ListRow(
                                        hidden.recordName() ?: "Hidden category",
                                        if (isReordering) null else "Hidden",
                                        leading = RowLeading.Icon(Lucide.EyeOff),
                                        options = ListRowOptions(showChevron = !isReordering),
                                        onLongClick =
                                            if (!state.readOnlyMode && !mobileSession.isPerformingBudgetAction && !isReordering) {
                                                { selectedCategoryOrderId = hidden.recordId }
                                            } else {
                                                null
                                            },
                                        onClick =
                                            {
                                                if (isReordering) {
                                                    selectedCategoryOrderId = null
                                                } else {
                                                    scope.launch {
                                                        mobileSession.manageBudgetCategory(
                                                            state.selectedBudgetId,
                                                            hidden.recordId,
                                                            "show",
                                                            null,
                                                            state.encryptedPassword.takeIf { it.isNotBlank() },
                                                        )
                                                    }
                                                }
                                            },
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (isReordering) {
                                        BudgetItemReorderActions(
                                            itemName = hidden.recordName() ?: "Hidden category",
                                            isHidden = true,
                                            canMoveUp = hiddenIndex > 0,
                                            canMoveDown = hiddenIndex >= 0 && hiddenIndex < hiddenCategoryRecords.lastIndex,
                                            enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
                                            onToggleHidden = {
                                                scope.launch {
                                                    if (mobileSession.manageBudgetCategory(
                                                            state.selectedBudgetId,
                                                            hidden.recordId,
                                                            "show",
                                                            null,
                                                            state.encryptedPassword.takeIf { it.isNotBlank() },
                                                        )
                                                    ) {
                                                        selectedCategoryOrderId = null
                                                    }
                                                }
                                            },
                                            onMoveUp = { moveHiddenCategory(-1) },
                                            onMoveDown = { moveHiddenCategory(1) },
                                        )
                                    }
                                }
                            }
                        }
                        items(hiddenGroupRecords, key = { "hidden-group:${it.recordId}" }) { hidden ->
                            SurfaceCard {
                                ListRow(
                                    hidden.recordName() ?: "Hidden category group",
                                    "Category group · hidden",
                                    leading = RowLeading.Icon(Lucide.EyeOff),
                                    trailing = "Show",
                                    onClick = {
                                        scope.launch {
                                            mobileSession.manageBudgetCategoryGroup(
                                                state.selectedBudgetId,
                                                hidden.recordId,
                                                "show",
                                                null,
                                                null,
                                                state.encryptedPassword.takeIf { it.isNotBlank() },
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        if (isLiveBudget && group != null) {
            FloatingAction(
                compact = true,
                enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
                label = "Add category",
                contentDescription = "Add category",
                icon = Lucide.Plus,
                onClick = {
                    newCategoryName = ""
                    mobileSession.clearBudgetActionMessage()
                    showCreateCategoryDialog = true
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp),
            )
        }
    }
    if (showGroupDialog && isLiveBudget && group != null) {
        CategoryGroupNameDialog(
            title = "Edit category group",
            name = groupNameDraft,
            onNameChange = { groupNameDraft = it },
            busy = mobileSession.isPerformingBudgetAction,
            message = mobileSession.budgetActionMessage,
            placeholder = group.name,
            onDismiss = { showGroupDialog = false },
            onSave = {
                scope.launch {
                    if (mobileSession.manageBudgetCategoryGroup(
                            state.selectedBudgetId,
                            group.id,
                            "rename",
                            groupNameDraft,
                            null,
                            state.encryptedPassword.takeIf { it.isNotBlank() },
                        )
                    ) {
                        showGroupDialog = false
                    }
                }
            },
        )
    }
    if (showCreateCategoryDialog && isLiveBudget && group != null) {
        CategoryGroupNameDialog(
            title = "Add category",
            name = newCategoryName,
            onNameChange = { newCategoryName = it },
            busy = mobileSession.isPerformingBudgetAction,
            message = mobileSession.budgetActionMessage,
            placeholder = "Groceries",
            onDismiss = { showCreateCategoryDialog = false },
            onSave = {
                scope.launch {
                    if (mobileSession.createBudgetCategory(
                            state.selectedBudgetId,
                            newCategoryName,
                            group.id,
                            state.encryptedPassword.takeIf { it.isNotBlank() },
                        ) != null
                    ) {
                        showCreateCategoryDialog = false
                    }
                }
            },
        )
    }
}

@Composable
internal fun BudgetItemReorderActions(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    enabled: Boolean,
    dragEnabled: Boolean = enabled,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    itemName: String = "item",
    isHidden: Boolean = false,
    onToggleHidden: (() -> Unit)? = null,
    onDragStarted: (() -> Unit)? = null,
    onDragBy: ((Float) -> Unit)? = null,
    onDragStopped: (() -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        onToggleHidden?.let { toggleHidden ->
            TandemIconButton(
                if (isHidden) Lucide.Eye else Lucide.EyeOff,
                if (isHidden) "Show $itemName" else "Hide $itemName",
                sizeDp = 44,
                iconSizeDp = 18,
                enabled = enabled,
                tint = if (isHidden) tandemPalette.positive else tandemPalette.muted,
                onClick = toggleHidden,
            )
        }
        TandemIconButton(
            Lucide.ChevronUp,
            "Move $itemName up",
            sizeDp = 44,
            iconSizeDp = 18,
            enabled = enabled && canMoveUp,
            onClick = onMoveUp,
        )
        TandemIconButton(
            Lucide.ChevronDown,
            "Move $itemName down",
            sizeDp = 44,
            iconSizeDp = 18,
            enabled = enabled && canMoveDown,
            onClick = onMoveDown,
        )
        ReorderDragHandle(
            enabled = dragEnabled && (canMoveUp || canMoveDown),
            canMoveUp = canMoveUp,
            canMoveDown = canMoveDown,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
            onDragStarted = onDragStarted,
            onDragBy = onDragBy,
            onDragStopped = onDragStopped,
        )
    }
}

@Composable
internal fun CategoryGroupNameDialog(
    title: String,
    name: String,
    onNameChange: (String) -> Unit,
    busy: Boolean,
    message: String?,
    placeholder: String = "Essentials",
    isIncome: Boolean? = null,
    onIncomeChange: ((Boolean) -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 440.dp).padding(horizontal = 16.dp)) {
            Column(Modifier.fillMaxWidth().padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                Text(title, color = tandemPalette.text, fontSize = TandemTypography.sectionTitle, fontWeight = FontWeight.Bold)
                TextFieldCard("Name", name, onNameChange, TextFieldOptions(placeholder = placeholder))
                if (isIncome != null && onIncomeChange != null) {
                    ToggleRow("Income group", "For income categories", isIncome, !busy, onIncomeChange)
                }
                message?.let { InlineMessage(it, InlineMessageKind.ERROR) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    TandemActionButton("Cancel", TandemActionButtonTone.NEUTRAL, Modifier.weight(1f), enabled = !busy, onClick = onDismiss)
                    TandemActionButton(
                        "Save",
                        TandemActionButtonTone.PRIMARY,
                        Modifier.weight(1f),
                        icon = Lucide.Check,
                        enabled =
                            name.isNotBlank() && !busy,
                        loading = busy,
                        onClick = onSave,
                    )
                }
            }
        }
    }
}
