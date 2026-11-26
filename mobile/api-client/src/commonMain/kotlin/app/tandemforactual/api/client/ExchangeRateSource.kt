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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One day's rates: one [base] is worth each rate of its currency. Rates are exact decimal strings, as Tandem stores them. */
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

    /** Every published day from [from] to [to], oldest first; the first may be the last working day before [from]. */
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

/** Rates from a Frankfurter server (ECB reference rates). Only the base currency, symbols and dates are sent. */
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

    private val root =
        try {
            parseServerUrl(baseUrl, allowInsecurePrivateNetwork = true)
                .takeIf { it.encodedQuery.isEmpty() && it.encodedFragment.isEmpty() }
                ?.toString()
                ?.trimEnd('/')
        } catch (_: TandemApiException) {
            null
        } ?: throw ExchangeRateException.InvalidAddress()

    override suspend fun latest(
        base: String,
        symbols: Set<String>,
    ): ExchangeRateDay {
        val body = get("latest", base, symbols)
        val date = (body["date"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        return day(date, body.rateBase(base), body["rates"])
    }

    override suspend fun range(
        base: String,
        symbols: Set<String>,
        from: String,
        to: String,
    ): List<ExchangeRateDay> {
        require(ISO_DATE.matches(from) && ISO_DATE.matches(to) && from <= to) { "Dates must be yyyy-MM-dd, oldest first." }
        val body = get("$from..$to", base, symbols)
        val currency = body.rateBase(base)
        val days = body["rates"] as? JsonObject ?: throw ExchangeRateException.BadResponse()
        return days.map { (date, rates) -> day(date, currency, rates) }.sortedBy { it.date }
    }

    private suspend fun get(
        path: String,
        base: String,
        symbols: Set<String>,
    ): JsonObject {
        val currency = base.trim().uppercase()
        val codes = symbols.map { it.trim().uppercase() }.distinct().sorted()
        if ((codes + currency).any { !CURRENCY.matches(it) }) throw ExchangeRateException.UnknownCurrency()
        val (status, text) =
            try {
                http
                    .get("$root/$path") {
                        parameter("base", currency)
                        if (codes.isNotEmpty()) parameter("symbols", codes.joinToString(","))
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
        return runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject
            ?: throw ExchangeRateException.BadResponse()
    }

    private fun JsonObject.rateBase(requested: String): String =
        (this["base"] as? JsonPrimitive)?.content?.takeIf { it == requested.trim().uppercase() }
            ?: throw ExchangeRateException.BadResponse()

    private fun day(
        date: String?,
        base: String,
        rates: JsonElement?,
    ): ExchangeRateDay {
        if (date == null || !ISO_DATE.matches(date) || rates !is JsonObject) throw ExchangeRateException.BadResponse()
        val parsed =
            rates.mapValues { (code, value) ->
                val rate = (value as? JsonPrimitive)?.takeIf { !it.isString }?.content
                if (!CURRENCY.matches(code) ||
                    rate == null ||
                    !DECIMAL.matches(rate) ||
                    rate.toDouble().let { it <= 0.0 || !it.isFinite() }
                ) {
                    throw ExchangeRateException.BadResponse()
                }
                rate
            }
        return ExchangeRateDay(date, base, parsed)
    }

    companion object {
        const val DEFAULT_URL = "https://api.frankfurter.dev/v1"
        private const val TIMEOUT_MILLIS = 15_000L
        private val CURRENCY = Regex("^[A-Z]{3}$")
        private val ISO_DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")
        private val DECIMAL = Regex("^\\d+(\\.\\d+)?([eE][+-]?\\d+)?$")
    }
}
