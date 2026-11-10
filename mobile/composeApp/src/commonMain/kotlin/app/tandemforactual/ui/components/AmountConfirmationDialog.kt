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
internal fun AmountConfirmationDialog(
    visible: Boolean,
    formattedAmount: String,
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
                    title = "Confirm amount",
                    titleSize = TandemTypography.cardTitle,
                    onClose = onDismissRequest,
                )
                BodyText("Tandem will save this as $formattedAmount in this budget.")
                Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    SecondaryButton("Edit", Modifier.weight(1f), onClick = onDismissRequest)
                    PrimaryButton("Save", modifier = Modifier.weight(1f), onClick = onConfirm)
                }
            }
        }
    }
}
