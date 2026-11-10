package app.tandemforactual.api.client

import app.tandemforactual.api.generated.Api
import app.tandemforactual.api.generated.Auth
import app.tandemforactual.api.generated.api.AuthenticationApi
import app.tandemforactual.api.generated.api.BudgetsApi
import app.tandemforactual.api.generated.api.TransactionsApi
import app.tandemforactual.api.generated.models.ApiV1HealthReady200Response
import arrow.core.Either
import com.kroegerama.openapi.kmp.gen.companion.AuthItem
import com.kroegerama.openapi.kmp.gen.companion.CallException
import com.kroegerama.openapi.kmp.gen.companion.HttpCallException
import com.kroegerama.openapi.kmp.gen.companion.HttpCallResponse
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.pluginOrNull
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.uuid.Uuid

data class AuthenticatedDevice(
    val deviceId: String,
    val principalId: String,
    val budgetIds: List<String>,
)

data class BudgetCatalogItem(
    val budgetId: String,
    val name: String,
    val isEncrypted: Boolean,
)

data class ActualLoginMethodOption(
    val method: String,
    val displayName: String,
    val active: Boolean,
)

data class ActualLoginMethods(
    val bootstrapped: Boolean,
    val loginMethod: String?,
    val availableLoginMethods: List<ActualLoginMethodOption>,
)

data class StatementImportItem(
    val date: String,
    val amountMinor: Long,
    val payeeName: String,
    val notes: String?,
    val importedId: String,
)

data class StatementImportSummary(
    val added: Int,
    val updated: Int,
    val errorCount: Int,
)

class TandemApiException(
    val errorCode: String,
    val httpStatus: Int? = null,
    cause: Throwable? = null,
    safeMessageOverride: String? = null,
) : Exception(
        safeMessageOverride ?: when {
            errorCode == "sign_in_required" -> "Sign in to load your budgets."
            errorCode == "sign_in_expired" -> "Sign in expired. Start again."
            errorCode == "invalid_credentials" -> "The password didn’t match this Actual server."
            errorCode == "invalid_device_credential" -> "Sign in again to continue."
            errorCode == "bank_sync_failed" -> "Actual couldn’t complete bank sync. Check its provider setup and try again."
            errorCode == "bank_sync_busy" -> "A bank sync is already running. Try again shortly."
            errorCode == "duplicate_imported_id" ->
                "This statement has repeated reference values. " +
                    "Choose a unique reference column and try again."
            errorCode == "actual_operation_failed" -> "Actual couldn’t complete this change. Check the budget and try again."
            errorCode == "operation_uncertain" -> "Tandem couldn’t confirm whether Actual saved this change. Check Actual, then try again."
            errorCode == "operation_in_progress" -> "The server is still processing this change. Wait a moment, then try again."
            errorCode == "operation_rejected" -> "Actual rejected this change earlier. Refresh the budget, then try again."
            errorCode == "idempotency_key_reused" ->
                "Tandem matched this change to a different request. Refresh the budget, then try again."
            errorCode == "operation_result_unavailable" ->
                "Actual saved this change, but Tandem needs a fresh budget copy. Refresh the budget."
            errorCode == "invalid_request" -> "Check the details and try again."
            httpStatus == 400 -> "The server rejected this request. Check the details and try again."
            httpStatus == 401 -> "The server couldn’t verify this request. Check the server address and try again."
            httpStatus == 403 -> "This device can’t open that budget."
            httpStatus in 500..599 -> "The Tandem server couldn’t read Actual. Try again later."
            else -> "Couldn’t reach the Tandem server. Check the address and connection."
        },
        cause,
    )

class TandemApiProtocolException(
    message: String,
) : IllegalStateException(message)

