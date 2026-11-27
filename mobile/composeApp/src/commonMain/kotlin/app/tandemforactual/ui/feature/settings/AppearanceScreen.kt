@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.homeLayout
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.templates.ToggleRow
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemLayout
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.ThemeMode
import app.tandemforactual.ui.design.theme.colorFor
import app.tandemforactual.ui.design.theme.isDarkTheme
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemPaletteFor
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.accent_clarity_green
import app.tandemforactual.ui.generated.resources.accent_cobalt
import app.tandemforactual.ui.generated.resources.accent_lagoon
import app.tandemforactual.ui.generated.resources.accent_orchid
import app.tandemforactual.ui.generated.resources.accent_rose
import app.tandemforactual.ui.generated.resources.accent_saffron
import app.tandemforactual.ui.generated.resources.accent_tandem_violet
import app.tandemforactual.ui.generated.resources.accent_warm_terracotta
import app.tandemforactual.ui.generated.resources.theme_mode_dark
import app.tandemforactual.ui.generated.resources.theme_mode_light
import app.tandemforactual.ui.generated.resources.theme_mode_pure_black
import app.tandemforactual.ui.generated.resources.theme_mode_system
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AppearanceScreen(state: AppState) {
    val isDark = isDarkTheme(state.theme, isSystemInDarkTheme())
    PageColumn {
        SectionCard("Theme") {
            ThemeMode.entries.forEachIndexed { index, mode ->
                if (index > 0) DividerLine()
                AppearanceChoiceRow(
                    stringResource(mode.labelRes),
                    selected = state.theme == mode,
                    onClick = { state.theme = mode },
                )
            }
        }
        SectionCard("Color theme", "Choose the palette used across Tandem.") {
            appearancePalettePresets.forEachIndexed { index, preset ->
                if (index > 0) DividerLine()
                AccentChoiceRow(
                    title = preset.title,
                    description = preset.description,
                    accent = preset.accent,
                    isDark = isDark,
                    selected = state.accent == preset.accent,
                ) { state.accent = preset.accent }
            }
        }
        val presetAccents = appearancePalettePresets.mapTo(mutableSetOf()) { it.accent }
        val additionalAccents = AccentChoice.entries.filterNot { it in presetAccents }
        SectionCard("More accent colors", "Use a different accent with the Modern surfaces.") {
            additionalAccents.forEachIndexed { index, accent ->
                if (index > 0) DividerLine()
                AccentChoiceRow(
                    title = stringResource(accent.labelRes),
                    description = null,
                    accent = accent,
                    isDark = isDark,
                    selected = state.accent == accent,
                ) { state.accent = accent }
            }
        }
        SectionCard("Background and motion") {
            listOf("Soft gradient", "Solid surface", "Low-motion glow").forEachIndexed { index, style ->
                if (index > 0) DividerLine()
                AppearanceChoiceRow(
                    style,
                    selected = state.backgroundStyle == style,
                    onClick = { state.backgroundStyle = style },
                )
            }
            DividerLine()
            ToggleRow(
                "Reduce motion",
                checked = state.reduceMotion,
            ) { state.reduceMotionOverride = it }
            if (state.reduceMotionOverride != null) {
                DividerLine()
                ListRow(
                    "Use device setting",
                    if (state.systemReduceMotionEnabled) "Reduced motion is on" else "Reduced motion is off",
                    onClick = { state.reduceMotionOverride = null },
                )
            }
        }
        SectionCard("Budget layout") {
            listOf("Comfortable", "Compact", "Essentials").forEachIndexed { index, layout ->
                if (index > 0) DividerLine()
                AppearanceChoiceRow(
                    layout,
                    selected = state.homeLayout == layout,
                    onClick = { state.homeLayout = layout },
                )
            }
        }
    }
}

private val ThemeMode.labelRes: StringResource
    get() =
        when (this) {
            ThemeMode.SYSTEM -> Res.string.theme_mode_system
            ThemeMode.LIGHT -> Res.string.theme_mode_light
            ThemeMode.DARK -> Res.string.theme_mode_dark
            ThemeMode.PURE_BLACK -> Res.string.theme_mode_pure_black
        }

