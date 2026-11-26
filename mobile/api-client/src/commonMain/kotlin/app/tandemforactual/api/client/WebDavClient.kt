package app.tandemforactual.api.client

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.decodeURLPart
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class WebDavException(
    val status: Int?,
    message: String,
) : IllegalStateException(message)

/**
 * A folder on a WebDAV server, such as Nextcloud, ownCloud or a NAS, where automatic backups are copied. Only HTTPS
 * addresses are accepted, except on the local network, so the password never crosses the internet in the clear.
 */
@OptIn(ExperimentalEncodingApi::class)
class WebDavClient(
    private val http: HttpClient,
    folderUrl: String,
    private val username: String,
    private val password: String,
) {
    private val folder =
        try {
            parseServerUrl(folderUrl, allowInsecurePrivateNetwork = true).toString().trimEnd('/') + "/"
        } catch (_: TandemApiException) {
            throw WebDavException(null, "Use an https:// WebDAV address, or http:// on your local network.")
        }

    /** Checks the address and sign-in, creating the folder when it doesn't exist yet. */
    suspend fun check() {
        val found = send(PROPFIND, folder) { header("Depth", "0") }
        if (found.status == HttpStatusCode.NotFound) {
            val created = send(MKCOL, folder)
            if (!created.status.isSuccess()) fail(created, "Couldn’t create the backup folder.")
        } else if (!found.status.isSuccess()) {
            fail(found, "Couldn’t open the backup folder.")
        }
    }

    suspend fun upload(
        name: String,
        bytes: ByteArray,
    ) {
        val response =
            send(HttpMethod.Put, file(name)) { setBody(ByteArrayContent(bytes, ContentType.Application.OctetStream)) }
        if (!response.status.isSuccess()) fail(response, "Couldn’t upload the backup.")
    }

    /** Renames [from] to [to], replacing any file already called [to]. */
    suspend fun move(
        from: String,
        to: String,
    ) {
        val response =
            send(MOVE, file(from)) {
                header("Destination", file(to))
                header("Overwrite", "T")
            }
        if (!response.status.isSuccess()) fail(response, "Couldn’t upload the backup.")
    }

    /** The names of the files directly inside the folder. */
    suspend fun list(): List<String> {
        val response = send(PROPFIND, folder) { header("Depth", "1") }
        if (!response.status.isSuccess()) fail(response, "Couldn’t list the backup folder.")
        return hrefs(response.bodyAsText())
            .map { it.trimEnd('/').substringAfterLast('/').decodeURLPart() }
            .filter { it.isNotEmpty() && !folder.trimEnd('/').endsWith("/$it") }
            .distinct()
    }

    suspend fun delete(name: String) {
        val response = send(HttpMethod.Delete, file(name))
        if (!response.status.isSuccess() && response.status != HttpStatusCode.NotFound) fail(response, "Couldn’t delete an old backup.")
    }

    private fun file(name: String) = folder + name.encodeURLPathPart()

    private suspend fun send(
        method: HttpMethod,
        url: String,
        configure: HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse =
        try {
            http.request(url) {
                this.method = method
                header("Authorization", "Basic " + Base64.encode("$username:$password".encodeToByteArray()))
                configure()
            }
        } catch (error: Exception) {
            if (error is kotlin.coroutines.cancellation.CancellationException) throw error
            throw WebDavException(null, "Couldn’t reach the WebDAV server.")
        }

    private fun fail(
        response: HttpResponse,
        message: String,
    ): Nothing =
        throw WebDavException(
            response.status.value,
            if (response.status == HttpStatusCode.Unauthorized) "The WebDAV username or password was rejected." else message,
        )

    companion object {
        /** A client with the platform's default HTTP engine. */
        fun connect(
            folderUrl: String,
            username: String,
            password: String,
        ) = WebDavClient(HttpClient(), folderUrl, username, password)

        private val PROPFIND = HttpMethod("PROPFIND")
        private val MKCOL = HttpMethod("MKCOL")
        private val MOVE = HttpMethod("MOVE")

        private fun hrefs(xml: String): List<String> =
            Regex("<(?:[A-Za-z0-9]+:)?href>([^<]*)</(?:[A-Za-z0-9]+:)?href>", RegexOption.IGNORE_CASE)
                .findAll(xml)
                .map {
                    it.groupValues[1].trim()
                }.toList()
    }
}
