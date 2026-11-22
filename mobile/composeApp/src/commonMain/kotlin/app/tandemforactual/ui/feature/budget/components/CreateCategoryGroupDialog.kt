@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.enqueueBudgetCategoryGroupCreate
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.feature.budget.CategoryGroupNameDialog
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Tags
import kotlinx.coroutines.launch

/** Dialog that names a new category group and queues its creation. */
@Composable
internal fun CreateCategoryGroupDialog(
    visible: Boolean,
    state: AppState,
    mobileSession: TandemMobileSession,
    name: String,
    onNameChange: (String) -> Unit,
    isIncome: Boolean,
    onIncomeChange: (Boolean) -> Unit,
    groupIds: List<String>,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var isCreatingCategoryGroup by rememberSaveable(state.selectedBudgetId) { mutableStateOf(false) }
    CategoryGroupNameDialog(
        visible = visible,
        title = "New category group",
        name = name,
        onNameChange = onNameChange,
        busy = isCreatingCategoryGroup || mobileSession.budgetQueue.isPersistingBudgetMutation,
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
        placeholder = "Travel & Leisure",
        nameLabel = "Group name",
        description = "Keep related categories together under one heading.",
        leadingIcon = Lucide.Tags,
        isIncome = isIncome,
        onIncomeChange = onIncomeChange,
        saveLabel = "Create group",
        onDismiss = onDismiss,
        onSave = {
            if (!isCreatingCategoryGroup) {
                isCreatingCategoryGroup = true
                scope.launch {
                    try {
                        if (mobileSession.budgetQueue.enqueueBudgetCategoryGroupCreate(
                                state.selectedBudgetId,
                                name,
                                isIncome,
                                groupIds,
                                state.encryptedPassword.takeIf { it.isNotBlank() },
                            ) != null
                        ) {
                            onDismiss()
                        }
                    } finally {
                        isCreatingCategoryGroup = false
                    }
                }
            }
        },
    )
}
