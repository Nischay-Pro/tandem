@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.ui.components.BodyText
import app.tandemforactual.ui.components.DestructiveConfirmationDialog
import app.tandemforactual.ui.components.DividerLine
import app.tandemforactual.ui.components.EmptyState
import app.tandemforactual.ui.components.ListRow
import app.tandemforactual.ui.components.ListRowOptions
import app.tandemforactual.ui.components.PrimaryButton
import app.tandemforactual.ui.components.RowLeading
import app.tandemforactual.ui.components.SecondaryButton
import app.tandemforactual.ui.components.SectionCard
import app.tandemforactual.ui.components.SurfaceCard
import app.tandemforactual.ui.model.longValue
import app.tandemforactual.ui.model.recordName
import app.tandemforactual.ui.model.stringValue
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Receipt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

internal fun conflictSnapshot(operation: OutboxOperationEntity?): JsonObject? =
    (operation?.optimisticJson ?: operation?.baseSnapshotJson)
        ?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }

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
    statusCheckSucceeded: Boolean,
): Boolean = statusCheckSucceeded && operationStatus in setOf("uncertain", "queued")

internal fun uncertainWriteRemovalWarning(
    payee: String,
    amount: String,
): String =
    "Remove the pending edit for $payee ($amount)? Actual may already have saved it; removing it here won’t undo a change already stored there."

@Composable
internal fun UncertainWriteRecoveryActions(
    showCheckAgain: Boolean,
    showRetryAndRemove: Boolean,
    isSyncing: Boolean,
    readOnly: Boolean,
    onCheckAgain: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    when {
        showCheckAgain ->
            PrimaryButton(
                "Check again",
                enabled = !isSyncing,
                loading = isSyncing,
                onClick = onCheckAgain,
            )
        showRetryAndRemove ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                PrimaryButton(
                    "Try again",
                    enabled = !readOnly && !isSyncing,
                    modifier = Modifier.weight(1f),
                    onClick = onRetry,
                )
                SecondaryButton(
                    "Remove edit",
                    modifier = Modifier.weight(1f),
                    enabled = !readOnly && !isSyncing,
                    onClick = onRemove,
                )
            }
    }
}

