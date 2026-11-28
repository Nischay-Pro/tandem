@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.rows

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.feedback.AnimatedStatusText
import app.tandemforactual.ui.design.icons.logoTileColor
import app.tandemforactual.ui.design.icons.rememberMerchantArtwork
import app.tandemforactual.ui.design.inputs.pointerHighlight
import app.tandemforactual.ui.design.money.amountAnnotatedString
import app.tandemforactual.ui.design.templates.financeIcon
import app.tandemforactual.ui.design.theme.TandemLayout
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CircleDot
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Store
import com.composables.icons.lucide.WalletCards

@Composable
internal fun ListRow(
    title: String,
    subtitle: String? = null,
    trailing: String? = null,
    leading: RowLeading = RowLeading.Automatic,
    options: ListRowOptions = ListRowOptions(),
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    subtitleContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val palette = tandemPalette
    val titleColor = if (options.muted) palette.muted else palette.text
    val rowMinHeight = if (options.dense) TandemLayout.compactListRowMinHeight else TandemLayout.listRowMinHeight
    val stackTrailing = LocalDensity.current.fontScale >= 1.5f
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = rowMinHeight)
                .then(if (options.selected) Modifier.background(palette.accentSurface).semantics { selected = true } else Modifier)
                .then(
                    if (onClick != null || onLongClick != null) {
                        Modifier
                            .pointerHighlight(palette.text, reduceMotion = tandemReduceMotion)
                            .combinedClickable(
                                role = Role.Button,
                                onClickLabel = title,
                                onLongClickLabel = "Select $title",
                                onClick = onClick ?: {},
                                onLongClick = onLongClick,
                            )
                    } else {
                        Modifier
                    },
                ).padding(
                    horizontal = TandemLayout.listRowHorizontalInset,
                    vertical =
                        if (options.dense) {
                            TandemLayout.compactListRowVerticalInset
                        } else {
                            TandemLayout.listRowVerticalInset
                        },
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemLayout.listRowGap),
    ) {
        if (leading != RowLeading.None) {
            ListRowLeadingTile(title, leading, options, titleColor)
        }
        ListRowBody(trailingContent.takeUnless { stackTrailing }) {
            ListRowText(title, subtitle, trailing, options, titleColor, stackTrailing, subtitleContent, trailingContent)
        }
        ListRowTrailing(trailing, options, stackTrailing, showChevron = onClick != null && options.showChevron, trailingContent)
    }
}

