@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

internal enum class ThemeMode(
    val label: String,
) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
    PURE_BLACK("Pure black"),
}

internal enum class AccentChoice(
    val label: String,
    val color: Color,
) {
    ACTUAL_PURPLE("Actual Purple", Color(0xFF8719E0)),
    TEAL("Lagoon", Color(0xFF31D4C7)),
    BLUE("Cobalt", Color(0xFF73A7FF)),
    VIOLET("Orchid", Color(0xFFB49AFF)),
    AMBER("Saffron", Color(0xFFFFC56E)),
    ROSE("Rose", Color(0xFFFF91A9)),
}

internal data class TandemPalette(
    val background: Color,
    val surface: Color,
    val raised: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val accentContent: Color,
    val accentSurface: Color,
    val positive: Color,
    val warning: Color,
    val negative: Color,
)

private val LocalTandemPalette =
    staticCompositionLocalOf {
        TandemPalette(
            background = Color(0xFF102A43),
            surface = Color(0xFF18344C),
            raised = Color(0xFF23435C),
            border = Color(0xFF36536B),
            text = Color(0xFFF7FAFC),
            muted = Color(0xFFB0C4D6),
            accent = Color(0xFFA368FC),
            accentContent = Color.White,
            accentSurface = Color(0xFF2B2440),
            positive = Color(0xFF73D9A4),
            warning = Color(0xFFFFC56E),
            negative = Color(0xFFFF8C98),
        )
    }

@Composable
internal fun TandemTheme(
    mode: ThemeMode,
    accent: AccentChoice,
    content: @Composable () -> Unit,
) {
    val dark = mode == ThemeMode.DARK || mode == ThemeMode.PURE_BLACK || (mode == ThemeMode.SYSTEM && isSystemInDarkTheme())
    val black = mode == ThemeMode.PURE_BLACK
    val selectedAccent =
        when {
            accent == AccentChoice.ACTUAL_PURPLE && dark -> Color(0xFFA368FC)
            accent == AccentChoice.TEAL && !dark -> Color(0xFF079AA2)
            else -> accent.color
        }
    val accentContent = if (selectedAccent.luminance() > 0.58f) Color(0xFF272630) else Color.White
    val palette =
        if (dark) {
            TandemPalette(
                background = if (black) Color.Black else Color(0xFF102A43),
                surface = if (black) Color(0xFF101216) else Color(0xFF18344C),
                raised = if (black) Color(0xFF1B1F25) else Color(0xFF23435C),
                border = if (black) Color(0xFF2C323C) else Color(0xFF36536B),
                text = Color(0xFFF7FAFC),
                muted = Color(0xFFB0C4D6),
                accent = selectedAccent,
                accentContent = accentContent,
                accentSurface = selectedAccent.copy(alpha = 0.18f),
                positive = Color(0xFF73D9A4),
                warning = Color(0xFFFFC56E),
                negative = Color(0xFFFF8C98),
            )
        } else {
            TandemPalette(
                background = Color(0xFFE8ECF0),
                surface = Color.White,
                raised = Color(0xFFF7FAFC),
                border = Color(0xFFE8ECF0),
                text = Color(0xFF272630),
                muted = Color(0xFF627D98),
                accent = selectedAccent,
                accentContent = accentContent,
                accentSurface = selectedAccent.copy(alpha = 0.10f),
                positive = Color(0xFF147D64),
                warning = Color(0xFFB88115),
                negative = Color(0xFFE12D39),
            )
        }

    CompositionLocalProvider(LocalTandemPalette provides palette) {
        MaterialTheme(content = content)
    }
}

internal val tandemPalette: TandemPalette
    @Composable get() = LocalTandemPalette.current

@Composable
@Suppress("ktlint:standard:function-naming")
internal expect fun TandemSystemBars(
    isDark: Boolean,
    background: Color,
)

@Composable
@Suppress("ktlint:standard:function-naming")
internal expect fun TandemBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
)
