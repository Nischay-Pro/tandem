@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Covers the app while a budget opens, so its data can settle before it is shown. */
@Composable
internal fun BudgetSwitchSplash(budgetName: String?) {
    AnimatedVisibility(
        visible = budgetName != null,
        enter = fadeIn(tween(TandemMotion.CONTROL_MILLIS)),
        exit = fadeOut(tween(TandemMotion.ROUTE_MILLIS)),
    ) {
        val palette = tandemPalette
        val breath = rememberInfiniteTransition(label = "budget-switch-breath")
        val phase by breath.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1_200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "budget-switch-phase",
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(palette.background)
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                TandemBrandMark(
                    Modifier.size(88.dp).graphicsLayer {
                        val scale = 0.92f + 0.08f * phase
                        scaleX = scale
                        scaleY = scale
                        alpha = 0.7f + 0.3f * phase
                    },
                    contentDescription = "Opening budget",
                )
                Text(
                    budgetName.orEmpty(),
                    Modifier.padding(horizontal = 32.dp),
                    color = palette.muted,
                    fontSize = TandemTypography.body,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
