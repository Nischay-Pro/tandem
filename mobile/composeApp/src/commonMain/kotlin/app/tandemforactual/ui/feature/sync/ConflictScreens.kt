@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sync
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.data.store.parseLocalWriteConflictDetails
import app.tandemforactual.ui.core.format.formatAmount
import app.tandemforactual.ui.core.format.formatDate
import app.tandemforactual.ui.core.format.formatSignedCurrency
import app.tandemforactual.ui.core.session.BudgetReorderKind
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budgetMutationCanRetry
import app.tandemforactual.ui.core.session.budgetMutationNeedsRefresh
import app.tandemforactual.ui.core.session.budgetMutationStatusLabel
import app.tandemforactual.ui.core.session.decodeBudgetReorderQueue
import app.tandemforactual.ui.core.session.isRejectedBudgetOrderRequest
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
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.BodyText
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.sheets.DestructiveConfirmationDialog
import app.tandemforactual.ui.design.templates.Heading
import app.tandemforactual.ui.design.templates.InfoBubble
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.templates.PageLazyColumn
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.templates.tandemListItemModifier
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import app.tandemforactual.ui.feature.transactions.conflictReviewRevision
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Receipt
import com.composables.icons.lucide.TriangleAlert
import kotlinx.coroutines.launch
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
        showCheckAgain && showRetryAndRemove -> {
            Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                SecondaryButton(
                    "Check again",
                    enabled = !isSyncing,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onCheckAgain,
                )
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
                    listOfNotNull(transactionIdentity(operation, transaction, state, mobileSession), categoryName).joinToString(" · "),
                    transaction?.merchantIcon,
                    transaction?.categoryIcon,
                )
            }
    PageLazyColumn {
        item(key = "conflicts-heading") {
            Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                SyncPageHeading(
                    eyebrow = "Sync",
                    title = if (conflicts.isEmpty()) "Nothing to review." else "Needs review.",
                    detail =
                        when (conflicts.size) {
                            0 -> "Every change on this device matches Actual."
                            1 -> "One item changed on this device and in Actual. Choose which version to keep."
                            else -> "${conflicts.size} items changed on this device and in Actual. Choose which version to keep."
                        },
                )
                if (conflicts.isNotEmpty()) {
                    SyncCountChip("${conflicts.size} to review", tandemPalette.warning)
                }
            }
        }
        items(conflicts, key = { "conflict:${it.id}" }) { conflict ->
            SurfaceCard(modifier = tandemListItemModifier(tandemReduceMotion)) {
                val merchantIcon = conflict.merchantIcon ?: merchantIconMatcher?.iconFor(conflict.title)
                ListRow(
                    conflict.title,
                    conflict.description,
                    leading =
                        RowLeading.Merchant(
                            merchantIcon,
                            fallback = conflict.categoryIcon ?: Lucide.Receipt,
                        ),
                    onClick = {
                        mobileSession.clearTransactionWriteMessage()
                        state.selectedConflictOperationId = conflict.id
                        navigation.open(AppRoute.CONFLICT_REVIEW)
                    },
                )
            }
        }
        if (conflicts.isNotEmpty()) {
            item(key = "conflicts-footer") {
                Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.large)) {
                    ConflictNote(
                        icon = Lucide.TriangleAlert,
                        title = "No changes will be discarded",
                        detail = "until you choose a version.",
                    )
                    PrimaryButton("Review first change") {
                        mobileSession.clearTransactionWriteMessage()
                        state.selectedConflictOperationId = conflicts.first().id
                        navigation.open(AppRoute.CONFLICT_REVIEW)
                    }
                }
            }
        }
    }
}

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

@Composable
internal fun ConflictVersionCard(
    label: String,
    amount: String,
    detail: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    val palette = tandemPalette
    val border by animateColorAsState(
        if (selected) palette.accent else palette.border,
        tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
        label = "conflict-version-border",
    )
    val fill by animateColorAsState(
        if (selected) palette.accentSurface else palette.surface,
        tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
        label = "conflict-version-fill",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
            .clip(RoundedCornerShape(TandemShapes.large))
            .background(fill)
            .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(TandemShapes.large))
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(TandemSpacing.large),
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .border(2.dp, if (selected) palette.accent else palette.muted, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(palette.accent))
            }
            Text(
                label.uppercase(),
                color = palette.muted,
                fontSize = TandemTypography.caption,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = TandemTypography.captionLetterSpacing,
            )
        }
        Text(amount, color = palette.text, fontSize = TandemTypography.amount, fontWeight = FontWeight.Bold)
        Text(detail, color = palette.muted, fontSize = TandemTypography.compactBody)
    }
}

