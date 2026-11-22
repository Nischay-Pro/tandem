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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.actual.HealthIssueKind
import app.tandemforactual.data.actual.HealthRow
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.formatLiveDate
import app.tandemforactual.ui.design.feedback.AnimatedReveal
import app.tandemforactual.ui.design.feedback.EmptyState
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
import app.tandemforactual.ui.feature.transactions.openTransaction
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
        mobileSession.insights.loadDataHealth(state.selectedBudgetId, state.encryptedPassword.takeIf { it.isNotBlank() })
        loading = false
    }
    val issues = cached
    val palette = tandemPalette
    PageLazyColumn(isRefreshing = loading && issues != null && reload > 0, onRefresh = { reload++ }) {
        val found = issues
        item(key = "summary") {
            Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
                Text(
                    if (loading && found != null) "Checking again…" else state.selectedBudget,
                    color = palette.muted,
                    fontSize = TandemTypography.body,
                )
                Text(
                    when {
                        found == null && loading -> "Checking…"
                        found == null -> "Couldn’t check"
                        found.isEmpty() -> "All clear"
                        else -> "${found.sumOf { it.rows.size }} things to look at"
                    },
                    color = palette.text,
                    fontSize = TandemTypography.heroTitle,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = tandemAlloFamily(),
                )
            }
        }
        when {
            found == null && loading -> item(key = "loading") { TandemLoadingIndicator(Modifier.size(24.dp), palette.accent, 2.dp) }
            found == null ->
                item(key = "error") {
                    EmptyState("Couldn’t check this budget", "Open it while connected to Actual, then pull to try again.")
                }
            found.isEmpty() ->
                item(key = "clear") {
                    SectionCard("Nothing needs attention", "Categories, payees and reconciliation all look good") {
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
                SectionCard(issue.kind.title(issue.rows.size), issue.kind.detail(), if (open) "Hide" else "Show", onClick = {
                    expanded = if (open) expanded - issue.kind.name else expanded + issue.kind.name
                }) {
                    AnimatedReveal(visible = open) {
                        Column {
                            issue.rows.take(limit).forEachIndexed { index, row ->
                                if (index > 0) DividerLine()
                                HealthRowItem(issue.kind, row, state, liveTransactions.firstOrNull { it.recordId == row.id }) { item ->
                                    openTransaction(state, navigation, item, isLive = true)
                                }
                            }
                            if (issue.rows.size > limit) {
                                DividerLine()
                                ListRow(
                                    "Show ${minOf(PAGE, issue.rows.size - limit)} more",
                                    "${issue.rows.size - limit} not shown",
                                    leading = RowLeading.Icon(Lucide.ChevronDown),
                                    options = ListRowOptions(showChevron = false, dense = true),
                                    onClick = { shown[issue.kind.name] = limit + PAGE },
                                )
                            }
                        }
                    }
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
    transaction: TransactionItem?,
    onOpen: (TransactionItem) -> Unit,
) {
    val detail = listOfNotNull(row.date?.let(::formatLiveDate), row.account).joinToString(" · ").ifBlank { null }
    ListRow(
        row.name ?: if (row.amount != null) "No payee" else "Unnamed",
        detail,
        row.amount?.let(state::formatCurrency),
        leading = RowLeading.Icon(kind.icon()),
        options = ListRowOptions(showChevron = transaction != null),
        onClick = transaction?.let { { onOpen(it) } },
    )
}
