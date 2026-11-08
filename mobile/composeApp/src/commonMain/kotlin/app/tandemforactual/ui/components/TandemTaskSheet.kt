@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.tandemforactual.ui.tandemReduceMotion
import app.tandemforactual.ui.tandemSheetEnter
import app.tandemforactual.ui.tandemSheetExit
import kotlinx.coroutines.flow.first

/** A full-screen task sheet with shared enter, dismiss, and reduce-motion behavior. */
@Composable
internal fun TandemTaskSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    onDismissed: () -> Unit,
    content: @Composable () -> Unit,
) {
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    val currentOnDismissed = rememberUpdatedState(onDismissed)
    LaunchedEffect(visible) {
        visibleState.targetState = visible
    }
    LaunchedEffect(visibleState) {
        snapshotFlow { visibleState.isIdle && !visibleState.currentState && !visibleState.targetState }
            .first { it }
        currentOnDismissed.value()
    }
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = tandemSheetEnter(tandemReduceMotion),
            exit = tandemSheetExit(tandemReduceMotion),
        ) {
            SurfaceCard(Modifier.fillMaxSize(), content = { content() })
        }
    }
}
