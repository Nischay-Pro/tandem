package app.tandemforactual.data.actual

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** A budget edit the gateway would have refused; [message] is shown to the user. */
class ActualEditException(
    message: String,
    val code: String? = null,
) : IllegalArgumentException(message)

private const val SORT_INCREMENT = 16384.0
private const val MAX_BUDGET_AMOUNT_MINOR = 100_000_000_000L

/**
 * Account, payee, category, budget and rule edits with the gateway's checks and Actual's handlers
 * (`accounts/app.ts`, `budget/app.ts`, `budget/actions.ts`, `db/index.ts`, `rules/app.ts`).
 */
class ActualBudgetEdits(
    private val writer: ActualWriter,
    private val today: () -> String,
    private val transactions: ActualTransactions = ActualTransactions(writer),
) {
    private val db get() = writer.db

    fun linkBankAccount(
        accountId: String,
        source: String,
        providerAccountId: String,
        bankId: String,
        bankName: String?,
    ) = ActualBankSync(writer, today).linkAccount(accountId, source, providerAccountId, bankId, bankName)

    /** `transactions-import`: reconciles statement rows into an account the way Actual does. */
    fun importTransactions(
        accountId: String,
        rows: List<ImportRow>,
    ): ImportResult = ActualImport(writer).reconcile(accountId, rows)

    val schedules = ActualScheduleEdits(writer, today)

    private fun fail(message: String): Nothing = throw ActualEditException(message)

    private fun accounts() = db.query("SELECT * FROM accounts WHERE tombstone = 0 ORDER BY sort_order, name")

    private fun categories() = db.query("SELECT * FROM categories WHERE tombstone = 0 ORDER BY sort_order, id")

    private fun groups() = db.query("SELECT * FROM category_groups WHERE tombstone = 0 ORDER BY is_income, sort_order, id")

    private fun payees() =
        db.query(
            "SELECT p.*, COALESCE(a.name, p.name) AS name FROM payees p " +
                "LEFT JOIN accounts a ON (p.transfer_acct = a.id AND a.tombstone = 0) " +
                "WHERE p.tombstone = 0 AND (p.transfer_acct IS NULL OR a.id IS NOT NULL)",
        )

    private fun tracking() = db.first("SELECT value FROM preferences WHERE id = 'budgetType'")?.get("value") == "tracking"

    private fun budgetTable() = if (tracking()) "reflect_budgets" else "zero_budgets"

    private data class Order(
        val id: String,
        val sortOrder: Double,
    )

    /** Actual's `shoveSortOrders`: the new position before [targetId], and any rows that must move to make room. */
    private fun shove(
        items: List<Order>,
        targetId: String?,
    ): Pair<List<Order>, Double> {
        val to = items.indexOfFirst { it.id == targetId }
        if (targetId == null || to == -1) return emptyList<Order>() to ((items.lastOrNull()?.sortOrder ?: 0.0) + SORT_INCREMENT)
        val updates = mutableListOf<Order>()
        val before = items.getOrNull(to - 1)
        if (items[to].sortOrder - (before?.sortOrder ?: 0.0) <= 2) {
            var next = to
            var order = kotlin.math.floor(items[next].sortOrder) + SORT_INCREMENT
            while (next < items.size && order > items[next].sortOrder) {
                updates += Order(items[next].id, order)
                next++
                order += SORT_INCREMENT
            }
        }
        val above = items[to]
        val position = if (before == null) above.sortOrder / 2 else (before.sortOrder + above.sortOrder) / 2
        return updates to position
    }

    private fun orders(rows: List<Row>) = rows.map { Order(it["id"] as String, (it["sort_order"] as Number?)?.toDouble() ?: 0.0) }

    // Accounts

    fun createAccount(
        name: String,
        offBudget: Boolean,
        accountGroupId: String?,
    ): String =
        writer.batch {
            if (accountGroupId != null &&
                db.first("SELECT id FROM account_groups WHERE id = ? AND tombstone = 0", accountGroupId) == null
            ) {
                fail("Choose an account group in this budget")
            }
            val peers = db.query("SELECT * FROM accounts WHERE offbudget = ? ORDER BY sort_order, name", if (offBudget) 1L else 0L)
            val (_, sortOrder) = shove(orders(peers), null)
            val id =
                writer.insert(
                    "accounts",
                    linkedMapOf(
                        "name" to name,
                        "offbudget" to offBudget,
                        "closed" to false,
                        "sort_order" to sortOrder,
                    ),
                )
            insertPayee(linkedMapOf("name" to "", "transfer_acct" to id))
            if (accountGroupId != null) writer.update("accounts", id, mapOf("account_group_id" to accountGroupId))
            id
        }

    fun renameAccount(
        id: String,
        name: String,
    ) = writer.batch {
        if (accounts().none { it["id"] == id }) fail("Account does not exist in this budget")
        writer.update("accounts", id, mapOf("name" to name))
    }

    fun reopenAccount(id: String) =
        writer.batch {
            val account = accounts().firstOrNull { it["id"] == id } ?: fail("Account does not exist in this budget")
            if (!truthy(account["closed"])) fail("This account is already open")
            writer.update("accounts", id, mapOf("closed" to 0L))
        }

    fun closeAccount(
        id: String,
        transferAccountId: String?,
        categoryId: String?,
    ) = writer.batch {
        val all = accounts()
        if (all.none { it["id"] == id }) fail("Account does not exist in this budget")
        if (transferAccountId == null && accountBalance(id, today()) != 0L) fail("Choose an account to move this balance before closing it")
        if (transferAccountId != null) {
            val destination = all.firstOrNull { it["id"] == transferAccountId }
            if (destination == null ||
                transferAccountId == id ||
                truthy(destination["closed"])
            ) {
                fail("Choose a different open account in this budget")
            }
        }
        if (categoryId != null && categories().none { it["id"] == categoryId }) fail("Choose a category in this budget")
        val row = db.first("SELECT * FROM accounts WHERE id = ?", id)!!
        if (row["bank"] != null) {
            writer.update(
                "accounts",
                id,
                linkedMapOf(
                    "account_id" to null,
                    "bank" to null,
                    "balance_current" to null,
                    "balance_available" to null,
                    "balance_limit" to null,
                    "account_sync_source" to null,
                    "bank_sync_status" to null,
                ),
            )
        }
        val account = db.first("SELECT * FROM accounts WHERE id = ? AND tombstone = 0", id) ?: return@batch
        if (truthy(account["closed"])) return@batch
        val balance =
            (
                db
                    .first(
                        "SELECT sum(amount) as balance FROM transactions WHERE acct = ? AND isParent = 0 AND tombstone = 0",
                        id,
                    )?.get("balance") as Number?
            )?.toLong()
                ?: 0L
        val count =
            (
                db
                    .first(
                        "SELECT count(id) as count FROM transactions WHERE acct = ? AND tombstone = 0",
                        id,
                    )?.get("count") as Number
            ).toLong()
        if (count == 0L) {
            writer.delete("accounts", id)
            return@batch
        }
        writer.update("accounts", id, mapOf("closed" to 1L))
        if (balance != 0L && transferAccountId != null) {
            val payee = checkNotNull(db.first("SELECT id FROM payees WHERE transfer_acct = ?", transferAccountId)?.get("id"))
            transactions.batchUpdate(
                added =
                    listOf(
                        linkedMapOf(
                            "id" to writer.uuid(),
                            "payee" to payee,
                            "amount" to -balance,
                            "account" to id,
                            "date" to today(),
                            "notes" to "Closing account",
                            "category" to categoryId,
                        ),
                    ),
            )
        }
    }

    fun moveAccount(
        id: String,
        targetId: String?,
    ) = writer.batch {
        val all = accounts()
        val account = all.firstOrNull { it["id"] == id } ?: fail("Account does not exist in this budget")
        val target = targetId?.let { t -> all.firstOrNull { it["id"] == t } ?: fail("Choose an account in this budget") }
        if (target?.get("id") == id) fail("Choose a different account position")
        if (target != null &&
            (truthy(target["closed"]) != truthy(account["closed"]) || truthy(target["offbudget"]) != truthy(account["offbudget"]))
        ) {
            fail("Accounts can only be reordered within the same section")
        }
        val peers =
            if (truthy(account["closed"])) {
                db.query("SELECT id, sort_order FROM accounts WHERE closed = 1 ORDER BY sort_order, name")
            } else {
                db.query(
                    "SELECT id, sort_order FROM accounts WHERE tombstone = 0 AND offbudget = ? ORDER BY sort_order, name",
                    if (truthy(account["offbudget"])) 1L else 0L,
                )
            }
        val (updates, order) = shove(orders(peers), targetId)
        updates.forEach { writer.update("accounts", it.id, mapOf("sort_order" to it.sortOrder)) }
        writer.update("accounts", id, linkedMapOf("sort_order" to order, "account_group_id" to account["account_group_id"]))
    }

    fun accountBalance(
        id: String,
        cutoff: String,
    ): Long =
        (
            db
                .first(
                    "SELECT sum(amount) as balance FROM transactions WHERE acct = ? AND isParent = 0 AND tombstone = 0 AND date <= ?",
                    id,
                    ActualMonths.dateInt(cutoff),
                )?.get("balance") as Number?
        )?.toLong() ?: 0L

    /** Actual's `account-move` with no checks, as the order merge sends it. */
    private fun rawMoveAccount(
        id: String,
        targetId: String?,
    ) {
        val account = db.first("SELECT * FROM accounts WHERE id = ?", id)!!
        val peers =
            if (truthy(account["closed"])) {
                db.query("SELECT id, sort_order FROM accounts WHERE closed = 1 ORDER BY sort_order, name")
            } else {
                db.query(
                    "SELECT id, sort_order FROM accounts WHERE tombstone = 0 AND offbudget = ? ORDER BY sort_order, name",
                    if (truthy(account["offbudget"])) 1L else 0L,
                )
            }
        val (updates, order) = shove(orders(peers), targetId)
        updates.forEach { writer.update("accounts", it.id, mapOf("sort_order" to it.sortOrder)) }
        writer.update("accounts", id, mapOf("sort_order" to order))
    }

    /**
     * The gateway's `setOrders`: each list carries the order the device started from and the order the user chose;
     * only the user's moves are replayed onto the live order, and the result is read back to confirm it.
     */
    fun setOrders(batch: JsonObject): JsonObject =
        writer.batch {
            fun ids(element: JsonElement?) =
                (element as? JsonArray)?.map { (it as JsonPrimitive).content }
                    ?: fail("The order must contain the same items as the base order")

            fun confirm(
                actual: List<String>,
                expected: List<String>,
            ) = check(actual == expected) { "Actual did not save the requested order" }
            val result = LinkedHashMap<String, JsonElement>()

            fun groups() = db.query("SELECT id, is_income FROM category_groups WHERE tombstone = 0 ORDER BY sort_order, id")
            (batch["categoryGroups"] as? JsonObject)?.let { change ->
                val targets = mutableListOf<String>()
                for (income in listOf(false, true)) {
                    val section = groups().filter { truthy(it["is_income"]) == income }.map { it["id"] as String }
                    val isIncome = groups().associate { it["id"] as String to truthy(it["is_income"]) }

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
                    confirm(groups().filter { truthy(it["is_income"]) == income }.map { it["id"] as String }, target)
                    targets += target
                }
                result["categoryGroups"] = JsonArray(targets.map(::JsonPrimitive))
            }
            (batch["categories"] as? JsonArray)?.takeIf { it.isNotEmpty() }?.let { changes ->
                val groupIds = groups().map { it["id"] as String }.toSet()
                result["categories"] =
                    JsonArray(
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
            (batch["accounts"] as? JsonObject)?.let { change ->
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
                result["accounts"] = JsonArray(saved.map(::JsonPrimitive))
            }
            JsonObject(result)
        }

    // Payees

    private fun insertPayee(payee: Map<String, Any?>): String {
        val id = writer.insert("payees", payee)
        writer.insert("payee_mapping", linkedMapOf("id" to id, "targetId" to id))
        return id
    }

    fun createPayee(name: String): String =
        writer.batch {
            if (payees().any {
                    (it["name"] as String?).orEmpty().trim().lowercase() == name.trim().lowercase()
                }
            ) {
                fail("A payee with this name already exists")
            }
            insertPayee(linkedMapOf("name" to name))
        }

    private fun regularPayee(id: String) = payees().firstOrNull { it["id"] == id && it["transfer_acct"] == null }

    fun renamePayee(
        id: String,
        name: String,
    ) = writer.batch {
        val clean = name.trim()
        if (clean.isEmpty() || clean.length > 100) fail("Payee names must be between 1 and 100 characters")
        regularPayee(id) ?: fail("Choose a payee in this budget")
        writer.update("payees", id, mapOf("name" to clean))
    }

    fun mergePayee(
        id: String,
        targetId: String,
    ) = writer.batch {
        if (id == targetId) fail("Choose a different payee to merge into")
        if (regularPayee(id) == null || regularPayee(targetId) == null) fail("Choose two payees in this budget")
        db.query("SELECT id FROM payee_mapping WHERE targetId = ?", id).forEach {
            writer.update(
                "payee_mapping",
                it["id"] as String,
                mapOf(
                    "targetId" to targetId,
                ),
            )
        }
        writer.update("payee_mapping", id, mapOf("targetId" to targetId))
        writer.delete("payees", id)
    }

    fun deletePayee(id: String) =
        writer.batch {
            regularPayee(id) ?: fail("Choose a payee in this budget")
            writer.delete("payees", id)
        }

    fun setPayeeDefaultCategory(
        payeeId: String,
        categoryId: String?,
    ) = writer.batch {
        if (categoryId != null && categoryId.isBlank()) fail("Choose a category or clear the default")
        regularPayee(payeeId) ?: fail("Choose a regular payee in this budget")
        if (categoryId != null && categories().none { it["id"] == categoryId }) fail("Choose an active category in this budget")
        val matching =
            loadRules().filter { rule ->
                val conditions = rule.getValue("conditions").jsonArray.map { it.jsonObject }
                val stage = rule["stage"]?.let(::scalar)
                (stage == null || stage == "default") &&
                    rule["conditionsOp"]?.let(::scalar) == "and" &&
                    conditions.size == 1 &&
                    conditions[0].value("field") == "payee" &&
                    conditions[0].value("op") == "is" &&
                    conditions[0].value("value") == payeeId &&
                    rule.getValue("actions").jsonArray.any {
                        it.jsonObject.value("field") == "category" &&
                            it.jsonObject.value("op") == "set"
                    }
            }
        if (matching.size > 1) fail("This payee has multiple category rules. Edit them in Actual first.")
        val rule = matching.firstOrNull()
        if (rule == null) {
            if (categoryId == null) return@batch
            insertRule(
                null,
                "and",
                JsonArray(listOf(condition("payee", "is", JsonPrimitive(payeeId)))),
                JsonArray(listOf(condition("category", "set", JsonPrimitive(categoryId)))),
            )
            return@batch
        }
        val actions = rule.getValue("actions").jsonArray.map { it.jsonObject }
        val indexes = actions.indices.filter { actions[it].value("field") == "category" && actions[it].value("op") == "set" }
        if (indexes.size > 1) fail("This payee has multiple category actions. Edit the rule in Actual first.")
        val index = indexes.single()
        val id = rule.value("id") as String
        if (categoryId == null) {
            val remaining = actions.filterIndexed { i, _ -> i != index }
            if (remaining.isEmpty()) deleteRule(id) else updateRuleRow(rule, JsonArray(remaining))
            return@batch
        }
        updateRuleRow(
            rule,
            JsonArray(
                actions.mapIndexed { i, action ->
                    if (i ==
                        index
                    ) {
                        JsonObject(action + ("value" to JsonPrimitive(categoryId)))
                    } else {
                        action
                    }
                },
            ),
        )
    }

    private fun condition(
        field: String,
        op: String,
        value: JsonElement,
    ) = JsonObject(linkedMapOf("field" to JsonPrimitive(field), "op" to JsonPrimitive(op), "value" to value))

    // Categories

    fun createCategory(
        groupId: String,
        name: String,
    ): String =
        writer.batch {
            val group = groups().firstOrNull { it["id"] == groupId && !truthy(it["hidden"]) } ?: fail("Choose an active category group")
            if (categories().any { it["cat_group"] == groupId && (it["name"] as String).trim().lowercase() == name.trim().lowercase() }) {
                fail("A category with this name already exists in that group")
            }
            val peers =
                db.query(
                    "SELECT id, sort_order FROM categories WHERE cat_group = ? AND tombstone = 0 ORDER BY sort_order, id",
                    groupId,
                )
            val (updates, order) = shove(orders(peers), peers.firstOrNull()?.get("id") as String?)
            updates.forEach { writer.update("categories", it.id, mapOf("sort_order" to it.sortOrder)) }
            val id =
                writer.insert(
                    "categories",
                    linkedMapOf(
                        "name" to name.trim(),
                        "cat_group" to groupId,
                        "is_income" to (if (truthy(group["is_income"])) 1L else 0L),
                        "hidden" to 0L,
                        "sort_order" to order,
                    ),
                )
            writer.insert("category_mapping", linkedMapOf("id" to id, "transferId" to id))
            id
        }

    fun renameCategory(
        id: String,
        name: String,
    ) = writer.batch {
        val category = categories().firstOrNull { it["id"] == id } ?: fail("Category does not exist in this budget")
        if (category["name"] != name) writer.update("categories", id, mapOf("name" to name.trim()))
    }

    fun createCategoryGroup(
        name: String,
        isIncome: Boolean,
    ): String =
        writer.batch {
            val clean = name.trim()
            if (clean.isEmpty() || clean.length > 100) fail("Category group names must be between 1 and 100 characters")
            if (groups().any { (it["name"] as String).trim().lowercase() == clean.lowercase() }) {
                fail(
                    "A category group with this name already exists",
                )
            }
            val last =
                db
                    .first(
                        "SELECT sort_order FROM category_groups WHERE tombstone = 0 ORDER BY sort_order DESC, id DESC LIMIT 1",
                    )?.get("sort_order") as Number?
            writer.insert(
                "category_groups",
                linkedMapOf(
                    "name" to clean,
                    "is_income" to (if (isIncome) 1L else 0L),
                    "hidden" to 0L,
                    "sort_order" to (last?.toDouble() ?: 0.0) + SORT_INCREMENT,
                ),
            )
        }

    fun manageCategoryGroup(
        groupId: String,
        action: String,
        name: String?,
        targetId: String?,
    ) = writer.batch {
        val all = groups()
        all.firstOrNull { it["id"] == groupId } ?: fail("Category group does not exist in this budget")
        when (action) {
            "rename" -> {
                val clean = name?.trim().orEmpty()
                if (clean.isEmpty() || clean.length > 100) fail("Category group names must be between 1 and 100 characters")
                if (all.any {
                        it["id"] != groupId && (it["name"] as String).trim().lowercase() == clean.lowercase()
                    }
                ) {
                    fail("A category group with this name already exists")
                }
                writer.update("category_groups", groupId, mapOf("name" to clean))
            }
            "hide", "show" -> writer.update("category_groups", groupId, mapOf("hidden" to (if (action == "hide") 1L else 0L)))
            "delete" -> {
                val cats = categories()
                val inGroup = cats.filter { it["cat_group"] == groupId }
                if (targetId != null) {
                    val destination = cats.firstOrNull { it["id"] == targetId && !truthy(it["hidden"]) }
                    val destinationGroup = all.firstOrNull { it["id"] == destination?.get("cat_group") && !truthy(it["hidden"]) }
                    if (destination == null ||
                        destinationGroup == null ||
                        inGroup.any { it["id"] == destination["id"] || truthy(it["is_income"]) != truthy(destination["is_income"]) }
                    ) {
                        fail("Choose a visible category outside this group with the same income type")
                    }
                    doTransfer(inGroup.map { it["id"] as String }, targetId)
                }
                db
                    .query(
                        "SELECT * FROM categories WHERE cat_group = ?",
                        groupId,
                    ).forEach { deleteCategoryRow(it["id"] as String, targetId) }
                writer.delete("category_groups", groupId)
            }
            else -> {
                if (targetId == groupId) fail("Choose a different category group position")
                if (targetId != null && all.none { it["id"] == targetId }) fail("Choose a category group in this budget")
                val peers = db.query("SELECT id, sort_order FROM category_groups WHERE tombstone = 0 ORDER BY sort_order, id")
                val (updates, order) = shove(orders(peers), targetId)
                updates.forEach { writer.update("category_groups", it.id, mapOf("sort_order" to it.sortOrder)) }
                writer.update("category_groups", groupId, mapOf("sort_order" to order))
            }
        }
    }

    fun manageCategory(
        categoryId: String,
        action: String,
        targetCategoryId: String?,
        targetGroupId: String?,
    ) = writer.batch {
        val cats = categories()
        val category = cats.firstOrNull { it["id"] == categoryId } ?: fail("Category does not exist in this budget")
        if (action == "hide" || action == "show") {
            writer.update("categories", categoryId, mapOf("hidden" to (if (action == "hide") 1L else 0L)))
            return@batch
        }
        val currentGroup = category["cat_group"] as String
        val destinationGroup = targetGroupId ?: currentGroup
        if (targetGroupId != null && destinationGroup != currentGroup) {
            val group =
                groups().firstOrNull { it["id"] == destinationGroup && !truthy(it["hidden"]) }
                    ?: fail("Choose a visible category group in this budget")
            if (truthy(group["is_income"]) != truthy(category["is_income"])) fail("Income categories must stay in an income group")
            if (targetCategoryId != null) fail("A category moved to another group starts at the end of that group")
            moveCategory(categoryId, destinationGroup, null)
            return@batch
        }
        if (targetCategoryId == categoryId) fail("Choose a different category position")
        if (targetCategoryId != null &&
            cats.firstOrNull { it["id"] == targetCategoryId }?.get("cat_group") != currentGroup
        ) {
            fail("Choose a category in the same group")
        }
        moveCategory(categoryId, currentGroup, targetCategoryId)
    }

    private fun moveCategory(
        id: String,
        groupId: String,
        targetId: String?,
    ) {
        val peers = db.query("SELECT id, sort_order FROM categories WHERE cat_group = ? AND tombstone = 0 ORDER BY sort_order, id", groupId)
        val (updates, order) = shove(orders(peers), targetId)
        updates.forEach { writer.update("categories", it.id, mapOf("sort_order" to it.sortOrder)) }
        writer.update("categories", id, linkedMapOf("sort_order" to order, "cat_group" to groupId))
    }

    fun deleteCategory(
        id: String,
        transferId: String?,
    ) = writer.batch {
        val cats = categories()
        val category = cats.firstOrNull { it["id"] == id } ?: return@batch
        if (transferId != null) {
            val destination = cats.firstOrNull { it["id"] == transferId }
            if (destination == null || transferId == id) fail("Choose a different category in this budget")
            if (truthy(destination["is_income"]) !=
                truthy(category["is_income"])
            ) {
                fail("Transactions can only move to a category of the same type")
            }
            if (!truthy(category["is_income"])) doTransfer(listOf(id), transferId)
        }
        deleteCategoryRow(id, transferId)
    }

    private fun deleteCategoryRow(
        id: String,
        transferId: String?,
    ) {
        if (transferId != null) {
            db.query("SELECT * FROM category_mapping WHERE transferId = ?", id).forEach {
                writer.update("category_mapping", it["id"] as String, mapOf("transferId" to transferId))
            }
            writer.update("category_mapping", id, mapOf("transferId" to transferId))
        }
        writer.delete("categories", id)
    }

    /** Actual's `doTransfer`: adds the deleted categories' budgeted amounts to the destination in every created month. */
    private fun doTransfer(
        categoryIds: List<String>,
        transferId: String,
    ) {
        for (month in createdMonths()) {
            val total = categoryIds.sumOf { getBudget(month, it) }
            setBudget(month, transferId, total + getBudget(month, transferId))
        }
    }

    // Budget planning

    private fun createdMonths(): List<String> {
        val current = ActualMonths.fromIsoDate(today())
        val earliest =
            db.first("SELECT date FROM transactions WHERE isChild=0 AND date IS NOT NULL ORDER BY date ASC LIMIT 1")?.get("date")?.let {
                ActualMonths.fromDateInt((it as Number).toLong())
            } ?: current
        return ActualMonths.rangeInclusive(ActualMonths.add(minOf(earliest, current), -3), ActualMonths.add(current, 12))
    }

    private fun requireMonth(month: String) {
        if (!Regex("^\\d{4}-(0[1-9]|1[0-2])$").matches(month)) fail("Choose a valid budget month")
    }

    private fun monthData(month: String): JsonObject =
        ActualBudgetReader(db)
            .read(today())
            .records
            .firstOrNull { it.type == "budget-month" && it.id == month }
            ?.body
            ?: fail("No budget exists for month: $month")

    private fun monthCategories(month: String): List<JsonObject> =
        monthData(month).getValue("categoryGroups").jsonArray.flatMap { group ->
            (group.jsonObject["categories"] as? JsonArray).orEmpty().map { it.jsonObject }
        }

    private fun getBudget(
        month: String,
        category: String,
    ): Long =
        (
            db
                .first(
                    "SELECT amount FROM ${budgetTable()} WHERE month = ? AND category = ?",
                    ActualMonths.dbMonth(month),
                    category,
                )?.get("amount") as Number?
        )?.toLong() ?: 0L

    private fun setBudget(
        month: String,
        category: String,
        amount: Long,
    ) {
        val table = budgetTable()
        val existing = db.first("SELECT id FROM $table WHERE month = ? AND category = ?", ActualMonths.dbMonth(month), category)
        if (existing != null) {
            writer.update(table, existing["id"] as String, mapOf("amount" to amount))
        } else {
            writer.insert(
                table,
                linkedMapOf(
                    "id" to "${ActualMonths.dbMonth(month)}-$category",
                    "month" to ActualMonths.dbMonth(month),
                    "category" to category,
                    "amount" to amount,
                ),
            )
        }
    }

    private fun requireAmount(
        amount: Long,
        message: String,
        allowZero: Boolean = true,
    ) {
        if (amount < 0 || (!allowZero && amount == 0L) || amount > MAX_BUDGET_AMOUNT_MINOR) fail(message)
    }

    fun setBudgetAmount(
        categoryId: String,
        month: String,
        amount: Long,
    ) = writer.batch {
        requireAmount(amount, "Enter a budget amount up to 1,000,000,000 major units")
        if (categories().none { it["id"] == categoryId }) fail("Category does not exist in this budget")
        if (month !in createdMonths().dropLast(1)) fail("No budget exists for month: $month")
        setBudget(month, categoryId, amount)
    }

    fun setBudgetCarryover(
        categoryId: String,
        month: String,
        enabled: Boolean,
    ) = writer.batch {
        if (monthCategories(month).none { it.value("id") == categoryId }) fail("Choose a category in this budget month")
        val category = categories().firstOrNull { it["id"] == categoryId }
        if (category == null || truthy(category["is_income"])) fail("budget-set-carryover: category is an income category")
        val table = budgetTable()
        for (each in createdMonths().filter { it >= month }) {
            val dbMonth = ActualMonths.dbMonth(each).toString()
            val existing = db.first("SELECT id FROM $table WHERE month = ? AND category = ?", dbMonth, categoryId)
            if (existing != null) {
                writer.update(table, existing["id"] as String, mapOf("carryover" to (if (enabled) 1L else 0L)))
            } else {
                writer.insert(
                    table,
                    linkedMapOf(
                        "id" to "$dbMonth-$categoryId",
                        "month" to dbMonth,
                        "category" to categoryId,
                        "carryover" to (if (enabled) 1L else 0L),
                    ),
                )
            }
        }
    }

    fun copyBudgetMonth(month: String) =
        writer.batch {
            requireMonth(month)
            if (month == "0001-01") fail("Choose a valid budget month")
            val previous = ActualMonths.add(month, -1)
            val source = monthCategories(previous).associate { it.value("id") as String to (it.value("budgeted") as Long? ?: 0L) }
            val targetIds = monthCategories(month).map { it.value("id") as String }.toSet()
            categories().filter { !truthy(it["hidden"]) && it["id"] in targetIds }.forEach {
                setBudget(month, it["id"] as String, source[it["id"]] ?: 0L)
            }
        }

    fun applyBudgetTemplate(
        month: String,
        assignments: List<Pair<String, Long>>,
    ) = writer.batch {
        requireMonth(month)
        if (assignments.isEmpty() || assignments.size > 500) fail("Choose a template with 1 to 500 categories")
        if (assignments.map { it.first }.toSet().size != assignments.size ||
            assignments.any { it.first.isBlank() || it.second < 0 || it.second > MAX_BUDGET_AMOUNT_MINOR }
        ) {
            fail("The template contains an invalid category amount")
        }
        val monthIds = monthCategories(month).map { it.value("id") as String }.toSet()
        val active = categories().filter { !truthy(it["hidden"]) }.map { it["id"] as String }.toSet()
        if (assignments.any { it.first !in active || it.first !in monthIds }) fail("A template category is no longer active in this month")
        assignments.forEach { (category, amount) -> setBudget(month, category, amount) }
    }

    private fun setBuffer(
        month: String,
        amount: Long,
    ) {
        if (db.first("SELECT id FROM zero_budget_months WHERE id = ?", month) != null) {
            writer.update("zero_budget_months", month, mapOf("buffered" to amount))
        } else {
            writer.insert("zero_budget_months", linkedMapOf("id" to month, "buffered" to amount))
        }
    }

    fun holdBudgetForNextMonth(
        month: String,
        amount: Long,
    ) = writer.batch {
        requireMonth(month)
        if (amount <= 0 || amount > MAX_BUDGET_AMOUNT_MINOR) fail("Enter a positive amount to hold for next month")
        if (tracking()) fail("Hold for next month is only available in an envelope budget")
        val data = monthData(month)
        val ready = data.value("toBudget") as Long? ?: 0L
        val held = data.value("forNextMonth") as Long? ?: 0L
        if (amount > ready + held) fail("The hold cannot exceed the amount ready to assign")
        if (amount == held) return@batch
        setBuffer(month, amount)
    }

    fun resetBudgetHold(month: String) =
        writer.batch {
            requireMonth(month)
            setBuffer(month, 0L)
        }

    fun moveMoney(
        month: String,
        fromCategoryId: String,
        toCategoryId: String,
        amount: Long,
    ) = writer.batch {
        if (amount <= 0 || amount > MAX_BUDGET_AMOUNT_MINOR) fail("Enter a positive move amount up to 1,000,000,000 major units")
        if (fromCategoryId == toCategoryId) fail("Choose two different categories")
        if (tracking()) fail("Money can only be moved in an envelope budget")
        val cats = monthCategories(month)
        val source = cats.firstOrNull { it.value("id") == fromCategoryId }
        val destination = cats.firstOrNull { it.value("id") == toCategoryId }
        if (source == null || destination == null) fail("Choose categories in this budget month")
        if (source.value("is_income") != destination.value("is_income")) fail("Move money between categories of the same type")
        if ((source.value("balance") as Long? ?: 0L) < amount) fail("The source category does not have enough available money")
        setBudget(month, fromCategoryId, (source.value("budgeted") as Long? ?: 0L) - amount)
        setBudget(month, toCategoryId, (destination.value("budgeted") as Long? ?: 0L) + amount)
    }

    // Rules

    private fun loadRules(): List<JsonObject> =
        ActualRules.load(db.query("SELECT * FROM rules WHERE conditions IS NOT NULL AND actions IS NOT NULL AND tombstone = 0"), emptyMap())

    private val internalFields =
        mapOf(
            "is_parent" to "isParent",
            "is_child" to "isChild",
            "account" to "acct",
            "imported_id" to "financial_id",
            "imported_payee" to "imported_description",
            "transfer_id" to "transferred_id",
            "payee" to "description",
        )

    private fun internal(items: JsonArray): String =
        stringifyJson(
            JsonArray(
                items.map { item ->
                    val obj = item.jsonObject
                    val field = (obj["field"] as? JsonPrimitive)?.contentOrNull
                    if (field != null &&
                        field in internalFields
                    ) {
                        JsonObject(obj + ("field" to JsonPrimitive(internalFields.getValue(field))))
                    } else {
                        obj
                    }
                },
            ),
        )

    private fun validateRule(
        conditionsOp: String,
        conditions: JsonArray,
        actions: JsonArray,
    ) {
        if (conditionsOp !in setOf("and", "or")) fail("Invalid rule conditionsOp: $conditionsOp")
        val probe =
            mapOf(
                "id" to "probe",
                "stage" to null,
                "conditions_op" to conditionsOp,
                "conditions" to internal(conditions),
                "actions" to internal(actions),
            )
        if (ActualRules.load(listOf(probe), emptyMap()).isEmpty()) fail("This rule has an invalid condition or action")
    }

    private fun insertRule(
        stage: String?,
        conditionsOp: String,
        conditions: JsonArray,
        actions: JsonArray,
    ): String {
        validateRule(conditionsOp, conditions, actions)
        return writer.insert(
            "rules",
            linkedMapOf(
                "stage" to stage,
                "conditions_op" to conditionsOp,
                "conditions" to internal(conditions),
                "actions" to internal(actions),
            ),
        )
    }

    private fun updateRuleRow(
        rule: JsonObject,
        actions: JsonArray,
    ) {
        val conditions = rule.getValue("conditions").jsonArray
        val op = rule.value("conditionsOp") as String? ?: "and"
        validateRule(op, conditions, actions)
        writer.update(
            "rules",
            rule.value("id") as String,
            linkedMapOf(
                "stage" to rule.value("stage"),
                "conditions_op" to op,
                "conditions" to internal(conditions),
                "actions" to internal(actions),
            ),
        )
    }

    private fun stageOf(rule: JsonObject): String? =
        when (val stage = rule["stage"]?.let(::scalar)) {
            null, "default" -> null
            "pre", "post" -> stage as String
            else -> fail("Invalid rule stage: $stage")
        }

    fun createRule(rule: JsonObject): String =
        writer.batch {
            insertRule(
                stageOf(rule),
                rule.value("conditionsOp") as String? ?: "and",
                rule["conditions"] as? JsonArray ?: fail("A rule needs conditions"),
                rule["actions"] as? JsonArray ?: fail("A rule needs actions"),
            )
        }

    fun updateRule(
        id: String,
        rule: JsonObject,
    ) = writer.batch {
        if (loadRules().none { it.value("id") == id }) fail("Rule does not exist in this budget")
        val conditions = rule["conditions"] as? JsonArray ?: fail("A rule needs conditions")
        val actions = rule["actions"] as? JsonArray ?: fail("A rule needs actions")
        val op = rule.value("conditionsOp") as String? ?: "and"
        validateRule(op, conditions, actions)
        writer.update(
            "rules",
            id,
            linkedMapOf(
                "stage" to stageOf(rule),
                "conditions_op" to op,
                "conditions" to internal(conditions),
                "actions" to internal(actions),
            ),
        )
    }

    /** Returns false when the rule belongs to a schedule, which Actual refuses to delete. */
    fun deleteRule(id: String): Boolean =
        writer.batch {
            if (db.first("SELECT id FROM schedules WHERE rule = ?", id) != null) return@batch false
            writer.delete("rules", id)
            true
        }
}
