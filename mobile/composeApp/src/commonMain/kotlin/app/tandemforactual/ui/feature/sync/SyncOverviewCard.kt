@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sync
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.format.formatMomentAtEpochMilliseconds
import app.tandemforactual.ui.core.session.SyncRecovery
import app.tandemforactual.ui.core.session.syncRecovery
import app.tandemforactual.ui.core.ui.SyncStatusRing
import app.tandemforactual.ui.design.buttons.PrimaryButton
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Cloud
import com.composables.icons.lucide.CloudAlert
import com.composables.icons.lucide.CloudCheck
import com.composables.icons.lucide.CloudOff
import com.composables.icons.lucide.CloudSync
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.TriangleAlert

/** Sync status heading and ring, error details, pending and review counts, and the main sync button. */
@Composable
internal fun SyncOverviewCard(
    budgetName: String,
    attentionCount: Int,
    waitingCount: Int,
    isSyncing: Boolean,
    isServerReachable: Boolean,
    syncError: String?,
    syncRecovery: SyncRecovery,
    onRecover: () -> Unit,
    lastSyncedAtEpochMs: Long?,
    nowEpochMilliseconds: Long,
    onSyncNow: (() -> Unit)?,
    onPendingClick: () -> Unit,
    onReviewClick: () -> Unit,
) {
    val status = syncStatusPresentation(isSyncing, isServerReachable, syncError, attentionCount, waitingCount, lastSyncedAtEpochMs)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TandemSpacing.large)) {
        SyncPageHeading(
            eyebrow = budgetName,
            title = "${status.title}.",
            detail = status.detail,
        )
        SyncStatusRing(
            icon = status.icon,
            color = status.color,
            active = isSyncing,
            caption =
                lastSyncedAtEpochMs?.let { "Last synced ${formatMomentAtEpochMilliseconds(it, nowEpochMilliseconds)}" }
                    ?: "Not synced yet",
        )
        if (syncError != null) {
            SyncErrorDetails(syncError, status.color)
        }
        SyncSummaryCard(waitingCount, attentionCount, onPendingClick, onReviewClick)
        val recovers = syncError != null && !isSyncing && syncRecovery != SyncRecovery.RETRY
        PrimaryButton(
            when {
                isSyncing -> "Syncing…"
                recovers -> syncRecovery.label
                !isServerReachable -> "Try connecting again"
                syncError != null -> "Try again"
                waitingCount > 0 -> "Sync $waitingCount ${if (waitingCount == 1) "change" else "changes"}"
                else -> "Sync now"
            },
            enabled = recovers || onSyncNow != null,
            loading = isSyncing,
            onClick = { if (recovers) onRecover() else onSyncNow?.invoke() },
        )
    }
}

@Composable
private fun syncStatusPresentation(
    isSyncing: Boolean,
    isServerReachable: Boolean,
    syncError: String?,
    attentionCount: Int,
    waitingCount: Int,
    lastSyncedAtEpochMs: Long?,
): SyncStatusPresentation {
    val palette = tandemPalette
    return when {
        isSyncing ->
            SyncStatusPresentation(
                title = "Syncing now",
                detail = "Getting the latest from Actual and sending your changes.",
                icon = Lucide.CloudSync,
                color = palette.syncing,
            )
        !isServerReachable ->
            SyncStatusPresentation(
                title = "You’re offline",
                detail =
                    when (waitingCount) {
                        0 -> "Showing the budget saved on this device. The latest from Actual loads when you reconnect."
                        1 -> "1 change is saved on this device. It syncs when you reconnect."
                        else -> "$waitingCount changes are saved on this device. They sync when you reconnect."
                    },
                icon = Lucide.CloudOff,
                color = palette.warning,
            )
        syncError != null ->
            SyncStatusPresentation(
                title = "Couldn’t finish syncing",
                detail =
                    when (waitingCount) {
                        0 -> "Couldn’t load the latest from Actual. Your saved copy is still here."
                        1 -> "Your change is saved on this device."
                        else -> "Your $waitingCount changes are saved on this device."
                    },
                icon = Lucide.CloudAlert,
                color = palette.negative,
            )
        attentionCount > 0 ->
            SyncStatusPresentation(
                title = "Needs review",
                detail = "$attentionCount ${if (attentionCount == 1) "change needs" else "changes need"} a decision.",
                icon = Lucide.CircleAlert,
                color = palette.warning,
            )
        waitingCount > 0 ->
            SyncStatusPresentation(
                title = "Ready to sync",
                detail = "$waitingCount ${if (waitingCount == 1) "change is" else "changes are"} waiting.",
                icon = Lucide.CloudSync,
                color = palette.syncing,
            )
        lastSyncedAtEpochMs != null ->
            SyncStatusPresentation(
                title = "Up to date",
                detail = "Your budget is in sync.",
                icon = Lucide.CloudCheck,
                color = palette.positive,
            )
        else ->
            SyncStatusPresentation(
                title = "Ready to sync",
                detail = "Your budget is saved on this device.",
                icon = Lucide.Cloud,
                color = palette.accent,
            )
    }
}

