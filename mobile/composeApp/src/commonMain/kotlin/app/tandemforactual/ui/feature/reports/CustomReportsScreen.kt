@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.reports
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.tandemforactual.data.planning.CustomReports
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.incomeCategoryIds
import app.tandemforactual.ui.core.state.rememberBudgetTransactions
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.theme.TandemSpacing
import com.composables.icons.lucide.ChartBar
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus

/** Every report the person has built and saved, with a way to start a new one. */
@Composable
internal fun CustomReportsScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
) {
    val reports = mobileSession.planning.customReports
    val transactions = rememberBudgetTransactions(state, mobileSession)
    val income = remember(mobileSession.categoryRecords, mobileSession.categoryGroupRecords) { mobileSession.incomeCategoryIds() }
    val today = platformTodayIsoDate()
    val totals =
        remember(reports, transactions, income, today) {
            transactions?.let { list -> reports.associate { it.id to CustomReports.run(it, list, income, today).totalMinor } }
        }

    fun open(id: String) {
        state.selectedCustomReportId = id
        state.selectedCustomReportName = reports.firstOrNull { it.id == id }?.name.orEmpty()
        navigation.open(AppRoute.CUSTOM_REPORT)
    }
    PageColumn(verticalSpacing = TandemSpacing.large) {
        if (reports.isEmpty()) {
            EmptyState(
                "Build your own report",
                "Pick a period, group by category, payee or account, and filter down to what you want to see. Save it to come back later.",
                actionLabel = "New report",
                onAction = { open("") },
            )
            return@PageColumn
        }
        TandemActionButton("New report", TandemActionButtonTone.PRIMARY, Modifier.fillMaxWidth(), icon = Lucide.Plus) { open("") }
        SectionCard("Saved reports", "${reports.size} saved") {
            reports.forEachIndexed { index, report ->
                if (index > 0) DividerLine()
                ListRow(
                    report.name,
                    report.summary(),
                    totals?.get(report.id)?.let(state::formatCurrency),
                    leading = RowLeading.Icon(Lucide.ChartBar),
                    onClick = { open(report.id) },
                )
            }
        }
    }
}
