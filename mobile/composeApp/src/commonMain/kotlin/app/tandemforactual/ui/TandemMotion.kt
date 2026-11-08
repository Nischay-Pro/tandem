package app.tandemforactual.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically

/** Shared timing for app transitions so navigation and state changes feel like one system. */
internal object TandemMotion {
    const val ROUTE_MILLIS = 220
    const val SHEET_MILLIS = 360
    const val COLOR_MILLIS = 200
    const val CONTROL_MILLIS = 180
    const val CONTENT_SIZE_MILLIS = 200
    const val REVEAL_MILLIS = 200
    const val STATUS_CHANGE_MILLIS = 160
    const val CROSSFADE_MILLIS = 100
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

internal fun tandemFadeEnter(
    reduceMotion: Boolean,
    durationMillis: Int = TandemMotion.REVEAL_MILLIS,
): EnterTransition = fadeIn(tandemMotionSpec(reduceMotion, durationMillis))

internal fun tandemFadeExit(
    reduceMotion: Boolean,
    durationMillis: Int = TandemMotion.REVEAL_MILLIS,
): ExitTransition = fadeOut(tandemMotionSpec(reduceMotion, durationMillis))

internal fun tandemRevealEnter(
    reduceMotion: Boolean,
    durationMillis: Int = TandemMotion.REVEAL_MILLIS,
): EnterTransition =
    expandVertically(animationSpec = tandemMotionSpec(reduceMotion, durationMillis)) +
        tandemFadeEnter(reduceMotion, durationMillis)

internal fun tandemRevealExit(
    reduceMotion: Boolean,
    durationMillis: Int = TandemMotion.REVEAL_MILLIS,
): ExitTransition =
    shrinkVertically(animationSpec = tandemMotionSpec(reduceMotion, durationMillis)) +
        tandemFadeExit(reduceMotion, durationMillis)

internal fun tandemSheetEnter(reduceMotion: Boolean): EnterTransition =
    slideInVertically(tandemMotionSpec(reduceMotion, TandemMotion.SHEET_MILLIS)) { it }

internal fun tandemSheetExit(reduceMotion: Boolean): ExitTransition =
    slideOutVertically(tandemMotionSpec(reduceMotion, TandemMotion.SHEET_MILLIS)) { it }

internal fun tandemFloatingEnter(reduceMotion: Boolean): EnterTransition =
    slideInVertically(tandemMotionSpec(reduceMotion, TandemMotion.REVEAL_MILLIS)) { it } +
        tandemFadeEnter(reduceMotion)

internal fun tandemFloatingExit(reduceMotion: Boolean): ExitTransition =
    slideOutVertically(tandemMotionSpec(reduceMotion, TandemMotion.REVEAL_MILLIS)) { it } +
        tandemFadeExit(reduceMotion)

internal fun tandemBottomBarEnter(reduceMotion: Boolean): EnterTransition =
    slideInVertically(tandemMotionSpec(reduceMotion, TandemMotion.ROUTE_MILLIS)) { it } +
        tandemFadeEnter(reduceMotion, TandemMotion.CROSSFADE_MILLIS)

internal fun tandemBottomBarExit(reduceMotion: Boolean): ExitTransition =
    slideOutVertically(tandemMotionSpec(reduceMotion, TandemMotion.ROUTE_MILLIS)) { it } +
        tandemFadeExit(reduceMotion, TandemMotion.CROSSFADE_MILLIS)
