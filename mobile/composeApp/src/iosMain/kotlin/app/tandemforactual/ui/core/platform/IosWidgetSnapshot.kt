package app.tandemforactual.ui.core.platform

import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.widgetSnapshotJson
import app.tandemforactual.ui.core.format.formatBudgetCurrency
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Swift saves this summary to the App Group for Tandem's widgets after a sync and when the app leaves the screen. */
fun readIosWidgetSnapshot(completion: (String?) -> Unit) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    scope.launch {
        val json =
            runCatching {
                val database = iosTandemDatabase
                widgetSnapshotJson(
                    LocalBudgetRepository(database) { Clock.System.now().toEpochMilliseconds() },
                    AppPreferencesStore(database).read(),
                    platformTodayIsoDate(),
                    ::formatBudgetCurrency,
                )
            }.getOrNull()
        runCatching { completion(json) }
        scope.cancel()
    }
}
