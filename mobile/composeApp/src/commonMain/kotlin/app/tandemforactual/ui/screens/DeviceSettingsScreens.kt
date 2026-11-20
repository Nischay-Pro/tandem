@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import app.tandemforactual.ui.components.DestructiveConfirmationDialog
import app.tandemforactual.ui.components.DividerLine
import app.tandemforactual.ui.components.ListRow
import app.tandemforactual.ui.components.ListRowOptions
import app.tandemforactual.ui.components.RowLeading
import app.tandemforactual.ui.components.SectionCard
import app.tandemforactual.ui.components.SurfaceCard
import com.composables.icons.lucide.FolderKey
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.LogIn
import com.composables.icons.lucide.LogOut
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Server
import com.composables.icons.lucide.Smartphone
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Wifi
import kotlinx.coroutines.launch

@Composable
internal fun ServerDeviceScreen(
    state: AppState,
    deviceName: String,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    onForgetDevice: () -> Unit,
    onForgetDeviceLocally: () -> Unit,
) {
    val encryptedBudgetVault = LocalEncryptedBudgetVault.current
    val device = mobileSession.authenticatedDevice
    val scope = rememberCoroutineScope()
    var confirmation by rememberSaveable { mutableStateOf("") }
    PageColumn {
        SectionCard("Server") {
            ListRow(
                "Server URL",
                state.serverUrl,
                if (mobileSession.isServerReachable) "Connected" else "Disconnected",
                leading = RowLeading.Icon(Lucide.Server),
                options =
                    ListRowOptions(
                        trailingColor = if (mobileSession.isServerReachable) tandemPalette.positive else tandemPalette.negative,
                    ),
                onClick = { navigation.open(AppRoute.SERVER_ADDRESS) },
            )
            DividerLine()
            ListRow(
                "Connection status",
                if (mobileSession.isServerReachable) "Server is reachable" else "Server can’t be reached",
                if (mobileSession.isServerReachable) "Connected" else "Disconnected",
                leading = RowLeading.Icon(Lucide.Wifi),
                options =
                    ListRowOptions(
                        trailingColor = if (mobileSession.isServerReachable) tandemPalette.positive else tandemPalette.negative,
                    ),
            )
        }
        SectionCard(
            "This device",
            when {
                device == null -> "No device is signed in"
                else -> null
            },
        ) {
            ListRow(
                "Device name",
                if (device != null) deviceName else "Not signed in",
                leading = RowLeading.Icon(Lucide.Smartphone),
            )
            DividerLine()
            ListRow(
                "Budgets this device can open",
                if (device == null) "Not signed in" else "${device.budgetIds.size} available",
                leading = RowLeading.Icon(Lucide.FolderKey),
            )
            DividerLine()
            ListRow(
                "Saved sign-in",
                if (mobileSession.isCredentialStored) "Protected on this device" else "Available until the app closes",
                when {
                    mobileSession.isCredentialStored -> "Saved"
                    device != null -> "Active"
                    else -> "None"
                },
                leading = RowLeading.Icon(Lucide.KeyRound),
            )
        }
        InfoBubble("Passwords are used to sign in and aren’t saved on this device.")
        if (mobileSession.signOutError != null) {
            InfoBubble(mobileSession.signOutError.orEmpty())
        }
        mobileSession.deviceRevocationError?.let { message ->
            AttentionBanner("Couldn’t unlink this device", message, "Try again", onForgetDevice)
        }
        AnimatedPreviewNote(mobileSession.credentialStorageError)
        SectionCard("Unlink", "Your budgets in Actual are never changed.") {
            when {
                device == null ->
                    ListRow(
                        "Sign in to this server",
                        "Link this device to Tandem again",
                        leading = RowLeading.Icon(Lucide.LogIn),
                        onClick = { navigation.open(AppRoute.SERVER_ADDRESS) },
                    )
                mobileSession.deviceRevocationError != null ->
                    ListRow(
                        "Unlink on this device only",
                        "Tandem couldn’t be reached. The server keeps this device linked until you unlink it from another device.",
                        leading = RowLeading.Icon(Lucide.LogOut),
                        options = ListRowOptions(muted = mobileSession.isRevokingDevice),
                        onClick = { if (!mobileSession.isRevokingDevice) confirmation = "local" },
                    )
                else ->
                    ListRow(
                        if (mobileSession.isRevokingDevice) "Unlinking…" else "Unlink this device",
                        "Signs out. Budgets and unsynced edits stay on this device for when you link again.",
                        leading = RowLeading.Icon(Lucide.LogOut),
                        options = ListRowOptions(muted = mobileSession.isRevokingDevice),
                        onClick = { if (!mobileSession.isRevokingDevice) confirmation = "server" },
                    )
            }
            DividerLine()
            ListRow(
                if (mobileSession.isSigningOut) "Erasing…" else "Unlink and erase data",
                "Signs out and deletes every saved budget from this device. Edits must be synced first.",
                leading = RowLeading.Icon(Lucide.Trash2),
                options = ListRowOptions(muted = mobileSession.isSigningOut, trailingColor = tandemPalette.negative),
                onClick = { if (!mobileSession.isSigningOut) confirmation = "signout" },
            )
        }
    }
    when (confirmation) {
        "signout" ->
            DestructiveConfirmationDialog(
                title = "Unlink and erase data?",
                message =
                    "This signs out and deletes every saved budget from this device. Your budgets in Actual stay unchanged. Edits waiting to sync must be synced or resolved first.",
                confirmLabel = "Unlink and erase",
                dismissLabel = "Cancel",
                enabled = !mobileSession.isSigningOut,
                onDismissRequest = { if (!mobileSession.isSigningOut) confirmation = "" },
                onConfirm = {
                    confirmation = ""
                    scope.launch {
                        if (mobileSession.signOutAndClearLocalData()) {
                            encryptedBudgetVault?.deleteAll()
                            state.clearBudgetSession()
                            navigation.reset(AppRoute.WELCOME)
                        }
                    }
                },
            )
        "server" ->
            DestructiveConfirmationDialog(
                title = "Unlink this device?",
                message =
                    "Tandem will stop trusting this device and you’ll need to sign in again to link it. Your saved budgets stay on this device. ${pendingDeviceChangeNotice()}",
                confirmLabel = "Unlink",
                dismissLabel = "Cancel",
                enabled = !mobileSession.isRevokingDevice,
                onDismissRequest = { if (!mobileSession.isRevokingDevice) confirmation = "" },
                onConfirm = {
                    confirmation = ""
                    onForgetDevice()
                },
            )
        "local" ->
            DestructiveConfirmationDialog(
                title = "Unlink on this device only?",
                message =
                    "This clears the sign-in saved on this device. The server keeps it linked until you unlink it from another signed-in device. ${pendingDeviceChangeNotice()}",
                confirmLabel = "Unlink here",
                dismissLabel = "Cancel",
                onDismissRequest = { confirmation = "" },
                onConfirm = {
                    confirmation = ""
                    onForgetDeviceLocally()
                },
            )
    }
}

