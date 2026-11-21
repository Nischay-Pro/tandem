package app.tandemforactual.data.database

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.tandemforactual.data.store.LocalBudgetRepository
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

@OptIn(ExperimentalForeignApi::class)
class IosDirectBudgetFilesTest {
    @Test
    fun persistsTheActualDatabaseAndMobileCacheAcrossReopen() =
        runBlocking {
            val files = IosDirectBudgetFiles.create()
            val directory = encryptedTandemDatabasePath("direct-actual/${files.installationId}")
            val source = encryptedTandemDatabasePath("actual-test-${Uuid.random()}.sqlite")
            try {
                assertFailsWith<IllegalStateException> { IosDirectBudgetFiles.reopen(files.installationId) }
                assertFailsWith<IllegalArgumentException> { IosDirectBudgetFiles.reopen("../other") }
                assertFailsWith<IllegalArgumentException> { files.installActual(byteArrayOf()) }
                BundledSQLiteDriver().open(source).use {
                    it.execSQL("CREATE TABLE fixture (value TEXT)")
                    it.execSQL("INSERT INTO fixture (value) VALUES ('downloaded')")
                }
                val data = checkNotNull(NSData.dataWithContentsOfFile(source))
                val bytes = ByteArray(data.length.toInt())
                bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
                val actual = files.installActual(bytes)
                try {
                    assertEquals("downloaded", actual.first("SELECT value FROM fixture")?.get("value"))
                    assertFailsWith<IllegalStateException> { files.installActual(bytes) }
                } finally {
                    actual.close()
                }
                val cache = files.openMobileCache()
                try {
                    LocalBudgetRepository(cache) { 100L }.markSyncStarted("budget")
                } finally {
                    cache.closeDatabase()
                }
                val reopened = IosDirectBudgetFiles.reopen(files.installationId)
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
                deleteEncryptedTandemDatabase(source)
                NSFileManager.defaultManager.removeItemAtPath(directory, error = null)
            }
            Unit
        }
}
