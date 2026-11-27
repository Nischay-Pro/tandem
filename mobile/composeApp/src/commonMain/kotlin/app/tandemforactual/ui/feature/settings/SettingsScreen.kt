@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
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
import app.tandemforactual.ui.core.ui.externalConnectionsStatus
import app.tandemforactual.ui.core.watch.WatchPairing
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
import app.tandemforactual.ui.generated.resources.automation_title
import app.tandemforactual.ui.generated.resources.home_customize
import app.tandemforactual.ui.generated.resources.mode_advanced
import app.tandemforactual.ui.generated.resources.mode_advanced_short
import app.tandemforactual.ui.generated.resources.settings_about
import app.tandemforactual.ui.generated.resources.settings_about_detail
import app.tandemforactual.ui.generated.resources.settings_appearance
import app.tandemforactual.ui.generated.resources.settings_budget_preferences
import app.tandemforactual.ui.generated.resources.settings_budget_preferences_detail
import app.tandemforactual.ui.generated.resources.settings_budget_rules
import app.tandemforactual.ui.generated.resources.settings_budget_rules_detail
import app.tandemforactual.ui.generated.resources.settings_connected_services
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
import app.tandemforactual.ui.generated.resources.settings_section_automation_connections
import app.tandemforactual.ui.generated.resources.settings_section_budget_sync
import app.tandemforactual.ui.generated.resources.settings_section_general
import app.tandemforactual.ui.generated.resources.settings_section_privacy
import app.tandemforactual.ui.generated.resources.settings_section_support
import app.tandemforactual.ui.generated.resources.settings_section_your_data
import app.tandemforactual.ui.generated.resources.settings_security
import app.tandemforactual.ui.generated.resources.settings_security_detail
import app.tandemforactual.ui.generated.resources.settings_server_device
import app.tandemforactual.ui.generated.resources.settings_server_device_detail
import app.tandemforactual.ui.generated.resources.settings_sync_preferences
import app.tandemforactual.ui.generated.resources.settings_sync_preferences_detail
import app.tandemforactual.ui.generated.resources.watch_title
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.BookOpen
import com.composables.icons.lucide.CloudSync
import com.composables.icons.lucide.Database
import com.composables.icons.lucide.FileDown
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.Languages
import com.composables.icons.lucide.ListChecks
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.SlidersHorizontal
import com.composables.icons.lucide.Smartphone
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Tags
import com.composables.icons.lucide.WalletCards
import com.composables.icons.lucide.Watch
import com.composables.icons.lucide.Zap
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SettingsScreen(
    state: AppState,
    navigation: AppNavigation,
    mobileSession: TandemMobileSession,
) {
    PageGrid(
        listOfNotNull(
            DashboardCard("settings-storage-error", fullWidth = true) {
                AnimatedReveal(visible = state.settingsStorageError != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                        PreviewNote(state.settingsStorageError.orEmpty())
                        SecondaryButton("Try again") { state.settingsStorageRetry += 1 }
                    }
                }
            },
            DashboardCard("settings-general") { GeneralSection(state, navigation) },
            DashboardCard("settings-budget") { BudgetSyncSection(state, navigation) },
            DashboardCard("settings-privacy") { PrivacySection(state, navigation, mobileSession) },
            DashboardCard("settings-data") { YourDataSection(navigation) },
            DashboardCard("settings-automation") { AutomationConnectionsSection(state, navigation) },
            DashboardCard("settings-support") { SupportSection(state, navigation) },
        ),
    )
}

/** A row that opens a settings page, marked while that page shows in the pane beside the list. */
@Composable
private fun SettingsPageRow(
    title: String,
    status: String,
    icon: ImageVector,
    route: AppRoute,
    navigation: AppNavigation,
) {
    ListRow(
        title,
        status,
        leading = RowLeading.Icon(icon),
        options = ListRowOptions(selected = LocalPaneDetailRoute.current == route, subtitleMaxLines = 1),
        onClick = { navigation.open(route) },
    )
}

