package app.tandemforactual.ui

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import app.tandemforactual.api.client.createOpenIdCallbackState
import app.tandemforactual.api.client.createOpenIdPkceProof

/** Short-lived proof for one browser round trip. Saved instance state restores it; credentials do not. */
internal class OpenIdLoginProofState(
    private val nowEpochMillis: () -> Long,
) {
    private var pendingVerifier: String? = null
    private var pendingCallbackState: String? = null
    private var pendingServerUrl: String? = null
    private var expiresAtEpochMillis: Long = 0

    fun begin(serverUrl: String): OpenIdLoginRequestProof {
        val proof = createOpenIdPkceProof()
        val callbackState = createOpenIdCallbackState()
        pendingVerifier = proof.codeVerifier
        pendingCallbackState = callbackState
        pendingServerUrl = serverUrl.trim()
        expiresAtEpochMillis = nowEpochMillis() + OPEN_ID_PROOF_TTL_MILLIS
        return OpenIdLoginRequestProof(proof.codeChallenge, callbackState)
    }

    fun takeVerifierForCallback(
        serverUrl: String,
        callbackState: String,
    ): String? {
        expireIfNeeded()
        if (pendingServerUrl != serverUrl.trim()) {
            clear()
            return null
        }
        if (pendingCallbackState != callbackState) return null
        return pendingVerifier.also { clear() }
    }

    fun expireIfNeeded() {
        if (pendingVerifier != null) {
            val now = nowEpochMillis()
            if (now >= expiresAtEpochMillis || expiresAtEpochMillis - now > OPEN_ID_PROOF_TTL_MILLIS) clear()
        }
    }

    fun remainingTtlMillis(): Long {
        expireIfNeeded()
        return if (pendingVerifier == null) 0 else (expiresAtEpochMillis - nowEpochMillis()).coerceAtLeast(0)
    }

    fun clear() {
        pendingVerifier = null
        pendingCallbackState = null
        pendingServerUrl = null
        expiresAtEpochMillis = 0
    }

    internal fun saveTransientSnapshot(): List<String> =
        listOf(
            pendingVerifier.orEmpty(),
            pendingServerUrl.orEmpty(),
            pendingCallbackState.orEmpty(),
            expiresAtEpochMillis.toString(),
        )

    companion object {
        fun saver(nowEpochMillis: () -> Long): Saver<OpenIdLoginProofState, Any> =
            listSaver(
                save = { state -> state.saveTransientSnapshot() },
                restore = { saved -> restoreTransientSnapshot(saved, nowEpochMillis) },
            )

        internal fun restoreTransientSnapshot(
            saved: List<String>,
            nowEpochMillis: () -> Long,
        ): OpenIdLoginProofState =
            OpenIdLoginProofState(nowEpochMillis).apply {
                pendingVerifier = saved.getOrNull(0)?.takeIf(String::isNotEmpty)
                pendingServerUrl = saved.getOrNull(1)?.takeIf(String::isNotEmpty)
                pendingCallbackState = saved.getOrNull(2)?.takeIf(String::isNotEmpty)
                expiresAtEpochMillis = saved.getOrNull(3)?.toLongOrNull() ?: 0
                if (pendingVerifier == null || pendingServerUrl == null || pendingCallbackState == null) {
                    clear()
                } else {
                    expireIfNeeded()
                }
            }
    }
}

internal data class OpenIdLoginRequestProof(
    val codeChallenge: String,
    val callbackState: String,
)

internal const val OPEN_ID_PROOF_TTL_MILLIS = 6 * 60 * 1_000L + 30_000L
