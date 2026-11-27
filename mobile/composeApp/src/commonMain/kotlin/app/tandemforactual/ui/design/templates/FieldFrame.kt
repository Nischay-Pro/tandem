@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.tandemPalette

@Composable
internal fun FieldFrame(
    label: String,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    onClickLabel: String? = null,
    onClick: (() -> Unit)? = null,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(TandemSpacing.medium),
    content: @Composable RowScope.() -> Unit,
) {
    val palette = tandemPalette
    val shape = RoundedCornerShape(TandemShapes.medium)
    Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
        FieldLabel(label)
        Row(
            modifier =
                modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = if (large) 82.dp else 52.dp)
                    .clip(shape)
                    .background(palette.surface)
                    .border(1.dp, palette.outline, shape)
                    .then(
                        if (onClick != null) {
                            Modifier.clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
                        } else {
                            Modifier
                        },
                    ).padding(
                        horizontal = if (large) TandemSpacing.screen else TandemSpacing.large,
                        vertical = if (large) TandemSpacing.screen else TandemSpacing.medium,
                    ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = horizontalArrangement,
        ) {
            content()
        }
    }
}
