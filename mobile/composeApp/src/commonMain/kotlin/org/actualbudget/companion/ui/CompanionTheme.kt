@file:Suppress("ktlint:standard:function-naming")

package org.actualbudget.companion.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

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
    TEAL("Lagoon", Color(0xFF31D4C7)),
    BLUE("Cobalt", Color(0xFF73A7FF)),
    VIOLET("Orchid", Color(0xFFB49AFF)),
    AMBER("Saffron", Color(0xFFFFC56E)),
    ROSE("Rose", Color(0xFFFF91A9)),
}

internal data class CompanionPalette(
    val background: Color,
    val surface: Color,
    val raised: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val accentSurface: Color,
    val positive: Color,
    val warning: Color,
    val negative: Color,
)

private val LocalCompanionPalette =
    staticCompositionLocalOf {
        CompanionPalette(
            background = Color(0xFF10131A),
            surface = Color(0xFF191E28),
            raised = Color(0xFF222A36),
            border = Color(0xFF313B4B),
            text = Color(0xFFF4F6FA),
            muted = Color(0xFFA9B2C1),
            accent = Color(0xFF31D4C7),
            accentSurface = Color(0xFF193834),
            positive = Color(0xFF73D9A4),
            warning = Color(0xFFFFC56E),
            negative = Color(0xFFFF8C98),
        )
    }

@Composable
internal fun CompanionTheme(
    mode: ThemeMode,
    accent: AccentChoice,
    content: @Composable () -> Unit,
) {
    val dark = mode == ThemeMode.DARK || mode == ThemeMode.PURE_BLACK || (mode == ThemeMode.SYSTEM && isSystemInDarkTheme())
    val black = mode == ThemeMode.PURE_BLACK
    val palette =
        if (dark) {
            CompanionPalette(
                background = if (black) Color.Black else Color(0xFF10131A),
                surface = if (black) Color(0xFF101216) else Color(0xFF191E28),
                raised = if (black) Color(0xFF1B1F25) else Color(0xFF222A36),
                border = if (black) Color(0xFF2C323C) else Color(0xFF313B4B),
                text = Color(0xFFF4F6FA),
                muted = Color(0xFFA9B2C1),
                accent = accent.color,
                accentSurface = accent.color.copy(alpha = 0.14f),
                positive = Color(0xFF73D9A4),
                warning = Color(0xFFFFC56E),
                negative = Color(0xFFFF8C98),
            )
        } else {
            CompanionPalette(
                background = Color(0xFFF7F8FA),
                surface = Color.White,
                raised = Color(0xFFF0F3F7),
                border = Color(0xFFD9E0E8),
                text = Color(0xFF172033),
                muted = Color(0xFF526176),
                accent = if (accent == AccentChoice.TEAL) Color(0xFF079AA2) else accent.color,
                accentSurface = accent.color.copy(alpha = 0.12f),
                positive = Color(0xFF16874E),
                warning = Color(0xFF9A6500),
                negative = Color(0xFFB93C4D),
            )
        }

    CompositionLocalProvider(LocalCompanionPalette provides palette) {
        MaterialTheme(content = content)
    }
}

internal val companionPalette: CompanionPalette
    @Composable get() = LocalCompanionPalette.current

@Composable
@Suppress("ktlint:standard:function-naming")
internal expect fun CompanionSystemBars(isDark: Boolean)

@Composable
@Suppress("ktlint:standard:function-naming")
internal expect fun CompanionBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
)
