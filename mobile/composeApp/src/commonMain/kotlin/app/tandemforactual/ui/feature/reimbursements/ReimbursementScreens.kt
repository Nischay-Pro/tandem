@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.reimbursements
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.formatLiveDate
import app.tandemforactual.ui.design.cards.StatTile
import app.tandemforactual.ui.design.feedback.EmptyStateCard
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.theme.TandemSpacing
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.HandCoins
import com.composables.icons.lucide.Lucide
import kotlinx.coroutines.launch

/** Purchases someone will pay back: what's still owed, from whom, and what has come in. */
@Composable
internal fun ReimbursementsScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
) {
    val scope = rememberCoroutineScope()
    val all = mobileSession.planning.reimbursements.sortedByDescending { it.date }
    val pending = all.filterNot { it.received }
    val received = all.filter { it.received }
    PageColumn(verticalSpacing = TandemSpacing.large) {
        Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
            StatTile("Still owed", state.formatCurrency(pending.sumOf { it.amountMinor }), "${pending.size} pending", Modifier.weight(1f))
            StatTile("Paid back", state.formatCurrency(received.sumOf { it.amountMinor }), "${received.size} received", Modifier.weight(1f))
        }
        if (all.isEmpty()) {
            EmptyStateCard("Nothing to be paid back", "Open a purchase and choose Expect a reimbursement to track it here.")
        }
        if (pending.isNotEmpty()) {
            SectionCard("Pending", "Tap one when the money arrives") {
                pending.forEachIndexed { index, item ->
                    if (index > 0) DividerLine()
                    ListRow(
                        item.description ?: "Purchase",
                        listOfNotNull(item.from?.let { "From $it" }, item.date?.let(::formatLiveDate)).joinToString(" · ").ifBlank { null },
                        state.formatCurrency(item.amountMinor),
                        leading = RowLeading.Payee(item.description, Lucide.HandCoins),
                        onClick = {
                            scope.launch {
                                mobileSession.planning.saveReimbursement(
                                    state.selectedBudgetId,
                                    item.copy(receivedOn = platformTodayIsoDate()),
                                )
                            }
                        },
                    )
                }
            }
        }
        if (received.isNotEmpty()) {
            SectionCard("Received", null) {
                received.forEachIndexed { index, item ->
                    if (index > 0) DividerLine()
                    ListRow(
                        item.description ?: "Purchase",
                        listOfNotNull(
                            item.from,
                            item.receivedOn?.let { "Paid back ${formatLiveDate(it)}" },
                        ).joinToString(" · ").ifBlank { null },
                        state.formatCurrency(item.amountMinor),
                        leading = RowLeading.Icon(Lucide.CircleCheck),
                        options = ListRowOptions(showChevron = false, muted = true),
                    )
                }
            }
        }
    }
}
