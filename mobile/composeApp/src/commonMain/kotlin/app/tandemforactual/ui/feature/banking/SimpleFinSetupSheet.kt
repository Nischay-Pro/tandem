@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.banking

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.tandemforactual.api.client.BankCredentialSource
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemChoiceDialog
import app.tandemforactual.ui.design.templates.ChoiceRow
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import app.tandemforactual.ui.design.theme.tandemRevealEnter
import app.tandemforactual.ui.design.theme.tandemRevealExit
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2

/** Hands a SimpleFIN setup token to the Actual server, for this budget alone or, for admins, every budget on the server. */
@Composable
internal fun SimpleFinSetupSheet(
    setup: SimpleFinSetup?,
    onDismiss: () -> Unit,
    onSave: (String, BankCredentialSource) -> Unit,
    onRemove: () -> Unit,
) {
    val palette = tandemPalette
    val visible = setup != null
    var token by remember(visible) { mutableStateOf("") }
    var scope by remember(visible) { mutableStateOf<BankCredentialSource?>(null) }
    val serverNow = setup?.allowServer == true && setup.current == BankCredentialSource.SERVER
    val target = scope ?: if (serverNow) BankCredentialSource.SERVER else BankCredentialSource.BUDGET
    val saving = setup?.saving == true
    TandemChoiceDialog(visible = visible, onDismissRequest = onDismiss) {
        SheetCard(
            spacing = TandemSpacing.medium,
            header = { TandemModalHeader("Set up SimpleFIN", onClose = onDismiss, leadingIcon = Lucide.KeyRound) },
            actions = {
                TandemActionButton(
                    "Save token",
                    TandemActionButtonTone.PRIMARY,
                    Modifier.fillMaxWidth(),
                    enabled = token.isNotBlank() && !saving,
                    loading = saving,
                ) { onSave(token, target) }
            },
        ) {
            Text(
                "Paste a setup token from SimpleFIN Bridge. Your Actual server claims it and keeps the access; " +
                    "Tandem doesn’t store it.",
                color = palette.muted,
                fontSize = TandemTypography.compactBody,
            )
            TextFieldCard("Setup token", token, { token = it }, TextFieldOptions(placeholder = "Paste token", isSecret = true))
            CredentialScopeChoice(setup?.allowServer == true, target) { scope = it }
            AnimatedVisibility(
                setup?.error != null,
                enter = tandemRevealEnter(tandemReduceMotion),
                exit = tandemRevealExit(tandemReduceMotion),
            ) {
                InlineMessage(setup?.error.orEmpty(), InlineMessageKind.ERROR)
            }
            if (setup?.current == BankCredentialSource.BUDGET) {
                ListRow(
                    "Remove this budget’s token",
                    "The budget goes back to the server’s token, if there is one",
                    leading = RowLeading.Icon(Lucide.Trash2),
                    options = ListRowOptions(trailingColor = palette.negative, showChevron = false),
                    onClick = if (saving) null else onRemove,
                )
            }
        }
    }
}

@Composable
private fun CredentialScopeChoice(
    allowServer: Boolean,
    target: BankCredentialSource,
    onChoose: (BankCredentialSource) -> Unit,
) {
    if (!allowServer) {
        Text(
            "Saved for this budget only. Only a server admin can set a token for every budget.",
            color = tandemPalette.muted,
            fontSize = TandemTypography.caption,
        )
        return
    }
    ChoiceRow("This budget only", "Other budgets keep their own bank access", target == BankCredentialSource.BUDGET) {
        onChoose(BankCredentialSource.BUDGET)
    }
    ChoiceRow(
        "All budgets on this server",
        "Replaces the server’s token for budgets without their own",
        target == BankCredentialSource.SERVER,
    ) { onChoose(BankCredentialSource.SERVER) }
}
