@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.theme

import android.app.Activity
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

@Composable
@Suppress("ktlint:standard:function-naming")
internal actual fun TandemSystemBars(
    isDark: Boolean,
    background: Color,
) {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window
    if (window != null) {
        SideEffect {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                @Suppress("DEPRECATION")
                window.statusBarColor = background.toArgb()
                @Suppress("DEPRECATION")
                window.navigationBarColor = background.toArgb()
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }
    }
}

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
