package app.tandemforactual.data.planning

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.tandemforactual.data.actual.ZipArchive
import kotlin.io.path.createTempFile
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class BackupArchiveTest {
    private val database = sqliteDatabase()
    private val metadata = """{"id":"b1","budgetName":"Household","exportedBy":"Tandem","exportedAt":1764163200000}"""
    private val planning =
        """{"format":"tandem-planning-backup","version":1,"budgetId":"b1","budgetName":"Household","createdOn":"2026-11-26",""" +
            """"records":[{"id":"g1","type":"planning.savings_goal","body":{"name":"Trip"}},""" +
            """{"id":"p1","type":"profile.jurisdiction_period","body":{}},{"id":"x","type":"budget.transaction","body":{}}]}"""
    private val records =
        listOf(
            Triple("g1", "planning.savings_goal", JsonObject(mapOf("name" to JsonPrimitive("Trip")))),
            Triple("p1", "profile.jurisdiction_period", JsonObject(emptyMap())),
        )
    private val opened = OpenedBackup.Contents(database, metadata, records, "b1", "Household", "2026-11-26", 1764163200000)

    @Test
    fun opensPlainBudgetBackups() {
        assertEquals(opened, BackupArchive.open(BudgetBackupFile.build(database, metadata, planning, null), null))
        val budgetOnly = BackupArchive.open(BudgetBackupFile.build(database, metadata, null, null), "ignored")
        assertEquals(opened.copy(planningRecords = null, createdOn = null), budgetOnly)
    }

    @Test
    fun opensSealedBudgetBackupsWithTheirPassphrase() {
        val sealed = BudgetBackupFile.build(database, metadata, planning, "correct horse")
        assertEquals(OpenedBackup.NeedsPassphrase, BackupArchive.open(sealed, null))
        assertEquals(OpenedBackup.NeedsPassphrase, BackupArchive.open(sealed, ""))
        assertEquals(OpenedBackup.WrongPassphrase, BackupArchive.open(sealed, "wrong"))
        assertEquals(OpenedBackup.WrongPassphrase, BackupArchive.open(sealed.copyOf(sealed.size - 1), "correct horse"))
        val contents = BackupArchive.open(sealed, "correct horse") as OpenedBackup.Contents
        assertEquals(opened, contents)
        assertContentEquals(database, contents.database)
    }

    @Test
    fun opensActualExportsWithoutTandemExtras() {
        val zip =
            ZipArchive.write(
                mapOf(
                    "db.sqlite" to database,
                    "metadata.json" to """{"id":"My-Budget-1a2b","budgetName":"Mine"}""".toByteArray(),
                ),
            )
        val contents = BackupArchive.open(zip, null) as OpenedBackup.Contents
        assertEquals("My-Budget-1a2b" to "Mine", contents.budgetId to contents.budgetName)
        assertNull(contents.exportedAt)
        val bare = BackupArchive.open(ZipArchive.write(mapOf("db.sqlite" to database)), null)
        assertEquals(OpenedBackup.Contents(database, null, null, null, null, null, null), bare)
    }

    @Test
    fun opensPlanningOnlyBackups() {
        val planningOnly = OpenedBackup.Contents(null, null, records, "b1", "Household", "2026-11-26", null)
        assertEquals(planningOnly, BackupArchive.open(planning.toByteArray(), null))
        val sealed = PlanningBackupSeal.seal(planning, "passphrase1").toByteArray()
        assertEquals(OpenedBackup.NeedsPassphrase, BackupArchive.open(sealed, null))
        assertEquals(OpenedBackup.WrongPassphrase, BackupArchive.open(sealed, "nope"))
        assertEquals(planningOnly, BackupArchive.open(sealed, "passphrase1"))
        val older = """{"format":"tandem-planning-backup","version":1,"budgetId":7,"records":[]}"""
        assertEquals(OpenedBackup.Contents(null, null, emptyList(), null, null, null, null), BackupArchive.open(older.toByteArray(), null))
        val sealedOther = PlanningBackupSeal.seal("""{"format":"other"}""", "passphrase1").toByteArray()
        assertEquals(OpenedBackup.Rejected(BackupFileProblem.DAMAGED), BackupArchive.open(sealedOther, "passphrase1"))
    }

    @Test
    fun rejectsFilesThatAreNotBackups() {
        val notABackup = OpenedBackup.Rejected(BackupFileProblem.NOT_A_BACKUP)
        assertEquals(notABackup, BackupArchive.open(ByteArray(0), null))
        assertEquals(notABackup, BackupArchive.open(byteArrayOf(0xC3.toByte(), 0x28), null))
        assertEquals(notABackup, BackupArchive.open("""{"format":"other","records":[]}""".toByteArray(), null))
        assertEquals(notABackup, BackupArchive.open("""{"format":"tandem-planning-backup","records":[{}]}""".toByteArray(), null))
        assertEquals(notABackup, BackupArchive.open("not json".toByteArray(), null))
        assertEquals(notABackup, BackupArchive.open(ZipArchive.write(mapOf("other.txt" to ByteArray(1))), null))
    }

    @Test
    fun rejectsDamagedZips() {
        val damaged = OpenedBackup.Rejected(BackupFileProblem.DAMAGED)
        val zip = BudgetBackupFile.build(database, metadata, planning, null)
        assertEquals(damaged, BackupArchive.open(zip.copyOf(zip.size / 2), null))
        assertEquals(damaged, BackupArchive.open(zip.copyOf().also { it[200] = (it[200] + 1).toByte() }, null))
        assertEquals(damaged, BackupArchive.open(BudgetBackupFile.build(database, "not json", null, null), null))
        assertEquals(damaged, BackupArchive.open(BudgetBackupFile.build(database, metadata, "[]", null), null))
    }

    @Test
    fun rejectsDatabasesThatAreNotSqlite() {
        val notSqlite = OpenedBackup.Rejected(BackupFileProblem.NOT_A_BUDGET_DATABASE)
        assertEquals(notSqlite, BackupArchive.open(BudgetBackupFile.build(Random(1).nextBytes(4096), metadata, null, null), null))
        assertEquals(notSqlite, BackupArchive.open(BudgetBackupFile.build(ByteArray(0), metadata, null, "correct horse"), "correct horse"))
    }

    @Test
    fun comparesContentsByValue() {
        assertFalse(opened.equals("x"))
        assertNotEquals(opened, opened.copy(database = database.copyOf(10)))
        assertNotEquals(opened, opened.copy(metadataJson = null))
        assertNotEquals(opened, opened.copy(planningRecords = null))
        assertNotEquals(opened, opened.copy(budgetId = null))
        assertNotEquals(opened, opened.copy(budgetName = null))
        assertNotEquals(opened, opened.copy(createdOn = null))
        assertNotEquals(opened, opened.copy(exportedAt = null))
        assertEquals(opened.hashCode(), opened.copy(database = database.copyOf()).hashCode())
    }

    private fun sqliteDatabase(): ByteArray {
        val file = createTempFile(suffix = ".sqlite").toFile()
        try {
            BundledSQLiteDriver().open(file.path).use { connection ->
                connection.execSQL("CREATE TABLE accounts (id TEXT PRIMARY KEY, name TEXT)")
                connection.execSQL("INSERT INTO accounts VALUES ('a1', 'Checking')")
            }
            return file.readBytes()
        } finally {
            file.delete()
        }
    }
}
