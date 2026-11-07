@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.ComposeUIViewController
import app.tandemforactual.data.database.openTandemDatabase
import app.tandemforactual.data.database.tandemDatabaseBuilder
import platform.UIKit.UIDevice
import platform.UIKit.UIViewController
import kotlin.time.Clock

@Suppress("ktlint:standard:function-naming")
fun MainViewController(
    onBackgroundSyncSettingsChanged: ((Boolean, Boolean, String) -> Unit)? = null,
    onSystemAppearanceChanged: ((Boolean, Boolean) -> Unit)? = null,
    onRequestNotificationPermission: (() -> Unit)? = null,
    onOpenNotificationSettings: (() -> Unit)? = null,
): UIViewController =
    createTandemViewController(
        openTandemDatabase(tandemDatabaseBuilder()),
        onBackgroundSyncSettingsChanged,
        onSystemAppearanceChanged,
        onRequestNotificationPermission,
        onOpenNotificationSettings,
    )

private fun createTandemViewController(
    database: app.tandemforactual.data.database.TandemDatabase,
    onBackgroundSyncSettingsChanged: ((Boolean, Boolean, String) -> Unit)?,
    onSystemAppearanceChanged: ((Boolean, Boolean) -> Unit)?,
    onRequestNotificationPermission: (() -> Unit)?,
    onOpenNotificationSettings: (() -> Unit)?,
): UIViewController {
    val syncEvents = IosSyncEventSource()
    return ComposeUIViewController {
        DisposableEffect(database, syncEvents) {
            onDispose {
                syncEvents.close()
                database.close()
            }
        }
        TandemApp(
            deviceName = UIDevice.currentDevice.name.ifBlank { "iPhone" },
            database = database,
            deviceCredentialStore = IosDeviceCredentialStore(),
            nowEpochMillis = { Clock.System.now().toEpochMilliseconds() },
            allowInsecurePrivateNetwork = true,
            onBackgroundSyncSettingsChanged = onBackgroundSyncSettingsChanged,
            onSystemAppearanceChanged = onSystemAppearanceChanged,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onOpenNotificationSettings = onOpenNotificationSettings,
            appResumeEvents = syncEvents.appResumeEvents,
            networkRestoredEvents = syncEvents.networkRestoredEvents,
        )
    }
}