@Composable
internal fun ConflictInboxScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
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
    val entryPointerSequence = remember(state.selectedBudgetId) { state.pointerInputSequence }
    val canOpenConflict = state.pointerInputSequence > entryPointerSequence
    val conflicts =
        mobileSession.pendingWrites
            .filter { it.status == "conflict" }
            .map { operation ->
                val transaction = liveTransactions.firstOrNull { it.recordId == operation.recordId }
                val snapshot = conflictSnapshot(operation)
                val categoryId = snapshot?.stringValue("category")
                val categoryName =
                    transaction?.category
                        ?: categoryId?.let { id ->
                            mobileSession.categoryRecords.firstOrNull { it.recordId == id }?.recordName()
                        }
                        ?: "Uncategorized"
                ConflictInboxItemUi(
                    operation.operationId,
                    conflictPayeeTitle(operation, liveTransactions, mobileSession) ?: "Transaction",
                    "${transaction?.dateLabel ?: formatLiveDate(snapshot?.stringValue("date"))} · $categoryName",
                    transaction?.merchantIcon,
                    transaction?.categoryIcon,
                )
            }
    PageLazyColumn {
        item(key = "conflicts-title") {
            BodyText("Choose between your edit and the latest version in Actual.")
        }
        if (conflicts.isEmpty()) {
            item(key = "conflicts-empty") {
                AnimatedAttentionContent(showingAttention = false, animateSizeChanges = true) { _ ->
                    SurfaceCard(modifier = tandemListItemModifier(tandemReduceMotion)) {
                        EmptyState("No changes need review", modifier = Modifier.padding(TandemSpacing.large))
                    }
                }
            }
        } else {
            items(conflicts, key = { "conflict:${it.id}" }) { conflict ->
                SurfaceCard(modifier = tandemListItemModifier(tandemReduceMotion)) {
                    val merchantIcon = conflict.merchantIcon ?: merchantIconMatcher?.iconFor(conflict.title)
                    ListRow(
                        conflict.title,
                        conflict.description,
                        "Review",
                        leading =
                            RowLeading.Merchant(
                                merchantIcon,
                                fallback = conflict.categoryIcon ?: Lucide.Receipt,
                            ),
                        options = ListRowOptions(trailingColor = tandemPalette.warning),
                        onClick =
                            if (canOpenConflict) {
                                {
                                    mobileSession.clearTransactionWriteMessage()
                                    state.selectedConflictOperationId = conflict.id
                                    navigation.open(AppRoute.CONFLICT_REVIEW)
                                }
                            } else {
                                null
                            },
                    )
                }
            }
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
internal fun ConflictComparisonCard(comparisons: List<ConflictFieldComparison>) {
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
                        Text("Actual", color = tandemPalette.muted, fontSize = TandemTypography.micro)
                        Text(comparison.actualValue, color = tandemPalette.text, fontSize = TandemTypography.compactBody)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ConflictReviewScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    onResolveConflict: (String, Boolean) -> Unit,
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
    var serverCheckAttempt by rememberSaveable(operation?.operationId) { mutableStateOf(0) }
    LaunchedEffect(operation?.operationId, serverCheckAttempt) {
        operation?.let {
            mobileSession.confirmConflictWithServer(
                state.selectedBudgetId,
                it.operationId,
                state.encryptedPassword.takeIf(String::isNotBlank),
            )
        }
    }
    val serverConfirmed = operation != null && mobileSession.serverConfirmedConflictOperationId == operation.operationId
    val record =
        operation?.let { item ->
            mobileSession.conflictRecordsByRecordId[item.recordId]
                ?: mobileSession.transactionRecords.firstOrNull { it.recordId == item.recordId }
        }
    val deviceVersion = operation?.optimisticJson?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
    val actualDeleted = serverConfirmed && record?.remoteDeleted == true
    val actualVersion =
        if (serverConfirmed && !actualDeleted) {
            record?.remoteJson?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
        } else {
            null
        }
    val deviceAmount = deviceVersion?.longValue("amount")
    val actualAmount = actualVersion?.longValue("amount")
    val transaction = operation?.let { conflict -> liveTransactions.firstOrNull { it.recordId == conflict.recordId } }
    val transactionTitle = conflictPayeeTitle(operation, liveTransactions, mobileSession) ?: "Transaction"
    val deviceAmountText = deviceAmount?.let { amount -> state.formatSignedCurrency(amount) } ?: "Deleted"
    val actualAmountText =
        conflictActualAmountText(
            serverConfirmed = serverConfirmed,
            isSyncing = mobileSession.isSyncing,
            actualDeleted = actualDeleted,
            amount = actualAmount,
            formatAmount = { amount -> state.formatSignedCurrency(amount) },
        )
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
            )
        } else {
            emptyList()
        }
    val detailComparisons = comparisons.filterNot { it.label == "Amount" }
    PageColumn {
        if (operation == null) {
            EmptyState("No change to review", "This change no longer needs attention.")
            mobileSession.conflictResolutionFeedback
                ?.takeIf { it.operationId == state.selectedConflictOperationId }
                ?.let { feedback -> BodyText(feedback.message) }
        } else {
            Heading("Two versions differ")
            when {
                mobileSession.isSyncing -> BodyText("Checking the latest transaction with the server…")
                serverConfirmed -> BodyText("The server confirmed the latest Actual version.")
                else -> {
                    BodyText(
                        "Connect to the server before choosing a version. Your edit stays on this device. " +
                            "Check the connection and try again.",
                    )
                    SecondaryButton(
                        "Check again",
                        enabled = !mobileSession.isSavingTransaction,
                    ) { serverCheckAttempt += 1 }
                }
            }
            ListRow(
                transactionTitle,
                "$dateLabel · $categoryName",
                leading =
                    RowLeading.Merchant(
                        transaction?.merchantIcon ?: merchantIconMatcher?.iconFor(transactionTitle),
                        fallback = transaction?.categoryIcon ?: state.categoryIconFor(categoryId.orEmpty(), categoryName),
                    ),
                options = ListRowOptions(dense = true),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                ConflictValueCard("Your edit", deviceAmountText, Modifier.weight(1f))
                ConflictValueCard("Actual", actualAmountText, Modifier.weight(1f))
            }
            when {
                !serverConfirmed -> BodyText("Actual’s latest transaction details appear after the server check.")
                actualDeleted -> BodyText("Actual no longer has this transaction.")
                actualVersion == null -> BodyText("Actual’s latest transaction details aren’t available.")
                comparisons.isEmpty() -> BodyText("The server returned no differing transaction fields.")
                detailComparisons.isEmpty() -> BodyText("Amount is the only differing field.")
                else -> ConflictComparisonCard(detailComparisons)
            }
            mobileSession.conflictResolutionFeedback
                ?.takeIf { it.operationId == operation.operationId }
                ?.let { feedback -> BodyText(feedback.message) }
            PrimaryButton(
                when {
                    operation.action == "DELETE" && record?.remoteDeleted == true -> "Clear pending deletion"
                    operation.action == "DELETE" -> "Keep deletion"
                    else -> "Keep your edit"
                },
                enabled =
                    serverConfirmed &&
                        !state.readOnlyMode &&
                        !mobileSession.isSavingTransaction &&
                        (operation.action == "DELETE" || record?.remoteDeleted != true),
            ) { onResolveConflict(operation.operationId, true) }
            KeepLastSyncedConflictAction(
                operationId = operation.operationId,
                enabled = serverConfirmed && !state.readOnlyMode && !mobileSession.isSavingTransaction,
                onResolveConflict = onResolveConflict,
            )
        }
    }
}

