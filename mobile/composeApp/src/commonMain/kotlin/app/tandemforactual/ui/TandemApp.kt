@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tandemforactual.api.client.BudgetCatalogItem
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.allDrawableResources
import app.tandemforactual.ui.generated.resources.tandem_logo_squircle
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.BriefcaseBusiness
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.CarFront
import com.composables.icons.lucide.Cloud
import com.composables.icons.lucide.CloudCheck
import com.composables.icons.lucide.CloudOff
import com.composables.icons.lucide.CloudSync
import com.composables.icons.lucide.Coffee
import com.composables.icons.lucide.Database
import com.composables.icons.lucide.FolderKey
import com.composables.icons.lucide.HeartPulse
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.LayoutDashboard
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.NotepadText
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.PawPrint
import com.composables.icons.lucide.Plane
import com.composables.icons.lucide.Receipt
import com.composables.icons.lucide.Server
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.ShoppingCart
import com.composables.icons.lucide.Smartphone
import com.composables.icons.lucide.Tags
import com.composables.icons.lucide.Utensils
import com.composables.icons.lucide.WalletCards
import com.composables.icons.lucide.Wifi
import com.composables.icons.lucide.Zap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private const val SUPPORTED_ACTUAL_SERVER_VERSION = "26.11.0"

private enum class AppRoute(
    val title: String,
) {
    STARTUP("Tandem"),
    WELCOME("Tandem"),
    SERVER_ADDRESS("Server address"),
    PAIRING_OPTIONS("Pair a device"),
    SCAN_QR("Scan pairing QR"),
    PAIRING_CODE("Pairing code"),
    PAIRING_RESULT("Device pairing"),
    BUDGET_CHOOSER("Budgets"),
    UNLOCK_BUDGET("Unlock budget"),
    FIRST_SYNC("Preparing budget"),
    BUDGET("Budget"),
    MONTH_PICKER("Choose month"),
    CATEGORY_GROUP("Essentials"),
    CATEGORY_DETAIL("Rent"),
    ACCOUNTS("Accounts"),
    ACCOUNT_DETAIL("Checking"),
    ACCOUNT_OPTIONS("Account options"),
    RECONCILIATION("Reconcile account"),
    ACTIVITY("Activity"),
    ACTIVITY_SEARCH("Search activity"),
    ACTIVITY_FILTER("Filter and sort"),
    TRANSACTION_DETAIL("Transaction details"),
    NEW_TRANSACTION("New transaction"),
    EDIT_TRANSACTION("Edit transaction"),
    CHOOSE_ACCOUNT("Choose account"),
    CHOOSE_PAYEE("Choose payee"),
    CHOOSE_CATEGORY("Choose category"),
    CHOOSE_DATE("Choose date"),
    SAVE_RESULT("Transaction status"),
    SYNC_CENTER("Sync center"),
    CONFLICT_INBOX("Review changes"),
    CONFLICT_REVIEW("Review conflict"),
    UNCERTAIN_WRITE("Status unknown"),
    REJECTED_WRITE("Not applied"),
    SETTINGS("Settings"),
    APPEARANCE("Appearance"),
    THEME_MODE("Theme mode"),
    ACCENT_COLOR("Accent color"),
    BACKGROUND_MOTION("Background and motion"),
    HOME_LAYOUT("Home screen layout"),
    BUDGET_PREFERENCES("Budget preferences"),
    SYNC_PREFERENCES("Sync preferences"),
    SERVER_DEVICE("Server and paired device"),
    SECURITY_PRIVACY("Security and privacy"),
    NOTIFICATIONS("Notifications"),
    LOCAL_DATA("Local data and storage"),
    ABOUT("About and help"),
    MERCHANT_ICON_LICENSES("Merchant icon licenses"),
}

internal enum class MainCloudStatus {
    OFF,
    SYNCED,
    SYNCING,
    ERROR,
}

internal fun resolveMainCloudStatus(
    syncEnabled: Boolean,
    isSyncing: Boolean,
    hasSyncError: Boolean,
    persistedSyncStatus: String?,
    pendingWriteStatuses: Collection<String>,
    hasSuccessfulSync: Boolean,
): MainCloudStatus {
    if (!syncEnabled) return MainCloudStatus.OFF
    if (isSyncing || persistedSyncStatus == "SYNCING") return MainCloudStatus.SYNCING

    val hasWriteNeedingReview = pendingWriteStatuses.any { it in setOf("conflict", "uncertain", "rejected") }
    if (hasSyncError || persistedSyncStatus in setOf("FAILED", "NEEDS_FULL_SYNC") || hasWriteNeedingReview) {
        return MainCloudStatus.ERROR
    }
    if (pendingWriteStatuses.any { it in setOf("queued", "sending") }) return MainCloudStatus.SYNCING
    return if (hasSuccessfulSync) MainCloudStatus.SYNCED else MainCloudStatus.ERROR
}

private val UNSYNCED_TRANSACTION_WRITE_STATUSES = setOf("queued", "sending", "uncertain", "conflict", "rejected")

private data class TransactionItem(
    val payee: String,
    val category: String,
    val dateLabel: String,
    val amountMinorUnits: Long,
    val icon: String,
    val categoryIcon: ImageVector? = null,
    val merchantIcon: DrawableResource? = null,
    val account: String = "Checking",
    val accountId: String? = null,
    val recordId: String? = null,
    val notes: String = "",
    val displaySettings: BudgetDisplaySettings? = null,
)

private fun merchantLicensePriority(license: String): Int =
    when (license) {
        "CC0-1.0" -> 0
        "MIT" -> 1
        "Apache-2.0" -> 2
        else -> 3
    }

private data class MerchantIconNotice(
    val title: String,
    val license: String,
    val registryUrl: String,
    val brandUrl: String,
    val resourceName: String,
    val resource: DrawableResource,
)

private data class MerchantIconPack(
    private val matcher: MerchantAliasMatcher<DrawableResource>,
    val notices: List<MerchantIconNotice>,
    val sourceSummary: String,
) {
    val iconCount: Int get() = notices.size
    val openSourceIconCount: Int get() = notices.count { it.license in setOf("CC0-1.0", "MIT", "Apache-2.0", "Public domain") }
    val fairUseIconCount: Int get() = notices.size - openSourceIconCount

    fun iconFor(payee: String): DrawableResource? = matcher.findPayeeIcon(payee)
}

