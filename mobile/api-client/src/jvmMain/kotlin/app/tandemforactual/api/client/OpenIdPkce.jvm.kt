package app.tandemforactual.api.client

import java.security.MessageDigest
import java.security.SecureRandom

private val pkceSecureRandom = SecureRandom()

internal actual fun secureRandomBytes(size: Int): ByteArray {
    require(size > 0)
    return ByteArray(size).also(pkceSecureRandom::nextBytes)
}

internal actual fun sha256(input: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(input)
