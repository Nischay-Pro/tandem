package app.tandemforactual.data.actual

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** What applying templates did: how many categories it filled, or why it filled none. */
data class GoalTemplateOutcome(
    val applied: Int,
    val errors: List<String>,
    val values: List<GoalTemplateValues>,
)

/**
 * Actual 26.11's `goal-template.ts` and `template-notes.ts`: reads each category's templates from its note (or from
 * Actual's automations editor), checks them, and works out every category's amount and goal for a month exactly as
 * "Apply budget templates" does on the desktop.
 */
class GoalTemplateEngine(
    private val db: ActualDatabase,
    private val today: String,
) {
    private val tracking = db.first("SELECT value FROM preferences WHERE id = 'budgetType'")?.get("value") == "tracking"

    internal class Computed(
        val contexts: List<GoalTemplateContext>,
        val errors: List<String>,
        val orphans: List<String>,
    )

    private class NoteTemplates(
        val id: String,
        val name: String,
        val templates: List<GoalTemplate>,
    )

    /** `getCategoriesWithTemplates`: categories whose note has template lines, in the order Actual's query returns them. */
    private fun noteTemplates(): List<NoteTemplates> =
        db
            .query(
                "SELECT c.id AS id, c.name AS name, n.note AS note, c.template_settings AS settings FROM notes n " +
                    "JOIN categories c ON n.id = c.id WHERE c.id = n.id AND c.tombstone = 0 " +
                    "AND (lower(note) LIKE '%#template%' OR lower(note) LIKE '%#goal%')",
            ).filter { source(it["settings"]) != "ui" }
            .map { NoteTemplates(it["id"] as String, it["name"] as String, goalTemplatesFromNote(it["note"] as String)) }
            .filter { it.templates.isNotEmpty() }

    private fun source(settings: Any?): String? =
        (settings as? String)
            ?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
            ?.let { (it["source"] as? JsonPrimitive)?.contentOrNull }

    /** Each category's templates: from its note, or from `goal_def` when Actual's automations editor manages it. */
    fun templates(): Map<String, List<GoalTemplate>> {
        val fromEditor =
            db
                .query("SELECT * FROM categories WHERE tombstone = 0")
                .filter { source(it["template_settings"]) == "ui" }
                .mapNotNull { row -> GoalTemplate.fromGoalDef(row["goal_def"] as String?)?.let { row["id"] as String to it } }
        return noteTemplates().associate { it.id to it.templates } + fromEditor
    }

    /** `checkTemplateNotes`: lines that don't parse and schedules that don't exist, as Actual words them. */
    fun check(): List<String> {
        val schedules = activeSchedules().mapNotNull { it["name"] as String? }.toSet()
        return noteTemplates().flatMap { category ->
            category.templates.mapNotNull { template ->
                when {
                    template.type == "error" && "adjustment" in template.error ->
                        "${category.name}: ${template.line}\nError: ${template.error}"
                    template.type == "error" -> "${category.name}: ${template.line}"
                    template.type == "schedule" && template.name != null && template.name !in schedules ->
                        "${category.name}: Schedule \"${template.name}\" does not exist"
                    else -> null
                }
            }
        }
    }

    private fun activeSchedules() = db.query("SELECT id, name FROM schedules WHERE name NOT NULL AND tombstone = 0")

    private fun category(row: Row) = GoalCategory(row["id"] as String, row["name"] as String, truthy(row["is_income"]))

    /** `getCategories`: visible categories in visible groups, in budget order; income only for a tracking budget. */
    private fun visibleCategories(): List<GoalCategory> {
        val byGroup = db.query("SELECT * FROM categories WHERE tombstone = 0 ORDER BY sort_order, id").groupBy { it["cat_group"] }
        return db
            .query("SELECT id FROM category_groups WHERE tombstone = 0 AND hidden = 0 ORDER BY is_income, sort_order, id")
            .flatMap { byGroup[it["id"]].orEmpty() }
            .filter { !truthy(it["hidden"]) && (tracking || !truthy(it["is_income"])) }
            .map(::category)
    }

    private fun chosenCategories(ids: Collection<String>): List<GoalCategory> =
        db
            .query("SELECT * FROM categories WHERE tombstone = 0 ORDER BY sort_order, id")
            .filter { (it["id"] as String) in ids }
            .map(::category)

    /** What applying templates to [month] would budget, without changing anything. */
    fun preview(
        month: String,
        force: Boolean,
        categoryIds: Collection<String>? = null,
    ): GoalTemplateOutcome = compute(month, force, categoryIds).outcome()

    internal fun Computed.outcome(): GoalTemplateOutcome =
        if (errors.isNotEmpty()) {
            GoalTemplateOutcome(0, errors, emptyList())
        } else {
            contexts.map { it.values() }.let { GoalTemplateOutcome(it.size, emptyList(), it) }
        }

    /** `computeTemplates`: every category's amount for [month]; [force] also refills categories that already have one. */
    internal fun compute(
        month: String,
        force: Boolean,
        categoryIds: Collection<String>? = null,
    ): Computed {
        val budget =
            GoalTemplateBudget(db, month, ActualMonths.fromIsoDate(today), tracking, ActualBudgetReader(db).budgetCells(today, tracking))
        val templates = templates()
        var available = budget.value(month, if (tracking) "total-saved" else "to-budget")
        val contexts = mutableListOf<GoalTemplateContext>()
        val errors = mutableListOf<String>()
        val orphans = mutableListOf<String>()
        for (category in categoryIds?.let(::chosenCategories) ?: visibleCategories()) {
            val list = templates[category.id]
            val budgeted = budget.value(month, "budget-${category.id}")
            if (list == null) {
                orphans += category.id
                continue
            }
            if (budgeted != 0L && !force) continue
            try {
                val context = GoalTemplateContext(category, budget, checked(budget, list), fromLastMonth(budget, category), budgeted)
                if (!context.isGoalOnly) available += budgeted
                available += context.limitExcess
                contexts += context
            } catch (error: GoalTemplateException) {
                errors += "${category.name}: ${error.message}"
            }
        }
        if (errors.isEmpty()) run(contexts, available)
        return Computed(contexts, errors, orphans)
    }

    private fun run(
        contexts: List<GoalTemplateContext>,
        start: Long,
    ) {
        var available = start
        for (priority in contexts.flatMap { it.priorities }.distinct().sorted()) {
            val availableAtStart = available
            contexts.forEach { available -= it.runPriority(priority, available, availableAtStart) }
        }
        var sharing = contexts.filter { it.hasRemainder() }
        while (available > 0 && sharing.isNotEmpty()) {
            val perWeight = available.toDouble() / sharing.sumOf { it.remainderWeight() }
            val before = available
            sharing.forEach { available -= it.runRemainder(available, perWeight) }
            if (available == before) break
            sharing = contexts.filter { it.hasRemainder() }
        }
    }

    private fun fromLastMonth(
        budget: GoalTemplateBudget,
        category: GoalCategory,
    ): Long {
        val last = ActualMonths.add(budget.month, -1)
        val leftover = budget.value(last, "leftover-${category.id}")
        val carryover = budget.flag(last, "carryover-${category.id}")
        return if ((leftover < 0 && !carryover) || category.isIncome || (tracking && !carryover)) 0 else leftover
    }

    /** The checks `CategoryTemplateContext.init` runs before it builds a category's context. */
    private fun checked(
        budget: GoalTemplateBudget,
        templates: List<GoalTemplate>,
    ): List<GoalTemplate> {
        if (templates.any { it.type == "periodic" && it.periodCount < 1 }) {
            throw GoalTemplateException("Template repeat interval must be a whole number greater than 0")
        }
        checkSchedulesAndTargets(budget, templates)
        val incomes = incomeCategories(db)
        templates.filter { it.type == "percentage" }.forEach { template ->
            val name = template.category.lowercase()
            val known =
                name in setOf("all income", "available funds") || incomes.any { it.name.lowercase() == name || it.id == template.category }
            if (!known) throw GoalTemplateException("Category \"${template.category}\" is not found in available income categories")
        }
        return templates
    }

    private fun checkSchedulesAndTargets(
        budget: GoalTemplateBudget,
        templates: List<GoalTemplate>,
    ) {
        val timed = templates.filter { it.type == "schedule" || it.type == "by" }
        if (timed.isEmpty()) return
        val active = activeSchedules()
        val ids = active.map { it["id"] }.toSet()
        val names = active.mapNotNull { (it["name"] as String?)?.trim() }.toSet()
        timed.filter { it.type == "schedule" }.forEach { template ->
            when {
                template.scheduleId != null && template.scheduleId !in ids ->
                    throw GoalTemplateException("Schedule ${template.name ?: template.scheduleId} does not exist")
                template.scheduleId == null && template.name == null -> throw GoalTemplateException(
                    "Schedule template has no scheduleId or name",
                )
                template.scheduleId == null && template.name!!.trim() !in names ->
                    throw GoalTemplateException("Schedule ${template.name.trim()} does not exist")
            }
        }
        val lowest = timed.minOf { it.priority ?: 0 }
        if (timed.any { it.priority != lowest }) {
            throw GoalTemplateException(
                "Schedule and By templates must be the same priority level. " +
                    "Fix by setting all Schedule and By templates to priority level $lowest",
            )
        }
        templates.filter { it.type == "by" || it.type == "spend" }.forEach { template ->
            val passed = GoalMonths.months(template.month, budget.month) < 0
            if (passed && (template.repeat ?: 0) == 0 && template.annual != true) {
                throw GoalTemplateException("Target month has passed, remove or update the target month")
            }
        }
    }
}
