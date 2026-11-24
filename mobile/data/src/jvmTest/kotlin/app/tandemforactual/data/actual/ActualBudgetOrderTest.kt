package app.tandemforactual.data.actual

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import java.util.UUID
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class ActualBudgetOrderTest {
    private fun letters(value: String) = value.map { it.toString() }

    private fun apply(
        order: List<String>,
        moves: List<OrderMove>,
    ): List<String> {
        val result = order.toMutableList()
        for (move in moves) {
            result.remove(move.id)
            if (move.beforeId == null) result += move.id else result.add(result.indexOf(move.beforeId), move.id)
        }
        return result
    }

    private fun lcs(
        a: List<String>,
        b: List<String>,
    ): Int {
        val table = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in 1..a.size) {
            for (j in 1..b.size) {
                table[i][j] =
                    if (a[i - 1] == b[j - 1]) table[i - 1][j - 1] + 1 else maxOf(table[i - 1][j], table[i][j - 1])
            }
        }
        return table[a.size][b.size]
    }

    @Test
    fun rebaseMatchesTheGatewayCases() {
        val base = letters("ABCDE")
        assertEquals(letters("BADEC"), BudgetOrderMerge.rebase(base, letters("BADEC"), base))
        assertEquals(letters("EBACD"), BudgetOrderMerge.rebase(base, letters("BACDE"), letters("EABCD")))
        assertEquals(letters("BCADE"), BudgetOrderMerge.rebase(base, letters("BCADE"), letters("BCDEA")))
        assertEquals(letters("DAXB"), BudgetOrderMerge.rebase(letters("ABCD"), letters("CDAB"), letters("AXBD")))
        assertFailsWith<ActualEditException> { BudgetOrderMerge.rebase(letters("ABC"), letters("ABD"), letters("ABC")) }
        assertFailsWith<ActualEditException> { BudgetOrderMerge.rebase(letters("ABC"), letters("AAB"), letters("ABC")) }
        val random = Random(7)
        repeat(500) {
            val items = List(1 + random.nextInt(12)) { "item-$it" }
            val desired = items.shuffled(random)
            assertEquals(desired, BudgetOrderMerge.rebase(items, desired, items))
            val live = (items.filter { random.nextDouble() > 0.2 } + listOf("created-1", "created-2")).shuffled(random)
            assertEquals(live.sorted(), BudgetOrderMerge.rebase(items, desired, live).sorted())
        }
    }

    @Test
    fun planUsesTheFewestMoves() {
        assertEquals(listOf(OrderMove("A", null)), BudgetOrderMerge.plan(letters("ABCDE"), letters("BCDEA")))
        assertEquals(emptyList(), BudgetOrderMerge.plan(letters("ABC"), letters("ABC")))
        val random = Random(3)
        repeat(500) {
            val current = List(1 + random.nextInt(15)) { "item-$it" }
            val target = current.shuffled(random)
            val moves = BudgetOrderMerge.plan(current, target)
            assertEquals(target, apply(current, moves))
            assertEquals(current.size - lcs(current, target), moves.size)
            val pinned = current.filter { random.nextDouble() < 0.3 }.toSet()
            val free = ArrayDeque(current.filter { it !in pinned }.shuffled(random))
            val pinnedTarget = current.map { if (it in pinned) it else free.removeFirst() }
            val pinnedMoves = BudgetOrderMerge.plan(current, pinnedTarget, pinned)
            assertFalse(pinnedMoves.any { it.id in pinned })
            assertEquals(pinnedTarget, apply(current, pinnedMoves))
        }
        assertFailsWith<ActualEditException> { BudgetOrderMerge.plan(letters("ABC"), letters("CBA"), setOf("A", "C")) }
        assertFailsWith<ActualEditException> { BudgetOrderMerge.plan(letters("AB"), letters("AC")) }
        assertFailsWith<ActualEditException> { BudgetOrderMerge.plan(letters("AA"), letters("AA")) }
    }

    @Test
    fun setOrdersReordersActualsLists() {
        val entries = ZipArchive.read(checkNotNull(javaClass.getResource("/actual/parity-envelope/budget.zip")).readBytes())
        val file =
            File.createTempFile("order", ".sqlite").apply {
                writeBytes(entries.getValue("db.sqlite"))
                deleteOnExit()
            }
        val db = ActualDatabase(BundledSQLiteDriver().open(file.path))
        val store = ActualMessageStore(db)
        val clock = store.loadClock()
        val server =
            SyncProtocol.decodeResponse(
                checkNotNull(javaClass.getResource("/actual/parity-envelope/sync-response.bin")).readBytes(),
            )
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
        val edits = ActualBudgetEdits(writer, { "2026-11-21" })

        fun ids(sql: String) = db.query(sql).map { it["id"] as String }

        fun change(
            base: List<String>,
            order: List<String>,
        ) = JsonObject(
            mapOf("baseOrder" to JsonArray(base.map { JsonPrimitive(it) }), "order" to JsonArray(order.map { JsonPrimitive(it) })),
        )
        val groups = ids("SELECT id FROM category_groups WHERE tombstone = 0 AND is_income = 0 ORDER BY sort_order, id")
        val income = ids("SELECT id FROM category_groups WHERE tombstone = 0 AND is_income = 1 ORDER BY sort_order, id")
        val everyday = db.first("SELECT id FROM category_groups WHERE name = 'Everyday'")!!["id"] as String
        val categories = ids("SELECT id FROM categories WHERE cat_group = '$everyday' AND tombstone = 0 ORDER BY sort_order, id")
        val open = ids("SELECT id FROM accounts WHERE tombstone = 0 AND closed = 0 AND offbudget = 0 ORDER BY sort_order, name")
        val closed = ids("SELECT id FROM accounts WHERE tombstone = 0 AND closed = 1 ORDER BY sort_order, name")
        val result =
            edits.setOrders(
                JsonObject(
                    mapOf(
                        "categoryGroups" to change(groups + income, groups.reversed() + income),
                        "categories" to
                            JsonArray(
                                listOf(JsonObject(change(categories, categories.reversed()) + ("groupId" to JsonPrimitive(everyday)))),
                            ),
                        "accounts" to change(open + closed, open.reversed() + closed),
                    ),
                ),
            )
        assertEquals(groups.reversed(), ids("SELECT id FROM category_groups WHERE tombstone = 0 AND is_income = 0 ORDER BY sort_order, id"))
        assertEquals(
            categories.reversed(),
            ids("SELECT id FROM categories WHERE cat_group = '$everyday' AND tombstone = 0 ORDER BY sort_order, id"),
        )
        assertEquals(
            open.reversed(),
            ids("SELECT id FROM accounts WHERE tombstone = 0 AND closed = 0 AND offbudget = 0 ORDER BY sort_order, name"),
        )
        assertEquals(open.reversed() + closed, result.getValue("accounts").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(
            JsonObject(emptyMap<String, kotlinx.serialization.json.JsonElement>()),
            edits.setOrders(
                JsonObject(
                    mapOf(
                        "categories" to JsonArray(emptyList()),
                    ),
                ),
            ),
        )
        assertFailsWith<ActualEditException> {
            edits.setOrders(
                JsonObject(
                    mapOf(
                        "categories" to
                            JsonArray(listOf(JsonObject(change(emptyList(), emptyList()) + ("groupId" to JsonPrimitive("missing"))))),
                    ),
                ),
            )
        }
        assertFailsWith<ActualEditException> {
            edits.setOrders(
                JsonObject(
                    mapOf(
                        "accounts" to JsonObject(mapOf("order" to JsonPrimitive("x"))),
                    ),
                ),
            )
        }
        assertEquals(
            JsonArray(emptyList()),
            edits.setOrders(JsonObject(mapOf("accounts" to change(listOf("missing"), listOf("missing"))))).getValue("accounts"),
        )
        val shut = listOf(edits.createAccount("Old A", false, null), edits.createAccount("Old B", false, null))
        shut.forEach { writer.update("accounts", it, mapOf("closed" to 1)) }
        val live = ids("SELECT id FROM accounts WHERE tombstone = 0 AND closed = 0 ORDER BY sort_order, name")
        val closedNow = ids("SELECT id FROM accounts WHERE tombstone = 0 AND closed = 1 ORDER BY sort_order, name")
        edits.setOrders(JsonObject(mapOf("accounts" to change(live + closedNow, live + closedNow.reversed()))))
        assertTrue(closedNow.size >= 2)
        assertEquals(closedNow.reversed(), ids("SELECT id FROM accounts WHERE tombstone = 0 AND closed = 1 ORDER BY sort_order, name"))
        val reader = ActualBudgetReader(db)
        val transaction = db.first("SELECT id FROM v_transactions LIMIT 1")!!["id"] as String
        assertEquals(transaction, reader.transactionRecord(transaction)?.id)
        assertEquals(null, reader.transactionRecord("missing"))
        db.close()
    }
}
