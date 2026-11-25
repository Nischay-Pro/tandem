package app.tandemforactual.ui.app.routes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import app.tandemforactual.ui.app.ScreenContext
import app.tandemforactual.ui.app.recentPayeeGroup
import app.tandemforactual.ui.core.session.budget.createBudgetPayee
import app.tandemforactual.ui.core.session.budget.enqueueBudgetCategoryCreate
import app.tandemforactual.ui.core.session.budget.suggestedCategoryForPayee
import app.tandemforactual.ui.core.session.budgetActionFeedbackMessage
import app.tandemforactual.ui.core.session.recentPayeeIds
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.SelectorGroup
import app.tandemforactual.ui.core.state.SelectorItem
import app.tandemforactual.ui.core.state.booleanValue
import app.tandemforactual.ui.core.state.recordBody
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.core.state.stringValue
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.BodyText
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.feature.reports.label
import app.tandemforactual.ui.feature.transactions.SelectorScreen
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Tags
import kotlinx.coroutines.launch

/** Screens for choosing a payee or category while editing a transaction. */
internal val selectorScreens: Map<AppRoute, @Composable (ScreenContext) -> Unit> =
    mapOf(
        AppRoute.CHOOSE_PAYEE to
            @Composable { ctx ->
                with(ctx) {
                    val scope = rememberCoroutineScope()
                    val isLiveBudget = state.selectedBudgetId.isNotBlank()
                    val selectingTransferDestination = isLiveBudget && state.transactionType == "Transfer"
                    val activePayees =
                        remember(mobileSession.payeeRecords) {
                            mobileSession.payeeRecords.filterNot { record ->
                                val body = record.recordBody()
                                record.localDeleted || record.remoteDeleted || body?.booleanValue("tombstone") == true
                            }
                        }
                    val values =
                        remember(isLiveBudget, selectingTransferDestination, state.newTransactionAccountId, activePayees) {
                            if (!isLiveBudget) {
                                listOf("Corner Market", "Electric Company", "Acme Corp", "Riverside Cafe", "City Transit")
                                    .map { SelectorItem(it, it) }
                            } else if (selectingTransferDestination) {
                                activePayees
                                    .mapNotNull { record ->
                                        val destinationId = record.recordBody()?.stringValue("transfer_acct")
                                        val name = record.recordName()
                                        if (destinationId.isNullOrBlank() ||
                                            destinationId == state.newTransactionAccountId ||
                                            name.isNullOrBlank()
                                        ) {
                                            null
                                        } else {
                                            val accountName = name.removePrefix("Transfer:").trim().ifBlank { name }
                                            SelectorItem(record.recordId, accountName)
                                        }
                                    }.distinctBy { it.id }
                            } else {
                                listOf(SelectorItem("", "No payee")) +
                                    activePayees
                                        .filter { it.recordBody()?.stringValue("transfer_acct").isNullOrBlank() }
                                        .mapNotNull { record -> record.recordName()?.let { SelectorItem(record.recordId, it) } }
                                        .distinctBy { it.id }
                            }
                        }
                    var recentPayeeIds by remember(state.selectedBudgetId) { mutableStateOf(emptyList<String>()) }
                    LaunchedEffect(state.selectedBudgetId, isLiveBudget) {
                        if (isLiveBudget) recentPayeeIds = mobileSession.recentPayeeIds(state.selectedBudgetId)
                    }
                    val payeeGroups =
                        remember(selectingTransferDestination, values, recentPayeeIds) {
                            when {
                                selectingTransferDestination && values.isNotEmpty() -> listOf(SelectorGroup("To account", values))
                                selectingTransferDestination -> emptyList()
                                else -> recentPayeeGroup(values, recentPayeeIds)
                            }
                        }
                    SelectorScreen(
                        values,
                        if (isLiveBudget) state.newTransactionPayeeId else state.transactionPayee,
                        live = isLiveBudget,
                        groups = payeeGroups,
                        searchLabel = if (selectingTransferDestination) "Search accounts" else "Search payees",
                        searchPlaceholder = if (selectingTransferDestination) "Find an account" else "Find a payee",
                        noMatches =
                            if (isLiveBudget) {
                                { query ->
                                    val newName = query.trim()
                                    if (selectingTransferDestination) {
                                        EmptyState("No destination account found", modifier = Modifier.padding(TandemSpacing.large))
                                    } else {
                                        Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                                            if (newName.isNotEmpty()) {
                                                SurfaceCard {
                                                    ListRow(
                                                        if (mobileSession.isPerformingBudgetAction) "Adding payee…" else "Add $newName",
                                                        leading = RowLeading.Icon(Lucide.Plus),
                                                        onClick = {
                                                            if (!mobileSession.isPerformingBudgetAction) {
                                                                scope.launch {
                                                                    val payeeId =
                                                                        mobileSession.budgetEdits.createBudgetPayee(
                                                                            state.selectedBudgetId,
                                                                            newName,
                                                                            state.encryptedPassword.takeIf { it.isNotBlank() },
                                                                        )
                                                                    if (payeeId != null) {
                                                                        state.transactionPayee = newName
                                                                        state.newTransactionPayeeId = payeeId
                                                                        navigation.back()
                                                                    }
                                                                }
                                                            }
                                                        },
                                                    )
                                                }
                                            } else {
                                                EmptyState("No matches", modifier = Modifier.padding(TandemSpacing.large))
                                            }
                                            budgetActionFeedbackMessage(mobileSession)
                                        }
                                    }
                                }
                            } else {
                                null
                            },
                        onSelect = { item ->
                            state.transactionPayee = if (item.label == "No payee") "" else item.label
                            state.newTransactionPayeeId = if (isLiveBudget) item.id else ""
                            if (isLiveBudget && !selectingTransferDestination && state.newTransactionCategoryId.isBlank()) {
                                scope.launch {
                                    val categoryId = mobileSession.budgetEdits.suggestedCategoryForPayee(state.selectedBudgetId, item.id)
                                    val categoryName = mobileSession.categoryRecords.firstOrNull { it.recordId == categoryId }?.recordName()
                                    if (categoryId != null && categoryName != null && state.newTransactionCategoryId.isBlank()) {
                                        state.newTransactionCategoryId = categoryId
                                        state.transactionCategory = categoryName
                                    }
                                }
                            }
                            navigation.back()
                        },
                    )
                }
            },
        AppRoute.CHOOSE_CATEGORY to
            @Composable { ctx ->
                with(ctx) {
                    val isLiveBudget = state.selectedBudgetId.isNotBlank()
                    val visibleMonthGroups =
                        liveBudgetMonth
                            ?.groups
                            ?.filter { it.isIncome == (state.transactionType == "Income") }
                            .orEmpty()
                    val allLiveCategoryItems =
                        if (!isLiveBudget) {
                            emptyList()
                        } else {
                            val cachedCategories =
                                mobileSession.categoryRecords
                                    .mapNotNull { record ->
                                        val body = record.recordBody()
                                        if (body?.booleanValue("hidden") == true || body?.booleanValue("deleted") == true) {
                                            null
                                        } else {
                                            record.recordName()?.let { SelectorItem(record.recordId, it) }
                                        }
                                    }
                            val visibleCategories =
                                visibleMonthGroups.flatMap { group ->
                                    group.categories.map { category -> SelectorItem(category.id, category.name) }
                                }
                            (cachedCategories + visibleCategories).distinctBy(SelectorItem::id)
                        }
                    val allCategoryItemsById = allLiveCategoryItems.associateBy(SelectorItem::id)
                    val visibleCategoryIds = visibleMonthGroups.flatMap { it.categories }.mapTo(mutableSetOf()) { it.id }
                    val visibleCategoryItems =
                        if (liveBudgetMonth == null) allLiveCategoryItems else allLiveCategoryItems.filter { it.id in visibleCategoryIds }
                    val values =
                        if (!isLiveBudget) {
                            listOf("Groceries", "Utilities", "Dining", "Rent", "Transport", "Personal")
                                .map { SelectorItem(it, it) }
                        } else {
                            listOf(SelectorItem("", "Uncategorized")) + visibleCategoryItems
                        }
                    val recentCategoryItems =
                        if (!isLiveBudget) {
                            emptyList()
                        } else {
                            liveTransactions
                                .mapNotNull { transaction -> transaction.categoryId?.let(allCategoryItemsById::get) }
                                .distinctBy(SelectorItem::id)
                                .take(5)
                        }
                    val categorySelectorGroups =
                        if (!isLiveBudget) {
                            emptyList()
                        } else {
                            buildList {
                                if (recentCategoryItems.isNotEmpty()) add(SelectorGroup("Recent", recentCategoryItems))
                                visibleMonthGroups.forEach { group ->
                                    val groupItems =
                                        group.categories
                                            .mapNotNull { category ->
                                                allCategoryItemsById[category.id] ?: SelectorItem(category.id, category.name)
                                            }.sortedBy { it.label.lowercase() }
                                    if (groupItems.isNotEmpty()) add(SelectorGroup(group.name, groupItems))
                                }
                                val groupedIds = flatMap { it.items }.mapTo(mutableSetOf()) { it.id }
                                val ungrouped = visibleCategoryItems.filterNot { it.id in groupedIds }.sortedBy { it.label.lowercase() }
                                if (ungrouped.isNotEmpty()) add(SelectorGroup("Other", ungrouped))
                            }
                        }
                    val categoryGroups =
                        if (!isLiveBudget) {
                            emptyList()
                        } else {
                            val visibleGroups = visibleMonthGroups.map { SelectorItem(it.id, it.name) }
                            if (visibleGroups.isNotEmpty()) {
                                visibleGroups
                            } else {
                                mobileSession.categoryGroupRecords.mapNotNull { record ->
                                    val body = record.recordBody() ?: return@mapNotNull null
                                    val isIncomeGroup = body.booleanValue("is_income") ?: false
                                    if (isIncomeGroup != (state.transactionType == "Income") || body.booleanValue("hidden") == true) {
                                        return@mapNotNull null
                                    }
                                    record.recordName()?.let { SelectorItem(record.recordId, it) }
                                }
                            }
                        }
                    var createGroupId by rememberSaveable(state.selectedBudgetId, state.transactionType) { mutableStateOf("") }
                    var createGroupMenuExpanded by rememberSaveable(state.selectedBudgetId, state.transactionType) { mutableStateOf(false) }
                    LaunchedEffect(categoryGroups) {
                        if (categoryGroups.none { it.id == createGroupId }) {
                            createGroupId = categoryGroups.firstOrNull()?.id.orEmpty()
                        }
                    }
                    SelectorScreen(
                        values,
                        when {
                            state.selectedBudgetId.isBlank() -> state.transactionCategory
                            else ->
                                state.transactionSplit
                                    ?.let { split -> split.lines.firstOrNull { it.key == split.pickingLineKey }?.categoryId }
                                    ?: state.newTransactionCategoryId
                        },
                        live = isLiveBudget,
                        groups = categorySelectorGroups,
                        searchLabel = "Search categories",
                        searchPlaceholder = "Find a category",
                        noMatches =
                            if (isLiveBudget) {
                                { query ->
                                    val newName = query.trim()
                                    if (newName.isBlank()) {
                                        EmptyState("Type a category name", modifier = Modifier.padding(TandemSpacing.large))
                                    } else {
                                        SurfaceCard {
                                            Column(
                                                Modifier.fillMaxWidth().padding(TandemSpacing.large),
                                                verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                                            ) {
                                                Text(
                                                    "Create category",
                                                    color = tandemPalette.text,
                                                    fontSize = TandemTypography.cardTitle,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                                if (categoryGroups.isEmpty()) {
                                                    BodyText("Add a ${state.transactionType.lowercase()} category group in Actual first.")
                                                } else {
                                                    Box {
                                                        val selectedGroup = categoryGroups.firstOrNull { it.id == createGroupId }
                                                        ListRow(
                                                            "Category group",
                                                            leading = RowLeading.Icon(Lucide.Tags),
                                                            trailing = selectedGroup?.label ?: "Choose group",
                                                            onClick = { createGroupMenuExpanded = true },
                                                        )
                                                        DropdownMenu(
                                                            expanded = createGroupMenuExpanded,
                                                            onDismissRequest = { createGroupMenuExpanded = false },
                                                        ) {
                                                            categoryGroups.forEach { group ->
                                                                DropdownMenuItem(
                                                                    onClick = {
                                                                        createGroupId = group.id
                                                                        createGroupMenuExpanded = false
                                                                    },
                                                                ) {
                                                                    Text(
                                                                        group.label,
                                                                        color = tandemPalette.text,
                                                                        fontSize = TandemTypography.compactBody,
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                    budgetActionFeedbackMessage(mobileSession)
                                                    TandemActionButton(
                                                        "Create \"$newName\"",
                                                        TandemActionButtonTone.PRIMARY,
                                                        modifier = Modifier.fillMaxWidth(),
                                                        enabled =
                                                            !state.readOnlyMode &&
                                                                createGroupId.isNotBlank() &&
                                                                newName.length <= 100,
                                                    ) {
                                                        mobileSession.feedback.clearBudgetActionMessage()
                                                        scope.launch {
                                                            val categoryId =
                                                                mobileSession.budgetQueue.enqueueBudgetCategoryCreate(
                                                                    state.selectedBudgetId,
                                                                    createGroupId,
                                                                    newName,
                                                                    visibleMonthGroups
                                                                        .firstOrNull { it.id == createGroupId }
                                                                        ?.categories
                                                                        ?.map { it.id }
                                                                        .orEmpty(),
                                                                    state.encryptedPassword.takeIf { it.isNotBlank() },
                                                                )
                                                            if (categoryId != null) {
                                                                chooseTransactionCategory(state, categoryId, newName)
                                                                navigation.back()
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                null
                            },
                    ) { item ->
                        chooseTransactionCategory(
                            state,
                            if (!isLiveBudget) "" else item.id,
                            if (item.label == "Uncategorized") "" else item.label,
                        )
                        navigation.back()
                    }
                }
            },
    )

/** Sets the form's category, or the split line the picker was opened for. */
private fun chooseTransactionCategory(
    state: AppState,
    id: String,
    name: String,
) {
    val split = state.transactionSplit
    if (split?.pickingLineKey != null) {
        state.transactionSplit = split.pickCategory(id, name)
    } else {
        state.transactionCategory = name
        state.newTransactionCategoryId = id
    }
}
