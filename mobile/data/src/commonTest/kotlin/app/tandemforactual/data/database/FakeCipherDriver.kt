package app.tandemforactual.data.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement

/**
 * Stands in for SQLCipher where its native library isn't available: plain SQLite with SQLCipher's export statements
 * replayed as `VACUUM INTO`, so the code moving budgets between plaintext and encrypted files runs unchanged.
 */
class FakeCipherDriver(
    private val delegate: SQLiteDriver,
    private val rawKey: String,
    private val keys: MutableMap<String, String>,
) : SQLiteDriver {
    override fun open(fileName: String): SQLiteConnection {
        keys[fileName]?.let { check(it == rawKey) { "file is not a database" } }
        return FakeCipherConnection(delegate.open(fileName), keys)
    }
}

private class FakeCipherConnection(
    private val delegate: SQLiteConnection,
    private val keys: MutableMap<String, String>,
) : SQLiteConnection {
    private var target: String? = null

    override fun inTransaction(): Boolean = delegate.inTransaction()

    override fun prepare(sql: String): SQLiteStatement =
        when {
            sql.startsWith("ATTACH DATABASE ? AS tandem_export KEY ?") ->
                ExportStatement { path, key ->
                    target =
                        path.also { keys[it] = key }
                }
            sql.startsWith("SELECT sqlcipher_export(") ->
                ExportStatement { _, _ ->
                    delegate.prepare("VACUUM INTO ?").use {
                        it.bindText(1, checkNotNull(target))
                        it.step()
                    }
                }
            sql.startsWith("PRAGMA tandem_export.") || sql.startsWith("DETACH DATABASE tandem_export") -> ExportStatement { _, _ -> }
            else -> delegate.prepare(sql)
        }

    override fun close() = delegate.close()
}

private class ExportStatement(
    private val run: (path: String, key: String) -> Unit,
) : SQLiteStatement {
    private val texts = mutableMapOf<Int, String>()

    override fun bindText(
        index: Int,
        value: String,
    ) {
        texts[index] = value
    }

    override fun step(): Boolean {
        run(texts[1].orEmpty(), texts[2].orEmpty())
        return false
    }

    override fun bindBlob(
        index: Int,
        value: ByteArray,
    ) = Unit

    override fun bindDouble(
        index: Int,
        value: Double,
    ) = Unit

    override fun bindLong(
        index: Int,
        value: Long,
    ) = Unit

    override fun bindNull(index: Int) = Unit

    override fun getBlob(index: Int): ByteArray = ByteArray(0)

    override fun getDouble(index: Int): Double = 0.0

    override fun getLong(index: Int): Long = 0

    override fun getText(index: Int): String = ""

    override fun isNull(index: Int): Boolean = true

    override fun getColumnCount(): Int = 0

    override fun getColumnName(index: Int): String = ""

    override fun getColumnType(index: Int): Int = 0

    override fun reset() = Unit

    override fun clearBindings() = Unit

    override fun close() = Unit
}
