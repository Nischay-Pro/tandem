@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.accounts
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.planning.AccountKind
import app.tandemforactual.ui.design.buttons.MIN_TOUCH_TARGET
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.X

/** Horizontally scrolling row of account type filter chips. */
@Composable
internal fun AccountTypeFilters(
    selectedFilter: String,
    onFilterChange: (String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
    ) {
        ACCOUNT_FILTERS.forEach { filter ->
            AccountFilterChip(filter, selected = selectedFilter == filter) {
                onFilterChange(filter)
            }
        }
    }
}

internal val ACCOUNT_FILTERS = listOf("All", "Bank", "Credit", "Loans", "Other")

/** The filter chip an account kind falls under. */
internal fun accountFilterFor(kind: AccountKind): String =
    when (kind) {
        AccountKind.BANK, AccountKind.SAVINGS, AccountKind.CASH -> "Bank"
        AccountKind.CREDIT_CARD -> "Credit"
        AccountKind.LOAN, AccountKind.MORTGAGE -> "Loans"
        AccountKind.INVESTMENT, AccountKind.OTHER -> "Other"
    }

internal fun accountMatchesFilter(
    account: AccountListItemUi,
    groupName: String,
    filter: String,
    query: String,
): Boolean {
    val matchesType = filter == "All" || accountFilterFor(account.kind) == filter
    val matchesSearch =
        query.isBlank() ||
            account.name.contains(query, ignoreCase = true) ||
            account.kind.label.contains(query, ignoreCase = true) ||
            account.status.contains(query, ignoreCase = true) ||
            groupName.contains(query, ignoreCase = true)
    return matchesType && matchesSearch
}

@Composable
internal fun AccountSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    val palette = tandemPalette
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    SurfaceCard {
        Row(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp).padding(horizontal = TandemSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
        ) {
            Icon(Lucide.Search, contentDescription = null, modifier = Modifier.size(20.dp), tint = palette.muted)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                singleLine = true,
                textStyle = TextStyle(color = palette.text, fontSize = TandemTypography.body),
                cursorBrush = SolidColor(palette.accent),
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text("Search accounts", color = palette.muted, fontSize = TandemTypography.body)
                        }
                        innerTextField()
                    }
                },
            )
            TandemIconButton(
                Lucide.X,
                "Clear account search",
                sizeDp = 36,
                iconSizeDp = 16,
                enabled = value.isNotEmpty(),
                onClick = onClear,
            )
        }
    }
}

@Composable
private fun AccountFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    val shape = CircleShape
    val interactions = remember { MutableInteractionSource() }
    Box(
        Modifier
            .heightIn(min = MIN_TOUCH_TARGET)
            .clickable(interactions, null, role = Role.Button, onClickLabel = "$label accounts", onClick = onClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            Modifier
                .clip(shape)
                .background(if (selected) palette.accent else palette.surface)
                .border(1.dp, if (selected) palette.accent else palette.border, shape)
                .indication(interactions, LocalIndication.current)
                .padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.small),
            color = if (selected) palette.accentContent else palette.text,
            fontSize = TandemTypography.label,
            fontWeight = FontWeight.Medium,
        )
    }
}
