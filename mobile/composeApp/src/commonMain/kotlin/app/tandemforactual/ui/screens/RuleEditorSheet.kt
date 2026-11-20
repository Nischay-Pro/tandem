@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.ui.components.DestructiveConfirmationDialog
import app.tandemforactual.ui.components.DividerLine
import app.tandemforactual.ui.components.InlineMessage
import app.tandemforactual.ui.components.InlineMessageKind
import app.tandemforactual.ui.components.ListRow
import app.tandemforactual.ui.components.ListRowOptions
import app.tandemforactual.ui.components.PrimaryButton
import app.tandemforactual.ui.components.RowLeading
import app.tandemforactual.ui.components.SecondaryButton
import app.tandemforactual.ui.components.SectionCard
import app.tandemforactual.ui.components.SegmentedSelector
import app.tandemforactual.ui.components.SurfaceCard
import app.tandemforactual.ui.components.TandemChoiceDialog
import app.tandemforactual.ui.components.TandemIconButton
import app.tandemforactual.ui.components.TandemModalHeader
import app.tandemforactual.ui.components.TandemTaskSheet
import app.tandemforactual.ui.model.recordBody
import app.tandemforactual.ui.model.recordName
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Trash2
import kotlinx.coroutines.launch

@Composable
internal fun RuleEditorSheet(
    state: AppState,
    mobileSession: TandemMobileSession,
) {
    val target = state.ruleEditorRuleId
    var shownRuleId by remember { mutableStateOf("") }
    if (target != null) shownRuleId = target
    val close = {
        if (!mobileSession.isPerformingBudgetAction) {
            state.ruleEditorRuleId = null
            mobileSession.clearBudgetActionMessage()
        }
    }
    TandemTaskSheet(
        visible = target != null,
        onDismissRequest = close,
        onDismissed = {},
        fullScreen = true,
    ) {
        Column(Modifier.fillMaxSize().background(tandemPalette.background)) {
            TandemModalHeader(
                title = if (shownRuleId.isBlank()) "New rule" else "Edit rule",
                onClose = close,
                modifier = Modifier.padding(horizontal = TandemSpacing.screen, vertical = TandemSpacing.medium),
            )
            RuleEditorContent(state, mobileSession, shownRuleId, onDone = { state.ruleEditorRuleId = null })
        }
    }
}

