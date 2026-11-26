@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.store.observeAccountBalanceThrough
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.transactions.createTransaction
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.formatAmount
import app.tandemforactual.ui.core.state.parseAmountMinorUnits
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.sheets.TandemChoiceDialog
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Scale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal const val BALANCE_ADJUSTMENT_NOTE = "Balance adjustment"

/** The transaction amount that moves [currentMinor] to [targetMinor], or null when nothing needs to change. */
internal fun balanceAdjustmentMinor(
    currentMinor: Long,
    targetMinor: Long,
): Long? = (targetMinor - currentMinor).takeIf { it != 0L }

/**
 * Sets an account's balance to what the bank, lender or valuation says by adding the difference as one cleared
 * adjustment, as Actual's reconcile does. Useful for accounts nothing syncs, such as a car or a loan.
 */
@Composable
internal fun AccountBalanceDialog(
    visible: Boolean,
    state: AppState,
    current: Long,
    onDismiss: () -> Unit,
    onSave: (adjustmentMinor: Long) -> Unit,
) {
    var draft by remember(visible) { mutableStateOf(state.formatAmount(current)) }
    val target = state.parseAmountMinorUnits(draft)
    val adjustment = target?.let { balanceAdjustmentMinor(current, it) }
    TandemChoiceDialog(visible = visible, onDismissRequest = onDismiss) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 440.dp).padding(horizontal = TandemSpacing.large)) {
            Column(Modifier.padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                TandemModalHeader("Update balance", onClose = onDismiss, leadingIcon = Lucide.Scale)
                TextFieldCard(
                    "New balance",
                    draft,
                    { draft = it },
                    TextFieldOptions(keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)),
                )
                Text(
                    when {
                        target == null -> "Enter the balance as a number."
                        adjustment == null -> "That’s already the balance."
                        else -> "Adds a ${state.formatAmount(adjustment)} adjustment dated today."
                    },
                    color = tandemPalette.muted,
                    fontSize = TandemTypography.compactBody,
                )
                TandemActionButton("Save", TandemActionButtonTone.PRIMARY, Modifier.fillMaxWidth(), enabled = adjustment != null) {
                    adjustment?.let(onSave)
                }
            }
        }
    }
}

internal suspend fun TandemMobileSession.adjustAccountBalance(
    state: AppState,
    accountId: String,
    adjustmentMinor: Long,
): Boolean =
    transactions.createTransaction(
        budgetId = state.selectedBudgetId,
        accountId = accountId,
        payeeId = null,
        categoryId = null,
        amountText = state.formatAmount(adjustmentMinor),
        dateText = platformTodayIsoDate(),
        notes = BALANCE_ADJUSTMENT_NOTE,
        encryptionPassword = state.encryptedPassword.takeIf { it.isNotBlank() },
        displaySettings = state.budgetDisplaySettings,
    )

/** The Update balance dialog for [account], saving the adjustment through the transaction outbox. */
@Composable
internal fun AccountBalanceUpdate(
    visible: MutableState<Boolean>,
    state: AppState,
    mobileSession: TandemMobileSession,
    account: LiveAccountSummary,
    scope: CoroutineScope,
) {
    val today = platformTodayIsoDate()
    val local =
        remember(state.selectedBudgetId, account.id, today) {
            mobileSession.repository?.observeAccountBalanceThrough(state.selectedBudgetId, account.id, today)
        }?.collectAsState(null)?.value
    AccountBalanceDialog(visible.value, state, local ?: account.balanceMinor ?: 0L, onDismiss = { visible.value = false }) { adjustment ->
        visible.value = false
        scope.launch { mobileSession.adjustAccountBalance(state, account.id, adjustment) }
    }
}