private suspend fun loadMerchantIconPack(): MerchantIconPack {
    val catalog = Json.parseToJsonElement(Res.readBytes("files/merchant-icon-catalog.json").decodeToString()).jsonObject
    val resources = Res.allDrawableResources
    val matcher = MerchantAliasMatcher<DrawableResource>()
    val notices = mutableListOf<MerchantIconNotice>()

    for (element in catalog["icons"]?.jsonArray.orEmpty()) {
        val icon = element.jsonObject
        val resourceName = icon["resourceName"]?.jsonPrimitive?.contentOrNull ?: continue
        val resource = resources[resourceName] ?: continue
        val title = icon["title"]?.jsonPrimitive?.contentOrNull ?: continue
        val license = icon["license"]?.jsonPrimitive?.contentOrNull ?: continue
        val slug = icon["slug"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val registryUrl =
            icon["registryUrl"]?.jsonPrimitive?.contentOrNull
                ?: "https://thesvg.org/icon/${icon["slug"]?.jsonPrimitive?.contentOrNull.orEmpty()}"
        val brandUrl = icon["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
        notices += MerchantIconNotice(title, license, registryUrl, brandUrl, resourceName, resource)
        val labels =
            buildList {
                add(title to true)
                icon["aliases"]?.jsonArray?.forEach { alias -> alias.jsonPrimitive.contentOrNull?.let { add(it to false) } }
                supplementaryMerchantAliases[slug]?.forEach { add(it to false) }
            }
        for ((label, isTitle) in labels) {
            val words = labelWords(label)
            if (words.isEmpty()) continue
            matcher.add(label, resource, merchantLicensePriority(license), isTitle)
        }
    }

    return MerchantIconPack(
        matcher = matcher,
        notices = notices.sortedBy { it.title.lowercase() },
        sourceSummary = catalog["sourceSummary"]?.jsonPrimitive?.contentOrNull.orEmpty(),
    )
}

private fun AppState.formatCurrency(minorUnits: Long): String = formatCurrency(minorUnits, budgetDisplaySettings)

private fun AppState.formatAmount(minorUnits: Long): String = formatAmount(minorUnits, budgetDisplaySettings)

private fun AppState.formatSignedCurrency(minorUnits: Long): String = formatSignedCurrency(minorUnits, budgetDisplaySettings)

private fun AppState.parseAmountMinorUnits(value: String): Long? = parseAmountMinorUnits(value, budgetDisplaySettings)

private val previewTransactions =
    listOf(
        TransactionItem(
            "Corner Market",
            "Groceries",
            formatShortDate(SampleDate(2026, 5, 14)),
            -4_218,
            "G",
            categoryIcon = financeIcon("Groceries"),
        ),
        TransactionItem(
            "Electric Company",
            "Utilities",
            formatShortDate(SampleDate(2026, 5, 12)),
            -12_000,
            "E",
            categoryIcon = financeIcon("Utilities"),
        ),
        TransactionItem(
            "Acme Corp",
            "Paycheck",
            formatShortDate(SampleDate(2026, 5, 10)),
            250_000,
            "A",
            categoryIcon = financeIcon("Paycheck"),
        ),
        TransactionItem(
            "Riverside Cafe",
            "Dining",
            formatShortDate(SampleDate(2026, 5, 8)),
            -2_840,
            "R",
            categoryIcon = financeIcon("Dining"),
        ),
        TransactionItem(
            "City Transit",
            "Transport",
            formatShortDate(SampleDate(2026, 5, 6)),
            -1_650,
            "C",
            categoryIcon = financeIcon("Transport"),
        ),
        TransactionItem("Book Nook", "Personal", formatShortDate(SampleDate(2026, 5, 4)), -2_399, "B", categoryIcon = Lucide.Receipt),
    )

private fun liveTransactionItems(
    session: TandemMobileSession,
    merchantIconPack: MerchantIconPack? = null,
): List<TransactionItem> {
    val accounts = session.accountRecords.mapNotNull { record -> record.recordName()?.let { record.recordId to it } }.toMap()
    val payees = session.payeeRecords.mapNotNull { record -> record.recordName()?.let { record.recordId to it } }.toMap()
    val categories = session.categoryRecords.mapNotNull { record -> record.recordName()?.let { record.recordId to it } }.toMap()
    val payeeIconCache = mutableMapOf<String, DrawableResource?>()
    return session.transactionRecords.mapNotNull { record ->
        val amount = record.amountMinor ?: return@mapNotNull null
        val payeeName = record.payeeId?.let(payees::get)
        val categoryName = record.categoryId?.let(categories::get) ?: record.categoryId ?: "Uncategorized"
        val displayedPayee = payeeName ?: record.payeeId ?: "No payee"
        TransactionItem(
            payee = displayedPayee,
            category = categoryName,
            dateLabel = formatLiveDate(record.recordDate),
            amountMinorUnits = amount,
            icon = displayedPayee.take(1).uppercase(),
            categoryIcon = financeIcon(categoryName),
            merchantIcon = payeeIconCache.getOrPut(displayedPayee) { merchantIconPack?.iconFor(displayedPayee) },
            account = record.accountId?.let(accounts::get) ?: record.accountId ?: "No account",
            accountId = record.accountId,
            recordId = record.recordId,
            notes = record.recordBody()?.stringValue("notes") ?: "",
            displaySettings = session.budgetDisplaySettings,
        )
    }
}

internal fun BudgetRecordEntity.recordBody(): JsonObject? {
    val json = localJson ?: remoteJson ?: return null
    return try {
        Json.parseToJsonElement(json).jsonObject
    } catch (_: Exception) {
        null
    }
}

private data class LiveAccountSummary(
    val id: String,
    val name: String,
    val balanceMinor: Long?,
    val type: String?,
    val offBudget: Boolean,
)

internal data class PreviewAccount(
    val id: String,
    val name: String,
    val description: String,
    val group: String,
    val balanceMinor: Long,
    val offBudget: Boolean = false,
)

internal val previewAccounts =
    listOf(
        PreviewAccount("sample:checking", "Checking", "Everyday spending", "Cash accounts", 348_216),
        PreviewAccount("sample:savings", "Savings", "Emergency fund", "Cash accounts", 820_000),
        PreviewAccount(
            "sample:travel-card",
            "Travel card",
            "Statement closes ${formatShortDate(SampleDate(2026, 5, 22))}",
            "Credit",
            -64_280,
        ),
        PreviewAccount("sample:car-loan", "Car loan", "Off budget", "Tracking", -830_000, offBudget = true),
    )

internal fun previewAccountById(id: String): PreviewAccount = previewAccounts.firstOrNull { it.id == id } ?: previewAccounts.first()

private data class LiveCategorySummary(
    val id: String,
    val name: String,
    val assignedMinor: Long,
    val activityMinor: Long,
    val balanceMinor: Long,
)

private data class LiveCategoryGroupSummary(
    val id: String,
    val name: String,
    val categories: List<LiveCategorySummary>,
) {
    val assignedMinor: Long get() = categories.sumOf { it.assignedMinor }
    val activityMinor: Long get() = categories.sumOf { it.activityMinor }
    val balanceMinor: Long get() = categories.sumOf { it.balanceMinor }
}

private data class LiveBudgetMonthSummary(
    val toBudgetMinor: Long?,
    val incomeAvailableMinor: Long?,
    val totalBudgetedMinor: Long?,
    val totalSpentMinor: Long?,
    val groups: List<LiveCategoryGroupSummary>,
)

private data class SelectorItem(
    val id: String,
    val label: String,
)

private fun liveAccountSummaries(session: TandemMobileSession): List<LiveAccountSummary> =
    session.accountRecords.mapNotNull { record ->
        val body = record.recordBody() ?: return@mapNotNull null
        LiveAccountSummary(
            id = record.recordId,
            name = body.stringValue("name") ?: return@mapNotNull null,
            balanceMinor = body.longValue("balance_current"),
            type = body.stringValue("type"),
            offBudget = body.booleanValue("offbudget") ?: body.booleanValue("offBudget") ?: false,
        )
    }

private fun liveBudgetMonthSummary(
    session: TandemMobileSession,
    monthId: String,
): LiveBudgetMonthSummary? {
    val monthRecord = session.budgetMonthRecords.firstOrNull { it.recordId == monthId } ?: return null
    val month = monthRecord.recordBody() ?: return null
    val categoryNames = session.categoryRecords.associate { it.recordId to (it.recordName() ?: it.recordId) }
    val groupNames = session.categoryGroupRecords.associate { it.recordId to (it.recordName() ?: it.recordId) }
    val groups =
        (month["categoryGroups"] as? JsonArray)
            ?.mapNotNull { element ->
                val group = element as? JsonObject ?: return@mapNotNull null
                if (group.booleanValue("hidden") == true) return@mapNotNull null
                val groupId = group.stringValue("id") ?: return@mapNotNull null
                val categories =
                    (group["categories"] as? JsonArray)
                        ?.mapNotNull { categoryElement ->
                            val category = categoryElement as? JsonObject ?: return@mapNotNull null
                            if (category.booleanValue("hidden") == true || category.booleanValue("deleted") == true) {
                                return@mapNotNull null
                            }
                            val categoryId = category.stringValue("id") ?: return@mapNotNull null
                            val assigned = category.longValue("budgeted") ?: category.longValue("assigned") ?: 0
                            val activity = category.longValue("activity") ?: category.longValue("spent") ?: 0
                            LiveCategorySummary(
                                id = categoryId,
                                name = category.stringValue("name") ?: categoryNames[categoryId] ?: categoryId,
                                assignedMinor = assigned,
                                activityMinor = activity,
                                balanceMinor = category.longValue("balance") ?: (assigned + activity),
                            )
                        }.orEmpty()
                LiveCategoryGroupSummary(
                    id = groupId,
                    name = group.stringValue("name") ?: groupNames[groupId] ?: groupId,
                    categories = categories,
                )
            }.orEmpty()
    return LiveBudgetMonthSummary(
        toBudgetMinor = month.longValue("toBudget"),
        incomeAvailableMinor = month.longValue("incomeAvailable"),
        totalBudgetedMinor = month.longValue("totalBudgeted"),
        totalSpentMinor = month.longValue("totalSpent"),
        groups = groups,
    )
}

private fun BudgetRecordEntity.recordName(): String? = recordBody()?.stringValue("name")?.takeIf { it.isNotBlank() }

private fun JsonObject.stringValue(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

private fun JsonObject.longValue(name: String): Long? =
    (this[name] as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }

internal fun JsonObject.booleanValue(name: String): Boolean? = (this[name] as? JsonPrimitive)?.booleanOrNull

private fun formatLiveDate(value: String?): String {
    val parts = value?.split('-')
    val year = parts?.getOrNull(0)?.toIntOrNull()
    val month = parts?.getOrNull(1)?.toIntOrNull()
    val day = parts?.getOrNull(2)?.take(2)?.toIntOrNull()
    return if (year != null && month != null && day != null) {
        formatShortDate(SampleDate(year, month, day))
    } else {
        value ?: "Undated"
    }
}

private fun sanitizePairingCodeInput(value: String): String =
    value.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' || it == '-' }.take(9)

internal fun encodeCachedBudgetCatalog(budgets: List<BudgetCatalogItem>): String =
    JsonArray(
        budgets.map { budget ->
            buildJsonObject {
                put("budgetId", budget.budgetId)
                put("name", budget.name)
                put("isEncrypted", budget.isEncrypted)
            }
        },
    ).toString()

internal fun decodeCachedBudgetCatalog(value: String): List<BudgetCatalogItem> =
    runCatching {
        Json
            .parseToJsonElement(value)
            .jsonArray
            .mapNotNull { element ->
                val budget = element as? JsonObject ?: return@mapNotNull null
                val budgetId = budget.stringValue("budgetId") ?: return@mapNotNull null
                val name = budget.stringValue("name") ?: return@mapNotNull null
                val isEncrypted = (budget["isEncrypted"] as? JsonPrimitive)?.booleanOrNull ?: return@mapNotNull null
                BudgetCatalogItem(budgetId, name, isEncrypted)
            }
    }.getOrDefault(emptyList())

internal fun cachedBudgetsWithLocalSnapshots(
    savedCatalog: List<BudgetCatalogItem>,
    localSnapshots: List<BudgetCatalogItem>,
): List<BudgetCatalogItem> =
    localSnapshots.map { localBudget ->
        savedCatalog.firstOrNull { it.budgetId == localBudget.budgetId } ?: localBudget
    }

internal fun budgetChoicesForSelection(
    serverCatalog: List<BudgetCatalogItem>,
    cachedCatalog: List<BudgetCatalogItem>,
    serverCatalogLoaded: Boolean,
): List<BudgetCatalogItem> =
    if (!serverCatalogLoaded) {
        cachedCatalog
    } else {
        serverCatalog + cachedCatalog.filter { cached -> serverCatalog.none { it.budgetId == cached.budgetId } }
    }

internal fun shouldTriggerAutomaticSync(
    isBudgetVisible: Boolean,
    isAuthenticated: Boolean,
    syncOnOpen: Boolean,
    budgetId: String,
    isSyncing: Boolean,
    encryptedBudgetNeedsPassword: Boolean,
    hasEncryptionPassword: Boolean,
    lastAutomaticSyncAtEpochMillis: Long?,
    nowEpochMillis: Long,
): Boolean {
    if (
        !isBudgetVisible ||
        !isAuthenticated ||
        !syncOnOpen ||
        budgetId.isBlank() ||
        isSyncing ||
        (encryptedBudgetNeedsPassword && !hasEncryptionPassword)
    ) {
        return false
    }
    val lastSyncAt = lastAutomaticSyncAtEpochMillis ?: return true
    return nowEpochMillis < lastSyncAt || nowEpochMillis - lastSyncAt >= AUTOMATIC_SYNC_COOLDOWN_MS
}

internal enum class AppStartupDestination {
    WAIT,
    CACHED_BUDGET,
    BUDGET_CHOOSER,
    WELCOME,
}

internal fun resolveAppStartupDestination(
    cacheDiscoveryComplete: Boolean,
    hasCachedBudget: Boolean,
    preferencesReady: Boolean,
    preferencesFailed: Boolean,
    sessionRestoreComplete: Boolean,
    isAuthenticated: Boolean,
): AppStartupDestination {
    if (!cacheDiscoveryComplete) return AppStartupDestination.WAIT
    if (hasCachedBudget) {
        return if (preferencesReady || preferencesFailed) AppStartupDestination.CACHED_BUDGET else AppStartupDestination.WAIT
    }
    if (!sessionRestoreComplete) return AppStartupDestination.WAIT
    return if (isAuthenticated) AppStartupDestination.BUDGET_CHOOSER else AppStartupDestination.WELCOME
}

private const val AUTOMATIC_SYNC_COOLDOWN_MS = 30_000L

private val LocalRouteScrollState = staticCompositionLocalOf<ScrollState?> { null }

private fun decodeScrollPositions(serialized: String?): Map<String, Int> =
    serialized
        ?.split('|')
        ?.mapNotNull { entry ->
            val delimiter = entry.lastIndexOf(':')
            if (delimiter < 1) {
                null
            } else {
                entry.substring(delimiter + 1).toIntOrNull()?.let { entry.substring(0, delimiter) to it }
            }
        }?.toMap()
        ?: emptyMap()

internal class AppState {
    var theme by mutableStateOf(ThemeMode.DARK)
    var accent by mutableStateOf(AccentChoice.ACTUAL_PURPLE)
    var selectedBudget by mutableStateOf("Household")
    var selectedBudgetId by mutableStateOf("")
    var selectedBudgetEncrypted by mutableStateOf(false)
    var cachedBudgetCatalogJson by mutableStateOf("")
    var budgetDisplaySettings by mutableStateOf<BudgetDisplaySettings?>(null)
    var selectedMonth by mutableStateOf(formatMonth(2026, 5))
    var selectedMonthId by mutableStateOf("2026-05")
    var selectedAccountId by mutableStateOf("")
    var selectedCategoryGroupId by mutableStateOf("")
    var selectedCategoryId by mutableStateOf("")
    var selectedTransactionRecordId by mutableStateOf("")
    var selectedConflictOperationId by mutableStateOf("")
    var serverUrl by mutableStateOf("https://budget.example.com")
    var pairingCode by mutableStateOf("")
    var encryptedPassword by mutableStateOf("")
    var transactionAmount by mutableStateOf(formatAmount(4_218))
    var transactionAccount by mutableStateOf("Checking")
    var newTransactionAccountId by mutableStateOf("")
    var transactionPayee by mutableStateOf("Corner Market")
    var newTransactionPayeeId by mutableStateOf("")
    var transactionCategory by mutableStateOf("Groceries")
    var newTransactionCategoryId by mutableStateOf("")
    var transactionDate by mutableStateOf(formatDate(SampleDate(2026, 5, 14)))
    var transactionNotes by mutableStateOf("")
    var selectedTransactionAmountMinor by mutableStateOf<Long?>(null)
    var selectedTransactionIsLive by mutableStateOf(false)
    var conflictChoice by mutableStateOf("")
    var backgroundStyle by mutableStateOf("Solid surface")
    var homeLayout by mutableStateOf("Comfortable")
    var reduceMotionOverride by mutableStateOf<Boolean?>(null)
    var systemReduceMotionEnabled by mutableStateOf(false)
    val reduceMotion: Boolean get() = reduceMotionOverride ?: systemReduceMotionEnabled
    var syncOnOpen by mutableStateOf(true)
    var backgroundSyncEnabled by mutableStateOf(false)
    var syncOnWifi by mutableStateOf(false)
    var syncReminders by mutableStateOf(true)
    var conflictReviewAlerts by mutableStateOf(true)
    var rejectedEditAlerts by mutableStateOf(true)
    var settingsStorageError by mutableStateOf<String?>(null)
    var settingsStorageRetry by mutableStateOf(0)
    var settingsStorageReady by mutableStateOf(false)
    val scrollPositions = mutableMapOf<String, Int>()

    fun persistentSettings(): Map<String, String> =
        mapOf(
            "theme" to theme.name,
            "accent" to accent.name,
            "backgroundStyle" to backgroundStyle,
            "homeLayout" to homeLayout,
            "selectedBudget" to selectedBudget,
            "selectedBudgetId" to selectedBudgetId,
            "selectedBudgetEncrypted" to selectedBudgetEncrypted.toString(),
            "cachedBudgetCatalogJson" to cachedBudgetCatalogJson,
            "reduceMotionMode" to (reduceMotionOverride?.let { if (it) "on" else "off" } ?: "system"),
            "syncOnOpen" to syncOnOpen.toString(),
            "backgroundSyncEnabled" to backgroundSyncEnabled.toString(),
            "syncOnWifi" to syncOnWifi.toString(),
            "syncReminders" to syncReminders.toString(),
            "conflictReviewAlerts" to conflictReviewAlerts.toString(),
            "rejectedEditAlerts" to rejectedEditAlerts.toString(),
        )

    fun restorePersistentSettings(saved: Map<String, String>) {
        theme = ThemeMode.entries.firstOrNull { it.name == saved["theme"] } ?: theme
        accent = AccentChoice.entries.firstOrNull { it.name == saved["accent"] } ?: accent
        backgroundStyle = saved["backgroundStyle"] ?: backgroundStyle
        homeLayout = saved["homeLayout"] ?: homeLayout
        selectedBudget = saved["selectedBudget"] ?: selectedBudget
        selectedBudgetId = saved["selectedBudgetId"] ?: selectedBudgetId
        selectedBudgetEncrypted = saved["selectedBudgetEncrypted"]?.toBooleanStrictOrNull() ?: selectedBudgetEncrypted
        cachedBudgetCatalogJson = saved["cachedBudgetCatalogJson"] ?: cachedBudgetCatalogJson
        reduceMotionOverride =
            when (saved["reduceMotionMode"]) {
                "on" -> true
                "off" -> false
                "system" -> null
                else -> saved["reduceMotion"]?.toBooleanStrictOrNull() ?: reduceMotionOverride
            }
        syncOnOpen = saved["syncOnOpen"]?.toBooleanStrictOrNull() ?: syncOnOpen
        backgroundSyncEnabled = saved["backgroundSyncEnabled"]?.toBooleanStrictOrNull() ?: backgroundSyncEnabled
        syncOnWifi = saved["syncOnWifi"]?.toBooleanStrictOrNull() ?: syncOnWifi
        val legacyNotifications = saved["notifications"]?.toBooleanStrictOrNull()
        syncReminders = saved["syncReminders"]?.toBooleanStrictOrNull() ?: legacyNotifications ?: syncReminders
        conflictReviewAlerts =
            saved["conflictReviewAlerts"]?.toBooleanStrictOrNull() ?: legacyNotifications ?: conflictReviewAlerts
        rejectedEditAlerts =
            saved["rejectedEditAlerts"]?.toBooleanStrictOrNull() ?: legacyNotifications ?: rejectedEditAlerts
    }

    companion object {
        val Saver =
            mapSaver<AppState>(
                save = { state ->
                    // Pairing codes and budget passwords must stay out of saved instance state.
                    mapOf(
                        "theme" to state.theme.name,
                        "accent" to state.accent.name,
                        "selectedBudget" to state.selectedBudget,
                        "selectedBudgetId" to state.selectedBudgetId,
                        "selectedBudgetEncrypted" to state.selectedBudgetEncrypted,
                        "cachedBudgetCatalogJson" to state.cachedBudgetCatalogJson,
                        "selectedMonth" to state.selectedMonth,
                        "selectedMonthId" to state.selectedMonthId,
                        "selectedAccountId" to state.selectedAccountId,
                        "selectedCategoryGroupId" to state.selectedCategoryGroupId,
                        "selectedCategoryId" to state.selectedCategoryId,
                        "selectedTransactionRecordId" to state.selectedTransactionRecordId,
                        "selectedConflictOperationId" to state.selectedConflictOperationId,
                        "serverUrl" to state.serverUrl,
                        "transactionAmount" to state.transactionAmount,
                        "transactionAccount" to state.transactionAccount,
                        "newTransactionAccountId" to state.newTransactionAccountId,
                        "transactionPayee" to state.transactionPayee,
                        "newTransactionPayeeId" to state.newTransactionPayeeId,
                        "transactionCategory" to state.transactionCategory,
                        "newTransactionCategoryId" to state.newTransactionCategoryId,
                        "transactionDate" to state.transactionDate,
                        "transactionNotes" to state.transactionNotes,
                        "selectedTransactionAmountMinor" to state.selectedTransactionAmountMinor,
                        "selectedTransactionIsLive" to state.selectedTransactionIsLive,
                        "conflictChoice" to state.conflictChoice,
                        "backgroundStyle" to state.backgroundStyle,
                        "homeLayout" to state.homeLayout,
                        "reduceMotionOverride" to state.reduceMotionOverride,
                        "syncOnOpen" to state.syncOnOpen,
                        "backgroundSyncEnabled" to state.backgroundSyncEnabled,
                        "syncOnWifi" to state.syncOnWifi,
                        "syncReminders" to state.syncReminders,
                        "conflictReviewAlerts" to state.conflictReviewAlerts,
                        "rejectedEditAlerts" to state.rejectedEditAlerts,
                        "scrollPositions" to state.scrollPositions.entries.joinToString("|") { "${it.key}:${it.value}" },
                    )
                },
                restore = { saved ->
                    AppState().apply {
                        theme = ThemeMode.entries.firstOrNull { it.name == saved["theme"] } ?: theme
                        accent = AccentChoice.entries.firstOrNull { it.name == saved["accent"] } ?: accent
                        selectedBudget = saved["selectedBudget"] as? String ?: selectedBudget
                        selectedBudgetId = saved["selectedBudgetId"] as? String ?: selectedBudgetId
                        selectedBudgetEncrypted = saved["selectedBudgetEncrypted"] as? Boolean ?: selectedBudgetEncrypted
                        cachedBudgetCatalogJson = saved["cachedBudgetCatalogJson"] as? String ?: cachedBudgetCatalogJson
                        selectedMonth = saved["selectedMonth"] as? String ?: selectedMonth
                        selectedMonthId = saved["selectedMonthId"] as? String ?: selectedMonthId
                        selectedAccountId = saved["selectedAccountId"] as? String ?: selectedAccountId
                        selectedCategoryGroupId = saved["selectedCategoryGroupId"] as? String ?: selectedCategoryGroupId
                        selectedCategoryId = saved["selectedCategoryId"] as? String ?: selectedCategoryId
                        selectedTransactionRecordId = saved["selectedTransactionRecordId"] as? String ?: selectedTransactionRecordId
                        selectedConflictOperationId = saved["selectedConflictOperationId"] as? String ?: selectedConflictOperationId
                        serverUrl = saved["serverUrl"] as? String ?: serverUrl
                        transactionAmount = saved["transactionAmount"] as? String ?: transactionAmount
                        transactionAccount = saved["transactionAccount"] as? String ?: transactionAccount
                        newTransactionAccountId = saved["newTransactionAccountId"] as? String ?: newTransactionAccountId
                        transactionPayee = saved["transactionPayee"] as? String ?: transactionPayee
                        newTransactionPayeeId = saved["newTransactionPayeeId"] as? String ?: newTransactionPayeeId
                        transactionCategory = saved["transactionCategory"] as? String ?: transactionCategory
                        newTransactionCategoryId = saved["newTransactionCategoryId"] as? String ?: newTransactionCategoryId
                        transactionDate = saved["transactionDate"] as? String ?: transactionDate
                        transactionNotes = saved["transactionNotes"] as? String ?: transactionNotes
                        selectedTransactionAmountMinor = saved["selectedTransactionAmountMinor"] as? Long
                        selectedTransactionIsLive = saved["selectedTransactionIsLive"] as? Boolean ?: selectedTransactionIsLive
                        conflictChoice = saved["conflictChoice"] as? String ?: conflictChoice
                        backgroundStyle = saved["backgroundStyle"] as? String ?: backgroundStyle
                        homeLayout = saved["homeLayout"] as? String ?: homeLayout
                        selectedBudgetId = saved["selectedBudgetId"] as? String ?: selectedBudgetId
                        reduceMotionOverride =
                            (saved["reduceMotionOverride"] as? Boolean) ?: (saved["reduceMotion"] as? Boolean)
                        syncOnOpen = saved["syncOnOpen"] as? Boolean ?: syncOnOpen
                        backgroundSyncEnabled = saved["backgroundSyncEnabled"] as? Boolean ?: backgroundSyncEnabled
                        syncOnWifi = saved["syncOnWifi"] as? Boolean ?: syncOnWifi
                        val legacyNotifications = saved["notifications"] as? Boolean
                        syncReminders = saved["syncReminders"] as? Boolean ?: legacyNotifications ?: syncReminders
                        conflictReviewAlerts =
                            saved["conflictReviewAlerts"] as? Boolean ?: legacyNotifications ?: conflictReviewAlerts
                        rejectedEditAlerts =
                            saved["rejectedEditAlerts"] as? Boolean ?: legacyNotifications ?: rejectedEditAlerts
                        scrollPositions.putAll(decodeScrollPositions(saved["scrollPositions"] as? String))
                    }
                },
            )
    }
}

private data class AppNavigation(
    val open: (AppRoute) -> Unit,
    val newTransaction: (accountId: String?) -> Unit,
    val selectTab: (AppRoute) -> Unit,
    val back: () -> Unit,
)

private data class AppRouteEntry(
    val route: AppRoute,
    val id: Long,
) {
    val stateKey: String
        get() = if (route.isTab()) "tab-${route.name}" else "route-${route.name}-$id"
}

private val appRouteStackSaver =
    listSaver<SnapshotStateList<AppRouteEntry>, String>(
        save = { stack -> stack.map { "${it.id}:${it.route.name}" } },
        restore = { saved ->
            mutableStateListOf<AppRouteEntry>().apply {
                saved.forEachIndexed { index, value ->
                    val split = value.split(':', limit = 2)
                    val savedId = split.firstOrNull()?.toLongOrNull()
                    val routeName = if (savedId == null) value else split.getOrNull(1).orEmpty()
                    val route = AppRoute.entries.firstOrNull { it.name == routeName }
                    if (route != null) add(AppRouteEntry(route, savedId ?: index.toLong() + 1))
                }
                if (isEmpty() || (size == 1 && single().route == AppRoute.WELCOME)) {
                    clear()
                    add(AppRouteEntry(AppRoute.STARTUP, 1))
                }
            }
        },
    )

/** Shared preview-first Tandem UI used by the Android app and iOS host. */
@Composable
fun TandemApp(
    startInPreview: Boolean = false,
    deviceName: String = "This device",
    database: TandemDatabase? = null,
    deviceCredentialStore: DeviceCredentialStore? = null,
    nowEpochMillis: () -> Long = { 0L },
    allowInsecureLoopback: Boolean = false,
    onBackgroundSyncSettingsChanged: ((enabled: Boolean, unmeteredOnly: Boolean, budgetId: String) -> Unit)? = null,
    onSystemAppearanceChanged: ((isDark: Boolean, followsSystem: Boolean) -> Unit)? = null,
    onRequestNotificationPermission: (() -> Unit)? = null,
    onOpenNotificationSettings: (() -> Unit)? = null,
    onStartupComplete: (() -> Unit)? = null,
    appResumeEvents: Flow<Unit> = emptyFlow(),
    networkRestoredEvents: Flow<Unit> = emptyFlow(),
) {
    var accessibilitySettingsRevision by remember { mutableStateOf(0) }
    val systemReduceMotion = key(accessibilitySettingsRevision) { SystemReduceMotionEnabled() }
    val state = rememberSaveable(saver = AppState.Saver) { AppState() }
    val saveableStateHolder = rememberSaveableStateHolder()
    val routeScrollStates = remember { mutableMapOf<String, ScrollState>() }
    SideEffect { state.systemReduceMotionEnabled = systemReduceMotion }
    val reduceMotion = state.reduceMotionOverride ?: systemReduceMotion
    val apiClient = remember(allowInsecureLoopback) { TandemApiClient(allowInsecureLoopback) }
    val mobileSession =
        remember(apiClient, database, deviceCredentialStore) {
            TandemMobileSession(apiClient, database, nowEpochMillis, deviceCredentialStore)
        }
    val appPreferencesStore = remember(database) { database?.let(::AppPreferencesStore) }
    val coroutineScope = rememberCoroutineScope()
    var merchantIconPack by remember { mutableStateOf<MerchantIconPack?>(null) }
    var merchantIconPackLoadError by remember { mutableStateOf<String?>(null) }
    val cachedBudgetCatalog =
        remember(state.cachedBudgetCatalogJson, mobileSession.cachedBudgets) {
            cachedBudgetsWithLocalSnapshots(
                decodeCachedBudgetCatalog(state.cachedBudgetCatalogJson),
                mobileSession.cachedBudgets,
            )
        }
    val availableBudgets =
        budgetChoicesForSelection(mobileSession.budgets, cachedBudgetCatalog, mobileSession.budgetCatalogLoaded)
    val usingCachedBudgetCatalog = !mobileSession.budgetCatalogLoaded && cachedBudgetCatalog.isNotEmpty()
    var syncRetryCounter by remember { mutableStateOf(0) }
    var lastAutomaticSyncAtEpochMillis by remember { mutableStateOf<Long?>(null) }
    var lastAutomaticSyncBudgetId by remember { mutableStateOf<String?>(null) }
    var automaticSyncInFlight by remember { mutableStateOf(false) }
    var pendingAutomaticSync by remember { mutableStateOf(false) }
    var cacheDiscoveryComplete by remember { mutableStateOf(false) }
    var sessionRestoreComplete by remember { mutableStateOf(false) }
    var routeTransitionDirection by remember { mutableStateOf(0f) }
    var backGestureProgress by remember { mutableStateOf(0f) }
    var backGestureDirection by remember { mutableStateOf(1f) }
    var backGestureCommitPending by remember { mutableStateOf(false) }
    var backGestureOutgoingEntry by remember { mutableStateOf<AppRouteEntry?>(null) }
    var backPreviewRoute by remember { mutableStateOf<AppRouteEntry?>(null) }
    var backGestureCancelJob by remember { mutableStateOf<Job?>(null) }
    var skipNextRouteTransition by remember { mutableStateOf(false) }
    var nextRouteEntryId by rememberSaveable { mutableStateOf(2L) }
    val backStack =
        rememberSaveable(startInPreview, saver = appRouteStackSaver) {
            mutableStateListOf(AppRouteEntry(if (startInPreview) AppRoute.BUDGET else AppRoute.STARTUP, 1))
        }
    val newRouteEntry: (AppRoute) -> AppRouteEntry = { nextRoute ->
        val entryId = nextRouteEntryId
        nextRouteEntryId += 1
        AppRouteEntry(nextRoute, entryId)
    }
    LaunchedEffect(mobileSession) {
        try {
            mobileSession.loadCachedBudgets()
        } finally {
            cacheDiscoveryComplete = true
        }
    }
    LaunchedEffect(mobileSession) {
        try {
            val storedServerUrl = mobileSession.restoreStoredSession()
            if (storedServerUrl != null) state.serverUrl = storedServerUrl
        } finally {
            sessionRestoreComplete = true
        }
    }
    LaunchedEffect(
        cacheDiscoveryComplete,
        mobileSession.cachedBudgets,
        state.settingsStorageReady,
        state.settingsStorageError,
        state.selectedBudgetId,
        state.cachedBudgetCatalogJson,
        sessionRestoreComplete,
        mobileSession.isAuthenticated,
    ) {
        if (backStack.lastOrNull()?.route != AppRoute.STARTUP) return@LaunchedEffect
        val destination =
            resolveAppStartupDestination(
                cacheDiscoveryComplete = cacheDiscoveryComplete,
                hasCachedBudget = mobileSession.cachedBudgets.isNotEmpty(),
                preferencesReady = state.settingsStorageReady,
                preferencesFailed = state.settingsStorageError != null,
                sessionRestoreComplete = sessionRestoreComplete,
                isAuthenticated = mobileSession.isAuthenticated,
            )
        when (destination) {
            AppStartupDestination.WAIT -> Unit
            AppStartupDestination.CACHED_BUDGET -> {
                val localCachedBudgets = mobileSession.cachedBudgets
                val availableCachedBudgets =
                    cachedBudgetsWithLocalSnapshots(
                        decodeCachedBudgetCatalog(state.cachedBudgetCatalogJson),
                        localCachedBudgets,
                    )
                val selectedCachedBudget = availableCachedBudgets.firstOrNull { it.budgetId == state.selectedBudgetId }
                val firstCachedBudget = selectedCachedBudget ?: availableCachedBudgets.firstOrNull()
                if (firstCachedBudget != null) {
                    state.selectedBudget = firstCachedBudget.name
                    state.selectedBudgetId = firstCachedBudget.budgetId
                    state.selectedBudgetEncrypted = firstCachedBudget.isEncrypted
                    backStack.clear()
                    backStack.add(newRouteEntry(AppRoute.BUDGET))
                }
            }
            AppStartupDestination.BUDGET_CHOOSER -> {
                backStack.clear()
                backStack.add(newRouteEntry(AppRoute.BUDGET_CHOOSER))
            }
            AppStartupDestination.WELCOME -> {
                backStack.clear()
                backStack.add(newRouteEntry(AppRoute.WELCOME))
            }
        }
    }
    LaunchedEffect(appPreferencesStore) {
        val store = appPreferencesStore
        if (store == null) {
            state.settingsStorageReady = true
            return@LaunchedEffect
        }
        val initialSettings = state.persistentSettings()
        val localChanges = mutableMapOf<String, String>()
        val localSettingsObserver =
            launch {
                snapshotFlow { state.persistentSettings() }
                    .collect { currentSettings ->
                        currentSettings
                            .filter { (name, value) -> initialSettings[name] != value }
                            .forEach { (name, value) -> localChanges[name] = value }
                    }
            }
        var storedPreferences: Map<String, String>? = null
        while (storedPreferences == null) {
            val retryAtRead = state.settingsStorageRetry
            try {
                storedPreferences = store.read()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                state.settingsStorageError = "App settings could not be loaded. Retry to load saved preferences."
                snapshotFlow { state.settingsStorageRetry }.first { it != retryAtRead }
            }
        }
        state
            .persistentSettings()
            .filter { (name, value) -> initialSettings[name] != value }
            .forEach { (name, value) -> localChanges[name] = value }
        localSettingsObserver.cancel()
        state.restorePersistentSettings(storedPreferences + localChanges)
        state.settingsStorageReady = true
        var lastSavedPreferences = if (localChanges.isEmpty()) state.persistentSettings() else emptyMap()
        var lastAttemptedRetry = state.settingsStorageRetry
        snapshotFlow { state.persistentSettings() to state.settingsStorageRetry }
            .collect { (updatedPreferences, retryCount) ->
                if (updatedPreferences == lastSavedPreferences && retryCount == lastAttemptedRetry) return@collect
                val retryRequested = retryCount != lastAttemptedRetry
                lastAttemptedRetry = retryCount
                try {
                    store.save(updatedPreferences)
                    lastSavedPreferences = updatedPreferences
                    state.settingsStorageError = null
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    state.settingsStorageError =
                        if (retryRequested) {
                            "App settings still could not be saved. Check local storage and retry."
                        } else {
                            "App settings could not be saved. Retry to save your changes."
                        }
                }
            }
    }
    LaunchedEffect(
        state.settingsStorageReady,
        state.backgroundSyncEnabled,
        state.syncOnWifi,
        state.selectedBudgetId,
        onBackgroundSyncSettingsChanged,
    ) {
        if (state.settingsStorageReady) {
            onBackgroundSyncSettingsChanged?.invoke(
                state.backgroundSyncEnabled,
                state.syncOnWifi,
                state.selectedBudgetId,
            )
        }
    }
    LaunchedEffect(mobileSession.budgetCatalogVersion) {
        if (mobileSession.budgetCatalogVersion > 0) {
            state.cachedBudgetCatalogJson = encodeCachedBudgetCatalog(mobileSession.budgets)
        }
    }
    val newTransaction: (String?) -> Unit = { requestedAccountId ->
        routeTransitionDirection = 1f
        val eligibleAccounts = liveAccountSummaries(mobileSession).filterNot { it.offBudget }
        val defaultAccount =
            if (state.selectedBudgetId.isNotBlank()) {
                eligibleAccounts.firstOrNull { it.id == requestedAccountId } ?: eligibleAccounts.firstOrNull()
            } else {
                null
            }
        state.transactionAmount = ""
        state.transactionAccount = defaultAccount?.name ?: "Checking"
        state.newTransactionAccountId = defaultAccount?.id.orEmpty()
        state.transactionPayee = ""
        state.newTransactionPayeeId = ""
        state.transactionCategory = ""
        state.newTransactionCategoryId = ""
        state.transactionDate = platformTodayIsoDate()
        state.transactionNotes = ""
        state.selectedTransactionRecordId = ""
        state.selectedTransactionAmountMinor = null
        state.selectedTransactionIsLive = false
        backStack.add(newRouteEntry(AppRoute.NEW_TRANSACTION))
    }
    val popRoute: () -> Unit = {
        if (backStack.size > 1) {
            routeTransitionDirection = -1f
            backStack.removeAt(backStack.lastIndex)
        }
    }
    val handleBack: () -> Unit = {
        backGestureCancelJob?.cancel()
        backGestureCancelJob = null
        if (backGestureCommitPending) {
            backGestureCommitPending = false
            skipNextRouteTransition = true
            popRoute()
        } else if (backStack.size > 1) {
            val outgoingEntry = backStack.last()
            val incomingEntry = backStack.getOrNull(backStack.lastIndex - 1)
            if (incomingEntry == null) {
                popRoute()
            } else {
                // Button back runs the same route pair and progress transform as predictive back.
                backGestureProgress = 0f
                backGestureDirection = 1f
                backGestureOutgoingEntry = outgoingEntry
                backPreviewRoute = incomingEntry
                backGestureCancelJob =
                    coroutineScope.launch {
                        val returnAnimation = Animatable(0f)
                        returnAnimation.animateTo(
                            targetValue = 1f,
                            animationSpec = tandemMotionSpec(reduceMotion, TandemMotion.ROUTE_MILLIS),
                        ) {
                            backGestureProgress = value
                        }
                        skipNextRouteTransition = true
                        popRoute()
                        backGestureCancelJob = null
                    }
            }
        }
    }
    val navigation =
        AppNavigation(
            open = { next ->
                if (next == AppRoute.NEW_TRANSACTION) {
                    newTransaction(null)
                } else {
                    routeTransitionDirection = 1f
                    backStack.add(newRouteEntry(next))
                }
            },
            newTransaction = newTransaction,
            selectTab = {
                if (backStack.lastOrNull()?.route != it) {
                    routeTransitionDirection = 0f
                    backStack.clear()
                    backStack.add(newRouteEntry(it))
                }
            },
            back = handleBack,
        )
    val routeEntry = backStack.last()
    val route = routeEntry.route
    val merchantCatalogNeeded =
        route == AppRoute.ABOUT || route == AppRoute.MERCHANT_ICON_LICENSES || mobileSession.transactionRecords.isNotEmpty()
    LaunchedEffect(mobileSession, merchantCatalogNeeded) {
        if (merchantIconPack == null && merchantIconPackLoadError == null && merchantCatalogNeeded) {
            try {
                merchantIconPack = withContext(Dispatchers.Default) { loadMerchantIconPack() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                val cause = failure.message?.take(160) ?: "unknown error"
                merchantIconPackLoadError = "The bundled merchant icon catalog or artwork could not be loaded ($cause)."
            }
        }
    }
    LaunchedEffect(routeEntry) {
        if (route != AppRoute.STARTUP) onStartupComplete?.invoke()
        if (skipNextRouteTransition) {
            backGestureProgress = 0f
            backGestureCommitPending = false
            backGestureOutgoingEntry = null
            backPreviewRoute = null
            skipNextRouteTransition = false
        }
    }
    var previousRoute by remember { mutableStateOf(route) }
    val automaticSyncRequest =
        rememberUpdatedState(
            newValue = {
                val now = nowEpochMillis()
                if (mobileSession.isSyncing || automaticSyncInFlight) {
                    pendingAutomaticSync = true
                } else if (
                    shouldTriggerAutomaticSync(
                        isBudgetVisible = route == AppRoute.BUDGET,
                        isAuthenticated = mobileSession.isAuthenticated,
                        syncOnOpen = state.syncOnOpen,
                        budgetId = state.selectedBudgetId,
                        isSyncing = false,
                        encryptedBudgetNeedsPassword = state.selectedBudgetEncrypted,
                        hasEncryptionPassword = state.encryptedPassword.isNotBlank(),
                        lastAutomaticSyncAtEpochMillis =
                            lastAutomaticSyncAtEpochMillis.takeIf {
                                lastAutomaticSyncBudgetId == state.selectedBudgetId
                            },
                        nowEpochMillis = now,
                    )
                ) {
                    val budgetId = state.selectedBudgetId
                    val encryptionPassword = state.encryptedPassword.takeIf { it.isNotBlank() }
                    automaticSyncInFlight = true
                    coroutineScope.launch {
                        var syncSucceeded = false
                        try {
                            syncSucceeded =
                                mobileSession.synchronize(
                                    budgetId,
                                    encryptionPassword,
                                ) != null
                        } finally {
                            lastAutomaticSyncAtEpochMillis = now.takeIf { syncSucceeded }
                            lastAutomaticSyncBudgetId = budgetId.takeIf { syncSucceeded }
                            automaticSyncInFlight = false
                        }
                    }
                }
            },
        )
    LaunchedEffect(appResumeEvents) {
        appResumeEvents.collect {
            accessibilitySettingsRevision += 1
            automaticSyncRequest.value()
        }
    }
    LaunchedEffect(networkRestoredEvents) {
        networkRestoredEvents.collect { automaticSyncRequest.value() }
    }
    LaunchedEffect(mobileSession.isSyncing, pendingAutomaticSync) {
        if (!mobileSession.isSyncing && pendingAutomaticSync) {
            pendingAutomaticSync = false
            if (!mobileSession.syncComplete) lastAutomaticSyncAtEpochMillis = null
            automaticSyncRequest.value()
        }
    }

    LaunchedEffect(route, mobileSession.pairedDevice?.deviceId) {
        if (
            route == AppRoute.BUDGET_CHOOSER &&
            mobileSession.isAuthenticated &&
            !mobileSession.budgetCatalogLoaded &&
            mobileSession.budgets.isEmpty()
        ) {
            mobileSession.loadBudgets()
        }
    }
    LaunchedEffect(route, state.selectedBudgetId, availableBudgets) {
        if (route == AppRoute.BUDGET_CHOOSER && state.selectedBudgetId.isBlank()) {
            availableBudgets.firstOrNull()?.let { budget ->
                state.selectedBudget = budget.name
                state.selectedBudgetId = budget.budgetId
                state.selectedBudgetEncrypted = budget.isEncrypted
            }
        }
    }
    LaunchedEffect(route, state.selectedBudgetId, syncRetryCounter) {
        if (route == AppRoute.FIRST_SYNC && state.selectedBudgetId.isNotBlank()) {
            mobileSession.synchronize(
                budgetId = state.selectedBudgetId,
                encryptionPassword = state.encryptedPassword.takeIf { it.isNotBlank() },
            )
            if (mobileSession.syncComplete) state.encryptedPassword = ""
        }
    }
    LaunchedEffect(state.selectedBudgetId, mobileSession) {
        if (state.selectedBudgetId.isNotBlank()) {
            mobileSession.observeLocalRecords(state.selectedBudgetId)
        }
    }
    LaunchedEffect(
        route,
        state.selectedBudgetId,
        state.syncOnOpen,
        mobileSession.isAuthenticated,
        state.selectedBudgetEncrypted,
        state.encryptedPassword,
    ) {
        if (
            route == AppRoute.BUDGET &&
            previousRoute != AppRoute.FIRST_SYNC &&
            state.syncOnOpen &&
            mobileSession.isAuthenticated &&
            state.selectedBudgetId.isNotBlank() &&
            (!state.selectedBudgetEncrypted || state.encryptedPassword.isNotBlank())
        ) {
            automaticSyncRequest.value()
        }
        previousRoute = route
    }
    LaunchedEffect(
        state.selectedBudgetId,
        mobileSession.budgetDisplaySettingsBudgetId,
        mobileSession.budgetDisplaySettings,
    ) {
        state.budgetDisplaySettings =
            mobileSession.budgetDisplaySettings.takeIf {
                mobileSession.budgetDisplaySettingsBudgetId == state.selectedBudgetId
            }
    }
    val liveTransactions =
        remember(
            mobileSession.transactionRecords,
            mobileSession.accountRecords,
            mobileSession.payeeRecords,
            mobileSession.categoryRecords,
            mobileSession.budgetDisplaySettings,
            merchantIconPack,
        ) {
            liveTransactionItems(mobileSession, merchantIconPack)
        }
    val liveAccounts = remember(mobileSession.accountRecords) { liveAccountSummaries(mobileSession) }
    val liveBudgetMonth =
        remember(
            mobileSession.budgetMonthRecords,
            mobileSession.categoryRecords,
            mobileSession.categoryGroupRecords,
            state.selectedMonthId,
        ) {
            liveBudgetMonthSummary(mobileSession, state.selectedMonthId)
        }

    val pairDevice = {
        if (!mobileSession.isPairing) {
            coroutineScope.launch {
                mobileSession.pair(state.serverUrl, state.pairingCode, deviceName)
                if (mobileSession.pairedDevice != null) {
                    state.pairingCode = ""
                    backStack.add(newRouteEntry(AppRoute.PAIRING_RESULT))
                }
            }
        }
    }
    val retryStoredSession: () -> Unit = {
        coroutineScope.launch {
            val storedServerUrl = mobileSession.restoreStoredSession()
            if (storedServerUrl != null) {
                state.serverUrl = storedServerUrl
                backStack.clear()
                backStack.add(newRouteEntry(AppRoute.BUDGET_CHOOSER))
            }
        }
    }
    val selectBudget: (BudgetCatalogItem) -> Unit = { budget ->
        if (state.selectedBudgetId != budget.budgetId) state.encryptedPassword = ""
        if (state.selectedBudgetId != budget.budgetId) state.budgetDisplaySettings = null
        state.selectedBudget = budget.name
        state.selectedBudgetId = budget.budgetId
        state.selectedBudgetEncrypted = budget.isEncrypted
    }
    val refreshBudgets: () -> Unit = {
        coroutineScope.launch {
            mobileSession.loadBudgets()
            if (mobileSession.budgets.none { it.budgetId == state.selectedBudgetId }) {
                mobileSession.budgets.firstOrNull()?.let(selectBudget)
            }
        }
    }
    val openSelectedBudget: () -> Unit = {
        val selected = availableBudgets.firstOrNull { it.budgetId == state.selectedBudgetId }
        if (selected == null) {
            // Keep a visible message in the chooser when neither cache nor server has a catalog.
            coroutineScope.launch {
                mobileSession.loadBudgets()
            }
        } else {
            coroutineScope.launch {
                val isCached = mobileSession.hasCachedBudgetSnapshot(selected.budgetId)
                when {
                    isCached -> backStack.add(newRouteEntry(AppRoute.BUDGET))
                    selected.isEncrypted && state.encryptedPassword.isBlank() -> backStack.add(newRouteEntry(AppRoute.UNLOCK_BUDGET))
                    else -> backStack.add(newRouteEntry(AppRoute.FIRST_SYNC))
                }
            }
        }
    }
    val syncNow: () -> Unit = {
        if (!mobileSession.isSyncing && state.selectedBudgetId.isNotBlank()) {
            coroutineScope.launch {
                mobileSession.synchronize(
                    state.selectedBudgetId,
                    state.encryptedPassword.takeIf { it.isNotBlank() },
                )
            }
        }
    }
    val saveTransaction: () -> Unit = {
        val transactionId = state.selectedTransactionRecordId
        val originalAmount = state.selectedTransactionAmountMinor
        if (transactionId.isNotBlank() && originalAmount != null && !mobileSession.isSavingTransaction) {
            coroutineScope.launch {
                mobileSession.updateTransaction(
                    budgetId = state.selectedBudgetId,
                    transactionId = transactionId,
                    amountText = state.transactionAmount,
                    notes = state.transactionNotes,
                    originalAmountMinor = originalAmount,
                    encryptionPassword = state.encryptedPassword.takeIf { it.isNotBlank() },
                    displaySettings = state.budgetDisplaySettings,
                )
            }
        }
    }
    val createTransaction: () -> Unit = {
        if (!mobileSession.isSavingTransaction) {
            coroutineScope.launch {
                val created =
                    mobileSession.createTransaction(
                        budgetId = state.selectedBudgetId,
                        accountId = state.newTransactionAccountId,
                        payeeId = state.newTransactionPayeeId.takeIf { it.isNotBlank() },
                        categoryId = state.newTransactionCategoryId.takeIf { it.isNotBlank() },
                        amountText = state.transactionAmount,
                        dateText = state.transactionDate,
                        notes = state.transactionNotes,
                        encryptionPassword = state.encryptedPassword.takeIf { it.isNotBlank() },
                        displaySettings = state.budgetDisplaySettings,
                    )
                if (created) navigation.back()
            }
        }
    }
    val resolveConflict: (String, Boolean) -> Unit = { operationId, keepDeviceVersion ->
        if (!mobileSession.isSavingTransaction) {
            coroutineScope.launch {
                mobileSession.resolveConflict(
                    budgetId = state.selectedBudgetId,
                    operationId = operationId,
                    keepDeviceVersion = keepDeviceVersion,
                    encryptionPassword = state.encryptedPassword.takeIf { it.isNotBlank() },
                )
            }
        }
    }
    val discardWrite: (String) -> Unit = { operationId ->
        coroutineScope.launch { mobileSession.discardWrite(state.selectedBudgetId, operationId) }
    }
    val retryUncertainWrite: (String) -> Unit = { operationId ->
        coroutineScope.launch {
            mobileSession.retryUncertainWrite(
                state.selectedBudgetId,
                operationId,
                state.encryptedPassword.takeIf { it.isNotBlank() },
            )
        }
    }

    TandemTheme(
        state.theme,
        state.accent,
        reduceMotion,
        animateThemeChanges = state.settingsStorageReady || appPreferencesStore == null,
    ) {
        val palette = tandemPalette
        val isDark =
            state.theme == ThemeMode.DARK ||
                state.theme == ThemeMode.PURE_BLACK ||
                (state.theme == ThemeMode.SYSTEM && isSystemInDarkTheme())
        SideEffect {
            onSystemAppearanceChanged?.invoke(isDark, state.theme == ThemeMode.SYSTEM)
        }
        TandemSystemBars(isDark, palette.background)
        val updateBackGesture: (Float, Float) -> Unit = { progress, direction ->
            backGestureDirection = direction
            if (progress > 0f) {
                backGestureCommitPending = progress >= 1f
                backGestureCancelJob?.cancel()
                backGestureCancelJob = null
                backGestureProgress = progress
                if (backGestureOutgoingEntry == null) {
                    backGestureOutgoingEntry = backStack.lastOrNull()
                }
                if (backPreviewRoute == null) {
                    backPreviewRoute = backStack.getOrNull(backStack.lastIndex - 1)
                }
            } else if (backGestureProgress > 0f) {
                backGestureCommitPending = false
                backGestureCancelJob?.cancel()
                backGestureCancelJob =
                    coroutineScope.launch {
                        val returnAnimation = Animatable(backGestureProgress)
                        returnAnimation.animateTo(
                            targetValue = 0f,
                            animationSpec = tandemMotionSpec(reduceMotion, TandemMotion.BACK_CANCEL_MILLIS),
                        ) {
                            backGestureProgress = value
                        }
                        backGestureOutgoingEntry = null
                        backPreviewRoute = null
                        backGestureCancelJob = null
                    }
            } else {
                backGestureCommitPending = false
                backGestureProgress = 0f
                backGestureOutgoingEntry = null
                backPreviewRoute = null
            }
        }
        TandemBackHandler(
            enabled = backStack.size > 1,
            onBack = handleBack,
            onBackProgress = updateBackGesture,
        )
        val renderHeader: @Composable (AppRoute) -> Unit = { headerRoute ->
            if (headerRoute != AppRoute.WELCOME && headerRoute != AppRoute.STARTUP) {
                AppHeader(
                    route = headerRoute,
                    state = state,
                    showBack = !headerRoute.isTab(),
                    onBack = handleBack,
                    onSync = { navigation.open(AppRoute.SYNC_CENTER) },
                    syncStatus =
                        resolveMainCloudStatus(
                            syncEnabled = state.selectedBudgetId.isNotBlank() && mobileSession.isAuthenticated,
                            isSyncing = mobileSession.isSyncing,
                            hasSyncError = mobileSession.syncError != null,
                            persistedSyncStatus = mobileSession.localSyncState?.status,
                            pendingWriteStatuses = mobileSession.pendingWrites.map { it.status },
                            hasSuccessfulSync = mobileSession.localSyncState?.lastSyncedAtEpochMs != null,
                        ),
                    onBudget = { navigation.open(AppRoute.BUDGET_CHOOSER) },
                    actualConnected = mobileSession.isServerReachable,
                    liveAccounts = liveAccounts,
                    liveBudgetMonth = liveBudgetMonth,
                )
            }
        }
        val renderRoute: @Composable (
            AppRouteEntry,
            Modifier,
            Boolean,
            Boolean,
        ) -> Unit = { visibleEntry, pageModifier, saveRouteState, includeChrome ->
            val visibleRoute = visibleEntry.route
            val routeScrollState =
                remember(visibleEntry.stateKey) {
                    routeScrollStates.getOrPut(visibleEntry.stateKey) {
                        ScrollState(state.scrollPositions[visibleEntry.stateKey] ?: 0)
                    }
                }
            LaunchedEffect(visibleEntry.stateKey, routeScrollState) {
                snapshotFlow { routeScrollState.value }.collect { state.scrollPositions[visibleEntry.stateKey] = it }
            }
            if (saveRouteState) {
                DisposableEffect(visibleEntry.stateKey, routeScrollState) {
                    onDispose {
                        state.scrollPositions[visibleEntry.stateKey] = routeScrollState.value
                        if (!visibleRoute.isTab() && backStack.none { it.stateKey == visibleEntry.stateKey }) {
                            state.scrollPositions.remove(visibleEntry.stateKey)
                        }
                        routeScrollStates.remove(visibleEntry.stateKey)
                    }
                }
            }
            val routeContent: @Composable () -> Unit = {
                CompositionLocalProvider(LocalRouteScrollState provides routeScrollState) {
                    Column(modifier = pageModifier.fillMaxSize()) {
                        if (includeChrome) renderHeader(visibleRoute)
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            ScreenContent(
                                route = visibleRoute,
                                state = state,
                                deviceName = deviceName,
                                navigation = navigation,
                                mobileSession = mobileSession,
                                onPairDevice = pairDevice,
                                onRetryStoredSession = retryStoredSession,
                                onSelectBudget = selectBudget,
                                onRefreshBudgets = refreshBudgets,
                                onOpenSelectedBudget = openSelectedBudget,
                                onRetrySync = { syncRetryCounter += 1 },
                                backgroundSyncSupported = onBackgroundSyncSettingsChanged != null,
                                merchantIconPack = merchantIconPack,
                                merchantIconPackLoadError = merchantIconPackLoadError,
                                liveTransactions = liveTransactions,
                                liveAccounts = liveAccounts,
                                liveBudgetMonth = liveBudgetMonth,
                                onSyncNow = syncNow,
                                onSaveTransaction = saveTransaction,
                                onCreateTransaction = createTransaction,
                                onResolveConflict = resolveConflict,
                                onDiscardWrite = discardWrite,
                                onRetryUncertainWrite = retryUncertainWrite,
                                onForgetDevice = { coroutineScope.launch { mobileSession.forgetDevice() } },
                                onRequestNotificationPermission = onRequestNotificationPermission,
                                onOpenNotificationSettings = onOpenNotificationSettings,
                            )
                        }
                        if (includeChrome && visibleRoute.isTab()) BottomNavigation(visibleRoute, navigation.selectTab)
                    }
                }
            }
            if (saveRouteState) {
                saveableStateHolder.SaveableStateProvider(visibleEntry.stateKey) { routeContent() }
            } else {
                routeContent()
            }
        }
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(appBackgroundBrush(state.backgroundStyle, palette))
                    .safeDrawingPadding(),
        ) {
            val stationaryTabShell = route.isTab() && routeTransitionDirection == 0f
            if (stationaryTabShell) {
                AnimatedContent(
                    targetState = route,
                    modifier = Modifier.fillMaxWidth().clipToBounds(),
                    transitionSpec = {
                        val (enter, exit) = crossfadeTransitions(reduceMotion, TandemMotion.TAB_MILLIS)
                        enter togetherWith exit using null
                    },
                    contentAlignment = Alignment.CenterStart,
                    label = "tandem-tab-header-transition",
                ) { headerRoute -> renderHeader(headerRoute) }
            }
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds()
                        .tandemEdgeBackGesture(backStack.size > 1, handleBack, updateBackGesture),
            ) {
                if (backGestureProgress > 0f && backPreviewRoute != null && backPreviewRoute != routeEntry) {
                    val direction = backGestureDirection
                    renderRoute(
                        backPreviewRoute!!,
                        Modifier
                            .fillMaxSize()
                            .clearAndSetSemantics {}
                            .graphicsLayer {
                                val progress = backGestureProgress
                                translationX = -direction * size.width * (1f - progress)
                            },
                        false,
                        !stationaryTabShell,
                    )
                }
                AnimatedContent(
                    targetState = routeEntry,
                    modifier = Modifier.fillMaxSize().clipToBounds(),
                    transitionSpec = {
                        when {
                            reduceMotion ||
                                skipNextRouteTransition ||
                                initialState.route == AppRoute.STARTUP ||
                                targetState.route == AppRoute.STARTUP ->
                                EnterTransition.None togetherWith ExitTransition.None
                            initialState.route.isTab() && targetState.route.isTab() -> {
                                val movesForward = targetState.route.tabOrder() > initialState.route.tabOrder()
                                val enterFrom = if (movesForward) 1 else -1
                                val durationMillis = TandemMotion.TAB_MILLIS
                                (
                                    slideInHorizontally(
                                        tween(durationMillis, easing = TandemMotion.movementEasing),
                                    ) { it * enterFrom }
                                ) togetherWith
                                    slideOutHorizontally(
                                        tween(durationMillis, easing = TandemMotion.movementEasing),
                                    ) { -it * enterFrom } using null
                            }
                            routeTransitionDirection > 0f ->
                                (
                                    slideInHorizontally(
                                        tween(TandemMotion.ROUTE_MILLIS, easing = TandemMotion.movementEasing),
                                    ) { it }
                                ) togetherWith
                                    slideOutHorizontally(
                                        tween(TandemMotion.ROUTE_MILLIS, easing = TandemMotion.movementEasing),
                                    ) { -it } using null
                            routeTransitionDirection < 0f ->
                                slideInHorizontally(
                                    tween(TandemMotion.ROUTE_MILLIS, easing = TandemMotion.movementEasing),
                                ) { -it } togetherWith
                                    slideOutHorizontally(
                                        tween(TandemMotion.ROUTE_MILLIS, easing = TandemMotion.movementEasing),
                                    ) { it } using null
                            else -> EnterTransition.None togetherWith ExitTransition.None
                        }
                    },
                    label = "tandem-screen-transition",
                ) { visibleEntry ->
                    renderRoute(
                        visibleEntry,
                        Modifier.fillMaxSize().graphicsLayer {
                            if (visibleEntry == backGestureOutgoingEntry && backGestureProgress > 0f) {
                                translationX = backGestureProgress * size.width * backGestureDirection
                            }
                        },
                        true,
                        !stationaryTabShell,
                    )
                }
            }
            if (stationaryTabShell) BottomNavigation(route, navigation.selectTab)
        }
    }
}

private fun AppRoute.isTab(): Boolean =
    this == AppRoute.BUDGET || this == AppRoute.ACCOUNTS || this == AppRoute.ACTIVITY || this == AppRoute.SETTINGS

private fun AppRoute.tabOrder(): Int =
    when (this) {
        AppRoute.BUDGET -> 0
        AppRoute.ACCOUNTS -> 1
        AppRoute.ACTIVITY -> 2
        AppRoute.SETTINGS -> 3
        else -> -1
    }

@Composable
private fun AppHeader(
    route: AppRoute,
    state: AppState,
    showBack: Boolean,
    onBack: () -> Unit,
    onSync: () -> Unit,
    syncStatus: MainCloudStatus,
    onBudget: () -> Unit,
    actualConnected: Boolean,
    liveAccounts: List<LiveAccountSummary>,
    liveBudgetMonth: LiveBudgetMonthSummary?,
) {
    val palette = tandemPalette
    val headerTitle =
        when (route) {
            AppRoute.BUDGET -> state.selectedBudget
            AppRoute.CATEGORY_GROUP ->
                liveBudgetMonth?.groups?.firstOrNull { it.id == state.selectedCategoryGroupId }?.name ?: route.title
            AppRoute.CATEGORY_DETAIL ->
                liveBudgetMonth
                    ?.groups
                    ?.firstOrNull { it.id == state.selectedCategoryGroupId }
                    ?.categories
                    ?.firstOrNull { it.id == state.selectedCategoryId }
                    ?.name ?: route.title
            AppRoute.ACCOUNT_DETAIL ->
                if (state.selectedBudgetId.isNotBlank()) {
                    liveAccounts.firstOrNull { it.id == state.selectedAccountId }?.name ?: route.title
                } else {
                    previewAccountById(state.selectedAccountId).name
                }
            AppRoute.TRANSACTION_DETAIL -> state.transactionPayee.ifBlank { route.title }
            else -> route.title
        }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            Text(
                text = "‹",
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .semantics(mergeDescendants = true) { contentDescription = "Back" }
                        .clickable(role = Role.Button, onClickLabel = "Back", onClick = onBack)
                        .wrapContentAlignment(),
                color = palette.text,
                fontSize = 32.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.width(6.dp))
        }
        Column(
            modifier =
                Modifier.weight(1f).clickable(
                    enabled = route.isTab(),
                    role = Role.Button,
                    onClickLabel = "Choose budget",
                    onClick = onBudget,
                ),
        ) {
            Text(
                text = headerTitle,
                color = palette.text,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (route == AppRoute.BUDGET) {
                Text(
                    if (state.selectedBudgetId.isNotBlank()) {
                        "${state.selectedMonth} · Actual"
                    } else {
                        "${state.selectedMonth} · Sample budget"
                    },
                    color = palette.muted,
                    fontSize = 11.sp,
                )
            }
        }
        if (route.isTab()) {
            val cloudTint by animateColorAsState(
                targetValue =
                    when (syncStatus) {
                        MainCloudStatus.OFF -> palette.muted
                        MainCloudStatus.SYNCED -> palette.positive
                        MainCloudStatus.SYNCING -> palette.syncing
                        MainCloudStatus.ERROR -> palette.negative
                    },
                animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
                label = "main-cloud-status-color",
            )
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .semantics(mergeDescendants = true) {
                            contentDescription =
                                when (syncStatus) {
                                    MainCloudStatus.OFF -> "Sync is off"
                                    MainCloudStatus.SYNCED -> "Everything synced"
                                    MainCloudStatus.SYNCING -> "Syncing changes"
                                    MainCloudStatus.ERROR -> "Sync needs attention"
                                }
                        }.clickable(
                            role = Role.Button,
                            onClickLabel = "Open sync center",
                            onClick = onSync,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (syncStatus == MainCloudStatus.OFF) Lucide.CloudOff else Lucide.Cloud,
                    contentDescription = null,
                    modifier = Modifier.size(25.dp),
                    tint = cloudTint,
                )
            }
        } else if (route == AppRoute.BUDGET_CHOOSER) {
            Text(
                when {
                    actualConnected -> "Actual budgets"
                    state.selectedBudgetId.isNotBlank() -> "Offline copy"
                    else -> "Sample data"
                },
                color = palette.muted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun Modifier.wrapContentAlignment(): Modifier = this

@Composable
private fun BottomNavigation(
    selected: AppRoute,
    selectTab: (AppRoute) -> Unit,
) {
    val palette = tandemPalette
    val items =
        listOf(
            AppRoute.BUDGET to "Budget",
            AppRoute.ACCOUNTS to "Accounts",
            AppRoute.ACTIVITY to "Activity",
            AppRoute.SETTINGS to "Settings",
        )
    Row(
        modifier = Modifier.fillMaxWidth().background(palette.surface).padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 9.dp),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        items.forEach { (route, label) ->
            val active = route == selected
            val tabColor by
                animateColorAsState(
                    targetValue = if (active) palette.accent else palette.muted,
                    animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
                    label = "tab-color-${route.name.lowercase()}",
                )
            val iconScale by
                animateFloatAsState(
                    targetValue = if (active) 1.08f else 1f,
                    animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTROL_MILLIS),
                    label = "tab-icon-scale-${route.name.lowercase()}",
                )
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(13.dp))
                        .selectable(
                            selected = active,
                            role = Role.Tab,
                            onClick = { selectTab(route) },
                        ).semantics(mergeDescendants = true) {
                            contentDescription = label
                            this.selected = active
                        }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                NavigationGlyph(route, tabColor, iconScale)
                Text(
                    label,
                    color = tabColor,
                    fontSize = 10.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun NavigationGlyph(
    route: AppRoute,
    color: Color,
    scale: Float,
) {
    val surface = tandemPalette.surface
    Canvas(
        Modifier
            .size(20.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }.clearAndSetSemantics {},
    ) {
        val stroke = 1.8.dp.toPx()
        val inset = 2.dp.toPx()
        when (route) {
            AppRoute.BUDGET -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(inset, inset * 2),
                    size = Size(size.width - inset * 2, size.height - inset * 4),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                    style = Stroke(stroke),
                )
                drawLine(
                    color,
                    Offset(inset, size.height * 0.45f),
                    Offset(size.width - inset, size.height * 0.45f),
                    stroke,
                )
                drawCircle(color, stroke, Offset(size.width * 0.72f, size.height * 0.67f))
            }
            AppRoute.ACCOUNTS -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(inset, inset * 2),
                    size = Size(size.width - inset * 2, size.height - inset * 4),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                    style = Stroke(stroke),
                )
                drawLine(
                    color,
                    Offset(inset * 2, size.height * 0.42f),
                    Offset(size.width - inset * 2, size.height * 0.42f),
                    stroke,
                )
                drawLine(
                    color,
                    Offset(inset * 2, size.height * 0.7f),
                    Offset(size.width * 0.52f, size.height * 0.7f),
                    stroke,
                )
            }
            AppRoute.ACTIVITY -> {
                drawLine(color, Offset(inset * 2, size.height - inset), Offset(size.width - inset, size.height - inset), stroke)
                drawLine(
                    color,
                    Offset(size.width * 0.28f, size.height - inset),
                    Offset(size.width * 0.28f, size.height * 0.48f),
                    stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.52f, size.height - inset),
                    Offset(size.width * 0.52f, inset * 2),
                    stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.76f, size.height - inset),
                    Offset(size.width * 0.76f, size.height * 0.32f),
                    stroke,
                    cap = StrokeCap.Round,
                )
            }
            AppRoute.SETTINGS -> {
                listOf(0.32f, 0.52f, 0.72f).forEachIndexed { index, fraction ->
                    val y = size.height * fraction
                    drawLine(
                        color,
                        Offset(inset, y),
                        Offset(size.width - inset, y),
                        stroke,
                        cap = StrokeCap.Round,
                    )
                    val x = size.width * listOf(0.36f, 0.68f, 0.45f)[index]
                    drawCircle(surface, stroke * 1.5f, Offset(x, y))
                    drawCircle(color, stroke, Offset(x, y), style = Stroke(stroke))
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun ScreenContent(
    route: AppRoute,
    state: AppState,
    deviceName: String,
    navigation: AppNavigation,
    mobileSession: TandemMobileSession,
    onPairDevice: () -> Unit,
    onRetryStoredSession: () -> Unit,
    onSelectBudget: (BudgetCatalogItem) -> Unit,
    onRefreshBudgets: () -> Unit,
    onOpenSelectedBudget: () -> Unit,
    onRetrySync: () -> Unit,
    backgroundSyncSupported: Boolean,
    merchantIconPack: MerchantIconPack?,
    merchantIconPackLoadError: String?,
    liveTransactions: List<TransactionItem>,
    liveAccounts: List<LiveAccountSummary>,
    liveBudgetMonth: LiveBudgetMonthSummary?,
    onSyncNow: () -> Unit,
    onSaveTransaction: () -> Unit,
    onCreateTransaction: () -> Unit,
    onResolveConflict: (String, Boolean) -> Unit,
    onDiscardWrite: (String) -> Unit,
    onRetryUncertainWrite: (String) -> Unit,
    onForgetDevice: () -> Unit,
    onRequestNotificationPermission: (() -> Unit)?,
    onOpenNotificationSettings: (() -> Unit)?,
) {
    when (route) {
        AppRoute.STARTUP -> StartupScreen()
        AppRoute.WELCOME -> WelcomeScreen(navigation)
        AppRoute.SERVER_ADDRESS -> ServerAddressScreen(state, navigation)
        AppRoute.PAIRING_OPTIONS -> PairingOptionsScreen(navigation)
        AppRoute.SCAN_QR -> ScanQrScreen(navigation)
        AppRoute.PAIRING_CODE -> PairingCodeScreen(state, mobileSession, navigation, onPairDevice, onRetryStoredSession)
        AppRoute.PAIRING_RESULT -> PairingResultScreen(mobileSession, navigation)
        AppRoute.BUDGET_CHOOSER ->
            BudgetChooserScreen(state, mobileSession, navigation, onSelectBudget, onRefreshBudgets, onOpenSelectedBudget)
        AppRoute.UNLOCK_BUDGET -> UnlockBudgetScreen(state, navigation)
        AppRoute.FIRST_SYNC -> FirstSyncScreen(state, mobileSession, navigation, onRetrySync)
        AppRoute.BUDGET -> BudgetScreen(state, navigation, mobileSession, liveTransactions, liveBudgetMonth)
        AppRoute.MONTH_PICKER -> MonthPickerScreen(state, navigation)
        AppRoute.CATEGORY_GROUP -> CategoryGroupScreen(state, navigation, liveBudgetMonth)
        AppRoute.CATEGORY_DETAIL -> CategoryDetailScreen(state, navigation, liveBudgetMonth)
        AppRoute.ACCOUNTS -> AccountsScreen(state, navigation, liveAccounts, liveTransactions)
        AppRoute.ACCOUNT_DETAIL -> AccountDetailScreen(state, navigation, liveAccounts, liveTransactions)
        AppRoute.ACCOUNT_OPTIONS -> AccountOptionsScreen(state, liveAccounts)
        AppRoute.RECONCILIATION -> ReconciliationScreen(state)
        AppRoute.ACTIVITY -> ActivityScreen(state, navigation, liveTransactions)
        AppRoute.ACTIVITY_SEARCH -> ActivitySearchScreen(state, navigation, liveTransactions)
        AppRoute.ACTIVITY_FILTER -> ActivityFilterScreen()
        AppRoute.TRANSACTION_DETAIL -> TransactionDetailScreen(state, mobileSession, navigation, merchantIconPack)
        AppRoute.NEW_TRANSACTION, AppRoute.EDIT_TRANSACTION ->
            TransactionFormScreen(route, state, navigation, mobileSession, onSaveTransaction, onCreateTransaction)
        AppRoute.CHOOSE_ACCOUNT -> {
            val values =
                if (state.selectedBudgetId.isBlank()) {
                    listOf("Checking", "Savings", "Credit Card", "Cash").map { SelectorItem(it, it) }
                } else {
                    liveAccountSummaries(mobileSession)
                        .filterNot { it.offBudget }
                        .map { SelectorItem(it.id, it.name) }
                }
            SelectorScreen(
                values,
                if (state.selectedBudgetId.isBlank()) state.transactionAccount else state.newTransactionAccountId,
                live = state.selectedBudgetId.isNotBlank(),
            ) { item ->
                state.transactionAccount = item.label
                state.newTransactionAccountId = if (state.selectedBudgetId.isBlank()) "" else item.id
                navigation.back()
            }
        }
        AppRoute.CHOOSE_PAYEE -> {
            val values =
                if (state.selectedBudgetId.isBlank()) {
                    listOf("Corner Market", "Electric Company", "Acme Corp", "Riverside Cafe", "City Transit")
                        .map { SelectorItem(it, it) }
                } else {
                    listOf(SelectorItem("", "No payee")) +
                        mobileSession.payeeRecords
                            .mapNotNull { record ->
                                record.recordName()?.let { SelectorItem(record.recordId, it) }
                            }.distinctBy { it.id }
                }
            SelectorScreen(
                values,
                if (state.selectedBudgetId.isBlank()) state.transactionPayee else state.newTransactionPayeeId,
                live = state.selectedBudgetId.isNotBlank(),
            ) { item ->
                state.transactionPayee = if (item.label == "No payee") "" else item.label
                state.newTransactionPayeeId = if (state.selectedBudgetId.isBlank()) "" else item.id
                navigation.back()
            }
        }
        AppRoute.CHOOSE_CATEGORY -> {
            val values =
                if (state.selectedBudgetId.isBlank()) {
                    listOf("Groceries", "Utilities", "Dining", "Rent", "Transport", "Personal")
                        .map { SelectorItem(it, it) }
                } else {
                    listOf(SelectorItem("", "Uncategorized")) +
                        mobileSession.categoryRecords
                            .mapNotNull { record ->
                                record.recordName()?.let { SelectorItem(record.recordId, it) }
                            }.distinctBy { it.id }
                }
            SelectorScreen(
                values,
                if (state.selectedBudgetId.isBlank()) state.transactionCategory else state.newTransactionCategoryId,
                live = state.selectedBudgetId.isNotBlank(),
            ) { item ->
                state.transactionCategory = if (item.label == "Uncategorized") "" else item.label
                state.newTransactionCategoryId = if (state.selectedBudgetId.isBlank()) "" else item.id
                navigation.back()
            }
        }
        AppRoute.CHOOSE_DATE -> DatePickerScreen(state, navigation)
        AppRoute.SAVE_RESULT -> SaveResultScreen(state, navigation)
        AppRoute.SYNC_CENTER -> SyncCenterScreen(state, mobileSession, navigation, onSyncNow)
        AppRoute.CONFLICT_INBOX -> ConflictInboxScreen(state, mobileSession, navigation)
        AppRoute.CONFLICT_REVIEW -> ConflictReviewScreen(state, mobileSession, onResolveConflict)
        AppRoute.UNCERTAIN_WRITE -> UncertainWriteScreen(state, mobileSession, onSyncNow, onRetryUncertainWrite, onDiscardWrite)
        AppRoute.REJECTED_WRITE -> RejectedWriteScreen(state, mobileSession, navigation, onDiscardWrite)
        AppRoute.SETTINGS -> SettingsScreen(state, navigation)
        AppRoute.APPEARANCE -> AppearanceScreen(state, navigation)
        AppRoute.THEME_MODE -> ThemeModeScreen(state)
        AppRoute.ACCENT_COLOR -> AccentColorScreen(state)
        AppRoute.BACKGROUND_MOTION -> BackgroundScreen(state)
        AppRoute.HOME_LAYOUT -> HomeLayoutScreen(state)
        AppRoute.BUDGET_PREFERENCES -> BudgetPreferencesScreen(state, mobileSession)
        AppRoute.SYNC_PREFERENCES ->
            SyncPreferencesScreen(
                state,
                mobileSession,
                onSyncNow,
                backgroundSyncSupported,
            )
        AppRoute.SERVER_DEVICE ->
            ServerDeviceScreen(state, deviceName, mobileSession, navigation, onForgetDevice)
        AppRoute.SECURITY_PRIVACY -> SecurityScreen(mobileSession)
        AppRoute.NOTIFICATIONS ->
            NotificationsScreen(
                state,
                onRequestNotificationPermission,
                onOpenNotificationSettings,
            )
        AppRoute.LOCAL_DATA -> LocalDataScreen(state, mobileSession, onSyncNow)
        AppRoute.ABOUT -> AboutScreen(merchantIconPack, merchantIconPackLoadError, navigation)
        AppRoute.MERCHANT_ICON_LICENSES -> MerchantIconLicensesScreen(merchantIconPack, merchantIconPackLoadError)
    }
}

@Composable
private fun PageColumn(
    verticalSpacing: androidx.compose.ui.unit.Dp = 16.dp,
    horizontalPadding: androidx.compose.ui.unit.Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = LocalRouteScrollState.current ?: rememberScrollState()
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = horizontalPadding, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        content = content,
    )
}

private fun appBackgroundBrush(
    style: String,
    palette: TandemPalette,
): Brush =
    when (style) {
        "Solid surface" -> Brush.verticalGradient(listOf(palette.background, palette.background))
        "Low-motion glow" -> Brush.radialGradient(listOf(palette.accentSurface, palette.background), radius = 1_300f)
        else -> Brush.verticalGradient(listOf(palette.background, palette.surface, palette.background))
    }

@Composable
private fun SampleBanner(message: String = "This screen remains a preview") {
    val palette = tandemPalette
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(12.dp),
                ).background(palette.accentSurface)
                .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("●", color = palette.accent, fontSize = 9.sp)
        Text("Sample data", color = palette.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(message, color = palette.muted, fontSize = 11.sp)
    }
}

@Composable
private fun AttentionBanner(
    title: String,
    detail: String,
    action: String,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    SurfaceCard(onClick = onClick, onClickLabel = action) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(palette.warning.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("!", color = palette.warning, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    color = palette.text,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(detail, color = palette.muted, fontSize = 10.sp, lineHeight = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                action,
                color = palette.accent,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
            )
            Text("›", color = palette.muted, fontSize = 18.sp)
        }
    }
}

@Composable
private fun Heading(
    title: String,
    subtitle: String? = null,
) {
    val palette = tandemPalette
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, color = palette.text, fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, color = palette.muted, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun BodyText(text: String) {
    Text(text, color = tandemPalette.muted, fontSize = 14.sp, lineHeight = 20.sp)
}

@Composable
private fun AnimatedStatusText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    fontWeight: FontWeight = FontWeight.Normal,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    animateSizeChanges: Boolean = false,
) {
    val reduceMotion = tandemReduceMotion
    AnimatedContent(
        targetState = AnimatedTextContent(text, color),
        modifier =
            modifier.then(
                if (animateSizeChanges) {
                    Modifier.animateContentSize(
                        animationSpec = tandemMotionSpec(reduceMotion, TandemMotion.CONTENT_SIZE_MILLIS),
                    )
                } else {
                    Modifier
                },
            ),
        transitionSpec = {
            val (enter, exit) = crossfadeTransitions(reduceMotion, TandemMotion.STATUS_CHANGE_MILLIS)
            enter togetherWith exit using null
        },
        contentKey = { it.text },
        contentAlignment = Alignment.CenterStart,
        label = "sync-status-text-transition",
    ) { visibleText ->
        val visibleColor by
            animateColorAsState(
                targetValue = visibleText.color,
                animationSpec = tandemMotionSpec(reduceMotion, TandemMotion.COLOR_MILLIS),
                label = "sync-status-text-color",
            )
        Text(
            visibleText.text,
            color = visibleColor,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = fontWeight,
            maxLines = maxLines,
            overflow = overflow,
        )
    }
}

private data class AnimatedTextContent(
    val text: String,
    val color: Color,
)

private fun crossfadeTransitions(
    reduceMotion: Boolean,
    durationMillis: Int,
): Pair<EnterTransition, ExitTransition> =
    if (reduceMotion) {
        EnterTransition.None to ExitTransition.None
    } else {
        val fadeMillis = minOf(TandemMotion.CROSSFADE_MILLIS, durationMillis)
        Pair(
            fadeIn(tween(fadeMillis, easing = LinearEasing)),
            fadeOut(tween(fadeMillis, easing = LinearEasing)),
        )
    }

@Composable
private fun LabelText(text: String) {
    Text(text, color = tandemPalette.muted, fontSize = 11.sp, letterSpacing = 0.2.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = tandemPalette
    Surface(
        modifier =
            modifier
                .animateContentSize(animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTENT_SIZE_MILLIS))
                .then(
                    if (onClick != null) {
                        Modifier.clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
                    } else {
                        Modifier
                    },
                ),
        shape = RoundedCornerShape(18.dp),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.border),
    ) {
        Column(content = content)
    }
}

