@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.accounts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.tandemforactual.ui.core.session.reorderAccessibilityAction
import app.tandemforactual.ui.core.ui.accountTypeIcon
import app.tandemforactual.ui.design.buttons.ReorderDragHandle
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.icons.LogoPainter
import app.tandemforactual.ui.design.icons.logoTileColor
import app.tandemforactual.ui.design.icons.rememberMerchantArtwork
import app.tandemforactual.ui.design.inputs.pointerHighlight
import app.tandemforactual.ui.design.money.PrivacyAmountText
import app.tandemforactual.ui.design.rows.RowBadge
import app.tandemforactual.ui.design.rows.RowBadgeMark
import app.tandemforactual.ui.design.rows.RowContextMenu
import app.tandemforactual.ui.design.rows.RowMenuAction
import app.tandemforactual.ui.design.theme.TandemLayout
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import com.composables.icons.lucide.ArrowUpDown
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import org.jetbrains.compose.resources.DrawableResource

@Composable
internal fun AccountSummaryCard(
    account: AccountListItemUi,
    merchantArtwork: DrawableResource?,
    bankIcon: LogoPainter? = null,
    privacyModeEnabled: Boolean = false,
    fractionDigits: Int = 2,
    modifier: Modifier = Modifier,
    isReordering: Boolean = false,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    reorderEnabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onDragStarted: (() -> Unit)? = null,
    onDragBy: ((Float) -> Unit)? = null,
    onDragStopped: (() -> Unit)? = null,
    highlighted: Boolean = false,
    bankBadge: RowBadge? = null,
) {
    val stackBalance = LocalDensity.current.fontScale >= 1.5f
    val balanceColor = accountBalanceColor(account)
    SurfaceCard(modifier = modifier, highlighted = highlighted) {
        RowContextMenu(if (isReordering) emptyList() else accountRowMenuActions(onClick, onEdit, onLongClick)) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .pointerHighlight(tandemPalette.text, reduceMotion = tandemReduceMotion)
                        .combinedClickable(
                            role = Role.Button,
                            onClickLabel = if (isReordering) "Finish reordering" else "Open ${account.name}",
                            onClick = onClick,
                        ).then(
                            if (onLongClick != null) {
                                Modifier.reorderAccessibilityAction(account.name, onLongClick)
                            } else {
                                Modifier
                            },
                        ).defaultMinSize(minHeight = TandemLayout.listRowMinHeight)
                        .padding(
                            horizontal = TandemLayout.listRowHorizontalInset,
                            vertical = TandemLayout.listRowVerticalInset,
                        ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TandemLayout.listRowGap),
            ) {
                val artwork = rememberMerchantArtwork(merchantArtwork)
                AccountSummaryIcon(account, bankIcon ?: artwork, bankBadge)
                AccountSummaryText(
                    account = account,
                    showBalance = stackBalance && !isReordering,
                    balanceColor = balanceColor,
                    privacyModeEnabled = privacyModeEnabled,
                    fractionDigits = fractionDigits,
                    modifier = Modifier.weight(1f),
                )
                if (isReordering) {
                    AccountReorderControls(
                        account = account,
                        reorderEnabled = reorderEnabled,
                        canMoveUp = canMoveUp,
                        canMoveDown = canMoveDown,
                        onEdit = onEdit,
                        onMoveUp = onMoveUp,
                        onMoveDown = onMoveDown,
                        onDragStarted = onDragStarted,
                        onDragBy = onDragBy,
                        onDragStopped = onDragStopped,
                    )
                } else if (!stackBalance) {
                    AccountBalanceText(account, balanceColor, privacyModeEnabled, fractionDigits)
                }
            }
        }
    }
}

/** Open, account options and reorder: what a tap, the edit button and long-press do, for a right-click. */
internal fun accountRowMenuActions(
    onOpen: () -> Unit,
    onEdit: (() -> Unit)?,
    onReorder: (() -> Unit)?,
): List<RowMenuAction> =
    listOfNotNull(
        RowMenuAction("Open", Lucide.ExternalLink, onClick = onOpen),
        onEdit?.let { RowMenuAction("Account options", Lucide.Pencil, onClick = it) },
        onReorder?.let { RowMenuAction("Reorder accounts", Lucide.ArrowUpDown, onClick = it) },
    )

