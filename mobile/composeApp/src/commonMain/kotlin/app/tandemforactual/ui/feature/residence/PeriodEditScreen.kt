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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.tandemforactual.data.planning.JurisdictionPeriod
import app.tandemforactual.data.planning.PeriodKind
import app.tandemforactual.data.regions.Region
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.inputs.FilterPills
import app.tandemforactual.ui.design.inputs.PillGrid
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2
import kotlin.random.Random
import kotlinx.coroutines.launch

/** Countries a period can be in; everywhere isn't a place to live. */
internal val countries = Region.entries.filter { it != Region.GLOBAL }

/** Adds or changes one period of living, working or tax residence in a country. */
@Composable
internal fun PeriodEditScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
) {
    val existing = mobileSession.planning.jurisdictionPeriods.firstOrNull { it.id == state.selectedJurisdictionPeriodId }
    val thisMonth = platformTodayIsoDate().take(7)
    var kind by remember(existing) { mutableStateOf(existing?.kind ?: PeriodKind.RESIDENCE) }
    var region by remember(existing) { mutableStateOf(existing?.region ?: Region.US) }
    var fromYear by remember(existing) { mutableStateOf((existing?.from ?: thisMonth).take(4)) }
    var fromMonth by remember(existing) { mutableStateOf((existing?.from ?: thisMonth).drop(5).toInt()) }
    var ongoing by remember(existing) { mutableStateOf(existing?.through == null) }
    var toYear by remember(existing) { mutableStateOf((existing?.through ?: thisMonth).take(4)) }
    var toMonth by remember(existing) { mutableStateOf((existing?.through ?: thisMonth).drop(5).toInt()) }
    val from = monthId(fromYear, fromMonth)
    val through = if (ongoing) null else monthId(toYear, toMonth)
    val problem =
        when {
            from == null -> "Enter the year it started."
            !ongoing && through == null -> "Enter the year it ended."
            through != null && through < from -> "It can’t end before it starts."
            else -> null
        }
    val scope = rememberCoroutineScope()
    PageColumn(verticalSpacing = TandemSpacing.large) {
        SectionCard("Period") {
            Column(Modifier.padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                FilterPills(PeriodKind.entries.map { it to it.label }, kind, { kind = it })
                PillGrid(countries.map { it to it.displayName }, region, { region = it }, columns = 2)
            }
        }
        SectionCard("Dates") {
            Column(Modifier.padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                MonthField("From", fromYear, fromMonth, { fromYear = it }, { fromMonth = it })
                FilterPills(listOf(true to "Still there", false to "Ended"), ongoing, { ongoing = it })
                if (!ongoing) MonthField("Through", toYear, toMonth, { toYear = it }, { toMonth = it })
                problem?.let { Text(it, color = tandemPalette.warning, fontSize = TandemTypography.label) }
            }
        }
        TandemActionButton(
            "Save",
            TandemActionButtonTone.PRIMARY,
            Modifier.fillMaxWidth(),
            enabled = problem == null && from != null && !state.readOnlyMode,
        ) {
            val id = existing?.id ?: "period-${Random.nextLong().toULong().toString(36)}"
            scope.launch {
                mobileSession.planning.saveJurisdictionPeriods(
                    state.selectedBudgetId,
                    listOf(JurisdictionPeriod(id, kind, region, from!!, through)),
                )
                mobileSession.planning.addRegion(state.selectedBudgetId, region.name)
                navigation.back()
            }
        }
        if (existing != null) {
            TandemActionButton("Delete", TandemActionButtonTone.DESTRUCTIVE, Modifier.fillMaxWidth(), icon = Lucide.Trash2) {
                scope.launch {
                    mobileSession.planning.deleteJurisdictionPeriod(state.selectedBudgetId, existing.id)
                    navigation.back()
                }
            }
        }
    }
}
