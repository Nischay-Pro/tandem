@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.transactions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.SelectorGroup
import app.tandemforactual.ui.core.state.SelectorItem
import app.tandemforactual.ui.core.ui.AccountIconTile
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.sheets.TandemChoiceDialog
import app.tandemforactual.ui.design.templates.ChoiceRow
import app.tandemforactual.ui.design.templates.PageLazyColumn
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.templates.TransactionTypeChip
import app.tandemforactual.ui.design.templates.transactionTypeColor
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import kotlinx.coroutines.flow.first

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun SelectorScreen(
    values: List<SelectorItem>,
    selected: String,
    live: Boolean = false,
    topContent: (@Composable () -> Unit)? = null,
    noMatches: (@Composable (String) -> Unit)? = null,
    groups: List<SelectorGroup> = emptyList(),
    searchLabel: String = "Search items",
    searchPlaceholder: String = "Find an item",
    onSelect: (SelectorItem) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filteredContent =
        remember(values, groups, query) {
            val filtered = values.filter { it.label.contains(query, ignoreCase = true) }
            val filteredIds = filtered.mapTo(mutableSetOf(), SelectorItem::id)
            val groupedItems =
                groups.mapNotNull { group ->
                    val items = group.items.filter { it.id in filteredIds }
                    if (items.isEmpty()) null else group to items
                }
            val groupedIds = groupedItems.flatMapTo(mutableSetOf()) { (_, items) -> items.map(SelectorItem::id) }
            Triple(filtered, groupedItems, filtered.filterNot { it.id in groupedIds })
        }
    val filtered = filteredContent.first
    val groupedItems = filteredContent.second
    val ungroupedItems = filteredContent.third
    PageLazyColumn {
        if (values.size > 8) {
            item {
                TextFieldCard(searchLabel, query, { query = it }, TextFieldOptions(placeholder = searchPlaceholder))
            }
        }
        topContent?.let { content -> item { content() } }
        if (filtered.isEmpty()) {
            item {
                if (noMatches != null) {
                    noMatches(query)
                } else {
                    SurfaceCard { EmptyState("No matches", modifier = Modifier.padding(TandemSpacing.large)) }
                }
            }
        } else {
            items(ungroupedItems, key = { "choice-${it.id}" }) { item ->
                ChoiceRow(
                    item.label,
                    selected = selected.isNotBlank() && selected == if (live) item.id else item.label,
                    onClick = { onSelect(item) },
                )
            }
            groupedItems.forEach { (group, groupItems) ->
                stickyHeader(key = "selector-group-${group.title}") {
                    SurfaceCard(Modifier.fillMaxWidth()) {
                        Text(
                            group.title,
                            modifier = Modifier.padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.small),
                            color = tandemPalette.text,
                            fontSize = TandemTypography.label,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                items(groupItems, key = { "${group.title}-${it.id}" }) { item ->
                    ChoiceRow(
                        item.label,
                        selected = selected.isNotBlank() && selected == if (live) item.id else item.label,
                        onClick = { onSelect(item) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun TransactionTypePicker(
    type: String,
    allowTransfer: Boolean = false,
    onBeforeOpen: () -> Unit = {},
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(contentAlignment = Alignment.Center) {
        TransactionTypeChip(type) {
            onBeforeOpen()
            expanded = true
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            (if (allowTransfer) listOf("Spending", "Income", "Transfer") else listOf("Spending", "Income")).forEach { choice ->
                DropdownMenuItem(
                    onClick = {
                        expanded = false
                        onSelect(choice)
                    },
                ) {
                    Text(
                        choice,
                        color = transactionTypeColor(choice, tandemPalette),
                        fontSize = TandemTypography.compactBody,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
internal fun TransactionAccountPickerDialog(
    visible: Boolean,
    accounts: List<LiveAccountSummary>,
    selectedId: String,
    selectedName: String,
    live: Boolean,
    onSelect: (SelectorItem) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val values =
        if (live) {
            accounts.filter { !it.closed || it.id == selectedId }.map { SelectorItem(it.id, it.name) }
        } else {
            listOf("Checking", "Savings", "Credit card", "Cash").map { SelectorItem(it, it) }
        }
    val filtered = values.filter { it.label.contains(query, ignoreCase = true) }
    TandemChoiceDialog(visible, onDismiss) {
        SurfaceCard(Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.large)) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 640.dp).padding(TandemSpacing.large),
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
            ) {
                TandemModalHeader(
                    title = "Choose account",
                    titleSize = TandemTypography.cardTitle,
                    onClose = onDismiss,
                )
                if (values.size > 6) {
                    TextFieldCard("Search accounts", query, { query = it }, TextFieldOptions(placeholder = "Find an account"))
                }
                if (filtered.isEmpty()) {
                    EmptyState("No accounts found", modifier = Modifier.padding(TandemSpacing.large))
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
                        items(filtered, key = { "account-choice-${it.id}" }) { item ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                            ) {
                                if (live) AccountIconTile(item.label, accounts.firstOrNull { it.id == item.id }?.type)
                                Box(Modifier.weight(1f)) {
                                    ChoiceRow(
                                        item.label,
                                        selected = if (live) selectedId == item.id else selectedName == item.label,
                                        onClick = { onSelect(item) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
