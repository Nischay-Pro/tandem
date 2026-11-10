@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.TandemMotion
import app.tandemforactual.ui.TandemShapes
import app.tandemforactual.ui.TandemSpacing
import app.tandemforactual.ui.TandemTypography
import app.tandemforactual.ui.financeIcon
import app.tandemforactual.ui.tandemMotionSpec
import app.tandemforactual.ui.tandemPalette
import app.tandemforactual.ui.tandemReduceMotion
import app.tandemforactual.ui.tandemRevealEnter
import app.tandemforactual.ui.tandemRevealExit
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CircleDot
import com.composables.icons.lucide.Lucide
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

internal data class ListRowOptions(
    val trailingColor: Color? = null,
    val dense: Boolean = false,
    val animateChanges: Boolean = false,
    val trailingAction: ListRowAction? = null,
    val showChevron: Boolean = true,
)

internal data class ListRowAction(
    val icon: ImageVector,
    val contentDescription: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

@Composable
internal fun DividerLine() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(tandemPalette.border.copy(alpha = 0.8f)))
}

internal sealed interface RowLeading {
    data object Automatic : RowLeading

    data object None : RowLeading

    data class Icon(
        val imageVector: ImageVector?,
    ) : RowLeading

    data class Merchant(
        val resource: DrawableResource?,
        val fallback: ImageVector? = null,
    ) : RowLeading