@Composable
internal fun ConflictCompareTable(
    comparisons: List<ConflictFieldComparison>,
    actualLabel: String,
) {
    val palette = tandemPalette
    SurfaceCard {
        Column(Modifier.fillMaxWidth().padding(vertical = TandemSpacing.small)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.small)) {
                listOf("Field", "This device", actualLabel).forEach { header ->
                    Text(
                        header.uppercase(),
                        Modifier.weight(1f),
                        color = palette.muted,
                        fontSize = TandemTypography.micro,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = TandemTypography.captionLetterSpacing,
                    )
                }
            }
            comparisons.forEach { comparison ->
                DividerLine()
                val differs = comparison.editedValue != comparison.actualValue
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(comparison.label, Modifier.weight(1f), color = palette.muted, fontSize = TandemTypography.compactBody)
                    Text(
                        comparison.editedValue,
                        Modifier.weight(1f),
                        color = if (differs) palette.accent else palette.text,
                        fontSize = TandemTypography.compactBody,
                        fontWeight = if (differs) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    Text(
                        comparison.actualValue,
                        Modifier.weight(1f),
                        color = if (differs) palette.warning else palette.text,
                        fontSize = TandemTypography.compactBody,
                        fontWeight = if (differs) FontWeight.SemiBold else FontWeight.Normal,
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
                mobileSession.conflictResolutionFeedback
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
                        mobileSession.clearConflictResolutionFeedback()
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
            mobileSession.conflictResolutionFeedback
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
        val isNotFound =
            operation != null &&
                isChecked &&
                operation.status == "uncertain" &&
                isUncertainWriteOperationNotFound(operation.lastErrorCode)
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
    val statusCheckFinished = isChecked && !isSyncing
    val canRetryOrRemove =
        operation != null &&
            canOfferUncertainWriteRecoveryActions(
                operation.status,
                statusCheckFinished,
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
                    isNotFound -> "Status not found"
                    isChecked -> "Status unknown"
                    operation.status == "uncertain" -> "Status unknown"
                    isSyncing -> "Checking status"
                    else -> "Status unknown"
                }
            val message =
                when {
                    checkErrorForOperation != null -> checkErrorForOperation
                    operation.status == "committed" -> "Actual saved this edit."
                    operation.status == "conflict" -> "Actual has a different version. Compare before choosing."
                    operation.status == "rejected" -> "Actual rejected this edit. The previous value is unchanged."
                    operation.status == "discarded" -> "This edit was removed from this device."
                    operation.status == "queued" && isChecked -> "Actual did not save this edit. It is still on this device."
                    operation.status == "queued" -> "Saved on this device. Waiting to sync."
                    operation.status == "sending" && isSyncing -> "Sending this edit to Actual."
                    operation.status == "sending" -> "Tandem couldn’t confirm the save. Check Actual before retrying."
                    isNotFound -> "Actual has no record of this edit. Keep it here or remove it."
                    isChecked -> "Actual couldn’t confirm whether this was saved. Check Actual before retrying."
                    isSyncing -> "Checking Actual for this edit."
                    else -> "Tandem couldn’t confirm whether Actual saved this edit. Check before retrying."
                }
            ReviewOutcomeCard(
                title = heading,
                explanation = message,
                isRejected = operation.status == "rejected" || checkErrorForOperation != null,
            )
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
            when {
                checkErrorForOperation != null -> {
                    UncertainWriteRecoveryActions(
                        showCheckAgain = true,
                        showRetryAndRemove = canRetryOrRemove,
                        isSyncing = isSyncing,
                        readOnly = state.readOnlyMode,
                        onCheckAgain = onCheckStatus,
                        onRetry = onRetry,
                        onRemove = { confirmDiscard = true },
                    )
                }
                canRetryOrRemove -> {
                    UncertainWriteRecoveryActions(
                        showCheckAgain = isNotFound,
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
            it.operationId == state.selectedConflictOperationId &&
                it.recordType == "budget-mutation" &&
                isBudgetMutationReviewable(it.status)
        }
    val mutation = operation?.let { decodeBudgetReorderQueue(it.patchJson).moves.singleOrNull() }
    val itemName = budgetMutationItemName(mutation, operation, mobileSession)
    val changeTitle =
        mutation?.let { budgetMutationChangeTitle(it, itemName, state, mobileSession) }
            ?: operation?.let { budgetMutationTitle(it.action, itemName) }
            ?: "Budget change"
    val statusLabel = operation?.let { budgetMutationStatusLabel(it.status) } ?: "Pending"
    val scope = rememberCoroutineScope()
    var showRenameCorrection by rememberSaveable(operation?.operationId) { mutableStateOf(false) }
    var correctedName by rememberSaveable(operation?.operationId) { mutableStateOf(mutation?.name.orEmpty()) }
    val isRejectedCategoryRename = operation?.status == "rejected" && operation.action == "RENAME_CATEGORY"
    var confirmDiscard by rememberSaveable(operation?.operationId) { mutableStateOf(false) }
    var requestedDiscardOperationId by rememberSaveable(state.selectedConflictOperationId) { mutableStateOf<String?>(null) }

    LaunchedEffect(state.selectedConflictOperationId) {
        mobileSession.clearTransactionWriteMessage()
    }
    LaunchedEffect(requestedDiscardOperationId, operation?.operationId) {
        if (requestedDiscardOperationId != null && operation?.operationId != requestedDiscardOperationId) {
            navigation.back()
        }
    }

    PageColumn {
        if (operation == null) {
            Heading("Change no longer needs review")
            InfoBubble(mobileSession.transactionWriteMessage ?: "This budget change has already been cleared.")
        } else {
            val outcome =
                when (operation.status) {
                    "rejected" -> "Rejected"
                    "uncertain" -> "Status unknown"
                    "conflict" -> "Needs review"
                    "queued" -> "Waiting to sync"
                    "sending" -> "Syncing"
                    else -> statusLabel
                }
            val outcomeExplanation =
                when {
                    operation.status == "rejected" &&
                        operation.lastErrorCode == "invalid_request" &&
                        mutation?.kind == BudgetReorderKind.SET_CATEGORY_AMOUNT ->
                        "Actual did not receive this assignment. The saved value is unchanged."
                    operation.status == "rejected" && isRejectedBudgetOrderRequest(operation.lastErrorCode, mutation?.kind) ->
                        "Tandem rejected this reorder. Actual’s saved order did not change."
                    operation.status == "rejected" -> "Actual rejected this change. The previous value is unchanged."
                    operation.status == "uncertain" -> "Actual has not confirmed whether this change was saved."
                    operation.status == "conflict" -> "The budget changed since this edit. Review the latest value first."
                    operation.status == "queued" -> "Saved on this device. Waiting to sync."
                    operation.status == "sending" -> "Sending this change to Actual."
                    else -> "Review the change and its current status."
                }
            ReviewOutcomeCard(outcome, outcomeExplanation, isRejected = operation.status == "rejected")
            BudgetMutationChangeDetails(
                changeTitle,
                budgetMutationLastSyncedAssignment(mutation, operation, state, mobileSession),
                budgetMutationAmountChanges(mutation, state, mobileSession),
            )
            BudgetMutationReasonCard(
                title = if (operation.status == "rejected") "WHY IT WAS REJECTED" else "WHAT HAPPENED",
                explanation = budgetMutationReason(operation, mutation, itemName),
            )
            if (operation.status == "rejected" &&
                operation.lastErrorCode == "invalid_request" &&
                mutation?.kind == BudgetReorderKind.SET_CATEGORY_AMOUNT
            ) {
                BudgetMutationReasonCard(
                    title = "WHAT TO DO NEXT",
                    explanation =
                        "Refresh the budget. After the server is updated, assign this category again from Budget.",
                )
            }
            if (operation.status == "rejected" && isRejectedBudgetOrderRequest(operation.lastErrorCode, mutation?.kind)) {
                BudgetMutationReasonCard(
                    title = "WHAT TO DO NEXT",
                    explanation =
                        "Check Actual’s order. Confirm the Tandem server is updated, then make this reorder again.",
                )
            }
            if (mutation?.requiresEncryptionPassword == true && state.encryptedPassword.isBlank()) {
                BudgetMutationReasonCard(
                    title = "ENCRYPTED BUDGET",
                    explanation = "Unlock this budget before Tandem can refresh or try again.",
                )
            }
            mobileSession.transactionWriteMessage?.let { InlineMessage(it, InlineMessageKind.ERROR) }

            when (operation.status) {
                "uncertain", "conflict" -> {
                    PrimaryButton(
                        if (budgetMutationNeedsRefresh(operation.lastErrorCode)) {
                            "Refresh budget"
                        } else if (budgetMutationCanRetry(operation.lastErrorCode)) {
                            "Try again"
                        } else {
                            "Check Actual"
                        },
                        enabled =
                            !state.readOnlyMode && !(mutation?.requiresEncryptionPassword == true && state.encryptedPassword.isBlank()),
                    ) { mobileSession.retryBudgetMutation(operation.operationId) }
                    if (!budgetMutationNeedsRefresh(operation.lastErrorCode) && !budgetMutationCanRetry(operation.lastErrorCode)) {
                        SecondaryButton("Discard change", enabled = !state.readOnlyMode) { confirmDiscard = true }
                    }
                    if (budgetMutationCanRetry(operation.lastErrorCode)) {
                        SecondaryButton("Discard change", enabled = !state.readOnlyMode) { confirmDiscard = true }
                    }
                }
                "rejected" -> {
                    if (isRejectedCategoryRename && mutation != null) {
                        if (showRenameCorrection) {
                            TextFieldCard(
                                "Category name",
                                correctedName,
                                { correctedName = it },
                                TextFieldOptions(placeholder = itemName),
                            )
                            mobileSession.budgetMutationCorrectionMessage?.let {
                                InlineMessage(it, InlineMessageKind.ERROR)
                            }
                            PrimaryButton(
                                if (mobileSession.isCorrectingBudgetMutation) "Saving correction" else "Save correction",
                                enabled =
                                    !state.readOnlyMode &&
                                        !mobileSession.isCorrectingBudgetMutation &&
                                        correctedName.trim().isNotBlank() &&
                                        correctedName.trim() != mutation.name &&
                                        (mutation.requiresEncryptionPassword.not() || state.encryptedPassword.isNotBlank()),
                                loading = mobileSession.isCorrectingBudgetMutation,
                            ) {
                                scope.launch {
                                    val saved =
                                        mobileSession.correctRejectedCategoryRename(
                                            budgetId = operation.budgetId,
                                            operationId = operation.operationId,
                                            correctedName = correctedName,
                                            encryptionPassword = state.encryptedPassword.takeIf(String::isNotBlank),
                                        )
                                    if (saved) navigation.back()
                                }
                            }
                            SecondaryButton(
                                "Cancel correction",
                                enabled = !mobileSession.isCorrectingBudgetMutation,
                                onClick = {
                                    showRenameCorrection = false
                                    mobileSession.clearBudgetMutationCorrectionMessage()
                                },
                            )
                        } else {
                            PrimaryButton(
                                "Correct category name",
                                enabled =
                                    !state.readOnlyMode &&
                                        (!mutation.requiresEncryptionPassword || state.encryptedPassword.isNotBlank()),
                            ) {
                                correctedName = mutation.name.orEmpty()
                                mobileSession.clearBudgetMutationCorrectionMessage()
                                showRenameCorrection = true
                            }
                            SecondaryButton("Discard change", enabled = !state.readOnlyMode) { confirmDiscard = true }
                        }
                    } else if (budgetMutationCanRetry(operation.lastErrorCode)) {
                        PrimaryButton(
                            "Try again",
                            enabled =
                                !state.readOnlyMode && !(mutation?.requiresEncryptionPassword == true && state.encryptedPassword.isBlank()),
                        ) { mobileSession.retryBudgetMutation(operation.operationId) }
                        SecondaryButton("Discard change", enabled = !state.readOnlyMode) { confirmDiscard = true }
                    } else {
                        PrimaryButton(
                            if (budgetMutationNeedsRefresh(operation.lastErrorCode)) "Refresh budget" else "Check Actual",
                            enabled =
                                !state.readOnlyMode && !(mutation?.requiresEncryptionPassword == true && state.encryptedPassword.isBlank()),
                        ) { mobileSession.retryBudgetMutation(operation.operationId) }
                        SecondaryButton("Discard change", enabled = !state.readOnlyMode) { confirmDiscard = true }
                    }
                }
                else -> Spacer(Modifier.height(72.dp))
            }
        }
    }

    if (confirmDiscard && operation != null) {
        DestructiveConfirmationDialog(
            title = "Discard this change?",
            message =
                if (operation.status == "uncertain") {
                    "Remove the pending change for $itemName from this device? Actual may already have saved it; this won’t undo a change already stored there."
                } else {
                    "Remove this budget change from this device?"
                },
            confirmLabel = "Discard change",
            dismissLabel = "Keep change",
            enabled = !state.readOnlyMode,
            onDismissRequest = { confirmDiscard = false },
            onConfirm = {
                confirmDiscard = false
                requestedDiscardOperationId = operation.operationId
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
            if (operation == null) {
                Heading("Rejected")
                BodyText("No rejected edits need review.")
            } else {
                ReviewOutcomeCard(
                    title = "Rejected",
                    explanation = "Actual kept the previous value.",
                    isRejected = true,
                )
                SectionCard(transaction?.payee ?: "Deleted transaction", transaction?.dateLabel ?: operationAmountText) {
                    ListRow(
                        "Your edit",
                        trailing = operationAmountText,
                    )
                }
                BudgetMutationReasonCard(
                    title = "WHY IT WAS REJECTED",
                    explanation = rejectedTransactionReferenceMessage(operation.lastErrorCode),
                )
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
