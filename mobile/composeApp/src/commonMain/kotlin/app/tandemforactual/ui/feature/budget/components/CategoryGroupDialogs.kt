@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget.components

import androidx.compose.runtime.Composable
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.enqueueBudgetCategoryCreate
import app.tandemforactual.ui.core.session.budget.enqueueBudgetCategoryGroupRename
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveCategoryGroupSummary
import app.tandemforactual.ui.feature.budget.CategoryGroupNameDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Dialog that renames a category group, with a way into deleting it. */
@Composable
internal fun EditCategoryGroupDialog(
    state: AppState,
    mobileSession: TandemMobileSession,
    group: LiveCategoryGroupSummary,
    groups: List<LiveCategoryGroupSummary>,
    screen: CategoryGroupScreenState,
    scope: CoroutineScope,
) {
    CategoryGroupNameDialog(
        visible = screen.showGroupDialog,
        title = "Edit category group",
        name = screen.groupNameDraft,
        onNameChange = { screen.groupNameDraft = it },
        busy = mobileSession.isPerformingBudgetAction || state.readOnlyMode,
        message = mobileSession.budgetActionMessage,
        messageKind = mobileSession.feedback.budgetActionMessageKind,
        messageActionLabel = mobileSession.feedback.budgetActionMessageActionLabel,
        onMessageAction =
            if (mobileSession.feedback.budgetActionMessageActionLabel ==
                null
            ) {
                null
            } else {
                mobileSession.feedback::retryBudgetAction
            },
        placeholder = group.name,
        onDismiss = { screen.showGroupDialog = false },
        onSave = {
            scope.launch {
                if (mobileSession.budgetQueue.enqueueBudgetCategoryGroupRename(
                        state.selectedBudgetId,
                        group.id,
                        screen.groupNameDraft,
                        groups.map { it.id },
                        state.encryptedPassword.takeIf { it.isNotBlank() },
                    )
                ) {
                    screen.showGroupDialog = false
                }
            }
        },
        onDelete = {
            screen.deleteGroupDestinationId = UNSELECTED_GROUP_DELETE_DESTINATION
            screen.showGroupDialog = false
            screen.showDeleteGroupDialog = true
        },
    )
}

/** Dialog that names a new category in this group. */
@Composable
internal fun AddCategoryDialog(
    state: AppState,
    mobileSession: TandemMobileSession,
    group: LiveCategoryGroupSummary,
    screen: CategoryGroupScreenState,
    scope: CoroutineScope,
) {
    CategoryGroupNameDialog(
        visible = screen.showCreateCategoryDialog,
        title = "Add category",
        name = screen.newCategoryName,
        onNameChange = { screen.newCategoryName = it },
        busy = mobileSession.isPerformingBudgetAction || state.readOnlyMode,
        message = mobileSession.budgetActionMessage,
        messageKind = mobileSession.feedback.budgetActionMessageKind,
        messageActionLabel = mobileSession.feedback.budgetActionMessageActionLabel,
        onMessageAction =
            if (mobileSession.feedback.budgetActionMessageActionLabel ==
                null
            ) {
                null
            } else {
                mobileSession.feedback::retryBudgetAction
            },
        placeholder = "Groceries",
        onDismiss = { screen.showCreateCategoryDialog = false },
        onSave = {
            scope.launch {
                if (mobileSession.budgetQueue.enqueueBudgetCategoryCreate(
                        state.selectedBudgetId,
                        group.id,
                        screen.newCategoryName,
                        group.categories.map { it.id },
                        state.encryptedPassword.takeIf { it.isNotBlank() },
                    ) != null
                ) {
                    screen.showCreateCategoryDialog = false
                }
            }
        },
    )
}
