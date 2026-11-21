package app.tandemforactual.data.planning

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A Tandem-only planning record, such as a savings goal or a debt's terms. Its contents are sealed with the budget's
 * planning key, so the table holds no readable financial details; only the type and revision stay visible for sync.
 */
@Entity(tableName = "planning_records", primaryKeys = ["budgetId", "recordId"])
data class PlanningRecordEntity(
    val budgetId: String,
    val recordId: String,
    val recordType: String,
    val schemaVersion: Int,
    val revision: Long,
    val updatedAtEpochMs: Long,
    val deleted: Boolean,
    val iv: ByteArray,
    val ciphertext: ByteArray,
    val pendingUpload: Boolean,
    @ColumnInfo(defaultValue = "0") val syncedRevision: Long = 0,
) {
    override fun equals(other: Any?): Boolean =
        other is PlanningRecordEntity &&
            budgetId == other.budgetId &&
            recordId == other.recordId &&
            revision == other.revision &&
            deleted == other.deleted &&
            ciphertext.contentEquals(other.ciphertext)

    override fun hashCode(): Int = (budgetId.hashCode() * 31 + recordId.hashCode()) * 31 + revision.hashCode()
}

@Dao
interface PlanningRecordDao {
    @Query("SELECT * FROM planning_records WHERE budgetId = :budgetId AND recordType = :recordType AND deleted = 0 ORDER BY recordId")
    fun observe(
        budgetId: String,
        recordType: String,
    ): Flow<List<PlanningRecordEntity>>

    @Query("SELECT * FROM planning_records WHERE budgetId = :budgetId AND recordId = :recordId")
    suspend fun find(
        budgetId: String,
        recordId: String,
    ): PlanningRecordEntity?

    @Query("SELECT * FROM planning_records WHERE budgetId = :budgetId AND pendingUpload = 1 ORDER BY updatedAtEpochMs")
    suspend fun pending(budgetId: String): List<PlanningRecordEntity>

    @Query("SELECT * FROM planning_records WHERE budgetId = :budgetId")
    suspend fun all(budgetId: String): List<PlanningRecordEntity>

    @Upsert
    suspend fun upsert(record: PlanningRecordEntity)

    @Query("DELETE FROM planning_records WHERE budgetId = :budgetId")
    suspend fun deleteBudget(budgetId: String)
}
