package app.tandemforactual.data.database

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.tandemforactual.data.actual.ActualDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSFileManager
import platform.Foundation.NSLock
import platform.Foundation.NSURL
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fflush
import platform.posix.fileno
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.fsync
import platform.posix.ftell
import platform.posix.fwrite
import platform.posix.rename

private val budgetFilesLock = NSLock()

/**
 * Keeps each Actual budget in Application Support with iOS file protection and backup exclusion. Encrypted budgets
 * are kept as SQLCipher databases; their plaintext only touches disk in a staging file deleted once it's copied.
 */
@OptIn(ExperimentalForeignApi::class)
class IosActualBudgetFiles(
    private val driver: SQLiteDriver = BundledSQLiteDriver(),
) : ActualBudgetFiles {
    private val files = NSFileManager.defaultManager
    private val root by lazy {
        val path = encryptedTandemDatabasePath("actual")
        val protection = mapOf<Any?, Any?>("NSFileProtectionKey" to "NSFileProtectionCompleteUntilFirstUserAuthentication")
        check(files.createDirectoryAtPath(path, true, protection, null)) { "Could not create the budget storage directory." }
        check(NSURL.fileURLWithPath(path).setResourceValue(true, forKey = "NSURLIsExcludedFromBackupKey", error = null)) {
            "Could not exclude budget storage from backup."
        }
        path
    }
    private val backups get() = "$root/backups"

    private fun path(budgetId: String) = "$root/${budgetFileName(budgetId)}"

    private fun sealedPath(budgetId: String) = path(budgetId) + ".sealed"

    private fun keyInfoPath(budgetId: String) = path(budgetId) + ".key"

    override val canSeal: Boolean get() = true

    override fun open(budgetId: String): ActualDatabase? =
        path(budgetId).takeIf { files.fileExistsAtPath(it) }?.let { ActualDatabase(driver.open(it)) }

    override fun install(
        budgetId: String,
        bytes: ByteArray,
    ): ActualDatabase {
        require(bytes.isNotEmpty()) { "The downloaded Actual database is empty." }
        return locked {
            removeStaleStaging()
            val target = path(budgetId)
            val staging = "$target.new"
            try {
                writeSynced(staging, bytes, "Could not save the downloaded Actual database.")
                removeCopies(budgetId)
                check(rename(staging, target) == 0) { "Could not install the downloaded Actual database." }
            } finally {
                delete(staging)
            }
            ActualDatabase(driver.open(target))
        }
    }

    override fun sealedKeyInfo(budgetId: String): String? {
        if (!files.fileExistsAtPath(sealedPath(budgetId))) return null
        return readFile(keyInfoPath(budgetId))?.decodeToString()
    }

    override fun openSealed(
        budgetId: String,
        key: ByteArray,
    ): ActualDatabase? {
        val sealed = sealedPath(budgetId).takeIf { files.fileExistsAtPath(it) } ?: return null
        return ActualDatabase(SqlCipherDriver(SealedBudgetSql.rawKey(key)).open(sealed))
    }

    override fun installSealed(
        budgetId: String,
        bytes: ByteArray,
        key: ByteArray,
        keyInfo: String,
    ): ActualDatabase {
        require(bytes.isNotEmpty()) { "The downloaded Actual database is empty." }
        return locked {
            removeStaleStaging()
            val staging = path(budgetId) + STAGING
            try {
                writeSynced(staging, bytes, "Could not save the downloaded Actual database.")
                removeCopies(budgetId)
                seal(budgetId, staging, key, keyInfo)
            } finally {
                deleteWithSidecars(staging)
            }
            checkNotNull(openSealed(budgetId, key)) { "Could not open the encrypted budget copy." }
        }
    }

    override fun sealBudget(
        budgetId: String,
        key: ByteArray,
        keyInfo: String,
    ) = locked {
        val plain = path(budgetId)
        check(files.fileExistsAtPath(plain)) { "There's no budget copy to encrypt." }
        removeStaleStaging()
        seal(budgetId, plain, key, keyInfo)
        deleteWithSidecars(plain)
        retiredCopies(budgetId).filterNot { files.fileExistsAtPath("$it.key") }.forEach(::deleteRetired)
    }

    /** Copies the plaintext database at [source] into the budget's encrypted file, then records [keyInfo] beside it. */
    private fun seal(
        budgetId: String,
        source: String,
        key: ByteArray,
        keyInfo: String,
    ) {
        val sealed = sealedPath(budgetId)
        val next = "$sealed.new"
        deleteWithSidecars(next)
        val plain = ActualDatabase(SqlCipherDriver("").open(source))
        try {
            SealedBudgetSql.exportSealed(plain, next, key)
        } finally {
            plain.close()
        }
        deleteWithSidecars(sealed)
        check(rename(next, sealed) == 0) { "Could not save the encrypted budget copy." }
        deleteWithSidecars(next)
        val info = keyInfoPath(budgetId)
        writeSynced("$info.new", keyInfo.encodeToByteArray(), "Could not save the encrypted budget copy.")
        check(rename("$info.new", info) == 0) { "Could not save the encrypted budget copy." }
    }

    override fun exportPlain(
        budgetId: String,
        db: ActualDatabase,
        path: String,
    ) = if (files.fileExistsAtPath(sealedPath(budgetId))) SealedBudgetSql.exportPlain(db, path) else db.execute("VACUUM INTO ?", path)

    override fun retire(
        budgetId: String,
        label: String,
    ) = locked {
        val previous = retiredCopies(budgetId)
        files.createDirectoryAtPath(backups, true, null, null)
        val backup = "$backups/$budgetId-${budgetFileName(label)}"
        val sealed = files.fileExistsAtPath(sealedPath(budgetId))
        val source = if (sealed) sealedPath(budgetId) else path(budgetId)
        if (!files.fileExistsAtPath(source)) return@locked
        SIDECARS.forEach { suffix ->
            if (files.fileExistsAtPath(source + suffix)) {
                check(rename(source + suffix, backup + suffix) == 0) { "Could not keep a backup of this budget." }
            }
        }
        if (sealed && files.fileExistsAtPath(keyInfoPath(budgetId))) {
            check(rename(keyInfoPath(budgetId), "$backup.key") == 0) { "Could not keep a backup of this budget." }
        }
        removeCopies(budgetId)
        previous.filter { it != backup }.forEach(::deleteRetired)
    }

    override fun snapshot(
        budgetId: String,
        write: (path: String) -> Unit,
    ): ByteArray {
        val copy = path(budgetId) + ".snapshot"
        deleteWithSidecars(copy)
        try {
            write(copy)
            return checkNotNull(readFile(copy)) { "Could not read the budget copy." }
        } finally {
            deleteWithSidecars(copy)
        }
    }

    override fun remove(budgetId: String) =
        locked {
            removeCopies(budgetId)
            retiredCopies(budgetId).forEach(::deleteRetired)
        }

    private fun removeCopies(budgetId: String) {
        deleteWithSidecars(path(budgetId))
        deleteWithSidecars(sealedPath(budgetId))
        delete(keyInfoPath(budgetId))
    }

    private fun retiredCopies(budgetId: String): List<String> {
        budgetFileName(budgetId)
        return files
            .contentsOfDirectoryAtPath(backups, null)
            .orEmpty()
            .filterIsInstance<String>()
            .filter { it.startsWith("$budgetId-reset-") && it.endsWith(".sqlite") }
            .map { "$backups/$it" }
    }

    /** A staging file left by a crash holds a decrypted budget, so it goes before anything new is written. */
    private fun removeStaleStaging() {
        files
            .contentsOfDirectoryAtPath(root, null)
            .orEmpty()
            .filterIsInstance<String>()
            .filter { it.contains(STAGING) }
            .forEach { delete("$root/$it") }
    }

    private fun deleteRetired(copy: String) {
        deleteWithSidecars(copy)
        delete("$copy.key")
    }

    private fun deleteWithSidecars(path: String) = SIDECARS.forEach { delete(path + it) }

    private fun delete(path: String) {
        if (files.fileExistsAtPath(path)) files.removeItemAtPath(path, error = null)
    }

    private fun writeSynced(
        path: String,
        bytes: ByteArray,
        failure: String,
    ) {
        val output = checkNotNull(fopen(path, "wb")) { failure }
        try {
            val length = bytes.size.toULong()
            val written = if (bytes.isEmpty()) 0uL else bytes.usePinned { fwrite(it.addressOf(0), 1uL, length, output) }
            check(written == length && fflush(output) == 0 && fsync(fileno(output)) == 0) { failure }
        } finally {
            fclose(output)
        }
    }

    private fun readFile(path: String): ByteArray? {
        val input = fopen(path, "rb") ?: return null
        try {
            fseek(input, 0, SEEK_END)
            val size = ftell(input)
            fseek(input, 0, SEEK_SET)
            val bytes = ByteArray(size.toInt())
            if (size > 0) {
                val read = bytes.usePinned { fread(it.addressOf(0), 1uL, size.toULong(), input) }
                check(read == size.toULong()) { "Could not read the budget copy." }
            }
            return bytes
        } finally {
            fclose(input)
        }
    }

    private fun <T> locked(block: () -> T): T {
        budgetFilesLock.lock()
        try {
            return block()
        } finally {
            budgetFilesLock.unlock()
        }
    }

    private companion object {
        const val STAGING = ".staging"
        val SIDECARS = listOf("", "-wal", "-shm", "-journal")
    }
}
