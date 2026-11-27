@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.rows

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.buttons.MIN_TOUCH_TARGET
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.AnimatedStatusText
import app.tandemforactual.ui.design.theme.TandemLayout
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import app.tandemforactual.ui.design.theme.tandemRevealEnter
import app.tandemforactual.ui.design.theme.tandemRevealExit
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Lucide
import org.jetbrains.compose.resources.DrawableResource

internal data class ListRowOptions(
    val trailingColor: Color? = null,
    val dense: Boolean = false,
    val muted: Boolean = false,
    val animateChanges: Boolean = false,
    val trailingAction: ListRowAction? = null,
    val showChevron: Boolean = true,
    val subtitleMaxLines: Int = 3,
    val badge: RowBadge? = null,
    val selected: Boolean = false,
)

/** A small status mark on the corner of the row's icon. */
internal data class RowBadge(
    val icon: ImageVector?,
    val color: Color,
    val description: String,
)

internal data class ListRowAction(
    val icon: ImageVector,
    val contentDescription: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

@Composable
internal fun BoxScope.RowBadgeMark(badge: RowBadge) {
    val palette = tandemPalette
    Box(
        Modifier
            .align(Alignment.BottomEnd)
            .offset(TandemSpacing.extraSmall, TandemSpacing.extraSmall)
            .size(TandemLayout.rowBadgeSize)
            .clip(CircleShape)
            .background(palette.surface)
            .padding(TandemSpacing.xxSmall)
            .clip(CircleShape)
            .background(badge.color)
            .semantics { contentDescription = badge.description },
        contentAlignment = Alignment.Center,
    ) {
        if (badge.icon != null) {
            Icon(badge.icon, contentDescription = null, Modifier.fillMaxSize(0.7f), tint = palette.surface)
        } else {
            Text("!", color = palette.surface, fontSize = TandemTypography.caption, fontWeight = FontWeight.Bold)
        }
    }
}

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

    /** The payee's logo when one is known, otherwise [fallback]. */
    data class Payee(
        val name: String?,
        val fallback: ImageVector? = null,
    ) : RowLeading

    /** The account's bank or brokerage logo when one is known, otherwise [fallback]. */
    data class Account(
        val name: String?,
        val fallback: ImageVector? = null,
    ) : RowLeading
}

@Composable
internal fun SectionCard(
    title: String,
    detail: String? = null,
    action: String? = null,
    onClick: (() -> Unit)? = null,
    animateDetail: Boolean = false,
    actionContent: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    SectionCardLayout(title, detail, action, null, onClick, animateDetail, null, actionContent, content)
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
    actionContent: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    SectionCardLayout(
        title,
        detail,
        action,
        actionColor,
        onClick,
        animateDetail,
        SectionCollapse(expanded, onToggleExpanded),
        actionContent,
        content,
    )
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
    actionContent: (@Composable () -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = tandemPalette
    SurfaceCard(onClick = onClick) {
        Column {
            if (title.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
                    horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionTitleText(title, detail, animateDetail)
                    if (actionContent != null) {
                        actionContent()
                    } else if (action != null) {
                        Text(
                            action,
                            color = actionColor ?: palette.accent,
                            fontSize = TandemTypography.label,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                        )
                    }
                    if (collapse != null) SectionCollapseChevron(title, collapse)
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

/** Title and optional detail of a section card header. */
@Composable
private fun RowScope.SectionTitleText(
    title: String,
    detail: String?,
    animateDetail: Boolean,
) {
    val palette = tandemPalette
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
}

/** Rotating chevron that toggles a collapsible section. */
@Composable
private fun SectionCollapseChevron(
    title: String,
    collapse: SectionCollapse,
) {
    val palette = tandemPalette
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
                .size(MIN_TOUCH_TARGET)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = collapse.onToggle)
                .padding(14.dp)
                .graphicsLayer { rotationZ = arrowRotation },
        tint = palette.muted,
    )
}
