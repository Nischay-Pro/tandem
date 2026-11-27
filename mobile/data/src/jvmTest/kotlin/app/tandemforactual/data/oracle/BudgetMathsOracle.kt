package app.tandemforactual.data.oracle

import java.lang.Math.addExact

// Read from Actual's packages/loot-core/src/server/budget/envelope.ts and tracking.ts; spending is selected as in
// base.ts and the v_transactions_internal_alive view. Months come back keyed like getBudgetMonth, flattened.
object BudgetMathsOracle {
    fun months(s: BudgetScenario): Map<String, Map<String, Any>> {
        val earliest = s.transactions.minOfOrNull { it.date.take(7) }
        require(earliest == s.months.first()) { "${s.name}: the first month must hold the earliest transaction" }
        require(s.months.zipWithNext().all { (a, b) -> nextMonth(a) == b }) { "${s.name}: months must be consecutive" }
        val ledger = Ledger(s)
        return if (s.tracking) Tracking(s, ledger).run() else Envelope(s, ledger).run()
    }

    fun nextMonth(month: String): String {
        val year = month.take(4).toInt()
        val m = month.drop(5).toInt()
        return if (m == 12) "${year + 1}-01" else "$year-${(m + 1).toString().padStart(2, '0')}"
    }
}

private class Ledger(
    s: BudgetScenario,
) {
    private val totals = HashMap<Pair<String, String>, Long>()

    init {
        val offBudget = s.accounts.associate { it.name to it.offBudget }

        fun post(
            account: String,
            date: String,
            category: String?,
            amount: Long,
        ) {
            if (category == null || offBudget.getValue(account)) return
            val key = date.take(7) to category
            totals[key] = addExact(totals[key] ?: 0L, amount)
        }
        for (t in s.transactions) {
            if (t.deleted) continue
            when {
                t.splits.isNotEmpty() -> t.splits.forEach { post(t.account, t.date, it.category, it.amount) }
                t.transferTo != null -> {
                    val keepsCategory = offBudget.getValue(t.account) != offBudget.getValue(t.transferTo)
                    post(t.account, t.date, if (keepsCategory) t.category else null, t.amount)
                }
                else -> post(t.account, t.date, t.category, t.amount)
            }
        }
    }

    fun activity(
        month: String,
        category: String,
    ): Long = totals[month to category] ?: 0L
}

private abstract class MonthMaths(
    protected val s: BudgetScenario,
    protected val ledger: Ledger,
) {
    private val allocated = s.budgets.associate { (it.month to it.category) to it.amount }

    protected fun budgeted(
        month: String,
        category: String,
    ): Long = allocated[month to category] ?: 0L

    // setCategoryCarryover in budget/actions.ts writes the flag into the chosen month and every later one.
    protected fun rollsOver(
        month: String,
        category: String,
    ): Boolean = s.carryover.lastOrNull { it.category == category && it.from <= month }?.flag ?: false

    protected fun sum(values: Iterable<Long>): Long = values.fold(0L) { total, v -> addExact(total, v) }

    abstract fun run(): Map<String, Map<String, Any>>
}