@Composable
private fun accountBalanceColor(account: AccountListItemUi): Color {
    val palette = tandemPalette
    return when {
        account.closed || account.balanceMinor == null || account.balanceMinor == 0L -> palette.muted
        account.balanceMinor > 0 -> palette.positive
        else -> palette.negative
    }
}

/** The account's logo or type icon, with its bank badge. */
@Composable
private fun AccountSummaryIcon(
    account: AccountListItemUi,
    artwork: LogoPainter?,
    bankBadge: RowBadge?,
) {
    val palette = tandemPalette
    Box {
        Box(
            Modifier
                .size(TandemLayout.listRowLeadingSize)
                .clip(RoundedCornerShape(TandemShapes.medium))
                .background(logoTileColor(artwork, if (account.closed) palette.surface else palette.raised, account.closed)),
            contentAlignment = Alignment.Center,
        ) {
            if (artwork != null) {
                Image(
                    artwork,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(TandemLayout.listRowLogoSize)
                            .padding(TandemSpacing.xxSmall)
                            .alpha(if (account.closed) 0.48f else 1f),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Icon(
                    accountTypeIcon(account.name, account.kind),
                    contentDescription = null,
                    modifier = Modifier.size(TandemLayout.listRowIconSize),
                    tint = if (account.closed) palette.muted else palette.accent,
                )
            }
        }
        bankBadge?.let { RowBadgeMark(it) }
    }
}

/** The account's name, type and status, and its balance when it is stacked under them. */
@Composable
private fun AccountSummaryText(
    account: AccountListItemUi,
    showBalance: Boolean,
    balanceColor: Color,
    privacyModeEnabled: Boolean,
    fractionDigits: Int,
    modifier: Modifier,
) {
    val palette = tandemPalette
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
    ) {
        Text(
            account.name,
            color = if (account.closed) palette.muted else palette.text,
            fontSize = TandemTypography.body,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${account.kind.label} · ${account.status}",
            color = palette.muted,
            fontSize = TandemTypography.caption,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (showBalance) {
            AccountBalanceText(
                account = account,
                color = balanceColor,
                privacyModeEnabled = privacyModeEnabled,
                fractionDigits = fractionDigits,
                modifier = Modifier.fillMaxWidth(),
                wrap = true,
            )
        }
    }
}

/** The edit button and drag handle shown while accounts are being reordered. */
@Composable
private fun AccountReorderControls(
    account: AccountListItemUi,
    reorderEnabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: (() -> Unit)?,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDragStarted: (() -> Unit)?,
    onDragBy: ((Float) -> Unit)?,
    onDragStopped: (() -> Unit)?,
) {
    onEdit?.let { edit ->
        TandemIconButton(
            Lucide.Pencil,
            "Edit ${account.name}",
            sizeDp = 40,
            iconSizeDp = 18,
            enabled = reorderEnabled,
            onClick = edit,
        )
    }
    ReorderDragHandle(
        itemName = account.name,
        enabled = reorderEnabled && (canMoveUp || canMoveDown),
        canMoveUp = canMoveUp,
        canMoveDown = canMoveDown,
        onMoveUp = onMoveUp,
        onMoveDown = onMoveDown,
        onDragStarted = onDragStarted,
        onDragBy = onDragBy,
        onDragStopped = onDragStopped,
    )
}

@Composable
private fun AccountBalanceText(
    account: AccountListItemUi,
    color: Color,
    privacyModeEnabled: Boolean,
    fractionDigits: Int = 2,
    modifier: Modifier = Modifier,
    wrap: Boolean = false,
) {
    PrivacyAmountText(
        account.balance,
        privacyModeEnabled = privacyModeEnabled,
        fractionDigits = fractionDigits,
        modifier =
            if (privacyModeEnabled) {
                modifier
            } else {
                modifier.semantics {
                    contentDescription = accountBalanceAccessibilityDescription(account.balanceMinor, account.balance)
                }
            },
        color = if (account.closed) tandemPalette.muted else color,
        fontSize = TandemTypography.compactBody,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.End,
        maxLines = if (wrap) Int.MAX_VALUE else 2,
        overflow = TextOverflow.Ellipsis,
    )
}
