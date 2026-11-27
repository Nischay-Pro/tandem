package app.tandemforactual.ui.app.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.app.AppMenuItem
import app.tandemforactual.ui.app.ListDetailPanes
import app.tandemforactual.ui.app.MenuEntry
import app.tandemforactual.ui.app.PaneContent
import app.tandemforactual.ui.app.RouteListDetailPanes
import app.tandemforactual.ui.app.ScreenContext
import app.tandemforactual.ui.core.format.formatInteger
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.design.theme.TandemBackHandler
import app.tandemforactual.ui.feature.activity.ActivitySearchScreen
import app.tandemforactual.ui.feature.rules.BudgetRulesScreen
import app.tandemforactual.ui.feature.rules.ManagePayeesScreen
import app.tandemforactual.ui.feature.rules.RuleEditorPane
import app.tandemforactual.ui.feature.rules.RuleEditorSheet
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Store
import com.composables.icons.lucide.Wand

/** Screens for the rules routes. */
internal val rulesScreens: Map<AppRoute, @Composable (ScreenContext) -> Unit> =
    mapOf(
        AppRoute.MANAGE_PAYEES to
            @Composable { ctx ->
                with(ctx) {
                    RouteListDetailPanes(
                        ctx,
                        paneRoutes = setOf(AppRoute.ACTIVITY_SEARCH),
                        emptyTitle = "Choose a payee",
                        emptyMessage = "Its transactions appear here.",
                        list = { listNavigation ->
                            ManagePayeesScreen(
                                state,
                                listNavigation,
                                mobileSession,
                                mobileSession.payeeRecords,
                                mobileSession.categoryRecords,
                                mobileSession.ruleRecords,
                                merchantIconMatcher,
                            )
                        },
                        detail = { _, paneNavigation ->
                            ActivitySearchScreen(
                                state,
                                mobileSession,
                                paneNavigation,
                                merchantIconMatcher,
                                onActivitySelectionTotalChanged,
                                autoFocusSearch = false,
                            )
                        },
                    )
                }
            },
        AppRoute.BUDGET_RULES to
            @Composable { ctx ->
                with(ctx) {
                    DisposableEffect(Unit) { onDispose { state.rulesReferenceFilter = null } }
                    ListDetailPanes(
                        detailKey = state.ruleEditorRuleId,
                        emptyTitle = "Choose a rule",
                        emptyMessage = "Its conditions and actions appear here.",
                        list = { detailInPane ->
                            BudgetRulesScreen(
                                mobileSession.ruleRecords,
                                mobileSession.accountRecords,
                                mobileSession.categoryRecords,
                                mobileSession.categoryGroupRecords,
                                mobileSession.payeeRecords,
                                canAddRules = !state.readOnlyMode,
                                onOpenRule = { ruleId -> state.ruleEditorRuleId = ruleId },
                                selectedRuleId = state.ruleEditorRuleId.takeIf { detailInPane },
                                referenceFilter = state.rulesReferenceFilter,
                                onClearReferenceFilter = { state.rulesReferenceFilter = null },
                            )
                            if (detailInPane) {
                                TandemBackHandler(
                                    enabled = state.ruleEditorRuleId != null,
                                    onBack = { state.ruleEditorRuleId = null },
                                    onBackProgress = { _, _ -> },
                                )
                            } else {
                                RuleEditorSheet(state, mobileSession)
                            }
                        },
                        detail = { PaneContent { RuleEditorPane(state, mobileSession) } },
                    )
                }
            },
    )

/** This feature's entries in the full-screen menu. */
internal val rulesMenu: List<MenuEntry> =
    listOf(
        MenuEntry(90) { ctx ->
            with(ctx) {
                AppMenuItem(
                    Lucide.Wand,
                    "Rules",
                    ruleCount.takeIf { it > 0 }?.let { "${formatInteger(it)} rules" },
                ) { open(AppRoute.BUDGET_RULES) }
            }
        },
        MenuEntry(100) { ctx ->
            with(ctx) {
                AppMenuItem(
                    Lucide.Store,
                    "Payees",
                    payeeCount.takeIf { it > 0 }?.let { "${formatInteger(it)} payees" },
                ) { open(AppRoute.MANAGE_PAYEES) }
            }
        },
    )
