package app.tandemforactual.data.planning

import androidx.room3.Room
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.database.openTandemDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject

class CurrencyConversionTest {
    @Test
    fun convertsBalancesWithEnteredRates() {
        val rates =
            listOf(
                ExchangeRate("EUR", "USD", "1.10", "2026-11-20"),
                ExchangeRate("USD", "INR", "80", "2026-11-01"),
                ExchangeRate("GBP", "USD", "nope", "2026-11-01"),
            )
        assertEquals(1.1, rateBetween("EUR", "USD", rates)?.value)
        assertEquals(0.0125, rateBetween("INR", "USD", rates)?.value)
        assertEquals("2026-11-01", rateBetween("INR", "USD", rates)?.asOf)
        assertNull(rateBetween("USD", "USD", rates))
        assertNull(rateBetween("GBP", "USD", rates))
        assertEquals(110L, convertMinor(100, rateBetween("EUR", "USD", rates)!!))

        val total =
            convertedTotal(
                listOf(1_000L to null, 1_000L to "USD", 10_000L to "EUR", 8_000L to "INR", 500L to "GBP"),
                "USD",
                rates,
            )
        assertEquals(ConvertedTotal(2_000 + 11_000 + 100, true, setOf("GBP"), "2026-11-01"), total)
        assertEquals(ConvertedTotal(5, false, emptySet(), null), convertedTotal(listOf(5L to null), "USD", rates))
    }

    @Test
    fun currencyModelsRoundTripThroughJson() {
        val currency = AccountCurrency("acc", "EUR")
        assertEquals("currency:acc", currency.id)
        assertEquals(currency, AccountCurrency.fromJson(currency.toJson()))
        assertEquals(AccountCurrency("", ""), AccountCurrency.fromJson(JsonObject(emptyMap())))
        val rate = ExchangeRate("EUR", "USD", "1.1", "2026-11-20")
        assertEquals("fx:EUR:USD:2026-11-20", rate.id)
        assertEquals(rate, ExchangeRate.fromJson(rate.toJson()))
        assertNull(rate.copy(rate = "0").value)
        assertNull(rate.copy(rate = "Infinity").value)
        assertEquals(ExchangeRate("", "", "", ""), ExchangeRate.fromJson(JsonObject(emptyMap())))
    }

    @Test
    fun storesCurrenciesAndRates(): Unit =
        runBlocking {
            val database = openTandemDatabase(Room.inMemoryDatabaseBuilder<TandemDatabase>().setQueryCoroutineContext(Dispatchers.IO))
            try {
                val store = PlanningStore(database.planningRecordDao(), "b", PlanningCipher(PlanningCipher.newKey())) { 1L }
                store.save(AccountCurrency("acc", "EUR"))
                assertEquals(listOf(AccountCurrency("acc", "EUR")), store.accountCurrencies().first())
                store.save(AccountCurrency("acc", ""))
                assertTrue(store.accountCurrencies().first().isEmpty())
                val rate = ExchangeRate("EUR", "USD", "1.1", "2026-11-20")
                store.save(rate)
                assertEquals(listOf(rate), store.exchangeRates().first())
                store.deleteExchangeRate(rate)
                assertTrue(store.exchangeRates().first().isEmpty())
            } finally {
                database.close()
            }
        }

    @Test
    fun picksTheRateOnOrBeforeADate() {
        val rates =
            listOf(
                ExchangeRate("EUR", "USD", "1.20", "2026-03-01"),
                ExchangeRate("EUR", "USD", "1.10", "2026-01-01"),
                ExchangeRate("USD", "EUR", "0.5", "2026-02-01"),
                ExchangeRate("USD", "EUR", "0.25", "2026-03-01"),
                ExchangeRate("EUR", "USD", "bad", "2026-04-01"),
            )
        assertEquals(listOf("2026-01-01", "2026-02-01", "2026-03-01", "2026-03-01"), rateHistory("EUR", "USD", rates).map { it.asOf })
        assertEquals(1.2, rateBetween("EUR", "USD", rates)?.value)
        assertEquals(1.2, rateBetween("EUR", "USD", rates, "2026-12-31")?.value)
        assertEquals(2.0, rateBetween("EUR", "USD", rates, "2026-02-28")?.value)
        assertEquals(1.1, rateBetween("EUR", "USD", rates, "2026-01-31")?.value)
        assertEquals(1.1, rateBetween("EUR", "USD", rates, "2025-06-30")?.value)
        assertEquals(1.1, rateBetween("EUR", "USD", rates, "2026-01-01")?.value)
        assertNull(rateBetween("GBP", "USD", rates, "2026-01-01"))
        assertEquals(20_000L, convertedTotal(listOf(10_000L to "EUR"), "USD", rates, "2026-02-15").totalMinor)
        assertEquals(12_000L, convertedTotal(listOf(10_000L to "EUR"), "USD", rates).totalMinor)
    }

    @Test
    fun keepsEachDatedRateAndReadsOldUndatedRecords(): Unit =
        runBlocking {
            val database = openTandemDatabase(Room.inMemoryDatabaseBuilder<TandemDatabase>().setQueryCoroutineContext(Dispatchers.IO))
            try {
                val store = PlanningStore(database.planningRecordDao(), "b", PlanningCipher(PlanningCipher.newKey())) { 1L }
                val old = ExchangeRate("EUR", "USD", "1.05", "2025-12-01")
                store.put("fx:EUR:USD", ExchangeRate.TYPE, old.toJson())
                val january = ExchangeRate("EUR", "USD", "1.1", "2026-01-15")
                val march = ExchangeRate("EUR", "USD", "1.2", "2026-03-10")
                store.save(january)
                store.save(march)
                store.save(march.copy(rate = "1.25"))

                val stored = store.exchangeRates().first()
                assertEquals(3, stored.size)
                val legacy = stored.single { it.asOf == "2025-12-01" }
                assertEquals("fx:EUR:USD", legacy.id)
                assertEquals(old.copy(recordId = "fx:EUR:USD"), legacy)
                assertTrue(january in stored)
                assertEquals("fx:EUR:USD:2026-03-10", stored.single { it.asOf == "2026-03-10" }.id)
                assertEquals(1.25, rateBetween("EUR", "USD", stored)?.value)
                assertEquals(1.1, rateBetween("EUR", "USD", stored, "2026-02-28")?.value)
                assertEquals(1.05, rateBetween("EUR", "USD", stored, "2026-01-01")?.value)
                assertEquals(1.05, rateBetween("EUR", "USD", stored, "2025-01-01")?.value)

                store.deleteExchangeRate(legacy)
                assertEquals(
                    listOf("2026-01-15", "2026-03-10"),
                    store
                        .exchangeRates()
                        .first()
                        .map { it.asOf }
                        .sorted(),
                )
            } finally {
                database.close()
            }
        }
}
