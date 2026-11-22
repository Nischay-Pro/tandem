@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import app.tandemforactual.ui.design.money.amountAnnotatedString
import app.tandemforactual.ui.design.theme.TandemLayout
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette

@Composable
internal fun SettingRowLayout(
    title: String,
    subtitle: String?,
    enabled: Boolean,
    compactTitle: Boolean = false,
    semiboldTitle: Boolean = false,
    leadingIcon: ImageVector? = null,
    interactionModifier: Modifier = Modifier,
    control: @Composable RowScope.() -> Unit,
) {
    val palette = tandemPalette
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = TandemLayout.listRowMinHeight)
                .then(interactionModifier)
                .padding(horizontal = TandemLayout.listRowHorizontalInset, vertical = TandemLayout.listRowVerticalInset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemLayout.listRowGap),
    ) {
        if (leadingIcon != null) {
            Box(
                Modifier
                    .size(TandemLayout.listRowLeadingSize)
                    .clip(RoundedCornerShape(TandemShapes.medium))
                    .background(palette.raised),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(TandemLayout.listRowIconSize),
                    tint = if (enabled) palette.accent else palette.muted,
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
            Text(
                amountAnnotatedString(title),
                color = if (enabled) palette.text else palette.muted,
                fontSize = if (compactTitle) TandemTypography.compactBody else TandemTypography.body,
                fontWeight = if (semiboldTitle) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    amountAnnotatedString(subtitle),
                    color = palette.muted,
                    fontSize = TandemTypography.caption,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        control()
    }
}
