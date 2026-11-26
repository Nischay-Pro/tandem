@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.retirement

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.planning.AccountMovement
import app.tandemforactual.data.planning.JurisdictionTimeline
import app.tandemforactual.data.planning.PeriodKind
import app.tandemforactual.data.planning.RetirementContributions
import app.tandemforactual.data.planning.RetirementProfile
import app.tandemforactual.data.planning.RetirementRoom
import app.tandemforactual.data.planning.YearMovements
import app.tandemforactual.data.regions.Region
import app.tandemforactual.data.regions.ca.retirement.fhsa.Fhsa
import app.tandemforactual.data.regions.ca.retirement.rrsp.Rrsp
import app.tandemforactual.data.regions.ca.retirement.tfsa.Tfsa
import app.tandemforactual.data.regions.global.retirement.RetirementPlan
import app.tandemforactual.data.regions.global.retirement.RetirementPlans
import app.tandemforactual.data.regions.us.retirement.k401.EmployerMatch
import app.tandemforactual.data.regions.us.retirement.k401.K401
import app.tandemforactual.ui.core.format.formatMonth
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.feedback.EmptyStateCard
import app.tandemforactual.ui.design.inputs.FilterPills
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plane
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Settings2

/**
 * Contribution room for the plans set up in the wizard. Contributions come from each plan's Actual account, so the years
 * offered are the ones with deposits, plus this year.
 */
@Composable
internal fun RetirementScreen(
    state: AppState,
    navigation: AppNavigation,
    mobileSession: TandemMobileSession,
    liveAccounts: List<LiveAccountSummary>,
) {
    val profiles = mobileSession.planning.retirementProfiles.filter { RetirementPlans.byId(it.planId) != null }
    val palette = tandemPalette
    if (profiles.isEmpty()) {
        PageColumn(verticalSpacing = TandemSpacing.large) {
            EmptyStateCard(
                "Track your retirement accounts",
                "Tell Tandem where you save and which Actual accounts hold your 401(k), IRA, PPF or others. It works out each year’s room from your deposits.",
            )
            TandemActionButton("Set up", TandemActionButtonTone.PRIMARY, Modifier.fillMaxWidth(), icon = Lucide.Settings2) {
                navigation.open(AppRoute.RETIREMENT_SETUP)
            }
        }
        return
    }
    val today = platformTodayIsoDate()
    val currentYear = today.take(4).toInt()
    var movements by remember(state.selectedBudgetId) { mutableStateOf<Map<String, List<AccountMovement>>>(emptyMap()) }
    val accountIds = profiles.mapNotNull { it.accountId }.distinct()
    LaunchedEffect(state.selectedBudgetId, accountIds) {
        movements =
            mobileSession.insights.loadAccountMovements(
                state.selectedBudgetId,
                accountIds,
                state.encryptedPassword.takeIf { it.isNotBlank() },
            )
    }
    val years =
        (movements.values.flatMap { list -> list.map { it.date.take(4).toInt() } } + currentYear)
            .filter { it <= currentYear }
            .distinct()
            .sortedDescending()
    var year by rememberSaveable { mutableStateOf(currentYear) }
    PageColumn(verticalSpacing = TandemSpacing.large) {
        if (years.size > 1) FilterPills(years.map { it to it.toString() }, year, { year = it })
        if (profiles.any { it.needsBirthYear }) BirthYearNotice { navigation.open(AppRoute.RETIREMENT_SETUP) }
        val order = mobileSession.planning.settings.regions
        val periods = mobileSession.planning.jurisdictionPeriods
        val livingIn = JurisdictionTimeline.regionsIn(periods, PeriodKind.RESIDENCE, today.take(7))
        val context = PlanContext(profiles, liveAccounts, movements, year, today, navigation)
        profiles
            .groupBy { RetirementPlans.byId(it.planId)!!.region }
            .entries
            .sortedBy { (region, _) -> order.indexOf(region.name).let { if (it < 0) Int.MAX_VALUE else it } }
            .forEach { (region, regionProfiles) -> RegionPlans(region, regionProfiles, livingIn, context) }
        TandemActionButton("Change accounts", TandemActionButtonTone.NEUTRAL, Modifier.fillMaxWidth(), icon = Lucide.Settings2) {
            navigation.open(AppRoute.RETIREMENT_SETUP)
        }
        TandemActionButton("Advanced", TandemActionButtonTone.NEUTRAL, Modifier.fillMaxWidth(), icon = Lucide.Settings) {
            navigation.open(AppRoute.RETIREMENT_LIMITS)
        }
        PublicPensionsSummary(mobileSession) { navigation.open(AppRoute.PUBLIC_PENSIONS) }
        TandemActionButton("Moving countries", TandemActionButtonTone.NEUTRAL, Modifier.fillMaxWidth(), icon = Lucide.Plane) {
            navigation.open(AppRoute.RELOCATION)
        }
        Text(
            "Limits are each year’s published figures unless you enter your own under Advanced. Contributions are what you put into the linked account; dividends, interest, fees, employer deposits and moves between your own plans aren’t counted. Indian plans follow the April to March financial year.",
            color = palette.muted,
            fontSize = TandemTypography.label,
        )
    }
}

