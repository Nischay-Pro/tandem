@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.checkup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.actual.HealthIssue
import app.tandemforactual.data.actual.HealthIssueKind
import app.tandemforactual.data.actual.HealthRow
import app.tandemforactual.data.actual.RuleFinding
import app.tandemforactual.ui.core.format.RuleText
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.format.ruleNamesByField
import app.tandemforactual.ui.core.format.ruleText
import app.tandemforactual.ui.core.navigation.openTransaction
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.deleteBudgetRule
import app.tandemforactual.ui.core.session.budget.editBudgetRule
import app.tandemforactual.ui.core.session.budget.manageBudgetPayee
import app.tandemforactual.ui.core.session.transactions.deleteTransactionsWithUndo
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.formatLiveDate
import app.tandemforactual.ui.core.state.recordBody
import app.tandemforactual.ui.design.feedback.AnimatedReveal
import app.tandemforactual.ui.design.feedback.EmptyStateCard
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.feedback.TandemLoadingIndicator
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.PageLazyColumn
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemAlloFamily
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.CalendarClock
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.CircleDashed
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Scale
import com.composables.icons.lucide.Store
import com.composables.icons.lucide.Tags
import com.composables.icons.lucide.UserX
import kotlinx.coroutines.launch

private fun HealthIssueKind.icon(): ImageVector =
    when (this) {
        HealthIssueKind.UNCATEGORIZED -> Lucide.Tags
        HealthIssueKind.POSSIBLE_DUPLICATES -> Lucide.Copy
        HealthIssueKind.FUTURE_DATED -> Lucide.CalendarClock
        HealthIssueKind.OLD_UNCLEARED -> Lucide.CircleDashed
        HealthIssueKind.NOT_RECONCILED -> Lucide.Scale
        HealthIssueKind.MISSING_PAYEE -> Lucide.UserX
        HealthIssueKind.UNUSED_PAYEES -> Lucide.Store
    }

private fun HealthIssueKind.title(count: Int): String =
    when (this) {
        HealthIssueKind.UNCATEGORIZED -> "$count uncategorized"
        HealthIssueKind.POSSIBLE_DUPLICATES -> "$count possible duplicates"
        HealthIssueKind.FUTURE_DATED -> "$count dated in the future"
        HealthIssueKind.OLD_UNCLEARED -> "$count uncleared for over a month"
        HealthIssueKind.NOT_RECONCILED -> if (count == 1) "1 account to reconcile" else "$count accounts to reconcile"
        HealthIssueKind.MISSING_PAYEE -> "$count without a payee"
        HealthIssueKind.UNUSED_PAYEES -> if (count == 1) "1 unused payee" else "$count unused payees"
    }

private fun HealthIssueKind.detail(): String =
    when (this) {
        HealthIssueKind.UNCATEGORIZED -> "On-budget transactions that don’t count toward any category yet"
        HealthIssueKind.POSSIBLE_DUPLICATES -> "Same account, date, payee and amount"
        HealthIssueKind.FUTURE_DATED -> "Not from a schedule, so they may be typos"
        HealthIssueKind.OLD_UNCLEARED -> "Check them against your bank and clear them"
        HealthIssueKind.NOT_RECONCILED -> "No reconciliation in the last 60 days"
        HealthIssueKind.MISSING_PAYEE -> "Adding a payee helps rules and reports"
        HealthIssueKind.UNUSED_PAYEES -> "No transactions or rules use them"
    }

