@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sync
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.format.formatMomentAtEpochMilliseconds
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.deviceWriteHistory
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.deviceWriteActionLabel
import app.tandemforactual.ui.core.state.deviceWriteStatusLabel
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.theme.tandemPalette
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

private const val DEVICE_HISTORY_PREVIEW = 8

/** Transaction changes sent from this device in the last 90 days, collapsed to a short preview. */
@Composable
internal fun DeviceWriteHistory(
    state: AppState,
    mobileSession: TandemMobileSession,
) {
    val budgetId = state.selectedBudgetId
    val history by produceState(emptyList<OutboxOperationEntity>(), budgetId, mobileSession.pendingWrites, mobileSession.localSyncState) {
        value = mobileSession.budgetEdits.deviceWriteHistory(budgetId)
    }
    var showAll by rememberSaveable(budgetId) { mutableStateOf(false) }
    if (history.isEmpty()) return
    val payeeNames = remember(mobileSession.payeeRecords) { mobileSession.payeeRecords.associate { it.recordId to it.recordName() } }
    val now = mobileSession.nowEpochMillis()
    SectionCard("Sent from this device", "Your transaction changes from the last 90 days") {
        val shown = if (showAll) history else history.take(DEVICE_HISTORY_PREVIEW)
        shown.forEachIndexed { index, write ->
            if (index > 0) DividerLine()
            val body =
                runCatching {
                    Json
                        .parseToJsonElement(
                            write.optimisticJson ?: write.baseSnapshotJson ?: "{}",
                        ).jsonObject
                }.getOrNull()
            val payee = (body?.get("payee") as? JsonPrimitive)?.contentOrNull?.let { payeeNames[it] }
            val amount = (body?.get("amount") as? JsonPrimitive)?.longOrNull
            ListRow(
                "${deviceWriteActionLabel(write.action)} ${payee ?: "a transaction"}",
                "${deviceWriteStatusLabel(
                    write.status,
                    write.lastErrorCode,
                )} · ${formatMomentAtEpochMilliseconds(write.updatedAtEpochMs, now)}",
                amount?.let { state.formatCurrency(it) },
                leading = RowLeading.None,
                options = ListRowOptions(dense = true, trailingColor = tandemPalette.muted),
            )
        }
        if (history.size > DEVICE_HISTORY_PREVIEW) {
            DividerLine()
            ListRow(
                if (showAll) "Show fewer" else "Show all ${history.size}",
                leading = RowLeading.None,
                options = ListRowOptions(dense = true),
                onClick = { showAll = !showAll },
            )
        }
    }
}
