package app.tandemforactual.ui.core.session.schedules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import app.tandemforactual.api.client.TandemApiException
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.beginScheduleAction
import app.tandemforactual.data.store.cancelQueuedScheduleAction
import app.tandemforactual.data.store.completeScheduleAction
import app.tandemforactual.data.store.findWrite
import app.tandemforactual.data.store.markScheduleActionConflict
import app.tandemforactual.data.store.markScheduleActionRejected
import app.tandemforactual.data.store.markScheduleActionUncertain
import app.tandemforactual.data.store.readPendingWrites
import app.tandemforactual.data.store.readScheduleActionQueue
import app.tandemforactual.data.store.recoverInterruptedScheduleActions
import app.tandemforactual.data.store.retryScheduleActionCheck
import app.tandemforactual.data.store.returnScheduleActionToQueue
import app.tandemforactual.ui.core.session.SessionHost
import app.tandemforactual.ui.core.session.indicatesTandemReachable
import app.tandemforactual.ui.core.session.schedules.ScheduleActionController
import app.tandemforactual.ui.core.session.shouldKeepBudgetMutationQueued
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Schedule actions (post, skip, edit, create, delete) saved on the device and sent to Actual in order once there's a connection. */
internal class ScheduleActionController(
    internal val host: SessionHost,
) {
    internal var scheduleActionRetrySignal by mutableStateOf(0)

    private val scheduleActionQueueMutex = Mutex()

    private var scheduleActionQueueLoaded = false

    var pendingScheduleActions by mutableStateOf<List<OutboxOperationEntity>>(emptyList())
        private set

    private var isRetryingScheduleAction by mutableStateOf(false)

    fun pendingScheduleActionFor(
        budgetId: String,
        scheduleId: String,
    ): OutboxOperationEntity? = pendingScheduleActionsFor(budgetId, scheduleId).firstOrNull()

    fun pendingScheduleActionsFor(
        budgetId: String,
        scheduleId: String,
    ): List<OutboxOperationEntity> = pendingScheduleActions.filter { it.budgetId == budgetId && it.recordId == scheduleId }

    fun scheduleActionStatusMessage(operation: OutboxOperationEntity): String = scheduleActionStatusText(operation)

    fun scheduleActionStatusActionLabel(operation: OutboxOperationEntity): String? = scheduleActionStatusAction(operation)

    fun performScheduleActionStatusAction(operation: OutboxOperationEntity) {
        when (scheduleActionStatusActionLabel(operation)) {
            "Check status" -> {
                retryScheduleActionStatus(operation.operationId)
            }

            "Undo" -> {
                host.sessionScope.launch {
                    val localRepository = host.repository ?: return@launch
                    if (localRepository.cancelQueuedScheduleAction(operation.operationId)) {
                        refreshScheduleActionQueue()
                        host.reportBudgetActionMessage(null)
                    }
                }
            }

            "Try again" -> {
                retryQueuedScheduleActions()
            }
        }
    }

    fun scheduleActionCanBeReplaced(
        operation: OutboxOperationEntity,
        currentNextDate: String,
    ): Boolean {
        if (operation.status != "conflict" || operation.lastErrorCode != "schedule_changed") return false
        val pendingDate =
            runCatching {
                Json
                    .parseToJsonElement(operation.patchJson)
                    .jsonObject["expectedNextDate"]
                    ?.jsonPrimitive
                    ?.contentOrNull
            }.getOrNull()
        return !pendingDate.isNullOrBlank() && pendingDate != currentNextDate
    }

    fun retryScheduleActionStatus(operationId: String) {
        host.sessionScope.launch {
            val localRepository = host.repository ?: return@launch
            val operation = localRepository.findWrite(operationId) ?: return@launch
            if (operation.recordType != "schedule-action" || operation.status != "uncertain") return@launch
            runCatching { localRepository.retryScheduleActionCheck(operationId) }
            refreshScheduleActionQueue(operation.budgetId)
            scheduleActionRetrySignal += 1
        }
    }

    internal suspend fun restoreScheduleActionQueue(): Boolean =
        scheduleActionQueueMutex.withLock {
            if (scheduleActionQueueLoaded) return@withLock true
            val localRepository = host.repository
            if (localRepository == null) {
                scheduleActionQueueLoaded = true
                return@withLock true
            }
            return@withLock try {
                localRepository.recoverInterruptedScheduleActions()
                pendingScheduleActions = localRepository.readScheduleActionQueue()
                scheduleActionQueueLoaded = true
                true
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                false
            }
        }

    internal suspend fun refreshScheduleActionQueue(budgetId: String? = null) {
        val localRepository = host.repository ?: return
        scheduleActionQueueMutex.withLock {
            pendingScheduleActions = localRepository.readScheduleActionQueue()
            scheduleActionQueueLoaded = true
            if (!budgetId.isNullOrBlank()) host.replacePendingWrites(localRepository.readPendingWrites(budgetId))
        }
    }

    fun retryQueuedScheduleActions() {
        scheduleActionRetrySignal += 1
    }

    /** Sends saved schedule changes in order after a connection is available. */
    suspend fun processQueuedScheduleActions() {
        while (!restoreScheduleActionQueue()) delay(1_000)
        var observedRetrySignal = scheduleActionRetrySignal
        var pauseUntilRetrySignal = false
        while (true) {
            if (pauseUntilRetrySignal) {
                observedRetrySignal =
                    withTimeoutOrNull(SCHEDULE_ACTION_RETRY_INTERVAL_MILLIS) { awaitRetrySignal(observedRetrySignal) }
                        ?: scheduleActionRetrySignal
                pauseUntilRetrySignal = false
            }
            val request = nextQueuedAction()
            if (request == null) {
                observedRetrySignal = awaitRetrySignal(observedRetrySignal)
                refreshScheduleActionQueue()
                continue
            }
            snapshotFlow {
                !host.isSigningOut &&
                    host.authenticatedDevice != null &&
                    !host.isSyncing &&
                    !host.isPerformingBudgetAction &&
                    !isRetryingScheduleAction
            }.first { it }
            when (val prepared = prepare(request)) {
                Prepared.Wait -> {
                    observedRetrySignal = awaitRetrySignal(observedRetrySignal)
                }

                Prepared.Skip -> {
                    Unit
                }

                is Prepared.Ready -> {
                    pauseUntilRetrySignal = send(request, prepared)
                    observedRetrySignal = scheduleActionRetrySignal
                }
            }
        }
    }

    private suspend fun awaitRetrySignal(observed: Int): Int = snapshotFlow { scheduleActionRetrySignal }.first { it != observed }

    /** The oldest queued action for each schedule, so a schedule's actions are always sent in the order they were made. */
    private fun nextQueuedAction(): OutboxOperationEntity? =
        pendingScheduleActions.firstOrNull { candidate ->
            candidate.status == "queued" &&
                pendingScheduleActions.none { earlier ->
                    earlier.operationId != candidate.operationId &&
                        earlier.budgetId == candidate.budgetId &&
                        earlier.recordId == candidate.recordId &&
                        earlier.createdAtEpochMs <= candidate.createdAtEpochMs &&
                        (earlier.createdAtEpochMs < candidate.createdAtEpochMs || earlier.operationId < candidate.operationId)
                }
        }

    private sealed interface Prepared {
        data object Wait : Prepared

        data object Skip : Prepared

        data class Ready(
            val repository: LocalBudgetRepository,
            val name: String?,
            val encryptionPassword: String?,
            val expectedNextDate: String?,
            val fields: JsonObject?,
        ) : Prepared
    }

    /** Checks the action can be sent by this signed-in account and budget, and claims it; invalid actions are rejected. */
    private suspend fun prepare(request: OutboxOperationEntity): Prepared {
        if (!host.isCurrentIdentityCacheOwner()) return Prepared.Wait
        val payload = runCatching { Json.parseToJsonElement(request.patchJson).jsonObject }.getOrNull()
        val expectedOwner = payload?.get("cacheOwner")?.jsonPrimitive?.contentOrNull
        if (payload == null || expectedOwner.isNullOrBlank() || expectedOwner != host.activeBudgetCacheOwner()) return Prepared.Wait
        val encryptionPassword = host.scheduleActionEncryptionPasswords[request.budgetId]
        if (payload["encrypted"]?.jsonPrimitive?.booleanOrNull == true && encryptionPassword.isNullOrBlank()) return Prepared.Wait
        val expectedNextDate = payload["expectedNextDate"]?.jsonPrimitive?.contentOrNull
        val fields = payload["fields"] as? JsonObject
        val validAction =
            when (request.action) {
                "post", "skip" -> !expectedNextDate.isNullOrBlank()
                "edit", "create" -> fields != null
                "delete" -> true
                else -> false
            }
        if (!validAction) {
            host.repository?.markScheduleActionRejected(request.operationId, "invalid-queued-action")
            refreshScheduleActionQueue(request.budgetId)
            return Prepared.Skip
        }
        val localRepository = host.repository ?: return Prepared.Skip
        if (!localRepository.beginScheduleAction(request.operationId)) {
            refreshScheduleActionQueue(request.budgetId)
            return Prepared.Skip
        }
        refreshScheduleActionQueue(request.budgetId)
        val name = payload["name"]?.jsonPrimitive?.contentOrNull
        return Prepared.Ready(localRepository, name, encryptionPassword, expectedNextDate, fields)
    }

    /** Sends one action to Actual and records the outcome. Returns true when sending should pause until a retry. */
    private suspend fun send(
        request: OutboxOperationEntity,
        ready: Prepared.Ready,
    ): Boolean {
        val localRepository = ready.repository
        isRetryingScheduleAction = true
        return try {
            host.apiClient.withExclusiveSession {
                host.apiClient.manageBudgetSchedule(
                    budgetId = request.budgetId,
                    scheduleId = request.recordId,
                    action = request.action,
                    name = ready.name,
                    encryptionPassword = ready.encryptionPassword,
                    fields = ready.fields,
                    expectedNextDate = ready.expectedNextDate,
                    idempotencyKeyOverride = request.operationId,
                )
            }
            localRepository.completeScheduleAction(request.operationId)
            refreshScheduleActionQueue(request.budgetId)
            host.withoutBudgetActionFeedback {
                host.synchronize(request.budgetId, ready.encryptionPassword, retryQueuedBudgetMutations = false)
            }
            false
        } catch (error: CancellationException) {
            throw error
        } catch (error: TandemApiException) {
            recordFailure(localRepository, request, error)
        } catch (error: Exception) {
            localRepository.returnScheduleActionToQueue(request.operationId, "network-error")
            host.markServerReachable(false)
            refreshScheduleActionQueue(request.budgetId)
            true
        } finally {
            isRetryingScheduleAction = false
        }
    }

    private suspend fun recordFailure(
        localRepository: LocalBudgetRepository,
        request: OutboxOperationEntity,
        error: TandemApiException,
    ): Boolean {
        val pause =
            when {
                error.errorCode == "schedule_changed" -> {
                    localRepository.markScheduleActionConflict(request.operationId, "schedule_changed")
                    false
                }

                error.errorCode in setOf("invalid_device_credential", "sign_in_required", "sign_in_expired") -> {
                    localRepository.returnScheduleActionToQueue(request.operationId, error.errorCode)
                    true
                }

                error.errorCode in setOf("operation_uncertain", "operation_in_progress", "operation_result_unavailable") -> {
                    localRepository.markScheduleActionUncertain(request.operationId, error.errorCode)
                    false
                }

                shouldKeepBudgetMutationQueued(error.httpStatus) -> {
                    localRepository.returnScheduleActionToQueue(request.operationId, error.errorCode)
                    host.markServerReachable(error.indicatesTandemReachable())
                    true
                }

                else -> {
                    localRepository.markScheduleActionRejected(request.operationId, error.errorCode)
                    false
                }
            }
        refreshScheduleActionQueue(request.budgetId)
        return pause
    }

    private companion object {
        const val SCHEDULE_ACTION_RETRY_INTERVAL_MILLIS = 30_000L
    }
}
