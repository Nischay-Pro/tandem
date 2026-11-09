package app.tandemforactual.data.store

import androidx.room3.withWriteTransaction
import app.tandemforactual.data.database.AppPreferenceEntity
import app.tandemforactual.data.database.TandemDatabase

/** Stores non-sensitive app preferences separately from the financial-data tables. */
class AppPreferencesStore(
    private val database: TandemDatabase,
) {
    companion object {
        const val PENDING_BUDGET_REORDERS_KEY = "internal:pending-budget-reorders-v1"
    }

    private val dao = database.appPreferenceDao()

    suspend fun read(): Map<String, String> = dao.readAll().associate { it.name to it.value }

    suspend fun save(preferences: Map<String, String>) {
        database.withWriteTransaction {
            val internalPreferences = dao.readAll().filter { it.name.startsWith("internal:") }
            dao.clearAll()
            val updated =
                internalPreferences.associate { it.name to it.value } + preferences
            if (updated.isNotEmpty()) {
                dao.putAll(updated.map { (name, value) -> AppPreferenceEntity(name, value) })
            }
        }
    }

    suspend fun putValue(
        name: String,
        value: String,
    ) {
        database.withWriteTransaction {
            dao.putAll(listOf(AppPreferenceEntity(name, value)))
        }
    }
}
