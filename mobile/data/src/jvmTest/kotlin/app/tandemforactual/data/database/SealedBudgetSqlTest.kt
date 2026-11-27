package app.tandemforactual.data.database

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.tandemforactual.data.actual.ActualDatabase
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class SealedBudgetSqlTest {
    private val directory: File = createTempDirectory("sealed").toFile()
    private val keys = mutableMapOf<String, String>()

    private fun open(
        file: File,
        rawKey: String = "",
    ) = ActualDatabase(FakeCipherDriver(BundledSQLiteDriver(), rawKey, keys).open(file.path))

    @Test
    fun exportsBetweenPlainAndSealedCopiesUnderTheRawKey() {
        assertEquals("x'00ff10'", SealedBudgetSql.rawKey(byteArrayOf(0, -1, 16)))
        val plain = open(directory.resolve("plain.sqlite"))
        plain.execute("CREATE TABLE t (v TEXT)")
        plain.execute("INSERT INTO t VALUES ('kept')")
        val sealedPath = directory.resolve("sealed.sqlite")
        SealedBudgetSql.exportSealed(plain, sealedPath.path, byteArrayOf(1, 2))
        plain.close()
        assertEquals("x'0102'", keys[sealedPath.path])
        assertFailsWith<IllegalStateException> { open(sealedPath, "x'00'") }

        val sealed = open(sealedPath, "x'0102'")
        val backup = directory.resolve("backup.sqlite")
        SealedBudgetSql.exportPlain(sealed, backup.path)
        sealed.close()
        assertEquals("", keys[backup.path])
        val restored = ActualDatabase(BundledSQLiteDriver().open(backup.path))
        assertEquals("kept", restored.first("SELECT v FROM t")!!["v"])
        restored.close()
    }

    @Test
    fun filesThatCantSealKeepThePlainDefaults() {
        val files =
            object : ActualBudgetFiles {
                override fun open(budgetId: String): ActualDatabase? = null

                override fun install(
                    budgetId: String,
                    bytes: ByteArray,
                ): ActualDatabase = error("unused")

                override fun remove(budgetId: String) = Unit

                override fun snapshot(
                    budgetId: String,
                    write: (path: String) -> Unit,
                ): ByteArray = ByteArray(0)

                override fun retire(
                    budgetId: String,
                    label: String,
                ) = Unit
            }
        assertFalse(files.canSeal)
        assertNull(files.sealedKeyInfo("b"))
        assertNull(files.openSealed("b", ByteArray(0)))
        assertFailsWith<UnsupportedOperationException> { files.installSealed("b", ByteArray(0), ByteArray(0), "") }
        assertFailsWith<UnsupportedOperationException> { files.sealBudget("b", ByteArray(0), "") }
        val db = ActualDatabase(BundledSQLiteDriver().open(directory.resolve("live.sqlite").path))
        db.execute("CREATE TABLE t (v INTEGER)")
        val copy = directory.resolve("copy.sqlite")
        files.exportPlain("b", db, copy.path)
        db.close()
        assertEquals(true, copy.isFile)
    }
}