@Composable
internal fun KeepLastSyncedConflictAction(
    operationId: String,
    enabled: Boolean,
    onResolveConflict: (String, Boolean) -> Unit,
) {
    var confirmDiscard by rememberSaveable(operationId) { mutableStateOf(false) }
    SecondaryButton(
        "Keep Actual’s version",
        enabled = enabled,
    ) { confirmDiscard = true }
    Text(
        "Discards your edit and keeps Actual’s version. The server must confirm it first.",
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
                onResolveConflict(operationId, false)
            },
        )
    }
}

@Composable
internal fun UncertainWriteScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    onCheckStatus: (String) -> Unit,
    onRetry: (String) -> Unit,
    onDiscard: (String) -> Unit,
    liveTransactions: List<TransactionItem>,
) {
    if (state.selectedBudgetId.isNotBlank()) {
        val pendingOperation =
            if (state.selectedConflictOperationId.isNotBlank()) {
                mobileSession.pendingWrites.firstOrNull { it.operationId == state.selectedConflictOperationId }
            } else {
                mobileSession.pendingWrites.firstOrNull { it.status == "uncertain" }
            }
        val checkedOperation =
            mobileSession.checkedWriteOperation?.takeIf {
                it.operationId == state.selectedConflictOperationId ||
                    (state.selectedConflictOperationId.isBlank() && it.operationId == mobileSession.checkedWriteOperationId)
            }
        val operation = checkedOperation ?: pendingOperation
        val transaction = operation?.let { write -> liveTransactions.firstOrNull { it.recordId == write.recordId } }
        val isChecked = operation != null && mobileSession.checkedWriteOperationId == operation.operationId
        val isNotFound = operation != null && isChecked && operation.status == "uncertain" && operation.lastErrorCode == "http-404"
        val checkError =
            operation?.let { checked ->
                mobileSession.uncertainWriteCheckError.takeIf {
                    mobileSession.uncertainWriteCheckErrorOperationId == checked.operationId
                }
            }
        UncertainWriteDetailsScreen(
            state = state,
            operation = operation,
            transaction = transaction,
            isChecked = isChecked,
            isNotFound = isNotFound,
            checkError = checkError,
            isSyncing = mobileSession.isSyncing,
            onCheckStatus = { operation?.let { onCheckStatus(it.operationId) } },
            onRetry = { operation?.let { onRetry(it.operationId) } },
            onDiscard = { operation?.let { onDiscard(it.operationId) } },
            onReviewConflict = {
                operation?.let {
                    mobileSession.clearTransactionWriteMessage()
                    state.selectedConflictOperationId = it.operationId
                    navigation.open(AppRoute.CONFLICT_REVIEW)
                }
            },
            onReviewRejected = {
                operation?.let {
                    state.selectedConflictOperationId = it.operationId
                    navigation.open(AppRoute.UNCERTAIN_WRITE)
                }
            },
            onDone = navigation.back,
        )
        return
    }
    PageColumn {
        EmptyState(
            "Choose a budget",
            "Open an Actual budget to check pending changes.",
            actionLabel = "Choose budget",
            onAction = { navigation.open(AppRoute.BUDGET_CHOOSER) },
        )
    }
}