@Composable
private fun PrimaryButton(
    label: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    val backgroundColor by
        animateColorAsState(
            targetValue = if (enabled) palette.accent else palette.raised,
            animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
            label = "primary-button-background",
        )
    val textColor by
        animateColorAsState(
            targetValue = if (enabled) palette.accentContent else palette.muted,
            animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
            label = "primary-button-text",
        )
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(backgroundColor)
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.animateContentSize(tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTENT_SIZE_MILLIS)),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedReveal(visible = loading, animateHeight = false) {
                TandemLoadingIndicator(Modifier.size(17.dp), textColor, 2.dp)
            }
            AnimatedStatusText(label, textColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SecondaryButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    val borderColor by
        animateColorAsState(
            targetValue = if (enabled) palette.border else palette.raised,
            animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
            label = "secondary-button-border",
        )
    val textColor by
        animateColorAsState(
            targetValue = if (enabled) palette.text else palette.muted,
            animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
            label = "secondary-button-text",
        )
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp)
                .clip(RoundedCornerShape(15.dp))
                .border(1.dp, borderColor, RoundedCornerShape(15.dp))
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FieldLabel(label: String) {
    Text(label, color = tandemPalette.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun TextFieldCard(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    singleLine: Boolean = true,
    isSecret: Boolean = false,
    secretKeyboardType: KeyboardType = KeyboardType.Password,
    keyboardOptions: KeyboardOptions? = null,
    textAlign: TextAlign = TextAlign.Start,
) {
    val palette = tandemPalette
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        FieldLabel(label)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.surface)
                    .border(1.dp, palette.border, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                keyboardOptions =
                    keyboardOptions ?: if (isSecret) {
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = secretKeyboardType,
                        )
                    } else {
                        KeyboardOptions.Default
                    },
                visualTransformation = if (isSecret) PasswordVisualTransformation() else VisualTransformation.None,
                textStyle = TextStyle(color = palette.text, fontSize = 15.sp, lineHeight = 20.sp, textAlign = textAlign),
                cursorBrush = SolidColor(palette.accent),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(
                                placeholder,
                                modifier = Modifier.fillMaxWidth(),
                                color = palette.muted,
                                fontSize = 14.sp,
                                textAlign = textAlign,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
    }
}

@Composable
private fun ReadOnlyField(
    label: String,
    value: String,
    leading: String = "",
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        FieldLabel(label)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.surface)
                    .border(1.dp, palette.border, RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button, onClickLabel = "$label, $value", onClick = onClick)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (leading.isNotEmpty()) {
                Text(
                    leading,
                    modifier = Modifier.clearAndSetSemantics {},
                    color = palette.accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(value, color = palette.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("›", modifier = Modifier.clearAndSetSemantics {}, color = palette.muted, fontSize = 22.sp)
        }
    }
}

@Composable
private fun ListRow(
    icon: String,
    title: String,
    subtitle: String? = null,
    trailing: String? = null,
    trailingColor: Color? = null,
    dense: Boolean = false,
    leadingVector: ImageVector? = null,
    fallbackVector: ImageVector? = null,
    leadingIconResource: DrawableResource? = null,
    animateChanges: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val palette = tandemPalette
    val resolvedPainter = leadingIconResource?.let { painterResource(it) }
    val resolvedVector = if (resolvedPainter == null) leadingVector ?: fallbackVector else null
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = if (dense) 48.dp else 62.dp)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(role = Role.Button, onClickLabel = title, onClick = onClick)
                    } else {
                        Modifier
                    },
                ).padding(horizontal = 14.dp, vertical = if (dense) 6.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(if (dense) 30.dp else 36.dp).clip(RoundedCornerShape(12.dp)).background(palette.raised),
            contentAlignment = Alignment.Center,
        ) {
            if (resolvedPainter != null) {
                Image(
                    painter = resolvedPainter,
                    contentDescription = null,
                    modifier = Modifier.size(if (dense) 22.dp else 26.dp).padding(3.dp),
                    contentScale = ContentScale.Fit,
                )
            } else if (resolvedVector != null) {
                Icon(
                    imageVector = resolvedVector,
                    contentDescription = null,
                    modifier = Modifier.size(if (dense) 17.dp else 20.dp),
                    tint = palette.accent,
                )
            } else {
                Text(
                    icon,
                    modifier = Modifier.clearAndSetSemantics {},
                    color = palette.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (animateChanges) {
                AnimatedStatusText(
                    title,
                    palette.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    title,
                    color = palette.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (subtitle != null) {
                if (animateChanges) {
                    AnimatedStatusText(
                        subtitle,
                        palette.muted,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(subtitle, color = palette.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (trailing != null) {
            val effectiveColor = trailingColor ?: palette.muted
            if (animateChanges) {
                AnimatedStatusText(
                    trailing,
                    effectiveColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(trailing, color = effectiveColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        } else if (onClick != null) {
            Text("›", modifier = Modifier.clearAndSetSemantics {}, color = palette.muted, fontSize = 20.sp)
        }
    }
}

internal fun financeIcon(label: String): ImageVector? {
    val words = labelWords(label)

    fun matches(vararg phrases: String): Boolean = phrases.any { containsPhrase(words, it) }

    return when {
        matches("pet", "pets", "veterinary", "vet clinic", "animal care") -> Lucide.PawPrint
        matches("flight", "airfare", "airport", "plane", "travel") -> Lucide.Plane
        matches("rent", "mortgage", "housing", "home maintenance", "home repair", "landlord") -> Lucide.House
        matches("electric", "utility", "utilities", "power bill", "water bill", "energy", "internet bill") -> Lucide.Zap
        matches("grocery", "groceries", "supermarket", "produce", "market", "shopping", "household supplies") -> Lucide.ShoppingCart
        matches("cafe", "coffee") -> Lucide.Coffee
        matches("dining", "restaurant", "takeout", "food and drink") -> Lucide.Utensils
        matches(
            "transport",
            "transit",
            "car payment",
            "auto repair",
            "fuel",
            "petrol",
            "gas station",
            "parking",
            "train fare",
        ) -> Lucide.CarFront
        matches("insurance", "coverage", "policy premium") -> Lucide.ShieldCheck
        matches("medical", "healthcare", "doctor", "pharmacy", "dental", "hospital") -> Lucide.HeartPulse
        matches("paycheck", "payroll", "salary", "income") -> Lucide.BriefcaseBusiness
        else -> null
    }
}

private fun containsPhrase(
    words: List<String>,
    phrase: String,
): Boolean {
    val phraseWords = labelWords(phrase)
    return phraseWords.isNotEmpty() && words.windowed(phraseWords.size).any { it == phraseWords }
}

@Composable
private fun SectionCard(
    title: String,
    detail: String? = null,
    action: String? = null,
    onClick: (() -> Unit)? = null,
    animateDetail: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = tandemPalette
    SurfaceCard(onClick = onClick) {
        Column {
            if (title.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(title, color = palette.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        if (detail != null) {
                            if (animateDetail) {
                                AnimatedStatusText(detail, palette.muted, fontSize = 11.sp, maxLines = 2)
                            } else {
                                Text(detail, color = palette.muted, fontSize = 11.sp)
                            }
                        }
                    }
                    if (action != null) Text(action, color = palette.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.border.copy(alpha = 0.75f)))
            }
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val palette = tandemPalette
    val thumbOffset by
        animateDpAsState(
            targetValue = if (checked) 18.dp else 0.dp,
            animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTROL_MILLIS),
            label = "toggle-thumb-position",
        )
    val trackColor by
        animateColorAsState(
            targetValue = if (checked) palette.accent else palette.raised,
            animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
            label = "toggle-track-color",
        )
    val thumbColor by
        animateColorAsState(
            targetValue = if (checked) palette.accentContent else palette.muted,
            animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
            label = "toggle-thumb-color",
        )
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 66.dp)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
                .padding(horizontal = 15.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = palette.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, color = palette.muted, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Box(
            modifier =
                Modifier
                    .width(
                        42.dp,
                    ).height(24.dp)
                    .clip(CircleShape)
                    .background(trackColor)
                    .padding(3.dp),
        ) {
            Box(
                Modifier
                    .offset(x = thumbOffset)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(thumbColor),
            )
        }
    }
}

@Composable
private fun SettingRow(
    icon: String,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    onClick: () -> Unit,
) {
    ListRow(icon, title, subtitle, value, onClick = onClick)
}

@Composable
private fun ChoiceRow(
    title: String,
    subtitle: String? = null,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    val selectionColor by
        animateColorAsState(
            targetValue = if (selected) palette.accent else palette.muted,
            animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
            label = "choice-selection-color",
        )
    val selectionProgress by
        animateFloatAsState(
            targetValue = if (selected) 1f else 0f,
            animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTROL_MILLIS),
            label = "choice-selection-progress",
        )
    SurfaceCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp).padding(horizontal = 15.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = palette.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                if (subtitle != null) Text(subtitle, color = palette.muted, fontSize = 11.sp)
            }
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, selectionColor, CircleShape)
                    .padding(5.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = selectionProgress
                            scaleX = 0.75f + 0.25f * selectionProgress
                            scaleY = 0.75f + 0.25f * selectionProgress
                        }.clip(CircleShape)
                        .background(palette.accent),
                )
            }
        }
    }
}

@Composable
private fun PreviewNote(text: String) {
    val palette = tandemPalette
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(palette.raised)
                .padding(13.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("i", color = palette.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        AnimatedStatusText(
            text,
            palette.muted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            animateSizeChanges = true,
        )
    }
}

@Composable
private fun AmountHero(
    label: String,
    amount: String,
    supporting: String,
    action: (() -> Unit)? = null,
) {
    val palette = tandemPalette
    SurfaceCard(onClick = action) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            LabelText(label)
            Text(amount, color = palette.text, fontSize = 31.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
            Text(supporting, color = palette.muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DividerLine() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(tandemPalette.border.copy(alpha = 0.8f)))
}

@Composable
private fun Badge(
    label: String,
    color: Color = tandemPalette.accent,
) {
    val motionSpec: FiniteAnimationSpec<Color> = tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS)
    val animatedColor by androidx.compose.animation.animateColorAsState(color, motionSpec, label = "badge-color")
    Box(
        Modifier
            .animateContentSize(tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTENT_SIZE_MILLIS))
            .clip(RoundedCornerShape(999.dp))
            .background(animatedColor.copy(alpha = 0.13f))
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        AnimatedStatusText(label, color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun TandemLoadingIndicator(
    modifier: Modifier,
    color: Color,
    strokeWidth: androidx.compose.ui.unit.Dp,
) {
    if (tandemReduceMotion) {
        Box(modifier.border(strokeWidth, color, CircleShape))
    } else {
        CircularProgressIndicator(modifier = modifier, color = color, strokeWidth = strokeWidth)
    }
}

@Composable
private fun AnimatedReveal(
    visible: Boolean,
    animateHeight: Boolean = true,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val enter =
        if (tandemReduceMotion) {
            EnterTransition.None
        } else {
            fadeIn(tween(TandemMotion.REVEAL_MILLIS, easing = TandemMotion.enterEasing)) +
                if (animateHeight) {
                    expandVertically(
                        expandFrom = Alignment.Top,
                        animationSpec = tween(TandemMotion.REVEAL_MILLIS, easing = TandemMotion.movementEasing),
                        clip = true,
                    )
                } else {
                    EnterTransition.None
                }
        }
    val exit =
        if (tandemReduceMotion) {
            ExitTransition.None
        } else {
            fadeOut(tween(TandemMotion.REVEAL_MILLIS, easing = TandemMotion.exitEasing)) +
                if (animateHeight) {
                    shrinkVertically(
                        shrinkTowards = Alignment.Top,
                        animationSpec = tween(TandemMotion.REVEAL_MILLIS, easing = TandemMotion.movementEasing),
                        clip = true,
                    )
                } else {
                    ExitTransition.None
                }
        }
    AnimatedVisibility(visible = visible, enter = enter, exit = exit, content = content)
}

@Composable
private fun AnimatedAttentionContent(
    showingAttention: Boolean,
    animateSizeChanges: Boolean = false,
    content: @Composable AnimatedContentScope.(Boolean) -> Unit,
) {
    val reduceMotion = tandemReduceMotion
    AnimatedContent(
        targetState = showingAttention,
        modifier =
            if (animateSizeChanges) {
                Modifier.animateContentSize(tandemMotionSpec(reduceMotion, TandemMotion.CONTENT_SIZE_MILLIS))
            } else {
                Modifier
            },
        transitionSpec = {
            val (enter, exit) = crossfadeTransitions(reduceMotion, TandemMotion.STATUS_CHANGE_MILLIS)
            enter togetherWith exit using null
        },
        contentAlignment = Alignment.TopStart,
        label = "attention-content-transition",
        content = content,
    )
}

@Composable
private fun AnimatedPreviewNote(text: String?) {
    var retainedText by remember { mutableStateOf(text) }
    LaunchedEffect(text) {
        if (text != null) retainedText = text
    }
    AnimatedReveal(visible = text != null) {
        PreviewNote(text ?: retainedText.orEmpty())
    }
}

private fun Color.withOpacity(alpha: Float) = copy(alpha = alpha)

@Composable
private fun StartupScreen() {
    val palette = tandemPalette
    var showLoadingIndicator by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(300)
        showLoadingIndicator = true
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TandemBrandMark(Modifier.size(96.dp))
        AnimatedReveal(showLoadingIndicator) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(24.dp))
                TandemLoadingIndicator(
                    modifier = Modifier.size(24.dp),
                    color = palette.accent,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.height(16.dp))
                Text("Opening your budget", color = palette.muted, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun WelcomeScreen(navigation: AppNavigation) {
    val palette = tandemPalette
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        TandemBrandMark(Modifier.size(72.dp))
        Heading("Budget and account overview", "Plan spending, review accounts, and follow recent changes.")
        SurfaceCard {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Badge("Private by design")
                    Text("Actual Budget remains the source of truth", color = palette.muted, fontSize = 11.sp)
                }
                Text(
                    "Review plans, account balances, and recent activity.",
                    color = palette.text,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Badge("Offline ready", palette.positive)
                    Badge("Conflict review", palette.warning)
                }
            }
        }
        Spacer(Modifier.weight(1f, fill = false))
        PrimaryButton("Get started") { navigation.open(AppRoute.SERVER_ADDRESS) }
        SecondaryButton("View sample budget") { navigation.selectTab(AppRoute.BUDGET) }
        PreviewNote("Names and amounts are sample data. This screen does not contact a server.")
    }
}

@Composable
private fun ServerAddressScreen(
    state: AppState,
    navigation: AppNavigation,
) {
    PageColumn {
        Heading("Connect to server", "Enter the server address used to pair this device.")
        TextFieldCard("Server URL", state.serverUrl, { state.serverUrl = it }, "https://budget.example.com")
        SurfaceCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LabelText("Before you continue")
                BodyText(
                    "Use the HTTPS address provided by the server administrator. The budget password only unlocks an encrypted budget; it is never used to pair this device.",
                )
            }
        }
        PrimaryButton("Continue securely", enabled = state.serverUrl.isNotBlank()) { navigation.open(AppRoute.PAIRING_OPTIONS) }
    }
}

@Composable
private fun PairingOptionsScreen(navigation: AppNavigation) {
    PageColumn {
        Heading("Choose a pairing method", "Use the pairing code shown in server setup.")
        SettingRow("▦", "Scan pairing QR", "Scan the code shown on the server", onClick = { navigation.open(AppRoute.SCAN_QR) })
            .let { }
        SettingRow(
            "⌗",
            "Enter pairing code",
            "Enter the 8-character one-time pairing code",
            onClick = { navigation.open(AppRoute.PAIRING_CODE) },
        ).let { }
        PreviewNote("Pairing codes work once and expire after five minutes.")
    }
}

@Composable
private fun ScanQrScreen(navigation: AppNavigation) {
    val palette = tandemPalette
    PageColumn {
        Heading("Align the camera", "Point it at the pairing QR code shown on the server.")
        SurfaceCard {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(270.dp)
                    .padding(18.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(palette.raised),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("⌗", color = palette.accent, fontSize = 62.sp)
                    Text("Camera view", color = palette.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Camera scanning is not available here yet.",
                        color = palette.muted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        PrimaryButton("Enter code instead") { navigation.open(AppRoute.PAIRING_CODE) }
        PreviewNote("Camera access is not requested on this screen.")
    }
}

@Composable
private fun PairingCodeScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    onPairDevice: () -> Unit,
    onRetryStoredSession: () -> Unit,
) {
    PageColumn {
        Heading("Enter the code", "Use the 8-character pairing code shown in server setup.")
        TextFieldCard(
            "Pairing code",
            state.pairingCode,
            { state.pairingCode = sanitizePairingCodeInput(it) },
            "For example, 4DF7-J1M9",
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                ),
            textAlign = TextAlign.Center,
        )
        BodyText("The code works once and expires after five minutes. It is sent securely to the server only when you pair.")
        mobileSession.pairingError?.let { error ->
            Text(error, color = tandemPalette.warning, fontSize = 13.sp, lineHeight = 19.sp)
        }
        mobileSession.credentialStorageError?.let { error ->
            Text(error, color = tandemPalette.warning, fontSize = 13.sp, lineHeight = 19.sp)
            SecondaryButton(
                if (mobileSession.isRestoringStoredSession) "Checking secure storage…" else "Retry saved pairing",
                enabled = !mobileSession.isRestoringStoredSession,
                onClick = onRetryStoredSession,
            )
        }
        PrimaryButton(
            if (mobileSession.isPairing) "Pairing securely…" else "Pair this device",
            enabled = !mobileSession.isPairing && state.pairingCode.filter(Char::isLetterOrDigit).length == 8,
            onClick = onPairDevice,
        )
    }
}

@Composable
private fun PairingResultScreen(
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
) {
    val palette = tandemPalette
    PageColumn {
        Spacer(Modifier.height(24.dp))
        Box(Modifier.size(60.dp).clip(CircleShape).background(palette.accentSurface), contentAlignment = Alignment.Center) {
            Text("✓", color = palette.accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        Heading("Device paired", "Choose an Actual budget to open on this phone.")
        mobileSession.pairedDevice?.let { device ->
            SurfaceCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LabelText("Device access")
                    BodyText(
                        "This device can access ${device.budgetIds.size} authorized budget scope${if (device.budgetIds.size == 1) "" else "s"}.",
                    )
                    BodyText(
                        when {
                            mobileSession.isCredentialStored ->
                                "The device credential is saved in this phone’s secure storage. Pairing codes and budget passwords are never saved."
                            mobileSession.credentialStorageError != null ->
                                "Secure storage is unavailable, so this pairing lasts only until the app closes. Pairing codes and budget passwords are never saved."
                            else ->
                                "This pairing lasts only until the app closes. Pairing codes and budget passwords are never saved."
                        },
                    )
                }
            }
        }
        mobileSession.budgetError?.let { error ->
            Text(error, color = palette.warning, fontSize = 13.sp, lineHeight = 19.sp)
        }
        PrimaryButton("Choose a budget", enabled = mobileSession.pairedDevice != null) {
            navigation.open(AppRoute.BUDGET_CHOOSER)
        }
    }
}

@Composable
private fun BudgetChooserScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    onSelectBudget: (BudgetCatalogItem) -> Unit,
    onRefreshBudgets: () -> Unit,
    onOpenSelectedBudget: () -> Unit,
) {
    val cachedCatalog =
        remember(state.cachedBudgetCatalogJson, mobileSession.cachedBudgets) {
            cachedBudgetsWithLocalSnapshots(
                decodeCachedBudgetCatalog(state.cachedBudgetCatalogJson),
                mobileSession.cachedBudgets,
            )
        }
    val selectableBudgets =
        budgetChoicesForSelection(mobileSession.budgets, cachedCatalog, mobileSession.budgetCatalogLoaded)
    val usingCachedCatalog = !mobileSession.budgetCatalogLoaded && cachedCatalog.isNotEmpty()
    val hasCachedOnlyBudgets = cachedCatalog.any { cached -> mobileSession.budgets.none { it.budgetId == cached.budgetId } }
    val cachedBudgetSelected = cachedCatalog.any { it.budgetId == state.selectedBudgetId }
    val selectedBudgetIsAvailable = selectableBudgets.any { it.budgetId == state.selectedBudgetId }
    PageColumn {
        Heading(
            if (usingCachedCatalog || (mobileSession.budgets.isEmpty() && cachedCatalog.isNotEmpty())) {
                "Saved budgets"
            } else {
                "Select a budget"
            },
            if (usingCachedCatalog) {
                "Open a budget already saved on this phone. You can sync again when the server is available."
            } else if (hasCachedOnlyBudgets) {
                "Choose a server budget or open one of the saved copies on this phone."
            } else {
                "Choose one of the budgets authorized for this device."
            },
        )
        when {
            usingCachedCatalog || (mobileSession.budgets.isEmpty() && cachedCatalog.isNotEmpty()) -> {
                BodyText("The live budget list is unavailable. These copies stay on this phone.")
                selectableBudgets.forEach { budget ->
                    ChoiceRow(
                        budget.name,
                        if (budget.isEncrypted) "Encrypted budget · saved on this phone" else "Saved on this phone",
                        selected = state.selectedBudgetId == budget.budgetId,
                    ) { onSelectBudget(budget) }
                }
            }
            selectableBudgets.isNotEmpty() -> {
                selectableBudgets.forEach { budget ->
                    val isOnServer = mobileSession.budgets.any { it.budgetId == budget.budgetId }
                    val detail =
                        when {
                            !isOnServer && budget.isEncrypted -> "Encrypted budget · saved on this phone"
                            !isOnServer -> "Saved on this phone"
                            budget.isEncrypted -> "Encrypted Actual budget"
                            else -> "Available on this server"
                        }
                    ChoiceRow(budget.name, detail, selected = state.selectedBudgetId == budget.budgetId) {
                        onSelectBudget(budget)
                    }
                }
            }
            mobileSession.isLoadingBudgets -> BodyText("Loading budgets from the Tandem server…")
            mobileSession.budgets.isEmpty() && mobileSession.isAuthenticated -> {
                mobileSession.budgetError?.let { error ->
                    Text(error, color = tandemPalette.warning, fontSize = 13.sp, lineHeight = 19.sp)
                } ?: BodyText("No budgets are available to this paired device.")
                SecondaryButton("Retry loading budgets", onClick = onRefreshBudgets)
            }
            mobileSession.budgets.isEmpty() -> {
                SurfaceCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LabelText("Pair this device first")
                        BodyText("The budget list comes from the server after a device is paired.")
                    }
                }
                SecondaryButton("Pair a device") { navigation.open(AppRoute.SERVER_ADDRESS) }
            }
        }
        mobileSession.budgetError?.takeIf { selectableBudgets.isNotEmpty() }?.let { error ->
            Text(error, color = tandemPalette.warning, fontSize = 12.sp)
        }
        PrimaryButton(
            if (state.selectedBudgetEncrypted && state.encryptedPassword.isBlank() && !cachedBudgetSelected) {
                "Unlock and open"
            } else {
                "Open budget"
            },
            enabled = selectedBudgetIsAvailable && (!mobileSession.isLoadingBudgets || cachedBudgetSelected),
            onClick = onOpenSelectedBudget,
        )
    }
}

