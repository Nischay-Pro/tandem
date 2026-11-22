@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.retirement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import app.tandemforactual.data.planning.RetirementProfile
import app.tandemforactual.data.regions.us.retirement.k401.EmployerMatch
import app.tandemforactual.data.regions.us.retirement.k401.K401
import app.tandemforactual.data.regions.us.retirement.k401.MatchBand
import app.tandemforactual.data.regions.us.retirement.k401.MatchTier
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.inputs.FilterPills
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Trash2
import kotlinx.coroutines.launch

private class TierDraft(
    rate: String,
    band: MatchBand,
    size: String,
) {
    var rate by mutableStateOf(rate)
    var band by mutableStateOf(band)
    var size by mutableStateOf(size)

    fun toTier(): MatchTier? {
        val rate = rate.toIntOrNull() ?: return null
        val size = size.toLongOrNull() ?: return null
        return MatchTier(rate * 100, band, if (band == MatchBand.AMOUNT) size * 100 else size * 100)
    }

    companion object {
        fun of(tier: MatchTier) = TierDraft((tier.rateBasisPoints / 100).toString(), tier.band, (tier.size / 100).toString())
    }
}

private val numbers = TextFieldOptions(keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))

/** Defines a 401(k) employer match tier by tier, from a preset or from scratch, with a live preview of what it adds. */
@Composable
internal fun MatchEditorScreen(
    state: AppState,
    navigation: AppNavigation,
    mobileSession: TandemMobileSession,
) {
    val scope = rememberCoroutineScope()
    val profile = mobileSession.planning.retirementProfiles.firstOrNull { it.planId == K401.id } ?: RetirementProfile(K401.id)
    val tiers =
        remember {
            mutableStateListOf<TierDraft>().apply {
                addAll(
                    profile.match
                        ?.tiers
                        .orEmpty()
                        .map(TierDraft::of),
                )
            }
        }
    var salary by remember { mutableStateOf(profile.salaryMinor?.let { (it / 100).toString() }.orEmpty()) }
    var cap by remember {
        mutableStateOf(
            profile.match
                ?.capMinor
                ?.let { (it / 100).toString() }
                .orEmpty(),
        )
    }
    var percent by remember { mutableStateOf(profile.contributionBasisPoints?.let { (it / 100).toString() }.orEmpty()) }
    val match = EmployerMatch(tiers.mapNotNull { it.toTier() }, cap.toLongOrNull()?.times(100))
    val salaryMinor = salary.toLongOrNull()?.times(100)
    val palette = tandemPalette
    val year = platformTodayIsoDate().take(4).toInt()
    PageColumn(verticalSpacing = TandemSpacing.large) {
        Text(
            "Start from a common formula, or build your employer’s own tier by tier.",
            color = palette.muted,
            fontSize = TandemTypography.compactBody,
        )
        FilterPills(
            EmployerMatch.presets.mapIndexed { index, (label, _) -> index to label },
            EmployerMatch.presets.indexOfFirst { it.second.tiers == match.tiers },
            { index ->
                tiers.clear()
                tiers.addAll(
                    EmployerMatch.presets[index]
                        .second.tiers
                        .map(TierDraft::of),
                )
            },
        )
        MatchTiersCard(tiers)
        TextFieldCard("Yearly match cap ($, optional)", cap, { cap = it.filter(Char::isDigit) }, numbers)
        TextFieldCard("Your yearly salary ($)", salary, { salary = it.filter(Char::isDigit) }, numbers)
        TextFieldCard("You plan to contribute (% of pay)", percent, { percent = it.filter(Char::isDigit) }, numbers)
        if (salaryMinor != null && match.tiers.isNotEmpty()) MatchPreviewCard(year, profile, salaryMinor, percent, match)
        TandemActionButton("Save match", TandemActionButtonTone.PRIMARY, Modifier.fillMaxWidth()) {
            scope.launch {
                mobileSession.planning.saveRetirementProfile(
                    state.selectedBudgetId,
                    profile.copy(
                        match = match.takeIf { it.tiers.isNotEmpty() },
                        salaryMinor = salaryMinor,
                        contributionBasisPoints = percent.toIntOrNull()?.times(100),
                    ),
                )
                navigation.back()
            }
        }
    }
}

/** Each match tier with its rate, band and size, and a button to add another. */
@Composable
private fun MatchTiersCard(tiers: MutableList<TierDraft>) {
    val palette = tandemPalette
    SectionCard("Match tiers", "Applied in order to what you put in") {
        Column(Modifier.padding(TandemSpacing.medium), verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
            tiers.forEachIndexed { index, tier ->
                Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        Text(if (index == 0) "First tier" else "Then", color = palette.muted, fontSize = TandemTypography.label)
                        TextFieldCard("Employer adds (%)", tier.rate, { tier.rate = it.filter(Char::isDigit) }, numbers)
                        TextFieldCard(
                            if (tier.band == MatchBand.AMOUNT) "Of the next ($)" else "Of the next (% of pay)",
                            tier.size,
                            { tier.size = it.filter(Char::isDigit) },
                            numbers,
                        )
                        FilterPills(listOf(MatchBand.PERCENT_OF_SALARY to "% of pay", MatchBand.AMOUNT to "Dollar amount"), tier.band, {
                            tier.band =
                                it
                        })
                    }
                    TandemIconButton(Lucide.Trash2, "Remove tier") { tiers.removeAt(index) }
                }
            }
            TandemActionButton("Add a tier", TandemActionButtonTone.NEUTRAL, Modifier.fillMaxWidth(), icon = Lucide.Plus) {
                tiers.add(TierDraft("50", MatchBand.PERCENT_OF_SALARY, "2"))
            }
        }
    }
}

/** What you and your employer put in this year at the planned contribution. */
@Composable
private fun MatchPreviewCard(
    year: Int,
    profile: RetirementProfile,
    salaryMinor: Long,
    percent: String,
    match: EmployerMatch,
) {
    val palette = tandemPalette
    val planned = percent.toLongOrNull()?.let { salaryMinor * it / 100 } ?: 0
    val result = K401.match(year, profile.age, salaryMinor, planned, match)
    val full = match.fullMatchAt(salaryMinor)
    SectionCard("Preview for $year", null) {
        Column(Modifier.padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
            Text(
                "At ${percent.ifBlank { "0" }}% you put in ${planMoney(result.employeeMinor, "USD")}.",
                color = palette.text,
                fontSize = TandemTypography.body,
            )
            Text(
                "Your employer adds ${planMoney(result.employerMinor, "USD")}.",
                color = palette.text,
                fontSize = TandemTypography.body,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (result.missedMinor > 0) {
                    "Put in ${planMoney(full, "USD")} to collect the remaining ${planMoney(result.missedMinor, "USD")}."
                } else {
                    "That collects the full match."
                },
                color = if (result.missedMinor > 0) palette.warning else palette.positive,
                fontSize = TandemTypography.compactBody,
            )
        }
    }
}
