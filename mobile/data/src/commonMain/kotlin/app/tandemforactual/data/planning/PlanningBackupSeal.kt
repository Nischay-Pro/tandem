package app.tandemforactual.data.planning

import dev.whyoleg.cryptography.BinarySize
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.PBKDF2
import dev.whyoleg.cryptography.algorithms.SHA512
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** Encrypts a planning backup with a passphrase: PBKDF2-HMAC-SHA512 derives the key, AES-256-GCM seals the contents. */
@OptIn(ExperimentalEncodingApi::class)
object PlanningBackupSeal {
    const val FORMAT = "tandem-planning-backup-sealed"
    private const val IDENTITY = "tandem-planning-backup/v1"
    private const val SALT_BYTES = 16
    private const val ITERATIONS = 210_000

    fun seal(
        contents: String,
        passphrase: String,
    ): String {
        val salt = CryptographyRandom.nextBytes(SALT_BYTES)
        val (iv, sealed) = PlanningCipher(key(passphrase, salt, ITERATIONS)).seal(contents.encodeToByteArray(), IDENTITY)
        return buildJsonObject {
            put("format", FORMAT)
            put("iterations", ITERATIONS)
            put("salt", Base64.encode(salt))
            put("iv", Base64.encode(iv))
            put("data", Base64.encode(sealed))
        }.toString()
    }

    /** Whether [contents] is a sealed backup, so the caller knows to ask for the passphrase. */
    fun isSealed(contents: String): Boolean = root(contents)?.text("format") == FORMAT

    /** The backup inside [contents], or null when the passphrase is wrong or the file isn't a sealed backup. */
    fun open(
        contents: String,
        passphrase: String,
    ): String? {
        val root = root(contents)?.takeIf { it.text("format") == FORMAT } ?: return null
        return runCatching {
            val iterations = root.text("iterations")!!.toInt()
            val cipher = PlanningCipher(key(passphrase, Base64.decode(root.text("salt")!!), iterations))
            cipher.open(Base64.decode(root.text("iv")!!), Base64.decode(root.text("data")!!), IDENTITY).decodeToString()
        }.getOrNull()
    }

    private fun root(contents: String): JsonObject? = runCatching { Json.parseToJsonElement(contents).jsonObject }.getOrNull()

    private fun JsonObject.text(name: String): String? = (get(name) as? JsonPrimitive)?.content

    private fun key(
        passphrase: String,
        salt: ByteArray,
        iterations: Int,
    ): ByteArray =
        CryptographyProvider.Default
            .get(PBKDF2)
            .secretDerivation(SHA512, iterations, with(BinarySize) { PlanningCipher.KEY_BYTES.bytes }, salt)
            .deriveSecretToByteArrayBlocking(passphrase.encodeToByteArray())
}