@Composable
internal fun SecurityScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
) {
    val hasPendingWrites = mobileSession.pendingWrites.isNotEmpty()
    val canChangeReadOnly = state.readOnlyMode || !hasPendingWrites
    PageColumn {
        SectionCard("App access") {
            val authenticator = LocalDeviceAuthenticator.current
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
                            authenticator.storeSecret(secretId, password, "Turn on biometric unlock") { stored ->
                                if (stored) state.biometricLockEnabled = true
                            }
                        else ->
                            authenticator.authenticate("Turn on biometric unlock") { success ->
                                if (success) state.biometricLockEnabled = true
                            }
                    }
                },
            )
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

@Composable
internal fun NotificationsScreen(
    state: AppState,
    onRequestNotificationPermission: (() -> Unit)?,
    onOpenNotificationSettings: (() -> Unit)?,
    notificationPermissionEnabled: Boolean?,
    onToggleNotificationPermission: ((Boolean) -> Unit)?,
) {
    PageColumn {
        SurfaceCard {
            if (notificationPermissionEnabled != null && onToggleNotificationPermission != null) {
                ToggleRow(
                    "Allow notifications",
                    if (notificationPermissionEnabled) {
                        "On · Turn off in notification settings"
                    } else {
                        "Off · Turn on in notification settings"
                    },
                    notificationPermissionEnabled,
                    onChange = onToggleNotificationPermission,
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                ) {
                    Text(
                        "Notifications",
                        modifier = Modifier.weight(1f),
                        color = tandemPalette.text,
                        fontSize = TandemTypography.compactBody,
                        fontWeight = FontWeight.Medium,
                    )
                    if (onRequestNotificationPermission != null) {
                        CompactActionButton("Turn on", Modifier.weight(1f), onRequestNotificationPermission)
                    }
                    if (onOpenNotificationSettings != null) {
                        CompactActionButton("Settings", Modifier.weight(1f), onOpenNotificationSettings)
                    }
                }
            }
        }
        SectionCard("Phone notifications") {
            ToggleRow(
                "Sync reminders",
                "When background sync fails",
                state.syncReminders,
            ) {
                state.syncReminders = it
            }
            DividerLine()
            ToggleRow(
                "Needs review alerts",
                "When a change needs review",
                state.conflictReviewAlerts,
            ) {
                state.conflictReviewAlerts =
                    it
            }
            DividerLine()
            ToggleRow(
                "Rejected edit alerts",
                "When Actual rejects an edit",
                state.rejectedEditAlerts,
            ) {
                state.rejectedEditAlerts = it
            }
        }
        InfoBubble("In-app banners always show, even when phone notifications are off.")
    }
}
