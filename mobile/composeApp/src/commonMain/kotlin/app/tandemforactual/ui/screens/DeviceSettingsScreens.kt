@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import app.tandemforactual.ui.components.DividerLine
import app.tandemforactual.ui.components.ListRow
import app.tandemforactual.ui.components.ListRowOptions
import app.tandemforactual.ui.components.RowLeading
import app.tandemforactual.ui.components.SecondaryButton
import app.tandemforactual.ui.components.SectionCard
import com.composables.icons.lucide.FingerprintPattern
import com.composables.icons.lucide.FolderKey
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.LockKeyhole
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Server
import com.composables.icons.lucide.Smartphone
import com.composables.icons.lucide.Wifi
import kotlinx.coroutines.flow.first

@Composable
internal fun ServerDeviceScreen(
    state: AppState,
    deviceName: String,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    onForgetDevice: () -> Unit,
    onForgetDeviceLocally: () -> Unit,
) {
    val device = mobileSession.pairedDevice
    PageColumn {
        Heading("Connection details", "Review the server address and device pairing.")
        SectionCard(
            "Server",
            when {
                device == null -> "No device pairing"
                mobileSession.isCredentialStored -> "Paired on this phone"
                else -> "Paired for this app session"
            },
        ) {
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
                device == null -> "No device is paired"
                mobileSession.isCredentialStored -> "Pairing is saved on this phone"
                else -> "Pairing is active until the app closes"
            },
        ) {
            ListRow(
                "Device name",
                if (device != null) deviceName else "Not paired",
                leading = RowLeading.Icon(Lucide.Smartphone),
            )
            DividerLine()
            ListRow(
                "Budgets this device can open",
                if (device == null) "Not paired" else "${device.budgetIds.size} available",
                leading = RowLeading.Icon(Lucide.FolderKey),
            )
            DividerLine()
            ListRow(
                "Saved pairing",
                if (mobileSession.isCredentialStored) "Protected on this phone" else "Available until the app closes",
                when {
                    mobileSession.isCredentialStored -> "Saved"
                    device != null -> "Active"
                    else -> "None"
                },
                leading = RowLeading.Icon(Lucide.KeyRound),
            )
        }
        InfoBubble("Pairing codes and budget passwords are never saved.")
        mobileSession.deviceRevocationError?.let { message ->
            AttentionBanner("Couldn’t remove this device", message, "Retry", onForgetDevice)
            InfoBubble("The server still trusts this phone until its owner removes it there.")
        }
        AnimatedPreviewNote(mobileSession.credentialStorageError)
        if (device == null) {
            SecondaryButton("Pair this device") { navigation.open(AppRoute.SERVER_ADDRESS) }
        } else if (mobileSession.deviceRevocationError != null) {
            SecondaryButton(
                "Forget on this phone only",
                enabled = !mobileSession.isRevokingDevice,
                onClick = onForgetDeviceLocally,
            )
        } else {
            SecondaryButton(
                if (mobileSession.isRevokingDevice) "Removing this device" else "Remove this device",
                enabled = !mobileSession.isRevokingDevice,
                onClick = onForgetDevice,
            )
        }
    }
}

@Composable
internal fun SecurityScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
) {
    val hasActiveConnection =
        state.selectedBudgetId.isNotBlank() && mobileSession.isAuthenticated && mobileSession.isServerReachable
    val hasPendingWrites = mobileSession.pendingWrites.isNotEmpty()
    val canEnableReadOnly = hasActiveConnection && !hasPendingWrites
    val canChangeReadOnly = state.readOnlyMode || canEnableReadOnly
    PageColumn {
        Heading("Device protection")
        SectionCard("App access") {
            ListRow(
                "Biometric unlock",
                "Not available yet",
                "Unavailable",
                leading = RowLeading.Icon(Lucide.FingerprintPattern),
            )
            DividerLine()
            CheckBoxRow(
                title = "Read-only mode",
                subtitle =
                    when {
                        canChangeReadOnly -> "Block transaction edits and notes"
                        !mobileSession.isAuthenticated -> "Pair this device first"
                        !mobileSession.isServerReachable -> "Connect to the server first"
                        else -> "Sync pending edits first"
                    },
                checked = state.readOnlyMode,
                enabled = canChangeReadOnly,
                onChange = { state.readOnlyMode = it },
            )
        }
        SectionCard("Access") {
            ListRow(
                "Saved pairing",
                if (mobileSession.isCredentialStored) "Protected on this phone" else "Available until the app closes",
                when {
                    mobileSession.isCredentialStored -> "Saved"
                    mobileSession.isAuthenticated -> "Active"
                    else -> "Not paired"
                },
                leading = RowLeading.Icon(Lucide.KeyRound),
            )
            DividerLine()
            ListRow(
                "Budget password",
                if (state.selectedBudgetEncrypted) "Used to unlock this budget" else "Encrypted budgets only",
                if (state.selectedBudgetEncrypted) "Not stored" else "Unavailable",
                leading = RowLeading.Icon(Lucide.LockKeyhole),
            )
        }
        PreviewNote("Budget passwords aren’t stored.")
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
        SectionCard("") {
            if (notificationPermissionEnabled != null && onToggleNotificationPermission != null) {
                ToggleRow(
                    "Notifications",
                    if (notificationPermissionEnabled) {
                        "On · Disable in notification settings"
                    } else {
                        "Off · Enable in notification settings"
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
                        CompactActionButton("Enable", Modifier.weight(1f), onRequestNotificationPermission)
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
                "Not applied alerts",
                "When an edit isn’t applied",
                state.rejectedEditAlerts,
            ) {
                state.rejectedEditAlerts = it
            }
        }
        InfoBubble("In-app alerts appear when you open Tandem.")
    }
}
