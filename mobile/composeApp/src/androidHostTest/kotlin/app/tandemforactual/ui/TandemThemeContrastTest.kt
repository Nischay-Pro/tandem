package app.tandemforactual.ui

import kotlin.test.Test
import kotlin.test.assertTrue

class TandemThemeContrastTest {
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
            }
        }
    }
}