/** Appearance, language, notifications and how Home and the app are laid out. */
@Composable
private fun GeneralSection(
    state: AppState,
    navigation: AppNavigation,
) {
    SectionCard(stringResource(Res.string.settings_section_general)) {
        SettingsPageRow(
            stringResource(Res.string.settings_appearance),
            stringResource(state.theme.labelRes) + " · " + stringResource(state.accent.labelRes),
            Lucide.Palette,
            AppRoute.APPEARANCE,
            navigation,
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
        DividerLine()
        SettingsPageRow(
            stringResource(Res.string.settings_notifications),
            stringResource(Res.string.settings_notifications_detail),
            Lucide.Bell,
            AppRoute.NOTIFICATIONS,
            navigation,
        )
        DividerLine()
        ListRow(
            stringResource(Res.string.home_customize),
            stringResource(Res.string.settings_personalize_detail),
            leading = RowLeading.Icon(Lucide.Sparkles),
            onClick = { navigation.open(AppRoute.HOME_LAYOUT) },
        )
        DividerLine()
        ToggleRow(
            stringResource(Res.string.mode_advanced),
            stringResource(Res.string.mode_advanced_short),
            (state.modeSwitchTarget ?: state.interfaceMode) == InterfaceMode.ADVANCED,
            leadingIcon = Lucide.SlidersHorizontal,
        ) { state.switchInterfaceMode(if (it) InterfaceMode.ADVANCED else InterfaceMode.MINIMAL) }
        if (state.experience != null) {
            DividerLine()
            ListRow(
                stringResource(Res.string.settings_reset_personalization),
                stringResource(Res.string.settings_reset_personalization_detail),
                leading = RowLeading.Icon(Lucide.RotateCcw),
                options = ListRowOptions(showChevron = false),
                onClick = { state.resetExperience() },
            )
        }
    }
}

/** Budget preferences, payees, rules, sync and the server. */
@Composable
private fun BudgetSyncSection(
    state: AppState,
    navigation: AppNavigation,
) {
    SectionCard(stringResource(Res.string.settings_section_budget_sync)) {
        SettingsPageRow(
            stringResource(Res.string.settings_budget_preferences),
            stringResource(Res.string.settings_budget_preferences_detail),
            Lucide.WalletCards,
            AppRoute.BUDGET_PREFERENCES,
            navigation,
        )
        DividerLine()
        ListRow(
            stringResource(Res.string.settings_manage_payees),
            stringResource(Res.string.settings_manage_payees_detail),
            leading = RowLeading.Icon(Lucide.Tags),
            options = ListRowOptions(subtitleMaxLines = 1),
            onClick = { navigation.open(AppRoute.MANAGE_PAYEES) },
        )
        DividerLine()
        ListRow(
            stringResource(Res.string.settings_budget_rules),
            stringResource(Res.string.settings_budget_rules_detail),
            leading = RowLeading.Icon(Lucide.ListChecks),
            options = ListRowOptions(subtitleMaxLines = 1),
            onClick = { navigation.open(AppRoute.BUDGET_RULES) },
        )
        DividerLine()
        SettingsPageRow(
            stringResource(Res.string.settings_sync_preferences),
            stringResource(Res.string.settings_sync_preferences_detail),
            Lucide.CloudSync,
            AppRoute.SYNC_PREFERENCES,
            navigation,
        )
        DividerLine()
        SettingsPageRow(
            stringResource(Res.string.settings_server_device),
            serverHost(state.serverUrl) ?: stringResource(Res.string.settings_server_device_detail),
            Lucide.Smartphone,
            AppRoute.SERVER_DEVICE,
            navigation,
        )
    }
}

internal fun serverHost(url: String): String? =
    url
        .substringAfter("://")
        .substringBefore('/')
        .takeIf { it.isNotBlank() }

/** Security and the planning recovery key. */
@Composable
private fun PrivacySection(
    state: AppState,
    navigation: AppNavigation,
    mobileSession: TandemMobileSession,
) {
    SectionCard(stringResource(Res.string.settings_section_privacy)) {
        SettingsPageRow(
            stringResource(Res.string.settings_security),
            stringResource(Res.string.settings_security_detail),
            Lucide.ShieldCheck,
            AppRoute.SECURITY_PRIVACY,
            navigation,
        )
        if (!state.selectedBudgetEncrypted && mobileSession.planning.syncStatus != PlanningSyncStatus.OFF) {
            DividerLine()
            PlanningRecoveryKeyRow(state, mobileSession)
        }
    }
}

@Composable
private fun PlanningRecoveryKeyRow(
    state: AppState,
    mobileSession: TandemMobileSession,
) {
    var showingRecoveryKey by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    ListRow(
        "Planning recovery key",
        "Add another device to your synced planning",
        leading = RowLeading.Icon(Lucide.KeyRound),
        options = ListRowOptions(subtitleMaxLines = 1),
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
}

/** Backups, exports and the copy kept on this device. */
@Composable
private fun YourDataSection(navigation: AppNavigation) {
    SectionCard(stringResource(Res.string.settings_section_your_data)) {
        SettingsPageRow(
            stringResource(Res.string.settings_export_backup),
            stringResource(Res.string.settings_export_backup_detail),
            Lucide.FileDown,
            AppRoute.EXPORT,
            navigation,
        )
        DividerLine()
        SettingsPageRow(
            stringResource(Res.string.settings_local_data),
            stringResource(Res.string.settings_local_data_detail),
            Lucide.Database,
            AppRoute.LOCAL_DATA,
            navigation,
        )
    }
}

/** Automation apps and the opt-in services Tandem can contact, each with what is on. */
@Composable
private fun AutomationConnectionsSection(
    state: AppState,
    navigation: AppNavigation,
) {
    SectionCard(stringResource(Res.string.settings_section_automation_connections)) {
        SettingsPageRow(
            stringResource(Res.string.automation_title),
            automationStatus(state),
            Lucide.Zap,
            AppRoute.AUTOMATION,
            navigation,
        )
        DividerLine()
        SettingsPageRow(
            stringResource(Res.string.settings_connected_services),
            externalConnectionsStatus(state),
            Lucide.Globe,
            AppRoute.CONNECTED_SERVICES,
            navigation,
        )
        val watchPaired by WatchPairing.available.collectAsState()
        if (watchPaired) {
            DividerLine()
            SettingsPageRow(
                stringResource(Res.string.watch_title),
                watchStatus(state),
                Lucide.Watch,
                AppRoute.WATCH,
                navigation,
            )
        }
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
            options = ListRowOptions(subtitleMaxLines = 1),
            onClick = {
                state.hasDismissedBudgetHint = false
                navigation.selectTab(AppRoute.BUDGET)
            },
        )
        DividerLine()
        SettingsPageRow(
            stringResource(Res.string.settings_about),
            stringResource(Res.string.settings_about_detail),
            Lucide.Info,
            AppRoute.ABOUT,
            navigation,
        )
    }
}
