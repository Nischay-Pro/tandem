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
import app.tandemforactual.data.planning.JurisdictionTimeline
import app.tandemforactual.data.planning.PeriodKind
import app.tandemforactual.data.planning.RetirementProfile
import app.tandemforactual.data.regions.global.retirement.RetirementPlan
import app.tandemforactual.data.regions.global.retirement.RetirementPlans
import app.tandemforactual.data.regions.us.retirement.k401.K401
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
    val currentYear = platformTodayIsoDate().take(4).toInt()
    var inflows by remember(state.selectedBudgetId) { mutableStateOf<Map<String, Map<Int, Long>>>(emptyMap()) }
    val accountIds = profiles.mapNotNull { it.accountId }
    LaunchedEffect(state.selectedBudgetId, accountIds) {
        inflows =
            mobileSession.insights.loadAccountInflows(
                state.selectedBudgetId,
                accountIds,
                state.encryptedPassword.takeIf { it.isNotBlank() },
            )
    }
    val years = (inflows.values.flatMap { it.keys } + currentYear).filter { it <= currentYear }.distinct().sortedDescending()
    var year by rememberSaveable { mutableStateOf(currentYear) }
    PageColumn(verticalSpacing = TandemSpacing.large) {
        if (years.size > 1) FilterPills(years.map { it to it.toString() }, year, { year = it })
        val order = mobileSession.planning.settings.regions
        val periods = mobileSession.planning.jurisdictionPeriods
        val livingIn = JurisdictionTimeline.regionsIn(periods, PeriodKind.RESIDENCE, platformTodayIsoDate().take(7))
        profiles
            .groupBy { RetirementPlans.byId(it.planId)!!.region }
            .entries
            .sortedBy { (region, _) -> order.indexOf(region.name).let { if (it < 0) Int.MAX_VALUE else it } }
            .forEach { (region, regionProfiles) ->
                Text(region.displayName.uppercase(), color = palette.muted, fontSize = TandemTypography.caption)
                if (livingIn.isNotEmpty() && region !in livingIn) {
                    Text(
                        "You don’t live here now. These plans keep their history; check the rules before paying in.",
                        color = palette.muted,
                        fontSize = TandemTypography.label,
                    )
                }
                regionProfiles.forEach { profile ->
                    val plan = RetirementPlans.byId(profile.planId)!!
                    val accountName = profile.accountId?.let { id -> liveAccounts.firstOrNull { it.id == id }?.name }
                    PlanCard(plan, profile, accountName, year, profile.accountId?.let { inflows[it]?.get(year) } ?: 0) {
                        navigation.open(AppRoute.RETIREMENT_MATCH)
                    }
                }
            }
        TandemActionButton("Change accounts", TandemActionButtonTone.NEUTRAL, Modifier.fillMaxWidth(), icon = Lucide.Settings2) {
            navigation.open(AppRoute.RETIREMENT_SETUP)
        }
        TandemActionButton("Advanced", TandemActionButtonTone.NEUTRAL, Modifier.fillMaxWidth(), icon = Lucide.Settings) {
            navigation.open(AppRoute.RETIREMENT_LIMITS)
        }
        TandemActionButton("Moving countries", TandemActionButtonTone.NEUTRAL, Modifier.fillMaxWidth(), icon = Lucide.Plane) {
            navigation.open(AppRoute.RELOCATION)
        }
        Text(
            "Limits are each year’s published figures unless you enter your own under Advanced. Contributions are deposits and transfers into the linked account.",
            color = palette.muted,
            fontSize = TandemTypography.label,
        )
    }
}

@Composable
private fun PlanCard(
    plan: RetirementPlan,
    profile: RetirementProfile,
    accountName: String?,
    year: Int,
    depositsMinor: Long,
    onEditMatch: () -> Unit,
) {
    val palette = tandemPalette
    val lookup = plan.limitFor(year)
    val allowance = profile.allowance(plan, year)
    val custom = year in profile.limitOverrides
    val match = profile.match
    val salary = profile.salaryMinor
    val (yours, employer) =
        if (plan == K401 && match != null && salary != null) K401.splitDeposits(depositsMinor, salary, match) else depositsMinor to 0L
    val room = (allowance - yours).coerceAtLeast(0)

    fun money(minor: Long) = planMoney(minor, plan.currency)
    SectionCard(plan.displayName, accountName?.let { "Linked to $it" } ?: "No Actual account linked") {
        Column(
            Modifier.padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
        ) {
            Row {
                Column(Modifier.weight(1f)) {
                    Text("Room left in $year", color = palette.muted, fontSize = TandemTypography.label)
                    Text(money(room), color = palette.text, fontSize = TandemTypography.cardTitle, fontWeight = FontWeight.SemiBold)
                }
                Column {
                    Text("Your limit", color = palette.muted, fontSize = TandemTypography.label)
                    Text(money(allowance), color = palette.text, fontSize = TandemTypography.body, fontWeight = FontWeight.Medium)
                }
            }
            PlanProgressBar(yours.toFloat() / allowance.coerceAtLeast(1))
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
                Text("Using the limit you entered for $year.", color = palette.muted, fontSize = TandemTypography.caption)
            } else if (!lookup.published) {
                Text(
                    "$year limits aren’t published yet; showing ${lookup.limit.year}.",
                    color = palette.warning,
                    fontSize = TandemTypography.caption,
                )
            }
            if (plan == K401) EmployerMatchStatus(year, profile, yours, ::money, onEditMatch)
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
    val result = if (match != null && salary != null) K401.match(year, profile.age, salary, yours, match) else null
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
