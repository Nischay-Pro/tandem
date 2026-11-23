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
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.inputs.FilterPills
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Settings

/** Vesting and the year you joined, folded away behind an Advanced gear since most matches vest right away. */
@Composable
internal fun AdvancedMatchCard(
    vesting: Vesting,
    onVestingChange: (Vesting) -> Unit,
    joinedYear: String,
    onJoinedYearChange: (String) -> Unit,
) {
    val palette = tandemPalette
    var open by remember { mutableStateOf(vesting != Vesting.IMMEDIATE) }
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
                    "Employer money can take a few years to become yours. Leave early and the unvested part goes back.",
                    color = palette.muted,
                    fontSize = TandemTypography.compactBody,
                )
                FilterPills(Vesting.presets.map { (label, value) -> value to label }, vesting, onVestingChange)
                if (vesting != Vesting.IMMEDIATE) {
                    TextFieldCard(
                        "Year you joined",
                        joinedYear,
                        { onJoinedYearChange(it.filter(Char::isDigit).take(4)) },
                        TextFieldOptions(placeholder = "2023", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)),
                    )
                }
            }
        }
    }
}
