package app.tandemforactual.ui.core.session.planning

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import app.tandemforactual.api.client.ExchangeRateDay
import app.tandemforactual.api.client.ExchangeRateException
import app.tandemforactual.api.client.ExchangeRateSource
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.planning.AccountKind
import app.tandemforactual.data.planning.ExchangeRate
import app.tandemforactual.ui.core.format.budgetCurrencyCode
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import app.tandemforactual.ui.feature.currency.ExchangeRatesScreen
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExchangeRateFetchingTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val database =
        Room
            .inMemoryDatabaseBuilder<TandemDatabase>()
            .setDriver(AndroidSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    private val session = TandemMobileSession(TandemApiClient(), database, { 1_000L })
    private val planning = session.planning
    private val scope = CoroutineScope(Dispatchers.IO)

    @AfterTest
    fun close() {
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
        database.close()
    }

    private class FakeSource(
        private val latest: ExchangeRateDay? = null,
        private val history: List<ExchangeRateDay> = emptyList(),
        private val failure: Exception? = null,
    ) : ExchangeRateSource {
        val calls = mutableListOf<String>()

        override suspend fun latest(
            base: String,
            symbols: Set<String>,
        ): ExchangeRateDay {
            calls += "latest $base ${symbols.sorted()}"
            failure?.let { throw it }
            return latest!!
        }

        override suspend fun range(
            base: String,
            symbols: Set<String>,
            from: String,
            to: String,
        ): List<ExchangeRateDay> {
            calls += "range $base ${symbols.sorted()} $from $to"
            failure?.let { throw it }
            return history
        }
    }

    private object FailingSource : ExchangeRateSource {
        override suspend fun latest(
            base: String,
            symbols: Set<String>,
        ): ExchangeRateDay = throw AssertionError("No request while fetching is off")

        override suspend fun range(
            base: String,
            symbols: Set<String>,
            from: String,
            to: String,
        ): List<ExchangeRateDay> = throw AssertionError("No request while fetching is off")
    }

    private fun day(
        date: String,
        vararg rates: Pair<String, String>,
    ) = ExchangeRateDay(date, "USD", rates.toMap())

    private suspend fun stored() =
        planning
            .planningStore(BUDGET)!!
            .exchangeRates()
            .first()
            .sortedWith(compareBy({ it.to }, { it.asOf }))

    private suspend fun fetch(
        source: ExchangeRateSource,
        fetching: ExchangeRateFetching = ExchangeRateFetching(enabled = true),
        today: String = "2026-11-25",
        historyStart: String? = null,
    ) = fetching.update(planning, BUDGET, "USD", { source }, today, historyStart, "$BUDGET $today")

    @Test
    fun fetchingIsOffByDefault() {
        val state = AppState()
        assertFalse(state.exchangeRateFetching.enabled)
        assertEquals("false", state.persistentSettings()[ExchangeRateFetching.ENABLED])

        state.restorePersistentSettings(emptyMap())
        assertFalse(state.exchangeRateFetching.enabled)

        state.restorePersistentSettings(mapOf(ExchangeRateFetching.ENABLED to "true", ExchangeRateFetching.SERVER to "https://fx.home/v2"))
        assertTrue(state.exchangeRateFetching.enabled)
        assertEquals("https://fx.home/v2", state.exchangeRateFetching.server)
    }

    @Test
    fun nothingIsRequestedWhileOff() {
        assertEquals(ExchangeRateFetching(), runBlocking { fetch(FailingSource, ExchangeRateFetching()) })

        val state = AppState().apply { selectedBudgetId = BUDGET }
        val foreign = if (state.budgetCurrencyCode == "EUR") "GBP" else "EUR"
        runBlocking { planning.saveAccountCurrency(BUDGET, "travel", foreign) }
        with(planning) { scope.observe(BUDGET) }
        repeat(100) { if (planning.accountCurrencies.isEmpty()) Thread.sleep(20) }
        assertEquals(mapOf("travel" to foreign), planning.accountCurrencies)

        val active = AtomicReference<ExchangeRateSource>(FailingSource)
        composeTestRule.setContent {
            CompositionLocalProvider(LocalExchangeRateSource provides { active.get() }) {
                FetchExchangeRatesWhenOn(state, session)
            }
        }
        composeTestRule.waitForIdle()
        assertEquals(emptyList(), runBlocking { stored() })

        val source = FakeSource(latest = ExchangeRateDay("2026-11-25", state.budgetCurrencyCode, mapOf(foreign to "0.95")))
        active.set(source)
        composeTestRule.runOnIdle { state.exchangeRateFetching = state.exchangeRateFetching.copy(enabled = true) }
        for (attempt in 1..100) {
            composeTestRule.waitForIdle()
            if (state.exchangeRateFetching.fetchedOn.isNotEmpty()) break
            Thread.sleep(50)
        }
        assertEquals(listOf("latest ${state.budgetCurrencyCode} [$foreign]"), source.calls)
        assertEquals("0.95", runBlocking { stored() }.single().rate)
        assertEquals("", state.exchangeRateFetching.error)
    }

    @Test
    fun dailyFetchStoresDatedRatesAndNeverReplacesOnesEnteredByHand(): Unit =
        runBlocking {
            planning.saveAccountCurrency(BUDGET, "travel", "EUR")
            planning.saveAccountCurrency(BUDGET, "savings", "GBP")
            planning.saveExchangeRate(BUDGET, ExchangeRate("EUR", "USD", "1.08", "2026-11-25"))
            val source = FakeSource(latest = day("2026-11-25", "EUR" to "0.95", "GBP" to "0.79"))

            val result = fetch(source)

            assertEquals(listOf("latest USD [EUR, GBP]"), source.calls)
            assertEquals(
                listOf(
                    ExchangeRate("USD", "GBP", "0.79", "2026-11-25", source = "frankfurter"),
                    ExchangeRate("EUR", "USD", "1.08", "2026-11-25"),
                ),
                stored(),
            )
            assertEquals(ExchangeRateFetching(enabled = true, checked = "$BUDGET 2026-11-25", fetchedOn = "2026-11-25"), result)

            assertEquals(result, fetch(source, result))
            assertEquals(1, source.calls.size)
        }

    @Test
    fun firstFetchBackfillsMonthEndsAndTheLatestOnly(): Unit =
        runBlocking {
            planning.saveAccountCurrency(BUDGET, "travel", "EUR")
            val history =
                listOf(
                    day("2026-09-29", "EUR" to "0.90"),
                    day("2026-09-30", "EUR" to "0.91"),
                    day("2026-10-30", "EUR" to "0.92"),
                    day("2026-10-31", "EUR" to "0.93"),
                    day("2026-11-24", "EUR" to "0.94"),
                )
            val source = FakeSource(latest = day("2026-11-25", "EUR" to "0.95"), history = history)

            fetch(source, historyStart = "2026-09-01")

            assertEquals(listOf("range USD [EUR] 2026-09-01 2026-11-25", "latest USD [EUR]"), source.calls)
            assertEquals(listOf("2026-09-30", "2026-10-31", "2026-11-25"), stored().map { it.asOf })

            val nextDay = FakeSource(latest = day("2026-11-26", "EUR" to "0.96"))
            fetch(nextDay, today = "2026-11-26", historyStart = "2026-09-01")
            assertEquals(listOf("latest USD [EUR]"), nextDay.calls)
            assertEquals(listOf("2026-09-30", "2026-10-31", "2026-11-26"), stored().map { it.asOf })
        }

    @Test
    fun aFailedFetchKeepsTheRatesAndShowsWhatWentWrong(): Unit =
        runBlocking {
            planning.saveAccountCurrency(BUDGET, "travel", "EUR")
            planning.saveExchangeRate(BUDGET, ExchangeRate("EUR", "USD", "1.08", "2026-11-20"))

            val offline = fetch(FakeSource(failure = ExchangeRateException.Unreachable()))
            assertEquals("Couldn’t reach Frankfurter. Rates stay as they were.", offline.error)
            assertEquals("", offline.checked)
            assertEquals(listOf(ExchangeRate("EUR", "USD", "1.08", "2026-11-20")), stored())

            val custom = ExchangeRateFetching(enabled = true, server = "https://fx.home/v2")
            val unknown = fetch(FakeSource(failure = ExchangeRateException.UnknownCurrency()), custom)
            assertEquals("Your exchange-rate server has no rates for one of your currencies. Rates stay as they were.", unknown.error)
            assertEquals("$BUDGET 2026-11-25", unknown.checked)

            val state =
                AppState().apply {
                    selectedBudgetId = BUDGET
                    exchangeRateFetching = offline
                }
            val foreign = if (state.budgetCurrencyCode == "EUR") "GBP" else "EUR"
            planning.saveAccountCurrency(BUDGET, "travel", foreign)
            with(planning) { scope.observe(BUDGET) }
            repeat(100) { if (planning.accountCurrencies.isEmpty()) Thread.sleep(20) }
            val accounts = listOf(LiveAccountSummary("travel", "Travel card", 10_000, AccountKind.BANK, offBudget = false))
            composeTestRule.setContent {
                TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                    ExchangeRatesScreen(state, session, accounts, NAVIGATION)
                }
            }
            composeTestRule.onNodeWithText("Couldn’t reach Frankfurter. Rates stay as they were.").performScrollTo()

            composeTestRule.runOnIdle { state.exchangeRateFetching = offline.copy(error = "", fetchedOn = "2026-11-25") }
            composeTestRule.onNodeWithText("Couldn’t reach Frankfurter. Rates stay as they were.").assertDoesNotExist()
            composeTestRule.onNodeWithText("Last fetched", substring = true).performScrollTo()
        }

    private companion object {
        const val BUDGET = "budget-1"
        val NAVIGATION =
            AppNavigation(open = {}, reset = {}, replaceTop = {}, newTransaction = {}, selectTab = {}, back = {}, backToList = {})
    }
}
