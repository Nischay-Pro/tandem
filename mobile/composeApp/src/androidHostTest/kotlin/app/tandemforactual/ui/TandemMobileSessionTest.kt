package app.tandemforactual.ui

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.api.client.TandemApiException
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.data.store.BudgetSyncRemoteException
import app.tandemforactual.data.store.LocalBudgetMutationOutboxItem
import app.tandemforactual.data.store.LocalBudgetMutationQueueSnapshot
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.LocalRecordProjection
import app.tandemforactual.data.store.LocalWriteAction
import app.tandemforactual.data.store.LocalWriteRequest
import app.tandemforactual.data.store.RemoteBudgetRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TandemMobileSessionTest {
    @Test
    fun openIdVerifierSurvivesBackgroundTimeButIsBoundToServerAndExpires() {
        var now = 1_000L
        val state = OpenIdLoginProofState { now }
        val challenge = state.begin("https://tandem.example")

        assertEquals(43, challenge.length)
        assertNotNull(state.takeVerifierForCallback("https://tandem.example"))
        assertNull(state.takeVerifierForCallback("https://tandem.example"))

        now += 60_000L
        state.begin("https://tandem.example")
        assertNull(state.takeVerifierForCallback("https://other.example"))
        assertNull(state.takeVerifierForCallback("https://tandem.example"))

        state.begin("https://tandem.example")
        now += OPEN_ID_PROOF_TTL_MILLIS
        assertNull(state.takeVerifierForCallback("https://tandem.example"))
    }

    @Test
    fun openIdTransientVerifierRestoresForCompletionThenExpiresOnRestore() {
        var now = 1_000L
        val original = OpenIdLoginProofState { now }
        original.begin("https://tandem.example")
        val snapshot = original.saveTransientSnapshot()

        val restored = OpenIdLoginProofState.restoreTransientSnapshot(snapshot, { now })
        assertNotNull(restored.takeVerifierForCallback("https://tandem.example"))
        assertNull(restored.takeVerifierForCallback("https://tandem.example"))

        now += OPEN_ID_PROOF_TTL_MILLIS
        val expired = OpenIdLoginProofState.restoreTransientSnapshot(snapshot, { now })
        assertNull(expired.takeVerifierForCallback("https://tandem.example"))
        val overlong =
            OpenIdLoginProofState.restoreTransientSnapshot(
                listOf("verifier", "https://tandem.example", (now + OPEN_ID_PROOF_TTL_MILLIS * 2).toString()),
                { now },
            )
        assertNull(overlong.takeVerifierForCallback("https://tandem.example"))
        original.clear()
        assertNull(original.takeVerifierForCallback("https://tandem.example"))
        assertEquals(listOf("", "", "0"), original.saveTransientSnapshot())
    }

    @Test
    fun cancellingOpenIdSignInClearsTheTransientVerifier() {
        val now = 1_000L
        val state = OpenIdLoginProofState { now }
        state.begin("https://tandem.example")
        val session = TandemMobileSession(TandemApiClient(), null, { now }, openIdLoginProofState = state)

        session.cancelActualOpenIdSignIn()

        assertNull(state.takeVerifierForCallback("https://tandem.example"))
        assertEquals(listOf("", "", "0"), state.saveTransientSnapshot())
    }

    @Test
    fun acceptsOnlyShortLivedTandemOpenIdReturnCodes() {
        val code = "C".repeat(43)
        assertEquals(code, actualOpenIdReturnCode("tandem://signin/complete?code=$code"))
        assertNull(actualOpenIdReturnCode("https://tandem.example/signin/complete?code=$code"))
        assertNull(actualOpenIdReturnCode("tandem://signin/complete?token=actual-session-token"))
        assertNull(actualOpenIdReturnCode("tandem://signin/complete?code=short"))
    }

    @Test
    fun mapsServerStatusesToAppMessagesWithoutShowingServerText() {
        assertEquals(
            "Tandem is temporarily unavailable. Try again shortly.",
            TandemApiException("database trace: private detail", httpStatus = 503).userFacingMessage("fallback"),
        )
        assertEquals(
            "Tandem rejected the request. Check the details and try again.",
            TandemApiException("raw validation detail", httpStatus = 422).userFacingMessage("fallback"),
        )
        assertEquals(
            "fallback",
            TandemApiException("raw transport detail").userFacingMessage("fallback"),
        )
        assertEquals(
            "Two statement rows share a reference. Choose a column with unique values.",
            TandemApiException("duplicate_imported_id", httpStatus = 400, safeMessageOverride = "untrusted server text")
                .userFacingMessage("fallback"),
        )
    }

    @Test
    fun commonApiErrorsExplainWhatHappenedAndGiveOneNextStep() {
        val messages =
            listOf(
                "sign_in_required" to (null to "Sign in to open a budget."),
                "invalid_credentials" to (null to "The password didn’t match this Actual server. Check it and try again."),
                "invalid_device_credential" to (null to "Sign in again to restore access."),
                "bank_sync_failed" to (null to "Actual couldn’t sync your bank transactions. Check the bank connection and try again."),
                "bank_sync_busy" to (null to "Bank sync is already running. Try again shortly."),
                "duplicate_imported_id" to (null to "Two statement rows share a reference. Choose a column with unique values."),
                "actual_operation_failed" to (null to "Actual couldn’t complete this change. Check the Actual connection and try again."),
                "operation_uncertain" to
                    (null to "Tandem couldn’t confirm whether Actual saved this change. Check Actual, then try again."),
                "operation_in_progress" to (null to "Tandem is still processing this change. Wait a moment, then try again."),
                "schedule_changed" to (409 to "The scheduled transaction changed in Actual. Refresh the schedule, then try again."),
                "operation_rejected" to (null to "Actual rejected this change earlier. Refresh the budget, then try again."),
                "idempotency_key_reused" to
                    (null to "Tandem matched this change to a different request. Refresh the budget, then try again."),
                "operation_result_unavailable" to
                    (null to "Actual saved this change, but Tandem needs a fresh budget copy. Refresh the budget."),
                "invalid_request" to (null to "Check the details and try again."),
                "server-error" to (503 to "Tandem is temporarily unavailable. Try again shortly."),
            )

        messages.forEach { (code, expected) ->
            val message = budgetApiErrorMessage(code, expected.first, "fallback")
            assertEquals(expected.second, message, code)
            assertFalse(message.contains("invalid", ignoreCase = true), message)
            assertFalse(message.contains("failed", ignoreCase = true), message)
            assertFalse(message.contains("error", ignoreCase = true), message)
        }
    }

    @Test
    fun signInAndSyncShareOutageMessagesAndOnlyNamePasswordsForCredentialFailures() {
        val fallback = "fallback"
        val sharedOutages =
            listOf(
                "network-error" to (null to "Couldn’t reach Tandem at this address. Check the address and connection."),
                "http-404" to (404 to "Tandem’s API route isn’t available. Check the address and the proxy’s /api/v1/ route."),
                "http-405" to (405 to "Tandem’s API route isn’t available. Check the address and the proxy’s /api/v1/ route."),
                "http-400" to (400 to "Tandem rejected the request. Check the details and try again."),
                "http-422" to (422 to "Tandem rejected the request. Check the details and try again."),
                "http-408" to (408 to "Tandem couldn’t complete the request. Wait a moment, then try again."),
                "http-429" to (429 to "Tandem couldn’t complete the request. Wait a moment, then try again."),
                "http-500" to (500 to "Tandem is temporarily unavailable. Try again shortly."),
                "http-502" to (502 to "Tandem is temporarily unavailable. Try again shortly."),
                "http-503" to (503 to "Tandem is temporarily unavailable. Try again shortly."),
                "http-504" to (504 to "Tandem is temporarily unavailable. Try again shortly."),
                "actual_unavailable" to (503 to "Tandem can’t reach Actual. Check the Actual connection, then try again."),
            )

        sharedOutages.forEach { (code, expected) ->
            val signIn = apiFailureMessage(code, expected.first, ApiFailureContext.SIGN_IN, fallback)
            val sync = apiFailureMessage(code, expected.first, ApiFailureContext.SYNC, fallback)
            assertEquals(expected.second, signIn, "sign-in: $code")
            assertEquals(signIn, sync, "sync: $code")
            assertFalse(signIn.contains("password", ignoreCase = true), code)
        }
        assertEquals(
            "Tandem is temporarily unavailable. Try again shortly.",
            apiFailureMessage("http-503", null, ApiFailureContext.SYNC, fallback),
        )

        val thrownNetwork =
            TandemApiException("network-error", cause = IllegalStateException("private host details"))
                .userFacingMessage(fallback, ApiFailureContext.SIGN_IN)
        assertEquals("Couldn’t reach Tandem at this address. Check the address and connection.", thrownNetwork)
        assertFalse(thrownNetwork.contains("private host details"))
        val thrownSyncNetwork =
            TandemApiException("network-error", cause = IllegalStateException("private host details"))
                .userFacingMessage(fallback, ApiFailureContext.SYNC)
        assertEquals(thrownNetwork, thrownSyncNetwork)
        assertFalse(thrownSyncNetwork.contains("password", ignoreCase = true))

        val unauthorizedSignIn = apiFailureMessage("http-401", 401, ApiFailureContext.SIGN_IN, fallback)
        val unauthorizedSync = apiFailureMessage("http-401", 401, ApiFailureContext.SYNC, fallback)
        assertTrue(unauthorizedSignIn.contains("Sign in again"))
        assertTrue(unauthorizedSync.contains("Sign in again"))
        assertFalse(unauthorizedSignIn.contains("password", ignoreCase = true))
        assertFalse(unauthorizedSync.contains("password", ignoreCase = true))

        val forbiddenSignIn = apiFailureMessage("http-403", 403, ApiFailureContext.SIGN_IN, fallback)
        val forbiddenSync = apiFailureMessage("http-403", 403, ApiFailureContext.SYNC, fallback)
        assertTrue(forbiddenSignIn.contains("Ask the budget owner"))
        assertTrue(forbiddenSync.contains("Ask the budget owner"))
        assertFalse(forbiddenSignIn.contains("password", ignoreCase = true))
        assertFalse(forbiddenSync.contains("password", ignoreCase = true))

        assertEquals(
            "This budget is no longer available. Choose another budget.",
            apiFailureMessage("not_found", 404, ApiFailureContext.SYNC, fallback),
        )
        assertEquals(
            "This item is no longer available. Refresh the budget and try again.",
            apiFailureMessage("not_found", 404, ApiFailureContext.GENERAL, fallback),
        )
        assertEquals(
            "Tandem has no saved status for this edit. Try again, or check Actual before removing it.",
            apiFailureMessage("operation_not_found", 404, ApiFailureContext.SYNC, fallback),
        )
        assertFalse(TandemApiException("http-404", 404).indicatesTandemReachable())
        assertTrue(TandemApiException("not_found", 404).indicatesTandemReachable())
        assertFalse(BudgetSyncRemoteException("http-404", 404).indicatesTandemReachable())
        assertTrue(BudgetSyncRemoteException("not_found", 404).indicatesTandemReachable())

        assertEquals(
            "The password didn’t match this Actual server. Check it and try again.",
            apiFailureMessage("invalid_credentials", 401, ApiFailureContext.SIGN_IN, fallback),
        )
        assertEquals(
            "That budget password didn’t unlock this budget. Check it and try again.",
            apiFailureMessage("invalid_budget_password", 422, ApiFailureContext.SYNC, fallback),
        )
        assertFalse(
            apiFailureMessage("http-422", 422, ApiFailureContext.SYNC, fallback).contains("password", ignoreCase = true),
        )
    }

    @Test
    fun mapsEveryBudgetMutationConflictCodeToItsRecoveryActionAndMessage() {
        val cases =
            mapOf(
                "operation_in_progress" to
                    (
                        BudgetMutationFailureDisposition.OPERATION_IN_PROGRESS to
                            "Tandem is still processing this change. Wait a moment, then try again."
                    ),
                "operation_uncertain" to
                    (
                        BudgetMutationFailureDisposition.OPERATION_UNCERTAIN to
                            "Tandem couldn’t confirm whether Actual saved this change. Refresh the budget and check Actual before deciding what to do with it."
                    ),
                "operation_rejected" to
                    (
                        BudgetMutationFailureDisposition.OPERATION_REJECTED to
                            "Actual rejected this change earlier. Refresh the budget, then try again."
                    ),
                "idempotency_key_reused" to
                    (
                        BudgetMutationFailureDisposition.IDEMPOTENCY_KEY_REUSED to
                            "Tandem matched this change to a different request. Refresh the budget, then try again."
                    ),
                "operation_result_unavailable" to
                    (
                        BudgetMutationFailureDisposition.RESULT_UNAVAILABLE to
                            "Actual saved this change, but Tandem needs a fresh budget copy. Refresh the budget."
                    ),
            )

        cases.forEach { (code, expected) ->
            assertEquals(expected.first, budgetMutationFailureDisposition(code), code)
            assertEquals(expected.second, budgetMutationFailureMessage(code), code)
        }
        assertEquals(
            cases.size,
            cases.values
                .map { it.first }
                .distinct()
                .size,
        )
        val recoveryActions =
            mapOf(
                "operation_in_progress" to (true to false),
                "operation_uncertain" to (false to true),
                "operation_rejected" to (true to false),
                "idempotency_key_reused" to (true to false),
                "operation_result_unavailable" to (false to false),
            )
        recoveryActions.forEach { (code, expected) ->
            assertEquals(expected.first, budgetMutationCanRetry(code), "$code can retry")
            assertEquals(expected.second, budgetMutationRequiresActualCheck(code), "$code requires an Actual check")
        }
        assertTrue(budgetMutationRetryNeedsNewKey("operation_rejected"))
        assertTrue(budgetMutationRetryNeedsNewKey("idempotency_key_reused"))
        assertFalse(budgetMutationRetryNeedsNewKey("operation_in_progress"))
        assertFalse(budgetMutationRetryNeedsNewKey("operation_uncertain"))
        assertFalse(budgetMutationRetryNeedsNewKey("operation_result_unavailable"))
        assertTrue(budgetMutationNeedsRefresh("operation_result_unavailable"))
        assertFalse(budgetMutationNeedsRefresh("operation_uncertain"))
    }

    @Test
    fun budgetMutationRetryUsesFreshKeysOnlyForTerminalRequestsAndBlocksUnknownOutcomes() {
        val request =
            QueuedBudgetReorder(
                budgetId = "budget-1",
                scopeId = "group-1",
                kind = BudgetReorderKind.RENAME_CATEGORY,
                itemId = "category-1",
                targetId = null,
                desiredOrder = emptyList(),
                encryptionPassword = null,
                name = "Household",
                operationId = "original-key",
            )

        assertEquals(
            request,
            budgetMutationRequestForRetry(request, "operation_in_progress", "replacement-key"),
        )
        assertNull(budgetMutationRequestForRetry(request, "operation_uncertain", "replacement-key"))
        listOf("operation_rejected", "idempotency_key_reused").forEach { code ->
            val retry = budgetMutationRequestForRetry(request, code, "replacement-key")
            assertEquals("replacement-key", retry?.operationId, code)
            assertEquals(request.copy(operationId = "replacement-key"), retry, code)
            assertTrue(budgetMutationRetryNeedsNewKey(code), code)
        }
        listOf("operation_uncertain", "operation_result_unavailable", "unexpected_409", null).forEach { code ->
            assertNull(budgetMutationRequestForRetry(request, code, "replacement-key"), "$code must not be replayed")
            assertFalse(budgetMutationCanRetry(code), "$code must not offer a retry")
            assertFalse(budgetMutationRetryNeedsNewKey(code), "$code must not mint a retry key")
        }
        assertTrue(budgetMutationCanRetry("operation_in_progress"))
        assertTrue(budgetMutationRequiresActualCheck("operation_uncertain"))
        assertFalse(budgetMutationRequiresActualCheck("operation_in_progress"))
        assertTrue(
            budgetMutationFailureMessage("unexpected_409").contains("check Actual", ignoreCase = true),
        )
    }

    @Test
    fun budgetMutationReviewUsesTheSharedUserFacingStatusNames() {
        assertEquals("Needs review", budgetMutationStatusLabel("conflict"))
        assertEquals("Status unknown", budgetMutationStatusLabel("uncertain"))
        assertEquals("Rejected", budgetMutationStatusLabel("rejected"))
        assertEquals("Waiting to sync", budgetMutationStatusLabel("queued"))
    }

    @Test
    fun activityRefreshKeepsOnePageAndCapsLargeLists() {
        assertEquals(50, activityTransactionRefreshLimit(0))
        assertEquals(50, activityTransactionRefreshLimit(50))
        assertEquals(125, activityTransactionRefreshLimit(125))
        assertEquals(200, activityTransactionRefreshLimit(5_000))
    }

    @Test
    fun activityRefreshPreservesFiveThousandLoadedRowsAndUpdatesTheNewestPage() {
        val previousRecords =
            (1..5_000).map { index ->
                activityTransaction("transaction-${index.toString().padStart(4, '0')}", "2026-11-09", index.toLong())
            }
        val newest = activityTransaction("new", "2026-11-10", 0)
        val refreshedPage = listOf(newest) + previousRecords.take(199).map { it.copy(amountMinor = -1) }
        val merged =
            mergeRefreshedActivityTransactions(
                previouslyLoaded = previousRecords,
                refreshedPage = refreshedPage,
            )

        assertEquals(5_001, merged.size)
        assertEquals("new", merged.first().recordId)
        assertEquals("transaction-0001", merged[1].recordId)
        assertEquals(-1L, merged[1].amountMinor)
        assertEquals("transaction-5000", merged.last().recordId)
        assertEquals(5_001, merged.map(BudgetRecordEntity::recordId).toSet().size)
    }

    @Test
    fun uncertainWriteScreenOffersRecoveryActionsOnlyAfterASettledStatusResponse() {
        assertFalse(canOfferUncertainWriteRecoveryActions("uncertain", statusCheckAttempted = false))
        assertTrue(canOfferUncertainWriteRecoveryActions("uncertain", statusCheckAttempted = true))
        assertTrue(canOfferUncertainWriteRecoveryActions("queued", statusCheckAttempted = true))
        assertFalse(canOfferUncertainWriteRecoveryActions("committed", statusCheckAttempted = true))
        assertFalse(canOfferUncertainWriteRecoveryActions("conflict", statusCheckAttempted = true))
        assertTrue(isUncertainWriteOperationNotFound("operation_not_found"))
        assertFalse(isUncertainWriteOperationNotFound("http-404"))

        val warning = uncertainWriteRemovalWarning("Walgreens", "$108.14")
        assertTrue(warning.contains("Actual may already have saved it"))
        assertTrue(warning.contains("won’t undo a change already stored there"))
    }

    @Test
    fun discardingAnUncertainWriteDoesNotClaimActualWasUnchanged() {
        val message = discardedPendingWriteMessage("uncertain")

        assertTrue(message.contains("removed from this device"))
        assertTrue(message.contains("Actual may already have saved it"))
        assertTrue(message.contains("doesn’t undo it"))
        assertFalse(message.contains("Actual wasn’t changed"))
        assertTrue(discardedPendingWriteMessage("queued").contains("Actual wasn’t changed"))
    }

    @Test
    fun budgetQueueReadFailureDoesNotOverwriteSavedChangesAndCanRetry() =
        runBlocking {
            val savedMove =
                QueuedBudgetReorder(
                    budgetId = "budget-1",
                    scopeId = "group-1",
                    kind = BudgetReorderKind.CATEGORY,
                    itemId = "category-1",
                    targetId = null,
                    desiredOrder = listOf("category-1"),
                    encryptionPassword = null,
                    operationId = "operation-1",
                )
            val savedState = BudgetReorderQueueState().enqueue(savedMove)
            val persistence =
                InMemoryBudgetMutationQueuePersistence(
                    items = listOf(budgetMutationItem(savedMove)),
                    metadataJson = encodeBudgetReorderQueueMetadata(savedState),
                    readFailuresRemaining = 1,
                )
            val session =
                TandemMobileSession(
                    apiClient = TandemApiClient(),
                    database = null,
                    nowEpochMillis = { 10L },
                    budgetMutationQueuePersistenceOverride = persistence,
                )

            assertFalse(session.restoreBudgetReorderQueue())
            assertEquals(0, persistence.replaceCount)
            assertEquals(listOf("operation-1"), persistence.items.map { it.operationId })

            assertTrue(session.restoreBudgetReorderQueue())
            assertEquals(listOf("operation-1"), session.budgetReorderMovesFor("budget-1", "group-1").map { it.operationId })
        }

    @Test
    fun restoresBudgetMutationOrderFromMetadataInsteadOfDeviceClock() =
        runBlocking {
            val firstMove =
                QueuedBudgetReorder(
                    budgetId = "budget-1",
                    scopeId = "group-1",
                    kind = BudgetReorderKind.CATEGORY,
                    itemId = "category-1",
                    targetId = null,
                    desiredOrder = listOf("category-1", "category-2"),
                    encryptionPassword = null,
                    operationId = "first-operation",
                )
            val secondMove =
                firstMove.copy(
                    itemId = "category-2",
                    operationId = "second-operation",
                )
            val savedState = BudgetReorderQueueState().enqueue(firstMove).enqueue(secondMove)
            val persistence =
                InMemoryBudgetMutationQueuePersistence(
                    items =
                        listOf(
                            budgetMutationItem(secondMove).copy(createdAtEpochMs = 1L),
                            budgetMutationItem(firstMove).copy(createdAtEpochMs = 2L),
                        ),
                    metadataJson = encodeBudgetReorderQueueMetadata(savedState),
                )
            val session =
                TandemMobileSession(
                    apiClient = TandemApiClient(),
                    database = null,
                    nowEpochMillis = { 10L },
                    budgetMutationQueuePersistenceOverride = persistence,
                )

            assertTrue(session.restoreBudgetReorderQueue())

            assertEquals(
                listOf("first-operation", "second-operation"),
                session.budgetReorderMovesFor("budget-1", "group-1").map { it.operationId },
            )
        }

    @Test
    fun budgetMutationIsPersistedBeforeEnqueueReturnsAndRestoresInANewSession() =
        runBlocking {
            val persistence = InMemoryBudgetMutationQueuePersistence()
            val originalSession =
                TandemMobileSession(
                    apiClient = TandemApiClient(),
                    database = null,
                    nowEpochMillis = { 10L },
                    budgetMutationQueuePersistenceOverride = persistence,
                )

            val localGroupId = originalSession.enqueueBudgetCategoryGroupCreate("budget-1", "Travel", false, emptyList(), null)

            assertTrue(localGroupId != null)
            assertEquals(1, persistence.items.size)
            assertEquals("CREATE_CATEGORY_GROUP", persistence.items.single().action)

            val restartedSession =
                TandemMobileSession(
                    apiClient = TandemApiClient(),
                    database = null,
                    nowEpochMillis = { 20L },
                    budgetMutationQueuePersistenceOverride = persistence,
                )
            assertTrue(restartedSession.restoreBudgetReorderQueue())
            assertEquals(1, restartedSession.budgetReorderMovesFor("budget-1", BUDGET_GROUP_ORDER_SCOPE).size)
        }

    @Test
    fun rejectedCategoryRenameCanBeCorrectedAndQueuedWithoutAConnection() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                val rejectedRename =
                    QueuedBudgetReorder(
                        budgetId = "budget-1",
                        scopeId = "category-groceries",
                        kind = BudgetReorderKind.RENAME_CATEGORY,
                        itemId = "groceries",
                        targetId = null,
                        desiredOrder = emptyList(),
                        encryptionPassword = null,
                        name = "Household",
                        operationId = "rejected-rename",
                    )
                val laterChange =
                    QueuedBudgetReorder(
                        budgetId = "budget-1",
                        scopeId = "utilities",
                        kind = BudgetReorderKind.HIDE_CATEGORY,
                        itemId = "phone",
                        targetId = null,
                        desiredOrder = listOf("phone"),
                        encryptionPassword = null,
                        operationId = "later-change",
                    )
                val initialQueue = BudgetReorderQueueState().enqueue(rejectedRename).enqueue(laterChange)
                repository.replaceBudgetMutationQueue(
                    listOf(budgetMutationItem(rejectedRename), budgetMutationItem(laterChange)),
                    encodeBudgetReorderQueueMetadata(initialQueue),
                )
                assertTrue(repository.beginBudgetMutation(rejectedRename.operationId))
                repository.markBudgetMutationRejected(rejectedRename.operationId, "operation_rejected")
                val afterRejection = initialQueue.failHead(rejectedRename, "Actual rejected the name.")
                repository.replaceBudgetMutationQueue(
                    listOf(budgetMutationItem(laterChange).copy(createdAtEpochMs = 101L)),
                    encodeBudgetReorderQueueMetadata(afterRejection),
                )
                val session = TandemMobileSession(TandemApiClient(), database, { 200L })

                assertTrue(
                    session.correctRejectedCategoryRename(
                        budgetId = "budget-1",
                        operationId = rejectedRename.operationId,
                        correctedName = "Home",
                        encryptionPassword = null,
                    ),
                )

                val pending = repository.readPendingWrites("budget-1")
                val replacement = pending.single { it.status == "queued" && it.action == "RENAME_CATEGORY" }
                val corrected = decodeBudgetReorderQueue(replacement.patchJson).moves.single()
                assertEquals("superseded", pending.single { it.operationId == rejectedRename.operationId }.status)
                assertEquals("Home", corrected.name)
                assertTrue(corrected.refreshBeforeSend)
                assertTrue(replacement.operationId != rejectedRename.operationId)
                assertEquals(
                    listOf(replacement.operationId, laterChange.operationId),
                    session.pendingBudgetMutationsFor("budget-1").map { it.operationId },
                )
            } finally {
                database.close()
            }
        }

    @Test
    fun waitingTransactionEditUsesTheSpecificSyncMessage() {
        assertEquals(
            "This transaction is waiting to sync. Try again after it syncs.",
            pendingTransactionEditMessage(),
        )
        assertEquals(
            "This transaction’s status is unknown. Check Pending Changes before editing it again.",
            blockedTransactionEditMessage("uncertain"),
        )
        assertEquals(
            "This transaction is syncing. Wait for it to finish before editing.",
            blockedTransactionEditMessage("sending"),
        )
    }

    @Test
    fun revertingToActualDiscardsOnlyAnUnsentQueuedUpdate() {
        val queuedUpdate = pendingTransactionWrite()

        assertEquals("operation-1", queuedTransactionEditToDiscardForNoOp(listOf(queuedUpdate), editingLocalCreate = false))
        assertNull(queuedTransactionEditToDiscardForNoOp(listOf(queuedUpdate), editingLocalCreate = true))
        assertNull(
            queuedTransactionEditToDiscardForNoOp(
                listOf(queuedUpdate.copy(serverOperationId = "server-operation-1")),
                editingLocalCreate = false,
            ),
        )
        assertNull(
            queuedTransactionEditToDiscardForNoOp(
                listOf(queuedUpdate.copy(status = "sending")),
                editingLocalCreate = false,
            ),
        )
    }

    @Test
    fun localCreateCanBeEditedAfterPreSubmissionFailureButNotWhileSentOrUncertain() {
        val queuedCreate = pendingTransactionWrite(action = "CREATE").copy(attemptCount = 1)

        assertEquals(queuedCreate.operationId, queuedTransactionCreateToEdit(listOf(queuedCreate))?.operationId)
        assertNull(queuedTransactionCreateToEdit(listOf(queuedCreate.copy(status = "sending"))))
        assertNull(queuedTransactionCreateToEdit(listOf(queuedCreate.copy(status = "uncertain"))))
        assertNull(
            queuedTransactionCreateToEdit(listOf(queuedCreate.copy(serverOperationId = "server-operation-1"))),
        )
    }

    @Test
    fun transactionEditPatchIncludesPayeeCategoryDateAndAccountChanges() {
        val base =
            Json
                .parseToJsonElement(
                    """{"account":"account-1","amount":-1200,"date":"2026-11-01","payee":"payee-1","category":"category-1","notes":"before"}""",
                ).jsonObject

        val patch =
            buildTransactionEditPatch(
                base = base,
                signedAmount = -1_200,
                fallbackCurrentAmount = -1_200,
                notes = "after",
                details = TransactionEditDetails("account-2", "payee-2", "category-2", "2026-11-05"),
            )

        assertEquals("account-2", patch.getValue("account").jsonPrimitive.content)
        assertEquals("payee-2", patch.getValue("payee").jsonPrimitive.content)
        assertEquals("category-2", patch.getValue("category").jsonPrimitive.content)
        assertEquals("2026-11-05", patch.getValue("date").jsonPrimitive.content)
        assertEquals("after", patch.getValue("notes").jsonPrimitive.content)
    }

    @Test
    fun transactionEditPatchCanClearOptionalFields() {
        val base =
            Json
                .parseToJsonElement(
                    """{"account":"account-1","amount":100,"date":"2026-11-01","payee":"payee-1","category":"category-1"}""",
                ).jsonObject

        val patch =
            buildTransactionEditPatch(
                base = base,
                signedAmount = 100,
                fallbackCurrentAmount = 0,
                notes = "",
                details = TransactionEditDetails("account-1", null, null, "2026-11-01"),
            )

        assertEquals(
            JsonObject(mapOf("payee" to kotlinx.serialization.json.JsonNull, "category" to kotlinx.serialization.json.JsonNull)),
            patch,
        )
    }

    @Test
    fun transactionWritesRejectAmountsAboveTheApiLimitBeforeStorageAccess() =
        runBlocking {
            val session =
                TandemMobileSession(
                    apiClient = TandemApiClient(),
                    database = null,
                    nowEpochMillis = { 0L },
                )
            val overLimit = formatAmount(MAX_TRANSACTION_AMOUNT_MINOR_UNITS + 1)

            session.updateTransaction("budget-1", "transaction-1", overLimit, "note", -100, "Spending")
            assertTrue(session.transactionWriteMessage.orEmpty().startsWith("Largest amount is"))

            assertFalse(
                session.createTransaction(
                    "budget-1",
                    "account-1",
                    null,
                    null,
                    overLimit,
                    "2026-05-14",
                    "note",
                    null,
                ),
            )
            assertTrue(session.transactionWriteMessage.orEmpty().startsWith("Largest amount is"))
        }

    @Test
    fun newAndEditedTransactionWritesEnforceMinimumAndConfirmFutureDatesBeforeStorageAccess() =
        runBlocking {
            val session =
                TandemMobileSession(
                    apiClient = TandemApiClient(),
                    database = null,
                    nowEpochMillis = { 0L },
                    currentDateIso = { "2026-11-10" },
                )

            assertFalse(
                session.createTransaction(
                    "budget-1",
                    "account-1",
                    null,
                    null,
                    "1.00",
                    "1990-01-01",
                    "note",
                    null,
                ),
            )
            assertEquals("Choose a date on or after ${displayTransactionDate("1995-01-01")}.", session.transactionWriteMessage)

            assertFalse(
                session.updateTransaction(
                    budgetId = "budget-1",
                    transactionId = "transaction-1",
                    amountText = "1.00",
                    notes = "note",
                    originalAmountMinor = -100,
                    transactionType = "Spending",
                    accountId = "account-1",
                    date = "1990-01-01",
                    updateDetails = true,
                ),
            )
            assertEquals("Choose a date on or after ${displayTransactionDate("1995-01-01")}.", session.transactionWriteMessage)

            for (futureDate in listOf("2036-11-11", "2062-03-04")) {
                for (create in listOf(true, false)) {
                    if (create) {
                        assertFalse(
                            session.createTransaction(
                                "budget-1",
                                "account-1",
                                null,
                                null,
                                "1.00",
                                futureDate,
                                "note",
                                null,
                            ),
                        )
                    } else {
                        assertFalse(
                            session.updateTransaction(
                                budgetId = "budget-1",
                                transactionId = "transaction-1",
                                amountText = "1.00",
                                notes = "note",
                                originalAmountMinor = -100,
                                transactionType = "Spending",
                                accountId = "account-1",
                                date = futureDate,
                                updateDetails = true,
                            ),
                        )
                    }
                    assertEquals("Confirm this future date before saving.", session.transactionWriteMessage)
                }
            }

            assertFalse(
                session.createTransaction(
                    "budget-1",
                    "account-1",
                    null,
                    null,
                    "1.00",
                    "2036-11-11",
                    "note",
                    null,
                    confirmedFutureDate = "2036-11-11",
                ),
            )
            assertEquals("Budget data isn’t available on this device. Reopen the budget and try again.", session.transactionWriteMessage)

            assertFalse(
                session.updateTransaction(
                    budgetId = "budget-1",
                    transactionId = "transaction-1",
                    amountText = "1.00",
                    notes = "note",
                    originalAmountMinor = -100,
                    transactionType = "Spending",
                    accountId = "account-1",
                    date = "2027-11-11",
                    updateDetails = true,
                    confirmedFutureDate = "2027-11-11",
                ),
            )
            assertEquals("Budget data isn’t available on this device. Reopen the budget and try again.", session.transactionWriteMessage)

            assertFalse(
                session.createTransaction(
                    "budget-1",
                    "account-1",
                    null,
                    null,
                    "1.00",
                    "2062-03-04",
                    "note",
                    null,
                    confirmedFutureDate = "2062-03-04",
                ),
            )
            assertEquals("Budget data isn’t available on this device. Reopen the budget and try again.", session.transactionWriteMessage)

            assertFalse(
                session.updateTransaction(
                    budgetId = "budget-1",
                    transactionId = "transaction-1",
                    amountText = "1.00",
                    notes = "note",
                    originalAmountMinor = -100,
                    transactionType = "Spending",
                    accountId = "account-1",
                    date = "2062-03-04",
                    updateDetails = true,
                    confirmedFutureDate = "2062-03-04",
                ),
            )
            assertEquals("Budget data isn’t available on this device. Reopen the budget and try again.", session.transactionWriteMessage)
        }

    @Test
    fun readOnlyModeBlocksEveryTransactionWriteEntryPoint() =
        runBlocking {
            val session =
                TandemMobileSession(
                    apiClient = TandemApiClient(),
                    database = null,
                    nowEpochMillis = { 0L },
                )
            session.setReadOnlyMode(true)
            val readOnlyMessage = "Read-only mode is on. Turn it off before changing this data."

            session.updateTransaction("budget-1", "transaction-1", "1.00", "note", -100, "Spending")
            assertEquals(readOnlyMessage, session.transactionWriteMessage)

            assertFalse(
                session.createTransaction(
                    "budget-1",
                    "account-1",
                    null,
                    null,
                    "1.00",
                    "2026-05-14",
                    "note",
                    null,
                ),
            )
            assertEquals(readOnlyMessage, session.transactionWriteMessage)

            session.resolveConflict("budget-1", "operation-1", true, null, reviewedRevision = "revision-1")
            assertEquals(readOnlyMessage, session.transactionWriteMessage)

            session.discardWrite("budget-1", "operation-1")
            assertEquals(readOnlyMessage, session.transactionWriteMessage)

            session.retryUncertainWrite("budget-1", "operation-1", null)
            assertEquals(readOnlyMessage, session.transactionWriteMessage)
        }

    @Test
    fun retryKeepsSyncAttentionVisibleUntilSuccess() {
        val previousFailure = SyncPresentationState().failed("Could not sync this budget.")

        val retrying = previousFailure.begin()

        assertTrue(retrying.isSyncing)
        assertEquals("Could not sync this budget.", retrying.error)
        assertEquals(SyncPresentationState(), retrying.succeeded())
    }

    @Test
    fun stoppingACancelledRetryKeepsThePreviousSyncAttention() {
        val previousFailure = SyncPresentationState().failed("Could not sync this budget.")

        val stopped = previousFailure.begin().stopped()

        assertFalse(stopped.isSyncing)
        assertEquals(previousFailure.error, stopped.error)
    }

    @Test
    fun restoringStoredDeviceSessionDoesNotWaitForTheRemoteBudgetCatalog() =
        runBlocking {
            val savedSession =
                """{"serverUrl":"https://budget.example.test","accessToken":"saved-token","device":{"deviceId":"device-1","principalId":"person-1","budgetIds":["budget-1"]}}"""
            val credentialStore =
                object : DeviceCredentialStore {
                    override fun read(): String = savedSession

                    override fun write(value: String) = Unit

                    override fun clear() = Unit
                }
            val session =
                TandemMobileSession(
                    apiClient = TandemApiClient(),
                    database = null,
                    nowEpochMillis = { 0L },
                    credentialStore = credentialStore,
                )

            assertEquals("https://budget.example.test", session.restoreStoredSession())
            assertTrue(session.isAuthenticated)
            assertTrue(session.budgets.isEmpty())
            assertFalse(session.budgetCatalogLoaded)
        }

    @Test
    fun transientCredentialReadFailureKeepsStoredCredentialAndCanBeRetried() =
        runBlocking {
            var readAttempts = 0
            var clearCalls = 0
            val credentialStore =
                object : DeviceCredentialStore {
                    override fun read(): String? {
                        readAttempts += 1
                        if (readAttempts == 1) error("Keychain is temporarily unavailable")
                        return null
                    }

                    override fun write(value: String) = Unit

                    override fun clear() {
                        clearCalls += 1
                    }
                }
            val session =
                TandemMobileSession(
                    apiClient = TandemApiClient(),
                    database = null,
                    nowEpochMillis = { 0L },
                    credentialStore = credentialStore,
                )

            assertNull(session.restoreStoredSession())
            assertEquals(0, clearCalls)
            assertFalse(session.isCredentialStored)
            assertTrue(session.credentialStorageError.orEmpty().contains("Try again"))

            assertNull(session.restoreStoredSession())
            assertEquals(2, readAttempts)
            assertEquals(0, clearCalls)
            assertNull(session.credentialStorageError)
        }

    @Test
    fun forgettingThisDeviceLocallyClearsTheSessionAndSavedCredential() =
        runBlocking {
            val savedSession =
                """{"serverUrl":"https://budget.example.test","accessToken":"saved-token","device":{"deviceId":"device-1","principalId":"person-1","budgetIds":["budget-1"]}}"""
            var clearCalls = 0
            val credentialStore =
                object : DeviceCredentialStore {
                    override fun read(): String = savedSession

                    override fun write(value: String) = Unit

                    override fun clear() {
                        clearCalls += 1
                    }
                }
            val apiClient = TandemApiClient()
            val session =
                TandemMobileSession(
                    apiClient = apiClient,
                    database = null,
                    nowEpochMillis = { 0L },
                    credentialStore = credentialStore,
                )

            session.restoreStoredSession()
            assertTrue(session.authenticatedDevice != null)
            assertTrue(apiClient.isAuthenticated)
            assertTrue(session.isCredentialStored)

            session.forgetDeviceLocally()

            assertNull(session.authenticatedDevice)
            assertFalse(apiClient.isAuthenticated)
            assertNull(apiClient.currentServerUrl)
            assertFalse(session.isCredentialStored)
            assertNull(session.deviceRevocationError)
            assertEquals(1, clearCalls)
        }

    @Test
    fun changingPrincipalWithUnresolvedWritesRestoresThePreviousIdentity() =
        runBlocking {
            val database = newDatabase()
            try {
                val preferences = AppPreferencesStore(database)
                val repository = LocalBudgetRepository(database) { 1_000L }
                seedCachedBudget(repository, preferences, withPendingWrite = true)
                val oldSession = storedSession("https://old.example.test", "principal-old")
                val credentialStore = memoryCredentialStore(oldSession)
                val apiClient = TandemApiClient()
                val session = TandemMobileSession(apiClient, database, { 1_000L }, credentialStore)

                session.restoreStoredSession()
                val newDevice = apiClient.restoreStoredSession(storedSession("https://old.example.test", "principal-new"))

                assertFalse(session.acceptAuthenticatedIdentity(newDevice, oldSession))

                assertEquals("https://old.example.test", apiClient.currentServerUrl)
                assertEquals("principal-old", session.authenticatedDevice?.principalId)
                assertEquals(listOf("budget-1"), session.loadCachedBudgets().map { it.budgetId })
                assertTrue(repository.hasUnresolvedWrites())
                assertNotNull(repository.findRecord("budget-1", "transaction", "txn-1"))
                assertTrue(session.signInError.orEmpty().contains("before switching"))
            } finally {
                database.close()
            }
        }

    @Test
    fun changingServerWithTheSamePrincipalIsRejectedWhileWritesRemain() =
        runBlocking {
            val database = newDatabase()
            try {
                val preferences = AppPreferencesStore(database)
                val repository = LocalBudgetRepository(database) { 1_000L }
                seedCachedBudget(repository, preferences, withPendingWrite = true)
                val oldSession = storedSession("https://old.example.test", "principal-old")
                val apiClient = TandemApiClient()
                val session = TandemMobileSession(apiClient, database, { 1_000L })
                session.restoreStoredSession()
                val newDevice = apiClient.restoreStoredSession(storedSession("https://new.example.test", "principal-old"))

                assertFalse(session.acceptAuthenticatedIdentity(newDevice, oldSession))

                assertEquals("https://old.example.test", apiClient.currentServerUrl)
                assertEquals("principal-old", session.authenticatedDevice?.principalId)
                assertTrue(repository.hasUnresolvedWrites())
            } finally {
                database.close()
            }
        }

    @Test
    fun changingIdentityWithoutUnresolvedWritesClearsThePreviousCacheBeforeAdopting() =
        runBlocking {
            val database = newDatabase()
            try {
                val preferences = AppPreferencesStore(database)
                val repository = LocalBudgetRepository(database) { 1_000L }
                seedCachedBudget(repository, preferences, withPendingWrite = false)
                val apiClient = TandemApiClient()
                val session = TandemMobileSession(apiClient, database, { 1_000L })
                val oldSession = storedSession("https://old.example.test", "principal-old")
                apiClient.restoreStoredSession(oldSession)
                val newDevice = apiClient.restoreStoredSession(storedSession("https://new.example.test", "principal-new"))

                assertTrue(session.acceptAuthenticatedIdentity(newDevice, oldSession))

                assertEquals("https://new.example.test", apiClient.currentServerUrl)
                assertEquals("principal-new", session.authenticatedDevice?.principalId)
                assertTrue(repository.findCachedBudgetIds().isEmpty())
                assertNull(repository.findRecord("budget-1", "transaction", "txn-1"))
                assertEquals(emptyList(), session.loadCachedBudgets())
                assertEquals(
                    """{"serverUrl":"https://new.example.test","principalId":"principal-new"}""",
                    preferences.read()["internal:budget-cache-owner-v1"],
                )
            } finally {
                database.close()
            }
        }

    @Test
    fun restoringSavedIdentityAdoptsLegacyCacheOwnerWithoutLosingQueuedWrites() =
        runBlocking {
            val database = newDatabase()
            try {
                val preferences = AppPreferencesStore(database)
                val repository = LocalBudgetRepository(database) { 1_000L }
                seedCachedBudget(repository, preferences, withPendingWrite = true, saveOwner = false)
                val credential = storedSession("https://old.example.test", "principal-old")
                val session =
                    TandemMobileSession(
                        TandemApiClient(),
                        database,
                        { 1_000L },
                        memoryCredentialStore(credential),
                    )

                assertEquals("https://old.example.test", session.restoreStoredSession())

                assertTrue(repository.hasUnresolvedWrites())
                assertNotNull(repository.findRecord("budget-1", "transaction", "txn-1"))
                assertEquals(listOf("budget-1"), session.cachedBudgets.map { it.budgetId })
                assertEquals(
                    cacheOwner("https://old.example.test", "principal-old"),
                    preferences.read()["internal:budget-cache-owner-v1"],
                )
            } finally {
                database.close()
            }
        }

    @Test
    fun interactiveSignInCannotClaimUnknownCacheWhileWritesRemain() =
        runBlocking {
            val database = newDatabase()
            try {
                val preferences = AppPreferencesStore(database)
                val repository = LocalBudgetRepository(database) { 1_000L }
                seedCachedBudget(repository, preferences, withPendingWrite = true, saveOwner = false)
                val apiClient = TandemApiClient()
                val session = TandemMobileSession(apiClient, database, { 1_000L })
                val newDevice = apiClient.restoreStoredSession(storedSession("https://new.example.test", "principal-new"))

                assertFalse(session.acceptAuthenticatedIdentity(newDevice, previousSession = null))

                assertFalse(apiClient.isAuthenticated)
                assertTrue(repository.hasUnresolvedWrites())
                assertNull(preferences.read()["internal:budget-cache-owner-v1"])
            } finally {
                database.close()
            }
        }

    @Test
    fun cachedBudgetReadsAreHiddenWhenAuthenticatedIdentityDoesNotOwnTheCache() =
        runBlocking {
            val database = newDatabase()
            try {
                val preferences = AppPreferencesStore(database)
                val repository = LocalBudgetRepository(database) { 1_000L }
                seedCachedBudget(repository, preferences, withPendingWrite = false)
                val apiClient = TandemApiClient()
                val session = TandemMobileSession(apiClient, database, { 1_000L })
                val restoredDevice = apiClient.restoreStoredSession(storedSession("https://old.example.test", "principal-old"))
                session.acceptAuthenticatedIdentity(restoredDevice, null)
                preferences.putValue(
                    "internal:budget-cache-owner-v1",
                    cacheOwner("https://new.example.test", "principal-new"),
                )

                assertTrue(session.loadCachedBudgets().isEmpty())
                session.loadLocalRecords("budget-1")

                assertFalse(session.hasCachedSnapshot)
                assertTrue(session.transactionRecords.isEmpty())
                assertNotNull(repository.findRecord("budget-1", "transaction", "txn-1"))
                Unit
            } finally {
                database.close()
            }
        }

    @Test
    fun forgettingLocallyKeepsUnresolvedRowsAndExplainsTheyStayOnDevice() =
        runBlocking {
            val database = newDatabase()
            try {
                val preferences = AppPreferencesStore(database)
                val repository = LocalBudgetRepository(database) { 1_000L }
                seedCachedBudget(repository, preferences, withPendingWrite = true)
                val credentialStore = memoryCredentialStore(storedSession("https://old.example.test", "principal-old"))
                val apiClient = TandemApiClient()
                val session = TandemMobileSession(apiClient, database, { 1_000L }, credentialStore)
                session.restoreStoredSession()

                session.forgetDeviceLocally()

                assertFalse(apiClient.isAuthenticated)
                assertTrue(repository.hasUnresolvedWrites())
                assertNotNull(repository.findRecord("budget-1", "transaction", "txn-1"))
                assertTrue(pendingDeviceChangeNotice().contains("stay on this device"))
                assertTrue(pendingDeviceChangeNotice().contains("same server and account"))
            } finally {
                database.close()
            }
        }

    @Test
    fun signingOutRevokesTheServerDeviceBeforeClearingLocalData() =
        runBlocking {
            val database = newDatabase()
            try {
                val preferences = AppPreferencesStore(database)
                val repository = LocalBudgetRepository(database) { 1_000L }
                seedCachedBudget(repository, preferences, withPendingWrite = false)
                var savedCredential: String? = storedSession("https://old.example.test", "principal-old")
                val credentialStore =
                    object : DeviceCredentialStore {
                        override fun read(): String? = savedCredential

                        override fun write(value: String) {
                            savedCredential = value
                        }

                        override fun clear() {
                            savedCredential = null
                        }
                    }
                var revokeCalls = 0
                val apiClient = TandemApiClient()
                val session =
                    TandemMobileSession(
                        apiClient,
                        database,
                        { 1_000L },
                        credentialStore,
                        revokeCurrentDeviceForSignOut = {
                            revokeCalls += 1
                            assertNotNull(savedCredential)
                            assertTrue(repository.findCachedBudgetIds().isNotEmpty())
                        },
                    )
                session.restoreStoredSession()

                assertTrue(session.signOutAndClearLocalData())

                assertEquals(1, revokeCalls)
                assertNull(savedCredential)
                assertFalse(apiClient.isAuthenticated)
                assertTrue(repository.findCachedBudgetIds().isEmpty())
                assertEquals("", preferences.read()["internal:budget-cache-owner-v1"])
                assertNull(session.signOutNotice)
            } finally {
                database.close()
            }
        }

    @Test
    fun signingOutOfflineClearsLocalDataAndExplainsHowToRevokeTheServerDevice() =
        runBlocking {
            val database = newDatabase()
            try {
                val preferences = AppPreferencesStore(database)
                val repository = LocalBudgetRepository(database) { 1_000L }
                seedCachedBudget(repository, preferences, withPendingWrite = false)
                var savedCredential: String? = storedSession("https://old.example.test", "principal-old")
                val credentialStore =
                    object : DeviceCredentialStore {
                        override fun read(): String? = savedCredential

                        override fun write(value: String) {
                            savedCredential = value
                        }

                        override fun clear() {
                            savedCredential = null
                        }
                    }
                val apiClient = TandemApiClient()
                val session =
                    TandemMobileSession(
                        apiClient,
                        database,
                        { 1_000L },
                        credentialStore,
                        revokeCurrentDeviceForSignOut = { throw TandemApiException("network-error") },
                    )
                session.restoreStoredSession()

                assertTrue(session.signOutAndClearLocalData())

                assertNull(savedCredential)
                assertFalse(apiClient.isAuthenticated)
                assertTrue(repository.findCachedBudgetIds().isEmpty())
                assertTrue(session.cachedBudgets.isEmpty())
                assertTrue(session.signOutNotice.orEmpty().contains("server still trusts this device"))
                assertTrue(session.signOutNotice.orEmpty().contains("Sign in again to this server"))
            } finally {
                database.close()
            }
        }

    private fun newDatabase(): TandemDatabase =
        Room
            .inMemoryDatabaseBuilder<TandemDatabase>()
            .setDriver(AndroidSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

    private suspend fun seedCachedBudget(
        repository: LocalBudgetRepository,
        preferences: AppPreferencesStore,
        withPendingWrite: Boolean,
        saveOwner: Boolean = true,
    ) {
        repository.applyFullSnapshot(
            budgetId = "budget-1",
            generation = "generation-1",
            cursor = "cursor-1",
            expectedCheckpoint = null,
            records =
                listOf(
                    RemoteBudgetRecord(
                        budgetId = "budget-1",
                        recordType = "transaction",
                        recordId = "txn-1",
                        revision = "revision-1",
                        bodyJson = """{"id":"txn-1","amount":1000,"account":"checking"}""",
                        generation = "generation-1",
                    ),
                ),
        )
        if (saveOwner) {
            preferences.putValue("internal:budget-cache-owner-v1", cacheOwner("https://old.example.test", "principal-old"))
        }
        if (withPendingWrite) {
            repository.enqueueWrite(
                LocalWriteRequest(
                    operationId = "edit-1",
                    idempotencyKey = "edit-key-1",
                    budgetId = "budget-1",
                    recordType = "transaction",
                    recordId = "txn-1",
                    action = LocalWriteAction.UPDATE,
                    baseRevision = "revision-1",
                    baseSnapshotJson = """{"id":"txn-1","amount":1000,"account":"checking"}""",
                    patchJson = """{"amount":1100}""",
                    optimisticJson = """{"id":"txn-1","amount":1100,"account":"checking"}""",
                    createdAtEpochMs = 1_000L,
                    optimisticProjection =
                        LocalRecordProjection(
                            recordDate = "2026-11-01",
                            accountId = "checking",
                            categoryId = null,
                            payeeId = null,
                            amountMinor = 1100L,
                            budgetMonth = null,
                        ),
                ),
            )
        }
    }

    private fun memoryCredentialStore(initial: String) =
        object : DeviceCredentialStore {
            private var value: String? = initial

            override fun read(): String? = value

            override fun write(value: String) {
                this.value = value
            }

            override fun clear() {
                value = null
            }
        }

    private fun storedSession(
        serverUrl: String,
        principalId: String,
    ): String =
        """{"serverUrl":"$serverUrl","accessToken":"saved-token","device":{"deviceId":"device-1","principalId":"$principalId","budgetIds":["budget-1"]}}"""

    private fun cacheOwner(
        serverUrl: String,
        principalId: String,
    ): String = """{"serverUrl":"$serverUrl","principalId":"$principalId"}"""

    private fun activityTransaction(
        id: String,
        date: String,
        amountMinor: Long,
    ) = BudgetRecordEntity(
        budgetId = "budget-1",
        recordType = "transaction",
        recordId = id,
        remoteJson = "{}",
        remoteRevision = "1",
        localJson = null,
        localDeleted = false,
        remoteDeleted = false,
        syncGeneration = "generation-1",
        recordDate = date,
        accountId = "account-1",
        categoryId = null,
        payeeId = null,
        amountMinor = amountMinor,
        budgetMonth = date.take(7),
    )

    private fun pendingTransactionWrite(
        action: String = "UPDATE",
        status: String = "queued",
        serverOperationId: String? = null,
    ) = OutboxOperationEntity(
        operationId = "operation-1",
        idempotencyKey = "key-1",
        budgetId = "budget-1",
        recordType = "transaction",
        recordId = "transaction-1",
        action = action,
        baseRevision = "revision-1",
        baseSnapshotJson = "{}",
        patchJson = "{}",
        optimisticJson = "{}",
        optimisticRecordDate = null,
        optimisticAccountId = null,
        optimisticCategoryId = null,
        optimisticPayeeId = null,
        optimisticAmountMinor = null,
        optimisticBudgetMonth = null,
        status = status,
        attemptCount = 0,
        serverOperationId = serverOperationId,
        lastErrorCode = null,
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
    )

    private fun budgetMutationItem(move: QueuedBudgetReorder) =
        LocalBudgetMutationOutboxItem(
            operationId = move.operationId,
            budgetId = move.budgetId,
            action = move.kind.name,
            recordId = move.itemId,
            patchJson = encodeBudgetReorderQueue(BudgetReorderQueueState().enqueue(move)),
            createdAtEpochMs = 1L,
        )

    private class InMemoryBudgetMutationQueuePersistence(
        var items: List<LocalBudgetMutationOutboxItem> = emptyList(),
        var metadataJson: String? = null,
        var readFailuresRemaining: Int = 0,
    ) : BudgetMutationQueuePersistence {
        var replaceCount = 0
            private set

        override suspend fun read(): LocalBudgetMutationQueueSnapshot {
            if (readFailuresRemaining > 0) {
                readFailuresRemaining -= 1
                error("temporary local read failure")
            }
            return LocalBudgetMutationQueueSnapshot(items.map(::toEntity), metadataJson)
        }

        override suspend fun replace(
            items: List<LocalBudgetMutationOutboxItem>,
            metadataJson: String,
            supersededOperationIds: Set<String>,
        ) {
            replaceCount += 1
            this.items = items
            this.metadataJson = metadataJson
        }

        private fun toEntity(item: LocalBudgetMutationOutboxItem) =
            OutboxOperationEntity(
                operationId = item.operationId,
                idempotencyKey = "budget-mutation:${item.operationId}",
                budgetId = item.budgetId,
                recordType = "budget-mutation",
                recordId = item.recordId,
                action = item.action,
                baseRevision = null,
                baseSnapshotJson = null,
                patchJson = item.patchJson,
                optimisticJson = null,
                optimisticRecordDate = null,
                optimisticAccountId = null,
                optimisticCategoryId = null,
                optimisticPayeeId = null,
                optimisticAmountMinor = null,
                optimisticBudgetMonth = null,
                status = "queued",
                attemptCount = 0,
                serverOperationId = null,
                lastErrorCode = null,
                createdAtEpochMs = item.createdAtEpochMs,
                updatedAtEpochMs = item.createdAtEpochMs,
            )
    }
}
