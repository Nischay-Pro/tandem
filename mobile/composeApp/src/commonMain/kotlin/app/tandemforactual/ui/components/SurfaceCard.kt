@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.components

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
import app.tandemforactual.ui.TandemMotion
import app.tandemforactual.ui.TandemShapes
import app.tandemforactual.ui.tandemMotionSpec
import app.tandemforactual.ui.tandemPalette
import app.tandemforactual.ui.tandemReduceMotion

@Composable
internal fun SurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = tandemPalette
    Surface(
        modifier =
            modifier
                .animateContentSize(animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTENT_SIZE_MILLIS))
                .then(
                    if (onClick != null) {
                        Modifier.clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
                    } else {
                        Modifier
                    },
                ),
        shape = RoundedCornerShape(TandemShapes.large),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.border),
    ) {
        Column(content = content)
    }
}
