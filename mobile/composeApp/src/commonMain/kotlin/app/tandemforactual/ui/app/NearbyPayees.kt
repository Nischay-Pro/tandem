@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import app.tandemforactual.ui.core.platform.CoarseLocator
import app.tandemforactual.ui.core.platform.rememberCoarseLocator
import app.tandemforactual.ui.core.platform.rememberLocationPermissionRequest
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.SelectorGroup
import app.tandemforactual.ui.core.state.SelectorItem
import app.tandemforactual.ui.core.state.adding
import app.tandemforactual.ui.core.state.decodePayeeLocations
import app.tandemforactual.ui.core.state.encodePayeeLocations
import app.tandemforactual.ui.core.state.nearby
import app.tandemforactual.ui.core.state.payeeLocationsKey
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.BodyText
import app.tandemforactual.ui.design.theme.TandemSpacing
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MapPin

/** Payees paid close to here, shown above the full list. */
internal fun nearbyPayeeGroup(
    values: List<SelectorItem>,
    nearbyPayeeIds: List<String>,
): List<SelectorGroup> {
    val byId = values.associateBy { it.id }
    val nearby = nearbyPayeeIds.mapNotNull { byId[it] }.take(MAX_NEARBY)
    return if (nearby.isEmpty()) emptyList() else listOf(SelectorGroup("Nearby", nearby))
}

/** Payees paid near the current location, read once as the picker opens while payee locations are on. */
@Composable
internal fun rememberNearbyPayeeIds(
    state: AppState,
    mobileSession: TandemMobileSession,
): List<String> {
    val locator = rememberCoarseLocator()
    val budgetId = state.selectedBudgetId
    val ids by produceState(emptyList<String>(), budgetId, state.payeeLocationsEnabled) {
        if (!state.payeeLocationsEnabled || !locator.permitted || budgetId.isBlank()) return@produceState
        val here = locator.current() ?: return@produceState
        value = decodePayeeLocations(mobileSession.preferencesStore?.read()?.get(payeeLocationsKey(budgetId))).nearby(here)
    }
    return ids
}

/** Keeps a rough location for the payee just paid, only while payee locations are on and permitted. */
internal suspend fun recordPayeeLocation(
    state: AppState,
    mobileSession: TandemMobileSession,
    locator: CoarseLocator,
    payeeId: String,
) {
    if (!state.payeeLocationsEnabled || !locator.permitted || payeeId.isBlank()) return
    val store = mobileSession.preferencesStore ?: return
    val here = locator.current() ?: return
    val key = payeeLocationsKey(state.selectedBudgetId)
    store.putValue(key, encodePayeeLocations(decodePayeeLocations(store.read()[key]).adding(payeeId, here)))
}

/** Asked once, the first time the payee picker opens: off unless the user turns it on and grants location. */
@Composable
internal fun NearbyPayeesPrompt(state: AppState) {
    val request =
        rememberLocationPermissionRequest { granted ->
            state.payeeLocationsEnabled = granted
            state.payeeLocationsAsked = true
        }
    if (state.payeeLocationsAsked || state.payeeLocationsEnabled || state.selectedBudgetId.isBlank()) return
    SurfaceCard {
        BodyText(
            "Show payees you’ve paid near here first? Tandem keeps a rough location with each transaction you add, " +
                "only while it’s open, and it stays on this device.",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
            TandemActionButton("Turn on", TandemActionButtonTone.PRIMARY, Modifier.weight(1f), icon = Lucide.MapPin, onClick = request)
            TandemActionButton("Not now", TandemActionButtonTone.NEUTRAL, Modifier.weight(1f)) { state.payeeLocationsAsked = true }
        }
    }
}

private const val MAX_NEARBY = 5
