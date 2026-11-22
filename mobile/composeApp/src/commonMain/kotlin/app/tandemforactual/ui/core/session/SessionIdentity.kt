package app.tandemforactual.ui.core.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

internal fun TandemMobileSession.signedInBudgetCacheOwner(): String? {
    val device = authenticatedDevice ?: return null
    val serverUrl = apiClient.currentServerUrl ?: return null
    return budgetCacheOwner(serverUrl, device.principalId)
}

internal suspend fun TandemMobileSession.cacheOwnerForLocalEdit(): String? {
    val activeOwner = activeBudgetCacheOwner()
    if (activeOwner != null) return activeOwner
    return try {
        preferencesStore?.read()?.get(BUDGET_CACHE_OWNER_KEY)
    } catch (_: Exception) {
        null
    }
}

internal suspend fun TandemMobileSession.ownsBudgetCache(): Boolean {
    if (!apiClient.isAuthenticated) return true
    val device = authenticatedDevice ?: return false
    val serverUrl = apiClient.currentServerUrl ?: return false
    val store = preferencesStore ?: return true
    return try {
        store.read()[BUDGET_CACHE_OWNER_KEY] == budgetCacheOwner(serverUrl, device.principalId)
    } catch (_: Exception) {
        false
    }
}