/** Expandable card with the sync error message. */
@Composable
private fun SyncErrorDetails(
    syncError: String,
    color: androidx.compose.ui.graphics.Color,
) {
    val palette = tandemPalette
    var showErrorDetails by rememberSaveable(syncError) { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        if (showErrorDetails) 90f else 0f,
        tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTROL_MILLIS),
        label = "sync-error-chevron",
    )
    SurfaceCard {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (showErrorDetails) "Hide error details" else "Show error details",
                ) { showErrorDetails = !showErrorDetails }
                .animateContentSize(tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTENT_SIZE_MILLIS))
                .padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
        ) {
            Icon(Lucide.CircleAlert, contentDescription = null, modifier = Modifier.size(22.dp), tint = color)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall)) {
                Text("What went wrong", color = palette.text, fontSize = TandemTypography.body, fontWeight = FontWeight.SemiBold)
                Text(
                    syncError,
                    color = palette.muted,
                    fontSize = TandemTypography.caption,
                    lineHeight = TandemTypography.compactLineHeight,
                    maxLines = if (showErrorDetails) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Lucide.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = chevronRotation },
                tint = palette.muted,
            )
        }
    }
}

/** Pending changes and needs-review rows with their counts. */
@Composable
private fun SyncSummaryCard(
    waitingCount: Int,
    attentionCount: Int,
    onPendingClick: () -> Unit,
    onReviewClick: () -> Unit,
) {
    val palette = tandemPalette
    SurfaceCard {
        SyncSummaryRow(
            icon = Lucide.RefreshCw,
            color = palette.syncing,
            title = "Pending changes",
            detail =
                when (waitingCount) {
                    0 -> "No changes waiting to upload"
                    1 -> "1 change saved on this device"
                    else -> "$waitingCount changes saved on this device"
                },
            count = waitingCount,
            onClick = onPendingClick,
        )
        DividerLine()
        SyncSummaryRow(
            icon = Lucide.TriangleAlert,
            color = palette.warning,
            title = "Needs review",
            detail =
                when (attentionCount) {
                    0 -> "Nothing needs a decision"
                    1 -> "1 change needs a decision"
                    else -> "$attentionCount changes need a decision"
                },
            count = attentionCount,
            onClick = onReviewClick.takeIf { attentionCount > 0 },
        )
    }
}

@Composable
private fun SyncSummaryRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
    title: String,
    detail: String,
    count: Int,
    onClick: (() -> Unit)?,
) {
    val palette = tandemPalette
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = if (count > 0) color else palette.muted)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall)) {
            Text(title, color = palette.text, fontSize = TandemTypography.body, fontWeight = FontWeight.SemiBold)
            Text(detail, color = palette.muted, fontSize = TandemTypography.caption)
        }
        Text(
            count.toString(),
            color =
                if (count >
                    0
                ) {
                    color
                } else {
                    palette.muted
                },
            fontSize = TandemTypography.body,
            fontWeight = FontWeight.SemiBold,
        )
        if (onClick != null) {
            Icon(Lucide.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp), tint = palette.muted)
        }
    }
}

private data class SyncStatusPresentation(
    val title: String,
    val detail: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: androidx.compose.ui.graphics.Color,
)
