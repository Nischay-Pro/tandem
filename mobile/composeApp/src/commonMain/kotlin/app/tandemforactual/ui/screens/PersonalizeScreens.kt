@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.components.TandemBackButton
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.decide_connect_detail
import app.tandemforactual.ui.generated.resources.decide_personalize
import app.tandemforactual.ui.generated.resources.decide_personalize_detail
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
import app.tandemforactual.ui.generated.resources.mode_advanced_short
import app.tandemforactual.ui.generated.resources.mode_detail
import app.tandemforactual.ui.generated.resources.mode_minimal
import app.tandemforactual.ui.generated.resources.mode_minimal_detail
import app.tandemforactual.ui.generated.resources.mode_minimal_short
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
import app.tandemforactual.ui.generated.resources.personalize_continue
import app.tandemforactual.ui.generated.resources.personalize_detail
import app.tandemforactual.ui.generated.resources.personalize_finish
import app.tandemforactual.ui.generated.resources.personalize_just_connect
import app.tandemforactual.ui.generated.resources.personalize_progress
import app.tandemforactual.ui.generated.resources.personalize_title
import app.tandemforactual.ui.generated.resources.tools_add_short
import app.tandemforactual.ui.generated.resources.tools_added_short
import app.tandemforactual.ui.generated.resources.tools_detail
import app.tandemforactual.ui.generated.resources.tools_title
import com.composables.icons.lucide.CalendarClock
import com.composables.icons.lucide.ChartColumnIncreasing
import com.composables.icons.lucide.ChartPie
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.CreditCard
import com.composables.icons.lucide.Inbox
import com.composables.icons.lucide.Landmark
import com.composables.icons.lucide.LayoutDashboard
import com.composables.icons.lucide.ListChecks
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.PiggyBank
import com.composables.icons.lucide.PlugZap
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.TrendingUp
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

/** After personalization, setup ends on Home; from Settings it returns to where it started. */
private fun finishPersonalizing(navigation: AppNavigation) = navigation.reset(AppRoute.HOME)

/** One suggestion per chosen goal: that goal's first module, never a long setup list. */
internal fun suggestedTools(goals: Set<FinancialGoal>): List<HomeModule> =
    FinancialGoal.entries
        .filter { it in goals }
        .mapNotNull { goal -> HomeModule.entries.firstOrNull { goal in it.goals && !it.advancedOnly } }
        .distinct()

private enum class PersonalizeStep { DECIDE, GOALS, EMPHASIS, MODE, TOOLS }

/** One route for personalization; its steps move forward and back without adding to the navigation stack. */
@Composable
internal fun PersonalizeScreen(
    state: AppState,
    navigation: AppNavigation,
) {
    var stepName by rememberSaveable(state.selectedBudgetId) { mutableStateOf(PersonalizeStep.DECIDE.name) }
    var history by rememberSaveable(state.selectedBudgetId) { mutableStateOf(listOf<String>()) }
    val step = PersonalizeStep.valueOf(stepName)
    val go: (PersonalizeStep) -> Unit = {
        history = history + stepName
        stepName = it.name
    }
    val back: () -> Unit = {
        if (history.isEmpty()) {
            navigation.back()
        } else {
            stepName = history.last()
            history = history.dropLast(1)
        }
    }
    TandemBackHandler(enabled = history.isNotEmpty(), onBack = back, onBackProgress = { _, _ -> })
    AnimatedContent(
        step,
        transitionSpec = {
            val forward = targetState.ordinal > initialState.ordinal
            (
                slideInHorizontally(tween(TandemMotion.ROUTE_MILLIS, easing = FastOutSlowInEasing)) { if (forward) it / 6 else -it / 6 } +
                    fadeIn(tween(TandemMotion.ROUTE_MILLIS - 80, delayMillis = 80))
            ) togetherWith fadeOut(tween(90))
        },
        label = "personalize-step",
    ) { current ->
        val finish = { finishPersonalizing(navigation) }
        when (current) {
            PersonalizeStep.DECIDE -> DecideStep(state, back, finish) { go(PersonalizeStep.GOALS) }
            PersonalizeStep.GOALS ->
                GoalsStep(state, back) { count ->
                    go(
                        if (count >=
                            2
                        ) {
                            PersonalizeStep.EMPHASIS
                        } else {
                            PersonalizeStep.MODE
                        },
                    )
                }
            PersonalizeStep.EMPHASIS -> EmphasisStep(state, back) { go(PersonalizeStep.MODE) }
            PersonalizeStep.MODE ->
                ModeStep(state, back) {
                    if (suggestedTools(state.experience?.selectedGoals.orEmpty()).isEmpty()) finish() else go(PersonalizeStep.TOOLS)
                }
            PersonalizeStep.TOOLS -> ToolsStep(state, back, finish)
        }
    }
}

