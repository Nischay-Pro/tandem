@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.tandemforactual.ui.tandemPalette
import app.tandemforactual.ui.tandemReduceMotion
import app.tandemforactual.ui.tandemSheetEnter
import app.tandemforactual.ui.tandemSheetExit
import kotlinx.coroutines.flow.first

/** A bottom-anchored task sheet with shared enter, dismiss, and reduce-motion behavior. */
@Composable
internal fun TandemTaskSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    onDismissed: () -> Unit,
    fullScreen: Boolean = false,
    content: @Composable () -> Unit,
) {
    val visibleState = remember { MutableTransitionState(false) }
    val currentOnDismissed = rememberUpdatedState(onDismissed)
    var hasBeenPresented by remember { mutableStateOf(false) }
    SideEffect {
        if (visible) hasBeenPresented = true
        visibleState.targetState = visible
    }
    LaunchedEffect(visible, visibleState) {
        if (!visible && hasBeenPresented) {
            snapshotFlow { visibleState.isIdle && !visibleState.currentState && !visibleState.targetState }
                .first { it }
            hasBeenPresented = false
            currentOnDismissed.value()
        }
    }
    if (visibleState.currentState || visibleState.targetState) {
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                AnimatedVisibility(
                    modifier = Modifier.fillMaxWidth(),
                    visibleState = visibleState,
                    enter = tandemSheetEnter(tandemReduceMotion),
                    exit = tandemSheetExit(tandemReduceMotion),
                ) {
                    if (fullScreen) {
                        Box(Modifier.fillMaxSize().background(tandemPalette.background)) {
                            content()
                        }
                    } else {
                        content()
                    }
                }
            }
        }
    }
}
