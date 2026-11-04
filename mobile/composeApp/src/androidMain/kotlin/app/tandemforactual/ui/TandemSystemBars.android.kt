@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

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
            window.statusBarColor = background.toArgb()
            window.navigationBarColor = background.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val appearance =
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                window.insetsController?.setSystemBarsAppearance(if (isDark) 0 else appearance, appearance)
            } else {
                @Suppress("DEPRECATION")
                val lightFlags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility =
                    if (isDark) {
                        window.decorView.systemUiVisibility and lightFlags.inv()
                    } else {
                        window.decorView.systemUiVisibility or lightFlags
                    }
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
internal actual fun TandemBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    BackHandler(enabled = enabled, onBack = onBack)
}
