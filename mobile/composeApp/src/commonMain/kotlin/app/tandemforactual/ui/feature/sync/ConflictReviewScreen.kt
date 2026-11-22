@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sync
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.store.parseLocalWriteConflictDetails
import app.tandemforactual.ui.core.format.formatAmount
import app.tandemforactual.ui.core.format.formatDate
import app.tandemforactual.ui.core.format.formatSignedCurrency
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.conflicts.conflictReviewRevision
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.MerchantIconMatcherPack
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.core.state.formatLiveDate
import app.tandemforactual.ui.core.state.formatSignedCurrency
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.core.state.stringValue
import app.tandemforactual.ui.design.buttons.PrimaryButton
import app.tandemforactual.ui.design.buttons.SecondaryButton
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.BodyText
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.sheets.DestructiveConfirmationDialog
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.TriangleAlert
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

@Composable
internal fun ConflictReviewScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    onResolveConflict: (String, Boolean, String) -> Unit,
    liveTransactions: List<TransactionItem>,
    merchantIconMatcher: MerchantIconMatcherPack?,
) {
    val isLiveBudget = state.selectedBudgetId.isNotBlank()
    if (!isLiveBudget) {
        PageColumn {
            EmptyState(
                "Choose a budget",
                "Open an Actual budget to review pending changes.",
                actionLabel = "Choose budget",
                onAction = { navigation.open(AppRoute.BUDGET_CHOOSER) },
            )
        }
        return
    }
    val operation =
        mobileSession.pendingWrites.firstOrNull {
            it.operationId == state.selectedConflictOperationId && it.status == "conflict"
        }
    var reviewedRecordId by rememberSaveable(state.selectedConflictOperationId) { mutableStateOf<String?>(null) }
    var reviewedRevision by rememberSaveable(state.selectedConflictOperationId) { mutableStateOf<String?>(null) }
    var redirectedToLatestConflict by remember { mutableStateOf(false) }
    LaunchedEffect(operation?.recordId) {
        if (reviewedRecordId == null) reviewedRecordId = operation?.recordId
    }
    LaunchedEffect(operation?.operationId, state.selectedConflictOperationId, mobileSession.pendingWrites) {
        if (operation == null && !reviewedRecordId.isNullOrBlank()) {
            val latestConflict =
                conflictReplacementForRecord(
                    mobileSession.pendingWrites,
                    state.selectedConflictOperationId,
                    reviewedRecordId,
                )
            if (latestConflict != null) {
                redirectedToLatestConflict = true
                state.selectedConflictOperationId = latestConflict.operationId
            }
        }
    }
    var serverCheckAttempt by rememberSaveable(operation?.operationId) { mutableStateOf(0) }
    LaunchedEffect(operation?.operationId, serverCheckAttempt) {
        operation?.let {
            mobileSession.conflicts.confirmConflictWithServer(
                state.selectedBudgetId,
                it.operationId,
                state.encryptedPassword.takeIf(String::isNotBlank),
            )
        }
    }
    val serverConfirmed = operation != null && mobileSession.conflicts.serverConfirmedConflictOperationId == operation.operationId
    val record =
        operation?.let { item ->
            mobileSession.conflictRecordsByRecordId[item.recordId]
                ?: mobileSession.transactionRecords.firstOrNull { it.recordId == item.recordId }
        }
    LaunchedEffect(operation?.operationId, serverConfirmed, record?.remoteRevision, record?.remoteDeleted) {
        if (reviewedRevision == null) reviewedRevision = record?.let(::conflictReviewRevision)
    }
    val latestRevision = record?.let(::conflictReviewRevision)
    val reviewRevisionChanged = conflictReviewRevisionChanged(reviewedRevision, latestRevision)
    val canResolveReviewedVersion = canResolveReviewedConflict(reviewedRevision, latestRevision)
    val deviceVersion = operation?.optimisticJson?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
    val userEditedFields = operation?.patchJson?.let { runCatching { Json.parseToJsonElement(it).jsonObject.keys }.getOrNull() }
    val actualDeleted = record?.remoteDeleted == true
    val conflictDetails = operation?.lastErrorCode?.let(::parseLocalWriteConflictDetails)
    val conflictReason =
        if (serverConfirmed) {
            conflictReasonMessage(
                conflictType = conflictDetails?.conflictType,
                conflictingFields = conflictDetails?.conflictingFields.orEmpty(),
                action = operation.action,
                actualDeleted = actualDeleted,
            )
        } else {
            null
        }
    val actualVersion =
        if (!actualDeleted) {
            record?.remoteJson?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
        } else {
            null
        }
    val transaction = operation?.let { conflict -> liveTransactions.firstOrNull { it.recordId == conflict.recordId } }
    val transactionTitle = conflictPayeeTitle(operation, liveTransactions, mobileSession) ?: "Transaction"
    val categoryId = deviceVersion?.stringValue("category")
    val categoryName =
        transaction?.category
            ?: categoryId?.let { id -> mobileSession.categoryRecords.firstOrNull { it.recordId == id }?.recordName() }
            ?: if (deviceVersion != null && categoryId == null) "Uncategorized" else "Unavailable"
    val dateLabel =
        transaction?.dateLabel
            ?: deviceVersion?.stringValue("date")?.let(::formatLiveDate)
            ?: "Unavailable"
    val comparisons =
        if (deviceVersion != null && actualVersion != null) {
            conflictFieldComparisons(
                edited = deviceVersion,
                actual = actualVersion,
                names =
                    ConflictFieldNameLookup(
                        payees =
                            mobileSession.payeeRecords
                                .mapNotNull { record ->
                                    record.recordName()?.let { record.recordId to it }
                                }.toMap(),
                        accounts =
                            mobileSession.accountRecords
                                .mapNotNull { record ->
                                    record.recordName()?.let { record.recordId to it }
                                }.toMap(),
                        categories =
                            mobileSession.categoryRecords
                                .mapNotNull { record ->
                                    record.recordName()?.let { record.recordId to it }
                                }.toMap(),
                    ),
                formatAmount = { amount -> state.formatSignedCurrency(amount) },
                formatDate = ::formatLiveDate,
                userEditedFields = userEditedFields,
            )
        } else {
            emptyList()
        }
    val detailComparisons = comparisons.filterNot { it.label == "Amount" }
    val openConflicts = mobileSession.pendingWrites.filter { it.status == "conflict" }
    var keepThisDevice by rememberSaveable(operation?.operationId) { mutableStateOf(true) }
    var showComparison by rememberSaveable(operation?.operationId) { mutableStateOf(false) }
    var confirmKeepActual by rememberSaveable(operation?.operationId) { mutableStateOf(false) }
    PageColumn {
        if (operation == null) {
            val nextConflict = openConflicts.firstOrNull()
            val feedback =
                mobileSession.conflicts.conflictResolutionFeedback
                    ?.takeIf { it.operationId == state.selectedConflictOperationId }
                    ?.message
            SyncPageHeading(eyebrow = "Sync", title = "Choice saved.", detail = null)
            SyncStatusRing(icon = Lucide.Check, color = tandemPalette.positive, active = false, caption = null)
            BodyText(
                feedback
                    ?: if (nextConflict != null) {
                        "Your choice has been kept. ${if (openConflicts.size == 1) "One more change needs" else "${openConflicts.size} more changes need"} your review."
                    } else {
                        "Your choice has been kept."
                    },
            )
            if (nextConflict != null) {
                val nextTitle = conflictPayeeTitle(nextConflict, liveTransactions, mobileSession) ?: "Transaction"
                SurfaceCard {
                    ListRow("Next up", nextTitle, onClick = { state.selectedConflictOperationId = nextConflict.operationId })
                }
                PrimaryButton("Review next change") { state.selectedConflictOperationId = nextConflict.operationId }
            } else {
                PrimaryButton("Back to Sync Center") { navigation.open(AppRoute.SYNC_CENTER) }
            }
        } else {
            val position = openConflicts.indexOfFirst { it.operationId == operation.operationId } + 1
            SyncPageHeading(
                eyebrow = if (position > 0 && openConflicts.size > 1) "Change $position of ${openConflicts.size}" else "Needs review",
                title = "Choose what to keep.",
                detail =
                    if (actualDeleted) {
                        "You edited this transaction on this device, but it was deleted in Actual."
                    } else {
                        "This transaction changed on this device and in Actual."
                    },
            )
            TransactionDetailsCard(
                rows =
                    transactionDetailRows(
                        current = deviceVersion,
                        previous =
                            operation.baseSnapshotJson?.let {
                                runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull()
                            },
                        editedFields = userEditedFields.orEmpty(),
                        payeeName = { id -> mobileSession.payeeRecords.firstOrNull { it.recordId == id }?.recordName() },
                        accountName = { id -> mobileSession.accountRecords.firstOrNull { it.recordId == id }?.recordName() },
                        categoryName = { id -> mobileSession.categoryRecords.firstOrNull { it.recordId == id }?.recordName() },
                        formatAmount = state::formatSignedCurrency,
                    ),
            )
            when {
                mobileSession.isSyncing -> BodyText("Checking the latest transaction with Tandem…")
                serverConfirmed -> Unit
                actualVersion != null ->
                    BodyText("Showing the last synced Actual version. Tandem checks it again before applying your choice.")
                else -> {
                    BodyText("No Actual version is saved on this device yet. Connect to Tandem to load it before choosing.")
                    SecondaryButton("Check again", enabled = !mobileSession.isSavingTransaction) { serverCheckAttempt += 1 }
                }
            }
            if (redirectedToLatestConflict || reviewRevisionChanged) {
                ConflictNote(
                    icon = Lucide.TriangleAlert,
                    title = "Actual changed again",
                    detail = "Review the latest version before choosing.",
                )
                if (reviewRevisionChanged) {
                    SecondaryButton("Review latest version", enabled = !latestRevision.isNullOrBlank()) {
                        mobileSession.conflicts.clearConflictResolutionFeedback()
                        reviewedRevision = latestRevision
                    }
                }
            }
            val amountComparison = comparisons.firstOrNull { it.label == "Amount" }
            val categoryComparison = comparisons.firstOrNull { it.label == "Category" }

            fun amountOf(version: JsonObject?): String =
                (version?.get("amount") as? JsonPrimitive)?.longOrNull?.let(state::formatSignedCurrency) ?: "Unavailable"
            val keepThisDeviceEnabled =
                canResolveReviewedVersion &&
                    !state.readOnlyMode &&
                    !mobileSession.isSavingTransaction &&
                    (operation.action == "DELETE" || record?.remoteDeleted != true)
            val keepActualEnabled = canResolveReviewedVersion && !state.readOnlyMode && !mobileSession.isSavingTransaction
            val thisDeviceKeepable = operation.action == "DELETE" || record?.remoteDeleted != true
            LaunchedEffect(operation.operationId, thisDeviceKeepable) {
                if (!thisDeviceKeepable) keepThisDevice = false
            }
            val editedFields =
                userEditedFields
                    .orEmpty()
                    .mapNotNull { field ->
                        when (field) {
                            "amount" -> "Amount"
                            "category" -> "Category"
                            "payee" -> "Payee"
                            "date" -> "Date"
                            "notes" -> "Notes"
                            "account" -> "Account"
                            "cleared" -> "Cleared"
                            else -> null
                        }
                    }.distinct()
            val actualLabel = if (serverConfirmed) "Actual" else "Last synced Actual"
            ConflictVersionCard(
                label = "This device",
                amount = if (operation.action == "DELETE") "Delete" else amountComparison?.editedValue ?: amountOf(deviceVersion),
                detail =
                    when {
                        operation.action == "DELETE" -> "Remove this transaction from Actual"
                        !thisDeviceKeepable -> "Can’t be kept because Actual deleted this transaction"
                        editedFields.isNotEmpty() && "Category" !in editedFields ->
                            "${editedFields.joinToString(", ")} edited"
                        else -> "Category · ${categoryComparison?.editedValue ?: categoryName}"
                    },
                selected = keepThisDevice,
                enabled = keepThisDeviceEnabled,
            ) { keepThisDevice = true }
            ConflictVersionCard(
                label = actualLabel,
                amount = if (actualDeleted) "Deleted" else amountComparison?.actualValue ?: amountOf(actualVersion),
                detail =
                    when {
                        actualDeleted -> "Already removed in Actual"
                        actualVersion == null -> "Not loaded yet"
                        else -> "Category · ${categoryComparison?.actualValue ?: categoryName}"
                    },
                selected = !keepThisDevice,
                enabled = keepActualEnabled,
            ) { keepThisDevice = false }
            conflictReason?.let { BodyText(it) }
            if (comparisons.isNotEmpty()) {
                val chevronRotation by animateFloatAsState(
                    if (showComparison) 90f else 0f,
                    tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTROL_MILLIS),
                    label = "conflict-compare-chevron",
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(TandemShapes.medium))
                        .clickable(role = Role.Button) { showComparison = !showComparison }
                        .padding(vertical = TandemSpacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "What changed?",
                        Modifier.weight(1f),
                        color = tandemPalette.accent,
                        fontSize = TandemTypography.body,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Icon(
                        Lucide.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = chevronRotation },
                        tint = tandemPalette.accent,
                    )
                }
                AnimatedVisibility(visible = showComparison) {
                    ConflictCompareTable(comparisons, actualLabel)
                }
            }
            mobileSession.conflicts.conflictResolutionFeedback
                ?.takeIf { it.operationId == operation.operationId }
                ?.let { feedback -> BodyText(feedback.message) }
            Text(
                "Only this transaction will be replaced.",
                color = tandemPalette.muted,
                fontSize = TandemTypography.caption,
            )
            PrimaryButton(
                "Keep this version",
                enabled = if (keepThisDevice) keepThisDeviceEnabled else keepActualEnabled,
            ) {
                if (keepThisDevice) {
                    onResolveConflict(operation.operationId, true, reviewedRevision.orEmpty())
                } else {
                    confirmKeepActual = true
                }
            }
            if (confirmKeepActual) {
                DestructiveConfirmationDialog(
                    title = "Discard your edit?",
                    message = "Tandem will check Actual’s latest version, then remove your edit from this device. Actual won’t be changed.",
                    confirmLabel = "Discard edit",
                    dismissLabel = "Keep editing",
                    enabled = keepActualEnabled,
                    onDismissRequest = { confirmKeepActual = false },
                    onConfirm = {
                        confirmKeepActual = false
                        onResolveConflict(operation.operationId, false, reviewedRevision.orEmpty())
                    },
                )
            }
        }
    }
}
