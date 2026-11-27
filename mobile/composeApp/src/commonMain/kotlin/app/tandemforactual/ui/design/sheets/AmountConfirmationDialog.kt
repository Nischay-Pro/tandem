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
internal fun AmountConfirmationDialog(
    visible: Boolean,
    formattedAmount: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    TandemChoiceDialog(visible, onDismissRequest) {
        SheetCard(
            spacing = TandemSpacing.small,
            header = {
                TandemModalHeader(
                    title = "Confirm amount",
                    titleSize = TandemTypography.cardTitle,
                    onClose = onDismissRequest,
                )
            },
            actions = {
                Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    SecondaryButton("Edit", Modifier.weight(1f), onClick = onDismissRequest)
                    PrimaryButton("Save", modifier = Modifier.weight(1f), onClick = onConfirm)
                }
            },
        ) {
            BodyText("Tandem will save this as $formattedAmount in this budget.")
        }
    }
}
