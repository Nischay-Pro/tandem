package app.tandemforactual.data.planning

import app.tandemforactual.data.actual.ZipArchive
import dev.whyoleg.cryptography.BinarySize
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.PBKDF2
import dev.whyoleg.cryptography.algorithms.SHA512
import dev.whyoleg.cryptography.random.CryptographyRandom

/** What a budget backup holds once opened. */
sealed interface BudgetBackupContents {
    data class Opened(
        val database: ByteArray,
        val planningJson: String?,
        val metadataJson: String?,
    ) : BudgetBackupContents {
        override fun equals(other: Any?): Boolean =
            other is Opened &&
                database.contentEquals(other.database) &&
                planningJson == other.planningJson &&
                metadataJson == other.metadataJson

        override fun hashCode(): Int = database.contentHashCode()
    }

    data object NeedsPassphrase : BudgetBackupContents

    data object WrongPassphrase : BudgetBackupContents

    data object NotABackup : BudgetBackupContents
}

/**
 * A whole-budget backup: a zip holding Actual's `db.sqlite` and `metadata.json`, which Actual can import as is, plus
 * Tandem's planning records. With a passphrase the zip is sealed like a planning backup: PBKDF2-HMAC-SHA512 derives the
 * key and AES-256-GCM seals the bytes behind a short header.
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

    fun build(
        database: ByteArray,
        metadataJson: String,
        planningJson: String?,
        passphrase: String?,
    ): ByteArray {
        val zip =
            ZipArchive.write(
                buildMap {
                    put(DATABASE, database)
                    put(METADATA, metadataJson.encodeToByteArray())
                    planningJson?.let { put(PLANNING, it.encodeToByteArray()) }
                },
            )
        if (passphrase.isNullOrEmpty()) return zip
        val salt = CryptographyRandom.nextBytes(SALT_BYTES)
        val (iv, sealed) = PlanningCipher(key(passphrase, salt, ITERATIONS)).seal(zip, IDENTITY)
        return MAGIC + intBytes(ITERATIONS) + salt + iv + sealed
    }

    fun isSealed(bytes: ByteArray): Boolean = bytes.size > MAGIC.size && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    fun read(
        bytes: ByteArray,
        passphrase: String?,
    ): BudgetBackupContents {
        val zip =
            if (isSealed(bytes)) {
                if (passphrase.isNullOrEmpty()) return BudgetBackupContents.NeedsPassphrase
                open(bytes, passphrase) ?: return BudgetBackupContents.WrongPassphrase
            } else {
                bytes
            }
        val entries = runCatching { ZipArchive.read(zip) }.getOrNull() ?: return BudgetBackupContents.NotABackup
        val database = entries[DATABASE] ?: return BudgetBackupContents.NotABackup
        return BudgetBackupContents.Opened(database, entries[PLANNING]?.decodeToString(), entries[METADATA]?.decodeToString())
    }

    private fun open(
        bytes: ByteArray,
        passphrase: String,
    ): ByteArray? =
        runCatching {
            var at = MAGIC.size
            val iterations = readInt(bytes, at).also { at += 4 }
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
