@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.cards

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion

@Composable
internal fun SurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    animateSizeChanges: Boolean = true,
    highlighted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = tandemPalette
    Surface(
        modifier =
            modifier
                .then(
                    if (animateSizeChanges) {
                        Modifier.animateContentSize(
                            animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTENT_SIZE_MILLIS),
                        )
                    } else {
                        Modifier
                    },
                ).then(
                    if (onClick != null) {
                        Modifier.clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
                    } else {
                        Modifier
                    },
                ),
        shape = RoundedCornerShape(TandemShapes.large),
        color = if (highlighted) palette.accentSurface else palette.surface,
        border = if (highlighted) BorderStroke(1.5.dp, palette.accent) else BorderStroke(1.dp, palette.border),
    ) {
        Column(content = content)
    }
}
