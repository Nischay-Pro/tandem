package app.tandemforactual.api.client

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Rates as of [date]: one [base] is worth each rate of its currency. Rates are exact decimal strings, as Tandem stores them. */
data class ExchangeRateDay(
    val date: String,
    val base: String,
    val rates: Map<String, String>,
)

/** Reference exchange rates. Dates are ISO `yyyy-MM-dd` strings; an empty set of symbols asks for every currency. */
interface ExchangeRateSource {
    suspend fun latest(
        base: String,
        symbols: Set<String>,
    ): ExchangeRateDay

    /** Every day from [from] to [to], oldest first; a currency whose last rate is older may add a day before [from]. */
    suspend fun range(
        base: String,
        symbols: Set<String>,
        from: String,
        to: String,
    ): List<ExchangeRateDay>
}

sealed class ExchangeRateException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class InvalidAddress : ExchangeRateException("Use an https:// exchange-rate address, or http:// on your local network.")

    class Unreachable(
        cause: Throwable? = null,
    ) : ExchangeRateException("Couldn’t reach the exchange-rate server. Check your connection and try again.", cause)

    class RateLimited : ExchangeRateException("The exchange-rate server is busy. Try again later.")

    class UnknownCurrency : ExchangeRateException("The exchange-rate server doesn’t have rates for one of these currencies.")

    class BadResponse : ExchangeRateException("The exchange-rate server sent a response Tandem couldn’t read.")
}

/**
 * Rates from a Frankfurter v2 server (blended central-bank reference rates). [baseUrl] is the server's `/v2` address;
 * only the base currency, quote currencies and dates are sent.
 */
class FrankfurterRateSource(
    http: HttpClient,
    baseUrl: String = DEFAULT_URL,
) : ExchangeRateSource {
    private val http =
        http.config {
            install(HttpTimeout) {
                requestTimeoutMillis = TIMEOUT_MILLIS
                connectTimeoutMillis = TIMEOUT_MILLIS
            }
        }

    private val root = address(baseUrl) ?: throw ExchangeRateException.InvalidAddress()

    /** The newest rate for each currency, dated by the oldest of them so a stale rate never looks current. */
    override suspend fun latest(
        base: String,
        symbols: Set<String>,
    ): ExchangeRateDay {
        val rows = rates(base, symbols).sortedBy { it.date }
        if (rows.isEmpty()) throw ExchangeRateException.BadResponse()
        return ExchangeRateDay(rows.first().date, normalized(base), rows.associate { it.quote to it.rate })
    }

    override suspend fun range(
        base: String,
        symbols: Set<String>,
        from: String,
        to: String,
    ): List<ExchangeRateDay> {
        require(ISO_DATE.matches(from) && ISO_DATE.matches(to) && from <= to) { "Dates must be yyyy-MM-dd, oldest first." }
        return rates(base, symbols, "from" to from, "to" to to)
            .groupBy { it.date }
            .entries
            .sortedBy { it.key }
            .map { (date, rows) -> ExchangeRateDay(date, normalized(base), rows.associate { it.quote to it.rate }) }
    }

    private suspend fun rates(
        base: String,
        symbols: Set<String>,
        vararg dates: Pair<String, String>,
    ): List<Row> {
        val currency = normalized(base)
        val codes = symbols.map(::normalized).distinct().sorted()
        if ((codes + currency).any { !CURRENCY.matches(it) }) throw ExchangeRateException.UnknownCurrency()
        val (status, text) =
            try {
                http
                    .get("$root/rates") {
                        parameter("base", currency)
                        if (codes.isNotEmpty()) parameter("quotes", codes.joinToString(","))
                        dates.forEach { (name, value) -> parameter(name, value) }
                    }.let { it.status to it.bodyAsText() }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                throw ExchangeRateException.Unreachable(error)
            }
        when {
            status == HttpStatusCode.TooManyRequests -> throw ExchangeRateException.RateLimited()
            status == HttpStatusCode.NotFound || status == HttpStatusCode.UnprocessableEntity ->
                throw ExchangeRateException.UnknownCurrency()
            status.value >= 500 -> throw ExchangeRateException.Unreachable()
            !status.isSuccess() -> throw ExchangeRateException.BadResponse()
        }
        val rows = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonArray ?: throw ExchangeRateException.BadResponse()
        return rows.map { row(it, currency) }.filter { it.quote != currency }
    }

    private fun row(
        element: JsonElement,
        base: String,
    ): Row {
        val row = element as? JsonObject
        val date = row?.text("date")
        val quote = row?.text("quote")
        val rate = (row?.get("rate") as? JsonPrimitive)?.takeIf { !it.isString }?.content
        if (date == null ||
            !ISO_DATE.matches(date) ||
            row.text("base") != base ||
            quote == null ||
            !CURRENCY.matches(quote) ||
            rate == null ||
            !DECIMAL.matches(rate) ||
            rate.toDouble().let { it <= 0.0 || !it.isFinite() }
        ) {
            throw ExchangeRateException.BadResponse()
        }
        return Row(date, quote, rate)
    }

    private fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private class Row(
        val date: String,
        val quote: String,
        val rate: String,
    )

    companion object {
        const val DEFAULT_URL = "https://api.frankfurter.dev/v2"
        private const val TIMEOUT_MILLIS = 15_000L
        private val CURRENCY = Regex("^[A-Z][A-Z0-9]{1,9}$")
        private val ISO_DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")
        private val DECIMAL = Regex("^\\d+(\\.\\d+)?([eE][+-]?\\d+)?$")

        private fun normalized(code: String) = code.trim().uppercase()

        /** A source using the platform's HTTP engine; a blank [baseUrl] means Frankfurter's public server. */
        fun connect(baseUrl: String = DEFAULT_URL) = FrankfurterRateSource(HttpClient(), baseUrl.ifBlank { DEFAULT_URL })

        /** The server address [baseUrl] names, without a trailing slash, or null when it isn't one Tandem would use. */
        fun address(baseUrl: String): String? =
            try {
                parseServerUrl(baseUrl, allowInsecurePrivateNetwork = true)
                    .takeIf { it.encodedQuery.isEmpty() && it.encodedFragment.isEmpty() }
                    ?.toString()
                    ?.trimEnd('/')
            } catch (_: TandemApiException) {
                null
            }
    }
}
