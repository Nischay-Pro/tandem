package app.tandemforactual.api.client

import app.tandemforactual.data.actual.ActualBudgetEdits
import app.tandemforactual.data.actual.ActualBudgetKey
import app.tandemforactual.data.actual.ActualDatabase
import app.tandemforactual.data.actual.ActualEditException
import app.tandemforactual.data.actual.ActualSyncException
import app.tandemforactual.data.actual.ChangeSet
import app.tandemforactual.data.actual.InvalidTransactionRequestException
import app.tandemforactual.data.actual.UnsupportedRuleException
import app.tandemforactual.data.actual.applyBudgetTemplate
import app.tandemforactual.data.actual.closeAccount
import app.tandemforactual.data.actual.copyBudgetMonth
import app.tandemforactual.data.actual.createAccount
import app.tandemforactual.data.actual.createCategory
import app.tandemforactual.data.actual.createCategoryGroup
import app.tandemforactual.data.actual.createPayee
import app.tandemforactual.data.actual.createRule
import app.tandemforactual.data.actual.deleteCategory
import app.tandemforactual.data.actual.deletePayee
import app.tandemforactual.data.actual.deleteRule
import app.tandemforactual.data.actual.holdBudgetForNextMonth
import app.tandemforactual.data.actual.manageCategory
import app.tandemforactual.data.actual.manageCategoryGroup
import app.tandemforactual.data.actual.mergePayee
import app.tandemforactual.data.actual.moveAccount
import app.tandemforactual.data.actual.moveMoney
import app.tandemforactual.data.actual.renameAccount
import app.tandemforactual.data.actual.renameCategory
import app.tandemforactual.data.actual.renamePayee
import app.tandemforactual.data.actual.reopenAccount
import app.tandemforactual.data.actual.resetBudgetHold
import app.tandemforactual.data.actual.setBudgetAmount
import app.tandemforactual.data.actual.setBudgetCarryover
import app.tandemforactual.data.actual.setOrders
import app.tandemforactual.data.actual.setPayeeDefaultCategory
import app.tandemforactual.data.actual.updateRule
import app.tandemforactual.data.database.ActualBudgetFiles
import io.ktor.client.HttpClient
import kotlin.time.Clock
import kotlin.uuid.Uuid
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

/**
 * Talks to the user's Actual server directly. Budgets are downloaded into [budgetFiles], synced with Actual's own
 * protocol, and edited on the device with Actual's rules.
 */
