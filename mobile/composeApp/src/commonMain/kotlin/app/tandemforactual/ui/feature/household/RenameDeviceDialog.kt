@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.household

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.sheets.TandemChoiceDialog
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemSpacing
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Smartphone

/** Renames this device as it appears in change history. */
@Composable
internal fun RenameDeviceDialog(
    visible: Boolean,
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var draft by remember(visible) { mutableStateOf(initialName) }
    TandemChoiceDialog(visible = visible, onDismissRequest = onDismiss) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 440.dp).padding(horizontal = TandemSpacing.large)) {
            Column(Modifier.padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                TandemModalHeader("Name this device", onClose = onDismiss, leadingIcon = Lucide.Smartphone)
                TextFieldCard("Name", draft, { draft = it.take(40) }, TextFieldOptions(placeholder = "Sam’s phone"))
                TandemActionButton("Save", TandemActionButtonTone.PRIMARY, Modifier.fillMaxWidth(), enabled = draft.isNotBlank()) {
                    onSave(draft)
                }
            }
        }
    }
}
