@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.TandemSpacing
import app.tandemforactual.ui.TandemTypography

@Composable
internal fun FutureTransactionDateConfirmationDialog(
    visible: Boolean,
    formattedDate: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    TandemChoiceDialog(visible, onDismissRequest) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 440.dp).padding(horizontal = TandemSpacing.large)) {
            Column(
                Modifier.fillMaxWidth().padding(TandemSpacing.large),
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
            ) {
                TandemModalHeader(
                    title = "Confirm future date",
                    titleSize = TandemTypography.cardTitle,
                    onClose = onDismissRequest,
                )
                BodyText("$formattedDate is more than 10 years away. Save this transaction for that date?")
                Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    SecondaryButton("Change date", Modifier.weight(1f), onClick = onDismissRequest)
                    PrimaryButton("Save date", modifier = Modifier.weight(1f), onClick = onConfirm)
                }
            }
        }
    }
}
