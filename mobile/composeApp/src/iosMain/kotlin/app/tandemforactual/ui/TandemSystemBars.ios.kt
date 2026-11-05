@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

@Composable
@Suppress("ktlint:standard:function-naming")
internal actual fun TandemSystemBars(
    isDark: Boolean,
    background: Color,
) = Unit

@Composable
@Suppress("ktlint:standard:function-naming")
internal actual fun TandemBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
    onBackProgress: (progress: Float, direction: Float) -> Unit,
) = Unit

@Composable
@Suppress("ktlint:standard:function-naming")
internal actual fun SystemReduceMotionEnabled(): Boolean = UIAccessibilityIsReduceMotionEnabled()

@OptIn(ExperimentalForeignApi::class)
internal actual fun Modifier.tandemEdgeBackGesture(
    enabled: Boolean,
    onBack: () -> Unit,
    onBackProgress: (progress: Float, direction: Float) -> Unit,
): Modifier =
    pointerInput(enabled) {
        if (!enabled) return@pointerInput
        val edgeWidth = 28.dp.toPx()
        val touchSlop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.position.x > edgeWidth) return@awaitEachGesture

            var tracking = true
            var active = false
            var completedProgress = 0f
            while (tracking) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val horizontal = change.position.x - down.position.x
                val vertical = change.position.y - down.position.y
                if (!active && (kotlin.math.abs(horizontal) > touchSlop || kotlin.math.abs(vertical) > touchSlop)) {
                    if (horizontal <= 0f || kotlin.math.abs(vertical) > kotlin.math.abs(horizontal)) {
                        tracking = false
                        continue
                    }
                    active = true
                }
                if (active) {
                    val progress = (horizontal / size.width).coerceIn(0f, 1f)
                    completedProgress = progress
                    onBackProgress(progress, 1f)
                    change.consume()
                }
                if (!change.pressed) tracking = false
            }
            if (active && completedProgress >= 0.3f) {
                onBackProgress(1f, 1f)
                onBack()
            } else {
                onBackProgress(0f, 1f)
            }
        }
    }
