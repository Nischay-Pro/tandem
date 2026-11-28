package app.tandemforactual.ui.feature.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.security.DeviceAuthenticator
import app.tandemforactual.ui.core.security.budgetPasswordSecretId
import app.tandemforactual.ui.core.session.EncryptedBudgetUnlockResult
import app.tandemforactual.ui.core.session.EncryptedBudgetVault
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.UnlockSecret
import app.tandemforactual.ui.core.session.unlockEncryptedBudget
import app.tandemforactual.ui.core.session.unlockSecret
import app.tandemforactual.ui.core.state.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the encrypted-budget unlock screen shows for one budget. */
internal class EncryptedBudgetUnlockForm(
    hasStoredPassword: Boolean,
    rememberWithBiometrics: Boolean,
    error: String?,
) {
    var hasStoredPassword by mutableStateOf(hasStoredPassword)
    var rememberWithBiometrics by mutableStateOf(rememberWithBiometrics)
    var usePassword by mutableStateOf(!hasStoredPassword)
    var password by mutableStateOf("")
    var error by mutableStateOf(error)
}

/** Whether an unlock check or a biometric prompt is in flight. */
internal class EncryptedBudgetUnlockProgress {
    var checking by mutableStateOf(false)
    var prompting by mutableStateOf(false)
}

