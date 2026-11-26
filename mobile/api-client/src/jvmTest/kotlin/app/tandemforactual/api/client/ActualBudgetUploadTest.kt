package app.tandemforactual.api.client

import app.tandemforactual.data.actual.ActualBudgetKey
import app.tandemforactual.data.actual.ZipArchive
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import java.net.URLDecoder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

class ActualBudgetUploadTest {
    private val database = ByteArray(4096) { (it % 251).toByte() }
    private val key = ActualBudgetKey.derive("key-1", "budget-secret", "c2FsdC1mb3ItdGFuZGVt")
    private val uploadKey = ActualUploadKey(key, "c2FsdC1mb3ItdGFuZGVt", "test-message")

    private class Seen(
        val path: String,
        val headers: Map<String, String?>,
        val contentType: ContentType?,
        val body: ByteArray,
    ) {
        val text get() = body.decodeToString()
    }

    private fun withServer(
        token: String? = "session",
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
        block: suspend (ActualServerClient, List<Seen>) -> Unit,
    ) = runBlocking {
        val seen = mutableListOf<Seen>()
        HttpClient(
            MockEngine { request ->
                val body = request.body as? OutgoingContent.ByteArrayContent
                seen +=
                    Seen(
                        request.url.encodedPath.removePrefix("/actual/sync/"),
                        request.headers.names().associateWith { request.headers[it] },
                        request.body.contentType,
                        body?.bytes() ?: ByteArray(0),
                    )
                handler(request)
            },
        ).use { http ->
            val client = ActualServerClient(http, "https://budget.example/actual")
            client.token = token
            block(client, seen)
        }
    }

    private fun MockRequestHandleScope.actual(
        request: HttpRequestData,
        files: String,
        keyId: String? = null,
    ): HttpResponseData =
        when (request.url.encodedPath.removePrefix("/actual/sync/")) {
            "list-user-files" -> respond("""{"status":"ok","data":$files}""")
            "user-get-key" -> respond("""{"status":"ok","data":{"id":${keyId?.let { "\"$it\"" }},"salt":null,"test":null}}""")
            "reset-user-file", "user-create-key" -> respond("""{"status":"ok"}""")
            "upload-user-file" -> respond("""{"status":"ok","groupId":"group-new"}""")
            else -> error("Unexpected request to ${request.url}")
        }

    private fun json(text: String) = Json.parseToJsonElement(text).jsonObject

    @Test
    fun uploadsAnUnencryptedBudgetUnderANewIdWhenTheServerNoLongerHasIt() =
        withServer(handler = { actual(it, """[{"fileId":"other","groupId":"g","name":"Other","deleted":0}]""") }) { client, seen ->
            val result =
                client.uploadBudget(
                    "file-1",
                    "Home & Away 50%",
                    database,
                    """{"id":"budget-1","budgetName":"Old name","exportedBy":"Tandem"}""",
                    key = null,
                ) { "file-new" }

            assertEquals(ActualUploadedBudget("file-new", "group-new"), result)
            assertEquals(listOf("list-user-files", "upload-user-file"), seen.map { it.path })
            val upload = seen.last()
            assertEquals("session", upload.headers["x-actual-token"])
            assertEquals("file-new", upload.headers["x-actual-file-id"])
            assertEquals("Home%20%26%20Away%2050%25", upload.headers["x-actual-name"])
            assertEquals("Home & Away 50%", URLDecoder.decode(upload.headers["x-actual-name"], "UTF-8"))
            assertEquals("2", upload.headers["x-actual-format"])
            assertNull(upload.headers["x-actual-encrypt-meta"])
            assertNull(upload.headers["x-actual-group-id"])
            assertEquals(ContentType("application", "encrypted-file"), upload.contentType)
            val entries = ZipArchive.read(upload.body)
            assertEquals(setOf("db.sqlite", "metadata.json"), entries.keys)
            assertContentEquals(database, entries.getValue("db.sqlite"))
            assertEquals(
                json("""{"id":"budget-1","budgetName":"Home & Away 50%","exportedBy":"Tandem","resetClock":true}"""),
                json(entries.getValue("metadata.json").decodeToString()),
            )
        }

    @Test
    fun resetsAnEncryptedBudgetTheServerStillHoldsAndUploadsItSealedWithTheSameKey() =
        withServer(handler = { actual(it, HELD_FILE, "key-1") }) { client, seen ->
            val result = client.uploadBudget("file-1", "Home", database, null, uploadKey) { error("The file keeps its id") }

            assertEquals(ActualUploadedBudget("file-1", "group-new"), result)
            assertEquals(listOf("list-user-files", "user-get-key", "reset-user-file", "upload-user-file"), seen.map { it.path })
            assertEquals("file-1", json(seen[1].text).getValue("fileId").let { (it as JsonPrimitive).content })
            assertEquals(json("""{"fileId":"file-1"}"""), json(seen[2].text))
            val upload = seen.last()
            assertEquals("file-1", upload.headers["x-actual-file-id"])
            assertNull(upload.headers["x-actual-group-id"])
            val meta = json(upload.headers.getValue("x-actual-encrypt-meta")!!)
            assertEquals(setOf("keyId", "algorithm", "iv", "authTag"), meta.keys)
            assertEquals("key-1", (meta.getValue("keyId") as JsonPrimitive).content)
            assertEquals("aes-256-gcm", (meta.getValue("algorithm") as JsonPrimitive).content)
            val archive = ActualBudgetKey.fromBase64("key-1", key.base64).decrypt(upload.body, meta)
            assertContentEquals(database, ZipArchive.read(archive).getValue("db.sqlite"))
            assertEquals(
                json("""{"budgetName":"Home","resetClock":true}"""),
                json(ZipArchive.read(archive).getValue("metadata.json").decodeToString()),
            )
        }

