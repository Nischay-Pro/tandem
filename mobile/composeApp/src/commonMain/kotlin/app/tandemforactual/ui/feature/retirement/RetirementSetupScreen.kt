@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.retirement

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tandemforactual.data.planning.RetirementProfile
import app.tandemforactual.data.regions.Region
import app.tandemforactual.data.regions.global.retirement.RetirementPlan
import app.tandemforactual.data.regions.global.retirement.RetirementPlans
import app.tandemforactual.ui.core.format.budgetCurrencyCode
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.design.buttons.SecondaryButton
import app.tandemforactual.ui.design.buttons.SetupPrimaryButton
import app.tandemforactual.ui.design.feedback.BodyText
import app.tandemforactual.ui.design.inputs.FilterPills
import app.tandemforactual.ui.design.templates.Heading
import app.tandemforactual.ui.design.templates.OnboardingLayout
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemLayout
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemFadeEnter
import app.tandemforactual.ui.design.theme.tandemFadeExit
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Settings2
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
    val existing = mobileSession.planning.retirementProfiles.associateBy { it.planId }
    val regions = remember { mutableStateListOf<Region>().apply { addAll(savedRegions(mobileSession.planning.settings.regions)) } }
    val chosen =
        remember {
            mutableStateMapOf<String, String?>().apply {
                existing.forEach { (id, profile) ->
                    put(id, profile.accountId)
                }
            }
        }
    val thisYear = platformTodayIsoDate().take(4).toInt()
    var birthYear by remember { mutableStateOf(savedBirthYear(existing.values, thisYear)) }
    var family by remember { mutableStateOf(existing.values.any { it.family }) }
    var stepIndex by remember { mutableStateOf(0) }
    val steps = listOf(SetupStep.Countries) + regions.map { SetupStep.Country(it) } + SetupStep.AboutYou
    val step = steps[stepIndex.coerceIn(0, steps.lastIndex)]
    val lived =
        mobileSession.planning.jurisdictionPeriods
            .sortedByDescending { it.isCurrent }
            .map { it.region }
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
                            CountriesStep(regions, lived, state.budgetCurrencyCode)
                        }

                        is SetupStep.Country -> {
                            CountryStep(current.region, accounts, chosen, navigation)
                        }

                        SetupStep.AboutYou -> {
                            AboutYouStep(
                                birthYear = birthYear,
                                onBirthYearChange = { birthYear = it },
                                family = family,
                                onFamilyChange = { family = it },
                                showFamily = needsFamilyCoverage(chosen.keys),
                            )
                        }
                    }
                }
            }
        },
        actions = {
            RetirementSetupActions(
                isLastStep = step == SetupStep.AboutYou,
                enabled = step != SetupStep.Countries || regions.isNotEmpty(),
                showBack = stepIndex > 0,
                onContinue = { stepIndex++ },
                onBack = { stepIndex-- },
            ) {
                val profiles = setupProfiles(chosen, regions, existing, birthYear.toIntOrNull()?.takeIf { it in 1900..thisYear }, family)
                scope.launch {
                    mobileSession.planning.saveRetirementSetup(state.selectedBudgetId, regions.map { it.name }, profiles)
                    navigation.back()
                }
            }
        },
    )
}

/** The birth year an earlier setup worked out, or blank. */
private fun savedBirthYear(
    profiles: Collection<RetirementProfile>,
    thisYear: Int,
): String =
    profiles
        .firstNotNullOfOrNull { it.estimatedBirthYear(thisYear) }
        ?.toString()
        .orEmpty()

/** Continue or finish, plus back once past the first step. */
@Composable
private fun RetirementSetupActions(
    isLastStep: Boolean,
    enabled: Boolean,
    showBack: Boolean,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit,
) {
    SetupPrimaryButton(if (isLastStep) "Finish" else "Continue", enabled = enabled) {
        if (!isLastStep) {
            onContinue()
            return@SetupPrimaryButton
        }
        onFinish()
    }
    if (showBack) SecondaryButton("Back", Modifier.fillMaxWidth()) { onBack() }
}

