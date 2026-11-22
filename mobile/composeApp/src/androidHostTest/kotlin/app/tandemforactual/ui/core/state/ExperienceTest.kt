package app.tandemforactual.ui.core.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExperienceTest {
    private val everything =
        HomeData(uncategorizedCount = 3, hasSchedules = true, hasBudget = true, hasAccounts = true, hasActivity = true)
    private val allGoals = FinancialGoal.entries.toSet()

    private fun HomePlan.modules() = prominent.map { it.module }

    @Test
    fun allFiveGoalsInMinimalLeadWithOrganizingAndStayBounded() {
        val settings = ExperienceSettings().withGoals(allGoals)
        assertEquals(FinancialGoal.ORGANIZE, settings.primaryGoal)
        val plan = planHome(settings, InterfaceMode.MINIMAL, everything)
        assertEquals(listOf(HomeModule.REVIEW_INBOX, HomeModule.CATEGORY_PROGRESS, HomeModule.UPCOMING_BILLS), plan.modules())
        assertTrue(plan.more.any { it is HomeEntry.Setup && it.module == HomeModule.SAVINGS_GOALS })
        assertTrue(plan.more.any { it is HomeEntry.Setup && it.module == HomeModule.DEBT_OVERVIEW })
        assertTrue(plan.more.none { it.module == HomeModule.NET_WORTH })
    }

    @Test
    fun advancedAnalysisShowsEverythingWithTheOverviewFirst() {
        val settings = ExperienceSettings().withGoals(allGoals).copy(primaryGoal = FinancialGoal.ANALYSIS)
        val plan = planHome(settings, InterfaceMode.ADVANCED, everything)
        assertEquals(HomeModule.MONTH_SUMMARY, plan.modules().first())
        assertTrue(HomeModule.NET_WORTH in plan.modules())
        assertTrue(plan.more.isEmpty())
        assertTrue(plan.prominent.takeLastWhile { it is HomeEntry.Setup }.isNotEmpty())
    }

    @Test
    fun debtOnlyAsksForSetupInsteadOfInventingNumbers() {
        val settings = ExperienceSettings().withGoals(setOf(FinancialGoal.DEBT))
        val plan = planHome(settings, InterfaceMode.MINIMAL, everything)
        assertEquals(HomeModule.UPCOMING_BILLS, plan.modules().first())
        assertEquals(HomeEntry.Setup(HomeModule.DEBT_OVERVIEW), plan.more.last())
        val withDebt = planHome(settings, InterfaceMode.MINIMAL, everything.copy(hasDebtAccounts = true))
        assertTrue(HomeEntry.Ready(HomeModule.DEBT_OVERVIEW) in withDebt.prominent)
    }

    @Test
    fun justConnectingKeepsChoicesButShowsTheNeutralHome() {
        val chosen = ExperienceSettings().withGoals(allGoals)
        val deferred = chosen.copy(personalizationDeferred = true)
        assertEquals(allGoals, deferred.selectedGoals)
        val plan = planHome(deferred, InterfaceMode.MINIMAL, everything)
        assertEquals(listOf(HomeModule.ACCOUNTS, HomeModule.BUDGET_SUMMARY, HomeModule.RECENT_ACTIVITY), plan.modules())
        assertTrue(plan.more.isEmpty())
        assertEquals(plan, planHome(ExperienceSettings(), InterfaceMode.MINIMAL, everything))
    }

    @Test
    fun pinsAndHiddenModulesWinAndEmptyModulesStayOut() {
        val settings =
            ExperienceSettings()
                .withGoals(setOf(FinancialGoal.ORGANIZE))
                .copy(pinnedModules = listOf(HomeModule.ACCOUNTS), hiddenModules = setOf(HomeModule.UPCOMING_BILLS))
        val plan = planHome(settings, InterfaceMode.ADVANCED, everything.copy(uncategorizedCount = 0))
        assertEquals(HomeModule.ACCOUNTS, plan.modules().first())
        assertTrue(HomeModule.UPCOMING_BILLS !in plan.modules())
        assertTrue(HomeModule.REVIEW_INBOX !in plan.modules())
    }

    @Test
    fun emphasisFollowsTheGoalsAndSurvivesEncoding() {
        assertEquals(null, recommendedEmphasis(emptySet()))
        assertEquals(FinancialGoal.SPENDING, recommendedEmphasis(setOf(FinancialGoal.SAVINGS, FinancialGoal.SPENDING)))
        val narrowed =
            ExperienceSettings()
                .withGoals(
                    allGoals,
                ).copy(primaryGoal = FinancialGoal.DEBT)
                .withGoals(setOf(FinancialGoal.SAVINGS))
        assertEquals(FinancialGoal.SAVINGS, narrowed.emphasis)
        val saved =
            mapOf(
                "budget-1" to narrowed.copy(pinnedModules = listOf(HomeModule.ACCOUNTS), hiddenModules = setOf(HomeModule.NET_WORTH)),
                "budget-2" to ExperienceSettings(personalizationDeferred = true),
            )
        assertEquals(saved, decodeExperienceSettings(encodeExperienceSettings(saved)))
        assertEquals(emptyMap(), decodeExperienceSettings("not json"))
        assertEquals(
            ExperienceSettings(selectedGoals = setOf(FinancialGoal.DEBT)),
            decodeExperienceSettings("""{"b":{"goals":["DEBT","LOTTERY"],"pinned":["GONE"]}}""").getValue("b"),
        )
    }

    @Test
    fun personalizeHomeListsEverySectionInHomesOrderWithTheRestSwitchedOff() {
        val settings = ExperienceSettings().withGoals(setOf(FinancialGoal.ORGANIZE))
        val layout = homeLayout(settings)
        assertEquals(HomeModule.entries.toSet(), layout.map { it.module }.toSet())
        assertEquals(HomeModule.REVIEW_INBOX, layout.first().module)
        assertEquals(false, layout.first { it.module == HomeModule.NET_WORTH }.shown)
        assertEquals(layout.takeWhile { it.shown }.size, layout.count { it.shown })
    }

    @Test
    fun anArrangedHomeKeepsItsOrderAndVisibilityAndSurvivesEncoding() {
        val start = ExperienceSettings().withGoals(setOf(FinancialGoal.ORGANIZE))
        val layout =
            listOf(
                HomeLayoutItem(HomeModule.DEBT_OVERVIEW, true),
                HomeLayoutItem(HomeModule.RECENT_ACTIVITY, true),
                HomeLayoutItem(HomeModule.ACCOUNTS, false),
                HomeLayoutItem(HomeModule.BUDGET_SUMMARY, true),
            )
        val arranged = start.withHomeLayout(layout)
        assertEquals(setOf(HomeModule.ACCOUNTS), arranged.hiddenModules)
        val plan = planHome(arranged, InterfaceMode.ADVANCED, everything)
        assertEquals(HomeEntry.Setup(HomeModule.DEBT_OVERVIEW), plan.prominent.first())
        assertEquals(HomeModule.RECENT_ACTIVITY, plan.prominent[1].module)
        assertTrue(HomeModule.ACCOUNTS !in plan.modules())
        assertEquals(layout, homeLayout(arranged).take(4))
        assertEquals(HomeModule.entries.size, homeLayout(arranged).size)
        val minimal = planHome(arranged, InterfaceMode.MINIMAL, everything)
        assertEquals(listOf(HomeModule.RECENT_ACTIVITY, HomeModule.BUDGET_SUMMARY), minimal.modules().take(2))
        assertEquals(HomeEntry.Setup(HomeModule.DEBT_OVERVIEW), minimal.more.first())
        val saved = mapOf("b" to arranged)
        assertEquals(saved, decodeExperienceSettings(encodeExperienceSettings(saved)))
        assertEquals(homeLayout(start), homeLayout(arranged.withSuggestedHome()))
    }
}
