package app.tandemforactual.api.client

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.tandemforactual.data.actual.ActualBudgetKey
import app.tandemforactual.data.actual.ActualDatabase
import app.tandemforactual.data.actual.ActualTimestamp
import app.tandemforactual.data.actual.MerkleTrie
import app.tandemforactual.data.actual.MessageEnvelope
import app.tandemforactual.data.actual.SyncProtocol
import app.tandemforactual.data.actual.SyncResponse
import app.tandemforactual.data.database.ActualBudgetFiles
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** An in-memory Actual server holding the parity fixture, for tests that talk to Actual's HTTP API. */
internal class FakeActualServer {
    private val fixture = File("../data/src/jvmTest/resources/actual/parity-envelope")
    val archive = fixture.resolve("budget.zip").readBytes()
    private val initial = SyncProtocol.decodeResponse(fixture.resolve("sync-response.bin").readBytes())
    val messages: MutableMap<String, MessageEnvelope> = initial.messages.associateByTo(linkedMapOf()) { it.timestamp }
    var merkle = MerkleTrie.fromJson(initial.merkle)
    var password = "test"
    var bankDownload = """{"status":"ok","data":{}}"""
    var openIdReturnUrl: String? = null
    var simpleFinConfigured = true
    var simpleFinAccounts =
        """{"status":"ok","data":{"accounts":[{"id":"SF-9","name":"Rewards Card","balance":"-42.10",""" +
            """"org":{"name":"Credit Union","domain":"cu.example"}},{"id":"SF-10","name":"Plain","org":{"id":"org-1"}},""" +
            """{"name":"Bare"}]}}"""
    var syncFailure: String? = null
    var admin = true
    val secrets = mutableMapOf<String, String>()
    val simpleFinFileIds = mutableListOf<String?>()
    var failUploads = false
    var failFileUploads = false
    val uploaded = linkedMapOf<String, UploadedFile>()
    val createdKeys = mutableListOf<String>()
    val resets = mutableListOf<String>()
    val bankRoutes = mutableMapOf<String, String>()
    var lastOrigin: String? = null

    @Volatile var syncCalls = 0
    var syncGate: CompletableDeferred<Unit>? = null
    val downloads = AtomicInteger()
    var extension = false
    val budgetKey: ActualBudgetKey = ActualBudgetKey.derive("key", "secret", "salt")
    private val sealedArchive = budgetKey.encrypt(archive)
    private val syncLock = Any()
    private val lockedMessages = linkedMapOf<String, MessageEnvelope>()
    private var lockedMerkle = MerkleTrie.fromJson("{}")
    private val keyTest =
        budgetKey.encrypt("test".encodeToByteArray()).let {
            """{"value":"${b64(
                it.data,
            )}","meta":{"keyId":"key","algorithm":"aes-256-gcm","iv":"${b64(it.iv)}","authTag":"${b64(it.authTag)}"}}"""
        }
    var files =
        """
        {"status":"ok","data":[{"fileId":"file","groupId":"group","name":"Direct"},
            {"fileId":"encrypted","groupId":"locked","encryptKeyId":"key","name":"Locked"},
            {"fileId":"deleted","groupId":"old","deleted":1,"name":"Old"},{"fileId":"local","name":"Local"}]}
        """.trimIndent()