private fun savedRegions(codes: List<String>): List<Region> = codes.mapNotNull { code -> Region.entries.firstOrNull { it.name == code } }

private fun needsFamilyCoverage(planIds: Set<String>): Boolean =
    planIds.any { id -> RetirementPlans.byId(id)?.limits?.any { it.familyMinor != null } == true }

private fun setupProfiles(
    chosen: Map<String, String?>,
    regions: List<Region>,
    existing: Map<String, RetirementProfile>,
    birthYear: Int?,
    family: Boolean,
): List<RetirementProfile> {
    val inRegions = chosen.filterKeys { id -> RetirementPlans.byId(id)?.region in regions }
    return inRegions.map { (id, accountId) ->
        (
            existing[id] ?: RetirementProfile(
                id,
            )
        ).copy(accountId = accountId, birthYear = birthYear, legacyAge = null, legacyAgeYear = null, family = family)
    }
}

/** Picks every country the person has retirement accounts in: chosen ones, a few suggestions, and search for the rest. */
@Composable
private fun CountriesStep(
    regions: MutableList<Region>,
    lived: List<Region>,
    currencyCode: String,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var browsing by rememberSaveable { mutableStateOf(false) }
    val available = RetirementPlans.regions
    val toggle: (Region) -> Unit = { region -> if (region in regions) regions.remove(region) else regions.add(region) }
    Heading("Where do you save?")
    BodyText("Pick every country you have retirement or tax-advantaged accounts in. Each has its own accounts and limits.")
    regions.forEach { CountryCard(it, true, toggle) }
    TextFieldCard(
        "Find a country",
        query,
        { query = it },
        TextFieldOptions(placeholder = "Country or account, like RRSP", leadingIcon = Lucide.Search),
    )
    if (query.isNotBlank()) {
        val found = searchRegions(available, query)
        if (found.isEmpty()) {
            BodyText(
                "No country matches “${query.trim()}” yet. Tell us which one you need through Share feedback in the menu.",
            )
        }
        found.forEach { CountryCard(it, it in regions, toggle) }
        return
    }
    val suggested = suggestedRegions(available, lived, currencyCode, regions)
    if (suggested.isNotEmpty()) SetupLabel("SUGGESTED")
    suggested.forEach { CountryCard(it, false, toggle) }
    val rest = available.filter { it !in regions && it !in suggested }
    if (rest.isEmpty()) return
    SecondaryButton(
        when {
            browsing -> "Hide other countries"
            rest.size == 1 -> "Browse 1 more country"
            else -> "Browse ${rest.size} more countries"
        },
        Modifier.fillMaxWidth(),
    ) {
        browsing = !browsing
    }
    if (browsing) rest.forEach { CountryCard(it, false, toggle) }
}

@Composable
private fun CountryCard(
    region: Region,
    picked: Boolean,
    toggle: (Region) -> Unit,
) {
    CheckCard(
        region.displayName,
        RetirementPlans.forRegion(region).filterNot { it.advanced }.joinToString(", ") { it.displayName },
        picked,
    ) {
        toggle(region)
    }
}

@Composable
private fun SetupLabel(text: String) {
    Text(
        text,
        Modifier.semantics { heading() },
        color = tandemPalette.muted,
        fontSize = TandemTypography.caption,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
    )
}