private val AccentChoice.labelRes: StringResource
    get() =
        when (this) {
            AccentChoice.ACTUAL_PURPLE -> Res.string.accent_tandem_violet
            AccentChoice.CLARITY -> Res.string.accent_clarity_green
            AccentChoice.WARM -> Res.string.accent_warm_terracotta
            AccentChoice.TEAL -> Res.string.accent_lagoon
            AccentChoice.BLUE -> Res.string.accent_cobalt
            AccentChoice.VIOLET -> Res.string.accent_orchid
            AccentChoice.AMBER -> Res.string.accent_saffron
            AccentChoice.ROSE -> Res.string.accent_rose
        }

private data class AppearancePalettePreset(
    val title: String,
    val description: String,
    val accent: AccentChoice,
)

private val appearancePalettePresets =
    listOf(
        AppearancePalettePreset("Modern", "Tandem violet with soft lavender surfaces.", AccentChoice.ACTUAL_PURPLE),
        AppearancePalettePreset("Clarity", "Calm green for everyday budgeting.", AccentChoice.CLARITY),
        AppearancePalettePreset("Warm", "Terracotta tones with a softer feel.", AccentChoice.WARM),
    )

@Composable
private fun AppearanceChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = TandemLayout.listRowMinHeight)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = title
                this.selected = selected
            }.padding(horizontal = TandemLayout.listRowHorizontalInset, vertical = TandemLayout.listRowVerticalInset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemLayout.listRowGap),
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            color = palette.text,
            fontSize = TandemTypography.body,
            fontWeight = FontWeight.Medium,
        )
        AppearanceSelectionIndicator(selected)
    }
}

@Composable
private fun AppearanceSelectionIndicator(selected: Boolean) {
    val palette = tandemPalette
    Box(
        Modifier
            .size(22.dp)
            .border(2.dp, if (selected) palette.accent else palette.muted, CircleShape)
            .padding(TandemSpacing.extraSmall),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(if (selected) palette.accent else palette.surface),
        )
    }
}

@Composable
private fun AccentChoiceRow(
    title: String,
    description: String?,
    accent: AccentChoice,
    isDark: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    val previewPalette = tandemPaletteFor(if (isDark) ThemeMode.DARK else ThemeMode.LIGHT, isDark, accent)
    val rowShape = RoundedCornerShape(TandemShapes.medium)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (description == null) TandemLayout.listRowMinHeight else 76.dp)
            .clip(rowShape)
            .background(if (selected) previewPalette.accentSurface else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "$title color theme"
                this.selected = selected
            }.padding(horizontal = TandemLayout.listRowHorizontalInset, vertical = TandemLayout.listRowVerticalInset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemLayout.listRowGap),
    ) {
        if (description == null) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(accent.colorFor(isDark)))
        } else {
            Row(
                Modifier
                    .clip(RoundedCornerShape(TandemShapes.small))
                    .background(previewPalette.surface)
                    .border(1.dp, previewPalette.border, RoundedCornerShape(TandemShapes.small))
                    .padding(TandemSpacing.extraSmall),
                horizontalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
            ) {
                Box(Modifier.size(14.dp).clip(RoundedCornerShape(TandemShapes.small)).background(previewPalette.accent))
                Box(Modifier.size(14.dp).clip(RoundedCornerShape(TandemShapes.small)).background(previewPalette.raised))
                Box(Modifier.size(14.dp).clip(RoundedCornerShape(TandemShapes.small)).background(previewPalette.border))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
            Text(
                title,
                color = palette.text,
                fontSize = TandemTypography.body,
                fontWeight = FontWeight.Medium,
            )
            if (description != null) {
                Text(
                    description,
                    color = palette.muted,
                    fontSize = TandemTypography.caption,
                )
            }
        }
        AppearanceSelectionIndicator(selected)
    }
}
