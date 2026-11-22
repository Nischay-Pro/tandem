@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sync
import androidx.compose.runtime.Composable
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.database.OutboxOperationEntity
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
import app.tandemforactual.ui.design.feedback.BodyText
import app.tandemforactual.ui.design.feedback.EmptyStateCard
import app.tandemforactual.ui.design.templates.PageColumn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

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
            EmptyStateCard(
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
    val review = rememberConflictReviewState(state, operation)
    val serverConfirmed = operation != null && mobileSession.conflicts.serverConfirmedConflictOperationId == operation.operationId
    val record =
        operation?.let { item ->
            mobileSession.conflictRecordsByRecordId[item.recordId]
                ?: mobileSession.transactionRecords.firstOrNull { it.recordId == item.recordId }
        }
    ConflictReviewEffects(review, operation, record, serverConfirmed, state, mobileSession)
    val openConflicts = mobileSession.pendingWrites.filter { it.status == "conflict" }
    PageColumn {
        if (operation == null) {
            ConflictChoiceSaved(openConflicts, state, mobileSession, navigation, liveTransactions)
        } else {
            ConflictReviewContent(
                operation,
                record,
                serverConfirmed,
                review,
                openConflicts,
                state,
                mobileSession,
                liveTransactions,
                onResolveConflict,
            )
        }
    }
}

/** Review of one open conflict: summary, server status, the two versions to choose from, and the resolve action. */
@Composable
private fun ConflictReviewContent(
    operation: OutboxOperationEntity,
    record: BudgetRecordEntity?,
    serverConfirmed: Boolean,
    review: ConflictReviewState,
    openConflicts: List<OutboxOperationEntity>,
    state: AppState,
    mobileSession: TandemMobileSession,
    liveTransactions: List<TransactionItem>,
    onResolveConflict: (String, Boolean, String) -> Unit,
) {
    val latestRevision = record?.let(::conflictReviewRevision)
    val reviewRevisionChanged = conflictReviewRevisionChanged(review.reviewedRevision, latestRevision)
    val canResolveReviewedVersion = canResolveReviewedConflict(review.reviewedRevision, latestRevision)
    val deviceVersion = operation.optimisticJson?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
    val userEditedFields = operation.patchJson?.let { runCatching { Json.parseToJsonElement(it).jsonObject.keys }.getOrNull() }
    val actualDeleted = record?.remoteDeleted == true
    val actualVersion =
        if (!actualDeleted) {
            record?.remoteJson?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
        } else {
            null
        }
    val comparisons = conflictReviewComparisons(state, mobileSession, deviceVersion, actualVersion, userEditedFields)
    ConflictReviewSummary(operation, deviceVersion, userEditedFields, actualDeleted, openConflicts, state, mobileSession)
    ConflictReviewStatus(
        serverConfirmed = serverConfirmed,
        hasActualVersion = actualVersion != null,
        showRevisionNote = review.redirectedToLatestConflict || reviewRevisionChanged,
        reviewRevisionChanged = reviewRevisionChanged,
        latestRevision = latestRevision,
        mobileSession = mobileSession,
        onCheckAgain = { review.serverCheckAttempt += 1 },
        onReviewLatest = { review.reviewedRevision = latestRevision },
    )
    val keepThisDeviceEnabled =
        canResolveReviewedVersion &&
            !state.readOnlyMode &&
            !mobileSession.isSavingTransaction &&
            (operation.action == "DELETE" || record?.remoteDeleted != true)
    val keepActualEnabled = canResolveReviewedVersion && !state.readOnlyMode && !mobileSession.isSavingTransaction
    val actualLabel = if (serverConfirmed) "Actual" else "Last synced Actual"
    ConflictVersionChoice(
        operation = operation,
        record = record,
        review = review,
        deviceVersion = deviceVersion,
        actualVersion = actualVersion,
        actualDeleted = actualDeleted,
        userEditedFields = userEditedFields,
        comparisons = comparisons,
        categoryName = conflictCategoryName(operation, deviceVersion, liveTransactions, mobileSession),
        actualLabel = actualLabel,
        keepThisDeviceEnabled = keepThisDeviceEnabled,
        keepActualEnabled = keepActualEnabled,
        state = state,
    )
    conflictReviewReason(operation, serverConfirmed, actualDeleted)?.let { BodyText(it) }
    if (comparisons.isNotEmpty()) {
        ConflictComparisonToggle(operation.operationId, comparisons, actualLabel)
    }
    ConflictResolveActions(operation, review, keepThisDeviceEnabled, keepActualEnabled, mobileSession, onResolveConflict)
}

private fun conflictReviewReason(
    operation: OutboxOperationEntity,
    serverConfirmed: Boolean,
    actualDeleted: Boolean,
): String? {
    val conflictDetails = operation.lastErrorCode?.let(::parseLocalWriteConflictDetails)
    return if (serverConfirmed) {
        conflictReasonMessage(
            conflictType = conflictDetails?.conflictType,
            conflictingFields = conflictDetails?.conflictingFields.orEmpty(),
            action = operation.action,
            actualDeleted = actualDeleted,
        )
    } else {
        null
    }
}

private fun conflictCategoryName(
    operation: OutboxOperationEntity,
    deviceVersion: JsonObject?,
    liveTransactions: List<TransactionItem>,
    mobileSession: TandemMobileSession,
): String {
    val transaction = liveTransactions.firstOrNull { it.recordId == operation.recordId }
    val categoryId = deviceVersion?.stringValue("category")
    return transaction?.category
        ?: categoryId?.let { id -> mobileSession.categoryRecords.firstOrNull { it.recordId == id }?.recordName() }
        ?: if (deviceVersion != null && categoryId == null) "Uncategorized" else "Unavailable"
}

private fun conflictReviewComparisons(
    state: AppState,
    mobileSession: TandemMobileSession,
    deviceVersion: JsonObject?,
    actualVersion: JsonObject?,
    userEditedFields: Set<String>?,
): List<ConflictFieldComparison> =
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
