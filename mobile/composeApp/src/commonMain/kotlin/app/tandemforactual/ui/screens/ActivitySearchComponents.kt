@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.components.SurfaceCard
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.CircleDot
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minus
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Receipt
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Tags
import com.composables.icons.lucide.WalletCards
import com.composables.icons.lucide.X
import com.composables.icons.lucide.Zap

@Composable
internal fun ActivitySearchInput(
    value: String,
    onValueChange: (String) -> Unit,
) {
    val palette = tandemPalette
    SurfaceCard {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(horizontal = TandemSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
        ) {
            Icon(Lucide.Search, contentDescription = null, modifier = Modifier.size(21.dp), tint = palette.muted)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                textStyle =
                    TextStyle(
                        color = palette.text,
                        fontSize = TandemTypography.body,
                        lineHeight = TandemTypography.bodyLineHeight,
                        textAlign = TextAlign.Start,
                    ),
                cursorBrush = SolidColor(palette.accent),
                decorationBox = { innerTextField ->
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                "Payee, category or amount",
                                color = palette.muted,
                                fontSize = TandemTypography.body,
                                lineHeight = TandemTypography.bodyLineHeight,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            if (value.isNotEmpty()) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClickLabel = "Clear search", onClick = { onValueChange("") }),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Lucide.X, contentDescription = null, modifier = Modifier.size(20.dp), tint = palette.muted)
                }
            }
        }
    }
}

@Composable
internal fun ActivitySearchFilterChip(
    filter: ActivitySearchFilter,
    onRemove: () -> Unit,
) {
    val palette = tandemPalette
    Row(
        Modifier
            .clip(RoundedCornerShape(TandemShapes.pill))
            .background(palette.accentSurface)
            .heightIn(min = 44.dp)
            .clickable(
                role = Role.Button,
                onClickLabel = "Remove ${filter.label}",
                onClick = onRemove,
            ).padding(
                start = TandemSpacing.medium,
                end = TandemSpacing.small,
                top = TandemSpacing.small,
                bottom = TandemSpacing.small,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
    ) {
        Text(
            filter.label,
            color = palette.accent,
            fontSize = TandemTypography.caption,
            maxLines = 1,
        )
        Icon(
            Lucide.X,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = palette.accent,
        )
    }
}

@Composable
internal fun ActivitySearchSuggestion(
    suggestion: ActivitySearchFilter,
    onClick: () -> Unit,
) {
    SurfaceCard(onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 42.dp).padding(horizontal = TandemSpacing.medium, vertical = TandemSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
        ) {
            Icon(
                when (suggestion.field) {
                    ActivitySearchField.DATE_MONTH,
                    ActivitySearchField.DATE_ON,
                    ActivitySearchField.DATE_RANGE,
                    ActivitySearchField.DATE_MONTH_OF_YEAR,
                    ActivitySearchField.DATE_MONTH_DAY,
                    -> Lucide.CalendarDays
                    ActivitySearchField.ACCOUNT -> Lucide.WalletCards
                    ActivitySearchField.CATEGORY, ActivitySearchField.TAG -> Lucide.Tags
                    ActivitySearchField.TRANSFER -> Lucide.Zap
                    ActivitySearchField.CLEARED,
                    ActivitySearchField.RECONCILED,
                    ActivitySearchField.APPROVED,
                    ActivitySearchField.MATCHED,
                    -> Lucide.Check
                    ActivitySearchField.UNCLEARED,
                    ActivitySearchField.UNRECONCILED,
                    ActivitySearchField.UNAPPROVED,
                    ActivitySearchField.UNMATCHED,
                    -> Lucide.CircleDot
                    ActivitySearchField.OUTFLOW_IS, ActivitySearchField.OUTFLOW_AT_LEAST, ActivitySearchField.OUTFLOW_AT_MOST,
                    ActivitySearchField.INFLOW_IS, ActivitySearchField.INFLOW_AT_LEAST, ActivitySearchField.INFLOW_AT_MOST,
                    -> Lucide.Receipt
                    else -> Lucide.Search
                },
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = tandemPalette.accent,
            )
            Text(
                suggestion.label,
                color = tandemPalette.text,
                fontSize = TandemTypography.compactBody,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun ActivitySearchQuickFilter(
    filter: ActivitySearchFilter,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    Row(
        Modifier
            .clip(RoundedCornerShape(TandemShapes.pill))
            .background(palette.raised)
            .clickable(role = Role.Button, onClickLabel = "Filter by ${filter.label}", onClick = onClick)
            .padding(horizontal = TandemSpacing.medium, vertical = TandemSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
    ) {
        Icon(
            if (isActive) Lucide.Minus else Lucide.Plus,
            contentDescription = if (isActive) "Remove ${filter.label}" else "Add ${filter.label}",
            modifier = Modifier.size(14.dp),
            tint = palette.accent,
        )
        Text(filter.label, color = palette.text, fontSize = TandemTypography.caption, maxLines = 1)
    }
}
