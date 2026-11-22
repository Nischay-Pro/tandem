package app.tandemforactual.data.actual

enum class HealthIssueKind {
    UNCATEGORIZED,
    POSSIBLE_DUPLICATES,
    FUTURE_DATED,
    OLD_UNCLEARED,
    NOT_RECONCILED,
    MISSING_PAYEE,
    UNUSED_PAYEES,
}

/** One kind of problem and the rows it affects, so the app can list and open them. */
data class HealthIssue(
    val kind: HealthIssueKind,
    val rows: List<HealthRow>,
) {
    val ids get() = rows.map { it.id }
}

/** An affected transaction, account or payee, named for display. Date and amount are set for transactions. */
data class HealthRow(
    val id: String,
    val name: String?,
    val date: String? = null,
    val amount: Long? = null,
    val account: String? = null,
)

/** Finds bookkeeping problems in an Actual budget that are worth a look, without changing anything. */
class ActualDataHealth(
    private val db: ActualDatabase,
) {
    fun check(today: String): List<HealthIssue> {
        val todayInt = ActualDates.toInt(today)
        val monthAgo = ActualDates.toInt(ActualDates.addDays(today, -30))
        val staleReconcile = ActualDates.toInt(ActualDates.addDays(today, -60))
        val onBudget = "account IN (SELECT id FROM accounts WHERE offbudget = 0 AND closed = 0 AND tombstone = 0)"
        val issues =
            listOf(
                HealthIssueKind.UNCATEGORIZED to
                    ids(
                        "SELECT id FROM v_transactions WHERE $onBudget AND category IS NULL AND is_parent = 0 " +
                            "AND transfer_id IS NULL AND starting_balance_flag = 0 AND date <= ?",
                        todayInt,
                    ),
                HealthIssueKind.POSSIBLE_DUPLICATES to
                    ids(
                        "SELECT t.id FROM v_transactions t JOIN v_transactions o ON o.account = t.account AND o.amount = t.amount " +
                            "AND o.date = t.date AND o.payee IS t.payee AND o.id != t.id AND o.is_child = 0 " +
                            "WHERE t.is_child = 0 AND t.reconciled = 0 AND t.transfer_id IS NULL AND t.amount != 0 GROUP BY t.id",
                    ),
                HealthIssueKind.FUTURE_DATED to
                    ids("SELECT id FROM v_transactions WHERE is_child = 0 AND schedule IS NULL AND date > ?", todayInt),
                HealthIssueKind.OLD_UNCLEARED to
                    ids("SELECT id FROM v_transactions WHERE $onBudget AND is_child = 0 AND cleared = 0 AND date < ?", monthAgo),
                HealthIssueKind.NOT_RECONCILED to
                    ids(
                        "SELECT a.id FROM accounts a WHERE a.tombstone = 0 AND a.closed = 0 AND EXISTS " +
                            "(SELECT 1 FROM v_transactions t WHERE t.account = a.id) " +
                            "AND COALESCE((SELECT max(t.date) FROM v_transactions t WHERE t.account = a.id AND t.reconciled = 1), 0) < ?",
                        staleReconcile,
                    ),
                HealthIssueKind.MISSING_PAYEE to
                    ids("SELECT id FROM v_transactions WHERE is_child = 0 AND payee IS NULL AND starting_balance_flag = 0"),
                HealthIssueKind.UNUSED_PAYEES to
                    ids(
                        "SELECT p.id FROM payees p WHERE p.tombstone = 0 AND p.transfer_acct IS NULL AND p.name != '' " +
                            "AND NOT EXISTS (SELECT 1 FROM v_transactions t WHERE t.payee = p.id) " +
                            "AND NOT EXISTS (SELECT 1 FROM rules r WHERE r.tombstone = 0 AND (r.conditions LIKE '%' || p.id || '%' " +
                            "OR r.actions LIKE '%' || p.id || '%'))",
                    ),
            )
        return issues.filter { it.second.isNotEmpty() }.map { HealthIssue(it.first, it.second) }
    }

    private fun ids(
        sql: String,
        vararg args: Any?,
    ): List<HealthRow> = rows(db.query(sql, *args).map { it["id"] as String }.distinct())

    /** Names transactions, accounts or payees by id for display, transactions with their date, amount and account. */
    fun rows(found: List<String>): List<HealthRow> =
        found.chunked(500).flatMap { chunk ->
            val marks = chunk.joinToString { "?" }
            db
                .query(
                    "SELECT t.id, p.name AS name, t.date, t.amount, a.name AS account FROM v_transactions t " +
                        "LEFT JOIN payees p ON p.id = t.payee LEFT JOIN accounts a ON a.id = t.account WHERE t.id IN ($marks) " +
                        "UNION ALL SELECT id, name, NULL, NULL, NULL FROM accounts WHERE id IN ($marks) " +
                        "UNION ALL SELECT id, name, NULL, NULL, NULL FROM payees WHERE id IN ($marks) ORDER BY 3 DESC",
                    *(chunk + chunk + chunk).toTypedArray(),
                ).map { row ->
                    HealthRow(
                        row["id"] as String,
                        row["name"] as String?,
                        (row["date"] as Long?)?.let(ActualDates::fromInt),
                        row["amount"] as Long?,
                        row["account"] as String?,
                    )
                }
        }
}
