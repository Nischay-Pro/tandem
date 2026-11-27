@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.DialogProperties
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIScreen
import platform.UIKit.UIScreenCapturedDidChangeNotification

@Suppress("UNUSED_PARAMETER")
internal actual fun edgeToEdgeDialogProperties(
    dismissOnBackPress: Boolean,
    secure: Boolean,
) = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = dismissOnBackPress)

/** iOS can't keep a view out of recordings or mirroring, so a secure sheet is covered for as long as the screen is captured. */
@Composable
internal actual fun SecureSheetContent(
    secure: Boolean,
    content: @Composable () -> Unit,
) {
    if (!secure) {
        content()
        return
    }
    var captured by remember { mutableStateOf(UIScreen.mainScreen.captured) }
    DisposableEffect(Unit) {
        val observer =
            NSNotificationCenter.defaultCenter.addObserverForName(
                name = UIScreenCapturedDidChangeNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
            ) { _ -> captured = UIScreen.mainScreen.captured }
        onDispose { NSNotificationCenter.defaultCenter.removeObserver(observer) }
    }
    Box(contentAlignment = Alignment.Center) {
        content()
        if (captured) {
            Box(Modifier.matchParentSize().background(tandemPalette.background), contentAlignment = Alignment.Center) {
                Text(
                    "Hidden while your screen is recorded or shared.",
                    Modifier.padding(TandemSpacing.large),
                    color = tandemPalette.muted,
                    fontSize = TandemTypography.compactBody,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