    data class Letter(
        val value: String,
    ) : RowLeading
}

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
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val palette = tandemPalette
    val stackTrailing = LocalDensity.current.fontScale >= 1.5f
    val merchantResource = (leading as? RowLeading.Merchant)?.resource
    val resolvedPainter = merchantResource?.let { painterResource(it) }
    val resolvedVector =
        when (leading) {
            RowLeading.Automatic -> financeIcon(title) ?: Lucide.CircleDot
            RowLeading.None, is RowLeading.Letter -> null
            is RowLeading.Icon -> leading.imageVector ?: financeIcon(title) ?: Lucide.CircleDot
            is RowLeading.Merchant -> leading.fallback ?: financeIcon(title) ?: Lucide.CircleDot
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = if (options.dense) 56.dp else 64.dp)
                .then(
                    if (onClick != null || onLongClick != null) {
                        Modifier.combinedClickable(
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
                    horizontal = TandemSpacing.large,
                    vertical =
                        if (options.dense) {
                            TandemSpacing.extraSmall + TandemSpacing.xxSmall
                        } else {
                            TandemSpacing.small +
                                TandemSpacing.xxSmall
                        },
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
    ) {
        if (leading != RowLeading.None) {
            Box(
                modifier =
                    Modifier
                        .size(
                            if (options.dense) 36.dp else 44.dp,
                        ).clip(RoundedCornerShape(TandemShapes.medium))
                        .background(palette.raised),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    resolvedPainter != null -> {
                        Image(
                            painter = resolvedPainter,
                            contentDescription = null,
                            modifier = Modifier.size(if (options.dense) 28.dp else 32.dp).padding(TandemSpacing.xxSmall),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    resolvedVector != null -> {
                        Icon(
                            imageVector = resolvedVector,
                            contentDescription = null,
                            modifier = Modifier.size(if (options.dense) 24.dp else 30.dp),
                            tint = palette.accent,
                        )
                    }
                    leading is RowLeading.Letter -> {
                        Text(
                            leading.value,
                            modifier = Modifier.clearAndSetSemantics {},
                            color = palette.accent,
                            fontSize = if (options.dense) TandemTypography.action else TandemTypography.cardTitle,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
            if (options.animateChanges) {
                AnimatedStatusText(
                    title,
                    palette.text,
                    fontSize = TandemTypography.body,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                )
            } else {
                Text(
                    title,
                    color = palette.text,
                    fontSize = TandemTypography.body,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                )
            }
            if (subtitle != null) {
                if (options.animateChanges) {
                    AnimatedStatusText(
                        subtitle,
                        palette.muted,
                        fontSize = TandemTypography.caption,
                        maxLines = 3,
                    )
                } else {
                    Text(
                        subtitle,
                        color = palette.muted,
                        fontSize = TandemTypography.caption,
                        maxLines = 3,
                    )
                }
            }
            if (stackTrailing && trailingContent != null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    trailingContent()
                }
            }
            if (stackTrailing && trailing != null) {
                if (options.animateChanges) {
                    AnimatedStatusText(
                        trailing,
                        options.trailingColor ?: palette.muted,
                        fontSize = TandemTypography.compactBody,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                    )
                } else {
                    Text(
                        trailing,
                        color = options.trailingColor ?: palette.muted,
                        fontSize = TandemTypography.compactBody,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                    )
                }
            }
        }
        if (!stackTrailing && trailingContent != null) {
            trailingContent()
        } else if (trailing != null && !stackTrailing) {
            val effectiveColor = options.trailingColor ?: palette.muted
            if (options.animateChanges) {
                AnimatedStatusText(
                    trailing,
                    effectiveColor,
                    fontSize = TandemTypography.compactBody,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                )
            } else {
                Text(
                    trailing,
                    color = effectiveColor,
                    fontSize = TandemTypography.compactBody,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                )
            }
        } else if (trailingContent == null && onClick != null && options.showChevron) {
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
}

@Composable
internal fun SectionCard(
    title: String,
    detail: String? = null,
    action: String? = null,
    onClick: (() -> Unit)? = null,
    animateDetail: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    SectionCardLayout(title, detail, action, null, onClick, animateDetail, null, content)
}

private data class SectionCollapse(
    val expanded: Boolean,
    val onToggle: () -> Unit,
)

@Composable
internal fun CollapsibleSectionCard(
    title: String,
    detail: String? = null,
    action: String? = null,
    actionColor: Color? = null,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onClick: (() -> Unit)? = null,
    animateDetail: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    SectionCardLayout(title, detail, action, actionColor, onClick, animateDetail, SectionCollapse(expanded, onToggleExpanded), content)
}

@Composable
private fun SectionCardLayout(
    title: String,
    detail: String?,
    action: String?,
    actionColor: Color?,
    onClick: (() -> Unit)?,
    animateDetail: Boolean,
    collapse: SectionCollapse?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = tandemPalette
    SurfaceCard(onClick = onClick) {
        Column {
            if (title.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
                        Text(
                            title,
                            color = palette.text,
                            fontSize = TandemTypography.body,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                        )
                        if (detail != null) {
                            if (animateDetail) {
                                AnimatedStatusText(
                                    detail,
                                    palette.muted,
                                    fontSize = TandemTypography.caption,
                                    maxLines = 3,
                                )
                            } else {
                                Text(
                                    detail,
                                    color = palette.muted,
                                    fontSize = TandemTypography.caption,
                                    maxLines = 3,
                                )
                            }
                        }
                    }
                    if (action != null) {
                        Text(
                            action,
                            color = actionColor ?: palette.accent,
                            fontSize = TandemTypography.label,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                        )
                    }
                    if (collapse != null) {
                        val arrowRotation by
                            animateFloatAsState(
                                targetValue = if (!collapse.expanded) -90f else 0f,
                                animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTROL_MILLIS),
                                label = "section-chevron-$title",
                            )
                        Icon(
                            imageVector = Lucide.ChevronDown,
                            contentDescription = if (!collapse.expanded) "Expand $title" else "Collapse $title",
                            modifier =
                                Modifier
                                    .padding(start = TandemSpacing.extraSmall)
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .clickable(role = Role.Button, onClick = collapse.onToggle)
                                    .padding(TandemSpacing.small)
                                    .graphicsLayer { rotationZ = arrowRotation },
                            tint = palette.muted,
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.border.copy(alpha = 0.75f)))
            }
            if (collapse == null) {
                content()
            } else {
                AnimatedVisibility(
                    visible = collapse.expanded,
                    enter = tandemRevealEnter(tandemReduceMotion),
                    exit = tandemRevealExit(tandemReduceMotion),
                ) {
                    Column(content = content)
                }
            }
        }
    }
}
