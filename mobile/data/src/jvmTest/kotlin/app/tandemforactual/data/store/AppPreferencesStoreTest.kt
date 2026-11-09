package app.tandemforactual.data.store

import androidx.room3.Room
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.database.openTandemDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class AppPreferencesStoreTest {
    @Test
    fun internalPreferencesSurviveSavingVisiblePreferences() =
        runBlocking {
            val database =
                openTandemDatabase(
                    Room
                        .inMemoryDatabaseBuilder<TandemDatabase>()
                        .setQueryCoroutineContext(Dispatchers.IO),
                )
            try {
                val store = AppPreferencesStore(database)
                store.putValue(AppPreferencesStore.PENDING_BUDGET_REORDERS_KEY, "queued-reorder")
                store.save(mapOf("theme" to "LIGHT"))

                assertEquals(
                    mapOf(
                        AppPreferencesStore.PENDING_BUDGET_REORDERS_KEY to "queued-reorder",
                        "theme" to "LIGHT",
                    ),
                    store.read(),
                )
            } finally {
                database.close()
            }
        }

    @Test
    fun preferencesSurviveDatabaseReopenAndReplacePreviousValues() =
        runBlocking {
            val path = File.createTempFile("tandem-preferences", ".db").absolutePath
            File(path).delete()
            try {
                val firstDatabase = database(path)
                val firstStore = AppPreferencesStore(firstDatabase)
                firstStore.save(
                    mapOf(
                        "theme" to "LIGHT",
                        "syncOnOpen" to "false",
                    ),
                )
                firstDatabase.close()

                val reopenedDatabase = database(path)
                try {
                    val store = AppPreferencesStore(reopenedDatabase)
                    assertEquals(
                        mapOf("theme" to "LIGHT", "syncOnOpen" to "false"),
                        store.read(),
                    )
                    store.save(mapOf("theme" to "PURE_BLACK", "syncOnOpen" to "false"))
                    assertEquals(
                        mapOf("theme" to "PURE_BLACK", "syncOnOpen" to "false"),
                        store.read(),
                    )
                    store.save(mapOf("theme" to "PURE_BLACK"))
                    assertEquals(mapOf("theme" to "PURE_BLACK"), store.read())
                    store.save(emptyMap())
                    assertEquals(emptyMap(), store.read())
                } finally {
                    reopenedDatabase.close()
                }
            } finally {
                File(path).delete()
                File("$path-wal").delete()
                File("$path-shm").delete()
            }
        }

    private fun database(path: String): TandemDatabase =
        openTandemDatabase(
            Room
                .databaseBuilder<TandemDatabase>(name = path)
                .setQueryCoroutineContext(Dispatchers.IO),
        )
}
