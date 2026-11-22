@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.ui.core.session.BudgetReorderStatus
import app.tandemforactual.ui.core.session.budget.enqueueBudgetCategoryGroupVisibilityChange
import app.tandemforactual.ui.core.session.dragReorderItemModifier
import app.tandemforactual.ui.core.state.LiveCategoryGroupSummary
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.design.buttons.SecondaryButton
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import app.tandemforactual.ui.feature.budget.BudgetItemReorderActions
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.Lucide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Hidden categories and category groups that are shown again when tapped. */
internal class HiddenCategoryLists(
    val hiddenCategoryRecords: List<BudgetRecordEntity>,
    val orderedHiddenCategoryRecords: List<BudgetRecordEntity>,
    val hiddenGroupRecords: List<BudgetRecordEntity>,
    val hiddenCategoryMoveStatus: BudgetReorderStatus,
)

/** Toggle and rows for the hidden categories and groups. */
internal fun LazyListScope.hiddenCategoryItems(
    group: LiveCategoryGroupSummary,
    hidden: HiddenCategoryLists,
    context: CategoryGroupListContext,
    scope: CoroutineScope,
) {
    val screen = context.screen
    if (context.isLiveBudget && (hidden.hiddenCategoryRecords.isNotEmpty() || hidden.hiddenGroupRecords.isNotEmpty())) {
        item(key = "category-group-hidden-toggle:${group.id}") {
            SecondaryButton(if (screen.showHidden) "Hide hidden items" else "Show hidden items") {
                screen.showHidden = !screen.showHidden
            }
        }
        if (screen.showHidden) {
            hidden.hiddenCategoryMoveStatus.message?.let { message ->
                item(key = "hidden-category-reorder-message:${group.id}") {
                    CategoryReorderMessage(context.state, context.mobileSession, message, hidden.hiddenCategoryMoveStatus)
                }
            }
            items(hidden.orderedHiddenCategoryRecords, key = { "hidden-category:${it.recordId}" }) { record ->
                HiddenCategoryCard(group, record, hidden, context)
            }
            items(hidden.hiddenGroupRecords, key = { "hidden-group:${it.recordId}" }) { record ->
                SurfaceCard {
                    ListRow(
                        record.recordName() ?: "Hidden category group",
                        "Category group · hidden",
                        leading = RowLeading.Icon(Lucide.EyeOff),
                        trailing = "Show",
                        onClick = {
                            scope.launch {
                                context.mobileSession.budgetQueue.enqueueBudgetCategoryGroupVisibilityChange(
                                    context.state.selectedBudgetId,
                                    record.recordId,
                                    hidden = false,
                                    encryptionPassword = context.state.encryptedPassword.takeIf { it.isNotBlank() },
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LazyItemScope.HiddenCategoryCard(
    group: LiveCategoryGroupSummary,
    hidden: BudgetRecordEntity,
    lists: HiddenCategoryLists,
    context: CategoryGroupListContext,
) {
    val state = context.state
    val screen = context.screen
    val reorder = context.reorder
    val isReordering = screen.selectedCategoryOrderId == hidden.recordId
    val hiddenIndex = lists.orderedHiddenCategoryRecords.indexOfFirst { it.recordId == hidden.recordId }
    val hiddenDragEnabled = context.isLiveBudget && !state.readOnlyMode && lists.orderedHiddenCategoryRecords.size > 1
    SurfaceCard(
        modifier =
            dragReorderItemModifier(
                itemId = hidden.recordId,
                dragState = reorder.hiddenDrag,
                reduceMotion = tandemReduceMotion,
                dragEnabled = hiddenDragEnabled,
                onDragStarted = { reorder.startHiddenDrag(hidden.recordId) },
                onDragBy = { reorder.dragHiddenBy(hidden.recordId, it) },
                onDragStopped = { reorder.dropHiddenDrag(hidden.recordId) },
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ListRow(
                hidden.recordName() ?: "Hidden category",
                if (isReordering) null else "Hidden",
                leading = RowLeading.Icon(Lucide.EyeOff),
                options = ListRowOptions(showChevron = !isReordering),
                // With drag enabled, a long press starts dragging (which also shows the controls).
                onLongClick =
                    if (!state.readOnlyMode &&
                        !isReordering &&
                        !hiddenDragEnabled
                    ) {
                        { screen.selectedCategoryOrderId = hidden.recordId }
                    } else {
                        null
                    },
                onClick =
                    {
                        if (isReordering) {
                            screen.selectedCategoryOrderId = null
                        } else {
                            reorder.setCategoryHidden(hidden.recordId, group.id, hidden = false)
                        }
                    },
                modifier = Modifier.weight(1f),
            )
            if (isReordering) {
                BudgetItemReorderActions(
                    itemName = hidden.recordName() ?: "Hidden category",
                    isHidden = true,
                    canMoveUp = hiddenIndex > 0,
                    canMoveDown = hiddenIndex >= 0 && hiddenIndex < lists.hiddenCategoryRecords.lastIndex,
                    enabled = !state.readOnlyMode,
                    toggleEnabled = !state.readOnlyMode,
                    onToggleHidden = {
                        reorder.setCategoryHidden(hidden.recordId, group.id, hidden = false) {
                            screen.selectedCategoryOrderId = null
                        }
                    },
                    onMoveUp = { reorder.moveHiddenCategory(hidden.recordId, -1) },
                    onMoveDown = { reorder.moveHiddenCategory(hidden.recordId, 1) },
                )
            }
        }
    }
}
