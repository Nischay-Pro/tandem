@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sync

import androidx.compose.runtime.Composable
import app.tandemforactual.data.regions.global.bank.BankProviders
import app.tandemforactual.ui.core.format.bankFailureLabel
import app.tandemforactual.ui.core.format.formatMomentAtEpochMilliseconds
import app.tandemforactual.ui.core.session.BudgetSyncOverview
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.PlanningSyncStatus
import app.tandemforactual.ui.core.ui.bankLinkBadge
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowBadge
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.feature.planning.planningSyncDetail
import com.composables.icons.lucide.CloudCheck
import com.composables.icons.lucide.CloudOff
import com.composables.icons.lucide.GitMerge
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Puzzle
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.WalletCards

/** Every budget on this device and where its sync stands, so a quiet second budget doesn't fall behind unnoticed. */
@Composable
internal fun SyncCenterBudgets(
    overview: List<BudgetSyncOverview>,
    mobileSession: TandemMobileSession,
    onOpenOther: () -> Unit,
) {
    val palette = tandemPalette
    val now = mobileSession.nowEpochMillis()
    SectionCard("Budgets", "${overview.size} on your Actual server") {
        overview.forEachIndexed { index, budget ->
            if (index > 0) DividerLine()
            val subtitle =
                when {
                    budget.locked -> "Encrypted · Open it to sync"
                    budget.lastSyncedAtEpochMs == null -> "Not on this device yet"
                    else ->
                        listOfNotNull(
                            "Synced ${formatMomentAtEpochMilliseconds(budget.lastSyncedAtEpochMs, now)}",
                            budget.waiting.takeIf { it > 0 }?.let { "$it waiting" },
                            budget.needsReview.takeIf { it > 0 }?.let { "$it to review" },
                        ).joinToString(" · ")
                }
            ListRow(
                budget.name,
                subtitle,
                if (budget.open) "Open" else null,
                leading =
                    RowLeading.Icon(
                        when {
                            budget.locked -> Lucide.Lock
                            budget.lastSyncedAtEpochMs == null -> Lucide.CloudOff
                            else -> Lucide.CloudCheck
                        },
                    ),
                options =
                    ListRowOptions(
                        dense = true,
                        showChevron = !budget.open,
                        trailingColor = palette.muted,
                        badge =
                            when {
                                budget.failed || budget.needsReview > 0 -> RowBadge(null, palette.warning, "Needs attention")
                                budget.waiting > 0 -> RowBadge(null, palette.syncing, "Changes waiting")
                                else -> null
                            },
                    ),
                onClick = if (budget.open) null else onOpenOther,
            )
        }
    }
}

/** Each bank link's health: when it last synced and whether its last sync failed. */
@Composable
internal fun SyncCenterBankLinks(
    mobileSession: TandemMobileSession,
    onManage: () -> Unit,
) {
    val links = mobileSession.banking.bankLinks
    val failing = links.count { it.failure != null }
    val now = mobileSession.nowEpochMillis()
    SectionCard(
        "Bank links",
        if (failing > 0) "$failing ${if (failing == 1) "link needs" else "links need"} attention" else "All linked accounts are healthy",
        "Manage",
        onClick = onManage,
    ) {
        links.forEachIndexed { index, link ->
            if (index > 0) DividerLine()
            ListRow(
                link.name,
                listOfNotNull(
                    BankProviders.forSource(link.source).displayName,
                    link.failure?.let { "Failed: ${bankFailureLabel(it)}" }
                        ?: link.lastSyncMillis?.let { "Synced ${formatMomentAtEpochMilliseconds(it, now)}" }
                        ?: "Not synced yet",
                ).joinToString(" · "),
                leading = RowLeading.Icon(Lucide.WalletCards),
                options = ListRowOptions(dense = true, badge = bankLinkBadge(link, now)),
                onClick = onManage,
            )
        }
    }
}

/** Where Tandem's optional extension service stands for this budget's planning details. */
@Composable
internal fun SyncCenterServices(
    mobileSession: TandemMobileSession,
    onRecoveryKey: () -> Unit,
) {
    val palette = tandemPalette
    val status = mobileSession.planning.syncStatus
    SectionCard("Extension sync", "Optional, for planning details") {
        ListRow(
            when (status) {
                PlanningSyncStatus.OFF -> "Not running on your server"
                PlanningSyncStatus.SYNCED -> "Planning details synced"
                PlanningSyncStatus.WAITING -> "Waiting for the extension service"
                PlanningSyncStatus.NEEDS_RECOVERY_KEY -> "Needs your recovery key"
            },
            when (status) {
                PlanningSyncStatus.OFF -> "Savings goals and debt details stay on this device. Your budget syncs with Actual as usual."
                else -> planningSyncDetail(status)?.second
            },
            leading = RowLeading.Icon(if (status == PlanningSyncStatus.NEEDS_RECOVERY_KEY) Lucide.KeyRound else Lucide.Puzzle),
            options =
                ListRowOptions(
                    showChevron = status == PlanningSyncStatus.NEEDS_RECOVERY_KEY,
                    badge =
                        when (status) {
                            PlanningSyncStatus.SYNCED -> RowBadge(null, palette.positive, "Synced")
                            PlanningSyncStatus.WAITING, PlanningSyncStatus.NEEDS_RECOVERY_KEY ->
                                RowBadge(
                                    null,
                                    palette.warning,
                                    "Needs attention",
                                )
                            PlanningSyncStatus.OFF -> null
                        },
                ),
            onClick = onRecoveryKey.takeIf { status == PlanningSyncStatus.NEEDS_RECOVERY_KEY },
        )
    }
}

/** Safe fixes in one place: send unconfirmed edits again, and open what needs a decision. */
@Composable
internal fun SyncCenterActions(
    retryableCount: Int,
    conflictCount: Int,
    busy: Boolean,
    onRetry: () -> Unit,
    onResolve: () -> Unit,
) {
    SectionCard("Fix sync problems") {
        if (retryableCount > 0) {
            ListRow(
                if (busy) "Trying again…" else "Try again",
                "Sends unconfirmed edits again and retries bank links and planning sync",
                retryableCount.toString(),
                leading = RowLeading.Icon(Lucide.RotateCcw),
                onClick = onRetry.takeIf { !busy },
            )
        }
        if (retryableCount > 0 && conflictCount > 0) DividerLine()
        if (conflictCount > 0) {
            ListRow(
                "Resolve conflicts",
                "Choose which version to keep",
                conflictCount.toString(),
                leading = RowLeading.Icon(Lucide.GitMerge),
                options = ListRowOptions(trailingColor = tandemPalette.warning),
                onClick = onResolve,
            )
        }
    }
}
