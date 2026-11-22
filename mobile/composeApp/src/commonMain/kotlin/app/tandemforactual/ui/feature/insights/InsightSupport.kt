@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.insights
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.planning.TrendDirection
import app.tandemforactual.ui.core.format.wholePercent
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.ArrowDownRight
import com.composables.icons.lucide.ArrowUpRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minus

/** Spending going up reads as a warning and going down as good news. */
@Composable
internal fun TrendDirection.color(): Color =
    when (this) {
        TrendDirection.UP -> tandemPalette.negative
        TrendDirection.DOWN -> tandemPalette.positive
        TrendDirection.FLAT -> tandemPalette.muted
    }

/** A small arrow and percentage showing which way spending moved. */
@Composable
internal fun TrendBadge(
    direction: TrendDirection,
    changeBasisPoints: Int?,
    modifier: Modifier = Modifier,
) {
    val tint = direction.color()
    val label =
        when {
            direction == TrendDirection.FLAT -> "About the same"
            changeBasisPoints == null -> "New"
            else -> wholePercent(changeBasisPoints)
        }
    Row(
        modifier
            .clip(RoundedCornerShape(TandemShapes.pill))
            .background(tint.copy(alpha = 0.14f))
            .padding(horizontal = TandemSpacing.small, vertical = TandemSpacing.xxSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            when (direction) {
                TrendDirection.UP -> Lucide.ArrowUpRight
                TrendDirection.DOWN -> Lucide.ArrowDownRight
                TrendDirection.FLAT -> Lucide.Minus
            },
            contentDescription =
                when (direction) {
                    TrendDirection.UP -> "Up"
                    TrendDirection.DOWN -> "Down"
                    TrendDirection.FLAT -> null
                },
            modifier = Modifier.size(14.dp),
            tint = tint,
        )
        Text(
            label,
            Modifier.padding(start = TandemSpacing.xxSmall),
            color = tint,
            fontSize = TandemTypography.caption,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}
