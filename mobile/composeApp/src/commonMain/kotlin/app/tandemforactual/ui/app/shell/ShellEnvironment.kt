package app.tandemforactual.ui.app.shell

import app.tandemforactual.data.database.ActualBudgetFiles
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.store.LocalAlert
import app.tandemforactual.ui.app.ScreenshotScript
import app.tandemforactual.ui.app.WindowHinge
import app.tandemforactual.ui.core.security.DeviceAuthenticator
import app.tandemforactual.ui.core.session.CrashReports
import app.tandemforactual.ui.core.session.DeviceCredentialStore
import app.tandemforactual.ui.core.session.EncryptedBudgetVault
import app.tandemforactual.ui.core.session.NetworkKind
import app.tandemforactual.ui.core.session.SyncNetwork
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** The platform hooks and storage the host passes to the app shell. */
internal class ShellEnvironment(
    val deviceName: String,
    val database: TandemDatabase?,
    val deviceCredentialStore: DeviceCredentialStore?,
    val nowEpochMillis: () -> Long,
    val allowInsecurePrivateNetwork: Boolean,
    val onBackgroundSyncSettingsChanged: ((enabled: Boolean, network: SyncNetwork, budgetId: String) -> Unit)?,
    val onSystemAppearanceChanged: ((isDark: Boolean, followsSystem: Boolean) -> Unit)?,
    val onRequestNotificationPermission: (() -> Unit)?,
    val onOpenNotificationSettings: (() -> Unit)?,
    val notificationPermissionEnabled: Boolean?,
    val onToggleNotificationPermission: ((Boolean) -> Unit)?,
    val onStartupComplete: (() -> Unit)?,
    val appResumeEvents: Flow<Unit>,
    val appInForeground: StateFlow<Boolean>,
    val networkRestoredEvents: Flow<Unit>,
    val deviceAuthenticator: DeviceAuthenticator?,
    val encryptedBudgetVault: EncryptedBudgetVault?,
    val currentNetwork: StateFlow<NetworkKind?>,
    val crashReports: CrashReports?,
    val onShareText: ((String) -> Unit)?,
    val windowHinge: StateFlow<WindowHinge?>,
    val screenshotScript: ScreenshotScript?,
    val actualBudgetFiles: ActualBudgetFiles?,
    val planningKeyStore: DeviceCredentialStore?,
    val postLocalAlert: ((LocalAlert) -> Unit)?,
)
