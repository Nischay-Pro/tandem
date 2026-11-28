@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.security.LocalDeviceAuthenticator
import app.tandemforactual.ui.core.security.LocalEncryptedBudgetVault
import app.tandemforactual.ui.core.security.budgetPasswordSecretId
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.unlockSecret
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.unsentWrites
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.AnimatedPreviewNote
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.templates.ToggleRow
import app.tandemforactual.ui.design.theme.tandemPalette

@Composable
internal fun SecurityScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
) {
    val hasPendingWrites = mobileSession.pendingWrites.unsentWrites().isNotEmpty()
    val canChangeReadOnly = state.readOnlyMode || !hasPendingWrites
    PageColumn {
        SectionCard("App access") {
            BiometricUnlockToggle(state)
            DividerLine()
            ToggleRow(
                title = "Lock editing",
                subtitle =
                    if (hasPendingWrites && !state.readOnlyMode) "Sync pending edits first" else "Blocks budget changes on this device",
                checked = state.readOnlyMode,
                enabled = canChangeReadOnly,
                onChange = { state.readOnlyMode = it },
            )
        }
        SectionCard("Privacy") {
            ToggleRow(
                title = "Hide budget amounts",
                subtitle = "Tap an amount to show it briefly",
                checked = state.privacyModeEnabled,
                onChange = { state.privacyModeEnabled = it },
            )
            DividerLine()
            ToggleRow(
                title = "Two-finger double tap",
                subtitle = "Turns Hide budget amounts on or off from any screen",
                checked = state.hideAmountsGesture,
                onChange = { state.hideAmountsGesture = it },
            )
            DividerLine()
            ListRow(
                "Encrypted budget",
                when {
                    !state.selectedBudgetEncrypted -> "Not used by this budget"
                    state.encryptedPassword.isNotBlank() -> "Password forgotten when Tandem closes"
                    else -> "Enter the password to open it"
                },
                when {
                    !state.selectedBudgetEncrypted -> null
                    state.encryptedPassword.isNotBlank() -> "Unlocked"
                    else -> "Locked"
                },
                leading = RowLeading.None,
                options =
                    ListRowOptions(
                        showChevron = false,
                        subtitleMaxLines = 1,
                        trailingColor = if (state.encryptedPassword.isNotBlank()) tandemPalette.positive else tandemPalette.warning,
                    ),
            )
        }
        AnimatedPreviewNote(mobileSession.credentialStorageError)
    }
}

/** Turns biometric unlock on or off, confirming with the device first. */
@Composable
private fun BiometricUnlockToggle(state: AppState) {
    val authenticator = LocalDeviceAuthenticator.current
    val vault = LocalEncryptedBudgetVault.current
    val biometricAvailable = remember(authenticator) { authenticator?.isAvailable() == true }
    ToggleRow(
        title = "Biometric unlock",
        subtitle =
            when {
                authenticator == null -> "Not available on this device"
                !biometricAvailable -> "Set up a screen lock on this device first"
                state.selectedBudgetEncrypted -> "Unlock your encrypted budget with biometrics"
                else -> "Fingerprint, face, or screen lock"
            },
        checked = state.biometricLockEnabled,
        enabled = biometricAvailable || state.biometricLockEnabled,
        onChange = { enable ->
            val secretId = budgetPasswordSecretId(state.selectedBudgetId)
            val password = state.encryptedPassword
            when {
                authenticator == null -> if (!enable) state.biometricLockEnabled = false
                !enable ->
                    authenticator.authenticate("Turn off biometric unlock") { success ->
                        if (success) {
                            authenticator.deleteAllSecrets()
                            state.biometricLockEnabled = false
                        }
                    }
                state.selectedBudgetEncrypted && password.isNotBlank() && authenticator.canStoreSecrets() ->
                    authenticator.storeSecret(
                        secretId,
                        unlockSecret(vault, state.selectedBudgetId, password),
                        "Turn on biometric unlock",
                    ) { stored ->
                        if (stored) state.biometricLockEnabled = true
                    }
                else ->
                    authenticator.authenticate("Turn on biometric unlock") { success ->
                        if (success) state.biometricLockEnabled = true
                    }
            }
        },
    )
}