/** A checkup of the budget's bookkeeping: what looks off and where, with each item one tap away. */
@Composable
internal fun DataHealthScreen(
    state: AppState,
    navigation: AppNavigation,
    mobileSession: TandemMobileSession,
    liveTransactions: List<TransactionItem>,
) {
    val cached =
        mobileSession.insights.dataHealthCache
            ?.takeIf { it.first == state.selectedBudgetId }
            ?.second
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }
    var expanded by rememberSaveable { mutableStateOf(listOf<String>()) }
    val shown = remember { mutableStateMapOf<String, Int>() }
    LaunchedEffect(state.selectedBudgetId, reload) {
        loading = true
        loadCheckup(mobileSession, state)
        loading = false
    }
    val ignored =
        mobileSession.planning.settings.ignoredChecks
            .toSet()
    val issues =
        cached?.mapNotNull { issue ->
            val rows = issue.rows.filterNot { "${issue.kind.name}:${it.id}" in ignored }
            val ordered = if (issue.kind == HealthIssueKind.POSSIBLE_DUPLICATES) rows.sortedWith(duplicateOrder) else rows
            ordered.takeIf { it.isNotEmpty() }?.let { HealthIssue(issue.kind, it) }
        }
    val ruleFindings = visibleRuleFindings(mobileSession, state.selectedBudgetId, ignored)
    val ruleTexts = rememberRuleTexts(mobileSession)
    var acting by remember { mutableStateOf<Pair<HealthIssue, HealthRow>?>(null) }
    var openFinding by remember { mutableStateOf<RuleFinding?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    val palette = tandemPalette
    PageLazyColumn(isRefreshing = loading && issues != null && reload > 0, onRefresh = { reload++ }) {
        val found = issues
        item(key = "summary") { DataHealthSummary(state.selectedBudget, found, ruleFindings.size, loading) }
        failure?.let { text -> item(key = "failure") { InlineMessage(text, InlineMessageKind.ERROR, onDismiss = { failure = null }) } }
        when {
            found == null && loading -> item(key = "loading") { TandemLoadingIndicator(Modifier.size(24.dp), palette.accent, 2.dp) }
            found == null ->
                item(key = "error") {
                    EmptyStateCard("Couldn’t check this budget", "Open it while connected to Actual, then pull to try again.")
                }
            found.isEmpty() && ruleFindings.isEmpty() ->
                item(key = "clear") {
                    SectionCard("Nothing needs attention", "Categories, payees, rules and reconciliation all look good") {
                        ListRow(
                            "Checked just now",
                            leading = RowLeading.Icon(Lucide.CircleCheck),
                            options = ListRowOptions(showChevron = false),
                        )
                    }
                }
        }
        found?.forEach { issue ->
            item(key = issue.kind.name) {
                val open = issue.kind.name in expanded
                val limit = shown[issue.kind.name] ?: PAGE
                HealthIssueCard(
                    issue,
                    open = open,
                    limit = limit,
                    onToggle = { expanded = if (open) expanded - issue.kind.name else expanded + issue.kind.name },
                    onShowMore = { shown[issue.kind.name] = limit + PAGE },
                    state = state,
                    liveTransactions = liveTransactions,
                    onAct = { row -> acting = issue to row },
                ) { item -> openTransaction(state, navigation, item, isLive = true) }
            }
        }
        if (ruleFindings.isNotEmpty()) {
            item(key = "rules") { RuleFindingsSection(ruleFindings, ruleTexts) { openFinding = it } }
        }
    }
    CheckupActions(state, navigation, mobileSession, liveTransactions, acting, { acting = null }) { failure = it }
    RuleFindingActions(state, navigation, mobileSession, openFinding, ruleTexts, { openFinding = null }, { reload++ }) { failure = it }
}

private suspend fun loadCheckup(
    mobileSession: TandemMobileSession,
    state: AppState,
) {
    val password = state.encryptedPassword.takeIf { it.isNotBlank() }
    mobileSession.insights.loadDataHealth(state.selectedBudgetId, password)
    mobileSession.insights.loadRuleFindings(state.selectedBudgetId, password)
}

private fun visibleRuleFindings(
    mobileSession: TandemMobileSession,
    budgetId: String,
    ignored: Set<String>,
): List<RuleFinding> =
    mobileSession.insights.ruleFindingsCache
        ?.takeIf { it.first == budgetId }
        ?.second
        .orEmpty()
        .filterNot { it.key in ignored }

@Composable
private fun rememberRuleTexts(mobileSession: TandemMobileSession): Map<String, RuleText> {
    val rules = mobileSession.ruleRecords
    val accounts = mobileSession.accountRecords
    val categories = mobileSession.categoryRecords
    val groups = mobileSession.categoryGroupRecords
    val payees = mobileSession.payeeRecords
    val names = remember(accounts, categories, groups, payees) { ruleNamesByField(accounts, categories, groups, payees) }
    return remember(rules, names) { rules.associate { it.recordId to ruleText(it.recordBody(), names) } }
}

@Composable
private fun RuleFindingActions(
    state: AppState,
    navigation: AppNavigation,
    mobileSession: TandemMobileSession,
    finding: RuleFinding?,
    rules: Map<String, RuleText>,
    onDone: () -> Unit,
    onChanged: () -> Unit,
    onFailure: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val password = state.encryptedPassword.takeIf { it.isNotBlank() }
    val budgetId = state.selectedBudgetId
    RuleFindingDialog(
        finding,
        rules,
        canEdit = mobileSession.canEditRules && !state.readOnlyMode,
        onDismiss = onDone,
        onEdit = { id ->
            onDone()
            state.ruleEditorRuleId = id
            navigation.open(AppRoute.BUDGET_RULES)
        },
        onMerge = { shown ->
            onDone()
            scope.launch {
                val edits = mobileSession.budgetEdits
                val kept = shown.merged?.let { edits.editBudgetRule(budgetId, shown.ruleId, it, password) } ?: true
                val merged = kept && shown.related.all { edits.deleteBudgetRule(budgetId, it, password) }
                if (merged) {
                    mobileSession.insights.dropRuleFindings(setOf(shown.key))
                    onChanged()
                } else {
                    onFailure(mobileSession.budgetActionMessage ?: "Tandem couldn’t merge these rules. Pull to check again.")
                }
            }
        },
        onIgnore = { shown ->
            onDone()
            scope.launch { mobileSession.planning.ignoreChecks(budgetId, listOf(shown.key)) }
        },
    )
}

