package app.tandemforactual.ui.core.session.planning

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.planning.AccountCurrency
import app.tandemforactual.data.planning.CustomReport
import app.tandemforactual.data.planning.DebtTerms
import app.tandemforactual.data.planning.DeviceName
import app.tandemforactual.data.planning.ExchangeRate
import app.tandemforactual.data.planning.PlanningCipher
import app.tandemforactual.data.planning.PlanningKeyMismatchException
import app.tandemforactual.data.planning.PlanningSettings
import app.tandemforactual.data.planning.PlanningStore
import app.tandemforactual.data.planning.PlanningSync
import app.tandemforactual.data.planning.Reimbursement
import app.tandemforactual.data.planning.RetirementProfile
import app.tandemforactual.data.planning.SavedSearch
import app.tandemforactual.data.planning.SavingsGoal
import app.tandemforactual.data.planning.SharedBill
import app.tandemforactual.ui.core.session.PlanningKeyring
import app.tandemforactual.ui.core.state.PlanningSyncStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Planning records Actual doesn't hold, kept encrypted on the device and optionally shared through extension sync. */
internal class PlanningController(
    private val apiClient: TandemApiClient,
    private val database: () -> TandemDatabase?,
    private val nowEpochMillis: () -> Long,
) {
    var keyring = PlanningKeyring(null)
    var savingsGoals by mutableStateOf<List<SavingsGoal>>(emptyList())
        private set
    var debtTerms by mutableStateOf<List<DebtTerms>>(emptyList())
        private set
    var retirementProfiles by mutableStateOf<List<RetirementProfile>>(emptyList())
    var reimbursements by mutableStateOf<List<Reimbursement>>(emptyList())
    var sharedBills by mutableStateOf<List<SharedBill>>(emptyList())
    var settings by mutableStateOf(PlanningSettings())
    var accountCurrencies by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var exchangeRates by mutableStateOf<List<ExchangeRate>>(emptyList())
        private set
    var deviceNames by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    suspend fun saveAccountCurrency(
        budgetId: String,
        accountId: String,
        currency: String,
    ) {
        planningStore(budgetId)?.save(AccountCurrency(accountId, currency))
    }

    suspend fun saveExchangeRate(
        budgetId: String,
        rate: ExchangeRate,
    ) {
        planningStore(budgetId)?.save(rate)
    }

    suspend fun deleteExchangeRate(
        budgetId: String,
        rate: ExchangeRate,
    ) {
        planningStore(budgetId)?.deleteExchangeRate(rate)
    }

    /** Names this device's Actual node, so change history on every device can say which device made an edit. */
    suspend fun nameDevice(
        budgetId: String,
        node: String,
        name: String,
    ) {
        if (node.isBlank() || name.isBlank()) return
        planningStore(budgetId)?.save(DeviceName(node, name.trim().take(40)))
    }

    var customReports by mutableStateOf<List<CustomReport>>(emptyList())
        private set
    var savedSearches by mutableStateOf<List<SavedSearch>>(emptyList())
        private set

    private fun planningStore(budgetId: String): PlanningStore? =
        database()?.let { PlanningStore(it.planningRecordDao(), budgetId, { keyring.cipherFor(budgetId) }, nowEpochMillis) }

    var syncStatus by mutableStateOf(PlanningSyncStatus.OFF)
        private set

    /**
     * Shares planning records through extension sync when the server runs it. It never blocks budget sync: any failure
     * leaves the records on this device to try again next time.
     */
    suspend fun sync(budgetId: String) {
        val dao = database()?.planningRecordDao() ?: return
        val transport = apiClient.planningTransport(budgetId)
        if (transport == null) {
            syncStatus = PlanningSyncStatus.OFF
            return
        }
        syncStatus =
            try {
                withContext(Dispatchers.Default) {
                    val keyring = keyring
                    val sync = PlanningSync(dao, budgetId, transport)
                    val local = keyring.key(budgetId)
                    val shared = sync.agreeOnKey(local, apiClient.budgetSecret(budgetId) ?: keyring.recoveryKey(budgetId))
                    if (!shared.contentEquals(local)) {
                        keyring.replaceKey(budgetId, shared)
                        sync.rekey(PlanningCipher(local), PlanningCipher(shared))
                    }
                    val result = sync.sync(PlanningCipher(shared), keyring.cursor(budgetId))
                    keyring.setCursor(budgetId, result.cursor)
                }
                PlanningSyncStatus.SYNCED
            } catch (error: CancellationException) {
                throw error
            } catch (_: PlanningKeyMismatchException) {
                PlanningSyncStatus.NEEDS_RECOVERY_KEY
            } catch (_: Exception) {
                PlanningSyncStatus.WAITING
            }
    }

    /** Uses the recovery key from another device, then tries planning sync again. */
    suspend fun useRecoveryKey(
        budgetId: String,
        key: ByteArray,
    ) {
        keyring.setRecoveryKey(budgetId, key)
        sync(budgetId)
    }

    suspend fun saveSharedBill(
        budgetId: String,
        bill: SharedBill,
    ) {
        planningStore(budgetId)?.save(bill)
    }

    suspend fun deleteSharedBill(
        budgetId: String,
        id: String,
    ) {
        planningStore(budgetId)?.deleteSharedBill(id)
    }

    suspend fun saveReimbursement(
        budgetId: String,
        reimbursement: Reimbursement,
    ) {
        planningStore(budgetId)?.save(reimbursement)
    }

    suspend fun deleteReimbursement(
        budgetId: String,
        transactionId: String,
    ) {
        planningStore(budgetId)?.deleteReimbursement(transactionId)
    }

    suspend fun saveRetirementProfile(
        budgetId: String,
        profile: RetirementProfile,
    ) {
        planningStore(budgetId)?.save(profile)
    }

    /** Saves the wizard's answers in one go: the chosen countries and the plans kept, removing plans no longer picked. */
    suspend fun saveRetirementSetup(
        budgetId: String,
        regions: List<String>,
        profiles: List<RetirementProfile>,
    ) {
        val store = planningStore(budgetId) ?: return
        store.save(settings.copy(regions = regions))
        retirementProfiles.filter { old -> profiles.none { it.planId == old.planId } }.forEach { store.deleteRetirementProfile(it.planId) }
        profiles.forEach { store.save(it) }
    }

    /** Hides budget checkup findings the person has marked as fine, keyed `KIND:id`. */
    suspend fun ignoreChecks(
        budgetId: String,
        keys: List<String>,
    ) {
        planningStore(budgetId)?.save(settings.copy(ignoredChecks = (settings.ignoredChecks + keys).distinct()))
    }

    suspend fun saveSavingsGoal(
        budgetId: String,
        goal: SavingsGoal,
    ) {
        planningStore(budgetId)?.save(goal)
    }

    suspend fun deleteSavingsGoal(
        budgetId: String,
        goalId: String,
    ) {
        planningStore(budgetId)?.delete(goalId, SavingsGoal.TYPE)
    }

    suspend fun saveDebtTerms(
        budgetId: String,
        terms: DebtTerms,
    ) {
        planningStore(budgetId)?.save(terms)
    }

    suspend fun saveCustomReport(
        budgetId: String,
        report: CustomReport,
    ) {
        planningStore(budgetId)?.save(report)
    }

    suspend fun deleteCustomReport(
        budgetId: String,
        id: String,
    ) {
        planningStore(budgetId)?.deleteCustomReport(id)
    }

    suspend fun saveSearch(
        budgetId: String,
        search: SavedSearch,
    ): Boolean = planningStore(budgetId)?.save(search) != null

    suspend fun deleteSavedSearch(
        budgetId: String,
        id: String,
    ) {
        planningStore(budgetId)?.deleteSavedSearch(id)
    }

    /** Writes every planning record back from a backup; records already on the device with the same id are replaced. */
    suspend fun restore(
        budgetId: String,
        records: List<Triple<String, String, kotlinx.serialization.json.JsonObject>>,
    ): Int {
        val store = planningStore(budgetId) ?: return 0
        records.forEach { (id, type, body) -> store.put(id, type, body) }
        return records.size
    }

    /** Keeps the state above in step with the open budget's planning records. */
    fun CoroutineScope.observe(budgetId: String) {
        val store = planningStore(budgetId) ?: return
        launch { store.savingsGoals().collect { savingsGoals = it } }
        launch { store.debtTerms().collect { debtTerms = it } }
        launch { store.retirementProfiles().collect { retirementProfiles = it } }
        launch { store.reimbursements().collect { reimbursements = it } }
        launch { store.sharedBills().collect { sharedBills = it } }
        launch { store.settings().collect { settings = it } }
        launch {
            store.accountCurrencies().collect { list ->
                accountCurrencies = list.associate { it.accountId to it.currency }
            }
        }
        launch { store.exchangeRates().collect { exchangeRates = it } }
        launch { store.deviceNames().collect { list -> deviceNames = list.associate { it.node to it.name } } }
        launch { store.customReports().collect { customReports = it } }
        launch { store.savedSearches().collect { savedSearches = it } }
    }
}
