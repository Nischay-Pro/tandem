package app.tandemforactual.ui

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween

/** Shared timing for app transitions so navigation and state changes feel like one system. */
internal object TandemMotion {
    const val ROUTE_MILLIS = 320
    const val TAB_MILLIS = 280
    const val COLOR_MILLIS = 240
    const val CONTROL_MILLIS = 220
    const val CONTENT_SIZE_MILLIS = 280
    const val REVEAL_MILLIS = 280
    const val STATUS_ENTER_MILLIS = 220
    const val STATUS_EXIT_MILLIS = 120
    const val STATUS_CHANGE_MILLIS = 180
    const val FADE_THROUGH_DELAY_MILLIS = STATUS_EXIT_MILLIS

    val enterEasing = LinearOutSlowInEasing
    val exitEasing = FastOutLinearInEasing
    val movementEasing = FastOutSlowInEasing
}

internal fun <T> tandemMotionSpec(
    reduceMotion: Boolean,
    durationMillis: Int,
): FiniteAnimationSpec<T> =
    if (reduceMotion) {
        snap()
    } else {
        tween(durationMillis, easing = TandemMotion.movementEasing)
    }
