@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.rules
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.navigation.openActivityFor
import app.tandemforactual.ui.core.navigation.openRulesUsing
import app.tandemforactual.ui.core.search.ActivitySearchField
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.manageBudgetPayee
import app.tandemforactual.ui.core.session.budgetActionFeedbackMessage
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.usage.UsageLinks
import app.tandemforactual.ui.core.usage.UsageSubject
import app.tandemforactual.ui.core.usage.WHERE_USED_TITLE
import app.tandemforactual.ui.core.usage.WhereUsedBody
import app.tandemforactual.ui.core.usage.WhereUsedRow
import app.tandemforactual.ui.core.usage.rememberRecordUsage
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemAlloFamily
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Link
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Tags
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.X
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Selected payee, sheet visibility, sheet mode and the drafts edited in the manage-payee sheet. */
internal class ManagePayeeState(
    selectedPayeeId: MutableState<String?>,
    mode: MutableState<String>,
    sheetVisible: MutableState<Boolean>,
    nameDraft: MutableState<String>,
    mergeTargetId: MutableState<String?>,
    defaultCategoryDraft: MutableState<String>,
) {
    var selectedPayeeId by selectedPayeeId
    var mode by mode
    var sheetVisible by sheetVisible
    var nameDraft by nameDraft
    var mergeTargetId by mergeTargetId
    var defaultCategoryDraft by defaultCategoryDraft
}

@Composable
internal fun rememberManagePayeeState(budgetId: String): ManagePayeeState {
    val selectedPayeeId = rememberSaveable(budgetId) { mutableStateOf<String?>(null) }
    val mode = rememberSaveable(budgetId) { mutableStateOf("menu") }
    val sheetVisible = rememberSaveable(budgetId) { mutableStateOf(false) }
    val nameDraft = rememberSaveable(budgetId, selectedPayeeId.value) { mutableStateOf("") }
    val mergeTargetId = rememberSaveable(budgetId, selectedPayeeId.value) { mutableStateOf<String?>(null) }
    val defaultCategoryDraft = rememberSaveable(budgetId, selectedPayeeId.value) { mutableStateOf("") }
    return ManagePayeeState(selectedPayeeId, mode, sheetVisible, nameDraft, mergeTargetId, defaultCategoryDraft)
}

/** Task sheet for renaming, merging, deleting or setting the default category of the selected payee. */
@Composable
internal fun ManagePayeeSheet(
    payee: ManagePayeeState,
    selectedPayee: Pair<String, String>?,
    payees: List<Pair<String, String>>,
    selectedDefaultCategoryId: String?,
    selectedDefaultCategoryLabel: String,
    state: AppState,
    navigation: AppNavigation,
    mobileSession: TandemMobileSession,
    scope: CoroutineScope,
    defaultCategoryContent: @Composable () -> Unit,
) {
    TandemTaskSheet(
        visible = payee.sheetVisible && selectedPayee != null,
        onDismissRequest = {
            if (!mobileSession.isPerformingBudgetAction) payee.sheetVisible = false
        },
        onDismissed = {
            payee.selectedPayeeId = null
            payee.mode = "menu"
        },
    ) {
        if (selectedPayee != null) {
            SheetCard(
                modifier = Modifier.heightIn(max = 620.dp),
                maxWidth = 480.dp,
                header = {
                    ManagePayeeSheetHeader(payee.mode, selectedPayee.second, !mobileSession.isPerformingBudgetAction) {
                        payee.sheetVisible = false
                    }
                },
            ) {
                when (payee.mode) {
                    "default-category" -> {
                        defaultCategoryContent()
                    }

                    "rename" -> {
                        RenamePayeeSection(payee, selectedPayee, state, mobileSession, scope)
                    }

                    "merge" -> {
                        MergePayeeSection(payee, selectedPayee, payees, mobileSession)
                    }

                    "confirm-merge" -> {
                        Text("Confirm the merge in the alert.", color = tandemPalette.muted, fontSize = TandemTypography.body)
                    }

                    "delete" -> {
                        Text("Confirm deletion in the alert.", color = tandemPalette.muted, fontSize = TandemTypography.body)
                    }

                    "usage" -> {
                        PayeeUsageSection(payee, selectedPayee, state, navigation, mobileSession)
                    }

                    else -> {
                        ManagePayeeMenu(
                            payee,
                            selectedPayee,
                            payees,
                            selectedDefaultCategoryId,
                            selectedDefaultCategoryLabel,
                            state,
                            mobileSession,
                        )
                    }
                }
            }
        }
    }
}

/** Mode icon, title and close button at the top of the manage-payee sheet. */
@Composable
private fun ManagePayeeSheetHeader(
    mode: String,
    payeeName: String,
    enabled: Boolean,
    onClose: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
    ) {
        Icon(
            when (mode) {
                "rename" -> Lucide.Pencil
                "usage" -> Lucide.Link
                else -> Lucide.Tags
            },
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = tandemPalette.accent,
        )
        Text(
            when (mode) {
                "rename" -> "Rename payee"
                "merge", "confirm-merge" -> "Merge payee"
                "default-category" -> "Default category"
                "delete" -> "Delete payee?"
                "usage" -> WHERE_USED_TITLE
                else -> payeeName
            },
            modifier = Modifier.weight(1f),
            color = tandemPalette.text,
            fontSize = TandemTypography.sectionTitle,
            fontFamily = tandemAlloFamily(),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        TandemIconButton(
            Lucide.X,
            "Close",
            sizeDp = 40,
            iconSizeDp = 18,
            containerColor = tandemPalette.raised,
            enabled = enabled,
            onClick = onClose,
        )
    }
}

