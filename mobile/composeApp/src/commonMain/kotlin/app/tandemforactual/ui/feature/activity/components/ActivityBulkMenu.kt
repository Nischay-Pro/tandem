@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.activity.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.store.BulkTransactionEdit
import app.tandemforactual.data.store.noteTags
import app.tandemforactual.ui.core.format.formatInteger
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemChoiceDialog
import app.tandemforactual.ui.design.theme.TandemSpacing
import com.composables.icons.lucide.CalendarClock
import com.composables.icons.lucide.Circle
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Eraser
import com.composables.icons.lucide.Hash
import com.composables.icons.lucide.Landmark
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Merge
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Store
import com.composables.icons.lucide.Tags
import com.composables.icons.lucide.Trash2

/** One row of the bulk menu: what it says, and what tapping it does, or why it can't be used now. */
internal class ActivityBulkMenuEntry(
    val title: String,
    val icon: ImageVector,
    val unavailable: String? = null,
    val choice: ActivityBulkChoice,
)

/** Where a bulk menu row leads: a picker, an action that runs straight away, or the delete confirmation. */
internal sealed interface ActivityBulkChoice {
    data class Pick(
        val picker: ActivityBulkPicker,
    ) : ActivityBulkChoice

    data class Run(
        val action: ActivityBulkAction,
    ) : ActivityBulkChoice

    data object Delete : ActivityBulkChoice
}

/** The bulk menu's rows for [items], with merge offered only for two transactions of the same amount, as Actual does. */
internal fun activityBulkMenuEntries(items: List<ActivityBulkItem>): List<ActivityBulkMenuEntry> {
    val markCleared = items.any { !it.cleared }
    val mergeIssue =
        when {
            items.size != 2 -> "Select 2 transactions to merge them"
            items[0].amountMinor != items[1].amountMinor -> "Only transactions with the same amount merge"
            else -> null
        }
    val hasTags = items.any { noteTags(it.notes).isNotEmpty() }
    return listOf(
        ActivityBulkMenuEntry("Set category", Lucide.Tags, choice = ActivityBulkChoice.Pick(ActivityBulkPicker.CATEGORY)),
        ActivityBulkMenuEntry("Set payee", Lucide.Store, choice = ActivityBulkChoice.Pick(ActivityBulkPicker.PAYEE)),
        ActivityBulkMenuEntry("Move to account", Lucide.Landmark, choice = ActivityBulkChoice.Pick(ActivityBulkPicker.ACCOUNT)),
        ActivityBulkMenuEntry(
            if (markCleared) "Mark cleared" else "Mark uncleared",
            if (markCleared) Lucide.CircleCheck else Lucide.Circle,
            choice = ActivityBulkChoice.Run(ActivityBulkAction.Edit(BulkTransactionEdit.SetCleared(markCleared))),
        ),
        ActivityBulkMenuEntry("Add tag", Lucide.Hash, choice = ActivityBulkChoice.Pick(ActivityBulkPicker.ADD_TAG)),
        ActivityBulkMenuEntry(
            "Remove tag",
            Lucide.Eraser,
            unavailable = "No tags in these notes".takeUnless { hasTags },
            choice = ActivityBulkChoice.Pick(ActivityBulkPicker.REMOVE_TAG),
        ),
        ActivityBulkMenuEntry("Link to schedule", Lucide.CalendarClock, choice = ActivityBulkChoice.Pick(ActivityBulkPicker.SCHEDULE)),
        ActivityBulkMenuEntry("Duplicate", Lucide.Copy, choice = ActivityBulkChoice.Run(ActivityBulkAction.Duplicate)),
        ActivityBulkMenuEntry("Merge", Lucide.Merge, unavailable = mergeIssue, choice = ActivityBulkChoice.Run(ActivityBulkAction.Merge)),
        ActivityBulkMenuEntry("Delete", Lucide.Trash2, choice = ActivityBulkChoice.Delete),
    )
}

/** Everything that can be done to the selected transactions at once, under a header with their count and total. */
@Composable
internal fun ActivityBulkMenu(
    visible: Boolean,
    items: List<ActivityBulkItem>,
    totalText: String,
    onDismiss: () -> Unit,
    onChoose: (ActivityBulkChoice) -> Unit,
) {
    TandemChoiceDialog(visible = visible, onDismissRequest = onDismiss) {
        val count = if (items.size == 1) "1 transaction" else "${formatInteger(items.size)} transactions"
        SheetCard(
            scrollBody = false,
            maxWidth = 480.dp,
            spacing = TandemSpacing.small,
            header = { TandemModalHeader("Edit $count", onClose = onDismiss, leadingIcon = Lucide.Pencil) },
        ) {
            ListRow("Selected total", trailing = totalText, leading = RowLeading.None, options = ListRowOptions(showChevron = false))
            DividerLine()
            Column(Modifier.weight(1f, fill = false).heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                activityBulkMenuEntries(items).forEachIndexed { index, entry ->
                    if (index > 0) DividerLine()
                    val available = entry.unavailable == null
                    ListRow(
                        entry.title,
                        subtitle = entry.unavailable,
                        leading = RowLeading.Icon(entry.icon),
                        options =
                            ListRowOptions(
                                muted = !available,
                                showChevron = available && entry.choice is ActivityBulkChoice.Pick,
                            ),
                        onClick = if (available) ({ onChoose(entry.choice) }) else null,
                    )
                }
            }
        }
    }
}
