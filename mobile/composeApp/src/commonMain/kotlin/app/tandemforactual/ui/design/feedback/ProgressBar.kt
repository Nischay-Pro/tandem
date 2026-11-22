@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.feedback

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion

/** A thin rounded bar filled to [fraction], animating when the value changes. */
@Composable
internal fun ProgressBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    marker: Float? = null,
) {
    val palette = tandemPalette
    val shown by animateFloatAsState(
        fraction.coerceIn(0f, 1f),
        tandemMotionSpec(tandemReduceMotion, TandemMotion.REVEAL_MILLIS),
        label = "insight-progress",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(palette.raised),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(shown)
                .clip(CircleShape)
                .background(color),
        )
        marker?.let { point ->
            Box(
                Modifier
                    .fillMaxWidth(point.coerceIn(0.01f, 1f))
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(Modifier.size(width = 2.dp, height = 6.dp).background(palette.text))
            }
        }
    }
}
