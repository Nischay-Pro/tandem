package app.tandemforactual.data.actual

import androidx.sqlite.SQLITE_DATA_BLOB
import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.execSQL

typealias Row = Map<String, Any?>

/** Thin access to an Actual `db.sqlite`. Values come back as Long, Double, String, ByteArray or null. */
class ActualDatabase(
    private val connection: SQLiteConnection,
) {
    private var depth = 0

    init {
        connection.execSQL("PRAGMA busy_timeout = 10000")
    }

    fun query(
        sql: String,
        vararg args: Any?,
    ): List<Row> =
        connection.prepare(sql).use { statement ->
            bind(statement, args)
            val rows = mutableListOf<Row>()
            while (statement.step()) {
                val row = LinkedHashMap<String, Any?>()
                for (i in 0 until statement.getColumnCount()) row[statement.getColumnName(i)] = read(statement, i)
                rows += row
            }
            rows
        }

    fun first(
        sql: String,
        vararg args: Any?,
    ): Row? = query(sql, *args).firstOrNull()

    fun execute(
        sql: String,
        vararg args: Any?,
    ) {
        connection.prepare(sql).use { statement ->
            bind(statement, args)
            while (statement.step()) Unit
        }
    }

    fun <T> transaction(block: () -> T): T {
        if (depth > 0) {
            depth++
            try {
                return block()
            } finally {
                depth--
            }
        }
        connection.execSQL("BEGIN IMMEDIATE")
        depth = 1
        try {
            val result = block()
            connection.execSQL("COMMIT")
            return result
        } catch (error: Throwable) {
            if (connection.inTransaction()) connection.execSQL("ROLLBACK")
            throw error
        } finally {
            depth = 0
        }
    }

    fun close() = connection.close()

    private fun bind(
        statement: SQLiteStatement,
        args: Array<out Any?>,
    ) {
        args.forEachIndexed { index, value ->
            val i = index + 1
            when (value) {
                null -> statement.bindNull(i)
                is String -> statement.bindText(i, value)
                is Boolean -> statement.bindLong(i, if (value) 1 else 0)
                is Int -> statement.bindLong(i, value.toLong())
                is Long -> statement.bindLong(i, value)
                is Double -> statement.bindDouble(i, value)
                is ByteArray -> statement.bindBlob(i, value)
                else -> throw IllegalArgumentException("unsupported argument ${value::class}")
            }
        }
    }

    private fun read(
        statement: SQLiteStatement,
        index: Int,
    ): Any? =
        when (statement.getColumnType(index)) {
            SQLITE_DATA_NULL -> null
            SQLITE_DATA_INTEGER -> statement.getLong(index)
            SQLITE_DATA_FLOAT -> statement.getDouble(index)
            SQLITE_DATA_BLOB -> statement.getBlob(index)
            else -> statement.getText(index)
        }
}
