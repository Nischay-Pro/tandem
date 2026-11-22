package app.tandemforactual.api.client

import app.tandemforactual.data.actual.ActualBudgetEdits
import app.tandemforactual.data.actual.ActualBudgetKey
import app.tandemforactual.data.actual.ActualEditException
import app.tandemforactual.data.actual.ActualSyncException
import app.tandemforactual.data.actual.ImportRow
import app.tandemforactual.data.actual.InvalidTransactionRequestException
import app.tandemforactual.data.actual.UnsupportedRuleException
import app.tandemforactual.data.database.ActualBudgetFiles
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock
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

data class StatementImportPreview(
    val existingImportedIds: Set<String>,
    val serverChecked: Boolean = true,
) {
    fun newRowCount(totalRows: Int): Int = (totalRows - existingImportedIds.size).coerceAtLeast(0)
}

private val errorMessages =
    mapOf(
        "address_unreachable" to "Tandem can’t reach an Actual server at this address. Check the server address.",
        "sign_in_required" to "Sign in to open your budgets.",
        "sign_in_expired" to "Your sign-in is no longer active. Sign in again.",
        "invalid_credentials" to "That password doesn’t match this Actual server. Check the password.",
        "openid_unsupported" to "Sign in with your Actual password. OpenID sign-in isn’t available in Tandem yet.",
        "budget_unavailable" to "This budget isn’t on the Actual server any more.",
        "encrypted_budget" to "Tandem can’t open encrypted budgets yet.",
        "file_has_reset" to "This budget’s sync history was reset in Actual. Remove it from this device and open it again.",
        "file_has_new_key" to "This budget’s encryption key changed in Actual. Remove it from this device and open it again.",
        "out_of_sync" to "Tandem and Actual couldn’t agree on this budget’s changes. Try syncing again.",
        "clock_drift" to "This device’s clock is too far from the server’s. Check the date and time.",
        "not_supported" to "Use Actual for this until Tandem supports it.",
        "rule_unsupported" to "A rule uses a formula or template that Tandem can’t run yet. Make this change in Actual.",
        "invalid_request" to "Tandem couldn’t use those details. Check the form.",
        "invalid-server-url" to "Enter a server address that starts with https://.",
    )

class TandemApiException(
    val errorCode: String,
    val httpStatus: Int? = null,
    cause: Throwable? = null,
    safeMessageOverride: String? = null,
) : Exception(
        safeMessageOverride ?: errorMessages[errorCode] ?: "Tandem couldn’t reach Actual. Check your connection and try again.",
        cause,
    )

class TandemApiProtocolException(
    message: String,
) : IllegalStateException(message)

/**
 * Talks to the user's Actual server directly. Budgets are downloaded into [budgetFiles], synced with Actual's own
 * protocol, and edited on the device with Actual's rules.
 */
