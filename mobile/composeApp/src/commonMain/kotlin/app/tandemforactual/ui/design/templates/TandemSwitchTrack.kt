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
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion

private val TRACK_WIDTH = 42.dp
private val TRACK_HEIGHT = 24.dp
private val THUMB_SIZE = 18.dp
private val THUMB_INSET = (TRACK_HEIGHT - THUMB_SIZE) / 2

/** The switch drawing; the row around it owns the toggle semantics. */
@Composable
internal fun TandemSwitchTrack(
    checked: Boolean,
    enabled: Boolean = true,
) {
    val palette = tandemPalette
    val thumbOffset by
        animateDpAsState(
            targetValue = if (checked) TRACK_WIDTH - THUMB_SIZE - THUMB_INSET * 2 else 0.dp,
            animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTROL_MILLIS),
            label = "toggle-thumb-position",
        )
    val trackColor by
        animateColorAsState(
            targetValue = if (checked && enabled) palette.accent else palette.border,
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
            .width(TRACK_WIDTH)
            .height(TRACK_HEIGHT)
            .clip(CircleShape)
            .background(if (enabled) trackColor else palette.border)
            .padding(THUMB_INSET),
    ) {
        Box(
            Modifier
                .offset(x = thumbOffset)
                .size(THUMB_SIZE)
                .clip(CircleShape)
                .background(if (enabled) thumbColor else palette.muted),
        )
    }
}
