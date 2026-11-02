@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.ui.window.ComposeUIViewController
import app.tandemforactual.data.database.openTandemDatabase
import app.tandemforactual.data.database.tandemDatabaseBuilder
import kotlin.time.Clock

@Suppress("ktlint:standard:function-naming")
fun MainViewController() = createTandemViewController(openTandemDatabase(tandemDatabaseBuilder()))

private fun createTandemViewController(database: app.tandemforactual.data.database.TandemDatabase) =
    ComposeUIViewController {
        TandemApp(
            database = database,
            deviceCredentialStore = IosDeviceCredentialStore(),
            nowEpochMillis = { Clock.System.now().toEpochMilliseconds() },
        )
    }
