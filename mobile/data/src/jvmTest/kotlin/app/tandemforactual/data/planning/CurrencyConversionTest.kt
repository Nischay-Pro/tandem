package app.tandemforactual.data.planning

import androidx.room3.Room
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.database.openTandemDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        assertEquals("fx:EUR:USD", rate.id)
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
}
