@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.planning.RetirementProfile
import app.tandemforactual.data.regions.Region
import app.tandemforactual.data.regions.global.retirement.RetirementPlans
import app.tandemforactual.ui.components.BodyText
import app.tandemforactual.ui.components.FilterPills
import app.tandemforactual.ui.components.SecondaryButton
import app.tandemforactual.ui.model.LiveAccountSummary
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import kotlinx.coroutines.launch

private sealed interface SetupStep {
    data object Countries : SetupStep

    data class Country(
        val region: Region,
    ) : SetupStep

    data object AboutYou : SetupStep
}

/** Asks where you live, which accounts you have in each country and where Actual tracks them, then a little about you. */
@Composable
internal fun RetirementSetupScreen(
    state: AppState,
    navigation: AppNavigation,
    mobileSession: TandemMobileSession,
    liveAccounts: List<LiveAccountSummary>,
) {
    val scope = rememberCoroutineScope()
    val existing = mobileSession.retirementProfiles.associateBy { it.planId }
    val regions =
        remember {
            mutableStateListOf<Region>().apply {
                addAll(mobileSession.planningSettings.regions.mapNotNull { code -> Region.entries.firstOrNull { it.name == code } })
            }
        }
    val chosen =
        remember {
            mutableStateMapOf<String, String?>().apply {
                existing.forEach { (id, profile) ->
                    put(id, profile.accountId)
                }
            }
        }
    var age by remember {
        mutableStateOf(
            existing.values
                .firstNotNullOfOrNull { it.age }
                ?.toString()
                .orEmpty(),
        )
    }
    var family by remember { mutableStateOf(existing.values.any { it.family }) }
    var stepIndex by remember { mutableStateOf(0) }
    val steps = listOf(SetupStep.Countries) + regions.map { SetupStep.Country(it) } + SetupStep.AboutYou
    val step = steps[stepIndex.coerceIn(0, steps.lastIndex)]
    val accounts = liveAccounts.filter { !it.closed }.sortedByDescending { it.offBudget }
    val palette = tandemPalette
    OnboardingLayout(
        content = {
            Text("Step ${stepIndex + 1} of ${steps.size}", color = palette.muted, fontSize = TandemTypography.label)
            val reduce = tandemReduceMotion
            AnimatedContent(step, transitionSpec = {
                tandemFadeEnter(reduce) togetherWith tandemFadeExit(reduce)
            }, label = "retirement-step") { current ->
                Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                    when (current) {
                        SetupStep.Countries -> {
                            Heading("Where do you save?")
                            BodyText(
                                "Pick every country you have retirement or tax-advantaged accounts in. Each has its own accounts and limits.",
                            )
                            RetirementPlans.regions.forEach { region ->
                                CheckCard(
                                    region.displayName,
                                    RetirementPlans.forRegion(region).joinToString(", ") { it.displayName },
                                    region in regions,
                                ) {
                                    if (region in regions) regions.remove(region) else regions.add(region)
                                }
                            }
                            BodyText(
                                "Somewhere else? More countries are coming; tell us which one you need through Share feedback in the menu.",
                            )
                        }
                        is SetupStep.Country -> {
                            Heading("Your ${current.region.displayName} accounts")
                            BodyText("Choose the ones you have, then the Actual account each one is tracked in.")
                            RetirementPlans.forRegion(current.region).forEach { plan ->
                                val picked = chosen.containsKey(plan.id)
                                CheckCard(plan.displayName, plan.description, picked) {
                                    if (picked) {
                                        chosen.remove(plan.id)
                                    } else {
                                        chosen[plan.id] =
                                            accounts.firstOrNull { plan.matchesAccount(it.name) }?.id
                                    }
                                }
                                if (picked) {
                                    Column(
                                        Modifier.padding(start = TandemSpacing.large),
                                        verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
                                    ) {
                                        Text("Tracked in Actual as", color = palette.muted, fontSize = TandemTypography.label)
                                        FilterPills(
                                            listOf<Pair<String?, String>>(null to "Not in Actual") + accounts.map { it.id to it.name },
                                            chosen[plan.id],
                                            { chosen[plan.id] = it },
                                        )
                                        if (chosen[plan.id] == null) {
                                            Text(
                                                "Connect it through bank sync so Tandem can read your contributions and the years you saved.",
                                                color = palette.muted,
                                                fontSize = TandemTypography.label,
                                            )
                                            SecondaryButton("Connect it") { navigation.open(AppRoute.BANK_CONNECTIONS) }
                                        }
                                    }
                                }
                            }
                        }
                        SetupStep.AboutYou -> {
                            Heading("A little about you")
                            BodyText("Your age sets catch-up limits. It stays in this budget’s encrypted planning data.")
                            TextFieldCard(
                                "Your age this year",
                                age,
                                { age = it.filter(Char::isDigit).take(3) },
                                TextFieldOptions(
                                    placeholder = "Optional",
                                    keyboardOptions =
                                        androidx.compose.foundation.text.KeyboardOptions(
                                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                                        ),
                                ),
                            )
                            if (chosen.keys.any { id -> RetirementPlans.byId(id)?.limits?.any { it.familyMinor != null } == true }) {
                                Text("Health coverage", color = palette.muted, fontSize = TandemTypography.label)
                                FilterPills(listOf(false to "Just me", true to "Family"), family, { family = it })
                            }
                        }
                    }
                }
            }
        },
        actions = {
            SetupPrimaryButton(
                if (step == SetupStep.AboutYou) "Finish" else "Continue",
                enabled = step != SetupStep.Countries || regions.isNotEmpty(),
            ) {
                if (step != SetupStep.AboutYou) {
                    stepIndex++
                    return@SetupPrimaryButton
                }
                val inRegions = chosen.filterKeys { id -> RetirementPlans.byId(id)?.region in regions }
                val profiles =
                    inRegions.map { (id, accountId) ->
                        (existing[id] ?: RetirementProfile(id)).copy(accountId = accountId, age = age.toIntOrNull(), family = family)
                    }
                scope.launch {
                    mobileSession.saveRetirementSetup(state.selectedBudgetId, regions.map { it.name }, profiles)
                    navigation.back()
                }
            }
            if (stepIndex > 0) SecondaryButton("Back", Modifier.fillMaxWidth()) { stepIndex-- }
        },
    )
}

@Composable
private fun CheckCard(
    title: String,
    detail: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val palette = tandemPalette
    val shape = RoundedCornerShape(TandemShapes.large)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = TandemLayout.listRowMinHeight)
            .clip(shape)
            .background(if (checked) palette.accentSurface else palette.surface)
            .border(1.dp, if (checked) palette.accent else palette.border, shape)
            .toggleable(value = checked, role = Role.Checkbox) { onToggle() }
            .padding(TandemSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = palette.text, fontSize = TandemTypography.action, fontWeight = FontWeight.SemiBold)
            Text(detail, color = palette.muted, fontSize = TandemTypography.compactBody)
        }
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(if (checked) palette.accent else palette.raised),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Lucide.Check, null, Modifier.size(16.dp), tint = palette.accentContent)
        }
    }
}
