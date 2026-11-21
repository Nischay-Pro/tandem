package app.tandemforactual.data.database

import androidx.sqlite.driver.AndroidSQLiteDriver
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidActualBudgetFilesTest {
    private fun sqlite(value: Long): ByteArray {
        val context = RuntimeEnvironment.getApplication()
        val file = context.cacheDir.resolve("source-$value.sqlite").apply { delete() }
        val db =
            app.tandemforactual.data.actual
                .ActualDatabase(AndroidSQLiteDriver().open(file.path))
        db.execute("CREATE TABLE t (v INTEGER)")
        db.execute("INSERT INTO t VALUES (?)", value)
        db.close()
        return file.readBytes()
    }

    @Test
    fun keepsOnePrivateActualDatabasePerBudget() {
        val context = RuntimeEnvironment.getApplication()
        val files = AndroidActualBudgetFiles(context, AndroidSQLiteDriver())
        assertNull(files.open("budget-1"))
        files.install("budget-1", sqlite(1)).close()
        val reopened = files.open("budget-1")!!
        assertEquals(1L, reopened.first("SELECT v FROM t")!!["v"])
        reopened.close()
        files.install("budget-1", sqlite(2)).close()
        val replaced = files.open("budget-1")!!
        assertEquals(2L, replaced.first("SELECT v FROM t")!!["v"])
        replaced.close()
        assertEquals(true, context.noBackupFilesDir.resolve("actual/budget-1.sqlite").isFile)
        files.remove("budget-1")
        assertNull(files.open("budget-1"))
        assertFailsWith<java.io.IOException> {
            files.install("budget-2") { output ->
                output.write(1)
                throw java.io.IOException("Interrupted download")
            }
        }
        assertNull(files.open("budget-2"))
        assertFailsWith<IllegalArgumentException> { files.open("../other") }
    }
}
