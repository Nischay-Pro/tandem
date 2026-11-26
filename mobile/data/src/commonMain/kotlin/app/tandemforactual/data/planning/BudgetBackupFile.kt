package app.tandemforactual.data.planning

import app.tandemforactual.data.actual.ZipArchive
import dev.whyoleg.cryptography.BinarySize
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.PBKDF2
import dev.whyoleg.cryptography.algorithms.SHA512
import dev.whyoleg.cryptography.random.CryptographyRandom

/**
 * A whole-budget backup: a zip holding Actual's `db.sqlite` and `metadata.json`, which Actual can import as is, plus
 * Tandem's planning records. Without a passphrase it stays a plain zip; with one the zip is sealed like a planning backup:
 * PBKDF2-HMAC-SHA512 derives the key and AES-256-GCM seals the bytes behind a short header.
 */
object BudgetBackupFile {
    const val DATABASE = "db.sqlite"
    const val METADATA = "metadata.json"
    const val PLANNING = "tandem-planning.json"
    private val MAGIC = "TNDMBK01".encodeToByteArray()
    private const val IDENTITY = "tandem-budget-backup/v1"
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val ITERATIONS = 210_000
    private const val MAX_ITERATIONS = ITERATIONS * 5

    fun build(
        database: ByteArray,
        metadataJson: String,
        planningJson: String?,
        passphrase: String?,
    ): ByteArray {
        require(passphrase == null || passphrase.isNotEmpty()) { "An empty passphrase can't seal a backup." }
        val zip =
            ZipArchive.write(
                buildMap {
                    put(DATABASE, database)
                    put(METADATA, metadataJson.encodeToByteArray())
                    planningJson?.let { put(PLANNING, it.encodeToByteArray()) }
                },
            )
        if (passphrase == null) return zip
        val salt = CryptographyRandom.nextBytes(SALT_BYTES)
        val (iv, sealed) = PlanningCipher(key(passphrase, salt, ITERATIONS)).seal(zip, IDENTITY)
        return MAGIC + intBytes(ITERATIONS) + salt + iv + sealed
    }

    fun isSealed(bytes: ByteArray): Boolean = bytes.size > MAGIC.size && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    /** The zip inside a sealed backup, or null when [passphrase] is wrong or the file is damaged. */
    internal fun unseal(
        bytes: ByteArray,
        passphrase: String,
    ): ByteArray? =
        runCatching {
            var at = MAGIC.size
            val iterations = readInt(bytes, at).also { at += 4 }
            require(iterations in ITERATIONS..MAX_ITERATIONS)
            val salt = bytes.copyOfRange(at, at + SALT_BYTES).also { at += SALT_BYTES }
            val iv = bytes.copyOfRange(at, at + IV_BYTES).also { at += IV_BYTES }
            PlanningCipher(key(passphrase, salt, iterations)).open(iv, bytes.copyOfRange(at, bytes.size), IDENTITY)
        }.getOrNull()

    private fun intBytes(value: Int) = ByteArray(4) { (value shr (24 - 8 * it)).toByte() }

    private fun readInt(
        bytes: ByteArray,
        at: Int,
    ): Int = (0 until 4).fold(0) { total, i -> (total shl 8) or (bytes[at + i].toInt() and 0xFF) }

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
