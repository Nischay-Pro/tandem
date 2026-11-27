@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.buttons

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.feedback.TandemLoadingIndicator
import app.tandemforactual.ui.design.inputs.pointerHighlight
import app.tandemforactual.ui.design.sheets.RegisterFormSubmit
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion

@Composable
internal fun TandemActionButton(
    label: String,
    tone: TandemActionButtonTone,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    val shape = RoundedCornerShape(TandemShapes.medium)
    if (tone == TandemActionButtonTone.PRIMARY) RegisterFormSubmit(enabled && !loading, onClick)
    val containerColor =
        when (tone) {
            TandemActionButtonTone.PRIMARY -> if (enabled) palette.accent else palette.raised
            TandemActionButtonTone.NEUTRAL -> palette.surface
            TandemActionButtonTone.DESTRUCTIVE -> palette.negative.copy(alpha = 0.12f)
        }
    val contentColor =
        when {
            !enabled -> palette.muted
            tone == TandemActionButtonTone.PRIMARY -> palette.accentContent
            tone == TandemActionButtonTone.DESTRUCTIVE -> palette.negative
            else -> palette.text
        }
    val outlineColor =
        when (tone) {
            TandemActionButtonTone.PRIMARY -> containerColor
            TandemActionButtonTone.NEUTRAL -> palette.border
            TandemActionButtonTone.DESTRUCTIVE -> palette.negative.copy(alpha = 0.32f)
        }
    Row(
        modifier =
            modifier
                .heightIn(min = 50.dp)
                .clip(shape)
                .background(containerColor)
                .border(1.dp, outlineColor, shape)
                .pointerHighlight(palette.text, shape, tandemReduceMotion, enabled && !loading)
                .clickable(enabled = enabled && !loading, role = Role.Button, onClickLabel = label, onClick = onClick)
                .padding(horizontal = TandemSpacing.small),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            TandemLoadingIndicator(Modifier.size(16.dp), contentColor, 2.dp)
        } else if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp), tint = contentColor)
        }
        Text(
            label,
            modifier = if (loading || icon != null) Modifier.padding(start = TandemSpacing.extraSmall) else Modifier,
            color = contentColor,
            fontSize = TandemTypography.label,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
