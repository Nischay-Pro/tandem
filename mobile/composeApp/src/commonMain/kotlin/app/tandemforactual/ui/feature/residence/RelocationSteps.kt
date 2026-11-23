@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.residence

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import app.tandemforactual.data.planning.RelocationStep
import app.tandemforactual.data.regions.Region
import app.tandemforactual.data.regions.global.retirement.RetirementPlans
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import com.composables.icons.lucide.BriefcaseBusiness
import com.composables.icons.lucide.CalendarCheck
import com.composables.icons.lucide.Coins
import com.composables.icons.lucide.Landmark
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus

private data class StepText(
    val icon: ImageVector,
    val title: String,
    val detail: String,
)

/** The checklist for a move to [to]: each plan you hold, plans you could add, tax and currency. Nothing is changed for you. */
@Composable
internal fun RelocationChecklist(
    steps: List<RelocationStep>,
    to: Region,
    onStep: (RelocationStep) -> Unit,
) {
    SectionCard("What to look at", "Nothing here closes or changes an account") {
        steps.forEachIndexed { index, step ->
            if (index > 0) DividerLine()
            val text = stepText(step, to)
            ListRow(
                title = text.title,
                subtitle = text.detail,
                leading = RowLeading.Icon(text.icon),
                options = ListRowOptions(showChevron = step is RelocationStep.ConsiderPlan || step is RelocationStep.NewCurrency),
                onClick = { onStep(step) },
            )
        }
    }
}

private fun planName(id: String) = RetirementPlans.byId(id)?.displayName ?: id

private fun stepText(
    step: RelocationStep,
    to: Region,
): StepText =
    when (step) {
        is RelocationStep.EmployerPlanEnds ->
            StepText(
                Lucide.BriefcaseBusiness,
                planName(step.planId),
                "Run through your employer. Contributions usually stop when that job ends; the balance and its history stay.",
            )
        is RelocationStep.ReviewContributions ->
            StepText(
                Lucide.Landmark,
                planName(step.planId),
                "Still yours. Check whether you can keep paying in while you live in ${to.displayName}.",
            )
        is RelocationStep.ConsiderPlan ->
            StepText(Lucide.Plus, planName(step.planId), "Offered in ${to.displayName}. Add it if you’re eligible.")
        is RelocationStep.SplitTaxYear ->
            StepText(
                Lucide.CalendarCheck,
                "The tax year you move",
                "It usually counts in both ${step.from.displayName} and ${step.to.displayName}, whose tax years may start on " +
                    "different dates. Worth checking with a professional.",
            )
        is RelocationStep.NewCurrency ->
            StepText(
                Lucide.Coins,
                "Accounts in ${step.currency}",
                "Set the currency of accounts you open there, so net worth converts them with dated rates.",
            )
    }
