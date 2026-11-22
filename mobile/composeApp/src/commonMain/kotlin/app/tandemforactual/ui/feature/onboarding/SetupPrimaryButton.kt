@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.onboarding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.feedback.TandemLoadingIndicator
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide

@Composable
internal fun SetupPrimaryButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    val light = palette.background.luminance() >= 0.5f
    val shape = RoundedCornerShape(TandemShapes.medium)
    val background =
        if (enabled) {
            if (light) {
                palette.text
            } else {
                palette.accent
            }
        } else {
            palette.raised
        }
    val foreground =
        when {
            !enabled -> palette.muted
            light -> palette.background
            else -> palette.accentContent
        }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clip(shape)
            .background(background)
            .clickable(enabled = enabled && !loading, role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = TandemSpacing.large),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) TandemLoadingIndicator(Modifier.size(18.dp), foreground, 2.dp)
        Text(
            label,
            modifier = Modifier.weight(1f).padding(start = if (loading) TandemSpacing.small else 0.dp),
            color = foreground,
            fontSize = TandemTypography.action,
            fontWeight = FontWeight.SemiBold,
        )
        Icon(Lucide.ChevronRight, contentDescription = null, modifier = Modifier.size(21.dp), tint = foreground)
    }
}
