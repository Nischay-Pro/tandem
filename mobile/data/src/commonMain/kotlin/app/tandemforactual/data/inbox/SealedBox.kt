package app.tandemforactual.data.inbox

import app.tandemforactual.data.planning.PlanningKeys
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom

/** A budget's inbox key pair: [publicKey] is uncompressed P-256, [privateKey] PKCS#8 and kept only inside the vault. */
class InboxKeyPair(
    val keyId: String,
    val publicKey: ByteArray,
    val privateKey: ByteArray,
)

/** What a sealed entry says about itself without opening it. */
data class SealedHeader(
    val keyId: String,
    val entryId: String,
)

/** A sealed entry, opened. */
class OpenedEntry(
    val entryId: String,
    val payload: ByteArray,
)

class SealedEntryException : IllegalArgumentException("This entry is damaged or wasn't sealed for this budget.")

/**
 * Sealed boxes for the automation inbox. Each entry gets its own ephemeral P-256 key; ECDH with the budget's inbox key
 * and HKDF-SHA256, bound to the budget, key id and both public keys, give a one-use AES-256-GCM key. The whole header
 * and the budget id are authenticated, so an entry can't be moved to another budget, key or entry id.
 */
@OptIn(DelicateCryptographyApi::class)
object SealedBox {
    const val VERSION: Byte = 1
    private const val ID_BYTES = 16
    private const val POINT_BYTES = 65
    private const val NONCE_BYTES = 12
    private const val TAG_BYTES = 16
    private const val KEY_ID_AT = 1
    private const val ENTRY_ID_AT = KEY_ID_AT + ID_BYTES
    private const val POINT_AT = ENTRY_ID_AT + ID_BYTES
    private const val NONCE_AT = POINT_AT + POINT_BYTES
    private const val HEADER_BYTES = NONCE_AT + NONCE_BYTES
    private const val INFO = "tandem-inbox/v1"

    private val ecdh get() = CryptographyProvider.Default.get(ECDH)

    fun newKeyPair(): InboxKeyPair {
        val pair = ecdh.keyPairGenerator(EC.Curve.P256).generateKeyBlocking()
        val publicKey = pair.publicKey.encodeToByteArrayBlocking(EC.PublicKey.Format.RAW)
        return InboxKeyPair(keyId(publicKey), publicKey, pair.privateKey.encodeToByteArrayBlocking(EC.PrivateKey.Format.DER))
    }

    fun keyId(publicKey: ByteArray): String =
        CryptographyProvider.Default
            .get(SHA256)
            .hasher()
            .hashBlocking(publicKey)
            .copyOf(ID_BYTES)
            .toHex()

    fun newEntryId(): String = CryptographyRandom.nextBytes(ID_BYTES).toHex()

    fun seal(
        publicKey: ByteArray,
        budgetId: String,
        entryId: String,
        payload: ByteArray,
    ): ByteArray {
        val recipient = ecdh.publicKeyDecoder(EC.Curve.P256).decodeFromByteArrayBlocking(EC.PublicKey.Format.RAW, publicKey)
        val ephemeral = ecdh.keyPairGenerator(EC.Curve.P256).generateKeyBlocking()
        val point = ephemeral.publicKey.encodeToByteArrayBlocking(EC.PublicKey.Format.RAW)
        val shared = ephemeral.privateKey.sharedSecretGenerator().generateSharedSecretToByteArrayBlocking(recipient)
        val keyId = keyId(publicKey)
        val header = byteArrayOf(VERSION) + keyId.hexBytes() + entryId.hexBytes() + point + CryptographyRandom.nextBytes(NONCE_BYTES)
        val key = entryKey(shared, point, publicKey, budgetId, keyId)
        return header + aes(key).encryptWithIvBlocking(header.nonce(), payload, header + budgetId.encodeToByteArray())
    }

    /** The key and entry ids of [sealed], or null when it isn't a version Tandem can read. */
    fun header(sealed: ByteArray): SealedHeader? {
        if (sealed.size < HEADER_BYTES + TAG_BYTES || sealed[0] != VERSION) return null
        return SealedHeader(
            sealed.copyOfRange(KEY_ID_AT, ENTRY_ID_AT).toHex(),
            sealed.copyOfRange(ENTRY_ID_AT, POINT_AT).toHex(),
        )
    }

    fun open(
        pair: InboxKeyPair,
        budgetId: String,
        sealed: ByteArray,
    ): OpenedEntry {
        val header = header(sealed)?.takeIf { it.keyId == pair.keyId } ?: throw SealedEntryException()
        return try {
            val point = sealed.copyOfRange(POINT_AT, NONCE_AT)
            val ephemeral = ecdh.publicKeyDecoder(EC.Curve.P256).decodeFromByteArrayBlocking(EC.PublicKey.Format.RAW, point)
            val shared =
                ecdh
                    .privateKeyDecoder(EC.Curve.P256)
                    .decodeFromByteArrayBlocking(EC.PrivateKey.Format.DER, pair.privateKey)
                    .sharedSecretGenerator()
                    .generateSharedSecretToByteArrayBlocking(ephemeral)
            val head = sealed.copyOf(HEADER_BYTES)
            val key = entryKey(shared, point, pair.publicKey, budgetId, pair.keyId)
            val body = sealed.copyOfRange(HEADER_BYTES, sealed.size)
            OpenedEntry(header.entryId, aes(key).decryptWithIvBlocking(head.nonce(), body, head + budgetId.encodeToByteArray()))
        } catch (_: Exception) {
            throw SealedEntryException()
        }
    }

    private fun entryKey(
        shared: ByteArray,
        point: ByteArray,
        publicKey: ByteArray,
        budgetId: String,
        keyId: String,
    ): ByteArray = PlanningKeys.hkdf(shared, point + publicKey, "$INFO|$budgetId|$keyId".encodeToByteArray())

    private fun ByteArray.nonce() = copyOfRange(NONCE_AT, HEADER_BYTES)

    private fun aes(key: ByteArray) =
        CryptographyProvider.Default
            .get(AES.GCM)
            .keyDecoder()
            .decodeFromByteArrayBlocking(AES.Key.Format.RAW, key)
            .cipher()

    private fun String.hexBytes(): ByteArray {
        require(length == ID_BYTES * 2 && all { it in '0'..'9' || it in 'a'..'f' }) { "Ids are 16 bytes of lowercase hex." }
        return ByteArray(ID_BYTES) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}

internal fun ByteArray.toHex() = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
