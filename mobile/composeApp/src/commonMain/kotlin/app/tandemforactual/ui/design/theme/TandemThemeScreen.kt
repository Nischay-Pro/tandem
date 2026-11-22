@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import app.tandemforactual.ui.app.LocalInterfaceMode
import app.tandemforactual.ui.core.format.AppLanguageController
import app.tandemforactual.ui.core.format.AppLocaleEnvironment
import app.tandemforactual.ui.core.format.LocalAppLanguage
import app.tandemforactual.ui.core.format.appLanguageTag
import app.tandemforactual.ui.core.state.InterfaceMode

@Composable
internal fun TandemTheme(
    mode: ThemeMode,
    accent: AccentChoice,
    reduceMotion: Boolean = false,
    animateThemeChanges: Boolean = true,
    language: AppLanguageController? = null,
    interfaceMode: InterfaceMode = InterfaceMode.MINIMAL,
    content: @Composable () -> Unit,
) {
    val systemIsDark = isSystemInDarkTheme()
    val dark = isDarkTheme(mode, systemIsDark)
    val palette = tandemPaletteFor(mode, systemIsDark, accent)

    val colorSpec =
        tandemMotionSpec<Color>(
            reduceMotion = reduceMotion || !animateThemeChanges,
            durationMillis = TandemMotion.COLOR_MILLIS,
        )
    val animatedPalette =
        TandemPalette(
            background = animateColorAsState(palette.background, colorSpec, label = "theme-background").value,
            surface = animateColorAsState(palette.surface, colorSpec, label = "theme-surface").value,
            raised = animateColorAsState(palette.raised, colorSpec, label = "theme-raised").value,
            border = animateColorAsState(palette.border, colorSpec, label = "theme-border").value,
            text = animateColorAsState(palette.text, colorSpec, label = "theme-text").value,
            muted = animateColorAsState(palette.muted, colorSpec, label = "theme-muted").value,
            accent = animateColorAsState(palette.accent, colorSpec, label = "theme-accent").value,
            accentContent = animateColorAsState(palette.accentContent, colorSpec, label = "theme-accent-content").value,
            accentSurface = animateColorAsState(palette.accentSurface, colorSpec, label = "theme-accent-surface").value,
            positive = animateColorAsState(palette.positive, colorSpec, label = "theme-positive").value,
            syncing = animateColorAsState(palette.syncing, colorSpec, label = "theme-syncing").value,
            warning = animateColorAsState(palette.warning, colorSpec, label = "theme-warning").value,
            negative = animateColorAsState(palette.negative, colorSpec, label = "theme-negative").value,
        )

    CompositionLocalProvider(
        LocalInterfaceMode provides interfaceMode,
        LocalTandemPalette provides animatedPalette,
        LocalReduceMotion provides reduceMotion,
    ) {
        val interFamily = tandemInterFamily()
        val materialTypography = remember(interFamily) { Typography(defaultFontFamily = interFamily) }
        val materialColors =
            if (dark) {
                darkColors(
                    primary = animatedPalette.accent,
                    primaryVariant = animatedPalette.accent,
                    secondary = animatedPalette.accent,
                    secondaryVariant = animatedPalette.accent,
                    background = animatedPalette.background,
                    surface = animatedPalette.raised,
                    error = animatedPalette.negative,
                    onPrimary = animatedPalette.accentContent,
                    onSecondary = animatedPalette.accentContent,
                    onBackground = animatedPalette.text,
                    onSurface = animatedPalette.text,
                    onError = Color.White,
                )
            } else {
                lightColors(
                    primary = animatedPalette.accent,
                    primaryVariant = animatedPalette.accent,
                    secondary = animatedPalette.accent,
                    secondaryVariant = animatedPalette.accent,
                    background = animatedPalette.background,
                    surface = animatedPalette.raised,
                    error = animatedPalette.negative,
                    onPrimary = animatedPalette.accentContent,
                    onSecondary = animatedPalette.accentContent,
                    onBackground = animatedPalette.text,
                    onSurface = animatedPalette.text,
                    onError = Color.White,
                )
            }
        if (language == null) {
            MaterialTheme(colors = materialColors, typography = materialTypography, content = content)
        } else {
            AppLocaleEnvironment(appLanguageTag(language.current)) {
                CompositionLocalProvider(LocalAppLanguage provides language) {
                    MaterialTheme(colors = materialColors, typography = materialTypography, content = content)
                }
            }
        }
    }
}
