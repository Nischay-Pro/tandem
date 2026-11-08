@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.tandemforactual.ui.TandemMotion
import app.tandemforactual.ui.tandemFadeEnter
import app.tandemforactual.ui.tandemFadeExit
import app.tandemforactual.ui.tandemReduceMotion

/** A compact choice dialog whose content uses the same fade timing and reduce-motion setting. */
@Composable
internal fun TandemChoiceDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    val visibility = remember { MutableTransitionState(false) }
    SideEffect { visibility.targetState = visible }
    if (visibility.currentState || visibility.targetState) {
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            AnimatedVisibility(
                visibleState = visibility,
                enter = tandemFadeEnter(tandemReduceMotion, TandemMotion.REVEAL_MILLIS),
                exit = tandemFadeExit(tandemReduceMotion, TandemMotion.REVEAL_MILLIS),
            ) {
                Box { content() }
            }
        }
    }
}
