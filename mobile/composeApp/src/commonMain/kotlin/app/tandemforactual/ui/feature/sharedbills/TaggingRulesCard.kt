@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sharedbills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.tandemforactual.data.planning.SharedBill
import app.tandemforactual.data.planning.SharedBills
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.createBudgetRule
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.feedback.BodyText
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.theme.TandemSpacing
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Wand
import kotlinx.coroutines.launch

/** Creates the Actual rules that tag each person's payments, once per person and payment text. */
@Composable
internal fun TaggingRulesCard(
    state: AppState,
    session: TandemMobileSession,
    bill: SharedBill,
    report: (Pair<String, InlineMessageKind>) -> Unit,
    onEdit: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    val pending = bill.members.filter { it.matchText.isNotBlank() && !it.ruleCreated }
    val noText = bill.members.filter { it.matchText.isBlank() }.map { it.name }
    SectionCard("Automatic tagging", "Actual rules add #${bill.tag} and the person’s tag as payments arrive") {
        Column(Modifier.padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
            BodyText(
                when {
                    noText.isNotEmpty() -> "Add payment text for ${noText.joinToString(", ")} so their payments can be found."
                    pending.isEmpty() -> "Rules are set up for everyone. Earlier payments can be tagged by adding the tags to their notes."
                    else -> "${pending.size} ${if (pending.size == 1) "person needs" else "people need"} a rule."
                },
            )
            if (pending.isNotEmpty()) {
                TandemActionButton(
                    "Create tagging rules",
                    TandemActionButtonTone.PRIMARY,
                    Modifier.fillMaxWidth(),
                    icon = Lucide.Wand,
                    enabled = !state.readOnlyMode && !working,
                    loading = working,
                ) {
                    working = true
                    scope.launch {
                        val password = state.encryptedPassword.takeIf { it.isNotBlank() }
                        val done =
                            pending.filter {
                                session.budgetEdits.createBudgetRule(
                                    state.selectedBudgetId,
                                    SharedBills.rule(bill, it),
                                    password,
                                )
                            }
                        val members = bill.members.map { member -> if (member in done) member.copy(ruleCreated = true) else member }
                        session.planning.saveSharedBill(state.selectedBudgetId, bill.copy(members = members))
                        working = false
                        report(
                            if (done.size == pending.size) {
                                "Rules created. New payments are tagged as they sync." to InlineMessageKind.SUCCESS
                            } else {
                                "Created ${done.size} of ${pending.size} rules. Check Rules and try again." to InlineMessageKind.WARNING
                            },
                        )
                    }
                }
            }
            TandemActionButton("Edit bill", TandemActionButtonTone.NEUTRAL, Modifier.fillMaxWidth(), icon = Lucide.Pencil, onClick = onEdit)
        }
    }
}
