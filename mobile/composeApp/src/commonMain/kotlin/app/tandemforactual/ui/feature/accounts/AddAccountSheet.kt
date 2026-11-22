@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.accounts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.createBudgetAccount
import app.tandemforactual.ui.core.session.budgetActionFeedbackMessage
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.templates.CheckBoxRow
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemAlloFamily
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.FolderKey
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.WalletCards
import com.composables.icons.lucide.X
import kotlinx.coroutines.launch

/** Saveable draft state for the add-account sheet. */
internal class AddAccountSheetState(
    visible: MutableState<Boolean>,
    name: MutableState<String>,
    offBudget: MutableState<Boolean>,
    groupId: MutableState<String>,
) {
    var visible by visible
    var name by name
    var offBudget by offBudget
    var groupId by groupId

    fun open(mobileSession: TandemMobileSession) {
        name = ""
        offBudget = false
        groupId = ""
        mobileSession.feedback.clearBudgetActionMessage()
        visible = true
    }
}

@Composable
internal fun rememberAddAccountSheetState(): AddAccountSheetState {
    val visible = rememberSaveable { mutableStateOf(false) }
    val name = rememberSaveable { mutableStateOf("") }
    val offBudget = rememberSaveable { mutableStateOf(false) }
    val groupId = rememberSaveable { mutableStateOf("") }
    return remember { AddAccountSheetState(visible, name, offBudget, groupId) }
}

/** Task sheet for creating a new account with a name, group and off-budget flag. */
@Composable
internal fun AddAccountSheet(
    sheet: AddAccountSheetState,
    state: AppState,
    mobileSession: TandemMobileSession,
) {
    val scope = rememberCoroutineScope()
    TandemTaskSheet(
        visible = sheet.visible,
        onDismissRequest = { if (!mobileSession.isPerformingBudgetAction) sheet.visible = false },
        onDismissed = {},
    ) {
        SurfaceCard(
            Modifier.fillMaxWidth().widthIn(max = 480.dp).padding(horizontal = 16.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(TandemSpacing.large),
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
            ) {
                AddAccountSheetHeader(enabled = !mobileSession.isPerformingBudgetAction) { sheet.visible = false }
                TextFieldCard("Name", sheet.name, { sheet.name = it }, TextFieldOptions(placeholder = "Checking"))
                AccountGroupPicker(mobileSession, sheet.groupId) { sheet.groupId = it }
                SurfaceCard {
                    CheckBoxRow(
                        "Off budget",
                        "Excluded from the budget",
                        checked = sheet.offBudget,
                        enabled = !mobileSession.isPerformingBudgetAction,
                        onChange = { sheet.offBudget = it },
                    )
                }
                budgetActionFeedbackMessage(mobileSession)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    TandemActionButton(
                        "Cancel",
                        TandemActionButtonTone.NEUTRAL,
                        Modifier.weight(1f),
                        enabled = !mobileSession.isPerformingBudgetAction,
                    ) { sheet.visible = false }
                    TandemActionButton(
                        if (mobileSession.isPerformingBudgetAction) "Adding…" else "Add account",
                        TandemActionButtonTone.PRIMARY,
                        Modifier.weight(1f),
                        icon = Lucide.Plus,
                        enabled = !mobileSession.isPerformingBudgetAction && sheet.name.isNotBlank(),
                        loading = mobileSession.isPerformingBudgetAction,
                    ) {
                        scope.launch {
                            val created =
                                mobileSession.budgetEdits.createBudgetAccount(
                                    state.selectedBudgetId,
                                    sheet.name,
                                    sheet.offBudget,
                                    sheet.groupId.ifBlank { null },
                                    state.encryptedPassword.takeIf { it.isNotBlank() },
                                )
                            if (created) sheet.visible = false
                        }
                    }
                }
            }
        }
    }
}

/** Icon, title and close button at the top of the add-account sheet. */
@Composable
private fun AddAccountSheetHeader(
    enabled: Boolean,
    onClose: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(tandemPalette.accentSurface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Lucide.WalletCards,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = tandemPalette.accent,
            )
        }
        Text(
            "Add account",
            modifier = Modifier.weight(1f),
            color = tandemPalette.text,
            fontSize = TandemTypography.sectionTitle,
            fontFamily = tandemAlloFamily(),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        TandemIconButton(
            Lucide.X,
            "Close",
            sizeDp = 40,
            iconSizeDp = 18,
            containerColor = tandemPalette.raised,
            enabled = enabled,
            onClick = onClose,
        )
    }
}

/** Row with a dropdown for choosing the new account's group. */
@Composable
private fun AccountGroupPicker(
    mobileSession: TandemMobileSession,
    groupId: String,
    onGroupIdChange: (String) -> Unit,
) {
    var accountGroupMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val accountGroupOptions =
        remember(mobileSession.accountGroupRecords) {
            mobileSession.accountGroupRecords
                .mapNotNull { record -> record.recordName()?.let { record.recordId to it } }
                .sortedBy { it.second.lowercase() }
        }
    Box {
        ListRow(
            "Account group",
            trailing = accountGroupOptions.firstOrNull { it.first == groupId }?.second ?: "No group",
            leading = RowLeading.Icon(Lucide.FolderKey),
            onClick = { accountGroupMenuExpanded = true },
        )
        DropdownMenu(
            expanded = accountGroupMenuExpanded,
            onDismissRequest = { accountGroupMenuExpanded = false },
        ) {
            DropdownMenuItem(onClick = {
                onGroupIdChange("")
                accountGroupMenuExpanded = false
            }) {
                Text("No group", color = tandemPalette.text, fontSize = TandemTypography.compactBody)
            }
            accountGroupOptions.forEach { (optionId, groupName) ->
                DropdownMenuItem(onClick = {
                    onGroupIdChange(optionId)
                    accountGroupMenuExpanded = false
                }) {
                    Text(groupName, color = tandemPalette.text, fontSize = TandemTypography.compactBody)
                }
            }
        }
    }
}
