@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.home
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.session.DragReorderState
import app.tandemforactual.ui.core.session.dragReorderItemModifier
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.ExperienceSettings
import app.tandemforactual.ui.core.state.HomeLayoutItem
import app.tandemforactual.ui.core.state.HomeModule
import app.tandemforactual.ui.core.state.InterfaceMode
import app.tandemforactual.ui.core.state.homeLayout
import app.tandemforactual.ui.core.state.reorderItemToIndex
import app.tandemforactual.ui.core.state.withHomeLayout
import app.tandemforactual.ui.core.state.withSuggestedHome
import app.tandemforactual.ui.design.buttons.MIN_TOUCH_TARGET
import app.tandemforactual.ui.design.buttons.ReorderDragHandle
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.PageLazyColumn
import app.tandemforactual.ui.design.templates.TandemSwitchTrack
import app.tandemforactual.ui.design.theme.TandemLayout
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.home_layout_advanced_only
import app.tandemforactual.ui.generated.resources.home_layout_detail
import app.tandemforactual.ui.generated.resources.home_layout_goals
import app.tandemforactual.ui.generated.resources.home_layout_goals_detail
import app.tandemforactual.ui.generated.resources.home_layout_minimal_note
import app.tandemforactual.ui.generated.resources.home_layout_more_title
import app.tandemforactual.ui.generated.resources.home_layout_reset
import app.tandemforactual.ui.generated.resources.home_layout_reset_detail
import app.tandemforactual.ui.generated.resources.home_layout_sections
import app.tandemforactual.ui.generated.resources.home_layout_toggle
import app.tandemforactual.ui.generated.resources.home_layout_when_ready
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.Sparkles
import org.jetbrains.compose.resources.stringResource

/** Personalize Home: the order and visibility of Home's sections for the open budget, saved as each change is made. */
@Composable
internal fun HomeLayoutScreen(
    state: AppState,
    navigation: AppNavigation,
) {
    val settings = state.experience ?: ExperienceSettings()
    val layout = remember(settings) { homeLayout(settings) }
    val drag = remember(state.selectedBudgetId) { DragReorderState() }
    val savedOrder = layout.map { it.module.name }
    val order = drag.displayOrder ?: drag.currentOrder(savedOrder)
    val byName = layout.associateBy { it.module.name }
    val shownLayout = order.mapNotNull(byName::get) + layout.filter { it.module.name !in order }
    val reduceMotion = tandemReduceMotion

    fun save(items: List<HomeLayoutItem>) = state.updateExperience { it.withHomeLayout(items) }

    fun move(
        module: HomeModule,
        direction: Int,
    ) {
        val names = shownLayout.map { it.module.name }
        val next = reorderItemToIndex(names, module.name, names.indexOf(module.name) + direction)
        if (next != names) save(next.mapNotNull(byName::get))
    }

    fun drop() {
        drag.finish(savedOrder)?.let { result ->
            if (result.finalOrder != result.startingOrder) save(result.finalOrder.mapNotNull(byName::get))
        }
    }

    PageLazyColumn(verticalSpacing = TandemSpacing.small) {
        item(key = "home-layout-intro") { HomeLayoutIntro(state.interfaceMode) }
        item(key = "home-layout-heading") {
            Text(
                stringResource(Res.string.home_layout_sections),
                color = tandemPalette.muted,
                fontSize = TandemTypography.label,
                fontWeight = FontWeight.SemiBold,
            )
        }
        homeLayoutRowItems(shownLayout, drag, reduceMotion, state.interfaceMode, ::save, ::move, ::drop)
        item(key = "home-layout-more") {
            HomeLayoutMoreSection(
                showReset = settings.moduleOrder.isNotEmpty() || settings.hiddenModules.isNotEmpty(),
                onPersonalize = { navigation.open(AppRoute.PERSONALIZE) },
                onReset = {
                    drag.releaseHeldDrop()
                    state.updateExperience { it.withSuggestedHome() }
                },
            )
        }
    }
}

/** One draggable, toggleable row per Home section. */
private fun LazyListScope.homeLayoutRowItems(
    shownLayout: List<HomeLayoutItem>,
    drag: DragReorderState,
    reduceMotion: Boolean,
    interfaceMode: InterfaceMode,
    onSave: (List<HomeLayoutItem>) -> Unit,
    onMove: (HomeModule, Int) -> Unit,
    onDrop: () -> Unit,
) {
    shownLayout.forEachIndexed { index, item ->
        item(key = "home-layout-${item.module.name}") {
            val name = item.module.name

            fun startDrag() = drag.start(shownLayout.map { it.module.name }, name)

            fun dragBy(delta: Float) {
                if (drag.draggedItemId == name) drag.dragBy(delta)
            }
            HomeLayoutRow(
                item = item,
                advancedOnlyHint = item.module.advancedOnly && interfaceMode == InterfaceMode.MINIMAL,
                canMoveUp = index > 0,
                canMoveDown = index < shownLayout.lastIndex,
                modifier =
                    dragReorderItemModifier(
                        itemId = name,
                        dragState = drag,
                        reduceMotion = reduceMotion,
                        dragEnabled = true,
                        onDragStarted = ::startDrag,
                        onDragBy = ::dragBy,
                        onDragStopped = onDrop,
                    ),
                onToggle = { shown -> onSave(shownLayout.map { if (it.module == item.module) it.copy(shown = shown) else it }) },
                onMoveUp = { onMove(item.module, -1) },
                onMoveDown = { onMove(item.module, 1) },
                onDragStarted = ::startDrag,
                onDragBy = ::dragBy,
                onDragStopped = onDrop,
            )
        }
    }
}

