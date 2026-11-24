@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.residence

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.tandemforactual.data.planning.JurisdictionTimeline
import app.tandemforactual.data.planning.PeriodKind
import app.tandemforactual.data.planning.RelocationStep
import app.tandemforactual.data.regions.Region
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.inputs.FilterPills
import app.tandemforactual.ui.design.inputs.PillGrid
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import kotlin.random.Random
import kotlinx.coroutines.launch

/**
 * Records a move to another country and lists what to look at: plans you hold there and here, plans you could add, the
 * split tax year and the new currency. Old plans and their history stay as they are.
 */
@Composable
internal fun RelocationScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
) {
    val planning = mobileSession.planning
    val current = JurisdictionTimeline.regionsIn(planning.jurisdictionPeriods, PeriodKind.RESIDENCE, platformTodayIsoDate().take(7))
    var movedFrom by remember { mutableStateOf<Region?>(null) }
    var saved by remember { mutableStateOf(false) }
    val from = if (saved) movedFrom else current.firstOrNull()
    val thisMonth = platformTodayIsoDate().take(7)
    var to by rememberSaveable { mutableStateOf(countries.first { it != from }.name) }
    var year by rememberSaveable { mutableStateOf(thisMonth.take(4)) }
    var month by rememberSaveable { mutableStateOf(thisMonth.drop(5).toInt()) }
    var newJob by rememberSaveable { mutableStateOf(true) }
    val destination = Region.valueOf(to)
    val moveMonth = monthId(year, month)
    val steps = JurisdictionTimeline.review(from, destination, planning.retirementProfiles.map { it.planId }.toSet())
    val scope = rememberCoroutineScope()
    PageColumn(verticalSpacing = TandemSpacing.large) {
        if (saved) {
            InlineMessage("Move saved. ${destination.displayName} is now where you live.", InlineMessageKind.SUCCESS)
        }
        SectionCard("The move", from?.let { "From ${it.displayName}" } ?: "Add where you live now to compare plans") {
            Column(Modifier.padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                Text("Moving to", color = tandemPalette.muted, fontSize = TandemTypography.label)
                PillGrid(countries.filter { it != from }.map { it.name to it.displayName }, to, { to = it }, columns = 2)
                MonthField("Starting", year, month, { year = it }, { month = it })
                Text("New job there too?", color = tandemPalette.muted, fontSize = TandemTypography.label)
                FilterPills(listOf(true to "Yes", false to "No, same employer"), newJob, { newJob = it })
            }
        }
        RelocationChecklist(steps, destination) { step ->
            when (step) {
                is RelocationStep.ConsiderPlan -> navigation.open(AppRoute.RETIREMENT_SETUP)
                is RelocationStep.NewCurrency -> navigation.open(AppRoute.EXCHANGE_RATES)
                else -> Unit
            }
        }
        TandemActionButton(
            if (saved) "Saved" else "Save the move",
            TandemActionButtonTone.PRIMARY,
            Modifier.fillMaxWidth(),
            enabled = moveMonth != null && !saved && !state.readOnlyMode,
        ) {
            movedFrom = from
            scope.launch {
                val changes =
                    JurisdictionTimeline.move(planning.jurisdictionPeriods, destination, moveMonth!!, newJob) {
                        "period-${Random.nextLong().toULong().toString(36)}"
                    }
                planning.saveJurisdictionPeriods(state.selectedBudgetId, changes)
                planning.addRegion(state.selectedBudgetId, destination.name)
                saved = true
            }
        }
        Text(
            "Tax residence isn’t changed for you; update it when you know where you’ll be taxed.",
            color = tandemPalette.muted,
            fontSize = TandemTypography.label,
        )
    }
}
