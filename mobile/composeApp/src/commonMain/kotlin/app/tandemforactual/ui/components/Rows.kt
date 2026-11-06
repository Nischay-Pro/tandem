@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.TandemMotion
import app.tandemforactual.ui.TandemShapes
import app.tandemforactual.ui.TandemSpacing
import app.tandemforactual.ui.TandemTypography
import app.tandemforactual.ui.financeIcon
import app.tandemforactual.ui.tandemMotionSpec
import app.tandemforactual.ui.tandemPalette
import app.tandemforactual.ui.tandemReduceMotion
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
)

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
    onClick: (() -> Unit)? = null,
) {
    val palette = tandemPalette
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
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = if (options.dense) 56.dp else 64.dp)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(role = Role.Button, onClickLabel = title, onClick = onClick)
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
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(TandemShapes.medium)).background(palette.raised),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    resolvedPainter != null -> {
                        Image(
                            painter = resolvedPainter,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp).padding(TandemSpacing.xxSmall),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    resolvedVector != null -> {
                        Icon(
                            imageVector = resolvedVector,
                            contentDescription = null,
                            modifier = Modifier.size(30.dp),
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    title,
                    color = palette.text,
                    fontSize = TandemTypography.body,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (subtitle != null) {
                if (options.animateChanges) {
                    AnimatedStatusText(
                        subtitle,
                        palette.muted,
                        fontSize = TandemTypography.caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        subtitle,
                        color = palette.muted,
                        fontSize = TandemTypography.caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (trailing != null) {
            val effectiveColor = options.trailingColor ?: palette.muted
            if (options.animateChanges) {
                AnimatedStatusText(
                    trailing,
                    effectiveColor,
                    fontSize = TandemTypography.compactBody,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    trailing,
                    color = effectiveColor,
                    fontSize = TandemTypography.compactBody,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else if (onClick != null) {
            Icon(
                Lucide.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp).clearAndSetSemantics {},
                tint = palette.muted,
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
    SectionCardLayout(title, detail, action, onClick, animateDetail, null, content)
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
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onClick: (() -> Unit)? = null,
    animateDetail: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    SectionCardLayout(title, detail, action, onClick, animateDetail, SectionCollapse(expanded, onToggleExpanded), content)
}

@Composable
private fun SectionCardLayout(
    title: String,
    detail: String?,
    action: String?,
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
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (detail != null) {
                            if (animateDetail) {
                                AnimatedStatusText(
                                    detail,
                                    palette.muted,
                                    fontSize = TandemTypography.caption,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            } else {
                                Text(
                                    detail,
                                    color = palette.muted,
                                    fontSize = TandemTypography.caption,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    if (action != null) {
                        Text(
                            action,
                            color = palette.accent,
                            fontSize = TandemTypography.label,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(content = content)
                }
            }
        }
    }
}
