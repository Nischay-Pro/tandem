@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.components.AnimatedReveal
import app.tandemforactual.ui.components.DividerLine
import app.tandemforactual.ui.components.LabelText
import app.tandemforactual.ui.components.ListRow
import app.tandemforactual.ui.components.ListRowOptions
import app.tandemforactual.ui.components.RowLeading
import app.tandemforactual.ui.components.SecondaryButton
import app.tandemforactual.ui.components.SectionCard
import app.tandemforactual.ui.components.SelectableTile
import app.tandemforactual.ui.components.SurfaceCard
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.CloudSync
import com.composables.icons.lucide.Database
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.LayoutDashboard
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.PaintbrushVertical
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.Smartphone
import com.composables.icons.lucide.SunMoon
import com.composables.icons.lucide.Tags
import com.composables.icons.lucide.WalletCards
import com.composables.icons.lucide.Wallpaper

@Composable
internal fun SettingsScreen(
    state: AppState,
    navigation: AppNavigation,
) {
    PageColumn {
        Heading("Preferences", "Adjust appearance, sync, privacy, and app behavior.")
        AnimatedReveal(visible = state.settingsStorageError != null) {
            Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                PreviewNote(state.settingsStorageError.orEmpty())
                SecondaryButton("Retry settings storage") { state.settingsStorageRetry += 1 }
            }
        }
        SectionCard("Appearance") {
            ListRow(
                "Visual settings",
                "Theme, accent, and background",
                state.theme.label,
                leading = RowLeading.Icon(Lucide.Palette),
                onClick = { navigation.open(AppRoute.APPEARANCE) },
            )
            DividerLine()
            ListRow(
                "Home screen layout",
                "Choose how much detail to show",
                state.homeLayout,
                leading = RowLeading.Icon(Lucide.LayoutDashboard),
                onClick = { navigation.open(AppRoute.HOME_LAYOUT) },
            )
            DividerLine()
            ListRow(
                "Budget preferences",
                "Currency and planning choices",
                leading = RowLeading.Icon(Lucide.WalletCards),
                onClick = { navigation.open(AppRoute.BUDGET_PREFERENCES) },
            )
        }
        SectionCard("Connection", "Sync and paired device") {
            ListRow(
                "Sync preferences",
                "Refresh and network behavior",
                leading = RowLeading.Icon(Lucide.CloudSync),
                onClick = { navigation.open(AppRoute.SYNC_PREFERENCES) },
            )
            DividerLine()
            ListRow(
                "Server and paired device",
                "Connection details and access",
                leading = RowLeading.Icon(Lucide.Smartphone),
                onClick = { navigation.open(AppRoute.SERVER_DEVICE) },
            )
            DividerLine()
            ListRow(
                "Notifications",
                "Sync reminders and review alerts",
                leading = RowLeading.Icon(Lucide.Bell),
                onClick = { navigation.open(AppRoute.NOTIFICATIONS) },
            )
        }
        SectionCard("Privacy", "Device data and security") {
            ListRow(
                "Security and privacy",
                "Access and local data",
                leading = RowLeading.Icon(Lucide.ShieldCheck),
                onClick = { navigation.open(AppRoute.SECURITY_PRIVACY) },
            )
            DividerLine()
            ListRow(
                "Local data and storage",
                "Cached budget and pending edits",
                leading = RowLeading.Icon(Lucide.Database),
                onClick = { navigation.open(AppRoute.LOCAL_DATA) },
            )
        }
        SectionCard("Support", "Help and project details") {
            ListRow(
                "Budget rules",
                "Rules from Actual",
                leading = RowLeading.Icon(Lucide.Tags),
                onClick = { navigation.open(AppRoute.BUDGET_RULES) },
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
internal fun AppearanceScreen(
    state: AppState,
    navigation: AppNavigation,
) {
    PageColumn {
        Heading("Visual settings")
        SectionCard("Theme", "${state.theme.label} appearance", "") {
            ListRow(
                "Theme mode",
                "System, light, dark, or pure black",
                state.theme.label,
                leading = RowLeading.Icon(Lucide.SunMoon),
                onClick = { navigation.open(AppRoute.THEME_MODE) },
            )
        }
        SectionCard("Color", "${state.accent.label} accent", "") {
            ListRow(
                "Accent color",
                "Applies to every screen",
                state.accent.label,
                leading = RowLeading.Icon(Lucide.PaintbrushVertical),
                onClick = { navigation.open(AppRoute.ACCENT_COLOR) },
            )
        }
        SectionCard("Background") {
            ListRow(
                "Background and motion",
                "Subtle treatments and reduced motion",
                leading = RowLeading.Icon(Lucide.Wallpaper),
                onClick = { navigation.open(AppRoute.BACKGROUND_MOTION) },
            )
        }
    }
}

@Composable
internal fun ThemeModeScreen(state: AppState) {
    PageColumn {
        Heading("Color mode", "Choose the colors used across the app.")
        ThemeMode.entries.forEach { mode ->
            ChoiceRow(
                mode.label,
                when (mode) {
                    ThemeMode.SYSTEM -> "Follow this device’s light or dark setting"
                    ThemeMode.LIGHT -> "Light appearance"
                    ThemeMode.DARK -> "The default low-glare Tandem palette"
                    ThemeMode.PURE_BLACK -> "Black background"
                },
                selected = state.theme == mode,
                onClick = { state.theme = mode },
            )
        }
    }
}

@Composable
internal fun AccentColorScreen(state: AppState) {
    PageColumn {
        AccentChoice.entries.forEach { accent ->
            val palette = tandemPalette
            val selected = state.accent == accent
            SelectableTile(
                selected = selected,
                contentDescription = "${accent.label} accent",
                onSelect = { state.accent = accent },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(TandemShapes.large),
                selectedBackground = palette.surface,
                unselectedBackground = palette.surface,
                selectedBorder = palette.border,
                unselectedBorder = palette.border,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                ) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(accent.color))
                    Text(
                        accent.label,
                        modifier = Modifier.weight(1f),
                        color = palette.text,
                        fontSize = TandemTypography.body,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (selected) {
                        Icon(Lucide.Check, contentDescription = null, modifier = Modifier.size(20.dp), tint = palette.accent)
                    }
                }
            }
        }
    }
}

@Composable
internal fun BackgroundScreen(state: AppState) {
    PageColumn {
        listOf(
            "Soft gradient" to "A subtle vertical blend behind the cards",
            "Solid surface" to "A single steady canvas color",
            "Low-motion glow" to "A static accent glow behind the content",
        ).forEach { (style, description) ->
            ChoiceRow(style, description, selected = state.backgroundStyle == style, onClick = { state.backgroundStyle = style })
        }
        SectionCard("Motion") {
            ToggleRow(
                "Reduce motion",
                "Fewer animations",
                state.reduceMotion,
            ) { state.reduceMotionOverride = it }
            if (state.reduceMotionOverride != null) {
                SettingRow(
                    "Use device setting",
                    if (state.systemReduceMotionEnabled) {
                        "The device currently requests reduced motion"
                    } else {
                        "The device default allows motion"
                    },
                    onClick = { state.reduceMotionOverride = null },
                )
            }
        }
    }
}

@Composable
internal fun HomeLayoutScreen(state: AppState) {
    PageColumn {
        Heading("Budget layout", "Set the amount of detail shown on the budget page.")
        listOf(
            "Comfortable" to "More detail and context",
            "Compact" to "More categories visible at once",
            "Essentials" to "Keep upcoming bills near the top",
        ).forEach { (label, description) ->
            ChoiceRow(label, description, selected = state.homeLayout == label, onClick = { state.homeLayout = label })
        }
        SurfaceCard {
            Column(Modifier.padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                LabelText("Layout preview")
                Text(
                    when (state.homeLayout) {
                        "Compact" -> "Compact rows and tighter spacing"
                        "Essentials" -> "Upcoming bills and essentials appear first"
                        else -> "More space between categories"
                    },
                    color = tandemPalette.muted,
                    fontSize = TandemTypography.label,
                )
                if (state.homeLayout == "Essentials") {
                    ListRow(
                        "Upcoming bills",
                        "Due soon",
                        state.formatCurrency(12_000),
                        leading = RowLeading.Icon(financeIcon("Upcoming bills")),
                        options = ListRowOptions(dense = false),
                    )
                    DividerLine()
                }
                ListRow(
                    "Essentials",
                    "3 categories",
                    state.formatCurrency(232_000),
                    leading = RowLeading.Icon(financeIcon("Essentials")),
                    options = ListRowOptions(dense = state.homeLayout == "Compact"),
                )
                DividerLine()
                ListRow(
                    "Groceries",
                    "Available this month",
                    state.formatCurrency(50_000),
                    leading = RowLeading.Icon(financeIcon("Groceries")),
                    options = ListRowOptions(dense = state.homeLayout == "Compact"),
                )
            }
        }
    }
}
