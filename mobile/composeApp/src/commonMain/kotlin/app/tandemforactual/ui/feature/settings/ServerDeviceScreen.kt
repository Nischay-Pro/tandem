@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.settings
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.tandemforactual.api.client.AuthenticatedDevice
import app.tandemforactual.ui.core.security.LocalEncryptedBudgetVault
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.pendingDeviceChangeNotice
import app.tandemforactual.ui.core.session.signOutAndClearLocalData
import app.tandemforactual.ui.core.session.signOutBlocker
import app.tandemforactual.ui.core.session.unsyncedPlanningRecords
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.sheets.DestructiveConfirmationDialog
import app.tandemforactual.ui.design.templates.AnimatedPreviewNote
import app.tandemforactual.ui.design.templates.AttentionBanner
import app.tandemforactual.ui.design.templates.InfoBubble
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.FolderKey
import com.composables.icons.lucide.House
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
        ServerSection(state, mobileSession, navigation)
        ThisDeviceSection(mobileSession, device, deviceName)
        InfoBubble("Passwords are used to sign in and aren’t saved on this device.")
        if (mobileSession.signOutError != null) {
            InfoBubble(mobileSession.signOutError.orEmpty())
        }
        mobileSession.deviceRevocationError?.let { message ->
            AttentionBanner("Couldn’t sign out", message, "Try again", onForgetDevice)
        }
        AnimatedPreviewNote(mobileSession.credentialStorageError)
        SignOutSection(mobileSession, device, navigation) { confirmation = it }
    }
    ServerDeviceConfirmation(
        confirmation,
        mobileSession,
        openBudgetId = state.selectedBudgetId,
        onDismiss = { confirmation = "" },
        onBackUpFirst = {
            confirmation = ""
            navigation.open(AppRoute.EXPORT)
        },
        onSignOut = {
            scope.launch {
                if (mobileSession.signOutAndClearLocalData()) {
                    encryptedBudgetVault?.deleteAll()
                    state.clearBudgetSession()
                    navigation.reset(AppRoute.WELCOME)
                }
            }
        },
        onForgetDevice = onForgetDevice,
        onForgetDeviceLocally = onForgetDeviceLocally,
    )
}

/** The server address, the optional local address and whether the server answers. */
@Composable
private fun ServerSection(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
) {
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
            "Local address",
            state.localServerUrl.ifBlank { "Optional · faster on your home network" },
            when {
                state.localServerUrl.isBlank() -> null
                mobileSession.isUsingLocalServer -> "In use"
                else -> "Not in use"
            },
            leading = RowLeading.Icon(Lucide.House),
            options =
                ListRowOptions(
                    trailingColor = if (mobileSession.isUsingLocalServer) tandemPalette.positive else tandemPalette.muted,
                ),
            onClick = { navigation.open(AppRoute.LOCAL_SERVER_ADDRESS) },
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
}

/** This device's name, the budgets it can open and how its sign-in is kept. */
@Composable
private fun ThisDeviceSection(
    mobileSession: TandemMobileSession,
    device: AuthenticatedDevice?,
    deviceName: String,
) {
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
}

/** Sign in again, sign out, or sign out and erase this device's data. */
@Composable
private fun SignOutSection(
    mobileSession: TandemMobileSession,
    device: AuthenticatedDevice?,
    navigation: AppNavigation,
    onConfirm: (String) -> Unit,
) {
    SectionCard("Sign out", "Your budgets in Actual are never changed.") {
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
                    "Sign out on this device only",
                    "Tandem couldn’t be reached. The server keeps this device linked until you unlink it from another device.",
                    leading = RowLeading.Icon(Lucide.LogOut),
                    options = ListRowOptions(muted = mobileSession.isRevokingDevice),
                    onClick = { if (!mobileSession.isRevokingDevice) onConfirm("local") },
                )
            else ->
                ListRow(
                    if (mobileSession.isRevokingDevice) "Signing out…" else "Sign out",
                    "Unlinks this device. Budgets and unsynced edits stay here for when you sign in again.",
                    leading = RowLeading.Icon(Lucide.LogOut),
                    options = ListRowOptions(muted = mobileSession.isRevokingDevice),
                    onClick = { if (!mobileSession.isRevokingDevice) onConfirm("server") },
                )
        }
        DividerLine()
        ListRow(
            if (mobileSession.isSigningOut) "Erasing…" else "Sign out and erase data",
            "Signs out and deletes every saved budget from this device. Edits must be synced first.",
            leading = RowLeading.Icon(Lucide.Trash2),
            options = ListRowOptions(muted = mobileSession.isSigningOut, trailingColor = tandemPalette.negative),
            onClick = { if (!mobileSession.isSigningOut) onConfirm("signout") },
        )
    }
}

