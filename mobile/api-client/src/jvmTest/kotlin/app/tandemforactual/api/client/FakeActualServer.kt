package app.tandemforactual.api.client

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
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

/** An in-memory Actual server holding the parity fixture, for tests that talk to Actual's HTTP API. */
internal class FakeActualServer {
    private val fixture = File("../data/src/jvmTest/resources/actual/parity-envelope")
    val archive = fixture.resolve("budget.zip").readBytes()
    private val initial = SyncProtocol.decodeResponse(fixture.resolve("sync-response.bin").readBytes())
    val messages: MutableMap<String, MessageEnvelope> = initial.messages.associateByTo(linkedMapOf()) { it.timestamp }
    var merkle = MerkleTrie.fromJson(initial.merkle)
    var password = "test"
    var syncFailure: String? = null
    var syncCalls = 0
    var files =
        """
        {"status":"ok","data":[{"fileId":"file","groupId":"group","name":"Direct"},
            {"fileId":"encrypted","groupId":"locked","encryptKeyId":"key","name":"Locked"},
            {"fileId":"deleted","groupId":"old","deleted":1,"name":"Old"},{"fileId":"local","name":"Local"}]}
        """.trimIndent()

    val http =
        HttpClient(
            MockEngine { request ->
                when (request.url.encodedPath) {
                    "/account/needs-bootstrap" -> respond("""{"status":"ok","data":{"bootstrapped":true,"loginMethod":"password"}}""")
                    "/account/login-methods" ->
                        respond(
                            """{"status":"ok","methods":[{"method":"password","active":1,"displayName":"Password"}]}""",
                        )
                    "/account/login" -> {
                        val body = (request.body as TextContent).text
                        if ("\"password\":\"$password\"" in body) {
                            respond("""{"status":"ok","data":{"token":"session"}}""")
                        } else {
                            respond("""{"status":"error","reason":"invalid-password"}""", HttpStatusCode.BadRequest)
                        }
                    }
                    "/sync/list-user-files" -> respond(files)
                    "/sync/get-user-file-info" -> respond("""{"status":"ok","data":{"fileId":"file","groupId":"group","name":"Direct"}}""")
                    "/sync/download-user-file" -> respond(archive)
                    "/sync/sync" -> {
                        syncCalls++
                        syncFailure?.let { return@MockEngine respond(it, HttpStatusCode.BadRequest) }
                        val sent = SyncProtocol.decodeRequest((request.body as OutgoingContent.ByteArrayContent).bytes())
                        for (message in sent.messages) {
                            if (messages.putIfAbsent(message.timestamp, message) ==
                                null
                            ) {
                                merkle = merkle.insert(checkNotNull(ActualTimestamp.parse(message.timestamp)))
                            }
                        }
                        val sentTimestamps = sent.messages.map { it.timestamp }.toSet()
                        respond(
                            SyncProtocol.encode(
                                SyncResponse(
                                    messages.values.filter { it.timestamp > sent.since && it.timestamp !in sentTimestamps },
                                    merkle.prune().toJson().toString(),
                                ),
                            ),
                        )
                    }
                    else -> error("Unexpected path ${request.url.encodedPath}")
                }
            },
        )
}

/** Keeps budget databases in a temporary directory. */
internal class TempBudgetFiles : ActualBudgetFiles {
    val directory: File = createTempDirectory("budgets").toFile()

    private fun file(budgetId: String) = directory.resolve("$budgetId.sqlite")

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
}
