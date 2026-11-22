package app.tandemforactual.data.actual

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The gateway's `setOrders`: each list carries the order the device started from and the order the user chose;
 * only the user's moves are replayed onto the live order, and the result is read back to confirm it.
 */
fun ActualBudgetEdits.setOrders(batch: JsonObject): JsonObject =
    writer.batch {
        val result = LinkedHashMap<String, JsonElement>()
        (batch["categoryGroups"] as? JsonObject)?.let { change ->
            result["categoryGroups"] = JsonArray(setCategoryGroupOrder(change).map(::JsonPrimitive))
        }
        (batch["categories"] as? JsonArray)?.takeIf { it.isNotEmpty() }?.let { changes ->
            result["categories"] = setCategoryOrders(changes)
        }
        (batch["accounts"] as? JsonObject)?.let { change ->
            result["accounts"] = JsonArray(setAccountOrder(change).map(::JsonPrimitive))
        }
        JsonObject(result)
    }

private fun ActualBudgetEdits.ids(element: JsonElement?) =
    (element as? JsonArray)?.map { (it as JsonPrimitive).content }
        ?: fail("The order must contain the same items as the base order")

private fun confirm(
    actual: List<String>,
    expected: List<String>,
) = check(actual == expected) { "Actual did not save the requested order" }

private fun ActualBudgetEdits.orderedGroups() =
    db.query("SELECT id, is_income FROM category_groups WHERE tombstone = 0 ORDER BY sort_order, id")

private fun ActualBudgetEdits.setCategoryGroupOrder(change: JsonObject): List<String> {
    val targets = mutableListOf<String>()
    for (income in listOf(false, true)) {
        val section = orderedGroups().filter { truthy(it["is_income"]) == income }.map { it["id"] as String }
        val isIncome = orderedGroups().associate { it["id"] as String to truthy(it["is_income"]) }

        fun inSection(id: String) = isIncome[id] == income
        val target =
            BudgetOrderMerge.rebase(
                ids(change["baseOrder"]).filter(::inSection),
                ids(change["order"]).filter(::inSection),
                section,
            )
        BudgetOrderMerge.plan(section, target).forEach { move ->
            val peers = db.query("SELECT id, sort_order FROM category_groups WHERE tombstone = 0 ORDER BY sort_order, id")
            val (updates, order) = shove(orders(peers), move.beforeId)
            updates.forEach { writer.update("category_groups", it.id, mapOf("sort_order" to it.sortOrder)) }
            writer.update("category_groups", move.id, mapOf("sort_order" to order))
        }
        confirm(orderedGroups().filter { truthy(it["is_income"]) == income }.map { it["id"] as String }, target)
        targets += target
    }
    return targets
}

private fun ActualBudgetEdits.setCategoryOrders(changes: JsonArray): JsonArray {
    val groupIds = orderedGroups().map { it["id"] as String }.toSet()
    return JsonArray(
        changes.map { element ->
            val change = element as JsonObject
            val groupId = (change["groupId"] as JsonPrimitive).content
            if (groupId !in groupIds) fail("Category group does not exist in this budget")

            fun live() =
                db
                    .query(
                        "SELECT id FROM categories WHERE cat_group = ? AND tombstone = 0 ORDER BY sort_order, id",
                        groupId,
                    ).map {
                        it["id"] as String
                    }
            val target = BudgetOrderMerge.rebase(ids(change["baseOrder"]), ids(change["order"]), live())
            BudgetOrderMerge.plan(live(), target).forEach { moveCategory(it.id, groupId, it.beforeId) }
            confirm(live(), target)
            JsonObject(mapOf("groupId" to JsonPrimitive(groupId), "order" to JsonArray(target.map(::JsonPrimitive))))
        },
    )
}

private fun ActualBudgetEdits.setAccountOrder(change: JsonObject): List<String> {
    val all = accounts()
    val byId = all.associateBy { it["id"] as String }

    fun section(row: Row) =
        if (truthy(row["closed"])) {
            "closed"
        } else if (truthy(row["offbudget"])) {
            "off-budget"
        } else {
            "on-budget"
        }
    val saved = mutableListOf<String>()
    for (name in listOf("on-budget", "off-budget", "closed")) {
        fun inSection(id: String) = byId[id]?.let { section(it) == name } == true
        val order = ids(change["order"]).filter(::inSection)
        if (order.isEmpty()) continue
        val scope =
            all
                .filter {
                    if (name ==
                        "closed"
                    ) {
                        truthy(it["closed"])
                    } else {
                        truthy(it["offbudget"]) == (name == "off-budget")
                    }
                }.map { it["id"] as String }
        val live = scope.filter(::inSection)
        val sectionTarget = BudgetOrderMerge.rebase(ids(change["baseOrder"]).filter(::inSection), order, live)
        val remaining = ArrayDeque(sectionTarget)
        val scopeTarget = scope.map { if (inSection(it)) remaining.removeFirst() else it }
        BudgetOrderMerge
            .plan(
                scope,
                scopeTarget,
                scope.filter { !inSection(it) }.toSet(),
            ).forEach { rawMoveAccount(it.id, it.beforeId) }
        saved += sectionTarget
    }
    val after = accounts().map { it["id"] as String }
    for (name in listOf("on-budget", "off-budget", "closed")) {
        val expected = saved.filter { section(byId.getValue(it)) == name }
        if (expected.isNotEmpty()) confirm(after.filter { id -> byId[id]?.let { section(it) == name } == true }, expected)
    }
    return saved
}
