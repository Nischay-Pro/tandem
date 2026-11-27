@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.reports
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions

@Composable
internal fun ReportNameSheet(
    visible: Boolean,
    suggestion: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember(visible) { mutableStateOf(suggestion) }
    TandemTaskSheet(visible = visible, onDismissRequest = onDismiss, onDismissed = {}) {
        SheetCard(
            header = {
                TandemModalHeader("Save report", onClose = onDismiss)
            },
            actions = {
                TandemActionButton("Save", TandemActionButtonTone.PRIMARY, Modifier.fillMaxWidth(), enabled = name.isNotBlank()) {
                    onSave(name.trim())
                }
            },
        ) {
            TextFieldCard("Name", name, { name = it.take(60) }, TextFieldOptions(placeholder = "Dining out this year"))
        }
    }
}
