@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.planning.RetirementProfile
import app.tandemforactual.data.regions.Region
import app.tandemforactual.data.regions.global.retirement.RetirementPlan
import app.tandemforactual.data.regions.global.retirement.RetirementPlans
import app.tandemforactual.data.regions.us.retirement.k401.EmployerMatch
import app.tandemforactual.data.regions.us.retirement.k401.K401
import app.tandemforactual.ui.components.FilterPills
import app.tandemforactual.ui.components.SectionCard
import app.tandemforactual.ui.components.SurfaceCard
import app.tandemforactual.ui.components.TandemActionButton
import app.tandemforactual.ui.components.TandemActionButtonTone
import app.tandemforactual.ui.components.TandemChoiceDialog
import app.tandemforactual.ui.components.TandemModalHeader
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.PiggyBank
import kotlinx.coroutines.launch

/** Contribution room for each tax-advantaged account in the chosen region and year, using that year's published limits. */
@Composable
internal fun RetirementScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
) {
    val scope = rememberCoroutineScope()
    val currentYear = platformTodayIsoDate().take(4).toInt()
    val defaultRegion =
        when (platformCurrencyCode()) {
            "CAD" -> Region.CA
            "INR" -> Region.IN
            else -> Region.US
        }
    var region by rememberSaveable { mutableStateOf(defaultRegion) }
    var year by rememberSaveable { mutableStateOf(currentYear) }
    var editing by remember { mutableStateOf<RetirementPlan?>(null) }
    val profiles = mobileSession.retirementProfiles.associateBy { it.planId }
    val palette = tandemPalette
    PageColumn(verticalSpacing = TandemSpacing.large) {
        FilterPills(RetirementPlans.regions.map { it to it.displayName }, region, { region = it })
        FilterPills((currentYear downTo currentYear - 3).map { it to it.toString() }, year, { year = it })
        RetirementPlans.forRegion(region).forEach { plan ->
            val profile = profiles[plan.id] ?: RetirementProfile(plan.id)
            PlanCard(plan, profile.takeIf { it.year == null || it.year == year } ?: RetirementProfile(plan.id), year) { editing = plan }
        }
        Text(
            "Limits are the published figures for each year. Check your plan’s own rules; employers and income can lower them.",
            color = palette.muted,
            fontSize = TandemTypography.label,
        )
    }
    val plan = editing
    if (plan != null) {
        ProfileEditor(plan, profiles[plan.id] ?: RetirementProfile(plan.id), year, onDismiss = { editing = null }) { updated ->
            editing = null
            scope.launch { mobileSession.saveRetirementProfile(state.selectedBudgetId, updated.copy(year = year)) }
        }
    }
}

@Composable
private fun PlanCard(
    plan: RetirementPlan,
    profile: RetirementProfile,
    year: Int,
    onEdit: () -> Unit,
) {
    val palette = tandemPalette
    val lookup = plan.limitFor(year)
    val allowance = plan.allowance(year, profile.age, profile.family)
    val room = plan.room(year, profile.age, profile.contributedMinor, profile.family)

    fun money(minor: Long) = planMoney(minor, plan.currency)
    SectionCard(plan.displayName, plan.description, "Edit", onClick = onEdit) {
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
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(TandemShapes.pill))
                    .background(palette.raised),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth((profile.contributedMinor.toFloat() / allowance.coerceAtLeast(1)).coerceIn(0f, 1f))
                        .height(6.dp)
                        .clip(RoundedCornerShape(TandemShapes.pill))
                        .background(palette.accent),
                )
            }
            Text(
                "${money(profile.contributedMinor)} put in so far" +
                    (if (allowance > lookup.limit.standardMinor && !profile.family) " · includes catch-up" else ""),
                color = palette.muted,
                fontSize = TandemTypography.caption,
            )
            if (!lookup.published) {
                Text(
                    "$year limits aren’t published yet; showing ${lookup.limit.year}.",
                    color = palette.warning,
                    fontSize = TandemTypography.caption,
                )
            }
            val salary = profile.salaryMinor
            val contribution = profile.contributionBasisPoints
            val matchRate = profile.matchRateBasisPoints
            if (plan == K401 && salary != null && contribution != null && matchRate != null) {
                val result =
                    K401.match(
                        year,
                        profile.age,
                        salary,
                        contribution,
                        EmployerMatch(matchRate, profile.matchUpToBasisPoints ?: 0),
                    )
                Text(
                    "Employer adds ${money(result.employerMinor)} a year" +
                        if (result.missedMinor >
                            0
                        ) {
                            ". You’re leaving ${money(result.missedMinor)} of match unclaimed."
                        } else {
                            ", the full match."
                        },
                    color = if (result.missedMinor > 0) palette.warning else palette.positive,
                    fontSize = TandemTypography.compactBody,
                )
            }
        }
    }
}

