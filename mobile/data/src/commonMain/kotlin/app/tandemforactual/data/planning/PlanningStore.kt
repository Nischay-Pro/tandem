package app.tandemforactual.data.planning

import app.tandemforactual.data.actual.civilFromDays
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Reads and writes one budget's planning records, sealing each with the budget's planning key. */
class PlanningStore(
    private val dao: PlanningRecordDao,
    private val budgetId: String,
    private val currentCipher: () -> PlanningCipher,
    private val now: () -> Long,
) {
    constructor(dao: PlanningRecordDao, budgetId: String, cipher: PlanningCipher, now: () -> Long) : this(dao, budgetId, { cipher }, now)

    /** Read on every use, so records stay readable after this device adopts a key shared by another device. */
    private val cipher: PlanningCipher get() = currentCipher()

    private fun open(record: PlanningRecordEntity): JsonObject =
        Json
            .parseToJsonElement(
                cipher
                    .open(
                        record.iv,
                        record.ciphertext,
                        PlanningCipher.identity(
                            record.budgetId,
                            record.recordId,
                            record.recordType,
                            record.schemaVersion,
                            record.revision,
                            record.deleted,
                        ),
                    ).decodeToString(),
            ).jsonObject

    /** Records of one type. A record this key can't open is left out rather than shown wrongly. */
    fun observe(recordType: String): Flow<List<Pair<String, JsonObject>>> =
        dao.observe(budgetId, recordType).map { records ->
            records.mapNotNull { record -> runCatching { record.recordId to open(record) }.getOrNull() }
        }

    /** Every live planning record as id, type and body, for a backup; records this key can't open are left out. */
    suspend fun backupRecords(): List<Triple<String, String, JsonObject>> =
        dao
            .all(budgetId)
            .filter { !it.deleted && isBackedUp(it.recordType) }
            .mapNotNull { record -> runCatching { Triple(record.recordId, record.recordType, open(record)) }.getOrNull() }
            .sortedWith(compareBy({ it.second }, { it.first }))

    suspend fun put(
        recordId: String,
        recordType: String,
        body: JsonObject,
    ) = write(recordId, recordType, body, deleted = false)

    suspend fun delete(
        recordId: String,
        recordType: String,
    ) = write(recordId, recordType, JsonObject(emptyMap()), deleted = true)

    private suspend fun write(
        recordId: String,
        recordType: String,
        body: JsonObject,
        deleted: Boolean,
    ) {
        val existing = dao.find(budgetId, recordId)
        val revision = (existing?.revision ?: 0) + 1
        val (iv, sealed) =
            cipher.seal(
                body.toString().encodeToByteArray(),
                PlanningCipher.identity(budgetId, recordId, recordType, SCHEMA_VERSION, revision, deleted),
            )
        dao.upsert(
            PlanningRecordEntity(
                budgetId,
                recordId,
                recordType,
                SCHEMA_VERSION,
                revision,
                now(),
                deleted,
                iv,
                sealed,
                pendingUpload = true,
                syncedRevision = existing?.syncedRevision ?: 0,
            ),
        )
    }

    fun savingsGoals(): Flow<List<SavingsGoal>> =
        observe(SavingsGoal.TYPE).map { list -> list.map { (id, body) -> SavingsGoal.fromJson(id, body) } }

    fun debtTerms(): Flow<List<DebtTerms>> = observe(DebtTerms.TYPE).map { list -> list.map { (id, body) -> DebtTerms.fromJson(id, body) } }

    suspend fun save(goal: SavingsGoal) = put(goal.id, SavingsGoal.TYPE, goal.toJson())

    suspend fun save(terms: DebtTerms) = put(terms.id, DebtTerms.TYPE, terms.toJson())

    fun retirementProfiles(): Flow<List<RetirementProfile>> =
        dao.observe(budgetId, RetirementProfile.TYPE).map { records ->
            records.mapNotNull { record ->
                runCatching { RetirementProfile.fromJson(record.recordId, open(record), savedYear(record.updatedAtEpochMs)) }.getOrNull()
            }
        }

    /** The settings record, with checkup findings marked as fine on any device added to its own list. */
    fun settings(): Flow<PlanningSettings> =
        combine(observe(PlanningSettings.TYPE), observe(IgnoredCheck.TYPE)) { settings, checks ->
            val saved = settings.firstOrNull()?.let { PlanningSettings.fromJson(it.second) } ?: PlanningSettings()
            saved.copy(ignoredChecks = (saved.ignoredChecks + checks.mapNotNull { IgnoredCheck.fromJson(it.second) }).distinct())
        }

    suspend fun ignoreChecks(keys: List<String>) =
        keys.distinct().forEach {
            put(IgnoredCheck.id(it), IgnoredCheck.TYPE, IgnoredCheck.toJson(it))
        }

    suspend fun save(settings: PlanningSettings) = put(PlanningSettings.ID, PlanningSettings.TYPE, settings.toJson())

    suspend fun deleteRetirementProfile(planId: String) = delete(planId, RetirementProfile.TYPE)

    fun reimbursements(): Flow<List<Reimbursement>> =
        observe(Reimbursement.TYPE).map { list -> list.map { (id, body) -> Reimbursement.fromJson(id, body) } }

    suspend fun save(reimbursement: Reimbursement) = put(reimbursement.transactionId, Reimbursement.TYPE, reimbursement.toJson())

    suspend fun deleteReimbursement(transactionId: String) = delete(transactionId, Reimbursement.TYPE)

    fun sharedBills(): Flow<List<SharedBill>> =
        observe(SharedBill.TYPE).map { list -> list.map { (id, body) -> SharedBill.fromJson(id, body) } }

    suspend fun save(bill: SharedBill) = put(bill.id, SharedBill.TYPE, bill.toJson())

    suspend fun deleteSharedBill(id: String) = delete(id, SharedBill.TYPE)

    suspend fun save(profile: RetirementProfile) = put(profile.planId, RetirementProfile.TYPE, profile.toJson())

    fun jurisdictionPeriods(): Flow<List<JurisdictionPeriod>> =
        observe(JurisdictionPeriod.TYPE).map { list -> list.mapNotNull { (id, body) -> JurisdictionPeriod.fromJson(id, body) } }

    suspend fun save(period: JurisdictionPeriod) = put(period.id, JurisdictionPeriod.TYPE, period.toJson())

    suspend fun deleteJurisdictionPeriod(id: String) = delete(id, JurisdictionPeriod.TYPE)

    fun pensionEstimates(): Flow<List<PensionEstimate>> =
        observe(PensionEstimate.TYPE).map { list -> list.mapNotNull { (id, body) -> PensionEstimate.fromJson(id, body) } }

    suspend fun save(estimate: PensionEstimate) = put(estimate.pensionId, PensionEstimate.TYPE, estimate.toJson())

    suspend fun deletePensionEstimate(pensionId: String) = delete(pensionId, PensionEstimate.TYPE)

    fun accountCurrencies(): Flow<List<AccountCurrency>> =
        observe(AccountCurrency.TYPE).map { list -> list.map { (_, body) -> AccountCurrency.fromJson(body) } }

    /** A blank currency clears it, so the account goes back to the budget's own currency. */
    suspend fun save(currency: AccountCurrency) =
        if (currency.currency.isBlank()) {
            delete(currency.id, AccountCurrency.TYPE)
        } else {
            put(currency.id, AccountCurrency.TYPE, currency.toJson())
        }

    fun accountKinds(): Flow<List<AccountKindChoice>> =
        observe(AccountKindChoice.TYPE).map { list -> list.mapNotNull { (_, body) -> AccountKindChoice.fromJson(body) } }

    suspend fun save(choice: AccountKindChoice) = put(choice.id, AccountKindChoice.TYPE, choice.toJson())

    suspend fun deleteAccountKind(accountId: String) = delete(AccountKindChoice.id(accountId), AccountKindChoice.TYPE)

    fun exchangeRates(): Flow<List<ExchangeRate>> =
        observe(ExchangeRate.TYPE).map { list -> list.map { (id, body) -> ExchangeRate.fromJson(id, body) } }

    suspend fun save(rate: ExchangeRate) = put(rate.id, ExchangeRate.TYPE, rate.toJson())

    suspend fun deleteExchangeRate(rate: ExchangeRate) = delete(rate.id, ExchangeRate.TYPE)

    fun deviceNames(): Flow<List<DeviceName>> = observe(DeviceName.TYPE).map { list -> list.map { (_, body) -> DeviceName.fromJson(body) } }

    suspend fun save(name: DeviceName) = put(name.id, DeviceName.TYPE, name.toJson())

    fun customReports(): Flow<List<CustomReport>> =
        observe(CustomReport.TYPE).map { list ->
            list.map { (id, body) -> CustomReport.fromJson(id, body) }.sortedBy { it.name.lowercase() }
        }

    suspend fun save(report: CustomReport) = put(report.id, CustomReport.TYPE, report.toJson())

    suspend fun deleteCustomReport(id: String) = delete(id, CustomReport.TYPE)

    fun savedSearches(): Flow<List<SavedSearch>> =
        observe(SavedSearch.TYPE).map { list -> list.map { (id, body) -> SavedSearch.fromJson(id, body) }.sortedBy { it.name.lowercase() } }

    suspend fun save(search: SavedSearch) = put(search.id, SavedSearch.TYPE, search.toJson())

    suspend fun deleteSavedSearch(id: String) = delete(id, SavedSearch.TYPE)

    fun depositTerms(): Flow<List<DepositTerms>> =
        observe(DepositTerms.TYPE).map { list -> list.map { (id, body) -> DepositTerms.fromJson(id, body) } }

    suspend fun save(terms: DepositTerms) = put(terms.id, DepositTerms.TYPE, terms.toJson())

    suspend fun deleteDepositTerms(id: String) = delete(id, DepositTerms.TYPE)

    fun paymentFormats(): Flow<List<CustomPaymentFormat>> =
        observe(CustomPaymentFormat.TYPE).map { list ->
            list.map { (id, body) -> CustomPaymentFormat.fromJson(id, body) }.sortedBy { it.name.lowercase() }
        }

    suspend fun save(format: CustomPaymentFormat) = put(format.id, CustomPaymentFormat.TYPE, format.toJson())

    suspend fun deletePaymentFormat(id: String) = delete(id, CustomPaymentFormat.TYPE)

    companion object {
        const val SCHEMA_VERSION = 1

        /** The year a record was written here; records stored from sync carry a sequence number instead, so give none. */
        internal fun savedYear(updatedAtEpochMs: Long): Int? =
            updatedAtEpochMs.takeIf { it > 1_500_000_000_000 }?.let { civilFromDays(it / 86_400_000).first.toInt() }

        /** Planning and profile records are what a planning backup holds; anything else on the device stays out. */
        fun isBackedUp(recordType: String): Boolean = recordType.startsWith("planning.") || recordType.startsWith("profile.")
    }
}
