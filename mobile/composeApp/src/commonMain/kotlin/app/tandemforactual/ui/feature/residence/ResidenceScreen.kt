@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.residence

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.tandemforactual.data.planning.JurisdictionPeriod
import app.tandemforactual.data.planning.JurisdictionTimeline
import app.tandemforactual.data.planning.PeriodKind
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.Badge
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plane
import com.composables.icons.lucide.Plus

/**
 * Where you've lived, worked and been a tax resident, each with its own dates. Plans keep the country they belong to, so
 * moving never rewrites their history.
 */
@Composable
internal fun ResidenceScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
) {
    val periods = mobileSession.planning.jurisdictionPeriods
    PageColumn(verticalSpacing = TandemSpacing.large) {
        if (periods.isEmpty()) {
            SurfaceCard {
                EmptyState(
                    "Add where you’ve lived",
                    "Keep where you lived, worked and paid tax, with dates. Retirement plans from each country keep their history " +
                        "when you move, and Tandem can walk you through a move.",
                    Modifier.fillMaxWidth().padding(TandemSpacing.large),
                )
            }
        }
        JurisdictionTimeline.overlaps(periods).forEach { (first, second) -> OverlapMessage(first, second) }
        PeriodKind.entries.forEach { kind ->
            val ofKind = periods.filter { it.kind == kind }.sortedByDescending { it.from }
            if (ofKind.isNotEmpty()) {
                SectionCard(sectionTitle(kind), sectionDetail(kind)) {
                    ofKind.forEachIndexed { index, period ->
                        if (index > 0) DividerLine()
                        ListRow(
                            title = period.region.displayName,
                            subtitle = spanText(period),
                            leading = RowLeading.None,
                            trailingContent = if (period.isCurrent) ({ Badge("Now", tandemPalette.positive) }) else null,
                            onClick = {
                                state.selectedJurisdictionPeriodId = period.id
                                navigation.open(AppRoute.RESIDENCE_PERIOD)
                            },
                        )
                    }
                }
            }
        }
        TandemActionButton("I’m moving", TandemActionButtonTone.PRIMARY, Modifier.fillMaxWidth(), icon = Lucide.Plane) {
            navigation.open(AppRoute.RELOCATION)
        }
        TandemActionButton("Add a period", TandemActionButtonTone.NEUTRAL, Modifier.fillMaxWidth(), icon = Lucide.Plus) {
            state.selectedJurisdictionPeriodId = ""
            navigation.open(AppRoute.RESIDENCE_PERIOD)
        }
    }
}

@Composable
private fun OverlapMessage(
    first: JurisdictionPeriod,
    second: JurisdictionPeriod,
) {
    val countries = "${first.region.displayName} and ${second.region.displayName}"
    InlineMessage(
        when (first.kind) {
            PeriodKind.TAX_RESIDENCE -> {
                "Tax resident of $countries in the same months. Cross-border tax is worth checking with a professional."
            }

            PeriodKind.RESIDENCE -> {
                "Lived in $countries in the same months. Check the dates."
            }

            PeriodKind.EMPLOYMENT -> {
                "Worked in $countries in the same months."
            }
        },
        if (first.kind == PeriodKind.EMPLOYMENT) InlineMessageKind.INFO else InlineMessageKind.WARNING,
    )
}

private fun sectionTitle(kind: PeriodKind) =
    when (kind) {
        PeriodKind.RESIDENCE -> "Where you’ve lived"
        PeriodKind.EMPLOYMENT -> "Where you’ve worked"
        PeriodKind.TAX_RESIDENCE -> "Tax residence"
    }

private fun sectionDetail(kind: PeriodKind) =
    when (kind) {
        PeriodKind.RESIDENCE -> null
        PeriodKind.EMPLOYMENT -> "Decides which employer plans you could join"
        PeriodKind.TAX_RESIDENCE -> "What you’ve claimed; Tandem never works this out for you"
    }
