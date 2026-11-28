@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.tandemforactual.ui.design.buttons.PrimaryButton
import app.tandemforactual.ui.design.buttons.SecondaryButton
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.feedback.BodyText
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography

@Composable
internal fun ReconciledEditConfirmationDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    TandemChoiceDialog(visible, onDismissRequest) {
        SheetCard(
            spacing = TandemSpacing.small,
            header = {
                TandemModalHeader(
                    title = "Edit a reconciled transaction?",
                    titleSize = TandemTypography.cardTitle,
                    onClose = onDismissRequest,
                )
            },
            actions = {
                Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    SecondaryButton("Cancel", Modifier.weight(1f), onClick = onDismissRequest)
                    PrimaryButton("Edit anyway", modifier = Modifier.weight(1f), onClick = onConfirm)
                }
            },
        ) {
            BodyText("This transaction was reconciled against a statement. Changing it can make the account stop matching that statement.")
        }
    }
}
