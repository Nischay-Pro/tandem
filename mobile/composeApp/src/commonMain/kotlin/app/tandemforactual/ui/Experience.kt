package app.tandemforactual.ui

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Where Tandem-only planning records stand with extension sync. */
enum class PlanningSyncStatus { OFF, SYNCED, WAITING, NEEDS_RECOVERY_KEY }

internal enum class FinancialGoal { ORGANIZE, SPENDING, DEBT, SAVINGS, ANALYSIS }

internal enum class InterfaceMode { MINIMAL, ADVANCED }

/** What a person wants help with in one budget. Kept on this device so one person's priorities never reach a shared budget. */
internal data class ExperienceSettings(
    val selectedGoals: Set<FinancialGoal> = emptySet(),
    val primaryGoal: FinancialGoal? = null,
    val personalizationDeferred: Boolean = false,
    val pinnedModules: List<HomeModule> = emptyList(),
    val hiddenModules: Set<HomeModule> = emptySet(),
) {
    /** The goal that orders Home: the chosen emphasis, or the only goal picked. */
    val emphasis: FinancialGoal? get() = primaryGoal?.takeIf { it in selectedGoals } ?: selectedGoals.singleOrNull()

    fun withGoals(goals: Set<FinancialGoal>): ExperienceSettings =
        copy(
            selectedGoals = goals,
            primaryGoal = primaryGoal?.takeIf { it in goals } ?: recommendedEmphasis(goals),
            personalizationDeferred = false,
        )
}

/** Stay organized is neutral and useful before any setup, so it leads whenever it was picked. */
internal fun recommendedEmphasis(goals: Set<FinancialGoal>): FinancialGoal? =
    if (FinancialGoal.ORGANIZE in goals) FinancialGoal.ORGANIZE else FinancialGoal.entries.firstOrNull { it in goals }

/** Home modules, in the order each goal recommends them. A module serves every goal it is tagged with. */
internal enum class HomeModule(
    val goals: Set<FinancialGoal>,
    val advancedOnly: Boolean = false,
    /** Modules that need details Actual doesn't hold show a skippable setup prompt until they are set up. */
    val needsSetup: Boolean = false,
) {
    REVIEW_INBOX(setOf(FinancialGoal.ORGANIZE)),
    CATEGORY_PROGRESS(setOf(FinancialGoal.SPENDING)),
    UPCOMING_BILLS(setOf(FinancialGoal.ORGANIZE, FinancialGoal.DEBT)),
    DEBT_OVERVIEW(setOf(FinancialGoal.DEBT), needsSetup = true),
    SAVINGS_GOALS(setOf(FinancialGoal.SAVINGS), needsSetup = true),
    MONTH_SUMMARY(setOf(FinancialGoal.ANALYSIS, FinancialGoal.SPENDING)),
    ACCOUNTS(setOf(FinancialGoal.ORGANIZE, FinancialGoal.ANALYSIS)),
    BUDGET_SUMMARY(emptySet()),
    RECENT_ACTIVITY(setOf(FinancialGoal.ORGANIZE)),
    NET_WORTH(setOf(FinancialGoal.ANALYSIS), advancedOnly = true),
}

/** What the device knows right now, so Home only offers modules that can show something real. */
internal data class HomeData(
    val uncategorizedCount: Int = 0,
    val hasSchedules: Boolean = false,
    val hasBudget: Boolean = false,
    val hasAccounts: Boolean = false,
    val hasActivity: Boolean = false,
    val hasDebtAccounts: Boolean = false,
    val savingsGoalCount: Int = 0,
)

internal sealed interface HomeEntry {
    val module: HomeModule

    data class Ready(
        override val module: HomeModule,
    ) : HomeEntry

    data class Setup(
        override val module: HomeModule,
    ) : HomeEntry
}

internal data class HomePlan(
    val prominent: List<HomeEntry>,
    val more: List<HomeEntry>,
)

private val NEUTRAL_HOME = listOf(HomeModule.ACCOUNTS, HomeModule.BUDGET_SUMMARY, HomeModule.RECENT_ACTIVITY)

internal const val MINIMAL_PROMINENT_MODULES = 3

private fun HomeModule.ready(data: HomeData): Boolean =
    when (this) {
        HomeModule.REVIEW_INBOX -> data.uncategorizedCount > 0
        HomeModule.CATEGORY_PROGRESS, HomeModule.BUDGET_SUMMARY, HomeModule.MONTH_SUMMARY -> data.hasBudget
        HomeModule.UPCOMING_BILLS -> data.hasSchedules
        HomeModule.DEBT_OVERVIEW -> data.hasDebtAccounts
        HomeModule.SAVINGS_GOALS -> data.savingsGoalCount > 0
        HomeModule.ACCOUNTS, HomeModule.NET_WORTH -> data.hasAccounts
        HomeModule.RECENT_ACTIVITY -> data.hasActivity
    }

