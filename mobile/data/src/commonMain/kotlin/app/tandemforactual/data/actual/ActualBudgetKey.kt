package app.tandemforactual.data.actual

import dev.whyoleg.cryptography.BinarySize
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.PBKDF2
import dev.whyoleg.cryptography.algorithms.SHA512
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * An Actual budget encryption key: PBKDF2-HMAC-SHA512 over the password, then AES-256-GCM with a 12-byte IV and a
 * 16-byte tag, matching Actual's `encryption-internals`.
 */
@OptIn(DelicateCryptographyApi::class, ExperimentalEncodingApi::class)
class ActualBudgetKey private constructor(
    override val keyId: String,
    private val raw: ByteArray,
) : ActualMessageCipher {
    private val cipher =
        CryptographyProvider.Default
            .get(AES.GCM)
            .keyDecoder()
            .decodeFromByteArrayBlocking(AES.Key.Format.RAW, raw)
            .cipher()

    val base64: String get() = Base64.encode(raw)

    override fun encrypt(plaintext: ByteArray): EncryptedData {
        val iv = CryptographyRandom.nextBytes(IV_BYTES)
        val sealed = cipher.encryptWithIvBlocking(iv, plaintext)
        return EncryptedData(iv, sealed.copyOfRange(sealed.size - TAG_BYTES, sealed.size), sealed.copyOfRange(0, sealed.size - TAG_BYTES))
    }

    override fun decrypt(data: EncryptedData): ByteArray = cipher.decryptWithIvBlocking(data.iv, data.data + data.authTag)

    /** Decrypts a value stored with Actual's `{iv, authTag}` metadata, such as a downloaded budget file. */
    fun decrypt(
        value: ByteArray,
        meta: JsonObject,
    ): ByteArray = decrypt(EncryptedData(Base64.decode(meta.text("iv")), Base64.decode(meta.text("authTag")), value))

    /** Whether this key opens the server's test message, which is how Actual checks a password. */
    fun opens(testJson: String): Boolean =
        runCatching {
            val test = Json.parseToJsonElement(testJson).jsonObject
            decrypt(Base64.decode(test.text("value")), test.getValue("meta").jsonObject)
        }.isSuccess

    companion object {
        private const val IV_BYTES = 12
        private const val TAG_BYTES = 16
        private const val ITERATIONS = 10_000

        fun derive(
            keyId: String,
            password: String,
            salt: String,
        ): ActualBudgetKey =
            ActualBudgetKey(
                keyId,
                CryptographyProvider.Default
                    .get(PBKDF2)
                    .secretDerivation(SHA512, ITERATIONS, with(BinarySize) { 32.bytes }, salt.encodeToByteArray())
                    .deriveSecretToByteArrayBlocking(password.encodeToByteArray()),
            )

        fun fromBase64(
            keyId: String,
            base64: String,
        ): ActualBudgetKey = ActualBudgetKey(keyId, Base64.decode(base64))

        private fun JsonObject.text(name: String) = getValue(name).jsonPrimitive.content
    }
}
