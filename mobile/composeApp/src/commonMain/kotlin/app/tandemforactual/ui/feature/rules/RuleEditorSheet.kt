@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.rules
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import app.tandemforactual.ui.design.templates.readablePadding
import app.tandemforactual.ui.design.theme.TandemLayout
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

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
                RuleStageCard(draft) { draft = it }
                RuleConditionsCard(draft, choices, { draft = it }, { error = null })
                RuleActionsCard(draft, choices, { draft = it }, { error = null })
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
                        if (saveRule(mobileSession, state.selectedBudgetId, ruleId, body, password)) onDone()
                    }
                }
            }
            if (ruleId.isNotBlank()) {
                SecondaryButton("Delete rule", modifier = Modifier.fillMaxWidth(), enabled = canEdit && !busy) { confirmDelete = true }
            }
        }
    }
    if (confirmDelete) {
        DeleteRuleDialog(busy, onDismiss = { confirmDelete = false }) {
            scope.launch {
                val deleted = mobileSession.budgetEdits.deleteBudgetRule(state.selectedBudgetId, ruleId, password)
                confirmDelete = false
                if (deleted) onDone()
            }
        }
    }
}

private suspend fun saveRule(
    mobileSession: TandemMobileSession,
    budgetId: String,
    ruleId: String,
    body: JsonObject,
    password: String?,
): Boolean =
    if (ruleId.isBlank()) {
        mobileSession.budgetEdits.createBudgetRule(budgetId, body, password)
    } else {
        mobileSession.budgetEdits.editBudgetRule(budgetId, ruleId, body, password)
    }

/** Chooses whether the rule runs before, with or after the default rules. */
@Composable
private fun RuleStageCard(
    draft: RuleDraft,
    onDraftChange: (RuleDraft) -> Unit,
) {
    SectionCard("Run order") {
        Column(Modifier.padding(TandemSpacing.medium), verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
            SegmentedSelector(RuleStage.entries.map { it.label to (it == draft.stage) }) {
                onDraftChange(draft.copy(stage = RuleStage.entries[it]))
            }
            Text(draft.stage.detail, color = tandemPalette.muted, fontSize = TandemTypography.caption)
        }
    }
}

/** The rule's conditions and whether all or any of them must match. */
@Composable
private fun RuleConditionsCard(
    draft: RuleDraft,
    choices: RuleChoices,
    onDraftChange: (RuleDraft) -> Unit,
    onEdited: () -> Unit,
) {
    SectionCard("If") {
        Column(Modifier.padding(TandemSpacing.medium), verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
            SegmentedSelector(listOf("All match" to draft.matchAll, "Any match" to !draft.matchAll)) {
                onDraftChange(draft.copy(matchAll = it == 0))
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
                        onDraftChange(draft.copy(conditions = draft.conditions.toMutableList().also { it[index] = next }))
                        onEdited()
                    },
                    onRemove = { onDraftChange(draft.copy(conditions = draft.conditions.filterIndexed { i, _ -> i != index })) },
                )
            }
        }
        DividerLine()
        ListRow(
            "Add condition",
            leading = RowLeading.Icon(Lucide.Plus),
            options = ListRowOptions(dense = true),
            onClick = { onDraftChange(draft.copy(conditions = draft.conditions + RuleConditionDraft())) },
        )
    }
}

/** The actions the rule applies to matching transactions. */
@Composable
private fun RuleActionsCard(
    draft: RuleDraft,
    choices: RuleChoices,
    onDraftChange: (RuleDraft) -> Unit,
    onEdited: () -> Unit,
) {
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
                        onDraftChange(draft.copy(actions = draft.actions.toMutableList().also { it[index] = next }))
                        onEdited()
                    },
                    onRemove = { onDraftChange(draft.copy(actions = draft.actions.filterIndexed { i, _ -> i != index })) },
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
                onDraftChange(draft.copy(actions = draft.actions + RuleActionDraft(unused ?: RuleActionKind.APPEND_NOTES)))
            },
        )
    }
}

/** Confirms deleting the rule being edited. */
@Composable
private fun DeleteRuleDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    DestructiveConfirmationDialog(
        title = "Delete this rule?",
        message = "Actual will stop applying it to new and imported transactions. Transactions it already changed stay as they are.",
        confirmLabel = "Delete",
        dismissLabel = "Keep rule",
        enabled = !busy,
        onDismissRequest = onDismiss,
        onConfirm = onConfirm,
    )
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
                    leading = RowLeading.Payee(row.name),
                    options = ListRowOptions(showChevron = false, dense = true),
                )
            }
        }
    }
}