    val http =
        HttpClient(
            MockEngine { request ->
                if (request.url.encodedPath.startsWith("/secret")) return@MockEngine respondSecret(this, request)
                bankRoutes[request.url.encodedPath]?.let { body ->
                    lastOrigin = request.headers["Origin"]
                    return@MockEngine respond(body)
                }
                when (request.url.encodedPath) {
                    "/extension/v1/capabilities" -> {
                        if (extension) {
                            respond(
                                """{"apiVersion":1,"features":["planning-records"]}""",
                            )
                        } else {
                            respond("", HttpStatusCode.NotFound)
                        }
                    }

                    "/account/validate" -> {
                        if (request.headers["x-actual-token"] == "session") {
                            respond("""{"status":"ok","data":{"validated":true,"permission":"${if (admin) "ADMIN" else "BASIC"}"}}""")
                        } else {
                            respond("""{"status":"error","reason":"unauthorized"}""", HttpStatusCode.Unauthorized)
                        }
                    }

                    "/account/needs-bootstrap" -> {
                        respond("""{"status":"ok","data":{"bootstrapped":true,"loginMethod":"password"}}""")
                    }

                    "/account/login-methods" -> {
                        respond(
                            """{"status":"ok","methods":[{"method":"password","active":1,"displayName":"Password"}]}""",
                        )
                    }

                    "/account/login" -> {
                        val body = (request.body as TextContent).text
                        if ("\"loginMethod\":\"openid\"" in body) {
                            openIdReturnUrl = Regex("\"returnUrl\":\"([^\"]+)\"").find(body)?.groupValues?.get(1)
                            respond("""{"status":"ok","data":{"returnUrl":"https://idp.example/authorize"}}""")
                        } else if ("\"password\":\"$password\"" in body) {
                            respond("""{"status":"ok","data":{"token":"session"}}""")
                        } else {
                            respond("""{"status":"error","reason":"invalid-password"}""", HttpStatusCode.BadRequest)
                        }
                    }

                    "/sync/list-user-files" -> {
                        respond(
                            files.trimEnd().removeSuffix("]}") +
                                uploaded.values.joinToString("") {
                                    """,{"fileId":"${it.fileId}","groupId":"${it.groupId}","name":"${it.name}"${
                                        it.encryptMeta?.let { ",\"encryptKeyId\":\"key\"" }.orEmpty()
                                    }}"""
                                } + "]}",
                        )
                    }

                    "/sync/upload-user-file" -> {
                        if (failFileUploads) return@MockEngine respond("", HttpStatusCode.InternalServerError)
                        val fileId = checkNotNull(request.headers["x-actual-file-id"])
                        val file =
                            UploadedFile(
                                fileId,
                                "restored-${uploaded.size + 1}",
                                java.net.URLDecoder.decode(request.headers["x-actual-name"], "UTF-8"),
                                (request.body as OutgoingContent.ByteArrayContent).bytes(),
                                request.headers["x-actual-encrypt-meta"],
                            )
                        uploaded[fileId] = file
                        respond("""{"status":"ok","groupId":"${file.groupId}"}""")
                    }

                    "/sync/user-create-key" -> {
                        createdKeys += (request.body as TextContent).text
                        respond("""{"status":"ok"}""")
                    }

                    "/sync/reset-user-file" -> {
                        resets += (request.body as TextContent).text
                        respond("""{"status":"ok"}""")
                    }

                    "/sync/get-user-file-info" -> {
                        uploaded[request.headers["x-actual-file-id"]]?.let { file ->
                            respond(
                                """{"status":"ok","data":{"fileId":"${file.fileId}","groupId":"${file.groupId}",""" +
                                    """"name":"${file.name}"${file.encryptMeta?.let { ",\"encryptMeta\":$it" }.orEmpty()}}}""",
                            )
                        } ?: if (request.headers["x-actual-file-id"] == "encrypted") {
                            respond(
                                """{"status":"ok","data":{"fileId":"encrypted","groupId":"locked","name":"Locked",""" +
                                    """"encryptMeta":{"keyId":"key","iv":"${b64(
                                        sealedArchive.iv,
                                    )}","authTag":"${b64(sealedArchive.authTag)}"}}}""",
                            )
                        } else {
                            respond("""{"status":"ok","data":{"fileId":"file","groupId":"group","name":"Direct"}}""")
                        }
                    }

                    "/sync/download-user-file" -> {
                        downloads.incrementAndGet()
                        uploaded[request.headers["x-actual-file-id"]]?.let { return@MockEngine respond(it.body) }
                        respond(
                            if (request.headers["x-actual-file-id"] ==
                                "encrypted"
                            ) {
                                sealedArchive.data
                            } else {
                                archive
                            },
                        )
                    }

                    "/sync/user-get-key" -> {
                        respond(
                            buildJsonObject {
                                put("status", "ok")
                                put(
                                    "data",
                                    buildJsonObject {
                                        put("id", "key")
                                        put("salt", "salt")
                                        put("test", keyTest)
                                    },
                                )
                            }.toString(),
                        )
                    }

                    "/sync/sync" -> {
                        syncCalls++
                        syncGate?.await()
                        syncFailure?.let { return@MockEngine respond(it, HttpStatusCode.BadRequest) }
                        val sent = SyncProtocol.decodeRequest((request.body as OutgoingContent.ByteArrayContent).bytes())
                        if (failUploads && sent.messages.isNotEmpty()) return@MockEngine respond("", HttpStatusCode.ServiceUnavailable)
                        val locked = sent.fileId == "encrypted"
                        val restored = uploaded[sent.fileId]
                        val store = restored?.messages ?: if (locked) lockedMessages else messages
                        val reply =
                            synchronized(syncLock) {
                                for (message in sent.messages) {
                                    if (store.putIfAbsent(message.timestamp, message) == null) {
                                        val stamp = checkNotNull(ActualTimestamp.parse(message.timestamp))
                                        when {
                                            restored != null -> restored.merkle = restored.merkle.insert(stamp)
                                            locked -> lockedMerkle = lockedMerkle.insert(stamp)
                                            else -> merkle = merkle.insert(stamp)
                                        }
                                    }
                                }
                                val sentTimestamps = sent.messages.map { it.timestamp }.toSet()
                                SyncResponse(
                                    store.values.filter { it.timestamp > sent.since && it.timestamp !in sentTimestamps },
                                    (restored?.merkle ?: if (locked) lockedMerkle else merkle).prune().toJson().toString(),
                                )
                            }
                        respond(SyncProtocol.encode(reply))
                    }

                    "/simplefin/transactions" -> {
                        respond(bankDownload).also { simpleFinFileIds += request.headers["x-actual-file-id"] }
                    }

                    "/simplefin/status" -> {
                        respond(simpleFinStatus(request.headers["x-actual-file-id"]))
                    }

                    "/simplefin/accounts" -> {
                        val fileId = request.headers["x-actual-file-id"]
                        simpleFinFileIds += fileId
                        respond(if (budgetCredentials(fileId) == null) simpleFinAccounts else BUDGET_SIMPLEFIN_ACCOUNTS)
                    }

                    else -> {
                        error("Unexpected path ${request.url.encodedPath}")
                    }
                }
            },
        )
}

