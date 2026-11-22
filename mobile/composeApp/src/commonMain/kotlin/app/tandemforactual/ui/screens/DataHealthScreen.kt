@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.actual.HealthIssue
import app.tandemforactual.data.actual.HealthIssueKind
import app.tandemforactual.data.actual.HealthRow
import app.tandemforactual.ui.components.AnimatedReveal
import app.tandemforactual.ui.components.DividerLine
import app.tandemforactual.ui.components.EmptyState
import app.tandemforactual.ui.components.ListRow
import app.tandemforactual.ui.components.ListRowOptions
import app.tandemforactual.ui.components.RowLeading
import app.tandemforactual.ui.components.SectionCard
import app.tandemforactual.ui.components.TandemLoadingIndicator
import com.composables.icons.lucide.CalendarClock
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
    var issues by remember(state.selectedBudgetId) { mutableStateOf<List<HealthIssue>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(state.selectedBudgetId, reload) {
        loading = true
        issues = mobileSession.loadDataHealth(state.selectedBudgetId, state.encryptedPassword.takeIf { it.isNotBlank() })
        loading = false
    }
    val palette = tandemPalette
    PageLazyColumn(isRefreshing = loading && issues != null, onRefresh = { reload++ }) {
        val found = issues
        item(key = "summary") {
            Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
                Text(state.selectedBudget, color = palette.muted, fontSize = TandemTypography.body)
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
                val open = expanded == issue.kind.name
                SectionCard(issue.kind.title(issue.rows.size), issue.kind.detail(), if (open) "Hide" else "Show", onClick = {
                    expanded = if (open) null else issue.kind.name
                }) {
                    AnimatedReveal(visible = open) {
                        Column {
                            issue.rows.take(MAX_ROWS).forEachIndexed { index, row ->
                                if (index > 0) DividerLine()
                                HealthRowItem(issue.kind, row, state, liveTransactions.firstOrNull { it.recordId == row.id }) { item ->
                                    openTransaction(state, navigation, item, isLive = true)
                                }
                            }
                            if (issue.rows.size > MAX_ROWS) {
                                Text(
                                    "and ${issue.rows.size - MAX_ROWS} more",
                                    color = palette.muted,
                                    fontSize = TandemTypography.compactBody,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val MAX_ROWS = 25

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
