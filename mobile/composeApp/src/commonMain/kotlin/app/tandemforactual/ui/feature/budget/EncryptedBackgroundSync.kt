@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.tandemforactual.ui.core.security.LocalBackgroundBudgetKeyStore
import app.tandemforactual.ui.core.session.BackgroundBudgetKey
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.buttons.SecondaryButton
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.feedback.BodyText
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemChoiceDialog
import app.tandemforactual.ui.design.templates.ToggleRow
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ShieldAlert

/**
 * Background sync for an encrypted budget: off means it syncs only while unlocked; on keeps its password in this device's
 * protected storage so scheduled syncs can open it.
 */
@Composable
internal fun EncryptedBackgroundSyncToggle(state: AppState) {
    val store = LocalBackgroundBudgetKeyStore.current
    var allowed by remember(state.selectedBudgetId, store) {
        mutableStateOf(BackgroundBudgetKey.budgetIn(store) == state.selectedBudgetId)
    }
    var confirming by remember { mutableStateOf(false) }
    val unlocked = state.encryptedPassword.isNotBlank()
    ToggleRow(
        "Schedule background sync",
        when {
            store == null -> "Encrypted budgets sync only while unlocked."
            allowed -> "Keeps the password on this device so syncs run while locked"
            !unlocked -> "Unlock the budget to allow syncing while locked"
            else -> "Off: syncs only while unlocked"
        },
        allowed && state.backgroundSyncEnabled,
        enabled = store != null && (allowed || unlocked),
    ) { enable ->
        if (enable) {
            confirming = true
        } else {
            runCatching { store?.clear() }
            allowed = false
            state.backgroundSyncEnabled = false
        }
    }
    TandemChoiceDialog(visible = confirming, onDismissRequest = { confirming = false }) {
        SheetCard(
            header = {
                TandemModalHeader("Sync while locked?", onClose = { confirming = false }, leadingIcon = Lucide.ShieldAlert)
            },
            actions = {
                SecondaryButton("Keep it off", Modifier.fillMaxWidth()) { confirming = false }
            },
        ) {
            BodyText(
                "To sync in the background, Tandem keeps this budget’s password in this device’s protected storage, " +
                    "where it can be used without your fingerprint or the password.",
            )
            BodyText(
                "Your budget stays encrypted on the server, but anyone who gets into this device and its apps could open it. " +
                    "Leave this off to keep the password only in your head and sync while unlocked.",
            )
            TandemActionButton("Allow on this device", TandemActionButtonTone.PRIMARY, Modifier.fillMaxWidth()) {
                val saved =
                    runCatching { store?.write(BackgroundBudgetKey.encode(state.selectedBudgetId, state.encryptedPassword)) }.isSuccess
                allowed = saved && store != null
                state.backgroundSyncEnabled = allowed
                confirming = false
            }
        }
    }
}
