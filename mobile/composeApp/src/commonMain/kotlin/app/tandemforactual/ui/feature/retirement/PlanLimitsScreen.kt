@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.retirement

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import app.tandemforactual.data.regions.global.retirement.RetirementPlans
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.inputs.FilterPills
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import kotlinx.coroutines.launch

/** Lets the person enter a year's limit themselves when the rules change before Tandem's published figures do. */
@Composable
internal fun PlanLimitsScreen(
    state: AppState,
    navigation: AppNavigation,
    mobileSession: TandemMobileSession,
) {
    val scope = rememberCoroutineScope()
    val palette = tandemPalette
    val profiles = mobileSession.planning.retirementProfiles.filter { RetirementPlans.byId(it.planId) != null }
    val thisYear = platformTodayIsoDate().take(4).toInt()
    var year by remember { mutableStateOf(thisYear) }
    val drafts = remember(year) { mutableStateMapOf<String, String>() }
    PageColumn(verticalSpacing = TandemSpacing.large) {
        Text(
            "Limits come from each country’s published figures. If a rule changes before Tandem is updated, enter the new limit here. Leave it blank to use the published one.",
            color = palette.muted,
            fontSize = TandemTypography.compactBody,
        )
        FilterPills(listOf(thisYear to thisYear.toString(), thisYear + 1 to (thisYear + 1).toString()), year, { year = it })
        profiles.forEach { profile ->
            val plan = RetirementPlans.byId(profile.planId)!!
            val lookup = plan.limitFor(year)
            val published = planMoney(plan.allowance(year, profile.age, profile.family), plan.currency)
            TextFieldCard(
                "${plan.displayName} limit for $year",
                drafts[plan.id] ?: profile.limitOverrides[year]?.let { (it / 100).toString() }.orEmpty(),
                { drafts[plan.id] = it.filter(Char::isDigit).take(9) },
                TextFieldOptions(
                    placeholder = if (lookup.published) "Published: $published" else "Not published yet; $published last known",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                ),
            )
        }
        TandemActionButton("Save limits", TandemActionButtonTone.PRIMARY, Modifier.fillMaxWidth()) {
            scope.launch {
                profiles.filter { it.planId in drafts }.forEach { profile ->
                    val entered = drafts[profile.planId]?.toLongOrNull()?.times(100)
                    val overrides = if (entered == null) profile.limitOverrides - year else profile.limitOverrides + (year to entered)
                    mobileSession.planning.saveRetirementProfile(state.selectedBudgetId, profile.copy(limitOverrides = overrides))
                }
                navigation.back()
            }
        }
    }
}