/** The confirmation dialog for whichever sign-out option was chosen. */
@Composable
private fun ServerDeviceConfirmation(
    confirmation: String,
    mobileSession: TandemMobileSession,
    openBudgetId: String,
    onDismiss: () -> Unit,
    onBackUpFirst: () -> Unit,
    onSignOut: () -> Unit,
    onForgetDevice: () -> Unit,
    onForgetDeviceLocally: () -> Unit,
) {
    when (confirmation) {
        "signout" -> EraseDataConfirmation(mobileSession, openBudgetId, onDismiss, onBackUpFirst, onSignOut)
        "server" ->
            DestructiveConfirmationDialog(
                title = "Sign out?",
                message =
                    "Tandem will stop trusting this device and you’ll need to sign in again to link it. Your saved budgets stay on this device. ${pendingDeviceChangeNotice()}",
                confirmLabel = "Sign out",
                dismissLabel = "Cancel",
                enabled = !mobileSession.isRevokingDevice,
                dismissEnabled = !mobileSession.isRevokingDevice,
                onDismissRequest = { if (!mobileSession.isRevokingDevice) onDismiss() },
                onConfirm = {
                    onDismiss()
                    onForgetDevice()
                },
            )
        "local" ->
            DestructiveConfirmationDialog(
                title = "Sign out on this device only?",
                message =
                    "This clears the sign-in saved on this device. The server keeps it linked until you unlink it from another signed-in device. ${pendingDeviceChangeNotice()}",
                confirmLabel = "Sign out here",
                dismissLabel = "Cancel",
                onDismissRequest = onDismiss,
                onConfirm = {
                    onDismiss()
                    onForgetDeviceLocally()
                },
            )
    }
}

/** Checks what erasing would lose before asking: edits that must sync first, and planning records found nowhere else. */
@Composable
private fun EraseDataConfirmation(
    mobileSession: TandemMobileSession,
    openBudgetId: String,
    onDismiss: () -> Unit,
    onBackUpFirst: () -> Unit,
    onSignOut: () -> Unit,
) {
    val check by produceState<EraseCheck?>(null) {
        value = EraseCheck(mobileSession.signOutBlocker(), eraseLossSummary(mobileSession.unsyncedPlanningRecords(), openBudgetId))
    }
    val ready = check ?: return
    val blocked = ready.blocker != null
    DestructiveConfirmationDialog(
        title = "Sign out and erase data?",
        message = "This signs out and deletes every saved budget from this device. Your budgets in Actual stay unchanged.",
        confirmLabel = "Erase",
        dismissLabel = "Cancel",
        enabled = !blocked && !mobileSession.isSigningOut,
        dismissEnabled = !mobileSession.isSigningOut,
        content = {
            ready.blocker?.let { InlineMessage(it, InlineMessageKind.WARNING) }
            ready.loss?.let {
                InlineMessage(
                    it,
                    InlineMessageKind.WARNING,
                    actionLabel = "Back up first".takeIf { openBudgetId.isNotBlank() },
                    onAction = onBackUpFirst.takeIf { openBudgetId.isNotBlank() },
                )
            }
        },
        onDismissRequest = { if (!mobileSession.isSigningOut) onDismiss() },
        onConfirm = {
            onDismiss()
            onSignOut()
        },
    )
}

private data class EraseCheck(
    val blocker: String?,
    val loss: String?,
)
