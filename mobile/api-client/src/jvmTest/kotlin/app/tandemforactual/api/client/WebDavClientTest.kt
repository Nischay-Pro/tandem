package app.tandemforactual.api.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking

class WebDavClientTest {
    private class FakeDav {
        val files = linkedMapOf<String, ByteArray>()
        var folderExists = false
        var rejectSignIn = false
        val seen = mutableListOf<String>()
        val http =
            HttpClient(
                MockEngine { request ->
                    val path = request.url.encodedPath
                    seen += "${request.method.value} $path ${request.headers["Authorization"]} ${request.headers["Depth"]}"
                    if (rejectSignIn) return@MockEngine respond("", HttpStatusCode.Unauthorized)
                    when (request.method.value) {
                        "PROPFIND" ->
                            if (!folderExists) {
                                respond("", HttpStatusCode.NotFound)
                            } else {
                                val items = files.keys.joinToString("") { "<d:response><d:href>/dav/backups/$it</d:href></d:response>" }
                                respond(
                                    "<d:multistatus><d:response><D:href>/dav/backups/</D:href></d:response>$items</d:multistatus>",
                                    HttpStatusCode.MultiStatus,
                                )
                            }
                        "MKCOL" -> respond("", HttpStatusCode.Created).also { folderExists = true }
                        "PUT" -> {
                            files[path.substringAfterLast('/')] =
                                (request.body as io.ktor.http.content.OutgoingContent.ByteArrayContent).bytes()
                            respond("", HttpStatusCode.Created)
                        }
                        "DELETE" ->
                            respond(
                                "",
                                if (files.remove(path.substringAfterLast('/')) !=
                                    null
                                ) {
                                    HttpStatusCode.NoContent
                                } else {
                                    HttpStatusCode.NotFound
                                },
                            )
                        else -> respond("", HttpStatusCode.MethodNotAllowed)
                    }
                },
            )
    }

    @Test
    fun createsTheFolderUploadsListsAndDeletes(): Unit =
        runBlocking {
            val dav = FakeDav()
            val client = WebDavClient(dav.http, "https://cloud.example/dav/backups", "sam", "secret")
            client.check()
            client.check()
            client.upload("tandem-backup-home-2026-11-24-0930.zip", byteArrayOf(1, 2, 3))
            client.upload("tandem-backup-home-2026-11-25-0930.zip", byteArrayOf(4))
            assertContentEquals(byteArrayOf(1, 2, 3), dav.files["tandem-backup-home-2026-11-24-0930.zip"])
            assertEquals(listOf("tandem-backup-home-2026-11-24-0930.zip", "tandem-backup-home-2026-11-25-0930.zip"), client.list())
            client.delete("tandem-backup-home-2026-11-24-0930.zip")
            client.delete("missing.zip")
            assertEquals(listOf("tandem-backup-home-2026-11-25-0930.zip"), client.list())
            assertEquals("PROPFIND /dav/backups/ Basic c2FtOnNlY3JldA== 0", dav.seen.first())
            assertEquals("MKCOL /dav/backups/ Basic c2FtOnNlY3JldA== null", dav.seen[1])
        }

    @Test
    fun explainsWhatWentWrong(): Unit =
        runBlocking {
            assertFailsWith<WebDavException> { WebDavClient(HttpClient(MockEngine { respond("") }), "http://cloud.example/dav", "a", "b") }
            WebDavClient(HttpClient(MockEngine { respond("") }), "http://192.168.1.20/dav", "a", "b")
            WebDavClient.connect("https://cloud.example/dav", "a", "b")
            val dav = FakeDav().apply { rejectSignIn = true }
            val client = WebDavClient(dav.http, "https://cloud.example/dav/backups/", "sam", "wrong")
            assertEquals("The WebDAV username or password was rejected.", assertFailsWith<WebDavException> { client.check() }.message)
            assertEquals(401, assertFailsWith<WebDavException> { client.upload("a", byteArrayOf()) }.status)
            assertFailsWith<WebDavException> { client.list() }
            assertFailsWith<WebDavException> { client.delete("a") }
            val offline =
                WebDavClient(HttpClient(MockEngine { throw kotlinx.io.IOException("down") }), "https://cloud.example/dav", "a", "b")
            assertEquals("Couldn’t reach the WebDAV server.", assertFailsWith<WebDavException> { offline.check() }.message)
            val broken =
                WebDavClient(
                    HttpClient(
                        MockEngine { request ->
                            respond(
                                "",
                                if (request.method.value ==
                                    "PROPFIND"
                                ) {
                                    HttpStatusCode.NotFound
                                } else {
                                    HttpStatusCode.Forbidden
                                },
                            )
                        },
                    ),
                    "https://cloud.example/dav",
                    "a",
                    "b",
                )
            assertEquals("Couldn’t create the backup folder.", assertFailsWith<WebDavException> { broken.check() }.message)
            val locked =
                WebDavClient(HttpClient(MockEngine { respond("", HttpStatusCode.Forbidden) }), "https://cloud.example/dav", "a", "b")
            assertEquals("Couldn’t open the backup folder.", assertFailsWith<WebDavException> { locked.check() }.message)
        }
}
