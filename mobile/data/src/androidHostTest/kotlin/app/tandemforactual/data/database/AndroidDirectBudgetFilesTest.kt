package app.tandemforactual.data.database

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import app.tandemforactual.data.store.LocalBudgetRepository
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidDirectBudgetFilesTest {
    @Test
    fun keepsTheActualDatabaseAndMobileCacheInOnePrivateInstallation() =
        runBlocking {
            val context = RuntimeEnvironment.getApplication()
            val driver = AndroidSQLiteDriver()
            val files = AndroidDirectBudgetFiles.create(context, driver)
            val other = AndroidDirectBudgetFiles.create(context)
            assertNotEquals(files.installationId, other.installationId)
            assertFailsWith<IllegalStateException> { files.openActual() }
            assertFailsWith<IllegalStateException> { AndroidDirectBudgetFiles.reopen(context, files.installationId, driver) }
            assertFailsWith<IllegalArgumentException> { AndroidDirectBudgetFiles.reopen(context, "../other", driver) }
            assertFailsWith<IOException> {
                files.installActual { output ->
                    output.write(1)
                    throw IOException("Interrupted database download")
                }
            }
            assertFailsWith<IllegalStateException> { files.openActual() }

            val source = context.cacheDir.resolve("actual-source.sqlite")
            try {
                driver.open(source.path).use { connection ->
                    connection.execSQL("CREATE TABLE fixture (value TEXT)")
                    connection.execSQL("INSERT INTO fixture (value) VALUES ('downloaded')")
                }
                val actual = files.installActual(source.readBytes())
                try {
                    assertEquals("downloaded", actual.first("SELECT value FROM fixture")?.get("value"))
                    assertFailsWith<IllegalStateException> { files.installActual(byteArrayOf()) }
                } finally {
                    actual.close()
                }
                val cache = files.openMobileCache()
                try {
                    LocalBudgetRepository(cache) { 100L }.markSyncStarted("budget")
                } finally {
                    cache.closeDatabase()
                }

                val reopened = AndroidDirectBudgetFiles.reopen(context, files.installationId, driver)
                val directory = context.noBackupFilesDir.resolve("direct-actual").resolve(files.installationId)
                assertTrue(directory.resolve("actual.sqlite").isFile)
                assertTrue(directory.resolve("mobile.db").isFile)
                assertEquals(files.installationId, AndroidDirectBudgetFiles.reopen(context, files.installationId).installationId)
                val reopenedActual = reopened.openActual()
                try {
                    assertEquals("downloaded", reopenedActual.first("SELECT value FROM fixture")?.get("value"))
                } finally {
                    reopenedActual.close()
                }
                val reopenedCache = reopened.openMobileCache()
                try {
                    assertEquals("SYNCING", LocalBudgetRepository(reopenedCache) { 200L }.readSyncState("budget")?.status)
                } finally {
                    reopenedCache.closeDatabase()
                }
            } finally {
                source.delete()
            }
        }
}
