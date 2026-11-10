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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
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
import app.tandemforactual.ui.components.SecondaryButton
import app.tandemforactual.ui.components.SectionCard
import app.tandemforactual.ui.components.SurfaceCard
import app.tandemforactual.ui.model.longValue
import app.tandemforactual.ui.model.recordName
import app.tandemforactual.ui.model.stringValue
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

@Composable
internal fun ConflictInboxScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    liveTransactions: List<TransactionItem>,
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
                )
            }
    PageLazyColumn {
        item(key = "conflicts-title") {
            BodyText("Choose whether to keep your edit or the latest Actual version.")
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
                    ListRow(
                        conflict.title,
                        conflict.description,
                        "Review",
                        options = ListRowOptions(trailingColor = tandemPalette.warning),
                    ) {
                        state.selectedConflictOperationId = conflict.id
                        navigation.open(AppRoute.CONFLICT_REVIEW)
                    }
                }
            }
        }
        mobileSession.transactionWriteMessage?.let { message ->
            item(key = "conflicts-write-message") { BodyText(message) }
        }
    }
}

internal data class ConflictInboxItemUi(
    val id: String,
    val title: String,
    val description: String,
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
internal fun ConflictReviewScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    onResolveConflict: (String, Boolean) -> Unit,
    liveTransactions: List<TransactionItem>,
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
    val record =
        operation?.let { item ->
            mobileSession.conflictRecordsByRecordId[item.recordId]
                ?: mobileSession.transactionRecords.firstOrNull { it.recordId == item.recordId }
        }
    val deviceVersion = operation?.optimisticJson?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
    val actualVersion = record?.remoteJson?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
    val baseVersion = operation?.baseSnapshotJson?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
    val detailVersion = actualVersion ?: baseVersion ?: deviceVersion
    val deviceAmount = deviceVersion?.longValue("amount")
    val actualAmount = actualVersion?.longValue("amount")
    val transaction = operation?.let { conflict -> liveTransactions.firstOrNull { it.recordId == conflict.recordId } }
    val deviceAmountText = deviceAmount?.let(::formatSignedCurrency) ?: "Deleted"
    val actualAmountText = actualAmount?.let(::formatSignedCurrency) ?: "Deleted"
    val accountName =
        transaction?.account
            ?: detailVersion?.stringValue("account")?.let { id ->
                mobileSession.accountRecords.firstOrNull { it.recordId == id }?.recordName()
            }
            ?: "Unavailable"
    val categoryId = detailVersion?.stringValue("category")
    val categoryName =
        transaction?.category
            ?: categoryId?.let { id -> mobileSession.categoryRecords.firstOrNull { it.recordId == id }?.recordName() }
            ?: if (detailVersion != null && categoryId == null) "Uncategorized" else "Unavailable"
    val dateLabel =
        transaction?.dateLabel
            ?: detailVersion?.stringValue("date")?.let(::formatLiveDate)
            ?: "Unavailable"
    PageColumn {
        if (operation == null) {
            EmptyState("No change to review", "This change no longer needs attention.")
        } else {
            Heading("Two versions differ")
            Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                ConflictValueCard("Your edit", deviceAmountText, Modifier.weight(1f))
                ConflictValueCard("In Actual", actualAmountText, Modifier.weight(1f))
            }
            SectionCard("Details") {
                ListRow("Account", trailing = accountName)
                DividerLine()
                ListRow("Category", trailing = categoryName)
                DividerLine()
                ListRow("Date", trailing = dateLabel)
            }
            mobileSession.transactionWriteMessage?.let { BodyText(it) }
            PrimaryButton(
                if (operation.action == "DELETE") "Keep deletion" else "Keep your edit",
                enabled = !state.readOnlyMode && !mobileSession.isSavingTransaction && record?.remoteDeleted != true,
            ) { onResolveConflict(operation.operationId, true) }
            SecondaryButton("Keep Actual", enabled = !state.readOnlyMode && !mobileSession.isSavingTransaction) {
                onResolveConflict(operation.operationId, false)
            }
        }
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
        val operationPayee = transaction?.payee ?: "this transaction"
        val operationAmount = operation?.optimisticAmountMinor ?: transaction?.amountMinorUnits
        val operationAmountText = operationAmount?.let(state::formatSignedCurrency) ?: "Unavailable"
        var confirmDiscard by rememberSaveable(operation?.operationId) { mutableStateOf(false) }
        PageColumn {
            if (operation == null) {
                Heading("Nothing to check")
                InfoBubble("This edit no longer needs confirmation.")
            } else {
                val isChecked = mobileSession.checkedWriteOperationId == operation.operationId
                val isNotFound = isChecked && operation.status == "uncertain" && operation.lastErrorCode == "http-404"
                val checkError =
                    mobileSession.uncertainWriteCheckError.takeIf {
                        mobileSession.uncertainWriteCheckErrorOperationId == operation.operationId
                    }
                val heading =
                    when {
                        checkError != null -> "Couldn’t check this change"
                        operation.status == "committed" -> "Saved to Actual"
                        operation.status == "conflict" -> "Needs review"
                        operation.status == "rejected" -> "Change not applied"
                        operation.status == "discarded" -> "Change discarded"
                        operation.status == "queued" -> "Waiting to sync"
                        operation.status == "sending" -> "Syncing"
                        operation.status == "uncertain" -> "Needs confirmation"
                        isNotFound -> "Ready to try again"
                        isChecked -> "Still unconfirmed"
                        mobileSession.isSyncing -> "Checking status"
                        else -> "Needs confirmation"
                    }
                val message =
                    when {
                        checkError != null -> checkError
                        operation.status == "committed" -> "This change is saved in your budget."
                        operation.status == "conflict" -> "Actual changed this transaction. Review both versions."
                        operation.status == "rejected" -> "Actual kept the previous value."
                        operation.status == "discarded" -> "Your edit was removed."
                        operation.status == "queued" -> "Your edit is saved on this device and is waiting to sync."
                        operation.status == "sending" && mobileSession.isSyncing -> "Syncing this edit with Actual."
                        operation.status == "sending" -> "Tandem couldn’t confirm the save. Check its status before trying again."
                        isNotFound -> "Actual has no copy of this edit. Try sending it again once."
                        isChecked -> "Actual still hasn’t confirmed this edit. Check again before trying it."
                        mobileSession.isSyncing -> "Checking Actual for this edit."
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
                    checkError != null -> {
                        PrimaryButton(
                            "Check again",
                            enabled = !mobileSession.isSyncing,
                            loading = mobileSession.isSyncing,
                        ) { onCheckStatus(operation.operationId) }
                    }
                    operation.status == "uncertain" && isNotFound -> {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                            PrimaryButton(
                                "Try again",
                                enabled = !state.readOnlyMode && !mobileSession.isSyncing,
                                modifier = Modifier.weight(1f),
                            ) { onRetry(operation.operationId) }
                            SecondaryButton(
                                "Remove edit",
                                modifier = Modifier.weight(1f),
                                enabled = !state.readOnlyMode && !mobileSession.isSyncing,
                            ) { confirmDiscard = true }
                        }
                    }
                    operation.status == "uncertain" -> {
                        PrimaryButton(
                            if (isChecked) "Check again" else "Check status",
                            enabled = !mobileSession.isSyncing,
                            loading = mobileSession.isSyncing,
                        ) { onCheckStatus(operation.operationId) }
                    }
                    operation.status == "queued" -> Spacer(Modifier.height(72.dp))
                    operation.status == "sending" ->
                        PrimaryButton(
                            if (mobileSession.isSyncing) "Syncing" else "Check status",
                            enabled = !mobileSession.isSyncing,
                            loading = mobileSession.isSyncing,
                        ) {
                            if (!mobileSession.isSyncing) onCheckStatus(operation.operationId)
                        }
                    operation.status == "conflict" ->
                        PrimaryButton("Review") {
                            state.selectedConflictOperationId = operation.operationId
                            navigation.open(AppRoute.CONFLICT_REVIEW)
                        }
                    operation.status == "rejected" ->
                        PrimaryButton("Review change") {
                            state.selectedConflictOperationId = operation.operationId
                            navigation.open(AppRoute.UNCERTAIN_WRITE)
                        }
                    else -> PrimaryButton("Done") { navigation.back() }
                }
            }
        }
        if (confirmDiscard && operation != null) {
            DestructiveConfirmationDialog(
                title = "Remove this edit?",
                message =
                    "Remove the pending edit for $operationPayee ($operationAmountText)? Actual may already have saved it; removing it here won’t undo a change already stored there.",
                confirmLabel = "Remove edit",
                dismissLabel = "Keep edit",
                enabled = !state.readOnlyMode && !mobileSession.isSyncing,
                onDismissRequest = { if (!mobileSession.isSyncing) confirmDiscard = false },
                onConfirm = {
                    confirmDiscard = false
                    onDiscard(operation.operationId)
                },
            )
        }
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
    var confirmDiscard by rememberSaveable(operation?.operationId) { mutableStateOf(false) }

    PageColumn {
        when (operation?.status) {
            "conflict" -> Heading("Needs review")
            "rejected" -> Heading("Change not applied")
            "queued" -> Heading("Waiting to sync")
            else -> Heading("Needs confirmation")
        }
        if (operation == null) {
            InfoBubble("This budget change no longer needs review.")
        } else {
            SectionCard(title, operation.status.replaceFirstChar { it.uppercase() }) {
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
                    "conflict" -> "The budget changed in Actual while this edit was waiting. Review the latest budget before trying again."
                    "rejected" -> "Actual did not apply this change. Remove it from Pending Changes or leave it for review."
                    else -> "Tandem couldn’t confirm whether Actual saved this change. Check Actual before trying again."
                },
            )
            when (operation.status) {
                "uncertain", "conflict" -> {
                    if (mutation?.requiresEncryptionPassword == true) {
                        InfoBubble("Unlock this encrypted budget before trying again.")
                    }
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
                "rejected" -> PrimaryButton("Discard change", enabled = !state.readOnlyMode) { confirmDiscard = true }
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
            Heading("Change not applied")
            if (operation == null) {
                BodyText("No unapplied edits need review.")
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
                    "Remove the rejected edit for $operationPayee ($operationAmountText) from this device? It wasn’t applied in Actual.",
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
