package app.tandemforactual.data.oracle

import app.tandemforactual.data.oracle.BudgetScenario.Account
import app.tandemforactual.data.oracle.BudgetScenario.Allocation
import app.tandemforactual.data.oracle.BudgetScenario.Carryover
import app.tandemforactual.data.oracle.BudgetScenario.Category
import app.tandemforactual.data.oracle.BudgetScenario.Group
import app.tandemforactual.data.oracle.BudgetScenario.Hold
import app.tandemforactual.data.oracle.BudgetScenario.Split
import app.tandemforactual.data.oracle.BudgetScenario.Transaction
import kotlin.random.Random

class ScenarioGenerator(
    seed: Long,
) {
    private val random = Random(seed)

    fun next(name: String): BudgetScenario {
        val tracking = random.nextInt(3) == 0
        val months = months()
        val accounts = listOf(Account("Checking", false), Account("Card", false), Account("Brokerage", true), Account("Loan", true))
        val groups = groups(tracking)
        val income = groups.first { it.income }.categories.map { it.name }
        val expense = groups.filterNot { it.income }.flatMap { g -> g.categories.map { it.name } }
        val all = income + expense
        val transactions =
            mutableListOf(Transaction("Checking", "${months.first()}-01", amount(500_000), income.first(), null, emptyList(), false))
        months.forEach { month -> repeat(random.nextInt(9)) { transactions += transaction(month, accounts, income, expense) } }
        val budgeted = if (tracking) all else expense
        val budgets =
            months.flatMap { month ->
                budgeted.filter { random.nextInt(10) < 6 }.map { Allocation(month, it, allocation()) }
            }
        val rollable = if (tracking || random.nextBoolean()) all else expense
        val carryover = List(random.nextInt(5)) { Carryover(months.random(random), rollable.random(random), random.nextInt(4) != 0) }
        val holds =
            if (tracking) {
                emptyList()
            } else {
                months.filter { random.nextInt(4) == 0 }.map { Hold(it, amount(60_000) + 1) }
            }
        return BudgetScenario(name, tracking, months, accounts, groups, transactions, budgets, carryover, holds)
    }

    private fun months(): List<String> {
        var month = "2024-${(random.nextInt(12) + 1).toString().padStart(2, '0')}"
        return List(random.nextInt(2, 9)) { month.also { month = BudgetMathsOracle.nextMonth(month) } }
    }

    private fun groups(tracking: Boolean): List<Group> {
        val incomeNames = listOf("Salary", "Interest").take(random.nextInt(1, 3))
        val income = Group("Income", true, false, incomeNames.mapIndexed { i, n -> Category(n, tracking && i > 0 && random.nextBoolean()) })
        val expense =
            List(random.nextInt(1, 4)) { g ->
                val hidden = tracking && g > 0 && random.nextInt(3) == 0
                Group(
                    "Group $g",
                    false,
                    hidden,
                    List(random.nextInt(1, 4)) { c -> Category("Cat $g.$c", tracking && random.nextInt(4) == 0) },
                )
            }
        return listOf(income) + expense
    }

    private fun transaction(
        month: String,
        accounts: List<Account>,
        income: List<String>,
        expense: List<String>,
    ): Transaction {
        val date = "$month-${(random.nextInt(28) + 1).toString().padStart(2, '0')}"
        val account = if (random.nextInt(6) == 0) accounts.random(random).name else listOf("Checking", "Card").random(random)
        val deleted = random.nextInt(12) == 0
        return when (random.nextInt(10)) {
            0 -> {
                Transaction(account, date, amount(400_000), income.random(random), null, emptyList(), deleted)
            }

            1 -> {
                Transaction(account, date, -amount(50_000), null, null, emptyList(), deleted)
            }

            2, 3 -> {
                val splits = List(random.nextInt(2, 4)) { split(income, expense) }
                Transaction(account, date, splits.sumOf { it.amount }, null, null, splits, deleted)
            }

            4 -> {
                val target = accounts.filter { it.name != account }.random(random).name
                Transaction(account, date, -amount(80_000), expense.random(random), target, emptyList(), deleted)
            }

            else -> {
                val refund = random.nextInt(8) == 0
                Transaction(
                    account,
                    date,
                    if (refund) amount(20_000) else -amount(90_000),
                    expense.random(random),
                    null,
                    emptyList(),
                    deleted,
                )
            }
        }
    }

    private fun split(
        income: List<String>,
        expense: List<String>,
    ): Split =
        when (random.nextInt(6)) {
            0 -> Split(amount(30_000), income.random(random))
            1 -> Split(-amount(10_000), null)
            else -> Split(-amount(40_000), expense.random(random))
        }

    private fun allocation(): Long = if (random.nextInt(15) == 0) -amount(5_000) else amount(70_000) - 1

    private fun amount(max: Int): Long = random.nextInt(1, max).toLong()
}