/**
 * Orders Home deterministically: pinned modules, then the emphasis goal's first module, then the first module of each
 * other chosen goal, then the rest. Modules without data become setup prompts when they need details, and are left out
 * when they would only be empty. Minimal shows at most three prominently.
 */
internal fun planHome(
    settings: ExperienceSettings,
    mode: InterfaceMode,
    data: HomeData,
): HomePlan {
    val allowed = HomeModule.entries.filter { (mode == InterfaceMode.ADVANCED || !it.advancedOnly) && it !in settings.hiddenModules }
    val goals = if (settings.personalizationDeferred) emptySet() else settings.selectedGoals
    val emphasis = if (settings.personalizationDeferred) null else settings.emphasis
    val ordered = linkedSetOf<HomeModule>()
    ordered += settings.pinnedModules.filter { it in allowed }
    if (goals.isEmpty()) {
        ordered += NEUTRAL_HOME.filter { it in allowed }
    } else {
        val forGoal = { goal: FinancialGoal -> allowed.filter { goal in it.goals } }
        val sequence = listOfNotNull(emphasis) + FinancialGoal.entries.filter { it in goals && it != emphasis }
        emphasis?.let { ordered += forGoal(it).take(1) }
        sequence.filter { it != emphasis }.forEach { goal -> forGoal(goal).firstOrNull { it !in ordered }?.let { ordered += it } }
        sequence.forEach { goal -> ordered += forGoal(goal) }
        ordered += NEUTRAL_HOME.filter { it in allowed }
    }
    val entries =
        ordered.mapNotNull { module ->
            when {
                module.ready(data) -> HomeEntry.Ready(module)
                module.needsSetup && module.goals.any { it in goals } -> HomeEntry.Setup(module)
                else -> null
            }
        }
    val ready = entries.filterIsInstance<HomeEntry.Ready>()
    val setup = entries.filterIsInstance<HomeEntry.Setup>()
    val sorted = ready + setup
    return if (mode == InterfaceMode.MINIMAL) {
        HomePlan(ready.take(MINIMAL_PROMINENT_MODULES), ready.drop(MINIMAL_PROMINENT_MODULES) + setup)
    } else {
        HomePlan(sorted, emptyList())
    }
}

/** Per-budget settings, encoded as one preference value. Unknown goals or modules from other versions are skipped. */
internal fun decodeExperienceSettings(json: String): Map<String, ExperienceSettings> =
    runCatching {
        Json.parseToJsonElement(json).jsonObject.mapValues { (_, value) ->
            val body = value.jsonObject

            fun names(key: String) = (body[key] as? JsonArray).orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }

            fun text(key: String) = (body[key] as? JsonPrimitive)?.contentOrNull
            ExperienceSettings(
                selectedGoals = names("goals").mapNotNull { name -> FinancialGoal.entries.firstOrNull { it.name == name } }.toSet(),
                primaryGoal = FinancialGoal.entries.firstOrNull { it.name == text("primary") },
                personalizationDeferred = (body["deferred"] as? JsonPrimitive)?.booleanOrNull ?: false,
                pinnedModules = names("pinned").mapNotNull { name -> HomeModule.entries.firstOrNull { it.name == name } },
                hiddenModules = names("hidden").mapNotNull { name -> HomeModule.entries.firstOrNull { it.name == name } }.toSet(),
            )
        }
    }.getOrDefault(emptyMap())

internal fun encodeExperienceSettings(settings: Map<String, ExperienceSettings>): String =
    JsonObject(
        settings.mapValues { (_, value) ->
            JsonObject(
                buildMap {
                    put("goals", JsonArray(value.selectedGoals.sortedBy { it.ordinal }.map { JsonPrimitive(it.name) }))
                    value.primaryGoal?.let { put("primary", JsonPrimitive(it.name)) }
                    put("deferred", JsonPrimitive(value.personalizationDeferred))
                    put("pinned", JsonArray(value.pinnedModules.map { JsonPrimitive(it.name) }))
                    put("hidden", JsonArray(value.hiddenModules.sortedBy { it.ordinal }.map { JsonPrimitive(it.name) }))
                },
            )
        },
    ).toString()
