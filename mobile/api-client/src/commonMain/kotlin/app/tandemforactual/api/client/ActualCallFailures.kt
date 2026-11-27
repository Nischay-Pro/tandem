package app.tandemforactual.api.client

import app.tandemforactual.data.actual.ActualEditException
import app.tandemforactual.data.actual.ActualSyncException
import app.tandemforactual.data.actual.InvalidTransactionRequestException
import app.tandemforactual.data.actual.UnsupportedRuleException
import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.IOException

/** The platform's HTTP engine with limits, so a stalled server fails the call instead of holding it forever. */
fun actualHttpClient(): HttpClient =
    HttpClient {
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 60_000
            requestTimeoutMillis = 10 * 60_000
        }
    }

/** Runs a call to Actual and reports its failures with Tandem's error codes. */
internal suspend fun <T> callActual(block: suspend () -> T): T =
    try {
        withContext(Dispatchers.IO) { block() }
    } catch (error: CancellationException) {
        throw error
    } catch (error: TandemApiException) {
        throw error
    } catch (error: ActualEditException) {
        throw TandemApiException(error.code ?: "invalid_request", 422, error, error.message)
    } catch (error: UnsupportedRuleException) {
        throw TandemApiException("rule_unsupported", 422, error)
    } catch (error: ActualServerException) {
        throw TandemApiException(serverErrorCode(error.reason), error.httpStatus, error)
    } catch (error: ActualSyncException) {
        throw TandemApiException(error.reason.replace('-', '_'), null, error)
    } catch (error: InvalidTransactionRequestException) {
        throw TandemApiException("invalid_request", 422, error, error.message)
    } catch (error: HttpRequestTimeoutException) {
        throw TandemApiException("server_timeout", null, error)
    } catch (error: SocketTimeoutException) {
        throw TandemApiException("server_timeout", null, error)
    } catch (error: IOException) {
        throw TandemApiException("address_unreachable", null, error)
    } catch (error: Exception) {
        throw TandemApiException("unexpected_error", null, error)
    }

private fun serverErrorCode(reason: String): String =
    when (reason) {
        "invalid-password" -> "invalid_credentials"
        "unauthorized", "token-expired" -> "sign_in_expired"
        "budget-unavailable", "file-not-found" -> "budget_unavailable"
        "encrypted-budget" -> "encrypted_budget"
        "file-has-reset" -> "file_has_reset"
        "file-has-new-key" -> "file_has_new_key"
        "not-found", "network-failure" -> "address_unreachable"
        else -> if (reason.startsWith("bank-sync-")) "bank_sync_failed" else reason.replace('-', '_')
    }
