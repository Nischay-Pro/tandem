@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.templates.ToggleRow
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2

@Composable
internal fun CategoryGroupNameDialog(
    visible: Boolean,
    title: String,
    name: String,
    onNameChange: (String) -> Unit,
    busy: Boolean,
    message: String?,
    messageKind: InlineMessageKind = InlineMessageKind.ERROR,
    messageActionLabel: String? = null,
    onMessageAction: (() -> Unit)? = null,
    placeholder: String = "Essentials",
    nameLabel: String = "Name",
    description: String? = null,
    leadingIcon: ImageVector? = null,
    isIncome: Boolean? = null,
    onIncomeChange: ((Boolean) -> Unit)? = null,
    saveLabel: String = "Save",
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    TandemTaskSheet(
        visible = visible,
        onDismissRequest = { if (!busy) onDismiss() },
        onDismissed = {},
    ) {
        SheetCard(
            header = {
                TandemModalHeader(
                    title = title,
                    enabled = !busy,
                    onClose = { if (!busy) onDismiss() },
                    leadingIcon = leadingIcon,
                )
            },
            actions = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    TandemActionButton("Cancel", TandemActionButtonTone.NEUTRAL, Modifier.weight(1f), enabled = !busy, onClick = onDismiss)
                    TandemActionButton(
                        saveLabel,
                        TandemActionButtonTone.PRIMARY,
                        Modifier.weight(1f),
                        icon = Lucide.Check,
                        enabled =
                            name.isNotBlank() && !busy,
                        loading = busy,
                        onClick = onSave,
                    )
                }
            },
        ) {
            description?.let {
                Text(
                    it,
                    color = tandemPalette.muted,
                    fontSize = TandemTypography.body,
                    lineHeight = TandemTypography.bodyLineHeight,
                )
            }
            TextFieldCard(nameLabel, name, onNameChange, TextFieldOptions(placeholder = placeholder))
            if (isIncome != null && onIncomeChange != null) {
                ToggleRow("Income group", "For income categories", isIncome, !busy, onIncomeChange)
            }
            message?.let { InlineMessage(it, messageKind, actionLabel = messageActionLabel, onAction = onMessageAction) }
            onDelete?.let { delete ->
                TandemActionButton(
                    "Delete group",
                    TandemActionButtonTone.DESTRUCTIVE,
                    Modifier.fillMaxWidth(),
                    icon = Lucide.Trash2,
                    enabled = !busy,
                    onClick = delete,
                )
            }
        }
    }
}
