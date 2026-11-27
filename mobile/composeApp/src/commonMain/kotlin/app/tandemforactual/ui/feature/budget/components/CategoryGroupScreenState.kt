package app.tandemforactual.ui.feature.budget.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

internal const val UNSELECTED_GROUP_DELETE_DESTINATION = "__choose_group_delete_destination__"

/** Open dialogs, drafts and the reordering selection of the category group screen. */
@Stable
internal class CategoryGroupScreenState(
    showHidden: MutableState<Boolean>,
    selectedCategoryOrderId: MutableState<String?>,
    showGroupDialog: MutableState<Boolean>,
    showDeleteGroupDialog: MutableState<Boolean>,
    deleteGroupDestinationId: MutableState<String>,
    showCreateCategoryDialog: MutableState<Boolean>,
    groupNameDraft: MutableState<String>,
    newCategoryName: MutableState<String>,
    showUsageSheet: MutableState<Boolean>,
) {
    var showHidden by showHidden
    var selectedCategoryOrderId by selectedCategoryOrderId
    var showGroupDialog by showGroupDialog
    var showDeleteGroupDialog by showDeleteGroupDialog
    var deleteGroupDestinationId by deleteGroupDestinationId
    var showCreateCategoryDialog by showCreateCategoryDialog
    var groupNameDraft by groupNameDraft
    var newCategoryName by newCategoryName
    var showUsageSheet by showUsageSheet
}

@Composable
internal fun rememberCategoryGroupScreenState(
    budgetId: String,
    groupId: String?,
    groupName: String?,
): CategoryGroupScreenState {
    val showHidden = rememberSaveable(budgetId) { mutableStateOf(false) }
    val selectedCategoryOrderId = rememberSaveable(budgetId, groupId) { mutableStateOf<String?>(null) }
    val showGroupDialog = rememberSaveable(budgetId, groupId) { mutableStateOf(false) }
    val showDeleteGroupDialog = rememberSaveable(budgetId, groupId) { mutableStateOf(false) }
    val deleteGroupDestinationId =
        rememberSaveable(budgetId, groupId) {
            mutableStateOf(UNSELECTED_GROUP_DELETE_DESTINATION)
        }
    val showCreateCategoryDialog = rememberSaveable(budgetId, groupId) { mutableStateOf(false) }
    val groupNameDraft = rememberSaveable(budgetId, groupId) { mutableStateOf(groupName.orEmpty()) }
    val newCategoryName = rememberSaveable(budgetId, groupId) { mutableStateOf("") }
    val showUsageSheet = rememberSaveable(budgetId, groupId) { mutableStateOf(false) }
    return remember(
        showHidden,
        selectedCategoryOrderId,
        showGroupDialog,
        showDeleteGroupDialog,
        deleteGroupDestinationId,
        showCreateCategoryDialog,
        groupNameDraft,
        newCategoryName,
        showUsageSheet,
    ) {
        CategoryGroupScreenState(
            showHidden,
            selectedCategoryOrderId,
            showGroupDialog,
            showDeleteGroupDialog,
            deleteGroupDestinationId,
            showCreateCategoryDialog,
            groupNameDraft,
            newCategoryName,
            showUsageSheet,
        )
    }
}
