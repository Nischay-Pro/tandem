@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.format.daysInMonth
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.setBudgetCategoryAmount
import app.tandemforactual.ui.core.session.budgetMonthBody
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveCategorySummary
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.feature.budget.CategoryPaceStatus
import app.tandemforactual.ui.feature.budget.canQuickAssign
import app.tandemforactual.ui.feature.budget.categoryPace
import app.tandemforactual.ui.feature.budget.categoryPaceDetail
import app.tandemforactual.ui.feature.budget.categorySpendingInMonth
import app.tandemforactual.ui.feature.budget.lastMonthSpendingDetail
import app.tandemforactual.ui.feature.budget.previousMonthId
import com.composables.icons.lucide.Gauge
import com.composables.icons.lucide.History
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.TriangleAlert
import com.composables.icons.lucide.WalletCards
import kotlinx.coroutines.launch

/** Spending pace and last-month comparison for a category, with a quick-assign shortcut. */
@Composable
internal fun CategoryInsightsSection(
    state: AppState,
    mobileSession: TandemMobileSession,
    category: LiveCategorySummary,
    isTrackingBudget: Boolean,
) {
    val today = remember { platformTodayIsoDate() }
    val monthId = state.selectedMonthId
    val isCurrentMonth = monthId == today.take(7)
    val pace =
        if (isTrackingBudget || !isCurrentMonth) {
            null
        } else {
            categoryPace(
                category.assignedMinor,
                category.activityMinor,
                today.drop(8).take(2).toIntOrNull() ?: 0,
                daysInMonth(monthId) ?: 0,
            )
        }
    var lastMonthSpent by remember(state.selectedBudgetId, monthId, category.id) { mutableStateOf<Long?>(null) }
    LaunchedEffect(state.selectedBudgetId, monthId, category.id) {
        lastMonthSpent =
            previousMonthId(monthId)?.let { previous ->
                categorySpendingInMonth(mobileSession.budgetMonthBody(state.selectedBudgetId, previous), category.id)
            }
    }
    val lastMonth = lastMonthSpent
    if (pace == null && lastMonth == null) return
    SectionCard("Insights") {
        pace?.let {
            ListRow(
                it.status.title,
                categoryPaceDetail(it, state::formatCurrency),
                leading = RowLeading.Icon(if (it.status == CategoryPaceStatus.ON_TRACK) Lucide.Gauge else Lucide.TriangleAlert),
            )
        }
        if (pace != null && lastMonth != null) DividerLine()
        lastMonth?.let {
            ListRow(
                "Last month",
                lastMonthSpendingDetail(it, (-category.activityMinor).coerceAtLeast(0), monthId < today.take(7), state::formatCurrency),
                leading = RowLeading.Icon(Lucide.History),
            )
        }
        if (lastMonth != null &&
            canQuickAssign(isTrackingBudget, state.readOnlyMode, category.assignedMinor, lastMonth, monthId >= today.take(7))
        ) {
            val scope = rememberCoroutineScope()
            DividerLine()
            ListRow(
                "Assign last month’s spending",
                leading = RowLeading.Icon(Lucide.WalletCards),
                trailing = state.formatCurrency(lastMonth),
                options = ListRowOptions(trailingColor = tandemPalette.accent, muted = mobileSession.isPerformingBudgetAction),
                onClick = {
                    if (!mobileSession.isPerformingBudgetAction) {
                        scope.launch {
                            mobileSession.budgetEdits.setBudgetCategoryAmount(
                                state.selectedBudgetId,
                                category.id,
                                monthId,
                                lastMonth,
                                state.encryptedPassword.takeIf { it.isNotBlank() },
                            )
                        }
                    }
                },
            )
        }
    }
}
