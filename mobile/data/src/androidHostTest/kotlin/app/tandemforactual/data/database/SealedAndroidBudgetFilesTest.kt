package app.tandemforactual.data.database

import androidx.sqlite.driver.AndroidSQLiteDriver
import app.tandemforactual.data.actual.ActualDatabase
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SealedAndroidBudgetFilesTest {
    private val context = RuntimeEnvironment.getApplication()
    private val root = context.noBackupFilesDir.resolve("actual")
    private val keys = mutableMapOf<String, String>()
    private val key = byteArrayOf(1, 2, 3)
    private val files = AndroidActualBudgetFiles(context, AndroidSQLiteDriver()) { FakeCipherDriver(AndroidSQLiteDriver(), it, keys) }

    private fun sqlite(marker: String): ByteArray {
        val file = context.cacheDir.resolve("source-$marker.sqlite").apply { delete() }
        val db = ActualDatabase(AndroidSQLiteDriver().open(file.path))
        db.execute("CREATE TABLE t (v TEXT)")
        db.execute("INSERT INTO t VALUES (?)", marker)
        db.close()
        return file.readBytes()
    }

    private fun backup(name: String) = File(root, "backups/$name")

    @Test
    fun keepsEncryptedBudgetsSealedThroughInstallBackupAndRetire() {
        assertTrue(files.canSeal)
        root.mkdirs()
        val leftover = root.resolve("other.sqlite.staging").apply { writeText("decrypted") }
        val installed = files.installSealed("budget-1", sqlite("first"), key, "info")
        assertFalse(leftover.exists())
        assertEquals("first", installed.first("SELECT v FROM t")!!["v"])
        val copy = files.snapshot("budget-1") { path -> files.exportPlain("budget-1", installed, path) }
        installed.close()
        assertTrue(copy.isNotEmpty())
        assertNull(files.open("budget-1"))
        assertEquals("info", files.sealedKeyInfo("budget-1"))
        assertNull(files.openSealed("missing", key))

        files.retire("budget-1", "reset-1")
        assertNull(files.sealedKeyInfo("budget-1"))
        assertTrue(backup("budget-1-reset-1.sqlite.key").isFile)
        files.installSealed("budget-1", sqlite("second"), key, "info").close()
        files.retire("budget-1", "reset-2")
        assertFalse(backup("budget-1-reset-1.sqlite.key").exists())
        files.remove("budget-1")
        assertFalse(backup("budget-1-reset-2.sqlite").exists())
    }

    @Test
    fun sealsAPlaintextCopyInPlaceAndDropsItsPlaintextRetiredCopies() {
        files.install("budget-2", sqlite("old")).close()
        files.retire("budget-2", "reset-1")
        files.install("budget-2", sqlite("old")).close()
        files.sealBudget("budget-2", key, "info")
        assertNull(files.open("budget-2"))
        assertFalse(backup("budget-2-reset-1.sqlite").exists())
        val sealed = files.openSealed("budget-2", key)!!
        assertEquals("old", sealed.first("SELECT v FROM t")!!["v"])
        sealed.close()
        assertFailsWith<IllegalStateException> { files.sealBudget("budget-3", key, "info") }

        val plainOnly = AndroidActualBudgetFiles(context, AndroidSQLiteDriver())
        assertFailsWith<IllegalStateException> { plainOnly.openSealed("budget-2", key) }
    }
}
