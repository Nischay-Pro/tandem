@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.rules
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.ui.core.search.ActivitySearchField
import app.tandemforactual.ui.core.search.ActivitySearchFilter
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.manageBudgetPayee
import app.tandemforactual.ui.core.session.budgetActionFeedbackMessage
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.MerchantIconMatcherPack
import app.tandemforactual.ui.core.state.defaultCategoryRulesByPayee
import app.tandemforactual.ui.core.state.payeesInRules
import app.tandemforactual.ui.core.ui.ActivitySearchInput
import app.tandemforactual.ui.core.ui.ScheduleChoiceField
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowAction
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.templates.PageLazyColumn
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Tags
import kotlinx.coroutines.CoroutineScope
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
    val payee = rememberManagePayeeState(state.selectedBudgetId)
    var searchQuery by rememberSaveable(state.selectedBudgetId) { mutableStateOf("") }
    var showAddPayee by rememberSaveable(state.selectedBudgetId) { mutableStateOf(false) }
    var showCleanup by rememberSaveable(state.selectedBudgetId) { mutableStateOf(false) }
    var newPayeeName by rememberSaveable(state.selectedBudgetId) { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val payees = rememberManagedPayees(payeeRecords)
    val defaultCategories = remember(ruleRecords) { defaultCategoryRulesByPayee(ruleRecords) }
    val inRules = remember(ruleRecords) { payeesInRules(ruleRecords) }
    val categoryOptions = rememberPayeeCategoryOptions(categoryRecords)
    val selectedPayee = payees.firstOrNull { it.first == payee.selectedPayeeId }
    val visiblePayees = remember(payees, searchQuery) { payees.filter { it.second.contains(searchQuery.trim(), ignoreCase = true) } }
    val mergeTarget = payees.firstOrNull { it.first == payee.mergeTargetId }
    val selectedDefaultCategories = payee.selectedPayeeId?.let(defaultCategories::get).orEmpty()
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
            PayeeListSummary(
                payeeCountSummary(visiblePayees.size, payees.size, searchQuery.isNotBlank()),
                addEnabled = !state.readOnlyMode,
                onCleanUp = { showCleanup = true }.takeIf { payees.size > 1 },
            ) {
                newPayeeName = ""
                mobileSession.feedback.clearBudgetActionMessage()
                showAddPayee = true
            }
        }
        if (payees.isEmpty()) {
            noPayeesItem()
        } else if (visiblePayees.isEmpty()) {
            noMatchingPayeesItem()
        } else {
            payeeItems(visiblePayees, payee, defaultCategories, state, navigation, mobileSession, merchantIconMatcher)
        }
    }

    AddPayeeSheet(showAddPayee, newPayeeName, { newPayeeName = it }, { showAddPayee = false }, payees, state, mobileSession, scope)
    PayeeCleanupSheet(showCleanup, payees, inRules, state, mobileSession, scope) { showCleanup = false }
    ManagePayeeSheet(
        payee,
        selectedPayee,
        payees,
        selectedDefaultCategoryId,
        selectedDefaultCategoryLabel,
        state,
        navigation,
        mobileSession,
        scope,
    ) {
        if (selectedPayee != null) {
            DefaultCategorySection(payee, selectedPayee, categoryOptions, selectedDefaultCategories, state, mobileSession, scope)
        }
    }
    if (payee.sheetVisible && selectedPayee != null && payee.mode == "confirm-merge") {
        MergePayeesDialog(payee, selectedPayee, mergeTarget, state, mobileSession, scope)
    }
    if (payee.sheetVisible && selectedPayee != null && payee.mode == "delete") {
        DeletePayeeDialog(payee, selectedPayee, state, mobileSession, scope)
    }
}

/** Payee rows that open activity search on tap and the manage sheet from the edit action. */
private fun LazyListScope.payeeItems(
    visiblePayees: List<Pair<String, String>>,
    payee: ManagePayeeState,
    defaultCategories: Map<String, List<String>>,
    state: AppState,
    navigation: AppNavigation,
    mobileSession: TandemMobileSession,
    merchantIconMatcher: MerchantIconMatcherPack?,
) {
    items(visiblePayees.size, key = { index -> visiblePayees[index].first }) { index ->
        val (payeeId, name) = visiblePayees[index]
        val openManagePayee = {
            payee.selectedPayeeId = payeeId
            payee.sheetVisible = true
            payee.nameDraft = name
            payee.mergeTargetId = null
            payee.defaultCategoryDraft = defaultCategories[payeeId]?.singleOrNull().orEmpty()
            payee.mode = "menu"
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

/** Category picker and save button for the payee's default category rule. */
@Composable
private fun DefaultCategorySection(
    payee: ManagePayeeState,
    selectedPayee: Pair<String, String>,
    categoryOptions: List<Pair<String, String>>,
    selectedDefaultCategories: List<String>,
    state: AppState,
    mobileSession: TandemMobileSession,
    scope: CoroutineScope,
) {
    val selectedDefaultCategoryId = selectedDefaultCategories.singleOrNull()
    ScheduleChoiceField(
        "Category",
        categoryOptions.firstOrNull { it.first == payee.defaultCategoryDraft }?.second ?: "No default",
        categoryOptions,
    ) { payee.defaultCategoryDraft = it }
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
                payee.defaultCategoryDraft != selectedDefaultCategoryId.orEmpty(),
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
                    payee.defaultCategoryDraft.ifBlank { null },
                )
            if (saved) payee.sheetVisible = false
        }
    }
    TandemActionButton(
        "Back",
        TandemActionButtonTone.NEUTRAL,
        modifier = Modifier.fillMaxWidth(),
        enabled = !mobileSession.isPerformingBudgetAction,
    ) { payee.mode = "menu" }
}
