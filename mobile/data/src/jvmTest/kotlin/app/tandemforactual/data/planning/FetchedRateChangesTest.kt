package app.tandemforactual.data.planning

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonPrimitive

class FetchedRateChangesTest {
    private fun fetched(
        quote: String,
        rate: String,
        date: String,
    ) = ExchangeRate("USD", quote, rate, date, source = "frankfurter")

    @Test
    fun fetchedRatesKeepTheirSourceAndEnteredOnesHaveNone() {
        val rate = fetched("EUR", "0.92", "2026-11-20")
        assertEquals(JsonPrimitive("frankfurter"), rate.toJson()["source"])
        assertEquals(rate, ExchangeRate.fromJson(rate.id, rate.toJson()))
        assertTrue(rate.fetched)
        assertTrue(rateBetween("EUR", "USD", listOf(rate))!!.fetched)

        val entered = ExchangeRate("EUR", "USD", "1.08", "2026-11-20")
        assertFalse("source" in entered.toJson())
        assertNull(ExchangeRate.fromJson(entered.toJson()).source)
        assertFalse(entered.fetched)
    }

    @Test
    fun backfillKeepsMonthEndsAndTheLatestOnly() {
        val days =
            listOf(
                fetched("EUR", "0.90", "2026-09-29"),
                fetched("EUR", "0.91", "2026-09-30"),
                fetched("GBP", "0.79", "2026-09-30"),
                fetched("EUR", "0.92", "2026-10-30"),
                fetched("EUR", "0.93", "2026-10-31"),
                fetched("EUR", "0.94", "2026-11-24"),
                fetched("EUR", "0.95", "2026-11-25"),
            )

        val changes = fetchedRateChanges(days, emptyList())

        assertEquals(
            listOf("EUR 2026-09-30", "GBP 2026-09-30", "EUR 2026-10-31", "EUR 2026-11-25"),
            changes.save.map { "${it.to} ${it.asOf}" },
        )
        assertEquals(emptyList(), changes.remove)
    }

    @Test
    fun aRateEnteredByHandIsNeverReplaced() {
        val stored =
            listOf(
                ExchangeRate("EUR", "USD", "1.08", "2026-11-25"),
                ExchangeRate("USD", "GBP", "0.80", "2026-11-25"),
            )
        val changes =
            fetchedRateChanges(
                listOf(
                    fetched("EUR", "0.95", "2026-11-25"),
                    fetched("GBP", "0.79", "2026-11-25"),
                    ExchangeRate("USD", "JPY", "150", "2026-11-25"),
                ),
                stored,
            )

        assertEquals(FetchedRateChanges(emptyList(), emptyList()), changes)
    }

    @Test
    fun aNewerDailyRateReplacesTheOlderOneInTheSameMonth() {
        val october = fetched("EUR", "0.93", "2026-10-31")
        val monday = fetched("EUR", "0.94", "2026-11-23")
        val tuesday = fetched("EUR", "0.95", "2026-11-24")

        val changes = fetchedRateChanges(listOf(tuesday), listOf(october, monday))
        assertEquals(listOf(tuesday), changes.save)
        assertEquals(listOf(monday), changes.remove)

        assertEquals(FetchedRateChanges(emptyList(), emptyList()), fetchedRateChanges(listOf(tuesday), listOf(october, tuesday)))

        val revised = tuesday.copy(rate = "0.96")
        assertEquals(FetchedRateChanges(listOf(revised), emptyList()), fetchedRateChanges(listOf(revised), listOf(tuesday)))
    }
}
