@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.tandemforactual.ui.components.EmptyState
import app.tandemforactual.ui.components.InlineMessage
import app.tandemforactual.ui.components.InlineMessageKind
import app.tandemforactual.ui.components.ListRow
import app.tandemforactual.ui.components.ListRowOptions
import app.tandemforactual.ui.components.PeriodStepper
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
import app.tandemforactual.ui.model.recordName
import app.tandemforactual.ui.model.stringValue
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
internal fun MonthPickerScreen(
    state: AppState,
    navigation: AppNavigation,
) {
    PageColumn {
        Heading("Choose a month")
        var selectedYear by remember {
            mutableStateOf(state.selectedMonthId.substringBefore('-').toIntOrNull() ?: 2026)
        }
        PeriodStepper(
            title = selectedYear.toString(),
            previousLabel = "Previous year",
            nextLabel = "Next year",
            onPrevious = { selectedYear -= 1 },
            onNext = { selectedYear += 1 },
        )
        val months = (1..12).map { month -> month to formatShortMonth(selectedYear, month) }
        months.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                row.forEach { (monthNumber, monthLabel) ->
                    val full = formatMonth(selectedYear, monthNumber)
                    val chosen = state.selectedMonth == full
                    SelectableTile(
                        selected = chosen,
                        contentDescription = full,
                        onSelect = {
                            state.selectedMonth = full
                            state.selectedMonthId = "$selectedYear-${monthNumber.toString().padStart(2, '0')}"
                            navigation.back()
                        },
                        modifier = Modifier.weight(1f).height(72.dp),
                        selectedBackground = tandemPalette.accentSurface,
                        unselectedBackground = tandemPalette.surface,
                        selectedBorder = tandemPalette.accent,
                        unselectedBorder = tandemPalette.border,
                    ) {
                        Text(
                            monthLabel,
                            color = if (chosen) tandemPalette.accent else tandemPalette.text,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = TandemTypography.body,
                        )
                    }
                }
            }
        }
        PreviewNote(
            if (state.selectedBudgetId.isNotBlank()) {
                "Only months cached from Actual show plan details."
            } else {
                "Months are sample planning periods."
            },
        )
    }
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
    val groupIndex = groups.indexOfFirst { it.id == group?.id }
    var showHidden by rememberSaveable(state.selectedBudgetId) { mutableStateOf(false) }
    var showGroupDialog by rememberSaveable(state.selectedBudgetId, group?.id) { mutableStateOf(false) }
    var showCreateGroupDialog by rememberSaveable(state.selectedBudgetId) { mutableStateOf(false) }
    var groupNameDraft by rememberSaveable(state.selectedBudgetId, group?.id) { mutableStateOf(group?.name.orEmpty()) }
    var newGroupName by rememberSaveable(state.selectedBudgetId) { mutableStateOf("") }
    var newGroupIsIncome by rememberSaveable(state.selectedBudgetId) { mutableStateOf(false) }
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
                Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    Heading(group.name, state.selectedMonth)
                    if (isLiveBudget) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                        ) {
                            TandemIconButton(
                                Lucide.Pencil,
                                "Edit category group",
                                enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
                            ) {
                                groupNameDraft = group.name
                                mobileSession.clearBudgetActionMessage()
                                showGroupDialog = true
                            }
                            TandemIconButton(
                                Lucide.Plus,
                                "Add category group",
                                enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
                            ) {
                                newGroupName = ""
                                newGroupIsIncome = group.isIncome
                                mobileSession.clearBudgetActionMessage()
                                showCreateGroupDialog = true
                            }
                            TandemIconButton(
                                Lucide.EyeOff,
                                "Hide category group",
                                enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
                            ) {
                                scope.launch {
                                    mobileSession.manageBudgetCategoryGroup(
                                        state.selectedBudgetId,
                                        group.id,
                                        "hide",
                                        null,
                                        null,
                                        state.encryptedPassword.takeIf { it.isNotBlank() },
                                    )
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            TandemIconButton(
                                Lucide.ChevronUp,
                                "Move category group up",
                                enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction && groupIndex > 0,
                            ) {
                                scope.launch {
                                    mobileSession.manageBudgetCategoryGroup(
                                        state.selectedBudgetId,
                                        group.id,
                                        "move",
                                        null,
                                        groups.getOrNull(groupIndex - 1)?.id,
                                        state.encryptedPassword.takeIf { it.isNotBlank() },
                                    )
                                }
                            }
                            TandemIconButton(
                                Lucide.ChevronDown,
                                "Move category group down",
                                enabled =
                                    !state.readOnlyMode &&
                                        !mobileSession.isPerformingBudgetAction &&
                                        groupIndex >= 0 &&
                                        groupIndex < groups.lastIndex,
                            ) {
                                val targetId = groups.getOrNull(groupIndex + 2)?.id
                                scope.launch {
                                    mobileSession.manageBudgetCategoryGroup(
                                        state.selectedBudgetId,
                                        group.id,
                                        "move",
                                        null,
                                        targetId,
                                        state.encryptedPassword.takeIf { it.isNotBlank() },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item(key = "category-group-summary:${group.id}") {
                AmountHero(
                    if (isTrackingBudget) "Activity this month" else "Assigned this month",
                    state.formatCurrency(if (isTrackingBudget) group.activityMinor else group.assignedMinor),
                    if (isTrackingBudget) {
                        "Across ${group.categories.size} categories"
                    } else {
                        "${state.formatCurrency(group.balanceMinor)} available across ${group.categories.size} categories"
                    },
                )
            }
            item(key = "category-group-list-title:${group.id}") {
                SectionCard("Categories", "${group.categories.size} categories") {}
            }
            items(group.categories, key = { "category-group:${group.id}:${it.id}" }) { category ->
                SurfaceCard(modifier = tandemListItemModifier(tandemReduceMotion)) {
                    ListRow(
                        category.name,
                        if (isTrackingBudget) {
                            "Activity ${state.formatCurrency(category.activityMinor)}"
                        } else {
                            "Spent ${state.formatCurrency(
                                kotlin.math.abs(category.activityMinor),
                            )} · ${state.formatCurrency(category.balanceMinor)} available"
                        },
                        if (isTrackingBudget) null else state.formatCurrency(category.assignedMinor),
                        leading = RowLeading.Icon(state.categoryIconFor(category.id, category.name)),
                        options = ListRowOptions(trailingColor = if (category.balanceMinor >= 0) palette.positive else palette.warning),
                    ) {
                        state.selectedCategoryGroupId = group.id
                        state.selectedCategoryId = category.id
                        navigation.open(AppRoute.CATEGORY_DETAIL)
                    }
                }
            }
            if (isLiveBudget) {
                item(key = "category-group-hidden-toggle:${group.id}") {
                    SecondaryButton(if (showHidden) "Hide hidden items" else "Show hidden items") {
                        showHidden = !showHidden
                    }
                }
                if (showHidden) {
                    items(hiddenCategoryRecords, key = { "hidden-category:${it.recordId}" }) { hidden ->
                        SurfaceCard {
                            ListRow(
                                hidden.recordName() ?: "Hidden category",
                                "Hidden",
                                leading = RowLeading.Icon(Lucide.EyeOff),
                                trailing = "Show",
                                onClick = {
                                    scope.launch {
                                        mobileSession.manageBudgetCategory(
                                            state.selectedBudgetId,
                                            hidden.recordId,
                                            "show",
                                            null,
                                            state.encryptedPassword.takeIf { it.isNotBlank() },
                                        )
                                    }
                                },
                            )
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
    if (showGroupDialog && isLiveBudget && group != null) {
        CategoryGroupNameDialog(
            title = "Edit category group",
            name = groupNameDraft,
            onNameChange = { groupNameDraft = it },
            busy = mobileSession.isPerformingBudgetAction,
            message = mobileSession.budgetActionMessage,
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
    if (showCreateGroupDialog && isLiveBudget) {
        CategoryGroupNameDialog(
            title = "Add category group",
            name = newGroupName,
            onNameChange = { newGroupName = it },
            busy = mobileSession.isPerformingBudgetAction,
            message = mobileSession.budgetActionMessage,
            isIncome = newGroupIsIncome,
            onIncomeChange = { newGroupIsIncome = it },
            onDismiss = { showCreateGroupDialog = false },
            onSave = {
                scope.launch {
                    if (mobileSession.createBudgetCategoryGroup(
                            state.selectedBudgetId,
                            newGroupName,
                            newGroupIsIncome,
                            state.encryptedPassword.takeIf { it.isNotBlank() },
                        ) != null
                    ) {
                        showCreateGroupDialog = false
                    }
                }
            },
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
    isIncome: Boolean? = null,
    onIncomeChange: ((Boolean) -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 440.dp).padding(horizontal = 16.dp)) {
            Column(Modifier.fillMaxWidth().padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                Text(title, color = tandemPalette.text, fontSize = TandemTypography.sectionTitle, fontWeight = FontWeight.Bold)
                TextFieldCard("Name", name, onNameChange, TextFieldOptions(placeholder = "Essentials"))
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
