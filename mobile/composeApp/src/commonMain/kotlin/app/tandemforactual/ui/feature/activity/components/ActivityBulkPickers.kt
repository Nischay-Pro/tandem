@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.activity.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.store.BulkTransactionEdit
import app.tandemforactual.data.store.noteTagName
import app.tandemforactual.data.store.noteTags
import app.tandemforactual.ui.core.state.LiveScheduleSummary
import app.tandemforactual.ui.core.state.booleanValue
import app.tandemforactual.ui.core.state.recordBody
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.core.state.stringValue
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemChoiceDialog
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemSpacing
import com.composables.icons.lucide.CalendarClock
import com.composables.icons.lucide.Eraser
import com.composables.icons.lucide.Hash
import com.composables.icons.lucide.Landmark
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Store
import com.composables.icons.lucide.Tags

/** One value a bulk picker offers; a null id clears the field. */
internal data class ActivityBulkOption(
    val id: String?,
    val title: String,
    val subtitle: String? = null,
)

/** The budget lists the pickers choose from. */
internal class ActivityBulkSources(
    val categories: List<BudgetRecordEntity>,
    val payees: List<BudgetRecordEntity>,
    val accounts: List<BudgetRecordEntity>,
    val schedules: List<LiveScheduleSummary>,
    val knownTags: List<String>,
)

/** The choices for [picker]: visible categories, payees other than transfers, open accounts, schedules or tags. */
internal fun activityBulkOptions(
    picker: ActivityBulkPicker,
    sources: ActivityBulkSources,
    items: List<ActivityBulkItem>,
): List<ActivityBulkOption> =
    when (picker) {
        ActivityBulkPicker.CATEGORY ->
            listOf(ActivityBulkOption(null, "No category")) +
                sources.categories
                    .filterNot { it.recordBody()?.booleanValue("hidden") == true }
                    .mapNotNull { record -> record.recordName()?.let { ActivityBulkOption(record.recordId, it) } }
        ActivityBulkPicker.PAYEE ->
            listOf(ActivityBulkOption(null, "No payee")) +
                sources.payees
                    .filter { it.recordBody()?.stringValue("transfer_acct") == null }
                    .mapNotNull { record -> record.recordName()?.let { ActivityBulkOption(record.recordId, it) } }
                    .sortedBy { it.title.lowercase() }
        ActivityBulkPicker.ACCOUNT ->
            sources.accounts
                .filterNot { it.recordBody()?.booleanValue("closed") == true }
                .mapNotNull { record -> record.recordName()?.let { ActivityBulkOption(record.recordId, it) } }
        ActivityBulkPicker.SCHEDULE ->
            listOf(ActivityBulkOption(null, "No schedule", "Unlink them from any schedule")) +
                sources.schedules.map { ActivityBulkOption(it.id, it.name, it.accountName) }
        ActivityBulkPicker.ADD_TAG -> (items.flatMap { noteTags(it.notes) } + sources.knownTags).tagOptions()
        ActivityBulkPicker.REMOVE_TAG -> items.flatMap { noteTags(it.notes) }.tagOptions()
    }

private fun List<String>.tagOptions() = distinctBy { it.lowercase() }.sortedBy { it.lowercase() }.map { ActivityBulkOption(it, "#$it") }

/** The edit a picked value makes. */
internal fun activityBulkPickedEdit(
    picker: ActivityBulkPicker,
    option: ActivityBulkOption,
): BulkTransactionEdit? =
    when (picker) {
        ActivityBulkPicker.CATEGORY -> BulkTransactionEdit.SetCategory(option.id)
        ActivityBulkPicker.PAYEE -> BulkTransactionEdit.SetPayee(option.id)
        ActivityBulkPicker.ACCOUNT -> option.id?.let { BulkTransactionEdit.MoveToAccount(it) }
        ActivityBulkPicker.SCHEDULE -> BulkTransactionEdit.LinkSchedule(option.id)
        ActivityBulkPicker.ADD_TAG -> option.id?.let { BulkTransactionEdit.AddTag(it) }
        ActivityBulkPicker.REMOVE_TAG -> option.id?.let { BulkTransactionEdit.RemoveTag(it) }
    }

