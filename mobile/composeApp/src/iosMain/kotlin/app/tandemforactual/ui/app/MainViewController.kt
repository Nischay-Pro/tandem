@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import app.tandemforactual.ui.core.platform.IosCrashReports
import app.tandemforactual.ui.core.platform.IosDeviceAuthenticator
import app.tandemforactual.ui.core.platform.IosDeviceCredentialStore
import app.tandemforactual.ui.core.platform.IosEncryptedBudgetVault
import app.tandemforactual.ui.core.platform.IosPrivacyCover
import app.tandemforactual.ui.core.platform.IosSyncEventSource
import app.tandemforactual.ui.core.platform.IosWatch
import app.tandemforactual.ui.core.platform.LocalWatchLink
import app.tandemforactual.ui.core.platform.SecurityIosKeychainClient
import app.tandemforactual.ui.core.platform.iosTandemDatabase
import app.tandemforactual.ui.core.platform.postIosLocalAlert
import app.tandemforactual.ui.core.platform.shareTextOnIos
import app.tandemforactual.ui.core.session.BackupSecrets
import app.tandemforactual.ui.core.session.LocalBackupSecrets
import app.tandemforactual.ui.core.session.LocalBankIcons
import app.tandemforactual.ui.core.session.SyncNetwork
import app.tandemforactual.ui.core.session.bankIcons
import kotlin.native.Platform
import kotlin.time.Clock
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIDevice
import platform.UIKit.UIView
import platform.UIKit.UIViewController

@Suppress("ktlint:standard:function-naming")
fun MainViewController(
    onBackgroundSyncSettingsChanged: ((Boolean, Boolean, String) -> Unit)? = null,
    onSystemAppearanceChanged: ((Boolean, Boolean) -> Unit)? = null,
    onRequestNotificationPermission: (() -> Unit)? = null,
    onOpenNotificationSettings: (() -> Unit)? = null,
    windowControlsInset: ((UIView) -> Double)? = null,
): UIViewController =
    createTandemViewController(
        iosTandemDatabase,
        onBackgroundSyncSettingsChanged,
        onSystemAppearanceChanged,
        onRequestNotificationPermission,
        onOpenNotificationSettings,
        windowControlsInset,
    )

private fun createTandemViewController(
    database: app.tandemforactual.data.database.TandemDatabase,
    onBackgroundSyncSettingsChanged: ((Boolean, Boolean, String) -> Unit)?,
    onSystemAppearanceChanged: ((Boolean, Boolean) -> Unit)?,
    onRequestNotificationPermission: (() -> Unit)?,
    onOpenNotificationSettings: (() -> Unit)?,
    windowControlsInset: ((UIView) -> Double)?,
): UIViewController {
    val syncEvents = IosSyncEventSource()
    var privacyCover: IosPrivacyCover? = null
    val controller =
        ComposeUIViewController {
            DisposableEffect(database, syncEvents) {
                onDispose {
                    syncEvents.close()
                    privacyCover?.close()
                }
            }
            val bankIcons =
                remember {
                    NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
                        .firstOrNull()
                        ?.let { bankIcons("$it/bank-icons") { Clock.System.now().toEpochMilliseconds() } }
                }
            val backupSecrets =
                remember {
                    BackupSecrets(
                        IosDeviceCredentialStore(SecurityIosKeychainClient("backup-passphrase")),
                        IosDeviceCredentialStore(SecurityIosKeychainClient("backup-webdav")),
                    )
                }
            CompositionLocalProvider(
                LocalBackupSecrets provides backupSecrets,
                LocalBankIcons provides bankIcons,
                LocalWindowControlsInset provides rememberWindowControlsInset(windowControlsInset),
                LocalWatchLink provides IosWatch,
            ) {
                TandemApp(
                    actualBudgetFiles =
                        remember {
                            app.tandemforactual.data.database
                                .IosActualBudgetFiles()
                        },
                    planningKeyStore = remember { IosDeviceCredentialStore(SecurityIosKeychainClient("planning-keys")) },
                    deviceName = UIDevice.currentDevice.name.ifBlank { UIDevice.currentDevice.model },
                    database = database,
                    deviceCredentialStore = remember { IosDeviceCredentialStore() },
                    deviceAuthenticator = remember { IosDeviceAuthenticator() },
                    encryptedBudgetVault = remember { IosEncryptedBudgetVault() },
                    crashReports = remember { IosCrashReports().also(IosCrashReports::install) },
                    onShareText = ::shareTextOnIos,
                    nowEpochMillis = { Clock.System.now().toEpochMilliseconds() },
                    allowInsecurePrivateNetwork = true,
                    onBackgroundSyncSettingsChanged =
                        onBackgroundSyncSettingsChanged?.let { changed ->
                            { enabled, network, budgetId -> changed(enabled, network == SyncNetwork.WIFI, budgetId) }
                        },
                    onSystemAppearanceChanged = onSystemAppearanceChanged,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onOpenNotificationSettings = onOpenNotificationSettings,
                    appInForeground = syncEvents.appInForeground,
                    appResumeEvents = syncEvents.appResumeEvents,
                    networkRestoredEvents = syncEvents.networkRestoredEvents,
                    screenshotScript = remember { iosScreenshotScript() },
                    postLocalAlert = ::postIosLocalAlert,
                    openSignInPage = ::openSignInPageOnIos,
                )
            }
        }
    privacyCover = IosPrivacyCover(controller)
    return controller
}

/** Re-reads how far the window controls reach whenever the window resizes, once now and again after UIKit lays out. */
@Composable
private fun rememberWindowControlsInset(measure: ((UIView) -> Double)?): Dp {
    val controller = LocalUIViewController.current
    val windowSize = LocalWindowInfo.current.containerSize
    val inset by produceState(0.dp, measure, windowSize) {
        if (measure == null) return@produceState
        repeat(2) {
            value = measure(controller.view).dp
            withFrameNanos { }
        }
    }
    return inset
}

@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
private fun iosScreenshotScript(): ScreenshotScript? {
    if (!Platform.isDebugBinary) return null
    val environment =
        NSProcessInfo.processInfo.environment.entries
            .associate { (key, value) -> key.toString() to value?.toString() }
    return ScreenshotScript.from(environment)
}
