package app.tandemforactual.data.planning

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.random.CryptographyRandom

/**
 * Seals planning records with AES-256-GCM under a random per-budget key. The budget, record, schema version and
 * revision are authenticated with each record, so a sealed record can't be replayed under another identity or revision.
 */
@OptIn(DelicateCryptographyApi::class)
class PlanningCipher(
    key: ByteArray,
) {
    init {
        require(key.size == KEY_BYTES) { "Planning keys are 32 bytes." }
    }

    private val cipher =
        CryptographyProvider.Default
            .get(AES.GCM)
            .keyDecoder()
            .decodeFromByteArrayBlocking(AES.Key.Format.RAW, key)
            .cipher()

    fun seal(
        plaintext: ByteArray,
        identity: String,
    ): Pair<ByteArray, ByteArray> {
        val iv = CryptographyRandom.nextBytes(IV_BYTES)
        return iv to cipher.encryptWithIvBlocking(iv, plaintext, identity.encodeToByteArray())
    }

    fun open(
        iv: ByteArray,
        ciphertext: ByteArray,
        identity: String,
    ): ByteArray = cipher.decryptWithIvBlocking(iv, ciphertext, identity.encodeToByteArray())

    companion object {
        const val KEY_BYTES = 32
        private const val IV_BYTES = 12

        fun newKey(): ByteArray = CryptographyRandom.nextBytes(KEY_BYTES)

        internal fun identity(
            budgetId: String,
            recordId: String,
            schemaVersion: Int,
            revision: Long,
        ) = "tandem-planning/v1|$budgetId|$recordId|$schemaVersion|$revision"
    }
}