@Composable
internal fun UncertainWriteDetailsScreen(
    state: AppState,
    operation: OutboxOperationEntity?,
    transaction: TransactionItem?,
    isChecked: Boolean,
    isNotFound: Boolean,
    checkError: String?,
    isSyncing: Boolean,
    onCheckStatus: () -> Unit,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
    onReviewConflict: () -> Unit,
    onReviewRejected: () -> Unit,
    onDone: () -> Unit,
) {
    val operationPayee = transaction?.payee ?: "this transaction"
    val operationAmount = operation?.optimisticAmountMinor ?: transaction?.amountMinorUnits
    val operationAmountText = operationAmount?.let(state::formatSignedCurrency) ?: "Unavailable"
    var confirmDiscard by rememberSaveable(operation?.operationId) { mutableStateOf(false) }
    val checkErrorForOperation = checkError.takeIf { operation != null }
    val canRetryOrRemove =
        operation != null &&
            canOfferUncertainWriteRecoveryActions(
                operation.status,
                isChecked && checkErrorForOperation == null,
            )
    PageColumn {
        if (operation == null) {
            Heading("Nothing to check")
            InfoBubble("This edit no longer needs a status check.")
        } else {
            val heading =
                when {
                    checkErrorForOperation != null -> "Couldn’t check this change"
                    operation.status == "committed" -> "Saved to Actual"
                    operation.status == "conflict" -> "Needs review"
                    operation.status == "rejected" -> "Rejected"
                    operation.status == "discarded" -> "Change discarded"
                    operation.status == "queued" -> "Waiting to sync"
                    operation.status == "sending" -> "Syncing"
                    isNotFound -> "Ready to try again"
                    isChecked -> "Status unknown"
                    operation.status == "uncertain" -> "Status unknown"
                    isSyncing -> "Checking status"
                    else -> "Status unknown"
                }
            val message =
                when {
                    checkErrorForOperation != null -> checkErrorForOperation
                    operation.status == "committed" -> "This change is saved in your budget."
                    operation.status == "conflict" -> "Actual changed this transaction. Review both versions."
                    operation.status == "rejected" -> "Actual kept the previous value."
                    operation.status == "discarded" -> "Your edit was removed."
                    operation.status == "queued" && isChecked -> "Actual confirmed this edit wasn’t saved. Try again."
                    operation.status == "queued" -> "Your edit is saved on this device and is waiting to sync."
                    operation.status == "sending" && isSyncing -> "Syncing this edit with Actual."
                    operation.status == "sending" -> "Tandem couldn’t confirm the save. Check its status before trying again."
                    isNotFound -> "Actual has no copy of this edit. Try sending it again once."
                    isChecked -> "Actual still can’t confirm whether this edit was saved. You can try again or remove it."
                    isSyncing -> "Checking Actual for this edit."
                    else -> "Tandem couldn’t confirm whether Actual saved this edit. Check its status before trying again."
                }
            Heading(heading)
            SectionCard(
                transaction?.payee ?: "Transaction",
                transaction?.dateLabel ?: "Saved on this device",
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.large),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Amount", color = tandemPalette.muted, fontSize = TandemTypography.body)
                    Text(
                        operation.optimisticAmountMinor?.let { state.formatSignedCurrency(it) } ?: "Unavailable",
                        color = tandemPalette.text,
                        fontSize = TandemTypography.cardTitle,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            InfoBubble(message)
            when {
                checkErrorForOperation != null -> {
                    UncertainWriteRecoveryActions(
                        showCheckAgain = true,
                        showRetryAndRemove = false,
                        isSyncing = isSyncing,
                        readOnly = state.readOnlyMode,
                        onCheckAgain = onCheckStatus,
                        onRetry = onRetry,
                        onRemove = { confirmDiscard = true },
                    )
                }
                canRetryOrRemove -> {
                    UncertainWriteRecoveryActions(
                        showCheckAgain = false,
                        showRetryAndRemove = true,
                        isSyncing = isSyncing,
                        readOnly = state.readOnlyMode,
                        onCheckAgain = onCheckStatus,
                        onRetry = onRetry,
                        onRemove = { confirmDiscard = true },
                    )
                }
                operation.status == "uncertain" -> {
                    PrimaryButton(
                        if (isChecked) "Check again" else "Check status",
                        enabled = !isSyncing,
                        loading = isSyncing,
                    ) { onCheckStatus() }
                }
                operation.status == "queued" -> Spacer(Modifier.height(72.dp))
                operation.status == "sending" ->
                    PrimaryButton(
                        if (isSyncing) "Syncing" else "Check status",
                        enabled = !isSyncing,
                        loading = isSyncing,
                    ) {
                        if (!isSyncing) onCheckStatus()
                    }
                operation.status == "conflict" -> PrimaryButton("Review", onClick = onReviewConflict)
                operation.status == "rejected" -> PrimaryButton("Review change", onClick = onReviewRejected)
                else -> PrimaryButton("Done", onClick = onDone)
            }
        }
    }
    if (confirmDiscard && operation != null) {
        DestructiveConfirmationDialog(
            title = "Remove this edit?",
            message = uncertainWriteRemovalWarning(operationPayee, operationAmountText),
            confirmLabel = "Remove edit",
            dismissLabel = "Keep edit",
            enabled = !state.readOnlyMode && !isSyncing,
            onDismissRequest = { if (!isSyncing) confirmDiscard = false },
            onConfirm = {
                confirmDiscard = false
                onDiscard()
            },
        )
    }
}

@Composable
internal fun BudgetMutationReviewScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    onDiscard: (String) -> Unit,
) {
    val operation =
        mobileSession.pendingWrites.firstOrNull {
            it.operationId == state.selectedConflictOperationId && it.recordType == "budget-mutation"
        }
    val mutation = operation?.let { decodeBudgetReorderQueue(it.patchJson).moves.singleOrNull() }
    val itemName = mutation?.name ?: "budget item"
    val title = operation?.let { budgetMutationTitle(it.action, itemName) } ?: "Budget change"
    val statusLabel = operation?.let { budgetMutationStatusLabel(it.status) } ?: "Pending"
    var confirmDiscard by rememberSaveable(operation?.operationId) { mutableStateOf(false) }

    PageColumn {
        Heading(statusLabel)
        if (operation == null) {
            InfoBubble("This budget change no longer needs review.")
        } else {
            SectionCard(title) {
                ListRow("Budget item", itemName)
                DividerLine()
                ListRow(
                    "Change",
                    operation.action
                        .replace('_', ' ')
                        .lowercase()
                        .replaceFirstChar { it.uppercase() },
                )
            }
            InfoBubble(
                when (operation.status) {
                    "conflict", "rejected", "uncertain" -> budgetMutationFailureMessage(operation.lastErrorCode)
                    else -> "Tandem couldn’t confirm whether Actual saved this change. Check Actual, then try again."
                },
            )
            when (operation.status) {
                "uncertain", "conflict" -> {
                    if (mutation?.requiresEncryptionPassword == true) {
                        InfoBubble("Unlock this encrypted budget before trying again.")
                    }
                    if (budgetMutationNeedsRefresh(operation.lastErrorCode)) {
                        PrimaryButton(
                            "Refresh budget",
                            enabled = !state.readOnlyMode && mutation?.requiresEncryptionPassword != true,
                        ) { mobileSession.retryBudgetMutation(operation.operationId) }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                            PrimaryButton(
                                "Try again",
                                enabled = !state.readOnlyMode && mutation?.requiresEncryptionPassword != true,
                                modifier = Modifier.weight(1f),
                            ) { mobileSession.retryBudgetMutation(operation.operationId) }
                            SecondaryButton(
                                "Discard change",
                                modifier = Modifier.weight(1f),
                                enabled = !state.readOnlyMode,
                            ) { confirmDiscard = true }
                        }
                    }
                }
                "rejected" -> {
                    if (operation.lastErrorCode == "operation_rejected") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                            PrimaryButton(
                                "Try again",
                                modifier = Modifier.weight(1f),
                                enabled = !state.readOnlyMode && mutation?.requiresEncryptionPassword != true,
                            ) { mobileSession.retryBudgetMutation(operation.operationId) }
                            SecondaryButton(
                                "Discard change",
                                modifier = Modifier.weight(1f),
                                enabled = !state.readOnlyMode,
                            ) { confirmDiscard = true }
                        }
                    } else {
                        PrimaryButton("Discard change", enabled = !state.readOnlyMode) { confirmDiscard = true }
                    }
                }
                else -> Spacer(Modifier.height(72.dp))
            }
        }
    }

    if (confirmDiscard && operation != null) {
        DestructiveConfirmationDialog(
            title = "Discard this change?",
            message = "Remove this budget change from this device?",
            confirmLabel = "Discard change",
            dismissLabel = "Keep change",
            enabled = !state.readOnlyMode,
            onDismissRequest = { confirmDiscard = false },
            onConfirm = {
                confirmDiscard = false
                onDiscard(operation.operationId)
            },
        )
    }
}

