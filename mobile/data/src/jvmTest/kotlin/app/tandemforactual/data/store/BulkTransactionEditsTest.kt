package app.tandemforactual.data.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

class BulkTransactionEditsTest {
    private fun body(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    private fun target(
        id: String,
        json: String,
        parent: BulkTarget? = null,
    ) = BulkTarget(id, body("""{"id":"$id",$json}"""), parent)

    private val split = target("split", """"account":"checking","cleared":false,"isParent":true,"category":null""")
    private val lineA = target("a", """"account":"checking","isChild":true,"category":"food"""", split)
    private val lineB = target("b", """"account":"checking","isChild":true,"category":"rent"""", split)
    private val plain = target("t", """"account":"checking","amount":-500,"cleared":true,"category":"food","notes":"lunch"""")
    private val reconciled = target("r", """"account":"checking","amount":-500,"cleared":false,"reconciled":true""")
    private val transfer = target("x", """"account":"checking","payee":"to-savings","category":null""")
    private val toBrokerage = target("y", """"account":"checking","payee":"to-brokerage","category":null""")
    private val context =
        BulkEditContext(
            offBudgetAccounts = setOf("brokerage"),
            transferAccountByPayee = mapOf("to-savings" to "savings", "to-brokerage" to "brokerage"),
        )

    private fun plan(
        edit: BulkTransactionEdit,
        vararg selected: BulkTarget,
    ) = planBulkEdit(selected.toList(), edit, context)

    private fun BulkEditPlan.fields() = changes.associate { (target, fields) -> target.id to fields }

    @Test
    fun categoriesSkipSplitsOffBudgetAccountsAndTransfersBetweenBudgetAccounts() {
        val offBudget = target("o", """"account":"brokerage","category":null""")
        val result = plan(BulkTransactionEdit.SetCategory("bills"), lineA, split, plain, transfer, toBrokerage, offBudget)
        assertEquals(setOf("a", "t", "y"), result.fields().keys)
        assertEquals(JsonPrimitive("bills"), result.fields().getValue("y")["category"])
        assertEquals(3, result.skipped)
        assertEquals(JsonNull, plan(BulkTransactionEdit.SetCategory(null), plain).fields().getValue("t")["category"])
        assertEquals(1, planBulkEdit(listOf(plain), BulkTransactionEdit.SetCategory("food")).skipped)
    }

    @Test
    fun clearedAndAccountBelongToTheSplitAndSkipReconciledOrUnchangedOnes() {
        val cleared = plan(BulkTransactionEdit.SetCleared(true), lineA, lineB, reconciled, plain)
        assertEquals(mapOf("split" to JsonObject(mapOf("cleared" to JsonPrimitive(true)))), cleared.fields())
        assertEquals(2, cleared.skipped)
        assertTrue(bulkClearedValue(listOf(lineA, plain)))
        assertFalse(bulkClearedValue(listOf(plain)))
        assertEquals(1, bulkReconciledCount(listOf(reconciled, plain, lineA, lineB)))

        val moved = plan(BulkTransactionEdit.MoveToAccount("brokerage"), lineA, plain, transfer, toBrokerage).fields()
        assertEquals(setOf("account", "reconciled"), moved.getValue("split").keys)
        assertEquals(JsonNull, moved.getValue("t")["category"])
        assertEquals(setOf("split", "t", "x"), moved.keys)
        assertTrue(plan(BulkTransactionEdit.MoveToAccount("checking"), plain).changes.isEmpty())
        assertEquals(setOf("account", "reconciled"), plan(BulkTransactionEdit.MoveToAccount("savings"), plain).fields().getValue("t").keys)
    }

    @Test
    fun payeesSchedulesAndTagsApplyToEachLine() {
        val payee = plan(BulkTransactionEdit.SetPayee("cafe"), lineA, lineB).fields()
        assertEquals(setOf("a", "b"), payee.keys)
        assertEquals(JsonPrimitive("sched"), plan(BulkTransactionEdit.LinkSchedule("sched"), plain).fields().getValue("t")["schedule"])
        assertEquals(JsonPrimitive("lunch #work"), plan(BulkTransactionEdit.AddTag("#work"), plain).fields().getValue("t")["notes"])
        val tagged = target("n", """"notes":"#Work"""")
        assertEquals(JsonNull, plan(BulkTransactionEdit.RemoveTag("work"), tagged).fields().getValue("n")["notes"])
    }

    @Test
    fun tagsAreAddedOnceAndRemovedWherePresent() {
        assertEquals("#work", addNoteTag(null, "work"))
        assertEquals("#work", addNoteTag("  ", "#work"))
        assertEquals("lunch #work", addNoteTag("lunch ", "work"))
        assertEquals("lunch #Work", addNoteTag("lunch #Work", "work"))
        assertEquals("lunch", addNoteTag("lunch", "two words"))
        assertEquals("lunch", addNoteTag("lunch", "##"))
        assertNull(noteTagName("  "))
        assertEquals(listOf("a", "y", "b"), noteTags("#a x#y #b ##c"))
        assertEquals("lunch with team", removeNoteTag("lunch #work with #work team", "work"))
        assertEquals("lunch #workday", removeNoteTag("lunch #workday", "work"))
        assertEquals("lunch#next", removeNoteTag("lunch#work#next", "work"))
        assertNull(removeNoteTag(null, "work"))
        assertEquals("lunch", removeNoteTag("lunch", ""))
    }

    @Test
    fun mergeKeepsTheImportedOneAndFillsItsGaps() {
        val manual =
            target("m", """"amount":-500,"payee":"cafe","category":null,"notes":"receipt","cleared":true,"reconciled":false""")
        val bank =
            target(
                "k",
                """"amount":-500,"importedId":"bank-1","payee":null,"category":"food","notes":null,"cleared":false,"reconciled":false""",
            )
        val plan = checkNotNull(planBulkMerge(listOf(manual, bank)).first)
        assertEquals("k", plan.keep.id)
        assertEquals("m", plan.drop.id)
        assertEquals(
            JsonObject(mapOf("payee" to JsonPrimitive("cafe"), "notes" to JsonPrimitive("receipt"), "cleared" to JsonPrimitive(true))),
            plan.keepFields,
        )
        val file = target("f", """"amount":-500,"importedPayee":"CAFE 123"""")
        assertEquals("f", planBulkMerge(listOf(manual, file)).first?.keep?.id)
        assertEquals("m", planBulkMerge(listOf(manual, target("n", """"amount":-500""""))).first?.keep?.id)
        assertEquals("k", planBulkMerge(listOf(bank, file)).first?.keep?.id)

        assertEquals(BulkMergeIssue.NOT_TWO, planBulkMerge(listOf(manual)).second)
        assertEquals(BulkMergeIssue.AMOUNTS_DIFFER, planBulkMerge(listOf(manual, target("z", """"amount":-1""""))).second)
        assertEquals(BulkMergeIssue.SPLIT, planBulkMerge(listOf(manual, target("c", """"amount":-500,"isChild":true""""))).second)
        assertEquals(BulkMergeIssue.SPLIT, planBulkMerge(listOf(manual, target("l", """"amount":-500""", split))).second)
    }

    @Test
    fun duplicatesCopyEachSplitOnceAndOneSideOfATransfer() {
        val other = target("o", """"transferId":"x"""")
        val linked = target("x2", """"transferId":"q"""")
        val counterpartOfLinked = target("q", """"transferId":"x2"""")
        val sources = bulkDuplicateSources(listOf(lineA, lineB, plain, transfer, other, linked, counterpartOfLinked))
        assertEquals(listOf("split", "t", "x", "x2"), sources.map { it.id })

        val copy = bulkDuplicatePatch(plain.body)
        assertEquals(setOf("account", "amount", "date", "payee", "category", "notes", "cleared"), copy.keys)
        assertEquals(JsonPrimitive(true), copy["cleared"])
        val lines = Json.parseToJsonElement("""[{"id":"a","amount":-300,"category":"food"},{"id":"b","amount":-200,"notes":"n"}]""")
        val splitCopy = bulkDuplicatePatch(JsonObject(split.body + ("subtransactions" to lines)))
        assertEquals(JsonNull, splitCopy["category"])
        assertEquals(JsonPrimitive(false), splitCopy["cleared"])
        assertEquals(setOf("amount", "category", "notes"), (splitCopy["subtransactions"] as JsonArray)[0].jsonObject.keys)
    }

    @Test
    fun bulkWritesCarryTheirActionInTheirKey() {
        val key = bulkIdempotencyKey("group-1", "op-1")
        assertEquals("group-1", bulkGroupOf(key))
        assertNull(bulkGroupOf("op-1"))
        assertNull(bulkGroupOf("bulk::op-1"))
        assertNull(bulkGroupOf("bulk:only"))
    }
}