@Composable
private fun StepScaffold(
    step: PersonalizeStep,
    onBack: () -> Unit,
    title: String,
    subtitle: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = tandemPalette
    val total = PersonalizeStep.entries.size
    val progressLabel = stringResource(Res.string.personalize_progress, step.ordinal + 1, total)
    OnboardingLayout(
        verticalSpacing = TandemSpacing.medium,
        content = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                TandemBackButton(onClick = onBack)
                Row(
                    Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = progressLabel },
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    repeat(total) { index ->
                        val color by animateColorAsState(
                            if (index <= step.ordinal) palette.accent else palette.raised,
                            tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
                            label = "personalize-progress-$index",
                        )
                        Box(
                            Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(color),
                        )
                    }
                }
            }
            SetupHeadline(title, subtitle)
            Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small), content = content)
        },
        actions = { SetupPrimaryButton(primaryLabel, modifier = Modifier.fillMaxWidth(), onClick = onPrimary) },
    )
}

/** A large tappable card; a check badge for multi-select, a filled ring for single choices. */
@Composable
private fun ChoiceCard(
    title: String,
    detail: String?,
    selected: Boolean,
    multiSelect: Boolean,
    icon: ImageVector? = null,
    badge: String? = null,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    val shape = RoundedCornerShape(TandemShapes.large)
    val motion = tandemMotionSpec<androidx.compose.ui.graphics.Color>(tandemReduceMotion, TandemMotion.COLOR_MILLIS)
    val background by animateColorAsState(if (selected) palette.accentSurface else palette.surface, motion, label = "choice-bg")
    val border by animateColorAsState(if (selected) palette.accent else palette.border, motion, label = "choice-border")
    val selection =
        if (multiSelect) {
            Modifier.toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
        } else {
            Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
        }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(if (selected) 1.5.dp else 1.dp, border, shape)
            .then(selection)
            .padding(horizontal = TandemSpacing.medium, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) {
            Box(
                Modifier
                    .size(
                        40.dp,
                    ).clip(
                        RoundedCornerShape(TandemShapes.control),
                    ).background(if (selected) palette.accent.copy(alpha = 0.18f) else palette.raised),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (selected) palette.accent else palette.muted,
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                Text(title, color = palette.text, fontSize = TandemTypography.action, fontWeight = FontWeight.SemiBold)
                if (badge != null) {
                    Text(
                        badge,
                        Modifier
                            .clip(
                                CircleShape,
                            ).background(palette.accent.copy(alpha = 0.16f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        color = palette.accent,
                        fontSize = TandemTypography.micro,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (detail != null) Text(detail, color = palette.muted, fontSize = TandemTypography.compactBody)
        }
        SelectionMark(selected, multiSelect)
    }
}

@Composable
private fun SelectionMark(
    selected: Boolean,
    multiSelect: Boolean,
) {
    val palette = tandemPalette
    val scale by animateFloatAsState(
        if (selected) 1f else 0f,
        tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTROL_MILLIS),
        label = "choice-mark",
    )
    val shape = if (multiSelect) RoundedCornerShape(TandemShapes.small) else CircleShape
    Box(
        Modifier.size(24.dp).clip(shape).border(1.5.dp, if (selected) palette.accent else palette.border, shape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(24.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.clip(shape)
                .background(palette.accent),
            contentAlignment = Alignment.Center,
        ) { Icon(Lucide.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = palette.accentContent) }
    }
}

@Composable
private fun DecideStep(
    state: AppState,
    onBack: () -> Unit,
    onConnect: () -> Unit,
    onPersonalize: () -> Unit,
) {
    var personalize by rememberSaveable { mutableStateOf(true) }
    StepScaffold(
        PersonalizeStep.DECIDE,
        onBack,
        stringResource(Res.string.personalize_title),
        stringResource(Res.string.personalize_detail),
        stringResource(Res.string.personalize_continue),
        onPrimary = {
            if (personalize) {
                onPersonalize()
            } else {
                state.updateExperience { it.copy(personalizationDeferred = true) }
                onConnect()
            }
        },
    ) {
        ChoiceCard(
            stringResource(Res.string.decide_personalize),
            stringResource(Res.string.decide_personalize_detail),
            personalize,
            multiSelect = false,
            icon = Lucide.Sparkles,
        ) { personalize = true }
        ChoiceCard(
            stringResource(Res.string.personalize_just_connect),
            stringResource(Res.string.decide_connect_detail),
            !personalize,
            multiSelect = false,
            icon = Lucide.PlugZap,
        ) { personalize = false }
    }
}

@Composable
private fun GoalsStep(
    state: AppState,
    onBack: () -> Unit,
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
    StepScaffold(
        PersonalizeStep.GOALS,
        onBack,
        stringResource(Res.string.goals_title),
        stringResource(Res.string.goals_detail),
        stringResource(Res.string.personalize_continue),
        onPrimary = {
            val goals = FinancialGoal.entries.filter { it.name in selected }.toSet()
            state.updateExperience { it.withGoals(goals) }
            onNext(goals.size)
        },
    ) {
        FinancialGoal.entries.forEach { goal ->
            ChoiceCard(
                stringResource(goal.title()),
                stringResource(goal.detail()),
                goal.name in selected,
                multiSelect = true,
                icon = goal.icon(),
            ) {
                selected = if (goal.name in selected) selected - goal.name else selected + goal.name
            }
        }
    }
}

@Composable
private fun EmphasisStep(
    state: AppState,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val experience = state.experience ?: return
    val recommended = recommendedEmphasis(experience.selectedGoals)
    StepScaffold(
        PersonalizeStep.EMPHASIS,
        onBack,
        stringResource(Res.string.emphasis_title),
        stringResource(Res.string.emphasis_detail),
        stringResource(Res.string.personalize_continue),
        onPrimary = onNext,
    ) {
        FinancialGoal.entries.filter { it in experience.selectedGoals }.forEach { goal ->
            ChoiceCard(
                stringResource(goal.title()),
                stringResource(goal.detail()),
                experience.emphasis == goal,
                multiSelect = false,
                icon = goal.icon(),
                badge = stringResource(Res.string.emphasis_recommended).takeIf { goal == recommended },
            ) { state.updateExperience { it.copy(primaryGoal = goal) } }
        }
    }
}

@Composable
private fun ModeStep(
    state: AppState,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val palette = tandemPalette
    StepScaffold(
        PersonalizeStep.MODE,
        onBack,
        stringResource(Res.string.mode_title),
        stringResource(Res.string.mode_detail),
        stringResource(Res.string.personalize_continue),
        onPrimary = onNext,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
            InterfaceMode.entries.forEach { mode ->
                ModePreviewCard(mode, state.interfaceMode == mode, Modifier.weight(1f)) { state.interfaceMode = mode }
            }
        }
        AnimatedContent(state.interfaceMode, transitionSpec = {
            fadeIn(tween(TandemMotion.CONTROL_MILLIS)) togetherWith fadeOut(tween(90))
        }, label = "mode-detail") { mode ->
            Text(
                stringResource(if (mode == InterfaceMode.MINIMAL) Res.string.mode_minimal_detail else Res.string.mode_advanced_detail),
                Modifier.padding(top = TandemSpacing.extraSmall),
                color = palette.muted,
                fontSize = TandemTypography.compactBody,
            )
        }
    }
}

/** A small drawing of each layout: a few large blocks for Minimal, a denser grid with a sidebar for Advanced. */
@Composable
private fun ModePreviewCard(
    mode: InterfaceMode,
    selected: Boolean,
    modifier: Modifier,
    onSelect: () -> Unit,
) {
    val palette = tandemPalette
    val shape = RoundedCornerShape(TandemShapes.large)
    val motion = tandemMotionSpec<androidx.compose.ui.graphics.Color>(tandemReduceMotion, TandemMotion.COLOR_MILLIS)
    val border by animateColorAsState(if (selected) palette.accent else palette.border, motion, label = "mode-border")
    val block by animateColorAsState(if (selected) palette.accent.copy(alpha = 0.32f) else palette.raised, motion, label = "mode-block")
    Column(
        modifier
            .clip(shape)
            .background(if (selected) palette.accentSurface else palette.surface)
            .border(if (selected) 1.5.dp else 1.dp, border, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(TandemSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(
                    112.dp,
                ).clip(RoundedCornerShape(TandemShapes.control))
                .background(palette.background)
                .padding(10.dp),
        ) {
            if (mode == InterfaceMode.MINIMAL) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.55f)
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(block),
                    )
                    repeat(3) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(22.dp)
                                .clip(RoundedCornerShape(TandemShapes.small))
                                .background(block),
                        )
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) { repeat(5) { Box(Modifier.size(10.dp).clip(CircleShape).background(block)) } }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        repeat(4) {
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(18.dp)
                                        .clip(RoundedCornerShape(TandemShapes.small))
                                        .background(block),
                                )
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(18.dp)
                                        .clip(RoundedCornerShape(TandemShapes.small))
                                        .background(block),
                                )
                            }
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (mode == InterfaceMode.MINIMAL) Res.string.mode_minimal else Res.string.mode_advanced),
                    color = palette.text,
                    fontSize = TandemTypography.action,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(if (mode == InterfaceMode.MINIMAL) Res.string.mode_minimal_short else Res.string.mode_advanced_short),
                    color = palette.muted,
                    fontSize = TandemTypography.caption,
                )
            }
            SelectionMark(selected, multiSelect = false)
        }
    }
}

