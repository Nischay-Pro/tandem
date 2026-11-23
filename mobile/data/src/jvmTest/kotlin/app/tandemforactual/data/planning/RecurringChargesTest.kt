package app.tandemforactual.data.planning

import app.tandemforactual.data.database.SearchableBudgetTransaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecurringChargesTest {
    private var next = 0

    private fun spend(
        payee: String?,
        date: String,
        amount: Long,
        transfer: Boolean = false,
        parent: Boolean = false,
    ) = SearchableBudgetTransaction(
        "t${next++}",
        date,
        "acct",
        null,
        payee,
        -amount,
        null,
        null,
        null,
        null,
        null,
        transfer,
        parent,
    )

    @Test
    fun findsMonthlyAndYearlyCharges() {
        val records =
            listOf(
                spend("netflix", "2026-08-03", 1_549),
                spend("netflix", "2026-09-03", 1_549),
                spend("netflix", "2026-10-03", 1_549),
                spend("netflix", "2026-11-03", 1_799),
                spend("domain", "2023-05-10", 1_200),
                spend("domain", "2024-05-10", 1_200),
                spend("domain", "2025-05-11", 1_200),
                spend("gym", "2026-04-01", 4_000),
                spend("gym", "2026-05-01", 4_000),
                spend("gym", "2026-06-01", 4_000),
                spend("groceries", "2026-11-01", 8_000),
                spend("groceries", "2026-11-04", 3_000),
                spend("groceries", "2026-11-20", 12_000),
                spend("odd", "2026-01-01", 1_000),
                spend("odd", "2026-02-01", 1_000),
                spend("odd", "2026-03-01", 9_000),
                spend("twice", "2026-01-01", 1_000),
                spend("twice", "2026-02-01", 1_000),
                spend("netflix", "2026-07-01", 500, transfer = true),
                spend("netflix", "2026-07-02", 500, parent = true),
                spend(null, "2026-07-03", 500),
                spend("weekly", "2026-11-01", 700),
                spend("weekly", "2026-11-08", 700),
                spend("weekly", "2026-11-15", 700),
                spend("weekly", "2026-11-22", 700),
            )
        val found = RecurringCharges.find(records, "2026-11-22")
        assertEquals(listOf("weekly", "netflix", "gym", "domain"), found.map { it.payeeId })
        val netflix = found[1]
        assertEquals(Cadence.MONTHLY, netflix.cadence)
        assertEquals(1_799, netflix.amountMinor)
        assertEquals(1_799, netflix.monthlyMinor)
        assertEquals("2026-12-03", netflix.nextDate)
        assertTrue(netflix.priceRose)
        assertFalse(netflix.stopped)
        assertEquals(4, netflix.charges)
        assertEquals("2026-11-29", found[0].nextDate)
        assertEquals("2026-05-11", found[3].nextDate)
        assertEquals(700 * 52 / 12, found[0].monthlyMinor)
        val domain = found[3]
        assertEquals(Cadence.YEARLY, domain.cadence)
        assertEquals(100, domain.monthlyMinor)
        assertTrue(domain.stopped)
        assertTrue(found[2].stopped)
        assertEquals("2026-06-01", found[2].lastDate)
    }

    @Test
    fun needsMostGapsToFitTheRhythm() {
        val uneven =
            listOf(
                spend("x", "2026-01-01", 1_000),
                spend("x", "2026-02-01", 1_000),
                spend("x", "2026-02-05", 1_000),
                spend("x", "2026-02-09", 1_000),
                spend("x", "2026-03-11", 1_000),
            )
        assertEquals(emptyList(), RecurringCharges.find(uneven, "2026-03-20"))
        val quarterly = (0..3).map { spend("q", listOf("2026-01-05", "2026-04-05", "2026-07-05", "2026-10-05")[it], 3_000) }
        assertEquals(Cadence.QUARTERLY, RecurringCharges.find(quarterly, "2026-11-01").single().cadence)
        assertEquals("2027-01-05", RecurringCharges.find(quarterly, "2026-11-01").single().nextDate)
        val monthEnd = listOf("2026-10-31", "2026-11-30", "2026-12-31").map { spend("rent", it, 50_000) }
        assertEquals("2027-01-31", RecurringCharges.find(monthEnd, "2027-01-02").single().nextDate)
        val janEnd = listOf("2026-11-30", "2026-12-31", "2027-01-31").map { spend("rent", it, 50_000) }
        assertEquals("2027-02-28", RecurringCharges.find(janEnd, "2027-02-02").single().nextDate)
    }
}
