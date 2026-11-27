package app.tandemforactual.api.client

import app.tandemforactual.data.actual.LinkedAccount
import app.tandemforactual.data.actual.SyncProtocol
import app.tandemforactual.data.actual.SyncRequest
import app.tandemforactual.data.actual.SyncResponse
import app.tandemforactual.data.regions.global.bank.BankInstitution
import app.tandemforactual.data.regions.global.bank.BankProvider
import app.tandemforactual.data.regions.global.bank.BankProviderAccount
import app.tandemforactual.data.regions.global.bank.BankProviders
import app.tandemforactual.data.regions.global.bank.BankRequest
import app.tandemforactual.data.regions.global.bank.BrowserBankProvider
import app.tandemforactual.data.regions.global.bank.BrowserLink
import app.tandemforactual.data.regions.global.bank.BrowserLinkStatus
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
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
import io.ktor.http.encodeURLParameter
import io.ktor.http.hostWithPortIfSpecified
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
                    (method["active"] as? JsonPrimitive)?.let { active ->
                        active.intOrNull?.let { n -> n != 0 }
                            ?: (active.content == "true")
                    }
                        ?: false,
                    method.text("displayName"),
                )
            }
        val bootstrapped = (bootstrap["bootstrapped"] as? JsonPrimitive)?.content == "true"
        return ActualServerStatus(
            bootstrapped,
            methods ?: listOf(ActualLoginMethod(bootstrap.text("loginMethod") ?: "password", true, null)),
        )
    }

    suspend fun signIn(password: String): String {
        val response =
            http.post("$base/account/login") {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("loginMethod", "password")
                        put("password", password)
                    }.toString(),
                )
            }
        val token =
            json(response)["data"]?.jsonObject?.text("token") ?: throw ActualServerException("invalid-password", response.status.value)
        this.token = token
        return token
    }

    /** Starts OpenID sign-in; Actual redirects to [returnUrl]`/openid-cb?token=…` once the provider signs the user in. */
    suspend fun startOpenId(returnUrl: String): String {
        val response =
            http.post("$base/account/login") {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("loginMethod", "openid")
                        put("returnUrl", returnUrl)
                    }.toString(),
                )
            }
        return json(response)["data"]?.jsonObject?.text("returnUrl")
            ?: throw ActualServerException("openid-unavailable", response.status.value)
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

    /** The live file holding the budget whose sync group is [groupId]. */
    suspend fun budgetFile(groupId: String): ActualRemoteFile =
        listFiles().firstOrNull { !it.deleted && it.groupId == groupId } ?: throw ActualServerException("budget-unavailable")

    suspend fun fileInfo(fileId: String): ActualFileInfo {
        val data =
            json(
                http.get("$base/sync/get-user-file-info") {
                    auth()
                    header("x-actual-file-id", fileId)
                },
            ).getValue("data").jsonObject
        return ActualFileInfo(
            fileId = data.text("fileId") ?: fileId,
            groupId = data.text("groupId"),
            name = data.text("name").orEmpty(),
            encryptMeta = data["encryptMeta"] as? JsonObject,
        )
    }

    suspend fun download(fileId: String): ByteArray {
        val response =
            http.get("$base/sync/download-user-file") {
                auth()
                header("x-actual-file-id", fileId)
            }
        if (!response.status.isSuccess()) throw failure(response)
        return response.bodyAsBytes()
    }

    /** Sends a budget zip as Actual's `upload` does, without a group id, so the server answers with a new sync group. */
    suspend fun upload(
        fileId: String,
        name: String,
        body: ByteArray,
        encryptMeta: JsonObject?,
    ): String {
        val response =
            http.post("$base/sync/upload-user-file") {
                auth()
                header("x-actual-file-id", fileId)
                header("x-actual-name", name.encodeURLParameter())
                header("x-actual-format", "2")
                encryptMeta?.let { header("x-actual-encrypt-meta", it.toString()) }
                contentType(ContentType("application", "encrypted-file"))
                setBody(body)
            }
        return json(response).text("groupId") ?: throw ActualServerException("missing-group-id")
    }

    /** Clears the file's sync group, so devices still on it are told to download the budget again. */
    suspend fun resetFile(fileId: String) {
        filePost("reset-user-file", buildJsonObject { put("fileId", fileId) })
    }

    suspend fun createKey(
        fileId: String,
        keyId: String,
        salt: String,
        test: String,
    ) {
        filePost(
            "user-create-key",
            buildJsonObject {
                put("fileId", fileId)
                put("keyId", keyId)
                put("keySalt", salt)
                put("testContent", test)
            },
        )
    }

    private suspend fun filePost(
        route: String,
        body: JsonObject,
    ) = json(
        http.post("$base/sync/$route") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        },
    )

    /** Whether the provider is set up for the budget and whose credentials it uses, through its standard `status` route. */
    suspend fun bankStatus(
        provider: BankProvider,
        fileId: String,
    ): BankProviderStatus {
        val data = bankPost(provider.statusRoute, "{}", fileId)
        val configured = (data["configured"] as? JsonPrimitive)?.content == "true"
        val source =
            when (data.text("source")) {
                "per-budget-file" -> BankCredentialSource.BUDGET
                "global" -> BankCredentialSource.SERVER
                else -> BankCredentialSource.SERVER.takeIf { configured }
            }
        return BankProviderStatus(configured, source)
    }

    /** The provider's accounts the budget can see, through its standard `accounts` route. */
    suspend fun bankAccounts(
        provider: BankProvider,
        fileId: String,
    ): List<BankProviderAccount> = provider.parseAccounts(bankPost(provider.accountsRoute, "{}", fileId))

    /** Stores a bank provider secret for one budget, or for the whole server when [fileId] is null, like Actual's `secret-set`. */
    suspend fun setSecret(
        name: String,
        value: String,
        fileId: String?,
    ) {
        json(
            http.post("$base/secret") {
                auth()
                fileId?.let { header("x-actual-file-id", it) }
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("name", name)
                        put("value", value)
                    }.toString(),
                )
            },
        )
    }

    suspend fun removeSecret(
        name: String,
        fileId: String?,
    ) {
        json(
            http.delete("$base/secret/$name") {
                auth()
                fileId?.let { header("x-actual-file-id", it) }
            },
        )
    }

    /** Whether the signed-in user is an admin, who alone may set server-wide bank credentials. */
    suspend fun isAdmin(): Boolean =
        json(http.get("$base/account/validate") { auth() })["data"]?.jsonObject?.text("permission") == "ADMIN"

    /** Asks the Actual server for an account's transactions from its bank provider; the server holds the provider secrets. */
    suspend fun bankTransactions(
        account: LinkedAccount,
        fileId: String,
    ): JsonObject {
        val provider = BankProviders.forSource(account.source)
        return bankPost(provider.transactionsRoute, provider.transactionsRequest(account).toString(), fileId)
    }

    /** A country's banks the provider can link, through its bank-list route. */
    suspend fun bankInstitutions(
        provider: BrowserBankProvider,
        country: String,
    ): List<BankInstitution> {
        val response = browserPost(provider.institutionsRequest(country))
        val data = response["data"] ?: JsonObject(emptyMap())
        (data as? JsonObject)?.let { failure ->
            failure.text("error_code")?.let { throw ActualServerException("bank-sync-${it.lowercase()}") }
            failure.text("error")?.let { throw ActualServerException("bank-link-failed") }
        }
        return provider.parseInstitutions(data, country)
    }

    /** Starts a bank sign-in; the bank sends the person back to this server, which finishes the link. */
    suspend fun startBankLink(
        provider: BrowserBankProvider,
        institution: BankInstitution,
    ): BrowserLink {
        val response = browserPost(provider.startRequest(institution, base))
        return provider.parseStart(response) ?: throw ActualServerException(
            (response["data"] as? JsonObject)?.text("error_code")?.let { "bank-sync-${it.lowercase()}" } ?: "bank-link-failed",
        )
    }

    suspend fun bankLinkStatus(
        provider: BrowserBankProvider,
        handle: String,
    ): BrowserLinkStatus = provider.parseStatus(browserPost(provider.statusRequest(handle)), handle)

    /** Sends the server's own address as the origin, which GoCardless uses to build the page the bank returns to. */
    private suspend fun browserPost(request: BankRequest): JsonObject =
        json(
            http.post("$base/${request.route}") {
                auth()
                header("Origin", Url(base).let { "${it.protocol.name}://${it.hostWithPortIfSpecified}" })
                contentType(ContentType.Application.Json)
                setBody(request.body.toString())
            },
        )

    private suspend fun bankPost(
        route: String,
        body: String,
        fileId: String,
    ): JsonObject {
        val data =
            json(
                http.post("$base/$route") {
                    auth()
                    header("x-actual-file-id", fileId)
                    contentType(ContentType.Application.Json)
                    setBody(body)
                },
            )["data"]?.jsonObject ?: JsonObject(emptyMap())
        (data["error_code"] as? JsonPrimitive)?.let { throw ActualServerException("bank-sync-${it.content.lowercase()}") }
        return data
    }

    suspend fun keyInfo(fileId: String): ActualKeyInfo {
        val data = filePost("user-get-key", buildJsonObject { put("fileId", fileId) }).getValue("data").jsonObject
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
        val reason =
            runCatching { (Json.parseToJsonElement(text) as JsonObject).text("reason") }.getOrNull()
                ?: reasonFor(response.status.value, text)
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
            status == 413 -> "file-too-large"
            status == 429 -> "rate-limited"
            status in 500..599 -> "server-unavailable"
            else -> "network-failure"
        }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
