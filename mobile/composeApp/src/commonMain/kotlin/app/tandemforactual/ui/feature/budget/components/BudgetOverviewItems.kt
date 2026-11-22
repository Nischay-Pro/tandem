@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.tandemforactual.ui.core.session.BudgetReorderStatus
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.retryQueuedBudgetReorders
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.AnimatedReveal
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.templates.AttentionBanner
import app.tandemforactual.ui.design.templates.Heading
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.feature.budget.budgetPendingChangeBannerItems
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.ChartNoAxesCombined
import com.composables.icons.lucide.Lucide

/** Alerts, overview heading, hints and month actions at the top of the budget list. */
internal fun LazyListScope.budgetOverviewItems(
    state: AppState,
    navigation: AppNavigation,
    mobileSession: TandemMobileSession,
    isLiveBudget: Boolean,
    isTrackingBudget: Boolean,
    liveBudgetMonth: LiveBudgetMonthSummary?,
    groupReorderStatus: BudgetReorderStatus,
    onCopyLastMonth: () -> Unit,
    onPlanTemplates: () -> Unit,
) {
    if (isLiveBudget) {
        if (!mobileSession.isAuthenticated) {
            item(key = "budget-reauthentication-alert") {
                AnimatedReveal(visible = true) {
                    AttentionBanner(
                        "Sign in to sync",
                        "This budget is saved on this device. Sign in again to reconnect and sync it.",
                        "Sign in",
                    ) {
                        navigation.open(AppRoute.SIGN_IN)
                    }
                }
            }
        }
        budgetPendingChangeBannerItems(state, navigation, mobileSession.pendingWrites)
    }
    item(key = "budget-overview-title") {
        Heading("Overview")
    }
    if (mobileSession.syncComplete && !state.hasDismissedBudgetHint) {
        item(key = "budget-getting-started-hint") {
            GettingStartedCard { state.hasDismissedBudgetHint = true }
        }
    }
    if (isLiveBudget) {
        groupReorderStatus.message?.let { message ->
            item(key = "budget-group-reorder-message") {
                InlineMessage(
                    message,
                    if (groupReorderStatus.warning) InlineMessageKind.WARNING else InlineMessageKind.ERROR,
                    actionLabel = "Try again".takeIf { groupReorderStatus.pendingCount > 0 },
                    onAction =
                        {
                            mobileSession.budgetQueue.retryQueuedBudgetReorders(
                                state.selectedBudgetId,
                                state.encryptedPassword.takeIf(String::isNotBlank),
                            )
                        }.takeIf { groupReorderStatus.pendingCount > 0 },
                )
            }
        }
    }
    if (isLiveBudget && !isTrackingBudget && liveBudgetMonth != null) {
        item(key = "budget-copy-last-month") {
            BudgetMonthActionsCard(state, mobileSession, onCopyLastMonth, onPlanTemplates)
        }
    }
    if (isTrackingBudget) {
        item(key = "budget-tracking-mode") {
            SurfaceCard {
                ListRow(
                    "Tracking budget",
                    "Actual does not assign money to categories in this budget.",
                    leading = RowLeading.Icon(Lucide.ChartNoAxesCombined),
                )
            }
        }
    }
}

@Composable
private fun BudgetMonthActionsCard(
    state: AppState,
    mobileSession: TandemMobileSession,
    onCopyLastMonth: () -> Unit,
    onPlanTemplates: () -> Unit,
) {
    SurfaceCard {
        Column(Modifier.fillMaxWidth().padding(TandemSpacing.small)) {
            TandemActionButton(
                "Copy last month",
                TandemActionButtonTone.NEUTRAL,
                modifier = Modifier.fillMaxWidth(),
                icon = Lucide.CalendarDays,
                enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
                onClick = {
                    mobileSession.feedback.clearBudgetActionMessage()
                    onCopyLastMonth()
                },
            )
            Spacer(Modifier.height(TandemSpacing.extraSmall))
            TandemActionButton(
                "Plan templates",
                TandemActionButtonTone.NEUTRAL,
                modifier = Modifier.fillMaxWidth(),
                icon = Lucide.CalendarDays,
                enabled = !mobileSession.isPerformingBudgetAction,
                onClick = {
                    mobileSession.feedback.clearBudgetActionMessage()
                    onPlanTemplates()
                },
            )
        }
    }
}

/** Card shown when the selected month has no saved plan. */
@Composable
internal fun BudgetMonthEmptyCard() {
    SurfaceCard {
        EmptyState(
            "No plan for this month",
            "Sync a month available in Actual, or choose a different planning period.",
            modifier = Modifier.fillMaxWidth().padding(TandemSpacing.large),
        )
    }
}

/** Card shown while the selected month's plan loads. */
@Composable
internal fun BudgetMonthLoadingCard(selectedMonth: String) {
    SurfaceCard {
        ListRow(
            "Loading $selectedMonth",
            "Checking the saved plan on this device.",
            leading = RowLeading.Icon(Lucide.CalendarDays),
        )
    }
}