private fun ActivityBulkPicker.title() =
    when (this) {
        ActivityBulkPicker.CATEGORY -> "Set category"
        ActivityBulkPicker.PAYEE -> "Set payee"
        ActivityBulkPicker.ACCOUNT -> "Move to account"
        ActivityBulkPicker.SCHEDULE -> "Link to schedule"
        ActivityBulkPicker.ADD_TAG -> "Add tag"
        ActivityBulkPicker.REMOVE_TAG -> "Remove tag"
    }

private fun ActivityBulkPicker.icon() =
    when (this) {
        ActivityBulkPicker.CATEGORY -> Lucide.Tags
        ActivityBulkPicker.PAYEE -> Lucide.Store
        ActivityBulkPicker.ACCOUNT -> Lucide.Landmark
        ActivityBulkPicker.SCHEDULE -> Lucide.CalendarClock
        ActivityBulkPicker.ADD_TAG -> Lucide.Hash
        ActivityBulkPicker.REMOVE_TAG -> Lucide.Eraser
    }

/** A searchable list of [options] for [picker]; adding a tag also offers the typed one when it is new. */
@Composable
internal fun ActivityBulkPickerSheet(
    picker: ActivityBulkPicker?,
    options: List<ActivityBulkOption>,
    onDismiss: () -> Unit,
    onPick: (ActivityBulkPicker, ActivityBulkOption) -> Unit,
) {
    val last = remember { arrayOfNulls<ActivityBulkPicker>(1) }
    if (picker != null) last[0] = picker
    val current = picker ?: last[0] ?: return
    var query by remember(picker) { mutableStateOf("") }
    val typedTag = noteTagName(query).takeIf { current == ActivityBulkPicker.ADD_TAG }
    val newTag = typedTag?.takeIf { tag -> options.none { it.id.equals(tag, ignoreCase = true) } }
    val matches = options.filter { it.title.contains(query.trim().removePrefix("#"), ignoreCase = true) }
    TandemChoiceDialog(visible = picker != null, onDismissRequest = onDismiss) {
        SheetCard(
            scrollBody = false,
            maxWidth = 480.dp,
            spacing = TandemSpacing.small,
            header = { TandemModalHeader(current.title(), onClose = onDismiss, leadingIcon = current.icon()) },
        ) {
            if (current != ActivityBulkPicker.REMOVE_TAG) {
                TextFieldCard(
                    if (current == ActivityBulkPicker.ADD_TAG) "Tag" else "Search",
                    query,
                    { query = it },
                    TextFieldOptions(
                        placeholder = if (current == ActivityBulkPicker.ADD_TAG) "work" else "Type to filter",
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { typedTag?.let { onPick(current, ActivityBulkOption(it, "#$it")) } }),
                    ),
                )
            }
            Column(Modifier.weight(1f, fill = false).heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                newTag?.let { tag ->
                    ListRow(
                        "Add #$tag",
                        leading = RowLeading.Icon(Lucide.Plus),
                        options = ListRowOptions(showChevron = false),
                        onClick = { onPick(current, ActivityBulkOption(tag, "#$tag")) },
                    )
                }
                matches.forEachIndexed { index, option ->
                    if (index > 0 || newTag != null) DividerLine()
                    ListRow(
                        option.title,
                        subtitle = option.subtitle,
                        leading = RowLeading.None,
                        options = ListRowOptions(showChevron = false),
                        onClick = { onPick(current, option) },
                    )
                }
                if (matches.isEmpty() && newTag == null) ListRow("Nothing matches", leading = RowLeading.None)
            }
        }
    }
}