/** Owns the generated API client's server address and in-memory device credential. */
class TandemApiClient(
    private val allowInsecurePrivateNetwork: Boolean = false,
) {
    private val sessionMutex = Mutex()
    private val maintenanceKeyMutex = Mutex()
    private val pendingMaintenanceKeys = mutableMapOf<String, String>()
    private var accessToken: String? = null
    private var serverUrl: String? = null
    private var authenticatedDevice: AuthenticatedDevice? = null
    private var activeAuthProvider: Auth.BearerAuth? = null
    internal val bearerAuth =
        Auth.BearerAuth {
            accessToken?.let(AuthItem::Bearer)
        }

    val isAuthenticated: Boolean
        get() = accessToken != null

    val currentServerUrl: String?
        get() = serverUrl

    suspend fun restoreSession(
        serverUrl: String,
        token: String,
    ) {
        sessionMutex.withLock {
            restoreSessionLocked(serverUrl, token)
        }
    }

    fun exportStoredSession(): String? {
        val token = accessToken ?: return null
        val server = serverUrl ?: return null
        val device = authenticatedDevice ?: return null
        return buildJsonObject {
            put("serverUrl", server)
            put("accessToken", token)
            put(
                "device",
                buildJsonObject {
                    put("deviceId", device.deviceId)
                    put("principalId", device.principalId)
                    put("budgetIds", JsonArray(device.budgetIds.map(::JsonPrimitive)))
                },
            )
        }.toString()
    }

    suspend fun restoreStoredSession(value: String): AuthenticatedDevice {
        val root =
            try {
                Json.parseToJsonElement(value).jsonObject
            } catch (_: Exception) {
                throw TandemApiProtocolException("Stored device session is invalid")
            }
        val server = root.requiredString("serverUrl")
        val token = root.requiredString("accessToken")
        val deviceJson =
            root["device"] as? JsonObject
                ?: throw TandemApiProtocolException("Stored device session has no device")
        val device =
            AuthenticatedDevice(
                deviceId = deviceJson.requiredString("deviceId"),
                principalId = deviceJson.requiredString("principalId"),
                budgetIds = deviceJson.requiredStringArray("budgetIds"),
            )
        return sessionMutex.withLock {
            restoreSessionLocked(server, token)
            authenticatedDevice = device
            device
        }
    }

    suspend fun readActualLoginMethods(serverUrl: String): ActualLoginMethods =
        sessionMutex.withLock {
            val parsedUrl = parseServerUrl(serverUrl, allowInsecurePrivateNetwork)
            GeneratedApiAccess.mutex.withLock {
                GeneratedApiAccess.ensureContentEncoding()
                clearGeneratedAuthProvider()
                Api.baseUrl = parsedUrl
                val payload = unwrap(AuthenticationApi.getActualLoginMethods())
                ActualLoginMethods(
                    bootstrapped = payload.requiredBoolean("bootstrapped"),
                    loginMethod = payload.optionalString("loginMethod"),
                    availableLoginMethods =
                        (payload["availableLoginMethods"] as? JsonArray)?.mapNotNull { item ->
                            val option = item as? JsonObject ?: return@mapNotNull null
                            ActualLoginMethodOption(
                                method = option.requiredString("method"),
                                displayName = option.requiredString("displayName"),
                                active = option.requiredBoolean("active"),
                            )
                        } ?: emptyList(),
                )
            }
        }

    suspend fun signInWithPassword(
        serverUrl: String,
        password: String,
        deviceName: String,
    ): AuthenticatedDevice =
        sessionMutex.withLock {
            val parsedUrl = parseServerUrl(serverUrl, allowInsecurePrivateNetwork)
            this.serverUrl = parsedUrl.toString()
            accessToken = null
            authenticatedDevice = null

            val body =
                buildJsonObject {
                    put("password", password)
                    put("deviceName", normalizeDeviceName(deviceName))
                }.toMap()
            GeneratedApiAccess.mutex.withLock {
                GeneratedApiAccess.ensureContentEncoding()
                clearGeneratedAuthProvider()
                Api.baseUrl = parsedUrl
                setGeneratedAuthProvider(bearerAuth)
                val payload =
                    try {
                        unwrap(AuthenticationApi.signInWithActualPassword(body))
                    } catch (error: TandemApiException) {
                        if (error.httpStatus == 400 && error.errorCode == "invalid_request") {
                            throw TandemApiException(
                                error.errorCode,
                                error.httpStatus,
                                error,
                                "Check your password and try again.",
                            )
                        }
                        throw error
                    }
                val deviceId = payload.requiredString("deviceId")
                val principalId = payload.requiredString("principalId")
                val budgetIds = payload.requiredStringArray("budgetIds")
                val token = payload.requiredString("accessToken")
                accessToken = token
                AuthenticatedDevice(deviceId, principalId, budgetIds).also { authenticatedDevice = it }
            }
        }

    suspend fun startActualOpenIdSignIn(
        serverUrl: String,
        deviceName: String,
    ): String =
        sessionMutex.withLock {
            val parsedUrl = parseServerUrl(serverUrl, allowInsecurePrivateNetwork)
            GeneratedApiAccess.mutex.withLock {
                GeneratedApiAccess.ensureContentEncoding()
                clearGeneratedAuthProvider()
                Api.baseUrl = parsedUrl
                val payload =
                    unwrap(
                        AuthenticationApi.startActualOpenIdSignIn(
                            buildJsonObject {
                                put("deviceName", normalizeDeviceName(deviceName))
                            }.toMap(),
                        ),
                    )
                payload.requiredString("redirectUrl")
            }
        }

    suspend fun completeActualOpenIdSignIn(
        serverUrl: String,
        code: String,
    ): AuthenticatedDevice =
        sessionMutex.withLock {
            val parsedUrl = parseServerUrl(serverUrl, allowInsecurePrivateNetwork)
            this.serverUrl = parsedUrl.toString()
            accessToken = null
            authenticatedDevice = null
            GeneratedApiAccess.mutex.withLock {
                GeneratedApiAccess.ensureContentEncoding()
                clearGeneratedAuthProvider()
                Api.baseUrl = parsedUrl
                val payload =
                    unwrap(
                        AuthenticationApi.completeActualOpenIdSignIn(
                            buildJsonObject { put("code", code) }.toMap(),
                        ),
                    )
                val deviceId = payload.requiredString("deviceId")
                val principalId = payload.requiredString("principalId")
                val budgetIds = payload.requiredStringArray("budgetIds")
                val token = payload.requiredString("accessToken")
                accessToken = token
                AuthenticatedDevice(deviceId, principalId, budgetIds).also { authenticatedDevice = it }
            }
        }

    suspend fun listBudgets(): List<BudgetCatalogItem> =
        sessionMutex.withLock {
            withGeneratedApi { parseBudgetCatalog(BudgetsApi.listBudgets()) }
        }

    suspend fun runBudgetBankSync(
        budgetId: String,
        encryptionPassword: String?,
    ) {
        withMaintenanceOperation(maintenanceSignature("bank-sync", budgetId)) { idempotencyKey ->
            withGeneratedApi {
                requireOk(
                    BudgetsApi.runBudgetBankSync(
                        xBudgetEncryptionPassword = encryptionPassword,
                        idempotencyKey = idempotencyKey,
                        budgetId = budgetId,
                    ),
                )
            }
        }
    }

    suspend fun importBudgetAccountTransactions(
        budgetId: String,
        accountId: String,
        transactions: List<StatementImportItem>,
        encryptionPassword: String?,
    ): StatementImportSummary {
        require(transactions.isNotEmpty() && transactions.size <= 1_000)
        val requestBody =
            buildJsonObject {
                put(
                    "transactions",
                    JsonArray(
                        transactions.map { transaction ->
                            buildJsonObject {
                                put("date", transaction.date)
                                put("amountMinor", transaction.amountMinor)
                                put("payeeName", transaction.payeeName)
                                transaction.notes?.let { put("notes", it) }
                                put("importedId", transaction.importedId)
                            }
                        },
                    ),
                )
            }.toMap()
        val signature =
            maintenanceSignature(
                "statement-import",
                budgetId,
                accountId,
                JsonArray(
                    transactions.map { transaction ->
                        buildJsonObject {
                            put("date", transaction.date)
                            put("amountMinor", transaction.amountMinor)
                            put("payeeName", transaction.payeeName)
                            transaction.notes?.let { put("notes", it) }
                            put("importedId", transaction.importedId)
                        }
                    },
                ).toString(),
            )
        return withMaintenanceOperation(signature) { idempotencyKey ->
            withGeneratedApi {
                val payload =
                    unwrap(
                        TransactionsApi.importBudgetAccountTransactions(
                            xBudgetEncryptionPassword = encryptionPassword,
                            idempotencyKey = idempotencyKey,
                            budgetId = budgetId,
                            accountId = accountId,
                            body = requestBody,
                        ),
                    )
                val status = (payload["status"] as? JsonPrimitive)?.contentOrNull
                if (status != "ok") throw TandemApiProtocolException("Statement import response has an invalid status")
                StatementImportSummary(
                    added =
                        (payload["added"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
                            ?: throw TandemApiProtocolException("Statement import response has an invalid added count"),
                    updated =
                        (payload["updated"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
                            ?: throw TandemApiProtocolException("Statement import response has an invalid updated count"),
                    errorCount =
                        (payload["errorCount"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
                            ?: throw TandemApiProtocolException("Statement import response has an invalid error count"),
                )
            }
        }
    }

    suspend fun createBudgetAccount(
        budgetId: String,
        name: String,
        offbudget: Boolean,
        accountGroupId: String?,
        encryptionPassword: String?,
    ) {
        withMaintenanceOperation(
            maintenanceSignature("account-create", budgetId, name, offbudget.toString(), accountGroupId.orEmpty()),
        ) { idempotencyKey ->
            withGeneratedApi {
                requireOk(
                    BudgetsApi.createBudgetAccount(
                        xBudgetEncryptionPassword = encryptionPassword,
                        idempotencyKey = idempotencyKey,
                        budgetId = budgetId,
                        body =
                            buildJsonObject {
                                put("name", name)
                                put("offbudget", offbudget)
                                if (accountGroupId == null) put("accountGroupId", JsonNull) else put("accountGroupId", accountGroupId)
                            }.toMap(),
                    ),
                )
            }
        }
    }

    suspend fun closeBudgetAccount(
        budgetId: String,
        accountId: String,
        transferAccountId: String?,
        transferCategoryId: String?,
        encryptionPassword: String?,
    ) {
        withMaintenanceOperation(
            maintenanceSignature("account-close", budgetId, accountId, transferAccountId, transferCategoryId),
        ) { idempotencyKey ->
            withGeneratedApi {
                requireOk(
                    BudgetsApi.closeBudgetAccount(
                        xBudgetEncryptionPassword = encryptionPassword,
                        idempotencyKey = idempotencyKey,
                        budgetId = budgetId,
                        accountId = accountId,
                        body =
                            buildJsonObject {
                                put("transferAccountId", transferAccountId?.let(::JsonPrimitive) ?: JsonNull)
                                put("transferCategoryId", transferCategoryId?.let(::JsonPrimitive) ?: JsonNull)
                            }.toMap(),
                    ),
                )
            }
        }
    }

    suspend fun renameBudgetAccount(
        budgetId: String,
        accountId: String,
        name: String,
        encryptionPassword: String?,
    ) {
        withMaintenanceOperation(maintenanceSignature("account-rename", budgetId, accountId, name)) { idempotencyKey ->
            withGeneratedApi {
                requireOk(
                    BudgetsApi.renameBudgetAccount(
                        xBudgetEncryptionPassword = encryptionPassword,
                        idempotencyKey = idempotencyKey,
                        budgetId = budgetId,
                        accountId = accountId,
                        body = buildJsonObject { put("name", name) }.toMap(),
                    ),
                )
            }
        }
    }

    suspend fun reopenBudgetAccount(
        budgetId: String,
        accountId: String,
        encryptionPassword: String?,
    ) {
        withMaintenanceOperation(maintenanceSignature("account-reopen", budgetId, accountId)) { idempotencyKey ->
            withGeneratedApi {
                requireOk(
                    BudgetsApi.reopenBudgetAccount(
                        xBudgetEncryptionPassword = encryptionPassword,
                        idempotencyKey = idempotencyKey,
                        budgetId = budgetId,
                        accountId = accountId,
                    ),
                )
            }
        }
    }

    suspend fun moveBudgetAccount(
        budgetId: String,
        accountId: String,
        targetAccountId: String?,
        encryptionPassword: String?,
    ) {
        withMaintenanceOperation(maintenanceSignature("account-move", budgetId, accountId, targetAccountId)) { idempotencyKey ->
            withGeneratedApi {
                requireOk(
                    BudgetsApi.moveBudgetAccount(
                        xBudgetEncryptionPassword = encryptionPassword,
                        idempotencyKey = idempotencyKey,
                        budgetId = budgetId,
                        accountId = accountId,
                        body =
                            buildJsonObject {
                                put("targetAccountId", targetAccountId?.let(::JsonPrimitive) ?: JsonNull)
                            }.toMap(),
                    ),
                )
            }
        }
    }

    suspend fun renameBudgetCategory(
        budgetId: String,
        categoryId: String,
        name: String,
        encryptionPassword: String?,
        idempotencyKey: String? = null,
    ) {
        withMaintenanceOperation(
            maintenanceSignature("category-rename", budgetId, categoryId, name),
            idempotencyKey,
        ) { operationKey ->
            withGeneratedApi {
                requireOk(
                    BudgetsApi.renameBudgetCategory(
                        xBudgetEncryptionPassword = encryptionPassword,
                        idempotencyKey = operationKey,
                        budgetId = budgetId,
                        categoryId = categoryId,
                        body = buildJsonObject { put("name", name) }.toMap(),
                    ),
                )
            }
        }
    }

    suspend fun createBudgetCategory(
        budgetId: String,
        categoryGroupId: String,
        name: String,
        encryptionPassword: String?,
        idempotencyKey: String? = null,
    ): String =
        withMaintenanceOperation(
            maintenanceSignature("category-create", budgetId, categoryGroupId, name),
            idempotencyKey,
        ) { operationKey ->
            withGeneratedApi {
                BudgetsApi
                    .createBudgetCategory(
                        xBudgetEncryptionPassword = encryptionPassword,
                        idempotencyKey = operationKey,
                        budgetId = budgetId,
                        body =
                            buildJsonObject {
                                put("name", name)
                                put("categoryGroupId", categoryGroupId)
                            }.toMap(),
                    ).fold(
                        { error -> throw error.toTandemApiException() },
                        { response ->
                            val categoryId = (response.data["categoryId"] as? JsonPrimitive)?.contentOrNull
                            verifyApiStatus(
                                isSuccessful = response.isSuccessful,
                                hasExpectedStatus = categoryId?.isNotBlank() == true,
                            )
                            categoryId ?: throw TandemApiProtocolException("Actual did not return the created category ID")
                        },
                    )
            }
        }

    suspend fun createBudgetCategoryGroup(
        budgetId: String,
        name: String,
        isIncome: Boolean,
        encryptionPassword: String?,
        idempotencyKey: String? = null,
    ): String =
        withMaintenanceOperation(
            maintenanceSignature("category-group-create", budgetId, name, isIncome.toString()),
            idempotencyKey,
        ) { operationKey ->
            withGeneratedApi {
                BudgetsApi
                    .createBudgetCategoryGroup(
                        xBudgetEncryptionPassword = encryptionPassword,
                        idempotencyKey = operationKey,
                        budgetId = budgetId,
                        body =
                            buildJsonObject {
                                put("name", name)
                                put("isIncome", isIncome)
                            }.toMap(),
                    ).fold(
                        { error -> throw error.toTandemApiException() },
                        { response ->
                            val groupId = (response.data["categoryGroupId"] as? JsonPrimitive)?.contentOrNull
                            verifyApiStatus(isSuccessful = response.isSuccessful, hasExpectedStatus = groupId?.isNotBlank() == true)
                            groupId ?: throw TandemApiProtocolException("Actual did not return the created category group ID")
                        },
                    )
            }
        }

    suspend fun manageBudgetCategoryGroup(
        budgetId: String,
        groupId: String,
        action: String,
        name: String?,
        targetId: String?,
        encryptionPassword: String?,
        idempotencyKey: String? = null,
    ) {
        withMaintenanceOperation(
            maintenanceSignature("category-group-$action", budgetId, groupId, name, targetId),
            idempotencyKey,
        ) { operationKey ->
            withGeneratedApi {
                requireOk(
                    BudgetsApi.manageBudgetCategoryGroup(
                        xBudgetEncryptionPassword = encryptionPassword,
                        idempotencyKey = operationKey,
                        budgetId = budgetId,
                        groupId = groupId,
                        body =
                            buildJsonObject {
                                put("action", action)
                                if (name != null) put("name", name)
                                if (action == "move") put("targetId", targetId?.let(::JsonPrimitive) ?: JsonNull)
                            }.toMap(),
                    ),
                )
            }
        }
    }

    suspend fun manageBudgetCategory(
        budgetId: String,
        categoryId: String,
        action: String,
        targetId: String?,
        encryptionPassword: String?,
        targetGroupId: String? = null,
        idempotencyKey: String? = null,
    ) {
        withMaintenanceOperation(
            maintenanceSignature("category-$action", budgetId, categoryId, targetId, targetGroupId),
            idempotencyKey,
        ) { operationKey ->
            withGeneratedApi {
                requireOk(
                    BudgetsApi.manageBudgetCategory(
                        xBudgetEncryptionPassword = encryptionPassword,
                        idempotencyKey = operationKey,
                        budgetId = budgetId,
                        categoryId = categoryId,
                        body =
                            buildJsonObject {
                                put("action", action)
                                if (action == "move") put("targetId", targetId?.let(::JsonPrimitive) ?: JsonNull)
                                if (action == "move" && targetGroupId != null) put("targetGroupId", JsonPrimitive(targetGroupId))
                            }.toMap(),
                    ),
                )
            }
        }
    }

    suspend fun createBudgetPayee(
        budgetId: String,
        name: String,
        encryptionPassword: String?,
    ): String =
        withMaintenanceOperation(maintenanceSignature("payee-create", budgetId, name)) { idempotencyKey ->
            withGeneratedApi {
                BudgetsApi
                    .createBudgetPayee(
                        xBudgetEncryptionPassword = encryptionPassword,
                        idempotencyKey = idempotencyKey,
                        budgetId = budgetId,
                        body = buildJsonObject { put("name", name) }.toMap(),
                    ).fold(
                        { error -> throw error.toTandemApiException() },
                        { response ->
                            val payeeId = (response.data["payeeId"] as? JsonPrimitive)?.contentOrNull
                            verifyApiStatus(
                                isSuccessful = response.isSuccessful,
                                hasExpectedStatus = payeeId?.isNotBlank() == true,
                            )
                            payeeId ?: throw TandemApiProtocolException("Actual did not return the created payee ID")
                        },
                    )
            }
        }

    suspend fun setBudgetCategoryAmount(
        budgetId: String,
        categoryId: String,
        month: String,
        amountMinor: Long,
        encryptionPassword: String?,
    ) {
        withMaintenanceOperation(
            maintenanceSignature("category-budget-amount", budgetId, categoryId, month, amountMinor.toString()),
        ) { idempotencyKey ->
            withGeneratedApi {
                requireOk(
                    BudgetsApi.setBudgetCategoryAmount(
                        xBudgetEncryptionPassword = encryptionPassword,
                        idempotencyKey = idempotencyKey,
                        budgetId = budgetId,
                        categoryId = categoryId,
                        body =
                            buildJsonObject {
                                put("month", month)
                                put("amountMinor", amountMinor)
                            }.toMap(),
                    ),
                )
            }
        }
    }

    suspend fun setBudgetCategoryCarryover(
        budgetId: String,
        categoryId: String,
        month: String,
        enabled: Boolean,
        encryptionPassword: String?,
    ) {
        withMaintenanceOperation(
            maintenanceSignature("category-carryover", budgetId, categoryId, month, enabled.toString()),
        ) { idempotencyKey ->
            withGeneratedApi {
                requireOk(
                    BudgetsApi.setBudgetCategoryCarryover(
                        xBudgetEncryptionPassword = encryptionPassword,
                        idempotencyKey = idempotencyKey,
                        budgetId = budgetId,
                        categoryId = categoryId,
                        body =
                            buildJsonObject {
                                put("month", month)
                                put("enabled", enabled)
                            }.toMap(),
                    ),
                )
            }
        }
    }

    suspend fun copyBudgetMonth(
        budgetId: String,
        month: String,
        encryptionPassword: String?,
    ) {
        withMaintenanceOperation(maintenanceSignature("budget-month-copy-last", budgetId, month)) { idempotencyKey ->
            withGeneratedApi {
                requireOk(
                    BudgetsApi.manageBudgetMonth(
                        xBudgetEncryptionPassword = encryptionPassword,
                        idempotencyKey = idempotencyKey,
                        budgetId = budgetId,
                        month = month,
                        body = buildJsonObject { put("action", "copy-last-month") }.toMap(),
                    ),
                )
            }
        }
    }

    suspend fun applyBudgetTemplate(
        budgetId: String,
        month: String,
        assignments: List<Pair<String, Long>>,
        encryptionPassword: String?,
    ) {
        require(assignments.isNotEmpty() && assignments.size <= 500)
        withMaintenanceOperation(
            maintenanceSignature(
                "budget-month-template",
                budgetId,
                month,
                kotlinx.serialization.json
                    .JsonArray(
                        assignments.map { (categoryId, amountMinor) ->
                            buildJsonObject {
                                put("categoryId", categoryId)
                                put("amountMinor", amountMinor)
                            }
                        },
                    ).toString(),
            ),
        ) { idempotencyKey ->
            withGeneratedApi {
                requireOk(
                    BudgetsApi.manageBudgetMonth(
                        xBudgetEncryptionPassword = encryptionPassword,
                        idempotencyKey = idempotencyKey,
                        budgetId = budgetId,
                        month = month,
                        body =
                            buildJsonObject {
                                put("action", "apply-template")
                                put(
                                    "assignments",
                                    kotlinx.serialization.json.JsonArray(
                                        assignments.map { (categoryId, amountMinor) ->
                                            buildJsonObject {
                                                put("categoryId", categoryId)
                                                put("amountMinor", amountMinor)
                                            }
                                        },
                                    ),
                                )
                            }.toMap(),
                    ),
                )
            }
        }
    }

    suspend fun holdBudgetForNextMonth(
        budgetId: String,
        month: String,
        amountMinor: Long,
        encryptionPassword: String?,
    ) {
        withMaintenanceOperation(
            maintenanceSignature("budget-month-hold-next", budgetId, month, amountMinor.toString()),
        ) { idempotencyKey ->
            withGeneratedApi {
                requireOk(
                    BudgetsApi.manageBudgetMonth(
                        xBudgetEncryptionPassword = encryptionPassword,
                        idempotencyKey = idempotencyKey,
                        budgetId = budgetId,
                        month = month,
                        body =
                            buildJsonObject {
                                put("action", "hold-next-month")
                                put("amountMinor", amountMinor)
                            }.toMap(),
                    ),
                )
            }
        }
    }

    suspend fun resetBudgetHold(
        budgetId: String,
        month: String,
        encryptionPassword: String?,
    ) {
        withMaintenanceOperation(maintenanceSignature("budget-month-reset-hold", budgetId, month)) { idempotencyKey ->
            withGeneratedApi {
                requireOk(
                    BudgetsApi.manageBudgetMonth(
                        xBudgetEncryptionPassword = encryptionPassword,
                        idempotencyKey = idempotencyKey,
                        budgetId = budgetId,
                        month = month,
                        body = buildJsonObject { put("action", "reset-hold") }.toMap(),
                    ),
                )
            }
        }
    }

    suspend fun moveMoneyBetweenCategories(
        budgetId: String,
        month: String,
        fromCategoryId: String,
        toCategoryId: String,
        amountMinor: Long,
        encryptionPassword: String?,
    ) {
        withMaintenanceOperation(
            maintenanceSignature(
                "category-move-money",
                budgetId,
                month,
                fromCategoryId,
                toCategoryId,
                amountMinor.toString(),
            ),
        ) { idempotencyKey ->
            withGeneratedApi {
                requireOk(
                    BudgetsApi.moveMoneyBetweenCategories(
                        xBudgetEncryptionPassword = encryptionPassword,
                        idempotencyKey = idempotencyKey,
                        budgetId = budgetId,
                        body =
                            buildJsonObject {
                                put("month", month)
                                put("fromCategoryId", fromCategoryId)
                                put("toCategoryId", toCategoryId)
                                put("amountMinor", amountMinor)
                            }.toMap(),
                    ),
                )
            }
        }
    }

    suspend fun deleteBudgetCategory(
        budgetId: String,
        categoryId: String,
        transferCategoryId: String?,
        encryptionPassword: String?,
        idempotencyKey: String? = null,
    ) {
        withMaintenanceOperation(
            maintenanceSignature("category-delete", budgetId, categoryId, transferCategoryId),
            idempotencyKey,
        ) { operationKey ->
            withGeneratedApi {
                requireOk(
                    BudgetsApi.deleteBudgetCategory(
                        xBudgetEncryptionPassword = encryptionPassword,
                        idempotencyKey = operationKey,
                        budgetId = budgetId,
                        categoryId = categoryId,
                        body =
                            buildJsonObject {
                                put("transferCategoryId", transferCategoryId?.let(::JsonPrimitive) ?: JsonNull)
                            }.toMap(),
                    ),
                )
            }
        }
    }

    suspend fun manageBudgetSchedule(
        budgetId: String,
        scheduleId: String,
        action: String,
        name: String?,
        encryptionPassword: String?,
        fields: JsonObject? = null,
    ) {
        withMaintenanceOperation(
            maintenanceSignature("schedule-$action", budgetId, scheduleId, name),
        ) { idempotencyKey ->
            withGeneratedApi {
                requireOk(
                    BudgetsApi.manageBudgetSchedule(
                        xBudgetEncryptionPassword = encryptionPassword,
                        idempotencyKey = idempotencyKey,
                        budgetId = budgetId,
                        scheduleId = scheduleId,
                        body =
                            buildJsonObject {
                                put("action", action)
                                name?.let { put("name", it) }
                                fields?.forEach { (key, value) -> put(key, value) }
                            }.toMap(),
                    ),
                )
            }
        }
    }

    suspend fun manageBudgetPayee(
        budgetId: String,
        payeeId: String,
        action: String,
        name: String?,
        targetPayeeId: String?,
        encryptionPassword: String?,
        categoryId: String? = null,
    ) {
        withMaintenanceOperation(
            maintenanceSignature("payee-$action", budgetId, payeeId, name, targetPayeeId, categoryId),
        ) { idempotencyKey ->
            withGeneratedApi {
                requireOk(
                    BudgetsApi.manageBudgetPayee(
                        xBudgetEncryptionPassword = encryptionPassword,
                        idempotencyKey = idempotencyKey,
                        budgetId = budgetId,
                        payeeId = payeeId,
                        body =
                            buildJsonObject {
                                put("action", action)
                                name?.let { put("name", it) }
                                targetPayeeId?.let { put("targetPayeeId", it) }
                                if (action == "default-category") {
                                    put("categoryId", categoryId?.let(::JsonPrimitive) ?: JsonNull)
                                }
                            }.toMap(),
                    ),
                )
            }
        }
    }

    /** Revokes this device on the server while its credential is still active. */
    suspend fun revokeCurrentDevice() {
        sessionMutex.withLock {
            val deviceId = authenticatedDevice?.deviceId ?: throw TandemApiException("sign_in_required")
            val parsedDeviceId =
                try {
                    Uuid.parse(deviceId)
                } catch (_: IllegalArgumentException) {
                    throw TandemApiProtocolException("Stored device ID is invalid")
                }
            GeneratedApiAccess.mutex.withLock {
                configureGeneratedApi()
                val response =
                    AuthenticationApi.revokeDevice(parsedDeviceId).fold(
                        { error -> throw error.toTandemApiException() },
                        { it },
                    )
                verifyDeviceRevocationSuccessful(response.isSuccessful)
            }
        }
    }

    /** Prevents credential changes during a multi-request sync. */
    suspend fun <T> withExclusiveSession(block: suspend () -> T): T = sessionMutex.withLock { block() }

    private suspend fun <T> withMaintenanceOperation(
        signature: String,
        idempotencyKeyOverride: String? = null,
        request: suspend (String) -> T,
    ): T {
        val idempotencyKey =
            idempotencyKeyOverride
                ?: maintenanceKeyMutex.withLock {
                    pendingMaintenanceKeys.getOrPut(signature) { Uuid.random().toString() }
                }
        return try {
            request(idempotencyKey).also {
                if (idempotencyKeyOverride == null) {
                    maintenanceKeyMutex.withLock {
                        if (pendingMaintenanceKeys[signature] == idempotencyKey) {
                            pendingMaintenanceKeys.remove(signature)
                        }
                    }
                }
            }
        } catch (error: TandemApiException) {
            if (idempotencyKeyOverride == null && (error.httpStatus == 400 || error.httpStatus == 429)) {
                maintenanceKeyMutex.withLock {
                    if (pendingMaintenanceKeys[signature] == idempotencyKey) {
                        pendingMaintenanceKeys.remove(signature)
                    }
                }
            }
            if (idempotencyKeyOverride == null && (error.errorCode == "operation_uncertain" || error.errorCode == "operation_rejected")) {
                // The server has definitively stopped processing this key. A later call is a
                // deliberate user retry and must get a new key; uncertain results still tell
                // the user to check Actual before submitting again.
                maintenanceKeyMutex.withLock {
                    if (pendingMaintenanceKeys[signature] == idempotencyKey) {
                        pendingMaintenanceKeys.remove(signature)
                    }
                }
            }
            throw error
        }
    }

    /** Runs one generated request with this instance's server and credential selected. */
    internal suspend fun <T> withGeneratedApi(block: suspend () -> T): T =
        GeneratedApiAccess.mutex.withLock {
            configureGeneratedApi()
            block()
        }

    private suspend fun parseBudgetCatalog(
        response: Either<CallException, HttpCallResponse<Map<String, JsonElement>>>,
    ): List<BudgetCatalogItem> {
        val payload = unwrap(response)
        val budgets =
            payload["budgets"] as? JsonArray
                ?: throw TandemApiProtocolException("Budget catalog response is missing its budgets")

        return budgets.map { element ->
            val budget =
                element as? JsonObject
                    ?: throw TandemApiProtocolException("Budget catalog contains an invalid item")
            val encrypted =
                (budget["isEncrypted"] as? JsonPrimitive)?.booleanOrNull
                    ?: throw TandemApiProtocolException("Budget catalog item has an invalid encryption flag")
            BudgetCatalogItem(
                budgetId = budget.requiredString("budgetId"),
                name = budget.requiredString("name"),
                isEncrypted = encrypted,
            )
        }
    }

    suspend fun forgetSession() {
        sessionMutex.withLock {
            GeneratedApiAccess.mutex.withLock {
                clearGeneratedAuthProvider()
                accessToken = null
                serverUrl = null
                authenticatedDevice = null
            }
        }
    }

    private fun configureGeneratedApi() {
        val currentServerUrl = serverUrl
        if (currentServerUrl.isNullOrBlank() || accessToken.isNullOrBlank()) {
            throw TandemApiException("sign_in_required")
        }
        GeneratedApiAccess.ensureContentEncoding()
        Api.baseUrl = parseServerUrl(currentServerUrl, allowInsecurePrivateNetwork)
        setGeneratedAuthProvider(bearerAuth)
    }

    private fun setGeneratedAuthProvider(provider: Auth.BearerAuth) {
        Api.setAuthProvider(provider)
        activeAuthProvider = provider
    }

    private fun clearGeneratedAuthProvider() {
        activeAuthProvider?.let(Api::clearAuthProvider)
        activeAuthProvider = null
    }

    private suspend fun restoreSessionLocked(
        serverUrl: String,
        token: String,
    ) {
        val sessionToken =
            token.takeIf { it.isNotBlank() }
                ?: throw TandemApiProtocolException("Stored device credential is empty")
        val parsedUrl = parseServerUrl(serverUrl, allowInsecurePrivateNetwork)
        GeneratedApiAccess.mutex.withLock {
            clearGeneratedAuthProvider()
            this.serverUrl = parsedUrl.toString()
            accessToken = sessionToken
        }
    }

    private suspend fun unwrap(result: Either<CallException, HttpCallResponse<Map<String, JsonElement>>>): JsonObject =
        result.fold(
            { error -> throw error.toTandemApiException() },
            { response -> JsonObject(response.data) },
        )

    private suspend fun requireOk(result: Either<CallException, HttpCallResponse<ApiV1HealthReady200Response>>) {
        result.fold(
            { error -> throw error.toTandemApiException() },
            { response ->
                verifyApiStatus(
                    isSuccessful = response.isSuccessful,
                    hasExpectedStatus = response.data.status == ApiV1HealthReady200Response.Status.OK,
                )
            },
        )
    }

    private suspend fun CallException.toTandemApiException(): TandemApiException =
        when (this) {
            is HttpCallException -> TandemApiException(apiErrorCode() ?: "http-$code", code, this)
            else -> TandemApiException("network-error", cause = this)
        }

    private suspend fun HttpCallException.apiErrorCode(): String? =
        try {
            val root = Json.parseToJsonElement(raw.bodyAsText()) as? JsonObject
            val error = root?.get("error") as? JsonObject
            (error?.get("code") as? JsonPrimitive)?.contentOrNull
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
}

internal fun verifyApiStatus(
    isSuccessful: Boolean,
    hasExpectedStatus: Boolean,
) {
    if (!isSuccessful || !hasExpectedStatus) {
        throw TandemApiProtocolException("The server returned an unexpected response")
    }
}

internal fun verifyDeviceRevocationSuccessful(isSuccessful: Boolean) {
    if (!isSuccessful) throw TandemApiException("device-revocation-failed")
}

internal fun normalizeDeviceName(value: String): String {
    val normalized = StringBuilder()
    var index = 0
    var codePoints = 0
    var pendingSpace = false
    while (index < value.length && codePoints < 100) {
        val character = value[index]
        val hasSurrogatePair =
            character.isHighSurrogate() && index + 1 < value.length && value[index + 1].isLowSurrogate()
        val unitCount = if (hasSurrogatePair) 2 else 1
        val isControl = character.code in 0..31 || character.code in 127..159
        val isSeparator = isControl || character == '\uFEFF' || character.isWhitespace() || (character.isSurrogate() && !hasSurrogatePair)
        when {
            isSeparator -> {
                if (normalized.isNotEmpty()) pendingSpace = true
                index += unitCount
            }
            else -> {
                if (pendingSpace) {
                    normalized.append(' ')
                    codePoints += 1
                    pendingSpace = false
                    if (codePoints == 100) break
                }
                normalized.append(character)
                if (hasSurrogatePair) normalized.append(value[index + 1])
                codePoints += 1
                index += unitCount
            }
        }
    }
    return normalized.toString().trim().ifBlank { "Mobile device" }
}

/** The generated OpenAPI package stores its URL and auth provider in process-wide state. */
internal object GeneratedApiAccess {
    val mutex = Mutex()

    fun ensureContentEncoding() {
        if (Api.client.pluginOrNull(ContentEncoding) != null) return
        Api.updateClient(
            decorator = {
                followRedirects = false
                install(ContentEncoding) {
                    gzip()
                    deflate()
                }
            },
        )
    }
}

private fun parseServerUrl(
    value: String,
    allowInsecurePrivateNetwork: Boolean,
): Url {
    val address = value.trim().trimEnd('/')
    val hasExplicitHost =
        address
            .substringAfter("://", missingDelimiterValue = "")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .isNotBlank()
    val url =
        try {
            Url(address)
        } catch (error: Exception) {
            throw TandemApiException("invalid-server-url", cause = error)
        }
    val secure = url.protocol.name == "https"
    val allowedLocalHttp = allowInsecurePrivateNetwork && url.protocol.name == "http" && isLocalHttpHost(url.host)
    if (!hasExplicitHost || url.host.isBlank() || (!secure && !allowedLocalHttp) || url.user != null || url.password != null) {
        throw TandemApiException("invalid-server-url")
    }
    return url
}

/** Allows cleartext only for loopback names and validated private-network IP literals. */
fun isLocalHttpHost(value: String): Boolean {
    val host =
        value
            .lowercase()
            .removePrefix("[")
            .removeSuffix("]")
            .removeSuffix(".")
    if (
        host == "localhost" ||
        host == "::1" ||
        host.endsWith(".localhost")
    ) {
        return true
    }
    val octetStrings = host.split('.')
    if (octetStrings.size == 4 && octetStrings.all { it.isNotEmpty() && it.all(Char::isDigit) }) {
        if (octetStrings.any { it.length > 1 && it.startsWith('0') }) return false
        val octets = octetStrings.map { it.toIntOrNull() ?: return false }
        if (octets.any { it !in 0..255 }) return false
        val (first, second) = octets
        return first == 10 ||
            (first == 172 && second in 16..31) ||
            (first == 192 && second == 168) ||
            first == 127 ||
            (first == 169 && second == 254)
    }
    if (!host.contains(':') || '%' in host) return false
    val compression = host.indexOf("::")
    val firstIpv6Segment =
        if (compression >= 0) {
            if (compression != host.lastIndexOf("::")) return false
            val (left, right) = host.split("::", limit = 2)
            val leftGroups = left.split(':').filter(String::isNotEmpty)
            val rightGroups = right.split(':').filter(String::isNotEmpty)
            val groups = leftGroups + rightGroups
            if (groups.isEmpty() || groups.size >= 8 || groups.any { !it.isIpv6Hextet() }) return false
            (leftGroups.firstOrNull() ?: rightGroups.first()).toIntOrNull(16) ?: return false
        } else {
            val groups = host.split(':')
            if (groups.size != 8 || groups.any { !it.isIpv6Hextet() }) return false
            groups.first().toIntOrNull(16) ?: return false
        }
    return firstIpv6Segment in 0xfc00..0xfdff ||
        firstIpv6Segment in 0xfe80..0xfebf
}

private fun String.isIpv6Hextet(): Boolean = length in 1..4 && all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }

private fun maintenanceSignature(vararg fields: String?): String =
    JsonArray(fields.map { field -> field?.let(::JsonPrimitive) ?: JsonNull }).toString()

private fun JsonObject.requiredString(name: String): String =
    (this[name] as? JsonPrimitive)
        ?.takeIf { it.isString }
        ?.contentOrNull
        ?.takeIf { it.isNotBlank() }
        ?: throw TandemApiProtocolException("Server response is missing a valid $name")

private fun JsonObject.optionalString(name: String): String? =
    when (val value = this[name]) {
        null, JsonNull -> null
        is JsonPrimitive -> value.contentOrNull?.takeIf(String::isNotBlank)
        else -> throw TandemApiProtocolException("Server response contains an invalid $name")
    }

private fun JsonObject.requiredBoolean(name: String): Boolean =
    (this[name] as? JsonPrimitive)?.booleanOrNull
        ?: throw TandemApiProtocolException("Server response is missing a valid $name")

private fun JsonObject.requiredStringArray(name: String): List<String> {
    val values =
        this[name] as? JsonArray
            ?: throw TandemApiProtocolException("Server response is missing a valid $name")
    return values.map { value ->
        (value as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?: throw TandemApiProtocolException("Server response contains an invalid $name entry")
    }
}
