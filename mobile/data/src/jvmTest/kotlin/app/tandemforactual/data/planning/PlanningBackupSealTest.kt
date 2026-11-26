package app.tandemforactual.data.planning

import dev.whyoleg.cryptography.BinarySize
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.PBKDF2
import dev.whyoleg.cryptography.algorithms.SHA512
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalEncodingApi::class)
class PlanningBackupSealTest {
    private val backup = """{"format":"tandem-planning-backup","version":1,"records":[]}"""

    @Test
    fun opensWithTheSamePassphrase() {
        val sealed = PlanningBackupSeal.seal(backup, "correct horse")
        assertTrue(PlanningBackupSeal.isSealed(sealed))
        assertFalse(sealed.contains("records"))
        assertEquals(backup, PlanningBackupSeal.open(sealed, "correct horse"))
    }

    @Test
    fun rejectsAWrongPassphraseOrTamperedFile() {
        val sealed = PlanningBackupSeal.seal(backup, "correct horse")
        assertNull(PlanningBackupSeal.open(sealed, "wrong"))
        assertNull(PlanningBackupSeal.open(sealed.replace("\"iterations\":210000", "\"iterations\":\"x\""), "correct horse"))
    }

    @Test
    fun ignoresFilesThatArentSealedBackups() {
        assertFalse(PlanningBackupSeal.isSealed(backup))
        assertFalse(PlanningBackupSeal.isSealed("not json"))
        assertNull(PlanningBackupSeal.open(backup, "correct horse"))
        assertNull(PlanningBackupSeal.open("not json", "correct horse"))
    }

    @Test
    fun rejectsIterationCountsOutsideWhatTandemWrites() {
        assertEquals(backup, PlanningBackupSeal.open(sealedWith(210_000), "correct horse"))
        assertEquals(backup, PlanningBackupSeal.open(sealedWith(1_050_000), "correct horse"))
        assertNull(PlanningBackupSeal.open(sealedWith(1), "correct horse"))
        assertNull(PlanningBackupSeal.open(sealedWith(209_999), "correct horse"))
        assertNull(PlanningBackupSeal.open(sealedWith(1_050_001), "correct horse"))
        assertNull(PlanningBackupSeal.open(sealedWith(1, Int.MAX_VALUE), "correct horse"))
    }

    private fun sealedWith(
        iterations: Int,
        claimed: Int = iterations,
    ): String {
        val salt = Random(3).nextBytes(16)
        val key =
            CryptographyProvider.Default
                .get(PBKDF2)
                .secretDerivation(SHA512, iterations, with(BinarySize) { PlanningCipher.KEY_BYTES.bytes }, salt)
                .deriveSecretToByteArrayBlocking("correct horse".encodeToByteArray())
        val (iv, sealed) = PlanningCipher(key).seal(backup.encodeToByteArray(), "tandem-planning-backup/v1")
        return """{"format":"${PlanningBackupSeal.FORMAT}","iterations":$claimed,"salt":"${Base64.encode(salt)}",""" +
            """"iv":"${Base64.encode(iv)}","data":"${Base64.encode(sealed)}"}"""
    }
}