/** What this screen changes, and how Minimal mode affects it. */
@Composable
private fun HomeLayoutIntro(interfaceMode: InterfaceMode) {
    Column(Modifier.padding(bottom = TandemSpacing.small), verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
        Text(
            stringResource(Res.string.home_layout_detail),
            color = tandemPalette.text,
            fontSize = TandemTypography.body,
            lineHeight = TandemTypography.bodyLineHeight,
        )
        val readyNote = stringResource(Res.string.home_layout_when_ready)
        Text(
            if (interfaceMode ==
                InterfaceMode.MINIMAL
            ) {
                "${stringResource(Res.string.home_layout_minimal_note)} $readyNote"
            } else {
                readyNote
            },
            color = tandemPalette.muted,
            fontSize = TandemTypography.compactBody,
            lineHeight = TandemTypography.compactLineHeight,
        )
    }
}

/** Links to goal-based personalization and to resetting Home's layout. */
@Composable
private fun HomeLayoutMoreSection(
    showReset: Boolean,
    onPersonalize: () -> Unit,
    onReset: () -> Unit,
) {
    Column(Modifier.padding(top = TandemSpacing.medium)) {
        SectionCard(stringResource(Res.string.home_layout_more_title)) {
            ListRow(
                stringResource(Res.string.home_layout_goals),
                stringResource(Res.string.home_layout_goals_detail),
                leading = RowLeading.Icon(Lucide.Sparkles),
                onClick = onPersonalize,
            )
            if (showReset) {
                DividerLine()
                ListRow(
                    stringResource(Res.string.home_layout_reset),
                    stringResource(Res.string.home_layout_reset_detail),
                    leading = RowLeading.Icon(Lucide.RotateCcw),
                    options = ListRowOptions(showChevron = false),
                    onClick = onReset,
                )
            }
        }
    }
}

@Composable
private fun HomeLayoutRow(
    item: HomeLayoutItem,
    advancedOnlyHint: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    modifier: Modifier,
    onToggle: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDragStarted: () -> Unit,
    onDragBy: (Float) -> Unit,
    onDragStopped: () -> Unit,
) {
    val palette = tandemPalette
    val title = stringResource(item.module.title())
    val toggleLabel = stringResource(Res.string.home_layout_toggle, title)
    val contentAlpha by animateFloatAsState(
        if (item.shown) 1f else 0.55f,
        tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
        label = "home-layout-alpha",
    )
    SurfaceCard(modifier.fillMaxWidth(), animateSizeChanges = false) {
        Row(
            Modifier.fillMaxWidth().padding(
                start = TandemSpacing.medium,
                end = TandemSpacing.extraSmall,
                top = TandemSpacing.small,
                bottom = TandemSpacing.small,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
        ) {
            HomeLayoutRowLabel(item, title, advancedOnlyHint, contentAlpha)
            Box(
                Modifier
                    .sizeIn(minWidth = MIN_TOUCH_TARGET, minHeight = MIN_TOUCH_TARGET)
                    .clip(RoundedCornerShape(TandemShapes.control))
                    .semantics(mergeDescendants = true) { contentDescription = toggleLabel }
                    .toggleable(value = item.shown, role = Role.Switch, onValueChange = onToggle),
                contentAlignment = Alignment.Center,
            ) { TandemSwitchTrack(item.shown) }
            ReorderDragHandle(
                itemName = title,
                enabled = canMoveUp || canMoveDown,
                canMoveUp = canMoveUp,
                canMoveDown = canMoveDown,
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
                onDragStarted = onDragStarted,
                onDragBy = onDragBy,
                onDragStopped = onDragStopped,
            )
        }
    }
}

/** Icon, title and detail of a Home layout row, faded when the section is hidden. */
@Composable
private fun RowScope.HomeLayoutRowLabel(
    item: HomeLayoutItem,
    title: String,
    advancedOnlyHint: Boolean,
    contentAlpha: Float,
) {
    val palette = tandemPalette
    Row(
        Modifier.weight(1f).graphicsLayer { alpha = contentAlpha },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
    ) {
        Box(
            Modifier
                .size(TandemLayout.compactListRowLeadingSize)
                .clip(RoundedCornerShape(TandemShapes.control))
                .background(palette.raised),
            contentAlignment = Alignment.Center,
        ) {
            Icon(item.module.icon(), contentDescription = null, modifier = Modifier.size(18.dp), tint = palette.accent)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall)) {
            Text(title, color = palette.text, fontSize = TandemTypography.body, fontWeight = FontWeight.SemiBold)
            Text(
                if (advancedOnlyHint) {
                    stringResource(
                        Res.string.home_layout_advanced_only,
                    )
                } else {
                    stringResource(item.module.detail())
                },
                color = palette.muted,
                fontSize = TandemTypography.caption,
                lineHeight = TandemTypography.tightLineHeight,
            )
        }
    }
}
