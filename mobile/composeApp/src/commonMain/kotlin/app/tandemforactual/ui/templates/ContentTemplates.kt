@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.components.LabelText
import app.tandemforactual.ui.components.SurfaceCard
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide

@Composable
internal fun AttentionBanner(
    title: String,
    detail: String,
    action: String,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    SurfaceCard(onClick = onClick, onClickLabel = action) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.medium, vertical = TandemSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
        ) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(palette.warning.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("!", color = palette.warning, fontSize = TandemTypography.label, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall)) {
                Text(
                    title,
                    color = palette.text,
                    fontSize = TandemTypography.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    detail,
                    color = palette.muted,
                    fontSize = TandemTypography.micro,
                    lineHeight = TandemTypography.microLineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                action,
                color = palette.accent,
                fontSize = TandemTypography.micro,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(Lucide.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp), tint = palette.muted)
        }
    }
}

@Composable
internal fun Heading(
    title: String,
    subtitle: String? = null,
    compactSubtitle: Boolean = false,
) {
    val palette = tandemPalette
    Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
        Text(
            title,
            color = palette.text,
            fontSize = TandemTypography.pageTitle,
            lineHeight = TandemTypography.headingLineHeight,
            fontWeight = FontWeight.Bold,
        )
        if (subtitle != null) {
            Text(
                subtitle,
                color = palette.muted,
                fontSize = if (compactSubtitle) TandemTypography.compactBody else TandemTypography.body,
                lineHeight = TandemTypography.bodyLineHeight,
            )
        }
    }
}

@Composable
internal fun MonthSelectorCard(
    month: String,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    SurfaceCard(onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                LabelText("Planning period")
                Spacer(Modifier.height(TandemSpacing.extraSmall))
                Text(month, color = palette.text, fontSize = TandemTypography.action, fontWeight = FontWeight.SemiBold)
            }
            Icon(
                Lucide.ChevronDown,
                contentDescription = "Choose month",
                modifier = Modifier.size(22.dp),
                tint = palette.accent,
            )
        }
    }
}

@Composable
internal fun CompactActionButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(TandemShapes.control))
            .border(1.dp, palette.border, RoundedCornerShape(TandemShapes.control))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = TandemSpacing.small, vertical = TandemSpacing.small),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = palette.text,
            fontSize = TandemTypography.label,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
        )
    }
}