/** What every plan card on the page reads from. */
private class PlanContext(
    val profiles: List<RetirementProfile>,
    val liveAccounts: List<LiveAccountSummary>,
    val movements: Map<String, List<AccountMovement>>,
    val year: Int,
    val today: String,
    val navigation: AppNavigation,
) {
    val planAccountIds = profiles.mapNotNull { it.accountId }.toSet()

    /** The plan year to show: today's for the current calendar year, so an April-to-March plan shows its running year. */
    fun yearFor(plan: RetirementPlan): Int = if (year == today.take(4).toInt()) plan.planYear(today) else year

    /** Yearly totals across the accounts of [linked], counting an account shared by two plans once. */
    fun totals(
        plan: RetirementPlan,
        linked: List<RetirementProfile>,
    ): Map<Int, YearMovements> =
        RetirementRoom.merge(
            linked.mapNotNull { it.accountId }.distinct().map { id ->
                RetirementContributions.byPlanYear(plan, movements[id].orEmpty(), planAccountIds - id)
            },
        )
}

/** Asks for a birth year once, for ages saved before Tandem kept birth years. */
@Composable
private fun BirthYearNotice(onOpen: () -> Unit) {
    Text(
        "Confirm the year you were born so catch-up limits stay right each year",
        Modifier.clip(RoundedCornerShape(TandemShapes.control)).clickable(role = Role.Button, onClick = onOpen),
        color = tandemPalette.warning,
        fontSize = TandemTypography.compactBody,
        fontWeight = FontWeight.Medium,
    )
}

/** One country's heading and its plans; plans that share another's room are folded into that plan's card. */
@Composable
private fun RegionPlans(
    region: Region,
    regionProfiles: List<RetirementProfile>,
    livingIn: List<Region>,
    context: PlanContext,
) {
    val palette = tandemPalette
    Text(region.displayName.uppercase(), color = palette.muted, fontSize = TandemTypography.caption)
    if (livingIn.isNotEmpty() && region !in livingIn) {
        Text(
            "You don’t live here now. These plans keep their history; check the rules before paying in.",
            color = palette.muted,
            fontSize = TandemTypography.label,
        )
    }
    val profiles = context.profiles
    val setUp = profiles.map { it.planId }.toSet()
    regionProfiles.filter { RetirementPlans.byId(it.planId)?.sharesRoomWith !in setUp }.forEach { profile ->
        val plan = RetirementPlans.byId(profile.planId)!!
        val sharing = profiles.filter { RetirementPlans.byId(it.planId)?.sharesRoomWith == plan.id }
        val accountName = profile.accountId?.let { id -> context.liveAccounts.firstOrNull { it.id == id }?.name }
        PlanCard(
            plan,
            profile,
            accountName,
            context.yearFor(plan),
            context.totals(plan, listOf(profile) + sharing),
            context,
            sharedWith = sharing.mapNotNull { RetirementPlans.byId(it.planId)?.displayName },
        )
    }
}

/** A plan year as people write it: the calendar year, or a financial year such as FY 2026–27. */
internal fun planYearLabel(
    plan: RetirementPlan,
    year: Int,
): String = if (plan.yearStartMonth == 1) year.toString() else "FY $year–${((year + 1) % 100).toString().padStart(2, '0')}"

