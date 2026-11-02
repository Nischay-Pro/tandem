package app.tandemforactual.data.store

import androidx.room3.withWriteTransaction
import app.tandemforactual.data.database.AppPreferenceEntity
import app.tandemforactual.data.database.TandemDatabase

/** Stores non-sensitive app preferences separately from the financial-data tables. */
class AppPreferencesStore(
    private val database: TandemDatabase,
) {
    private val dao = database.appPreferenceDao()

    suspend fun read(): Map<String, String> = dao.readAll().associate { it.name to it.value }

    suspend fun save(preferences: Map<String, String>) {
        database.withWriteTransaction {
            dao.clearAll()
            if (preferences.isNotEmpty()) {
                dao.putAll(preferences.map { (name, value) -> AppPreferenceEntity(name, value) })
            }
        }
    }
}
