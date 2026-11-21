package app.tandemforactual.data.planning

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom

/** A budget's planning key as other devices receive it: wrapped, never in the clear. */
data class WrappedPlanningKey(
    val keyId: String,
    val wrappedKey: ByteArray,
    val wrapSalt: ByteArray,
    val keyCheck: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is WrappedPlanningKey &&
            keyId == other.keyId &&
            wrappedKey.contentEquals(other.wrappedKey) &&
            wrapSalt.contentEquals(other.wrapSalt) &&
            keyCheck.contentEquals(other.keyCheck)

    override fun hashCode(): Int = keyId.hashCode() * 31 + keyCheck.contentHashCode()
}

class PlanningKeyMismatchException : IllegalStateException("This device's key can't unwrap the budget's planning key.")

/**
 * Wraps a budget's random planning key for other devices. The wrapping key comes from HKDF-SHA256 over a secret every
 * member already holds: the Actual budget key for encrypted budgets, or a recovery key otherwise. Changing the Actual
 * password only re-wraps; the planning key and its records stay the same.
 */
@OptIn(DelicateCryptographyApi::class)
object PlanningKeys {
    private const val INFO = "tandem-planning-key/v1"

    fun hkdf(
        secret: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        length: Int = 32,
    ): ByteArray {
        val prk = hmac(salt.takeIf { it.isNotEmpty() } ?: ByteArray(32), secret)
        val out = ArrayList<Byte>()
        var block = ByteArray(0)
        var counter = 1
        while (out.size < length) {
            block = hmac(prk, block + info + counter.toByte())
            out += block.toList()
            counter++
        }
        return out.take(length).toByteArray()
    }

    private fun hmac(
        key: ByteArray,
        data: ByteArray,
    ): ByteArray =
        CryptographyProvider.Default
            .get(HMAC)
            .keyDecoder(SHA256)
            .decodeFromByteArrayBlocking(HMAC.Key.Format.RAW, key)
            .signatureGenerator()
            .generateSignatureBlocking(data)

    /** A short value that lets devices tell whether they hold the same key without revealing it. */
    fun check(key: ByteArray): ByteArray = hmac(key, "tandem-planning-check".encodeToByteArray()).copyOf(16)

    fun wrap(
        planningKey: ByteArray,
        secret: ByteArray,
        budgetId: String,
    ): WrappedPlanningKey {
        val salt = CryptographyRandom.nextBytes(16)
        val iv = CryptographyRandom.nextBytes(12)
        val sealed =
            aes(
                hkdf(secret, salt, "$INFO|$budgetId".encodeToByteArray()),
            ).encryptWithIvBlocking(iv, planningKey, budgetId.encodeToByteArray())
        return WrappedPlanningKey(check(planningKey).toHex(), iv + sealed, salt, check(planningKey))
    }

    fun unwrap(
        wrapped: WrappedPlanningKey,
        secret: ByteArray,
        budgetId: String,
    ): ByteArray {
        val kek = hkdf(secret, wrapped.wrapSalt, "$INFO|$budgetId".encodeToByteArray())
        val key =
            runCatching {
                aes(
                    kek,
                ).decryptWithIvBlocking(
                    wrapped.wrappedKey.copyOf(12),
                    wrapped.wrappedKey.copyOfRange(12, wrapped.wrappedKey.size),
                    budgetId.encodeToByteArray(),
                )
            }.getOrElse { throw PlanningKeyMismatchException() }
        if (!check(key).contentEquals(wrapped.keyCheck)) throw PlanningKeyMismatchException()
        return key
    }

    private fun aes(key: ByteArray) =
        CryptographyProvider.Default
            .get(AES.GCM)
            .keyDecoder()
            .decodeFromByteArrayBlocking(AES.Key.Format.RAW, key)
            .cipher()

    private fun ByteArray.toHex() = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}
