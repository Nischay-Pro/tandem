package app.tandemforactual.ui.core.session.budget
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.session.SessionHost
import app.tandemforactual.ui.core.session.budget.BudgetEditQueue
import app.tandemforactual.ui.core.session.budget.BudgetEdits

/** Changes to the budget's accounts, categories, payees, rules, templates and money, sent now or queued. */
internal class BudgetEdits(
    internal val host: SessionHost,
    internal val queue: BudgetEditQueue,
)
