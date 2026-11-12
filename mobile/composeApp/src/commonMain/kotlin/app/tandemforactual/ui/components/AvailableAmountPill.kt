@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import app.tandemforactual.ui.TandemShapes
import app.tandemforactual.ui.TandemSpacing
import app.tandemforactual.ui.TandemTypography
import app.tandemforactual.ui.tandemPalette

@Composable
internal fun AvailableAmountPill(
    amount: String,
    isOverspent: Boolean,
) {
    val palette = tandemPalette
    val label = if (isOverspent) "Overspent, $amount available" else "$amount available"
    Row(
        Modifier
            .clearAndSetSemantics { contentDescription = label }
            .clip(RoundedCornerShape(TandemShapes.pill))
            .background(palette.raised)
            .padding(horizontal = TandemSpacing.medium, vertical = TandemSpacing.extraSmall),
    ) {
        Text(
            amount,
            color = if (isOverspent) palette.negative else palette.positive,
            fontSize = TandemTypography.label,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