@Composable
private fun ProfileEditor(
    plan: RetirementPlan,
    profile: RetirementProfile,
    year: Int,
    onDismiss: () -> Unit,
    onSave: (RetirementProfile) -> Unit,
) {
    var age by remember(plan) { mutableStateOf(profile.age?.toString().orEmpty()) }
    var contributed by remember(plan) {
        mutableStateOf(
            if (profile.contributedMinor ==
                0L
            ) {
                ""
            } else {
                (profile.contributedMinor / 100).toString()
            },
        )
    }
    var family by remember(plan) { mutableStateOf(profile.family) }
    var salary by remember(plan) { mutableStateOf(profile.salaryMinor?.let { (it / 100).toString() }.orEmpty()) }
    var percent by remember(plan) { mutableStateOf(profile.contributionBasisPoints?.let { (it / 100).toString() }.orEmpty()) }
    var matchRate by remember(plan) { mutableStateOf(profile.matchRateBasisPoints?.let { (it / 100).toString() }.orEmpty()) }
    var matchUpTo by remember(plan) { mutableStateOf(profile.matchUpToBasisPoints?.let { (it / 100).toString() }.orEmpty()) }

    fun whole(text: String) = text.filter(Char::isDigit).toLongOrNull()
    TandemChoiceDialog(visible = true, onDismissRequest = onDismiss) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 480.dp).padding(horizontal = TandemSpacing.large)) {
            Column(Modifier.padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                TandemModalHeader("${plan.displayName} for $year", onClose = onDismiss, leadingIcon = Lucide.PiggyBank)
                TextFieldCard("Contributed so far this year", contributed, { contributed = it }, numberField("0"))
                TextFieldCard("Your age at the end of $year", age, { age = it }, numberField("For catch-up limits"))
                if (plan.limits.any { it.familyMinor != null }) {
                    FilterPills(listOf(false to "Self-only", true to "Family"), family, { family = it })
                }
                if (plan == K401) {
                    TextFieldCard("Yearly salary", salary, { salary = it }, numberField("Optional, for the match"))
                    TextFieldCard("You contribute (% of salary)", percent, { percent = it }, numberField("e.g. 6"))
                    TextFieldCard("Employer matches (% of yours)", matchRate, { matchRate = it }, numberField("e.g. 50"))
                    TextFieldCard("…on up to (% of salary)", matchUpTo, { matchUpTo = it }, numberField("e.g. 6"))
                }
                TandemActionButton("Save", TandemActionButtonTone.PRIMARY, Modifier.fillMaxWidth()) {
                    onSave(
                        profile.copy(
                            age = whole(age)?.toInt(),
                            family = family,
                            contributedMinor = (whole(contributed) ?: 0) * 100,
                            salaryMinor = whole(salary)?.times(100),
                            contributionBasisPoints = whole(percent)?.toInt()?.times(100),
                            matchRateBasisPoints = whole(matchRate)?.toInt()?.times(100),
                            matchUpToBasisPoints = whole(matchUpTo)?.toInt()?.times(100),
                        ),
                    )
                }
            }
        }
    }
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

private fun numberField(placeholder: String) =
    TextFieldOptions(
        placeholder = placeholder,
        keyboardOptions =
            androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
            ),
    )
