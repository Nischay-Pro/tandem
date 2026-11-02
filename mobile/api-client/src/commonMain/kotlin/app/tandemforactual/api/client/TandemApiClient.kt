package app.tandemforactual.api.client

import app.tandemforactual.api.generated.Api
import app.tandemforactual.api.generated.Auth
import app.tandemforactual.api.generated.api.AuthenticationApi
import app.tandemforactual.api.generated.api.BudgetsApi
import arrow.core.Either
import com.kroegerama.openapi.kmp.gen.companion.AuthItem
import com.kroegerama.openapi.kmp.gen.companion.CallException
import com.kroegerama.openapi.kmp.gen.companion.HttpCallException
import com.kroegerama.openapi.kmp.gen.companion.HttpCallResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

data class PairedDevice(
    val deviceId: String,
    val principalId: String,
    val budgetIds: List<String>,
)

data class BudgetCatalogItem(
    val budgetId: String,
    val name: String,
    val isEncrypted: Boolean,
)

class TandemApiException(
    val errorCode: String,
    val httpStatus: Int? = null,
    cause: Throwable? = null,
    safeMessageOverride: String? = null,
) : Exception(
        safeMessageOverride ?: when {
            errorCode == "not-paired" -> "Pair this device before loading its budgets."
            httpStatus == 400 -> "Check the pairing code and try again."
            errorCode == "invalid_pairing_code" -> "The pairing code is invalid, expired, or already used."
            errorCode == "invalid_device_credential" -> "This device needs to be paired again."
            httpStatus == 401 -> "The server could not verify this request. Check server access and try again."
            httpStatus == 403 -> "This device is not allowed to access that budget."
            httpStatus in 500..599 -> "The Tandem server could not read Actual. Try again later."
            else -> "Could not connect to the Tandem server. Check the address and connection."
        },
        cause,
    )

class TandemApiProtocolException(
    message: String,
) : IllegalStateException(message)

