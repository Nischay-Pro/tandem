@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.FinancialGoal
import app.tandemforactual.ui.core.state.recommendedEmphasis
import app.tandemforactual.ui.design.buttons.TandemBackButton
import app.tandemforactual.ui.design.templates.OnboardingLayout
import app.tandemforactual.ui.design.theme.TandemBackHandler
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import app.tandemforactual.ui.feature.onboarding.SetupHeadline
import app.tandemforactual.ui.feature.onboarding.SetupPrimaryButton
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.decide_connect_detail
import app.tandemforactual.ui.generated.resources.decide_personalize
import app.tandemforactual.ui.generated.resources.decide_personalize_detail
import app.tandemforactual.ui.generated.resources.emphasis_detail
import app.tandemforactual.ui.generated.resources.emphasis_recommended
import app.tandemforactual.ui.generated.resources.emphasis_title
import app.tandemforactual.ui.generated.resources.goals_detail
import app.tandemforactual.ui.generated.resources.goals_title
import app.tandemforactual.ui.generated.resources.personalize_continue
import app.tandemforactual.ui.generated.resources.personalize_detail
import app.tandemforactual.ui.generated.resources.personalize_finish
import app.tandemforactual.ui.generated.resources.personalize_just_connect
import app.tandemforactual.ui.generated.resources.personalize_progress
import app.tandemforactual.ui.generated.resources.personalize_title
import app.tandemforactual.ui.generated.resources.tools_detail
import app.tandemforactual.ui.generated.resources.tools_title
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.PlugZap
import com.composables.icons.lucide.Sparkles
import org.jetbrains.compose.resources.stringResource

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

/** After personalization, setup ends on Home; from Settings it returns to where it started. */
private fun finishPersonalizing(navigation: AppNavigation) = navigation.reset(AppRoute.HOME)

internal enum class PersonalizeStep { DECIDE, GOALS, EMPHASIS, MODE, TOOLS }

/** Progress, back button, headline and primary action shared by every personalization step. */
@Composable
internal fun StepScaffold(
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
