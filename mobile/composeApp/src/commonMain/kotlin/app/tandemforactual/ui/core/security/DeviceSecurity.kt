package app.tandemforactual.ui.core.security

import androidx.compose.runtime.staticCompositionLocalOf
import app.tandemforactual.ui.core.session.DeviceCredentialStore
import app.tandemforactual.ui.core.session.EncryptedBudgetVault

interface DeviceAuthenticator {
    fun isAvailable(): Boolean

    fun authenticate(
        title: String,
        onResult: (Boolean) -> Unit,
    )

    fun canStoreSecrets(): Boolean = false

    fun hasStoredSecret(id: String): Boolean = false

    fun storeSecret(
        id: String,
        secret: String,
        title: String,
        onResult: (Boolean) -> Unit,
    ) = onResult(false)

    fun readSecret(
        id: String,
        title: String,
        onResult: (String?) -> Unit,
    ) = onResult(null)

    fun deleteSecret(id: String) = Unit

    fun rememberPasswordCheck(
        id: String,
        password: String,
    ) = Unit

    fun matchesPasswordCheck(
        id: String,
        password: String,
    ): Boolean? = null

    fun forgetPasswordCheck(id: String) = Unit

    fun deleteAllSecrets() = Unit
}

internal val LocalDeviceAuthenticator = staticCompositionLocalOf<DeviceAuthenticator?> { null }

internal val LocalBackgroundBudgetKeyStore = staticCompositionLocalOf<DeviceCredentialStore?> { null }

internal val LocalEncryptedBudgetVault = staticCompositionLocalOf<EncryptedBudgetVault?> { null }

internal fun budgetPasswordSecretId(budgetId: String): String = "budget-password:$budgetId"