@Composable
private fun UnlockBudgetScreen(
    state: AppState,
    navigation: AppNavigation,
) {
    PageColumn {
        Heading("Encrypted budget", "Enter its password to continue.")
        TextFieldCard(
            "Budget password",
            state.encryptedPassword,
            { state.encryptedPassword = it },
            "Enter to unlock this encrypted budget",
            isSecret = true,
        )
        BodyText(
            "This password is sent only to unlock the selected budget and is cleared after the initial sync. It is not saved in app state.",
        )
        PrimaryButton("Unlock budget", enabled = state.encryptedPassword.isNotBlank()) { navigation.open(AppRoute.FIRST_SYNC) }
    }
}

@Composable
private fun FirstSyncScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    onRetrySync: () -> Unit,
) {
    val palette = tandemPalette
    PageColumn {
        Heading("Initial setup", "A private local copy keeps this budget available offline.")
        SurfaceCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                listOf("Connecting to Tandem", "Reading the Actual budget", "Saving a local copy").forEachIndexed { index, title ->
                    val complete = mobileSession.syncComplete || (!mobileSession.isSyncing && mobileSession.syncError != null && index == 0)
                    Row(horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically) {
                        val icon =
                            if (complete) {
                                "✓"
                            } else if (mobileSession.isSyncing && index == 0) {
                                "…"
                            } else {
                                "${index + 1}"
                            }
                        val color =
                            if (complete) {
                                palette.positive
                            } else if (mobileSession.syncError !=
                                null
                            ) {
                                palette.warning
                            } else {
                                palette.accent
                            }
                        Badge(icon, color)
                        Text(title, color = palette.text, fontSize = 13.sp)
                    }
                }
            }
        }
        val syncMessage =
            when {
                mobileSession.isSyncing && mobileSession.syncError != null ->
                    "Retrying initial sync · ${mobileSession.syncError}"
                mobileSession.isSyncing -> "Sync is running. Keep this screen open while the first local copy is prepared."
                mobileSession.syncComplete ->
                    "Initial sync complete. ${mobileSession.syncedTransactionCount} transactions are available on this device."
                mobileSession.syncError != null -> mobileSession.syncError.orEmpty()
                else -> "Preparing your local budget copy."
            }
        val syncMessageColor by
            animateColorAsState(
                targetValue = if (mobileSession.syncError != null) palette.warning else palette.muted,
                animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
                label = "first-sync-status-color",
            )
        AnimatedStatusText(
            syncMessage,
            syncMessageColor,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            animateSizeChanges = true,
        )
        AnimatedReveal(visible = mobileSession.syncError != null && !mobileSession.isSyncing) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Retry initial sync", onClick = onRetrySync)
                if (mobileSession.cachedSnapshotBudgetId == state.selectedBudgetId && mobileSession.hasCachedSnapshot) {
                    BodyText("The last saved copy is still available on this phone.")
                    SecondaryButton("Open saved copy") {
                        selectLatestCachedMonth(state, mobileSession.budgetMonthRecords, mobileSession.transactionRecords)
                        navigation.selectTab(AppRoute.BUDGET)
                    }
                }
            }
        }
        PrimaryButton("Done", enabled = mobileSession.syncComplete) {
            selectLatestCachedMonth(state, mobileSession.budgetMonthRecords, mobileSession.transactionRecords)
            navigation.selectTab(AppRoute.BUDGET)
        }
    }
}

