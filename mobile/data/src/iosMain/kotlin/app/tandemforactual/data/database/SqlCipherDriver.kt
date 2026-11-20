package app.tandemforactual.data.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.throwSQLiteException
import app.tandemforactual.data.sqlcipher.tandem_autocommit
import app.tandemforactual.data.sqlcipher.tandem_bind_blob
import app.tandemforactual.data.sqlcipher.tandem_bind_double
import app.tandemforactual.data.sqlcipher.tandem_bind_int64
import app.tandemforactual.data.sqlcipher.tandem_bind_null
import app.tandemforactual.data.sqlcipher.tandem_bind_text
import app.tandemforactual.data.sqlcipher.tandem_clear_bindings
import app.tandemforactual.data.sqlcipher.tandem_close
import app.tandemforactual.data.sqlcipher.tandem_column_blob
import app.tandemforactual.data.sqlcipher.tandem_column_bytes
import app.tandemforactual.data.sqlcipher.tandem_column_count
import app.tandemforactual.data.sqlcipher.tandem_column_double
import app.tandemforactual.data.sqlcipher.tandem_column_int64
import app.tandemforactual.data.sqlcipher.tandem_column_name
import app.tandemforactual.data.sqlcipher.tandem_column_text
import app.tandemforactual.data.sqlcipher.tandem_column_type
import app.tandemforactual.data.sqlcipher.tandem_errmsg
import app.tandemforactual.data.sqlcipher.tandem_finalize
import app.tandemforactual.data.sqlcipher.tandem_key
import app.tandemforactual.data.sqlcipher.tandem_open
import app.tandemforactual.data.sqlcipher.tandem_prepare
import app.tandemforactual.data.sqlcipher.tandem_reset
import app.tandemforactual.data.sqlcipher.tandem_step
import cnames.structs.tandem_db
import cnames.structs.tandem_stmt
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value

private const val SQLITE_OK = 0
private const val SQLITE_MISUSE = 21
private const val SQLITE_RANGE = 25
private const val SQLITE_ROW = 100
private const val SQLITE_DONE = 101

/** Opens SQLCipher databases keyed with [rawKey], an `x'…'` hex key, so each connection skips key derivation. */
@OptIn(ExperimentalForeignApi::class)
class SqlCipherDriver(
    private val rawKey: String,
) : SQLiteDriver {
    override fun open(fileName: String): SQLiteConnection =
        memScoped {
            val handle = alloc<CPointerVar<tandem_db>>()
            val openCode = tandem_open(fileName, handle.ptr)
            val db = handle.value
            if (openCode != SQLITE_OK || db == null) {
                val message = db?.let { tandem_errmsg(it)?.toKString() }
                db?.let { tandem_close(it) }
                throwSQLiteException(openCode, message)
            }
            val keyBytes = rawKey.encodeToByteArray()
            val keyCode = keyBytes.usePinned { tandem_key(db, it.addressOf(0), keyBytes.size) }
            if (keyCode != SQLITE_OK) {
                tandem_close(db)
                throwSQLiteException(keyCode, "Couldn’t apply the database key")
            }
            SqlCipherConnection(db)
        }
}

@OptIn(ExperimentalForeignApi::class)
private class SqlCipherConnection(
    private val db: CPointer<tandem_db>,
) : SQLiteConnection {
    private var closed = false

    override fun inTransaction(): Boolean {
        checkOpen()
        return tandem_autocommit(db) == 0
    }

    override fun prepare(sql: String): SQLiteStatement {
        checkOpen()
        return memScoped {
            val handle = alloc<CPointerVar<tandem_stmt>>()
            val code = tandem_prepare(db, sql, handle.ptr)
            if (code != SQLITE_OK) db.fail(code)
            SqlCipherStatement(db, handle.value ?: throwSQLiteException(SQLITE_MISUSE, "Statement is empty"))
        }
    }

    override fun close() {
        if (!closed) {
            closed = true
            tandem_close(db)
        }
    }

    private fun checkOpen() {
        if (closed) throwSQLiteException(SQLITE_MISUSE, "Connection is closed")
    }
}

@OptIn(ExperimentalForeignApi::class)
private class SqlCipherStatement(
    private val db: CPointer<tandem_db>,
    private val stmt: CPointer<tandem_stmt>,
) : SQLiteStatement {
    private var closed = false

    override fun bindBlob(
        index: Int,
        value: ByteArray,
    ) {
        checkOpen()
        val bytes = if (value.isEmpty()) ByteArray(1) else value
        check(bytes.usePinned { tandem_bind_blob(stmt, index, it.addressOf(0), value.size) })
    }

    override fun bindDouble(
        index: Int,
        value: Double,
    ) {
        checkOpen()
        check(tandem_bind_double(stmt, index, value))
    }

    override fun bindLong(
        index: Int,
        value: Long,
    ) {
        checkOpen()
        check(tandem_bind_int64(stmt, index, value))
    }

    override fun bindText(
        index: Int,
        value: String,
    ) {
        checkOpen()
        check(tandem_bind_text(stmt, index, value, -1))
    }

    override fun bindNull(index: Int) {
        checkOpen()
        check(tandem_bind_null(stmt, index))
    }

    override fun getBlob(index: Int): ByteArray {
        checkColumn(index)
        val size = tandem_column_bytes(stmt, index)
        val bytes = tandem_column_blob(stmt, index) ?: return ByteArray(0)
        return bytes.reinterpret<ByteVar>().readBytes(size)
    }

    override fun getDouble(index: Int): Double {
        checkColumn(index)
        return tandem_column_double(stmt, index)
    }

    override fun getLong(index: Int): Long {
        checkColumn(index)
        return tandem_column_int64(stmt, index)
    }

    override fun getText(index: Int): String {
        checkColumn(index)
        val size = tandem_column_bytes(stmt, index)
        val text = tandem_column_text(stmt, index) ?: return ""
        return text.reinterpret<ByteVar>().readBytes(size).decodeToString()
    }

    override fun isNull(index: Int): Boolean = getColumnType(index) == androidx.sqlite.SQLITE_DATA_NULL

    override fun getColumnCount(): Int {
        checkOpen()
        return tandem_column_count(stmt)
    }

    override fun getColumnName(index: Int): String {
        checkColumn(index)
        return tandem_column_name(stmt, index)?.toKString() ?: throwSQLiteException(SQLITE_RANGE, "Column name unavailable")
    }

    override fun getColumnType(index: Int): Int {
        checkColumn(index)
        return tandem_column_type(stmt, index)
    }

    override fun step(): Boolean {
        checkOpen()
        return when (val code = tandem_step(stmt)) {
            SQLITE_ROW -> true
            SQLITE_DONE -> false
            else -> db.fail(code)
        }
    }

    override fun reset() {
        checkOpen()
        check(tandem_reset(stmt))
    }

    override fun clearBindings() {
        checkOpen()
        check(tandem_clear_bindings(stmt))
    }

    override fun close() {
        if (!closed) {
            closed = true
            tandem_finalize(stmt)
        }
    }

    private fun check(code: Int) {
        if (code != SQLITE_OK) db.fail(code)
    }

    private fun checkOpen() {
        if (closed) throwSQLiteException(SQLITE_MISUSE, "Statement is closed")
    }

    private fun checkColumn(index: Int) {
        checkOpen()
        if (index < 0 || index >= tandem_column_count(stmt)) throwSQLiteException(SQLITE_RANGE, "Column index $index out of range")
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun CPointer<tandem_db>.fail(code: Int): Nothing = throwSQLiteException(code, tandem_errmsg(this)?.toKString())
