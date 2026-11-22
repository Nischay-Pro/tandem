package app.tandemforactual.data.planning

import kotlinx.coroutines.flow.Flow
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
                        PlanningCipher.identity(record.budgetId, record.recordId, record.schemaVersion, record.revision),
                    ).decodeToString(),
            ).jsonObject

    /** Records of one type. A record this key can't open is left out rather than shown wrongly. */
    fun observe(recordType: String): Flow<List<Pair<String, JsonObject>>> =
        dao.observe(budgetId, recordType).map { records ->
            records.mapNotNull { record -> runCatching { record.recordId to open(record) }.getOrNull() }
        }

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
                PlanningCipher.identity(budgetId, recordId, SCHEMA_VERSION, revision),
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
            ),
        )
    }

    fun savingsGoals(): Flow<List<SavingsGoal>> =
        observe(SavingsGoal.TYPE).map { list -> list.map { (id, body) -> SavingsGoal.fromJson(id, body) } }

    fun debtTerms(): Flow<List<DebtTerms>> = observe(DebtTerms.TYPE).map { list -> list.map { (id, body) -> DebtTerms.fromJson(id, body) } }

    suspend fun save(goal: SavingsGoal) = put(goal.id, SavingsGoal.TYPE, goal.toJson())

    suspend fun save(terms: DebtTerms) = put(terms.id, DebtTerms.TYPE, terms.toJson())

    fun retirementProfiles(): Flow<List<RetirementProfile>> =
        observe(RetirementProfile.TYPE).map { list -> list.map { (id, body) -> RetirementProfile.fromJson(id, body) } }

    fun settings(): Flow<PlanningSettings> =
        observe(PlanningSettings.TYPE).map { list ->
            list.firstOrNull()?.let { PlanningSettings.fromJson(it.second) } ?: PlanningSettings()
        }

    suspend fun save(settings: PlanningSettings) = put(PlanningSettings.ID, PlanningSettings.TYPE, settings.toJson())

    suspend fun deleteRetirementProfile(planId: String) = delete(planId, RetirementProfile.TYPE)

    fun reimbursements(): Flow<List<Reimbursement>> =
        observe(Reimbursement.TYPE).map { list -> list.map { (id, body) -> Reimbursement.fromJson(id, body) } }

    suspend fun save(reimbursement: Reimbursement) = put(reimbursement.transactionId, Reimbursement.TYPE, reimbursement.toJson())

    suspend fun deleteReimbursement(transactionId: String) = delete(transactionId, Reimbursement.TYPE)

    suspend fun save(profile: RetirementProfile) = put(profile.planId, RetirementProfile.TYPE, profile.toJson())

    fun customReports(): Flow<List<CustomReport>> =
        observe(CustomReport.TYPE).map { list -> list.map { (id, body) -> CustomReport.fromJson(id, body) }.sortedBy { it.name.lowercase() } }

    suspend fun save(report: CustomReport) = put(report.id, CustomReport.TYPE, report.toJson())

    suspend fun deleteCustomReport(id: String) = delete(id, CustomReport.TYPE)

    fun savedSearches(): Flow<List<SavedSearch>> =
        observe(SavedSearch.TYPE).map { list -> list.map { (id, body) -> SavedSearch.fromJson(id, body) }.sortedBy { it.name.lowercase() } }

    suspend fun save(search: SavedSearch) = put(search.id, SavedSearch.TYPE, search.toJson())

    suspend fun deleteSavedSearch(id: String) = delete(id, SavedSearch.TYPE)

    companion object {
        const val SCHEMA_VERSION = 1
    }
}
