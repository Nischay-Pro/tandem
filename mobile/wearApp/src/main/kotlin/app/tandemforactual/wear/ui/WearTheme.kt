@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

/** Tandem violet on true black, which round OLED screens show with the least light and battery. */
object WearColors {
    val accent = Color(0xFFB9A8FF)
    val accentDim = Color(0xFF7047E8)
    val accentContainer = Color(0xFF2A2050)
    val onAccent = Color(0xFF1B1238)
    val surface = Color(0xFF17151F)
    val surfaceHigh = Color(0xFF221F2E)
    val muted = Color(0xFFB7B2C9)
    val over = Color(0xFFFFB4A9)
    val overContainer = Color(0xFF5C1A13)
    val track = Color(0xFF332E45)
}

private val tandemColors =
    ColorScheme(
        primary = WearColors.accent,
        primaryDim = WearColors.accentDim,
        primaryContainer = WearColors.accentContainer,
        onPrimary = WearColors.onAccent,
        onPrimaryContainer = WearColors.accent,
        surfaceContainerLow = WearColors.surface,
        surfaceContainer = WearColors.surface,
        surfaceContainerHigh = WearColors.surfaceHigh,
        onSurfaceVariant = WearColors.muted,
        error = WearColors.over,
        errorContainer = WearColors.overContainer,
        background = Color.Black,
    )

@Composable
fun TandemWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = tandemColors, content = content)
}
