@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.household

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.session.LocalShareText
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.PlanningSyncStatus
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.sheets.TandemChoiceDialog
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
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
        value = mobileSession.loadChangeHistory(state.selectedBudgetId, password)?.deviceNode
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
        Text(
            "Actual decides who can open this budget. Everyone who can sees the same accounts and transactions, and their " +
                "changes sync to you.",
            color = palette.muted,
            fontSize = TandemTypography.compactBody,
        )
        SectionCard(
            "Share this budget",
            when (openId) {
                true -> "Your server uses OpenID, so each person gets their own sign-in"
                false -> "Your server uses one shared password. Switch it to OpenID to give each person their own sign-in."
                null -> null
            },
        ) {
            val steps =
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
        SectionCard("Devices", "Names show in change history. They come from each device and aren’t verified by Actual.") {
            val names = mobileSession.planning.deviceNames
            val node = thisNode
            ListRow(
                node?.let { names[it] } ?: deviceName,
                "This device · Tap to rename",
                leading = RowLeading.Icon(Lucide.Smartphone),
                onClick = { renaming = true }.takeIf { node != null },
            )
            names.filterKeys { it != node }.entries.sortedBy { it.value.lowercase() }.forEach { (_, name) ->
                DividerLine()
                ListRow(name, "Another device", leading = RowLeading.Icon(Lucide.Users), options = ListRowOptions(showChevron = false))
            }
        }
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
    var draft by remember(renaming) { mutableStateOf(thisNode?.let { mobileSession.planning.deviceNames[it] } ?: deviceName) }
    TandemChoiceDialog(visible = renaming, onDismissRequest = { renaming = false }) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 440.dp).padding(horizontal = TandemSpacing.large)) {
            Column(Modifier.padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                TandemModalHeader("Name this device", onClose = { renaming = false }, leadingIcon = Lucide.Smartphone)
                TextFieldCard("Name", draft, { draft = it.take(40) }, TextFieldOptions(placeholder = "Sam’s phone"))
                TandemActionButton("Save", TandemActionButtonTone.PRIMARY, Modifier.fillMaxWidth(), enabled = draft.isNotBlank()) {
                    val node = thisNode
                    renaming = false
                    if (node != null) scope.launch { mobileSession.planning.nameDevice(state.selectedBudgetId, node, draft) }
                }
            }
        }
    }
}

/** An invite with only what's safe to send: the budget's name and the server address, never a password or key. */
internal fun householdInvite(
    budgetName: String,
    serverUrl: String,
): String =
    "Join our budget “${budgetName.ifBlank { "Budget" }}” in Tandem. Install Tandem, enter the server address " +
        "${serverUrl.ifBlank { "our Actual server" }} and sign in with the account our Actual admin made for you."