@Composable
private fun ToolsStep(
    state: AppState,
    onBack: () -> Unit,
    onFinish: () -> Unit,
) {
    val experience = state.experience ?: return
    val palette = tandemPalette
    StepScaffold(
        PersonalizeStep.TOOLS,
        onBack,
        stringResource(Res.string.tools_title),
        stringResource(Res.string.tools_detail),
        stringResource(Res.string.personalize_finish),
        onPrimary = onFinish,
    ) {
        suggestedTools(experience.selectedGoals).forEach { module ->
            val added = module in experience.pinnedModules
            val shape = RoundedCornerShape(TandemShapes.large)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(palette.surface)
                    .border(1.dp, palette.border, shape)
                    .toggleable(value = added, role = Role.Checkbox) { add ->
                        state.updateExperience {
                            if (add) {
                                it.copy(pinnedModules = it.pinnedModules + module)
                            } else {
                                it.copy(
                                    pinnedModules =
                                        it.pinnedModules - module,
                                )
                            }
                        }
                    }.padding(horizontal = TandemSpacing.medium, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(TandemShapes.control)).background(palette.raised),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(module.icon(), contentDescription = null, modifier = Modifier.size(20.dp), tint = palette.accent)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(module.title()),
                        color = palette.text,
                        fontSize = TandemTypography.action,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(stringResource(module.detail()), color = palette.muted, fontSize = TandemTypography.compactBody)
                }
                AddChip(added)
            }
        }
    }
}

@Composable
private fun AddChip(added: Boolean) {
    val palette = tandemPalette
    val motion = tandemMotionSpec<androidx.compose.ui.graphics.Color>(tandemReduceMotion, TandemMotion.COLOR_MILLIS)
    val background by animateColorAsState(if (added) palette.accent else palette.raised, motion, label = "add-chip-bg")
    val content by animateColorAsState(if (added) palette.accentContent else palette.text, motion, label = "add-chip-content")
    Row(
        Modifier.clip(CircleShape).background(background).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(if (added) Lucide.Check else Lucide.Plus, contentDescription = null, modifier = Modifier.size(14.dp), tint = content)
        Text(
            stringResource(if (added) Res.string.tools_added_short else Res.string.tools_add_short),
            color = content,
            fontSize = TandemTypography.label,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