private fun selectLatestCachedMonth(
    state: AppState,
    budgetMonthRecords: List<BudgetRecordEntity>,
    transactionRecords: List<BudgetRecordEntity>,
) {
    val cachedMonthIds =
        budgetMonthRecords
            .mapNotNull { record ->
                val parts = record.recordId.split('-')
                if (parts.size != 2) return@mapNotNull null
                val year = parts[0].toIntOrNull() ?: return@mapNotNull null
                val month = parts[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return@mapNotNull null
                record.recordId
            }.distinct()
            .sorted()
    if (state.selectedMonthId in cachedMonthIds) return

    val latestActivityMonth =
        transactionRecords
            .mapNotNull { it.recordDate?.take(7)?.takeIf { monthId -> monthId in cachedMonthIds } }
            .maxOrNull()
    val selectedMonthId =
        latestActivityMonth
            ?: cachedMonthIds.filter { it <= state.selectedMonthId }.maxOrNull()
            ?: cachedMonthIds.firstOrNull()
            ?: return
    val (year, month) = selectedMonthId.split('-').map(String::toInt)
    state.selectedMonthId = selectedMonthId
    state.selectedMonth = formatMonth(year, month)
}

@Composable
private fun BudgetScreen(
    state: AppState,
    navigation: AppNavigation,
    mobileSession: TandemMobileSession,
    liveTransactions: List<TransactionItem>,
    liveBudgetMonth: LiveBudgetMonthSummary?,
) {
    val palette = tandemPalette
    val compact = state.homeLayout == "Compact"
    val essentialsFirst = state.homeLayout == "Essentials first"
    if (state.selectedBudgetId.isNotBlank()) {
        PageColumn(verticalSpacing = if (compact) 10.dp else 16.dp, horizontalPadding = if (compact) 16.dp else 20.dp) {
            val conflictCount = mobileSession.pendingWrites.count { it.status == "conflict" }
            AnimatedReveal(visible = state.conflictReviewAlerts && conflictCount > 0) {
                AttentionBanner(
                    "$conflictCount change${if (conflictCount == 1) "" else "s"} need${if (conflictCount == 1) "s" else ""} review",
                    "Choose which version of the budget change to keep.",
                    "Review changes",
                ) { navigation.open(AppRoute.CONFLICT_INBOX) }
            }
            val rejectedCount = mobileSession.pendingWrites.count { it.status == "rejected" }
            AnimatedReveal(visible = state.rejectedEditAlerts && rejectedCount > 0) {
                AttentionBanner(
                    "$rejectedCount edit${if (rejectedCount == 1) "" else "s"} not applied",
                    "Review the reason and dismiss or correct the local edit.",
                    "View rejected edits",
                ) { navigation.open(AppRoute.REJECTED_WRITE) }
            }
            Badge("Actual · read only", palette.positive)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Budget overview", color = palette.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Review the synced plan and recent activity for the selected month.", color = palette.muted, fontSize = 13.sp)
            }
            SurfaceCard(onClick = { navigation.open(AppRoute.MONTH_PICKER) }) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        LabelText("Planning period")
                        Spacer(Modifier.height(4.dp))
                        Text(state.selectedMonth, color = palette.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text("⌄", color = palette.accent, fontSize = 20.sp)
                }
            }
            if (liveBudgetMonth == null) {
                SurfaceCard {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("No plan for this month is cached", color = palette.text, fontWeight = FontWeight.SemiBold)
                        BodyText("Sync a month available in Actual, or choose a different planning period.")
                    }
                }
            } else {
                SurfaceCard {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LabelText("Ready to assign")
                        Text(
                            state.formatCurrency(liveBudgetMonth.toBudgetMinor ?: 0),
                            color = palette.text,
                            fontSize = 34.sp,
                            lineHeight = 39.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${liveBudgetMonth.groups.sumOf { it.categories.size }} categories · ${state.selectedMonth}",
                            color = palette.muted,
                            fontSize = 12.sp,
                        )
                    }
                }
                val displayGroups =
                    if (essentialsFirst) {
                        liveBudgetMonth.groups
                            .withIndex()
                            .sortedWith(
                                compareBy<IndexedValue<LiveCategoryGroupSummary>>(
                                    { essentialGroupOrder(it.value.name) },
                                    { it.index },
                                ),
                            ).map { it.value }
                    } else {
                        liveBudgetMonth.groups
                    }
                displayGroups.forEach { group ->
                    SectionCard(
                        group.name,
                        "${state.formatCurrency(group.assignedMinor)} assigned · ${group.categories.size} categories",
                        state.formatCurrency(group.balanceMinor),
                        onClick = {
                            state.selectedCategoryGroupId = group.id
                            navigation.open(AppRoute.CATEGORY_GROUP)
                        },
                    ) {
                        val displayCategories =
                            if (essentialsFirst) {
                                group.categories
                                    .withIndex()
                                    .sortedWith(
                                        compareBy<IndexedValue<LiveCategorySummary>>(
                                            { essentialCategoryOrder(it.value.name) },
                                            { it.index },
                                        ),
                                    ).map { it.value }
                            } else {
                                group.categories
                            }
                        displayCategories.forEachIndexed { index, category ->
                            if (index > 0) DividerLine()
                            ListRow(
                                category.name.take(1).uppercase(),
                                category.name,
                                "Spent ${state.formatCurrency(
                                    kotlin.math.abs(category.activityMinor),
                                )} · ${state.formatCurrency(category.balanceMinor)} left",
                                state.formatCurrency(category.assignedMinor),
                                if (category.balanceMinor >= 0) palette.positive else palette.warning,
                                dense = compact,
                                leadingVector = financeIcon(category.name),
                            ) {
                                state.selectedCategoryGroupId = group.id
                                state.selectedCategoryId = category.id
                                navigation.open(AppRoute.CATEGORY_DETAIL)
                            }
                        }
                    }
                }
            }
            if (liveTransactions.isNotEmpty()) {
                SectionCard("Recent activity", "${liveTransactions.size} synced transactions", "See all", onClick = {
                    navigation.selectTab(AppRoute.ACTIVITY)
                }) {
                    liveTransactions.take(3).forEachIndexed { index, item ->
                        if (index > 0) DividerLine()
                        TransactionRow(item) { openTransaction(state, navigation, item, isLive = true) }
                    }
                }
            }
        }
        return
    }
    PageColumn(verticalSpacing = if (compact) 10.dp else 16.dp, horizontalPadding = if (compact) 16.dp else 20.dp) {
        SampleBanner(
            if (liveTransactions.isNotEmpty()) {
                "Category plan is preview; recent activity is live"
            } else {
                "Budget categories are a sample preview"
            },
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Budget overview", color = palette.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Review category balances and spending for the selected month.", color = palette.muted, fontSize = 13.sp)
        }
        SurfaceCard(onClick = { navigation.open(AppRoute.MONTH_PICKER) }) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    LabelText("Planning period")
                    Spacer(Modifier.height(4.dp))
                    Text(state.selectedMonth, color = palette.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Text("⌄", color = palette.accent, fontSize = 20.sp)
            }
        }
        SurfaceCard {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LabelText("Ready to assign")
                Text(
                    state.formatCurrency(124_000),
                    color = palette.text,
                    fontSize = 34.sp,
                    lineHeight = 39.sp,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Available this month", color = palette.muted, fontSize = 12.sp)
                    Badge("On track", palette.positive)
                }
            }
        }
        if (essentialsFirst) UpcomingBillsCard(state, navigation)
        SectionCard("Essentials", "${state.formatCurrency(232_000)} assigned · 3 categories", state.formatCurrency(232_000), onClick = {
            navigation.open(AppRoute.CATEGORY_GROUP)
        }) {
            ListRow(
                "H",
                "Rent",
                "Due ${formatShortDate(SampleDate(2026, 5, 1))}",
                state.formatCurrency(150_000),
                palette.positive,
                dense = compact,
                leadingVector = financeIcon("Rent"),
            ) {
                navigation.open(AppRoute.CATEGORY_DETAIL)
            }
            DividerLine()
            ListRow(
                "G",
                "Groceries",
                "Spent ${state.formatCurrency(18_400)} this month",
                state.formatCurrency(50_000),
                palette.positive,
                dense = compact,
                leadingVector = financeIcon("Groceries"),
            ) {
                navigation.open(AppRoute.CATEGORY_DETAIL)
            }
            DividerLine()
            ListRow(
                "U",
                "Utilities",
                "Spent ${state.formatCurrency(11_200)} this month",
                state.formatCurrency(32_000),
                palette.positive,
                dense = compact,
                leadingVector = financeIcon("Utilities"),
            ) {
                navigation.open(AppRoute.CATEGORY_DETAIL)
            }
        }
        SectionCard(
            "Everyday",
            "Flexible spending",
            "${state.formatCurrency(18_000)} left",
            onClick = { navigation.open(AppRoute.CATEGORY_GROUP) },
        ) {
            ListRow(
                "D",
                "Dining",
                "Spent ${state.formatCurrency(17_000)} this month",
                state.formatCurrency(18_000),
                palette.positive,
                dense = compact,
            ) {
                navigation.open(AppRoute.CATEGORY_DETAIL)
            }
        }
        if (!essentialsFirst) UpcomingBillsCard(state, navigation)
        SectionCard("Recent activity", "Latest budget changes", "See all", onClick = { navigation.selectTab(AppRoute.ACTIVITY) }) {
            (liveTransactions.takeIf { it.isNotEmpty() } ?: previewTransactions).take(2).forEachIndexed { index, item ->
                if (index > 0) DividerLine()
                TransactionRow(item) { openTransaction(state, navigation, item, liveTransactions.isNotEmpty()) }
            }
        }
        SurfaceCard(onClick = { navigation.open(AppRoute.SYNC_CENTER) }) {
            Row(
                Modifier.fillMaxWidth().padding(15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Badge("✓", palette.positive)
                Column(modifier = Modifier.weight(1f)) {
                    Text("All changes accounted for", color = palette.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (liveTransactions.isNotEmpty()) {
                            "${liveTransactions.size} recent Actual transactions cached"
                        } else {
                            "Sample data · nothing has synced"
                        },
                        color = palette.muted,
                        fontSize = 11.sp,
                    )
                }
                Text("›", color = palette.muted, fontSize = 20.sp)
            }
        }
    }
}

private fun essentialGroupOrder(name: String): Int =
    if (listOf("essential", "housing", "fixed bill", "bill").any { it in name.lowercase() }) 0 else 1

private fun essentialCategoryOrder(name: String): Int =
    when {
        listOf("rent", "mortgage", "housing").any { it in name.lowercase() } -> 0
        listOf("utility", "electric", "water", "gas").any { it in name.lowercase() } -> 1
        listOf("insurance", "groceries", "food").any { it in name.lowercase() } -> 2
        else -> 3
    }

@Composable
private fun UpcomingBillsCard(
    state: AppState,
    navigation: AppNavigation,
) {
    val palette = tandemPalette
    SectionCard(
        "Upcoming",
        "Two bills before the end of the month",
        "View all",
        onClick = { navigation.open(AppRoute.CATEGORY_GROUP) },
    ) {
        ListRow(
            "↗",
            "Electric Company",
            "Utilities · due ${formatShortDate(SampleDate(2026, 5, 18))}",
            state.formatCurrency(12_000),
            palette.text,
            leadingVector = financeIcon("Utilities"),
        )
        DividerLine()
        ListRow(
            "H",
            "Rent",
            "Essentials · due ${formatShortDate(SampleDate(2026, 6, 1))}",
            state.formatCurrency(150_000),
            palette.text,
            leadingVector = financeIcon("Rent"),
        )
    }
}

