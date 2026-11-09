@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

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
import app.tandemforactual.ui.components.DestructiveConfirmationDialog
import app.tandemforactual.ui.components.EmptyState
import app.tandemforactual.ui.components.InlineMessage
import app.tandemforactual.ui.components.ListRow
import app.tandemforactual.ui.components.ListRowAction
import app.tandemforactual.ui.components.ListRowOptions
import app.tandemforactual.ui.components.RowLeading
import app.tandemforactual.ui.components.SurfaceCard
import app.tandemforactual.ui.components.TandemActionButton
import app.tandemforactual.ui.components.TandemActionButtonTone
import app.tandemforactual.ui.components.TandemIconButton
import app.tandemforactual.ui.components.TandemTaskSheet
import app.tandemforactual.ui.model.LiveScheduleSummary
import app.tandemforactual.ui.model.booleanValue
import app.tandemforactual.ui.model.recordBody
import app.tandemforactual.ui.model.recordName
import app.tandemforactual.ui.model.stringValue
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Tags
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.X
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal fun defaultCategoryRulesByPayee(rules: List<BudgetRecordEntity>): Map<String, List<String>> {
    val categoriesByPayee = mutableMapOf<String, MutableSet<String>>()
    rules.forEach { record ->
        if (record.localDeleted || record.remoteDeleted) return@forEach
        val body = record.recordBody() ?: return@forEach
        if (body.booleanValue("tombstone") == true) return@forEach
        val stage = body["stage"]?.jsonPrimitive?.contentOrNull
        if (stage != null && stage != "default") return@forEach
        if (body.stringValue("conditionsOp") != "and") return@forEach
        val conditions = (body["conditions"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
        if (conditions.size != 1) return@forEach
        val condition = conditions.single()
        if (condition.stringValue("field") != "payee" || condition.stringValue("op") != "is") {
            return@forEach
        }
        val payeeId = condition["value"]?.jsonPrimitive?.contentOrNull ?: return@forEach
        val actions = (body["actions"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
        actions.forEach { action ->
            if (action.stringValue("field") == "category" && action.stringValue("op") == "set") {
                val categoryId = action["value"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                categoriesByPayee.getOrPut(payeeId, ::mutableSetOf).add(categoryId)
            }
        }
    }
    return categoriesByPayee.mapValues { (_, categories) -> categories.sorted() }
}

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
                    mobileSession.clearBudgetActionMessage()
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
                    mobileSession.clearBudgetActionMessage()
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
                Text(
                    "Add payee",
                    color = tandemPalette.text,
                    fontSize = TandemTypography.sectionTitle,
                    fontWeight = FontWeight.Bold,
                )
                TextFieldCard("Name", newPayeeName, { newPayeeName = it })
                mobileSession.budgetActionMessage?.let { InlineMessage(it, mobileSession.budgetActionMessageKind) }
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
                                mobileSession.createBudgetPayee(
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
                            mobileSession.budgetActionMessage?.let {
                                InlineMessage(it, mobileSession.budgetActionMessageKind)
                            }
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
                                        mobileSession.manageBudgetPayee(
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
                            mobileSession.budgetActionMessage?.let { InlineMessage(it, mobileSession.budgetActionMessageKind) }
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
                                        mobileSession.manageBudgetPayee(
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
                            mobileSession.budgetActionMessage?.let { InlineMessage(it, mobileSession.budgetActionMessageKind) }
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
                            mobileSession.budgetActionMessage?.let { InlineMessage(it, mobileSession.budgetActionMessageKind) }
                            SurfaceCard {
                                ListRow(
                                    "Default category",
                                    selectedDefaultCategoryLabel,
                                    leading = RowLeading.Icon(Lucide.Tags),
                                    onClick = {
                                        defaultCategoryDraft = selectedDefaultCategoryId.orEmpty()
                                        mobileSession.clearBudgetActionMessage()
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
                                mobileSession.clearBudgetActionMessage()
                                mode = "rename"
                            }
                            TandemActionButton(
                                "Merge into another",
                                TandemActionButtonTone.NEUTRAL,
                                modifier = Modifier.fillMaxWidth(),
                                icon = Lucide.Tags,
                                enabled = !state.readOnlyMode && payees.size > 1,
                            ) {
                                mobileSession.clearBudgetActionMessage()
                                mode = "merge"
                            }
                            TandemActionButton(
                                "Delete",
                                TandemActionButtonTone.DESTRUCTIVE,
                                modifier = Modifier.fillMaxWidth(),
                                icon = Lucide.Trash2,
                                enabled = !state.readOnlyMode,
                            ) {
                                mobileSession.clearBudgetActionMessage()
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
            messageKind = mobileSession.budgetActionMessageKind.takeIf { mobileSession.budgetActionMessage != null },
            confirmLabel = if (mobileSession.isPerformingBudgetAction) "Merging…" else "Merge",
            dismissLabel = "Choose another",
            enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction && mergeTarget != null,
            onDismissRequest = {
                if (!mobileSession.isPerformingBudgetAction) mode = "merge"
            },
            onConfirm = {
                scope.launch {
                    val merged =
                        mobileSession.manageBudgetPayee(
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
            messageKind = mobileSession.budgetActionMessageKind.takeIf { mobileSession.budgetActionMessage != null },
            confirmLabel = if (mobileSession.isPerformingBudgetAction) "Deleting…" else "Delete",
            dismissLabel = "Keep payee",
            enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
            onDismissRequest = {
                if (!mobileSession.isPerformingBudgetAction) mode = "menu"
            },
            onConfirm = {
                scope.launch {
                    val deleted =
                        mobileSession.manageBudgetPayee(
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

@Composable
internal fun BudgetRulesScreen(
    rules: List<BudgetRecordEntity>,
    accountRecords: List<BudgetRecordEntity>,
    categoryRecords: List<BudgetRecordEntity>,
    categoryGroupRecords: List<BudgetRecordEntity>,
    payeeRecords: List<BudgetRecordEntity>,
) {
    val namesByField =
        remember(accountRecords, categoryRecords, categoryGroupRecords, payeeRecords) {
            mapOf(
                "account" to accountRecords.mapNotNull { it.recordName()?.let { name -> it.recordId to name } }.toMap(),
                "category" to categoryRecords.mapNotNull { it.recordName()?.let { name -> it.recordId to name } }.toMap(),
                "category_group" to categoryGroupRecords.mapNotNull { it.recordName()?.let { name -> it.recordId to name } }.toMap(),
                "payee" to payeeRecords.mapNotNull { it.recordName()?.let { name -> it.recordId to name } }.toMap(),
            )
        }
    val activeRules = remember(rules) { rules.filterNot { it.recordBody()?.booleanValue("tombstone") == true } }
    PageLazyColumn {
        if (activeRules.isEmpty()) {
            item(key = "budget-rules-empty") {
                SurfaceCard {
                    EmptyState(
                        "No rules",
                        "Rules created in Actual will appear here.",
                        modifier = Modifier.fillMaxWidth().padding(TandemSpacing.large),
                    )
                }
            }
        } else {
            items(activeRules.size, key = { index -> activeRules[index].recordId }) { index ->
                val body = activeRules[index].recordBody()
                val conditions = (body?.get("conditions") as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
                val actions = (body?.get("actions") as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
                val conditionSummary =
                    conditions
                        .joinToString(" · ") { condition ->
                            val field = condition.stringValue("field")?.replace('_', ' ') ?: "Field"
                            val operation = condition.stringValue("op")?.replace('_', ' ') ?: "matches"
                            val rawValue =
                                condition["value"]
                                    ?.let { value -> (value as? JsonPrimitive)?.contentOrNull ?: value.toString() }
                                    ?.trim('"')
                            val mappedValue =
                                rawValue?.let { value -> namesByField[condition.stringValue("field")]?.get(value) ?: value }
                            listOfNotNull(field, operation, mappedValue).joinToString(" ")
                        }.ifBlank { "Every transaction" }
                val actionSummary =
                    actions
                        .joinToString(" · ") { action ->
                            val field = action.stringValue("field")?.replace('_', ' ')
                            val operation = action.stringValue("op")
                            val rawValue =
                                action["value"]
                                    ?.let { value -> (value as? JsonPrimitive)?.contentOrNull ?: value.toString() }
                                    ?.trim('"')
                            val mappedValue = rawValue?.let { value -> namesByField[field?.replace(' ', '_')]?.get(value) ?: value }
                            when (operation) {
                                "append-notes" -> "Append note"
                                "prepend-notes" -> "Add note"
                                "delete-transaction" -> "Delete transaction"
                                "link-schedule" -> "Link schedule"
                                else -> listOfNotNull(field, mappedValue).joinToString(": ")
                            }
                        }.ifBlank { "No actions" }
                val stage =
                    when (body?.stringValue("stage")) {
                        "pre" -> "Before import"
                        "post" -> "After import"
                        else -> "All stages"
                    }
                SurfaceCard {
                    ListRow(
                        conditionSummary,
                        actionSummary,
                        stage,
                        leading = RowLeading.Icon(Lucide.Tags),
                        options = ListRowOptions(trailingColor = tandemPalette.muted),
                    )
                }
            }
        }
    }
}

internal fun LiveScheduleSummary.amountLabel(state: AppState): String =
    when {
        amountMinor != null -> state.formatCurrency(amountMinor)
        amountRangeMinor != null -> "${state.formatCurrency(amountRangeMinor.first)}–${state.formatCurrency(amountRangeMinor.second)}"
        else -> "Variable"
    }