/** A budget uploaded to the fake server, kept in its own sync group with its own messages. */
internal class UploadedFile(
    val fileId: String,
    val groupId: String,
    val name: String,
    val body: ByteArray,
    val encryptMeta: String?,
) {
    val messages = linkedMapOf<String, MessageEnvelope>()
    var merkle = MerkleTrie.fromJson("{}")
}

private fun FakeActualServer.budgetCredentials(fileId: String?) =
    fileId?.takeIf { "simplefin_token:$it" in secrets || "simplefin_accessKey:$it" in secrets }

/** Picks the budget's own SimpleFIN token over the server's, as Actual 26.11 does. */
private fun FakeActualServer.simpleFinStatus(fileId: String?): String {
    simpleFinFileIds += fileId
    val own = budgetCredentials(fileId)
    val configured = if (own != null) "simplefin_token:$own" in secrets else simpleFinConfigured || "simplefin_token" in secrets
    val source =
        when {
            own != null -> "\"per-budget-file\""
            configured -> "\"global\""
            else -> "null"
        }
    return """{"status":"ok","data":{"configured":$configured,"source":$source}}"""
}

/** Actual's `/secret` routes: budget owners manage their budget's secrets, only admins the server-wide ones. */
private suspend fun FakeActualServer.respondSecret(
    scope: MockRequestHandleScope,
    request: HttpRequestData,
): HttpResponseData {
    val fileId = request.headers["x-actual-file-id"]
    val name =
        if (request.method == HttpMethod.Delete) {
            request.url.encodedPath.substringAfterLast('/')
        } else {
            Regex("\"name\":\"([^\"]+)\"").find((request.body as TextContent).text)!!.groupValues[1]
        }
    val denied =
        when {
            fileId != null && fileId != "file" -> "file-access-denied"
            fileId == null && !admin -> "not-admin"
            else -> null
        }
    if (denied != null) return scope.respond("""{"status":"error","reason":"$denied"}""", HttpStatusCode.Forbidden)
    val key = fileId?.let { "$name:$it" } ?: name
    if (request.method == HttpMethod.Delete) {
        secrets.remove(key)
    } else {
        secrets[key] = Regex("\"value\":\"([^\"]+)\"").find((request.body as TextContent).text)!!.groupValues[1]
    }
    return scope.respond("""{"status":"ok"}""")
}