@Composable
private fun PlanCard(
    plan: RetirementPlan,
    profile: RetirementProfile,
    accountName: String?,
    year: Int,
    years: Map<Int, YearMovements>,
    context: PlanContext,
    sharedWith: List<String>,
) {
    val palette = tandemPalette
    val lookup = plan.limitFor(year)
    val custom = year in profile.limitOverrides
    val result = RetirementRoom.forYear(plan, profile, year, years, plan.planYear(context.today))
    val yours = result.yoursMinor
    val employer = result.employerMinor
    val label = planYearLabel(plan, year)

    fun money(minor: Long) = planMoney(minor, plan.currency)
    SectionCard(plan.displayName, accountName?.let { "Linked to $it" } ?: "No Actual account linked") {
        Column(
            Modifier.padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
        ) {
            Row {
                Column(Modifier.weight(1f)) {
                    Text("Room left in $label", color = palette.muted, fontSize = TandemTypography.label)
                    Text(
                        money(result.roomMinor),
                        color = palette.text,
                        fontSize = TandemTypography.cardTitle,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Column {
                    Text("Your limit", color = palette.muted, fontSize = TandemTypography.label)
                    Text(
                        money(result.allowanceMinor),
                        color = palette.text,
                        fontSize = TandemTypography.body,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            PlanProgressBar(yours.toFloat() / result.allowanceMinor.coerceAtLeast(1))
            Text(
                when {
                    accountName == null -> "Link an Actual account to read contributions."
                    employer > 0 -> "${money(yours)} from you and ${money(employer)} from your employer"
                    else -> "${money(yours)} put in"
                },
                color = palette.muted,
                fontSize = TandemTypography.caption,
            )
            if (custom) {
                Text("Using the limit you entered for $label.", color = palette.muted, fontSize = TandemTypography.caption)
            } else if (!lookup.published) {
                Text(
                    "$label limits aren’t published yet; showing ${planYearLabel(plan, lookup.limit.year)}.",
                    color = palette.warning,
                    fontSize = TandemTypography.caption,
                )
            }
            if (plan == K401) EmployerMatchStatus(year, profile, yours, ::money) { context.navigation.open(AppRoute.RETIREMENT_MATCH) }
            PlanRoomNotes(plan, result.carriesForward && !custom, sharedWith) {
                context.navigation.open(AppRoute.RRSP_ROOM)
            }
            if (plan == K401) {
                val asOf = if (year == context.today.take(4).toInt()) context.today else "$year-12-31"
                VestingStatus(profile.match, asOf, RetirementRoom.employerThrough(plan, profile, years, year), ::money)
            }
        }
    }
}

/** How much of the yearly allowance has been used. */
@Composable
private fun PlanProgressBar(fraction: Float) {
    val palette = tandemPalette
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(TandemShapes.pill))
            .background(palette.raised),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(RoundedCornerShape(TandemShapes.pill))
                .background(palette.accent),
        )
    }
}

/** Whether the full 401(k) employer match is on track, tapping through to edit the match. */
@Composable
private fun EmployerMatchStatus(
    year: Int,
    profile: RetirementProfile,
    yours: Long,
    money: (Long) -> String,
    onEditMatch: () -> Unit,
) {
    val palette = tandemPalette
    val match = profile.match
    val salary = profile.salaryMinor
    val result = if (match != null && salary != null) K401.match(year, profile.ageIn(year), salary, yours, match) else null
    Text(
        when {
            result == null -> "Set up your employer match"
            result.missedMinor > 0 -> "${money(result.missedMinor)} of employer match still available this year"
            else -> "Full employer match on track"
        },
        Modifier.clip(RoundedCornerShape(TandemShapes.control)).clickable(role = Role.Button, onClick = onEditMatch),
        color =
            when {
                result == null -> palette.accent
                result.missedMinor > 0 -> palette.warning
                else -> palette.positive
            },
        fontSize = TandemTypography.compactBody,
        fontWeight = FontWeight.Medium,
    )
}

/** Which plans share this room, and for RRSP, TFSA and FHSA, a link to enter the room CRA shows. */
@Composable
private fun PlanRoomNotes(
    plan: RetirementPlan,
    ownRoom: Boolean,
    sharedWith: List<String>,
    onEditRoom: () -> Unit,
) {
    val palette = tandemPalette
    if (sharedWith.isNotEmpty()) {
        Text(
            "Includes ${sharedWith.joinToString()}, which shares this room",
            color = palette.muted,
            fontSize = TandemTypography.caption,
        )
    }
    val prompt =
        when {
            plan == Rrsp && ownRoom -> "Your own room, from your notice of assessment"
            plan == Rrsp -> "Work out your own room from your notice of assessment"
            plan == Tfsa && ownRoom -> "Includes room carried forward from CRA’s figure"
            plan == Tfsa -> "Add your room from CRA to include unused room from earlier years"
            plan == Fhsa && ownRoom -> "Includes up to \$8,000 carried forward, within the \$40,000 lifetime limit"
            else -> null
        } ?: return
    Text(
        prompt,
        Modifier.clip(RoundedCornerShape(TandemShapes.control)).clickable(role = Role.Button, onClick = onEditRoom),
        color = palette.accent,
        fontSize = TandemTypography.compactBody,
        fontWeight = FontWeight.Medium,
    )
}

/** How much of the employer's money, from every year so far, is yours yet when the match vests over time. */
@Composable
private fun VestingStatus(
    match: EmployerMatch?,
    asOf: String,
    employerMinor: Long,
    money: (Long) -> String,
) {
    if (employerMinor <= 0) return
    val vested = match?.vestedBasisPoints(asOf)?.takeIf { it < 10_000 } ?: return
    val fully = match.fullyVestedMonth?.let { formatMonth(it.take(4).toInt(), it.substring(5, 7).toInt()) }
    Text(
        "${money(employerMinor * vested / 10_000)} of the employer’s ${money(employerMinor)} so far is yours" +
            (fully?.let { "; all of it from $it" } ?: ""),
        color = tandemPalette.muted,
        fontSize = TandemTypography.caption,
    )
}

/** Whole-unit amounts in the plan's own currency, grouped the way that country writes them. */
internal fun planMoney(
    minor: Long,
    currency: String,
): String {
    val digits = (kotlin.math.abs(minor) / 100).toString()
    val grouped =
        if (currency == "INR" && digits.length > 3) {
            digits
                .dropLast(3)
                .reversed()
                .chunked(2)
                .joinToString(",")
                .reversed() + "," + digits.takeLast(3)
        } else {
            digits
                .reversed()
                .chunked(3)
                .joinToString(",")
                .reversed()
        }
    val symbol =
        when (currency) {
            "USD" -> "$"
            "CAD" -> "CA$"
            "INR" -> "₹"
            else -> "$currency "
        }
    return (if (minor < 0) "-" else "") + symbol + grouped
}
