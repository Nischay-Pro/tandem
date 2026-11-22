@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sync
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette

@Composable
internal fun ConflictCompareTable(
    comparisons: List<ConflictFieldComparison>,
    actualLabel: String,
) {
    val palette = tandemPalette
    SurfaceCard {
        Column(Modifier.fillMaxWidth().padding(vertical = TandemSpacing.small)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.small)) {
                listOf("Field", "This device", actualLabel).forEach { header ->
                    Text(
                        header.uppercase(),
                        Modifier.weight(1f),
                        color = palette.muted,
                        fontSize = TandemTypography.micro,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = TandemTypography.captionLetterSpacing,
                    )
                }
            }
            comparisons.forEach { comparison ->
                DividerLine()
                val differs = comparison.editedValue != comparison.actualValue
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(comparison.label, Modifier.weight(1f), color = palette.muted, fontSize = TandemTypography.compactBody)
                    Text(
                        comparison.editedValue,
                        Modifier.weight(1f),
                        color = if (differs) palette.accent else palette.text,
                        fontSize = TandemTypography.compactBody,
                        fontWeight = if (differs) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    Text(
                        comparison.actualValue,
                        Modifier.weight(1f),
                        color = if (differs) palette.warning else palette.text,
                        fontSize = TandemTypography.compactBody,
                        fontWeight = if (differs) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
