@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.templates

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion

/** The switch drawing; the row around it owns the toggle semantics. */
@Composable
internal fun TandemSwitchTrack(
    checked: Boolean,
    enabled: Boolean = true,
) {
    val palette = tandemPalette
    val thumbOffset by
        animateDpAsState(
            targetValue = if (checked) 18.dp else 0.dp,
            animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTROL_MILLIS),
            label = "toggle-thumb-position",
        )
    val trackColor by
        animateColorAsState(
            targetValue = if (checked && enabled) palette.accent else palette.raised,
            animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
            label = "toggle-track-color",
        )
    val thumbColor by
        animateColorAsState(
            targetValue = if (checked && enabled) palette.accentContent else palette.muted,
            animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
            label = "toggle-thumb-color",
        )
    Box(
        Modifier
            .width(42.dp)
            .height(24.dp)
            .clip(CircleShape)
            .background(if (enabled) trackColor else palette.raised)
            .padding(TandemSpacing.extraSmall),
    ) {
        Box(
            Modifier
                .offset(x = thumbOffset)
                .size(18.dp)
                .clip(CircleShape)
                .background(if (enabled) thumbColor else palette.muted),
        )
    }
}
