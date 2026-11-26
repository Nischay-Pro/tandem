package app.tandemforactual.data.actual

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ActualBatchWorkTest {
    private class CountingConnection(
        private val connection: SQLiteConnection,
    ) : SQLiteConnection {
        val statements = mutableListOf<String>()

        override fun prepare(sql: String): SQLiteStatement = connection.prepare(sql).also { statements += sql }

        override fun inTransaction(): Boolean = connection.inTransaction()

        override fun close() = connection.close()

        fun count(sql: String) = statements.count { it.trim() == sql }
    }

    private class Fixture(
        val connection: CountingConnection,
        val db: ActualDatabase,
        val writer: ActualWriter,
    ) {
        var today = "2026-11-21"
        val transactions = ActualTransactions(writer)
        val schedules = ActualScheduleEdits(writer, { today }, transactions)

        fun <T> counting(block: () -> T): T {
            connection.statements.clear()
            return block()
        }
    }

    private fun fixture(): Fixture {
        val name = "parity-envelope"
        val entries = ZipArchive.read(checkNotNull(javaClass.getResource("/actual/$name/budget.zip")).readBytes())
        val file =
            File.createTempFile("batch", ".sqlite").apply {
                writeBytes(entries.getValue("db.sqlite"))
                deleteOnExit()
            }
        val connection = CountingConnection(BundledSQLiteDriver().open(file.path))
        val db = ActualDatabase(connection)
        val store = ActualMessageStore(db)
        val clock = store.loadClock()
        val server = SyncProtocol.decodeResponse(checkNotNull(javaClass.getResource("/actual/$name/sync-response.bin")).readBytes())
        runBlocking {
            ActualSyncEngine(
                store,
                { request -> SyncResponse(server.messages.filter { it.timestamp > request.since }, server.merkle) },
                "g",
                "f",
            ) {
                System.currentTimeMillis()
            }.fullSync(clock, null)
        }
        return Fixture(connection, db, ActualWriter(store, clock, System::currentTimeMillis) { UUID.randomUUID().toString() })
    }

    @Test
    fun addingManyTransactionsLoadsRulesAndPayeesOnce() {
        val f = fixture()
        val account = f.db.first("SELECT id FROM accounts WHERE name = 'Checking'")!!["id"] as String
        val rows = (1..50).map { mapOf("date" to "2026-11-20", "amount" to -it.toLong(), "payee_name" to "Shop ${it % 5}") }
        val ids = f.counting { f.transactions.addTransactions(account, rows, runTransfers = true) }
        assertEquals(50, ids.size)
        assertEquals(1, f.connection.count(RULES))
        assertEquals(1, f.connection.count(PAYEES))
        assertEquals(1, f.connection.count(SCHEDULES))
        assertEquals(1, f.connection.count(CLOCK))
        assertEquals(5L, f.db.first("SELECT count(*) AS n FROM payees WHERE name LIKE 'Shop %' AND tombstone = 0")!!["n"])
    }

    @Test
    fun catchingUpADailyScheduleSkipsFullBudgetReads() {
        val f = fixture()
        val id = f.db.first("SELECT id FROM schedules WHERE tombstone = 0")!!["id"] as String
        f.db.execute("UPDATE schedules SET posts_transaction = 1 WHERE id = ?", id)
        f.schedules.edit(
            id,
            buildJsonObject {
                put(
                    "date",
                    buildJsonObject {
                        put("frequency", "daily")
                        put("start", "2026-11-21")
                    },
                )
            },
        )
        assertEquals("2026-11-21", f.schedules.nextDate(id))
        f.today = "2026-12-21"
        f.counting { f.schedules.runService(syncSuccess = true) }
        assertEquals(0, f.connection.count(ALL_TRANSACTIONS))
        assertEquals(2, f.connection.count(RULES))
        assertEquals(1, f.connection.count(CLOCK))
        assertEquals(31L, f.db.first("SELECT count(*) AS n FROM v_transactions_internal_alive WHERE schedule = ?", id)!!["n"])
        assertEquals("2026-12-21", f.schedules.nextDate(id))
    }

    private companion object {
        const val ALL_TRANSACTIONS = "SELECT * FROM v_transactions"
        const val RULES = "SELECT * FROM rules WHERE conditions IS NOT NULL AND actions IS NOT NULL AND tombstone = 0"
        const val PAYEES = "SELECT id, name FROM payees WHERE tombstone = 0"
        const val CLOCK = "INSERT OR REPLACE INTO messages_clock (id, clock) VALUES (1, ?)"
        const val SCHEDULES = "SELECT id, rule, completed, tombstone FROM schedules"
    }
}
