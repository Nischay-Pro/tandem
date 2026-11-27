package app.tandemforactual.data.database

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.RecordUsage
import app.tandemforactual.data.store.accountUsage
import app.tandemforactual.data.store.categoryGroupUsage
import app.tandemforactual.data.store.categoryUsage
import app.tandemforactual.data.store.payeeTransactionCounts
import app.tandemforactual.data.store.payeeUsage
import app.tandemforactual.data.store.ruleUsage
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class UsageQueryTest {
    @Test
    fun payeeCountsSplitLinesAndSkipsParentsDeletedRowsAndOtherBudgets() =
        withRepository { database, repository ->
            database.budgetRecordDao().putAll(
                listOf(
                    transaction("plain", payeeId = "coffee"),
                    transaction("split", payeeId = "coffee", isParent = true),
                    transaction("split-a", payeeId = "coffee"),
                    transaction("split-b", payeeId = "coffee"),
                    transaction("other", payeeId = "rent"),
                    transaction("deleted-here", payeeId = "coffee", localDeleted = true),
                    transaction("deleted-remote", payeeId = "coffee", remoteDeleted = true),
                    transaction("edited-after-remote-delete", payeeId = "coffee", remoteDeleted = true, local = "{}"),
                    transaction("elsewhere", payeeId = "coffee", budgetId = "other"),
                    rule("by-payee", condition("payee", "is", "\"coffee\"")),
                    rule("in-list", condition("payee", "oneOf", "[\"rent\",\"coffee\"]")),
                    rule("sets-payee", condition("notes", "contains", "\"latte\""), action("payee", "\"coffee\"")),
                    rule("other-payee", condition("payee", "is", "\"rent\"")),
                    rule("same-id-other-field", condition("notes", "is", "\"coffee\"")),
                    rule("tombstoned", condition("payee", "is", "\"coffee\""), extra = ",\"tombstone\":true"),
                    rule("deleted", condition("payee", "is", "\"coffee\""), localDeleted = true),
                    record("rule", "broken", "not json"),
                    record("rule", "odd-items", """{"conditions":["payee",3,null],"actions":"payee"}"""),
                    record("rule", "no-arrays", """{"id":"no-arrays"}"""),
                ),
            )

            assertEquals(RecordUsage(transactions = 4, rules = 3), repository.payeeUsage(BUDGET, "coffee"))
            assertEquals(RecordUsage(transactions = 1, rules = 2), repository.payeeUsage(BUDGET, "rent"))
            assertEquals(RecordUsage(), repository.payeeUsage(BUDGET, "unused"))
            assertEquals(mapOf("coffee" to 4L, "rent" to 1L), repository.payeeTransactionCounts(BUDGET))
        }

    @Test
    fun categoryCountsSplitChildrenAndRuleConditionsAndActions() =
        withRepository { database, repository ->
            database.budgetRecordDao().putAll(
                listOf(
                    transaction("split", isParent = true),
                    transaction("split-food", categoryId = "food"),
                    transaction("split-fuel", categoryId = "fuel"),
                    transaction("groceries", categoryId = "food"),
                    transaction("gone", categoryId = "food", localDeleted = true),
                    rule("food-condition", condition("category", "is", "\"food\"")),
                    rule("food-action", condition("payee", "is", "\"shop\""), action("category", "\"food\"")),
                    rule("fuel-action", condition("payee", "is", "\"station\""), action("category", "\"fuel\"")),
                ),
            )

            assertEquals(RecordUsage(transactions = 2, rules = 2), repository.categoryUsage(BUDGET, "food"))
            assertEquals(RecordUsage(transactions = 1, rules = 1), repository.categoryUsage(BUDGET, "fuel"))
        }

    @Test
    fun groupCountsItsLiveCategoriesTheirTransactionsAndRulesForEither() =
        withRepository { database, repository ->
            database.budgetRecordDao().putAll(
                listOf(
                    record("category", "food", """{"name":"Food","group_id":"living"}"""),
                    record("category", "fuel", """{"name":"Fuel","group":"living"}"""),
                    record("category", "fun", """{"name":"Fun","group_id":"extras"}"""),
                    record("category", "removed", """{"group_id":"living"}""", localDeleted = true),
                    record("category", "broken", "not json"),
                    transaction("t1", categoryId = "food"),
                    transaction("t2", categoryId = "fuel"),
                    transaction("t3", categoryId = "fun"),
                    transaction("t4", categoryId = "removed"),
                    rule("group-condition", condition("category_group", "is", "\"living\"")),
                    rule("child-action", condition("payee", "is", "\"x\""), action("category", "\"fuel\"")),
                    rule("other-group", condition("category_group", "is", "\"extras\"")),
                ),
            )

            assertEquals(
                RecordUsage(transactions = 2, rules = 2, childCategories = 2),
                repository.categoryGroupUsage(BUDGET, "living"),
            )
            assertEquals(RecordUsage(), repository.categoryGroupUsage(BUDGET, "empty"))
        }

    @Test
    fun accountCountsTransactionsRulesAndBalanceThroughTheDate() =
        withRepository { database, repository ->
            database.budgetRecordDao().putAll(
                listOf(
                    transaction("paid", accountId = "card", amountMinor = -2_000, date = "2026-03-01"),
                    transaction("split", accountId = "card", amountMinor = -1_000, date = "2026-03-02", isParent = true),
                    transaction("split-a", accountId = "card", amountMinor = -600, date = "2026-03-02"),
                    transaction("split-b", accountId = "card", amountMinor = -400, date = "2026-03-02"),
                    transaction("future", accountId = "card", amountMinor = -5_000, date = "2026-04-01"),
                    transaction("removed", accountId = "card", amountMinor = -9_000, date = "2026-03-03", remoteDeleted = true),
                    rule("by-account", condition("account", "is", "\"card\"")),
                ),
            )

            assertEquals(
                RecordUsage(transactions = 4, rules = 1, balanceMinor = -3_000),
                repository.accountUsage(BUDGET, "card", "2026-03-31"),
            )
        }

    @Test
    fun ruleCountsTheSchedulesBuiltOnIt() =
        withRepository { database, repository ->
            database.budgetRecordDao().putAll(
                listOf(
                    record("schedule", "rent", """{"name":"Rent","rule":"rent-rule"}"""),
                    record("schedule", "old-rent", """{"rule":"rent-rule"}""", localDeleted = true),
                    record("schedule", "gym", """{"rule":"gym-rule"}"""),
                    record("schedule", "broken", "not json"),
                ),
            )

            assertEquals(RecordUsage(schedules = 1), repository.ruleUsage(BUDGET, "rent-rule"))
            assertEquals(RecordUsage(), repository.ruleUsage(BUDGET, "lonely-rule"))
            assertFailsWith<IllegalArgumentException> { repository.ruleUsage(BUDGET, "") }
        }

    @Test
    fun payeeCountsWalkThePayeeIndex() =
        withRepository { database, _, path ->
            database.budgetRecordDao().countVisible(BUDGET, "transaction")
            val connection = BundledSQLiteDriver().open(path)
            try {
                val plan =
                    connection
                        .prepare(
                            "EXPLAIN QUERY PLAN SELECT COUNT(*) FROM budget_records WHERE budgetId = 'b' " +
                                "AND recordType = 'transaction' AND isParent = 0 AND payeeId = 'p'",
                        ).use { statement -> buildList { while (statement.step()) add(statement.getText(3)) } }
                assertTrue(plan.any { "recordType_payeeId" in it }, plan.toString())
            } finally {
                connection.close()
            }
        }

    private fun withRepository(block: suspend (TandemDatabase, LocalBudgetRepository) -> Unit) =
        withRepository { database, repository, _ -> block(database, repository) }

    private fun withRepository(block: suspend (TandemDatabase, LocalBudgetRepository, String) -> Unit) =
        runBlocking {
            val file = File.createTempFile("usage", ".db").apply { delete() }
            val database =
                openTandemDatabase(Room.databaseBuilder<TandemDatabase>(name = file.path).setQueryCoroutineContext(Dispatchers.IO))
            try {
                block(database, LocalBudgetRepository(database) { 100L }, file.path)
            } finally {
                database.close()
                listOf("", "-wal", "-shm").forEach { File(file.path + it).delete() }
            }
        }

    private fun condition(
        field: String,
        op: String,
        value: String,
    ) = """{"field":"$field","op":"$op","value":$value,"type":"id"}"""

    private fun action(
        field: String,
        value: String,
    ) = """{"field":"$field","op":"set","value":$value}"""

    private fun rule(
        id: String,
        condition: String,
        action: String? = null,
        extra: String = "",
        localDeleted: Boolean = false,
    ) = record(
        "rule",
        id,
        """{"id":"$id","conditions":[$condition],"actions":[${action.orEmpty()}]$extra}""",
        localDeleted = localDeleted,
    )

    private fun record(
        type: String,
        id: String,
        json: String,
        localDeleted: Boolean = false,
    ) = BudgetRecordEntity(BUDGET, type, id, json, "r", null, localDeleted, false, "g", null, null, null, null, null, null)

    private fun transaction(
        id: String,
        payeeId: String? = null,
        categoryId: String? = null,
        accountId: String? = null,
        amountMinor: Long = -100,
        date: String = "2026-03-01",
        isParent: Boolean = false,
        localDeleted: Boolean = false,
        remoteDeleted: Boolean = false,
        local: String? = null,
        budgetId: String = BUDGET,
    ) = BudgetRecordEntity(
        budgetId = budgetId,
        recordType = "transaction",
        recordId = id,
        remoteJson = if (remoteDeleted) null else "{}",
        remoteRevision = "r",
        localJson = local,
        localDeleted = localDeleted,
        remoteDeleted = remoteDeleted,
        syncGeneration = "g",
        recordDate = date,
        accountId = accountId,
        categoryId = categoryId,
        payeeId = payeeId,
        amountMinor = amountMinor,
        budgetMonth = null,
        isParent = isParent,
    )

    private companion object {
        const val BUDGET = "b"
    }
}
