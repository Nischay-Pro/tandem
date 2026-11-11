package app.tandemforactual.ui

import app.tandemforactual.api.client.ActualLoginMethodOption
import app.tandemforactual.api.client.ActualLoginMethods
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.api.generated.Api
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal data class LoginMethodsRequest(
    val path: String,
    val authorization: String?,
)

internal data class LoginMethodsObservation(
    val signInError: String?,
    val isServerReachable: Boolean,
    val actualLoginMethods: ActualLoginMethods?,
    val requests: List<LoginMethodsRequest>,
)

internal data class LoginMethodsOutcomes(
    val success: LoginMethodsObservation,
    val actualOnly: LoginMethodsObservation,
    val unreachable: LoginMethodsObservation,
    val actualUnavailable: LoginMethodsObservation,
)

internal suspend fun loginMethodsOutcomes(): LoginMethodsOutcomes {
    val requests = mutableListOf<HttpRequestData>()
    var responses: Map<String, Pair<String, HttpStatusCode>> = emptyMap()
    var offline = false
    val engine =
        MockEngine { request ->
            requests += request
            if (offline) throw IllegalStateException("offline")
            val (body, status) = responses[request.url.encodedPath] ?: ("" to HttpStatusCode.NotFound)
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
    val previousClient = Api.client
    val previousBaseUrl = Api.baseUrl
    val client = TandemApiClient()
    Api.updateClient(
        createHttpClient = {
            HttpClient(engine) {
                followRedirects = false
                install(ContentNegotiation) { json(Api.json) }
                install(ContentEncoding) {
                    gzip()
                    deflate()
                }
                install(DefaultRequest) { url.takeFrom(Api.baseUrl) }
            }
        },
    )

    suspend fun loadScenario(
        scenarioResponses: Map<String, Pair<String, HttpStatusCode>>,
        simulateOffline: Boolean = false,
    ): LoginMethodsObservation {
        requests.clear()
        responses = scenarioResponses
        offline = simulateOffline
        val session = TandemMobileSession(client, null, { 1_000L })
        session.loadActualLoginMethods("https://tandem.example")
        return LoginMethodsObservation(
            signInError = session.signInError,
            isServerReachable = session.isServerReachable,
            actualLoginMethods = session.actualLoginMethods,
            requests = requests.map { LoginMethodsRequest(it.url.encodedPath, it.headers[HttpHeaders.Authorization]) },
        )
    }

    try {
        val success =
            loadScenario(
                mapOf(
                    "/api/v1/auth/methods" to
                        (
                            """{"bootstrapped":true,"loginMethod":"password","availableLoginMethods":[{"method":"password","displayName":"Password","active":true},
                                {"method":"openid","displayName":"OpenID","active":true}]}""" to
                                HttpStatusCode.OK
                        ),
                ),
            )
        val actualOnly =
            loadScenario(
                mapOf(
                    "/api/v1/auth/methods" to ("Not found" to HttpStatusCode.NotFound),
                    "/api/v1/health/ready" to
                        (
                            """{"error":{"code":"proxy_unavailable","message":"private detail"}}""" to
                                HttpStatusCode.InternalServerError
                        ),
                    "/account/needs-bootstrap" to
                        ("""{"status":"ok","data":{"bootstrapped":true}}""" to HttpStatusCode.OK),
                ),
            )
        val unreachable = loadScenario(emptyMap(), simulateOffline = true)
        val actualUnavailable =
            loadScenario(
                mapOf(
                    "/api/v1/auth/methods" to
                        (
                            """{"error":{"code":"actual_unavailable","message":"private detail"}}""" to
                                HttpStatusCode.ServiceUnavailable
                        ),
                ),
            )

        return LoginMethodsOutcomes(success, actualOnly, unreachable, actualUnavailable)
    } finally {
        Api.baseUrl = previousBaseUrl
        Api.client.close()
        previousClient.close()
    }
}

internal fun assertLoginMethodsOutcomes(outcomes: LoginMethodsOutcomes) {
    assertEquals(null, outcomes.success.signInError)
    assertTrue(outcomes.success.isServerReachable)
    assertEquals(
        ActualLoginMethods(
            bootstrapped = true,
            loginMethod = "password",
            availableLoginMethods =
                listOf(
                    ActualLoginMethodOption("password", "Password", true),
                    ActualLoginMethodOption("openid", "OpenID", true),
                ),
        ),
        outcomes.success.actualLoginMethods,
    )

    assertEquals(
        "Actual is running at this address, but Tandem isn’t. Check that Tandem is running and that the proxy sends /api/v1/ to it.",
        outcomes.actualOnly.signInError,
    )
    assertFalse(outcomes.actualOnly.isServerReachable)
    assertNull(outcomes.actualOnly.actualLoginMethods)
    assertEquals(
        listOf("/api/v1/auth/methods", "/api/v1/health/ready", "/account/needs-bootstrap"),
        outcomes.actualOnly.requests.map { it.path },
    )

    assertEquals("Couldn’t reach Tandem at this address. Check the address and connection.", outcomes.unreachable.signInError)
    assertFalse(outcomes.unreachable.isServerReachable)
    assertNull(outcomes.unreachable.actualLoginMethods)
    assertEquals(3, outcomes.unreachable.requests.size)

    assertEquals("Tandem can’t reach Actual. Check the Actual connection, then try again.", outcomes.actualUnavailable.signInError)
    assertTrue(outcomes.actualUnavailable.isServerReachable)
    assertNull(outcomes.actualUnavailable.actualLoginMethods)
    assertEquals(listOf("/api/v1/auth/methods"), outcomes.actualUnavailable.requests.map { it.path })

    assertTrue(
        listOf(outcomes.success, outcomes.actualOnly, outcomes.unreachable, outcomes.actualUnavailable)
            .flatMap { it.requests }
            .all { it.authorization == null },
        "Sign-in availability checks and diagnostic probes must not send credentials",
    )
}
