package app.tandemforactual.api.client

import app.tandemforactual.api.generated.api.BudgetsApi
import app.tandemforactual.api.generated.api.TransactionsApi
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.data.store.BudgetSyncProtocolException
import app.tandemforactual.data.store.BudgetSyncRemote
import app.tandemforactual.data.store.BudgetSyncRemoteException
import app.tandemforactual.data.store.RemoteBudgetChangePage
import app.tandemforactual.data.store.RemoteBudgetSnapshot
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
class GeneratedBudgetSyncRemote : BudgetSyncRemote {
    override suspend fun readSnapshot(
        budgetId: String,
        encryptionPassword: String?,
    ): RemoteBudgetSnapshot =
        BudgetSyncJson.parseSnapshot(
            unwrap(
                BudgetsApi.getBudgetSnapshot(
                    xBudgetEncryptionPassword = encryptionPassword,
                    budgetId = budgetId,
                ),
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
                BudgetsApi.getBudgetChanges(
                    xBudgetEncryptionPassword = encryptionPassword,
                    budgetId = budgetId,
                    generation = generation,
                    cursor = cursor,
                    throughCursor = throughCursor,
                ),
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
                TransactionsApi.submitTransactionOperation(
                    xBudgetEncryptionPassword = encryptionPassword,
                    idempotencyKey = operation.idempotencyKey,
                    budgetId = operation.budgetId,
                    transactionId = operation.recordId,
                    body = payload,
                ),
            ),
        )
    }

    override suspend fun readOperation(operationId: String): RemoteOperationResult {
        val parsedId =
            try {
                Uuid.parse(operationId)
            } catch (error: IllegalArgumentException) {
                throw BudgetSyncProtocolException("Outbox operation ID is not a UUID", error)
            }
        return BudgetSyncJson.parseOperation(
            unwrap(TransactionsApi.getTransactionOperation(parsedId)),
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
                    TransactionsApi.getBudgetTransaction(
                        xBudgetEncryptionPassword = encryptionPassword,
                        budgetId = budgetId,
                        transactionId = transactionId,
                    ),
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

    private fun CallException.toBudgetSyncException(): BudgetSyncRemoteException =
        when (this) {
            is HttpCallException -> BudgetSyncRemoteException("http-$code", code, this)
            else -> BudgetSyncRemoteException("network-error", cause = this)
        }
}
