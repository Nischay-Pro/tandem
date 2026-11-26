@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.retirement

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import app.tandemforactual.data.planning.RetirementProfile
import app.tandemforactual.data.planning.RoomStart
import app.tandemforactual.data.planning.RrspInputs
import app.tandemforactual.data.regions.ca.retirement.fhsa.Fhsa
import app.tandemforactual.data.regions.ca.retirement.rrsp.Rrsp
import app.tandemforactual.data.regions.ca.retirement.tfsa.Tfsa
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.buttons.SecondaryButton
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import kotlinx.coroutines.launch

private val dollars = TextFieldOptions(placeholder = "0", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))

/** Room CRA shows: RRSP room worked out from last year's figures, and TFSA and FHSA room to carry forward from. */
@Composable
internal fun RrspRoomScreen(
    state: AppState,
    navigation: AppNavigation,
    mobileSession: TandemMobileSession,
) {
    val scope = rememberCoroutineScope()
    val profiles = mobileSession.planning.retirementProfiles
    val year = platformTodayIsoDate().take(4).toInt()
    val rrsp = profiles.firstOrNull { it.planId == Rrsp.id }
    val carried = listOfNotNull(profiles.firstOrNull { it.planId == Tfsa.id }, profiles.firstOrNull { it.planId == Fhsa.id })
    val rrspDraft = remember { RrspDraft(rrsp?.rrspInputs) }
    val starts =
        remember {
            carried.associate {
                it.planId to
                    mutableStateOf(
                        it.roomStart
                            ?.takeIf { s -> s.year == year }
                            ?.amountMinor
                            ?.let { m -> (m / 100).toString() }
                            .orEmpty(),
                    )
            }
        }
    PageColumn(verticalSpacing = TandemSpacing.large) {
        if (rrsp != null || carried.isEmpty()) RrspFields(year, rrspDraft)
        carried.forEach { profile ->
            val field = starts.getValue(profile.planId)
            TextFieldCard(
                if (profile.planId == Tfsa.id) "TFSA room on January 1, $year" else "FHSA participation room for $year",
                field.value,
                { field.value = it.filter(Char::isDigit).take(9) },
                dollars,
            )
        }
        if (carried.isNotEmpty()) {
            Text(
                "CRA My Account shows these. Tandem carries them forward each year from what goes in and out of the linked accounts.",
                color = tandemPalette.muted,
                fontSize = TandemTypography.label,
            )
        }
        TandemActionButton("Save", TandemActionButtonTone.PRIMARY, Modifier.fillMaxWidth()) {
            scope.launch {
                val planning = mobileSession.planning
                if (rrsp != null || carried.isEmpty()) {
                    planning.saveRetirementProfile(
                        state.selectedBudgetId,
                        (rrsp ?: RetirementProfile(Rrsp.id)).copy(rrspInputs = rrspDraft.inputs()),
                    )
                }
                carried.forEach { profile ->
                    val amount =
                        starts
                            .getValue(profile.planId)
                            .value
                            .toLongOrNull()
                            ?.times(100)
                    val start = amount?.let { RoomStart(year, it) } ?: profile.roomStart?.takeIf { it.year != year }
                    planning.saveRetirementProfile(state.selectedBudgetId, profile.copy(roomStart = start))
                }
                navigation.back()
            }
        }
        if (rrsp?.rrspInputs != null || carried.any { it.roomStart != null }) {
            SecondaryButton("Use the yearly limits instead", Modifier.fillMaxWidth()) {
                scope.launch {
                    rrsp?.let { mobileSession.planning.saveRetirementProfile(state.selectedBudgetId, it.copy(rrspInputs = null)) }
                    carried.forEach { mobileSession.planning.saveRetirementProfile(state.selectedBudgetId, it.copy(roomStart = null)) }
                    navigation.back()
                }
            }
        }
    }
}

/** Last year's figures from the notice of assessment, as typed. */
private class RrspDraft(
    inputs: RrspInputs?,
) {
    var income by mutableStateOf(inputs?.earnedIncomeMinor?.let { (it / 100).toString() }.orEmpty())
    var adjustment by mutableStateOf(
        inputs
            ?.pensionAdjustmentMinor
            ?.takeIf { it > 0 }
            ?.let { (it / 100).toString() }
            .orEmpty(),
    )
    var carried by mutableStateOf(
        inputs
            ?.carryForwardMinor
            ?.takeIf { it > 0 }
            ?.let { (it / 100).toString() }
            .orEmpty(),
    )

    fun inputs(): RrspInputs? =
        income.toLongOrNull()?.let { RrspInputs(it * 100, (adjustment.toLongOrNull() ?: 0) * 100, (carried.toLongOrNull() ?: 0) * 100) }
}

@Composable
private fun RrspFields(
    year: Int,
    draft: RrspDraft,
) {
    val palette = tandemPalette
    Text(
        "Your RRSP room is 18% of last year’s earned income up to the yearly limit, less any pension adjustment from a workplace plan, plus room you didn’t use before.",
        color = palette.muted,
        fontSize = TandemTypography.compactBody,
    )
    TextFieldCard("Earned income in ${year - 1}", draft.income, { draft.income = it.filter(Char::isDigit).take(9) }, dollars)
    TextFieldCard("Pension adjustment (T4 box 52)", draft.adjustment, { draft.adjustment = it.filter(Char::isDigit).take(9) }, dollars)
    TextFieldCard("Unused room carried forward", draft.carried, { draft.carried = it.filter(Char::isDigit).take(9) }, dollars)
    draft.inputs()?.let { entered ->
        Text(
            "RRSP room for $year: ${planMoney(
                Rrsp.personalRoom(year, entered.earnedIncomeMinor, entered.pensionAdjustmentMinor, entered.carryForwardMinor),
                Rrsp.currency,
            )}",
            color = palette.text,
            fontSize = TandemTypography.cardTitle,
            fontWeight = FontWeight.SemiBold,
        )
    }
    Text(
        "Your notice of assessment lists the same figure as your RRSP deduction limit. Group RRSP deposits count against it too.",
        color = palette.muted,
        fontSize = TandemTypography.label,
    )
}