class TandemApiClient(
    private val allowInsecurePrivateNetwork: Boolean = false,
    private val http: HttpClient = HttpClient(),
    var budgetFiles: ActualBudgetFiles? = null,
    var today: () -> String = {
        Clock.System
            .now()
            .toString()
            .take(10)
    },
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val newId: () -> String = { Uuid.random().toString() },
) {
    private val sessionMutex = Mutex()
    private val budgetMutex = Mutex()
    private var server: ActualServerClient? = null
    private var serverUrl: String? = null
    private var device: AuthenticatedDevice? = null
    private val budgetPasswords = mutableMapOf<String, String>()
    private var extensionAvailable: Boolean? = null
    private val openBudgets = mutableMapOf<String, ActualDirectBudget>()

    val isAuthenticated: Boolean get() = server?.token != null

    val currentServerUrl: String? get() = serverUrl

    val activeServerUrl: String? get() = serverUrl

    val isUsingLocalServer: Boolean get() = false

    @Suppress("UNUSED_PARAMETER")
    suspend fun setLocalServerUrl(value: String?) = Unit

    @Suppress("UNUSED_PARAMETER")
    suspend fun refreshLocalServer(timeoutMillis: Long = 0): Boolean = false

    fun useExternalServerAfterLocalFailure() = Unit

    fun readServerCapabilities(): ServerCapabilities? =
        if (isAuthenticated) ServerCapabilities(CLIENT_API_VERSION, CLIENT_API_VERSION, DIRECT_FEATURES, actualVersion = null) else null

    suspend fun <T> withExclusiveSession(block: suspend () -> T): T = sessionMutex.withLock { block() }

    fun exportStoredSession(): String? {
        val url = serverUrl ?: return null
        val token = server?.token ?: return null
        val current = device ?: return null
        return buildJsonObject {
            put("serverUrl", url)
            put("token", token)
            put("deviceId", current.deviceId)
            put("principalId", current.principalId)
            put("budgetIds", JsonArray(current.budgetIds.map(::JsonPrimitive)))
        }.toString()
    }

    suspend fun restoreStoredSession(value: String): AuthenticatedDevice {
        val root =
            runCatching { Json.parseToJsonElement(value).jsonObject }.getOrNull()
                ?: throw TandemApiProtocolException("Stored session is invalid")

        fun text(key: String) =
            (root[key] as? JsonPrimitive)?.contentOrNull ?: throw TandemApiProtocolException("Stored session is invalid")
        val restored =
            AuthenticatedDevice(
                text("deviceId"),
                text("principalId"),
                (root["budgetIds"] as? JsonArray)?.map { (it as JsonPrimitive).content }
                    ?: throw TandemApiProtocolException("Stored session is invalid"),
            )
        return sessionMutex.withLock {
            connect(text("serverUrl")).token = text("token")
            device = restored
            restored
        }
    }

    suspend fun readActualLoginMethods(serverUrl: String): ActualLoginMethods {
        val status = call { ActualServerClient(http, parse(serverUrl).toString()).status() }
        return ActualLoginMethods(
            bootstrapped = status.bootstrapped,
            loginMethod = status.loginMethods.firstOrNull { it.active }?.method,
            availableLoginMethods = status.loginMethods.map { ActualLoginMethodOption(it.method, it.displayName ?: it.method, it.active) },
        )
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun signInWithPassword(
        serverUrl: String,
        password: String,
        deviceName: String,
    ): AuthenticatedDevice =
        sessionMutex.withLock {
            closeBudgets()
            val client = connect(serverUrl)
            call { client.signIn(password) }
            val files = call { client.listFiles() }
            val signedIn =
                AuthenticatedDevice(
                    deviceId = newId(),
                    principalId = "actual:${parse(serverUrl).host}",
                    budgetIds = files.filter { !it.deleted }.mapNotNull { it.groupId },
                )
            device = signedIn
            signedIn
        }

    @Suppress("UNUSED_PARAMETER")
    suspend fun startActualOpenIdSignIn(
        serverUrl: String,
        deviceName: String,
        codeChallenge: String,
        callbackState: String,
    ): String = throw TandemApiException("openid_unsupported")

    @Suppress("UNUSED_PARAMETER")
    suspend fun completeActualOpenIdSignIn(
        serverUrl: String,
        code: String,
        codeVerifier: String,
    ): AuthenticatedDevice = throw TandemApiException("openid_unsupported")

    suspend fun listBudgets(): List<BudgetCatalogItem> =
        sessionMutex.withLock {
            val client = requireServer()
            call { client.listFiles() }.filter { !it.deleted && !it.groupId.isNullOrBlank() }.map {
                BudgetCatalogItem(it.groupId!!, it.name, it.encryptKeyId != null)
            }
        }

    suspend fun revokeCurrentDevice() = forgetSession()

    suspend fun forgetSession() {
        sessionMutex.withLock {
            closeBudgets()
            server = null
            serverUrl = null
            device = null
            budgetPasswords.clear()
            extensionAvailable = null
        }
    }

    /** Opens the device's copy of a budget, downloading it from Actual the first time. */
    internal suspend fun budget(
        budgetId: String,
        password: String? = null,
    ): ActualDirectBudget = withContext(Dispatchers.IO) { openBudget(budgetId, password) }

    private suspend fun openBudget(
        budgetId: String,
        password: String?,
    ): ActualDirectBudget =
        budgetMutex.withLock {
            password?.let { budgetPasswords[budgetId] = it }
            openBudgets[budgetId]?.let { return@withLock it }
            val client = requireServer()
            val files = budgetFiles ?: throw TandemApiException("not_supported")
            val existing = files.open(budgetId)
            val budget =
                if (existing != null) {
                    val fileId = ActualDirectBudget.storedFileId(existing) ?: call { fileFor(client, budgetId).fileId }
                    val key =
                        ActualDirectBudget.storedKeyInfo(existing)?.let { info ->
                            runCatching { unlock(budgetId, info) }.onFailure { existing.close() }.getOrThrow()
                        }
                    ActualDirectBudget.reopen(existing, client, fileId, budgetId, now, newId, key)
                } else {
                    val file = call { fileFor(client, budgetId) }
                    call {
                        ActualDirectBudget.download(
                            client,
                            file.fileId,
                            now,
                            newId,
                            expectedGroupId = budgetId,
                            unlock = { info -> unlock(budgetId, info) },
                        ) { files.install(budgetId, it) }
                    }
                }
            openBudgets[budgetId] = budget
            budget
        }

    /** Derives the budget key from its password and checks it against Actual's test message. */
    private fun unlock(
        budgetId: String,
        info: ActualKeyInfo,
    ): ActualBudgetKey {
        val password = budgetPasswords[budgetId] ?: throw TandemApiException("budget_password_required")
        val keyId = info.id ?: throw TandemApiException("encrypted_budget")
        val test = info.test ?: throw TandemApiException("encrypted_budget")
        val key = ActualBudgetKey.derive(keyId, password, info.salt.orEmpty())
        if (!key.opens(test)) {
            budgetPasswords.remove(budgetId)
            throw TandemApiException("budget_password_incorrect")
        }
        return key
    }

    /** Closes and deletes the device's copy of a budget. */
    suspend fun removeBudget(budgetId: String) {
        budgetMutex.withLock {
            openBudgets.remove(budgetId)?.close()
            budgetFiles?.remove(budgetId)
        }
    }

    /** Planning sync for a budget when this server runs Tandem's extension service, otherwise null. */
    suspend fun planningTransport(budgetId: String): ExtensionSyncClient? {
        val url = serverUrl ?: return null
        if (extensionAvailable == null) extensionAvailable = ExtensionSyncClient.available(http, url)
        return if (extensionAvailable == true) ExtensionSyncClient(http, url, budgetId) { server?.token } else null
    }

    /** The Actual key of an open encrypted budget, used to wrap its planning key. */
    fun budgetSecret(budgetId: String): ByteArray? = openBudgets[budgetId]?.keySecret

    /** Sets the device's copy aside as a dated backup, for when Actual reset the budget's sync history. */
    suspend fun retireBudget(budgetId: String) {
        budgetMutex.withLock {
            openBudgets.remove(budgetId)?.close()
            budgetPasswords.remove(budgetId)
            budgetFiles?.retire(budgetId, "reset-${now()}")
        }
    }

    internal suspend fun <T> edit(
        budgetId: String,
        password: String? = null,
        block: (ActualBudgetEdits) -> T,
    ): T {
        val budget = budget(budgetId, password)
        return call { budget.edit(today(), block) }
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun runBudgetBankSync(
        budgetId: String,
        encryptionPassword: String?,
    ): Unit = call { budget(budgetId, encryptionPassword).bankSync(today()) }

    @Suppress("UNUSED_PARAMETER")
    suspend fun importBudgetAccountTransactions(
        budgetId: String,
        accountId: String,
        transactions: List<StatementImportItem>,
        encryptionPassword: String?,
        idempotencyKeyOverride: String? = null,
    ): StatementImportSummary {
        val result =
            edit(budgetId, encryptionPassword) { edits ->
                edits.importTransactions(
                    accountId,
                    transactions.map { ImportRow(it.date, it.amountMinor, it.payeeName, it.notes, it.importedId) },
                )
            }
        return StatementImportSummary(result.added.size, result.updated.size, 0)
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun previewBudgetAccountStatementImport(
        budgetId: String,
        accountId: String,
        importedIds: List<String>,
        encryptionPassword: String?,
    ): StatementImportPreview = StatementImportPreview(call { budget(budgetId, encryptionPassword).importedIds(accountId, importedIds) })

    @Suppress("UNUSED_PARAMETER")
    suspend fun createBudgetAccount(
        budgetId: String,
        name: String,
        offbudget: Boolean,
        accountGroupId: String?,
        encryptionPassword: String?,
        idempotencyKeyOverride: String? = null,
    ): String = edit(budgetId, encryptionPassword) { it.createAccount(name, offbudget, accountGroupId) }

    @Suppress("UNUSED_PARAMETER")
    suspend fun closeBudgetAccount(
        budgetId: String,
        accountId: String,
        transferAccountId: String?,
        transferCategoryId: String?,
        encryptionPassword: String?,
        idempotencyKeyOverride: String? = null,
    ) = edit(budgetId, encryptionPassword) { it.closeAccount(accountId, transferAccountId, transferCategoryId) }

    @Suppress("UNUSED_PARAMETER")
    suspend fun renameBudgetAccount(
        budgetId: String,
        accountId: String,
        name: String,
        encryptionPassword: String?,
        idempotencyKeyOverride: String? = null,
    ) = edit(budgetId, encryptionPassword) { it.renameAccount(accountId, name) }

    @Suppress("UNUSED_PARAMETER")
    suspend fun reopenBudgetAccount(
        budgetId: String,
        accountId: String,
        encryptionPassword: String?,
        idempotencyKeyOverride: String? = null,
    ) = edit(budgetId, encryptionPassword) { it.reopenAccount(accountId) }

    @Suppress("UNUSED_PARAMETER")
    suspend fun moveBudgetAccount(
        budgetId: String,
        accountId: String,
        targetAccountId: String?,
        encryptionPassword: String?,
        idempotencyKeyOverride: String? = null,
    ) = edit(budgetId, encryptionPassword) { it.moveAccount(accountId, targetAccountId) }

    @Suppress("UNUSED_PARAMETER")
    suspend fun renameBudgetCategory(
        budgetId: String,
        categoryId: String,
        name: String,
        encryptionPassword: String?,
        idempotencyKey: String? = null,
    ) = edit(budgetId, encryptionPassword) { it.renameCategory(categoryId, name) }

    @Suppress("UNUSED_PARAMETER")
    suspend fun createBudgetCategory(
        budgetId: String,
        categoryGroupId: String,
        name: String,
        encryptionPassword: String?,
        idempotencyKey: String? = null,
    ): String = edit(budgetId, encryptionPassword) { it.createCategory(categoryGroupId, name) }

    @Suppress("UNUSED_PARAMETER")
    suspend fun createBudgetCategoryGroup(
        budgetId: String,
        name: String,
        isIncome: Boolean,
        encryptionPassword: String?,
        idempotencyKey: String? = null,
    ): String = edit(budgetId, encryptionPassword) { it.createCategoryGroup(name, isIncome) }

    @Suppress("UNUSED_PARAMETER")
    suspend fun manageBudgetCategoryGroup(
        budgetId: String,
        groupId: String,
        action: String,
        name: String?,
        targetId: String?,
        encryptionPassword: String?,
        idempotencyKey: String? = null,
    ) = edit(budgetId, encryptionPassword) { it.manageCategoryGroup(groupId, action, name, targetId) }

    @Suppress("UNUSED_PARAMETER")
    suspend fun manageBudgetCategory(
        budgetId: String,
        categoryId: String,
        action: String,
        targetId: String?,
        encryptionPassword: String?,
        targetGroupId: String? = null,
        idempotencyKey: String? = null,
    ) = edit(budgetId, encryptionPassword) { it.manageCategory(categoryId, action, targetId, targetGroupId) }

    /** [order] holds optional `categoryGroups`, `categories` and `accounts` lists, each with `baseOrder` and `order`. */
    @Suppress("UNUSED_PARAMETER")
    suspend fun setBudgetOrder(
        budgetId: String,
        order: JsonObject,
        encryptionPassword: String?,
        idempotencyKey: String,
    ) {
        edit(budgetId, encryptionPassword) { it.setOrders(order) }
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun createBudgetPayee(
        budgetId: String,
        name: String,
        encryptionPassword: String?,
        idempotencyKeyOverride: String? = null,
    ): String = edit(budgetId, encryptionPassword) { it.createPayee(name) }

    @Suppress("UNUSED_PARAMETER")
    suspend fun setBudgetCategoryAmount(
        budgetId: String,
        categoryId: String,
        month: String,
        amountMinor: Long,
        encryptionPassword: String?,
        idempotencyKeyOverride: String? = null,
    ) = edit(budgetId, encryptionPassword) { it.setBudgetAmount(categoryId, month, amountMinor) }

    @Suppress("UNUSED_PARAMETER")
    suspend fun setBudgetCategoryCarryover(
        budgetId: String,
        categoryId: String,
        month: String,
        enabled: Boolean,
        encryptionPassword: String?,
        idempotencyKeyOverride: String? = null,
    ) = edit(budgetId, encryptionPassword) { it.setBudgetCarryover(categoryId, month, enabled) }

    @Suppress("UNUSED_PARAMETER")
    suspend fun copyBudgetMonth(
        budgetId: String,
        month: String,
        encryptionPassword: String?,
        idempotencyKeyOverride: String? = null,
    ) = edit(budgetId, encryptionPassword) { it.copyBudgetMonth(month) }

    @Suppress("UNUSED_PARAMETER")
    suspend fun applyBudgetTemplate(
        budgetId: String,
        month: String,
        assignments: List<Pair<String, Long>>,
        encryptionPassword: String?,
        idempotencyKeyOverride: String? = null,
    ) = edit(budgetId, encryptionPassword) { it.applyBudgetTemplate(month, assignments) }

    @Suppress("UNUSED_PARAMETER")
    suspend fun holdBudgetForNextMonth(
        budgetId: String,
        month: String,
        amountMinor: Long,
        encryptionPassword: String?,
        idempotencyKeyOverride: String? = null,
    ) = edit(budgetId, encryptionPassword) { it.holdBudgetForNextMonth(month, amountMinor) }

    @Suppress("UNUSED_PARAMETER")
    suspend fun resetBudgetHold(
        budgetId: String,
        month: String,
        encryptionPassword: String?,
        idempotencyKeyOverride: String? = null,
    ) = edit(budgetId, encryptionPassword) { it.resetBudgetHold(month) }

    @Suppress("UNUSED_PARAMETER")
    suspend fun moveMoneyBetweenCategories(
        budgetId: String,
        month: String,
        fromCategoryId: String,
        toCategoryId: String,
        amountMinor: Long,
        encryptionPassword: String?,
        idempotencyKeyOverride: String? = null,
    ) = edit(budgetId, encryptionPassword) { it.moveMoney(month, fromCategoryId, toCategoryId, amountMinor) }

    @Suppress("UNUSED_PARAMETER")
    suspend fun deleteBudgetCategory(
        budgetId: String,
        categoryId: String,
        transferCategoryId: String?,
        encryptionPassword: String?,
        idempotencyKey: String? = null,
    ) = edit(budgetId, encryptionPassword) { it.deleteCategory(categoryId, transferCategoryId) }

    /** [action] is rename, edit, skip, post or delete; [fields] carries an edit's changes. */
    @Suppress("UNUSED_PARAMETER")
    suspend fun manageBudgetSchedule(
        budgetId: String,
        scheduleId: String,
        action: String,
        name: String?,
        encryptionPassword: String?,
        fields: JsonObject? = null,
        expectedNextDate: String? = null,
        idempotencyKeyOverride: String? = null,
    ) {
        require(action !in setOf("post", "skip") || !expectedNextDate.isNullOrBlank()) {
            "Posting or skipping a schedule requires its displayed next date"
        }
        edit(budgetId, encryptionPassword) { edits ->
            when (action) {
                "rename" -> edits.schedules.rename(scheduleId, name.orEmpty())
                "edit" ->
                    edits.schedules.edit(
                        scheduleId,
                        JsonObject(
                            fields.orEmpty() + (name?.let { mapOf("name" to JsonPrimitive(it)) } ?: emptyMap()),
                        ),
                    )
                "skip" -> edits.schedules.skip(scheduleId, expectedNextDate!!)
                "post" -> edits.schedules.post(scheduleId, expectedNextDate!!)
                "delete" -> edits.schedules.delete(scheduleId)
                else -> throw ActualEditException("Choose a schedule action")
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun createBudgetRule(
        budgetId: String,
        rule: JsonObject,
        encryptionPassword: String?,
        idempotencyKeyOverride: String? = null,
    ) {
        edit(budgetId, encryptionPassword) { it.createRule(rule) }
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun manageBudgetRule(
        budgetId: String,
        ruleId: String,
        action: String,
        rule: JsonObject?,
        encryptionPassword: String?,
        idempotencyKeyOverride: String? = null,
    ) {
        edit(budgetId, encryptionPassword) { edits ->
            when (action) {
                "edit" -> edits.updateRule(ruleId, rule ?: throw ActualEditException("A rule needs conditions"))
                "delete" ->
                    if (!edits.deleteRule(
                            ruleId,
                        )
                    ) {
                        throw ActualEditException("This rule belongs to a schedule. Delete the schedule instead.")
                    }
                else -> throw ActualEditException("Choose a rule action")
            }
        }
    }

    /** [action] is rename, merge, delete or default-category, as the gateway accepted. */
    @Suppress("UNUSED_PARAMETER")
    suspend fun manageBudgetPayee(
        budgetId: String,
        payeeId: String,
        action: String,
        name: String?,
        targetPayeeId: String?,
        encryptionPassword: String?,
        categoryId: String? = null,
        idempotencyKeyOverride: String? = null,
    ) {
        edit(budgetId, encryptionPassword) { edits ->
            when (action) {
                "rename" -> edits.renamePayee(payeeId, name.orEmpty())
                "merge" -> edits.mergePayee(payeeId, targetPayeeId ?: throw ActualEditException("Choose a payee to merge into"))
                "delete" -> edits.deletePayee(payeeId)
                "default-category" -> edits.setPayeeDefaultCategory(payeeId, categoryId)
                else -> throw ActualEditException("Choose a payee action")
            }
        }
    }

    private fun parse(serverUrl: String) = parseServerUrl(serverUrl, allowInsecurePrivateNetwork)

    private fun connect(serverUrl: String): ActualServerClient {
        val url = parse(serverUrl).toString().trimEnd('/')
        if (url != this.serverUrl) closeBudgetsNow()
        return ActualServerClient(http, url).also {
            server = it
            this.serverUrl = url
        }
    }

    private fun requireServer(): ActualServerClient = server?.takeIf { it.token != null } ?: throw TandemApiException("sign_in_required")

    private suspend fun fileFor(
        client: ActualServerClient,
        budgetId: String,
    ): ActualRemoteFile =
        client.listFiles().firstOrNull { !it.deleted && it.groupId == budgetId } ?: throw ActualServerException("budget-unavailable")

    private suspend fun closeBudgets() = budgetMutex.withLock { closeBudgetsNow() }

    private fun closeBudgetsNow() {
        openBudgets.values.forEach { runCatching { it.closeNow() } }
        openBudgets.clear()
    }

    /** Runs a call to Actual and reports its failures with Tandem's error codes. */
    internal suspend fun <T> call(block: suspend () -> T): T =
        try {
            withContext(Dispatchers.IO) { block() }
        } catch (error: CancellationException) {
            throw error
        } catch (error: TandemApiException) {
            throw error
        } catch (error: ActualEditException) {
            throw TandemApiException(error.code ?: "invalid_request", 422, error, error.message)
        } catch (error: UnsupportedRuleException) {
            throw TandemApiException("rule_unsupported", 422, error)
        } catch (error: ActualServerException) {
            throw TandemApiException(serverErrorCode(error.reason), error.httpStatus, error)
        } catch (error: ActualSyncException) {
            throw TandemApiException(error.reason.replace('-', '_'), null, error)
        } catch (error: InvalidTransactionRequestException) {
            throw TandemApiException("invalid_request", 422, error, error.message)
        } catch (error: IOException) {
            throw TandemApiException("address_unreachable", null, error)
        } catch (error: Exception) {
            throw TandemApiException("unexpected_error", null, error)
        }

    private fun serverErrorCode(reason: String): String =
        when (reason) {
            "invalid-password" -> "invalid_credentials"
            "unauthorized", "token-expired" -> "sign_in_expired"
            "budget-unavailable", "file-not-found" -> "budget_unavailable"
            "encrypted-budget" -> "encrypted_budget"
            "file-has-reset" -> "file_has_reset"
            "file-has-new-key" -> "file_has_new_key"
            "not-found", "network-failure" -> "address_unreachable"
            else -> if (reason.startsWith("bank-sync-")) "bank_sync_failed" else reason.replace('-', '_')
        }
}

private val DIRECT_FEATURES = setOf("hold-total", "budget-order-merge", "rule-edit")
