package app.tandemforactual.api.client

import app.tandemforactual.api.generated.api.BudgetsApi
import app.tandemforactual.api.generated.api.TransactionsApi
import app.tandemforactual.api.generated.models.GetBudgetSnapshotPageAfterType
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.data.store.BudgetSyncProtocolException
import app.tandemforactual.data.store.BudgetSyncRemote
import app.tandemforactual.data.store.BudgetSyncRemoteException
import app.tandemforactual.data.store.RemoteBudgetChangePage
import app.tandemforactual.data.store.RemoteBudgetSnapshot
import app.tandemforactual.data.store.RemoteBudgetSnapshotPage
import app.tandemforactual.data.store.RemoteOperationResult
import app.tandemforactual.data.store.RemoteTransactionRecord
import arrow.core.Either
import com.kroegerama.openapi.kmp.gen.companion.CallException
import com.kroegerama.openapi.kmp.gen.companion.HttpCallException
import com.kroegerama.openapi.kmp.gen.companion.HttpCallResponse
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.uuid.Uuid

/** Adapts the generated OpenAPI client to the local-first sync protocol. */
class GeneratedBudgetSyncRemote(
    private val apiClient: TandemApiClient,
) : BudgetSyncRemote {
    override suspend fun readSnapshot(
        budgetId: String,
        encryptionPassword: String?,
    ): RemoteBudgetSnapshot =
        BudgetSyncJson.parseSnapshot(
            unwrap(
                withApiRequest {
                    BudgetsApi.getBudgetSnapshot(
                        xBudgetEncryptionPassword = encryptionPassword,
                        budgetId = budgetId,
                    )
                },
            ),
            budgetId,
        )

    override suspend fun readSnapshotPage(
        budgetId: String,
        generation: String?,
        cursor: String?,
        afterType: String?,
        afterId: String?,
        limit: Int,
        encryptionPassword: String?,
    ): RemoteBudgetSnapshotPage =
        BudgetSyncJson.parseSnapshotPage(
            unwrap(
                withApiRequest {
                    BudgetsApi.getBudgetSnapshotPage(
                        xBudgetEncryptionPassword = encryptionPassword,
                        budgetId = budgetId,
                        generation = generation?.let(Uuid::parse),
                        cursor = cursor,
                        afterType = afterType?.toGeneratedSnapshotRecordType(),
                        afterId = afterId,
                        limit = limit.toLong(),
                    )
                },
            ),
            budgetId,
        )

    override suspend fun readChanges(
        budgetId: String,
        generation: String,
        cursor: String,
        throughCursor: String?,
        encryptionPassword: String?,
    ): RemoteBudgetChangePage =
        BudgetSyncJson.parseChanges(
            unwrap(
                withApiRequest {
                    BudgetsApi.getBudgetChanges(
                        xBudgetEncryptionPassword = encryptionPassword,
                        budgetId = budgetId,
                        generation = generation,
                        cursor = cursor,
                        throughCursor = throughCursor,
                    )
                },
            ),
            budgetId,
        )

    override suspend fun submitOperation(
        operation: OutboxOperationEntity,
        encryptionPassword: String?,
    ): RemoteOperationResult {
        val action =
            when (operation.action) {
                "CREATE" -> "create"
                "UPDATE" -> "update"
                "DELETE" -> "delete"
                else -> throw BudgetSyncProtocolException("This write action is not supported by the remote API")
            }
        val payload =
            buildJsonObject {
                put("operationId", operation.operationId)
                put("action", action)
                if (action != "create") {
                    val baseRevision =
                        operation.baseRevision
                            ?: throw BudgetSyncProtocolException("Outbox write is missing its base revision")
                    val baseSnapshot =
                        operation.baseSnapshotJson
                            ?: throw BudgetSyncProtocolException("Outbox write is missing its base snapshot")
                    put("baseRevision", baseRevision)
                    put("baseSnapshot", BudgetSyncJson.parseJsonObject(baseSnapshot))
                }
                put("patch", BudgetSyncJson.parseJsonObject(operation.patchJson))
            }
        return BudgetSyncJson.parseOperation(
            unwrap(
                withApiRequest {
                    TransactionsApi.submitTransactionOperation(
                        xBudgetEncryptionPassword = encryptionPassword,
                        idempotencyKey = operation.idempotencyKey,
                        budgetId = operation.budgetId,
                        transactionId = operation.recordId,
                        body = payload,
                    )
                },
            ),
        )
    }

    override suspend fun readOperation(operationId: String): RemoteOperationResult = readOperation(operationId, null)

    override suspend fun readOperation(
        operationId: String,
        encryptionPassword: String?,
    ): RemoteOperationResult {
        val parsedId =
            try {
                Uuid.parse(operationId)
            } catch (error: IllegalArgumentException) {
                throw BudgetSyncProtocolException("Outbox operation ID is not a UUID", error)
            }
        return BudgetSyncJson.parseOperation(
            unwrap(
                withApiRequest {
                    TransactionsApi.getTransactionOperation(
                        xBudgetEncryptionPassword = encryptionPassword,
                        operationId = parsedId,
                    )
                },
            ),
        )
    }

    override suspend fun readTransaction(
        budgetId: String,
        transactionId: String,
        encryptionPassword: String?,
    ): RemoteTransactionRecord? {
        val payload =
            try {
                unwrap(
                    withApiRequest {
                        TransactionsApi.getBudgetTransaction(
                            xBudgetEncryptionPassword = encryptionPassword,
                            budgetId = budgetId,
                            transactionId = transactionId,
                        )
                    },
                )
            } catch (error: BudgetSyncRemoteException) {
                if (error.httpStatus == 404) return null
                throw error
            }
        return BudgetSyncJson.parseTransaction(payload, transactionId)
    }

    private fun unwrap(result: Either<CallException, HttpCallResponse<Map<String, JsonElement>>>): Map<String, JsonElement> {
        val error = result.swap().getOrNull()
        if (error != null) throw error.toBudgetSyncException()
        return result.getOrNull()!!.data
    }

    private suspend fun <T> withApiRequest(block: suspend () -> T): T = apiClient.withGeneratedApi(block)

    private fun String.toGeneratedSnapshotRecordType(): GetBudgetSnapshotPageAfterType =
        when (this) {
            "account" -> GetBudgetSnapshotPageAfterType.ACCOUNT
            "account-group" -> GetBudgetSnapshotPageAfterType.ACCOUNT_GROUP
            "category" -> GetBudgetSnapshotPageAfterType.CATEGORY
            "category-group" -> GetBudgetSnapshotPageAfterType.CATEGORY_GROUP
            "payee" -> GetBudgetSnapshotPageAfterType.PAYEE
            "transaction" -> GetBudgetSnapshotPageAfterType.TRANSACTION
            "budget-month" -> GetBudgetSnapshotPageAfterType.BUDGET_MONTH
            else -> throw BudgetSyncProtocolException("Snapshot continuation type is invalid")
        }

    private fun CallException.toBudgetSyncException(): BudgetSyncRemoteException =
        when (this) {
            is HttpCallException -> BudgetSyncRemoteException("http-$code", code, this)
            else -> BudgetSyncRemoteException("network-error", cause = this)
        }
}
