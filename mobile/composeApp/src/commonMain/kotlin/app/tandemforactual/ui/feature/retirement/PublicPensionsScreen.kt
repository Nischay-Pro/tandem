@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.retirement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import app.tandemforactual.data.planning.PensionEstimate
import app.tandemforactual.data.regions.Region
import app.tandemforactual.data.regions.global.pension.PublicPension
import app.tandemforactual.data.regions.global.pension.PublicPensions
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.inputs.FilterPills
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Landmark
import com.composables.icons.lucide.Lucide
import kotlinx.coroutines.launch

/** Monthly estimates for the public pensions where you've lived, as income to expect rather than money you hold. */
@Composable
internal fun PublicPensionsScreen(
    state: AppState,
    navigation: AppNavigation,
    mobileSession: TandemMobileSession,
) {
    val scope = rememberCoroutineScope()
    val palette = tandemPalette
    val saved = mobileSession.planning.pensionEstimates.associateBy { it.pensionId }
    val regions =
        mobileSession.planning.settings.regions
            .mapNotNull { code -> Region.entries.firstOrNull { it.name == code } }
    val pensions = PublicPensions.forRegions(regions).ifEmpty { PublicPensions.all }
    val amounts =
        remember {
            mutableStateMapOf<String, String>().apply {
                saved.forEach { (id, e) ->
                    put(id, (e.monthlyMinor / 100).toString())
                }
            }
        }
    val ages = remember { mutableStateMapOf<String, Int>().apply { saved.forEach { (id, e) -> put(id, e.startAge) } } }
    PageColumn(verticalSpacing = TandemSpacing.large) {
        Text(
            "Enter the monthly amount from your latest statement. These are income to expect later, so they aren’t added to your savings.",
            color = palette.muted,
            fontSize = TandemTypography.compactBody,
        )
        pensions.forEach { pension ->
            PensionCard(pension, amounts[pension.id].orEmpty(), { amounts[pension.id] = it }, ages[pension.id] ?: pension.standardAge) {
                ages[pension.id] = it
            }
        }
        TandemActionButton("Save estimates", TandemActionButtonTone.PRIMARY, Modifier.fillMaxWidth()) {
            scope.launch {
                pensions.forEach { pension ->
                    val monthly = amounts[pension.id]?.toLongOrNull()?.takeIf { it > 0 }?.times(100)
                    val estimate = monthly?.let { PensionEstimate(pension.id, it, ages[pension.id] ?: pension.standardAge) }
                    if (estimate !=
                        saved[pension.id]
                    ) {
                        mobileSession.planning.savePensionEstimate(state.selectedBudgetId, pension.id, estimate)
                    }
                }
                navigation.back()
            }
        }
    }
}

@Composable
private fun PensionCard(
    pension: PublicPension,
    amount: String,
    onAmountChange: (String) -> Unit,
    age: Int,
    onAgeChange: (Int) -> Unit,
) {
    val palette = tandemPalette
    SectionCard(pension.displayName, pension.description) {
        Column(Modifier.padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
            if (pension.startAges.size > 1) FilterPills(pension.startAges.map { it to "Start at $it" }, age, onAgeChange)
            TextFieldCard(
                "Monthly estimate at $age",
                amount,
                { onAmountChange(it.filter(Char::isDigit).take(7)) },
                TextFieldOptions(placeholder = "Optional", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)),
            )
            Text("Find it in ${pension.source}.", color = palette.muted, fontSize = TandemTypography.label)
        }
    }
}

/** The pensions you've entered on the retirement page, or a prompt to add them. */
@Composable
internal fun PublicPensionsSummary(
    mobileSession: TandemMobileSession,
    onOpen: () -> Unit,
) {
    val estimates = mobileSession.planning.pensionEstimates.mapNotNull { e -> PublicPensions.byId(e.pensionId)?.let { it to e } }
    if (estimates.isEmpty()) {
        SurfaceCard {
            ListRow(
                "Public pensions",
                "Add your Social Security, CPP, OAS or EPS estimate",
                leading = RowLeading.Icon(Lucide.Landmark),
                onClick = onOpen,
            )
        }
        return
    }
    SectionCard("Public pensions", "Income to expect later") {
        estimates.forEachIndexed { index, (pension, estimate) ->
            if (index > 0) DividerLine()
            ListRow(
                pension.displayName,
                "From age ${estimate.startAge}",
                "${planMoney(estimate.monthlyMinor, pension.currency)} a month",
                leading = RowLeading.Icon(Lucide.Landmark),
                onClick = onOpen,
            )
        }
    }
}
