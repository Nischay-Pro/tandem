@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.TandemLayout
import app.tandemforactual.ui.TandemShapes
import app.tandemforactual.ui.TandemSpacing
import app.tandemforactual.ui.components.AnimatedReveal
import app.tandemforactual.ui.components.DividerLine
import app.tandemforactual.ui.components.ListRow
import app.tandemforactual.ui.components.RowLeading
import app.tandemforactual.ui.components.SecondaryButton
import app.tandemforactual.ui.components.SectionCard
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.BookOpen
import com.composables.icons.lucide.CloudSync
import com.composables.icons.lucide.Database
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.Smartphone
import com.composables.icons.lucide.Tags
import com.composables.icons.lucide.WalletCards

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
internal fun SettingsScreen(
    state: AppState,
    navigation: AppNavigation,
) {
    PageColumn {
        AnimatedReveal(visible = state.settingsStorageError != null) {
            Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                PreviewNote(state.settingsStorageError.orEmpty())
                SecondaryButton("Try again") { state.settingsStorageRetry += 1 }
            }
        }
        SectionCard("Personalization") {
            ListRow(
                "Appearance",
                leading = RowLeading.Icon(Lucide.Palette),
                onClick = { navigation.open(AppRoute.APPEARANCE) },
            )
        }
        SectionCard("Budget") {
            ListRow(
                "Budget preferences",
                leading = RowLeading.Icon(Lucide.WalletCards),
                onClick = { navigation.open(AppRoute.BUDGET_PREFERENCES) },
            )
            DividerLine()
            ListRow(
                "Manage payees",
                leading = RowLeading.Icon(Lucide.Tags),
                onClick = { navigation.open(AppRoute.MANAGE_PAYEES) },
            )
            DividerLine()
            ListRow(
                "Budget rules",
                leading = RowLeading.Icon(Lucide.Tags),
                onClick = { navigation.open(AppRoute.BUDGET_RULES) },
            )
        }
        SectionCard("Connection") {
            ListRow(
                "Sync preferences",
                leading = RowLeading.Icon(Lucide.CloudSync),
                onClick = { navigation.open(AppRoute.SYNC_PREFERENCES) },
            )
            DividerLine()
            ListRow(
                "Server and device",
                leading = RowLeading.Icon(Lucide.Smartphone),
                onClick = { navigation.open(AppRoute.SERVER_DEVICE) },
            )
            DividerLine()
            ListRow(
                "Notifications",
                leading = RowLeading.Icon(Lucide.Bell),
                onClick = { navigation.open(AppRoute.NOTIFICATIONS) },
            )
        }
        SectionCard("Privacy") {
            ListRow(
                "Security and privacy",
                "Read-only access and device protection",
                leading = RowLeading.Icon(Lucide.ShieldCheck),
                onClick = { navigation.open(AppRoute.SECURITY_PRIVACY) },
            )
            DividerLine()
            ListRow(
                "Local data and storage",
                "Cached records and pending edits",
                leading = RowLeading.Icon(Lucide.Database),
                onClick = { navigation.open(AppRoute.LOCAL_DATA) },
            )
        }
        SectionCard("Support") {
            ListRow(
                "Getting started",
                leading = RowLeading.Icon(Lucide.BookOpen),
                onClick = {
                    state.hasDismissedBudgetHint = false
                    navigation.selectTab(AppRoute.BUDGET)
                },
            )
            DividerLine()
            ListRow(
                "About, help, and licenses",
                "Version and open-source licenses",
                leading = RowLeading.Icon(Lucide.Info),
                onClick = { navigation.open(AppRoute.ABOUT) },
            )
        }
    }
}

@Composable
internal fun AppearanceScreen(state: AppState) {
    val isDark = isDarkTheme(state.theme, isSystemInDarkTheme())
    PageColumn {
        SectionCard("Theme") {
            ThemeMode.entries.forEachIndexed { index, mode ->
                if (index > 0) DividerLine()
                AppearanceChoiceRow(
                    mode.label,
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
                    title = accent.label,
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
