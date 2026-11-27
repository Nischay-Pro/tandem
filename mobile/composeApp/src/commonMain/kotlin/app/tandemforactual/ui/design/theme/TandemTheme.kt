@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    PURE_BLACK,
}

internal enum class AccentChoice(
    val lightColor: Color,
    val darkColor: Color,
) {
    ACTUAL_PURPLE(Color(0xFF7047E8), Color(0xFFB9A8FF)),
    CLARITY(Color(0xFF176B4B), Color(0xFF76DDA6)),
    WARM(Color(0xFFA84D2A), Color(0xFFFFB98B)),
    TEAL(Color(0xFF006C70), Color(0xFF54D7CE)),
    BLUE(Color(0xFF0055A4), Color(0xFF85C1FF)),
    VIOLET(Color(0xFF6941A5), Color(0xFFC6AAFF)),
    AMBER(Color(0xFF8A4C00), Color(0xFFFFD08A)),
    ROSE(Color(0xFFB22650), Color(0xFFFFABC1)),
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
    val railWidth = 80.dp
    val bottomBarHeight = 88.dp
    val listPaneWidth = 360.dp
    val wideListPaneWidth = 420.dp
    val minDetailPaneWidth = 340.dp
    val listRowMinHeight = 64.dp
    val compactListRowMinHeight = 56.dp
    val listRowHorizontalInset = TandemSpacing.large
    val listRowVerticalInset = TandemSpacing.small
    val compactListRowVerticalInset = TandemSpacing.extraSmall + TandemSpacing.xxSmall
    val listRowGap = TandemSpacing.medium
    val listRowLeadingSize = 44.dp
    val rowBadgeSize = 18.dp
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
    val outline: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val accentContent: Color,
    val accentSurface: Color,
    val positive: Color,
    val syncing: Color,
    val warning: Color,
    val negative: Color,
    val negativeContent: Color,
    val categories: List<Color>,
)

internal val LocalTandemPalette =
    staticCompositionLocalOf { tandemPaletteFor(ThemeMode.DARK, systemIsDark = true, accent = AccentChoice.ACTUAL_PURPLE) }

private data class TandemStatusColors(
    val positive: Color,
    val syncing: Color,
    val warning: Color,
    val negative: Color,
)

private val darkStatusColors =
    TandemStatusColors(
        positive = Color(0xFF4FD1C5),
        syncing = Color(0xFF8AB4F8),
        warning = Color(0xFFF5CE6E),
        negative = Color(0xFFFF8A65),
    )

private val lightStatusColors =
    TandemStatusColors(
        positive = Color(0xFF00696E),
        syncing = Color(0xFF2F57C4),
        warning = Color(0xFF704800),
        negative = Color(0xFFC42B1C),
    )

private val darkCategoryColors =
    listOf(
        Color(0xFFA491FE),
        Color(0xFF7CC24A),
        Color(0xFFF16BCA),
        Color(0xFFE0A02E),
        Color(0xFF519EFD),
        Color(0xFFC9A77C),
        Color(0xFFD178F7),
        Color(0xFF6EB3B2),
    )

private val lightCategoryColors =
    listOf(
        Color(0xFF7853E1),
        Color(0xFF478809),
        Color(0xFFBC3099),
        Color(0xFF9E6A00),
        Color(0xFF2474CF),
        Color(0xFF8A6A4A),
        Color(0xFFA041C5),
        Color(0xFF3F8F8F),
    )

internal val LocalReduceMotion = staticCompositionLocalOf { false }

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
    val surfaces = tandemSurfaceColors(accent, dark).let { if (black) it.onPureBlack() else it }
    val selectedAccent = accent.colorFor(dark)
    val status = if (dark) darkStatusColors else lightStatusColors
    return TandemPalette(
        background = surfaces.background,
        surface = surfaces.surface,
        raised = surfaces.raised,
        border = surfaces.border,
        outline = surfaces.outline,
        text = surfaces.text,
        muted = surfaces.muted,
        accent = selectedAccent,
        accentContent = readableContentOn(selectedAccent),
        accentSurface = selectedAccent.copy(alpha = if (dark) 0.16f else 0.10f).compositeOver(surfaces.surface),
        positive = status.positive,
        syncing = status.syncing,
        warning = status.warning,
        negative = status.negative,
        negativeContent = readableContentOn(status.negative),
        categories = if (dark) darkCategoryColors else lightCategoryColors,
    )
}

private fun readableContentOn(background: Color): Color {
    val darkContent = Color(0xFF111827)
    val lightContent = Color.White
    return if (wcagContrastRatio(darkContent, background) >= wcagContrastRatio(lightContent, background)) darkContent else lightContent
}

private data class TandemSurfaceColors(
    val background: Color,
    val surface: Color,
    val raised: Color,
    val border: Color,
    val outline: Color,
    val text: Color,
    val muted: Color,
)

private fun TandemSurfaceColors.onPureBlack(): TandemSurfaceColors =
    copy(
        background = Color.Black,
        surface = Color(0xFF101116),
        raised = Color(0xFF1B1D25),
        border = Color(0xFF30313C),
        outline = Color(0xFF6A6978),
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
                outline = Color(0xFF657F6D),
                text = Color(0xFFEEF8F0),
                muted = Color(0xFFB6CCBA),
            )
        accent == AccentChoice.CLARITY ->
            TandemSurfaceColors(
                background = Color(0xFFF3F8F4),
                surface = Color.White,
                raised = Color(0xFFF7FBF8),
                border = Color(0xFFDCE8DF),
                outline = Color(0xFF819086),
                text = Color(0xFF19251D),
                muted = Color(0xFF526358),
            )
        accent == AccentChoice.WARM && isDark ->
            TandemSurfaceColors(
                background = Color(0xFF1A100B),
                surface = Color(0xFF291A12),
                raised = Color(0xFF39251B),
                border = Color(0xFF543A2B),
                outline = Color(0xFF8A6F5E),
                text = Color(0xFFFFF3EA),
                muted = Color(0xFFE3C6B0),
            )
        accent == AccentChoice.WARM ->
            TandemSurfaceColors(
                background = Color(0xFFFBF5F0),
                surface = Color.White,
                raised = Color(0xFFFFF9F5),
                border = Color(0xFFEBDDD2),
                outline = Color(0xFF9A897E),
                text = Color(0xFF2E251F),
                muted = Color(0xFF69564B),
            )
        isDark ->
            TandemSurfaceColors(
                background = Color(0xFF100F19),
                surface = Color(0xFF171622),
                raised = Color(0xFF211F31),
                border = Color(0xFF39364E),
                outline = Color(0xFF6E6B81),
                text = Color(0xFFF6F3FF),
                muted = Color(0xFFC2BDD2),
            )
        else ->
            TandemSurfaceColors(
                background = Color(0xFFF6F4FC),
                surface = Color.White,
                raised = Color(0xFFFAF9FF),
                border = Color(0xFFE2DFF0),
                outline = Color(0xFF8D899D),
                text = Color(0xFF1E1B2B),
                muted = Color(0xFF5B576D),
            )
    }

internal val tandemPalette: TandemPalette
    @Composable get() = LocalTandemPalette.current

internal val tandemReduceMotion: Boolean
    @Composable get() = LocalReduceMotion.current

@Composable
@Suppress("ktlint:standard:function-naming")
internal expect fun TandemSystemBars(isDark: Boolean)

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
