@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.review

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.core.ui.AccountIconTile
import app.tandemforactual.ui.design.icons.rememberMerchantArtwork
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Tags
import com.composables.icons.lucide.X
import kotlin.math.abs
import kotlinx.coroutines.launch

/** How far a card must travel, as a share of its width, before letting go decides it. */
private const val SWIPE_THRESHOLD = 0.32f

/**
 * The transaction under review as a card: drag it right to categorize, left to skip. It tilts as it moves and names what
 * letting go will do; [onSkip] and [onAccept] also serve the buttons and screen readers.
 */
@Composable
internal fun ReviewCard(
    item: TransactionItem,
    amount: String,
    acceptLabel: String,
    footer: @Composable () -> Unit,
    onSkip: () -> Unit,
    onAccept: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val offset = remember(item.recordId) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val reduceMotion = tandemReduceMotion
    BoxWithConstraints(modifier) {
        val width = with(LocalDensity.current) { maxWidth.toPx() }
        val progress = (offset.value / (width * SWIPE_THRESHOLD)).coerceIn(-1f, 1f)
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = offset.value
                    rotationZ = if (reduceMotion) 0f else progress * 8f
                }.pointerInput(item.recordId) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val decided = abs(offset.value) > width * SWIPE_THRESHOLD
                            scope.launch {
                                if (decided) {
                                    val toRight = offset.value > 0
                                    offset.animateTo(if (toRight) width * 1.4f else -width * 1.4f, tween(180))
                                    if (toRight) onAccept() else onSkip()
                                    offset.snapTo(0f)
                                } else {
                                    offset.animateTo(0f, spring())
                                }
                            }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f, spring()) } },
                    ) { change, delta ->
                        change.consume()
                        scope.launch { offset.snapTo(offset.value + delta) }
                    }
                }.semantics {
                    customActions =
                        listOf(
                            CustomAccessibilityAction("Skip for now") { onSkip().let { true } },
                            CustomAccessibilityAction(acceptLabel) { onAccept().let { true } },
                        )
                },
        ) {
            ReviewCardFace(item, amount, footer)
            SwipeHint(progress, acceptLabel)
        }
    }
}

@Composable
private fun ReviewCardFace(
    item: TransactionItem,
    amount: String,
    footer: @Composable () -> Unit,
) {
    val palette = tandemPalette
    val shape = RoundedCornerShape(TandemShapes.capsule)
    Column(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(palette.raised)
            .border(1.dp, palette.border, shape)
            .padding(TandemSpacing.large),
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
            AccountIconTile(item.account)
            Text(item.account, color = palette.muted, fontSize = TandemTypography.compactBody, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        Detail(Lucide.CalendarDays, item.dateLabel)
        Text(
            amount,
            color = if (item.amountMinorUnits > 0) palette.positive else palette.text,
            fontSize = TandemTypography.heroTitle,
            fontWeight = FontWeight.Bold,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
            rememberMerchantArtwork(item.merchantIcon)?.let { logo ->
                Image(logo, contentDescription = null, modifier = Modifier.size(28.dp).clip(RoundedCornerShape(TandemShapes.small)))
            }
            Text(item.payee, color = palette.text, fontSize = TandemTypography.cardTitle, fontWeight = FontWeight.SemiBold, maxLines = 2)
        }
        if (item.notes.isNotBlank()) Text(item.notes, color = palette.muted, fontSize = TandemTypography.compactBody, maxLines = 2)
        Spacer(Modifier.weight(1f))
        footer()
    }
}

@Composable
private fun Detail(
    icon: ImageVector,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
        Icon(icon, null, Modifier.size(16.dp), tint = tandemPalette.muted)
        Text(text, color = tandemPalette.muted, fontSize = TandemTypography.compactBody, maxLines = 1)
    }
}

/** Names what letting go does, fading in with the drag: skip on the left, the accept action on the right. */
@Composable
private fun SwipeHint(
    progress: Float,
    acceptLabel: String,
) {
    if (progress == 0f) return
    val palette = tandemPalette
    val accept = progress > 0
    val tint: Color = if (accept) palette.accent else palette.muted
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(TandemShapes.capsule))
            .background(tint.copy(alpha = 0.55f * abs(progress)))
            .padding(TandemSpacing.large),
        contentAlignment = if (accept) Alignment.TopStart else Alignment.TopEnd,
    ) {
        Column(
            Modifier.graphicsLayer { alpha = abs(progress) },
            horizontalAlignment = if (accept) Alignment.Start else Alignment.End,
            verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
        ) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(TandemShapes.pill)).background(palette.background.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (accept) Lucide.Check else Lucide.X, null, Modifier.size(24.dp), tint = tint)
            }
            Text(
                if (accept) acceptLabel else "Skip for now",
                color = palette.text,
                fontSize = TandemTypography.sectionTitle,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** The card's footer: the suggested category to accept, or a note that one is needed, plus a way to pick another. */
@Composable
internal fun ReviewCardFooter(
    suggestion: String?,
    onPick: () -> Unit,
) {
    val palette = tandemPalette
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
        ReviewChip(suggestion ?: "Category needed", if (suggestion == null) palette.warning else palette.accent, Lucide.Tags, onPick)
        if (suggestion != null) ReviewChip("Other", palette.muted, null, onPick)
    }
}

@Composable
private fun ReviewChip(
    label: String,
    color: Color,
    icon: ImageVector?,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(TandemShapes.pill)
    Row(
        Modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.5f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = TandemSpacing.medium, vertical = TandemSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(16.dp), tint = color)
        Text(label, color = tandemPalette.text, fontSize = TandemTypography.label, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
