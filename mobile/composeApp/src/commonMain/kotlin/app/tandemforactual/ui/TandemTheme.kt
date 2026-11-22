@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    val lightColor: Color,
    val darkColor: Color,
) {
    ACTUAL_PURPLE("Tandem violet", Color(0xFF7047E8), Color(0xFFB9A8FF)),
    CLARITY("Clarity green", Color(0xFF176B4B), Color(0xFF76DDA6)),
    WARM("Warm terracotta", Color(0xFFA84D2A), Color(0xFFFFB98B)),
    TEAL("Lagoon", Color(0xFF006C70), Color(0xFF54D7CE)),
    BLUE("Cobalt", Color(0xFF0055A4), Color(0xFF85C1FF)),
    VIOLET("Orchid", Color(0xFF6941A5), Color(0xFFC6AAFF)),
    AMBER("Saffron", Color(0xFF8A4C00), Color(0xFFFFD08A)),
    ROSE("Rose", Color(0xFFB22650), Color(0xFFFFABC1)),
}

internal fun AccentChoice.colorFor(isDark: Boolean): Color = if (isDark) darkColor else lightColor

internal object TandemTypography {
    val indicator = 9.sp
    val micro = 10.sp
    val caption = 11.sp
    val label = 12.sp
    val compactBody = 13.sp
    val body = 14.sp
    val action = 15.sp
    val keypadOperator = 16.sp
    val periodTitle = 17.sp
    val cardTitle = 18.sp
    val sectionTitle = 20.sp
    val keypadNumber = 22.sp
    val pageTitle = 24.sp
    val heroTitle = 28.sp
    val setupBrandWordmark = 21.sp
    val welcomeWordmark = 48.sp
    val welcomeWordmarkLineHeight = 52.sp
    val setupHeadline = 32.sp
    val amount = 32.sp
    val amountField = 40.sp
    val microLineHeight = 13.sp
    val tightLineHeight = 14.sp
    val compactLineHeight = 17.sp
    val bodyLineHeight = 20.sp
    val actionLineHeight = 22.sp
    val largeInputLineHeight = 38.sp
    val headingLineHeight = 29.sp
    val setupHeadlineLineHeight = 38.sp
    val amountLineHeight = 36.sp
    val amountFieldLineHeight = 48.sp
    val captionLetterSpacing = 0.2.sp
}

internal object TandemShapes {
    val small = 6.dp
    val control = 12.dp
    val medium = 14.dp
    val roomy = 16.dp
    val large = 18.dp
    val capsule = 28.dp
    val pill = 999.dp
}

internal object TandemSpacing {
    val xxSmall = 2.dp
    val extraSmall = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val extraLarge = 24.dp
    val screen = 20.dp
}

internal object TandemLayout {
    val readableContentWidth = 720.dp
    val setupContentWidth = 560.dp
    val listRowMinHeight = 64.dp
    val compactListRowMinHeight = 56.dp
    val listRowHorizontalInset = TandemSpacing.large
    val listRowVerticalInset = TandemSpacing.small
    val compactListRowVerticalInset = TandemSpacing.extraSmall + TandemSpacing.xxSmall
    val listRowGap = TandemSpacing.medium
    val listRowLeadingSize = 44.dp
    val compactListRowLeadingSize = 36.dp
    val listRowIconSize = 30.dp
    val compactListRowIconSize = 24.dp
    val listRowLogoSize = 32.dp
    val compactListRowLogoSize = 28.dp
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
    val syncing: Color,
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
            accent = Color(0xFFC9B1FF),
            accentContent = Color(0xFF111827),
            accentSurface = Color(0xFF384B6C),
            positive = Color(0xFF73D9A4),
            syncing = Color(0xFF73B7FF),
            warning = Color(0xFFFFD18A),
            negative = Color(0xFFFF9CA5),
        )
    }
private val LocalReduceMotion = staticCompositionLocalOf { false }

internal fun wcagContrastRatio(
    foreground: Color,
    background: Color,
): Float {
    val (lighter, darker) = listOf(foreground.luminance(), background.luminance()).sortedDescending()
    return (lighter + 0.05f) / (darker + 0.05f)
}

internal fun isDarkTheme(
    mode: ThemeMode,
    systemIsDark: Boolean,
): Boolean = mode == ThemeMode.DARK || mode == ThemeMode.PURE_BLACK || (mode == ThemeMode.SYSTEM && systemIsDark)

