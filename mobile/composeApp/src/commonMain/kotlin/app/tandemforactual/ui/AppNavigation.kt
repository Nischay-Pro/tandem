@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
import app.tandemforactual.ui.components.TandemIconButton
import app.tandemforactual.ui.components.TandemModalCloseButton
import app.tandemforactual.ui.model.LiveAccountSummary
import app.tandemforactual.ui.model.LiveBudgetMonthSummary
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.Cloud
import com.composables.icons.lucide.CloudAlert
import com.composables.icons.lucide.CloudOff
import com.composables.icons.lucide.CloudSync
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus

@Composable
internal fun FloatingAction(
    compact: Boolean,
    enabled: Boolean,
    label: String,
    contentDescription: String,
    icon: ImageVector,
    loading: Boolean = false,
    outlined: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = tandemPalette
    val isCompact = compact || LocalDensity.current.fontScale >= 1.5f
    val shape = if (isCompact) CircleShape else RoundedCornerShape(TandemShapes.large)
    val actionColor =
        when {
            !enabled -> palette.muted
            outlined -> palette.accent
            else -> palette.accentContent
        }
    val backgroundColor =
        when {
            !enabled -> palette.raised
            outlined -> palette.surface
            else -> palette.accent
        }
    Row(
        modifier =
            modifier
                .animateContentSize(tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTENT_SIZE_MILLIS))
                .height(56.dp)
                .defaultMinSize(minWidth = 56.dp)
                .clip(shape)
                .background(backgroundColor)
                .then(if (outlined) Modifier.border(1.dp, palette.border, shape) else Modifier)
                .semantics(mergeDescendants = true) { this.contentDescription = contentDescription }
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
                .padding(horizontal = if (isCompact) TandemSpacing.large else TandemSpacing.extraLarge),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isCompact) 0.dp else TandemSpacing.small),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), color = actionColor, strokeWidth = 2.dp)
        } else {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = actionColor)
        }
        AnimatedVisibility(visible = !isCompact, enter = tandemFadeEnter(tandemReduceMotion), exit = tandemFadeExit(tandemReduceMotion)) {
            Text(
                label,
                color = actionColor,
                fontSize = TandemTypography.body,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun FloatingTransactionAction(
    compact: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingAction(
        compact = compact,
        enabled = enabled,
        label = "Add transaction",
        contentDescription = if (enabled) "Add transaction" else "Read-only mode is on",
        icon = Lucide.Plus,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
internal fun FloatingSyncAction(
    compact: Boolean,
    syncing: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingAction(
        compact = compact,
        enabled = enabled,
        label = "Sync",
        contentDescription = "Sync",
        icon = Lucide.CloudSync,
        loading = syncing,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
internal fun MainCloudStatusButton(
    syncStatus: MainCloudStatus,
    pendingWriteStatuses: Collection<String>,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    val cloudTint =
        when (syncStatus) {
            MainCloudStatus.OFF -> palette.muted
            MainCloudStatus.SYNCED -> palette.positive
            MainCloudStatus.SYNCING -> palette.syncing
            MainCloudStatus.ERROR -> palette.negative
        }
    TandemIconButton(
        icon =
            when (syncStatus) {
                MainCloudStatus.OFF -> Lucide.CloudOff
                MainCloudStatus.ERROR -> Lucide.CloudAlert
                else -> Lucide.Cloud
            },
        contentDescription = mainCloudStatusAccessibilityDescription(syncStatus, pendingWriteStatuses),
        onClickLabel = "Open sync center",
        onClick = onClick,
        iconSizeDp = 25,
        tint = cloudTint,
    )
}

@Composable
internal fun AppHeader(
    route: AppRoute,
    state: AppState,
    showBack: Boolean,
    onBack: () -> Unit,
    onSync: () -> Unit,
    syncStatus: MainCloudStatus,
    pendingWriteStatuses: Collection<String>,
    onBudget: () -> Unit,
    actualConnected: Boolean,
    liveAccounts: List<LiveAccountSummary>,
    liveBudgetMonth: LiveBudgetMonthSummary?,
    activitySelectionTotalMinor: Long?,
    conflictTitle: String?,
    compactLandscape: Boolean = false,
    onAddTransaction: (() -> Unit)? = null,
    addTransactionEnabled: Boolean = true,
) {
    val palette = tandemPalette
    val headerFontScale = LocalDensity.current.fontScale
    val budgetHeaderSlotHeight = maxOf(88.dp, 88.dp * headerFontScale)
    val headerMinHeight =
        if (route == AppRoute.BUDGET || route == AppRoute.BUDGET_CHOOSER) {
            if (compactLandscape && headerFontScale < 1.5f) 64.dp else budgetHeaderSlotHeight
        } else {
            64.dp
        }
    val headerCategoryGroups = liveBudgetMonth?.groups.orEmpty()
    val headerGroup =
        headerCategoryGroups.firstOrNull { it.id == state.selectedCategoryGroupId }
            ?: headerCategoryGroups.firstOrNull()
    val headerGroupName = headerGroup?.name
    val headerCategoryName =
        headerCategoryGroups
            .asSequence()
            .flatMap { it.categories.asSequence() }
            .firstOrNull { it.id == state.selectedCategoryId }
            ?.name
    val headerTitle =
        when (route) {
            AppRoute.NEW_TRANSACTION -> "Add transaction"
            AppRoute.BUDGET -> state.selectedBudget.ifBlank { "Choose a budget" }
            AppRoute.CATEGORY_GROUP ->
                headerGroupName ?: "Category group"
            AppRoute.CATEGORY_DETAIL ->
                headerCategoryName ?: "Category"
            AppRoute.ACCOUNT_DETAIL ->
                liveAccounts.firstOrNull { it.id == state.selectedAccountId }?.name ?: "Account"
            AppRoute.TRANSACTION_DETAIL -> state.transactionPayee.ifBlank { route.title }
            AppRoute.ACTIVITY,
            AppRoute.ACTIVITY_SEARCH,
            -> activitySelectionTotalMinor?.let(state::formatCurrency) ?: route.title
            AppRoute.CONFLICT_REVIEW -> conflictTitle?.takeIf(String::isNotBlank) ?: route.title
            AppRoute.REPORT_DETAIL ->
                app.tandemforactual.ui.reports.ReportKind.entries
                    .firstOrNull { it.name == state.selectedReportKindName }
                    ?.title ?: route.title
            else -> route.title
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = headerMinHeight)
                .padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack && !route.isTransactionForm()) {
            TandemIconButton(
                icon = Lucide.ChevronLeft,
                contentDescription = "Back",
                onClick = onBack,
                iconSizeDp = 24,
            )
            Spacer(Modifier.width(6.dp))
        }
        Column(
            modifier =
                Modifier.weight(1f).clickable(
                    enabled = route.isTab(),
                    role = Role.Button,
                    onClickLabel = "Choose budget",
                    onClick = onBudget,
                ),
        ) {
            if (headerTitle.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = headerTitle,
                        modifier = Modifier.weight(1f),
                        color = palette.text,
                        fontSize = TandemTypography.periodTitle,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = if (route == AppRoute.BUDGET) budgetHeaderTitleMaxLines() else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (route == AppRoute.BUDGET) {
                        Icon(
                            Lucide.ChevronDown,
                            contentDescription = null,
                            modifier = Modifier.padding(start = TandemSpacing.xxSmall).size(16.dp),
                            tint = palette.muted,
                        )
                    }
                }
                if (route == AppRoute.BUDGET) {
                    Text(
                        state.selectedMonth,
                        color = palette.muted,
                        fontSize = TandemTypography.caption,
                    )
                }
            }
        }
        if (route == AppRoute.BUDGET && onAddTransaction != null) {
            TandemIconButton(
                icon = Lucide.Plus,
                contentDescription = if (addTransactionEnabled) "Add transaction" else "Read-only mode is on",
                onClickLabel = "Add transaction",
                enabled = addTransactionEnabled,
                iconSizeDp = 22,
                onClick = onAddTransaction,
            )
        }
        if (route.isTab()) {
            MainCloudStatusButton(syncStatus, pendingWriteStatuses, onSync)
        } else if (route == AppRoute.BUDGET_CHOOSER) {
            Text(
                when {
                    actualConnected -> "Actual budgets"
                    state.selectedBudgetId.isNotBlank() -> "Offline copy"
                    else -> "Connect to Actual"
                },
                color = palette.muted,
                fontSize = TandemTypography.micro,
                fontWeight = FontWeight.Bold,
            )
        } else if (route.isTransactionForm()) {
            TandemModalCloseButton(onClick = onBack)
        }
    }
}

@Composable
internal fun BottomNavigation(
    selected: AppRoute,
    selectTab: (AppRoute) -> Unit,
    visible: Boolean = true,
) {
    val palette = tandemPalette
    val density = LocalDensity.current
    val labelDensity = Density(density.density, density.fontScale.coerceAtMost(1.3f))
    val items =
        listOf(
            AppRoute.BUDGET to "Budget",
            AppRoute.ACCOUNTS to "Accounts",
            AppRoute.ACTIVITY to "Activity",
            AppRoute.REPORTS to "Reports",
            AppRoute.SETTINGS to "Settings",
        )
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = if (visible) 1f else 0f }
                .background(
                    palette.surface,
                ).padding(
                    start = TandemSpacing.small,
                    end = TandemSpacing.small,
                    top = TandemSpacing.small,
                    bottom = TandemSpacing.small,
                ),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        items.forEach { (route, label) ->
            val active = route == selected
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
                        .defaultMinSize(minHeight = 44.dp)
                        .clip(RoundedCornerShape(TandemShapes.medium))
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
                        .padding(vertical = TandemSpacing.extraSmall),
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
    }
}

internal fun isLandscapeViewport(
    widthPx: Int,
    heightPx: Int,
): Boolean = widthPx > heightPx

@Composable
internal fun SideNavigationRail(
    selected: AppRoute,
    selectTab: (AppRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = tandemPalette
    val items =
        listOf(
            AppRoute.BUDGET to "Budget",
            AppRoute.ACCOUNTS to "Accounts",
            AppRoute.ACTIVITY to "Activity",
            AppRoute.REPORTS to "Reports",
            AppRoute.SETTINGS to "Settings",
        )
    Column(
        modifier =
            modifier
                .width(80.dp)
                .fillMaxHeight()
                .background(palette.surface)
                .padding(vertical = TandemSpacing.extraSmall),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
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
internal fun NavigationGlyph(
    route: AppRoute,
    color: Color,
    scale: Float,
) {
    val surface = tandemPalette.surface
    Canvas(
        Modifier
            .size(24.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }.clearAndSetSemantics {},
    ) {
        val stroke = 1.8.dp.toPx()
        val inset = 2.dp.toPx()
        when (route) {
            AppRoute.BUDGET -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(inset, inset * 2),
                    size = Size(size.width - inset * 2, size.height - inset * 4),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                    style = Stroke(stroke),
                )
                drawLine(
                    color,
                    Offset(inset, size.height * 0.45f),
                    Offset(size.width - inset, size.height * 0.45f),
                    stroke,
                )
                drawCircle(color, stroke, Offset(size.width * 0.72f, size.height * 0.67f))
            }
            AppRoute.ACCOUNTS -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(inset, inset * 2),
                    size = Size(size.width - inset * 2, size.height - inset * 4),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                    style = Stroke(stroke),
                )
                drawLine(
                    color,
                    Offset(inset * 2, size.height * 0.42f),
                    Offset(size.width - inset * 2, size.height * 0.42f),
                    stroke,
                )
                drawLine(
                    color,
                    Offset(inset * 2, size.height * 0.7f),
                    Offset(size.width * 0.52f, size.height * 0.7f),
                    stroke,
                )
            }
            AppRoute.ACTIVITY -> {
                drawLine(color, Offset(inset * 2, size.height - inset), Offset(size.width - inset, size.height - inset), stroke)
                drawLine(
                    color,
                    Offset(size.width * 0.28f, size.height - inset),
                    Offset(size.width * 0.28f, size.height * 0.48f),
                    stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.52f, size.height - inset),
                    Offset(size.width * 0.52f, inset * 2),
                    stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.76f, size.height - inset),
                    Offset(size.width * 0.76f, size.height * 0.32f),
                    stroke,
                    cap = StrokeCap.Round,
                )
            }
            AppRoute.REPORTS -> {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 250f,
                    useCenter = false,
                    topLeft = Offset(inset * 2, inset * 2),
                    size = Size(size.width - inset * 4, size.height - inset * 4),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color = color,
                    startAngle = 170f,
                    sweepAngle = 72f,
                    useCenter = false,
                    topLeft = Offset(inset * 2, inset * 2),
                    size = Size(size.width - inset * 4, size.height - inset * 4),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            AppRoute.SETTINGS -> {
                listOf(0.32f, 0.52f, 0.72f).forEachIndexed { index, fraction ->
                    val y = size.height * fraction
                    drawLine(
                        color,
                        Offset(inset, y),
                        Offset(size.width - inset, y),
                        stroke,
                        cap = StrokeCap.Round,
                    )
                    val x = size.width * listOf(0.36f, 0.68f, 0.45f)[index]
                    drawCircle(surface, stroke * 1.5f, Offset(x, y))
                    drawCircle(color, stroke, Offset(x, y), style = Stroke(stroke))
                }
            }
            else -> Unit
        }
    }
}
