package app.tandemforactual.data.actual

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.tandemforactual.data.oracle.BudgetMathsFixtures
import app.tandemforactual.data.oracle.BudgetMathsOracle
import app.tandemforactual.data.oracle.BudgetScenario
import app.tandemforactual.data.oracle.ScenarioGenerator
import java.io.File
import kotlin.test.Test
import kotlin.test.fail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class BudgetEngineOracleTest {
    @Test
    fun engineAgreesWithActual() =
        withDatabase { db ->
            val problems =
                BudgetMathsFixtures.scenarios.flatMap { s ->
                    BudgetMathsFixtures.differences(BudgetMathsFixtures.actual.getValue(s.name), db.engineMonths(s)).map { "${s.name} $it" }
                }
            if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
        }

    @Test
    fun engineAgreesWithTheOracleOnGeneratedBudgets() =
        withDatabase { db ->
            val generator = ScenarioGenerator(SEED)
            repeat(SCENARIOS) { i ->
                val s = generator.next("generated-$i")
                val problems = BudgetMathsFixtures.differences(BudgetMathsOracle.months(s), db.engineMonths(s))
                if (problems.isNotEmpty()) fail("${s.name} (seed $SEED)\n${problems.take(20).joinToString("\n")}\n${s.toJson()}")
            }
        }

    private fun <T> withDatabase(block: (ActualDatabase) -> T): T {
        val bytes =
            ZipArchive
                .read(
                    checkNotNull(javaClass.getResource("/actual/parity-envelope/budget.zip")).readBytes(),
                ).getValue("db.sqlite")
        val file = File.createTempFile("budget-maths", ".sqlite").apply { writeBytes(bytes) }
        val db = ActualDatabase(BundledSQLiteDriver().open(file.path))
        try {
            return block(db)
        } finally {
            db.close()
            file.delete()
        }
    }

    private fun ActualDatabase.engineMonths(s: BudgetScenario): Map<String, Map<String, Any>> {
        store(s)
        val reader = ActualBudgetReader(this)
        return s.months.associateWith { month -> flatten(checkNotNull(reader.budgetMonth("${s.months.last()}-15", month)) { month }) }
    }

    private fun flatten(month: JsonObject): Map<String, Any> {
        val fields = mutableMapOf<String, JsonElement>()
        SUMMARY.forEach { key -> month[key]?.let { fields[key] = it } }
        month.getValue("categoryGroups").jsonArray.map { it.jsonObject }.forEach { group ->
            val name = group.getValue("name").jsonPrimitive.content
            CELLS.forEach { key -> group[key]?.let { fields["group:$name:$key"] = it } }
            (group["categories"] as? JsonArray).orEmpty().map { it.jsonObject }.forEach { category ->
                val categoryName = category.getValue("name").jsonPrimitive.content
                CELLS.forEach { key -> category[key]?.let { fields["category:$categoryName:$key"] = it } }
            }
        }
        return BudgetMathsFixtures.cells(JsonObject(fields))
    }

    private fun ActualDatabase.store(s: BudgetScenario) =
        transaction {
            TABLES.forEach { execute("DELETE FROM $it") }
            execute("DELETE FROM preferences WHERE id = 'budgetType'")
            execute("INSERT INTO preferences (id, value) VALUES ('budgetType', ?)", if (s.tracking) "tracking" else "envelope")
            s.accounts.forEachIndexed { i, a ->
                execute(
                    "INSERT INTO accounts (id, name, offbudget, closed, tombstone, sort_order) VALUES (?, ?, ?, 0, 0, ?)",
                    "a:${a.name}",
                    a.name,
                    if (a.offBudget) 1 else 0,
                    i,
                )
            }
            s.groups.forEachIndexed { gi, g ->
                execute(
                    "INSERT INTO category_groups (id, name, is_income, sort_order, tombstone, hidden) VALUES (?, ?, ?, ?, 0, ?)",
                    "g:${g.name}",
                    g.name,
                    if (g.income) 1 else 0,
                    gi,
                    if (g.hidden) 1 else 0,
                )
                g.categories.forEachIndexed { ci, c ->
                    execute(
                        "INSERT INTO categories (id, name, is_income, cat_group, sort_order, tombstone, hidden) VALUES (?, ?, ?, ?, ?, 0, ?)",
                        "c:${c.name}",
                        c.name,
                        if (g.income) 1 else 0,
                        "g:${g.name}",
                        ci,
                        if (c.hidden) 1 else 0,
                    )
                    execute("INSERT INTO category_mapping (id, transferId) VALUES (?, ?)", "c:${c.name}", "c:${c.name}")
                }
            }
            storeTransactions(s)
            storeBudgets(s)
            s.holds.forEach { execute("INSERT INTO zero_budget_months (id, buffered) VALUES (?, ?)", it.month, it.amount) }
        }

    private fun ActualDatabase.storeTransactions(s: BudgetScenario) {
        val offBudget = s.accounts.associate { it.name to it.offBudget }

        fun row(
            id: String,
            account: String,
            date: String,
            amount: Long,
            category: String?,
            deleted: Boolean,
            parent: String? = null,
            isParent: Boolean = false,
            transfer: String? = null,
        ) = execute(
            "INSERT INTO transactions (id, isParent, isChild, acct, category, amount, date, tombstone, parent_id, transferred_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            id,
            if (isParent) 1 else 0,
            if (parent != null) 1 else 0,
            "a:$account",
            category?.let { "c:$it" },
            amount,
            date.replace("-", "").toLong(),
            if (deleted) 1 else 0,
            parent,
            transfer,
        )
        s.transactions.forEachIndexed { i, t ->
            val id = "t$i"
            when {
                t.splits.isNotEmpty() -> {
                    row(id, t.account, t.date, t.amount, null, t.deleted, isParent = true)
                    t.splits.forEachIndexed {
                        j,
                        split,
                        ->
                        row("$id/$j", t.account, t.date, split.amount, split.category, false, parent = id)
                    }
                }

                t.transferTo != null -> {
                    val keeps = offBudget.getValue(t.account) != offBudget.getValue(t.transferTo)
                    row(id, t.account, t.date, t.amount, t.category.takeIf { keeps }, t.deleted, transfer = "$id/to")
                    row("$id/to", t.transferTo!!, t.date, -t.amount, null, t.deleted, transfer = id)
                }

                else -> {
                    row(id, t.account, t.date, t.amount, t.category, t.deleted)
                }
            }
        }
    }

    private fun ActualDatabase.storeBudgets(s: BudgetScenario) {
        val rows = LinkedHashMap<Pair<String, String>, Pair<Long, Boolean>>()
        s.budgets.forEach { rows[it.month to it.category] = it.amount to (rows[it.month to it.category]?.second ?: false) }
        s.carryover.forEach { c ->
            s.months.filter { it >= c.from }.forEach { month ->
                rows[month to c.category] =
                    (rows[month to c.category]?.first ?: 0L) to c.flag
            }
        }
        val table = if (s.tracking) "reflect_budgets" else "zero_budgets"
        rows.forEach { (key, value) ->
            val month = key.first.replace("-", "").toLong()
            execute(
                "INSERT INTO $table (id, month, category, amount, carryover) VALUES (?, ?, ?, ?, ?)",
                "$month-c:${key.second}",
                month,
                "c:${key.second}",
                value.first,
                if (value.second) 1 else 0,
            )
        }
    }

    private companion object {
        const val SEED = 20261127L
        const val SCENARIOS = 400
        val TABLES =
            listOf(
                "transactions",
                "categories",
                "category_groups",
                "category_mapping",
                "accounts",
                "zero_budgets",
                "reflect_budgets",
                "zero_budget_months",
            )
        val SUMMARY =
            listOf(
                "toBudget",
                "incomeAvailable",
                "lastMonthOverspent",
                "forNextMonth",
                "totalBudgeted",
                "fromLastMonth",
                "totalIncome",
                "totalSpent",
                "totalBalance",
            )
        val CELLS = listOf("budgeted", "spent", "received", "balance", "carryover")
    }
}
