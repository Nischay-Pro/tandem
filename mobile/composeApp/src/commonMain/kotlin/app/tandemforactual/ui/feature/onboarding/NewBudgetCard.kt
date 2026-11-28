@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.tandemforactual.api.client.BudgetCatalogItem
import app.tandemforactual.api.client.restoreBudgetAsNew
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.loadBudgets
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.setup_cancel
import app.tandemforactual.ui.generated.resources.setup_create_budget
import app.tandemforactual.ui.generated.resources.setup_new_budget
import app.tandemforactual.ui.generated.resources.setup_new_budget_detail
import app.tandemforactual.ui.generated.resources.setup_new_budget_failed
import app.tandemforactual.ui.generated.resources.setup_new_budget_name
import app.tandemforactual.ui.generated.resources.setup_new_budget_starts
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Starts an empty budget on the Actual server, with Actual's starter categories, and opens it. */
@Composable
internal fun NewBudgetCard(
    mobileSession: TandemMobileSession,
    onCreated: (BudgetCatalogItem) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    SurfaceCard {
        ListRow(
            stringResource(Res.string.setup_new_budget),
            stringResource(Res.string.setup_new_budget_detail),
            leading = RowLeading.Icon(Lucide.Plus),
            onClick = { open = true },
        )
    }
    NewBudgetSheet(open, mobileSession, onDismiss = { open = false }) {
        open = false
        onCreated(it)
    }
}

@Composable
private fun NewBudgetSheet(
    visible: Boolean,
    mobileSession: TandemMobileSession,
    onDismiss: () -> Unit,
    onCreated: (BudgetCatalogItem) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    TandemTaskSheet(visible = visible, onDismissRequest = { if (!busy) onDismiss() }, onDismissed = {}) {
        SheetCard(
            header = { TandemModalHeader(stringResource(Res.string.setup_new_budget), enabled = !busy, onClose = onDismiss) },
            actions = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    TandemActionButton(
                        stringResource(Res.string.setup_cancel),
                        TandemActionButtonTone.NEUTRAL,
                        Modifier.weight(1f),
                        enabled = !busy,
                        onClick = onDismiss,
                    )
                    TandemActionButton(
                        stringResource(Res.string.setup_create_budget),
                        TandemActionButtonTone.PRIMARY,
                        Modifier.weight(1f),
                        icon = Lucide.Check,
                        enabled = name.isNotBlank() && !busy,
                        loading = busy,
                    ) {
                        busy = true
                        error = null
                        scope.launch {
                            val created = createEmptyBudget(mobileSession, name)
                            busy = false
                            if (created == null) error = getString(Res.string.setup_new_budget_failed) else onCreated(created)
                        }
                    }
                }
            },
        ) {
            Text(
                stringResource(Res.string.setup_new_budget_starts),
                color = tandemPalette.muted,
                fontSize = TandemTypography.body,
                lineHeight = TandemTypography.bodyLineHeight,
            )
            TextFieldCard(
                stringResource(Res.string.setup_new_budget_name),
                name,
                { name = it },
                TextFieldOptions(placeholder = "Household"),
            )
            error?.let { InlineMessage(it, InlineMessageKind.ERROR) }
        }
    }
}

/** Uploads Actual's empty new-budget file under [name]; null when the server couldn't take it. */
internal suspend fun createEmptyBudget(
    mobileSession: TandemMobileSession,
    name: String,
): BudgetCatalogItem? =
    try {
        val restored = mobileSession.apiClient.restoreBudgetAsNew(Res.readBytes(EMPTY_BUDGET), null, name.trim(), null)
        mobileSession.loadBudgets()
        BudgetCatalogItem(restored.budgetId, restored.name, isEncrypted = false)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

internal const val EMPTY_BUDGET = "files/empty-budget.sqlite"
