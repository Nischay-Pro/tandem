@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sync
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion

@Composable
internal fun SyncStatusRing(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
    active: Boolean,
    caption: String?,
) {
    val palette = tandemPalette
    val ringColor by animateColorAsState(color, tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS), label = "sync-ring")
    val breath by
        rememberInfiniteTransition(label = "sync-ring-breath").animateFloat(
            initialValue = 1f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(tween(900, easing = TandemMotion.movementEasing), RepeatMode.Reverse),
            label = "sync-ring-phase",
        )
    val emphasis by animateFloatAsState(
        if (active && !tandemReduceMotion) breath else 1f,
        spring(stiffness = Spring.StiffnessMediumLow),
        label = "sync-ring-emphasis",
    )
    Column(
        Modifier.fillMaxWidth().padding(vertical = TandemSpacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
    ) {
        Box(
            Modifier
                .size(104.dp)
                .graphicsLayer {
                    scaleX = 0.94f + 0.06f * emphasis
                    scaleY = 0.94f + 0.06f * emphasis
                }.clip(CircleShape)
                .background(ringColor.copy(alpha = 0.08f + 0.06f * (1f - emphasis)))
                .border(2.dp, ringColor.copy(alpha = 0.35f + 0.4f * (1f - emphasis)), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp), tint = ringColor)
        }
        caption?.let { Text(it, color = palette.muted, fontSize = TandemTypography.compactBody) }
    }
}
