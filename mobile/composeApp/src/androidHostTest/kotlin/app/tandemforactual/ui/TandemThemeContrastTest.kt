package app.tandemforactual.ui

import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.ThemeMode
import app.tandemforactual.ui.design.theme.tandemPaletteFor
import app.tandemforactual.ui.design.theme.wcagContrastRatio
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TandemThemeContrastTest {
    @Test
    fun designPresetsHaveDistinctLightAndDarkSurfacePalettes() {
        val modernLight = tandemPaletteFor(ThemeMode.LIGHT, systemIsDark = false, accent = AccentChoice.ACTUAL_PURPLE)
        val clarityLight = tandemPaletteFor(ThemeMode.LIGHT, systemIsDark = false, accent = AccentChoice.CLARITY)
        val warmLight = tandemPaletteFor(ThemeMode.LIGHT, systemIsDark = false, accent = AccentChoice.WARM)
        val modernDark = tandemPaletteFor(ThemeMode.DARK, systemIsDark = true, accent = AccentChoice.ACTUAL_PURPLE)
        val clarityDark = tandemPaletteFor(ThemeMode.DARK, systemIsDark = true, accent = AccentChoice.CLARITY)
        val warmDark = tandemPaletteFor(ThemeMode.DARK, systemIsDark = true, accent = AccentChoice.WARM)

        assertNotEquals(modernLight.background, clarityLight.background)
        assertNotEquals(modernLight.background, warmLight.background)
        assertNotEquals(clarityLight.background, warmLight.background)
        assertNotEquals(modernDark.background, clarityDark.background)
        assertNotEquals(modernDark.background, warmDark.background)
        assertNotEquals(clarityDark.background, warmDark.background)
    }

    @Test
    fun textAccentsAndStatusColorsMeetWcagContrastInEveryThemeAndAccent() {
        val themes = ThemeMode.entries.flatMap { mode -> listOf(mode to false, mode to true) }
        themes.forEach { (mode, systemIsDark) ->
            AccentChoice.entries.forEach { accent ->
                val palette = tandemPaletteFor(mode, systemIsDark, accent)
                val textSurfaces = listOf(palette.background, palette.surface, palette.raised, palette.accentSurface)
                val checks =
                    listOf(
                        "primary text" to (palette.text to textSurfaces),
                        "muted text" to (palette.muted to textSurfaces),
                        "accent" to (palette.accent to textSurfaces),
                        "positive status" to (palette.positive to listOf(palette.background, palette.surface, palette.raised)),
                        "syncing status" to (palette.syncing to listOf(palette.background, palette.surface, palette.raised)),
                        "warning status" to (palette.warning to listOf(palette.background, palette.surface, palette.raised)),
                        "negative status" to (palette.negative to listOf(palette.background, palette.surface, palette.raised)),
                    )

                checks.forEach { (label, pair) ->
                    val (foreground, backgrounds) = pair
                    backgrounds.forEach { background ->
                        assertTrue(
                            wcagContrastRatio(foreground, background) >= 4.5f,
                            "$label contrast is below 4.5:1 in $mode with ${accent.label} on $background",
                        )
                    }
                }
                assertTrue(
                    wcagContrastRatio(palette.accentContent, palette.accent) >= 4.5f,
                    "Accent button text contrast is below 4.5:1 in $mode with ${accent.label}",
                )
                assertTrue(
                    wcagContrastRatio(palette.negativeContent, palette.negative) >= 4.5f,
                    "Text on the negative color is below 4.5:1 in $mode with ${accent.label}",
                )
            }
        }
    }
}
