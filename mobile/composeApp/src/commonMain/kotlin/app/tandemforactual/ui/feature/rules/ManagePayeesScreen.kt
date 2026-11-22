@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.rules
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.createBudgetPayee
import app.tandemforactual.ui.core.session.budget.manageBudgetPayee
import app.tandemforactual.ui.core.session.budgetActionFeedbackMessage
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.MerchantIconMatcherPack
import app.tandemforactual.ui.core.state.booleanValue
import app.tandemforactual.ui.core.state.defaultCategoryRulesByPayee
import app.tandemforactual.ui.core.state.recordBody
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.core.state.stringValue
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowAction
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.sheets.DestructiveConfirmationDialog
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.templates.PageLazyColumn
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemAlloFamily
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.feature.activity.ActivitySearchField
import app.tandemforactual.ui.feature.activity.ActivitySearchFilter
import app.tandemforactual.ui.feature.activity.ActivitySearchInput
import app.tandemforactual.ui.feature.schedules.ScheduleChoiceField
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Tags
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.X
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
internal fun ManagePayeesScreen(
    state: AppState,
    navigation: AppNavigation,
    mobileSession: TandemMobileSession,
    payeeRecords: List<BudgetRecordEntity>,
    categoryRecords: List<BudgetRecordEntity>,
    ruleRecords: List<BudgetRecordEntity>,
    merchantIconMatcher: MerchantIconMatcherPack?,
) {
    var selectedPayeeId by rememberSaveable(state.selectedBudgetId) { mutableStateOf<String?>(null) }
    var mode by rememberSaveable(state.selectedBudgetId) { mutableStateOf("menu") }
    var searchQuery by rememberSaveable(state.selectedBudgetId) { mutableStateOf("") }
    var showAddPayee by rememberSaveable(state.selectedBudgetId) { mutableStateOf(false) }
    var selectedPayeeSheetVisible by rememberSaveable(state.selectedBudgetId) { mutableStateOf(false) }
    var newPayeeName by rememberSaveable(state.selectedBudgetId) { mutableStateOf("") }
    var nameDraft by rememberSaveable(state.selectedBudgetId, selectedPayeeId) { mutableStateOf("") }
    var mergeTargetId by rememberSaveable(state.selectedBudgetId, selectedPayeeId) { mutableStateOf<String?>(null) }
    var defaultCategoryDraft by rememberSaveable(state.selectedBudgetId, selectedPayeeId) { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val payees =
        remember(payeeRecords) {
            payeeRecords
                .filterNot { record ->
                    val body = record.recordBody()
                    record.localDeleted ||
                        record.remoteDeleted ||
                        body?.booleanValue("tombstone") == true ||
                        body?.stringValue("transfer_acct") != null
                }.mapNotNull { record -> record.recordName()?.let { record.recordId to it } }
                .distinctBy { it.first }
                .sortedBy { it.second.lowercase() }
        }
    val defaultCategories = remember(ruleRecords) { defaultCategoryRulesByPayee(ruleRecords) }
    val categoryOptions =
        remember(categoryRecords) {
            listOf("" to "No default") +
                categoryRecords
                    .filterNot { record ->
                        record.localDeleted ||
                            record.remoteDeleted ||
                            record.recordBody()?.booleanValue("tombstone") == true ||
                            record.recordBody()?.booleanValue("hidden") == true
                    }.mapNotNull { record -> record.recordName()?.let { record.recordId to it } }
                    .sortedBy { it.second.lowercase() }
        }
    val selectedPayee = payees.firstOrNull { it.first == selectedPayeeId }
    val visiblePayees = remember(payees, searchQuery) { payees.filter { it.second.contains(searchQuery.trim(), ignoreCase = true) } }
    val mergeTarget = payees.firstOrNull { it.first == mergeTargetId }
    val selectedDefaultCategories = selectedPayeeId?.let(defaultCategories::get).orEmpty()
    val selectedDefaultCategoryId = selectedDefaultCategories.singleOrNull()
    val selectedDefaultCategoryLabel =
        when {
            selectedDefaultCategories.size > 1 -> "Multiple rules"
            else -> categoryOptions.firstOrNull { it.first == selectedDefaultCategoryId }?.second ?: "No default"
        }

    PageLazyColumn {
        item(key = "manage-payees-search") {
            ActivitySearchInput(searchQuery, onValueChange = { searchQuery = it }, placeholder = "Find a payee")
        }
        item(key = "manage-payees-summary") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (searchQuery.isBlank()) "${payees.size} payees" else "${visiblePayees.size} of ${payees.size} payees",
                    modifier = Modifier.weight(1f),
                    color = tandemPalette.muted,
                    fontSize = TandemTypography.caption,
                )
                TandemIconButton(
                    Lucide.Plus,
                    "Add payee",
                    enabled = !state.readOnlyMode,
                    tint = tandemPalette.accent,
                    containerColor = tandemPalette.accentSurface,
                ) {
                    newPayeeName = ""
                    mobileSession.feedback.clearBudgetActionMessage()
                    showAddPayee = true
                }
            }
        }
        if (payees.isEmpty()) {
            item(key = "manage-payees-empty") {
                SurfaceCard {
                    EmptyState(
                        "No payees",
                        "Add a payee to use it in a transaction.",
                        modifier = Modifier.fillMaxWidth().padding(TandemSpacing.large),
                    )
                }
            }
        } else if (visiblePayees.isEmpty()) {
            item(key = "manage-payees-no-matches") {
                SurfaceCard {
                    EmptyState(
                        "No matching payees",
                        modifier = Modifier.fillMaxWidth().padding(TandemSpacing.large),
                    )
                }
            }
        } else {
            items(visiblePayees.size, key = { index -> visiblePayees[index].first }) { index ->
                val (payeeId, name) = visiblePayees[index]
                val openManagePayee = {
                    selectedPayeeId = payeeId
                    selectedPayeeSheetVisible = true
                    nameDraft = name
                    mergeTargetId = null
                    defaultCategoryDraft = defaultCategories[payeeId]?.singleOrNull().orEmpty()
                    mode = "menu"
                    mobileSession.feedback.clearBudgetActionMessage()
                }
                SurfaceCard {
                    ListRow(
                        name,
                        leading = RowLeading.Merchant(merchantIconMatcher?.iconFor(name), fallback = Lucide.Tags),
                        options =
                            ListRowOptions(
                                trailingAction =
                                    ListRowAction(
                                        Lucide.Pencil,
                                        "Manage $name",
                                        enabled = !state.readOnlyMode,
                                        onClick = openManagePayee,
                                    ),
                            ),
                        onClick = {
                            state.activitySearchQuery = ""
                            state.activitySearchFocusRequest += 1
                            state.activitySearchFilters =
                                listOf(ActivitySearchFilter(ActivitySearchField.PAYEE_CONTAINS, payeeId, "Payee: $name"))
                            navigation.open(AppRoute.ACTIVITY_SEARCH)
                        },
                    )
                }
            }
        }
    }

    TandemTaskSheet(
        visible = showAddPayee,
        onDismissRequest = {
            if (!mobileSession.isPerformingBudgetAction) showAddPayee = false
        },
        onDismissed = {},
    ) {
        SurfaceCard(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .padding(horizontal = TandemSpacing.large),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(TandemSpacing.large),
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
            ) {
                TandemModalHeader(
                    title = "Add payee",
                    enabled = !mobileSession.isPerformingBudgetAction,
                    onClose = { if (!mobileSession.isPerformingBudgetAction) showAddPayee = false },
                )
                TextFieldCard("Name", newPayeeName, { newPayeeName = it })
                budgetActionFeedbackMessage(mobileSession)
                Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                    TandemActionButton(
                        "Cancel",
                        TandemActionButtonTone.NEUTRAL,
                        modifier = Modifier.weight(1f),
                        enabled = !mobileSession.isPerformingBudgetAction,
                    ) { showAddPayee = false }
                    TandemActionButton(
                        if (mobileSession.isPerformingBudgetAction) "Adding…" else "Add",
                        TandemActionButtonTone.PRIMARY,
                        modifier = Modifier.weight(1f),
                        icon = Lucide.Plus,
                        enabled =
                            !state.readOnlyMode &&
                                !mobileSession.isPerformingBudgetAction &&
                                newPayeeName.isNotBlank() &&
                                payees.none { it.second.equals(newPayeeName.trim(), ignoreCase = true) },
                        loading = mobileSession.isPerformingBudgetAction,
                    ) {
                        scope.launch {
                            val created =
                                mobileSession.budgetEdits.createBudgetPayee(
                                    state.selectedBudgetId,
                                    newPayeeName.trim(),
                                    state.encryptedPassword.takeIf { it.isNotBlank() },
                                )
                            if (created != null) {
                                showAddPayee = false
                                newPayeeName = ""
                            }
                        }
                    }
                }
            }
        }
    }

    TandemTaskSheet(
        visible = selectedPayeeSheetVisible && selectedPayee != null,
        onDismissRequest = {
            if (!mobileSession.isPerformingBudgetAction) selectedPayeeSheetVisible = false
        },
        onDismissed = {
            selectedPayeeId = null
            mode = "menu"
        },
    ) {
        if (selectedPayee != null) {
            SurfaceCard(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .padding(horizontal = TandemSpacing.large),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 620.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(TandemSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                    ) {
                        Icon(
                            if (mode == "rename") Lucide.Pencil else Lucide.Tags,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = tandemPalette.accent,
                        )
                        Text(
                            when (mode) {
                                "rename" -> "Rename payee"
                                "merge", "confirm-merge" -> "Merge payee"
                                "default-category" -> "Default category"
                                "delete" -> "Delete payee?"
                                else -> selectedPayee.second
                            },
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
                        ) { selectedPayeeSheetVisible = false }
                    }
                    when (mode) {
                        "default-category" -> {
                            ScheduleChoiceField(
                                "Category",
                                categoryOptions.firstOrNull { it.first == defaultCategoryDraft }?.second ?: "No default",
                                categoryOptions,
                            ) { defaultCategoryDraft = it }
                            budgetActionFeedbackMessage(mobileSession)
                            TandemActionButton(
                                if (mobileSession.isPerformingBudgetAction) "Saving…" else "Save",
                                TandemActionButtonTone.PRIMARY,
                                modifier = Modifier.fillMaxWidth(),
                                icon = Lucide.Check,
                                enabled =
                                    !state.readOnlyMode &&
                                        !mobileSession.isPerformingBudgetAction &&
                                        selectedDefaultCategories.size <= 1 &&
                                        defaultCategoryDraft != selectedDefaultCategoryId.orEmpty(),
                                loading = mobileSession.isPerformingBudgetAction,
                            ) {
                                scope.launch {
                                    val saved =
                                        mobileSession.budgetEdits.manageBudgetPayee(
                                            state.selectedBudgetId,
                                            selectedPayee.first,
                                            "default-category",
                                            null,
                                            null,
                                            state.encryptedPassword.takeIf { it.isNotBlank() },
                                            defaultCategoryDraft.ifBlank { null },
                                        )
                                    if (saved) selectedPayeeSheetVisible = false
                                }
                            }
                            TandemActionButton(
                                "Back",
                                TandemActionButtonTone.NEUTRAL,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !mobileSession.isPerformingBudgetAction,
                            ) { mode = "menu" }
                        }
                        "rename" -> {
                            TextFieldCard("Name", nameDraft, { nameDraft = it })
                            budgetActionFeedbackMessage(mobileSession)
                            TandemActionButton(
                                if (mobileSession.isPerformingBudgetAction) "Saving…" else "Save",
                                TandemActionButtonTone.PRIMARY,
                                modifier = Modifier.fillMaxWidth(),
                                icon = Lucide.Check,
                                enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction && nameDraft.isNotBlank(),
                                loading = mobileSession.isPerformingBudgetAction,
                            ) {
                                scope.launch {
                                    val saved =
                                        mobileSession.budgetEdits.manageBudgetPayee(
                                            state.selectedBudgetId,
                                            selectedPayee.first,
                                            "rename",
                                            nameDraft,
                                            null,
                                            state.encryptedPassword.takeIf { it.isNotBlank() },
                                        )
                                    if (saved) selectedPayeeSheetVisible = false
                                }
                            }
                            TandemActionButton(
                                "Back",
                                TandemActionButtonTone.NEUTRAL,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !mobileSession.isPerformingBudgetAction,
                            ) { mode = "menu" }
                        }
                        "merge" -> {
                            Text("Choose the payee to keep.", color = tandemPalette.muted, fontSize = TandemTypography.body)
                            budgetActionFeedbackMessage(mobileSession)
                            payees.filterNot { it.first == selectedPayee.first }.forEach { target ->
                                SurfaceCard {
                                    ListRow(
                                        target.second,
                                        leading = RowLeading.Icon(Lucide.Tags),
                                        onClick = {
                                            mergeTargetId = target.first
                                            mode = "confirm-merge"
                                        },
                                    )
                                }
                            }
                            TandemActionButton(
                                "Back",
                                TandemActionButtonTone.NEUTRAL,
                                modifier = Modifier.fillMaxWidth(),
                            ) { mode = "menu" }
                        }
                        "confirm-merge" -> {
                            Text("Confirm the merge in the alert.", color = tandemPalette.muted, fontSize = TandemTypography.body)
                        }
                        "delete" -> {
                            Text("Confirm deletion in the alert.", color = tandemPalette.muted, fontSize = TandemTypography.body)
                        }
                        else -> {
                            budgetActionFeedbackMessage(mobileSession)
                            SurfaceCard {
                                ListRow(
                                    "Default category",
                                    selectedDefaultCategoryLabel,
                                    leading = RowLeading.Icon(Lucide.Tags),
                                    onClick = {
                                        defaultCategoryDraft = selectedDefaultCategoryId.orEmpty()
                                        mobileSession.feedback.clearBudgetActionMessage()
                                        mode = "default-category"
                                    },
                                )
                            }
                            TandemActionButton(
                                "Rename",
                                TandemActionButtonTone.NEUTRAL,
                                modifier = Modifier.fillMaxWidth(),
                                icon = Lucide.Pencil,
                                enabled = !state.readOnlyMode,
                            ) {
                                nameDraft = selectedPayee.second
                                mobileSession.feedback.clearBudgetActionMessage()
                                mode = "rename"
                            }
                            TandemActionButton(
                                "Merge into another",
                                TandemActionButtonTone.NEUTRAL,
                                modifier = Modifier.fillMaxWidth(),
                                icon = Lucide.Tags,
                                enabled = !state.readOnlyMode && payees.size > 1,
                            ) {
                                mobileSession.feedback.clearBudgetActionMessage()
                                mode = "merge"
                            }
                            TandemActionButton(
                                "Delete",
                                TandemActionButtonTone.DESTRUCTIVE,
                                modifier = Modifier.fillMaxWidth(),
                                icon = Lucide.Trash2,
                                enabled = !state.readOnlyMode,
                            ) {
                                mobileSession.feedback.clearBudgetActionMessage()
                                mode = "delete"
                            }
                        }
                    }
                }
            }
        }
    }
    if (selectedPayeeSheetVisible && selectedPayee != null && mode == "confirm-merge") {
        DestructiveConfirmationDialog(
            title = "Merge payees?",
            message =
                mobileSession.budgetActionMessage
                    ?: "Move transactions from ${selectedPayee.second} to ${mergeTarget?.second ?: "the selected payee"}?",
            messageKind = mobileSession.feedback.budgetActionMessageKind.takeIf { mobileSession.budgetActionMessage != null },
            messageActionLabel = mobileSession.feedback.budgetActionMessageActionLabel,
            onMessageAction =
                if (mobileSession.feedback.budgetActionMessageActionLabel ==
                    null
                ) {
                    null
                } else {
                    mobileSession.feedback::retryBudgetAction
                },
            confirmLabel = if (mobileSession.isPerformingBudgetAction) "Merging…" else "Merge",
            dismissLabel = "Choose another",
            enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction && mergeTarget != null,
            onDismissRequest = {
                if (!mobileSession.isPerformingBudgetAction) mode = "merge"
            },
            onConfirm = {
                scope.launch {
                    val merged =
                        mobileSession.budgetEdits.manageBudgetPayee(
                            state.selectedBudgetId,
                            selectedPayee.first,
                            "merge",
                            null,
                            mergeTarget?.first,
                            state.encryptedPassword.takeIf { it.isNotBlank() },
                        )
                    if (merged) selectedPayeeSheetVisible = false else mode = "merge"
                }
            },
        )
    }
    if (selectedPayeeSheetVisible && selectedPayee != null && mode == "delete") {
        DestructiveConfirmationDialog(
            title = "Delete ${selectedPayee.second}?",
            message = mobileSession.budgetActionMessage ?: "Remove this payee from the budget?",
            messageKind = mobileSession.feedback.budgetActionMessageKind.takeIf { mobileSession.budgetActionMessage != null },
            messageActionLabel = mobileSession.feedback.budgetActionMessageActionLabel,
            onMessageAction =
                if (mobileSession.feedback.budgetActionMessageActionLabel ==
                    null
                ) {
                    null
                } else {
                    mobileSession.feedback::retryBudgetAction
                },
            confirmLabel = if (mobileSession.isPerformingBudgetAction) "Deleting…" else "Delete",
            dismissLabel = "Keep payee",
            enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
            onDismissRequest = {
                if (!mobileSession.isPerformingBudgetAction) mode = "menu"
            },
            onConfirm = {
                scope.launch {
                    val deleted =
                        mobileSession.budgetEdits.manageBudgetPayee(
                            state.selectedBudgetId,
                            selectedPayee.first,
                            "delete",
                            null,
                            null,
                            state.encryptedPassword.takeIf { it.isNotBlank() },
                        )
                    if (deleted) selectedPayeeSheetVisible = false else mode = "menu"
                }
            },
        )
    }
}
