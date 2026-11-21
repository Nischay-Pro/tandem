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
    private val cipher: PlanningCipher,
    private val now: () -> Long,
) {
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

    companion object {
        const val SCHEMA_VERSION = 1
    }
}
