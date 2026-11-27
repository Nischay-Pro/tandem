@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.enqueueBudgetCategoryDelete
import app.tandemforactual.ui.core.session.budgetActionFeedbackMessage
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveCategorySummary
import app.tandemforactual.ui.core.state.categoryIconFor
import app.tandemforactual.ui.core.state.setCategoryIconChoice
import app.tandemforactual.ui.core.usage.UsageSubject
import app.tandemforactual.ui.core.usage.UsageSummary
import app.tandemforactual.ui.core.usage.isChecking
import app.tandemforactual.ui.core.usage.rememberRecordUsage
import app.tandemforactual.ui.design.buttons.SelectableTile
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.LabelText
import app.tandemforactual.ui.design.sheets.DestructiveConfirmationDialog
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemAlloFamily
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.CircleDot
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.X
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Sheet for choosing where a deleted category's transactions go. */
@Composable
internal fun DeleteCategorySheet(
    visible: Boolean,
    state: AppState,
    mobileSession: TandemMobileSession,
    category: LiveCategorySummary?,
    deleteDestinations: List<LiveCategorySummary>,
    sheets: CategoryDetailSheetState,
) {
    TandemTaskSheet(
        visible = visible,
        onDismissRequest = { if (!mobileSession.isPerformingBudgetAction) sheets.showDeleteDialog = false },
        onDismissed = {},
    ) {
        if (category != null) {
            SurfaceCard(
                Modifier
                    .padding(horizontal = 16.dp)
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .heightIn(max = 720.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(TandemSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                ) {
                    DeleteCategoryHeader(category, mobileSession) { sheets.showDeleteDialog = false }
                    LabelText("Move transactions to")
                    Column(
                        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                    ) {
                        val destinations: List<Pair<String?, String>> =
                            listOf(null to "Leave unassigned") + deleteDestinations.map { it.id to it.name }
                        destinations.forEach { (destinationId, destinationName) ->
                            DeleteDestinationTile(state, destinationId, destinationName, sheets)
                        }
                        budgetActionFeedbackMessage(mobileSession)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        TandemActionButton(
                            "Cancel",
                            TandemActionButtonTone.NEUTRAL,
                            modifier = Modifier.weight(1f),
                            enabled = !mobileSession.isPerformingBudgetAction,
                        ) { sheets.showDeleteDialog = false }
                        TandemActionButton(
                            "Continue",
                            TandemActionButtonTone.PRIMARY,
                            modifier = Modifier.weight(1f),
                            enabled = !state.readOnlyMode,
                        ) {
                            sheets.confirmCategoryDelete = true
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeleteCategoryHeader(
    category: LiveCategorySummary,
    mobileSession: TandemMobileSession,
    onClose: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(tandemPalette.negative.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Lucide.Trash2, contentDescription = null, modifier = Modifier.size(20.dp), tint = tandemPalette.negative)
        }
        Column(Modifier.weight(1f)) {
            Text(
                "Delete category",
                color = tandemPalette.text,
                fontSize = TandemTypography.sectionTitle,
                fontFamily = tandemAlloFamily(),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                category.name,
                color = tandemPalette.muted,
                fontSize = TandemTypography.caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TandemIconButton(
            Lucide.X,
            "Close",
            sizeDp = 40,
            iconSizeDp = 18,
            containerColor = tandemPalette.raised,
            enabled = !mobileSession.isPerformingBudgetAction,
        ) { onClose() }
    }
}

@Composable
private fun DeleteDestinationTile(
    state: AppState,
    destinationId: String?,
    destinationName: String,
    sheets: CategoryDetailSheetState,
) {
    val selected = sheets.deleteDestinationId == destinationId
    SelectableTile(
        selected = selected,
        contentDescription = "Move to $destinationName",
        onSelect = { sheets.deleteDestinationId = destinationId },
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(TandemShapes.control),
        selectedBackground = tandemPalette.accentSurface,
        unselectedBackground = tandemPalette.surface,
        selectedBorder = tandemPalette.accent,
        unselectedBorder = tandemPalette.border,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
        ) {
            Icon(
                if (destinationId ==
                    null
                ) {
                    Lucide.CircleDot
                } else {
                    state.categoryIconFor(destinationId, destinationName)
                },
                contentDescription = null,
                modifier = Modifier.size(21.dp),
                tint = tandemPalette.accent,
            )
            Text(
                destinationName,
                modifier = Modifier.weight(1f),
                color = tandemPalette.text,
                fontSize = TandemTypography.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (selected) {
                Icon(
                    Lucide.Check,
                    contentDescription = "Selected",
                    modifier = Modifier.size(18.dp),
                    tint = tandemPalette.accent,
                )
            }
        }
    }
}

/** Final confirmation before a category is deleted. */
@Composable
internal fun DeleteCategoryConfirmation(
    state: AppState,
    navigation: AppNavigation,
    mobileSession: TandemMobileSession,
    category: LiveCategorySummary,
    deleteDestinations: List<LiveCategorySummary>,
    sheets: CategoryDetailSheetState,
    scope: CoroutineScope,
) {
    val usage = rememberRecordUsage(mobileSession, state.selectedBudgetId, UsageSubject.CATEGORY, category.id)
    DestructiveConfirmationDialog(
        title = "Delete ${category.name}?",
        message =
            "Transactions will be moved to ${sheets.deleteDestinationId?.let { id ->
                deleteDestinations.firstOrNull { it.id == id }?.name
            } ?: "Unassigned"}.",
        confirmLabel = "Delete",
        dismissLabel = "Keep category",
        enabled = !state.readOnlyMode && !usage.isChecking,
        content = { UsageSummary(usage, UsageSubject.CATEGORY) },
        onDismissRequest = { sheets.confirmCategoryDelete = false },
        onConfirm = {
            scope.launch {
                val deleted =
                    mobileSession.budgetQueue.enqueueBudgetCategoryDelete(
                        state.selectedBudgetId,
                        category.id,
                        sheets.deleteDestinationId,
                        state.encryptedPassword.takeIf { it.isNotBlank() },
                    )
                sheets.confirmCategoryDelete = false
                if (deleted) {
                    state.setCategoryIconChoice(category.id, "Automatic")
                    sheets.showDeleteDialog = false
                    navigation.back()
                }
            }
        },
    )
}
