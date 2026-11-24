package app.tandemforactual.data.planning

import app.tandemforactual.data.actual.ZipArchive
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
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
    fun opensPlainBackupsAsActualImportsThem() {
        val bytes = BudgetBackupFile.build(database, metadata, planning, passphrase = null)
        assertFalse(BudgetBackupFile.isSealed(bytes))
        assertEquals(BudgetBackupContents.Opened(database, planning, metadata), BudgetBackupFile.read(bytes, null))
        val budgetOnly = BudgetBackupFile.build(database, metadata, null, "")
        assertNull((BudgetBackupFile.read(budgetOnly, null) as BudgetBackupContents.Opened).planningJson)
    }

    @Test
    fun sealsWithAPassphrase() {
        val bytes = BudgetBackupFile.build(database, metadata, planning, "correct horse")
        assertTrue(BudgetBackupFile.isSealed(bytes))
        assertEquals(BudgetBackupContents.NeedsPassphrase, BudgetBackupFile.read(bytes, null))
        assertEquals(BudgetBackupContents.WrongPassphrase, BudgetBackupFile.read(bytes, "wrong"))
        assertEquals(BudgetBackupContents.WrongPassphrase, BudgetBackupFile.read(bytes.copyOf(20), "correct horse"))
        assertEquals(BudgetBackupContents.Opened(database, planning, metadata), BudgetBackupFile.read(bytes, "correct horse"))
    }

    @Test
    fun rejectsFilesThatAreNotBackups() {
        assertEquals(BudgetBackupContents.NotABackup, BudgetBackupFile.read(Random(1).nextBytes(64), null))
        assertEquals(BudgetBackupContents.NotABackup, BudgetBackupFile.read(ZipArchive.write(mapOf("a" to ByteArray(1))), null))
        assertFalse(BudgetBackupFile.isSealed("TNDMBK01".encodeToByteArray()))
        val opened = BudgetBackupContents.Opened(database, planning, metadata)
        assertFalse(opened.equals("x"))
        assertFalse(opened == opened.copy(planningJson = null))
        assertEquals(opened.hashCode(), opened.copy().hashCode())
    }
}
