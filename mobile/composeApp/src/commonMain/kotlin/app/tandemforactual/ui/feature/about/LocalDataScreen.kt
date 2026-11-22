@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.about
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.format.formatInteger
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.removeBudgetFromDevice
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.decodeCachedBudgetCatalog
import app.tandemforactual.ui.core.state.encodeCachedBudgetCatalog
import app.tandemforactual.ui.design.feedback.AnimatedReveal
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.sheets.DestructiveConfirmationDialog
import app.tandemforactual.ui.design.templates.AmountHero
import app.tandemforactual.ui.design.templates.AnimatedPreviewNote
import app.tandemforactual.ui.design.templates.Heading
import app.tandemforactual.ui.design.templates.InfoBubble
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.feature.auth.LocalDeviceAuthenticator
import app.tandemforactual.ui.feature.auth.LocalEncryptedBudgetVault
import app.tandemforactual.ui.feature.auth.budgetPasswordSecretId
import com.composables.icons.lucide.CloudAlert
import com.composables.icons.lucide.CloudSync
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2
import kotlinx.coroutines.launch

@Composable
internal fun LocalDataScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
) {
    val scope = rememberCoroutineScope()
    val vault = LocalEncryptedBudgetVault.current
    val authenticator = LocalDeviceAuthenticator.current
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    var removeError by remember { mutableStateOf<String?>(null) }
    val hasSelectedBudget = state.selectedBudgetId.isNotBlank()
    val recordCount =
        mobileSession.cachedTransactionCount +
            mobileSession.accountRecords.size +
            mobileSession.payeeRecords.size +
            mobileSession.categoryRecords.size +
            mobileSession.categoryGroupRecords.size +
            mobileSession.budgetMonthIds.size
    val queuedCount = mobileSession.pendingWrites.count { it.status == "queued" || it.status == "sending" }
    val conflictCount = mobileSession.pendingWrites.count { it.status == "conflict" }
    val attentionCount = mobileSession.pendingWrites.count { it.status == "uncertain" || it.status == "rejected" }
    val syncStatus =
        when {
            !hasSelectedBudget -> "No budget selected"
            mobileSession.isSyncing -> "Syncing"
            mobileSession.syncError != null -> "Needs attention"
            mobileSession.localSyncState?.lastSyncedAtEpochMs != null -> "Ready"
            else -> "Not synced"
        }
    PageColumn {
        Heading(
            "Local copy",
            if (hasSelectedBudget) "Cached records and sync status." else "Choose a budget to view the cache.",
        )
        AmountHero(
            "Cached records",
            formatInteger(recordCount),
            if (hasSelectedBudget) "Available offline" else null,
        )
        SectionCard("On this device", syncStatus, animateDetail = true) {
            ListRow(
                "Transactions",
                trailing = formatInteger(mobileSession.cachedTransactionCount),
            )
            DividerLine()
            ListRow("Accounts", trailing = formatInteger(mobileSession.accountRecords.size))
            DividerLine()
            ListRow(
                "Pending changes",
                leading = RowLeading.Icon(Lucide.CloudSync),
                trailing = formatInteger(queuedCount),
            )
            DividerLine()
            ListRow(
                "Needs review",
                "Changes waiting for your decision",
                formatInteger(conflictCount),
                leading = RowLeading.Icon(Lucide.CloudAlert),
            )
            AnimatedReveal(visible = attentionCount > 0, animateHeight = false) {
                Column {
                    DividerLine()
                    ListRow("Other changes needing attention", "Review locally", formatInteger(attentionCount))
                }
            }
        }
        if (hasSelectedBudget) {
            SectionCard("Storage") {
                ListRow(
                    "Remove from this device",
                    "Deletes the copy saved here. Your budget in Actual isn’t changed.",
                    leading = RowLeading.Icon(Lucide.Trash2),
                    onClick = {
                        if (!removing) {
                            removeError = null
                            confirmRemove = true
                        }
                    },
                )
            }
            removeError?.let { InlineMessage(it, InlineMessageKind.ERROR) }
        }
        AnimatedPreviewNote(mobileSession.syncError)
        if (mobileSession.pendingWrites.isNotEmpty()) InfoBubble("Pending edits stay on this device.")
    }
    if (confirmRemove) {
        val budgetName = state.selectedBudget.ifBlank { "this budget" }
        DestructiveConfirmationDialog(
            title = "Remove $budgetName from this device?",
            message =
                "Tandem deletes the copy saved on this device, including any saved password for it. " +
                    "You can download it again from your server. Your budget in Actual isn’t changed.",
            confirmLabel = "Remove",
            dismissLabel = "Cancel",
            enabled = !removing,
            onDismissRequest = { if (!removing) confirmRemove = false },
            onConfirm = {
                removing = true
                scope.launch {
                    val budgetId = state.selectedBudgetId
                    val error = mobileSession.removeBudgetFromDevice(budgetId, vault)
                    removing = false
                    confirmRemove = false
                    if (error != null) {
                        removeError = error
                        return@launch
                    }
                    authenticator?.deleteSecret(budgetPasswordSecretId(budgetId))
                    authenticator?.forgetPasswordCheck(budgetPasswordSecretId(budgetId))
                    state.cachedBudgetCatalogJson =
                        encodeCachedBudgetCatalog(
                            decodeCachedBudgetCatalog(state.cachedBudgetCatalogJson).filterNot {
                                it.budgetId ==
                                    budgetId
                            },
                        )
                    state.encryptedPassword = ""
                    state.selectedBudget = ""
                    state.selectedBudgetId = ""
                    state.selectedBudgetEncrypted = false
                    navigation.reset(AppRoute.BUDGET_CHOOSER)
                }
            },
        )
    }
}