@Composable
private fun RuleEditorContent(
    state: AppState,
    mobileSession: TandemMobileSession,
    ruleId: String,
    onDone: () -> Unit,
) {
    val record = mobileSession.ruleRecords.firstOrNull { it.recordId == ruleId }
    val formatAmount: (Long) -> String = { formatAmount(it, state.budgetDisplaySettings) }
    val existing = remember(record) { record?.recordBody()?.toRuleDraftOrNull(formatAmount) }
    val unsupported = ruleId.isNotBlank() && record != null && existing == null
    var draft by remember(ruleId, existing) { mutableStateOf(existing ?: RuleDraft()) }
    var error by remember(ruleId) { mutableStateOf<String?>(null) }
    var confirmDelete by remember(ruleId) { mutableStateOf(false) }
    val canEdit = mobileSession.canEditRules && !state.readOnlyMode
    val busy = mobileSession.isPerformingBudgetAction
    val scope = rememberCoroutineScope()
    val password = state.encryptedPassword.takeIf { it.isNotBlank() }
    val choices = rememberRuleChoices(mobileSession)

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = TandemSpacing.screen, vertical = TandemSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.large),
    ) {
        if (!mobileSession.canEditRules) {
            InlineMessage("Update your Tandem server to add or edit rules here.", InlineMessageKind.WARNING)
        }
        if (unsupported) {
            InlineMessage(
                "This rule uses options Tandem can’t edit yet, such as splits or formulas. Edit it in Actual.",
                InlineMessageKind.WARNING,
            )
        } else {
            SectionCard("Run order", "Rules in the same order run from top to bottom in Actual.") {
                Column(Modifier.padding(TandemSpacing.medium)) {
                    SegmentedSelector(RuleStage.entries.map { it.label to (it == draft.stage) }) {
                        draft = draft.copy(stage = RuleStage.entries[it])
                    }
                }
            }
            SectionCard("If") {
                Column(
                    Modifier.padding(TandemSpacing.medium),
                    verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                ) {
                    SegmentedSelector(listOf("All match" to draft.matchAll, "Any match" to !draft.matchAll)) {
                        draft = draft.copy(matchAll = it == 0)
                    }
                    draft.conditions.forEachIndexed { index, condition ->
                        if (index > 0) DividerLine()
                        RuleConditionEditor(
                            condition = condition,
                            choices = choices,
                            removable = draft.conditions.size > 1,
                            onChange = { next ->
                                draft = draft.copy(conditions = draft.conditions.toMutableList().also { it[index] = next })
                                error = null
                            },
                            onRemove = { draft = draft.copy(conditions = draft.conditions.filterIndexed { i, _ -> i != index }) },
                        )
                    }
                }
                DividerLine()
                ListRow(
                    "Add condition",
                    leading = RowLeading.Icon(Lucide.Plus),
                    options = ListRowOptions(dense = true),
                    onClick = { draft = draft.copy(conditions = draft.conditions + RuleConditionDraft()) },
                )
            }
            SectionCard("Then") {
                Column(
                    Modifier.padding(TandemSpacing.medium),
                    verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                ) {
                    draft.actions.forEachIndexed { index, action ->
                        if (index > 0) DividerLine()
                        RuleActionEditor(
                            action = action,
                            choices = choices,
                            removable = draft.actions.size > 1,
                            onChange = { next ->
                                draft = draft.copy(actions = draft.actions.toMutableList().also { it[index] = next })
                                error = null
                            },
                            onRemove = { draft = draft.copy(actions = draft.actions.filterIndexed { i, _ -> i != index }) },
                        )
                    }
                }
                DividerLine()
                ListRow(
                    "Add action",
                    leading = RowLeading.Icon(Lucide.Plus),
                    options = ListRowOptions(dense = true),
                    onClick = {
                        val unused = RuleActionKind.entries.firstOrNull { kind -> draft.actions.none { it.kind == kind } }
                        draft = draft.copy(actions = draft.actions + RuleActionDraft(unused ?: RuleActionKind.APPEND_NOTES))
                    },
                )
            }
            error?.let { InlineMessage(it, InlineMessageKind.ERROR) }
            mobileSession.budgetActionMessage?.let { InlineMessage(it, mobileSession.budgetActionMessageKind) }
            PrimaryButton(
                if (ruleId.isBlank()) "Add rule" else "Save rule",
                enabled = canEdit && !busy,
                loading = busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val body =
                    draft.toRequestBody { text -> state.parseAmountMinorUnits(text) }.getOrElse {
                        error = it.message
                        return@PrimaryButton
                    }
                scope.launch {
                    val saved =
                        if (ruleId.isBlank()) {
                            mobileSession.createBudgetRule(state.selectedBudgetId, body, password)
                        } else {
                            mobileSession.editBudgetRule(state.selectedBudgetId, ruleId, body, password)
                        }
                    if (saved) onDone()
                }
            }
        }
        if (ruleId.isNotBlank()) {
            SecondaryButton("Delete rule", modifier = Modifier.fillMaxWidth(), enabled = canEdit && !busy) { confirmDelete = true }
        }
    }
    if (confirmDelete) {
        DestructiveConfirmationDialog(
            title = "Delete this rule?",
            message = "Actual will stop applying it to new and imported transactions. Transactions it already changed stay as they are.",
            confirmLabel = "Delete",
            dismissLabel = "Keep rule",
            enabled = !busy,
            onDismissRequest = { confirmDelete = false },
            onConfirm = {
                scope.launch {
                    val deleted = mobileSession.deleteBudgetRule(state.selectedBudgetId, ruleId, password)
                    confirmDelete = false
                    if (deleted) onDone()
                }
            },
        )
    }
}

internal data class RuleChoices(
    val byField: Map<String, List<Pair<String, String>>>,
) {
    fun options(field: String): List<Pair<String, String>> = byField[field].orEmpty()

    fun name(
        field: String,
        id: String,
    ): String? = options(field).firstOrNull { it.first == id }?.second
}

@Composable
internal fun rememberRuleChoices(mobileSession: TandemMobileSession): RuleChoices {
    val payees = mobileSession.payeeRecords
    val accounts = mobileSession.accountRecords
    val categories = mobileSession.categoryRecords
    return remember(payees, accounts, categories) {
        fun List<BudgetRecordEntity>.named() =
            mapNotNull { record -> record.recordName()?.let { record.recordId to it } }.sortedBy { it.second.lowercase() }
        RuleChoices(mapOf("payee" to payees.named(), "account" to accounts.named(), "category" to categories.named()))
    }
}

@Composable
private fun RuleConditionEditor(
    condition: RuleConditionDraft,
    choices: RuleChoices,
    removable: Boolean,
    onChange: (RuleConditionDraft) -> Unit,
    onRemove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                RuleChoiceField(
                    "Field",
                    condition.field.label,
                    RuleConditionField.entries.map { it.name to it.label },
                    selectedIds = setOf(condition.field.name),
                ) { onChange(condition.withField(RuleConditionField.valueOf(it.first()))) }
                RuleChoiceField(
                    "Comparison",
                    ruleOperatorLabel(condition.op),
                    condition.field.ops.map { it to ruleOperatorLabel(it) },
                    selectedIds = setOf(condition.op),
                ) { onChange(condition.withOp(it.first())) }
            }
            if (removable) TandemIconButton(Lucide.Trash2, "Remove condition", tint = tandemPalette.muted, onClick = onRemove)
        }
        val field = condition.field
        when {
            field.usesIds -> {
                val multiple = condition.op in setOf("oneOf", "notOneOf")
                val label =
                    condition.ids
                        .mapNotNull { choices.name(field.wire, it) }
                        .joinToString(", ")
                        .ifBlank { "Choose ${field.label.lowercase()}" }
                RuleChoiceField(
                    field.label,
                    label,
                    choices.options(field.wire),
                    selectedIds = condition.ids.toSet(),
                    multiple = multiple,
                ) { onChange(condition.copy(ids = it)) }
            }
            field.usesAmount -> {
                val amountOptions = TextFieldOptions(keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                if (condition.op == "isbetween") {
                    Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        Column(Modifier.weight(1f)) {
                            TextFieldCard("From", condition.amountText, { onChange(condition.copy(amountText = it)) }, amountOptions)
                        }
                        Column(Modifier.weight(1f)) {
                            TextFieldCard("To", condition.amountMaxText, { onChange(condition.copy(amountMaxText = it)) }, amountOptions)
                        }
                    }
                } else {
                    TextFieldCard("Amount", condition.amountText, { onChange(condition.copy(amountText = it)) }, amountOptions)
                }
                if (field == RuleConditionField.AMOUNT) {
                    Text(
                        "Use a minus sign for money out, or choose Money out as the field.",
                        color = tandemPalette.muted,
                        fontSize = TandemTypography.caption,
                    )
                }
            }
            else ->
                TextFieldCard(
                    if (condition.op == "matches") "Pattern" else "Text",
                    condition.text,
                    { onChange(condition.copy(text = it)) },
                )
        }
    }
}

