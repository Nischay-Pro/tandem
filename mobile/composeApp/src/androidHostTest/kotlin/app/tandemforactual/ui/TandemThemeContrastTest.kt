package app.tandemforactual.ui

import androidx.compose.ui.graphics.Color
import app.tandemforactual.ui.design.templates.appBackgroundBrush
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.ThemeMode
import app.tandemforactual.ui.design.theme.tandemPaletteFor
import app.tandemforactual.ui.design.theme.wcagContrastRatio
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TandemThemeContrastTest {
    private val everyPalette =
        ThemeMode.entries
            .flatMap { mode ->
                listOf(false, true).map { systemIsDark -> mode to systemIsDark }
            }.flatMap { (mode, systemIsDark) ->
                AccentChoice.entries.map { accent ->
                    "$mode/${if (systemIsDark) "dark" else "light"} system/$accent" to
                        tandemPaletteFor(mode, systemIsDark, accent)
                }
            }
    private val colourBlindVision = listOf(ColourVision.DEUTERANOPIA, ColourVision.PROTANOPIA)

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
                            "$label contrast is below 4.5:1 in $mode with $accent on $background",
                        )
                    }
                }
                assertTrue(
                    wcagContrastRatio(palette.accentContent, palette.accent) >= 4.5f,
                    "Accent button text contrast is below 4.5:1 in $mode with $accent",
                )
                assertTrue(
                    wcagContrastRatio(palette.negativeContent, palette.negative) >= 4.5f,
                    "Text on the negative color is below 4.5:1 in $mode with $accent",
                )
            }
        }
    }

    @Test
    fun moneyAndStatusColoursStayApartWithColourBlindness() {
        everyPalette.forEach { (name, palette) ->
            val pairs =
                listOf(
                    Triple("positive", "negative", 30.0) to (palette.positive to palette.negative),
                    Triple("positive", "warning", 30.0) to (palette.positive to palette.warning),
                    Triple("positive", "syncing", 25.0) to (palette.positive to palette.syncing),
                    Triple("syncing", "negative", 30.0) to (palette.syncing to palette.negative),
                    Triple("syncing", "warning", 30.0) to (palette.syncing to palette.warning),
                )
            pairs.forEach { (rule, colors) ->
                val (first, second, minimum) = rule
                (colourBlindVision + ColourVision.TYPICAL).forEach { vision ->
                    val difference = deltaE(colors.first, colors.second, vision)
                    assertTrue(difference >= minimum, "$first and $second are only ΔE $difference apart with $vision in $name")
                }
            }
            assertTrue(deltaE(palette.warning, palette.negative) >= 30.0, "warning and negative are too close in $name")
        }
    }

    @Test
    fun categoryColoursAreVisibleDistinctAndApartFromStatusColours() {
        everyPalette.forEach { (name, palette) ->
            val categories = palette.categories
            val reserved = listOf(palette.positive, palette.syncing, palette.warning, palette.negative, palette.muted)
            assertEquals(8, categories.size)
            categories.forEachIndexed { index, color ->
                listOf(palette.background, palette.surface, palette.raised).forEach { background ->
                    assertTrue(wcagContrastRatio(color, background) >= 3f, "category $index is below 3:1 in $name")
                }
                reserved.forEach { status ->
                    assertTrue(deltaE(color, status) >= 15.0, "category $index looks like a status colour in $name")
                }
                val next = categories[(index + 1) % categories.size]
                assertTrue(deltaE(color, next) >= 18.0, "categories $index and next are too close in $name")
                colourBlindVision.forEach { vision ->
                    assertTrue(deltaE(color, next, vision) >= 25.0, "categories $index and next merge with $vision in $name")
                }
            }
            categories.indices.forEach { first ->
                (first + 1 until categories.size).forEach { second ->
                    assertTrue(
                        deltaE(categories[first], categories[second]) >= 18.0,
                        "categories $first and $second are too close in $name",
                    )
                }
            }
        }
    }

    @Test
    fun outlinesMeetNonTextContrastOnEverySurface() {
        everyPalette.forEach { (name, palette) ->
            listOf(palette.background, palette.surface, palette.raised).forEach { background ->
                assertTrue(wcagContrastRatio(palette.outline, background) >= 3f, "outline is below 3:1 in $name")
            }
        }
    }

    @Test
    fun pureBlackStaysBlack() {
        AccentChoice.entries.forEach { accent ->
            val palette = tandemPaletteFor(ThemeMode.PURE_BLACK, systemIsDark = false, accent = accent)
            assertEquals(Color.Black, palette.background)
            assertEquals(
                appBackgroundBrush("Low-motion glow", palette, pureBlack = true),
                appBackgroundBrush("Solid surface", palette, pureBlack = true),
            )
        }
    }
}
