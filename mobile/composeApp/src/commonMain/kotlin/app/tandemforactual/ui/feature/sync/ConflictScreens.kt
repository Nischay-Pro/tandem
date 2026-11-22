@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sync
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.ui.core.format.formatAmount
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.core.state.formatLiveDate
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.core.state.stringValue
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.sheets.DestructiveConfirmationDialog
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

internal fun conflictSnapshot(operation: OutboxOperationEntity?): JsonObject? =
    (operation?.optimisticJson ?: operation?.baseSnapshotJson)
        ?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }

internal fun conflictReviewRevisionChanged(
    reviewedRevision: String?,
    latestRevision: String?,
): Boolean =
    !reviewedRevision.isNullOrBlank() &&
        !latestRevision.isNullOrBlank() &&
        reviewedRevision != latestRevision

internal fun canResolveReviewedConflict(
    reviewedRevision: String?,
    latestRevision: String?,
): Boolean =
    !reviewedRevision.isNullOrBlank() &&
        reviewedRevision == latestRevision

internal fun conflictReplacementForRecord(
    pendingWrites: List<OutboxOperationEntity>,
    selectedOperationId: String?,
    recordId: String?,
): OutboxOperationEntity? =
    if (recordId.isNullOrBlank()) {
        null
    } else {
        pendingWrites.firstOrNull {
            it.status == "conflict" && it.recordId == recordId && it.operationId != selectedOperationId
        }
    }

internal fun conflictPayeeTitle(
    operation: OutboxOperationEntity?,
    liveTransactions: List<TransactionItem>,
    mobileSession: TandemMobileSession,
): String? {
    val transaction = operation?.let { write -> liveTransactions.firstOrNull { it.recordId == write.recordId } }
    if (transaction != null) return transaction.payee
    val snapshot = conflictSnapshot(operation) ?: return null
    return snapshot.stringValue("importedPayee")?.takeIf(String::isNotBlank)
        ?: snapshot.stringValue("payee")?.let { payeeId ->
            mobileSession.payeeRecords.firstOrNull { it.recordId == payeeId }?.recordName()
        }
}

internal fun canOfferUncertainWriteRecoveryActions(
    operationStatus: String,
    statusCheckFinished: Boolean,
): Boolean = statusCheckFinished && operationStatus == "uncertain"

internal fun isUncertainWriteOperationNotFound(errorCode: String?): Boolean = errorCode == "operation_not_found"

internal fun isBudgetMutationReviewable(status: String): Boolean = status in setOf("queued", "sending", "uncertain", "conflict", "rejected")

internal fun uncertainWriteRemovalWarning(
    payee: String,
    amount: String,
): String =
    "Remove the pending edit for $payee ($amount)? Actual may already have saved it; removing it here won’t undo a change already stored there."

@Composable
internal fun ConflictNote(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TandemShapes.large))
            .background(tandemPalette.raised)
            .padding(TandemSpacing.large),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = tandemPalette.warning)
        Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall)) {
            Text(title, color = tandemPalette.text, fontSize = TandemTypography.body, fontWeight = FontWeight.SemiBold)
            Text(detail, color = tandemPalette.muted, fontSize = TandemTypography.caption)
        }
    }
}

internal data class ConflictInboxItemUi(
    val id: String,
    val title: String,
    val description: String,
    val merchantIcon: org.jetbrains.compose.resources.DrawableResource?,
    val categoryIcon: androidx.compose.ui.graphics.vector.ImageVector?,
)

@Composable
internal fun ConflictValueCard(
    label: String,
    amount: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(TandemShapes.medium)).background(tandemPalette.raised).padding(TandemSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
    ) {
        Text(label, color = tandemPalette.muted, fontSize = TandemTypography.micro)
        Text(amount, color = tandemPalette.text, fontSize = TandemTypography.cardTitle, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun ConflictComparisonCard(
    comparisons: List<ConflictFieldComparison>,
    actualLabel: String = "Actual",
) {
    SectionCard("Changed details") {
        comparisons.forEachIndexed { index, comparison ->
            if (index > 0) DividerLine()
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
            ) {
                Text(comparison.label, color = tandemPalette.text, fontSize = TandemTypography.label, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
                        Text("Your edit", color = tandemPalette.muted, fontSize = TandemTypography.micro)
                        Text(comparison.editedValue, color = tandemPalette.text, fontSize = TandemTypography.compactBody)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
                        Text(actualLabel, color = tandemPalette.muted, fontSize = TandemTypography.micro)
                        Text(comparison.actualValue, color = tandemPalette.text, fontSize = TandemTypography.compactBody)
                    }
                }
            }
        }
    }
}

