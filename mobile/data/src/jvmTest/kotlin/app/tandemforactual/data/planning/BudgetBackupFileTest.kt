package app.tandemforactual.data.planning

import app.tandemforactual.data.actual.ZipArchive
import dev.whyoleg.cryptography.BinarySize
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.PBKDF2
import dev.whyoleg.cryptography.algorithms.SHA512
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BudgetBackupFileTest {
    private val database = Random(7).nextBytes(70_000)
    private val metadata = """{"budgetName":"Household"}"""
    private val planning = """{"format":"tandem-planning-backup","version":1,"records":[]}"""

    @Test
    fun writesAZipEveryToolCanRead() {
        val zip = ZipArchive.write(mapOf("db.sqlite" to database, "metadata.json" to metadata.encodeToByteArray(), "é.txt" to ByteArray(0)))
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zip)).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                entries[entry.name] = input.readBytes()
            }
        }
        assertEquals(setOf("db.sqlite", "metadata.json", "é.txt"), entries.keys)
        assertContentEquals(database, entries["db.sqlite"])
        assertContentEquals(database, ZipArchive.read(zip)["db.sqlite"])
        assertEquals(0xCBF43926L, ZipArchive.crc32("123456789".encodeToByteArray()))
    }

    @Test
    fun recognisesOnlyItsOwnSealedFiles() {
        assertTrue(BudgetBackupFile.isSealed(BudgetBackupFile.build(database, metadata, planning, "correct horse")))
        assertFalse(BudgetBackupFile.isSealed(BudgetBackupFile.build(database, metadata, planning, null)))
        assertFalse(BudgetBackupFile.isSealed("TNDMBK01".encodeToByteArray()))
        assertNull(
            BudgetBackupFile.unseal(BudgetBackupFile.build(database, metadata, planning, "correct horse").copyOf(20), "correct horse"),
        )
    }

    @Test
    fun refusesToWriteAnUnsealedBackupForAnEmptyPassphrase() {
        assertFailsWith<IllegalArgumentException> { BudgetBackupFile.build(database, metadata, planning, "") }
    }

    @Test
    fun rejectsIterationCountsOutsideWhatTandemWrites() {
        val zip = ZipArchive.write(mapOf("db.sqlite" to database, "metadata.json" to metadata.encodeToByteArray()))
        assertContentEquals(zip, BudgetBackupFile.unseal(sealedWith(zip, 210_000), "correct horse"))
        assertContentEquals(zip, BudgetBackupFile.unseal(sealedWith(zip, 1_050_000), "correct horse"))
        assertNull(BudgetBackupFile.unseal(sealedWith(zip, 1), "correct horse"))
        assertNull(BudgetBackupFile.unseal(sealedWith(zip, 209_999), "correct horse"))
        assertNull(BudgetBackupFile.unseal(sealedWith(zip, 1_050_001), "correct horse"))
        assertNull(BudgetBackupFile.unseal(sealedWith(zip, 1, Int.MAX_VALUE), "correct horse"))
    }

    private fun sealedWith(
        zip: ByteArray,
        iterations: Int,
        claimed: Int = iterations,
    ): ByteArray {
        val salt = Random(3).nextBytes(16)
        val key =
            CryptographyProvider.Default
                .get(PBKDF2)
                .secretDerivation(SHA512, iterations, with(BinarySize) { PlanningCipher.KEY_BYTES.bytes }, salt)
                .deriveSecretToByteArrayBlocking("correct horse".encodeToByteArray())
        val (iv, sealed) = PlanningCipher(key).seal(zip, "tandem-budget-backup/v1")
        val count = ByteArray(4) { (claimed shr (24 - 8 * it)).toByte() }
        return "TNDMBK01".encodeToByteArray() + count + salt + iv + sealed
    }
}
