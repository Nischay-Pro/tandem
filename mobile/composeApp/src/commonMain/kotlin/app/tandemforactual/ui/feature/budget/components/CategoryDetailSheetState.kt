package app.tandemforactual.ui.feature.budget.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/** Open sheets and drafts of the category detail screen. */
@Stable
internal class CategoryDetailSheetState(
    showEditDialog: MutableState<Boolean>,
    showDeleteDialog: MutableState<Boolean>,
    confirmCategoryDelete: MutableState<Boolean>,
    showAssignedAmountDialog: MutableState<Boolean>,
    showMoveMoneyDialog: MutableState<Boolean>,
    moveMoneyFromId: MutableState<String>,
    moveMoneyToId: MutableState<String>,
    moveMoneyAmountDraft: MutableState<String>,
    assignedAmountDraft: MutableState<String>,
    editName: MutableState<String>,
    editCategoryGroupId: MutableState<String>,
    iconSearch: MutableState<String>,
    selectedIconChoice: MutableState<String>,
    deleteDestinationId: MutableState<String?>,
    showUsageSheet: MutableState<Boolean>,
) {
    var showEditDialog by showEditDialog
    var showDeleteDialog by showDeleteDialog
    var confirmCategoryDelete by confirmCategoryDelete
    var showAssignedAmountDialog by showAssignedAmountDialog
    var showMoveMoneyDialog by showMoveMoneyDialog
    var moveMoneyFromId by moveMoneyFromId
    var moveMoneyToId by moveMoneyToId
    var moveMoneyAmountDraft by moveMoneyAmountDraft
    var assignedAmountDraft by assignedAmountDraft
    var editName by editName
    var editCategoryGroupId by editCategoryGroupId
    var iconSearch by iconSearch
    var selectedIconChoice by selectedIconChoice
    var deleteDestinationId by deleteDestinationId
    var showUsageSheet by showUsageSheet
}

@Composable
internal fun rememberCategoryDetailSheetState(
    budgetId: String,
    categoryId: String,
    monthId: String,
    initialGroupId: String,
): CategoryDetailSheetState {
    val showEditDialog = rememberSaveable(budgetId, categoryId) { mutableStateOf(false) }
    val showDeleteDialog = rememberSaveable(budgetId, categoryId) { mutableStateOf(false) }
    val confirmCategoryDelete = rememberSaveable(budgetId, categoryId) { mutableStateOf(false) }
    val showAssignedAmountDialog = rememberSaveable(budgetId, categoryId, monthId) { mutableStateOf(false) }
    val showMoveMoneyDialog = rememberSaveable(budgetId, categoryId, monthId) { mutableStateOf(false) }
    val moveMoneyFromId = rememberSaveable(budgetId, categoryId, monthId) { mutableStateOf("") }
    val moveMoneyToId = rememberSaveable(budgetId, categoryId, monthId) { mutableStateOf("") }
    val moveMoneyAmountDraft = rememberSaveable(budgetId, categoryId, monthId) { mutableStateOf("") }
    val assignedAmountDraft = rememberSaveable(budgetId, categoryId, monthId) { mutableStateOf("") }
    val editName = rememberSaveable(budgetId, categoryId) { mutableStateOf("") }
    val editCategoryGroupId = rememberSaveable(budgetId, categoryId) { mutableStateOf(initialGroupId) }
    val iconSearch = rememberSaveable(budgetId, categoryId) { mutableStateOf("") }
    val selectedIconChoice = rememberSaveable(budgetId, categoryId) { mutableStateOf("Automatic") }
    val deleteDestinationId = rememberSaveable(budgetId, categoryId) { mutableStateOf<String?>(null) }
    val showUsageSheet = rememberSaveable(budgetId, categoryId) { mutableStateOf(false) }
    return remember(budgetId, categoryId, monthId) {
        CategoryDetailSheetState(
            showEditDialog,
            showDeleteDialog,
            confirmCategoryDelete,
            showAssignedAmountDialog,
            showMoveMoneyDialog,
            moveMoneyFromId,
            moveMoneyToId,
            moveMoneyAmountDraft,
            assignedAmountDraft,
            editName,
            editCategoryGroupId,
            iconSearch,
            selectedIconChoice,
            deleteDestinationId,
            showUsageSheet,
        )
    }
}
