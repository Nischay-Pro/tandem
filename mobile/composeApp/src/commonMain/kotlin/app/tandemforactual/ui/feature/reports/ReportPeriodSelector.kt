@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.reports
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.format.reportMonthLabel
import app.tandemforactual.ui.core.state.ReportPeriodMode
import app.tandemforactual.ui.core.state.ReportRangePreset
import app.tandemforactual.ui.design.buttons.PeriodStepper
import app.tandemforactual.ui.design.buttons.SegmentedSelector
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Lucide

@Composable
internal fun ReportPeriodSelector(
    monthId: String,
    mode: ReportPeriodMode,
    rangePreset: ReportRangePreset,
    availableMonthIds: List<String>,
    onMonthChange: (String) -> Unit,
    onModeChange: (ReportPeriodMode) -> Unit,
    onRangePresetChange: (ReportRangePreset) -> Unit,
) {
    SurfaceCard {
        Column(
            Modifier.padding(TandemSpacing.large),
            verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
        ) {
            Text("Period", color = tandemPalette.muted, fontSize = TandemTypography.caption, fontWeight = FontWeight.SemiBold)
            SegmentedSelector(
                listOf("Month" to (mode == ReportPeriodMode.MONTH), "Range" to (mode == ReportPeriodMode.RANGE)),
            ) { index -> onModeChange(if (index == 0) ReportPeriodMode.MONTH else ReportPeriodMode.RANGE) }
            if (mode == ReportPeriodMode.RANGE) {
                ReportRangePresetDropdown(rangePreset, onRangePresetChange)
                val rangeWindow = createReportWindow(monthId, mode, rangePreset, platformTodayIsoDate(), availableMonthIds)
                when (rangePreset) {
                    ReportRangePreset.LAST_3_MONTHS,
                    ReportRangePreset.LAST_6_MONTHS,
                    ReportRangePreset.LAST_12_MONTHS,
                    ReportRangePreset.LAST_3_YEARS,
                    -> {
                        Text(
                            "End month",
                            color = tandemPalette.muted,
                            fontSize = TandemTypography.caption,
                            fontWeight = FontWeight.SemiBold,
                        )
                        PeriodStepper(
                            title = reportMonthLabel(monthId),
                            previousLabel = "Previous end month",
                            nextLabel = "Next end month",
                            onPrevious = { shiftReportMonthId(monthId, -1)?.let(onMonthChange) },
                            onNext = { shiftReportMonthId(monthId, 1)?.let(onMonthChange) },
                        )
                        rangeWindow?.let { window ->
                            Text(
                                reportMonthRangeLabel(window.startMonthId, window.endMonthId),
                                modifier = Modifier.fillMaxWidth(),
                                color = tandemPalette.muted,
                                fontSize = TandemTypography.caption,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    ReportRangePreset.YEAR_TO_DATE,
                    ReportRangePreset.LAST_YEAR,
                    -> {
                        rangeWindow?.let { window ->
                            Text(
                                reportMonthRangeLabel(window.startMonthId, window.endMonthId),
                                modifier = Modifier.fillMaxWidth(),
                                color = tandemPalette.muted,
                                fontSize = TandemTypography.caption,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    ReportRangePreset.ALL_TIME -> Unit
                }
            } else {
                PeriodStepper(
                    title = reportMonthLabel(monthId),
                    previousLabel = "Previous month",
                    nextLabel = "Next month",
                    onPrevious = { shiftReportMonthId(monthId, -1)?.let(onMonthChange) },
                    onNext = { shiftReportMonthId(monthId, 1)?.let(onMonthChange) },
                )
            }
        }
    }
}

@Composable
internal fun ReportRangePresetDropdown(
    selected: ReportRangePreset,
    onSelect: (ReportRangePreset) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(TandemShapes.control))
                .background(tandemPalette.raised)
                .clickable(role = Role.Button, onClickLabel = "Choose report range", onClick = { expanded = true })
                .padding(horizontal = TandemSpacing.medium, vertical = TandemSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(selected.label, modifier = Modifier.weight(1f), color = tandemPalette.text, fontSize = TandemTypography.body)
            Icon(Lucide.ChevronDown, contentDescription = null, modifier = Modifier.size(20.dp), tint = tandemPalette.muted)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ReportRangePreset.entries.forEach { preset ->
                DropdownMenuItem(onClick = {
                    expanded = false
                    onSelect(preset)
                }) {
                    Text(preset.label, color = tandemPalette.text, fontSize = TandemTypography.compactBody)
                }
            }
        }
    }
}