    @Test
    fun registersTheKeyAfterUploadingAnEncryptedBudgetUnderANewId() =
        withServer(handler = { actual(it, """[{"fileId":"file-1","name":"Home","deleted":1}]""") }) { client, seen ->
            val result = client.uploadBudget("file-1", "Home", database, "not json", uploadKey) { "file-new" }

            assertEquals(ActualUploadedBudget("file-new", "group-new"), result)
            assertEquals(listOf("list-user-files", "upload-user-file", "user-create-key"), seen.map { it.path })
            val meta = json(seen[1].headers.getValue("x-actual-encrypt-meta")!!)
            assertContentEquals(database, ZipArchive.read(key.decrypt(seen[1].body, meta)).getValue("db.sqlite"))
            assertEquals(
                json("""{"fileId":"file-new","keyId":"key-1","keySalt":"c2FsdC1mb3ItdGFuZGVt","testContent":"test-message"}"""),
                json(seen[2].text),
            )
        }

    @Test
    fun uploadsUnderANewIdWithoutListingWhenNoFileIdIsKnown() =
        withServer(handler = { actual(it, "[]") }) { client, seen ->
            assertEquals(
                ActualUploadedBudget("file-new", "group-new"),
                client.uploadBudget(null, "Home", database, null, null) { "file-new" },
            )
            assertEquals(listOf("upload-user-file"), seen.map { it.path })
        }

    @Test
    fun refusesToReplaceABudgetTheServerKeepsUnderAnotherKey() {
        withServer(handler = { actual(it, HELD_FILE, "key-2") }) { client, seen ->
            val error = assertFailsWith<ActualServerException> { client.uploadBudget("file-1", "Home", database, null, uploadKey) { "x" } }
            assertEquals("file-has-new-key", error.reason)
            assertEquals(listOf("list-user-files", "user-get-key"), seen.map { it.path })
        }
        withServer(handler = { actual(it, HELD_FILE, "key-1") }) { client, _ ->
            val error = assertFailsWith<ActualServerException> { client.uploadBudget("file-1", "Home", database, null, null) { "x" } }
            assertEquals("file-has-new-key", error.reason)
        }
    }

    @Test
    fun mapsServerFailuresToActualReasons() {
        fun failure(
            token: String? = "session",
            reply: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
        ): ActualServerException {
            var error: ActualServerException? = null
            withServer(token, handler = { request ->
                if (request.url.encodedPath.endsWith("list-user-files")) {
                    respond("""{"status":"ok","data":[]}""")
                } else {
                    reply(request)
                }
            }) { client, _ ->
                error =
                    assertFailsWith<ActualServerException> { client.uploadBudget("file-1", "Home", database, null, null) { "file-new" } }
            }
            return error!!
        }

        assertEquals("unauthorized", failure(token = null) { error("No request without a token") }.reason)
        failure { respond("PayloadTooLargeError: request entity too large", HttpStatusCode.PayloadTooLarge) }.let {
            assertEquals("file-too-large", it.reason)
            assertEquals(413, it.httpStatus)
        }
        assertEquals(
            "unauthorized",
            failure { respond("""{"status":"error","reason":"unauthorized"}""", HttpStatusCode.Unauthorized) }.reason,
        )
        assertEquals("file-has-reset", failure { respond("file-has-reset", HttpStatusCode.BadRequest) }.reason)
        assertEquals("file-access-not-allowed", failure { respond("file-access-not-allowed", HttpStatusCode.Forbidden) }.reason)
        assertEquals("network-failure", failure { respond("""{"status":"error"}""", HttpStatusCode.InternalServerError) }.reason)
        assertEquals("missing-group-id", failure { respond("""{"status":"ok"}""") }.reason)
        failure { throw IOException("connection reset") }.let {
            assertEquals("network-failure", it.reason)
            assertEquals("connection reset", it.cause?.message)
        }
    }

    @Test
    fun keepsMetadataThatIsNotAnObjectOutOfTheArchive() {
        val metadata = ZipArchive.read(budgetArchive("Home", database, "[1]")).getValue("metadata.json").decodeToString()
        assertEquals(JsonObject(mapOf("budgetName" to JsonPrimitive("Home"), "resetClock" to JsonPrimitive(true))), json(metadata))
    }

    private companion object {
        const val HELD_FILE = """[{"fileId":"file-1","groupId":"group-old","name":"Home","deleted":0}]"""
    }
}
