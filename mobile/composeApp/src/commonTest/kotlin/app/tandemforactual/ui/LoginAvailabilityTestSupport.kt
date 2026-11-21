package app.tandemforactual.ui

import app.tandemforactual.api.client.ActualLoginMethodOption
import app.tandemforactual.api.client.ActualLoginMethods
import app.tandemforactual.api.client.TandemApiClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.io.IOException
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
    val unreachable: LoginMethodsObservation,
    val serverError: LoginMethodsObservation,
)

internal suspend fun loginMethodsOutcomes(): LoginMethodsOutcomes {
    val requests = mutableListOf<HttpRequestData>()
    var responses: Map<String, Pair<String, HttpStatusCode>> = emptyMap()
    var offline = false
    val engine =
        MockEngine { request ->
            requests += request
            if (offline) throw IOException("offline")
            val (body, status) = responses[request.url.encodedPath] ?: ("" to HttpStatusCode.NotFound)
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
    val client = TandemApiClient(http = HttpClient(engine))

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

    val success =
        loadScenario(
            mapOf(
                "/account/needs-bootstrap" to
                    ("""{"status":"ok","data":{"bootstrapped":true,"loginMethod":"password"}}""" to HttpStatusCode.OK),
                "/account/login-methods" to
                    (
                        """{"status":"ok","methods":[{"method":"password","displayName":"Password","active":1},
                            {"method":"openid","displayName":"OpenID","active":1}]}""" to HttpStatusCode.OK
                    ),
            ),
        )
    val unreachable = loadScenario(emptyMap(), simulateOffline = true)
    val serverError =
        loadScenario(
            mapOf(
                "/account/needs-bootstrap" to ("""{"status":"error","reason":"internal-error"}""" to HttpStatusCode.ServiceUnavailable),
            ),
        )
    return LoginMethodsOutcomes(success, unreachable, serverError)
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
    assertEquals(listOf("/account/needs-bootstrap", "/account/login-methods"), outcomes.success.requests.map { it.path })

    assertEquals("Actual isn’t responding at this address. Check the server address.", outcomes.unreachable.signInError)
    assertFalse(outcomes.unreachable.isServerReachable)
    assertNull(outcomes.unreachable.actualLoginMethods)

    assertTrue(outcomes.serverError.isServerReachable)
    assertNull(outcomes.serverError.actualLoginMethods)
    assertEquals(listOf("/account/needs-bootstrap"), outcomes.serverError.requests.map { it.path })

    assertTrue(
        listOf(outcomes.success, outcomes.unreachable, outcomes.serverError).flatMap { it.requests }.all { it.authorization == null },
        "Sign-in availability checks must not send credentials",
    )
}
