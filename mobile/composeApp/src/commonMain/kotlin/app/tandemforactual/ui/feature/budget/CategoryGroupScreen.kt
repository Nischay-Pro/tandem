@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.app.FloatingAction
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.session.BudgetReorderKind
import app.tandemforactual.ui.core.session.DragReorderState
import app.tandemforactual.ui.core.session.HIDDEN_CATEGORY_SCOPE_SUFFIX
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.budgetReorderMovesFor
import app.tandemforactual.ui.core.session.budget.budgetReorderStatus
import app.tandemforactual.ui.core.session.budget.categoryGroupVisibilityOverridesFor
import app.tandemforactual.ui.core.session.budget.categoryVisibilityOverridesFor
import app.tandemforactual.ui.core.session.budget.clearBudgetReorderMessage
import app.tandemforactual.ui.core.session.budget.deletedCategoryIdsFor
import app.tandemforactual.ui.core.session.budget.enqueueBudgetCategoryCreate
import app.tandemforactual.ui.core.session.budget.enqueueBudgetCategoryGroupDelete
import app.tandemforactual.ui.core.session.budget.enqueueBudgetCategoryGroupRename
import app.tandemforactual.ui.core.session.budget.enqueueBudgetCategoryGroupVisibilityChange
import app.tandemforactual.ui.core.session.budget.enqueueBudgetCategoryVisibilityChange
import app.tandemforactual.ui.core.session.budget.enqueueBudgetReorder
import app.tandemforactual.ui.core.session.budget.resolveCategoryGroupId
import app.tandemforactual.ui.core.session.budget.retryQueuedBudgetReorders
import app.tandemforactual.ui.core.session.budgetActionFeedbackMessage
import app.tandemforactual.ui.core.session.budgetOrderForScreen
import app.tandemforactual.ui.core.session.dragReorderItemModifier
import app.tandemforactual.ui.core.session.reorderAccessibilityAction
import app.tandemforactual.ui.core.session.reorderModeConsumesItemTap
import app.tandemforactual.ui.core.session.shouldShowBudgetCreateAction
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.core.state.SelectorItem
import app.tandemforactual.ui.core.state.booleanValue
import app.tandemforactual.ui.core.state.categoryIconFor
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.recordBody
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.core.state.stringValue
import app.tandemforactual.ui.design.buttons.SecondaryButton
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.sheets.DestructiveConfirmationDialog
import app.tandemforactual.ui.design.templates.AmountHero
import app.tandemforactual.ui.design.templates.PageLazyColumn
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import app.tandemforactual.ui.feature.accounts.AccountActionDropdown
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import kotlinx.coroutines.launch

