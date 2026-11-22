package app.tandemforactual.data.actual

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ActualReaderOptionsTest {
    private fun fixtureDb(): ActualDatabase {
        val entries = ZipArchive.read(checkNotNull(javaClass.getResource("/actual/parity-envelope/budget.zip")).readBytes())
        val file = File.createTempFile("actual-options", ".sqlite").apply { writeBytes(entries.getValue("db.sqlite")) }
        file.deleteOnExit()
        return ActualDatabase(BundledSQLiteDriver().open(file.path))
    }

    @Test
    fun readsAccountGroupsAndCurrencyPreferences() {
        val db = fixtureDb()
        db.execute("INSERT INTO account_groups (id, name, sort_order, tombstone) VALUES ('g1', 'Everyday', 1, 0)")
        db.execute("INSERT INTO preferences (id, value) VALUES ('flags.currency', 'true'), ('defaultCurrencyCode', 'EUR')")
        val euro = ActualBudgetReader(db).read("2026-11-21")
        assertEquals(JsonPrimitive("Everyday"), euro.records.single { it.type == "account-group" }.body["name"])
        assertEquals("EUR", euro.displaySettings.currencyCode)
        assertEquals("after", euro.displaySettings.currencySymbolPosition)
        db.execute(
            "INSERT INTO preferences (id, value) VALUES ('currencySymbolPosition', 'before'), ('currencySpaceBetweenAmountAndSymbol', 'true')",
        )
        val before = ActualBudgetReader(db).read("2026-11-21")
        assertEquals("before", before.displaySettings.currencySymbolPosition)
        assertTrue(before.displaySettings.currencySpaceBetweenAmountAndSymbol)
        val account =
            db
                .first(
                    "SELECT account FROM v_transactions WHERE amount > 0 AND starting_balance_flag = 0",
                )?.get("account") as String?
        if (account != null) assertTrue(ActualBudgetReader(db).inflowsByYear(account).values.all { it > 0 })
        assertEquals(emptyMap(), ActualBudgetReader(db).inflowsByYear("missing"))
        val id = db.first("SELECT id FROM transactions WHERE isParent = 0 AND tombstone = 0")!!["id"] as String
        db.execute("UPDATE transactions SET notes = 'Lunch, with \"Sam\"', reconciled = 1, cleared = 1 WHERE id = ?", id)
        val other = db.first("SELECT id FROM transactions WHERE isParent = 0 AND tombstone = 0 AND id != ?", id)?.get("id")
        if (other != null) db.execute("UPDATE transactions SET cleared = 1, reconciled = 0 WHERE id = ?", other)
        db.execute("UPDATE transactions SET cleared = 0, reconciled = 0 WHERE id NOT IN (?, ?) AND isParent = 0", id, other)
        db.execute("UPDATE transactions SET amount = -1234 WHERE id = ?", id)
        val csv = ActualBudgetReader(db).exportCsv("2000-01-01", "2099-12-31")
        assertTrue(csv.startsWith("Date,Account,Payee,Category,Amount,Notes,Status\n"))
        assertTrue("\"Lunch, with \"\"Sam\"\"\"" in csv)
        assertTrue(",-12.34," in csv && "Reconciled" in csv)
        assertTrue("Cleared" in csv.replace("Reconciled", "") || other == null)
        assertTrue("Not cleared" in csv || db.first("SELECT count(*) AS n FROM v_transactions")!!["n"] == 2L)
        db.close()
    }

    @Test
    fun emptyBudgetsStartFromTheCurrentMonth() {
        val db = fixtureDb()
        db.execute("DELETE FROM transactions")
        val months =
            ActualBudgetReader(db)
                .read("2026-11-21")
                .records
                .filter { it.type == "budget-month" }
                .map { it.id }
        assertEquals("2026-08", months.first())
        assertEquals("2027-10", months.last())
        db.close()
    }

    @Test
    fun rulesFollowActualValidation() {
        fun row(
            conditions: String?,
            actions: String? = """[{"op":"set","field":"notes","value":"x"}]""",
            stage: String? = null,
            op: String? = "and",
        ) = mapOf("id" to "r", "stage" to stage, "conditions_op" to op, "conditions" to conditions, "actions" to actions)
        val mappings = mapOf("old" to "new")
        val loaded =
            ActualRules
                .load(
                    listOf(
                        row(
                            """[{"op":"oneOf","field":"description","value":["old","new","x"]},{"op":"contains","field":"imported_description","value":"a","options":{"x":1}},{"op":"matches","field":"description","value":"o.*"}]""",
                            """[{"op":"set-split-amount","value":5,"options":{"splitIndex":1}},{"op":"append-notes","value":"n"},{"op":"delete-transaction"},{"op":"set","field":"category","value":"old"}]""",
                            stage = "modify",
                            op = null,
                        ),
                    ),
                    mappings,
                ).single()
        assertEquals(JsonPrimitive("pre"), loaded["stage"])
        assertTrue("conditionsOp" !in loaded)
        val conditions = loaded.getValue("conditions").jsonArray.map { it.jsonObject }
        assertEquals("[\"new\",\"x\"]", conditions[0].getValue("value").toString())
        assertEquals(JsonPrimitive("imported_payee"), conditions[1]["field"])
        val actions = loaded.getValue("actions").jsonArray.map { it.jsonObject }
        assertEquals(listOf("number", "id", null, "id"), actions.map { (it["type"] as JsonPrimitive?)?.content })
        assertEquals(JsonPrimitive("new"), actions[3]["value"])
        assertEquals(
            0,
            ActualRules
                .load(
                    listOf(
                        row(null),
                        row("not json"),
                        row("[1]"),
                        row("""[{"op":"is","field":"nope","value":1}]"""),
                        row("""[{"op":"onBudget","field":"payee","value":"a"}]"""),
                        row("""[{"field":"payee","value":"a"}]"""),
                        row("""[{"op":"is","field":"amount","value":null}]"""),
                        row("""[{"op":"oneOf","field":"payee","value":"a"}]"""),
                        row("[]", """[{"op":"explode"}]"""),
                        row("[]", """[{"op":"set","field":"nope","value":1}]"""),
                        row("[]", """[{"op":"set","field":"account","value":""}]"""),
                    ),
                    mappings,
                ).size,
        )
    }
}
