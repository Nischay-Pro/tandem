package app.tandemforactual.api.client

import app.tandemforactual.data.actual.SyncProtocol
import app.tandemforactual.data.actual.SyncRequest
import app.tandemforactual.data.actual.SyncResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** A failure reported by an Actual server; [reason] is Actual's own code, such as `file-has-reset` or `invalid-password`. */
class ActualServerException(
    val reason: String,
    val httpStatus: Int? = null,
    cause: Throwable? = null,
) : Exception(reason, cause)

data class ActualLoginMethod(
    val method: String,
    val active: Boolean,
    val displayName: String?,
)

data class ActualServerStatus(
    val bootstrapped: Boolean,
    val loginMethods: List<ActualLoginMethod>,
)

data class ActualRemoteFile(
    val fileId: String,
    val groupId: String?,
    val name: String,
    val encryptKeyId: String?,
    val deleted: Boolean,
)

data class ActualFileInfo(
    val fileId: String,
    val groupId: String?,
    val name: String,
    val encryptMeta: JsonObject?,
)

data class ActualKeyInfo(
    val id: String?,
    val salt: String?,
    val test: String?,
)

/** Talks to an Actual server's own HTTP API, the same endpoints Actual's web client uses. */
class ActualServerClient(
    private val http: HttpClient,
    serverUrl: String,
) {
    private val base = serverUrl.trimEnd('/')

    init {
        Url(base)
    }

    var token: String? = null

    suspend fun status(): ActualServerStatus {
        val bootstrap = json(http.get("$base/account/needs-bootstrap")).getValue("data").jsonObject
        val methods =
            runCatching { json(http.get("$base/account/login-methods"))["methods"]?.jsonArray }.getOrNull()?.map {
                val method = it.jsonObject
                ActualLoginMethod(
                    method.text("method") ?: "password",
                    (method["active"] as? JsonPrimitive)?.let { active -> active.intOrNull?.let { n -> n != 0 } ?: active.content == "true" } ?: false,
                    method.text("displayName"),
                )
            }
        val bootstrapped = (bootstrap["bootstrapped"] as? JsonPrimitive)?.content == "true"
        return ActualServerStatus(bootstrapped, methods ?: listOf(ActualLoginMethod(bootstrap.text("loginMethod") ?: "password", true, null)))
    }

    suspend fun signIn(password: String): String {
        val response =
            http.post("$base/account/login") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("loginMethod", "password"); put("password", password) }.toString())
            }
        val token = json(response)["data"]?.jsonObject?.text("token") ?: throw ActualServerException("invalid-password", response.status.value)
        this.token = token
        return token
    }

    /** Starts OpenID sign-in; Actual redirects to [returnUrl]`/openid-cb?token=…` once the provider signs the user in. */
    suspend fun startOpenId(returnUrl: String): String {
        val response =
            http.post("$base/account/login") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("loginMethod", "openid"); put("returnUrl", returnUrl) }.toString())
            }
        return json(response)["data"]?.jsonObject?.text("returnUrl") ?: throw ActualServerException("openid-unavailable", response.status.value)
    }

    suspend fun validate(): Boolean {
        val response = http.get("$base/account/validate") { auth() }
        return response.status.isSuccess() && runCatching { json(response) }.isSuccess
    }

    suspend fun listFiles(): List<ActualRemoteFile> =
        json(http.get("$base/sync/list-user-files") { auth() }).getValue("data").jsonArray.map {
            val file = it.jsonObject
            ActualRemoteFile(
                fileId = file.text("fileId") ?: throw ActualServerException("invalid-response"),
                groupId = file.text("groupId"),
                name = file.text("name").orEmpty(),
                encryptKeyId = file.text("encryptKeyId"),
                deleted = (file["deleted"] as? JsonPrimitive)?.content.let { d -> d == "1" || d == "true" },
            )
        }

    suspend fun fileInfo(fileId: String): ActualFileInfo {
        val data = json(http.get("$base/sync/get-user-file-info") { auth(); header("x-actual-file-id", fileId) }).getValue("data").jsonObject
        return ActualFileInfo(
            fileId = data.text("fileId") ?: fileId,
            groupId = data.text("groupId"),
            name = data.text("name").orEmpty(),
            encryptMeta = data["encryptMeta"] as? JsonObject,
        )
    }

    suspend fun download(fileId: String): ByteArray {
        val response = http.get("$base/sync/download-user-file") { auth(); header("x-actual-file-id", fileId) }
        if (!response.status.isSuccess()) throw failure(response)
        return response.bodyAsBytes()
    }

    suspend fun keyInfo(fileId: String): ActualKeyInfo {
        val data =
            json(
                http.post("$base/sync/user-get-key") {
                    auth()
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject { put("fileId", fileId) }.toString())
                },
            ).getValue("data").jsonObject
        return ActualKeyInfo(data.text("id"), data.text("salt"), data.text("test"))
    }

    suspend fun sync(request: SyncRequest): SyncResponse {
        val response =
            http.post("$base/sync/sync") {
                auth()
                contentType(ContentType("application", "actual-sync"))
                setBody(SyncProtocol.encode(request))
            }
        if (!response.status.isSuccess()) throw failure(response)
        return SyncProtocol.decodeResponse(response.bodyAsBytes())
    }

    private fun io.ktor.client.request.HttpRequestBuilder.auth() {
        header("x-actual-token", token ?: throw ActualServerException("unauthorized"))
    }

    private suspend fun json(response: HttpResponse): JsonObject {
        val text = response.bodyAsText()
        val body = runCatching { Json.parseToJsonElement(text) as JsonObject }.getOrNull()
        if (!response.status.isSuccess() || body == null || body.text("status") == "error") {
            throw ActualServerException(body?.text("reason") ?: reasonFor(response.status.value, text), response.status.value)
        }
        return body
    }

    private suspend fun failure(response: HttpResponse): ActualServerException {
        val text = runCatching { response.bodyAsText() }.getOrDefault("")
        val reason = runCatching { (Json.parseToJsonElement(text) as JsonObject).text("reason") }.getOrNull() ?: reasonFor(response.status.value, text)
        return ActualServerException(reason, response.status.value)
    }

    private fun reasonFor(
        status: Int,
        text: String,
    ): String =
        when {
            text.trim().matches(Regex("^[a-z-]{3,40}$")) -> text.trim()
            status == 401 || status == 403 -> "unauthorized"
            status == 404 -> "not-found"
            else -> "network-failure"
        }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

}