@Composable
internal fun RejectedWriteScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    onDiscard: (String) -> Unit,
    liveTransactions: List<TransactionItem>,
) {
    if (state.selectedBudgetId.isNotBlank()) {
        val operation =
            mobileSession.pendingWrites.firstOrNull {
                it.operationId == state.selectedConflictOperationId && it.status == "rejected"
            } ?: mobileSession.pendingWrites.firstOrNull { it.status == "rejected" }
        val transaction = operation?.let { write -> liveTransactions.firstOrNull { it.recordId == write.recordId } }
        val operationPayee = transaction?.payee ?: "this transaction"
        val operationAmount = operation?.optimisticAmountMinor ?: transaction?.amountMinorUnits
        val operationAmountText = operationAmount?.let(state::formatSignedCurrency) ?: "Unavailable"
        var confirmDismiss by rememberSaveable(operation?.operationId) { mutableStateOf(false) }
        PageColumn {
            Heading("Rejected")
            if (operation == null) {
                BodyText("No rejected edits need review.")
            } else {
                SectionCard(transaction?.payee ?: "Deleted transaction", transaction?.dateLabel ?: operationAmountText) {
                    ListRow(
                        "Your edit",
                        trailing = operationAmountText,
                    )
                }
                PrimaryButton("Remove edit", enabled = !state.readOnlyMode) { confirmDismiss = true }
            }
        }
        if (confirmDismiss && operation != null) {
            DestructiveConfirmationDialog(
                title = "Remove this edit?",
                message =
                    "Remove the rejected edit for $operationPayee ($operationAmountText) from this device? Actual kept the previous value.",
                confirmLabel = "Remove edit",
                dismissLabel = "Keep edit",
                enabled = !state.readOnlyMode,
                onDismissRequest = { confirmDismiss = false },
                onConfirm = {
                    confirmDismiss = false
                    onDiscard(operation.operationId)
                },
            )
        }
        return
    }
    PageColumn {
        EmptyState(
            "Choose a budget",
            "Open an Actual budget to review rejected changes.",
            actionLabel = "Choose budget",
            onAction = { navigation.open(AppRoute.BUDGET_CHOOSER) },
        )
    }
}
