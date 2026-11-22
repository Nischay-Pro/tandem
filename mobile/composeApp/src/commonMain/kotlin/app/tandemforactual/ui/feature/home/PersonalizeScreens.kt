@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import app.tandemforactual.ui.core.state.FinancialGoal
import app.tandemforactual.ui.core.state.HomeModule
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.goal_analysis
import app.tandemforactual.ui.generated.resources.goal_analysis_detail
import app.tandemforactual.ui.generated.resources.goal_debt
import app.tandemforactual.ui.generated.resources.goal_debt_detail
import app.tandemforactual.ui.generated.resources.goal_organize
import app.tandemforactual.ui.generated.resources.goal_organize_detail
import app.tandemforactual.ui.generated.resources.goal_savings
import app.tandemforactual.ui.generated.resources.goal_savings_detail
import app.tandemforactual.ui.generated.resources.goal_spending
import app.tandemforactual.ui.generated.resources.goal_spending_detail
import app.tandemforactual.ui.generated.resources.module_accounts
import app.tandemforactual.ui.generated.resources.module_accounts_detail
import app.tandemforactual.ui.generated.resources.module_budget_summary
import app.tandemforactual.ui.generated.resources.module_budget_summary_detail
import app.tandemforactual.ui.generated.resources.module_category_progress
import app.tandemforactual.ui.generated.resources.module_category_progress_detail
import app.tandemforactual.ui.generated.resources.module_debt_overview
import app.tandemforactual.ui.generated.resources.module_debt_overview_detail
import app.tandemforactual.ui.generated.resources.module_month_summary
import app.tandemforactual.ui.generated.resources.module_month_summary_detail
import app.tandemforactual.ui.generated.resources.module_net_worth
import app.tandemforactual.ui.generated.resources.module_net_worth_detail
import app.tandemforactual.ui.generated.resources.module_recent_activity
import app.tandemforactual.ui.generated.resources.module_recent_activity_detail
import app.tandemforactual.ui.generated.resources.module_review_inbox
import app.tandemforactual.ui.generated.resources.module_review_inbox_detail
import app.tandemforactual.ui.generated.resources.module_savings_goals
import app.tandemforactual.ui.generated.resources.module_savings_goals_detail
import app.tandemforactual.ui.generated.resources.module_upcoming_bills
import app.tandemforactual.ui.generated.resources.module_upcoming_bills_detail
import com.composables.icons.lucide.CalendarClock
import com.composables.icons.lucide.ChartColumnIncreasing
import com.composables.icons.lucide.ChartPie
import com.composables.icons.lucide.CreditCard
import com.composables.icons.lucide.Inbox
import com.composables.icons.lucide.Landmark
import com.composables.icons.lucide.LayoutDashboard
import com.composables.icons.lucide.ListChecks
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.PiggyBank
import com.composables.icons.lucide.TrendingUp
import org.jetbrains.compose.resources.StringResource

internal fun FinancialGoal.title(): StringResource =
    when (this) {
        FinancialGoal.ORGANIZE -> Res.string.goal_organize
        FinancialGoal.SPENDING -> Res.string.goal_spending
        FinancialGoal.DEBT -> Res.string.goal_debt
        FinancialGoal.SAVINGS -> Res.string.goal_savings
        FinancialGoal.ANALYSIS -> Res.string.goal_analysis
    }

internal fun FinancialGoal.detail(): StringResource =
    when (this) {
        FinancialGoal.ORGANIZE -> Res.string.goal_organize_detail
        FinancialGoal.SPENDING -> Res.string.goal_spending_detail
        FinancialGoal.DEBT -> Res.string.goal_debt_detail
        FinancialGoal.SAVINGS -> Res.string.goal_savings_detail
        FinancialGoal.ANALYSIS -> Res.string.goal_analysis_detail
    }

internal fun HomeModule.title(): StringResource =
    when (this) {
        HomeModule.REVIEW_INBOX -> Res.string.module_review_inbox
        HomeModule.CATEGORY_PROGRESS -> Res.string.module_category_progress
        HomeModule.UPCOMING_BILLS -> Res.string.module_upcoming_bills
        HomeModule.DEBT_OVERVIEW -> Res.string.module_debt_overview
        HomeModule.SAVINGS_GOALS -> Res.string.module_savings_goals
        HomeModule.MONTH_SUMMARY -> Res.string.module_month_summary
        HomeModule.ACCOUNTS -> Res.string.module_accounts
        HomeModule.BUDGET_SUMMARY -> Res.string.module_budget_summary
        HomeModule.RECENT_ACTIVITY -> Res.string.module_recent_activity
        HomeModule.NET_WORTH -> Res.string.module_net_worth
    }

internal fun HomeModule.detail(): StringResource =
    when (this) {
        HomeModule.REVIEW_INBOX -> Res.string.module_review_inbox_detail
        HomeModule.CATEGORY_PROGRESS -> Res.string.module_category_progress_detail
        HomeModule.UPCOMING_BILLS -> Res.string.module_upcoming_bills_detail
        HomeModule.DEBT_OVERVIEW -> Res.string.module_debt_overview_detail
        HomeModule.SAVINGS_GOALS -> Res.string.module_savings_goals_detail
        HomeModule.MONTH_SUMMARY -> Res.string.module_month_summary_detail
        HomeModule.ACCOUNTS -> Res.string.module_accounts_detail
        HomeModule.BUDGET_SUMMARY -> Res.string.module_budget_summary_detail
        HomeModule.RECENT_ACTIVITY -> Res.string.module_recent_activity_detail
        HomeModule.NET_WORTH -> Res.string.module_net_worth_detail
    }

internal fun FinancialGoal.icon(): ImageVector =
    when (this) {
        FinancialGoal.ORGANIZE -> Lucide.ListChecks
        FinancialGoal.SPENDING -> Lucide.ChartPie
        FinancialGoal.DEBT -> Lucide.CreditCard
        FinancialGoal.SAVINGS -> Lucide.PiggyBank
        FinancialGoal.ANALYSIS -> Lucide.ChartColumnIncreasing
    }

internal fun HomeModule.icon(): ImageVector =
    when (this) {
        HomeModule.REVIEW_INBOX -> Lucide.Inbox
        HomeModule.CATEGORY_PROGRESS -> Lucide.ChartPie
        HomeModule.UPCOMING_BILLS -> Lucide.CalendarClock
        HomeModule.DEBT_OVERVIEW -> Lucide.CreditCard
        HomeModule.SAVINGS_GOALS -> Lucide.PiggyBank
        HomeModule.MONTH_SUMMARY -> Lucide.ChartColumnIncreasing
        HomeModule.ACCOUNTS -> Lucide.Landmark
        HomeModule.BUDGET_SUMMARY -> Lucide.LayoutDashboard
        HomeModule.RECENT_ACTIVITY -> Lucide.ListChecks
        HomeModule.NET_WORTH -> Lucide.TrendingUp
    }

/** One suggestion per chosen goal: that goal's first module, never a long setup list. */
internal fun suggestedTools(goals: Set<FinancialGoal>): List<HomeModule> =
    FinancialGoal.entries
        .filter { it in goals }
        .mapNotNull { goal -> HomeModule.entries.firstOrNull { goal in it.goals && !it.advancedOnly } }
        .distinct()
