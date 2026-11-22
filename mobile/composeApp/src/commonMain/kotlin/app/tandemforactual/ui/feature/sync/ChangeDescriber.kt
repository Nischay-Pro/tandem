@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sync
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.data.actual.ChangeSet
import app.tandemforactual.data.actual.CrdtValue
import app.tandemforactual.data.actual.RowChange
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.formatLiveDate

/** Turns raw cell changes into plain sentences using the budget's names and money format. */
internal class ChangeDescriber(
    private val names: Map<String, String>,
    private val state: AppState,
) {
    fun title(set: ChangeSet): String {
        val rows = set.rows
        val kinds = rows.map { it.dataset }.distinct()
        if (rows.size > 1 && kinds.size == 1) return "${verb(rows)} ${rows.size} ${noun(kinds.single(), plural = true)}"
        if (rows.size > 1) return "Changed ${rows.size} items"
        val row = rows.single()
        val subject = subject(row)
        return when {
            row.created -> "Added ${noun(row.dataset)}${subject?.let { " $it" }.orEmpty()}"
            row.deleted -> "Deleted ${noun(row.dataset)}${subject?.let { " $it" }.orEmpty()}"
            else -> {
                val fields = row.fields.map { label(it.column).lowercase() }.distinct()
                "Changed ${fields.take(2).joinToString(" and ")}${if (fields.size > 2) " and more" else ""}" +
                    (subject?.let { " on $it" } ?: " on a ${noun(row.dataset)}")
            }
        }
    }

    fun details(row: RowChange): List<Pair<String, String>> =
        row.fields
            .filter { it.column !in HIDDEN_COLUMNS }
            .map { field ->
                val after = format(field.column, field.after)
                label(field.column) to (field.before?.let { "${format(field.column, it)} → $after" } ?: after)
            }

    private fun verb(rows: List<RowChange>) =
        when {
            rows.all { it.created } -> "Added"
            rows.all { it.deleted } -> "Deleted"
            else -> "Changed"
        }

    private fun subject(row: RowChange): String? {
        if (row.dataset == "transactions") {
            val payee = row.current["description"]?.let { names[it] }
            val amount =
                row.current["amount"]
                    ?.toDoubleOrNull()
                    ?.toLong()
                    ?.let(state::formatCurrency)
            return listOfNotNull(payee, amount).joinToString(" · ").ifBlank { null }
        }
        return row.fields
            .firstOrNull { it.column == "name" }
            ?.after
            ?.text() ?: row.current["name"] ?: names[row.row]
    }

    private fun noun(
        dataset: String,
        plural: Boolean = false,
    ): String {
        val word =
            when (dataset) {
                "transactions" -> "transaction"
                "payees" -> "payee"
                "categories" -> "category"
                "category_groups" -> "category group"
                "accounts" -> "account"
                "rules" -> "rule"
                "schedules" -> "schedule"
                "zero_budgets", "reflect_budgets" -> "budget amount"
                "notes" -> "note"
                else -> "item"
            }
        return if (plural) (if (word.endsWith("y")) word.dropLast(1) + "ies" else word + "s") else word
    }

    private fun label(column: String) =
        when (column) {
            "amount" -> "Amount"
            "budgeted" -> "Budgeted"
            "category" -> "Category"
            "description" -> "Payee"
            "acct" -> "Account"
            "notes", "note" -> "Notes"
            "date" -> "Date"
            "cleared" -> "Cleared"
            "reconciled" -> "Reconciled"
            "tombstone" -> "Deleted"
            "hidden" -> "Hidden"
            "name" -> "Name"
            "carryover" -> "Rollover"
            "goal" -> "Goal"
            "cat_group" -> "Group"
            "is_income" -> "Income"
            "offbudget" -> "Off budget"
            "closed" -> "Closed"
            "transfer_acct" -> "Transfer account"
            "imported_description" -> "Imported payee"
            else -> column.replace('_', ' ').replaceFirstChar(Char::uppercaseChar)
        }

    private fun format(
        column: String,
        value: CrdtValue,
    ): String =
        when {
            value == CrdtValue.Null -> "None"
            column in MONEY_COLUMNS -> (value as? CrdtValue.Number)?.let { state.formatCurrency(it.value.toLong()) } ?: value.display()
            column == "date" ->
                (value as? CrdtValue.Number)?.value?.toLong()?.toString()?.let {
                    if (it.length ==
                        8
                    ) {
                        formatLiveDate("${it.take(4)}-${it.substring(4, 6)}-${it.takeLast(2)}")
                    } else {
                        it
                    }
                }
                    ?: value.display()
            column in FLAG_COLUMNS -> if ((value as? CrdtValue.Number)?.value == 1.0) "Yes" else "No"
            else -> value.text()?.let { names[it] ?: it } ?: value.display()
        }

    private fun CrdtValue.text() = (this as? CrdtValue.Text)?.value

    private fun CrdtValue.display(): String =
        when (this) {
            is CrdtValue.Number -> formatNumberText(value)
            is CrdtValue.Text -> value
            else -> "—"
        }

    private fun formatNumberText(value: Double) = if (value == kotlin.math.floor(value)) value.toLong().toString() else value.toString()

    private companion object {
        val MONEY_COLUMNS = setOf("amount", "budgeted", "goal", "long_goal")
        val FLAG_COLUMNS =
            setOf("cleared", "reconciled", "tombstone", "hidden", "is_income", "offbudget", "closed", "carryover", "starting_balance_flag")
        val HIDDEN_COLUMNS = setOf("sort_order", "parent_id", "isChild", "isParent", "is_parent", "is_child")
    }
}