private class Envelope(
    s: BudgetScenario,
    ledger: Ledger,
) : MonthMaths(s, ledger) {
    override fun run(): Map<String, Map<String, Any>> {
        val incomeGroup = s.groups.first { it.income }
        val expenseGroups = s.groups.filter { !it.income }
        val expense = expenseGroups.flatMap { it.categories }.map { it.name }
        val income = incomeGroup.categories.map { it.name }
        val holds = s.holds.associate { it.month to it.amount }

        var lastBalance = expense.associateWith { 0L }
        var lastRolls = expense.associateWith { false }
        var lastToBudget = 0L
        var lastHeld = 0L
        val out = LinkedHashMap<String, Map<String, Any>>()
        for (month in s.months) {
            val cells = LinkedHashMap<String, Any>()
            val balance = HashMap<String, Long>()
            for (c in expense) {
                val previous = lastBalance.getValue(c)
                val brought = if (lastRolls.getValue(c)) previous else maxOf(previous, 0L)
                balance[c] = sum(listOf(budgeted(month, c), ledger.activity(month, c), brought))
                cells["category:$c:budgeted"] = budgeted(month, c)
                cells["category:$c:spent"] = ledger.activity(month, c)
                cells["category:$c:balance"] = balance.getValue(c)
                cells["category:$c:carryover"] = rollsOver(month, c)
            }
            for (group in expenseGroups) {
                val names = group.categories.map { it.name }
                cells["group:${group.name}:budgeted"] = sum(names.map { budgeted(month, it) })
                cells["group:${group.name}:spent"] = sum(names.map { ledger.activity(month, it) })
                cells["group:${group.name}:balance"] = sum(names.map { balance.getValue(it) })
            }
            income.forEach { cells["category:$it:received"] = ledger.activity(month, it) }
            val received = sum(income.map { ledger.activity(month, it) })
            cells["group:${incomeGroup.name}:received"] = received

            val fromLastMonth = addExact(lastToBudget, lastHeld)
            val available = addExact(received, fromLastMonth)
            val overspentLastMonth = sum(expense.filterNot { lastRolls.getValue(it) }.map { minOf(lastBalance.getValue(it), 0L) })
            val totalBudgeted = sum(expense.map { budgeted(month, it) })
            val heldByHand = holds[month] ?: 0L
            val heldAutomatically = sum(income.filter { rollsOver(month, it) }.map { ledger.activity(month, it) })
            val held = if (heldByHand != 0L) heldByHand else heldAutomatically
            val toBudget = sum(listOf(available, overspentLastMonth, -totalBudgeted, -held))

            cells["toBudget"] = toBudget
            cells["incomeAvailable"] = available
            cells["lastMonthOverspent"] = overspentLastMonth
            cells["forNextMonth"] = heldByHand
            cells["totalBudgeted"] = -totalBudgeted
            cells["fromLastMonth"] = fromLastMonth
            cells["totalIncome"] = received
            cells["totalSpent"] = sum(expense.map { ledger.activity(month, it) })
            cells["totalBalance"] = sum(expense.map { balance.getValue(it) })
            out[month] = cells

            lastBalance = balance
            lastRolls = expense.associateWith { rollsOver(month, it) }
            lastToBudget = toBudget
            lastHeld = held
        }
        return out
    }
}

private class Tracking(
    s: BudgetScenario,
    ledger: Ledger,
) : MonthMaths(s, ledger) {
    override fun run(): Map<String, Map<String, Any>> {
        val all = s.groups.flatMap { g -> g.categories.map { it.name to g.income } }
        var lastBalance = emptyMap<String, Long>()
        var lastRolls = emptyMap<String, Boolean>()
        val out = LinkedHashMap<String, Map<String, Any>>()
        for (month in s.months) {
            val cells = LinkedHashMap<String, Any>()
            val balance = HashMap<String, Long>()
            for ((c, isIncome) in all) {
                val activity = ledger.activity(month, c)
                val brought = if (lastRolls[c] == true) lastBalance.getValue(c) else 0L
                balance[c] = sum(listOf(budgeted(month, c), if (isIncome) -activity else activity, brought))
                cells["category:$c:budgeted"] = budgeted(month, c)
                cells["category:$c:${if (isIncome) "received" else "spent"}"] = activity
                cells["category:$c:balance"] = balance.getValue(c)
                cells["category:$c:carryover"] = rollsOver(month, c)
            }
            val groupTotals = HashMap<String, Triple<Long, Long, Long>>()
            for (group in s.groups) {
                val shown = group.categories.filterNot { it.hidden }.map { it.name }
                val totals =
                    Triple(
                        sum(shown.map { budgeted(month, it) }),
                        sum(shown.map { ledger.activity(month, it) }),
                        sum(shown.map { balance.getValue(it) }),
                    )
                groupTotals[group.name] = totals
                cells["group:${group.name}:budgeted"] = totals.first
                cells["group:${group.name}:${if (group.income) "received" else "spent"}"] = totals.second
                cells["group:${group.name}:balance"] = totals.third
            }
            val counted = s.groups.filter { !it.income && !it.hidden }.map { groupTotals.getValue(it.name) }
            cells["totalBudgeted"] = sum(counted.map { it.first })
            cells["totalSpent"] = sum(counted.map { it.second })
            cells["totalBalance"] = sum(counted.map { it.third })
            cells["totalIncome"] = groupTotals.getValue(s.groups.first { it.income }.name).second
            out[month] = cells

            lastBalance = balance
            lastRolls = all.associate { (c, _) -> c to rollsOver(month, c) }
        }
        return out
    }
}
