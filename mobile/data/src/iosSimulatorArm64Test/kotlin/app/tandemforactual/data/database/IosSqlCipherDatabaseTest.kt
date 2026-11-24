package app.tandemforactual.data.database

import androidx.sqlite.SQLiteException
import androidx.sqlite.execSQL
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.LocalRecordProjection
import app.tandemforactual.data.store.LocalWriteAction
import app.tandemforactual.data.store.LocalWriteRequest
import app.tandemforactual.data.store.enqueueWrite
import app.tandemforactual.data.store.readPendingWrites
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

class IosSqlCipherDatabaseTest {
    private val key = "x'" + "0123456789abcdef".repeat(4) + "'"
    private val wrongKey = "x'" + "fedcba9876543210".repeat(4) + "'"

    @Test
    fun encryptsTheFileAndRejectsTheWrongKey() {
        val path = encryptedTandemDatabasePath("sqlcipher-driver-test.db")
        deleteEncryptedTandemDatabase(path)
        try {
            SqlCipherDriver(key).open(path).use { connection ->
                connection.execSQL("CREATE TABLE notes(id INTEGER PRIMARY KEY, body TEXT, data BLOB, amount REAL)")
                connection.prepare("INSERT INTO notes(body, data, amount) VALUES (?, ?, ?)").use { statement ->
                    statement.bindText(1, "Rent · ₹")
                    statement.bindBlob(2, byteArrayOf(1, 2, 3))
                    statement.bindDouble(3, 12.5)
                    assertFalse(statement.step())
                }
            }
            assertFalse(fileHeader(path).startsWith("SQLite format 3"))

            SqlCipherDriver(key).open(path).use { connection ->
                connection.prepare("SELECT id, body, data, amount, NULL FROM notes").use { statement ->
                    assertTrue(statement.step())
                    assertEquals(1L, statement.getLong(0))
                    assertEquals("Rent · ₹", statement.getText(1))
                    assertEquals(listOf<Byte>(1, 2, 3), statement.getBlob(2).toList())
                    assertEquals(12.5, statement.getDouble(3))
                    assertTrue(statement.isNull(4))
                    assertEquals("body", statement.getColumnName(1))
                    assertFalse(statement.step())
                }
            }

            assertFailsWith<SQLiteException> {
                SqlCipherDriver(wrongKey).open(path).use { connection -> connection.execSQL("SELECT count(*) FROM sqlite_master") }
            }
        } finally {
            deleteEncryptedTandemDatabase(path)
        }
    }

    @Test
    fun roomRunsOnTheEncryptedDriver() =
        runBlocking {
            val path = encryptedTandemDatabasePath("sqlcipher-room-test.db")
            deleteEncryptedTandemDatabase(path)
            try {
                val first = openTandemDatabase(encryptedTandemDatabaseBuilder(path), SqlCipherDriver(key))
                try {
                    LocalBudgetRepository(first) { 100L }.enqueueWrite(
                        LocalWriteRequest(
                            operationId = "sqlcipher-operation",
                            idempotencyKey = "sqlcipher-idempotency",
                            budgetId = "sqlcipher-budget",
                            recordType = "transaction",
                            recordId = "sqlcipher-transaction",
                            action = LocalWriteAction.CREATE,
                            baseRevision = null,
                            baseSnapshotJson = null,
                            patchJson = "{\"amount\":1200}",
                            optimisticJson = "{\"id\":\"sqlcipher-transaction\",\"amount\":1200}",
                            createdAtEpochMs = 100L,
                            optimisticProjection = LocalRecordProjection("2026-11-03", "checking", "groceries", "market", 1200L, null),
                        ),
                    )
                } finally {
                    first.close()
                }
                assertTrue(NSFileManager.defaultManager.fileExistsAtPath(path))
                assertFalse(fileHeader(path).startsWith("SQLite format 3"))
                val reopened = openTandemDatabase(encryptedTandemDatabaseBuilder(path), SqlCipherDriver(key))
                try {
                    assertEquals("queued", LocalBudgetRepository(reopened) { 200L }.readPendingWrites("sqlcipher-budget").single().status)
                } finally {
                    reopened.close()
                }
            } finally {
                deleteEncryptedTandemDatabase(path)
            }
            Unit
        }

    @OptIn(ExperimentalForeignApi::class)
    private fun fileHeader(path: String): String {
        val data = NSData.dataWithContentsOfFile(path) ?: return ""
        val bytes = ByteArray(minOf(16, data.length.toInt()))
        bytes.usePinned { memcpy(it.addressOf(0), data.bytes, bytes.size.toULong()) }
        return bytes.decodeToString()
    }
}
