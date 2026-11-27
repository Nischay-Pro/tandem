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
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import java.io.File
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
    var failUploads = false
    val bankRoutes = mutableMapOf<String, String>()
    var lastOrigin: String? = null

    @Volatile var syncCalls = 0
    var syncGate: CompletableDeferred<Unit>? = null
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
                bankRoutes[request.url.encodedPath]?.let { body ->
                    lastOrigin = request.headers["Origin"]
                    return@MockEngine respond(body)
                }
                when (request.url.encodedPath) {
                    "/extension/v1/capabilities" ->
                        if (extension) {
                            respond(
                                """{"apiVersion":1,"features":["planning-records"]}""",
                            )
                        } else {
                            respond("", HttpStatusCode.NotFound)
                        }
                    "/account/validate" ->
                        if (request.headers["x-actual-token"] == "session") {
                            respond("""{"status":"ok","data":{"validated":true}}""")
                        } else {
                            respond("""{"status":"error","reason":"unauthorized"}""", HttpStatusCode.Unauthorized)
                        }
                    "/account/needs-bootstrap" -> respond("""{"status":"ok","data":{"bootstrapped":true,"loginMethod":"password"}}""")
                    "/account/login-methods" ->
                        respond(
                            """{"status":"ok","methods":[{"method":"password","active":1,"displayName":"Password"}]}""",
                        )
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
                    "/sync/list-user-files" -> respond(files)
                    "/sync/get-user-file-info" ->
                        if (request.headers["x-actual-file-id"] == "encrypted") {
                            respond(
                                """{"status":"ok","data":{"fileId":"encrypted","groupId":"locked","name":"Locked",""" +
                                    """"encryptMeta":{"keyId":"key","iv":"${b64(
                                        sealedArchive.iv,
                                    )}","authTag":"${b64(sealedArchive.authTag)}"}}}""",
                            )
                        } else {
                            respond("""{"status":"ok","data":{"fileId":"file","groupId":"group","name":"Direct"}}""")
                        }
                    "/sync/download-user-file" ->
                        respond(
                            if (request.headers["x-actual-file-id"] ==
                                "encrypted"
                            ) {
                                sealedArchive.data
                            } else {
                                archive
                            },
                        )
                    "/sync/user-get-key" ->
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
                    "/sync/sync" -> {
                        syncCalls++
                        syncGate?.await()
                        syncFailure?.let { return@MockEngine respond(it, HttpStatusCode.BadRequest) }
                        val sent = SyncProtocol.decodeRequest((request.body as OutgoingContent.ByteArrayContent).bytes())
                        if (failUploads && sent.messages.isNotEmpty()) return@MockEngine respond("", HttpStatusCode.ServiceUnavailable)
                        val locked = sent.fileId == "encrypted"
                        val store = if (locked) lockedMessages else messages
                        val reply =
                            synchronized(syncLock) {
                                for (message in sent.messages) {
                                    if (store.putIfAbsent(message.timestamp, message) == null) {
                                        val stamp = checkNotNull(ActualTimestamp.parse(message.timestamp))
                                        if (locked) lockedMerkle = lockedMerkle.insert(stamp) else merkle = merkle.insert(stamp)
                                    }
                                }
                                val sentTimestamps = sent.messages.map { it.timestamp }.toSet()
                                SyncResponse(
                                    store.values.filter { it.timestamp > sent.since && it.timestamp !in sentTimestamps },
                                    (if (locked) lockedMerkle else merkle).prune().toJson().toString(),
                                )
                            }
                        respond(SyncProtocol.encode(reply))
                    }
                    "/simplefin/transactions" -> respond(bankDownload)
                    "/simplefin/status" -> respond("""{"status":"ok","data":{"configured":$simpleFinConfigured}}""")
                    "/simplefin/accounts" -> respond(simpleFinAccounts)
                    else -> error("Unexpected path ${request.url.encodedPath}")
                }
            },
        )
}

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
