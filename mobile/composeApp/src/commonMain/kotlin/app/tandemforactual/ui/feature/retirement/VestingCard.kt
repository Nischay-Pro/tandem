@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.retirement

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.regions.us.retirement.k401.Vesting
import app.tandemforactual.data.regions.us.retirement.k401.VestingStep
import app.tandemforactual.ui.core.format.formatShortMonth
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.inputs.FilterPills
import app.tandemforactual.ui.design.inputs.PillGrid
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Trash2

/** One vesting step being edited: after how many years, and how much is yours by then. */
internal class StepDraft(
    years: String,
    percent: String,
) {
    var years by mutableStateOf(years)
    var percent by mutableStateOf(percent)

    fun toStep(): VestingStep? {
        val y = years.toIntOrNull() ?: return null
        val p = percent.toIntOrNull()?.coerceIn(0, 100) ?: return null
        return VestingStep(y, p * 100)
    }

    companion object {
        fun of(step: VestingStep) = StepDraft(step.years.toString(), (step.basisPoints / 100).toString())
    }
}

@Composable
internal fun rememberStepDrafts(vesting: Vesting?): MutableList<StepDraft> =
    remember { mutableStateListOf<StepDraft>().apply { addAll(vesting?.steps.orEmpty().map(StepDraft::of)) } }

private val digits = TextFieldOptions(keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))

/** When you joined, as typed: service for vesting counts from this month. */
internal class JoinedDate(
    val year: String,
    val onYear: (String) -> Unit,
    val month: Int?,
    val onMonth: (Int?) -> Unit,
)

/** Vesting and when you joined, folded away behind an Advanced gear since most matches vest right away. */
@Composable
internal fun AdvancedMatchCard(
    steps: MutableList<StepDraft>,
    joined: JoinedDate,
) {
    val palette = tandemPalette
    var open by remember { mutableStateOf(steps.isNotEmpty()) }
    SurfaceCard {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button) { open = !open }.padding(TandemSpacing.large),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
        ) {
            Icon(Lucide.Settings, null, Modifier.size(20.dp), tint = palette.muted)
            Column(Modifier.weight(1f)) {
                Text("Advanced", color = palette.text, fontSize = TandemTypography.action, fontWeight = FontWeight.Medium)
                Text("Vesting schedule", color = palette.muted, fontSize = TandemTypography.compactBody)
            }
            Icon(if (open) Lucide.ChevronUp else Lucide.ChevronDown, null, Modifier.size(20.dp), tint = palette.muted)
        }
        AnimatedVisibility(open) {
            Column(
                Modifier.padding(start = TandemSpacing.large, end = TandemSpacing.large, bottom = TandemSpacing.large),
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
            ) {
                Text(
                    "Employer money can take a few years to become yours. Start from a common schedule or enter your employer’s own.",
                    color = palette.muted,
                    fontSize = TandemTypography.compactBody,
                )
                val current = Vesting(steps.mapNotNull { it.toStep() })
                FilterPills(
                    Vesting.presets.mapIndexed { index, (label, _) -> index to label },
                    Vesting.presets.indexOfFirst { it.second == current },
                    { index ->
                        steps.clear()
                        steps.addAll(
                            Vesting.presets[index]
                                .second.steps
                                .map(StepDraft::of),
                        )
                    },
                )
                VestingSteps(steps)
                if (steps.isNotEmpty()) {
                    TextFieldCard("Year you joined", joined.year, { joined.onYear(it.filter(Char::isDigit).take(4)) }, digits)
                    Text("Month you joined", color = palette.muted, fontSize = TandemTypography.label)
                    PillGrid(
                        (1..12).map<Int, Pair<Int?, String>> {
                            it to formatShortMonth(2000, it).substringBefore(' ')
                        },
                        joined.month,
                        joined.onMonth,
                        columns = 6,
                    )
                }
            }
        }
    }
}

/** Each step as years of service and the share that's yours, with a button to add another. */
@Composable
private fun VestingSteps(steps: MutableList<StepDraft>) {
    steps.forEachIndexed { index, step ->
        Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                TextFieldCard("After (years)", step.years, { step.years = it.filter(Char::isDigit).take(2) }, digits)
            }
            Column(Modifier.weight(1f)) {
                TextFieldCard("Yours (%)", step.percent, { step.percent = it.filter(Char::isDigit).take(3) }, digits)
            }
            TandemIconButton(Lucide.Trash2, "Remove step") { steps.removeAt(index) }
        }
    }
    TandemActionButton("Add a step", TandemActionButtonTone.NEUTRAL, Modifier.fillMaxWidth(), icon = Lucide.Plus) {
        val last = steps.lastOrNull()?.toStep()
        steps.add(StepDraft(((last?.years ?: 0) + 1).toString(), "100"))
    }
}