@Composable
private fun RuleActionEditor(
    action: RuleActionDraft,
    choices: RuleChoices,
    removable: Boolean,
    onChange: (RuleActionDraft) -> Unit,
    onRemove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
            Column(Modifier.weight(1f)) {
                RuleChoiceField(
                    "Action",
                    action.kind.label,
                    RuleActionKind.entries.map { it.name to it.label },
                    selectedIds = setOf(action.kind.name),
                ) {
                    val next = RuleActionKind.valueOf(it.first())
                    if (next != action.kind) onChange(RuleActionDraft(next))
                }
            }
            if (removable) TandemIconButton(Lucide.Trash2, "Remove action", tint = tandemPalette.muted, onClick = onRemove)
        }
        val kind = action.kind
        when {
            kind == RuleActionKind.SET_CLEARED ->
                SegmentedSelector(listOf("Cleared" to action.cleared, "Not cleared" to !action.cleared)) {
                    onChange(action.copy(cleared = it == 0))
                }
            kind.usesId -> {
                val field = kind.field!!
                RuleChoiceField(
                    field.replaceFirstChar { it.uppercase() },
                    choices.name(field, action.id) ?: "Choose $field",
                    choices.options(field),
                    selectedIds = setOfNotNull(action.id.takeIf { it.isNotBlank() }),
                ) { onChange(action.copy(id = it.first())) }
            }
            else -> TextFieldCard("Notes", action.text, { onChange(action.copy(text = it)) })
        }
    }
}

@Composable
internal fun RuleChoiceField(
    label: String,
    selectedLabel: String,
    options: List<Pair<String, String>>,
    selectedIds: Set<String>,
    multiple: Boolean = false,
    onSelect: (List<String>) -> Unit,
) {
    var expanded by rememberSaveable(label) { mutableStateOf(false) }
    var query by rememberSaveable(label) { mutableStateOf("") }
    var picked by remember(expanded, selectedIds) { mutableStateOf(selectedIds) }
    FieldFrame(label, onClickLabel = "Choose $label", onClick = { expanded = true }) {
        Text(
            selectedLabel,
            Modifier.weight(1f),
            color = tandemPalette.text,
            fontSize = TandemTypography.action,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(Lucide.ChevronDown, contentDescription = null, modifier = Modifier.size(18.dp), tint = tandemPalette.muted)
    }
    val close = {
        expanded = false
        query = ""
    }
    TandemChoiceDialog(visible = expanded, onDismissRequest = close) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 420.dp).padding(horizontal = TandemSpacing.large)) {
            Column(Modifier.padding(TandemSpacing.medium), verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                TandemModalHeader(title = "Choose ${label.lowercase()}", titleSize = TandemTypography.cardTitle, onClose = close)
                if (options.size > SEARCH_THRESHOLD) {
                    TextFieldCard("Search", query, { query = it }, TextFieldOptions(placeholder = "Type to filter"))
                }
                val shown =
                    remember(options, query) {
                        val needle = query.trim().lowercase()
                        if (needle.isEmpty()) options else options.filter { it.second.lowercase().contains(needle) }
                    }
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(shown, key = { it.first }) { (id, name) ->
                        val selected = id in picked
                        ListRow(
                            name,
                            leading = RowLeading.None,
                            options = ListRowOptions(dense = true),
                            trailingContent =
                                if (selected) {
                                    { Icon(Lucide.Check, contentDescription = "Selected", tint = tandemPalette.accent) }
                                } else {
                                    null
                                },
                            onClick = {
                                if (multiple) {
                                    picked = if (selected) picked - id else picked + id
                                } else {
                                    onSelect(listOf(id))
                                    close()
                                }
                            },
                        )
                    }
                }
                if (multiple) {
                    PrimaryButton("Done", enabled = picked.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                        onSelect(options.map { it.first }.filter { it in picked })
                        close()
                    }
                }
            }
        }
    }
}

private const val SEARCH_THRESHOLD = 8
