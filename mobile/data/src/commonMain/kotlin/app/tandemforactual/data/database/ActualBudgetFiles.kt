package app.tandemforactual.data.database

import app.tandemforactual.data.actual.ActualDatabase

/**
 * The device's copy of each Actual budget, one private, backup-excluded SQLite file per budget. Where [canSeal], an
 * encrypted budget's copy is a SQLCipher database instead, so it's never readable without the budget's key.
 */
interface ActualBudgetFiles {
    fun open(budgetId: String): ActualDatabase?

    fun install(
        budgetId: String,
        bytes: ByteArray,
    ): ActualDatabase

    /** Deletes the budget's copy, its encrypted form and every retired copy of it. */
    fun remove(budgetId: String)

    /** A copy of the budget's database that [write] makes at the path it's given, read back and then deleted. */
    fun snapshot(
        budgetId: String,
        write: (path: String) -> Unit,
    ): ByteArray

    /**
     * Moves a closed budget aside instead of deleting it, so a sync reset never discards local changes. Only the newest
     * retired copy of each budget is kept.
     */
    fun retire(
        budgetId: String,
        label: String,
    )

    /** Writes [db], this budget's open copy, to [path] as a plaintext database, as a backup needs it. */
    fun exportPlain(
        budgetId: String,
        db: ActualDatabase,
        path: String,
    ) = db.execute("VACUUM INTO ?", path)

    val canSeal: Boolean get() = false

    /** What [sealBudget] saved beside an encrypted copy, or null when the copy isn't encrypted or doesn't exist. */
    fun sealedKeyInfo(budgetId: String): String? = null

    fun openSealed(
        budgetId: String,
        key: ByteArray,
    ): ActualDatabase? = null

    /** Saves downloaded [bytes] as an encrypted copy under [key], replacing any copy, with [keyInfo] beside it. */
    fun installSealed(
        budgetId: String,
        bytes: ByteArray,
        key: ByteArray,
        keyInfo: String,
    ): ActualDatabase = throw UnsupportedOperationException("Encrypted budget copies aren't supported here.")

    /** Encrypts the budget's existing, closed plaintext copy under [key], deleting the plaintext and its retired copies. */
    fun sealBudget(
        budgetId: String,
        key: ByteArray,
        keyInfo: String,
    ): Unit = throw UnsupportedOperationException("Encrypted budget copies aren't supported here.")
}

/** Budget ids become file names, so only Actual's id characters are accepted. */
internal fun budgetFileName(budgetId: String): String {
    require(Regex("^[A-Za-z0-9-]{1,100}$").matches(budgetId)) { "Invalid budget id." }
    return "$budgetId.sqlite"
}

/** SQLCipher statements for moving an Actual budget between its plaintext and encrypted forms. */
object SealedBudgetSql {
    /** SQLCipher's raw key form, which skips its own key derivation. */
    fun rawKey(key: ByteArray): String = "x'${key.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }}'"

    /** Writes the plaintext database open on [plain] into a new encrypted database at [path]. */
    fun exportSealed(
        plain: ActualDatabase,
        path: String,
        key: ByteArray,
    ) = export(plain, path, rawKey(key))

    /** Writes the encrypted database open on [sealed] into a new plaintext database at [path]. */
    fun exportPlain(
        sealed: ActualDatabase,
        path: String,
    ) = export(sealed, path, "")

    private fun export(
        source: ActualDatabase,
        path: String,
        targetKey: String,
    ) {
        source.execute("ATTACH DATABASE ? AS tandem_export KEY ?", path, targetKey)
        try {
            source.query("SELECT sqlcipher_export('tandem_export')")
            val version = source.first("PRAGMA main.user_version")?.values?.firstOrNull() as? Long ?: 0L
            source.execute("PRAGMA tandem_export.user_version = $version")
        } finally {
            source.execute("DETACH DATABASE tandem_export")
        }
    }
}
