@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import app.tandemforactual.ui.components.DividerLine
import app.tandemforactual.ui.components.SecondaryButton
import app.tandemforactual.ui.components.SurfaceCard
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.emphasis_detail
import app.tandemforactual.ui.generated.resources.emphasis_recommended
import app.tandemforactual.ui.generated.resources.emphasis_title
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
import app.tandemforactual.ui.generated.resources.goals_detail
import app.tandemforactual.ui.generated.resources.goals_title
import app.tandemforactual.ui.generated.resources.mode_advanced
import app.tandemforactual.ui.generated.resources.mode_advanced_detail
import app.tandemforactual.ui.generated.resources.mode_detail
import app.tandemforactual.ui.generated.resources.mode_minimal
import app.tandemforactual.ui.generated.resources.mode_minimal_detail
import app.tandemforactual.ui.generated.resources.mode_title
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
import app.tandemforactual.ui.generated.resources.personalize_choose_goals
import app.tandemforactual.ui.generated.resources.personalize_continue
import app.tandemforactual.ui.generated.resources.personalize_detail
import app.tandemforactual.ui.generated.resources.personalize_finish
import app.tandemforactual.ui.generated.resources.personalize_just_connect
import app.tandemforactual.ui.generated.resources.personalize_title
import app.tandemforactual.ui.generated.resources.tools_add
import app.tandemforactual.ui.generated.resources.tools_added
import app.tandemforactual.ui.generated.resources.tools_detail
import app.tandemforactual.ui.generated.resources.tools_not_interested
import app.tandemforactual.ui.generated.resources.tools_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal fun FinancialGoal.title(): StringResource =
    when (this) {
        FinancialGoal.ORGANIZE -> Res.string.goal_organize
        FinancialGoal.SPENDING -> Res.string.goal_spending
        FinancialGoal.DEBT -> Res.string.goal_debt
        FinancialGoal.SAVINGS -> Res.string.goal_savings
        FinancialGoal.ANALYSIS -> Res.string.goal_analysis
    }

private fun FinancialGoal.detail(): StringResource =
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

/** After personalization, setup ends on Home; from Settings it returns to where it started. */
private fun finishPersonalizing(navigation: AppNavigation) = navigation.selectTab(AppRoute.HOME)

/** The tools recommended for each chosen goal: its first one or two modules, never a long setup list. */
internal fun suggestedTools(goals: Set<FinancialGoal>): List<HomeModule> =
    FinancialGoal.entries
        .filter { it in goals }
        .flatMap { goal -> HomeModule.entries.filter { goal in it.goals && !it.advancedOnly }.take(2) }
        .distinct()

private enum class PersonalizeStep { DECIDE, GOALS, EMPHASIS, MODE, TOOLS }

/** One route for personalization; its steps move forward and back without adding to the navigation stack. */
@Composable
internal fun PersonalizeScreen(
    state: AppState,
    navigation: AppNavigation,
) {
    var stepName by rememberSaveable(state.selectedBudgetId) { mutableStateOf(PersonalizeStep.DECIDE.name) }
    val step = PersonalizeStep.valueOf(stepName)
    var history by rememberSaveable(state.selectedBudgetId) { mutableStateOf(listOf<String>()) }
    val go: (PersonalizeStep) -> Unit = {
        history = history + stepName
        stepName = it.name
    }
    TandemBackHandler(enabled = history.isNotEmpty(), onBack = {
        stepName = history.last()
        history = history.dropLast(1)
    }, onBackProgress = { _, _ -> })
    AnimatedContent(
        step,
        transitionSpec = {
            val forward = targetState.ordinal > initialState.ordinal
            val spec = tween<IntOffset>(TandemMotion.ROUTE_MILLIS, easing = FastOutSlowInEasing)
            (slideInHorizontally(spec) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(TandemMotion.ROUTE_MILLIS))) togetherWith
                (slideOutHorizontally(spec) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(TandemMotion.CONTROL_MILLIS)))
        },
        label = "personalize-step",
    ) { current ->
        when (current) {
            PersonalizeStep.DECIDE -> DecideStep(state, navigation) { go(PersonalizeStep.GOALS) }
            PersonalizeStep.GOALS -> GoalsStep(state) { goals -> go(if (goals >= 2) PersonalizeStep.EMPHASIS else PersonalizeStep.MODE) }
            PersonalizeStep.EMPHASIS -> EmphasisStep(state) { go(PersonalizeStep.MODE) }
            PersonalizeStep.MODE ->
                ModeStep(state) {
                    if (suggestedTools(
                            state.experience?.selectedGoals.orEmpty(),
                        ).isEmpty()
                    ) {
                        finishPersonalizing(navigation)
                    } else {
                        go(PersonalizeStep.TOOLS)
                    }
                }
            PersonalizeStep.TOOLS -> ToolsStep(state) { finishPersonalizing(navigation) }
        }
    }
}

@Composable
private fun DecideStep(
    state: AppState,
    navigation: AppNavigation,
    onChooseGoals: () -> Unit,
) {
    OnboardingLayout(
        content = { SetupHeadline(stringResource(Res.string.personalize_title), stringResource(Res.string.personalize_detail)) },
        actions = {
            SetupPrimaryButton(
                stringResource(Res.string.personalize_choose_goals),
                modifier = Modifier.fillMaxWidth(),
                onClick = onChooseGoals,
            )
            SecondaryButton(stringResource(Res.string.personalize_just_connect), modifier = Modifier.fillMaxWidth()) {
                state.updateExperience { it.copy(personalizationDeferred = true) }
                finishPersonalizing(navigation)
            }
        },
    )
}

