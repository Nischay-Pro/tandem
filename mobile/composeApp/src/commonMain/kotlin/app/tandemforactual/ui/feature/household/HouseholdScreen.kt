@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.household
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.tandemforactual.ui.core.session.LocalShareText
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.loadActualLoginMethods
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.PlanningSyncStatus
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.cards.IntroCard
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Share2
import com.composables.icons.lucide.Smartphone
import com.composables.icons.lucide.Users
import kotlinx.coroutines.launch

/**
 * Sharing a budget with the people you live with. Actual decides who can open a budget; Tandem explains the steps,
 * names the devices that make changes, and keeps chosen planning details private to one device.
 */
@Composable
internal fun HouseholdScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    deviceName: String,
) {
    val scope = rememberCoroutineScope()
    val share = LocalShareText.current
    val palette = tandemPalette
    val password = state.encryptedPassword.takeIf { it.isNotBlank() }
    LaunchedEffect(
        state.serverUrl,
    ) { if (state.serverUrl.isNotBlank()) runCatching { mobileSession.loadActualLoginMethods(state.serverUrl) } }
    val thisNode by produceState<String?>(null, state.selectedBudgetId) {
        value = mobileSession.insights.loadChangeHistory(state.selectedBudgetId, password)?.deviceNode
    }
    LaunchedEffect(thisNode) {
        val node = thisNode ?: return@LaunchedEffect
        if (node !in mobileSession.planning.deviceNames) mobileSession.planning.nameDevice(state.selectedBudgetId, node, deviceName)
    }
    var renaming by remember { mutableStateOf(false) }
    val openId =
        mobileSession.actualLoginMethods
            ?.availableLoginMethods
            ?.any { it.method == "openid" && it.active }
    PageColumn(verticalSpacing = TandemSpacing.large) {
        IntroCard(
            "Actual decides who can open this budget. Everyone who can sees the same accounts and transactions, and their " +
                "changes sync to you.",
        )
        ShareBudgetSection(state, householdShareSteps(state, mobileSession, openId), openId, share)
        DevicesSection(mobileSession.planning.deviceNames, thisNode, deviceName) { renaming = true }
        SharedAndPrivateSection(mobileSession)
    }
    RenameDeviceDialog(
        visible = renaming,
        initialName = thisNode?.let { mobileSession.planning.deviceNames[it] } ?: deviceName,
        onDismiss = { renaming = false },
    ) { draft ->
        val node = thisNode
        renaming = false
        if (node != null) scope.launch { mobileSession.planning.nameDevice(state.selectedBudgetId, node, draft) }
    }
}

/** The steps to share this budget with someone, depending on how the server signs people in. */
private fun householdShareSteps(
    state: AppState,
    mobileSession: TandemMobileSession,
    openId: Boolean?,
): List<Pair<String, String>> =
    buildList {
        if (openId != true) {
            add(
                "Turn on OpenID in Actual" to
                    "An admin sets up OpenID sign-in in Actual’s settings. Actual shares budgets per person only with OpenID.",
            )
        }
        add("Add them in Actual" to "In Actual’s settings, open the user directory and add the person.")
        add("Give them this budget" to "Under User access for ${state.selectedBudget.ifBlank { "this budget" }}, add them.")
        add(
            "They sign in with Tandem" to
                "They enter ${state.serverUrl.ifBlank {
                    "your server address"
                }} and sign in. The budget then shows up in their list.",
        )
        if (state.selectedBudgetEncrypted) {
            add(
                "Share the encryption password" to
                    "This budget is encrypted. Tell them its password in person, never in the invite.",
            )
        }
        if (mobileSession.planning.syncStatus != PlanningSyncStatus.OFF) {
            add(
                "Share planning details" to
                    "Shared goals and debt details reach their device once it has the planning recovery key from Settings.",
            )
        }
    }

/** How to share the budget, step by step, with a button to send an invite. */
@Composable
private fun ShareBudgetSection(
    state: AppState,
    steps: List<Pair<String, String>>,
    openId: Boolean?,
    share: ((String) -> Unit)?,
) {
    SectionCard(
        "Share this budget",
        when (openId) {
            true -> "Your server uses OpenID, so each person gets their own sign-in"
            false -> "Your server uses one shared password. Switch it to OpenID to give each person their own sign-in."
            null -> null
        },
    ) {
        steps.forEachIndexed { index, (title, detail) ->
            if (index > 0) DividerLine()
            ListRow(
                title,
                detail,
                leading = RowLeading.Letter("${index + 1}"),
                options = ListRowOptions(showChevron = false, subtitleMaxLines = 4),
            )
        }
        if (share != null) {
            TandemActionButton(
                "Share an invite",
                TandemActionButtonTone.PRIMARY,
                Modifier.fillMaxWidth().padding(TandemSpacing.medium),
                icon = Lucide.Share2,
            ) { share(householdInvite(state.selectedBudget, state.serverUrl)) }
        }
    }
}

/** This device and the other devices that have changed the budget. */
@Composable
private fun DevicesSection(
    names: Map<String, String>,
    thisNode: String?,
    deviceName: String,
    onRename: () -> Unit,
) {
    SectionCard("Devices", "Names show in change history. They come from each device and aren’t verified by Actual.") {
        val node = thisNode
        ListRow(
            node?.let { names[it] } ?: deviceName,
            "This device · Tap to rename",
            leading = RowLeading.Icon(Lucide.Smartphone),
            onClick = onRename.takeIf { node != null },
        )
        names.filterKeys { it != node }.entries.sortedBy { it.value.lowercase() }.forEach { (_, name) ->
            DividerLine()
            ListRow(name, "Another device", leading = RowLeading.Icon(Lucide.Users), options = ListRowOptions(showChevron = false))
        }
    }
}

/** How many goals and debts are shared with the household and how many stay private. */
@Composable
private fun SharedAndPrivateSection(mobileSession: TandemMobileSession) {
    val goals = mobileSession.planning.savingsGoals
    val debts = mobileSession.planning.debtTerms
    val privateCount = goals.count { !it.shared } + debts.count { !it.shared }
    val sharedCount = goals.size + debts.size - privateCount
    SectionCard("Shared and private", "Savings goals and debt details") {
        ListRow(
            "$sharedCount shared",
            if (mobileSession.planning.syncStatus == PlanningSyncStatus.OFF) {
                "Stay on this device until your server runs Tandem’s extension service"
            } else {
                "Reach every device on this budget"
            },
            leading = RowLeading.Icon(Lucide.Users),
            options = ListRowOptions(showChevron = false),
        )
        DividerLine()
        ListRow(
            "$privateCount private",
            "Stay on this device. Turn on Keep private when you edit a goal or debt.",
            leading = RowLeading.Icon(Lucide.Lock),
            options = ListRowOptions(showChevron = false),
        )
    }
}

/** An invite with only what's safe to send: the budget's name and the server address, never a password or key. */
internal fun householdInvite(
    budgetName: String,
    serverUrl: String,
): String =
    "Join our budget “${budgetName.ifBlank { "Budget" }}” in Tandem. Install Tandem, enter the server address " +
        "${serverUrl.ifBlank { "our Actual server" }} and sign in with the account our Actual admin made for you."
