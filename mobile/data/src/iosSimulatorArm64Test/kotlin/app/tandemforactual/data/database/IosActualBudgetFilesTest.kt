package app.tandemforactual.data.database

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.tandemforactual.data.actual.ActualDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.uuid.Uuid
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
class IosActualBudgetFilesTest {
    private fun sqlite(value: Long): ByteArray {
        val path = NSTemporaryDirectory() + "source-${Uuid.random()}.sqlite"
        val db = ActualDatabase(BundledSQLiteDriver().open(path))
        db.execute("CREATE TABLE t (v INTEGER)")
        db.execute("INSERT INTO t VALUES (?)", value)
        db.close()
        val data = checkNotNull(NSData.dataWithContentsOfFile(path))
        val bytes = ByteArray(data.length.toInt())
        bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
        return bytes
    }

    @Test
    fun keepsOneProtectedActualDatabasePerBudget() {
        val files = IosActualBudgetFiles()
        val id = "ios-${Uuid.random()}"
        assertNull(files.open(id))
        files.install(id, sqlite(1)).close()
        val first = checkNotNull(files.open(id))
        assertEquals(1L, first.first("SELECT v FROM t")!!["v"])
        first.close()
        files.install(id, sqlite(2)).close()
        val second = checkNotNull(files.open(id))
        assertEquals(2L, second.first("SELECT v FROM t")!!["v"])
        second.close()
        files.remove(id)
        assertNull(files.open(id))
        assertFailsWith<IllegalArgumentException> { files.install(id, ByteArray(0)) }
        assertFailsWith<IllegalArgumentException> { files.open("../other") }
    }
}
