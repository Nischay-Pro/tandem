package app.tandemforactual.data.actual

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActualBankSyncTest {
    private fun fixture(): Pair<ActualDatabase, ActualWriter> {
        val resource = { name: String -> checkNotNull(javaClass.getResource("/actual/import-parity/$name")).readBytes() }
        val file =
            File
                .createTempFile(
                    "bank",
                    ".sqlite",
                ).apply { writeBytes(ZipArchive.read(resource("budget.zip")).getValue("db.sqlite")) }
        file.deleteOnExit()
        val db = ActualDatabase(BundledSQLiteDriver().open(file.path))
        val store = ActualMessageStore(db)
        val clock = store.loadClock()
        val server = SyncProtocol.decodeResponse(resource("sync-response.bin"))
        runBlocking {
            ActualSyncEngine(store, { r -> SyncResponse(server.messages.filter { it.timestamp > r.since }, server.merkle) }, "g", "f") {
                System.currentTimeMillis()
            }.fullSync(clock, null)
        }
        return db to ActualWriter(store, clock, System::currentTimeMillis) { UUID.randomUUID().toString() }
    }

    private fun json(text: String) = Json.parseToJsonElement(text).jsonObject

    @Test
    fun syncsALinkedAccountLikeActual() {
        val (db, writer) = fixture()
        val checking = db.first("SELECT id FROM accounts WHERE name = 'Checking'")!!["id"] as String
        writer.insert("banks", linkedMapOf("id" to "bank-row", "bank_id" to "sfin-bank", "name" to "Credit Union"))
        writer.update("accounts", checking, mapOf("account_id" to "ACT-1", "account_sync_source" to "simpleFin", "bank" to "bank-row"))
        val sync = ActualBankSync(writer) { "2026-11-21" }
        val account = sync.linkedAccounts().single()
        assertEquals(LinkedAccount(checking, "Checking", "simpleFin", "ACT-1", "sfin-bank", "Credit Union", "2026-11-02"), account)

        val download =
            json(
                """{"transactions":{"all":[
                  {"transactionId":"tx-1","booked":true,"date":"2026-11-03","payeeName":"Corner Market","notes":"card #tag","transactionAmount":{"amount":"-10.00","currency":"USD"}},
                  {"transactionId":"tx-2","booked":true,"date":"2026-11-20","payeeName":"Gym","transactionAmount":{"amount":"-45.50","currency":"USD"}},
                  {"internalTransactionId":"p-9","booked":false,"account":"ACT-1","date":"2026-11-21","payeeName":"Coffee","transactionAmount":{"amount":"-4.25","currency":"USD"}}
                ]},"startingBalance":499000}""",
            )
        val result = sync.apply(account, download)
        assertEquals(2, result.added.size)
        assertEquals(1, result.updated.size)
        assertEquals(499000L, db.first("SELECT balance_current FROM accounts WHERE id = ?", checking)!!["balance_current"])
        val gym = db.first("SELECT * FROM v_transactions WHERE imported_id = 'tx-2'")!!
        assertEquals(-4550L, gym["amount"])
        assertEquals(1L, gym["cleared"])
        val pending = db.first("SELECT * FROM v_transactions WHERE amount = -425")!!
        assertNull(pending["imported_id"])
        assertEquals(0L, pending["cleared"])
        assertEquals("card ##tag", db.first("SELECT notes FROM v_transactions WHERE imported_id = 'tx-1'")!!["notes"])

        writer.insert("preferences", linkedMapOf("id" to "sync-import-pending-$checking", "value" to "false"))
        writer.insert("preferences", linkedMapOf("id" to "sync-import-notes-$checking", "value" to "false"))
        writer.insert(
            "preferences",
            linkedMapOf(
                "id" to "custom-sync-mappings-$checking",
                "value" to """{"payment":{"date":"bookingDate","payee":"creditor","notes":"memo"},"deposit":{}}""",
            ),
        )
        val rows =
            sync.normalize(
                checking,
                listOf(
                    json(
                        """{"transactionId":"m-1","booked":true,"bookingDate":"2026-11-19","creditor":"Mapped","memo":"x","date":"2026-11-01","payeeName":"Ignored","amount":-1.5}""",
                    ),
                    json("""{"booked":false,"date":"2026-11-19","payeeName":"Skipped","amount":-2}"""),
                    json(
                        """{"booked":true,"date":"2026-11-18","payeeName":"Pay","amount":"12.34","category":"nope","internalTransactionId":"i-1","account":"ACT-1"}""",
                    ),
                ),
            )
        assertEquals(
            listOf(
                ImportRow("2026-11-19", -150, "Mapped", null, "m-1", null, true, rows[0].rawSyncedData),
                ImportRow("2026-11-18", 1234, "Pay", null, "ACT-1-i-1", null, true, rows[1].rawSyncedData),
            ),
            rows,
        )
        assertFailsWith<IllegalStateException> { sync.normalize(checking, listOf(json("""{"booked":true,"payeeName":"x","amount":1}"""))) }
        assertFailsWith<IllegalStateException> {
            sync.normalize(
                checking,
                listOf(json("""{"booked":true,"date":"2026-11-01","amount":1}""")),
            )
        }
        writer.insert("preferences", linkedMapOf("id" to "sync-import-transactions-$checking", "value" to "false"))
        assertEquals(0, sync.apply(account, json("""{"transactions":[]}""")).added.size)
        assertEquals(1234L, ActualBankSync.amountToInteger("12.34"))
        assertEquals(0L, ActualBankSync.amountToInteger("n/a"))
    }

    @Test
    fun firstSyncRecordsTheStartingBalance() {
        val (db, writer) = fixture()
        val fresh = ActualBudgetEdits(writer, { "2026-11-21" }).createAccount("Card", false, null)
        writer.insert("banks", linkedMapOf("id" to "bank-2", "bank_id" to "gc-req", "name" to "Big Bank"))
        writer.update("accounts", fresh, mapOf("account_id" to "GC-1", "bank" to "bank-2"))
        val sync = ActualBankSync(writer) { "2026-11-21" }
        val account = sync.linkedAccounts().first { it.id == fresh }
        assertEquals("goCardless", account.source)
        assertEquals("2026-08-24", account.startDate)
        val result =
            sync.apply(
                account,
                json(
                    """{"transactions":[{"transactionId":"g1","booked":true,"date":"2026-11-10","payeeName":"Shop","transactionAmount":{"amount":"-20.00"}}],"startingBalance":-5000}""",
                ),
            )
        assertEquals(2, result.added.size)
        val opening = db.first("SELECT * FROM v_transactions WHERE account = ? AND starting_balance_flag = 1", fresh)!!
        assertEquals(-5000L, opening["amount"])
        assertTrue(opening["category"] != null)
        for (source in listOf("simpleFin", "pluggyai", "akahu", "enableBanking")) {
            val id = ActualBudgetEdits(writer, { "2026-11-21" }).createAccount("A $source", true, null)
            writer.update("accounts", id, mapOf("account_id" to "X-$source", "account_sync_source" to source, "bank" to "bank-2"))
            val linked = sync.linkedAccounts().first { it.id == id }
            sync.apply(
                linked,
                json(
                    """{"transactions":[{"transactionId":"$source-1","booked":true,"date":"2026-11-10","payeeName":"P","transactionAmount":{"amount":"-1.00"}}],"startingBalance":900}""",
                ),
            )
            val start = db.first("SELECT amount, category FROM v_transactions WHERE account = ? AND starting_balance_flag = 1", id)!!
            assertEquals(1000L, start["amount"], source)
            assertNull(start["category"])
        }
        db.query("SELECT id FROM categories WHERE LOWER(name) = 'starting balances'").forEach {
            writer.update(
                "categories",
                it["id"] as String,
                mapOf(
                    "name" to "Opening",
                ),
            )
        }
        db.query("SELECT id FROM payees WHERE name = 'Starting Balance'").forEach {
            writer.update(
                "payees",
                it["id"] as String,
                mapOf(
                    "tombstone" to true,
                ),
            )
        }
        val empty = ActualBudgetEdits(writer, { "2026-11-21" }).createAccount("Empty", false, null)
        sync.linkAccount(empty, "goCardless", "E-1", "gc-req", "Big Bank")
        ActualBudgetEdits(writer, { "2026-11-21" }).linkBankAccount(empty, "goCardless", "E-1", "gc-new", null)
        assertEquals(2L, db.first("SELECT COUNT(*) AS n FROM banks WHERE tombstone = 0")!!["n"])
        sync.linkAccount(empty, "goCardless", "E-1", "gc-req", "Big Bank")
        sync.apply(sync.linkedAccounts().first { it.id == empty }, json("""{"transactions":[]}"""))
        val start = db.first("SELECT date, payee, category FROM v_transactions WHERE account = ?", empty)!!
        assertEquals(20261121L, start["date"])
        assertEquals("Starting Balance", db.first("SELECT name FROM payees WHERE id = ?", start["payee"])!!["name"])
        assertTrue(start["category"] != null)
        ActualBudgetEdits(writer, { "2026-11-21" }).unlinkBankAccount(empty)
        assertTrue(sync.linkedAccounts().none { it.id == empty })
        assertEquals(null, db.first("SELECT account_sync_source FROM accounts WHERE id = ?", empty)!!["account_sync_source"])
    }
}
