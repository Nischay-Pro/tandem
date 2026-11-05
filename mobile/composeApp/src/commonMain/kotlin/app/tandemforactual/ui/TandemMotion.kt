package app.tandemforactual.ui

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween

/** Shared timing for app transitions so navigation and state changes feel like one system. */
internal object TandemMotion {
    const val ROUTE_MILLIS = 260
    const val TAB_MILLIS = 180
    const val COLOR_MILLIS = 200
    const val CONTROL_MILLIS = 180
    const val CONTENT_SIZE_MILLIS = 200
    const val REVEAL_MILLIS = 200
    const val STATUS_CHANGE_MILLIS = 200
    const val FADE_THROUGH_DELAY_MILLIS = 50
    const val BACK_CANCEL_MILLIS = 200

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
