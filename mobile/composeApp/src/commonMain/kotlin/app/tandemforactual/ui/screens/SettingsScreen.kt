@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.components.AnimatedReveal
import app.tandemforactual.ui.components.DividerLine
import app.tandemforactual.ui.components.ListRow
import app.tandemforactual.ui.components.RowLeading
import app.tandemforactual.ui.components.SecondaryButton
import app.tandemforactual.ui.components.SectionCard
import app.tandemforactual.ui.components.SurfaceCard
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.CloudSync
import com.composables.icons.lucide.Database
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.Smartphone
import com.composables.icons.lucide.Tags
import com.composables.icons.lucide.WalletCards

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
        SurfaceCard {
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
                "Rules from Actual",
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
                "Server and paired device",
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
        SectionCard("Accent color") {
            AccentChoice.entries.forEachIndexed { index, accent ->
                if (index > 0) DividerLine()
                AccentChoiceRow(accent, selected = state.accent == accent) { state.accent = accent }
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
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = title
                this.selected = selected
            }.padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
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
            .padding(4.dp),
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
    accent: AccentChoice,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "${accent.label} accent"
                this.selected = selected
            }.padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
    ) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(accent.color))
        Text(
            accent.label,
            modifier = Modifier.weight(1f),
            color = palette.text,
            fontSize = TandemTypography.body,
            fontWeight = FontWeight.Medium,
        )
        AppearanceSelectionIndicator(selected)
    }
}
