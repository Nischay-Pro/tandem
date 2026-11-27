package app.tandemforactual.data.actual

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.tandemforactual.data.store.BulkEditContext
import app.tandemforactual.data.store.BulkTarget
import app.tandemforactual.data.store.BulkTransactionEdit
import app.tandemforactual.data.store.bulkDuplicatePatch
import app.tandemforactual.data.store.bulkDuplicateSources
import app.tandemforactual.data.store.planBulkEdit
import app.tandemforactual.data.store.planBulkMerge
import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** Bulk edits planned on the device and sent through Actual's transaction operations, one write at a time. */
class ActualBulkEditsTest {
    private class Budget(
        val db: ActualDatabase,
        val writer: ActualWriter,
        val clock: () -> Unit,
    ) {
        val operations = ActualTransactionOperations(writer)
        val reader = ActualBudgetReader(db)
        val history = ActualChangeHistory(writer)
        val checking = account("Checking")
        val savings = account("Savings")
        val savingsPayee = db.first("SELECT id FROM payees WHERE transfer_acct = ?", savings)?.get("id") as String
        val groceries = db.first("SELECT id FROM categories WHERE name = 'Groceries'")?.get("id") as String

        fun account(name: String) = db.first("SELECT id FROM accounts WHERE name = ?", name)?.get("id") as String

        fun create(
            payee: String? = null,
            notes: String? = null,
        ): String {
            val id = "tandem:${UUID.randomUUID()}"
            val patch =
                buildJsonObject {
                    put("account", checking)
                    put("amount", -1234)
                    put("date", "2026-11-21")
                    put("payee", payee)
                    put("category", if (payee == null) groceries else null)
                    put("notes", notes)
                    put("cleared", false)
                }
            assertEquals(OperationStatus.COMMITTED, submit(id, "create", patch).status)
            return id
        }

        fun target(id: String): BulkTarget {
            val body = checkNotNull(reader.transaction(id))
            val parent = (body["parentId"] as? JsonPrimitive)?.contentOrNull?.let(::target)
            return BulkTarget(id, body, parent)
        }

        fun submit(
            id: String,
            action: String,
            patch: JsonObject,
            base: JsonObject? = null,
            group: String? = null,
        ): OperationOutcome {
            clock()
            return operations.submit(UUID.randomUUID().toString(), id, action, patch, base, group)
        }

        fun apply(
            changes: List<Pair<BulkTarget, JsonObject>>,
            group: String = UUID.randomUUID().toString(),
        ) = changes.map { (target, fields) -> submit(target.id, "update", fields, target.body, group).status }
    }

    private fun withBudget(block: Budget.() -> Unit) {
        val path = File.createTempFile("actual-bulk", ".sqlite")

        fun fixture(name: String) = checkNotNull(javaClass.getResource("/actual/parity-envelope/$name")).readBytes()
        path.writeBytes(ZipArchive.read(fixture("budget.zip")).getValue("db.sqlite"))
        val db = ActualDatabase(BundledSQLiteDriver().open(path.path))
        try {
            var now = 1_795_255_200_000L
            val store = ActualMessageStore(db)
            val clock = store.loadClock()
            store.resetNode(clock)
            val response = SyncProtocol.decodeResponse(fixture("sync-response.bin"))
            val engine =
                ActualSyncEngine(store, {
                    SyncResponse(response.messages.filter { msg -> msg.timestamp > it.since }, response.merkle)
                }, "group", "file", now = { now })
            runBlocking { engine.fullSync(clock, null) }
            Budget(db, ActualWriter(store, clock, { now }) { UUID.randomUUID().toString() }) { now += 1_000 }.block()
        } finally {
            db.close()
            path.delete()
        }
    }

    @Test
    fun aBulkEditSentOneWriteAtATimeIsOneChangeThatUndoesInOneStep() =
        withBudget {
            val first = create(notes = "lunch")
            val second = create(notes = "coffee #work")
            val third = create(notes = "dinner")
            val plan = planBulkEdit(listOf(target(first), target(second), target(third)), BulkTransactionEdit.AddTag("work"))
            assertEquals(1, plan.skipped)
            assertEquals(listOf(OperationStatus.COMMITTED, OperationStatus.COMMITTED), apply(plan.changes))
            assertEquals(JsonPrimitive("lunch #work"), reader.transaction(first)?.get("notes"))
            assertEquals(JsonPrimitive("dinner #work"), reader.transaction(third)?.get("notes"))

            val edit = history.recent().first()
            assertEquals(2, edit.rows.size)
            history.undo(edit)
            assertEquals(JsonPrimitive("lunch"), reader.transaction(first)?.get("notes"))
            assertEquals(JsonPrimitive("dinner"), reader.transaction(third)?.get("notes"))
        }

    @Test
    fun writesOfDifferentActionsOrSentFarApartStaySeparateChanges() =
        withBudget {
            val first = create()
            val second = create()
            val notes = buildJsonObject { put("notes", "a") }
            submit(first, "update", notes, target(first).body, group = "one")
            submit(second, "update", notes, target(second).body, group = "two")
            val (latest, before) = history.recent().take(2)
            assertNotEquals(latest.millis, before.millis)

            val more = buildJsonObject { put("notes", "b") }
            submit(first, "update", more, target(first).body, group = "three")
            repeat(11 * 60) { clock() }
            submit(second, "update", more, target(second).body, group = "three")
            assertEquals(
                1,
                history
                    .recent()
                    .first()
                    .rows.size,
            )
            submit(first, "update", notes, target(first).body)
            submit(second, "update", notes, target(second).body, group = "three")
            assertEquals(
                1,
                history
                    .recent()
                    .first()
                    .rows.size,
            )
        }

