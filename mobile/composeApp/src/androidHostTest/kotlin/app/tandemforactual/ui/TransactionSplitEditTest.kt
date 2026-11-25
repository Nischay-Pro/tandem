package app.tandemforactual.ui

import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.ui.core.session.transactions.SplitEdit
import app.tandemforactual.ui.core.session.transactions.splitEditPatch
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.SplitEditLine
import app.tandemforactual.ui.core.state.SplitLine
import app.tandemforactual.ui.core.state.TransactionSplitDraft
import app.tandemforactual.ui.core.state.findSplit
import app.tandemforactual.ui.core.state.loadSplitForEdit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

class TransactionSplitEditTest {
    private val parentJson =
        """{"id":"tandem:p","amount":-5000,"payee":"store","notes":"weekly","isParent":true,"subtransactions":[""" +
            """{"id":"c1","amount":-3000,"category":"food"},{"id":"c2","amount":-2000,"category":"home"}]}"""

    private fun record(
        id: String,
        json: String,
        parent: Boolean = false,
    ) = BudgetRecordEntity(
        budgetId = "budget",
        recordType = "transaction",
        recordId = id,
        remoteJson = json,
        remoteRevision = "revision",
        localJson = null,
        localDeleted = false,
        remoteDeleted = false,
        syncGeneration = "generation",
        recordDate = "2026-11-24",
        accountId = "checking",
        categoryId = null,
        payeeId = "store",
        amountMinor = -5000,
        budgetMonth = null,
        notes = "weekly",
        isParent = parent,
    )

    private val records =
        listOf(
            record("tandem:p", parentJson, parent = true),
            record("c1", """{"id":"c1","amount":-3000,"category":"food","isChild":true}"""),
            record("plain", """{"id":"plain","amount":-100}"""),
        )
    private val names = mapOf("food" to "Food", "home" to "Home")
    private val format = { minor: Long -> "%.2f".format(minor / 100.0) }

    @Test
    fun findsTheWholeSplitFromAnyPart() {
        val fromChild = findSplit(records, "c1", names::get, format)!!
        assertEquals("tandem:p", fromChild.parent.recordId)
        assertEquals(
            listOf(SplitLine(1, "30.00", "food", "Food", "c1"), SplitLine(2, "20.00", "home", "Home", "c2")),
            fromChild.lines,
        )
        assertEquals(fromChild, findSplit(records, "tandem:p", names::get, format))
        assertNull(findSplit(records, "plain", names::get, format))
        assertNull(findSplit(listOf(record("tandem:p", """{"subtransactions":[]}""", parent = true)), "tandem:p", names::get, format))
    }

    @Test
    fun loadsTheParentIntoTheEditForm() {
        val state = AppState()
        val split = findSplit(records, "c1", names::get, format)!!
        loadSplitForEdit(state, split, "Store", format)
        assertEquals("tandem:p", state.selectedTransactionRecordId)
        assertEquals(-5000, state.selectedTransactionAmountMinor)
        assertEquals("50.00", state.transactionAmount)
        assertEquals("weekly", state.transactionNotes)
        assertEquals("Store", state.transactionPayee)
        assertEquals("", state.newTransactionCategoryId)
        assertEquals(TransactionSplitDraft(split.lines), state.transactionSplit)
        assertEquals(state.transactionSplit, state.selectedTransactionSplit)
    }

    @Test
    fun sendsTheLinesAndOnlyTheParentFieldsThatChanged() {
        val base = Json.parseToJsonElement(parentJson).jsonObject
        val parse = { text: String -> text.toBigDecimalOrNull()?.movePointRight(2)?.toLong() }
        val lines =
            TransactionSplitDraft(listOf(SplitLine(1, "30", "food", id = "c1"), SplitLine(2, "25", "home")))
                .editedLines(parse, income = false)
        assertEquals(listOf(SplitEditLine("c1", -3000, "food"), SplitEditLine(null, -2500, "home")), lines)
        val patch = splitEditPatch(base, SplitEdit("tandem:p", lines, -5500, "weekly", "store"))
        assertEquals(setOf("subtransactions", "amount"), patch.keys)
        assertEquals(JsonPrimitive(-5500), patch["amount"])

        val unsplit = splitEditPatch(base, SplitEdit("tandem:p", emptyList(), -5000, " ", "other", unsplitCategoryId = "food"))
        assertEquals(setOf("subtransactions", "notes", "payee", "category"), unsplit.keys)
        assertEquals(JsonNull, unsplit["notes"])
        assertEquals(JsonPrimitive("food"), unsplit["category"])
    }
}
