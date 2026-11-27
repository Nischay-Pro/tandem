@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.buttons

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.inputs.pointerHighlight
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X

@Composable
internal fun TandemIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClickLabel: String = contentDescription,
    modifier: Modifier = Modifier,
    sizeDp: Int = 44,
    iconSizeDp: Int = 22,
    enabled: Boolean = true,
    tint: Color? = null,
    containerColor: Color? = null,
    hoveredContainerColor: Color? = null,
    shape: Shape = CircleShape,
    borderColor: Color? = null,
    modalClose: Boolean = icon == Lucide.X && contentDescription == "Close",
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    val closeHoverColor = if (palette.surface.luminance() < 0.5f) palette.border else palette.surface
    val resolvedContainerColor = containerColor ?: palette.raised.takeIf { modalClose }
    val resolvedHoveredContainerColor = hoveredContainerColor ?: closeHoverColor.takeIf { modalClose }
    val resolvedBorderColor = borderColor ?: palette.border.copy(alpha = 0.56f).takeIf { modalClose }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val backgroundTarget =
        when {
            (hovered || pressed) && resolvedHoveredContainerColor != null -> resolvedHoveredContainerColor
            else -> resolvedContainerColor
        }
    val animatedContainerColor by
        animateColorAsState(
            targetValue = backgroundTarget ?: Color.Transparent,
            animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
            label = "icon-button-background",
        )
    Box(
        modifier =
            modifier
                .sizeIn(minWidth = MIN_TOUCH_TARGET, minHeight = MIN_TOUCH_TARGET)
                .semantics(mergeDescendants = true) { this.contentDescription = contentDescription }
                .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClickLabel = onClickLabel,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(sizeDp.dp)
                .clip(shape)
                .then(
                    if (resolvedContainerColor != null || resolvedHoveredContainerColor != null) {
                        Modifier.background(animatedContainerColor)
                    } else {
                        Modifier
                    },
                ).hoverable(interactionSource)
                .pointerHighlight(palette.text, shape, tandemReduceMotion, enabled && resolvedHoveredContainerColor == null)
                .then(resolvedBorderColor?.let { Modifier.border(1.dp, it, shape) } ?: Modifier)
                .indication(interactionSource, LocalIndication.current),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(iconSizeDp.dp), tint = tint ?: palette.text)
        }
    }
}
