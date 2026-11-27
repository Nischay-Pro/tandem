package app.tandemforactual.data.database

import android.content.Context
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tandemforactual.data.actual.ActualDatabase
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.zetetic.database.sqlcipher.driver.SQLCipherDriver
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SealedBudgetFilesDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val root = context.noBackupFilesDir.resolve("actual")
    private val key = ByteArray(32) { it.toByte() }

    private fun files(): AndroidActualBudgetFiles {
        System.loadLibrary("sqlcipher")
        return AndroidActualBudgetFiles(
            context,
            BundledSQLiteDriver(),
        ) { rawKey -> SQLCipherDriver(rawKey.encodeToByteArray(), null, null) }
    }

    private fun plainBudget(marker: String): ByteArray {
        val file = context.cacheDir.resolve("plain-$marker.sqlite").apply { delete() }
        val db = ActualDatabase(BundledSQLiteDriver().open(file.path))
        db.execute("PRAGMA user_version = 7")
        db.execute("CREATE TABLE notes (v TEXT)")
        db.execute("CREATE INDEX notes_v ON notes (v)")
        db.execute("INSERT INTO notes VALUES (?)", marker)
        db.close()
        return file.readBytes().also { file.delete() }
    }

    private fun File.contains(marker: String) = readBytes().toString(Charsets.ISO_8859_1).contains(marker)

    @Test
    fun encryptedBudgetsNeverReachDiskInPlaintext() {
        root.deleteRecursively()
        val files = files()
        val sealed = files.installSealed("budget-1", plainBudget("salary-figure"), key, "{\"id\":\"key\"}")
        assertEquals("salary-figure", sealed.first("SELECT v FROM notes")!!["v"])
        sealed.close()
        assertNull(files.open("budget-1"))
        assertEquals("{\"id\":\"key\"}", files.sealedKeyInfo("budget-1"))
        assertTrue(root.listFiles()!!.none { it.name.contains(".staging") })
        assertTrue(root.listFiles()!!.filter { it.isFile }.none { it.contains("salary-figure") })

        val reopened = files.openSealed("budget-1", key)!!
        assertEquals(7L, reopened.first("PRAGMA user_version")!!["user_version"])
        val backup = files.snapshot("budget-1") { path -> files.exportPlain("budget-1", reopened, path) }
        reopened.close()
        assertTrue(String(backup, Charsets.ISO_8859_1).contains("salary-figure"))
        assertFails { files.openSealed("budget-1", ByteArray(32))!!.first("SELECT v FROM notes") }

        files.retire("budget-1", "reset-1")
        assertNull(files.sealedKeyInfo("budget-1"))
        assertTrue(root.resolve("backups/budget-1-reset-1.sqlite.key").isFile)
        files.installSealed("budget-1", plainBudget("second"), key, "{}").close()
        files.retire("budget-1", "reset-2")
        assertFalse(root.resolve("backups/budget-1-reset-1.sqlite").exists())
        assertFalse(root.resolve("backups/budget-1-reset-1.sqlite.key").exists())
        assertTrue(root.resolve("backups/budget-1-reset-2.sqlite").isFile)
        files.remove("budget-1")
        assertTrue(root.resolve("backups").listFiles()!!.isEmpty())
    }

    @Test
    fun aPlaintextCopyIsEncryptedInPlace() {
        root.deleteRecursively()
        val files = files()
        files.install("budget-2", plainBudget("old-copy")).close()
        files.retire("budget-2", "reset-1")
        files.install("budget-2", plainBudget("old-copy")).close()
        files.sealBudget("budget-2", key, "{\"id\":\"key\"}")
        assertNull(files.open("budget-2"))
        assertFalse(root.resolve("backups/budget-2-reset-1.sqlite").exists())
        assertTrue(root.listFiles()!!.filter { it.isFile }.none { it.contains("old-copy") })
        val sealed = files.openSealed("budget-2", key)!!
        assertEquals("old-copy", sealed.first("SELECT v FROM notes")!!["v"])
        assertEquals(1L, sealed.first("SELECT count(*) AS n FROM sqlite_master WHERE type = 'index'")!!["n"])
        sealed.close()
    }
}
