package app.tandemforactual.data.actual

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive

class ActualNotesTest {
    private val today = "2026-11-21"

    private class Fixture(
        val db: ActualDatabase,
        val edits: ActualBudgetEdits,
    ) {
        fun categoryId(name: String) = db.first("SELECT id FROM categories WHERE name = ? AND tombstone = 0", name)!!["id"] as String

        fun note(id: String) = db.first("SELECT note FROM notes WHERE id = ?", id)?.get("note")

        fun messages() = db.first("SELECT count(*) AS n FROM messages_crdt WHERE dataset = 'notes'")!!["n"]

        fun recordNote(
            id: String,
            today: String,
            type: String = "category",
        ) = ActualBudgetReader(db)
            .read(today)
            .records
            .first { it.type == type && it.id == id }
            .body["note"]

        fun accountId() = db.first("SELECT id FROM accounts WHERE tombstone = 0 ORDER BY sort_order, name")!!["id"] as String

        fun groupId() = db.first("SELECT cat_group FROM categories WHERE name = 'Groceries' AND tombstone = 0")!!["cat_group"] as String
    }

    private fun fixture(): Fixture {
        val entries = ZipArchive.read(checkNotNull(javaClass.getResource("/actual/parity-envelope/budget.zip")).readBytes())
        val file =
            File.createTempFile("notes", ".sqlite").apply {
                writeBytes(entries.getValue("db.sqlite"))
                deleteOnExit()
            }
        val db = ActualDatabase(BundledSQLiteDriver().open(file.path))
        val store = ActualMessageStore(db)
        val clock = store.loadClock()
        store.resetNode(clock)
        val response = checkNotNull(javaClass.getResource("/actual/parity-envelope/sync-response.bin")).readBytes()
        val server = SyncProtocol.decodeResponse(response)
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
        val writer = ActualWriter(store, clock, System::currentTimeMillis) { UUID.randomUUID().toString() }
        return Fixture(db, ActualBudgetEdits(writer, { today }))
    }

    @Test
    fun aCategoryNoteIsSavedUnderTheCategoryIdAndRead() {
        val f = fixture()
        val groceries = f.categoryId("Groceries")
        assertNull(f.recordNote(groceries, today))
        f.edits.setCategoryNote(groceries, "  Weekly shop, not takeaway  ")
        assertEquals("Weekly shop, not takeaway", f.note(groceries))
        assertEquals(JsonPrimitive("Weekly shop, not takeaway"), f.recordNote(groceries, today))
        val sent = f.messages()
        f.edits.setCategoryNote(groceries, "Weekly shop, not takeaway")
        assertEquals(sent, f.messages())
        f.edits.setCategoryNote(groceries, "Weekly shop")
        assertEquals("Weekly shop", f.note(groceries))
    }

    @Test
    fun clearingANoteLeavesItEmptyAndOffTheRecord() {
        val f = fixture()
        val dining = f.categoryId("Dining")
        val sent = f.messages()
        f.edits.setCategoryNote(dining, " ")
        assertEquals(sent, f.messages())
        f.edits.setCategoryNote(dining, "Friday nights")
        f.edits.setCategoryNote(dining, "")
        assertEquals("", f.note(dining))
        assertNull(f.recordNote(dining, today))
    }

    @Test
    fun aMissingCategoryIsRejected() {
        val error = assertFailsWith<ActualEditException> { fixture().edits.setCategoryNote("missing", "x") }
        assertEquals("Category does not exist in this budget", error.message)
    }

    @Test
    fun anAccountNoteUsesActualsPrefixedId() {
        val f = fixture()
        val account = f.accountId()
        assertNull(f.recordNote(account, today, "account"))
        f.edits.setAccountNote(account, " Joint with Sam ")
        assertEquals("Joint with Sam", f.note("account-$account"))
        assertNull(f.note(account))
        assertEquals(JsonPrimitive("Joint with Sam"), f.recordNote(account, today, "account"))
        f.edits.setAccountNote(account, "")
        assertEquals("", f.note("account-$account"))
        assertNull(f.recordNote(account, today, "account"))
        val error = assertFailsWith<ActualEditException> { f.edits.setAccountNote("missing", "x") }
        assertEquals("Account does not exist in this budget", error.message)
    }

    @Test
    fun aGroupNoteIsSavedUnderTheGroupId() {
        val f = fixture()
        val group = f.groupId()
        f.edits.setCategoryGroupNote(group, "Day to day")
        assertEquals("Day to day", f.note(group))
        assertEquals(JsonPrimitive("Day to day"), f.recordNote(group, today, "category-group"))
        val sent = f.messages()
        f.edits.setCategoryGroupNote(group, " Day to day ")
        assertEquals(sent, f.messages())
        f.edits.setCategoryGroupNote(group, " ")
        assertNull(f.recordNote(group, today, "category-group"))
        val error = assertFailsWith<ActualEditException> { f.edits.setCategoryGroupNote("missing", "x") }
        assertEquals("Category group does not exist in this budget", error.message)
    }

    @Test
    fun aMonthNoteIsSavedUnderTheBudgetMonthId() {
        val f = fixture()
        val month =
            ActualBudgetReader(f.db)
                .read(today)
                .records
                .first { it.type == "budget-month" }
                .id
        f.edits.setMonthNote(month, "Holiday month")
        assertEquals("Holiday month", f.note("budget-$month"))
        assertEquals(JsonPrimitive("Holiday month"), f.recordNote(month, today, "budget-month"))
        assertEquals(JsonPrimitive("Holiday month"), ActualBudgetReader(f.db).budgetMonth(today, month)!!["note"])
        f.edits.setMonthNote(month, "")
        assertNull(f.recordNote(month, today, "budget-month"))
        listOf("2026-13", "2026-1", "budget-2026-11").forEach {
            val error = assertFailsWith<ActualEditException> { f.edits.setMonthNote(it, "x") }
            assertEquals("Choose a month in this budget", error.message)
        }
    }
}
