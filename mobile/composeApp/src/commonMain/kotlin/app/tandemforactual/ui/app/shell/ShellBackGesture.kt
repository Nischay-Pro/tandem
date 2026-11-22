package app.tandemforactual.ui.app.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.withFrameNanos
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import kotlinx.coroutines.launch

/** Animates a button back press through the same preview transform as predictive back. */
internal fun ShellNavigator.startBackButtonTransition() {
    val backStack = shell.backStack
    if (backStack.size > 1) {
        val outgoingEntry = backStack.last()
        val incomingEntry = backStack.getOrNull(backStack.lastIndex - 1)
        if (incomingEntry == null) {
            popRoute()
        } else {
            shell.backGestureProgress = 0f
            shell.backGestureDirection = 1f
            shell.backGestureOutgoingEntry = outgoingEntry
            shell.backPreviewRoute = incomingEntry
            shell.backButtonTransitionRunning = true
            shell.backGestureCancelJob =
                coroutineScope.launch {
                    val returnAnimation = Animatable(0f)
                    returnAnimation.animateTo(
                        targetValue = 1f,
                        animationSpec =
                            if (reduceMotion) {
                                tandemMotionSpec(true, TandemMotion.ROUTE_MILLIS)
                            } else {
                                tween(TandemMotion.ROUTE_MILLIS, easing = TandemMotion.emphasizedEasing)
                            },
                    ) {
                        shell.backGestureProgress = value
                    }
                    shell.backButtonTransitionRunning = false
                    shell.skipNextRouteTransition = true
                    popRoute()
                    shell.backGestureCancelJob = null
                    if (shell.queuedBackPresses > 0) {
                        shell.backButtonTransitionRunning = true
                        coroutineScope.launch {
                            withFrameNanos { }
                            shell.backButtonTransitionRunning = false
                            if (shell.queuedBackPresses > 0 && backStack.size > 1) {
                                shell.queuedBackPresses -= 1
                                startBackButtonTransition()
                            } else {
                                shell.queuedBackPresses = 0
                            }
                        }
                    }
                }
        }
    }
}

/** Tracks predictive back progress, animating the preview away when the gesture is cancelled. */
internal fun ShellNavigator.updateBackGesture(
    progress: Float,
    direction: Float,
) {
    shell.backGestureDirection = direction
    if (progress > 0f) {
        shell.backGestureCommitPending = progress >= 1f
        shell.backGestureCancelJob?.cancel()
        shell.backGestureCancelJob = null
        shell.backButtonTransitionRunning = false
        shell.backGestureProgress = progress
        if (shell.backGestureOutgoingEntry == null) {
            shell.backGestureOutgoingEntry = shell.backStack.lastOrNull()
        }
        if (shell.backPreviewRoute == null) {
            shell.backPreviewRoute = shell.backStack.getOrNull(shell.backStack.lastIndex - 1)
        }
    } else if (shell.backGestureProgress > 0f) {
        shell.backGestureCommitPending = false
        shell.backButtonTransitionRunning = false
        shell.backGestureCancelJob?.cancel()
        shell.backGestureCancelJob =
            coroutineScope.launch {
                val returnAnimation = Animatable(shell.backGestureProgress)
                returnAnimation.animateTo(
                    targetValue = 0f,
                    animationSpec = tandemMotionSpec(reduceMotion, TandemMotion.BACK_CANCEL_MILLIS),
                ) {
                    shell.backGestureProgress = value
                }
                shell.backGestureOutgoingEntry = null
                shell.backPreviewRoute = null
                shell.backGestureCancelJob = null
            }
    } else {
        shell.backGestureCommitPending = false
        shell.backGestureProgress = 0f
        shell.backGestureOutgoingEntry = null
        shell.backPreviewRoute = null
    }
}