internal fun tandemPaletteFor(
    mode: ThemeMode,
    systemIsDark: Boolean,
    accent: AccentChoice,
): TandemPalette {
    val dark = isDarkTheme(mode, systemIsDark)
    val black = mode == ThemeMode.PURE_BLACK
    val surfaces = tandemSurfaceColors(accent, dark)
    val selectedAccent = accent.colorFor(dark)
    val darkContent = Color(0xFF111827)
    val lightContent = Color.White
    val accentContent =
        if (wcagContrastRatio(darkContent, selectedAccent) >= wcagContrastRatio(lightContent, selectedAccent)) {
            darkContent
        } else {
            lightContent
        }

    return if (dark) {
        val background = if (black) Color.Black else surfaces.background
        val surface = if (black) Color(0xFF101116) else surfaces.surface
        TandemPalette(
            background = background,
            surface = surface,
            raised = if (black) Color(0xFF1B1D25) else surfaces.raised,
            border = if (black) Color(0xFF30313C) else surfaces.border,
            text = surfaces.text,
            muted = surfaces.muted,
            accent = selectedAccent,
            accentContent = accentContent,
            accentSurface = selectedAccent.copy(alpha = 0.16f).compositeOver(surface),
            positive = Color(0xFF73D9A4),
            syncing = Color(0xFF73B7FF),
            warning = Color(0xFFFFD18A),
            negative = Color(0xFFFF9CA5),
        )
    } else {
        TandemPalette(
            background = surfaces.background,
            surface = surfaces.surface,
            raised = surfaces.raised,
            border = surfaces.border,
            text = surfaces.text,
            muted = surfaces.muted,
            accent = selectedAccent,
            accentContent = accentContent,
            accentSurface = selectedAccent.copy(alpha = 0.10f).compositeOver(surfaces.surface),
            positive = Color(0xFF0E6F4F),
            syncing = Color(0xFF174EA6),
            warning = Color(0xFF7A4B00),
            negative = Color(0xFFB3261E),
        )
    }
}

private data class TandemSurfaceColors(
    val background: Color,
    val surface: Color,
    val raised: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
)

private fun tandemSurfaceColors(
    accent: AccentChoice,
    isDark: Boolean,
): TandemSurfaceColors =
    when {
        accent == AccentChoice.CLARITY && isDark ->
            TandemSurfaceColors(
                background = Color(0xFF09180F),
                surface = Color(0xFF11251A),
                raised = Color(0xFF1A3324),
                border = Color(0xFF2D4A38),
                text = Color(0xFFEEF8F0),
                muted = Color(0xFFB6CCBA),
            )
        accent == AccentChoice.CLARITY ->
            TandemSurfaceColors(
                background = Color(0xFFF3F8F4),
                surface = Color.White,
                raised = Color(0xFFF7FBF8),
                border = Color(0xFFDCE8DF),
                text = Color(0xFF19251D),
                muted = Color(0xFF526358),
            )
        accent == AccentChoice.WARM && isDark ->
            TandemSurfaceColors(
                background = Color(0xFF1A100B),
                surface = Color(0xFF291A12),
                raised = Color(0xFF39251B),
                border = Color(0xFF543A2B),
                text = Color(0xFFFFF3EA),
                muted = Color(0xFFE3C6B0),
            )
        accent == AccentChoice.WARM ->
            TandemSurfaceColors(
                background = Color(0xFFFBF5F0),
                surface = Color.White,
                raised = Color(0xFFFFF9F5),
                border = Color(0xFFEBDDD2),
                text = Color(0xFF2E251F),
                muted = Color(0xFF69564B),
            )
        isDark ->
            TandemSurfaceColors(
                background = Color(0xFF100F19),
                surface = Color(0xFF171622),
                raised = Color(0xFF211F31),
                border = Color(0xFF39364E),
                text = Color(0xFFF6F3FF),
                muted = Color(0xFFC2BDD2),
            )
        else ->
            TandemSurfaceColors(
                background = Color(0xFFF6F4FC),
                surface = Color.White,
                raised = Color(0xFFFAF9FF),
                border = Color(0xFFE2DFF0),
                text = Color(0xFF1E1B2B),
                muted = Color(0xFF5B576D),
            )
    }

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

internal val tandemPalette: TandemPalette
    @Composable get() = LocalTandemPalette.current

internal val tandemReduceMotion: Boolean
    @Composable get() = LocalReduceMotion.current

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
    onBackProgress: (progress: Float, direction: Float) -> Unit,
)

@Composable
@Suppress("ktlint:standard:function-naming")
internal expect fun SystemReduceMotionEnabled(): Boolean

internal expect fun Modifier.tandemEdgeBackGesture(
    enabled: Boolean,
    onBack: () -> Unit,
    onBackProgress: (progress: Float, direction: Float) -> Unit,
): Modifier
