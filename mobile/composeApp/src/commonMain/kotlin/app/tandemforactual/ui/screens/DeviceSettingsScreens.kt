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
import app.tandemforactual.ui.components.SecondaryButton
import app.tandemforactual.ui.components.SectionCard
import app.tandemforactual.ui.components.SurfaceCard
import com.composables.icons.lucide.FingerprintPattern
import com.composables.icons.lucide.FolderKey
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.LockKeyhole
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Server
import com.composables.icons.lucide.Smartphone
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
            AttentionBanner("Couldn’t remove this device", message, "Try again", onForgetDevice)
            InfoBubble("The server still trusts this device until its owner removes it there.")
        }
        AnimatedPreviewNote(mobileSession.credentialStorageError)
        InfoBubble("Sign out clears all cached budgets from this device. Your budgets on the server stay unchanged.")
        SecondaryButton(
            if (mobileSession.isSigningOut) "Signing out" else "Sign out and clear data",
            enabled = !mobileSession.isSigningOut,
            onClick = { confirmation = "signout" },
        )
        if (device == null) {
            SecondaryButton("Sign in to this server") { navigation.open(AppRoute.SERVER_ADDRESS) }
        } else if (mobileSession.deviceRevocationError != null) {
            SecondaryButton(
                "Forget on this device only",
                enabled = !mobileSession.isRevokingDevice,
                onClick = { confirmation = "local" },
            )
        } else {
            SecondaryButton(
                if (mobileSession.isRevokingDevice) "Removing this device" else "Remove this device",
                enabled = !mobileSession.isRevokingDevice,
                onClick = { confirmation = "server" },
            )
        }
    }
    when (confirmation) {
        "signout" ->
            DestructiveConfirmationDialog(
                title = "Sign out and clear data?",
                message =
                    "This removes saved access and every cached budget from this device. Your budgets on the server will stay unchanged. Pending changes must be synced or resolved first.",
                confirmLabel = "Sign out and clear data",
                dismissLabel = "Cancel",
                enabled = !mobileSession.isSigningOut,
                onDismissRequest = { if (!mobileSession.isSigningOut) confirmation = "" },
                onConfirm = {
                    confirmation = ""
                    scope.launch {
                        if (mobileSession.signOutAndClearLocalData()) {
                            state.clearBudgetSession()
                            navigation.reset(AppRoute.WELCOME)
                        }
                    }
                },
            )
        "server" ->
            DestructiveConfirmationDialog(
                title = "Remove this device?",
                message =
                    "This revokes this device’s access on the server and clears its saved sign-in. You’ll need to sign in again to connect.",
                confirmLabel = "Remove device",
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
                title = "Forget this device here?",
                message =
                    "This clears the sign-in saved on this device. The server will still allow this device until it is removed there.",
                confirmLabel = "Forget here",
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
            ListRow("Biometric unlock", "Coming later", leading = RowLeading.Icon(Lucide.FingerprintPattern))
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
        SurfaceCard {
            ListRow(
                "Budget password",
                trailing = if (state.selectedBudgetEncrypted) "Available this session" else "Unavailable",
                leading = RowLeading.Icon(Lucide.LockKeyhole),
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
        SectionCard("Alert preferences") {
            ToggleRow(
                "Sync reminders",
                "When background sync fails",
                state.syncReminders,
            ) {
                state.syncReminders = it
            }
            DividerLine()
            ToggleRow(
                "Review alerts",
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
        InfoBubble("In-app alerts appear when you open Tandem.")
    }
}
