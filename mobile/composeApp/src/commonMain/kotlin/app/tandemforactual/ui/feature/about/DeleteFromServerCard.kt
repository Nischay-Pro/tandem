@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.security.LocalBackgroundBudgetKeyStore
import app.tandemforactual.ui.core.security.LocalDeviceAuthenticator
import app.tandemforactual.ui.core.security.LocalEncryptedBudgetVault
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.deleteBudgetFromServer
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.sheets.DestructiveConfirmationDialog
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemSpacing
import com.composables.icons.lucide.CloudOff
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.Lucide
import kotlinx.coroutines.launch

/** Deleting the open budget from the Actual server, for every device, after typing its name. */
@Composable
internal fun DeleteFromServerCard(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    SectionCard("Server") {
        ListRow(
            "Delete from server",
            "Removes the budget from Actual for every device",
            leading = RowLeading.Icon(Lucide.CloudOff),
            onClick = {
                error = null
                confirming = true
            },
        )
    }
    error?.let { InlineMessage(it, InlineMessageKind.ERROR) }
    if (confirming) {
        DeleteFromServerDialog(state, mobileSession, navigation, onDismiss = { confirming = false }) { error = it }
    }
}

@Composable
private fun DeleteFromServerDialog(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    onDismiss: () -> Unit,
    onError: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val vault = LocalEncryptedBudgetVault.current
    val authenticator = LocalDeviceAuthenticator.current
    val keyStore = LocalBackgroundBudgetKeyStore.current
    val name = state.selectedBudget
    var typed by rememberSaveable { mutableStateOf("") }
    var deleting by remember { mutableStateOf(false) }
    DestructiveConfirmationDialog(
        title = "Delete $name from the server?",
        message =
            "Every device loses this budget, and Actual can’t bring it back. Back up first if you might need it. " +
                "Tandem keeps this device’s copy as a backup.",
        confirmLabel = if (deleting) "Deleting…" else "Delete",
        dismissLabel = "Cancel",
        enabled = !deleting && deleteNameMatches(typed, name),
        dismissEnabled = !deleting,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                TandemActionButton(
                    "Back up first",
                    TandemActionButtonTone.NEUTRAL,
                    icon = Lucide.Download,
                    enabled = !deleting,
                ) {
                    onDismiss()
                    navigation.open(AppRoute.EXPORT)
                }
                TextFieldCard("Type $name to confirm", typed, { typed = it }, TextFieldOptions(placeholder = name))
            }
        },
        onDismissRequest = onDismiss,
        onConfirm = {
            deleting = true
            scope.launch {
                val budgetId = state.selectedBudgetId
                val failure = mobileSession.deleteBudgetFromServer(budgetId, vault)
                deleting = false
                onDismiss()
                if (failure != null) {
                    onError(failure)
                    return@launch
                }
                forgetBudgetHere(state, budgetId, authenticator, keyStore)
                navigation.reset(AppRoute.BUDGET_CHOOSER)
            }
        },
    )
}

/** The typed confirmation must be the budget's name, ignoring case and surrounding spaces. */
internal fun deleteNameMatches(
    typed: String,
    name: String,
): Boolean = name.isNotBlank() && typed.trim().equals(name.trim(), ignoreCase = true)