private const val BUDGET_SIMPLEFIN_ACCOUNTS =
    """{"status":"ok","data":{"accounts":[{"id":"SF-B","name":"Budget Card","org":{"name":"Budget Bank","domain":"bb.example"}}]}}"""

private fun b64(bytes: ByteArray) =
    java.util.Base64
        .getEncoder()
        .encodeToString(bytes)

/** Keeps budget databases in a temporary directory. */
internal class TempBudgetFiles : ActualBudgetFiles {
    val directory: File = createTempDirectory("budgets").toFile()

    private fun file(budgetId: String) = directory.resolve("$budgetId.sqlite")

    override fun retire(
        budgetId: String,
        label: String,
    ) {
        file(budgetId).renameTo(directory.resolve("$budgetId-$label.sqlite"))
    }

    override fun open(budgetId: String): ActualDatabase? =
        file(budgetId)
            .takeIf {
                it.isFile
            }?.let { ActualDatabase(BundledSQLiteDriver().open(it.path)) }

    override fun install(
        budgetId: String,
        bytes: ByteArray,
    ): ActualDatabase {
        file(budgetId).writeBytes(bytes)
        return ActualDatabase(BundledSQLiteDriver().open(file(budgetId).path))
    }

    override fun remove(budgetId: String) {
        file(budgetId).delete()
    }

    override fun snapshot(
        budgetId: String,
        write: (path: String) -> Unit,
    ): ByteArray {
        val copy = directory.resolve("$budgetId.snapshot").apply { delete() }
        write(copy.path)
        return copy.readBytes().also { copy.delete() }
    }
}

/** Keeps encrypted budgets' copies apart under the key they were sealed with, checking it on every open as SQLCipher would. */
internal class SealingBudgetFiles(
    private val plain: TempBudgetFiles = TempBudgetFiles(),
) : ActualBudgetFiles by plain {
    val directory get() = plain.directory

    val keys = mutableMapOf<String, ByteArray>()
    private val keyInfo = mutableMapOf<String, String>()

    private fun sealed(budgetId: String) = plain.directory.resolve("$budgetId.sealed")

    override val canSeal = true

    override fun sealedKeyInfo(budgetId: String): String? = keyInfo[budgetId]?.takeIf { sealed(budgetId).isFile }

    override fun openSealed(
        budgetId: String,
        key: ByteArray,
    ): ActualDatabase? {
        val file = sealed(budgetId).takeIf { it.isFile } ?: return null
        check(keys[budgetId].contentEquals(key)) { "Wrong key for the sealed copy" }
        return ActualDatabase(BundledSQLiteDriver().open(file.path))
    }

    override fun installSealed(
        budgetId: String,
        bytes: ByteArray,
        key: ByteArray,
        keyInfo: String,
    ): ActualDatabase {
        plain.remove(budgetId)
        sealed(budgetId).writeBytes(bytes)
        keys[budgetId] = key.copyOf()
        this.keyInfo[budgetId] = keyInfo
        return checkNotNull(openSealed(budgetId, key))
    }

    override fun remove(budgetId: String) {
        plain.remove(budgetId)
        sealed(budgetId).delete()
    }

    override fun sealBudget(
        budgetId: String,
        key: ByteArray,
        keyInfo: String,
    ) {
        check(plain.directory.resolve("$budgetId.sqlite").renameTo(sealed(budgetId)))
        keys[budgetId] = key.copyOf()
        this.keyInfo[budgetId] = keyInfo
    }
}
