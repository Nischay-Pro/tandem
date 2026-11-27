package app.tandemforactual.data.store

/** What a payee, category, group, account or rule is still tied to on this device, counted before it is deleted or closed. */
data class RecordUsage(
    val transactions: Long = 0,
    val rules: Long = 0,
    val childCategories: Long = 0,
    val schedules: Long = 0,
    val balanceMinor: Long? = null,
)

suspend fun LocalBudgetRepository.payeeUsage(
    budgetId: String,
    payeeId: String,
): RecordUsage {
    requireUsageIds(budgetId, payeeId)
    val dao = database.budgetUsageDao()
    return RecordUsage(
        transactions = dao.countPayeeTransactions(budgetId, payeeId),
        rules = dao.countRulesReferencing(budgetId, listOf("payee"), listOf(payeeId)),
    )
}

/** Every payee's transaction count in one query, for screens that weigh many payees at once. */
suspend fun LocalBudgetRepository.payeeTransactionCounts(budgetId: String): Map<String, Long> =
    database
        .budgetUsageDao()
        .countTransactionsByPayee(budgetId)
        .associate { it.payeeId to it.transactions }

suspend fun LocalBudgetRepository.categoryUsage(
    budgetId: String,
    categoryId: String,
): RecordUsage {
    requireUsageIds(budgetId, categoryId)
    val dao = database.budgetUsageDao()
    return RecordUsage(
        transactions = dao.countCategoryTransactions(budgetId, listOf(categoryId)),
        rules = dao.countRulesReferencing(budgetId, listOf("category"), listOf(categoryId)),
    )
}

/** The group's categories count too: deleting a group deletes them, so their transactions and rules are affected. */
suspend fun LocalBudgetRepository.categoryGroupUsage(
    budgetId: String,
    groupId: String,
): RecordUsage {
    requireUsageIds(budgetId, groupId)
    val dao = database.budgetUsageDao()
    val categoryIds = dao.findGroupCategoryIds(budgetId, groupId)
    return RecordUsage(
        transactions = categoryIds.chunked(USAGE_ID_CHUNK).sumOf { dao.countCategoryTransactions(budgetId, it) },
        rules = dao.countRulesReferencing(budgetId, listOf("category_group", "category"), listOf(groupId) + categoryIds),
        childCategories = categoryIds.size.toLong(),
    )
}

/** [throughDate] limits the balance to transactions on or before it, as the account list shows today's balance. */
suspend fun LocalBudgetRepository.accountUsage(
    budgetId: String,
    accountId: String,
    throughDate: String,
): RecordUsage {
    requireUsageIds(budgetId, accountId)
    val dao = database.budgetUsageDao()
    return RecordUsage(
        transactions = dao.countAccountTransactions(budgetId, accountId),
        rules = dao.countRulesReferencing(budgetId, listOf("account"), listOf(accountId)),
        balanceMinor = readAccountBalance(budgetId, accountId, throughDate),
    )
}

suspend fun LocalBudgetRepository.ruleUsage(
    budgetId: String,
    ruleId: String,
): RecordUsage {
    requireUsageIds(budgetId, ruleId)
    return RecordUsage(schedules = database.budgetUsageDao().countRuleSchedules(budgetId, ruleId))
}

private fun LocalBudgetRepository.requireUsageIds(
    budgetId: String,
    recordId: String,
) {
    requireIdentifier(budgetId, "budgetId")
    requireIdentifier(recordId, "recordId")
}

private const val USAGE_ID_CHUNK = 400
