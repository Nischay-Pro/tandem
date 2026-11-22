@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app

import androidx.compose.runtime.Composable
import app.tandemforactual.data.database.ActualBudgetFiles
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.store.LocalAlert
import app.tandemforactual.ui.app.shell.AppShell
import app.tandemforactual.ui.app.shell.ShellEnvironment
import app.tandemforactual.ui.core.security.DeviceAuthenticator
import app.tandemforactual.ui.core.session.CrashReports
import app.tandemforactual.ui.core.session.DeviceCredentialStore
import app.tandemforactual.ui.core.session.EncryptedBudgetVault
import app.tandemforactual.ui.core.session.NetworkKind
import app.tandemforactual.ui.core.session.SyncNetwork
import app.tandemforactual.ui.core.state.AppRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

@Composable
fun TandemApp(
    deviceName: String = "This device",
    database: TandemDatabase? = null,
    deviceCredentialStore: DeviceCredentialStore? = null,
    nowEpochMillis: () -> Long = { 0L },
    allowInsecurePrivateNetwork: Boolean = false,
    onBackgroundSyncSettingsChanged: ((enabled: Boolean, network: SyncNetwork, budgetId: String) -> Unit)? = null,
    onSystemAppearanceChanged: ((isDark: Boolean, followsSystem: Boolean) -> Unit)? = null,
    onRequestNotificationPermission: (() -> Unit)? = null,
    onOpenNotificationSettings: (() -> Unit)? = null,
    notificationPermissionEnabled: Boolean? = null,
    onToggleNotificationPermission: ((Boolean) -> Unit)? = null,
    onStartupComplete: (() -> Unit)? = null,
    appResumeEvents: Flow<Unit> = emptyFlow(),
    /** Whether the app is on screen; periodic sync runs only then. */
    appInForeground: StateFlow<Boolean> = MutableStateFlow(true),
    networkRestoredEvents: Flow<Unit> = emptyFlow(),
    deviceAuthenticator: DeviceAuthenticator? = null,
    encryptedBudgetVault: EncryptedBudgetVault? = null,
    currentNetwork: StateFlow<NetworkKind?> = MutableStateFlow(null),
    crashReports: CrashReports? = null,
    onShareText: ((String) -> Unit)? = null,
    windowHinge: StateFlow<WindowHinge?> = MutableStateFlow(null),
    screenshotScript: ScreenshotScript? = null,
    actualBudgetFiles: ActualBudgetFiles? = null,
    planningKeyStore: DeviceCredentialStore? = null,
    postLocalAlert: ((LocalAlert) -> Unit)? = null,
) {
    AppShell(
        ShellEnvironment(
            deviceName,
            database,
            deviceCredentialStore,
            nowEpochMillis,
            allowInsecurePrivateNetwork,
            onBackgroundSyncSettingsChanged,
            onSystemAppearanceChanged,
            onRequestNotificationPermission,
            onOpenNotificationSettings,
            notificationPermissionEnabled,
            onToggleNotificationPermission,
            onStartupComplete,
            appResumeEvents,
            appInForeground,
            networkRestoredEvents,
            deviceAuthenticator,
            encryptedBudgetVault,
            currentNetwork,
            crashReports,
            onShareText,
            windowHinge,
            screenshotScript,
            actualBudgetFiles,
            planningKeyStore,
            postLocalAlert,
        ),
    )
}

internal fun AppRoute.isTab(): Boolean =
    this == AppRoute.HOME ||
        this == AppRoute.BUDGET ||
        this == AppRoute.ACCOUNTS ||
        this == AppRoute.ACTIVITY ||
        this == AppRoute.REPORTS ||
        this == AppRoute.SETTINGS

internal fun AppRoute.isTransactionForm(): Boolean = this == AppRoute.NEW_TRANSACTION || this == AppRoute.EDIT_TRANSACTION

internal fun AppRoute.showsBottomNavigation(): Boolean =
    when (this) {
        AppRoute.STARTUP,
        AppRoute.WELCOME,
        AppRoute.SWITCH_BUDGET,
        AppRoute.SERVER_ADDRESS,
        AppRoute.SIGN_IN,
        AppRoute.BUDGET_CHOOSER,
        AppRoute.BUDGET_UNLOCK,
        AppRoute.FIRST_SYNC,
        AppRoute.PERSONALIZE,
        AppRoute.SETUP_COMPLETE,
        AppRoute.NEW_TRANSACTION,
        AppRoute.EDIT_TRANSACTION,
        AppRoute.CHOOSE_PAYEE,
        AppRoute.CHOOSE_CATEGORY,
        AppRoute.RETIREMENT_SETUP,
        -> false
        else -> true
    }

internal fun AppRoute.isReportDetail(): Boolean = this == AppRoute.REPORT_DETAIL

/** How often an open budget pulls changes from the server while the app is on screen. */
internal const val UNDO_NOTICE_MILLIS = 6_000L

internal const val BUDGET_PERIODIC_SYNC_INTERVAL_MS = 60_000L

internal fun <T> isBackPreviewStale(
    preview: T?,
    gestureOrigin: T?,
    current: T,
): Boolean = preview != null && gestureOrigin != current && preview != current