@Composable
private fun TransactionRow(
    transaction: TransactionItem,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    Column {
        Text(
            transaction.dateLabel,
            modifier = Modifier.padding(start = 14.dp, top = 11.dp),
            color = palette.muted,
            fontSize = 10.sp,
        )
        ListRow(
            transaction.icon,
            transaction.payee,
            transaction.category,
            formatSignedCurrency(transaction.amountMinorUnits, transaction.displaySettings),
            if (transaction.amountMinorUnits > 0) palette.positive else palette.text,
            leadingVector = transaction.categoryIcon ?: Lucide.Receipt,
            leadingIconResource = transaction.merchantIcon,
            onClick = onClick,
        )
    }
}

private fun openTransaction(
    state: AppState,
    navigation: AppNavigation,
    transaction: TransactionItem,
    isLive: Boolean,
) {
    state.transactionPayee = transaction.payee
    state.transactionCategory = transaction.category
    state.transactionAccount = transaction.account
    state.transactionDate = transaction.dateLabel
    state.transactionNotes = transaction.notes
    state.transactionAmount = formatAmount(kotlin.math.abs(transaction.amountMinorUnits), state.budgetDisplaySettings)
    state.selectedTransactionAmountMinor = transaction.amountMinorUnits
    state.selectedTransactionRecordId = transaction.recordId.orEmpty()
    state.selectedTransactionIsLive = isLive
    navigation.open(AppRoute.TRANSACTION_DETAIL)
}

@Composable
private fun MonthPickerScreen(
    state: AppState,
    navigation: AppNavigation,
) {
    PageColumn {
        Heading("Select a month", "Choose which month to review.")
        var selectedYear by remember {
            mutableStateOf(state.selectedMonthId.substringBefore('-').toIntOrNull() ?: 2026)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                "‹",
                modifier =
                    Modifier
                        .semantics { contentDescription = "Previous year" }
                        .clickable(role = Role.Button, onClickLabel = "Previous year") {
                            selectedYear -= 1
                        }.padding(10.dp),
                color = tandemPalette.text,
                fontSize = 24.sp,
            )
            Text(formatInteger(selectedYear), color = tandemPalette.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(
                "›",
                modifier =
                    Modifier
                        .semantics { contentDescription = "Next year" }
                        .clickable(role = Role.Button, onClickLabel = "Next year") {
                            selectedYear += 1
                        }.padding(10.dp),
                color = tandemPalette.text,
                fontSize = 24.sp,
            )
        }
        val months = (1..12).map { month -> month to formatShortMonth(selectedYear, month) }
        months.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                row.forEach { (monthNumber, monthLabel) ->
                    val full = formatMonth(selectedYear, monthNumber)
                    val chosen = state.selectedMonth == full
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(72.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(if (chosen) tandemPalette.accentSurface else tandemPalette.surface)
                                .border(1.dp, if (chosen) tandemPalette.accent else tandemPalette.border, RoundedCornerShape(15.dp))
                                .semantics { contentDescription = full }
                                .selectable(
                                    selected = chosen,
                                    role = Role.RadioButton,
                                    onClick = {
                                        state.selectedMonth = full
                                        state.selectedMonthId = "$selectedYear-${monthNumber.toString().padStart(2, '0')}"
                                        navigation.back()
                                    },
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            monthLabel,
                            color = if (chosen) tandemPalette.accent else tandemPalette.text,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
        PreviewNote(
            if (state.selectedBudgetId.isNotBlank()) {
                "Only months cached from Actual show plan details."
            } else {
                "Months are sample planning periods."
            },
        )
    }
}

@Composable
private fun CategoryGroupScreen(
    state: AppState,
    navigation: AppNavigation,
    liveBudgetMonth: LiveBudgetMonthSummary?,
) {
    val palette = tandemPalette
    if (state.selectedBudgetId.isNotBlank()) {
        val group =
            liveBudgetMonth?.groups?.firstOrNull { it.id == state.selectedCategoryGroupId }
                ?: liveBudgetMonth?.groups?.firstOrNull()
        PageColumn {
            Badge("Actual · read only", palette.positive)
            if (group == null) {
                Heading("Category group", state.selectedMonth)
                BodyText("No synced category group is available for this month.")
            } else {
                Heading(group.name, state.selectedMonth)
                AmountHero(
                    "Assigned this month",
                    state.formatCurrency(group.assignedMinor),
                    "${state.formatCurrency(group.balanceMinor)} available across ${group.categories.size} categories",
                )
                SectionCard("Categories", "${group.categories.size} synced categories", "") {
                    group.categories.forEachIndexed { index, category ->
                        if (index > 0) DividerLine()
                        ListRow(
                            category.name.take(1).uppercase(),
                            category.name,
                            "Spent ${state.formatCurrency(
                                kotlin.math.abs(category.activityMinor),
                            )} · ${state.formatCurrency(category.balanceMinor)} available",
                            state.formatCurrency(category.assignedMinor),
                            if (category.balanceMinor >= 0) palette.positive else palette.warning,
                            leadingVector = financeIcon(category.name),
                        ) {
                            state.selectedCategoryGroupId = group.id
                            state.selectedCategoryId = category.id
                            navigation.open(AppRoute.CATEGORY_DETAIL)
                        }
                    }
                }
            }
        }
        return
    }
    PageColumn {
        SampleBanner()
        Heading("Group overview", "Review the bills and needs planned first.")
        AmountHero("Assigned this month", state.formatCurrency(232_000), "${state.formatCurrency(232_000)} available across 3 categories")
        SectionCard("Categories", "3 plans", "Edit group") {
            ListRow(
                "H",
                "Rent",
                "Assigned ${state.formatCurrency(150_000)} · Available ${state.formatCurrency(150_000)}",
                state.formatCurrency(150_000),
                palette.positive,
                leadingVector = financeIcon("Rent"),
            ) { navigation.open(AppRoute.CATEGORY_DETAIL) }
            DividerLine()
            ListRow(
                "G",
                "Groceries",
                "Assigned ${state.formatCurrency(50_000)} · Available ${state.formatCurrency(50_000)}",
                state.formatCurrency(50_000),
                palette.positive,
                leadingVector = financeIcon("Groceries"),
            ) { navigation.open(AppRoute.CATEGORY_DETAIL) }
            DividerLine()
            ListRow(
                "U",
                "Utilities",
                "Assigned ${state.formatCurrency(32_000)} · Available ${state.formatCurrency(32_000)}",
                state.formatCurrency(32_000),
                palette.positive,
                leadingVector = financeIcon("Utilities"),
            ) { navigation.open(AppRoute.CATEGORY_DETAIL) }
        }
        PreviewNote("Category balances and activity use sample data.")
    }
}

@Composable
private fun CategoryDetailScreen(
    state: AppState,
    navigation: AppNavigation,
    liveBudgetMonth: LiveBudgetMonthSummary?,
) {
    if (state.selectedBudgetId.isNotBlank()) {
        val category =
            liveBudgetMonth
                ?.groups
                ?.firstOrNull { it.id == state.selectedCategoryGroupId }
                ?.categories
                ?.firstOrNull { it.id == state.selectedCategoryId }
        PageColumn {
            Badge("Actual · read only", tandemPalette.positive)
            if (category == null) {
                Heading("Category details", state.selectedMonth)
                BodyText("This category is not included in the cached plan for this month.")
            } else {
                Heading(category.name, state.selectedMonth)
                AmountHero(
                    "Available",
                    state.formatCurrency(category.balanceMinor),
                    "${state.formatCurrency(category.assignedMinor)} assigned",
                )
                SectionCard("This month", "Actual plan and activity") {
                    ListRow("↗", "Assigned", "Budgeted in Actual", state.formatCurrency(category.assignedMinor))
                    DividerLine()
                    ListRow("◷", "Activity", "Transactions in this category", state.formatCurrency(kotlin.math.abs(category.activityMinor)))
                    DividerLine()
                    ListRow("=", "Available", "Assigned plus activity", state.formatCurrency(category.balanceMinor))
                }
                PrimaryButton("View all activity") { navigation.selectTab(AppRoute.ACTIVITY) }
            }
        }
        return
    }
    PageColumn {
        SampleBanner()
        Heading("Category details", "Essentials · monthly plan")
        AmountHero("Available", state.formatCurrency(150_000), "Fully funded for ${formatMonth(2026, 5)}")
        SectionCard("This month", "Assigned ${state.formatCurrency(150_000)} · Spent ${state.formatCurrency(0)}") {
            ListRow("↗", "Planned amount", "Monthly target", state.formatCurrency(150_000))
            DividerLine()
            ListRow("◷", "Next due date", "Scheduled payment", formatShortDate(SampleDate(2026, 6, 1)))
        }
        PrimaryButton("View related activity") { navigation.selectTab(AppRoute.ACTIVITY) }
        PreviewNote("This category uses sample data and is read-only. The app keeps a local budget copy for offline use.")
    }
}

@Composable
private fun AccountsScreen(
    state: AppState,
    navigation: AppNavigation,
    liveAccounts: List<LiveAccountSummary>,
    liveTransactions: List<TransactionItem>,
) {
    if (state.selectedBudgetId.isNotBlank()) {
        val onBudgetAccounts = liveAccounts.filterNot { it.offBudget }
        val totalOnBudget = onBudgetAccounts.sumOf { it.balanceMinor ?: 0L }
        val groupedAccounts =
            listOf("Cash", "Credit", "Tracking").mapNotNull { groupName ->
                val accounts =
                    liveAccounts.filter { account ->
                        when (groupName) {
                            "Tracking" -> account.offBudget
                            "Credit" -> !account.offBudget && account.type.orEmpty().contains("credit", ignoreCase = true)
                            else -> !account.offBudget && !account.type.orEmpty().contains("credit", ignoreCase = true)
                        }
                    }
                groupName.takeIf { accounts.isNotEmpty() }?.let { it to accounts }
            }
        PageColumn {
            Badge("Actual · read only", tandemPalette.positive)
            Heading("Balances", "Synced account balances from Actual.")
            AmountHero("Total on budget", state.formatCurrency(totalOnBudget), "Across ${onBudgetAccounts.size} on-budget accounts")
            if (liveAccounts.isEmpty()) {
                SurfaceCard {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("No accounts are cached", color = tandemPalette.text, fontWeight = FontWeight.SemiBold)
                        BodyText("Sync this budget to load its Actual account list and balances.")
                    }
                }
            } else {
                groupedAccounts.forEach { (groupName, accounts) ->
                    SectionCard(groupName, "${accounts.size} Actual accounts", "") {
                        accounts.forEachIndexed { index, account ->
                            if (index > 0) DividerLine()
                            ListRow(
                                account.name.take(1).uppercase(),
                                account.name,
                                if (account.offBudget) "Tracking · off budget" else account.type ?: "On budget",
                                account.balanceMinor?.let { state.formatCurrency(it) } ?: "Unavailable",
                                if (account.offBudget) tandemPalette.muted else tandemPalette.text,
                            ) {
                                state.selectedAccountId = account.id
                                navigation.open(AppRoute.ACCOUNT_DETAIL)
                            }
                        }
                    }
                }
            }
            Text("Balances are read-only in this build.", color = tandemPalette.muted, fontSize = 12.sp)
        }
        return
    }
    PageColumn {
        SampleBanner()
        Heading("Balances", "Review account balances and recent activity.")
        AmountHero(
            "Total on budget",
            state.formatCurrency(previewAccounts.filterNot { it.offBudget }.sumOf { it.balanceMinor }),
            "Across ${previewAccounts.count { !it.offBudget }} sample accounts",
        )
        previewAccounts.groupBy { it.group }.forEach { (groupName, accounts) ->
            SectionCard(groupName, "${accounts.size} ${if (accounts.size == 1) "account" else "accounts"}", "") {
                accounts.forEachIndexed { index, account ->
                    if (index > 0) DividerLine()
                    ListRow(
                        account.name.take(1),
                        account.name,
                        account.description,
                        state.formatCurrency(account.balanceMinor),
                        if (account.offBudget) tandemPalette.muted else tandemPalette.text,
                    ) {
                        state.selectedAccountId = account.id
                        navigation.open(AppRoute.ACCOUNT_DETAIL)
                    }
                }
            }
        }
        SecondaryButton("Account options") { navigation.open(AppRoute.ACCOUNT_OPTIONS) }
    }
}

@Composable
private fun AccountDetailScreen(
    state: AppState,
    navigation: AppNavigation,
    liveAccounts: List<LiveAccountSummary>,
    liveTransactions: List<TransactionItem>,
) {
    if (state.selectedBudgetId.isNotBlank()) {
        val account = liveAccounts.firstOrNull { it.id == state.selectedAccountId }
        val accountActivity = liveTransactions.filter { it.accountId == state.selectedAccountId }
        PageColumn {
            Badge("Actual · read only", tandemPalette.positive)
            if (account == null) {
                Heading("Account details", "Actual account not found in the local copy.")
            } else {
                AmountHero(
                    "Available balance",
                    account.balanceMinor?.let { state.formatCurrency(it) } ?: "Unavailable",
                    "${account.name} · ${if (account.offBudget) "off budget" else "on budget"}",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    if (account.offBudget) {
                        PrimaryButton("New transaction unavailable", enabled = false, modifier = Modifier.weight(1f)) {}
                    } else {
                        PrimaryButton("Add transaction", modifier = Modifier.weight(1f)) {
                            navigation.newTransaction(account.id)
                        }
                    }
                    SecondaryButton(
                        "Reconciliation unavailable",
                        modifier = Modifier.weight(1f),
                        enabled = false,
                    ) {}
                }
                if (accountActivity.isEmpty()) {
                    SurfaceCard {
                        Text("No recent activity for this account.", modifier = Modifier.padding(16.dp), color = tandemPalette.muted)
                    }
                } else {
                    SectionCard("Recent activity", account.name, "See all", onClick = { navigation.selectTab(AppRoute.ACTIVITY) }) {
                        accountActivity.take(6).forEachIndexed { index, item ->
                            if (index > 0) DividerLine()
                            TransactionRow(item) { openTransaction(state, navigation, item, isLive = true) }
                        }
                    }
                }
                SettingRow("•••", "Account options", "Read-only account details", onClick = { navigation.open(AppRoute.ACCOUNT_OPTIONS) })
            }
        }
        return
    }
    val account = previewAccountById(state.selectedAccountId)
    val accountActivity = previewTransactions.filter { it.account == account.name }
    PageColumn {
        SampleBanner()
        AmountHero(
            "Available balance",
            state.formatCurrency(account.balanceMinor),
            "${account.name} · ${if (account.offBudget) "off budget" else "Updated ${formatShortDate(SampleDate(2026, 5, 14))}"}",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            PrimaryButton(
                if (account.offBudget) "New transaction unavailable" else "Add transaction",
                enabled = !account.offBudget,
                modifier = Modifier.weight(1f),
            ) {
                if (!account.offBudget) navigation.open(AppRoute.NEW_TRANSACTION)
            }
            SecondaryButton("Reconcile", modifier = Modifier.weight(1f)) { navigation.open(AppRoute.RECONCILIATION) }
        }
        if (accountActivity.isEmpty()) {
            SurfaceCard {
                Text("No recent activity for this account.", modifier = Modifier.padding(16.dp), color = tandemPalette.muted)
            }
        } else {
            SectionCard(account.name, "Recent activity", "See all", onClick = { navigation.selectTab(AppRoute.ACTIVITY) }) {
                accountActivity.take(4).forEachIndexed { index, item ->
                    if (index > 0) DividerLine()
                    TransactionRow(item) { navigation.open(AppRoute.TRANSACTION_DETAIL) }
                }
            }
        }
        SettingRow("•••", "Account options", "${account.name} settings", onClick = { navigation.open(AppRoute.ACCOUNT_OPTIONS) })
    }
}

@Composable
private fun AccountOptionsScreen(
    state: AppState,
    liveAccounts: List<LiveAccountSummary>,
) {
    if (state.selectedBudgetId.isNotBlank()) {
        val account = liveAccounts.firstOrNull { it.id == state.selectedAccountId }
        PageColumn {
            Heading(account?.name ?: "Account options", "Actual account details")
            if (account == null) {
                BodyText("This account is not available in the local copy.")
            } else {
                SectionCard("Account", "Read-only Actual data") {
                    ListRow("A", "Name", "Account name", account.name)
                    DividerLine()
                    ListRow("¤", "Balance", "Current Actual balance", account.balanceMinor?.let(::formatCurrency) ?: "Unavailable")
                    DividerLine()
                    ListRow("◷", "Budget status", "Account scope", if (account.offBudget) "Off budget" else "On budget")
                }
                PreviewNote("Renaming and hiding accounts are not available here. No Actual account settings are changed.")
            }
        }
        return
    }
    val account = previewAccountById(state.selectedAccountId)
    PageColumn {
        Heading(account.name, "Account options")
        ListRow("✎", "Edit account details", "Name and notes", "Unavailable")
        ListRow("◷", "Reconcile balance", "Compare the account against a statement", "Unavailable")
        ListRow("⊖", "Hide from budget", "Keep historical activity available", "Unavailable")
        PreviewNote(
            "Account changes and statement reconciliation are not available in this sample view.",
        )
    }
}

@Composable
private fun ReconciliationScreen(state: AppState) {
    PageColumn {
        Heading("Compare balances", "Compare cleared transactions with a statement.")
        TextFieldCard("Statement balance", state.formatAmount(348_216), {}, state.formatAmount(0))
        SectionCard("Difference", "Sample calculation") {
            ListRow("=", "Account balance", "Current sample balance", state.formatCurrency(348_216))
            DividerLine()
            ListRow("Δ", "Out of balance", "No cleared transactions compared", state.formatCurrency(0), tandemPalette.positive)
        }
        PreviewNote(
            "Reconciliation is display-only. Do not treat the sample balance as verified, and no statement or transaction will be changed.",
        )
        PrimaryButton("Reconcile account", enabled = false) {}
    }
}

@Composable
private fun ActivityScreen(
    state: AppState,
    navigation: AppNavigation,
    liveTransactions: List<TransactionItem>,
) {
    val isLiveBudget = state.selectedBudgetId.isNotBlank()
    PageColumn {
        if (!isLiveBudget) SampleBanner()
        Heading(
            "Recent transactions",
            if (isLiveBudget) "Recent Actual transactions saved on this device." else "Search activity and filter the sample transactions.",
        )
        SurfaceCard(onClick = { navigation.open(AppRoute.ACTIVITY_SEARCH) }) {
            Row(
                Modifier.fillMaxWidth().padding(13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("⌕", color = tandemPalette.accent, fontSize = 20.sp)
                Text("Search transactions", color = tandemPalette.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("⌘ K", color = tandemPalette.muted, fontSize = 10.sp)
            }
        }
        if (!isLiveBudget) {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Income", "Spending", "Transfers").forEachIndexed { index, filter ->
                    Badge(filter, if (index == 0) tandemPalette.accent else tandemPalette.muted)
                }
                Badge("Filter · 2", tandemPalette.accent)
            }
        }
        if (isLiveBudget) {
            SectionCard("All transactions", "${liveTransactions.size} synced transactions · newest first") {
                if (liveTransactions.isEmpty()) {
                    BodyText("No transactions are available in the local copy yet. Sync the budget to refresh it.")
                } else {
                    liveTransactions.forEachIndexed { index, item ->
                        if (index > 0) DividerLine()
                        TransactionRow(item) { openTransaction(state, navigation, item, isLive = true) }
                    }
                }
            }
        } else {
            SectionCard(state.selectedMonth, "6 sample transactions", "Filter", onClick = { navigation.open(AppRoute.ACTIVITY_FILTER) }) {
                previewTransactions.forEachIndexed { index, item ->
                    if (index > 0) DividerLine()
                    TransactionRow(item) { openTransaction(state, navigation, item, isLive = false) }
                }
            }
        }
        if (isLiveBudget) {
            BodyText("Tap a transaction to view or edit it. New entries are saved locally before syncing.")
            PrimaryButton("Add transaction") { navigation.newTransaction(null) }
        } else {
            PreviewNote("Transactions use sample data. Search and filters do not change a live budget.")
            PrimaryButton("Add transaction") { navigation.open(AppRoute.NEW_TRANSACTION) }
        }
    }
}

@Composable
private fun ActivitySearchScreen(
    state: AppState,
    navigation: AppNavigation,
    liveTransactions: List<TransactionItem>,
) {
    var search by remember { mutableStateOf("") }
    val isLiveBudget = state.selectedBudgetId.isNotBlank()
    val searchTransactions = if (isLiveBudget) liveTransactions else previewTransactions
    val results =
        searchTransactions.filter { transaction ->
            search.isBlank() ||
                listOf(
                    transaction.payee,
                    transaction.category,
                    transaction.account,
                    transaction.dateLabel,
                    state.formatCurrency(kotlin.math.abs(transaction.amountMinorUnits)),
                    state.formatSignedCurrency(transaction.amountMinorUnits),
                ).any { it.contains(search.trim(), ignoreCase = true) }
        }
    PageColumn {
        Heading(
            "Find transactions",
            if (isLiveBudget) {
                "Search synced transactions by payee, account, category, date, or amount."
            } else {
                "Search the sample activity by payee, amount, or category."
            },
        )
        TextFieldCard("Search", search, { search = it }, "Try “groceries” or “${state.formatCurrency(4_200)}”")
        if (isLiveBudget) {
            SectionCard("Matches", "${results.size} transactions in this device’s local copy") {
                if (results.isEmpty()) {
                    BodyText("No matching transactions were found in the local copy.")
                } else {
                    results.forEachIndexed { index, transaction ->
                        if (index > 0) DividerLine()
                        TransactionRow(transaction) { openTransaction(state, navigation, transaction, isLive = true) }
                    }
                }
            }
        } else {
            SectionCard("Suggested", "Recent sample searches", "") {
                listOf("Groceries", "Electric Company", "Paycheck").forEachIndexed { index, value ->
                    if (index > 0) DividerLine()
                    ListRow("⌕", value, "Sample results", onClick = { navigation.open(AppRoute.TRANSACTION_DETAIL) })
                }
            }
            SectionCard("Results", "${results.size} matching sample transactions", "") {
                results.forEachIndexed { index, item ->
                    if (index > 0) DividerLine()
                    TransactionRow(item) { openTransaction(state, navigation, item, isLive = false) }
                }
            }
        }
    }
}

@Composable
private fun ActivityFilterScreen() {
    PageColumn {
        Heading("Filter results", "Narrow the activity list by type, date, or amount.")
        SectionCard("Transaction type", "Choose one or more") {
            listOf("Income", "Spending", "Transfers").forEachIndexed { index, item -> ToggleRow(item, checked = index != 1, onChange = {}) }
        }
        SectionCard(
            "Date range",
            "${formatDate(SampleDate(2026, 5, 1))} – ${formatDate(SampleDate(2026, 5, 31))}",
        ) {
            ListRow("▣", "Start date", formatDate(SampleDate(2026, 5, 1)), onClick = {})
            DividerLine()
            ListRow("▣", "End date", formatDate(SampleDate(2026, 5, 31)), onClick = {})
        }
        SectionCard("Sort order", "Most recent first") {
            ChoiceRow("Newest first", selected = true, onClick = {})
            ChoiceRow("Oldest first", selected = false, onClick = {})
        }
        PreviewNote("Filters are not saved or applied to a live activity list.")
    }
}

@Composable
private fun TransactionDetailScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    merchantIconPack: MerchantIconPack?,
) {
    val isLive = state.selectedTransactionIsLive
    val amount = state.selectedTransactionAmountMinor ?: -4_218L
    val merchantIcon = remember(merchantIconPack, state.transactionPayee) { merchantIconPack?.iconFor(state.transactionPayee) }
    val categoryIcon = remember(state.transactionCategory) { financeIcon(state.transactionCategory) ?: Lucide.Receipt }
    val hasUnsentTransactionChange =
        mobileSession.pendingWrites.any { operation ->
            operation.recordType == "transaction" &&
                operation.recordId == state.selectedTransactionRecordId &&
                operation.status in UNSYNCED_TRANSACTION_WRITE_STATUSES
        }
    val isSyncedToActual = isLive && state.selectedTransactionRecordId.isNotBlank() && !hasUnsentTransactionChange
    val syncDescription =
        when {
            isSyncedToActual -> "Synced to Actual"
            isLive -> "Changes not synced to Actual"
            else -> "Sample transaction, not synced to Actual"
        }
    PageColumn(verticalSpacing = 12.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(tandemPalette.raised),
                    contentAlignment = Alignment.Center,
                ) {
                    if (merchantIcon != null) {
                        Image(
                            painter = painterResource(merchantIcon),
                            contentDescription = null,
                            modifier = Modifier.size(38.dp).padding(3.dp),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Icon(
                            imageVector = categoryIcon,
                            contentDescription = null,
                            modifier = Modifier.size(23.dp),
                            tint = tandemPalette.accent,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.size(28.dp))
                Text(
                    state.formatCurrency(amount),
                    modifier = Modifier.weight(1f),
                    color = tandemPalette.text,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = if (isSyncedToActual) Lucide.CloudCheck else Lucide.CloudOff,
                    contentDescription = syncDescription,
                    modifier = Modifier.size(28.dp),
                    tint = if (isSyncedToActual) tandemPalette.positive else tandemPalette.muted,
                )
            }
        }
        SectionCard(title = "") {
            ListRow("", "Account", trailing = state.transactionAccount, leadingVector = Lucide.WalletCards)
            DividerLine()
            ListRow("", "Category", trailing = state.transactionCategory, leadingVector = Lucide.Tags)
            DividerLine()
            ListRow("", "Date", trailing = state.transactionDate, leadingVector = Lucide.CalendarDays)
            DividerLine()
            ListRow("", "Notes", trailing = state.transactionNotes.ifBlank { "No notes" }, leadingVector = Lucide.NotepadText)
        }
        if (isLive) {
            PrimaryButton("Edit transaction", enabled = state.selectedTransactionRecordId.isNotBlank()) {
                navigation.open(AppRoute.EDIT_TRANSACTION)
            }
        } else {
            PreviewNote("This sample transaction is read-only. Editing it will not change Actual Budget.")
            PrimaryButton("Edit transaction") { navigation.open(AppRoute.EDIT_TRANSACTION) }
        }
    }
}

