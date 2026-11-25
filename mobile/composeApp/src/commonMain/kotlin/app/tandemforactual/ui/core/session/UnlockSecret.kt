package app.tandemforactual.ui.core.session

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * What biometric unlock keeps for an encrypted budget: its password, and the key of the budget's local vault when it
 * is known, so unlocking with biometrics opens the vault without deriving that key from the password again. Secrets
 * saved before the key was kept hold only the password.
 */
@OptIn(ExperimentalEncodingApi::class)
internal data class UnlockSecret(
    val password: String,
    val vaultKey: ByteArray? = null,
) {
    fun encode(): String = vaultKey?.let { "$PREFIX${Base64.encode(it)}:$password" } ?: password

    override fun equals(other: Any?): Boolean =
        other is UnlockSecret && other.password == password && other.vaultKey.contentEquals(vaultKey)

    override fun hashCode(): Int = 31 * password.hashCode() + vaultKey.contentHashCode()

    companion object {
        private const val PREFIX = "tandem-unlock-v2:"

        fun decode(secret: String): UnlockSecret {
            if (!secret.startsWith(PREFIX)) return UnlockSecret(secret)
            val key = secret.removePrefix(PREFIX).substringBefore(':')
            val password = secret.removePrefix(PREFIX).substringAfter(':', "")
            return runCatching { UnlockSecret(password, Base64.decode(key)) }.getOrElse { UnlockSecret(secret) }
        }
    }
}

/** The secret to keep for [password], with the vault's key when [vault] has just opened this budget. */
internal fun unlockSecret(
    vault: EncryptedBudgetVault?,
    budgetId: String,
    password: String,
): String = UnlockSecret(password, vault?.openedKey(budgetId)).encode()
