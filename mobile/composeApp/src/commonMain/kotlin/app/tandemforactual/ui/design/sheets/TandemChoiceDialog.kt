@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.tandemFadeEnter
import app.tandemforactual.ui.design.theme.tandemFadeExit
import app.tandemforactual.ui.design.theme.tandemReduceMotion

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
            // A window that wraps its content is resized by the system on every frame its content animates, which
            // stalls the animation; filling the screen keeps the window still and centres the content instead.
            TabletopAwareBox(
                Modifier
                    .fillMaxSize()
                    .clickable(remember { MutableInteractionSource() }, null, onClick = onDismissRequest)
                    .safeDrawingPadding()
                    .padding(vertical = TandemSpacing.small),
            ) {
                AnimatedVisibility(
                    visibleState = visibility,
                    enter = tandemFadeEnter(tandemReduceMotion, TandemMotion.REVEAL_MILLIS),
                    exit = tandemFadeExit(tandemReduceMotion, TandemMotion.REVEAL_MILLIS),
                ) {
                    Box(Modifier.clickable(remember { MutableInteractionSource() }, null) {}) { content() }
                }
            }
        }
    }
}
