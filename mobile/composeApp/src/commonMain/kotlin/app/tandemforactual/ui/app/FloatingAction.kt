@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.feedback.reservesNoticeSpace
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemLabelEnter
import app.tandemforactual.ui.design.theme.tandemLabelExit
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion

@Composable
internal fun FloatingAction(
    compact: Boolean,
    enabled: Boolean,
    label: String,
    contentDescription: String,
    icon: ImageVector,
    loading: Boolean = false,
    outlined: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = tandemPalette
    val isCompact = compact || LocalDensity.current.fontScale >= 1.5f
    val shape = RoundedCornerShape(TandemShapes.capsule)
    val morph = spring<Dp>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)
    val horizontalPadding by animateDpAsState(
        if (isCompact) TandemSpacing.large else TandemSpacing.extraLarge,
        if (tandemReduceMotion) snap() else morph,
        label = "floating-action-padding",
    )
    val actionColor =
        when {
            !enabled -> palette.muted
            outlined -> palette.accent
            else -> palette.accentContent
        }
    val backgroundColor =
        when {
            !enabled -> palette.raised
            outlined -> palette.surface
            else -> palette.accent
        }
    Row(
        modifier =
            modifier
                .reservesNoticeSpace()
                .height(56.dp)
                .defaultMinSize(minWidth = 56.dp)
                .clip(shape)
                .background(backgroundColor)
                .then(if (outlined) Modifier.border(1.dp, palette.border, shape) else Modifier)
                .semantics(mergeDescendants = true) { this.contentDescription = contentDescription }
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
                .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), color = actionColor, strokeWidth = 2.dp)
        } else {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = actionColor)
        }
        AnimatedVisibility(
            visible = !isCompact,
            enter = tandemLabelEnter(tandemReduceMotion),
            exit = tandemLabelExit(tandemReduceMotion),
        ) {
            Text(
                label,
                modifier = Modifier.padding(start = TandemSpacing.small),
                color = actionColor,
                fontSize = TandemTypography.body,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
