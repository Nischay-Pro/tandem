@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.format.APP_LANGUAGES
import app.tandemforactual.ui.core.format.LanguagePickerSheet
import app.tandemforactual.ui.core.format.LocalAppLanguage
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.formatRecoveryKey
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.InterfaceMode
import app.tandemforactual.ui.core.state.LocalPaneDetailRoute
import app.tandemforactual.ui.core.state.PlanningSyncStatus
import app.tandemforactual.ui.core.ui.ExternalConnectionsSection
import app.tandemforactual.ui.design.buttons.SecondaryButton
import app.tandemforactual.ui.design.feedback.AnimatedReveal
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.DashboardCard
import app.tandemforactual.ui.design.templates.PageGrid
import app.tandemforactual.ui.design.templates.PreviewNote
import app.tandemforactual.ui.design.templates.ToggleRow
import app.tandemforactual.ui.design.theme.TandemSpacing
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
import app.tandemforactual.ui.generated.resources.settings_export_backup
import app.tandemforactual.ui.generated.resources.settings_export_backup_detail
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
import app.tandemforactual.ui.generated.resources.settings_section_your_data
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
import com.composables.icons.lucide.FileDown
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.KeyRound
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
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SettingsScreen(
    state: AppState,
    navigation: AppNavigation,
    mobileSession: TandemMobileSession,
) {
    PageGrid(
        listOf(
            DashboardCard("settings-storage-error", fullWidth = true) {
                AnimatedReveal(visible = state.settingsStorageError != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                        PreviewNote(state.settingsStorageError.orEmpty())
                        SecondaryButton("Try again") { state.settingsStorageRetry += 1 }
                    }
                }
            },
            DashboardCard("settings-personalization") { PersonalizationSection(state, navigation) },
            DashboardCard("settings-budget") { BudgetSettingsSection(navigation) },
            DashboardCard("settings-connection") { ConnectionSection(navigation) },
            DashboardCard("settings-privacy") { PrivacySection(state, navigation, mobileSession) },
            DashboardCard("settings-external") { ExternalConnectionsSection(state, showAdvanced = true) },
            DashboardCard("settings-data") { YourDataSection(navigation) },
            DashboardCard("settings-support") { SupportSection(state, navigation) },
        ),
    )
}

/** Marks the row whose page is open in the pane beside the list. */
@Composable
private fun paneRowOptions(route: AppRoute) = ListRowOptions(selected = LocalPaneDetailRoute.current == route)

/** Home layout, interface mode, appearance and language. */
@Composable
private fun PersonalizationSection(
    state: AppState,
    navigation: AppNavigation,
) {
    SectionCard(stringResource(Res.string.settings_section_personalization)) {
        ListRow(
            stringResource(Res.string.home_customize),
            stringResource(Res.string.settings_personalize_detail),
            leading = RowLeading.Icon(Lucide.Sparkles),
            onClick = { navigation.open(AppRoute.HOME_LAYOUT) },
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
            options = paneRowOptions(AppRoute.APPEARANCE),
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
}

/** Budget preferences, payees and rules. */
@Composable
private fun BudgetSettingsSection(navigation: AppNavigation) {
    SectionCard(stringResource(Res.string.settings_section_budget)) {
        ListRow(
            stringResource(Res.string.settings_budget_preferences),
            stringResource(Res.string.settings_budget_preferences_detail),
            leading = RowLeading.Icon(Lucide.WalletCards),
            options = paneRowOptions(AppRoute.BUDGET_PREFERENCES),
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
}

/** Sync, server and notification settings. */
@Composable
private fun ConnectionSection(navigation: AppNavigation) {
    SectionCard(stringResource(Res.string.settings_section_connection)) {
        ListRow(
            stringResource(Res.string.settings_sync_preferences),
            stringResource(Res.string.settings_sync_preferences_detail),
            leading = RowLeading.Icon(Lucide.CloudSync),
            options = paneRowOptions(AppRoute.SYNC_PREFERENCES),
            onClick = { navigation.open(AppRoute.SYNC_PREFERENCES) },
        )
        DividerLine()
        ListRow(
            stringResource(Res.string.settings_server_device),
            stringResource(Res.string.settings_server_device_detail),
            leading = RowLeading.Icon(Lucide.Smartphone),
            options = paneRowOptions(AppRoute.SERVER_DEVICE),
            onClick = { navigation.open(AppRoute.SERVER_DEVICE) },
        )
        DividerLine()
        ListRow(
            stringResource(Res.string.settings_notifications),
            stringResource(Res.string.settings_notifications_detail),
            leading = RowLeading.Icon(Lucide.Bell),
            options = paneRowOptions(AppRoute.NOTIFICATIONS),
            onClick = { navigation.open(AppRoute.NOTIFICATIONS) },
        )
    }
}

/** The planning recovery key, security and the local copy. */
@Composable
private fun PrivacySection(
    state: AppState,
    navigation: AppNavigation,
    mobileSession: TandemMobileSession,
) {
    SectionCard(stringResource(Res.string.settings_section_privacy)) {
        if (!state.selectedBudgetEncrypted && mobileSession.planning.syncStatus != PlanningSyncStatus.OFF) {
            var showingRecoveryKey by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            ListRow(
                "Planning recovery key",
                "Add another device to your synced planning details",
                leading = RowLeading.Icon(Lucide.KeyRound),
                onClick = { showingRecoveryKey = true },
            )
            var keyVersion by remember { mutableStateOf(0) }
            RecoveryKeySheet(
                showingRecoveryKey,
                remember(state.selectedBudgetId, showingRecoveryKey, keyVersion) {
                    formatRecoveryKey(mobileSession.planning.keyring.recoveryKey(state.selectedBudgetId))
                },
                onUseKey = { key ->
                    scope.launch { mobileSession.planning.useRecoveryKey(state.selectedBudgetId, key) }
                    showingRecoveryKey = false
                },
                onReplaceKey = {
                    scope.launch { if (mobileSession.planning.replacePlanningKey(state.selectedBudgetId)) keyVersion++ }
                },
                onDismiss = { showingRecoveryKey = false },
            )
            DividerLine()
        }
        ListRow(
            stringResource(Res.string.settings_security),
            stringResource(Res.string.settings_security_detail),
            leading = RowLeading.Icon(Lucide.ShieldCheck),
            options = paneRowOptions(AppRoute.SECURITY_PRIVACY),
            onClick = { navigation.open(AppRoute.SECURITY_PRIVACY) },
        )
        DividerLine()
        ListRow(
            stringResource(Res.string.settings_local_data),
            stringResource(Res.string.settings_local_data_detail),
            leading = RowLeading.Icon(Lucide.Database),
            options = paneRowOptions(AppRoute.LOCAL_DATA),
            onClick = { navigation.open(AppRoute.LOCAL_DATA) },
        )
    }
}

/** Backups, recovery and exports of the budget and planning data. */
@Composable
private fun YourDataSection(navigation: AppNavigation) {
    SectionCard(stringResource(Res.string.settings_section_your_data)) {
        ListRow(
            stringResource(Res.string.settings_export_backup),
            stringResource(Res.string.settings_export_backup_detail),
            leading = RowLeading.Icon(Lucide.FileDown),
            options = paneRowOptions(AppRoute.EXPORT),
            onClick = { navigation.open(AppRoute.EXPORT) },
        )
    }
}

/** Getting started again and about Tandem. */
@Composable
private fun SupportSection(
    state: AppState,
    navigation: AppNavigation,
) {
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
            options = paneRowOptions(AppRoute.ABOUT),
            onClick = { navigation.open(AppRoute.ABOUT) },
        )
    }
}
