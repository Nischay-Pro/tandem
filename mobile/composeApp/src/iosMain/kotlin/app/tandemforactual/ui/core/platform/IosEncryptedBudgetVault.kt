package app.tandemforactual.ui.core.platform

import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.database.createEncryptedTandemDatabase
import app.tandemforactual.data.database.deleteEncryptedTandemDatabase
import app.tandemforactual.data.database.encryptedTandemDatabasePath
import app.tandemforactual.data.database.unlockEncryptedTandemDatabase
import app.tandemforactual.data.inbox.InboxFile
import app.tandemforactual.ui.core.session.EncryptedBudgetVault
import app.tandemforactual.ui.core.session.PendingEncryptedBudget
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.cinterop.refTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.CoreCrypto.CCKeyDerivationPBKDF
import platform.CoreCrypto.kCCPBKDF2
import platform.CoreCrypto.kCCPRFHmacAlgSHA256
import platform.CoreCrypto.kCCSuccess
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault

/** Keeps each encrypted budget in its own SQLCipher database, keyed from the budget password. */
@OptIn(ExperimentalForeignApi::class)
class IosEncryptedBudgetVault : EncryptedBudgetVault {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun hasStore(budgetId: String): Boolean = entries().containsKey(budgetId)

    override suspend fun open(
        budgetId: String,
        password: String,
    ): TandemDatabase? =
        withContext(Dispatchers.IO) {
            val entry = entries()[budgetId] ?: return@withContext null
            unlockEncryptedTandemDatabase(encryptedTandemDatabasePath(entry.fileName), rawKey(password, entry.salt))
        }

    override suspend fun create(
        budgetId: String,
        password: String,
    ): PendingEncryptedBudget =
        withContext(Dispatchers.IO) {
            val entry = VaultEntry("budget-${NSUUID().UUIDString}.db", randomBytes(SALT_BYTES))
            val path = encryptedTandemDatabasePath(entry.fileName)
            val database = createEncryptedTandemDatabase(path, rawKey(password, entry.salt))
            object : PendingEncryptedBudget {
                override val database: TandemDatabase = database

                override suspend fun commit() {
                    val previous = entries()[budgetId]
                    save(entries() + (budgetId to entry))
                    previous?.let { deleteEncryptedTandemDatabase(encryptedTandemDatabasePath(it.fileName)) }
                }

                override suspend fun discard() {
                    deleteEncryptedTandemDatabase(path)
                }
            }
        }

    override fun inboxFile(budgetId: String): InboxFile = IosInboxFile.forBudget(budgetId)

    override fun delete(budgetId: String) {
        IosInboxFile.delete(budgetId)
        val current = entries()
        current[budgetId]?.let { deleteEncryptedTandemDatabase(encryptedTandemDatabasePath(it.fileName)) }
        save(current - budgetId)
    }

    override fun deleteAll() {
        IosInboxFile.deleteAll()
        entries().values.forEach { deleteEncryptedTandemDatabase(encryptedTandemDatabasePath(it.fileName)) }
        defaults.removeObjectForKey(VAULT_KEY)
    }

    private fun entries(): Map<String, VaultEntry> =
        (defaults.dictionaryForKey(VAULT_KEY) ?: emptyMap<Any?, Any?>())
            .entries
            .mapNotNull { (key, value) ->
                val entry = (value as? String)?.let(VaultEntry::parse)
                if (key is String && entry != null) key to entry else null
            }.toMap()

    private fun save(entries: Map<String, VaultEntry>) {
        val stored: Map<Any?, Any?> = entries.mapValues { it.value.serialize() }
        defaults.setObject(stored, forKey = VAULT_KEY)
    }

    private fun rawKey(
        password: String,
        salt: ByteArray,
    ): String {
        val passwordLength = password.encodeToByteArray().size
        val saltBytes = salt.toUByteArray()
        val key = UByteArray(KEY_BYTES)
        val status =
            CCKeyDerivationPBKDF(
                kCCPBKDF2.convert(),
                password,
                passwordLength.convert(),
                saltBytes.refTo(0),
                saltBytes.size.convert(),
                kCCPRFHmacAlgSHA256.convert(),
                PBKDF2_ITERATIONS.convert(),
                key.refTo(0),
                key.size.convert(),
            )
        check(status == kCCSuccess.toInt()) { "Couldn’t derive the budget key" }
        val hex = key.joinToString("") { it.toString(16).padStart(2, '0') }
        key.fill(0u)
        return "x'$hex'"
    }

    private fun randomBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        val status = SecRandomCopyBytes(kSecRandomDefault, size.convert(), bytes.refTo(0))
        check(status == errSecSuccess) { "Couldn’t create a random salt" }
        return bytes
    }

    private class VaultEntry(
        val fileName: String,
        val salt: ByteArray,
    ) {
        fun serialize(): String = "$fileName|${salt.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }}"

        companion object {
            fun parse(value: String): VaultEntry? {
                val parts = value.split("|").takeIf { it.size == 2 && it[1].length % 2 == 0 } ?: return null
                val salt = runCatching { parts[1].chunked(2).map { it.toInt(16).toByte() }.toByteArray() }.getOrNull() ?: return null
                return VaultEntry(parts[0], salt)
            }
        }
    }

    private companion object {
        const val VAULT_KEY = "tandem_budget_vaults"
        const val KEY_BYTES = 32
        const val SALT_BYTES = 16
        const val PBKDF2_ITERATIONS = 310_000
    }
}
