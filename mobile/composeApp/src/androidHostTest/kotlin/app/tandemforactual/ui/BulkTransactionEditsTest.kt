package app.tandemforactual.ui

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.store.BulkTransactionEdit
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.RemoteBudgetRecord
import app.tandemforactual.data.store.applyFullSnapshot
import app.tandemforactual.data.store.bulkGroupOf
import app.tandemforactual.data.store.readPendingWrites
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.transactions.applyBulkEdit
import app.tandemforactual.ui.core.session.transactions.deleteTransactionsWithUndo
import app.tandemforactual.ui.core.session.transactions.duplicateTransactions
import app.tandemforactual.ui.core.session.transactions.mergeTransactions
import app.tandemforactual.ui.core.session.transactions.updateTransaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Bulk actions from the activity selection: what they queue, how they group, and how one undo takes them back. */
@RunWith(RobolectricTestRunner::class)
class BulkTransactionEditsTest {
    private class Budget(
        val repository: LocalBudgetRepository,
        val session: TandemMobileSession,
    ) {
        suspend fun queued(): List<OutboxOperationEntity> = repository.readPendingWrites(BUDGET).filter { it.status == "queued" }

        suspend fun undo() = checkNotNull(session.transactions.undoNotice?.undo).invoke()
    }

    private fun withBudget(block: suspend Budget.() -> Unit) =
        runBlocking {
            val database =
                Room
                    .inMemoryDatabaseBuilder<TandemDatabase>()
                    .setDriver(AndroidSQLiteDriver())
                    .setQueryCoroutineContext(Dispatchers.IO)
                    .build()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                repository.applyFullSnapshot(BUDGET, "generation-1", "cursor-1", null, records())
                Budget(repository, TandemMobileSession(TandemApiClient(), database, { 100L })).block()
            } finally {
                database.close()
            }
        }

    private fun records() =
        listOf(
            record("t1", """"amount":-1000,"notes":"lunch","cleared":false"""),
            record("t2", """"amount":-1000,"notes":null,"cleared":false,"importedId":"bank-1"""),
            record("t3", """"amount":-500,"notes":null,"cleared":true,"reconciled":true"""),
            record(
                "p1",
                """"amount":-900,"cleared":false,"isParent":true,"category":null,""" +
                    """"subtransactions":[{"id":"c1","amount":-400,"category":"food"},{"id":"c2","amount":-500,"category":"rent"}]""",
            ),
            record("c1", """"amount":-400,"cleared":false,"isChild":true,"parentId":"p1","category":"food""""),
            record("c2", """"amount":-500,"cleared":false,"isChild":true,"parentId":"p1","category":"rent""""),
        )

    private fun record(
        id: String,
        fields: String,
    ) = RemoteBudgetRecord(
        budgetId = BUDGET,
        recordType = "transaction",
        recordId = id,
        revision = "revision-$id",
        bodyJson = """{"id":"$id","date":"2026-11-01","account":"checking",$fields}""",
        generation = "generation-1",
        recordDate = "2026-11-01",
        accountId = "checking",
        amountMinor = Json.parseToJsonElement("{$fields}").jsonObject["amount"]?.jsonPrimitive?.content?.toLong(),
        budgetMonth = "2026-11",
    )

    private fun OutboxOperationEntity.patch(): JsonObject = Json.parseToJsonElement(patchJson).jsonObject

    @Test
    fun aSplitLineIsClearedThroughItsSplitAndOneUndoTakesTheWholeActionBack() =
        withBudget {
            assertTrue(session.transactions.applyBulkEdit(BUDGET, listOf("c1", "c2", "t1", "t3"), BulkTransactionEdit.SetCleared(true)))
            val writes = queued()
            assertEquals(setOf("p1", "t1"), writes.map { it.recordId }.toSet())
            assertEquals(1, writes.mapNotNull { bulkGroupOf(it.idempotencyKey) }.distinct().size)
            assertEquals("true", writes.first().patch()["cleared"]?.jsonPrimitive?.content)
            assertEquals("Marked cleared · 2 transactions, 1 unchanged", session.transactions.undoNotice?.message)

            undo()
            assertTrue(queued().isEmpty())
        }

    @Test
    fun aBulkEditJoinsAnEditStillWaitingAndUndoPutsThatEditBack() =
        withBudget {
            assertTrue(session.transactions.updateTransaction(BUDGET, "t1", "10.00", "first", -1000L, "Spending"))
            val earlier = queued().single()
            assertTrue(session.transactions.applyBulkEdit(BUDGET, listOf("t1"), BulkTransactionEdit.AddTag("work")))
            val joined = queued().single()
            assertEquals(earlier.operationId, joined.operationId)
            assertEquals("first #work", joined.patch()["notes"]?.jsonPrimitive?.content)

            undo()
            assertEquals("first", queued().single().patch()["notes"]?.jsonPrimitive?.content)
        }

    @Test
    fun nothingIsQueuedWhenEveryTransactionAlreadyMatches() =
        withBudget {
            assertFalse(session.transactions.applyBulkEdit(BUDGET, listOf("t3"), BulkTransactionEdit.SetCleared(false)))
            assertNotNull(session.transactionWriteMessage)
            assertTrue(queued().isEmpty())
        }

    @Test
    fun duplicatingASplitLineCopiesTheWholeSplit() =
        withBudget {
            assertTrue(session.transactions.duplicateTransactions(BUDGET, listOf("c1", "c2", "t1")))
            val creates = queued()
            assertEquals(2, creates.size)
            assertTrue(creates.all { it.action == "CREATE" && it.recordId.startsWith("tandem:") })
            val split = creates.first { "subtransactions" in it.patch() }
            assertEquals(2, (split.patch()["subtransactions"] as JsonArray).size)

            undo()
            assertTrue(queued().isEmpty())
        }

    @Test
    fun mergingKeepsTheImportedTransactionAndDeletesTheOther() =
        withBudget {
            assertTrue(session.transactions.mergeTransactions(BUDGET, listOf("t1", "t2")))
            val writes = queued().associateBy { it.recordId }
            assertEquals("UPDATE", writes.getValue("t2").action)
            assertEquals("lunch", writes.getValue("t2").patch()["notes"]?.jsonPrimitive?.content)
            assertEquals("DELETE", writes.getValue("t1").action)
            assertEquals(bulkGroupOf(writes.getValue("t1").idempotencyKey), bulkGroupOf(writes.getValue("t2").idempotencyKey))

            assertFalse(session.transactions.mergeTransactions(BUDGET, listOf("t2", "t3")))
            assertEquals("Only transactions with the same amount can be merged.", session.transactionWriteMessage)
        }

    @Test
    fun deletingSeveralSendsThemAsOneChange() =
        withBudget {
            assertTrue(session.transactions.deleteTransactionsWithUndo(BUDGET, setOf("t1", "t2")))
            val groups = queued().map { bulkGroupOf(it.idempotencyKey) }
            assertEquals(2, groups.size)
            assertNotNull(groups.first())
            assertEquals(1, groups.distinct().size)
            assertTrue(session.transactions.deleteTransactionsWithUndo(BUDGET, setOf("t3")))
            assertNull(bulkGroupOf(queued().single { it.recordId == "t3" }.idempotencyKey))
        }

    private companion object {
        const val BUDGET = "budget-1"
    }
}
