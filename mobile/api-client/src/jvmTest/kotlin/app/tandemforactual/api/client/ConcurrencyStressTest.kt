package app.tandemforactual.api.client

import app.tandemforactual.data.actual.OperationStatus
import app.tandemforactual.data.actual.createPayee
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Hammers one budget from many coroutines at once and checks nothing is lost, doubled or left half-written. */
class ConcurrencyStressTest {
    private suspend fun signedIn(server: FakeActualServer = FakeActualServer()): TandemApiClient =
        TandemApiClient(http = server.http, budgetFiles = TempBudgetFiles(), today = { "2026-11-21" }).also {
            it.signInWithPassword("https://budget.example", "test", "Phone")
        }

    private fun create(
        account: String,
        amount: Long,
    ): JsonObject =
        buildJsonObject {
            put("account", account)
            put("amount", amount)
            put("date", "2026-11-20")
            put("payee", null as String?)
            put("category", null as String?)
            put("notes", "stress")
            put("cleared", false)
        }

    private suspend fun ActualDirectBudget.checking() =
        snapshot("2026-11-21").records.first { it.type == "account" && it.body["name"] == JsonPrimitive("Checking") }.id

    private suspend fun ActualDirectBudget.transactions() = snapshot("2026-11-21").records.filter { it.type == "transaction" }

    @Test
    fun parallelEditsAndSyncsAllLandExactlyOnce(): Unit =
        runBlocking(Dispatchers.Default) {
            val server = FakeActualServer()
            val client = signedIn(server)
            val budget = client.budget("group").also { it.sync() }
            val checking = budget.checking()
            val before = budget.transactions().size
            val ids = List(60) { "tandem:${UUID.randomUUID()}" }
            coroutineScope {
                val writes =
                    ids.mapIndexed { index, id ->
                        async {
                            budget.submit(UUID.randomUUID().toString(), id, "create", create(checking, -(index + 1L)), null)
                        }
                    }
                val payees = List(15) { n -> async { client.edit("group") { it.createPayee("Stress payee $n") } } }
                val syncs = List(15) { launch { budget.sync() } }
                assertTrue(writes.awaitAll().all { it.status == OperationStatus.COMMITTED })
                payees.awaitAll()
                syncs.forEach { it.join() }
            }
            budget.sync()
            val records = budget.transactions()
            assertEquals(before + ids.size, records.size)
            assertEquals(ids.toSet(), records.map { it.id }.filter { it in ids }.toSet())
            val amounts = records.filter { it.id in ids }.map { (it.body["amount"] as JsonPrimitive).content.toLong() }.sorted()
            assertEquals((1L..60L).map { -it }.sorted(), amounts)
            val names =
                budget
                    .snapshot("2026-11-21")
                    .records
                    .filter { it.type == "payee" }
                    .map { it.body["name"] }
            repeat(15) { assertEquals(1, names.count { name -> name == JsonPrimitive("Stress payee $it") }) }

            val fresh = signedIn(server).budget("group")
            fresh.sync()
            assertEquals(records.map { it.id to it.body }.toSet(), fresh.transactions().map { it.id to it.body }.toSet())
        }

    @Test
    fun racingRetriesOfOneOperationWriteOnce(): Unit =
        runBlocking(Dispatchers.Default) {
            val budget = signedIn().budget("group")
            val checking = budget.checking()
            repeat(5) { round ->
                val operationId = UUID.randomUUID().toString()
                val id = "tandem:${UUID.randomUUID()}"
                val outcomes =
                    List(25) { async { budget.submit(operationId, id, "create", create(checking, -100L - round), null) } }.awaitAll()
                assertEquals(1, outcomes.toSet().size, "every retry must see the first outcome")
                assertEquals(1, budget.transactions().count { it.id == id })
            }
        }

    @Test
    fun cancelledSyncsAndEditsLeaveTheBudgetConsistent(): Unit =
        runBlocking(Dispatchers.Default) {
            val server = FakeActualServer()
            val client = signedIn(server)
            val budget = client.budget("group")
            val checking = budget.checking()
            val committed = mutableSetOf<String>()
            repeat(40) { round ->
                val id = "tandem:${UUID.randomUUID()}"
                val job =
                    launch {
                        val outcome = budget.submit(UUID.randomUUID().toString(), id, "create", create(checking, -round - 1L), null)
                        synchronized(committed) { if (outcome.status == OperationStatus.COMMITTED) committed += id }
                        budget.sync()
                    }
                repeat(round % 4) { yield() }
                job.cancel()
                job.join()
            }
            budget.sync()
            val ids = budget.transactions().map { it.id }.toSet()
            assertTrue(ids.containsAll(committed), "a write reported as saved is missing")
            val other = signedIn(server).budget("group")
            other.sync()
            assertEquals(budget.transactions().map { it.id to it.body }.toSet(), other.transactions().map { it.id to it.body }.toSet())
        }
}
