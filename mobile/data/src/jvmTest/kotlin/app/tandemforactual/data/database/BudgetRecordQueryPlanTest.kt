package app.tandemforactual.data.database

import androidx.room3.Room
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.findRecords
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class BudgetRecordQueryPlanTest {
    @Test
    fun accountPagesWalkTheOrderedIndex() =
        withDatabase { database, connection ->
            database.budgetRecordDao().putAll(
                (0 until 8).map { category("c$it", """{"is_income":${it == 0}}""") } +
                    (0 until 200).map { transaction("t$it", "2026-01-%02d".format(it % 28 + 1), "c${it % 8}", -it.toLong()) },
            )
            val page =
                plan(
                    connection,
                    "SELECT * FROM budget_records WHERE budgetId = 'b' AND recordType = 'transaction' AND accountId = 'a' " +
                        "AND localDeleted = 0 ORDER BY recordDate DESC, recordId ASC LIMIT 50 OFFSET 100",
                )
            assertTrue(page.any { "accountId_recordDate_recordId" in it }, page.toString())
            assertFalse(page.any { "TEMP B-TREE" in it }, page.toString())
            assertEquals(
                50,
                database
                    .budgetRecordDao()
                    .observeRecentTransactionsForAccount("b", "a", 50, 100)
                    .first()
                    .size,
            )
        }

    @Test
    fun reportsKeepActualsIncomeFlagRules() =
        withDatabase { database, _ ->
            database.budgetRecordDao().putAll(
                listOf(
                    category("income", """{"is_income":true}"""),
                    category("camel", """{"isIncome":1}"""),
                    category("expense", """{"is_income":false}"""),
                    category("broken", "not json"),
                    category("odd", """{"is_income":2}"""),
                    transaction("t1", "2026-02-01", "income", 1_000),
                    transaction("t2", "2026-02-02", "camel", 200),
                    transaction("t3", "2026-02-03", "expense", -300),
                    transaction("t4", "2026-02-04", "broken", -40),
                    transaction("t5", "2026-02-05", "odd", -5),
                    transaction("t6", "2026-02-06", "missing", -6),
                    transaction("t7", "2026-02-07", null, 70),
                    transaction("t8", "2026-02-08", null, -8),
                ),
            )
            assertEquals(
                listOf(
                    CategoryMonthAggregate("2026-02", "broken", 40),
                    CategoryMonthAggregate("2026-02", "expense", 300),
                    CategoryMonthAggregate("2026-02", "missing", 6),
                ),
                database.budgetRecordDao().observeCategoryMonthAggregates("b", null, null).first(),
            )
            assertEquals(
                listOf(MonthlyIncomeSpendingAggregate("2026-02", 1_265, 346)),
                database.budgetRecordDao().observeMonthlyIncomeAndSpending("b", "2026-02-01", "2026-02-28").first(),
            )
        }

    @Test
    fun findRecordsMatchesEveryFilterShape() =
        withDatabase { database, connection ->
            database.budgetRecordDao().putAll(
                listOf(
                    transaction("jan", "2026-01-10", "c1", -1),
                    transaction("feb", "2026-02-10", "c2", -2),
                    transaction("mar", "2026-03-10", "c1", -3),
                    transaction("undated", null, "c1", -4),
                ),
            )
            val repository = LocalBudgetRepository(database) { 1L }

            suspend fun ids(
                fromDate: String? = null,
                throughDate: String? = null,
                categoryId: String? = null,
            ) = repository.findRecords("b", "transaction", categoryId = categoryId, fromDate = fromDate, throughDate = throughDate).map {
                it.recordId
            }
            assertEquals(listOf("mar", "feb", "jan", "undated"), ids())
            assertEquals(listOf("mar", "feb"), ids(fromDate = "2026-02-01"))
            assertEquals(listOf("feb"), ids(fromDate = "2026-02-01", throughDate = "2026-02-28"))
            assertEquals(listOf("feb", "jan"), ids(throughDate = "2026-02-28"))
            assertEquals(listOf("mar", "jan"), ids(fromDate = "2026-01-01", categoryId = "c1"))
            val ranged =
                plan(
                    connection,
                    "SELECT * FROM budget_records WHERE budgetId = 'b' AND recordType = 'transaction' AND recordDate >= '2026-02-01' " +
                        "AND localDeleted = 0 ORDER BY recordDate DESC, recordId ASC",
                )
            assertTrue(ranged.any { "recordDate>?" in it || "recordDate>=?" in it }, ranged.toString())
        }

    private fun withDatabase(block: suspend (TandemDatabase, SQLiteConnection) -> Unit) =
        runBlocking {
            val file = File.createTempFile("query-plan", ".db").apply { delete() }
            val database =
                openTandemDatabase(Room.databaseBuilder<TandemDatabase>(name = file.path).setQueryCoroutineContext(Dispatchers.IO))
            val connection = BundledSQLiteDriver().open(file.path)
            try {
                database.budgetRecordDao().observeVisibleCount("b", "transaction").first()
                block(database, connection)
            } finally {
                connection.close()
                database.close()
                listOf("", "-wal", "-shm").forEach { File(file.path + it).delete() }
            }
        }

    private fun plan(
        connection: SQLiteConnection,
        sql: String,
    ): List<String> =
        connection.prepare("EXPLAIN QUERY PLAN $sql").use { statement ->
            buildList { while (statement.step()) add(statement.getText(3)) }
        }

    private fun category(
        id: String,
        json: String,
    ) = BudgetRecordEntity("b", "category", id, json, "r", null, false, false, "g", null, null, null, null, null, null)

    private fun transaction(
        id: String,
        date: String?,
        categoryId: String?,
        amountMinor: Long,
    ) = BudgetRecordEntity(
        "b",
        "transaction",
        id,
        "{}",
        "r",
        null,
        false,
        false,
        "g",
        date,
        "a",
        categoryId,
        null,
        amountMinor,
        date?.take(7),
        transfer = false,
    )
}
