package app.tandemforactual.data.planning

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
}
