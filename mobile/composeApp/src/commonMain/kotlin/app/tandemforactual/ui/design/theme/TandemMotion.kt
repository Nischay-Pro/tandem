package app.tandemforactual.ui.design.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.Alignment

/** Shared timing for app transitions so navigation and state changes feel like one system. */
internal object TandemMotion {
    const val ROUTE_MILLIS = 340
    const val SHEET_MILLIS = 360
    const val COLOR_MILLIS = 200
    const val CONTROL_MILLIS = 180
    const val CONTENT_SIZE_MILLIS = 200
    const val REVEAL_MILLIS = 200
    const val STATUS_CHANGE_MILLIS = 160
    const val CROSSFADE_MILLIS = 100
    const val TAB_TRANSITION_MILLIS = 210
    const val TAB_EXIT_MILLIS = 90
    const val ROUTE_PARALLAX = 0.25f
    const val ROUTE_COVERED_ALPHA = 0.55f
    const val BACK_CANCEL_MILLIS = 200

    val enterEasing = LinearOutSlowInEasing
    val exitEasing = FastOutLinearInEasing
    val movementEasing = FastOutSlowInEasing
    val emphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
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

internal fun tandemBottomBarEnter(reduceMotion: Boolean): EnterTransition =
    slideInVertically(tandemMotionSpec(reduceMotion, TandemMotion.ROUTE_MILLIS)) { it } +
        tandemFadeEnter(reduceMotion, TandemMotion.CROSSFADE_MILLIS)

internal fun tandemBottomBarExit(reduceMotion: Boolean): ExitTransition =
    slideOutVertically(tandemMotionSpec(reduceMotion, TandemMotion.ROUTE_MILLIS)) { it } +
        tandemFadeExit(reduceMotion, TandemMotion.CROSSFADE_MILLIS)

/** Floating buttons glide off the bottom edge and back, instead of being clipped like an expanding section. */
internal fun tandemFloatingEnter(reduceMotion: Boolean): EnterTransition =
    if (reduceMotion) {
        fadeIn(tween(TandemMotion.CONTROL_MILLIS))
    } else {
        slideInVertically(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) { it * 2 } +
            fadeIn(tween(TandemMotion.REVEAL_MILLIS, easing = TandemMotion.enterEasing))
    }

internal fun tandemFloatingExit(reduceMotion: Boolean): ExitTransition =
    if (reduceMotion) {
        fadeOut(tween(TandemMotion.CONTROL_MILLIS))
    } else {
        slideOutVertically(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)) { it * 2 } +
            fadeOut(tween(TandemMotion.REVEAL_MILLIS, easing = TandemMotion.exitEasing))
    }

/** A floating button's label slides in and out with the width change, so pill and circle morph in one motion. */
internal fun tandemLabelEnter(reduceMotion: Boolean): EnterTransition =
    if (reduceMotion) {
        fadeIn(tween(TandemMotion.CONTROL_MILLIS))
    } else {
        expandHorizontally(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow), Alignment.Start) +
            fadeIn(tween(TandemMotion.REVEAL_MILLIS, easing = TandemMotion.enterEasing))
    }

internal fun tandemLabelExit(reduceMotion: Boolean): ExitTransition =
    if (reduceMotion) {
        fadeOut(tween(TandemMotion.CONTROL_MILLIS))
    } else {
        shrinkHorizontally(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow), Alignment.Start) +
            fadeOut(tween(TandemMotion.CONTROL_MILLIS, easing = TandemMotion.exitEasing))
    }
