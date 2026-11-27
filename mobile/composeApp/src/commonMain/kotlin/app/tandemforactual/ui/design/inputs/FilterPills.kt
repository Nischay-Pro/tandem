@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.inputs

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.buttons.MIN_TOUCH_TARGET
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion

/** A scrolling row of single-choice filters, like Coinbase's All · Tradable · Gainers pills. */
@Composable
internal fun <T> FilterPills(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val scroll = rememberScrollState()
    val offsets = remember { mutableStateMapOf<Int, Int>() }
    val index = options.indexOfFirst { it.first == selected }
    val density = LocalDensity.current
    LaunchedEffect(index, offsets[index]) {
        val x = offsets[index] ?: return@LaunchedEffect
        scroll.animateScrollTo((x - with(density) { TandemSpacing.large.roundToPx() }).coerceAtLeast(0))
    }
    Row(
        modifier.wheelScrollsHorizontally(scroll, tandemReduceMotion).horizontalScroll(scroll).padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { i, (value, label) ->
            Box(Modifier.onGloballyPositioned { offsets[i] = it.positionInParent().x.toInt() }) {
                FilterPill(label, value == selected) { onSelect(value) }
            }
        }
    }
}

/** The same choices as [FilterPills], laid out in rows of [columns] so every option shows at once. */
@Composable
internal fun <T> PillGrid(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    columns: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        options.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                row.forEach { (value, label) ->
                    FilterPill(label, value == selected, Modifier.weight(1f), compact = true) { onSelect(value) }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    val background by animateColorAsState(
        if (selected) palette.accentSurface else palette.raised,
        tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
        label = "filter-pill",
    )
    val content by animateColorAsState(
        if (selected) palette.accent else palette.muted,
        tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
        label = "filter-pill-text",
    )
    val interactions = remember { MutableInteractionSource() }
    Box(
        modifier
            .heightIn(min = MIN_TOUCH_TARGET)
            .selectable(
                selected = selected,
                interactionSource = interactions,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            (if (compact) Modifier.fillMaxWidth() else Modifier)
                .clip(RoundedCornerShape(TandemShapes.control))
                .background(background)
                .pointerHighlight(palette.text, RoundedCornerShape(TandemShapes.control), tandemReduceMotion)
                .indication(interactions, LocalIndication.current)
                .heightIn(min = 40.dp)
                .padding(horizontal = if (compact) TandemSpacing.extraSmall else TandemSpacing.large, vertical = 10.dp)
                .wrapContentHeight(),
            color = content,
            textAlign = TextAlign.Center,
            fontSize = TandemTypography.compactBody,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}
