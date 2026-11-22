@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.transactions.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.format.transactionAmountInputError
import app.tandemforactual.ui.core.format.transactionAmountNeedsFormatConfirmation
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LocalRouteScrollState
import app.tandemforactual.ui.core.state.formatAmount
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.design.feedback.AnimatedReveal
import app.tandemforactual.ui.design.feedback.BodyText
import app.tandemforactual.ui.design.feedback.FeedbackMessageOwner
import app.tandemforactual.ui.design.feedback.FieldErrorMessage
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.templates.TransactionAmountField
import app.tandemforactual.ui.design.templates.TransactionPickerRow
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.feature.transactions.TransactionDatePickerDialog
import app.tandemforactual.ui.feature.transactions.TransactionTypePicker
import app.tandemforactual.ui.feature.transactions.displayTransactionDate
import app.tandemforactual.ui.feature.transactions.selectTransactionType
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Receipt
import com.composables.icons.lucide.Tags
import com.composables.icons.lucide.WalletCards

internal fun transactionAmountInputErrorMessage(
    state: AppState,
    parsedAmount: Long?,
): String? =
    when {
        state.transactionAmount.isBlank() -> null
        parsedAmount == null -> transactionAmountInputError(state.transactionAmount, state.budgetDisplaySettings)
        parsedAmount < 0 -> "Enter a positive amount."
        else -> null
    }

internal fun transactionBudgetAmountPreview(
    state: AppState,
    parsedAmount: Long?,
): String? =
    parsedAmount
        ?.takeIf {
            it > 0 &&
                transactionAmountNeedsFormatConfirmation(
                    state.transactionAmount,
                    it,
                    state.budgetDisplaySettings,
                )
        }?.let(state::formatCurrency)

/** Scrolls notes into view while they are focused, hides the software keyboard while the keypad shows, and reports whether the save button should be compact. */
@Composable
internal fun transactionFormKeyboardHandling(
    notesFocused: Boolean,
    keypadVisible: Boolean,
): Boolean {
    val softwareKeyboard = LocalSoftwareKeyboardController.current
    val routeScrollState = LocalRouteScrollState.current
    LaunchedEffect(notesFocused) {
        if (notesFocused) {
            routeScrollState?.let { scroll -> snapshotFlow { scroll.maxValue }.collect { scroll.scrollTo(it) } }
        }
    }
    val compactSave by remember(routeScrollState) {
        derivedStateOf { (routeScrollState?.value ?: 0) > 72 }
    }
    LaunchedEffect(keypadVisible) {
        if (keypadVisible) {
            softwareKeyboard?.hide()
        }
    }
    return compactSave
}

/** The scrolling form body; tapping outside a field dismisses the keypad. */
@Composable
internal fun ColumnScope.TransactionFormPage(
    dismissEnabled: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    PageColumn(
        verticalSpacing = 14.dp,
        modifier =
            Modifier
                .weight(1f)
                .clickable(
                    enabled = dismissEnabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
    ) {
        content()
        Spacer(Modifier.height(TandemSpacing.large))
    }
}

/** The amount, its format preview and errors, and the spending/income/transfer picker. */
@Composable
internal fun TransactionAmountHeader(
    state: AppState,
    mobileSession: TandemMobileSession,
    amountMinorUnits: Long,
    keypadVisible: Boolean,
    budgetAmountPreview: String?,
    transactionFieldError: String?,
    onShowKeypad: () -> Unit,
    onDismissKeypad: () -> Unit,
) {
    TransactionAmountField(
        amount = state.formatAmount(amountMinorUnits),
        amountMinorUnits = amountMinorUnits,
        transactionType = state.transactionType,
        showCursor = keypadVisible,
        fractionDigits = state.budgetDisplaySettings?.currencyFractionDigits ?: 2,
        onShowKeypad = onShowKeypad,
    )
    budgetAmountPreview?.let { BodyText("Budget format: $it") }
    val transactionActionError = mobileSession.transactionWriteMessage
    AnimatedReveal(visible = transactionFieldError != null || transactionActionError != null) {
        TransactionFormFeedback(transactionFieldError, transactionActionError)
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        TransactionTypePicker(
            state.transactionType,
            allowTransfer = true,
            onBeforeOpen = onDismissKeypad,
            onSelect = { type -> selectTransactionType(state, type) },
        )
    }
}

@Composable
private fun TransactionFormFeedback(
    fieldError: String?,
    actionError: String?,
) {
    when {
        fieldError != null -> FieldErrorMessage(fieldError, owner = FeedbackMessageOwner.Field("amount"))
        actionError != null ->
            InlineMessage(
                actionError,
                InlineMessageKind.ERROR,
                owner = FeedbackMessageOwner.Action("transaction.save"),
            )
    }
}

/** The payee, category, account and date rows, or source and destination accounts for a transfer. */
@Composable
internal fun TransactionFormDetailsCard(
    state: AppState,
    navigation: AppNavigation,
    pickers: TransactionFormPickerState,
    onDismissKeypad: () -> Unit,
) {
    SectionCard(title = "") {
        if (state.transactionType == "Transfer") {
            TransactionPickerRow(state.transactionAccount, "Choose source account", Lucide.WalletCards) {
                onDismissKeypad()
                pickers.showAccountPicker = true
            }
            DividerLine()
            TransactionPickerRow(state.transactionPayee, "Choose destination account", Lucide.WalletCards) {
                onDismissKeypad()
                navigation.open(AppRoute.CHOOSE_PAYEE)
            }
        } else {
            TransactionPickerRow(state.transactionPayee, "Choose payee", Lucide.Receipt) {
                onDismissKeypad()
                navigation.open(AppRoute.CHOOSE_PAYEE)
            }
            DividerLine()
            TransactionPickerRow(state.transactionCategory, "Choose category", Lucide.Tags) {
                onDismissKeypad()
                navigation.open(AppRoute.CHOOSE_CATEGORY)
            }
            DividerLine()
            TransactionPickerRow(state.transactionAccount, "Choose account", Lucide.WalletCards) {
                onDismissKeypad()
                pickers.showAccountPicker = true
            }
        }
        DividerLine()
        TransactionPickerRow(displayTransactionDate(state.transactionDate), "Choose date", Lucide.CalendarDays) {
            onDismissKeypad()
            pickers.showDatePicker = true
        }
    }
}

/** The multi-line notes field; focusing it hides the keypad. */
@Composable
internal fun TransactionNotesField(
    state: AppState,
    onFocusedChange: (Boolean) -> Unit,
) {
    TextFieldCard(
        "Notes",
        state.transactionNotes,
        { state.transactionNotes = it },
        TextFieldOptions(
            placeholder = "Add a note",
            singleLine = false,
            onFocusedChange = onFocusedChange,
        ),
    )
}

/** The bar under the form that holds its cancel or delete and save actions. */
@Composable
internal fun TransactionFormActionBar(
    keypadVisible: Boolean,
    content: @Composable RowScope.() -> Unit,
) {
    Box(Modifier.fillMaxWidth().padding(bottom = if (keypadVisible) 0.dp else 12.dp)) {
        Row(
            Modifier.fillMaxWidth().background(tandemPalette.surface).padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** The date picker shared by new and edited transactions. */
@Composable
internal fun TransactionFormDatePicker(
    state: AppState,
    pickers: TransactionFormPickerState,
) {
    TransactionDatePickerDialog(
        visible = pickers.showDatePicker,
        selectedDate = state.transactionDate,
        onSelect = {
            state.transactionDate = it
            pickers.showDatePicker = false
        },
        onDismiss = { pickers.showDatePicker = false },
    )
}
