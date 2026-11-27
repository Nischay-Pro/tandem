@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.tandemforactual.ui.design.theme.TandemLayout
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.currentWindowSizeInfo
import app.tandemforactual.ui.design.theme.escapeClosesSheetsInCompose
import app.tandemforactual.ui.design.theme.tandemDialogEnter
import app.tandemforactual.ui.design.theme.tandemDialogExit
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import app.tandemforactual.ui.design.theme.tandemSheetEnter
import app.tandemforactual.ui.design.theme.tandemSheetExit
import kotlinx.coroutines.flow.first

/**
 * A bottom-anchored task sheet with shared enter, dismiss, and reduce-motion behavior. A [fullScreen] sheet fills a
 * phone, and floats as a centred dialog where the window has room around it.
 */
@Composable
internal fun TandemTaskSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    onDismissed: () -> Unit,
    fullScreen: Boolean = false,
    dismissOnBackPress: Boolean = true,
    secure: Boolean = false,
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
    val floats = fullScreen && currentWindowSizeInfo().floatsEditors
    if (visibleState.currentState || visibleState.targetState) {
        Dialog(
            onDismissRequest = onDismissRequest,
            properties =
                edgeToEdgeDialogProperties(dismissOnBackPress, secure),
        ) {
            FormShortcutHost(
                onEscape = onDismissRequest.takeIf { dismissOnBackPress && escapeClosesSheetsInCompose },
                Modifier.fillMaxSize(),
            ) {
                if (floats) {
                    FloatingSheet(visibleState, secure, content)
                } else {
                    TaskSheetFrame(visibleState, fullScreen, secure, content)
                }
            }
        }
    }
}

@Composable
private fun TaskSheetFrame(
    visibleState: MutableTransitionState<Boolean>,
    fullScreen: Boolean,
    secure: Boolean,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().then(if (fullScreen) Modifier else Modifier.safeDrawingPadding()),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            modifier = Modifier.fillMaxWidth(),
            visibleState = visibleState,
            enter = tandemSheetEnter(tandemReduceMotion),
            exit = tandemSheetExit(tandemReduceMotion),
        ) {
            if (fullScreen) {
                Box(Modifier.fillMaxSize().background(tandemPalette.background).safeDrawingPadding()) {
                    SecureSheetContent(secure, content)
                }
            } else {
                SecureSheetContent(secure, content)
            }
        }
    }
}

/** A full-screen sheet's content as a centred card, so an iPad or unfolded phone keeps the page visible around it. */
@Composable
private fun FloatingSheet(
    visibleState: MutableTransitionState<Boolean>,
    secure: Boolean,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(TandemShapes.large)
    Box(
        Modifier.fillMaxSize().safeDrawingPadding().padding(TandemSpacing.extraLarge),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = tandemDialogEnter(tandemReduceMotion),
            exit = tandemDialogExit(tandemReduceMotion),
        ) {
            Box(
                Modifier
                    .widthIn(max = TandemLayout.editorDialogWidth)
                    .fillMaxWidth()
                    .heightIn(max = TandemLayout.editorDialogMaxHeight)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(tandemPalette.background)
                    .border(1.dp, tandemPalette.border, shape),
            ) {
                SecureSheetContent(secure, content)
            }
        }
    }
}

/**
 * Dialog properties that let the sheet draw behind the system bars and the keyboard, so it can pad for them itself. A
 * [secure] sheet shows a secret, so it's kept out of screenshots and screen recordings where the platform allows.
 */
internal expect fun edgeToEdgeDialogProperties(
    dismissOnBackPress: Boolean,
    secure: Boolean = false,
): DialogProperties

/** Where the platform can't keep a [secure] sheet out of captures itself, it covers the sheet while the screen is captured. */
@Composable
internal expect fun SecureSheetContent(
    secure: Boolean,
    content: @Composable () -> Unit,
)
