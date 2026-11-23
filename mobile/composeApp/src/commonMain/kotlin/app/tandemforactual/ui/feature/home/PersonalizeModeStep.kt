@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.InterfaceMode
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemFadeEnter
import app.tandemforactual.ui.design.theme.tandemFadeExit
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.mode_advanced
import app.tandemforactual.ui.generated.resources.mode_advanced_detail
import app.tandemforactual.ui.generated.resources.mode_advanced_short
import app.tandemforactual.ui.generated.resources.mode_detail
import app.tandemforactual.ui.generated.resources.mode_minimal
import app.tandemforactual.ui.generated.resources.mode_minimal_detail
import app.tandemforactual.ui.generated.resources.mode_minimal_short
import app.tandemforactual.ui.generated.resources.mode_title
import app.tandemforactual.ui.generated.resources.personalize_continue
import org.jetbrains.compose.resources.stringResource

/** Choose between the Minimal and Advanced layouts. */
@Composable
internal fun ModeStep(
    state: AppState,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val palette = tandemPalette
    StepScaffold(
        PersonalizeStep.MODE,
        onBack,
        stringResource(Res.string.mode_title),
        stringResource(Res.string.mode_detail),
        stringResource(Res.string.personalize_continue),
        onPrimary = onNext,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
            InterfaceMode.entries.forEach { mode ->
                ModePreviewCard(mode, state.interfaceMode == mode, Modifier.weight(1f)) { state.interfaceMode = mode }
            }
        }
        val reduceMotion = tandemReduceMotion
        AnimatedContent(state.interfaceMode, transitionSpec = {
            tandemFadeEnter(reduceMotion, TandemMotion.CONTROL_MILLIS) togetherWith
                tandemFadeExit(reduceMotion, TandemMotion.TAB_EXIT_MILLIS)
        }, label = "mode-detail") { mode ->
            Text(
                stringResource(if (mode == InterfaceMode.MINIMAL) Res.string.mode_minimal_detail else Res.string.mode_advanced_detail),
                Modifier.padding(top = TandemSpacing.extraSmall),
                color = palette.muted,
                fontSize = TandemTypography.compactBody,
            )
        }
    }
}

/** A small drawing of each layout: a few large blocks for Minimal, a denser grid with a sidebar for Advanced. */
@Composable
private fun ModePreviewCard(
    mode: InterfaceMode,
    selected: Boolean,
    modifier: Modifier,
    onSelect: () -> Unit,
) {
    val palette = tandemPalette
    val shape = RoundedCornerShape(TandemShapes.large)
    val motion = tandemMotionSpec<androidx.compose.ui.graphics.Color>(tandemReduceMotion, TandemMotion.COLOR_MILLIS)
    val border by animateColorAsState(if (selected) palette.accent else palette.border, motion, label = "mode-border")
    val block by animateColorAsState(if (selected) palette.accent.copy(alpha = 0.32f) else palette.raised, motion, label = "mode-block")
    Column(
        modifier
            .clip(shape)
            .background(if (selected) palette.accentSurface else palette.surface)
            .border(if (selected) 1.5.dp else 1.dp, border, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(TandemSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
    ) {
        ModeLayoutSketch(mode, block)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (mode == InterfaceMode.MINIMAL) Res.string.mode_minimal else Res.string.mode_advanced),
                    color = palette.text,
                    fontSize = TandemTypography.action,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(if (mode == InterfaceMode.MINIMAL) Res.string.mode_minimal_short else Res.string.mode_advanced_short),
                    color = palette.muted,
                    fontSize = TandemTypography.caption,
                )
            }
            SelectionMark(selected, multiSelect = false)
        }
    }
}

/** The block drawing inside a layout preview card. */
@Composable
private fun ModeLayoutSketch(
    mode: InterfaceMode,
    block: Color,
) {
    val palette = tandemPalette
    Box(
        Modifier
            .fillMaxWidth()
            .height(
                112.dp,
            ).clip(RoundedCornerShape(TandemShapes.control))
            .background(palette.background)
            .padding(10.dp),
    ) {
        if (mode == InterfaceMode.MINIMAL) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth(0.55f)
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(block),
                )
                repeat(3) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(22.dp)
                            .clip(RoundedCornerShape(TandemShapes.small))
                            .background(block),
                    )
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) { repeat(5) { Box(Modifier.size(10.dp).clip(CircleShape).background(block)) } }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(4) {
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(18.dp)
                                    .clip(RoundedCornerShape(TandemShapes.small))
                                    .background(block),
                            )
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(18.dp)
                                    .clip(RoundedCornerShape(TandemShapes.small))
                                    .background(block),
                            )
                        }
                    }
                }
            }
        }
    }
}
