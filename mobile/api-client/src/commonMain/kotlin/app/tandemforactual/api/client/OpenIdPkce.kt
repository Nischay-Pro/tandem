package app.tandemforactual.api.client

private const val PKCE_VERIFIER_BYTE_COUNT = 32
private const val BASE64_URL_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

/** A short-lived PKCE pair. Its string representation intentionally never reveals the verifier. */
class OpenIdPkceProof internal constructor(
    val codeVerifier: String,
    val codeChallenge: String,
) {
    override fun toString(): String = "OpenIdPkceProof(codeVerifier=<redacted>)"
}

/** Generates an RFC 7636 S256 proof using a platform cryptographic random source. */
fun createOpenIdPkceProof(): OpenIdPkceProof {
    val verifier = encodeBase64Url(secureRandomBytes(PKCE_VERIFIER_BYTE_COUNT))
    return OpenIdPkceProof(verifier, openIdPkceS256Challenge(verifier))
}

/** Generates an independent state value to match the browser response to this app's pending login. */
fun createOpenIdCallbackState(): String = encodeBase64Url(secureRandomBytes(PKCE_VERIFIER_BYTE_COUNT))

internal fun openIdPkceS256Challenge(verifier: String): String = encodeBase64Url(sha256(verifier.encodeToByteArray()))

internal expect fun secureRandomBytes(size: Int): ByteArray

internal expect fun sha256(input: ByteArray): ByteArray

private fun encodeBase64Url(input: ByteArray): String {
    val output = StringBuilder((input.size * 4 + 2) / 3)
    var index = 0
    while (index + 2 < input.size) {
        val first = input[index].toInt() and 0xff
        val second = input[index + 1].toInt() and 0xff
        val third = input[index + 2].toInt() and 0xff
        output
            .append(BASE64_URL_ALPHABET[first ushr 2])
            .append(BASE64_URL_ALPHABET[((first and 0x03) shl 4) or (second ushr 4)])
            .append(BASE64_URL_ALPHABET[((second and 0x0f) shl 2) or (third ushr 6)])
            .append(BASE64_URL_ALPHABET[third and 0x3f])
        index += 3
    }
    if (index < input.size) {
        val first = input[index].toInt() and 0xff
        output
            .append(BASE64_URL_ALPHABET[first ushr 2])
            .append(BASE64_URL_ALPHABET[(first and 0x03) shl 4])
        if (index + 1 < input.size) {
            val second = input[index + 1].toInt() and 0xff
            output.set(output.lastIndex, BASE64_URL_ALPHABET[((first and 0x03) shl 4) or (second ushr 4)])
            output.append(BASE64_URL_ALPHABET[(second and 0x0f) shl 2])
        }
    }
    return output.toString()
}
