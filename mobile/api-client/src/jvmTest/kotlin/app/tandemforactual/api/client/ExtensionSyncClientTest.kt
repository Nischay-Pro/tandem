package app.tandemforactual.api.client

import app.tandemforactual.data.planning.RemoteWrite
import app.tandemforactual.data.planning.WrappedPlanningKey
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class ExtensionSyncClientTest {
    private val json = headersOf("Content-Type", "application/json")

    @Test
    fun speaksTheExtensionSyncContract(): Unit =
        runBlocking {
            val seen = mutableListOf<String>()
            var keyStored = false
            val http =
                HttpClient(
                    MockEngine { request ->
                        seen +=
                            "${request.method.value} ${request.url.encodedPath}${request.url.encodedQuery.takeIf {
                                it.isNotEmpty()
                            }?.let { "?$it" }.orEmpty()} ${request.headers["x-actual-token"]}"
                        val body = (request.body as? TextContent)?.text.orEmpty()
                        when {
                            request.url.encodedPath.endsWith("/key") && request.method == HttpMethod.Get -> {
                                if (keyStored) {
                                    respond("""{"keyId":"k","wrappedKey":"AQI=","wrapSalt":"Aw==","keyCheck":"BA=="}""", headers = json)
                                } else {
                                    respond("", HttpStatusCode.NotFound)
                                }
                            }

                            request.url.encodedPath.endsWith("/key") -> {
                                if ("\"expectedKeyId\":null" in body && !keyStored) {
                                    keyStored = true
                                    respond("""{"status":"stored"}""", headers = json)
                                } else {
                                    respond("""{"error":{"code":"key_conflict","message":"x"}}""", HttpStatusCode.Conflict, json)
                                }
                            }

                            request.url.encodedPath.endsWith("/changes") -> {
                                respond(
                                    """{"records":[{"recordId":"r","recordType":"planning.savings_goal","schemaVersion":1,"revision":2,"deleted":false,"iv":"AQ==","ciphertext":"Ag==","seq":7}],"cursor":7,"hasMore":false${if ("anchor" in request.url.encodedQuery) ""","restored":true""" else ""}}""",
                                    headers = json,
                                )
                            }

                            request.url.encodedPath.endsWith("/records/fresh") -> {
                                respond("""{"seq":8}""", headers = json)
                            }

                            request.url.encodedPath.endsWith("/records/stale") -> {
                                respond(
                                    """{"error":{"code":"revision_conflict","message":"x"},"current":{"recordId":"stale","recordType":"t","schemaVersion":1,"revision":4,"deleted":true,"iv":"AQ==","ciphertext":"Ag==","seq":3}}""",
                                    HttpStatusCode.Conflict,
                                    json,
                                )
                            }

                            request.url.encodedPath.endsWith("/records/new") -> {
                                respond(
                                    """{"error":{"code":"revision_conflict","message":"x"},"current":null}""",
                                    HttpStatusCode.Conflict,
                                    json,
                                )
                            }

                            else -> {
                                respond("""{"error":{"code":"budget_forbidden","message":"x"}}""", HttpStatusCode.Forbidden, json)
                            }
                        }
                    },
                )
            var token: String? = "session"
            val client = ExtensionSyncClient(http, "https://actual.example/", "b1") { token }
            assertNull(client.key())
            val key = WrappedPlanningKey("k", byteArrayOf(1, 2), byteArrayOf(3), byteArrayOf(4))
            assertTrue(client.putKey(key, null))
            assertFalse(client.putKey(key, null))
            assertEquals(key, client.key())
            val page = client.changes(3)
            assertEquals(7, page.cursor)
            assertFalse(page.hasMore)
            assertContentEquals(byteArrayOf(2), page.records.single().ciphertext)
            assertFalse(page.restored)
            assertTrue(client.changes(7, "r 1").restored)
            assertTrue("GET /extension/v1/budgets/b1/changes?after=7&limit=200&anchor=r%201 session" in seen)
            assertEquals(RemoteWrite.Stored(8), client.put("fresh", "t", 1, 1, null, false, byteArrayOf(1), byteArrayOf(2)))
            assertEquals(
                4L,
                (client.put("stale", "t", 1, 2, 1, false, byteArrayOf(1), byteArrayOf(2)) as RemoteWrite.Conflict).current?.revision,
            )
            assertNull((client.put("new", "t", 1, 1, null, false, byteArrayOf(1), byteArrayOf(2)) as RemoteWrite.Conflict).current)
            assertEquals(
                403,
                assertFailsWith<ExtensionSyncException> {
                    client.put("other", "t", 1, 1, null, false, byteArrayOf(), byteArrayOf())
                }.status,
            )
            assertEquals("GET /extension/v1/budgets/b1/changes?after=3&limit=200 session", seen.first { "changes" in it })
            token = null
            assertEquals("sign_in_required", assertFailsWith<TandemApiException> { client.changes(0) }.errorCode)
        }

    @Test
    fun detectsWhetherTheServerRunsExtensionSync(): Unit =
        runBlocking {
            fun server(
                status: HttpStatusCode,
                body: String,
            ) = HttpClient(MockEngine { respond(body, status, headersOf("Content-Type", "application/json")) })
            assertEquals(
                true,
                ExtensionSyncClient.available(
                    server(HttpStatusCode.OK, """{"apiVersion":1,"features":["planning-records"]}"""),
                    "https://a",
                ),
            )
            assertEquals(false, ExtensionSyncClient.available(server(HttpStatusCode.OK, """{"apiVersion":1}"""), "https://a"))
            assertEquals(false, ExtensionSyncClient.available(server(HttpStatusCode.NotFound, "Not found"), "https://a"))
            assertEquals(false, ExtensionSyncClient.available(server(HttpStatusCode.OK, "not json"), "https://a"))
            assertNull(ExtensionSyncClient.available(server(HttpStatusCode.ServiceUnavailable, ""), "https://a"))
            assertNull(ExtensionSyncClient.available(server(HttpStatusCode.TooManyRequests, ""), "https://a"))
            assertNull(ExtensionSyncClient.available(server(HttpStatusCode.RequestTimeout, ""), "https://a"))
            assertNull(ExtensionSyncClient.available(HttpClient(MockEngine { throw kotlinx.io.IOException("offline") }), "https://a"))
        }
}
