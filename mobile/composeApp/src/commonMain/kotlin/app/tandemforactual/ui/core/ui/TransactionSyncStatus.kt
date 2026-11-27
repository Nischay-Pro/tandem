@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.ui.core.navigation.openNeedsReview
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.UNSYNCED_TRANSACTION_WRITE_STATUSES
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.TriangleAlert

private fun transactionSyncRank(status: String): Int =
    when (status) {
        "rejected", "conflict" -> 3
        "uncertain" -> 2
        "sending" -> 1
        else -> 0
    }

internal fun transactionSyncOperations(pendingWrites: List<OutboxOperationEntity>): Map<String, OutboxOperationEntity> =
    pendingWrites
        .filter { it.recordType == "transaction" && it.status in UNSYNCED_TRANSACTION_WRITE_STATUSES }
        .groupBy { it.recordId }
        .mapValues { (_, operations) -> operations.maxBy { transactionSyncRank(it.status) } }

internal fun transactionSyncNeedsReview(status: String): Boolean = transactionSyncRank(status) >= 2

internal fun transactionSyncShortLabel(status: String): String =
    when (status) {
        "queued" -> "Waiting"
        "sending" -> "Syncing"
        "uncertain" -> "Unconfirmed"
        "rejected" -> "Rejected"
        else -> "Needs review"
    }

@Composable
internal fun transactionSyncColor(status: String): Color =
    when (status) {
        "rejected" -> tandemPalette.negative
        "conflict", "uncertain" -> tandemPalette.warning
        else -> tandemPalette.accent
    }

@Composable
internal fun TransactionSyncChip(status: String) {
    val color = transactionSyncColor(status)
    Text(
        transactionSyncShortLabel(status),
        Modifier
            .clip(RoundedCornerShape(TandemShapes.capsule))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = TandemSpacing.small, vertical = 2.dp),
        color = color,
        fontSize = TandemTypography.micro,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun SyncNoticeCard(
    icon: ImageVector,
    color: Color,
    title: String,
    detail: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val palette = tandemPalette
    val shape = RoundedCornerShape(TandemShapes.large)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.35f), shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(TandemSpacing.large),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = color)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall)) {
            Text(title, color = palette.text, fontSize = TandemTypography.body, fontWeight = FontWeight.SemiBold)
            Text(detail, color = palette.muted, fontSize = TandemTypography.caption)
        }
        if (onClick != null) {
            Icon(Lucide.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp), tint = palette.muted)
        }
    }
}

@Composable
internal fun TransactionSyncNotice(
    operation: OutboxOperationEntity,
    state: AppState,
    navigation: AppNavigation,
) {
    val status = operation.status
    val (icon, title, detail) =
        when (status) {
            "queued" ->
                Triple(Lucide.Clock, "Waiting to sync", "Your edit is saved on this device and will be sent on the next sync.")
            "sending" -> Triple(Lucide.RefreshCw, "Syncing", "Sending your edit to Actual.")
            "uncertain" ->
                Triple(Lucide.CircleAlert, "Status unknown", "Tandem couldn’t confirm that Actual saved this edit. Check its status.")
            "rejected" -> Triple(Lucide.TriangleAlert, "Change rejected", "Actual didn’t accept this edit. See what happened.")
            else ->
                Triple(Lucide.TriangleAlert, "Needs review", "This transaction also changed in Actual. Choose which version to keep.")
        }
    SyncNoticeCard(icon, transactionSyncColor(status), title, detail, onClick = {
        if (transactionSyncNeedsReview(status)) {
            openNeedsReview(state, navigation, operation, null, null)
        } else {
            navigation.open(AppRoute.PENDING_CHANGES)
        }
    })
}
