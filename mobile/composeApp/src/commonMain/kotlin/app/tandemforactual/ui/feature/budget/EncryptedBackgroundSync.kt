@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.tandemforactual.ui.core.automation.automationViaShortcuts
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
    val copy = if (automationViaShortcuts) READ_WHILE_LOCKED else SYNC_WHILE_LOCKED
    ToggleRow(
        copy.title,
        when {
            store == null -> "Encrypted budgets sync only while unlocked."
            allowed -> copy.on
            !unlocked -> copy.needsUnlock
            else -> copy.off
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
                TandemModalHeader(copy.question, onClose = { confirming = false }, leadingIcon = Lucide.ShieldAlert)
            },
            actions = {
                SecondaryButton("Keep it off", Modifier.fillMaxWidth()) { confirming = false }
            },
        ) {
            BodyText(copy.keeps)
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

private class LockedAccessCopy(
    val title: String,
    val on: String,
    val off: String,
    val needsUnlock: String,
    val question: String,
    val keeps: String,
)

private val SYNC_WHILE_LOCKED =
    LockedAccessCopy(
        title = "Schedule background sync",
        on = "Keeps the password on this device so syncs run while locked",
        off = "Off: syncs only while unlocked",
        needsUnlock = "Unlock the budget to allow syncing while locked",
        question = "Sync while locked?",
        keeps =
            "To sync in the background, Tandem keeps this budget’s password in this device’s protected storage, " +
                "where it can be used without your fingerprint or the password.",
    )

private val READ_WHILE_LOCKED =
    LockedAccessCopy(
        title = "Read while locked",
        on = "Widgets, Siri, Shortcuts and background sync can read it",
        off = "Off: open Tandem to unlock it",
        needsUnlock = "Unlock the budget to allow reading while locked",
        question = "Read while locked?",
        keeps =
            "To let widgets, Siri and Shortcuts read this budget while it’s locked, and sync it in the background, " +
                "Tandem keeps its password in this device’s Keychain, usable after the first unlock since restart.",
    )
