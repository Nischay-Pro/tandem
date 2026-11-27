package app.tandemforactual.ui.core.platform

import app.tandemforactual.data.database.TandemDatabase
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
                val preferences = AppPreferencesStore(database).read()
                val vault = readableVault(preferences)
                try {
                    widgetSnapshotJson(
                        LocalBudgetRepository(vault ?: database) { Clock.System.now().toEpochMilliseconds() },
                        if (vault == null) preferences else preferences - "selectedBudgetEncrypted",
                        platformTodayIsoDate(),
                        ::formatBudgetCurrency,
                    )
                } finally {
                    vault?.close()
                }
            }.getOrNull()
        runCatching { completion(json) }
        scope.cancel()
    }
}

/** An encrypted budget's vault, opened only when the person let widgets read it while it's locked; otherwise widgets say Locked. */
private suspend fun readableVault(preferences: Map<String, String>): TandemDatabase? {
    if (preferences["selectedBudgetEncrypted"] != "true") return null
    val budgetId = preferences["selectedBudgetId"]?.takeIf(String::isNotBlank) ?: return null
    return iosReadablePassword(budgetId)?.let { IosEncryptedBudgetVault().open(budgetId, it) }
}