class TandemApiClient(
    private val allowInsecurePrivateNetwork: Boolean = false,
    private val http: HttpClient = HttpClient(),
    var budgetFiles: ActualBudgetFiles? = null,
    var today: () -> String = ::localTodayIsoDate,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val newId: () -> String = { Uuid.random().toString() },
) {
    internal val sessionMutex = Mutex()
    private val budgetMutex = processBudgetMutex
    private var server: ActualServerClient? = null
    private var serverUrl: String? = null
    private var device: AuthenticatedDevice? = null
    private val budgetPasswords = mutableMapOf<String, String>()
    private var extensionAvailable: Boolean? = null
    private var extensionRetryAt = 0L
    private var extensionBackoff = 0L
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

    /**
     * Asks Actual for the provider's sign-in page. Actual sends the browser back to the return address with `/openid-cb?token=`
     * appended; the address carries [callbackState] so only a sign-in this device started can complete.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun startActualOpenIdSignIn(
        serverUrl: String,
        deviceName: String,
        codeChallenge: String,
        callbackState: String,
    ): String =
        sessionMutex.withLock {
            val client = ActualServerClient(http, parse(serverUrl).toString())
            call { client.startOpenId("$OPEN_ID_RETURN/$callbackState") }
        }

    /** Finishes OpenID sign-in with the session token Actual returned, as Actual's own web app does. */
    @Suppress("UNUSED_PARAMETER")
    suspend fun completeActualOpenIdSignIn(
        serverUrl: String,
        code: String,
        codeVerifier: String,
    ): AuthenticatedDevice =
        sessionMutex.withLock {
            closeBudgets()
            val client = connect(serverUrl)
            client.token = code
            if (!call { client.validate() }) throw TandemApiException("invalid_credentials", 401)
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
            forgetExtension()
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
            val budget =
                reopenStored(client, files, budgetId) ?: run {
                    val file = call { fileFor(client, budgetId) }
                    call {
                        ActualDirectBudget.download(
                            client,
                            file.fileId,
                            now,
                            newId,
                            expectedGroupId = budgetId,
                            unlock = { info -> unlock(budgetId, info) },
                            installSealed =
                                if (files.canSeal) {
                                    { bytes, key, info ->
                                        files.installSealed(
                                            budgetId,
                                            bytes,
                                            ActualDirectBudget.copyKey(key, budgetId),
                                            ActualDirectBudget.encodeKeyInfo(info),
                                        )
                                    }
                                } else {
                                    null
                                },
                        ) { files.install(budgetId, it) }
                    }
                }
            openBudgets[budgetId] = budget
            budget
        }

    /**
     * Opens the device's copy, or null when there's none to open. An encrypted budget's copy is kept encrypted under a
     * key derived from its Actual key; a plaintext copy left by an earlier version is encrypted the first time it opens.
     */
    private suspend fun reopenStored(
        client: ActualServerClient,
        files: ActualBudgetFiles,
        budgetId: String,
    ): ActualDirectBudget? {
        files.sealedKeyInfo(budgetId)?.let { stored ->
            val info = ActualDirectBudget.decodeKeyInfo(stored) ?: return null
            val key = unlock(budgetId, info)
            val sealed = runCatching { files.openSealed(budgetId, ActualDirectBudget.copyKey(key, budgetId)) }.getOrNull() ?: return null
            if (runCatching { sealed.first("SELECT count(*) AS tables FROM sqlite_master") }.isFailure) {
                runCatching { sealed.close() }
                return null
            }
            return reopen(client, files, sealed, budgetId, key)
        }
        val existing = files.open(budgetId) ?: return null
        val info = ActualDirectBudget.storedKeyInfo(existing)
        val key = info?.let { runCatching { unlock(budgetId, it) }.onFailure { existing.close() }.getOrThrow() }
        if (key == null || info == null || !files.canSeal) return reopen(client, files, existing, budgetId, key)
        existing.close()
        val copyKey = ActualDirectBudget.copyKey(key, budgetId)
        val sealed =
            runCatching {
                files.sealBudget(budgetId, copyKey, ActualDirectBudget.encodeKeyInfo(info))
                files.openSealed(budgetId, copyKey)
            }.getOrNull()
        return (sealed ?: files.open(budgetId))?.let { reopen(client, files, it, budgetId, key) }
    }

    private suspend fun reopen(
        client: ActualServerClient,
        files: ActualBudgetFiles,
        db: ActualDatabase,
        budgetId: String,
        key: ActualBudgetKey?,
    ): ActualDirectBudget? {
        val fileId = ActualDirectBudget.storedFileId(db)
        if (fileId == null) {
            db.close()
            files.remove(budgetId)
            return null
        }
        return ActualDirectBudget.reopen(db, client, fileId, budgetId, now, newId, key)
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

    /**
     * Planning sync for a budget when this server runs Tandem's extension service, otherwise null. Throws
     * [ExtensionSyncException] while the server can't say whether it does; it asks again after a growing pause.
     */
    suspend fun planningTransport(budgetId: String): ExtensionSyncClient? {
        val url = serverUrl ?: return null
        if (extensionAvailable == null) {
            if (now() < extensionRetryAt) throw ExtensionSyncException(null)
            val answer = ExtensionSyncClient.available(http, url)
            if (url != serverUrl) return null
            if (answer == null) {
                extensionBackoff = (extensionBackoff * 2).coerceIn(EXTENSION_RETRY_MIN_MILLIS, EXTENSION_RETRY_MAX_MILLIS)
                extensionRetryAt = now() + extensionBackoff
                throw ExtensionSyncException(null)
            }
            extensionAvailable = answer
            extensionBackoff = 0
        }
        return if (extensionAvailable == true) ExtensionSyncClient(http, url, budgetId) { server?.token } else null
    }

    private fun forgetExtension() {
        extensionAvailable = null
        extensionRetryAt = 0
        extensionBackoff = 0
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
        if (url != this.serverUrl) {
            closeBudgetsNow()
            forgetExtension()
        }
        return ActualServerClient(http, url).also {
            server = it
            this.serverUrl = url
        }
    }

    internal fun requireServer(): ActualServerClient = server?.takeIf { it.token != null } ?: throw TandemApiException("sign_in_required")

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

/** Foreground and background clients download, replace and retire the same budget files, so they share one lock. */
private val processBudgetMutex = Mutex()

private val DIRECT_FEATURES = setOf("hold-total", "budget-order-merge", "rule-edit")

private const val EXTENSION_RETRY_MIN_MILLIS = 30_000L
private const val EXTENSION_RETRY_MAX_MILLIS = 15 * 60_000L

/** The budget's recent edits, and which device id is this one so the list can say "This device". */
data class BudgetChangeHistory(
    val deviceNode: String,
    val changes: List<ChangeSet>,
)

const val OPEN_ID_RETURN = "app.tandemforactual://signin/complete"
