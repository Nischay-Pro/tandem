package app.tandemforactual.data.database

import android.content.Context
import android.util.AtomicFile
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.tandemforactual.data.actual.ActualDatabase
import java.io.File

/**
 * Keeps each Actual budget in `noBackupFilesDir`, written atomically so an interrupted download leaves no partial file.
 * With a [sealedDriver], which opens SQLCipher databases under a raw key (an empty key opens plaintext), encrypted
 * budgets are kept encrypted; their plaintext only touches disk in a staging file deleted as soon as it's copied.
 */
class AndroidActualBudgetFiles(
    context: Context,
    private val driver: SQLiteDriver = BundledSQLiteDriver(),
    private val sealedDriver: ((rawKey: String) -> SQLiteDriver)? = null,
) : ActualBudgetFiles {
    private val root = context.applicationContext.noBackupFilesDir.resolve("actual")
    private val backups = root.resolve("backups")

    private fun file(budgetId: String): File = root.resolve(budgetFileName(budgetId))

    private fun sealedFile(budgetId: String): File = File(file(budgetId).path + ".sealed")

    private fun keyInfoFile(budgetId: String): File = File(file(budgetId).path + ".key")

    private fun stagingFile(budgetId: String): File = File(file(budgetId).path + STAGING)

    override val canSeal: Boolean get() = sealedDriver != null

    override fun open(budgetId: String): ActualDatabase? =
        file(budgetId)
            .takeIf {
                it.isFile
            }?.let { ActualDatabase(driver.open(it.absolutePath)) }

    override fun install(
        budgetId: String,
        bytes: ByteArray,
    ): ActualDatabase = install(budgetId) { it.write(bytes) }

    internal fun install(
        budgetId: String,
        write: (java.io.OutputStream) -> Unit,
    ): ActualDatabase =
        synchronized(lock) {
            prepareRoot()
            val target = file(budgetId)
            removeCopies(budgetId)
            writeAtomically(target, write)
            ActualDatabase(driver.open(target.absolutePath))
        }

    override fun sealedKeyInfo(budgetId: String): String? =
        keyInfoFile(budgetId).takeIf { it.isFile && sealedFile(budgetId).isFile }?.readText()

    override fun openSealed(
        budgetId: String,
        key: ByteArray,
    ): ActualDatabase? {
        val sealed = sealedFile(budgetId).takeIf { it.isFile } ?: return null
        return ActualDatabase(requireSealedDriver()(SealedBudgetSql.rawKey(key)).open(sealed.absolutePath))
    }

    override fun installSealed(
        budgetId: String,
        bytes: ByteArray,
        key: ByteArray,
        keyInfo: String,
    ): ActualDatabase =
        synchronized(lock) {
            prepareRoot()
            val staging = stagingFile(budgetId)
            try {
                staging.writeBytes(bytes)
                removeCopies(budgetId)
                seal(budgetId, staging, key, keyInfo)
            } finally {
                deleteWithSidecars(staging)
            }
            checkNotNull(openSealed(budgetId, key)) { "Could not open the encrypted budget copy." }
        }

    override fun sealBudget(
        budgetId: String,
        key: ByteArray,
        keyInfo: String,
    ) = synchronized(lock) {
        val plain = file(budgetId)
        check(plain.isFile) { "There's no budget copy to encrypt." }
        prepareRoot()
        seal(budgetId, plain, key, keyInfo)
        deleteWithSidecars(plain)
        retiredCopies(budgetId).filterNot { File(it.path + ".key").isFile }.forEach(::deleteRetired)
    }

    /** Copies the plaintext database at [source] into the budget's encrypted file, then records [keyInfo] beside it. */
    private fun seal(
        budgetId: String,
        source: File,
        key: ByteArray,
        keyInfo: String,
    ) {
        val sealed = sealedFile(budgetId)
        val next = File(sealed.path + ".new")
        deleteWithSidecars(next)
        val plain = ActualDatabase(requireSealedDriver()("").open(source.absolutePath))
        try {
            SealedBudgetSql.exportSealed(plain, next.absolutePath, key)
        } finally {
            plain.close()
        }
        deleteWithSidecars(sealed)
        check(next.renameTo(sealed)) { "Could not save the encrypted budget copy." }
        deleteWithSidecars(next)
        writeAtomically(keyInfoFile(budgetId)) { it.write(keyInfo.encodeToByteArray()) }
    }

    private fun writeAtomically(
        target: File,
        write: (java.io.OutputStream) -> Unit,
    ) {
        val destination = AtomicFile(target)
        val output = destination.startWrite()
        try {
            write(output)
            destination.finishWrite(output)
        } catch (error: Throwable) {
            destination.failWrite(output)
            throw error
        }
    }

    override fun exportPlain(
        budgetId: String,
        db: ActualDatabase,
        path: String,
    ) = if (sealedFile(budgetId).isFile) SealedBudgetSql.exportPlain(db, path) else db.execute("VACUUM INTO ?", path)

    override fun snapshot(
        budgetId: String,
        write: (path: String) -> Unit,
    ): ByteArray {
        ensureRoot()
        val copy = File(file(budgetId).path + ".snapshot").apply { delete() }
        try {
            write(copy.absolutePath)
            return copy.readBytes()
        } finally {
            deleteWithSidecars(copy)
        }
    }

    override fun remove(budgetId: String) =
        synchronized(lock) {
            removeCopies(budgetId)
            retiredCopies(budgetId).forEach(::deleteRetired)
        }

    override fun retire(
        budgetId: String,
        label: String,
    ) = synchronized(lock) {
        val previous = retiredCopies(budgetId)
        val backup = backups.apply { mkdirs() }.resolve("$budgetId-${budgetFileName(label)}")
        val sealed = sealedFile(budgetId).isFile
        val source = if (sealed) sealedFile(budgetId) else file(budgetId)
        if (!source.isFile) return@synchronized
        sidecarSuffixes.forEach { suffix ->
            val from = File(source.path + suffix)
            if (from.exists()) check(from.renameTo(File(backup.path + suffix))) { "Could not keep a backup of this budget." }
        }
        val info = keyInfoFile(budgetId)
        if (sealed && info.isFile) {
            check(info.renameTo(File(backup.path + ".key"))) { "Could not keep a backup of this budget." }
        }
        removeCopies(budgetId)
        previous.filter { it.path != backup.path }.forEach(::deleteRetired)
    }

    private fun removeCopies(budgetId: String) {
        deleteWithSidecars(file(budgetId))
        deleteWithSidecars(sealedFile(budgetId))
        keyInfoFile(budgetId).delete()
    }

    private fun retiredCopies(budgetId: String): List<File> {
        budgetFileName(budgetId)
        return backups
            .listFiles { candidate -> candidate.name.startsWith("$budgetId-reset-") && candidate.name.endsWith(".sqlite") }
            .orEmpty()
            .toList()
    }

    private fun deleteWithSidecars(file: File) {
        sidecarSuffixes.forEach { File(file.path + it).delete() }
    }

    private fun deleteRetired(copy: File) {
        deleteWithSidecars(copy)
        File(copy.path + ".key").delete()
    }

    private fun ensureRoot() =
        check(root.isDirectory || root.mkdirs() || root.isDirectory) { "Could not create the budget storage directory." }

    /** A staging file left by a crash holds a decrypted budget, so it goes before anything new is written. */
    private fun prepareRoot() {
        ensureRoot()
        root.listFiles { candidate -> candidate.name.contains(STAGING) }?.forEach(File::delete)
    }

    private fun requireSealedDriver() = checkNotNull(sealedDriver) { "Encrypted budget copies aren't supported here." }

    private companion object {
        const val STAGING = ".staging"
        val lock = Any()
        val sidecarSuffixes = listOf("", "-wal", "-shm", "-journal")
    }
}
