@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.schedules
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.format.amountLabel
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveScheduleSummary
import app.tandemforactual.ui.core.state.formatTransactionListDate
import app.tandemforactual.ui.core.state.isBudgetSectionCollapsed
import app.tandemforactual.ui.core.state.toggleBudgetSection
import app.tandemforactual.ui.design.rows.CollapsibleSectionCard
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.Lucide

@Composable
internal fun LiveUpcomingBillsCard(
    state: AppState,
    navigation: AppNavigation,
    schedules: List<LiveScheduleSummary>,
) {
    val sectionKey = "live:${state.selectedBudgetId}:upcoming"
    val expanded = !state.isBudgetSectionCollapsed(sectionKey)
    CollapsibleSectionCard(
        "Upcoming bills",
        "${schedules.size} scheduled",
        "See all",
        expanded = expanded,
        onToggleExpanded = { state.toggleBudgetSection(sectionKey) },
        onClick = { navigation.open(AppRoute.SCHEDULES) },
    ) {
        schedules.take(3).forEachIndexed { index, schedule ->
            if (index > 0) DividerLine()
            ListRow(
                schedule.name,
                listOfNotNull(
                    formatTransactionListDate(schedule.nextDate),
                    schedule.accountName,
                ).joinToString(" · "),
                schedule.amountLabel(state),
                leading = RowLeading.Icon(Lucide.CalendarDays),
                onClick = { navigation.openSchedule(schedule.id) },
            )
        }
    }
}

internal fun scheduleFieldErrorMessage(
    message: String?,
    messageField: String?,
    field: String,
): String? = message?.takeIf { messageField == field }