/** Leading icon, merchant logo or letter tile of a list row, with its badge. */
@Composable
private fun ListRowLeadingTile(
    title: String,
    leading: RowLeading,
    options: ListRowOptions,
    titleColor: Color,
) {
    val palette = tandemPalette
    val logos = LocalRowLogos.current
    val merchantResource =
        when (leading) {
            is RowLeading.Merchant -> leading.resource
            is RowLeading.Payee -> leading.name?.takeIf(String::isNotBlank)?.let { logos?.payee?.invoke(it) }
            is RowLeading.Account -> leading.name?.takeIf(String::isNotBlank)?.let { logos?.account?.invoke(it) }
            else -> null
        }
    val bankIcon = (leading as? RowLeading.Account)?.name?.takeIf(String::isNotBlank)?.let { logos?.bankIcon?.invoke(it) }
    val photo = (leading as? RowLeading.Account)?.name?.takeIf(String::isNotBlank)?.let { logos?.photo?.invoke(it) }
    val artwork = rememberMerchantArtwork(merchantResource)
    val resolvedPainter = bankIcon ?: artwork
    val resolvedVector =
        when (leading) {
            RowLeading.Automatic -> financeIcon(title) ?: Lucide.CircleDot
            RowLeading.None, is RowLeading.Letter -> null
            is RowLeading.Icon -> leading.imageVector ?: financeIcon(title) ?: Lucide.CircleDot
            is RowLeading.Merchant -> leading.fallback ?: financeIcon(title) ?: Lucide.CircleDot
            is RowLeading.Payee -> leading.fallback ?: financeIcon(leading.name.orEmpty()) ?: Lucide.Store
            is RowLeading.Account -> leading.fallback ?: Lucide.WalletCards
        }
    Box {
        Box(
            modifier =
                Modifier
                    .size(
                        if (options.dense) TandemLayout.compactListRowLeadingSize else TandemLayout.listRowLeadingSize,
                    ).clip(RoundedCornerShape(TandemShapes.medium))
                    .background(logoTileColor(resolvedPainter, if (options.muted) palette.surface else palette.raised, options.muted)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                photo != null -> {
                    AccountPhotoFill(photo)
                }

                resolvedPainter != null -> {
                    Image(
                        painter = resolvedPainter,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(if (options.dense) TandemLayout.compactListRowLogoSize else TandemLayout.listRowLogoSize)
                                .padding(TandemSpacing.xxSmall)
                                .alpha(if (options.muted) 0.48f else 1f),
                        contentScale = ContentScale.Fit,
                    )
                }

                resolvedVector != null -> {
                    Icon(
                        imageVector = resolvedVector,
                        contentDescription = null,
                        modifier =
                            Modifier.size(
                                if (options.dense) TandemLayout.compactListRowIconSize else TandemLayout.listRowIconSize,
                            ),
                        tint = if (options.muted) palette.muted else palette.accent,
                    )
                }

                leading is RowLeading.Letter -> {
                    Text(
                        leading.value,
                        modifier = Modifier.clearAndSetSemantics {},
                        color = titleColor,
                        fontSize = if (options.dense) TandemTypography.action else TandemTypography.cardTitle,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        options.badge?.let { RowBadgeMark(it) }
    }
}

/** The title column with [trailing] beside it, or under it once the two no longer fit side by side. */
@Composable
private fun RowScope.ListRowBody(
    trailing: (@Composable () -> Unit)?,
    text: @Composable () -> Unit,
) {
    Layout(
        content = {
            Box { text() }
            if (trailing != null) Box { trailing() }
        },
        modifier = Modifier.weight(1f),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val gap = TandemLayout.listRowGap.roundToPx()
        val value = measurables.getOrNull(1)?.measure(Constraints(maxWidth = width, maxHeight = constraints.maxHeight))
        val beside = value == null || value.width + gap <= width - LIST_ROW_MIN_TITLE_WIDTH.roundToPx()
        val textWidth = if (value != null && beside) width - value.width - gap else width
        val label = measurables[0].measure(Constraints(textWidth, textWidth, 0, constraints.maxHeight))
        val under = TandemSpacing.extraSmall.roundToPx()
        val height =
            when {
                value == null -> label.height
                beside -> maxOf(label.height, value.height)
                else -> label.height + under + value.height
            }.coerceAtLeast(constraints.minHeight)
        layout(width, height) {
            if (value == null || beside) {
                label.place(0, (height - label.height) / 2)
                value?.place(width - value.width, (height - value.height) / 2)
            } else {
                label.place(0, 0)
                value.place(width - value.width, label.height + under)
            }
        }
    }
}

private val LIST_ROW_MIN_TITLE_WIDTH = 112.dp

/** Title, subtitle and (at large font scales) stacked trailing content of a list row. */
@Composable
private fun ListRowText(
    title: String,
    subtitle: String?,
    trailing: String?,
    options: ListRowOptions,
    titleColor: Color,
    stackTrailing: Boolean,
    subtitleContent: (@Composable () -> Unit)?,
    trailingContent: (@Composable () -> Unit)?,
) {
    val palette = tandemPalette
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
        if (options.animateChanges) {
            AnimatedStatusText(
                title,
                titleColor,
                fontSize = TandemTypography.body,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(
                title,
                color = titleColor,
                fontSize = TandemTypography.body,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (subtitleContent != null) {
            subtitleContent()
        } else if (subtitle != null) {
            if (options.animateChanges) {
                AnimatedStatusText(
                    subtitle,
                    palette.muted,
                    fontSize = TandemTypography.caption,
                    maxLines = options.subtitleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                    fractionDigits = 2,
                )
            } else {
                Text(
                    amountAnnotatedString(subtitle),
                    color = palette.muted,
                    fontSize = TandemTypography.caption,
                    maxLines = options.subtitleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (stackTrailing) ListRowStackedTrailing(trailing, options, trailingContent)
    }
}

/** Trailing content placed under the title when the font scale is large. */
@Composable
private fun ListRowStackedTrailing(
    trailing: String?,
    options: ListRowOptions,
    trailingContent: (@Composable () -> Unit)?,
) {
    val palette = tandemPalette
    if (trailingContent != null) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            trailingContent()
        }
    }
    if (trailing != null) {
        if (options.animateChanges) {
            AnimatedStatusText(
                trailing,
                if (options.muted) palette.muted else options.trailingColor ?: palette.muted,
                fontSize = TandemTypography.compactBody,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fractionDigits = 2,
            )
        } else {
            Text(
                amountAnnotatedString(trailing),
                color = if (options.muted) palette.muted else options.trailingColor ?: palette.muted,
                fontSize = TandemTypography.compactBody,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Trailing value, chevron and trailing action of a list row. */
@Composable
private fun ListRowTrailing(
    trailing: String?,
    options: ListRowOptions,
    stackTrailing: Boolean,
    showChevron: Boolean,
    trailingContent: (@Composable () -> Unit)?,
) {
    val palette = tandemPalette
    if (trailingContent == null && trailing != null && !stackTrailing) {
        val effectiveColor = if (options.muted) palette.muted else options.trailingColor ?: palette.muted
        Box(Modifier.trailingWidthCap(), contentAlignment = Alignment.CenterEnd) {
            if (options.animateChanges) {
                AnimatedStatusText(
                    trailing,
                    effectiveColor,
                    fontSize = TandemTypography.compactBody,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fractionDigits = 2,
                )
            } else {
                Text(
                    amountAnnotatedString(trailing),
                    color = effectiveColor,
                    fontSize = TandemTypography.compactBody,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                )
            }
        }
    } else if (trailingContent == null && showChevron) {
        Icon(
            Lucide.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp).clearAndSetSemantics {},
            tint = palette.muted,
        )
    }
    options.trailingAction?.let { action ->
        TandemIconButton(
            icon = action.icon,
            contentDescription = action.contentDescription,
            modifier = Modifier.padding(start = TandemSpacing.extraSmall),
            sizeDp = 40,
            iconSizeDp = 18,
            enabled = action.enabled,
            tint = palette.accent,
            containerColor = palette.raised,
            onClick = action.onClick,
        )
    }
}

private fun Modifier.trailingWidthCap(): Modifier =
    layout { measurable, constraints ->
        val cap = (constraints.maxWidth * 0.45f).toInt()
        val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = minOf(constraints.maxWidth, cap)))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