@Composable
private fun TransactionFormScreen(
    route: AppRoute,
    state: AppState,
    navigation: AppNavigation,
    mobileSession: TandemMobileSession,
    onSaveTransaction: () -> Unit,
    onCreateTransaction: () -> Unit,
) {
    val isNew = route == AppRoute.NEW_TRANSACTION
    if (state.selectedBudgetId.isNotBlank()) {
        if (isNew) {
            PageColumn {
                Badge("Actual · saved locally first", tandemPalette.positive)
                Heading("Add transaction", "New entries sync safely with the current Actual budget.")
                TextFieldCard(
                    "Amount",
                    state.transactionAmount,
                    { state.transactionAmount = it },
                    "For example, -42.18 for spending or 2500 for income",
                )
                if (state.newTransactionAccountId.isBlank()) {
                    BodyText("Choose an on-budget account to save this transaction.")
                }
                ReadOnlyField("Account", state.transactionAccount, "A") { navigation.open(AppRoute.CHOOSE_ACCOUNT) }
                ReadOnlyField("Payee", state.transactionPayee.ifBlank { "No payee" }, "P") { navigation.open(AppRoute.CHOOSE_PAYEE) }
                ReadOnlyField("Category", state.transactionCategory.ifBlank { "Uncategorized" }, "C") {
                    navigation.open(AppRoute.CHOOSE_CATEGORY)
                }
                TextFieldCard("Date · YYYY-MM-DD", state.transactionDate, { state.transactionDate = it }, "${platformTodayIsoDate()}")
                TextFieldCard("Notes", state.transactionNotes, { state.transactionNotes = it }, "Add a note (optional)", singleLine = false)
                AnimatedReveal(visible = mobileSession.transactionWriteMessage != null) {
                    SurfaceCard {
                        Column(Modifier.padding(14.dp)) { BodyText(mobileSession.transactionWriteMessage.orEmpty()) }
                    }
                }
                PrimaryButton(
                    "Save transaction",
                    enabled =
                        !mobileSession.isSavingTransaction &&
                            state.newTransactionAccountId.isNotBlank() &&
                            state.transactionAmount.isNotBlank(),
                ) { onCreateTransaction() }
                SecondaryButton("Cancel") { navigation.back() }
            }
            return
        }
        PageColumn {
            Heading("Edit transaction", "Changes stay local until the sync confirms them.")
            TextFieldCard("Amount", state.transactionAmount, { state.transactionAmount = it }, state.formatAmount(4_218))
            TextFieldCard("Notes", state.transactionNotes, { state.transactionNotes = it }, "Add a note (optional)", singleLine = false)
            SectionCard("Transaction details", "Other fields are read-only in this build") {
                ListRow("A", "Account", "Account is unchanged", state.transactionAccount)
                DividerLine()
                ListRow("P", "Payee", "Payee is unchanged", state.transactionPayee)
                DividerLine()
                ListRow("C", "Category", "Category is unchanged", state.transactionCategory)
                DividerLine()
                ListRow("◷", "Date", "Date is unchanged", state.transactionDate)
            }
            mobileSession.transactionWriteMessage?.let { message ->
                SurfaceCard {
                    Column(Modifier.padding(14.dp)) { BodyText(message) }
                }
            }
            PrimaryButton("Save changes", enabled = !mobileSession.isSavingTransaction) { onSaveTransaction() }
            SecondaryButton("Cancel") { navigation.back() }
        }
        return
    }
    PageColumn {
        SampleBanner()
        Heading("Transaction details", "Record a change to the budget.")
        TextFieldCard("Amount", state.transactionAmount, { state.transactionAmount = it }, "For example, ${state.formatAmount(1_250)}")
        ReadOnlyField("Account", state.transactionAccount, "A") { navigation.open(AppRoute.CHOOSE_ACCOUNT) }
        ReadOnlyField("Payee", state.transactionPayee, "P") { navigation.open(AppRoute.CHOOSE_PAYEE) }
        ReadOnlyField("Category", state.transactionCategory, "C") { navigation.open(AppRoute.CHOOSE_CATEGORY) }
        ReadOnlyField("Date", state.transactionDate, "▣") { navigation.open(AppRoute.CHOOSE_DATE) }
        TextFieldCard("Notes", state.transactionNotes, { state.transactionNotes = it }, "Add a note (optional)", singleLine = false)
        PreviewNote(
            if (isNew) {
                "New transactions are not available here yet. This sample form does not save or sync changes."
            } else {
                "This sample form is not connected. Changes will not be saved or synced."
            },
        )
        PrimaryButton("Review save status") { navigation.open(AppRoute.SAVE_RESULT) }
    }
}

@Composable
private fun SelectorScreen(
    values: List<SelectorItem>,
    selected: String,
    live: Boolean = false,
    onSelect: (SelectorItem) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    PageColumn {
        BodyText(if (live) "Choose an item from the open Actual budget." else "Choose an item for the sample transaction.")
        TextFieldCard("Search", query, { query = it }, "Start typing")
        val filtered = values.filter { it.label.contains(query, ignoreCase = true) }
        if (filtered.isEmpty()) {
            SurfaceCard {
                Column(Modifier.padding(14.dp)) {
                    BodyText(if (live) "No matching items are available in this budget." else "No sample items match your search.")
                }
            }
        }
        filtered.forEach { item ->
            ChoiceRow(
                item.label,
                selected = selected == if (live) item.id else item.label,
                onClick = { onSelect(item) },
            )
        }
        if (!live) PreviewNote("Selections update only this sample form.")
    }
}

@Composable
private fun DatePickerScreen(
    state: AppState,
    navigation: AppNavigation,
) {
    val year = 2026
    val month = 5
    val firstWeekday = platformFirstWeekday()
    val firstDayOffset = (platformWeekday(year, month, 1) - firstWeekday + 7) % 7
    val dayCells = List<Int?>(firstDayOffset) { null } + (1..31).map { it }
    PageColumn {
        Heading("Select a day", formatMonth(year, month))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            (0..6).forEach { column ->
                val representativeDay = 1 + ((column - firstDayOffset + 7) % 7)
                Text(
                    formatWeekday(year, month, representativeDay),
                    modifier = Modifier.weight(1f),
                    color = tandemPalette.muted,
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp,
                )
            }
        }
        dayCells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { day ->
                    if (day == null) {
                        Spacer(Modifier.weight(1f).height(44.dp))
                    } else {
                        val label = formatDate(SampleDate(year, month, day))
                        val selected = state.transactionDate == label
                        val cellBackground by
                            animateColorAsState(
                                targetValue = if (selected) tandemPalette.accentSurface else Color.Transparent,
                                animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
                                label = "date-selection-background",
                            )
                        val cellTextColor by
                            animateColorAsState(
                                targetValue = if (selected) tandemPalette.accent else tandemPalette.text,
                                animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
                                label = "date-selection-text",
                            )
                        Box(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(CircleShape)
                                    .background(cellBackground)
                                    .semantics { contentDescription = label }
                                    .selectable(
                                        selected = selected,
                                        role = Role.RadioButton,
                                        onClick = {
                                            state.transactionDate = label
                                            navigation.back()
                                        },
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                formatInteger(day),
                                color = cellTextColor,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f).height(44.dp)) }
            }
        }
        PreviewNote("The date selector uses sample local dates.")
    }
}

@Composable
private fun SaveResultScreen(
    state: AppState,
    navigation: AppNavigation,
) {
    val palette = tandemPalette
    PageColumn {
        Spacer(Modifier.height(16.dp))
        Badge("Not saved", palette.warning)
        Heading("No change was made", "This sample form cannot save a transaction yet.")
        SectionCard("Transaction draft", "Sample data") {
            ListRow("¤", "Amount", "Corner Market", state.formatCurrency(-4_218))
            DividerLine()
            ListRow("C", "Account and category", "Checking · Groceries")
        }
        PreviewNote(
            "Actual Budget is unchanged. New transactions are not available here yet, and this screen does not send changes.",
        )
        PrimaryButton("Return to activity") { navigation.selectTab(AppRoute.ACTIVITY) }
        SecondaryButton("Open sync center") { navigation.open(AppRoute.SYNC_CENTER) }
    }
}

@Composable
private fun SyncCenterScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    onSyncNow: () -> Unit,
) {
    if (state.selectedBudgetId.isNotBlank()) {
        val attention = mobileSession.pendingWrites.filter { it.status in setOf("queued", "conflict", "uncertain", "rejected") }
        PageColumn {
            Badge(if (mobileSession.isServerReachable) "Actual · connected" else "Offline copy", tandemPalette.positive)
            Heading("Sync center", "Review the local budget copy and any changes that need attention.")
            SurfaceCard {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabelText(state.selectedBudget)
                    BodyText("${mobileSession.cachedTransactionCount} transactions · ${mobileSession.accountRecords.size} accounts cached")
                    val syncMessage =
                        when {
                            mobileSession.isSyncing && mobileSession.syncError != null ->
                                "Retrying sync · ${mobileSession.syncError}"
                            mobileSession.isSyncing -> "Sync in progress"
                            mobileSession.syncError != null -> mobileSession.syncError.orEmpty()
                            else -> "Local data is available offline"
                        }
                    val syncMessageColor by
                        animateColorAsState(
                            targetValue = if (mobileSession.syncError != null) tandemPalette.warning else tandemPalette.muted,
                            animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
                            label = "sync-center-status-color",
                        )
                    AnimatedStatusText(
                        syncMessage,
                        syncMessageColor,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            SectionCard("Changes needing attention", "${attention.size} local operations", animateDetail = true) {
                AnimatedAttentionContent(showingAttention = attention.isNotEmpty()) { showingAttention ->
                    if (showingAttention) {
                        Column {
                            attention.forEachIndexed { index, operation ->
                                if (index > 0) DividerLine()
                                val transaction =
                                    liveTransactionItems(mobileSession).firstOrNull { it.recordId == operation.recordId }
                                ListRow(
                                    when (operation.status) {
                                        "conflict" -> "!"
                                        "uncertain" -> "?"
                                        "rejected" -> "×"
                                        else -> "↻"
                                    },
                                    transaction?.payee ?: "Transaction ${operation.recordId.take(8)}",
                                    "${operation.status.replaceFirstChar {
                                        it.uppercase()
                                    }} · ${transaction?.category ?: "Transaction edit"}",
                                    "Review",
                                    onClick = {
                                        state.selectedConflictOperationId = operation.operationId
                                        navigation.open(
                                            when (operation.status) {
                                                "conflict" -> AppRoute.CONFLICT_REVIEW
                                                "uncertain" -> AppRoute.UNCERTAIN_WRITE
                                                "rejected" -> AppRoute.REJECTED_WRITE
                                                else -> AppRoute.UNCERTAIN_WRITE
                                            },
                                        )
                                    },
                                )
                            }
                        }
                    } else {
                        Text(
                            "No pending, uncertain, rejected, or conflicted changes.",
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                            color = tandemPalette.muted,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                        )
                    }
                }
            }
            PrimaryButton(
                if (mobileSession.isSyncing) "Syncing…" else "Sync now",
                enabled = mobileSession.isAuthenticated && !mobileSession.isSyncing,
                loading = mobileSession.isSyncing,
            ) { onSyncNow() }
            AnimatedReveal(visible = mobileSession.transactionWriteMessage != null) {
                BodyText(mobileSession.transactionWriteMessage.orEmpty())
            }
        }
        return
    }
    PageColumn {
        Heading("Connection status", "Review device data, pending edits, and server activity.")
        SurfaceCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Badge("Sample data")
                    Text("No server connection", color = tandemPalette.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Text("Last sync · sample only", color = tandemPalette.muted, fontSize = 12.sp)
                PreviewNote("No data has been uploaded or downloaded.")
            }
        }
        SectionCard("Attention needed", "Sample recovery states") {
            ListRow("!", "Conflict to review", "Corner Market · two versions", "1", tandemPalette.warning) {
                navigation.open(AppRoute.CONFLICT_INBOX)
            }
            DividerLine()
            ListRow(
                "?",
                "Status unknown",
                "Check before retrying",
                "1",
                tandemPalette.warning,
            ) { navigation.open(AppRoute.UNCERTAIN_WRITE) }
            DividerLine()
            ListRow(
                "×",
                "Not applied",
                "New transactions are not available yet",
                "1",
                tandemPalette.negative,
            ) { navigation.open(AppRoute.REJECTED_WRITE) }
        }
        SectionCard("Recent sync activity", "Sample events", "") {
            ListRow("↓", "Budget snapshot", "Ready for initial download", "Pending")
            DividerLine()
            ListRow("↑", "Local edits", "None in sample data", "0")
        }
        SecondaryButton("Review all conflicts") { navigation.open(AppRoute.CONFLICT_INBOX) }
    }
}

@Composable
private fun ConflictInboxScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
) {
    if (state.selectedBudgetId.isNotBlank()) {
        val conflicts = mobileSession.pendingWrites.filter { it.status == "conflict" }
        PageColumn {
            Badge("Actual · review required", tandemPalette.warning)
            Heading("Changes to review", "Choose whether to keep the device edit or the latest Actual version.")
            AnimatedAttentionContent(showingAttention = conflicts.isNotEmpty(), animateSizeChanges = true) { showingConflicts ->
                if (showingConflicts) {
                    SectionCard("Conflicts", "${conflicts.size} transaction changes") {
                        conflicts.forEachIndexed { index, operation ->
                            if (index > 0) DividerLine()
                            val transaction = liveTransactionItems(mobileSession).firstOrNull { it.recordId == operation.recordId }
                            ListRow(
                                "!",
                                transaction?.payee ?: "Transaction ${operation.recordId.take(8)}",
                                "${transaction?.dateLabel ?: "Actual changed"} · ${transaction?.category ?: "Transaction"}",
                                "Review",
                                tandemPalette.warning,
                            ) {
                                state.selectedConflictOperationId = operation.operationId
                                navigation.open(AppRoute.CONFLICT_REVIEW)
                            }
                        }
                    }
                } else {
                    SurfaceCard {
                        Column(Modifier.padding(15.dp)) { BodyText("There are no conflicts in the current local copy.") }
                    }
                }
            }
            mobileSession.transactionWriteMessage?.let { BodyText(it) }
        }
        return
    }
    PageColumn {
        SampleBanner()
        Heading("Changes to review", "Choose which version to keep before a change is sent.")
        SurfaceCard(onClick = { navigation.open(AppRoute.CONFLICT_REVIEW) }) {
            Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Badge("1 conflict", tandemPalette.warning)
                    Text("Corner Market", color = tandemPalette.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Text("${formatDate(SampleDate(2026, 5, 14))} · Groceries · Checking", color = tandemPalette.muted, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ConflictValueCard("On this device", state.formatCurrency(1_200), Modifier.weight(1f))
                    ConflictValueCard("In Actual", state.formatCurrency(1_500), Modifier.weight(1f))
                }
                Text("Review both versions ›", color = tandemPalette.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        PreviewNote("This conflict is sample data. No resolution is sent or applied.")
    }
}

@Composable
private fun ConflictValueCard(
    label: String,
    amount: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(13.dp)).background(tandemPalette.raised).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(label, color = tandemPalette.muted, fontSize = 10.sp)
        Text(amount, color = tandemPalette.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ConflictReviewScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    onResolveConflict: (String, Boolean) -> Unit,
) {
    if (state.selectedBudgetId.isNotBlank()) {
        val operation =
            mobileSession.pendingWrites.firstOrNull {
                it.operationId == state.selectedConflictOperationId &&
                    it.status == "conflict"
            }
        val record = operation?.let { item -> mobileSession.transactionRecords.firstOrNull { it.recordId == item.recordId } }
        val deviceVersion = operation?.optimisticJson?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
        val actualVersion = record?.remoteJson?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
        val deviceAmount = deviceVersion?.longValue("amount")
        val actualAmount = actualVersion?.longValue("amount")
        PageColumn {
            Badge("Actual · conflict", tandemPalette.warning)
            if (operation == null) {
                Heading("Conflict is no longer available", "Refresh the sync center to see the current state.")
            } else {
                val transaction = liveTransactionItems(mobileSession).firstOrNull { it.recordId == operation.recordId }
                Heading("Two versions differ", transaction?.payee ?: "Review this transaction before continuing.")
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    ConflictValueCard(
                        "On this device",
                        deviceAmount?.let(::formatSignedCurrency) ?: "Deleted",
                        Modifier.weight(1f),
                    )
                    ConflictValueCard(
                        "In Actual",
                        actualAmount?.let(::formatSignedCurrency) ?: "Deleted",
                        Modifier.weight(1f),
                    )
                }
                SectionCard("Details", "Latest cached transaction") {
                    ListRow("A", "Account", "Actual account", transaction?.account ?: "Unavailable")
                    DividerLine()
                    ListRow("C", "Category", "Actual category", transaction?.category ?: "Unavailable")
                    DividerLine()
                    ListRow("◷", "Date", "Actual date", transaction?.dateLabel ?: "Unavailable")
                }
                if (record?.remoteDeleted == true) {
                    PreviewNote(
                        "Actual deleted this transaction. The device version cannot be rebased; keep Actual to discard the local draft.",
                    )
                } else {
                    PreviewNote(
                        "Keeping the device version rebases the edit on the latest Actual revision, then submits it through the normal sync queue.",
                    )
                }
                mobileSession.transactionWriteMessage?.let { BodyText(it) }
                PrimaryButton(
                    "Keep device version",
                    enabled = !mobileSession.isSavingTransaction && record?.remoteDeleted != true,
                ) { onResolveConflict(operation.operationId, true) }
                SecondaryButton("Keep latest Actual version", enabled = !mobileSession.isSavingTransaction) {
                    onResolveConflict(operation.operationId, false)
                }
            }
        }
        return
    }
    PageColumn {
        Badge("Conflict · sample data", tandemPalette.warning)
        Heading("Two versions differ", "Review the two sample amounts for Corner Market.")
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            ConflictValueCard("On this device", state.formatCurrency(1_200), Modifier.weight(1f))
            ConflictValueCard("In Actual", state.formatCurrency(1_500), Modifier.weight(1f))
        }
        SectionCard("Other details", "Review before choosing") {
            ListRow("C", "Account", "Same on both versions", "Checking")
            DividerLine()
            ListRow("G", "Category", "Same on both versions", "Groceries")
            DividerLine()
            ListRow("◷", "Date", "Same on both versions", formatShortDate(SampleDate(2026, 5, 14)))
        }
        if (state.conflictChoice.isNotEmpty()) {
            PreviewNote("Sample choice selected: ${state.conflictChoice}. No server update has been sent.")
        }
        PrimaryButton("Keep ${state.formatCurrency(1_200)} on this device") {
            state.conflictChoice =
                "device value ${state.formatCurrency(1_200)}"
        }
        SecondaryButton("Keep ${state.formatCurrency(1_500)} from Actual") {
            state.conflictChoice =
                "Actual value ${state.formatCurrency(1_500)}"
        }
        PreviewNote(
            "The app would compare that choice with the latest server copy before saving. Nothing changes until the server confirms it.",
        )
    }
}

@Composable
private fun UncertainWriteScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    onSyncNow: () -> Unit,
    onRetry: (String) -> Unit,
    onDiscard: (String) -> Unit,
) {
    if (state.selectedBudgetId.isNotBlank()) {
        val operation =
            mobileSession.pendingWrites.firstOrNull {
                it.operationId == state.selectedConflictOperationId && it.status == "uncertain"
            } ?: mobileSession.pendingWrites.firstOrNull { it.status == "uncertain" }
        PageColumn {
            Badge("Status unknown", tandemPalette.warning)
            Heading("Check before retrying", "The server has not confirmed whether this edit was applied.")
            if (operation == null) {
                BodyText("There is no uncertain operation in the current local copy.")
            } else {
                val transaction = liveTransactionItems(mobileSession).firstOrNull { it.recordId == operation.recordId }
                SectionCard(transaction?.payee ?: "Transaction", "${transaction?.dateLabel ?: "Local edit"} · ${operation.status}") {
                    ListRow(
                        "¤",
                        "Requested amount",
                        "Device edit",
                        operation.optimisticAmountMinor?.let { state.formatSignedCurrency(it) } ?: "Unavailable",
                    )
                    DividerLine()
                    ListRow(
                        "?",
                        "Server result",
                        "Must be checked before retry",
                        operation.lastErrorCode ?: "Unknown",
                        tandemPalette.warning,
                    )
                }
                PreviewNote(
                    "Checking status asks the server about the same operation without sending it again. Retry is a separate explicit action.",
                )
                PrimaryButton("Check status", enabled = !mobileSession.isSyncing) { onSyncNow() }
                SecondaryButton("Retry the same operation", enabled = !mobileSession.isSyncing) { onRetry(operation.operationId) }
                SecondaryButton("Discard local edit") { onDiscard(operation.operationId) }
            }
        }
        return
    }
    PageColumn {
        Badge("Status unknown", tandemPalette.warning)
        Heading("Check before retrying", "The connection ended before this change could be confirmed.")
        SectionCard("Corner Market", "${formatShortDate(SampleDate(2026, 5, 14))} · Groceries") {
            ListRow("¤", "Requested amount", "Local change", state.formatCurrency(1_200))
            DividerLine()
            ListRow("?", "Server result", "The change has not been confirmed", "Unknown", tandemPalette.warning)
        }
        PreviewNote(
            "Do not send the change again yet. Check its status first. Retry or discard options appear after the server confirms what happened.",
        )
        PrimaryButton("Check status", enabled = false) {}
    }
}

@Composable
private fun RejectedWriteScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    onDiscard: (String) -> Unit,
) {
    if (state.selectedBudgetId.isNotBlank()) {
        val operation =
            mobileSession.pendingWrites.firstOrNull {
                it.operationId == state.selectedConflictOperationId && it.status == "rejected"
            } ?: mobileSession.pendingWrites.firstOrNull { it.status == "rejected" }
        PageColumn {
            Badge("Not applied", tandemPalette.negative)
            Heading("Previous value kept", "Actual rejected this change. The remote value remains in the budget.")
            if (operation == null) {
                BodyText("There are no rejected edits in the current local copy.")
            } else {
                val transaction = liveTransactionItems(mobileSession).firstOrNull { it.recordId == operation.recordId }
                SectionCard(transaction?.payee ?: "Transaction", transaction?.dateLabel ?: "Rejected edit") {
                    ListRow(
                        "¤",
                        "Requested amount",
                        "Local draft",
                        operation.optimisticAmountMinor?.let { state.formatSignedCurrency(it) } ?: "Unavailable",
                    )
                    DividerLine()
                    ListRow("!", "Reason", "The update was not applied", operation.lastErrorCode ?: "Rejected", tandemPalette.warning)
                }
                PreviewNote(
                    "The data layer removed the rejected optimistic overlay and retained the local audit entry until you dismiss it.",
                )
                PrimaryButton("Dismiss rejected edit") { onDiscard(operation.operationId) }
            }
        }
        return
    }
    PageColumn {
        Badge("Not applied", tandemPalette.negative)
        Heading("Previous value kept", "This change was not applied in Actual Budget.")
        SectionCard("Sample edit", "Corner Market · ${formatShortDate(SampleDate(2026, 5, 14))}") {
            ListRow("¤", "Requested amount", "Device draft", state.formatCurrency(1_200))
            DividerLine()
            ListRow("!", "Reason", "New transactions are not available yet", "Unavailable", tandemPalette.warning)
        }
        PreviewNote(
            "The change was not saved. The app keeps the reason and will offer a way to dismiss it.",
        )
        PrimaryButton("Close details") { navigation.back() }
    }
}

@Composable
private fun SettingsScreen(
    state: AppState,
    navigation: AppNavigation,
) {
    PageColumn {
        Heading("Preferences", "Adjust appearance, sync, privacy, and app behavior.")
        AnimatedReveal(visible = state.settingsStorageError != null) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PreviewNote(state.settingsStorageError.orEmpty())
                SecondaryButton("Retry settings storage") { state.settingsStorageRetry += 1 }
            }
        }
        SectionCard("Personalize", "Appearance and budget") {
            ListRow(
                "",
                "Appearance",
                "Theme, accent, and background",
                state.theme.label,
                leadingVector = Lucide.Palette,
                onClick = { navigation.open(AppRoute.APPEARANCE) },
            )
            DividerLine()
            ListRow(
                "",
                "Home screen layout",
                "Choose how much detail to show",
                state.homeLayout,
                leadingVector = Lucide.LayoutDashboard,
                onClick = { navigation.open(AppRoute.HOME_LAYOUT) },
            )
            DividerLine()
            ListRow(
                "",
                "Budget preferences",
                "Currency and planning choices",
                leadingVector = Lucide.WalletCards,
                onClick = { navigation.open(AppRoute.BUDGET_PREFERENCES) },
            )
        }
        SectionCard("Connection", "Sync and paired device") {
            ListRow(
                "",
                "Sync preferences",
                "Refresh and network behavior",
                leadingVector = Lucide.CloudSync,
                onClick = { navigation.open(AppRoute.SYNC_PREFERENCES) },
            )
            DividerLine()
            ListRow(
                "",
                "Server and paired device",
                "Connection details and access",
                leadingVector = Lucide.Smartphone,
                onClick = { navigation.open(AppRoute.SERVER_DEVICE) },
            )
            DividerLine()
            ListRow(
                "",
                "Notifications",
                "Sync reminders and review alerts",
                leadingVector = Lucide.Bell,
                onClick = { navigation.open(AppRoute.NOTIFICATIONS) },
            )
        }
        SectionCard("Privacy", "Device data and security") {
            ListRow(
                "",
                "Security and privacy",
                "Credentials and local protection",
                leadingVector = Lucide.ShieldCheck,
                onClick = { navigation.open(AppRoute.SECURITY_PRIVACY) },
            )
            DividerLine()
            ListRow(
                "",
                "Local data and storage",
                "Cached budget and pending edits",
                leadingVector = Lucide.Database,
                onClick = { navigation.open(AppRoute.LOCAL_DATA) },
            )
        }
        SectionCard("Support", "Help and project details") {
            ListRow(
                "",
                "About, help, and licenses",
                "Version and open-source licenses",
                leadingVector = Lucide.Info,
                onClick = { navigation.open(AppRoute.ABOUT) },
            )
        }
    }
}

