@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.navigation.openTransaction
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.core.state.uncategorized
import app.tandemforactual.ui.design.buttons.PrimaryButton
import app.tandemforactual.ui.design.buttons.SecondaryButton
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Lucide
import kotlinx.coroutines.launch

/**
 * New uncategorized transactions as a stack of cards: swipe right or tap the main button to categorize (or take the
 * suggested category) or swipe left to skip; each categorization can be undone from the undo notice. Skipped ones can be revisited at the end.
 */
@Composable
internal fun TransactionReviewScreen(
    state: AppState,
    navigation: AppNavigation,
    mobileSession: TandemMobileSession,
    transactions: List<TransactionItem>,
    month: LiveBudgetMonthSummary?,
) {
    val waiting = remember(transactions) { transactions.uncategorized().filter { it.recordId != null } }
    val seen = remember { HashMap<String, TransactionItem>() }
    val byId: Map<String, TransactionItem> = remember(waiting) { seen.apply { waiting.forEach { put(it.recordId!!, it) } } }
    var queue by remember { mutableStateOf(ReviewQueue(waiting.mapNotNull { it.recordId })) }
    LaunchedEffect(waiting) {
        queue = queue.refresh(waiting.mapNotNull { it.recordId })
    }
    var picking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val current = queue.current?.let(byId::get)
    val suggestion = rememberSuggestion(state, mobileSession, current)
    val categories = month?.groups.orEmpty().flatMap { group -> group.categories.map { it.id to it.name } }
    val categoryName = { id: String -> categories.firstOrNull { it.first == id }?.second }

    fun categorize(categoryId: String) {
        val item = current ?: return
        val id = item.recordId ?: return
        queue = queue.categorize(categoryId)
        scope.launch { mobileSession.setReviewCategory(state, item, categoryId) }
        val name = categoryName(categoryId)
        mobileSession.transactions.showUndoNotice(
            listOfNotNull(item.payee, name).joinToString(" → ").let { "Categorized $it" },
            state.selectedBudgetId,
        ) {
            queue = queue.restore(id)
            mobileSession.setReviewCategory(state, item, null)
        }
    }

    Column(Modifier.fillMaxSize().padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.large)) {
        ReviewHeader(queue.pending.size)
        if (current == null) {
            AllCaughtUp(queue.skipped.size, onRevisit = { queue = queue.revisitSkipped() }, onDone = { navigation.back() })
            return@Column
        }
        val accept = suggestion?.let(categoryName)?.let { "Use $it" } ?: "Categorize"
        ReviewCard(
            current,
            formatCurrency(current.amountMinorUnits, state.budgetDisplaySettings),
            accept,
            footer = { ReviewCardFooter(suggestion?.let(categoryName)) { picking = true } },
            onSkip = { queue = queue.skip() },
            onAccept = { suggestion?.let(::categorize) ?: run { picking = true } },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
            SecondaryButton("Skip for now", Modifier.weight(1f)) { queue = queue.skip() }
            PrimaryButton(accept, modifier = Modifier.weight(1f)) { suggestion?.let(::categorize) ?: run { picking = true } }
        }
        SecondaryButton("Open transaction", Modifier.fillMaxWidth()) { openTransaction(state, navigation, current, isLive = true) }
    }
    ReviewCategoryPicker(picking, categories, onDismiss = { picking = false }) {
        picking = false
        categorize(it)
    }
}

@Composable
private fun rememberSuggestion(
    state: AppState,
    mobileSession: TandemMobileSession,
    item: TransactionItem?,
): String? {
    var suggestion by remember(item?.recordId) { mutableStateOf<String?>(null) }
    LaunchedEffect(item?.recordId) {
        suggestion = item?.payeeId?.let { mobileSession.suggestedReviewCategory(state.selectedBudgetId, it) }
    }
    return suggestion
}

@Composable
private fun ReviewHeader(remaining: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (remaining == 0) "All done" else "$remaining to go",
            Modifier.weight(1f),
            color = tandemPalette.text,
            fontSize = TandemTypography.sectionTitle,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AllCaughtUp(
    skipped: Int,
    onRevisit: () -> Unit,
    onDone: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
        Spacer(Modifier.size(48.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Icon(Lucide.CircleCheck, null, Modifier.size(96.dp).graphicsLayer(), tint = tandemPalette.positive)
        }
        Text(
            "All caught up",
            Modifier.fillMaxWidth(),
            color = tandemPalette.text,
            fontSize = TandemTypography.pageTitle,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.size(48.dp))
        Column(Modifier.widthIn(max = 480.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
            if (skipped > 0) SecondaryButton("Revisit $skipped skipped", Modifier.fillMaxWidth(), onClick = onRevisit)
            PrimaryButton("Done", modifier = Modifier.fillMaxWidth(), onClick = onDone)
        }
    }
}