/** Checks a candidate budget password, by the vault when there is one and by syncing otherwise. */
internal class EncryptedBudgetUnlocker(
    private val state: AppState,
    private val mobileSession: TandemMobileSession,
    private val authenticator: DeviceAuthenticator?,
    private val vault: EncryptedBudgetVault?,
    private val lockState: AppLockState,
    private val onUnlocked: () -> Unit,
    private val form: EncryptedBudgetUnlockForm,
    private val progress: EncryptedBudgetUnlockProgress,
    private val scope: CoroutineScope,
    private val canUseBiometrics: Boolean,
    private val retryAtMillis: Long,
) {
    private val budgetId = state.selectedBudgetId
    private val secretId = budgetPasswordSecretId(budgetId)

    fun submit(
        candidate: String,
        fromBiometrics: Boolean,
        vaultKey: ByteArray? = null,
    ) {
        if (candidate.isBlank() || progress.checking || mobileSession.nowEpochMillis() < retryAtMillis) return
        progress.checking = true
        form.error = null
        scope.launch {
            if (vault != null) {
                val result = mobileSession.unlockEncryptedBudget(vault, budgetId, candidate, vaultKey)
                progress.checking = false
                applyVaultResult(result, candidate, fromBiometrics)
                if (result == EncryptedBudgetUnlockResult.UNLOCKED && fromBiometrics && vaultKey == null) keepVaultKey(candidate)
                return@launch
            }
            unlockBySync(candidate, fromBiometrics)
        }
    }

    fun unlockWithBiometrics() {
        if (authenticator != null && !progress.prompting && !progress.checking) {
            progress.prompting = true
            authenticator.readSecret(secretId, "Unlock ${state.selectedBudget}") { secret ->
                progress.prompting = false
                if (secret == null) {
                    if (authenticator.hasStoredSecret(secretId).not()) {
                        form.hasStoredPassword = false
                        form.usePassword = true
                    }
                } else {
                    val saved = UnlockSecret.decode(secret)
                    submit(saved.password, fromBiometrics = true, vaultKey = saved.vaultKey)
                }
            }
        }
    }

    /** Saves the vault's key with a password kept before keys were saved, so later biometric unlocks open faster. */
    private fun keepVaultKey(password: String) {
        if (vault?.openedKey(budgetId) == null || !canUseBiometrics) return
        authenticator?.storeSecret(secretId, unlockSecret(vault, budgetId, password), "Speed up unlocking ${state.selectedBudget}") {}
    }

    private fun applyVaultResult(
        result: EncryptedBudgetUnlockResult,
        candidate: String,
        fromBiometrics: Boolean,
    ) {
        when (result) {
            EncryptedBudgetUnlockResult.UNLOCKED -> {
                rememberChoice(candidate, fromBiometrics)
            }

            EncryptedBudgetUnlockResult.WRONG_PASSWORD -> {
                rejectStoredPassword(fromBiometrics)
                form.error =
                    if (fromBiometrics) {
                        "Your saved password no longer works. It may have changed in Actual. Enter the current password."
                    } else {
                        "That password didn’t unlock this budget. It may have changed in Actual."
                    }
            }

            EncryptedBudgetUnlockResult.TOO_MANY_ATTEMPTS -> {
                form.error = "Too many incorrect passwords. Try again in a few minutes."
            }

            EncryptedBudgetUnlockResult.NEEDS_CONNECTION -> {
                form.error = "Connect to the internet to unlock this budget for the first time."
            }

            EncryptedBudgetUnlockResult.FAILED -> {
                rejectStoredPassword(fromBiometrics)
                form.error = "Actual didn’t accept that password. Check it and try again."
            }
        }
    }

    private suspend fun unlockBySync(
        candidate: String,
        fromBiometrics: Boolean,
    ) {
        mobileSession.synchronize(budgetId = budgetId, encryptionPassword = candidate)
        val synced = mobileSession.syncComplete
        val offlineCheck =
            if (!synced && !mobileSession.isServerReachable) {
                withContext(Dispatchers.Default) { authenticator?.matchesPasswordCheck(secretId, candidate) }
            } else {
                null
            }
        if (synced) withContext(Dispatchers.Default) { authenticator?.rememberPasswordCheck(secretId, candidate) }
        if (mobileSession.budgetPasswordRejection == "budget_password_incorrect") authenticator?.forgetPasswordCheck(secretId)
        progress.checking = false
        if (!encryptedBudgetUnlockAccepted(synced, mobileSession.isServerReachable, offlineCheck)) {
            if (!mobileSession.isServerReachable && offlineCheck == null) {
                form.error = "Connect to the internet to unlock this budget for the first time."
                return
            }
            rejectStoredPassword(fromBiometrics)
            form.error = "That password didn’t unlock this budget. It may have changed in Actual."
            return
        }
        rememberChoice(candidate, fromBiometrics)
    }

    private fun unlockWith(candidate: String) {
        state.unlockFailureCount = 0
        state.unlockRetryAtEpochMillis = 0L
        state.encryptedPassword = candidate
        lockState.unlock()
        onUnlocked()
    }

    private fun rememberChoice(
        candidate: String,
        fromBiometrics: Boolean,
    ) {
        when {
            fromBiometrics || authenticator == null -> {
                state.biometricLockEnabled = biometricUnlockAfterUnlock(state.biometricLockEnabled, fromBiometrics, kept = null)
                unlockWith(candidate)
            }

            form.rememberWithBiometrics && canUseBiometrics -> {
                authenticator.storeSecret(
                    secretId,
                    unlockSecret(vault, budgetId, candidate),
                    "Use biometrics for ${state.selectedBudget}",
                ) { stored ->
                    state.biometricLockEnabled = biometricUnlockAfterUnlock(state.biometricLockEnabled, false, kept = stored.takeIf { it })
                    unlockWith(candidate)
                }
            }

            else -> {
                authenticator.deleteSecret(secretId)
                state.biometricLockEnabled = biometricUnlockAfterUnlock(state.biometricLockEnabled, false, kept = false)
                unlockWith(candidate)
            }
        }
    }

    private fun rejectStoredPassword(fromBiometrics: Boolean) {
        state.unlockFailureCount += 1
        unlockWaitMillis(state.unlockFailureCount).takeIf { it > 0 }?.let {
            state.unlockRetryAtEpochMillis = mobileSession.nowEpochMillis() + it
        }
        if (fromBiometrics) {
            authenticator?.deleteSecret(secretId)
            form.hasStoredPassword = false
            form.usePassword = true
        }
    }
}

/**
 * The Biometric unlock setting after an encrypted budget unlocks: on when biometrics unlocked it or its password was
 * just kept behind biometrics, off when the person chose not to keep it, otherwise unchanged.
 */
internal fun biometricUnlockAfterUnlock(
    current: Boolean,
    fromBiometrics: Boolean,
    kept: Boolean?,
): Boolean = fromBiometrics || (kept ?: current)