@Composable
internal fun KeepLastSyncedConflictAction(
    operationId: String,
    reviewedRevision: String,
    enabled: Boolean,
    onResolveConflict: (String, Boolean, String) -> Unit,
) {
    var confirmDiscard by rememberSaveable(operationId) { mutableStateOf(false) }
    TandemActionButton(
        "Keep Actual’s version",
        tone = TandemActionButtonTone.DESTRUCTIVE,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        onClick = { confirmDiscard = true },
    )
    Text(
        "Discards your edit and keeps Actual’s version. Tandem must confirm it first.",
        color = tandemPalette.muted,
        fontSize = TandemTypography.caption,
    )
    if (confirmDiscard) {
        DestructiveConfirmationDialog(
            title = "Discard your edit?",
            message = "Tandem will check Actual’s latest version, then remove your edit from this device. Actual won’t be changed.",
            confirmLabel = "Discard edit",
            dismissLabel = "Keep editing",
            enabled = enabled,
            onDismissRequest = { confirmDiscard = false },
            onConfirm = {
                confirmDiscard = false
                onResolveConflict(operationId, false, reviewedRevision)
            },
        )
    }
}

internal data class TransactionDetailRow(
    val label: String,
    val value: String,
    val previousValue: String?,
)

internal fun transactionDetailRows(
    current: JsonObject?,
    previous: JsonObject?,
    editedFields: Collection<String>,
    payeeName: (String) -> String?,
    accountName: (String) -> String?,
    categoryName: (String) -> String?,
    formatAmount: (Long) -> String,
): List<TransactionDetailRow> {
    if (current == null) return emptyList()

    fun text(
        version: JsonObject?,
        field: String,
    ): String? {
        val value = version?.get(field) as? JsonPrimitive ?: return null
        if (value.contentOrNull == null) return null
        return when (field) {
            "payee" -> value.contentOrNull?.let { payeeName(it) ?: "Unknown payee" }
            "account" -> value.contentOrNull?.let { accountName(it) ?: "Unknown account" }
            "category" -> value.contentOrNull?.let { categoryName(it) ?: "Uncategorized" }
            "amount" -> value.longOrNull?.let(formatAmount)
            "date" -> value.contentOrNull?.let(::formatLiveDate)
            else -> value.contentOrNull?.takeIf(String::isNotBlank)
        }
    }
    return listOf(
        "payee" to "Payee",
        "account" to "Account",
        "date" to "Date",
        "amount" to "Amount",
        "category" to "Category",
        "notes" to "Notes",
    ).mapNotNull { (field, label) ->
        val value = text(current, field)
        val edited = field in editedFields
        if (value == null && !edited) return@mapNotNull null
        TransactionDetailRow(
            label = label,
            value = value ?: "None",
            previousValue = if (edited) text(previous, field) ?: "None" else null,
        )
    }
}

@Composable
internal fun TransactionDetailsCard(rows: List<TransactionDetailRow>) {
    if (rows.isEmpty()) return
    val palette = tandemPalette
    SurfaceCard {
        Column(Modifier.fillMaxWidth().padding(vertical = TandemSpacing.extraSmall)) {
            rows.forEachIndexed { index, row ->
                if (index > 0) DividerLine()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
                    horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                ) {
                    Text(row.label, Modifier.width(88.dp), color = palette.muted, fontSize = TandemTypography.compactBody)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall)) {
                        Text(
                            row.value,
                            color = if (row.previousValue != null) palette.accent else palette.text,
                            fontSize = TandemTypography.compactBody,
                            fontWeight = if (row.previousValue != null) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        row.previousValue?.let {
                            Text("Edited on this device · was $it", color = palette.muted, fontSize = TandemTypography.caption)
                        }
                    }
                }
            }
        }
    }
}