    @Test
    fun clearingASplitLineClearsItsSplitAndEveryLine() =
        withBudget {
            val records = reader.read("2026-11-21").records.filter { it.type == "transaction" }
            val split = records.first { it.body.value("isParent") == true }
            db.execute("UPDATE transactions SET reconciled = 0 WHERE id = ? OR parent_id = ?", split.id, split.id)
            val line = target(records.first { it.body.value("parentId") == split.id }.id)
            val value = !(line.parent!!.body.value("cleared") as Boolean)
            val plan = planBulkEdit(listOf(line), BulkTransactionEdit.SetCleared(value))
            assertEquals(
                split.id,
                plan.changes
                    .single()
                    .first.id,
            )
            apply(plan.changes)
            val lines = reader.transaction(split.id)?.get("subtransactions") as JsonArray
            assertTrue(lines.all { (it as JsonObject)["cleared"] == JsonPrimitive(value) })
        }

    @Test
    fun movingAndRenamingTransfersKeepsTheirOtherSideInStep() =
        withBudget {
            val transfer = create(payee = savingsPayee)
            val counterpart = target(transfer).body.value("transferId") as String
            val context = BulkEditContext(transferAccountByPayee = mapOf(savingsPayee to savings))
            assertTrue(planBulkEdit(listOf(target(transfer)), BulkTransactionEdit.MoveToAccount(savings), context).changes.isEmpty())
            assertTrue(planBulkEdit(listOf(target(transfer)), BulkTransactionEdit.SetCategory(groceries), context).changes.isEmpty())

            val card = account("Card")
            apply(planBulkEdit(listOf(target(transfer)), BulkTransactionEdit.MoveToAccount(card), context).changes)
            val cardPayee = db.first("SELECT id FROM payees WHERE transfer_acct = ?", card)?.get("id") as String
            assertEquals(JsonPrimitive(cardPayee), reader.transaction(counterpart)?.get("payee"))

            apply(planBulkEdit(listOf(target(transfer)), BulkTransactionEdit.SetPayee(null)).changes)
            assertNull(reader.transaction(counterpart))
        }

    @Test
    fun linkingASchedulePassesToTheTransfersOtherSide() =
        withBudget {
            val transfer = create(payee = savingsPayee)
            val link = planBulkEdit(listOf(target(transfer)), BulkTransactionEdit.LinkSchedule("missing")).changes
            assertEquals(listOf(OperationStatus.REJECTED), apply(link))
            val schedule = writer.insert("schedules", mapOf("name" to "Rent", "rule" to "rule"))
            apply(planBulkEdit(listOf(target(transfer)), BulkTransactionEdit.LinkSchedule(schedule)).changes)
            val counterpart = target(transfer).body.value("transferId") as String
            assertEquals(JsonPrimitive(schedule), reader.transaction(transfer)?.get("schedule"))
            assertEquals(JsonPrimitive(schedule), reader.transaction(counterpart)?.get("schedule"))
        }

    @Test
    fun duplicatesCopySplitsAndTransfersAsActualDoes() =
        withBudget {
            val lines =
                listOf(-1000L, -234L).map { amount ->
                    buildJsonObject {
                        put("amount", amount)
                        put("category", groceries)
                    }
                }
            val splitId = "tandem:${UUID.randomUUID()}"
            val splitPatch =
                buildJsonObject {
                    put("account", checking)
                    put("amount", -1234)
                    put("date", "2026-11-21")
                    put("payee", null as String?)
                    put("category", null as String?)
                    put("notes", "split")
                    put("cleared", true)
                    put("subtransactions", JsonArray(lines))
                }
            submit(splitId, "create", splitPatch)
            val split = target(splitId)
            val lineId = ((split.body["subtransactions"] as JsonArray)[0] as JsonObject).value("id") as String
            val line = target(lineId)
            val transfer = create(payee = savingsPayee)
            val counterpart = target(transfer).body.value("transferId") as String
            val sources = bulkDuplicateSources(listOf(line, target(transfer), target(counterpart)))
            assertEquals(listOf(line.parent?.id, transfer), sources.map { it.id })
            val copies = sources.map { "tandem:${UUID.randomUUID()}".also { id -> submit(id, "create", bulkDuplicatePatch(it.body)) } }
            val splitCopy = checkNotNull(reader.transaction(copies[0]))
            assertEquals(
                (split.body["subtransactions"] as JsonArray).size,
                (splitCopy["subtransactions"] as JsonArray).size,
            )
            assertEquals(split.body["amount"], splitCopy["amount"])
            assertTrue(reader.transaction(copies[1])?.value("transferId") != null)
        }

    @Test
    fun mergingKeepsOneAndFillsItFromTheOther() =
        withBudget {
            val manual = create(notes = "from receipt")
            val other = create(payee = null)
            db.execute("UPDATE transactions SET imported_description = 'BANK' WHERE financial_id = ?", other)
            val plan = checkNotNull(planBulkMerge(listOf(target(manual), target(other))).first)
            assertEquals(other, plan.keep.id)
            submit(plan.keep.id, "update", plan.keepFields, plan.keep.body, group = "merge")
            submit(plan.drop.id, "delete", JsonObject(emptyMap()), plan.drop.body, group = "merge")
            assertEquals(JsonPrimitive("from receipt"), reader.transaction(other)?.get("notes"))
            assertNull(reader.transaction(manual))
            assertEquals(
                2,
                history
                    .recent()
                    .first()
                    .rows.size,
            )
        }
}
