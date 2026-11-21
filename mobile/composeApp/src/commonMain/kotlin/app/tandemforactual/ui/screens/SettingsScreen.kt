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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import app.tandemforactual.ui.TandemLayout
import app.tandemforactual.ui.TandemShapes
import app.tandemforactual.ui.TandemSpacing
import app.tandemforactual.ui.components.AnimatedReveal
import app.tandemforactual.ui.components.DividerLine
import app.tandemforactual.ui.components.ListRow
import app.tandemforactual.ui.components.RowLeading
import app.tandemforactual.ui.components.SecondaryButton
import app.tandemforactual.ui.components.SectionCard
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.home_customize
import app.tandemforactual.ui.generated.resources.mode_advanced
import app.tandemforactual.ui.generated.resources.mode_advanced_detail
import app.tandemforactual.ui.generated.resources.settings_about
import app.tandemforactual.ui.generated.resources.settings_about_detail
import app.tandemforactual.ui.generated.resources.settings_appearance
import app.tandemforactual.ui.generated.resources.settings_appearance_detail
import app.tandemforactual.ui.generated.resources.settings_budget_preferences
import app.tandemforactual.ui.generated.resources.settings_budget_preferences_detail
import app.tandemforactual.ui.generated.resources.settings_budget_rules
import app.tandemforactual.ui.generated.resources.settings_budget_rules_detail
import app.tandemforactual.ui.generated.resources.settings_getting_started
import app.tandemforactual.ui.generated.resources.settings_getting_started_detail
import app.tandemforactual.ui.generated.resources.settings_language
import app.tandemforactual.ui.generated.resources.settings_language_device
import app.tandemforactual.ui.generated.resources.settings_local_data
import app.tandemforactual.ui.generated.resources.settings_local_data_detail
import app.tandemforactual.ui.generated.resources.settings_manage_payees
import app.tandemforactual.ui.generated.resources.settings_manage_payees_detail
import app.tandemforactual.ui.generated.resources.settings_notifications
import app.tandemforactual.ui.generated.resources.settings_notifications_detail
import app.tandemforactual.ui.generated.resources.settings_personalize_detail
import app.tandemforactual.ui.generated.resources.settings_reset_personalization
import app.tandemforactual.ui.generated.resources.settings_reset_personalization_detail
import app.tandemforactual.ui.generated.resources.settings_section_budget
import app.tandemforactual.ui.generated.resources.settings_section_connection
import app.tandemforactual.ui.generated.resources.settings_section_personalization
import app.tandemforactual.ui.generated.resources.settings_section_privacy
import app.tandemforactual.ui.generated.resources.settings_section_support
import app.tandemforactual.ui.generated.resources.settings_security
import app.tandemforactual.ui.generated.resources.settings_security_detail
import app.tandemforactual.ui.generated.resources.settings_server_device
import app.tandemforactual.ui.generated.resources.settings_server_device_detail
import app.tandemforactual.ui.generated.resources.settings_sync_preferences
import app.tandemforactual.ui.generated.resources.settings_sync_preferences_detail
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.BookOpen
import com.composables.icons.lucide.CloudSync
import com.composables.icons.lucide.Database
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Languages
import com.composables.icons.lucide.ListChecks
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.Smartphone
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Tags
import com.composables.icons.lucide.WalletCards
import org.jetbrains.compose.resources.stringResource

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
        SectionCard(stringResource(Res.string.settings_section_personalization)) {
            ListRow(
                stringResource(Res.string.home_customize),
                stringResource(Res.string.settings_personalize_detail),
                leading = RowLeading.Icon(Lucide.Sparkles),
                onClick = { navigation.open(AppRoute.PERSONALIZE) },
            )
            DividerLine()
            ToggleRow(
                stringResource(Res.string.mode_advanced),
                stringResource(Res.string.mode_advanced_detail),
                (state.modeSwitchTarget ?: state.interfaceMode) == InterfaceMode.ADVANCED,
            ) { state.switchInterfaceMode(if (it) InterfaceMode.ADVANCED else InterfaceMode.MINIMAL) }
            if (state.experience != null) {
                DividerLine()
                ListRow(
                    stringResource(Res.string.settings_reset_personalization),
                    stringResource(Res.string.settings_reset_personalization_detail),
                    leading = RowLeading.Icon(Lucide.RotateCcw),
                    onClick = { state.resetExperience() },
                )
            }
            DividerLine()
            ListRow(
                stringResource(Res.string.settings_appearance),
                stringResource(Res.string.settings_appearance_detail),
                leading = RowLeading.Icon(Lucide.Palette),
                onClick = { navigation.open(AppRoute.APPEARANCE) },
            )
            LocalAppLanguage.current?.let { language ->
                var choosingLanguage by remember { mutableStateOf(false) }
                DividerLine()
                ListRow(
                    stringResource(Res.string.settings_language),
                    APP_LANGUAGES.firstOrNull { it.tag == language.current }?.nativeName
                        ?: stringResource(Res.string.settings_language_device),
                    leading = RowLeading.Icon(Lucide.Languages),
                    onClick = { choosingLanguage = true },
                )
                LanguagePickerSheet(choosingLanguage, language) { choosingLanguage = false }
            }
        }
        SectionCard(stringResource(Res.string.settings_section_budget)) {
            ListRow(
                stringResource(Res.string.settings_budget_preferences),
                stringResource(Res.string.settings_budget_preferences_detail),
                leading = RowLeading.Icon(Lucide.WalletCards),
                onClick = { navigation.open(AppRoute.BUDGET_PREFERENCES) },
            )
            DividerLine()
            ListRow(
                stringResource(Res.string.settings_manage_payees),
                stringResource(Res.string.settings_manage_payees_detail),
                leading = RowLeading.Icon(Lucide.Tags),
                onClick = { navigation.open(AppRoute.MANAGE_PAYEES) },
            )
            DividerLine()
            ListRow(
                stringResource(Res.string.settings_budget_rules),
                stringResource(Res.string.settings_budget_rules_detail),
                leading = RowLeading.Icon(Lucide.ListChecks),
                onClick = { navigation.open(AppRoute.BUDGET_RULES) },
            )
        }
        SectionCard(stringResource(Res.string.settings_section_connection)) {
            ListRow(
                stringResource(Res.string.settings_sync_preferences),
                stringResource(Res.string.settings_sync_preferences_detail),
                leading = RowLeading.Icon(Lucide.CloudSync),
                onClick = { navigation.open(AppRoute.SYNC_PREFERENCES) },
            )
            DividerLine()
            ListRow(
                stringResource(Res.string.settings_server_device),
                stringResource(Res.string.settings_server_device_detail),
                leading = RowLeading.Icon(Lucide.Smartphone),
                onClick = { navigation.open(AppRoute.SERVER_DEVICE) },
            )
            DividerLine()
            ListRow(
                stringResource(Res.string.settings_notifications),
                stringResource(Res.string.settings_notifications_detail),
                leading = RowLeading.Icon(Lucide.Bell),
                onClick = { navigation.open(AppRoute.NOTIFICATIONS) },
            )
        }
        SectionCard(stringResource(Res.string.settings_section_privacy)) {
            ListRow(
                stringResource(Res.string.settings_security),
                stringResource(Res.string.settings_security_detail),
                leading = RowLeading.Icon(Lucide.ShieldCheck),
                onClick = { navigation.open(AppRoute.SECURITY_PRIVACY) },
            )
            DividerLine()
            ListRow(
                stringResource(Res.string.settings_local_data),
                stringResource(Res.string.settings_local_data_detail),
                leading = RowLeading.Icon(Lucide.Database),
                onClick = { navigation.open(AppRoute.LOCAL_DATA) },
            )
        }
        SectionCard(stringResource(Res.string.settings_section_support)) {
            ListRow(
                stringResource(Res.string.settings_getting_started),
                stringResource(Res.string.settings_getting_started_detail),
                leading = RowLeading.Icon(Lucide.BookOpen),
                onClick = {
                    state.hasDismissedBudgetHint = false
                    navigation.selectTab(AppRoute.BUDGET)
                },
            )
            DividerLine()
            ListRow(
                stringResource(Res.string.settings_about),
                stringResource(Res.string.settings_about_detail),
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
