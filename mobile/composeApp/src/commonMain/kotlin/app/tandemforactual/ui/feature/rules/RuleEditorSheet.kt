@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.rules
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.ui.core.format.formatAmount
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.format.parseAmountMinorUnits
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.createBudgetRule
import app.tandemforactual.ui.core.session.budget.deleteBudgetRule
import app.tandemforactual.ui.core.session.budget.editBudgetRule
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.formatLiveDate
import app.tandemforactual.ui.core.state.parseAmountMinorUnits
import app.tandemforactual.ui.core.state.recordBody
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.design.buttons.PrimaryButton
import app.tandemforactual.ui.design.buttons.SecondaryButton
import app.tandemforactual.ui.design.buttons.SegmentedSelector
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.sheets.DestructiveConfirmationDialog
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.templates.readablePadding
import app.tandemforactual.ui.design.theme.TandemLayout
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
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
            mobileSession.feedback.clearBudgetActionMessage()
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

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(
                    horizontal = readablePadding(maxWidth, TandemSpacing.screen, TandemLayout.readableContentWidth),
                    vertical = TandemSpacing.medium,
                ),
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
                SectionCard("Run order") {
                    Column(Modifier.padding(TandemSpacing.medium), verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        SegmentedSelector(RuleStage.entries.map { it.label to (it == draft.stage) }) {
                            draft = draft.copy(stage = RuleStage.entries[it])
                        }
                        Text(draft.stage.detail, color = tandemPalette.muted, fontSize = TandemTypography.caption)
                    }
                }
                SectionCard("If") {
                    Column(Modifier.padding(TandemSpacing.medium), verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        SegmentedSelector(listOf("All match" to draft.matchAll, "Any match" to !draft.matchAll)) {
                            draft = draft.copy(matchAll = it == 0)
                        }
                        Text(
                            if (draft.matchAll) "Every condition must match the transaction." else "One matching condition is enough.",
                            color = tandemPalette.muted,
                            fontSize = TandemTypography.caption,
                        )
                    }
                    Column {
                        draft.conditions.forEachIndexed { index, condition ->
                            DividerLine()
                            RuleConditionEditor(
                                number = index + 1,
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
                    Column {
                        draft.actions.forEachIndexed { index, action ->
                            if (index > 0) DividerLine()
                            RuleActionEditor(
                                number = index + 1,
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
                RulePreview(state, mobileSession, draft)
                error?.let { InlineMessage(it, InlineMessageKind.ERROR) }
                mobileSession.budgetActionMessage?.let { InlineMessage(it, mobileSession.feedback.budgetActionMessageKind) }
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
                                mobileSession.budgetEdits.createBudgetRule(state.selectedBudgetId, body, password)
                            } else {
                                mobileSession.budgetEdits.editBudgetRule(state.selectedBudgetId, ruleId, body, password)
                            }
                        if (saved) onDone()
                    }
                }
            }
            if (ruleId.isNotBlank()) {
                SecondaryButton("Delete rule", modifier = Modifier.fillMaxWidth(), enabled = canEdit && !busy) { confirmDelete = true }
            }
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
                    val deleted = mobileSession.budgetEdits.deleteBudgetRule(state.selectedBudgetId, ruleId, password)
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
private fun RuleItemHeader(
    title: String,
    removable: Boolean,
    removeLabel: String,
    onRemove: () -> Unit,
) {
    if (!removable) return
    Row(
        Modifier.fillMaxWidth().padding(start = TandemLayout.listRowHorizontalInset, end = TandemSpacing.small, top = TandemSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), color = tandemPalette.muted, fontSize = TandemTypography.caption, fontWeight = FontWeight.SemiBold)
        TandemIconButton(Lucide.Trash2, removeLabel, sizeDp = 36, iconSizeDp = 18, tint = tandemPalette.muted, onClick = onRemove)
    }
}

@Composable
private fun RuleConditionEditor(
    number: Int,
    condition: RuleConditionDraft,
    choices: RuleChoices,
    removable: Boolean,
    onChange: (RuleConditionDraft) -> Unit,
    onRemove: () -> Unit,
) {
    Column {
        RuleItemHeader("Condition $number", removable, "Remove condition $number", onRemove)
        RuleChoiceField(
            "Field",
            condition.field.label,
            RuleConditionField.entries.map { it.name to it.label },
            selectedIds = setOf(condition.field.name),
        ) { onChange(condition.withField(RuleConditionField.valueOf(it.first()))) }
        DividerLine()
        RuleChoiceField(
            "Comparison",
            ruleOperatorLabel(condition.op),
            condition.field.ops.map { it to ruleOperatorLabel(it) },
            selectedIds = setOf(condition.op),
        ) { onChange(condition.withOp(it.first())) }
        val field = condition.field
        if (field.usesIds) {
            DividerLine()
            val multiple = condition.op in setOf("oneOf", "notOneOf")
            val label =
                condition.ids
                    .mapNotNull { choices.name(field.wire, it) }
                    .joinToString(", ")
                    .ifBlank { "Choose" }
            RuleChoiceField(
                field.label,
                label,
                choices.options(field.wire),
                selectedIds = condition.ids.toSet(),
                multiple = multiple,
            ) { onChange(condition.copy(ids = it)) }
        } else {
            Column(
                Modifier.padding(start = TandemSpacing.medium, end = TandemSpacing.medium, bottom = TandemSpacing.medium),
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
            ) {
                if (field.usesAmount) {
                    val amountOptions = TextFieldOptions(keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    if (condition.op == "isbetween") {
                        Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                            Column(Modifier.weight(1f)) {
                                TextFieldCard("From", condition.amountText, { onChange(condition.copy(amountText = it)) }, amountOptions)
                            }
                            Column(Modifier.weight(1f)) {
                                TextFieldCard(
                                    "To",
                                    condition.amountMaxText,
                                    { onChange(condition.copy(amountMaxText = it)) },
                                    amountOptions,
                                )
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
                } else {
                    TextFieldCard(
                        if (condition.op == "matches") "Pattern" else "Text",
                        condition.text,
                        { onChange(condition.copy(text = it)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleActionEditor(
    number: Int,
    action: RuleActionDraft,
    choices: RuleChoices,
    removable: Boolean,
    onChange: (RuleActionDraft) -> Unit,
    onRemove: () -> Unit,
) {
    Column {
        RuleItemHeader("Action $number", removable, "Remove action $number", onRemove)
        RuleChoiceField(
            "Action",
            action.kind.label,
            RuleActionKind.entries.map { it.name to it.label },
            selectedIds = setOf(action.kind.name),
        ) {
            val next = RuleActionKind.valueOf(it.first())
            if (next != action.kind) onChange(RuleActionDraft(next))
        }
        val kind = action.kind
        when {
            kind == RuleActionKind.SET_CLEARED ->
                Column(Modifier.padding(start = TandemSpacing.medium, end = TandemSpacing.medium, bottom = TandemSpacing.medium)) {
                    SegmentedSelector(listOf("Cleared" to action.cleared, "Not cleared" to !action.cleared)) {
                        onChange(action.copy(cleared = it == 0))
                    }
                }
            kind.usesId -> {
                DividerLine()
                val field = kind.field!!
                RuleChoiceField(
                    field.replaceFirstChar { it.uppercase() },
                    choices.name(field, action.id) ?: "Choose",
                    choices.options(field),
                    selectedIds = setOfNotNull(action.id.takeIf { it.isNotBlank() }),
                ) { onChange(action.copy(id = it.first())) }
            }
            else ->
                Column(Modifier.padding(start = TandemSpacing.medium, end = TandemSpacing.medium, bottom = TandemSpacing.medium)) {
                    TextFieldCard("Notes", action.text, { onChange(action.copy(text = it)) })
                }
        }
    }
}

/** Shows which existing transactions the rule's conditions match while it's being edited. */
@Composable
private fun RulePreview(
    state: AppState,
    mobileSession: TandemMobileSession,
    draft: RuleDraft,
) {
    var matches by remember { mutableStateOf<List<app.tandemforactual.data.actual.HealthRow>?>(null) }
    androidx.compose.runtime.LaunchedEffect(draft) {
        kotlinx.coroutines.delay(400)
        val body = draft.toPreviewBody { text -> state.parseAmountMinorUnits(text) }
        matches =
            body?.let {
                mobileSession.insights.previewRule(
                    state.selectedBudgetId,
                    it,
                    state.encryptedPassword.takeIf { p ->
                        p.isNotBlank()
                    },
                )
            }
    }
    val found = matches ?: return
    SectionCard(
        when (found.size) {
            0 -> "Matches no transactions yet"
            1 -> "Matches 1 transaction"
            50 -> "Matches 50 or more transactions"
            else -> "Matches ${found.size} transactions"
        },
        "Newest first, from this budget",
    ) {
        Column {
            found.take(5).forEachIndexed { index, row ->
                if (index > 0) DividerLine()
                ListRow(
                    row.name ?: "No payee",
                    listOfNotNull(row.date?.let(::formatLiveDate), row.account).joinToString(" · "),
                    row.amount?.let(state::formatCurrency),
                    leading = RowLeading.None,
                    options = ListRowOptions(showChevron = false, dense = true),
                )
            }
        }
    }
}
