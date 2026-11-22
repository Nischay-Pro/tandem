@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette

@Composable
internal fun AmountPadKey(
    label: String,
    modifier: Modifier = Modifier,
    style: AmountPadKeyStyle = AmountPadKeyStyle.NUMBER,
    accessibilityLabel: String = label,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    val background =
        when (style) {
            AmountPadKeyStyle.NUMBER -> Color.Transparent
            AmountPadKeyStyle.DONE -> palette.accent
            else -> palette.raised
        }
    val color =
        when (style) {
            AmountPadKeyStyle.DONE -> palette.accentContent
            AmountPadKeyStyle.NUMBER -> palette.text
            else -> palette.muted
        }
    val shape =
        when (style) {
            AmountPadKeyStyle.NUMBER -> RoundedCornerShape(TandemShapes.roomy)
            AmountPadKeyStyle.OPERATOR -> CircleShape
            else -> RoundedCornerShape(TandemShapes.capsule)
        }
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .padding(TandemSpacing.xxSmall)
                .clip(shape)
                .background(background)
                .semantics(mergeDescendants = true) { contentDescription = accessibilityLabel }
                .clickable(role = Role.Button, onClickLabel = accessibilityLabel, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = color,
            fontSize = if (style == AmountPadKeyStyle.NUMBER) TandemTypography.keypadNumber else TandemTypography.keypadOperator,
            fontWeight = if (style == AmountPadKeyStyle.DONE) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}
