package app.tandemforactual.api.client

import app.tandemforactual.data.planning.PlanningTransport
import app.tandemforactual.data.planning.RemotePlanningPage
import app.tandemforactual.data.planning.RemotePlanningRecord
import app.tandemforactual.data.planning.RemoteWrite
import app.tandemforactual.data.planning.WrappedPlanningKey
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/** Extension sync failed; [status] is the HTTP status, or null when the service couldn't be reached. */
class ExtensionSyncException(
    val status: Int?,
) : IllegalStateException(status?.let { "Extension sync answered HTTP $it" } ?: "Extension sync is unreachable")

/** Talks to Tandem's optional extension sync for one budget, authorized by the Actual session token. */
@OptIn(ExperimentalEncodingApi::class)
class ExtensionSyncClient(
    private val http: HttpClient,
    serverUrl: String,
    private val budgetId: String,
    private val token: () -> String?,
) : PlanningTransport {
    private val base = "${serverUrl.trimEnd('/')}/extension/v1/budgets/$budgetId"

    private fun HttpRequestBuilder.auth() = header("x-actual-token", token() ?: throw TandemApiException("sign_in_required"))

    private suspend fun json(response: HttpResponse): JsonObject {
        if (!response.status.isSuccess()) throw ExtensionSyncException(response.status.value)
        return Json.parseToJsonElement(response.bodyAsText()).jsonObject
    }

    override suspend fun key(): WrappedPlanningKey? {
        val response = http.get("$base/key") { auth() }
        if (response.status == HttpStatusCode.NotFound) return null
        val body = json(response)
        return WrappedPlanningKey(
            body.text("keyId"),
            Base64.decode(body.text("wrappedKey")),
            Base64.decode(body.text("wrapSalt")),
            Base64.decode(body.text("keyCheck")),
        )
    }

    override suspend fun putKey(
        key: WrappedPlanningKey,
        expectedKeyId: String?,
    ): Boolean {
        val response =
            http.put("$base/key") {
                auth()
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("keyId", key.keyId)
                        put("wrappedKey", Base64.encode(key.wrappedKey))
                        put("wrapSalt", Base64.encode(key.wrapSalt))
                        put("keyCheck", Base64.encode(key.keyCheck))
                        put("expectedKeyId", expectedKeyId?.let(::JsonPrimitive) ?: JsonNull)
                    }.toString(),
                )
            }
        if (response.status == HttpStatusCode.Conflict) return false
        json(response)
        return true
    }

    override suspend fun changes(
        after: Long,
        anchor: String?,
    ): RemotePlanningPage {
        val query = anchor?.let { "&anchor=${it.encodeURLParameter()}" }.orEmpty()
        val body = json(http.get("$base/changes?after=$after&limit=200$query") { auth() })
        return RemotePlanningPage(
            body.getValue("records").jsonArray.map { record(it.jsonObject) },
            body.getValue("cursor").jsonPrimitive.long,
            body.getValue("hasMore").jsonPrimitive.boolean,
            body["restored"]?.jsonPrimitive?.boolean ?: false,
        )
    }

    override suspend fun put(
        recordId: String,
        recordType: String,
        schemaVersion: Int,
        revision: Long,
        baseRevision: Long?,
        deleted: Boolean,
        iv: ByteArray,
        ciphertext: ByteArray,
    ): RemoteWrite {
        val response =
            http.put("$base/records/$recordId") {
                auth()
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("recordType", recordType)
                        put("schemaVersion", schemaVersion)
                        put("revision", revision)
                        put("baseRevision", baseRevision?.let(::JsonPrimitive) ?: JsonNull)
                        put("deleted", deleted)
                        put("iv", Base64.encode(iv))
                        put("ciphertext", Base64.encode(ciphertext))
                    }.toString(),
                )
            }
        if (response.status == HttpStatusCode.Conflict) {
            val current = Json.parseToJsonElement(response.bodyAsText()).jsonObject["current"]
            return RemoteWrite.Conflict((current as? JsonObject)?.let(::record))
        }
        return RemoteWrite.Stored(json(response).getValue("seq").jsonPrimitive.long)
    }

    private fun record(body: JsonObject) =
        RemotePlanningRecord(
            recordId = body.text("recordId"),
            recordType = body.text("recordType"),
            schemaVersion = body.getValue("schemaVersion").jsonPrimitive.int,
            revision = body.getValue("revision").jsonPrimitive.long,
            deleted = body.getValue("deleted").jsonPrimitive.boolean,
            iv = Base64.decode(body.text("iv")),
            ciphertext = Base64.decode(body.text("ciphertext")),
            seq = body.getValue("seq").jsonPrimitive.long,
        )

    private fun JsonObject.text(name: String) = getValue(name).jsonPrimitive.content

    companion object {
        /**
         * Whether this server runs extension sync; budgets work the same either way. Null when the server couldn't say
         * right now (offline, timed out, overloaded), so the answer isn't remembered.
         */
        suspend fun available(
            http: HttpClient,
            serverUrl: String,
        ): Boolean? =
            try {
                val response = http.get("${serverUrl.trimEnd('/')}/extension/v1/capabilities")
                when {
                    response.status.isSuccess() ->
                        runCatching {
                            Json.parseToJsonElement(response.bodyAsText()).jsonObject["features"]?.jsonArray.orEmpty().any {
                                it.jsonPrimitive.content == "planning-records"
                            }
                        }.getOrDefault(false)
                    response.status.value == 408 || response.status.value == 429 || response.status.value >= 500 -> null
                    else -> false
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
    }
}
