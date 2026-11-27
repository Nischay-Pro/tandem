@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sync
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.templates.DisplayHeadline
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemAlloFamily
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide

@Composable
internal fun SyncCountChip(
    label: String,
    color: androidx.compose.ui.graphics.Color,
) {
    Text(
        label.uppercase(),
        Modifier
            .clip(RoundedCornerShape(TandemShapes.pill))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = TandemSpacing.medium, vertical = TandemSpacing.extraSmall),
        color = color,
        fontSize = TandemTypography.caption,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = TandemTypography.captionLetterSpacing,
    )
}

@Composable
internal fun AmountTransitionLine(change: BudgetAmountChange) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
    ) {
        Text(
            change.label,
            modifier = Modifier.weight(1f),
            color = tandemPalette.text,
            fontSize = TandemTypography.caption,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            change.before,
            color = tandemPalette.muted,
            fontSize = TandemTypography.caption,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text("→", color = tandemPalette.accent, fontSize = TandemTypography.caption, fontWeight = FontWeight.Bold)
        Text(
            change.after,
            color = tandemPalette.text,
            fontSize = TandemTypography.caption,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun SyncPageHeading(
    eyebrow: String,
    title: String,
    detail: String?,
) {
    val palette = tandemPalette
    Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
        Text(
            eyebrow.uppercase(),
            color = palette.muted,
            fontSize = TandemTypography.caption,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = TandemTypography.captionLetterSpacing,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        DisplayHeadline(title)
        detail?.let {
            Text(
                it,
                color = palette.muted,
                fontSize = TandemTypography.body,
                lineHeight = TandemTypography.bodyLineHeight,
            )
        }
    }
}

@Composable
internal fun SyncCountTile(
    label: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(TandemShapes.medium)
    Row(
        modifier
            .clip(shape)
            .background(color.copy(alpha = 0.09f))
            .border(1.dp, color.copy(alpha = 0.28f), shape)
            .clickable(role = Role.Button, onClickLabel = "Open $label", onClick = onClick)
            .padding(horizontal = TandemSpacing.small, vertical = TandemSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = color)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall)) {
            Text(count.toString(), color = tandemPalette.text, fontSize = TandemTypography.cardTitle, fontWeight = FontWeight.Bold)
            Text(label, color = tandemPalette.muted, fontSize = TandemTypography.micro, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Lucide.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp), tint = color)
    }
}

@Composable
internal fun ChangeSectionHeading(
    title: String,
    detail: String,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = TandemSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            color = tandemPalette.text,
            fontSize = TandemTypography.sectionTitle,
            fontFamily = tandemAlloFamily(),
            fontWeight = FontWeight.Bold,
        )
        Text(detail, color = tandemPalette.muted, fontSize = TandemTypography.label)
    }
}

@Composable
internal fun SyncChangeSection(
    title: String,
    detail: String,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
    ) {
        ChangeSectionHeading(title, detail)
        SurfaceCard {
            Column(Modifier.fillMaxWidth()) { content() }
        }
    }
}
