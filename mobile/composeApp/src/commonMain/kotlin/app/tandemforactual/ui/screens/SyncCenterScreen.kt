@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.components.AnimatedReveal
import app.tandemforactual.ui.components.AnimatedStatusText
import app.tandemforactual.ui.components.Badge
import app.tandemforactual.ui.components.BodyText
import app.tandemforactual.ui.components.DividerLine
import app.tandemforactual.ui.components.EmptyState
import app.tandemforactual.ui.components.LabelText
import app.tandemforactual.ui.components.ListRow
import app.tandemforactual.ui.components.ListRowOptions
import app.tandemforactual.ui.components.RowLeading
import app.tandemforactual.ui.components.SecondaryButton
import app.tandemforactual.ui.components.SectionCard
import app.tandemforactual.ui.components.SurfaceCard
import com.composables.icons.lucide.Cloud
import com.composables.icons.lucide.CloudAlert
import com.composables.icons.lucide.CloudSync
import com.composables.icons.lucide.Lucide

@Composable
internal fun SyncCenterScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    liveTransactions: List<TransactionItem>,
) {
    if (state.selectedBudgetId.isNotBlank()) {
        val attention = mobileSession.pendingWrites.filter { it.status in setOf("queued", "conflict", "uncertain", "rejected") }
        PageLazyColumn {
            item(key = "sync-center-title") {
                Heading("Sync")
            }
            item(key = "sync-center-budget-summary") {
                SurfaceCard {
                    Column(
                        Modifier.fillMaxWidth().padding(TandemSpacing.large),
                        verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                    ) {
                        LabelText(budgetDisplayNameForHeader(state.selectedBudget))
                        BodyText(
                            "${mobileSession.cachedTransactionCount} transactions · ${mobileSession.accountRecords.size} accounts cached",
                        )
                        val syncMessage =
                            when {
                                mobileSession.isSyncing && mobileSession.syncError != null ->
                                    "Retrying sync · ${mobileSession.syncError}"
                                mobileSession.isSyncing -> "Sync in progress"
                                mobileSession.syncError != null -> mobileSession.syncError.orEmpty()
                                else -> "Local data is available offline"
                            }
                        val syncMessageColor by
                            animateColorAsState(
                                targetValue = if (mobileSession.syncError != null) tandemPalette.warning else tandemPalette.muted,
                                animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
                                label = "sync-center-status-color",
                            )
                        AnimatedStatusText(
                            syncMessage,
                            syncMessageColor,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                            fontSize = TandemTypography.label,
                            lineHeight = TandemTypography.compactLineHeight,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            item(key = "pending-changes-title") {
                SectionCard("Pending Changes", "${attention.size} edits", animateDetail = true) {}
            }
            if (attention.isEmpty()) {
                item(key = "pending-changes-empty") {
                    AnimatedAttentionContent(showingAttention = false) { _ ->
                        EmptyState(
                            "No pending changes",
                            modifier = Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
                        )
                    }
                }
            } else {
                items(attention, key = { "pending:${it.operationId}" }) { operation ->
                    val transaction = liveTransactions.firstOrNull { it.recordId == operation.recordId }
                    SurfaceCard {
                        ListRow(
                            transaction?.payee ?: "Transaction ${operation.recordId.take(8)}",
                            "${writeStatusLabel(operation.status)} · ${transaction?.category ?: "Transaction edit"}",
                            "Review",
                            leading =
                                RowLeading.Icon(
                                    when (operation.status) {
                                        "conflict", "uncertain", "rejected" -> Lucide.CloudAlert
                                        "queued", "sending" -> Lucide.CloudSync
                                        else -> Lucide.Cloud
                                    },
                                ),
                            options =
                                ListRowOptions(
                                    trailingColor =
                                        when (operation.status) {
                                            "conflict", "uncertain", "rejected" -> tandemPalette.warning
                                            else -> tandemPalette.syncing
                                        },
                                ),
                            onClick = {
                                state.selectedConflictOperationId = operation.operationId
                                navigation.open(
                                    when (operation.status) {
                                        "conflict" -> AppRoute.CONFLICT_REVIEW
                                        "uncertain" -> AppRoute.UNCERTAIN_WRITE
                                        "rejected" -> AppRoute.UNCERTAIN_WRITE
                                        else -> AppRoute.UNCERTAIN_WRITE
                                    },
                                )
                            },
                        )
                    }
                }
            }
            item(key = "sync-write-message") {
                AnimatedReveal(visible = mobileSession.transactionWriteMessage != null) {
                    BodyText(mobileSession.transactionWriteMessage.orEmpty())
                }
            }
            item(key = "sync-center-bottom-spacer") { Spacer(Modifier.height(72.dp)) }
        }
        return
    }
    PageColumn {
        Heading("Connection status", "Review device data, pending edits, and server activity.")
        SurfaceCard {
            Column(Modifier.padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small), verticalAlignment = Alignment.CenterVertically) {
                    Badge("Sample data")
                    Text(
                        "No server connection",
                        color = tandemPalette.text,
                        fontSize = TandemTypography.compactBody,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text("Last sync · sample only", color = tandemPalette.muted, fontSize = TandemTypography.label)
                PreviewNote("No data has been uploaded or downloaded.")
            }
        }
        SectionCard("Attention needed", "Sample recovery states") {
            ListRow(
                "Needs your review",
                "Corner Market · two versions",
                "1",
                options = ListRowOptions(trailingColor = tandemPalette.warning),
            ) {
                navigation.open(AppRoute.CONFLICT_INBOX)
            }
            DividerLine()
            ListRow(
                "Status unknown",
                "Check before retrying",
                "1",
                options = ListRowOptions(trailingColor = tandemPalette.warning),
            ) { navigation.open(AppRoute.UNCERTAIN_WRITE) }
        }
        SectionCard("Recent sync activity", "Sample events", "") {
            ListRow("Budget snapshot", "Ready for initial download", "Pending")
            DividerLine()
            ListRow("Local edits", "None in sample data", "0")
        }
        SecondaryButton("Review changes") { navigation.open(AppRoute.CONFLICT_INBOX) }
    }
}
