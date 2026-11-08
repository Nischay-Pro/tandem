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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.tandemforactual.ui.components.BodyText
import app.tandemforactual.ui.components.DividerLine
import app.tandemforactual.ui.components.InlineMessage
import app.tandemforactual.ui.components.InlineMessageKind
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
import app.tandemforactual.ui.model.LiveBudgetMonthSummary
import app.tandemforactual.ui.model.booleanValue
import app.tandemforactual.ui.model.recordBody
import app.tandemforactual.ui.reports.ReportKind
import app.tandemforactual.ui.reports.ReportWindow
import app.tandemforactual.ui.reports.reportMonthLabel
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.CircleDot
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minus
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Receipt
import com.composables.icons.lucide.Tags
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.WalletCards
import com.composables.icons.lucide.X
import kotlinx.coroutines.flow.first
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
    val isTrackingBudget = isLiveBudget && state.budgetDisplaySettings?.budgetType == "tracking"
    val category =
        if (isLiveBudget) {
            liveBudgetMonth
                ?.groups
                ?.asSequence()
                ?.flatMap { it.categories.asSequence() }
                ?.firstOrNull { it.id == state.selectedCategoryId }
        } else {
            previewCategoryGroups
                .firstOrNull { it.id == state.selectedCategoryGroupId }
                ?.categories
                ?.firstOrNull { it.id == state.selectedCategoryId }
                ?: previewCategoryGroups.first().categories.first()
        }
    var showEditDialog by rememberSaveable(state.selectedBudgetId, state.selectedCategoryId) { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable(state.selectedBudgetId, state.selectedCategoryId) { mutableStateOf(false) }
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
        if (!isLiveBudget) SampleBanner()
        if (category == null) {
            Heading("Category details", state.selectedMonth)
            BodyText("This category isn’t in the saved plan for this month.")
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                Column(Modifier.weight(1f)) { Heading(category.name, state.selectedMonth) }
                if (isLiveBudget) {
                    TandemIconButton(
                        Lucide.Pencil,
                        "Edit category",
                        enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
                        tint = tandemPalette.accent,
                    ) { openCategoryEditor() }
                }
            }
            AmountHero(
                if (isTrackingBudget) "Activity" else "Available",
                state.formatCurrency(if (isTrackingBudget) category.activityMinor else category.balanceMinor),
                if (isTrackingBudget) "For the selected month" else "${state.formatCurrency(category.assignedMinor)} assigned",
            )
            SectionCard("This month") {
                if (!isTrackingBudget) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .clickable(
                                enabled = isLiveBudget && !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
                                role = Role.Button,
                                onClickLabel = "Edit assigned amount",
                                onClick = {
                                    assignedAmountDraft = state.formatAmount(category.assignedMinor)
                                    mobileSession.clearBudgetActionMessage()
                                    showAssignedAmountDialog = true
                                },
                            ).padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.small),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                    ) {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(TandemShapes.medium)).background(tandemPalette.raised),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Lucide.WalletCards,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = tandemPalette.accent,
                            )
                        }
                        Text("Assigned", modifier = Modifier.weight(1f), color = tandemPalette.text, fontSize = TandemTypography.body)
                        Text(
                            state.formatCurrency(category.assignedMinor),
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
            if (isLiveBudget && selectedGroup != null && categoryIndex >= 0) {
                SectionCard("Category options") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        TandemIconButton(
                            Lucide.ChevronUp,
                            "Move category up",
                            enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction && categoryIndex > 0,
                        ) {
                            scope.launch {
                                mobileSession.manageBudgetCategory(
                                    state.selectedBudgetId,
                                    state.selectedCategoryId,
                                    "move",
                                    selectedGroup.categories.getOrNull(categoryIndex - 1)?.id,
                                    state.encryptedPassword.takeIf { it.isNotBlank() },
                                )
                            }
                        }
                        TandemIconButton(
                            Lucide.ChevronDown,
                            "Move category down",
                            enabled =
                                !state.readOnlyMode &&
                                    !mobileSession.isPerformingBudgetAction &&
                                    categoryIndex < selectedGroup.categories.lastIndex,
                        ) {
                            scope.launch {
                                mobileSession.manageBudgetCategory(
                                    state.selectedBudgetId,
                                    state.selectedCategoryId,
                                    "move",
                                    selectedGroup.categories.getOrNull(categoryIndex + 2)?.id,
                                    state.encryptedPassword.takeIf { it.isNotBlank() },
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        TandemIconButton(
                            Lucide.EyeOff,
                            "Hide category",
                            enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
                            tint = tandemPalette.negative,
                        ) {
                            scope.launch {
                                if (mobileSession.manageBudgetCategory(
                                        state.selectedBudgetId,
                                        state.selectedCategoryId,
                                        "hide",
                                        null,
                                        state.encryptedPassword.takeIf { it.isNotBlank() },
                                    )
                                ) {
                                    navigation.back()
                                }
                            }
                        }
                    }
                }
            }
            if (isLiveBudget) {
                SecondaryButton(
                    if (state.isCategoryPinned(category.id)) "Unpin" else "Pin",
                ) { state.togglePinnedCategory(category.id) }
            }
        }
    }
    if (showAssignedAmountDialog && category != null && isLiveBudget && !isTrackingBudget) {
        val parsedAmount = state.parseAmountMinorUnits(assignedAmountDraft)
        Dialog(
            onDismissRequest = { if (!mobileSession.isPerformingBudgetAction) showAssignedAmountDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
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
                    TextFieldCard(
                        "Amount",
                        assignedAmountDraft,
                        { assignedAmountDraft = it },
                        TextFieldOptions(
                            placeholder = state.formatAmount(0),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            textAlign = TextAlign.Center,
                            large = true,
                        ),
                    )
                    mobileSession.budgetActionMessage?.let { InlineMessage(it, InlineMessageKind.ERROR) }
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
                            enabled =
                                !state.readOnlyMode && !mobileSession.isPerformingBudgetAction && parsedAmount != null && parsedAmount >= 0,
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
    if (showMoveMoneyDialog && category != null && isLiveBudget && !isTrackingBudget) {
        val parsedMoveAmount = state.parseAmountMinorUnits(moveMoneyAmountDraft)
        val fromLabel = moveMoneyCategories.firstOrNull { it.second.id == moveMoneyFromId }?.second?.name ?: "Choose category"
        val toLabel = moveMoneyCategories.firstOrNull { it.second.id == moveMoneyToId }?.second?.name ?: "Choose category"
        Dialog(
            onDismissRequest = { if (!mobileSession.isPerformingBudgetAction) showMoveMoneyDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
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
                    TextFieldCard(
                        "Amount",
                        moveMoneyAmountDraft,
                        { moveMoneyAmountDraft = it },
                        TextFieldOptions(
                            placeholder = state.formatAmount(0),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            textAlign = TextAlign.Center,
                            large = true,
                        ),
                    )
                    mobileSession.budgetActionMessage?.let { InlineMessage(it, InlineMessageKind.ERROR) }
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
    if (showEditDialog && category != null) {
        Dialog(
            onDismissRequest = { if (!mobileSession.isPerformingBudgetAction) showEditDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
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
                            liveBudgetMonth?.groups.orEmpty().filter { candidateGroup ->
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
                        mobileSession.budgetActionMessage?.let { InlineMessage(it, InlineMessageKind.ERROR) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        TandemActionButton(
                            "Delete",
                            TandemActionButtonTone.DESTRUCTIVE,
                            modifier = Modifier.weight(1f),
                            icon = Lucide.Trash2,
                            enabled = !mobileSession.isPerformingBudgetAction,
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
                            enabled = !mobileSession.isPerformingBudgetAction,
                        ) { showEditDialog = false }
                        TandemActionButton(
                            if (mobileSession.isPerformingBudgetAction) "Saving…" else "Save",
                            TandemActionButtonTone.PRIMARY,
                            modifier = Modifier.weight(1f),
                            enabled = !mobileSession.isPerformingBudgetAction && editName.isNotBlank(),
                            loading = mobileSession.isPerformingBudgetAction,
                        ) {
                            scope.launch {
                                var saved = true
                                val currentGroupId = selectedGroup?.id ?: state.selectedCategoryGroupId
                                if (editCategoryGroupId != currentGroupId) {
                                    saved =
                                        mobileSession.manageBudgetCategory(
                                            state.selectedBudgetId,
                                            category.id,
                                            "move",
                                            null,
                                            state.encryptedPassword.takeIf { it.isNotBlank() },
                                            editCategoryGroupId,
                                        )
                                    if (saved) state.selectedCategoryGroupId = editCategoryGroupId
                                }
                                if (saved && editName.trim() != category.name) {
                                    saved =
                                        mobileSession.renameBudgetCategory(
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
    if (showDeleteDialog && category != null) {
        Dialog(
            onDismissRequest = { if (!mobileSession.isPerformingBudgetAction) showDeleteDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
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
                        mobileSession.budgetActionMessage?.let { InlineMessage(it, InlineMessageKind.ERROR) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        TandemActionButton(
                            "Cancel",
                            TandemActionButtonTone.NEUTRAL,
                            modifier = Modifier.weight(1f),
                            enabled = !mobileSession.isPerformingBudgetAction,
                        ) { showDeleteDialog = false }
                        TandemActionButton(
                            if (mobileSession.isPerformingBudgetAction) "Deleting…" else "Delete",
                            TandemActionButtonTone.DESTRUCTIVE,
                            modifier = Modifier.weight(1f),
                            icon = Lucide.Trash2,
                            enabled = !mobileSession.isPerformingBudgetAction,
                            loading = mobileSession.isPerformingBudgetAction,
                        ) {
                            scope.launch {
                                val deleted =
                                    mobileSession.deleteBudgetCategory(
                                        state.selectedBudgetId,
                                        category.id,
                                        deleteDestinationId,
                                        state.encryptedPassword.takeIf { it.isNotBlank() },
                                    )
                                if (deleted) {
                                    state.setCategoryIconChoice(category.id, "Automatic")
                                    showDeleteDialog = false
                                    navigation.back()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun openCategoryActivity(
    state: AppState,
    navigation: AppNavigation,
    categoryId: String,
    categoryName: String,
) {
    state.activitySearchQuery = ""
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
    state.activitySearchFilters = emptyList()
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

internal fun reportRoute(kind: ReportKind): AppRoute =
    when (kind) {
        ReportKind.SPENDING_BY_CATEGORY -> AppRoute.REPORT_SPENDING_BY_CATEGORY
        ReportKind.SPENDING_OVER_TIME -> AppRoute.REPORT_SPENDING_OVER_TIME
        ReportKind.INCOME_AND_SPENDING -> AppRoute.REPORT_INCOME_AND_SPENDING
        ReportKind.BUDGETED_AND_SPENT -> AppRoute.REPORT_BUDGETED_AND_SPENT
        ReportKind.NET_WORTH -> AppRoute.REPORT_NET_WORTH
    }

internal fun reportKind(route: AppRoute): ReportKind =
    when (route) {
        AppRoute.REPORT_SPENDING_BY_CATEGORY -> ReportKind.SPENDING_BY_CATEGORY
        AppRoute.REPORT_SPENDING_OVER_TIME -> ReportKind.SPENDING_OVER_TIME
        AppRoute.REPORT_INCOME_AND_SPENDING -> ReportKind.INCOME_AND_SPENDING
        AppRoute.REPORT_BUDGETED_AND_SPENT -> ReportKind.BUDGETED_AND_SPENT
        AppRoute.REPORT_NET_WORTH -> ReportKind.NET_WORTH
        else -> error("Route is not a report detail: $route")
    }
