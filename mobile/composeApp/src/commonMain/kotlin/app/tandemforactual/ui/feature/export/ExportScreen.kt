@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.export

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
import app.tandemforactual.data.planning.JurisdictionPeriod
import app.tandemforactual.data.planning.PlanningSettings
import app.tandemforactual.data.planning.Reimbursement
import app.tandemforactual.data.planning.RetirementProfile
import app.tandemforactual.data.planning.SavedSearch
import app.tandemforactual.data.planning.SavingsGoal
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.platform.rememberFileSharer
import app.tandemforactual.ui.core.platform.rememberStatementFilePicker
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.inputs.FilterPills
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
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
                val count = mobileSession.planning.restore(state.selectedBudgetId, records)
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
                    val (from, to) = exportRangeDates(range, state.selectedMonthId, year)
                    exporting = true
                    scope.launch {
                        val csv =
                            mobileSession.insights.exportTransactions(
                                state.selectedBudgetId,
                                from,
                                to,
                                state.encryptedPassword.takeIf { it.isNotBlank() },
                            )
                        exporting = false
                        if (csv == null) {
                            message = "Couldn’t export. Open the budget and try again." to InlineMessageKind.ERROR
                        } else {
                            val name = exportFileStem(state.selectedBudget)
                            share("$name-${range.name.lowercase().replace('_', '-')}.csv", "text/csv", csv)
                        }
                    }
                }
            }
        }
        PlanningBackupSection(
            onBackup = { share("tandem-planning-$today.json", "application/json", planningBackup(mobileSession)) },
            onRestore = { restorePicker() },
        )
    }
}

/** Back up or restore the planning data Actual doesn't hold. */
@Composable
private fun PlanningBackupSection(
    onBackup: () -> Unit,
    onRestore: () -> Unit,
) {
    SectionCard("Planning data", "Goals, debt details, reimbursements, reports and saved searches live outside Actual") {
        Column(Modifier.padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
            Text(
                "The backup is plain JSON, not encrypted. Keep it somewhere private.",
                color = tandemPalette.muted,
                fontSize = TandemTypography.label,
            )
            TandemActionButton("Back up", TandemActionButtonTone.PRIMARY, Modifier.fillMaxWidth(), icon = Lucide.Share2, onClick = onBackup)
            TandemActionButton(
                "Restore a backup",
                TandemActionButtonTone.NEUTRAL,
                Modifier.fillMaxWidth(),
                icon = Lucide.ArchiveRestore,
                onClick = onRestore,
            )
        }
    }
}

private fun exportRangeDates(
    range: ExportRange,
    selectedMonthId: String,
    year: Int,
): Pair<String, String> =
    when (range) {
        ExportRange.MONTH ->
            "$selectedMonthId-01" to
                ActualDates.addDays("$selectedMonthId-01", 40).take(7).let { ActualDates.addDays("$it-01", -1) }
        ExportRange.YEAR -> "$year-01-01" to "$year-12-31"
        ExportRange.LAST_YEAR -> "${year - 1}-01-01" to "${year - 1}-12-31"
        ExportRange.ALL -> "1900-01-01" to "2999-12-31"
    }

private fun exportFileStem(budgetName: String): String =
    budgetName
        .ifBlank { "budget" }
        .replace(Regex("[^A-Za-z0-9]+"), "-")
        .trim('-')
        .lowercase()

private fun planningBackup(session: TandemMobileSession): String {
    val records =
        session.planning.savingsGoals.map { Triple(it.id, SavingsGoal.TYPE, it.toJson()) } +
            session.planning.debtTerms.map { Triple(it.id, DebtTerms.TYPE, it.toJson()) } +
            session.planning.retirementProfiles.map { Triple(it.planId, RetirementProfile.TYPE, it.toJson()) } +
            session.planning.reimbursements.map { Triple(it.transactionId, Reimbursement.TYPE, it.toJson()) } +
            session.planning.customReports.map { Triple(it.id, CustomReport.TYPE, it.toJson()) } +
            session.planning.savedSearches.map { Triple(it.id, SavedSearch.TYPE, it.toJson()) } +
            session.planning.jurisdictionPeriods.map { Triple(it.id, JurisdictionPeriod.TYPE, it.toJson()) } +
            listOf(Triple(PlanningSettings.ID, PlanningSettings.TYPE, session.planning.settings.toJson()))
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