@Composable
private fun CheckupActions(
    state: AppState,
    navigation: AppNavigation,
    mobileSession: TandemMobileSession,
    liveTransactions: List<TransactionItem>,
    acting: Pair<HealthIssue, HealthRow>?,
    onDone: () -> Unit,
    onFailure: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val password = state.encryptedPassword.takeIf { it.isNotBlank() }
    val transaction = acting?.second?.let { row -> transactionFor(row, liveTransactions) }
    CheckupActionDialog(
        acting,
        acting?.first?.kind?.icon(),
        canOpen = transaction != null,
        onDismiss = onDone,
        onOpen = {
            onDone()
            transaction?.let { openTransaction(state, navigation, it, isLive = true) }
        },
        onDelete = { group ->
            val (issue, row) = acting ?: return@CheckupActionDialog
            onDone()
            scope.launch {
                val deleted =
                    if (issue.kind == HealthIssueKind.UNUSED_PAYEES) {
                        mobileSession.budgetEdits.manageBudgetPayee(state.selectedBudgetId, row.id, "delete", null, null, password)
                    } else {
                        mobileSession.transactions.deleteTransactionsWithUndo(
                            state.selectedBudgetId,
                            setOf(transaction?.recordId ?: row.id),
                        )
                    }
                if (deleted) {
                    mobileSession.insights.dropHealthRows(
                        issue.kind,
                        if (group.size <=
                            2
                        ) {
                            group.map { it.id }.toSet()
                        } else {
                            setOf(row.id)
                        },
                    )
                }
                if (!deleted) {
                    onFailure(
                        mobileSession.transactionWriteMessage
                            ?: mobileSession.budgetActionMessage
                            ?: "Tandem couldn’t delete it. Pull to check again.",
                    )
                }
            }
        },
        onIgnore = { group ->
            val kind = acting?.first?.kind ?: return@CheckupActionDialog
            onDone()
            scope.launch { mobileSession.planning.ignoreChecks(state.selectedBudgetId, group.map { "${kind.name}:${it.id}" }) }
        },
    )
}

/** The budget name and how many things the checkup found. */
@Composable
private fun DataHealthSummary(
    budgetName: String,
    found: List<HealthIssue>?,
    ruleFindings: Int,
    loading: Boolean,
) {
    val palette = tandemPalette
    Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
        Text(
            if (loading && found != null) "Checking again…" else budgetName,
            color = palette.muted,
            fontSize = TandemTypography.body,
        )
        Text(
            when {
                found == null && loading -> "Checking…"
                found == null -> "Couldn’t check"
                found.isEmpty() && ruleFindings == 0 -> "All clear"
                else -> "${found.sumOf { it.rows.size } + ruleFindings} things to look at"
            },
            color = palette.text,
            fontSize = TandemTypography.heroTitle,
            fontWeight = FontWeight.SemiBold,
            fontFamily = tandemAlloFamily(),
        )
    }
}

/** One kind of issue, expandable to its rows a page at a time. */
@Composable
private fun HealthIssueCard(
    issue: HealthIssue,
    open: Boolean,
    limit: Int,
    onToggle: () -> Unit,
    onShowMore: () -> Unit,
    state: AppState,
    liveTransactions: List<TransactionItem>,
    onAct: (HealthRow) -> Unit,
    onOpen: (TransactionItem) -> Unit,
) {
    SectionCard(issue.kind.title(issue.rows.size), issue.kind.detail(), if (open) "Hide" else "Show", onClick = onToggle) {
        AnimatedReveal(visible = open) {
            Column {
                issue.rows.take(limit).forEachIndexed { index, row ->
                    if (index > 0) DividerLine()
                    val transaction = transactionFor(row, liveTransactions)
                    HealthRowItem(issue.kind, row, state, transaction != null || issue.kind.hasActions()) {
                        when {
                            issue.kind.hasActions() -> onAct(row)
                            transaction != null -> onOpen(transaction)
                        }
                    }
                }
                if (issue.rows.size > limit) {
                    DividerLine()
                    ListRow(
                        "Show ${minOf(PAGE, issue.rows.size - limit)} more",
                        "${issue.rows.size - limit} not shown",
                        leading = RowLeading.Icon(Lucide.ChevronDown),
                        options = ListRowOptions(showChevron = false, dense = true),
                        onClick = onShowMore,
                    )
                }
            }
        }
    }
}

private const val PAGE = 25

@Composable
private fun HealthRowItem(
    kind: HealthIssueKind,
    row: HealthRow,
    state: AppState,
    clickable: Boolean,
    onClick: () -> Unit,
) {
    val detail = listOfNotNull(row.date?.let(::formatLiveDate), row.account).joinToString(" · ").ifBlank { null }
    ListRow(
        row.name ?: if (row.amount != null) "No payee" else "Unnamed",
        detail,
        row.amount?.let(state::formatCurrency),
        leading = RowLeading.Icon(kind.icon()),
        options = ListRowOptions(showChevron = clickable),
        onClick = if (clickable) onClick else null,
    )
}
