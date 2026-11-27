@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.design.feedback.reservesNoticeSpace
import app.tandemforactual.ui.design.inputs.pointerHighlight
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion

@Composable
internal fun BottomNavigation(
    selected: AppRoute,
    selectTab: (AppRoute) -> Unit,
    visible: Boolean = true,
) {
    val palette = tandemPalette
    val density = LocalDensity.current
    val labelDensity = navigationLabelDensity(density)
    val items =
        navigationTabs()
    val gap = TandemSpacing.xxSmall
    val tabShape = RoundedCornerShape(TandemShapes.capsule - TandemSpacing.extraSmall)
    val selectedIndex = items.indexOfFirst { it.first == selected }
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = if (visible) 1f else 0f }
                .padding(horizontal = TandemSpacing.medium, vertical = TandemSpacing.small)
                .reservesNoticeSpace(enabled = visible)
                .clip(RoundedCornerShape(TandemShapes.capsule))
                .background(palette.raised)
                .border(1.dp, palette.border, RoundedCornerShape(TandemShapes.capsule))
                .padding(TandemSpacing.extraSmall),
    ) {
        val tabWidth = (maxWidth - gap * (items.size - 1)) / items.size
        val indicatorOffset by
            animateDpAsState(
                targetValue = (tabWidth + gap) * selectedIndex.coerceAtLeast(0),
                animationSpec =
                    if (tandemReduceMotion) {
                        tandemMotionSpec(true, TandemMotion.CONTROL_MILLIS)
                    } else {
                        spring(dampingRatio = 0.82f, stiffness = 520f)
                    },
                label = "tab-indicator-offset",
            )
        Box(Modifier.matchParentSize()) {
            if (selectedIndex >= 0) {
                Box(
                    Modifier
                        .offset(x = indicatorOffset)
                        .width(tabWidth)
                        .fillMaxHeight()
                        .clip(tabShape)
                        .background(palette.accentSurface),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            items.forEach { (route, label) ->
                BottomNavigationTab(route, label, route == selected, visible, tabShape, labelDensity, selectTab)
            }
        }
    }
}

/** One tab in the bottom bar: its glyph and label, tinted and scaled when selected. */
@Composable
private fun RowScope.BottomNavigationTab(
    route: AppRoute,
    label: String,
    active: Boolean,
    visible: Boolean,
    tabShape: RoundedCornerShape,
    labelDensity: Density,
    selectTab: (AppRoute) -> Unit,
) {
    val palette = tandemPalette
    val tabColor by
        animateColorAsState(
            targetValue = if (active) palette.accent else palette.muted,
            animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
            label = "tab-color-${route.name.lowercase()}",
        )
    val iconScale by
        animateFloatAsState(
            targetValue = if (active) 1.08f else 1f,
            animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTROL_MILLIS),
            label = "tab-icon-scale-${route.name.lowercase()}",
        )
    Column(
        modifier =
            Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 52.dp)
                .clip(tabShape)
                .pointerHighlight(tandemPalette.text, tabShape, tandemReduceMotion, visible)
                .selectable(
                    selected = active,
                    enabled = visible,
                    role = Role.Tab,
                    onClick = { selectTab(route) },
                ).semantics(mergeDescendants = true) {
                    if (visible) {
                        contentDescription = label
                        this.selected = active
                    }
                }.then(if (visible) Modifier else Modifier.clearAndSetSemantics {})
                .padding(vertical = TandemSpacing.small),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall),
    ) {
        NavigationGlyph(route, tabColor, iconScale)
        CompositionLocalProvider(LocalDensity provides labelDensity) {
            Text(
                label,
                color = tabColor,
                fontSize = TandemTypography.micro,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
    }
}
