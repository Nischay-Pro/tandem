package app.tandemforactual.ui.core.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.StateFlow

/**
 * The paired watch a platform can reach: an Apple Watch on iOS, a Wear OS watch on Android. Settings shows the Watch
 * page only while [paired] is true, and Tandem hands each new summary to [sendSummary].
 */
interface WatchLink {
    val paired: StateFlow<Boolean>

    fun sendSummary(json: String)
}

val LocalWatchLink = staticCompositionLocalOf<WatchLink?> { null }

/** Whether a watch with Tandem on it is paired right now. */
@Composable
fun watchPaired(): Boolean {
    val link = LocalWatchLink.current ?: return false
    val paired by link.paired.collectAsState()
    return paired
}
