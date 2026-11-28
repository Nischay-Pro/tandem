@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.planning.MonthStart
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.booleanValue
import app.tandemforactual.ui.core.state.recordBody
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.core.state.startPageChoices
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.ToggleRow
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.WalletCards

/** Open accounts a new transaction can start in, as (id, name). */
internal fun defaultAccountChoices(accounts: List<BudgetRecordEntity>): List<Pair<String, String>> =
    accounts.mapNotNull { record ->
        val open = !record.localDeleted && record.recordBody()?.booleanValue("closed") != true
        record.recordName()?.takeIf { open }?.let { record.recordId to it }
    }

/** Which tab the app opens on and which account new transactions start in. */
@Composable
internal fun StartPreferencesSection(
    state: AppState,
    mobileSession: TandemMobileSession,
) {
    val accounts = defaultAccountChoices(mobileSession.accountRecords)
    SectionCard("Start and new transactions") {
        ChoiceRow(
            "Open on",
            Lucide.House,
            startPageChoices.map { it.name to it.title },
            state.startPageName.ifBlank { startPageChoices.first().name },
        ) { state.startPageName = it }
        DividerLine()
        ChoiceRow(
            "New transactions go to",
            Lucide.WalletCards,
            listOf("" to "The last account used") + accounts,
            state.defaultTransactionAccountId.takeIf { id -> accounts.any { it.first == id } }.orEmpty(),
        ) { state.defaultTransactionAccountId = it }
    }
}

/** When reports and insights start each month, for budgets that run from payday to payday. */
@Composable
internal fun MonthStartSection(state: AppState) {
    SectionCard("Reports and insights", "Spending trends and custom reports use this month") {
        ChoiceRow(
            "Month starts on",
            Lucide.CalendarDays,
            (1..MonthStart.MAX_DAY).map { it.toString() to monthStartLabel(it) },
            state.monthStartDay.toString(),
        ) { state.monthStartDay = it.toInt() }
        DividerLine()
        ToggleRow(
            "Start on a weekday",
            "A month that would start on a weekend starts on the Friday before",
            state.monthStartPreviousWorkingDay,
        ) { state.monthStartPreviousWorkingDay = it }
    }
}

internal fun monthStartLabel(day: Int): String {
    val suffix =
        when {
            day in 11..13 -> "th"
            day % 10 == 1 -> "st"
            day % 10 == 2 -> "nd"
            day % 10 == 3 -> "rd"
            else -> "th"
        }
    return if (day == 1) "The 1st (calendar month)" else "The $day$suffix"
}

@Composable
private fun ChoiceRow(
    title: String,
    icon: ImageVector,
    choices: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ListRow(
            title,
            trailing = choices.firstOrNull { it.first == selected }?.second,
            leading = RowLeading.Icon(icon),
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { (key, label) ->
                DropdownMenuItem(onClick = {
                    onSelect(key)
                    expanded = false
                }) {
                    Text(label, color = tandemPalette.text, fontSize = TandemTypography.compactBody)
                }
            }
        }
    }
}
