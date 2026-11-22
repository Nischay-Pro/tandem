@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette

@Composable
internal fun StatTile(
    label: String,
    value: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    val palette = tandemPalette
    Column(
        modifier
            .clip(RoundedCornerShape(TandemShapes.large))
            .background(palette.surface)
            .padding(TandemSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall),
    ) {
        Text(label, color = palette.muted, fontSize = TandemTypography.label)
        Text(value, color = palette.text, fontSize = TandemTypography.cardTitle, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(detail, Modifier.fillMaxWidth(), color = palette.muted, fontSize = TandemTypography.caption, maxLines = 2)
    }
}
