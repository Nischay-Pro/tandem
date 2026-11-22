package app.tandemforactual.ui.app.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.app.ScreenContext
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.feature.rules.BudgetRulesScreen
import app.tandemforactual.ui.feature.rules.ManagePayeesScreen
import app.tandemforactual.ui.feature.rules.RuleEditorSheet

/** Screens for the rules routes. */
internal val rulesScreens: Map<AppRoute, @Composable (ScreenContext) -> Unit> =
    mapOf(
        AppRoute.MANAGE_PAYEES to
            @Composable { ctx ->
                with(ctx) {
                    ManagePayeesScreen(
                        state,
                        navigation,
                        mobileSession,
                        mobileSession.payeeRecords,
                        mobileSession.categoryRecords,
                        mobileSession.ruleRecords,
                        merchantIconMatcher,
                    )
                }
            },
        AppRoute.BUDGET_RULES to
            @Composable { ctx ->
                with(ctx) {
                    BudgetRulesScreen(
                        mobileSession.ruleRecords,
                        mobileSession.accountRecords,
                        mobileSession.categoryRecords,
                        mobileSession.categoryGroupRecords,
                        mobileSession.payeeRecords,
                        canAddRules = !state.readOnlyMode,
                        onOpenRule = { ruleId -> state.ruleEditorRuleId = ruleId },
                    )
                    RuleEditorSheet(state, mobileSession)
                }
            },
    )