/** Name field and save button for renaming the payee. */
@Composable
private fun RenamePayeeSection(
    payee: ManagePayeeState,
    selectedPayee: Pair<String, String>,
    state: AppState,
    mobileSession: TandemMobileSession,
    scope: CoroutineScope,
) {
    TextFieldCard("Name", payee.nameDraft, { payee.nameDraft = it })
    budgetActionFeedbackMessage(mobileSession)
    TandemActionButton(
        if (mobileSession.isPerformingBudgetAction) "Saving…" else "Save",
        TandemActionButtonTone.PRIMARY,
        modifier = Modifier.fillMaxWidth(),
        icon = Lucide.Check,
        enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction && payee.nameDraft.isNotBlank(),
        loading = mobileSession.isPerformingBudgetAction,
    ) {
        scope.launch {
            val saved =
                mobileSession.budgetEdits.manageBudgetPayee(
                    state.selectedBudgetId,
                    selectedPayee.first,
                    "rename",
                    payee.nameDraft,
                    null,
                    state.encryptedPassword.takeIf { it.isNotBlank() },
                )
            if (saved) payee.sheetVisible = false
        }
    }
    TandemActionButton(
        "Back",
        TandemActionButtonTone.NEUTRAL,
        modifier = Modifier.fillMaxWidth(),
        enabled = !mobileSession.isPerformingBudgetAction,
    ) { payee.mode = "menu" }
}

/** List of other payees to merge the selected payee into. */
@Composable
private fun MergePayeeSection(
    payee: ManagePayeeState,
    selectedPayee: Pair<String, String>,
    payees: List<Pair<String, String>>,
    mobileSession: TandemMobileSession,
) {
    Text("Choose the payee to keep.", color = tandemPalette.muted, fontSize = TandemTypography.body)
    budgetActionFeedbackMessage(mobileSession)
    payees.filterNot { it.first == selectedPayee.first }.forEach { target ->
        SurfaceCard {
            ListRow(
                target.second,
                leading = RowLeading.Icon(Lucide.Tags),
                onClick = {
                    payee.mergeTargetId = target.first
                    payee.mode = "confirm-merge"
                },
            )
        }
    }
    TandemActionButton(
        "Back",
        TandemActionButtonTone.NEUTRAL,
        modifier = Modifier.fillMaxWidth(),
    ) { payee.mode = "menu" }
}

/** Default category row and rename, merge and delete actions for the payee. */
@Composable
private fun ManagePayeeMenu(
    payee: ManagePayeeState,
    selectedPayee: Pair<String, String>,
    payees: List<Pair<String, String>>,
    selectedDefaultCategoryId: String?,
    selectedDefaultCategoryLabel: String,
    state: AppState,
    mobileSession: TandemMobileSession,
) {
    budgetActionFeedbackMessage(mobileSession)
    SurfaceCard {
        WhereUsedRow { payee.mode = "usage" }
    }
    SurfaceCard {
        ListRow(
            "Default category",
            selectedDefaultCategoryLabel,
            leading = RowLeading.Icon(Lucide.Tags),
            onClick = {
                payee.defaultCategoryDraft = selectedDefaultCategoryId.orEmpty()
                mobileSession.feedback.clearBudgetActionMessage()
                payee.mode = "default-category"
            },
        )
    }
    TandemActionButton(
        "Rename",
        TandemActionButtonTone.NEUTRAL,
        modifier = Modifier.fillMaxWidth(),
        icon = Lucide.Pencil,
        enabled = !state.readOnlyMode,
    ) {
        payee.nameDraft = selectedPayee.second
        mobileSession.feedback.clearBudgetActionMessage()
        payee.mode = "rename"
    }
    TandemActionButton(
        "Merge into another",
        TandemActionButtonTone.NEUTRAL,
        modifier = Modifier.fillMaxWidth(),
        icon = Lucide.Tags,
        enabled = !state.readOnlyMode && payees.size > 1,
    ) {
        mobileSession.feedback.clearBudgetActionMessage()
        payee.mode = "merge"
    }
    TandemActionButton(
        "Delete",
        TandemActionButtonTone.DESTRUCTIVE,
        modifier = Modifier.fillMaxWidth(),
        icon = Lucide.Trash2,
        enabled = !state.readOnlyMode,
    ) {
        mobileSession.feedback.clearBudgetActionMessage()
        payee.mode = "delete"
    }
}

/** Read-only counts for the payee, linking to its transactions and the rules that name it. */
@Composable
private fun PayeeUsageSection(
    payee: ManagePayeeState,
    selectedPayee: Pair<String, String>,
    state: AppState,
    navigation: AppNavigation,
    mobileSession: TandemMobileSession,
) {
    val (payeeId, name) = selectedPayee
    WhereUsedBody(
        name,
        rememberRecordUsage(mobileSession, state.selectedBudgetId, UsageSubject.PAYEE, payeeId),
        UsageSubject.PAYEE,
        links =
            UsageLinks(
                transactions = {
                    payee.sheetVisible = false
                    openActivityFor(state, navigation, ActivitySearchField.PAYEE_CONTAINS, payeeId, "Payee: $name")
                },
                rules = {
                    payee.sheetVisible = false
                    openRulesUsing(state, navigation, listOf(payeeId), name)
                },
            ),
    )
    TandemActionButton("Back", TandemActionButtonTone.NEUTRAL, modifier = Modifier.fillMaxWidth()) { payee.mode = "menu" }
}