@Composable
private fun GoalsStep(
    state: AppState,
    onNext: (Int) -> Unit,
) {
    var selected by rememberSaveable(state.selectedBudgetId) {
        mutableStateOf(
            state.experience
                ?.selectedGoals
                .orEmpty()
                .map { it.name },
        )
    }
    OnboardingLayout(
        verticalSpacing = TandemSpacing.medium,
        content = {
            SetupHeadline(stringResource(Res.string.goals_title), stringResource(Res.string.goals_detail))
            SurfaceCard {
                FinancialGoal.entries.forEachIndexed { index, goal ->
                    if (index > 0) DividerLine()
                    CheckBoxRow(
                        stringResource(goal.title()),
                        stringResource(goal.detail()),
                        goal.name in selected,
                        enabled = true,
                    ) { checked ->
                        selected = if (checked) selected + goal.name else selected - goal.name
                    }
                }
            }
        },
        actions = {
            SetupPrimaryButton(stringResource(Res.string.personalize_continue), modifier = Modifier.fillMaxWidth()) {
                val goals = FinancialGoal.entries.filter { it.name in selected }.toSet()
                state.updateExperience { it.withGoals(goals) }
                onNext(goals.size)
            }
        },
    )
}

@Composable
private fun EmphasisStep(
    state: AppState,
    onNext: () -> Unit,
) {
    val experience = state.experience ?: return
    val recommended = recommendedEmphasis(experience.selectedGoals)
    OnboardingLayout(
        verticalSpacing = TandemSpacing.medium,
        content = {
            SetupHeadline(stringResource(Res.string.emphasis_title), stringResource(Res.string.emphasis_detail))
            SurfaceCard {
                FinancialGoal.entries.filter { it in experience.selectedGoals }.forEachIndexed { index, goal ->
                    if (index > 0) DividerLine()
                    ChoiceRow(
                        stringResource(goal.title()),
                        stringResource(Res.string.emphasis_recommended).takeIf { goal == recommended },
                        selected = experience.emphasis == goal,
                    ) { state.updateExperience { it.copy(primaryGoal = goal) } }
                }
            }
        },
        actions = {
            SetupPrimaryButton(stringResource(Res.string.personalize_continue), modifier = Modifier.fillMaxWidth(), onClick = onNext)
        },
    )
}

@Composable
private fun ModeStep(
    state: AppState,
    onNext: () -> Unit,
) {
    OnboardingLayout(
        verticalSpacing = TandemSpacing.medium,
        content = {
            SetupHeadline(stringResource(Res.string.mode_title), stringResource(Res.string.mode_detail))
            SurfaceCard {
                ChoiceRow(
                    stringResource(Res.string.mode_minimal),
                    stringResource(Res.string.mode_minimal_detail),
                    selected = state.interfaceMode == InterfaceMode.MINIMAL,
                ) { state.interfaceMode = InterfaceMode.MINIMAL }
                DividerLine()
                ChoiceRow(
                    stringResource(Res.string.mode_advanced),
                    stringResource(Res.string.mode_advanced_detail),
                    selected = state.interfaceMode == InterfaceMode.ADVANCED,
                ) { state.interfaceMode = InterfaceMode.ADVANCED }
            }
        },
        actions = {
            SetupPrimaryButton(stringResource(Res.string.personalize_continue), modifier = Modifier.fillMaxWidth(), onClick = onNext)
        },
    )
}

@Composable
private fun ToolsStep(
    state: AppState,
    onFinish: () -> Unit,
) {
    val experience = state.experience ?: return
    val palette = tandemPalette
    OnboardingLayout(
        verticalSpacing = TandemSpacing.medium,
        content = {
            SetupHeadline(stringResource(Res.string.tools_title), stringResource(Res.string.tools_detail))
            suggestedTools(experience.selectedGoals).forEach { module ->
                val pinned = module in experience.pinnedModules
                SurfaceCard {
                    Text(stringResource(module.title()), color = palette.text, fontSize = TandemTypography.body)
                    Text(stringResource(module.detail()), color = palette.muted, fontSize = TandemTypography.caption)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        SecondaryButton(
                            stringResource(if (pinned) Res.string.tools_added else Res.string.tools_add),
                            modifier = Modifier.weight(1f),
                            enabled = !pinned,
                        ) {
                            state.updateExperience {
                                it.copy(
                                    pinnedModules = it.pinnedModules + module,
                                    hiddenModules =
                                        it.hiddenModules - module,
                                )
                            }
                        }
                        SecondaryButton(stringResource(Res.string.tools_not_interested), modifier = Modifier.weight(1f)) {
                            state.updateExperience {
                                it.copy(
                                    pinnedModules = it.pinnedModules - module,
                                    hiddenModules =
                                        it.hiddenModules + module,
                                )
                            }
                        }
                    }
                }
            }
        },
        actions = {
            SetupPrimaryButton(
                stringResource(Res.string.personalize_finish),
                modifier = Modifier.fillMaxWidth(),
                onClick = onFinish,
            )
        },
    )
}
