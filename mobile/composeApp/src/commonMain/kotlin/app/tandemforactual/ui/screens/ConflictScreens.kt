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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.components.Badge
import app.tandemforactual.ui.components.BodyText
import app.tandemforactual.ui.components.DividerLine
import app.tandemforactual.ui.components.EmptyState
import app.tandemforactual.ui.components.ListRow
import app.tandemforactual.ui.components.ListRowOptions
import app.tandemforactual.ui.components.PrimaryButton
import app.tandemforactual.ui.components.SecondaryButton
import app.tandemforactual.ui.components.SectionCard
import app.tandemforactual.ui.components.SurfaceCard
import app.tandemforactual.ui.model.longValue
import com.composables.icons.lucide.Check
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

@Composable
internal fun ConflictInboxScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    liveTransactions: List<TransactionItem>,
) {
    val isLiveBudget = state.selectedBudgetId.isNotBlank()
    val conflicts =
        if (isLiveBudget) {
            mobileSession.pendingWrites
                .filter { it.status == "conflict" }
                .map { operation ->
                    val transaction = liveTransactions.firstOrNull { it.recordId == operation.recordId }
                    ConflictInboxItemUi(
                        operation.operationId,
                        transaction?.payee ?: "Transaction ${operation.recordId.take(8)}",
                        "${transaction?.dateLabel ?: "Actual changed"} · ${transaction?.category ?: "Transaction"}",
                    )
                }
        } else {
            listOf(
                ConflictInboxItemUi(
                    "sample-conflict",
                    "Corner Market",
                    "${formatDate(SampleDate(2026, 5, 14))} · Groceries · Checking",
                ),
            )
        }
    PageLazyColumn {
        if (!isLiveBudget) item(key = "conflicts-sample-banner") { SampleBanner() }
        item(key = "conflicts-title") {
            Heading(
                "Changes to review",
                if (isLiveBudget) "Choose whether to keep your edit or the latest Actual version." else "Choose which version to keep.",
            )
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
            item(key = "conflicts-section-title") {
                SectionCard("Needs your review", "${conflicts.size} edits") {}
            }
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
        if (isLiveBudget) {
            mobileSession.transactionWriteMessage?.let { message ->
                item(key = "conflicts-write-message") { BodyText(message) }
            }
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
    onResolveConflict: (String, Boolean) -> Unit,
    liveTransactions: List<TransactionItem>,
) {
    val isLiveBudget = state.selectedBudgetId.isNotBlank()
    val operation =
        if (isLiveBudget) {
            mobileSession.pendingWrites.firstOrNull {
                it.operationId == state.selectedConflictOperationId &&
                    it.status == "conflict"
            }
        } else {
            null
        }
    val record = operation?.let { item -> mobileSession.transactionRecords.firstOrNull { it.recordId == item.recordId } }
    val deviceVersion = operation?.optimisticJson?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
    val actualVersion = record?.remoteJson?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
    val deviceAmount = deviceVersion?.longValue("amount")
    val actualAmount = actualVersion?.longValue("amount")
    val transaction = operation?.let { conflict -> liveTransactions.firstOrNull { it.recordId == conflict.recordId } }
    val deviceAmountText =
        if (isLiveBudget) {
            deviceAmount?.let(::formatSignedCurrency) ?: "Deleted"
        } else {
            state.formatCurrency(1_200)
        }
    val actualAmountText =
        if (isLiveBudget) {
            actualAmount?.let(::formatSignedCurrency) ?: "Deleted"
        } else {
            state.formatCurrency(1_500)
        }
    val accountName = transaction?.account ?: if (isLiveBudget) "Unavailable" else "Checking"
    val categoryName = transaction?.category ?: if (isLiveBudget) "Unavailable" else "Groceries"
    val dateLabel =
        transaction?.dateLabel
            ?: if (isLiveBudget) "Unavailable" else formatShortDate(SampleDate(2026, 5, 14))
    PageColumn {
        if (!isLiveBudget) SampleBanner()
        if (isLiveBudget && operation == null) {
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
            if (isLiveBudget) {
                mobileSession.transactionWriteMessage?.let { BodyText(it) }
                PrimaryButton(
                    "Keep your edit",
                    enabled = !state.readOnlyMode && !mobileSession.isSavingTransaction && record?.remoteDeleted != true,
                ) { onResolveConflict(operation!!.operationId, true) }
                SecondaryButton("Keep Actual", enabled = !state.readOnlyMode && !mobileSession.isSavingTransaction) {
                    onResolveConflict(operation!!.operationId, false)
                }
            } else {
                PrimaryButton("Keep $deviceAmountText") { state.conflictChoice = "Your edit" }
                SecondaryButton("Keep $actualAmountText") { state.conflictChoice = "Actual" }
                if (state.conflictChoice.isNotBlank()) {
                    InfoBubble("Sample choice: ${state.conflictChoice}")
                }
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
        PageColumn {
            if (operation == null) {
                Heading("Nothing to check")
                InfoBubble("This edit no longer needs confirmation.")
            } else {
                val transaction = liveTransactions.firstOrNull { it.recordId == operation.recordId }
                val isChecked = mobileSession.checkedWriteOperationId == operation.operationId
                val isNotFound = isChecked && operation.status == "uncertain" && operation.lastErrorCode == "http-404"
                val checkError =
                    mobileSession.uncertainWriteCheckError.takeIf {
                        mobileSession.uncertainWriteCheckErrorOperationId == operation.operationId
                    }
                val heading =
                    when {
                        checkError != null -> "Couldn’t check status"
                        operation.status == "committed" -> "Saved to Actual"
                        operation.status == "conflict" -> "Needs your review"
                        operation.status == "rejected" -> "Change not applied"
                        operation.status == "discarded" -> "Change discarded"
                        operation.status == "queued" -> "Waiting to sync"
                        operation.status == "sending" -> "Syncing"
                        operation.status == "uncertain" -> "Status unknown"
                        isNotFound -> "No server record found"
                        isChecked -> "Status unknown"
                        mobileSession.isSyncing -> "Checking status"
                        else -> "Status unknown"
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
                        operation.status == "sending" -> "Status unknown. Check before retrying."
                        isNotFound -> "No matching edit is on the server. Retrying sends it once."
                        isChecked -> "Actual hasn’t confirmed whether it saved this edit. Check again before retrying."
                        mobileSession.isSyncing -> "Checking Actual for this edit."
                        else -> "Tandem couldn’t confirm whether Actual saved this edit. Check its status before retrying."
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
                                "Retry edit",
                                enabled = !state.readOnlyMode && !mobileSession.isSyncing,
                                modifier = Modifier.weight(1f),
                            ) { onRetry(operation.operationId) }
                            SecondaryButton(
                                "Discard",
                                modifier = Modifier.weight(1f),
                                enabled = !state.readOnlyMode && !mobileSession.isSyncing,
                            ) { onDiscard(operation.operationId) }
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
                        PrimaryButton("Review changes") {
                            state.selectedConflictOperationId = operation.operationId
                            navigation.open(AppRoute.CONFLICT_REVIEW)
                        }
                    operation.status == "rejected" ->
                        PrimaryButton("Review change") {
                            state.selectedConflictOperationId = operation.operationId
                            navigation.open(AppRoute.REJECTED_WRITE)
                        }
                    else -> PrimaryButton("Done") { navigation.back() }
                }
            }
        }
        return
    }
    PageColumn {
        Heading("Status unknown")
        SectionCard("Corner Market", "${formatShortDate(SampleDate(2026, 5, 14))} · Groceries") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.large),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Amount", color = tandemPalette.muted, fontSize = TandemTypography.body)
                Text(
                    state.formatCurrency(1_200),
                    color = tandemPalette.text,
                    fontSize = TandemTypography.cardTitle,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        InfoBubble("Tandem couldn’t confirm whether Actual saved this edit. Check its status before retrying.")
        PrimaryButton("Check status", enabled = false) {}
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
        PageColumn {
            Badge("Not applied", tandemPalette.negative)
            Heading("Change not applied")
            if (operation == null) {
                BodyText("No unapplied edits need review.")
            } else {
                val transaction = liveTransactions.firstOrNull { it.recordId == operation.recordId }
                SectionCard(transaction?.payee ?: "Transaction", transaction?.dateLabel ?: "Not applied") {
                    ListRow(
                        "Your edit",
                        "Not applied",
                        operation.optimisticAmountMinor?.let { state.formatSignedCurrency(it) } ?: "Unavailable",
                    )
                }
                PrimaryButton("Dismiss", enabled = !state.readOnlyMode) { onDiscard(operation.operationId) }
            }
        }
        return
    }
    PageColumn {
        Badge("Not applied", tandemPalette.negative)
        Heading("Change not applied")
        SectionCard("Sample edit", "Corner Market · ${formatShortDate(SampleDate(2026, 5, 14))}") {
            ListRow("Your edit", "Not applied", state.formatCurrency(1_200))
            DividerLine()
            ListRow(
                "Reason",
                "New transactions aren’t available yet",
                "Unavailable",
                options = ListRowOptions(trailingColor = tandemPalette.warning),
            )
        }
        PreviewNote(
            "The change wasn’t saved. You can dismiss this message.",
        )
        PrimaryButton("Close details") { navigation.back() }
    }
}
