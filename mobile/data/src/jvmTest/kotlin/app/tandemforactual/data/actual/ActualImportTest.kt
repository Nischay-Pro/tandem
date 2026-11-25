package app.tandemforactual.data.actual

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

class ActualImportTest {
    private fun resource(name: String) = checkNotNull(javaClass.getResource("/actual/import-parity/$name")).readBytes()

    private fun fixture(): Pair<ActualDatabase, ActualWriter> {
        val file =
            File
                .createTempFile(
                    "import",
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

    private fun JsonObject.text(name: String) = (this[name] as? JsonPrimitive)?.contentOrNull

    @Test
    fun rulesThatSplitOrDeleteANewRowApplyLikeAddingIt() {
        val (db, writer) = fixture()
        val account = db.first("SELECT id FROM accounts WHERE name = 'Checking'")!!["id"] as String

        fun rule(
            amount: Long,
            actions: String,
        ) = db.execute(
            "INSERT INTO rules (id, stage, conditions, actions, conditions_op, tombstone) VALUES (?, NULL, ?, ?, 'and', 0)",
            UUID.randomUUID().toString(),
            """[{"op":"is","field":"amount","value":$amount}]""",
            actions,
        )
        rule(-9_000, """[{"op":"set-split-amount","value":-4000,"options":{"splitIndex":1,"method":"fixed-amount"}}]""")
        rule(-7_000, """[{"op":"delete-transaction"}]""")

        val result =
            ActualImport(writer).reconcile(
                account,
                listOf(
                    ImportRow(date = "2026-10-01", amount = -9_000, payeeName = "Split Shop", importedId = "bank-split-1"),
                    ImportRow(date = "2026-10-01", amount = -7_000, payeeName = "Ignored Shop", importedId = "bank-delete-1"),
                ),
            )

        assertEquals(listOf(ImportOutcome.ADD, ImportOutcome.UNCHANGED), result.outcomes)
        val parent = db.first("SELECT id, is_parent FROM v_transactions WHERE imported_id = 'bank-split-1'")!!
        assertEquals(listOf(parent["id"]), result.added)
        assertEquals(1L, parent["is_parent"])
        assertEquals(
            listOf(-4_000L),
            db.query("SELECT amount FROM v_transactions WHERE parent_id = ?", parent["id"]).map { (it["amount"] as Number).toLong() },
        )
        assertEquals(0L, db.first("SELECT COUNT(*) AS n FROM transactions WHERE financial_id = 'bank-delete-1'")!!["n"])
    }

    @Test
    fun importsLikeActual() {
        val cases = Json.parseToJsonElement(resource("import-cases.json").decodeToString()).jsonObject
        val rows =
            cases.getValue("rows").jsonArray.map {
                val row = it.jsonObject
                ImportRow(
                    date = row.text("date")!!,
                    amount = row.getValue("amount").jsonPrimitive.long,
                    payeeName = row.text("payee_name"),
                    notes = row.text("notes"),
                    importedId = row.text("imported_id"),
                    cleared = (row["cleared"] as? JsonPrimitive)?.booleanOrNull,
                )
            }
        val (db, writer) = fixture()
        val account = db.first("SELECT id FROM accounts WHERE name = 'Checking'")!!["id"] as String
        val import = ActualImport(writer)
        val preview = import.reconcile(account, rows, preview = true)
        assertEquals(
            cases
                .getValue("first")
                .jsonObject
                .getValue("added")
                .jsonPrimitive.int,
            preview.added.size,
        )
        assertEquals(
            listOf(
                ImportOutcome.UPDATE,
                ImportOutcome.UPDATE,
                ImportOutcome.UPDATE,
                ImportOutcome.RECONCILED,
                ImportOutcome.ADD,
                ImportOutcome.ADD,
                ImportOutcome.ADD,
            ),
            preview.outcomes,
        )
        assertEquals(4L, db.first("SELECT COUNT(*) AS n FROM v_transactions WHERE amount < 0")!!["n"])
        val first = import.reconcile(account, rows)
        assertEquals(
            cases
                .getValue("first")
                .jsonObject
                .getValue("added")
                .jsonPrimitive.int,
            first.added.size,
        )
        assertEquals(
            cases
                .getValue("first")
                .jsonObject
                .getValue("updated")
                .jsonPrimitive.int,
            first.updated.size,
        )
        val again = import.reconcile(account, rows)
        assertEquals(0, again.added.size)
        assertEquals(0, again.updated.size)

        val actual =
            db
                .query(
                    "SELECT t.date, t.amount, p.name AS payee, t.notes, t.imported_id, t.imported_payee, t.cleared, t.reconciled " +
                        "FROM v_transactions t LEFT JOIN payees p ON p.id = t.payee WHERE t.account = ?",
                    account,
                ).map { row ->
                    listOf(
                        ActualMonths.dateString((row["date"] as Number).toLong()),
                        (row["amount"] as Number).toLong(),
                        row["payee"],
                        row["notes"],
                        row["imported_id"],
                        row["imported_payee"],
                        row["cleared"] == 1L,
                        row["reconciled"] == 1L,
                    )
                }.sortedBy { "${it[0]}${it[1]}${it[2]}" }
        val expected =
            cases
                .getValue("transactions")
                .jsonArray
                .map {
                    val t = it.jsonObject
                    listOf(
                        t.text("date"),
                        t.getValue("amount").jsonPrimitive.long,
                        t.text("payee"),
                        t.text("notes"),
                        t.text("imported_id"),
                        t.text("imported_payee"),
                        t["cleared"] != JsonNull && t.getValue("cleared").jsonPrimitive.booleanOrNull == true,
                        t["reconciled"] != JsonNull && t.getValue("reconciled").jsonPrimitive.booleanOrNull == true,
                    )
                }.sortedBy { "${it[0]}${it[1]}${it[2]}" }
        assertEquals(expected, actual)
    }

    @Test
    fun clearingASplitParentClearsItsChildren() {
        val (db, writer) = fixture()
        val account = db.first("SELECT id FROM accounts WHERE name = 'Checking'")!!["id"] as String
        ActualTransactions(writer).addTransactions(
            account,
            listOf(
                mapOf(
                    "date" to "2026-11-15",
                    "amount" to -3_000L,
                    "imported_id" to "split-1",
                    "cleared" to false,
                    "subtransactions" to listOf(mapOf("amount" to -1_000L), mapOf("amount" to -2_000L)),
                ),
            ),
            runTransfers = true,
        )
        val result =
            ActualBudgetEdits(writer, {
                "2026-11-21"
            }).importTransactions(account, listOf(ImportRow("2026-11-15", -3_000, "Split shop", importedId = "split-1", cleared = true)))
        assertEquals(3, result.updated.size)
        assertEquals(
            listOf(
                1L,
                1L,
            ),
            db.query("SELECT cleared FROM v_transactions WHERE parent_id IS NOT NULL AND account = ?", account).map {
                it["cleared"]
            },
        )
    }

    @Test
    fun titleCasesLikeActual() {
        assertEquals("The Lord of the Rings Shop", ActualImport.titleCase("the lord of the rings shop"))
        assertEquals("Acme Payroll (API)", ActualImport.titleCase("ACME PAYROLL (API)"))
        assertEquals("Shop - For You", ActualImport.titleCase("shop - for you"))
        assertEquals("GitHub Sponsors", ActualImport.titleCase("github sponsors"))
        assertEquals("Mcdonald's", ActualImport.titleCase("McDonald's"))
    }

    @Test
    fun datesAddAcrossMonthsAndYears() {
        assertEquals("2027-01-03", ActualDates.addDays("2026-12-27", 7))
        assertEquals("2024-02-29", ActualDates.addDays("2024-03-07", -7))
        assertEquals(1L, ActualDates.dayNumber(19700102) - ActualDates.dayNumber(19700101))
        assertEquals(20261121L, ActualDates.toInt("2026-11-21"))
    }
}
