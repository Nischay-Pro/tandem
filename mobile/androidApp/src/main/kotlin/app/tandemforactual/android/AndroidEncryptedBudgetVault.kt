package app.tandemforactual.android

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.database.openTandemDatabase
import app.tandemforactual.data.database.tandemDatabaseBuilder
import app.tandemforactual.ui.core.session.EncryptedBudgetVault
import app.tandemforactual.ui.core.session.PendingEncryptedBudget
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.zetetic.database.sqlcipher.driver.SQLCipherDriver

class AndroidEncryptedBudgetVault(
    context: Context,
) : EncryptedBudgetVault {
    private val appContext = context.applicationContext
    private val entries by lazy { appContext.getSharedPreferences(VAULT_FILE, Context.MODE_PRIVATE) }
    private val opened = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()

    init {
        System.loadLibrary("sqlcipher")
    }

    override fun hasStore(budgetId: String): Boolean = entries.contains(budgetId)

    override suspend fun open(
        budgetId: String,
        password: String,
    ): TandemDatabase? = open(budgetId, password, null)

    override suspend fun open(
        budgetId: String,
        password: String,
        vaultKey: ByteArray?,
    ): TandemDatabase? =
        withContext(Dispatchers.Default) {
            val entry = entries.getString(budgetId, null)?.let(VaultEntry::parse) ?: return@withContext null
            if (!appContext.getDatabasePath(entry.fileName).exists()) return@withContext null
            val databaseKey = vaultKey?.takeIf { keyOpens(entry.fileName, it) } ?: unwrap(entry, password) ?: return@withContext null
            opened[budgetId] = databaseKey
            openDatabase(entry.fileName, databaseKey)
        }

    override fun openedKey(budgetId: String): ByteArray? = opened[budgetId]?.copyOf()

    override suspend fun create(
        budgetId: String,
        password: String,
    ): PendingEncryptedBudget =
        withContext(Dispatchers.Default) {
            val databaseKey = randomBytes(KEY_BYTES)
            val salt = randomBytes(SALT_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, passwordKey(password, salt)) }
            val entry =
                VaultEntry(
                    fileName = "budget-${UUID.randomUUID()}.db",
                    salt = salt,
                    iv = cipher.iv,
                    wrappedKey = cipher.doFinal(databaseKey),
                )
            val database = openDatabase(entry.fileName, databaseKey)
            opened[budgetId] = databaseKey
            object : PendingEncryptedBudget {
                override val database: TandemDatabase = database

                override suspend fun commit() {
                    val previous = entries.getString(budgetId, null)?.let(VaultEntry::parse)
                    entries.edit(commit = true) { putString(budgetId, entry.serialize()) }
                    previous?.let { deleteDatabaseFiles(it.fileName) }
                }

                override suspend fun discard() {
                    opened.remove(budgetId)
                    deleteDatabaseFiles(entry.fileName)
                }
            }
        }

    override fun delete(budgetId: String) {
        opened.remove(budgetId)
        entries.getString(budgetId, null)?.let(VaultEntry::parse)?.let { deleteDatabaseFiles(it.fileName) }
        entries.edit(commit = true) { remove(budgetId) }
    }

    override fun deleteAll() {
        opened.clear()
        entries.all.values
            .mapNotNull { (it as? String)?.let(VaultEntry::parse) }
            .forEach { deleteDatabaseFiles(it.fileName) }
        entries.edit(commit = true) { clear() }
    }

    private fun openDatabase(
        fileName: String,
        databaseKey: ByteArray,
    ): TandemDatabase = openTandemDatabase(tandemDatabaseBuilder(appContext, fileName), SQLCipherDriver(rawKey(databaseKey), null, null))

    /** Whether [databaseKey] still opens the vault's database, checked by reading its schema before anything is built on it. */
    private fun keyOpens(
        fileName: String,
        databaseKey: ByteArray,
    ): Boolean =
        runCatching {
            val connection = SQLCipherDriver(rawKey(databaseKey), null, null).open(appContext.getDatabasePath(fileName).path)
            try {
                val statement = connection.prepare("SELECT count(*) FROM sqlite_master")
                try {
                    statement.step()
                } finally {
                    statement.close()
                }
            } finally {
                connection.close()
            }
        }.getOrDefault(false)

    private fun rawKey(databaseKey: ByteArray) = "x'${databaseKey.joinToString("") { "%02x".format(it) }}'".encodeToByteArray()

    private fun unwrap(
        entry: VaultEntry,
        password: String,
    ): ByteArray? =
        runCatching {
            Cipher
                .getInstance(TRANSFORMATION)
                .apply { init(Cipher.DECRYPT_MODE, passwordKey(password, entry.salt), GCMParameterSpec(GCM_TAG_BITS, entry.iv)) }
                .doFinal(entry.wrappedKey)
        }.getOrNull()

    private fun passwordKey(
        password: String,
        salt: ByteArray,
    ): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BYTES * 8)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun deleteDatabaseFiles(fileName: String) {
        val file = appContext.getDatabasePath(fileName)
        listOf("", "-wal", "-shm", "-journal").forEach { suffix -> java.io.File(file.path + suffix).delete() }
    }

    private fun randomBytes(size: Int) = ByteArray(size).also { SecureRandom().nextBytes(it) }

    private class VaultEntry(
        val fileName: String,
        val salt: ByteArray,
        val iv: ByteArray,
        val wrappedKey: ByteArray,
    ) {
        fun serialize(): String = listOf(fileName, encode(salt), encode(iv), encode(wrappedKey)).joinToString("|")

        companion object {
            fun parse(value: String): VaultEntry? {
                val parts = value.split("|").takeIf { it.size == 4 } ?: return null
                return runCatching { VaultEntry(parts[0], decode(parts[1]), decode(parts[2]), decode(parts[3])) }.getOrNull()
            }
        }
    }

    private companion object {
        const val VAULT_FILE = "tandem_budget_vaults"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val KEY_BYTES = 32
        const val SALT_BYTES = 16
        const val PBKDF2_ITERATIONS = 310_000

        fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

        fun decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)
    }
}
