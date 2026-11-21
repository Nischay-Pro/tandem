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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.components.BodyText
import app.tandemforactual.ui.components.DestructiveConfirmationDialog
import app.tandemforactual.ui.components.DividerLine
import app.tandemforactual.ui.components.EmptyState
import app.tandemforactual.ui.components.LabelText
import app.tandemforactual.ui.components.ListRow
import app.tandemforactual.ui.components.RowLeading
import app.tandemforactual.ui.components.SecondaryButton
import app.tandemforactual.ui.components.SectionCard
import app.tandemforactual.ui.components.SelectableTile
import app.tandemforactual.ui.components.SurfaceCard
import app.tandemforactual.ui.components.TandemActionButton
import app.tandemforactual.ui.components.TandemActionButtonTone
import app.tandemforactual.ui.components.TandemIconButton
import app.tandemforactual.ui.components.TandemTaskSheet
import app.tandemforactual.ui.model.LiveBudgetMonthSummary
import app.tandemforactual.ui.model.LiveCategorySummary
import app.tandemforactual.ui.model.booleanValue
import app.tandemforactual.ui.model.recordBody
import app.tandemforactual.ui.reports.ReportWindow
import app.tandemforactual.ui.reports.reportMonthLabel
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.CircleDot
import com.composables.icons.lucide.Gauge
import com.composables.icons.lucide.History
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minus
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Receipt
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Tags
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.TriangleAlert
import com.composables.icons.lucide.WalletCards
import com.composables.icons.lucide.X
import kotlinx.coroutines.launch

