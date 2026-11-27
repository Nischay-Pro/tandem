@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import app.tandemforactual.watch.QuickAddQueue
import app.tandemforactual.watch.WatchSummary
import app.tandemforactual.wear.data.AmountEntry
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private object Routes {
    const val GLANCE = "glance"
    const val CATEGORY = "category/{id}"
    const val AMOUNT = "add/amount"
    const val CATEGORY_CHOICE = "add/category"
    const val PAYEE = "add/payee"
    const val CONFIRM = "add/confirm"
    const val SENT = "add/sent"

    fun category(id: String) = "category/$id"
}

/** What the person has chosen so far while adding a transaction on the watch. */
class QuickAddDraft {
    var amount by mutableStateOf(AmountEntry())
    var categoryId by mutableStateOf<String?>(null)
    var categoryName by mutableStateOf<String?>(null)
    var payee by mutableStateOf<String?>(null)
    var delivered by mutableStateOf(false)

    fun reset(fractionDigits: Int) {
        amount = AmountEntry(fractionDigits = fractionDigits)
        categoryId = null
        categoryName = null
        payee = null
    }
}

@Composable
fun TandemWearApp(
    summaryFlow: StateFlow<WatchSummary?>,
    queueFlow: StateFlow<QuickAddQueue>,
    phoneFlow: StateFlow<Boolean?>,
    startOnQuickAdd: Boolean,
    onAdd: suspend (amountMinor: Long, categoryId: String?, payee: String?) -> Boolean,
    onRefresh: () -> Unit,
    onInstallOnPhone: () -> Unit,
) {
    val summary by summaryFlow.collectAsState()
    val queue by queueFlow.collectAsState()
    val phoneHasTandem by phoneFlow.collectAsState()
    val navController = rememberSwipeDismissableNavController()
    val draft = remember { QuickAddDraft() }
    val scope = rememberCoroutineScope()
    val digits = summary?.money?.fractionDigits ?: 2

    fun startQuickAdd() {
        draft.reset(digits)
        navController.navigate(Routes.AMOUNT)
    }

    LaunchedEffect(startOnQuickAdd) { if (startOnQuickAdd && summary?.quickAdd == true) startQuickAdd() }

    TandemWearTheme {
        AppScaffold {
            SwipeDismissableNavHost(navController = navController, startDestination = Routes.GLANCE) {
                composable(Routes.GLANCE) {
                    GlancePager(
                        summary = summary,
                        waiting = queue.items.size,
                        phoneHasTandem = phoneHasTandem,
                        onCategory = { navController.navigate(Routes.category(it)) },
                        onAdd = { startQuickAdd() },
                        onRefresh = onRefresh,
                        onInstallOnPhone = onInstallOnPhone,
                    )
                }
                composable(Routes.CATEGORY) { entry ->
                    val id = entry.arguments?.getString("id")
                    CategoryScreen(summary, summary?.categories?.firstOrNull { it.id == id })
                }
                composable(Routes.AMOUNT) {
                    AmountScreen(draft, summary) { navController.navigate(Routes.CATEGORY_CHOICE) }
                }
                composable(Routes.CATEGORY_CHOICE) {
                    CategoryChoiceScreen(summary?.categoryChoices.orEmpty()) { choice ->
                        draft.categoryId = choice?.id
                        draft.categoryName = choice?.name
                        navController.navigate(if (summary?.recentPayees.isNullOrEmpty()) Routes.CONFIRM else Routes.PAYEE)
                    }
                }
                composable(Routes.PAYEE) {
                    PayeeChoiceScreen(summary?.recentPayees.orEmpty()) { payee ->
                        draft.payee = payee
                        navController.navigate(Routes.CONFIRM)
                    }
                }
                composable(Routes.CONFIRM) {
                    ConfirmScreen(draft, summary) {
                        scope.launch {
                            draft.delivered = onAdd(draft.amount.signedMinor, draft.categoryId, draft.payee)
                            navController.navigate(Routes.SENT) { popUpTo(Routes.GLANCE) }
                        }
                    }
                }
                composable(Routes.SENT) {
                    SentScreen(draft.delivered) { navController.popBackStack(Routes.GLANCE, inclusive = false) }
                }
            }
        }
    }
}
