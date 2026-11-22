package app.tandemforactual.data.actual

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ActualDataHealthTest {
    @Test
    fun findsBookkeepingProblems() {
        val resource = checkNotNull(javaClass.getResource("/actual/import-parity/budget.zip")).readBytes()
        val file = File.createTempFile("health", ".sqlite").apply { writeBytes(ZipArchive.read(resource).getValue("db.sqlite")) }
        file.deleteOnExit()
        val db = ActualDatabase(BundledSQLiteDriver().open(file.path))
        val store = ActualMessageStore(db)
        val writer = ActualWriter(store, store.loadClock(), System::currentTimeMillis) { UUID.randomUUID().toString() }
        val account = db.first("SELECT id FROM accounts")!!["id"] as String
        db.query("SELECT id FROM v_transactions WHERE reconciled = 1").forEach {
            writer.update(
                "transactions",
                it["id"] as String,
                mapOf(
                    "reconciled" to 0L,
                ),
            )
        }
        val payee = writer.insert("payees", mapOf("name" to "Diner"))
        writer.insert("payee_mapping", mapOf("id" to payee, "targetId" to payee))
        writer.insert("payees", mapOf("name" to "Never used"))

        fun tx(
            date: Long,
            amount: Long,
            payee: String?,
            cleared: Long = 1,
        ) = writer.insert(
            "transactions",
            mapOf(
                "acct" to account,
                "date" to date,
                "amount" to amount,
                "description" to payee,
                "cleared" to cleared,
                "isParent" to 0L,
                "isChild" to 0L,
            ),
        )
        val first = tx(20261110, -1_200, payee)
        val second = tx(20261110, -1_200, payee)
        val future = tx(20261230, -500, payee)
        val old = tx(20260901, -700, payee, cleared = 0)
        val noPayee = tx(20261115, -300, null)

        val issues = ActualDataHealth(db).check("2026-11-21").associate { it.kind to it.ids }
        assertTrue(issues.getValue(HealthIssueKind.UNCATEGORIZED).containsAll(listOf(first, old, noPayee)))
        assertEquals(setOf(first, second), issues.getValue(HealthIssueKind.POSSIBLE_DUPLICATES).toSet())
        assertEquals(listOf(future), issues.getValue(HealthIssueKind.FUTURE_DATED))
        assertEquals(listOf(old), issues.getValue(HealthIssueKind.OLD_UNCLEARED))
        assertEquals(listOf(account), issues.getValue(HealthIssueKind.NOT_RECONCILED))
        assertTrue(noPayee in issues.getValue(HealthIssueKind.MISSING_PAYEE))
        assertTrue(issues.getValue(HealthIssueKind.UNUSED_PAYEES).isNotEmpty())
        val rows = ActualDataHealth(db).check("2026-11-21").first { it.kind == HealthIssueKind.FUTURE_DATED }.rows
        assertEquals(HealthRow(future, "Diner", "2026-12-30", -500, "Checking"), rows.single())
    }
}