@Composable
private fun AppearanceScreen(
    state: AppState,
    navigation: AppNavigation,
) {
    PageColumn {
        Heading("Visual settings", "The same appearance choices work on Android and iOS.")
        SectionCard("Theme", "${state.theme.label} appearance", "") {
            ListRow("◐", "Theme mode", "System, light, dark, or pure black", state.theme.label) { navigation.open(AppRoute.THEME_MODE) }
        }
        SectionCard("Color", "${state.accent.label} accent", "") {
            ListRow("●", "Accent color", "Selected across every app page", state.accent.label) { navigation.open(AppRoute.ACCENT_COLOR) }
        }
        SectionCard("Background", state.backgroundStyle, "") {
            ListRow(
                "✦",
                "Background and motion",
                "Subtle treatments and reduced motion",
                onClick = { navigation.open(AppRoute.BACKGROUND_MOTION) },
            )
        }
    }
}

@Composable
private fun ThemeModeScreen(state: AppState) {
    PageColumn {
        Heading("Color mode", "Choose the colors used across the app.")
        ThemeMode.entries.forEach { mode ->
            ChoiceRow(
                mode.label,
                when (mode) {
                    ThemeMode.SYSTEM -> "Follow this device’s light or dark setting"
                    ThemeMode.LIGHT -> "Bright surfaces with the Tandem color system"
                    ThemeMode.DARK -> "The default low-glare Tandem palette"
                    ThemeMode.PURE_BLACK -> "Black canvas with clear raised cards for OLED screens"
                },
                selected = state.theme == mode,
                onClick = { state.theme = mode },
            )
        }
        PreviewNote("Theme choices and colors stay consistent across Android and iOS.")
    }
}

@Composable
private fun AccentColorScreen(state: AppState) {
    PageColumn {
        Heading("Accent color")
        AccentChoice.entries.forEach { accent ->
            val palette = tandemPalette
            SurfaceCard(onClick = { state.accent = accent }) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(accent.color))
                    Text(
                        accent.label,
                        modifier = Modifier.weight(1f),
                        color = palette.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (state.accent == accent) {
                        Text("✓", color = palette.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun BackgroundScreen(state: AppState) {
    PageColumn {
        Heading("Background and motion", "These choices apply across every screen.")
        SurfaceCard {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(appBackgroundBrush(state.backgroundStyle, tandemPalette)),
                contentAlignment = Alignment.Center,
            ) {
                Text("${state.backgroundStyle} preview", color = tandemPalette.text, fontWeight = FontWeight.SemiBold)
            }
        }
        listOf(
            "Soft gradient" to "A subtle vertical blend behind the cards",
            "Solid surface" to "A single steady canvas color",
            "Low-motion glow" to "A static accent glow behind the content",
        ).forEach { (style, description) ->
            ChoiceRow(style, description, selected = state.backgroundStyle == style, onClick = { state.backgroundStyle = style })
        }
        SectionCard("Motion", "Animation should never get in the way") {
            ToggleRow(
                "Reduce motion",
                "Use the device setting by default; turn on to reduce motion further.",
                state.reduceMotion,
            ) { state.reduceMotionOverride = it }
            if (state.reduceMotionOverride != null) {
                SettingRow(
                    "↺",
                    "Use device setting",
                    if (state.systemReduceMotionEnabled) {
                        "The device currently requests reduced motion"
                    } else {
                        "The device default allows motion"
                    },
                    onClick = { state.reduceMotionOverride = null },
                )
            }
        }
        PreviewNote("Tandem follows the device motion preference unless you choose an override here.")
    }
}

@Composable
private fun HomeLayoutScreen(state: AppState) {
    PageColumn {
        Heading("Budget layout", "Set the amount of detail shown on the budget page.")
        listOf(
            "Comfortable" to "Roomy cards and extra context",
            "Compact" to "More categories visible at once",
            "Essentials first" to "Keep upcoming bills near the top",
        ).forEach { (label, description) ->
            ChoiceRow(label, description, selected = state.homeLayout == label, onClick = { state.homeLayout = label })
        }
        SurfaceCard {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LabelText("Layout preview")
                Text(
                    when (state.homeLayout) {
                        "Compact" -> "Compact rows and tighter spacing"
                        "Essentials first" -> "Upcoming bills and essentials appear first"
                        else -> "Roomy category rows with more breathing space"
                    },
                    color = tandemPalette.muted,
                    fontSize = 12.sp,
                )
                if (state.homeLayout == "Essentials first") {
                    ListRow("◷", "Upcoming bills", "Due soon", state.formatCurrency(12_000), dense = false)
                    DividerLine()
                }
                ListRow("H", "Essentials", "3 categories", state.formatCurrency(232_000), dense = state.homeLayout == "Compact")
                DividerLine()
                ListRow("G", "Groceries", "Available this month", state.formatCurrency(50_000), dense = state.homeLayout == "Compact")
            }
        }
    }
}

@Composable
private fun BudgetPreferencesScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
) {
    val currencyCode = state.budgetDisplaySettings?.currencyCode ?: platformCurrencyCode()
    PageColumn {
        Heading("Amount and planning", "Choose how amounts and plans are presented.")
        SectionCard(
            "Number format",
            if (state.budgetDisplaySettings != null) "Uses Actual budget settings" else "Device locale fallback",
        ) {
            ListRow(
                "¤",
                "Currency",
                if (mobileSession.budgetDisplaySettings != null) "Selected Actual budget" else "Follows the device locale",
                currencyCode,
            )
            DividerLine()
            ListRow("◷", "Start of week", "Follows the device locale", formatFirstWeekday())
        }
        SectionCard("Planning", "Values come from the selected Actual budget") {
            ListRow("✓", "Available category amounts", "Shown from the synced plan", "Available", tandemPalette.positive)
            DividerLine()
            ListRow("◷", "Upcoming bills", "Due-date data is not in the mobile sync yet", "Unavailable")
            DividerLine()
            ListRow("↻", "Carry balances forward", "Controlled by Actual and shown read-only", "Actual")
        }
        PreviewNote("Tandem displays planning values from Actual. It does not change budget rules from this page.")
    }
}

@Composable
private fun SyncPreferencesScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    onSyncNow: () -> Unit,
    backgroundSyncSupported: Boolean,
) {
    val hasSelectedBudget = state.selectedBudgetId.isNotBlank()
    val syncState = mobileSession.localSyncState
    val syncStatus =
        when {
            !hasSelectedBudget -> "No budget selected"
            mobileSession.isSyncing && mobileSession.syncError != null -> "Retrying"
            mobileSession.isSyncing -> "Syncing"
            mobileSession.syncError != null -> "Needs attention"
            syncState?.lastSyncedAtEpochMs != null -> "Ready"
            else -> "Never synced"
        }
    val syncDescription =
        when {
            !hasSelectedBudget -> "Pair this device and choose an Actual budget to sync"
            mobileSession.syncError != null -> mobileSession.syncError.orEmpty()
            mobileSession.isSyncing -> "Reading changes from Actual"
            syncState?.lastSyncedAtEpochMs != null -> "Last successful sync for ${state.selectedBudget}"
            else -> "Open a budget and sync it to start keeping a local copy"
        }
    PageColumn {
        Heading("Sync preferences", "Choose when this device checks Actual for changes.")
        SectionCard("When to sync", "Local data stays available offline") {
            ToggleRow("Sync when opening a budget", "Check for server changes when returning to a budget", state.syncOnOpen) {
                state.syncOnOpen =
                    it
            }
            if (backgroundSyncSupported) {
                DividerLine()
                ToggleRow(
                    "Schedule background sync",
                    "Refresh the selected budget about every six hours",
                    state.backgroundSyncEnabled,
                ) { state.backgroundSyncEnabled = it }
                DividerLine()
                ToggleRow(
                    "Background sync on Wi-Fi or other unrestricted connections",
                    "Your device pauses sync on networks marked as limited or costly.",
                    state.syncOnWifi,
                ) { state.syncOnWifi = it }
                BodyText("Encrypted budgets are skipped because budget passwords are never stored.")
            } else {
                BodyText("Scheduled sync is managed by the iOS host app and is not configured in this workspace.")
            }
            BodyText(
                "Manual sync and sync on open use the current connection.",
            )
        }
        SectionCard("Current budget", if (hasSelectedBudget) state.selectedBudget else "Choose an Actual budget", animateDetail = true) {
            ListRow(
                "↻",
                "Sync now",
                "Read changes and check pending edits",
                when {
                    mobileSession.isSyncing -> "Syncing"
                    hasSelectedBudget -> "Sync"
                    else -> "Unavailable"
                },
                onClick = onSyncNow.takeIf { hasSelectedBudget && !mobileSession.isSyncing },
                animateChanges = true,
            )
            DividerLine()
            ListRow(
                "◷",
                "Sync status",
                syncDescription,
                syncStatus,
                trailingColor = if (mobileSession.syncError != null) tandemPalette.warning else null,
                animateChanges = true,
            )
        }
        AnimatedPreviewNote(mobileSession.syncError)
    }
}

@Composable
private fun ServerDeviceScreen(
    state: AppState,
    deviceName: String,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    onForgetDevice: () -> Unit,
) {
    val device = mobileSession.pairedDevice
    PageColumn {
        Heading("Connection details", "Review the server address and device pairing.")
        SectionCard(
            "Server",
            when {
                device == null -> "No device pairing"
                mobileSession.isCredentialStored -> "Paired on this phone"
                else -> "Paired for this app session"
            },
        ) {
            ListRow(
                "",
                "Server URL",
                state.serverUrl,
                if (device != null) "Paired" else "Add",
                leadingVector = Lucide.Server,
                onClick = { navigation.open(AppRoute.SERVER_ADDRESS) },
            )
            DividerLine()
            ListRow(
                "",
                "Connection status",
                if (mobileSession.isAuthenticated) "Device credential is active" else "Pair this device to access budgets",
                if (mobileSession.isAuthenticated) "Paired" else "Offline",
                if (mobileSession.isAuthenticated) tandemPalette.positive else tandemPalette.warning,
                leadingVector = Lucide.Wifi,
            )
        }
        SectionCard(
            "This device",
            when {
                device == null -> "Pairing is not active"
                mobileSession.isCredentialStored -> "Pairing is saved on this phone"
                else -> "Pairing is active until the app closes"
            },
        ) {
            ListRow(
                "",
                "Device name",
                if (device != null) deviceName else "Not paired",
                leadingIconResource = if (device != null) Res.drawable.tandem_logo_squircle else null,
                leadingVector = Lucide.Smartphone,
            )
            DividerLine()
            ListRow(
                "",
                "Budget access",
                if (device == null) {
                    "No device pairing"
                } else {
                    when (device.budgetIds.size) {
                        0 -> "No budgets available to this device"
                        1 -> "1 budget available to this device"
                        else -> "${device.budgetIds.size} budgets available to this device"
                    }
                },
                leadingVector = Lucide.FolderKey,
            )
            DividerLine()
            ListRow(
                "",
                "Device credential",
                if (mobileSession.isCredentialStored) "Encrypted in system secure storage" else "In-memory for this app session",
                when {
                    mobileSession.isCredentialStored -> "Saved"
                    device != null -> "Active"
                    else -> "None"
                },
                leadingVector = Lucide.KeyRound,
            )
        }
        BodyText(
            if (mobileSession.isCredentialStored) {
                "Pairing codes and budget passwords are never saved. The device credential is protected by the platform secure store. Removing it here only clears this phone; revoke the device on the server to disable its token."
            } else {
                "Pairing codes and budget passwords are never saved. The device credential is active only until the app closes."
            },
        )
        AnimatedPreviewNote(mobileSession.credentialStorageError)
        if (device == null) {
            SecondaryButton("Pair this device") { navigation.open(AppRoute.SERVER_ADDRESS) }
        } else {
            SecondaryButton("Forget this device on this phone", onClick = onForgetDevice)
        }
    }
}

@Composable
private fun SecurityScreen(mobileSession: TandemMobileSession) {
    PageColumn {
        Heading("Device protection", "Protect financial data and manage device access.")
        SectionCard("Feature availability", "Pairing and budget credentials") {
            ListRow("◎", "Biometric unlock", "App lock is not available in this build", "Unavailable")
            DividerLine()
            ListRow("⌫", "Transaction deletion", "Delete actions are not available in the mobile app", "Unavailable")
        }
        SectionCard("Data handling", "Server connection") {
            ListRow(
                "⌑",
                "Device credential",
                if (mobileSession.isCredentialStored) "Encrypted in system secure storage" else "In-memory until the app closes",
                when {
                    mobileSession.isCredentialStored -> "Saved"
                    mobileSession.isAuthenticated -> "Active"
                    else -> "Not paired"
                },
            )
            DividerLine()
            ListRow("◉", "Budget password", "Used only to open an encrypted budget", "Not stored")
        }
        PreviewNote(
            "Pairing codes and encrypted-budget passwords are never stored. Device credentials use the platform secure store when available.",
        )
        AnimatedPreviewNote(mobileSession.credentialStorageError)
    }
}

@Composable
private fun NotificationsScreen(
    state: AppState,
    onRequestNotificationPermission: (() -> Unit)?,
    onOpenNotificationSettings: (() -> Unit)?,
) {
    PageColumn {
        Heading("Alert preferences", "Choose in-app reminders and background notifications.")
        SectionCard("Budget activity", "In-app banners appear when you open the budget") {
            ToggleRow(
                "Sync reminders",
                "Show a banner and notification when a background sync fails",
                state.syncReminders,
            ) {
                state.syncReminders = it
            }
            DividerLine()
            ToggleRow(
                "Conflict review alerts",
                "Show a banner and notification when a background sync finds a conflict",
                state.conflictReviewAlerts,
            ) {
                state.conflictReviewAlerts =
                    it
            }
            DividerLine()
            ToggleRow(
                "Rejected edit alerts",
                "Show a banner and notification when a background sync cannot apply an edit",
                state.rejectedEditAlerts,
            ) {
                state.rejectedEditAlerts = it
            }
        }
        SectionCard("System permission", "The operating system controls notification delivery") {
            if (onRequestNotificationPermission != null) {
                SecondaryButton("Allow notifications") { onRequestNotificationPermission() }
                Spacer(Modifier.height(8.dp))
            }
            if (onOpenNotificationSettings != null) {
                SecondaryButton("Open notification settings") { onOpenNotificationSettings() }
            }
        }
        PreviewNote("System alerts follow scheduled sync; banners appear in-app. If disabled, manage permission in system settings.")
    }
}

@Composable
private fun LocalDataScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    onSyncNow: () -> Unit,
) {
    val hasSelectedBudget = state.selectedBudgetId.isNotBlank()
    val recordCount =
        mobileSession.cachedTransactionCount +
            mobileSession.accountRecords.size +
            mobileSession.payeeRecords.size +
            mobileSession.categoryRecords.size +
            mobileSession.categoryGroupRecords.size +
            mobileSession.budgetMonthRecords.size
    val queuedCount = mobileSession.pendingWrites.count { it.status == "queued" || it.status == "sending" }
    val conflictCount = mobileSession.pendingWrites.count { it.status == "conflict" }
    val attentionCount = mobileSession.pendingWrites.count { it.status == "uncertain" || it.status == "rejected" }
    val syncStatus =
        when {
            !hasSelectedBudget -> "No budget selected"
            mobileSession.isSyncing && mobileSession.syncError != null -> "Retrying"
            mobileSession.isSyncing -> "Syncing"
            mobileSession.syncError != null -> "Needs attention"
            mobileSession.localSyncState?.lastSyncedAtEpochMs != null -> "Ready"
            else -> "Not synced"
        }
    PageColumn {
        Heading(
            "Stored data",
            if (hasSelectedBudget) {
                "Review the local copy and queued changes for ${state.selectedBudget}."
            } else {
                "Pair this device and select an Actual budget to view its cached data."
            },
        )
        AmountHero(
            "Cached records",
            formatInteger(recordCount),
            "${formatInteger(mobileSession.cachedTransactionCount)} transactions in the selected budget",
        )
        SectionCard("On this device", syncStatus, animateDetail = true) {
            ListRow(
                "▤",
                "Transactions",
                "Full local record count",
                formatInteger(mobileSession.cachedTransactionCount),
            )
            DividerLine()
            ListRow("▣", "Accounts", "Cached account records", formatInteger(mobileSession.accountRecords.size))
            DividerLine()
            ListRow("↑", "Queued changes", "Waiting to sync", formatInteger(queuedCount))
            DividerLine()
            ListRow("!", "Conflicts to review", "Changes with a newer server version", formatInteger(conflictCount))
            AnimatedReveal(visible = attentionCount > 0, animateHeight = false) {
                Column {
                    DividerLine()
                    ListRow("⚠", "Other changes needing attention", "Uncertain or rejected", formatInteger(attentionCount))
                }
            }
        }
        SectionCard("Storage actions", "") {
            ListRow(
                "↻",
                "Refresh local budget",
                "Sync the selected budget with Actual",
                when {
                    mobileSession.isSyncing -> "Syncing"
                    hasSelectedBudget -> "Refresh"
                    else -> "Unavailable"
                },
                onClick = onSyncNow.takeIf { hasSelectedBudget && !mobileSession.isSyncing },
                animateChanges = true,
            )
            DividerLine()
            ListRow("×", "Clear local data", "Not available while local-first sync is active", "Unavailable")
        }
        AnimatedPreviewNote(mobileSession.syncError)
        BodyText(
            "Cached data belongs to this budget on this device. Clearing it is disabled so queued, " +
                "conflicted, or uncertain edits cannot be lost.",
        )
    }
}

@Composable
private fun MerchantIconLicensesScreen(
    merchantIconPack: MerchantIconPack?,
    loadError: String?,
) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Heading("Merchant icon licenses", "Each bundled mark, its reported license, and its source.")
        when {
            loadError != null -> BodyText(loadError)
            merchantIconPack == null -> {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    TandemLoadingIndicator(
                        modifier = Modifier.size(20.dp),
                        color = tandemPalette.accent,
                        strokeWidth = 2.dp,
                    )
                    BodyText("Loading the bundled icon catalog…")
                }
            }
            else -> {
                TextFieldCard("Search", query, { query = it }, "Name, license, or source")
                val filteredNotices =
                    remember(merchantIconPack, query) {
                        val normalizedQuery = query.trim()
                        merchantIconPack.notices.filter { notice ->
                            normalizedQuery.isEmpty() ||
                                notice.title.contains(normalizedQuery, ignoreCase = true) ||
                                notice.license.contains(normalizedQuery, ignoreCase = true) ||
                                notice.registryUrl.contains(normalizedQuery, ignoreCase = true) ||
                                notice.brandUrl.contains(normalizedQuery, ignoreCase = true)
                        }
                    }
                Text(
                    "${filteredNotices.size} of ${merchantIconPack.notices.size} bundled icons",
                    color = tandemPalette.muted,
                    fontSize = 12.sp,
                )
                if (filteredNotices.isEmpty()) {
                    BodyText("No bundled icons match that search.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        items(filteredNotices, key = { it.resourceName }) { notice ->
                            SurfaceCard(
                                modifier =
                                    Modifier.animateItem(
                                        fadeInSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.REVEAL_MILLIS),
                                        placementSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTENT_SIZE_MILLIS),
                                        fadeOutSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.REVEAL_MILLIS),
                                    ),
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(13.dp),
                                    verticalArrangement = Arrangement.spacedBy(7.dp),
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Image(
                                            painter = painterResource(notice.resource),
                                            contentDescription = null,
                                            modifier = Modifier.size(34.dp),
                                            contentScale = ContentScale.Fit,
                                        )
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                notice.title,
                                                color = tandemPalette.text,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Text(notice.license, color = tandemPalette.muted, fontSize = 12.sp)
                                        }
                                    }
                                    Text(
                                        "Registry source: ${notice.registryUrl}",
                                        color = tandemPalette.muted,
                                        fontSize = 10.sp,
                                        lineHeight = 14.sp,
                                        modifier = Modifier.semantics { contentDescription = "Registry source URL: ${notice.registryUrl}" },
                                    )
                                    if (notice.brandUrl.isNotBlank()) {
                                        Text(
                                            "Brand site: ${notice.brandUrl}",
                                            color = tandemPalette.muted,
                                            fontSize = 10.sp,
                                            lineHeight = 14.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutScreen(
    merchantIconPack: MerchantIconPack?,
    merchantIconPackLoadError: String?,
    navigation: AppNavigation,
) {
    PageColumn {
        Heading("About Tandem", "An independent, local-first companion for Actual Budget.")
        SurfaceCard {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TandemBrandMark(Modifier.size(84.dp), contentDescription = "Tandem app icon")
                Text("Tandem", color = tandemPalette.text, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Badge("Version 0.1.0")
                Text(
                    "Your budget stays on this device and remains available when the server is offline.",
                    color = tandemPalette.muted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Not affiliated with, endorsed by, or supported by Actual Budget.",
                    color = tandemPalette.muted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
        SectionCard("Project", "Independent companion for Actual Budget") {
            ListRow("A", "Supported Actual server", "Required by this Tandem release", SUPPORTED_ACTUAL_SERVER_VERSION)
            DividerLine()
            ListRow("⌘", "Open-source license", "GNU AGPL-3.0-or-later")
        }
        SectionCard("Open-source libraries", "Direct mobile-app dependencies and licenses") {
            ListRow("K", "Kotlin and kotlinx", "Kotlin, Coroutines and Serialization · Apache-2.0")
            DividerLine()
            ListRow("C", "Compose Multiplatform and resources", "Apache-2.0")
            DividerLine()
            ListRow("N", "Ktor", "Apache-2.0")
            DividerLine()
            ListRow("R", "AndroidX Room and SQLite", "Apache-2.0")
            DividerLine()
            ListRow(
                "W",
                "AndroidX platform libraries",
                "Activity Compose, WorkManager and SplashScreen · Apache-2.0",
            )
            DividerLine()
            ListRow("M", "Compose Icons", "MIT")
            DividerLine()
            ListRow("L", "Lucide icon artwork", "ISC")
            DividerLine()
            ListRow("T", "TheSVG icon package", "Build-time catalog and tooling · MIT")
            DividerLine()
            ListRow("T", "TheSVG merchant artwork", "Per-icon licenses and source links are bundled")
            DividerLine()
            ListRow("R", "REI Cedar icons", "MIT · REI merchant artwork")
            DividerLine()
            ListRow("W", "Panera Bread wordmark", "Public domain · Wikimedia Commons source")
        }
        SectionCard("Merchant artwork", "License-approved marks are bundled for offline matching") {
            ListRow(
                "M",
                "Bundled merchant icons",
                merchantIconPack?.let { "${it.iconCount} icons · ${it.sourceSummary}" }
                    ?: if (merchantIconPackLoadError == null) "Catalog details load when opened" else "Unavailable",
            )
            DividerLine()
            ListRow(
                "CC",
                "Artwork license scope",
                merchantIconPack?.let { "${it.openSourceIconCount} open license / public domain · ${it.fairUseIconCount} Fair Use" }
                    ?: "CC0, MIT, Apache-2.0, public domain, and Fair Use entries",
            )
            DividerLine()
            ListRow(
                "©",
                "View icon licenses and sources",
                "Search the license and source record for every bundled mark",
                trailing = merchantIconPack?.iconCount?.toString(),
                onClick = { navigation.open(AppRoute.MERCHANT_ICON_LICENSES) },
            )
            DividerLine()
            Column(
                Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BodyText(
                    "Tandem matches merchant names and aliases against this local catalog. The match runs on this device and does not request an icon from a service.",
                )
                BodyText(
                    "The full TheSVG catalog is checked and every CC0, MIT, Apache-2.0, public-domain, or Fair Use mark is included. REI artwork is from REI Cedar (MIT), and the Panera Bread wordmark is public domain. Other license types are excluded. Every mark keeps its license and source URL.",
                )
                BodyText(
                    "Names, logos, and trademarks belong to their respective owners. Tandem displays a matched mark only to identify the payee and is not affiliated with those brands. Follow each brand’s usage guidelines.",
                )
            }
        }
        AnimatedPreviewNote(merchantIconPackLoadError)
        PreviewNote("TheSVG package license and the per-icon license and source list are bundled with the app resources.")
    }
}
