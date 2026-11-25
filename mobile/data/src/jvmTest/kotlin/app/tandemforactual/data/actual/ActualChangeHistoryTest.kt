package app.tandemforactual.data.actual

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActualChangeHistoryTest {
    private var now = 1_800_000_000_000L

    private fun writer(): ActualWriter {
        val resource = checkNotNull(javaClass.getResource("/actual/import-parity/budget.zip")).readBytes()
        val file = File.createTempFile("history", ".sqlite").apply { writeBytes(ZipArchive.read(resource).getValue("db.sqlite")) }
        file.deleteOnExit()
        val store = ActualMessageStore(ActualDatabase(BundledSQLiteDriver().open(file.path)))
        return ActualWriter(store, store.loadClock(), { now }) { UUID.randomUUID().toString() }
    }

    private fun ActualWriter.value(
        table: String,
        id: String,
        column: String,
    ) = db.first("SELECT $column FROM $table WHERE id = ?", id)?.get(column)

    @Test
    fun readsEditsAsChangeSetsAndUndoesThem() {
        val writer = writer()
        val history = ActualChangeHistory(writer)
        val payee =
            writer.batch {
                writer.insert("payees", mapOf("name" to "Cafe")).also {
                    writer.insert("payee_mapping", mapOf("id" to it, "targetId" to it))
                }
            }
        val account = writer.db.first("SELECT id FROM accounts WHERE tombstone = 0")!!["id"] as String
        now += 1_000
        val transaction =
            writer.insert(
                "transactions",
                mapOf("acct" to account, "amount" to -500L, "date" to 20261101L, "notes" to null, "description" to payee),
            )
        now += 1_000
        writer.batch {
            writer.update("payees", payee, mapOf("name" to "Corner Cafe"))
            writer.update("transactions", transaction, mapOf("amount" to -750L, "notes" to "lunch"))
        }

        val recent = history.recent()
        val edit = recent.first()
        assertEquals(2, edit.rows.size)
        assertEquals(
            FieldChange("name", CrdtValue.Text("Cafe"), CrdtValue.Text("Corner Cafe"), edit.rows[0].fields[0].timestamp),
            edit.rows[0].fields[0],
        )
        assertFalse(edit.rows[0].created)
        assertEquals("Corner Cafe", edit.rows[0].current["name"])
        assertFalse(RowChange("notes", "n", listOf(FieldChange("note", null, CrdtValue.Text("x"), "t"))).created)
        assertFalse(RowChange("payees", "p", listOf(FieldChange("name", null, CrdtValue.Text("x"), "t"))).created)
        assertFalse(RowChange("transactions", "t", listOf(FieldChange("amount", null, CrdtValue.Number(1.0), "t"))).created)
        assertTrue(recent[2].rows.first { it.dataset == "payees" }.created)
        assertFalse(recent[2].rows.first { it.dataset == "payees" }.deleted)

        assertEquals(UndoResult(3, 0), history.undo(edit))
        assertEquals("Cafe", writer.value("payees", payee, "name"))
        assertEquals(-500L, writer.value("transactions", transaction, "amount"))
        assertNull(writer.value("transactions", transaction, "notes"))
        assertEquals(UndoResult(0, 3), history.undo(edit))

        now += 1_000
        assertEquals(UndoResult(1, 0), history.undo(recent[2]))
        assertEquals(1L, writer.value("payees", payee, "tombstone"))
        assertTrue(
            history
                .recent()
                .first()
                .rows
                .single()
                .deleted,
        )
        assertEquals(UndoResult(0, 1), history.undo(recent[2]))
        assertEquals(1, history.recent(limit = 1).size)
    }

    @Test
    fun undoingEditsToRowsFromTheDownloadedBudgetRevertsThemInsteadOfDeleting() {
        val writer = writer()
        val history = ActualChangeHistory(writer)
        val payee = writer.db.first("SELECT id FROM payees WHERE tombstone = 0 AND name IS NOT NULL")!!["id"] as String
        val transaction = writer.db.first("SELECT id, acct FROM transactions WHERE tombstone = 0")!!
        val otherAccount = "another-account"
        val category = writer.db.first("SELECT id FROM categories WHERE tombstone = 0")!!["id"] as String

        now += 1_000
        writer.batch {
            writer.update("payees", payee, mapOf("name" to "Renamed"))
            writer.update("transactions", transaction["id"] as String, mapOf("acct" to otherAccount))
        }
        val moved = history.recent().first()
        assertTrue(moved.rows.none { it.created })
        assertEquals(UndoResult(0, 2), history.undo(moved))
        assertEquals(0L, writer.value("payees", payee, "tombstone"))
        assertEquals(0L, writer.value("transactions", transaction["id"] as String, "tombstone"))

        now += 1_000
        writer.insert("zero_budgets", mapOf("id" to "202611-$category", "month" to 202611L, "category" to category, "amount" to 5_000L))
        assertEquals(UndoResult(1, 2), history.undo(history.recent().first()))
        assertEquals(0L, writer.value("zero_budgets", "202611-$category", "amount"))

        now += 1_000
        writer.delete("payees", payee)
        assertEquals(UndoResult(1, 0), history.undo(history.recent().first()))
        assertEquals(0L, writer.value("payees", payee, "tombstone"))

        writer.db.execute(
            "INSERT INTO messages_crdt (timestamp, dataset, row, column, value) VALUES (?, 'payees', ?, 'favorite', 'X:unreadable')",
            "1970-01-01T00:00:00.000Z-0000-0000000000000001",
            payee,
        )
        now += 1_000
        writer.update("payees", payee, mapOf("favorite" to 1L))
        assertEquals(UndoResult(0, 1), history.undo(history.recent().first()))
    }

    @Test
    fun keepsAnEditTogetherWhenTheClockTicksDuringIt() {
        val ticking = writer()
        val ticks = ActualWriter(ticking.store, ticking.clock, { now++ }) { UUID.randomUUID().toString() }
        val payee = ticks.insert("payees", mapOf("name" to "Cafe", "favorite" to 0L))
        ticks.atOneMoment {
            ticks.update("payees", payee, mapOf("name" to "Corner Cafe"))
            ticks.atOneMoment { ticks.update("payees", payee, mapOf("favorite" to 1L)) }
        }
        val edit = ActualChangeHistory(ticks).recent().first()
        assertEquals(
            listOf("name", "favorite"),
            edit.rows
                .single()
                .fields
                .map { it.column },
        )
        assertEquals(2, ActualChangeHistory(ticks).undo(edit).reverted)
    }
}
