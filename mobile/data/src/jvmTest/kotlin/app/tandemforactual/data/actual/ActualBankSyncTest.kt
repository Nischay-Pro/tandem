package app.tandemforactual.data.actual

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

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
        assertEquals(-1234L, ActualBankSync.amountToInteger(" -12.34 "))
        for (bad in listOf("n/a", "", "1,000.00", "Infinity", "1e400")) {
            assertFailsWith<IllegalStateException>(bad) { ActualBankSync.amountToInteger(bad) }
        }
    }

    private fun linkedChecking(
        db: ActualDatabase,
        writer: ActualWriter,
    ): Pair<ActualBankSync, LinkedAccount> {
        val checking = db.first("SELECT id FROM accounts WHERE name = 'Checking'")!!["id"] as String
        writer.insert("banks", linkedMapOf("id" to "bank-row", "bank_id" to "sfin-bank", "name" to "Credit Union"))
        writer.update("accounts", checking, mapOf("account_id" to "ACT-1", "account_sync_source" to "simpleFin", "bank" to "bank-row"))
        val sync = ActualBankSync(writer) { "2026-11-21" }
        return sync to sync.linkedAccounts().single()
    }

    @Test
    fun deletedBankTransactionsStayDeletedWhenTheAccountSaysSo() {
        val (db, writer) = fixture()
        val (sync, account) = linkedChecking(db, writer)
        val download =
            json(
                """{"transactions":[{"transactionId":"del-1","booked":true,"date":"2026-11-12","payeeName":"Kiosk","amount":"-7.31"}]}""",
            )

        fun alive() = db.query("SELECT id FROM v_transactions WHERE imported_id = 'del-1'").map { it["id"] as String }

        fun deleteIt() = ActualTransactions(writer).batchUpdate(deleted = alive())

        sync.apply(account, download)
        deleteIt()
        sync.apply(account, download)
        assertEquals(1, alive().size, "deleted rows come back by default, like Actual")

        deleteIt()
        writer.insert("preferences", linkedMapOf("id" to "sync-reimport-deleted-${account.id}", "value" to "false"))
        val result = sync.apply(account, download)
        assertEquals(emptyList(), result.added)
        assertEquals(emptyList(), alive())
    }

    @Test
    fun aFuzzyMatchCannotClaimAChildOfAnExactlyMatchedSplit() {
        val (db, writer) = fixture()
        val (sync, account) = linkedChecking(db, writer)
        ActualTransactions(writer).addTransactions(
            account.id,
            listOf(
                mapOf(
                    "date" to "2026-11-15",
                    "amount" to -3_000L,
                    "imported_id" to "split-bank",
                    "subtransactions" to listOf(mapOf("amount" to -1_037L), mapOf("amount" to -1_963L)),
                ),
            ),
            runTransfers = false,
        )
        val download =
            json(
                """{"transactions":[
                  {"transactionId":"split-bank","booked":true,"date":"2026-11-15","payeeName":"Shop","amount":"-30.00"},
                  {"transactionId":"other-1","booked":true,"date":"2026-11-16","payeeName":"Other","amount":"-10.37"}
                ]}""",
            )
        val result = sync.apply(account, download)
        assertEquals(1, result.added.size)
        val added = db.first("SELECT * FROM v_transactions WHERE id = ?", result.added.single())!!
        assertEquals(listOf("other-1", -1_037L, null), listOf(added["imported_id"], added["amount"], added["parent_id"]))
        assertEquals(1L, db.first("SELECT COUNT(*) AS n FROM v_transactions WHERE imported_id = 'other-1'")!!["n"])
    }

    @Test
    fun anUnreadableBankAmountFailsInsteadOfWritingZero() {
        val (db, writer) = fixture()
        val (sync, account) = linkedChecking(db, writer)
        val before = db.first("SELECT COUNT(*) AS n FROM v_transactions WHERE account = ?", account.id)!!["n"]
        for (row in listOf(
            """{"transactionId":"bad-1","booked":true,"date":"2026-11-12","payeeName":"Kiosk","amount":"n/a"}""",
            """{"transactionId":"bad-2","booked":true,"date":"2026-11-12","payeeName":"Kiosk","transactionAmount":{"amount":"??"}}""",
            """{"transactionId":"bad-3","booked":true,"date":"2026-11-12","payeeName":"Kiosk"}""",
        )) {
            assertFailsWith<IllegalStateException>(row) { sync.apply(account, json("""{"transactions":[$row]}""")) }
        }
        assertEquals(before, db.first("SELECT COUNT(*) AS n FROM v_transactions WHERE account = ?", account.id)!!["n"])
        val fresh = ActualBudgetEdits(writer, { "2026-11-21" }).createLinkedAccount("New", false, "goCardless", "GC-2", "gc", "GC")
        assertFailsWith<IllegalStateException> {
            sync.apply(
                sync.linkedAccounts().first { it.id == fresh },
                json("""{"transactions":[{"transactionId":"bad-4","booked":true,"date":"2026-11-12","payeeName":"K","amount":"x"}]}"""),
            )
        }
        assertEquals(0L, db.first("SELECT COUNT(*) AS n FROM v_transactions WHERE account = ?", fresh)!!["n"])
    }

    @Test
    fun syncsAutomaticallyOnActualsSchedule() {
        val (db, writer) = fixture()
        val sync = ActualBankSync(writer) { "2026-11-21" }
        val day = 1_440 * 60_000L
        assertEquals(0, sync.syncIntervalMinutes())
        assertFalse(sync.automaticSyncDue(10 * day, null))
        db.execute("INSERT OR REPLACE INTO preferences (id, value) VALUES ('bank-sync-interval', 'soon')")
        assertEquals(0, sync.syncIntervalMinutes())
        sync.setSyncIntervalMinutes(1_440)
        assertEquals(1_440, sync.syncIntervalMinutes())
        assertFalse(sync.automaticSyncDue(10 * day, null), "nothing is linked yet")

        val account = db.first("SELECT id FROM accounts WHERE tombstone = 0 AND closed = 0")!!["id"] as String
        sync.linkAccount(account, "simpleFin", "SF-1", "bank", "Bank")
        assertTrue(sync.automaticSyncDue(10 * day, null), "a never-synced account is due")
        db.execute("UPDATE accounts SET last_sync = ? WHERE id = ?", (10 * day).toString(), account)
        assertFalse(sync.automaticSyncDue(10 * day + day - 1, null))
        assertTrue(sync.automaticSyncDue(11 * day, null))
        assertFalse(sync.automaticSyncDue(11 * day, 11 * day - 60_000), "a failed attempt waits an interval")
        assertTrue(sync.automaticSyncDue(12 * day, 11 * day - 60_000))

        sync.setSyncIntervalMinutes(5)
        assertEquals(ActualBankSync.MIN_SYNC_INTERVAL_MINUTES, sync.syncIntervalMinutes())
        ActualBudgetEdits(writer, { "2026-11-21" }).setBankSyncInterval(0)
        assertEquals(0, sync.syncIntervalMinutes())
        assertFalse(sync.automaticSyncDue(99 * day, null))
    }

    @Test
    fun firstSyncRecordsWhenTheAccountLastSynced() {
        val (db, writer) = fixture()
        val sync = ActualBankSync(writer) { "2026-11-21" }
        sync.setSyncIntervalMinutes(1_440)
        val edits = ActualBudgetEdits(writer, { "2026-11-21" })
        val downloads =
            listOf(
                """{"transactions":[{"transactionId":"f1","booked":true,"date":"2026-11-10","payeeName":"Shop","amount":"-2.00"}],"startingBalance":100}""",
                """{"transactions":[]}""",
            )
        for ((index, download) in downloads.withIndex()) {
            val id = edits.createLinkedAccount("First $index", false, "simpleFin", "F-$index", "first.example", "First Bank")
            val before = System.currentTimeMillis()
            sync.apply(sync.linkedAccounts().first { it.id == id }, json(download))
            val lastSync = (db.first("SELECT last_sync FROM accounts WHERE id = ?", id)!!["last_sync"] as String?)?.toLong()
            assertTrue(lastSync != null && lastSync in before..System.currentTimeMillis(), download)
            assertEquals(lastSync, sync.linkedAccounts().first { it.id == id }.lastSyncMillis)
        }
        assertFalse(sync.automaticSyncDue(System.currentTimeMillis(), null))
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

        val edits = ActualBudgetEdits(writer, { "2026-11-21" })
        val card = edits.createLinkedAccount(" Rewards Card ", true, "simpleFin", "SF-9", "cu.example", "Credit Union")
        val created = db.first("SELECT name, offbudget, account_id, account_sync_source FROM accounts WHERE id = ?", card)!!
        assertEquals(listOf("Rewards Card", 1L, "SF-9", "simpleFin"), created.values.toList())
        assertTrue(sync.linkedAccounts().any { it.id == card })
        val unnamed = edits.createLinkedAccount("", false, "simpleFin", "SF-8", "cu.example", "Credit Union")
        val fallback = edits.createLinkedAccount(" ", false, "simpleFin", "SF-7", "", " ")
        assertEquals("Credit Union", db.first("SELECT name FROM accounts WHERE id = ?", unnamed)!!["name"])
        assertEquals("Bank account", db.first("SELECT name FROM accounts WHERE id = ?", fallback)!!["name"])
        assertEquals(
            "Bank account",
            db.first(
                "SELECT name FROM accounts WHERE id = ?",
                edits.createLinkedAccount("", false, "simpleFin", "SF-6", "", null),
            )!!["name"],
        )
    }
}
