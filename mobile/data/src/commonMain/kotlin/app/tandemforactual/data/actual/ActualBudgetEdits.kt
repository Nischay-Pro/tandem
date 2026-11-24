package app.tandemforactual.data.actual

/** A budget edit the gateway would have refused; [message] is shown to the user. */
class ActualEditException(
    message: String,
    val code: String? = null,
) : IllegalArgumentException(message)

internal const val SORT_INCREMENT = 16384.0
internal const val MAX_BUDGET_AMOUNT_MINOR = 100_000_000_000L

/**
 * Account, payee, category, budget and rule edits with the gateway's checks and Actual's handlers
 * (`accounts/app.ts`, `budget/app.ts`, `budget/actions.ts`, `db/index.ts`, `rules/app.ts`).
 */
class ActualBudgetEdits(
    internal val writer: ActualWriter,
    internal val today: () -> String,
    internal val transactions: ActualTransactions = ActualTransactions(writer),
) {
    internal val db get() = writer.db

    fun linkBankAccount(
        accountId: String,
        source: String,
        providerAccountId: String,
        bankId: String,
        bankName: String?,
    ) = ActualBankSync(writer, today).linkAccount(accountId, source, providerAccountId, bankId, bankName)

    fun unlinkBankAccount(accountId: String) = ActualBankSync(writer, today).unlinkAccount(accountId)

    fun setBankSyncInterval(minutes: Int) = ActualBankSync(writer, today).setSyncIntervalMinutes(minutes)

    /** `transactions-import`: reconciles statement rows into an account the way Actual does. */
    fun importTransactions(
        accountId: String,
        rows: List<ImportRow>,
    ): ImportResult = ActualImport(writer).reconcile(accountId, rows)

    val schedules = ActualScheduleEdits(writer, today)

    internal fun fail(message: String): Nothing = throw ActualEditException(message)

    internal fun accounts() = db.query("SELECT * FROM accounts WHERE tombstone = 0 ORDER BY sort_order, name")

    internal fun categories() = db.query("SELECT * FROM categories WHERE tombstone = 0 ORDER BY sort_order, id")

    internal fun groups() = db.query("SELECT * FROM category_groups WHERE tombstone = 0 ORDER BY is_income, sort_order, id")

    internal fun payees() =
        db.query(
            "SELECT p.*, COALESCE(a.name, p.name) AS name FROM payees p " +
                "LEFT JOIN accounts a ON (p.transfer_acct = a.id AND a.tombstone = 0) " +
                "WHERE p.tombstone = 0 AND (p.transfer_acct IS NULL OR a.id IS NOT NULL)",
        )

    internal fun tracking() = db.first("SELECT value FROM preferences WHERE id = 'budgetType'")?.get("value") == "tracking"

    internal fun budgetTable() = if (tracking()) "reflect_budgets" else "zero_budgets"

    internal data class Order(
        val id: String,
        val sortOrder: Double,
    )

    /** Actual's `shoveSortOrders`: the new position before [targetId], and any rows that must move to make room. */
    internal fun shove(
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

    internal fun orders(rows: List<Row>) = rows.map { Order(it["id"] as String, (it["sort_order"] as Number?)?.toDouble() ?: 0.0) }
}