@Composable
internal fun CategoryDetailScreen(
    state: AppState,
    navigation: AppNavigation,
    liveBudgetMonth: LiveBudgetMonthSummary?,
    mobileSession: TandemMobileSession,
) {
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
    val category =
        liveBudgetMonth
            ?.groups
            ?.asSequence()
            ?.flatMap { it.categories.asSequence() }
            ?.firstOrNull { it.id == state.selectedCategoryId }
    var showEditDialog by rememberSaveable(state.selectedBudgetId, state.selectedCategoryId) { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable(state.selectedBudgetId, state.selectedCategoryId) { mutableStateOf(false) }
    var confirmCategoryDelete by rememberSaveable(state.selectedBudgetId, state.selectedCategoryId) { mutableStateOf(false) }
    var showAssignedAmountDialog by rememberSaveable(state.selectedBudgetId, state.selectedCategoryId, state.selectedMonthId) {
        mutableStateOf(false)
    }
    var showMoveMoneyDialog by rememberSaveable(state.selectedBudgetId, state.selectedCategoryId, state.selectedMonthId) {
        mutableStateOf(false)
    }
    var moveMoneyFromId by rememberSaveable(state.selectedBudgetId, state.selectedCategoryId, state.selectedMonthId) { mutableStateOf("") }
    var moveMoneyToId by rememberSaveable(state.selectedBudgetId, state.selectedCategoryId, state.selectedMonthId) { mutableStateOf("") }
    var moveMoneyAmountDraft by rememberSaveable(
        state.selectedBudgetId,
        state.selectedCategoryId,
        state.selectedMonthId,
    ) { mutableStateOf("") }
    var moveMoneyFromExpanded by rememberSaveable { mutableStateOf(false) }
    var moveMoneyToExpanded by rememberSaveable { mutableStateOf(false) }
    var assignedAmountDraft by
        rememberSaveable(state.selectedBudgetId, state.selectedCategoryId, state.selectedMonthId) {
            mutableStateOf("")
        }
    var editName by rememberSaveable(state.selectedBudgetId, state.selectedCategoryId) { mutableStateOf("") }
    var editCategoryGroupId by rememberSaveable(state.selectedBudgetId, state.selectedCategoryId) {
        mutableStateOf(state.selectedCategoryGroupId)
    }
    var categoryGroupMenuExpanded by rememberSaveable(state.selectedBudgetId, state.selectedCategoryId) { mutableStateOf(false) }
    var iconSearch by rememberSaveable(state.selectedBudgetId, state.selectedCategoryId) { mutableStateOf("") }
    var selectedIconChoice by rememberSaveable(state.selectedBudgetId, state.selectedCategoryId) { mutableStateOf("Automatic") }
    var deleteDestinationId by rememberSaveable(state.selectedBudgetId, state.selectedCategoryId) { mutableStateOf<String?>(null) }
    val currentIncomeType =
        mobileSession.categoryRecords
            .firstOrNull { it.recordId == state.selectedCategoryId }
            ?.recordBody()
            ?.booleanValue("is_income") ?: false
    val deleteDestinations =
        liveBudgetMonth?.groups.orEmpty().flatMap { it.categories }.filter { candidate ->
            candidate.id != state.selectedCategoryId &&
                (
                    mobileSession.categoryRecords
                        .firstOrNull { it.recordId == candidate.id }
                        ?.recordBody()
                        ?.booleanValue("is_income") ?: false
                ) == currentIncomeType
        }
    val moveMoneyCategories =
        remember(liveBudgetMonth, mobileSession.categoryRecords, currentIncomeType) {
            liveBudgetMonth?.groups.orEmpty().flatMap { group ->
                group.categories
                    .filter { candidate ->
                        (
                            mobileSession.categoryRecords
                                .firstOrNull { it.recordId == candidate.id }
                                ?.recordBody()
                                ?.booleanValue("is_income") ?: false
                        ) == currentIncomeType
                    }.map { group.name to it }
            }
        }
    val selectedGroup =
        liveBudgetMonth?.groups?.firstOrNull { group ->
            group.categories.any { it.id == state.selectedCategoryId }
        }
    val categoryIndex = selectedGroup?.categories?.indexOfFirst { it.id == state.selectedCategoryId } ?: -1

    fun openCategoryEditor() {
        val selectedCategory = category ?: return
        editName = selectedCategory.name
        editCategoryGroupId = selectedGroup?.id ?: state.selectedCategoryGroupId
        selectedIconChoice =
            when (state.categoryIconChoice(selectedCategory.id)) {
                "Food" -> "Groceries"
                "Transport" -> "Car"
                else -> state.categoryIconChoice(selectedCategory.id) ?: "Automatic"
            }
        iconSearch = ""
        mobileSession.clearBudgetActionMessage()
        showEditDialog = true
    }
    LaunchedEffect(state.selectedCategoryId, state.categoryEditRequested, category?.id) {
        if (state.categoryEditRequested && category != null) {
            state.categoryEditRequested = false
            openCategoryEditor()
        }
    }
    PageColumn {
        if (category == null) {
            LabelText(state.selectedMonth)
            BodyText("This category isn’t in the saved plan for this month.")
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                Column(Modifier.weight(1f)) { LabelText(state.selectedMonth) }
                if (isLiveBudget) {
                    TandemIconButton(
                        Lucide.Pencil,
                        "Edit category",
                        enabled = !state.readOnlyMode,
                        tint = tandemPalette.accent,
                    ) { openCategoryEditor() }
                }
            }
            AmountHero(
                if (isTrackingBudget) "Activity" else "Available",
                state.formatCurrency(if (isTrackingBudget) category.activityMinor else category.balanceMinor),
                if (isTrackingBudget) "For the selected month" else "${state.formatCurrency(category.assignedMinor)} assigned",
            )
            CategoryInsightsSection(state, mobileSession, category, isTrackingBudget)
            SectionCard("This month") {
                if (!isTrackingBudget) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = TandemLayout.listRowMinHeight)
                            .clickable(
                                enabled = isLiveBudget && !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
                                role = Role.Button,
                                onClickLabel = "Edit assigned amount",
                                onClick = {
                                    assignedAmountDraft = state.formatAmount(category.assignedMinor)
                                    mobileSession.clearBudgetActionMessage()
                                    showAssignedAmountDialog = true
                                },
                            ).padding(
                                horizontal = TandemLayout.listRowHorizontalInset,
                                vertical = TandemLayout.listRowVerticalInset,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TandemLayout.listRowGap),
                    ) {
                        Box(
                            Modifier
                                .size(TandemLayout.listRowLeadingSize)
                                .clip(RoundedCornerShape(TandemShapes.medium))
                                .background(tandemPalette.raised),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Lucide.WalletCards,
                                contentDescription = null,
                                modifier = Modifier.size(TandemLayout.listRowIconSize),
                                tint = tandemPalette.accent,
                            )
                        }
                        Text(
                            "Assigned",
                            modifier = Modifier.weight(1f),
                            color = tandemPalette.text,
                            fontSize = TandemTypography.body,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            app.tandemforactual.ui.components.amountAnnotatedString(
                                state.formatCurrency(category.assignedMinor),
                                state.currencyFractionDigitsForDisplay,
                            ),
                            color = tandemPalette.text,
                            fontSize = TandemTypography.compactBody,
                        )
                        if (isLiveBudget) {
                            Icon(
                                Lucide.Pencil,
                                contentDescription = "Edit assigned amount",
                                modifier = Modifier.size(19.dp),
                                tint = if (state.readOnlyMode) tandemPalette.muted else tandemPalette.accent,
                            )
                        }
                    }
                }
                if (!isTrackingBudget) DividerLine()
                ListRow(
                    "Activity",
                    "${state.selectedMonth} · ${state.formatCurrency(category.activityMinor)}",
                    leading = RowLeading.Icon(Lucide.Receipt),
                    onClick = { openCategoryActivity(state, navigation, category.id, category.name) },
                )
                if (isLiveBudget && !isTrackingBudget) {
                    DividerLine()
                    ToggleRow(
                        "Rollover",
                        "Unused money rolls forward",
                        checked = category.carryover,
                        enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
                        leadingIcon = Lucide.RefreshCw,
                    ) { enabled ->
                        scope.launch {
                            mobileSession.setBudgetCategoryCarryover(
                                state.selectedBudgetId,
                                category.id,
                                state.selectedMonthId,
                                enabled,
                                state.encryptedPassword.takeIf { it.isNotBlank() },
                            )
                        }
                    }
                }
                if (isLiveBudget && !isTrackingBudget) {
                    DividerLine()
                    ListRow(
                        "Move money",
                        leading = RowLeading.Icon(Lucide.WalletCards),
                        onClick = {
                            moveMoneyFromId = category.id
                            moveMoneyToId =
                                moveMoneyCategories
                                    .firstOrNull { it.second.id != category.id }
                                    ?.second
                                    ?.id
                                    .orEmpty()
                            moveMoneyAmountDraft = ""
                            mobileSession.clearBudgetActionMessage()
                            showMoveMoneyDialog = true
                        },
                    )
                }
                DividerLine()
                ListRow(
                    "Icon",
                    state.categoryIconChoice(category.id) ?: "Automatic",
                    leading = RowLeading.Icon(state.categoryIconFor(category.id, category.name)),
                )
                if (!isTrackingBudget) {
                    DividerLine()
                    ListRow("Available", "Assigned plus activity", state.formatCurrency(category.balanceMinor))
                }
            }
            if (isLiveBudget) {
                SecondaryButton(
                    if (state.isCategoryPinned(category.id)) "Unpin" else "Pin",
                ) { state.togglePinnedCategory(category.id) }
            }
        }
    }
    TandemTaskSheet(
        visible = showAssignedAmountDialog && category != null && isLiveBudget && !isTrackingBudget,
        onDismissRequest = { if (!mobileSession.isPerformingBudgetAction) showAssignedAmountDialog = false },
        onDismissed = {},
    ) {
        if (category != null) {
            val parsedAmount = state.parseAmountMinorUnits(assignedAmountDraft)
            val canSaveAssignedAmount =
                parsedAmount != null && parsedAmount >= 0 && parsedAmount != category.assignedMinor
            SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 460.dp).padding(horizontal = 16.dp)) {
                Column(
                    Modifier.fillMaxWidth().padding(TandemSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        Icon(Lucide.WalletCards, contentDescription = null, modifier = Modifier.size(24.dp), tint = tandemPalette.accent)
                        Text(
                            "Assigned amount",
                            modifier = Modifier.weight(1f),
                            color = tandemPalette.text,
                            fontSize = TandemTypography.sectionTitle,
                            fontFamily = tandemAlloFamily(),
                            fontWeight = FontWeight.Bold,
                        )
                        TandemIconButton(
                            Lucide.X,
                            "Close",
                            sizeDp = 40,
                            iconSizeDp = 18,
                            enabled = !mobileSession.isPerformingBudgetAction,
                        ) { showAssignedAmountDialog = false }
                    }
                    Text("${category.name} · ${state.selectedMonth}", color = tandemPalette.muted, fontSize = TandemTypography.caption)
                    MoneyAmountInput(
                        label = "Amount",
                        value = assignedAmountDraft,
                        placeholder = state.formatAmount(0),
                        onValueChange = { assignedAmountDraft = it },
                        fractionDigits = state.budgetDisplaySettings?.currencyFractionDigits ?: 2,
                    )
                    budgetActionFeedbackMessage(mobileSession)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        TandemActionButton(
                            "Cancel",
                            TandemActionButtonTone.NEUTRAL,
                            Modifier.weight(1f),
                            enabled = !mobileSession.isPerformingBudgetAction,
                        ) { showAssignedAmountDialog = false }
                        TandemActionButton(
                            if (mobileSession.isPerformingBudgetAction) "Saving…" else "Save",
                            TandemActionButtonTone.PRIMARY,
                            Modifier.weight(1f),
                            icon = Lucide.Check,
                            enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction && canSaveAssignedAmount,
                            loading = mobileSession.isPerformingBudgetAction,
                        ) {
                            scope.launch {
                                val saved =
                                    mobileSession.setBudgetCategoryAmount(
                                        state.selectedBudgetId,
                                        category.id,
                                        state.selectedMonthId,
                                        parsedAmount ?: 0L,
                                        state.encryptedPassword.takeIf { it.isNotBlank() },
                                    )
                                if (saved) showAssignedAmountDialog = false
                            }
                        }
                    }
                }
            }
        }
    }
    TandemTaskSheet(
        visible = showMoveMoneyDialog && category != null && isLiveBudget && !isTrackingBudget,
        onDismissRequest = { if (!mobileSession.isPerformingBudgetAction) showMoveMoneyDialog = false },
        onDismissed = {},
    ) {
        if (category != null) {
            val parsedMoveAmount = state.parseAmountMinorUnits(moveMoneyAmountDraft)
            val fromLabel = moveMoneyCategories.firstOrNull { it.second.id == moveMoneyFromId }?.second?.name ?: "Choose category"
            val toLabel = moveMoneyCategories.firstOrNull { it.second.id == moveMoneyToId }?.second?.name ?: "Choose category"
            SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 480.dp).padding(horizontal = 16.dp)) {
                Column(
                    Modifier.fillMaxWidth().padding(TandemSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        Icon(Lucide.WalletCards, contentDescription = null, modifier = Modifier.size(24.dp), tint = tandemPalette.accent)
                        Text(
                            "Move money",
                            modifier = Modifier.weight(1f),
                            color = tandemPalette.text,
                            fontSize = TandemTypography.sectionTitle,
                            fontFamily = tandemAlloFamily(),
                            fontWeight = FontWeight.Bold,
                        )
                        TandemIconButton(
                            Lucide.X,
                            "Close",
                            sizeDp = 40,
                            iconSizeDp = 18,
                            enabled = !mobileSession.isPerformingBudgetAction,
                        ) { showMoveMoneyDialog = false }
                    }
                    Box {
                        ListRow("From", trailing = fromLabel, leading = RowLeading.Icon(Lucide.Minus), onClick = {
                            moveMoneyFromExpanded =
                                true
                        })
                        DropdownMenu(expanded = moveMoneyFromExpanded, onDismissRequest = { moveMoneyFromExpanded = false }) {
                            moveMoneyCategories.forEach { (_, candidate) ->
                                DropdownMenuItem(onClick = {
                                    moveMoneyFromId = candidate.id
                                    moveMoneyFromExpanded = false
                                }) {
                                    Text(candidate.name, color = tandemPalette.text, fontSize = TandemTypography.compactBody)
                                }
                            }
                        }
                    }
                    Box {
                        ListRow("To", trailing = toLabel, leading = RowLeading.Icon(Lucide.Plus), onClick = { moveMoneyToExpanded = true })
                        DropdownMenu(expanded = moveMoneyToExpanded, onDismissRequest = { moveMoneyToExpanded = false }) {
                            moveMoneyCategories.filter { it.second.id != moveMoneyFromId }.forEach { (_, candidate) ->
                                DropdownMenuItem(onClick = {
                                    moveMoneyToId = candidate.id
                                    moveMoneyToExpanded = false
                                }) {
                                    Text(candidate.name, color = tandemPalette.text, fontSize = TandemTypography.compactBody)
                                }
                            }
                        }
                    }
                    MoneyAmountInput(
                        label = "Amount",
                        value = moveMoneyAmountDraft,
                        placeholder = state.formatAmount(0),
                        onValueChange = { moveMoneyAmountDraft = it },
                        fractionDigits = state.budgetDisplaySettings?.currencyFractionDigits ?: 2,
                    )
                    budgetActionFeedbackMessage(mobileSession)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        TandemActionButton(
                            "Cancel",
                            TandemActionButtonTone.NEUTRAL,
                            Modifier.weight(1f),
                            enabled = !mobileSession.isPerformingBudgetAction,
                        ) { showMoveMoneyDialog = false }
                        TandemActionButton(
                            if (mobileSession.isPerformingBudgetAction) "Moving…" else "Move money",
                            TandemActionButtonTone.PRIMARY,
                            Modifier.weight(1f),
                            icon = Lucide.Check,
                            enabled =
                                !state.readOnlyMode &&
                                    !mobileSession.isPerformingBudgetAction &&
                                    parsedMoveAmount != null &&
                                    parsedMoveAmount > 0 &&
                                    moveMoneyFromId.isNotBlank() &&
                                    moveMoneyToId.isNotBlank() &&
                                    moveMoneyFromId != moveMoneyToId,
                            loading = mobileSession.isPerformingBudgetAction,
                        ) {
                            scope.launch {
                                val moved =
                                    mobileSession.moveBudgetMoney(
                                        state.selectedBudgetId,
                                        state.selectedMonthId,
                                        moveMoneyFromId,
                                        moveMoneyToId,
                                        parsedMoveAmount ?: 0L,
                                        state.encryptedPassword.takeIf { it.isNotBlank() },
                                    )
                                if (moved) showMoveMoneyDialog = false
                            }
                        }
                    }
                }
            }
        }
    }
    TandemTaskSheet(
        visible = showEditDialog && category != null,
        onDismissRequest = { if (!mobileSession.isPerformingBudgetAction) showEditDialog = false },
        onDismissed = {},
    ) {
        if (category != null) {
            SurfaceCard(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .padding(horizontal = 16.dp)
                    .heightIn(max = 720.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(TandemSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        Box(
                            Modifier.size(42.dp).clip(CircleShape).background(tandemPalette.accentSurface),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Lucide.Pencil, contentDescription = null, modifier = Modifier.size(20.dp), tint = tandemPalette.accent)
                        }
                        Text(
                            "Edit category",
                            modifier = Modifier.weight(1f),
                            color = tandemPalette.text,
                            fontSize = TandemTypography.sectionTitle,
                            fontFamily = tandemAlloFamily(),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TandemIconButton(
                            Lucide.X,
                            "Close",
                            sizeDp = 40,
                            iconSizeDp = 18,
                            containerColor = tandemPalette.raised,
                            enabled = !mobileSession.isPerformingBudgetAction,
                        ) { showEditDialog = false }
                    }
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                    ) {
                        TextFieldCard("Name", editName, { editName = it }, TextFieldOptions(placeholder = category.name))
                        val destinationGroups =
                            liveBudgetMonth.groups.filter { candidateGroup ->
                                candidateGroup.isIncome == currentIncomeType
                            }
                        TransactionPickerRow(
                            value = destinationGroups.firstOrNull { it.id == editCategoryGroupId }?.name.orEmpty(),
                            placeholder = "Category group",
                            icon = Lucide.Tags,
                        ) { categoryGroupMenuExpanded = true }
                        DropdownMenu(
                            expanded = categoryGroupMenuExpanded,
                            onDismissRequest = { categoryGroupMenuExpanded = false },
                        ) {
                            destinationGroups.forEach { candidateGroup ->
                                DropdownMenuItem(
                                    onClick = {
                                        editCategoryGroupId = candidateGroup.id
                                        categoryGroupMenuExpanded = false
                                    },
                                ) {
                                    Text(candidateGroup.name, color = tandemPalette.text, fontSize = TandemTypography.compactBody)
                                }
                            }
                        }
                        LabelText("Choose an icon")
                        TextFieldCard(
                            "Find an icon",
                            iconSearch,
                            { iconSearch = it },
                            TextFieldOptions(placeholder = "Search by name"),
                        )
                        val visibleIconChoices = categoryIconChoices.filter { it.contains(iconSearch.trim(), ignoreCase = true) }
                        if (visibleIconChoices.isEmpty()) {
                            BodyText("No icons match that search.")
                        }
                        visibleIconChoices.chunked(4).forEach { rowChoices ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                                rowChoices.forEach { choice ->
                                    val icon = categoryIconVector(choice) ?: financeIcon(category.name) ?: Lucide.Receipt
                                    SelectableTile(
                                        selected = selectedIconChoice == choice,
                                        contentDescription = if (choice == "Automatic") "Automatic icon" else choice,
                                        onSelect = { selectedIconChoice = choice },
                                        modifier = Modifier.weight(1f).heightIn(min = 66.dp),
                                        shape = RoundedCornerShape(TandemShapes.control),
                                        selectedBackground = tandemPalette.accentSurface,
                                        unselectedBackground = tandemPalette.surface,
                                        selectedBorder = tandemPalette.accent,
                                        unselectedBorder = tandemPalette.border,
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall),
                                        ) {
                                            Icon(
                                                icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(23.dp),
                                                tint = tandemPalette.accent,
                                            )
                                            Text(
                                                if (choice == "Automatic") "Auto" else choice,
                                                color = tandemPalette.text,
                                                fontSize = TandemTypography.micro,
                                                maxLines = 2,
                                            )
                                        }
                                    }
                                }
                                repeat(4 - rowChoices.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                        budgetActionFeedbackMessage(mobileSession)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        TandemActionButton(
                            "Delete",
                            TandemActionButtonTone.DESTRUCTIVE,
                            modifier = Modifier.weight(1f),
                            icon = Lucide.Trash2,
                            enabled = !state.readOnlyMode,
                        ) {
                            showEditDialog = false
                            deleteDestinationId = null
                            mobileSession.clearBudgetActionMessage()
                            showDeleteDialog = true
                        }
                        TandemActionButton(
                            "Cancel",
                            TandemActionButtonTone.NEUTRAL,
                            modifier = Modifier.weight(1f),
                            enabled = true,
                        ) { showEditDialog = false }
                        TandemActionButton(
                            "Save",
                            TandemActionButtonTone.PRIMARY,
                            modifier = Modifier.weight(1f),
                            enabled = !state.readOnlyMode && editName.isNotBlank(),
                        ) {
                            scope.launch {
                                var saved = true
                                val currentGroupId = selectedGroup?.id ?: state.selectedCategoryGroupId
                                if (editCategoryGroupId != currentGroupId) {
                                    saved =
                                        mobileSession.enqueueBudgetCategoryMoveToGroup(
                                            state.selectedBudgetId,
                                            category.id,
                                            editCategoryGroupId,
                                            state.encryptedPassword.takeIf { it.isNotBlank() },
                                        )
                                    if (saved) state.selectedCategoryGroupId = editCategoryGroupId
                                }
                                if (saved && editName.trim() != category.name) {
                                    saved =
                                        mobileSession.enqueueBudgetCategoryRename(
                                            state.selectedBudgetId,
                                            category.id,
                                            editName,
                                            state.encryptedPassword.takeIf { it.isNotBlank() },
                                        )
                                }
                                if (saved) {
                                    state.setCategoryIconChoice(category.id, selectedIconChoice)
                                    showEditDialog = false
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    TandemTaskSheet(
        visible = showDeleteDialog && category != null,
        onDismissRequest = { if (!mobileSession.isPerformingBudgetAction) showDeleteDialog = false },
        onDismissed = {},
    ) {
        if (category != null) {
            SurfaceCard(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .padding(horizontal = 16.dp)
                    .heightIn(max = 720.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(TandemSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        Box(
                            Modifier.size(42.dp).clip(CircleShape).background(tandemPalette.negative.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Lucide.Trash2, contentDescription = null, modifier = Modifier.size(20.dp), tint = tandemPalette.negative)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Delete category",
                                color = tandemPalette.text,
                                fontSize = TandemTypography.sectionTitle,
                                fontFamily = tandemAlloFamily(),
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                category.name,
                                color = tandemPalette.muted,
                                fontSize = TandemTypography.caption,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TandemIconButton(
                            Lucide.X,
                            "Close",
                            sizeDp = 40,
                            iconSizeDp = 18,
                            containerColor = tandemPalette.raised,
                            enabled = !mobileSession.isPerformingBudgetAction,
                        ) { showDeleteDialog = false }
                    }
                    LabelText("Move transactions to")
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                    ) {
                        val destinations: List<Pair<String?, String>> =
                            listOf(null to "Leave unassigned") + deleteDestinations.map { it.id to it.name }
                        destinations.forEach { (destinationId, destinationName) ->
                            val selected = deleteDestinationId == destinationId
                            SelectableTile(
                                selected = selected,
                                contentDescription = "Move to $destinationName",
                                onSelect = { deleteDestinationId = destinationId },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(TandemShapes.control),
                                selectedBackground = tandemPalette.accentSurface,
                                unselectedBackground = tandemPalette.surface,
                                selectedBorder = tandemPalette.accent,
                                unselectedBorder = tandemPalette.border,
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.medium),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                                ) {
                                    Icon(
                                        if (destinationId ==
                                            null
                                        ) {
                                            Lucide.CircleDot
                                        } else {
                                            state.categoryIconFor(destinationId, destinationName)
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(21.dp),
                                        tint = tandemPalette.accent,
                                    )
                                    Text(
                                        destinationName,
                                        modifier = Modifier.weight(1f),
                                        color = tandemPalette.text,
                                        fontSize = TandemTypography.body,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (selected) {
                                        Icon(
                                            Lucide.Check,
                                            contentDescription = "Selected",
                                            modifier = Modifier.size(18.dp),
                                            tint = tandemPalette.accent,
                                        )
                                    }
                                }
                            }
                        }
                        budgetActionFeedbackMessage(mobileSession)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        TandemActionButton(
                            "Cancel",
                            TandemActionButtonTone.NEUTRAL,
                            modifier = Modifier.weight(1f),
                            enabled = !mobileSession.isPerformingBudgetAction,
                        ) { showDeleteDialog = false }
                        TandemActionButton(
                            "Continue",
                            TandemActionButtonTone.PRIMARY,
                            modifier = Modifier.weight(1f),
                            enabled = !state.readOnlyMode,
                        ) {
                            confirmCategoryDelete = true
                        }
                    }
                }
            }
        }
    }
    if (confirmCategoryDelete && category != null) {
        DestructiveConfirmationDialog(
            title = "Delete ${category.name}?",
            message =
                "Transactions will be moved to ${deleteDestinationId?.let { id ->
                    deleteDestinations.firstOrNull { it.id == id }?.name
                } ?: "Unassigned"}.",
            confirmLabel = "Delete",
            dismissLabel = "Keep category",
            enabled = !state.readOnlyMode,
            onDismissRequest = { confirmCategoryDelete = false },
            onConfirm = {
                scope.launch {
                    val deleted =
                        mobileSession.enqueueBudgetCategoryDelete(
                            state.selectedBudgetId,
                            category.id,
                            deleteDestinationId,
                            state.encryptedPassword.takeIf { it.isNotBlank() },
                        )
                    confirmCategoryDelete = false
                    if (deleted) {
                        state.setCategoryIconChoice(category.id, "Automatic")
                        showDeleteDialog = false
                        navigation.back()
                    }
                }
            },
        )
    }
}

internal fun openCategoryActivity(
    state: AppState,
    navigation: AppNavigation,
    categoryId: String,
    categoryName: String,
) {
    state.activitySearchQuery = ""
    state.activitySearchFocusRequest += 1
    state.activitySearchFilters =
        listOf(
            ActivitySearchFilter(
                ActivitySearchField.CATEGORY,
                if (state.selectedBudgetId.isBlank()) categoryName else categoryId.ifBlank { categoryName },
                "Category: $categoryName",
            ),
            ActivitySearchFilter(ActivitySearchField.DATE_MONTH, state.selectedMonthId, "Date: ${state.selectedMonth}"),
        )
    navigation.open(AppRoute.ACTIVITY_SEARCH)
}

internal fun openGeneralActivitySearch(
    state: AppState,
    navigation: AppNavigation,
) {
    state.activitySearchQuery = ""
    state.activitySearchFocusRequest += 1
    state.activitySearchFilters = emptyList()
    navigation.open(AppRoute.ACTIVITY_SEARCH)
}

internal fun openRecentActivity(
    state: AppState,
    navigation: AppNavigation,
) {
    state.activitySearchQuery = ""
    state.activitySearchFilters = emptyList()
    state.activitySearchResultCache = null
    navigation.selectTabRoot(AppRoute.ACTIVITY)
}

internal fun openAccountActivity(
    state: AppState,
    navigation: AppNavigation,
    accountId: String,
    accountName: String,
) {
    state.activitySearchQuery = ""
    state.activitySearchResultCache = null
    state.activitySearchFilters =
        listOf(ActivitySearchFilter(ActivitySearchField.ACCOUNT, accountId.ifBlank { accountName }, "Account: $accountName"))
    navigation.open(AppRoute.ACTIVITY_SEARCH)
}

internal fun openReportCategoryActivity(
    state: AppState,
    navigation: AppNavigation,
    categoryId: String,
    categoryName: String,
    window: ReportWindow,
) {
    state.activitySearchQuery = ""
    state.activitySearchFocusRequest += 1
    val dateFilter =
        when {
            window.allDates ->
                ActivitySearchFilter(
                    ActivitySearchField.DATE_RANGE,
                    "${window.startDate}..${window.throughDate}",
                    "Date: All dates",
                )
            window.monthCount == 1 ->
                ActivitySearchFilter(
                    ActivitySearchField.DATE_MONTH,
                    window.endMonthId,
                    "Date: ${reportMonthLabel(window.endMonthId)}",
                )
            else ->
                ActivitySearchFilter(
                    ActivitySearchField.DATE_RANGE,
                    "${window.startDate}..${window.throughDate}",
                    "Date: ${reportMonthLabel(window.startMonthId)} – ${reportMonthLabel(window.endMonthId)}",
                )
        }
    state.activitySearchFilters =
        listOf(
            ActivitySearchFilter(
                ActivitySearchField.CATEGORY,
                if (state.selectedBudgetId.isBlank()) categoryName else categoryId.ifBlank { categoryName },
                "Category: $categoryName",
            ),
            dateFilter,
        )
    navigation.open(AppRoute.ACTIVITY_SEARCH)
}

@Composable
private fun CategoryInsightsSection(
    state: AppState,
    mobileSession: TandemMobileSession,
    category: LiveCategorySummary,
    isTrackingBudget: Boolean,
) {
    val today = remember { platformTodayIsoDate() }
    val monthId = state.selectedMonthId
    val isCurrentMonth = monthId == today.take(7)
    val pace =
        if (isTrackingBudget || !isCurrentMonth) {
            null
        } else {
            categoryPace(
                category.assignedMinor,
                category.activityMinor,
                today.drop(8).take(2).toIntOrNull() ?: 0,
                daysInMonth(monthId) ?: 0,
            )
        }
    var lastMonthSpent by remember(state.selectedBudgetId, monthId, category.id) { mutableStateOf<Long?>(null) }
    LaunchedEffect(state.selectedBudgetId, monthId, category.id) {
        lastMonthSpent =
            previousMonthId(monthId)?.let { previous ->
                categorySpendingInMonth(mobileSession.budgetMonthBody(state.selectedBudgetId, previous), category.id)
            }
    }
    val lastMonth = lastMonthSpent
    if (pace == null && lastMonth == null) return
    SectionCard("Insights") {
        pace?.let {
            ListRow(
                it.status.title,
                categoryPaceDetail(it, state::formatCurrency),
                leading = RowLeading.Icon(if (it.status == CategoryPaceStatus.ON_TRACK) Lucide.Gauge else Lucide.TriangleAlert),
            )
        }
        if (pace != null && lastMonth != null) DividerLine()
        lastMonth?.let {
            ListRow(
                "Last month",
                lastMonthSpendingDetail(it, (-category.activityMinor).coerceAtLeast(0), monthId < today.take(7), state::formatCurrency),
                leading = RowLeading.Icon(Lucide.History),
            )
        }
    }
}
