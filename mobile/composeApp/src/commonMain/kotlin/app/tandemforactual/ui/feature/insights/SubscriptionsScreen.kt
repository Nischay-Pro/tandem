@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import app.tandemforactual.data.planning.Cadence
import app.tandemforactual.data.planning.RecurringCharge
import app.tandemforactual.data.planning.RecurringCharges
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveScheduleSummary
import app.tandemforactual.ui.core.state.RecordNames
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.formatLiveDate
import app.tandemforactual.ui.core.state.rememberBudgetTransactions
import app.tandemforactual.ui.core.state.rememberRecordNames
import app.tandemforactual.ui.design.feedback.EmptyStateCard
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.rows.SkeletonRows
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemAlloFamily
import app.tandemforactual.ui.design.theme.tandemPalette

/** Subscriptions and other charges that repeat, what they cost each month, and which have gone up or stopped. */
@Composable
internal fun SubscriptionsScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    schedules: List<LiveScheduleSummary>,
) {
    val transactions = rememberBudgetTransactions(state, mobileSession)
    val names = mobileSession.rememberRecordNames()
    val today = platformTodayIsoDate()
    val found = remember(transactions, today) { transactions?.let { RecurringCharges.find(it, today) } }
    PageColumn(verticalSpacing = TandemSpacing.large) {
        if (found == null) {
            SkeletonRows()
            return@PageColumn
        }
        val (active, stopped) = found.partition { !it.stopped }
        if (found.isEmpty()) {
            EmptyStateCard(
                "No subscriptions found",
                "Tandem looks for a payee charged at least three times, weekly, monthly, every quarter or yearly, for a similar amount.",
            )
            return@PageColumn
        }
        SubscriptionsHeadline(state, active)
        val scheduled = schedules.mapNotNullTo(mutableSetOf()) { it.payeeId }
        if (active.isNotEmpty()) {
            SectionCard("Active", "Charged on a steady rhythm") {
                ChargeRows(state, active, names) { charge ->
                    listOfNotNull(
                        "Price went up".takeIf { charge.priceRose },
                        cadenceLabel(charge.cadence),
                        "next around ${formatLiveDate(charge.nextDate)}",
                        "scheduled".takeIf { charge.payeeId in scheduled },
                    ).joinToString(" · ")
                }
            }
        }
        if (stopped.isNotEmpty()) {
            SectionCard("Stopped", "No charge for well over a cycle; possibly cancelled") {
                ChargeRows(state, stopped, names) { "Last charged ${formatLiveDate(it.lastDate)}" }
            }
        }
        Text(
            "Found on this device from your transactions. Amounts are the latest charge; the monthly total spreads weekly, " +
                "quarterly and yearly charges across months.",
            color = tandemPalette.muted,
            fontSize = TandemTypography.label,
        )
    }
}

@Composable
private fun SubscriptionsHeadline(
    state: AppState,
    active: List<RecurringCharge>,
) {
    val palette = tandemPalette
    val monthly = active.sumOf { it.monthlyMinor }
    Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
        Text("About each month", color = palette.muted, fontSize = TandemTypography.body)
        Text(
            state.formatCurrency(monthly),
            color = palette.text,
            fontSize = TandemTypography.heroTitle,
            fontWeight = FontWeight.SemiBold,
            fontFamily = tandemAlloFamily(),
        )
        Text(
            "${active.size} active · ${state.formatCurrency(monthly * 12)} a year",
            color = palette.muted,
            fontSize = TandemTypography.compactBody,
        )
    }
}

@Composable
private fun ChargeRows(
    state: AppState,
    charges: List<RecurringCharge>,
    names: RecordNames,
    detail: (RecurringCharge) -> String,
) {
    charges.forEachIndexed { index, charge ->
        if (index > 0) DividerLine()
        val name = names.payees[charge.payeeId] ?: "Unknown payee"
        ListRow(
            name,
            detail(charge),
            state.formatCurrency(charge.amountMinor),
            leading = RowLeading.Payee(name),
            options = ListRowOptions(showChevron = false),
        )
    }
}

private fun cadenceLabel(cadence: Cadence) =
    when (cadence) {
        Cadence.WEEKLY -> "Weekly"
        Cadence.MONTHLY -> "Monthly"
        Cadence.QUARTERLY -> "Every 3 months"
        Cadence.YEARLY -> "Yearly"
    }
