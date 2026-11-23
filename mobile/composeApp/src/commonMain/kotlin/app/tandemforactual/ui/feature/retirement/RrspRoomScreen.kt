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
import app.tandemforactual.data.planning.RrspInputs
import app.tandemforactual.data.regions.ca.retirement.rrsp.Rrsp
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

/** Works out this year's RRSP room from last year's earned income, pension adjustment and unused room. */
@Composable
internal fun RrspRoomScreen(
    state: AppState,
    navigation: AppNavigation,
    mobileSession: TandemMobileSession,
) {
    val scope = rememberCoroutineScope()
    val palette = tandemPalette
    val profile = mobileSession.planning.retirementProfiles.firstOrNull { it.planId == Rrsp.id } ?: RetirementProfile(Rrsp.id)
    val inputs = profile.rrspInputs
    var income by remember { mutableStateOf(inputs?.earnedIncomeMinor?.let { (it / 100).toString() }.orEmpty()) }
    var adjustment by remember {
        mutableStateOf(
            inputs
                ?.pensionAdjustmentMinor
                ?.takeIf { it > 0 }
                ?.let { (it / 100).toString() }
                .orEmpty(),
        )
    }
    var carried by remember {
        mutableStateOf(
            inputs
                ?.carryForwardMinor
                ?.takeIf { it > 0 }
                ?.let { (it / 100).toString() }
                .orEmpty(),
        )
    }
    val year = platformTodayIsoDate().take(4).toInt()
    val entered =
        income.toLongOrNull()?.let {
            RrspInputs(
                it * 100,
                (adjustment.toLongOrNull() ?: 0) * 100,
                (carried.toLongOrNull() ?: 0) * 100,
            )
        }
    PageColumn(verticalSpacing = TandemSpacing.large) {
        Text(
            "Your room is 18% of last year’s earned income up to the yearly limit, less any pension adjustment from a workplace plan, plus room you didn’t use before.",
            color = palette.muted,
            fontSize = TandemTypography.compactBody,
        )
        TextFieldCard("Earned income in ${year - 1}", income, { income = it.filter(Char::isDigit).take(9) }, dollars)
        TextFieldCard("Pension adjustment (T4 box 52)", adjustment, { adjustment = it.filter(Char::isDigit).take(9) }, dollars)
        TextFieldCard("Unused room carried forward", carried, { carried = it.filter(Char::isDigit).take(9) }, dollars)
        if (entered != null) {
            Text(
                "Room for $year: ${planMoney(
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
        TandemActionButton("Save", TandemActionButtonTone.PRIMARY, Modifier.fillMaxWidth()) {
            scope.launch {
                mobileSession.planning.saveRetirementProfile(state.selectedBudgetId, profile.copy(rrspInputs = entered))
                navigation.back()
            }
        }
        if (inputs != null) {
            SecondaryButton("Use the yearly limit instead", Modifier.fillMaxWidth()) {
                scope.launch {
                    mobileSession.planning.saveRetirementProfile(state.selectedBudgetId, profile.copy(rrspInputs = null))
                    navigation.back()
                }
            }
        }
    }
}
