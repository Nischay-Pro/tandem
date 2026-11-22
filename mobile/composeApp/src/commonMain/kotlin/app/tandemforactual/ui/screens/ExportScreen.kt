@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.tandemforactual.data.actual.ActualDates
import app.tandemforactual.data.planning.CustomReport
import app.tandemforactual.data.planning.DebtTerms
import app.tandemforactual.data.planning.PlanningSettings
import app.tandemforactual.data.planning.Reimbursement
import app.tandemforactual.data.planning.RetirementProfile
import app.tandemforactual.data.planning.SavingsGoal
import app.tandemforactual.ui.components.FilterPills
import app.tandemforactual.ui.components.InlineMessage
import app.tandemforactual.ui.components.InlineMessageKind
import app.tandemforactual.ui.components.SectionCard
import app.tandemforactual.ui.components.TandemActionButton
import app.tandemforactual.ui.components.TandemActionButtonTone
import com.composables.icons.lucide.ArchiveRestore
import com.composables.icons.lucide.FileDown
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Share2
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private enum class ExportRange(
    val label: String,
) {
    MONTH("This month"),
    YEAR("This year"),
    LAST_YEAR("Last year"),
    ALL("Everything"),
}

private const val BACKUP_FORMAT = "tandem-planning-backup"

/** Exports transactions as CSV, and backs up or restores the planning data Actual doesn't hold. */
@Composable
internal fun ExportScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
) {
    val scope = rememberCoroutineScope()
    val share = rememberFileSharer()
    var range by rememberSaveable { mutableStateOf(ExportRange.YEAR) }
    var exporting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<String, InlineMessageKind>?>(null) }
    val today = platformTodayIsoDate()
    val year = today.take(4).toInt()
    val restorePicker =
        rememberStatementFilePicker { file ->
            val contents = file.contents
            val records = contents?.let(::readPlanningBackup)
            if (records == null) {
                message = "That file isn’t a Tandem planning backup." to InlineMessageKind.ERROR
                return@rememberStatementFilePicker
            }
            scope.launch {
                val count = mobileSession.restorePlanning(state.selectedBudgetId, records)
                message = "Restored $count planning records." to InlineMessageKind.SUCCESS
            }
        }
    PageColumn(verticalSpacing = TandemSpacing.large) {
        message?.let { (text, kind) -> InlineMessage(text, kind, onDismiss = { message = null }) }
        SectionCard("Transactions", "A CSV file that opens in any spreadsheet") {
            Column(Modifier.padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                FilterPills(ExportRange.entries.map { it to it.label }, range, { range = it })
                TandemActionButton(
                    "Export CSV",
                    TandemActionButtonTone.PRIMARY,
                    Modifier.fillMaxWidth(),
                    icon = Lucide.FileDown,
                    loading = exporting,
                ) {
                    val (from, to) =
                        when (range) {
                            ExportRange.MONTH ->
                                "${state.selectedMonthId}-01" to
                                    ActualDates.addDays("${state.selectedMonthId}-01", 40).take(7).let { ActualDates.addDays("$it-01", -1) }
                            ExportRange.YEAR -> "$year-01-01" to "$year-12-31"
                            ExportRange.LAST_YEAR -> "${year - 1}-01-01" to "${year - 1}-12-31"
                            ExportRange.ALL -> "1900-01-01" to "2999-12-31"
                        }
                    exporting = true
                    scope.launch {
                        val csv =
                            mobileSession.exportTransactions(
                                state.selectedBudgetId,
                                from,
                                to,
                                state.encryptedPassword.takeIf { it.isNotBlank() },
                            )
                        exporting = false
                        if (csv == null) {
                            message = "Couldn’t export. Open the budget and try again." to InlineMessageKind.ERROR
                        } else {
                            val name =
                                state.selectedBudget
                                    .ifBlank { "budget" }
                                    .replace(Regex("[^A-Za-z0-9]+"), "-")
                                    .trim('-')
                                    .lowercase()
                            share("$name-${range.name.lowercase().replace('_', '-')}.csv", "text/csv", csv)
                        }
                    }
                }
            }
        }
        SectionCard("Planning data", "Goals, debt details, reimbursements and reports live outside Actual") {
            Column(Modifier.padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                Text(
                    "The backup is plain JSON, not encrypted. Keep it somewhere private.",
                    color = tandemPalette.muted,
                    fontSize = TandemTypography.label,
                )
                TandemActionButton("Back up", TandemActionButtonTone.PRIMARY, Modifier.fillMaxWidth(), icon = Lucide.Share2) {
                    share("tandem-planning-$today.json", "application/json", planningBackup(mobileSession))
                }
                TandemActionButton(
                    "Restore a backup",
                    TandemActionButtonTone.NEUTRAL,
                    Modifier.fillMaxWidth(),
                    icon = Lucide.ArchiveRestore,
                ) {
                    restorePicker()
                }
            }
        }
    }
}

private fun planningBackup(session: TandemMobileSession): String {
    val records =
        session.savingsGoals.map { Triple(it.id, SavingsGoal.TYPE, it.toJson()) } +
            session.debtTerms.map { Triple(it.id, DebtTerms.TYPE, it.toJson()) } +
            session.retirementProfiles.map { Triple(it.planId, RetirementProfile.TYPE, it.toJson()) } +
            session.reimbursements.map { Triple(it.transactionId, Reimbursement.TYPE, it.toJson()) } +
            session.customReports.map { Triple(it.id, CustomReport.TYPE, it.toJson()) } +
            listOf(Triple(PlanningSettings.ID, PlanningSettings.TYPE, session.planningSettings.toJson()))
    return buildJsonObject {
        put("format", BACKUP_FORMAT)
        put("version", 1)
        put(
            "records",
            JsonArray(
                records.map { (id, type, body) ->
                    buildJsonObject {
                        put("id", id)
                        put("type", type)
                        put("body", body)
                    }
                },
            ),
        )
    }.toString()
}

internal fun readPlanningBackup(contents: String): List<Triple<String, String, JsonObject>>? =
    runCatching {
        val root = Json.parseToJsonElement(contents).jsonObject
        if ((root["format"] as? JsonPrimitive)?.content != BACKUP_FORMAT) return null
        root
            .getValue("records")
            .jsonArray
            .map { element ->
                val record = element.jsonObject
                Triple(
                    record.getValue("id").jsonPrimitive.content,
                    record.getValue("type").jsonPrimitive.content,
                    record.getValue("body").jsonObject,
                )
            }.filter { (_, type, _) -> type.startsWith("planning.") }
    }.getOrNull()