@Composable
internal fun CategoryGroupScreen(
    state: AppState,
    navigation: AppNavigation,
    liveBudgetMonth: LiveBudgetMonthSummary?,
    mobileSession: TandemMobileSession,
) {
    val palette = tandemPalette
    val scope = rememberCoroutineScope()
    val isLiveBudget = state.selectedBudgetId.isNotBlank()
    if (!isLiveBudget) {
        EmptyState(
            "Choose a budget",
            "Open an Actual budget to view its categories.",
            actionLabel = "Choose budget",
            onAction = { navigation.open(AppRoute.BUDGET_CHOOSER) },
        )
        return
    }
    val isTrackingBudget = isLiveBudget && state.budgetDisplaySettings?.budgetType == "tracking"
    val groups = liveBudgetMonth?.groups.orEmpty()
    val selectedGroupId = mobileSession.budgetQueue.resolveCategoryGroupId(state.selectedBudgetId, state.selectedCategoryGroupId)
    val group = groups.firstOrNull { it.id == selectedGroupId } ?: groups.firstOrNull()
    var showHidden by rememberSaveable(state.selectedBudgetId) { mutableStateOf(false) }
    var selectedCategoryOrderId by rememberSaveable(state.selectedBudgetId, group?.id) { mutableStateOf<String?>(null) }
    var showGroupDialog by rememberSaveable(state.selectedBudgetId, group?.id) { mutableStateOf(false) }
    var showDeleteGroupDialog by rememberSaveable(state.selectedBudgetId, group?.id) { mutableStateOf(false) }
    var deleteGroupDestinationId by
        rememberSaveable(state.selectedBudgetId, group?.id) {
            mutableStateOf(UNSELECTED_GROUP_DELETE_DESTINATION)
        }
    var showCreateCategoryDialog by rememberSaveable(state.selectedBudgetId, group?.id) { mutableStateOf(false) }
    var groupNameDraft by rememberSaveable(state.selectedBudgetId, group?.id) { mutableStateOf(group?.name.orEmpty()) }
    var newCategoryName by rememberSaveable(state.selectedBudgetId, group?.id) { mutableStateOf("") }
    val categoryScopeId = group?.id.orEmpty()
    val groupDeleteDestinations =
        remember(groups, group?.id) {
            groups
                .filter { it.id != group?.id && it.isIncome == group?.isIncome }
                .flatMap { destinationGroup ->
                    destinationGroup.categories.map { category ->
                        SelectorItem(category.id, "${category.name} · ${destinationGroup.name}")
                    }
                }
        }
    val groupHasCategories =
        remember(group?.id, group?.categories, mobileSession.categoryRecords) {
            group?.categories?.isNotEmpty() == true ||
                mobileSession.categoryRecords.any { record ->
                    val body = record.recordBody()
                    record.recordType == "category" &&
                        body?.booleanValue("deleted") != true &&
                        (body?.stringValue("group") ?: body?.stringValue("group_id")) == group?.id
                }
        }
    val queuedCategoryMoves = mobileSession.budgetQueue.budgetReorderMovesFor(state.selectedBudgetId, categoryScopeId)
    val categoryMoveStatus = mobileSession.budgetQueue.budgetReorderStatus(state.selectedBudgetId, categoryScopeId)
    val serverCategoryOrder = group?.categories.orEmpty().map { it.id }
    val queuedCategoryOrder = budgetOrderForScreen(serverCategoryOrder, categoryMoveStatus, queuedCategoryMoves)
    val dragReorder = remember(state.selectedBudgetId, group?.id) { DragReorderState() }
    val categoryOrderIds = dragReorder.currentOrder(queuedCategoryOrder)
    val orderedCategories =
        remember(group?.categories, categoryOrderIds) {
            val categories = group?.categories.orEmpty()
            val categoriesById = categories.associateBy { it.id }
            categoryOrderIds.mapNotNull(categoriesById::get) + categories.filterNot { it.id in categoryOrderIds }
        }

    fun currentCategoryOrder(): List<String> =
        dragReorder.currentOrder(
            budgetOrderForScreen(
                serverCategoryOrder,
                mobileSession.budgetQueue.budgetReorderStatus(state.selectedBudgetId, categoryScopeId),
                mobileSession.budgetQueue.budgetReorderMovesFor(state.selectedBudgetId, categoryScopeId),
            ),
        )

    fun enqueueCategoryMove(
        itemId: String,
        direction: Int,
    ) {
        val oldOrder = currentCategoryOrder()
        val nextOrder = reorderItemToIndex(oldOrder, itemId, oldOrder.indexOf(itemId) + direction)
        if (oldOrder == nextOrder) return
        mobileSession.budgetQueue.clearBudgetReorderMessage(state.selectedBudgetId, categoryScopeId)
        mobileSession.feedback.clearBudgetActionMessage()
        scope.launch {
            mobileSession.budgetQueue.enqueueBudgetReorder(
                state.selectedBudgetId,
                categoryScopeId,
                BudgetReorderKind.CATEGORY,
                itemId,
                itemTargetIdAfterMove(nextOrder, itemId),
                nextOrder,
                state.encryptedPassword.takeIf { it.isNotBlank() },
                baseOrder = serverCategoryOrder,
            )
        }
    }
    val hiddenGroupRecords =
        remember(
            mobileSession.categoryGroupRecords,
            mobileSession.budgetQueue.categoryGroupVisibilityOverridesFor(state.selectedBudgetId),
        ) {
            val visibilityOverrides = mobileSession.budgetQueue.categoryGroupVisibilityOverridesFor(state.selectedBudgetId)
            mobileSession.categoryGroupRecords.filter { record ->
                visibilityOverrides[record.recordId] ?: (record.recordBody()?.booleanValue("hidden") == true)
            }
        }
    val categoryVisibilityOverrides = mobileSession.budgetQueue.categoryVisibilityOverridesFor(state.selectedBudgetId)
    val deletedCategoryIds = mobileSession.budgetQueue.deletedCategoryIdsFor(state.selectedBudgetId)
    val hiddenCategoryRecords =
        remember(mobileSession.categoryRecords, group?.id, categoryVisibilityOverrides, deletedCategoryIds) {
            mobileSession.categoryRecords.filter { record ->
                val body = record.recordBody()
                record.recordId !in deletedCategoryIds &&
                    (categoryVisibilityOverrides[record.recordId] ?: body?.booleanValue("hidden") ?: false) &&
                    (body?.stringValue("group") ?: body?.stringValue("group_id")) == group?.id
            }
        }
    val hiddenCategoryScopeId = "${group?.id.orEmpty()}$HIDDEN_CATEGORY_SCOPE_SUFFIX"
    val queuedHiddenCategoryMoves = mobileSession.budgetQueue.budgetReorderMovesFor(state.selectedBudgetId, hiddenCategoryScopeId)
    val hiddenCategoryMoveStatus = mobileSession.budgetQueue.budgetReorderStatus(state.selectedBudgetId, hiddenCategoryScopeId)
    val queuedHiddenCategoryOrder =
        budgetOrderForScreen(
            hiddenCategoryRecords.map { it.recordId },
            hiddenCategoryMoveStatus,
            queuedHiddenCategoryMoves,
        )
    val hiddenDrag = remember(state.selectedBudgetId, group?.id) { DragReorderState() }
    val hiddenCategoryOrder = hiddenDrag.currentOrder(queuedHiddenCategoryOrder)

    fun latestQueuedHiddenCategoryOrder(): List<String> =
        budgetOrderForScreen(
            hiddenCategoryRecords.map { it.recordId },
            mobileSession.budgetQueue.budgetReorderStatus(state.selectedBudgetId, hiddenCategoryScopeId),
            mobileSession.budgetQueue.budgetReorderMovesFor(state.selectedBudgetId, hiddenCategoryScopeId),
        )
    val orderedHiddenCategoryRecords =
        remember(hiddenCategoryRecords, hiddenCategoryOrder) {
            val recordsById = hiddenCategoryRecords.associateBy { it.recordId }
            hiddenCategoryOrder.mapNotNull(recordsById::get) + hiddenCategoryRecords.filterNot { it.recordId in hiddenCategoryOrder }
        }
    Box(Modifier.fillMaxSize()) {
        PageLazyColumn {
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
                            Text(state.selectedMonth, color = tandemPalette.muted, fontSize = TandemTypography.label)
                        }
                        if (isLiveBudget) {
                            TandemIconButton(
                                Lucide.Pencil,
                                "Edit category group",
                                enabled = !state.readOnlyMode,
                            ) {
                                groupNameDraft = group.name
                                mobileSession.feedback.clearBudgetActionMessage()
                                showGroupDialog = true
                            }
                        }
                    }
                }
                categoryMoveStatus.message?.let { message ->
                    item(key = "category-group-reorder-message:${group.id}") {
                        InlineMessage(
                            message,
                            if (categoryMoveStatus.warning) InlineMessageKind.WARNING else InlineMessageKind.ERROR,
                            Modifier.padding(horizontal = TandemSpacing.large),
                            actionLabel = "Try again".takeIf { categoryMoveStatus.pendingCount > 0 },
                            onAction =
                                {
                                    mobileSession.budgetQueue.retryQueuedBudgetReorders(
                                        state.selectedBudgetId,
                                        state.encryptedPassword.takeIf(String::isNotBlank),
                                    )
                                }.takeIf { categoryMoveStatus.pendingCount > 0 },
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
                    val categoryIndex = orderedCategories.indexOfFirst { it.id == category.id }
                    val canMoveUp = categoryIndex > 0
                    val canMoveDown = categoryIndex >= 0 && categoryIndex < orderedCategories.lastIndex
                    val moveCategory: (Int) -> Unit = { direction ->
                        enqueueCategoryMove(category.id, direction)
                    }

                    fun startDrag() {
                        selectedCategoryOrderId = category.id
                        mobileSession.budgetQueue.clearBudgetReorderMessage(state.selectedBudgetId, categoryScopeId)
                        mobileSession.feedback.clearBudgetActionMessage()
                        dragReorder.start(currentCategoryOrder(), category.id)
                    }

                    fun dragBy(delta: Float) {
                        if (dragReorder.draggedItemId == category.id) {
                            dragReorder.dragBy(delta)
                        }
                    }

                    fun dropDrag() {
                        dragReorder.finish()?.let { result ->
                            if (result.itemId == category.id && result.finalOrder != result.startingOrder) {
                                scope.launch {
                                    val saved =
                                        mobileSession.budgetQueue.enqueueBudgetReorder(
                                            state.selectedBudgetId,
                                            categoryScopeId,
                                            BudgetReorderKind.CATEGORY,
                                            result.itemId,
                                            itemTargetIdAfterMove(result.finalOrder, result.itemId),
                                            result.finalOrder,
                                            state.encryptedPassword.takeIf { it.isNotBlank() },
                                            baseOrder = serverCategoryOrder,
                                        )
                                    if (!saved) dragReorder.releaseHeldDrop()
                                }
                            }
                        }
                    }
                    val categoryCardModifier =
                        dragReorderItemModifier(
                            itemId = category.id,
                            dragState = dragReorder,
                            reduceMotion = tandemReduceMotion,
                            dragEnabled = isLiveBudget && !state.readOnlyMode && orderedCategories.size > 1,
                            onDragStarted = ::startDrag,
                            onDragBy = ::dragBy,
                            onDragStopped = ::dropDrag,
                        )
                    SurfaceCard(
                        modifier =
                            categoryCardModifier
                                .combinedClickable(
                                    role = Role.Button,
                                    onClickLabel = category.name,
                                    onClick = {
                                        if (reorderModeConsumesItemTap(selectedCategoryOrderId)) {
                                            selectedCategoryOrderId = null
                                        } else {
                                            state.selectedCategoryGroupId = group.id
                                            state.selectedCategoryId = category.id
                                            state.categoryEditRequested = false
                                            navigation.open(AppRoute.CATEGORY_DETAIL)
                                        }
                                    },
                                ).then(
                                    if (isLiveBudget && !state.readOnlyMode && orderedCategories.size > 1) {
                                        Modifier.reorderAccessibilityAction(category.name) {
                                            selectedCategoryOrderId = category.id
                                        }
                                    } else {
                                        Modifier
                                    },
                                ),
                    ) {
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
                                modifier = Modifier.weight(1f),
                            )
                            if (isReordering && isLiveBudget) {
                                BudgetItemReorderActions(
                                    itemName = category.name,
                                    isHidden = false,
                                    canMoveUp = canMoveUp,
                                    canMoveDown = canMoveDown,
                                    enabled = !state.readOnlyMode,
                                    toggleEnabled = !state.readOnlyMode,
                                    dragEnabled = !state.readOnlyMode,
                                    onToggleHidden = {
                                        scope.launch {
                                            val saved =
                                                mobileSession.budgetQueue.enqueueBudgetCategoryVisibilityChange(
                                                    budgetId = state.selectedBudgetId,
                                                    categoryId = category.id,
                                                    groupId = categoryScopeId,
                                                    hidden = true,
                                                    currentOrder = currentCategoryOrder(),
                                                    encryptionPassword = state.encryptedPassword.takeIf { it.isNotBlank() },
                                                )
                                            if (saved) {
                                                selectedCategoryOrderId = null
                                                showHidden = true
                                            }
                                        }
                                    },
                                    onMoveUp = { moveCategory(-1) },
                                    onMoveDown = { moveCategory(1) },
                                    onDragStarted = ::startDrag,
                                    onDragBy = ::dragBy,
                                    onDragStopped = ::dropDrag,
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
                        hiddenCategoryMoveStatus.message?.let { message ->
                            item(key = "hidden-category-reorder-message:${group.id}") {
                                InlineMessage(
                                    message,
                                    if (hiddenCategoryMoveStatus.warning) InlineMessageKind.WARNING else InlineMessageKind.ERROR,
                                    Modifier.padding(horizontal = TandemSpacing.large),
                                    actionLabel = "Try again".takeIf { hiddenCategoryMoveStatus.pendingCount > 0 },
                                    onAction =
                                        {
                                            mobileSession.budgetQueue.retryQueuedBudgetReorders(
                                                state.selectedBudgetId,
                                                state.encryptedPassword.takeIf(String::isNotBlank),
                                            )
                                        }.takeIf { hiddenCategoryMoveStatus.pendingCount > 0 },
                                )
                            }
                        }
                        items(orderedHiddenCategoryRecords, key = { "hidden-category:${it.recordId}" }) { hidden ->
                            val isReordering = selectedCategoryOrderId == hidden.recordId
                            val hiddenIndex = orderedHiddenCategoryRecords.indexOfFirst { it.recordId == hidden.recordId }

                            fun saveHiddenOrder(nextOrder: List<String>) {
                                if (nextOrder == latestQueuedHiddenCategoryOrder()) return
                                mobileSession.budgetQueue.clearBudgetReorderMessage(state.selectedBudgetId, hiddenCategoryScopeId)
                                scope.launch {
                                    mobileSession.budgetQueue.enqueueBudgetReorder(
                                        state.selectedBudgetId,
                                        hiddenCategoryScopeId,
                                        BudgetReorderKind.CATEGORY,
                                        hidden.recordId,
                                        itemTargetIdAfterMove(nextOrder, hidden.recordId),
                                        nextOrder,
                                        state.encryptedPassword.takeIf { it.isNotBlank() },
                                        baseOrder = hiddenCategoryRecords.map { it.recordId },
                                    )
                                }
                            }

                            fun moveHiddenCategory(direction: Int) {
                                val currentOrder = hiddenDrag.currentOrder(latestQueuedHiddenCategoryOrder())
                                saveHiddenOrder(
                                    reorderItemToIndex(currentOrder, hidden.recordId, currentOrder.indexOf(hidden.recordId) + direction),
                                )
                            }

                            val hiddenDragEnabled = isLiveBudget && !state.readOnlyMode && orderedHiddenCategoryRecords.size > 1

                            fun startHiddenDrag() {
                                selectedCategoryOrderId = hidden.recordId
                                mobileSession.budgetQueue.clearBudgetReorderMessage(state.selectedBudgetId, hiddenCategoryScopeId)
                                mobileSession.feedback.clearBudgetActionMessage()
                                hiddenDrag.start(hiddenDrag.currentOrder(latestQueuedHiddenCategoryOrder()), hidden.recordId)
                            }

                            fun dragHiddenBy(delta: Float) {
                                if (hiddenDrag.draggedItemId == hidden.recordId) hiddenDrag.dragBy(delta)
                            }

                            fun dropHiddenDrag() {
                                val result = hiddenDrag.finish(latestQueuedHiddenCategoryOrder()) ?: return
                                if (result.itemId != hidden.recordId || result.finalOrder == result.startingOrder) return
                                scope.launch {
                                    val saved =
                                        mobileSession.budgetQueue.enqueueBudgetReorder(
                                            state.selectedBudgetId,
                                            hiddenCategoryScopeId,
                                            BudgetReorderKind.CATEGORY,
                                            result.itemId,
                                            itemTargetIdAfterMove(result.finalOrder, result.itemId),
                                            result.finalOrder,
                                            state.encryptedPassword.takeIf { it.isNotBlank() },
                                            baseOrder = hiddenCategoryRecords.map { it.recordId },
                                        )
                                    if (!saved) hiddenDrag.releaseHeldDrop()
                                }
                            }

                            SurfaceCard(
                                modifier =
                                    dragReorderItemModifier(
                                        itemId = hidden.recordId,
                                        dragState = hiddenDrag,
                                        reduceMotion = tandemReduceMotion,
                                        dragEnabled = hiddenDragEnabled,
                                        onDragStarted = ::startHiddenDrag,
                                        onDragBy = ::dragHiddenBy,
                                        onDragStopped = ::dropHiddenDrag,
                                    ),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ListRow(
                                        hidden.recordName() ?: "Hidden category",
                                        if (isReordering) null else "Hidden",
                                        leading = RowLeading.Icon(Lucide.EyeOff),
                                        options = ListRowOptions(showChevron = !isReordering),
                                        // With drag enabled, a long press starts dragging (which also shows the controls).
                                        onLongClick =
                                            if (!state.readOnlyMode &&
                                                !isReordering &&
                                                !hiddenDragEnabled
                                            ) {
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
                                                        mobileSession.budgetQueue.enqueueBudgetCategoryVisibilityChange(
                                                            budgetId = state.selectedBudgetId,
                                                            categoryId = hidden.recordId,
                                                            groupId = group.id,
                                                            hidden = false,
                                                            currentOrder = currentCategoryOrder(),
                                                            encryptionPassword = state.encryptedPassword.takeIf { it.isNotBlank() },
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
                                            enabled = !state.readOnlyMode,
                                            toggleEnabled = !state.readOnlyMode,
                                            onToggleHidden = {
                                                scope.launch {
                                                    val saved =
                                                        mobileSession.budgetQueue.enqueueBudgetCategoryVisibilityChange(
                                                            budgetId = state.selectedBudgetId,
                                                            categoryId = hidden.recordId,
                                                            groupId = group.id,
                                                            hidden = false,
                                                            currentOrder = currentCategoryOrder(),
                                                            encryptionPassword = state.encryptedPassword.takeIf { it.isNotBlank() },
                                                        )
                                                    if (saved) selectedCategoryOrderId = null
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
                                            mobileSession.budgetQueue.enqueueBudgetCategoryGroupVisibilityChange(
                                                state.selectedBudgetId,
                                                hidden.recordId,
                                                hidden = false,
                                                encryptionPassword = state.encryptedPassword.takeIf { it.isNotBlank() },
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
        if (group != null && shouldShowBudgetCreateAction(isLiveBudget, selectedCategoryOrderId != null)) {
            FloatingAction(
                compact = true,
                enabled = !state.readOnlyMode,
                label = "Add category",
                contentDescription = "Add category",
                icon = Lucide.Plus,
                outlined = true,
                onClick = {
                    newCategoryName = ""
                    mobileSession.feedback.clearBudgetActionMessage()
                    showCreateCategoryDialog = true
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp),
            )
        }
    }
    if (isLiveBudget && group != null) {
        CategoryGroupNameDialog(
            visible = showGroupDialog,
            title = "Edit category group",
            name = groupNameDraft,
            onNameChange = { groupNameDraft = it },
            busy = mobileSession.isPerformingBudgetAction || state.readOnlyMode,
            message = mobileSession.budgetActionMessage,
            messageKind = mobileSession.feedback.budgetActionMessageKind,
            messageActionLabel = mobileSession.feedback.budgetActionMessageActionLabel,
            onMessageAction =
                if (mobileSession.feedback.budgetActionMessageActionLabel ==
                    null
                ) {
                    null
                } else {
                    mobileSession.feedback::retryBudgetAction
                },
            placeholder = group.name,
            onDismiss = { showGroupDialog = false },
            onSave = {
                scope.launch {
                    if (mobileSession.budgetQueue.enqueueBudgetCategoryGroupRename(
                            state.selectedBudgetId,
                            group.id,
                            groupNameDraft,
                            groups.map { it.id },
                            state.encryptedPassword.takeIf { it.isNotBlank() },
                        )
                    ) {
                        showGroupDialog = false
                    }
                }
            },
            onDelete = {
                deleteGroupDestinationId = UNSELECTED_GROUP_DELETE_DESTINATION
                showGroupDialog = false
                showDeleteGroupDialog = true
            },
        )
        if (showDeleteGroupDialog) {
            DestructiveConfirmationDialog(
                title = "Delete ${group.name}?",
                message =
                    if (groupHasCategories) {
                        "The group’s categories will be deleted. Move their budget and transaction history to another category, or leave transactions unassigned."
                    } else {
                        "This category group is empty."
                    },
                confirmLabel = "Delete group",
                dismissLabel = "Keep group",
                enabled =
                    !state.readOnlyMode &&
                        !mobileSession.isPerformingBudgetAction &&
                        (!groupHasCategories || deleteGroupDestinationId != UNSELECTED_GROUP_DELETE_DESTINATION),
                content = {
                    if (groupHasCategories) {
                        AccountActionDropdown(
                            title = "Reassign to",
                            selectedLabel =
                                when {
                                    deleteGroupDestinationId == UNSELECTED_GROUP_DELETE_DESTINATION -> "Choose destination"
                                    deleteGroupDestinationId.isBlank() -> "Leave unassigned"
                                    else ->
                                        groupDeleteDestinations.firstOrNull { it.id == deleteGroupDestinationId }?.label
                                            ?: "Leave unassigned"
                                },
                            options =
                                listOf("Leave unassigned" to null) +
                                    groupDeleteDestinations.map { it.label to it.id },
                            onSelect = { deleteGroupDestinationId = it.orEmpty() },
                        )
                        InlineMessage(
                            when {
                                deleteGroupDestinationId == UNSELECTED_GROUP_DELETE_DESTINATION ->
                                    "Choose a destination or leave transactions unassigned."
                                deleteGroupDestinationId.isBlank() ->
                                    "Transactions become unassigned; deleted-category budget amounts are removed."
                                else ->
                                    "Budget amounts and transaction history will move to the selected category."
                            },
                            InlineMessageKind.INFO,
                        )
                    }
                    budgetActionFeedbackMessage(mobileSession)
                },
                onDismissRequest = { if (!mobileSession.isPerformingBudgetAction) showDeleteGroupDialog = false },
                onConfirm = {
                    scope.launch {
                        val deleted =
                            mobileSession.budgetQueue.enqueueBudgetCategoryGroupDelete(
                                state.selectedBudgetId,
                                group.id,
                                deleteGroupDestinationId
                                    .takeUnless {
                                        it.isBlank() || it == UNSELECTED_GROUP_DELETE_DESTINATION
                                    },
                                groups.map { it.id },
                                state.encryptedPassword.takeIf(String::isNotBlank),
                            )
                        if (deleted) {
                            showDeleteGroupDialog = false
                            navigation.back()
                        }
                    }
                },
            )
        }
        CategoryGroupNameDialog(
            visible = showCreateCategoryDialog,
            title = "Add category",
            name = newCategoryName,
            onNameChange = { newCategoryName = it },
            busy = mobileSession.isPerformingBudgetAction || state.readOnlyMode,
            message = mobileSession.budgetActionMessage,
            messageKind = mobileSession.feedback.budgetActionMessageKind,
            messageActionLabel = mobileSession.feedback.budgetActionMessageActionLabel,
            onMessageAction =
                if (mobileSession.feedback.budgetActionMessageActionLabel ==
                    null
                ) {
                    null
                } else {
                    mobileSession.feedback::retryBudgetAction
                },
            placeholder = "Groceries",
            onDismiss = { showCreateCategoryDialog = false },
            onSave = {
                scope.launch {
                    if (mobileSession.budgetQueue.enqueueBudgetCategoryCreate(
                            state.selectedBudgetId,
                            group.id,
                            newCategoryName,
                            group.categories.map { it.id },
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

private const val UNSELECTED_GROUP_DELETE_DESTINATION = "__choose_group_delete_destination__"
