@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.DropdownMenu
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.WorkspaceLayout
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.theme.TandemLayout
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Menu

@Composable
internal fun SideNavigationRail(
    selected: AppRoute,
    selectTab: (AppRoute) -> Unit,
    modifier: Modifier = Modifier,
    layouts: List<WorkspaceLayout> = emptyList(),
    layout: WorkspaceLayout = WorkspaceLayout.SINGLE,
    onLayout: (WorkspaceLayout) -> Unit = {},
    onOpenMenu: (() -> Unit)? = null,
) {
    val palette = tandemPalette
    val items =
        navigationTabs()
    BoxWithConstraints(
        modifier
            .width(TandemLayout.railWidth)
            .fillMaxHeight()
            .background(palette.surface),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(vertical = TandemSpacing.extraSmall),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            RailItems(items, selected, selectTab, onOpenMenu)
            if (layouts.size > 1) WorkspaceLayoutButton(layouts, layout, onLayout)
        }
    }
}

@Composable
private fun RailItems(
    items: List<Pair<AppRoute, String>>,
    selected: AppRoute,
    selectTab: (AppRoute) -> Unit,
    onOpenMenu: (() -> Unit)?,
) {
    val palette = tandemPalette
    if (onOpenMenu != null) TandemIconButton(Lucide.Menu, "Menu", containerColor = palette.background, onClick = onOpenMenu)
    items.forEach { (route, label) ->
        val active = route == selected
        val tabColor by
            animateColorAsState(
                targetValue = if (active) palette.accent else palette.muted,
                animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
                label = "rail-tab-color-${route.name.lowercase()}",
            )
        val iconScale by
            animateFloatAsState(
                targetValue = if (active) 1.08f else 1f,
                animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTROL_MILLIS),
                label = "rail-tab-icon-scale-${route.name.lowercase()}",
            )
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 52.dp)
                    .clip(RoundedCornerShape(TandemShapes.medium))
                    .selectable(
                        selected = active,
                        role = Role.Tab,
                        onClick = { selectTab(route) },
                    ).semantics(mergeDescendants = true) {
                        contentDescription = label
                        this.selected = active
                    }.padding(vertical = TandemSpacing.xxSmall),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall),
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (active) palette.accentSurface else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                NavigationGlyph(route, tabColor, iconScale)
            }
            CompositionLocalProvider(LocalDensity provides navigationLabelDensity(LocalDensity.current)) {
                Text(
                    label,
                    color = tabColor,
                    fontSize = TandemTypography.micro,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun WorkspaceLayoutButton(
    layouts: List<WorkspaceLayout>,
    layout: WorkspaceLayout,
    onLayout: (WorkspaceLayout) -> Unit,
) {
    val palette = tandemPalette
    var expanded by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (layout != WorkspaceLayout.SINGLE) palette.accentSurface else Color.Transparent)
                .clickable(onClickLabel = "Choose how many screens to show") { expanded = true }
                .semantics { contentDescription = "Screen layout: ${layout.label}" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                layout.icon,
                null,
                tint =
                    if (layout !=
                        WorkspaceLayout.SINGLE
                    ) {
                        palette.accent
                    } else {
                        palette.muted
                    },
                modifier = Modifier.size(22.dp),
            )
        }
        androidx.compose.material.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(palette.surface),
        ) {
            layouts.forEach { option ->
                androidx.compose.material.DropdownMenuItem(onClick = {
                    expanded = false
                    onLayout(option)
                }) {
                    Icon(option.icon, null, tint = if (option == layout) palette.accent else palette.muted, modifier = Modifier.size(20.dp))
                    Text(
                        option.label,
                        color = if (option == layout) palette.accent else palette.text,
                        fontSize = TandemTypography.compactBody,
                        fontWeight = if (option == layout) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(start = TandemSpacing.small),
                    )
                }
            }
        }
    }
}