/** Owns the generated API client's server address and in-memory device credential. */
class TandemApiClient(
    private val allowInsecureLoopback: Boolean = false,
) {
    private var accessToken: String? = null
    private var serverUrl: String? = null
    private var pairedDevice: PairedDevice? = null
    internal val bearerAuth =
        Auth.BearerAuth {
            accessToken?.let(AuthItem::Bearer)
        }

    init {
        Api.setAuthProvider(bearerAuth)
    }

    val isAuthenticated: Boolean
        get() = accessToken != null

    val currentServerUrl: String?
        get() = serverUrl

    fun restoreSession(
        serverUrl: String,
        token: String,
    ) {
        val sessionToken =
            token.takeIf { it.isNotBlank() }
                ?: throw TandemApiProtocolException("Stored device credential is empty")
        val parsedUrl = parseServerUrl(serverUrl, allowInsecureLoopback)
        Api.baseUrl = parsedUrl
        this.serverUrl = parsedUrl.toString()
        accessToken = sessionToken
        Api.setAuthProvider(bearerAuth)
    }

    fun exportStoredSession(): String? {
        val token = accessToken ?: return null
        val server = serverUrl ?: return null
        val device = pairedDevice ?: return null
        return buildJsonObject {
            put("serverUrl", server)
            put("accessToken", token)
            put(
                "device",
                buildJsonObject {
                    put("deviceId", device.deviceId)
                    put("principalId", device.principalId)
                    put("budgetIds", JsonArray(device.budgetIds.map(::JsonPrimitive)))
                },
            )
        }.toString()
    }

    fun restoreStoredSession(value: String): PairedDevice {
        val root =
            try {
                Json.parseToJsonElement(value).jsonObject
            } catch (_: Exception) {
                throw TandemApiProtocolException("Stored device session is invalid")
            }
        val server = root.requiredString("serverUrl")
        val token = root.requiredString("accessToken")
        val deviceJson =
            root["device"] as? JsonObject
                ?: throw TandemApiProtocolException("Stored device session has no device")
        val device =
            PairedDevice(
                deviceId = deviceJson.requiredString("deviceId"),
                principalId = deviceJson.requiredString("principalId"),
                budgetIds = deviceJson.requiredStringArray("budgetIds"),
            )
        restoreSession(server, token)
        pairedDevice = device
        return device
    }

    suspend fun pair(
        serverUrl: String,
        code: String,
        deviceName: String,
    ): PairedDevice {
        Api.baseUrl = parseServerUrl(serverUrl, allowInsecureLoopback)
        this.serverUrl = Api.baseUrl.toString()
        accessToken = null
        pairedDevice = null

        val body =
            buildJsonObject {
                put("code", code.trim())
                put("deviceName", deviceName.trim())
            }.toMap()
        val payload = unwrap(AuthenticationApi.pairDevice(body))
        val deviceId = payload.requiredString("deviceId")
        val principalId = payload.requiredString("principalId")
        val budgetIds = payload.requiredStringArray("budgetIds")
        val token = payload.requiredString("accessToken")

        accessToken = token
        return PairedDevice(deviceId, principalId, budgetIds).also { pairedDevice = it }
    }

    suspend fun listBudgets(): List<BudgetCatalogItem> {
        requireAuthenticated()
        return parseBudgetCatalog(BudgetsApi.listBudgets())
    }

    private suspend fun parseBudgetCatalog(
        response: Either<CallException, HttpCallResponse<Map<String, JsonElement>>>,
    ): List<BudgetCatalogItem> {
        val payload = unwrap(response)
        val budgets =
            payload["budgets"] as? JsonArray
                ?: throw TandemApiProtocolException("Budget catalog response is missing its budgets")

        return budgets.map { element ->
            val budget =
                element as? JsonObject
                    ?: throw TandemApiProtocolException("Budget catalog contains an invalid item")
            val encrypted =
                (budget["isEncrypted"] as? JsonPrimitive)?.booleanOrNull
                    ?: throw TandemApiProtocolException("Budget catalog item has an invalid encryption flag")
            BudgetCatalogItem(
                budgetId = budget.requiredString("budgetId"),
                name = budget.requiredString("name"),
                isEncrypted = encrypted,
            )
        }
    }

    fun forgetSession() {
        accessToken = null
        serverUrl = null
        pairedDevice = null
        Api.clearAuthProvider(bearerAuth)
    }

    private fun requireAuthenticated() {
        if (accessToken.isNullOrBlank()) {
            throw TandemApiException("not-paired")
        }
        Api.setAuthProvider(bearerAuth)
    }

    private suspend fun unwrap(result: Either<CallException, HttpCallResponse<Map<String, JsonElement>>>): JsonObject =
        result.fold(
            { error -> throw error.toTandemApiException() },
            { response -> JsonObject(response.data) },
        )

    private suspend fun CallException.toTandemApiException(): TandemApiException =
        when (this) {
            is HttpCallException -> {
                val errorCode = if (code == 401) apiErrorCode() else null
                TandemApiException(errorCode ?: "http-$code", code, this)
            }
            else -> TandemApiException("network-error", cause = this)
        }

    private suspend fun HttpCallException.apiErrorCode(): String? =
        try {
            val root = Json.parseToJsonElement(raw.bodyAsText()) as? JsonObject
            val error = root?.get("error") as? JsonObject
            (error?.get("code") as? JsonPrimitive)?.contentOrNull
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
}

private fun parseServerUrl(
    value: String,
    allowInsecureLoopback: Boolean,
): Url {
    val address = value.trim().trimEnd('/')
    val hasExplicitHost =
        address
            .substringAfter("://", missingDelimiterValue = "")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .isNotBlank()
    val url =
        try {
            Url(address)
        } catch (error: Exception) {
            throw TandemApiException("invalid-server-url", cause = error)
        }
    val secure = url.protocol.name == "https"
    val allowedLoopback =
        allowInsecureLoopback &&
            url.protocol.name == "http" &&
            url.host.lowercase() in setOf("localhost", "127.0.0.1")
    if (!hasExplicitHost || url.host.isBlank() || (!secure && !allowedLoopback) || url.user != null || url.password != null) {
        throw TandemApiException("invalid-server-url")
    }
    return url
}

private fun JsonObject.requiredString(name: String): String =
    (this[name] as? JsonPrimitive)
        ?.takeIf { it.isString }
        ?.contentOrNull
        ?.takeIf { it.isNotBlank() }
        ?: throw TandemApiProtocolException("Server response is missing a valid $name")

private fun JsonObject.requiredStringArray(name: String): List<String> {
    val values =
        this[name] as? JsonArray
            ?: throw TandemApiProtocolException("Server response is missing a valid $name")
    return values.map { value ->
        (value as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?: throw TandemApiProtocolException("Server response contains an invalid $name entry")
    }
}
