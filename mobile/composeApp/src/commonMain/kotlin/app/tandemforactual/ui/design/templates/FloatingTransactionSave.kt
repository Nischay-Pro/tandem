@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.templates

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.sheets.RegisterFormSubmit
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemFadeEnter
import app.tandemforactual.ui.design.theme.tandemFadeExit
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide

@Composable
internal fun FloatingTransactionSave(
    compact: Boolean,
    enabled: Boolean,
    saved: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = tandemPalette
    RegisterFormSubmit(enabled && !saved, onClick)
    Row(
        modifier =
            modifier
                .animateContentSize(tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTENT_SIZE_MILLIS))
                .height(56.dp)
                .clip(if (compact) CircleShape else RoundedCornerShape(TandemShapes.large))
                .background(
                    if (saved) {
                        palette.positive
                    } else if (enabled) {
                        palette.accent
                    } else {
                        palette.raised
                    },
                ).semantics(mergeDescendants = true) {
                    contentDescription = if (saved) "Saved on this device" else "Save transaction"
                }.clickable(enabled = enabled && !saved, role = Role.Button, onClickLabel = "Save transaction", onClick = onClick)
                .padding(horizontal = if (compact) TandemSpacing.large else TandemSpacing.extraLarge),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 0.dp else TandemSpacing.small),
    ) {
        Icon(
            Lucide.Check,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint =
                when {
                    saved -> palette.background
                    enabled -> palette.accentContent
                    else -> palette.muted
                },
        )
        AnimatedVisibility(visible = !compact, enter = tandemFadeEnter(tandemReduceMotion), exit = tandemFadeExit(tandemReduceMotion)) {
            Text(
                if (saved) "Saved" else "Save",
                color = if (saved || enabled) palette.accentContent else palette.muted,
                fontSize = TandemTypography.action,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
