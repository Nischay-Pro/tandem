package app.tandemforactual.ui.app

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** How far the system window controls reach in from the window's leading edge; zero when there are none. */
internal val LocalWindowControlsInset = compositionLocalOf { 0.dp }

/** Moves the content in from the leading edge just enough to clear the window controls. */
@Composable
internal fun Modifier.windowControlsPadding(): Modifier {
    val reach = LocalWindowControlsInset.current
    if (reach <= 0.dp) return this
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val windowWidth = LocalWindowInfo.current.containerSize.width
    var leadingPx by remember { mutableFloatStateOf(0f) }
    val start: Dp = with(LocalDensity.current) { (reach - leadingPx.toDp()).coerceAtLeast(0.dp) }
    return onGloballyPositioned {
        val bounds = it.boundsInWindow()
        leadingPx = if (rtl) windowWidth - bounds.right else bounds.left
    }.padding(start = start)
}
