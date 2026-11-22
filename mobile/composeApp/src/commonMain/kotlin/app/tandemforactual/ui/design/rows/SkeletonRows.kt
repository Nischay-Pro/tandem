@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.rows

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.theme.TandemLayout
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion

/** Placeholder rows shaped like list rows, shown while the first page of a list loads. */
@Composable
internal fun SkeletonRows(
    count: Int = 6,
    modifier: Modifier = Modifier,
) {
    val palette = tandemPalette
    val pulse =
        if (tandemReduceMotion) {
            0.6f
        } else {
            rememberInfiniteTransition(label = "skeleton")
                .animateFloat(0.45f, 0.85f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "skeleton-alpha")
                .value
        }
    val shape = RoundedCornerShape(TandemShapes.small)
    Column(modifier.fillMaxWidth().alpha(pulse).semantics { contentDescription = "Loading" }) {
        repeat(count) { index ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = TandemLayout.listRowMinHeight)
                    .padding(horizontal = TandemLayout.listRowHorizontalInset, vertical = TandemLayout.listRowVerticalInset),
                horizontalArrangement = Arrangement.spacedBy(TandemLayout.listRowGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.size(TandemLayout.listRowLeadingSize).clip(CircleShape).background(palette.border)) {}
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    Row(
                        Modifier
                            .fillMaxWidth(if (index % 2 == 0) 0.55f else 0.4f)
                            .height(12.dp)
                            .clip(shape)
                            .background(palette.border),
                    ) {}
                    Row(
                        Modifier
                            .fillMaxWidth(if (index % 2 == 0) 0.35f else 0.5f)
                            .height(10.dp)
                            .clip(shape)
                            .background(palette.border),
                    ) {}
                }
                Row(Modifier.size(width = 56.dp, height = 12.dp).clip(shape).background(palette.border)) {}
            }
        }
    }
}
