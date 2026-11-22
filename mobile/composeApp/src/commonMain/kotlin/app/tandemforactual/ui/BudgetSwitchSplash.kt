@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ChartCandlestick
import com.composables.icons.lucide.Feather
import com.composables.icons.lucide.Lucide

/** Covers the app while a budget opens, so its data can settle before it is shown. */
@Composable
internal fun BudgetSwitchSplash(
    budgetName: String?,
    mode: InterfaceMode? = null,
) {
    var shownName by remember { mutableStateOf(budgetName) }
    var shownMode by remember { mutableStateOf(mode) }
    if (budgetName != null || mode != null) {
        shownName = budgetName
        shownMode = mode
    }
    AnimatedVisibility(
        visible = budgetName != null || mode != null,
        enter = fadeIn(tween(TandemMotion.CONTROL_MILLIS)),
        exit = fadeOut(tween(TandemMotion.ROUTE_MILLIS)),
    ) {
        val palette = tandemPalette
        val breath = rememberInfiniteTransition(label = "budget-switch-breath")
        val breathPhase by breath.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1_200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "budget-switch-phase",
        )
        val phase = if (tandemReduceMotion) 1f else breathPhase
        Box(
            Modifier
                .fillMaxSize()
                .background(palette.background)
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                TandemBrandMark(
                    Modifier.size(88.dp).graphicsLayer {
                        val scale = 0.92f + 0.08f * phase
                        scaleX = scale
                        scaleY = scale
                        alpha = 0.7f + 0.3f * phase
                    },
                    contentDescription = if (shownMode != null) "Tandem" else "Opening budget",
                )
                val target = shownMode
                if (target != null) {
                    val alloFamily = tandemAlloFamily()
                    Text(
                        buildAnnotatedString {
                            append("TANDEM ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(if (target == InterfaceMode.ADVANCED) "ADVANCED" else "MINIMAL")
                            }
                        },
                        color = palette.text,
                        fontFamily = alloFamily,
                        fontSize = TandemTypography.cardTitle,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 3.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                    return@Column
                }
                Text(
                    shownName.orEmpty(),
                    Modifier.padding(horizontal = 32.dp),
                    color = palette.muted,
                    fontSize = TandemTypography.body,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Coinbase-style pill that turns Advanced mode on or off; the change happens behind the splash. */
@Composable
internal fun AdvancedModeSwitch(
    state: AppState,
    modifier: Modifier = Modifier,
    labeled: Boolean = true,
) {
    val palette = tandemPalette
    val advanced = (state.modeSwitchTarget ?: state.interfaceMode) == InterfaceMode.ADVANCED
    val thumbOffset by animateDpAsState(
        if (advanced) (if (labeled) 16.dp else 22.dp) else 0.dp,
        tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTROL_MILLIS),
        label = "advanced-thumb",
    )
    val trackColor by animateColorAsState(
        if (advanced) palette.accent else palette.raised,
        tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
        label = "advanced-track",
    )
    val thumbColor by animateColorAsState(
        if (advanced) palette.accentContent else palette.muted,
        tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
        label = "advanced-thumb-color",
    )
    if (!labeled) {
        Box(
            modifier
                .width(52.dp)
                .height(30.dp)
                .clip(CircleShape)
                .background(trackColor)
                .padding(3.dp),
        ) {
            Box(
                Modifier
                    .offset(x = thumbOffset)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(thumbColor),
            )
        }
        return
    }
    val pillThumb by animateDpAsState(
        if (advanced) 36.dp else 0.dp,
        tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTROL_MILLIS),
        label = "mode-thumb",
    )
    Box(
        modifier
            .width(76.dp)
            .height(40.dp)
            .clip(CircleShape)
            .background(palette.surface)
            .border(1.dp, palette.border, CircleShape)
            .toggleable(value = advanced, role = Role.Switch) {
                state.switchInterfaceMode(if (it) InterfaceMode.ADVANCED else InterfaceMode.MINIMAL)
            }.semantics { contentDescription = "Advanced mode" }
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .offset(x = pillThumb)
                .size(36.dp)
                .clip(CircleShape)
                .background(palette.text),
        )
        Row(Modifier.fillMaxSize()) {
            listOf(Lucide.Feather to !advanced, Lucide.ChartCandlestick to advanced).forEach { (icon, on) ->
                val tint by animateColorAsState(
                    if (on) palette.background else palette.muted,
                    tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
                    label = "mode-icon",
                )
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = tint)
                }
            }
        }
    }
}
