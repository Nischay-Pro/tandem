@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.theme

import android.graphics.Color
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

@Composable
@Suppress("ktlint:standard:function-naming")
internal actual fun TandemSystemBars(isDark: Boolean) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    LaunchedEffect(activity, isDark) {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { isDark },
            navigationBarStyle = SystemBarStyle.auto(LIGHT_NAVIGATION_SCRIM, DARK_NAVIGATION_SCRIM) { isDark },
        )
    }
}

private val LIGHT_NAVIGATION_SCRIM = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
private val DARK_NAVIGATION_SCRIM = Color.argb(0x80, 0x1B, 0x1B, 0x1B)

@Composable
@Suppress("ktlint:standard:function-naming")
internal actual fun TandemBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
    onBackProgress: (progress: Float, direction: Float) -> Unit,
) {
    PredictiveBackHandler(enabled = enabled) { progress ->
        var completed = false
        var gestureStarted = false
        var direction = 1f
        try {
            progress.collect { event ->
                direction = if (event.swipeEdge == androidx.activity.BackEventCompat.EDGE_RIGHT) -1f else 1f
                gestureStarted = gestureStarted || event.progress > 0f
                onBackProgress(event.progress, direction)
            }
            completed = true
            if (gestureStarted) onBackProgress(1f, direction)
            onBack()
        } catch (error: CancellationException) {
            throw error
        } finally {
            if (!completed) onBackProgress(0f, 1f)
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
internal actual fun SystemReduceMotionEnabled(): Boolean {
    val context = LocalContext.current
    return Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}

internal actual fun Modifier.tandemEdgeBackGesture(
    enabled: Boolean,
    onBack: () -> Unit,
    onBackProgress: (progress: Float, direction: Float) -> Unit,
): Modifier = this
