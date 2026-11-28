@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.money

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette

@Composable
internal fun AvailableAmountPill(
    amount: String,
    isOverspent: Boolean,
    privacyModeEnabled: Boolean = false,
    fractionDigits: Int = 2,
    statusColors: Boolean = true,
) {
    val palette = tandemPalette
    val amountSemantics =
        if (privacyModeEnabled) {
            Modifier
        } else {
            val label = if (isOverspent) "Overspent, $amount available" else "$amount available"
            Modifier.clearAndSetSemantics { contentDescription = label }
        }
    Row(
        Modifier
            .then(amountSemantics)
            .clip(RoundedCornerShape(TandemShapes.pill))
            .background(palette.raised)
            .padding(horizontal = TandemSpacing.medium, vertical = TandemSpacing.extraSmall),
    ) {
        PrivacyAmountText(
            amount,
            privacyModeEnabled = privacyModeEnabled,
            color =
                when {
                    !statusColors -> palette.text
                    isOverspent -> palette.negative
                    else -> palette.positive
                },
            fontSize = TandemTypography.label,
            fontWeight = FontWeight.SemiBold,
            fractionDigits = fractionDigits,
        )
    }
}