/** Picks the retirement accounts held in one country. */
@Composable
private fun CountryStep(
    region: Region,
    accounts: List<LiveAccountSummary>,
    chosen: MutableMap<String, String?>,
    navigation: AppNavigation,
) {
    Heading("Your ${region.displayName} accounts")
    BodyText("Choose the ones you have, then the Actual account each one is tracked in.")
    val (advanced, common) = RetirementPlans.forRegion(region).partition { it.advanced }
    common.forEach { PlanChoice(it, accounts, chosen, navigation) }
    if (advanced.isEmpty()) return
    var showAdvanced by remember(region) { mutableStateOf(advanced.any { chosen.containsKey(it.id) }) }
    AdvancedToggle(advanced.joinToString(", ") { it.displayName }, showAdvanced) { showAdvanced = !showAdvanced }
    if (showAdvanced) advanced.forEach { PlanChoice(it, accounts, chosen, navigation) }
}

@Composable
private fun PlanChoice(
    plan: RetirementPlan,
    accounts: List<LiveAccountSummary>,
    chosen: MutableMap<String, String?>,
    navigation: AppNavigation,
) {
    val picked = chosen.containsKey(plan.id)
    CheckCard(plan.displayName, plan.description, picked) {
        if (picked) {
            chosen.remove(plan.id)
        } else {
            chosen[plan.id] = accounts.firstOrNull { plan.matchesAccount(it.name) }?.id
        }
    }
    if (picked) TrackedAccountPicker(plan.id, accounts, chosen, navigation)
}

/** Shows or hides the less common plans behind a settings gear. */
@Composable
private fun AdvancedToggle(
    names: String,
    open: Boolean,
    onToggle: () -> Unit,
) {
    val palette = tandemPalette
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = TandemLayout.listRowMinHeight)
            .clip(RoundedCornerShape(TandemShapes.large))
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(TandemSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
    ) {
        Icon(Lucide.Settings2, null, Modifier.size(20.dp), tint = palette.muted)
        Column(Modifier.weight(1f)) {
            Text("Advanced", color = palette.text, fontSize = TandemTypography.action, fontWeight = FontWeight.Medium)
            Text(names, color = palette.muted, fontSize = TandemTypography.compactBody)
        }
        Icon(if (open) Lucide.ChevronUp else Lucide.ChevronDown, null, Modifier.size(20.dp), tint = palette.muted)
    }
}

/** Chooses which Actual account tracks a retirement account, or offers to connect one. */
@Composable
private fun TrackedAccountPicker(
    planId: String,
    accounts: List<LiveAccountSummary>,
    chosen: MutableMap<String, String?>,
    navigation: AppNavigation,
) {
    val palette = tandemPalette
    Column(
        Modifier.padding(start = TandemSpacing.large),
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
    ) {
        Text("Tracked in Actual as", color = palette.muted, fontSize = TandemTypography.label)
        FilterPills(
            listOf<Pair<String?, String>>(null to "Not in Actual") + accounts.map { it.id to it.name },
            chosen[planId],
            { chosen[planId] = it },
        )
        if (chosen[planId] == null) {
            Text(
                "Connect it through bank sync so Tandem can read your contributions and the years you saved.",
                color = palette.muted,
                fontSize = TandemTypography.label,
            )
            SecondaryButton("Connect it") { navigation.open(AppRoute.BANK_CONNECTIONS) }
        }
    }
}

/** Asks for the person's birth year and, when a plan needs it, their health coverage. */
@Composable
private fun AboutYouStep(
    birthYear: String,
    onBirthYearChange: (String) -> Unit,
    family: Boolean,
    onFamilyChange: (Boolean) -> Unit,
    showFamily: Boolean,
) {
    val palette = tandemPalette
    Heading("A little about you")
    BodyText("The year you were born sets catch-up limits as you get older. It stays in this budget’s encrypted planning data.")
    TextFieldCard(
        "Year you were born",
        birthYear,
        { onBirthYearChange(it.filter(Char::isDigit).take(4)) },
        TextFieldOptions(
            placeholder = "Optional",
            keyboardOptions =
                androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                ),
        ),
    )
    if (showFamily) {
        Text("Health coverage", color = palette.muted, fontSize = TandemTypography.label)
        FilterPills(listOf(false to "Just me", true to "Family"), family, onFamilyChange)
    }
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
