@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import app.tandemforactual.api.client.BudgetCatalogItem
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.database.SearchableBudgetTransaction
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.allDrawableResources
import app.tandemforactual.ui.model.LiveCategoryGroupSummary
import app.tandemforactual.ui.model.LiveCategorySummary
import app.tandemforactual.ui.model.recordName
import app.tandemforactual.ui.model.stringValue
import com.composables.icons.lucide.Baby
import com.composables.icons.lucide.Banknote
import com.composables.icons.lucide.Bike
import com.composables.icons.lucide.BookOpen
import com.composables.icons.lucide.BriefcaseBusiness
import com.composables.icons.lucide.Building2
import com.composables.icons.lucide.BusFront
import com.composables.icons.lucide.CarFront
import com.composables.icons.lucide.Carrot
import com.composables.icons.lucide.Cat
import com.composables.icons.lucide.Coffee
import com.composables.icons.lucide.Dog
import com.composables.icons.lucide.Droplets
import com.composables.icons.lucide.Dumbbell
import com.composables.icons.lucide.Flower2
import com.composables.icons.lucide.Fuel
import com.composables.icons.lucide.Gamepad2
import com.composables.icons.lucide.Gift
import com.composables.icons.lucide.GraduationCap
import com.composables.icons.lucide.HeartHandshake
import com.composables.icons.lucide.HeartPulse
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Landmark
import com.composables.icons.lucide.Leaf
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Music2
import com.composables.icons.lucide.NotepadText
import com.composables.icons.lucide.PawPrint
import com.composables.icons.lucide.PiggyBank
import com.composables.icons.lucide.Pill
import com.composables.icons.lucide.Plane
import com.composables.icons.lucide.PlugZap
import com.composables.icons.lucide.Receipt
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.Shirt
import com.composables.icons.lucide.ShoppingBag
import com.composables.icons.lucide.Smartphone
import com.composables.icons.lucide.Sofa
import com.composables.icons.lucide.Stethoscope
import com.composables.icons.lucide.TrainFront
import com.composables.icons.lucide.Tv
import com.composables.icons.lucide.Utensils
import com.composables.icons.lucide.WalletCards
import com.composables.icons.lucide.Wifi
import com.composables.icons.lucide.Wrench
import com.composables.icons.lucide.Zap
import kotlinx.coroutines.flow.first
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
import kotlinx.serialization.json.put
import org.jetbrains.compose.resources.DrawableResource

internal const val SUPPORTED_ACTUAL_SERVER_VERSION = "26.11.0"

internal fun budgetDisplayNameForHeader(name: String): String {
    val segments = name.split(" · ")
    return segments.firstOrNull { segment ->
        val codeStart = segment.lastIndexOf('(')
        segment.endsWith(')') &&
            codeStart >= 0 &&
            segment.substring(codeStart + 1, segment.lastIndex).length == 3 &&
            segment.substring(codeStart + 1, segment.lastIndex).all { it in 'A'..'Z' }
    } ?: segments.firstOrNull().orEmpty()
}

internal enum class AppRoute(
    val title: String,
) {
    STARTUP("Tandem"),
    WELCOME("Tandem"),
    SERVER_ADDRESS("Server address"),
    PAIRING_OPTIONS("Pair this device"),
    PAIRING_CODE("Enter pairing code"),
    PAIRING_RESULT("Device paired"),
    BUDGET_CHOOSER("Budgets"),
    UNLOCK_BUDGET("Unlock budget"),
    FIRST_SYNC("Preparing budget"),
    BUDGET("Budget"),
    MONTH_PICKER("Choose month"),
    CATEGORY_GROUP("Essentials"),
    CATEGORY_DETAIL("Rent"),
    SCHEDULES("Upcoming bills"),
    BUDGET_RULES("Rules"),
    ACCOUNTS("Accounts"),
    ACCOUNT_DETAIL("Checking"),
    ACCOUNT_OPTIONS("Account options"),
    ACTIVITY("Activity"),
    REPORTS("Reports"),
    REPORT_SPENDING_BY_CATEGORY("Spending by category"),
    REPORT_SPENDING_OVER_TIME("Spending over time"),
    REPORT_INCOME_AND_SPENDING("Income and spending"),
    REPORT_BUDGETED_AND_SPENT("Budgeted and spent"),
    REPORT_NET_WORTH("Net worth"),
    ACTIVITY_SEARCH("Search transactions"),
    TRANSACTION_DETAIL("Transaction details"),
    NEW_TRANSACTION("Add transaction"),
    EDIT_TRANSACTION("Edit transaction"),
    CHOOSE_ACCOUNT("Choose account"),
    CHOOSE_PAYEE("Choose payee"),
    MANAGE_PAYEES("Manage payees"),
    CHOOSE_CATEGORY("Choose category"),
    CHOOSE_TRANSACTION_TYPE("Choose transaction type"),
    CHOOSE_DATE("Choose date"),
    SYNC_CENTER("Sync center"),
    CONFLICT_INBOX("Needs review"),
    CONFLICT_REVIEW("Review conflict"),
    UNCERTAIN_WRITE("Status unknown"),
    REJECTED_WRITE("Not applied"),
    SETTINGS("Settings"),
    APPEARANCE("Appearance"),
    BUDGET_PREFERENCES("Budget preferences"),
    SYNC_PREFERENCES("Sync preferences"),
    SERVER_DEVICE("Server and paired device"),
    SECURITY_PRIVACY("Security and privacy"),
    NOTIFICATIONS("Notifications"),
    LOCAL_DATA("Local data and storage"),
    ABOUT("About and help"),
    MERCHANT_ICON_LICENSES("Icon licenses and sources"),
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

internal val UNSYNCED_TRANSACTION_WRITE_STATUSES = setOf("queued", "sending", "uncertain", "conflict", "rejected")

internal fun writeStatusLabel(status: String): String =
    when (status) {
        "queued" -> "Waiting to sync"
        "sending" -> "Syncing"
        "uncertain" -> "Status unknown"
        "conflict" -> "Needs your review"
        "rejected" -> "Not applied"
        "committed" -> "Saved"
        "discarded" -> "Discarded"
        else -> "Change"
    }

internal data class TransactionItem(
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
    val dateIso: String = "",
    val payeeId: String? = null,
    val categoryId: String? = null,
    val cleared: Boolean? = null,
    val reconciled: Boolean? = null,
    val approved: Boolean? = null,
    val matched: Boolean? = null,
    val isTransfer: Boolean = false,
    val isSplitParent: Boolean = false,
)

internal data class MerchantIconNotice(
    val title: String,
    val license: String,
    val registryUrl: String,
    val brandUrl: String,
    val resourceName: String,
    val resource: DrawableResource,
)

internal data class MerchantIconMatcherPack(
    private val matcher: MerchantAliasMatcher<DrawableResource>,
    private val accountMatcher: MerchantAliasMatcher<DrawableResource>,
) {
    fun iconFor(payee: String): DrawableResource? = matcher.findPayeeIcon(payee)

    fun accountIconFor(accountName: String): DrawableResource? = accountMatcher.findAccountIcon(accountName)
}

internal data class ActivitySearchCache(
    val budgetId: String,
    val sourceTransactions: List<SearchableBudgetTransaction>,
    val sourceAccounts: List<BudgetRecordEntity>,
    val sourcePayees: List<BudgetRecordEntity>,
    val sourceCategories: List<BudgetRecordEntity>,
    val displaySettings: BudgetDisplaySettings?,
    val searchableRecords: List<ActivitySearchRecord>,
) {
    fun matches(
        budgetId: String,
        transactions: List<SearchableBudgetTransaction>,
        accounts: List<BudgetRecordEntity>,
        payees: List<BudgetRecordEntity>,
        categories: List<BudgetRecordEntity>,
        displaySettings: BudgetDisplaySettings?,
    ): Boolean =
        this.budgetId == budgetId &&
            sourceTransactions === transactions &&
            sourceAccounts === accounts &&
            sourcePayees === payees &&
            sourceCategories === categories &&
            this.displaySettings == displaySettings
}

internal data class ActivitySearchResultCache(
    val budgetId: String,
    val query: String,
    val filters: List<ActivitySearchFilter>,
    val sourceRecords: List<ActivitySearchRecord>,
    val indices: List<Int>,
    val suggestions: List<ActivitySearchFilter>,
)

internal data class MerchantIconCatalog(
    val notices: List<MerchantIconNotice>,
)

internal suspend fun loadMerchantIconMatcherPack(): MerchantIconMatcherPack {
    val index = Res.readBytes("files/merchant-icon-matcher.tsv").decodeToString()
    val resources = Res.allDrawableResources
    val matcher = MerchantAliasMatcher<DrawableResource>()
    val resourcesBySlug = mutableMapOf<String, DrawableResource>()
    val prioritiesBySlug = mutableMapOf<String, Int>()
    val accountMatcher = MerchantAliasMatcher<DrawableResource>()

    for (line in index.lineSequence()) {
        if (line.isBlank()) continue
        val fields = line.split('\t', limit = 5)
        if (fields.size != 5) continue
        val slug = fields[0]
        val resourceName = fields[1]
        val licensePriority = fields[2].toIntOrNull() ?: continue
        val isTitle = fields[3] == "1"
        val label = fields[4]
        val resource = resources[resourceName] ?: continue
        resourcesBySlug[slug] = resource
        prioritiesBySlug[slug] = licensePriority
        matcher.add(label, resource, licensePriority, isTitle)
        if (slug in accountIssuerSlugs) accountMatcher.add(label, resource, licensePriority, isTitle)
    }

    for ((slug, aliases) in supplementaryMerchantAliases) {
        val resource = resourcesBySlug[slug] ?: continue
        val licensePriority = prioritiesBySlug[slug] ?: continue
        aliases.forEach { alias -> matcher.add(alias, resource, licensePriority, isTitle = false) }
    }

    for ((slug, aliases) in supplementaryAccountIssuerAliases) {
        val resource = resourcesBySlug[slug] ?: continue
        val licensePriority = prioritiesBySlug[slug] ?: continue
        aliases.forEach { alias -> accountMatcher.add(alias, resource, licensePriority, isTitle = false) }
    }

    return MerchantIconMatcherPack(matcher, accountMatcher)
}

internal suspend fun loadMerchantIconCatalog(): MerchantIconCatalog {
    val catalog = Json.parseToJsonElement(Res.readBytes("files/merchant-icon-catalog.json").decodeToString()).jsonObject
    val resources = Res.allDrawableResources
    val notices =
        catalog["icons"]
            ?.jsonArray
            .orEmpty()
            .mapNotNull { element ->
                val icon = element.jsonObject
                val resourceName = icon["resourceName"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val resource = resources[resourceName] ?: return@mapNotNull null
                val title = icon["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val license = icon["license"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val slug = icon["slug"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val registryUrl =
                    icon["registryUrl"]?.jsonPrimitive?.contentOrNull
                        ?: "https://thesvg.org/icon/$slug"
                val brandUrl = icon["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
                MerchantIconNotice(title, license, registryUrl, brandUrl, resourceName, resource)
            }.sortedBy { it.title.lowercase() }

    return MerchantIconCatalog(notices)
}

internal fun AppState.formatCurrency(minorUnits: Long): String = formatCurrency(minorUnits, budgetDisplaySettings)

internal fun AppState.formatAmount(minorUnits: Long): String = formatAmount(minorUnits, budgetDisplaySettings)

internal fun AppState.formatSignedCurrency(minorUnits: Long): String = formatSignedCurrency(minorUnits, budgetDisplaySettings)

internal fun AppState.parseAmountMinorUnits(value: String): Long? = parseAmountMinorUnits(value, budgetDisplaySettings)

internal val previewTransactions =
    listOf(
        TransactionItem(
            "Corner Market",
            "Groceries",
            formatShortDate(SampleDate(2026, 5, 14)),
            -4_218,
            "G",
            categoryIcon = financeIcon("Groceries"),
            dateIso = "2026-05-14",
        ),
        TransactionItem(
            "Electric Company",
            "Utilities",
            formatShortDate(SampleDate(2026, 5, 12)),
            -12_000,
            "E",
            categoryIcon = financeIcon("Utilities"),
            dateIso = "2026-05-12",
        ),
        TransactionItem(
            "Acme Corp",
            "Paycheck",
            formatShortDate(SampleDate(2026, 5, 10)),
            250_000,
            "A",
            categoryIcon = financeIcon("Paycheck"),
            dateIso = "2026-05-10",
        ),
        TransactionItem(
            "Riverside Cafe",
            "Dining",
            formatShortDate(SampleDate(2026, 5, 8)),
            -2_840,
            "R",
            categoryIcon = financeIcon("Dining"),
            dateIso = "2026-05-08",
        ),
        TransactionItem(
            "City Transit",
            "Transport",
            formatShortDate(SampleDate(2026, 5, 6)),
            -1_650,
            "C",
            categoryIcon = financeIcon("Transport"),
            dateIso = "2026-05-06",
        ),
        TransactionItem(
            "Book Nook",
            "Personal",
            formatShortDate(SampleDate(2026, 5, 4)),
            -2_399,
            "B",
            categoryIcon = Lucide.Receipt,
            dateIso = "2026-05-04",
        ),
    )

internal val previewCategoryGroups =
    listOf(
        LiveCategoryGroupSummary(
            id = "sample-essentials",
            name = "Essentials",
            categories =
                listOf(
                    LiveCategorySummary("sample-rent", "Rent", 150_000, 0, 150_000),
                    LiveCategorySummary("sample-groceries", "Groceries", 50_000, -18_400, 31_600),
                    LiveCategorySummary("sample-utilities", "Utilities", 32_000, -11_200, 20_800),
                ),
        ),
        LiveCategoryGroupSummary(
            id = "sample-everyday",
            name = "Everyday",
            categories = listOf(LiveCategorySummary("sample-dining", "Dining", 35_000, -17_000, 18_000)),
        ),
    )

internal fun buildLiveTransactionItems(
    records: List<BudgetRecordEntity>,
    accountRecords: List<BudgetRecordEntity>,
    payeeRecords: List<BudgetRecordEntity>,
    categoryRecords: List<BudgetRecordEntity>,
    displaySettings: BudgetDisplaySettings?,
    merchantIconMatcher: MerchantIconMatcherPack?,
): List<TransactionItem> {
    val accounts = accountRecords.mapNotNull { record -> record.recordName()?.let { record.recordId to it } }.toMap()
    val payees = payeeRecords.mapNotNull { record -> record.recordName()?.let { record.recordId to it } }.toMap()
    val categories = categoryRecords.mapNotNull { record -> record.recordName()?.let { record.recordId to it } }.toMap()
    val payeeIconCache = mutableMapOf<String, DrawableResource?>()
    return records.mapNotNull { record ->
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
            merchantIcon = payeeIconCache.getOrPut(displayedPayee) { merchantIconMatcher?.iconFor(displayedPayee) },
            account = record.accountId?.let(accounts::get) ?: record.accountId ?: "No account",
            accountId = record.accountId,
            recordId = record.recordId,
            notes = record.notes.orEmpty(),
            displaySettings = displaySettings,
            dateIso = record.recordDate.orEmpty(),
            payeeId = record.payeeId,
            categoryId = record.categoryId,
            cleared = record.cleared,
            reconciled = record.reconciled,
            approved = record.approved,
            matched = record.matched,
            isTransfer = record.transfer == true,
            isSplitParent = record.isParent,
        )
    }
}

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

internal data class SelectorItem(
    val id: String,
    val label: String,
)

internal data class SelectorGroup(
    val title: String,
    val items: List<SelectorItem>,
)

internal fun formatLiveDate(value: String?): String {
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

internal fun sanitizePairingCodeInput(value: String): String =
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

internal const val AUTOMATIC_SYNC_COOLDOWN_MS = 30_000L

internal val LocalRouteScrollState = staticCompositionLocalOf<ScrollState?> { null }
internal val LocalRouteLazyListState = staticCompositionLocalOf<LazyListState?> { null }

internal fun AppState.isBudgetSectionCollapsed(sectionKey: String): Boolean =
    collapsedBudgetSections.split('\u001f').any { it == sectionKey }

internal fun AppState.toggleBudgetSection(sectionKey: String) {
    val collapsed = collapsedBudgetSections.split('\u001f').filter(String::isNotBlank).toMutableSet()
    if (!collapsed.add(sectionKey)) collapsed.remove(sectionKey)
    collapsedBudgetSections = collapsed.joinToString("\u001f")
}

internal fun AppState.isCategoryPinned(categoryId: String): Boolean =
    pinnedCategoryIds.split('\u001f').any { it == categoryPinKey(categoryId) || it == categoryId }

internal val categoryIconChoices =
    listOf(
        "Automatic",
        "Home",
        "Rent",
        "Furniture",
        "Maintenance",
        "Utilities",
        "Water",
        "Groceries",
        "Dining",
        "Coffee",
        "Shopping",
        "Clothing",
        "Travel",
        "Car",
        "Fuel",
        "Transit",
        "Train",
        "Bike",
        "Health",
        "Medicine",
        "Dental",
        "Pet",
        "Cat",
        "Dog",
        "Family",
        "Education",
        "Books",
        "Gifts",
        "Income",
        "Paycheck",
        "Savings",
        "Money",
        "Insurance",
        "Phone",
        "Internet",
        "Electricity",
        "Entertainment",
        "Music",
        "Games",
        "Fitness",
        "Garden",
        "Plants",
        "Charity",
        "Business",
        "Taxes",
        "Other",
    )

internal fun categoryIconVector(choice: String): ImageVector? =
    when (choice) {
        "Home" -> Lucide.House
        "Food" -> Lucide.Carrot
        "Transport" -> Lucide.CarFront
        "Rent" -> Lucide.Building2
        "Furniture" -> Lucide.Sofa
        "Maintenance" -> Lucide.Wrench
        "Utilities" -> Lucide.PlugZap
        "Water" -> Lucide.Droplets
        "Groceries" -> Lucide.Carrot
        "Dining" -> Lucide.Utensils
        "Coffee" -> Lucide.Coffee
        "Shopping" -> Lucide.ShoppingBag
        "Clothing" -> Lucide.Shirt
        "Travel" -> Lucide.Plane
        "Car" -> Lucide.CarFront
        "Fuel" -> Lucide.Fuel
        "Transit" -> Lucide.BusFront
        "Train" -> Lucide.TrainFront
        "Bike" -> Lucide.Bike
        "Health" -> Lucide.HeartPulse
        "Medicine" -> Lucide.Pill
        "Dental" -> Lucide.Stethoscope
        "Pet" -> Lucide.PawPrint
        "Cat" -> Lucide.Cat
        "Dog" -> Lucide.Dog
        "Family" -> Lucide.Baby
        "Education" -> Lucide.GraduationCap
        "Books" -> Lucide.BookOpen
        "Gifts" -> Lucide.Gift
        "Income" -> Lucide.BriefcaseBusiness
        "Paycheck" -> Lucide.Banknote
        "Savings" -> Lucide.PiggyBank
        "Money" -> Lucide.WalletCards
        "Insurance" -> Lucide.ShieldCheck
        "Phone" -> Lucide.Smartphone
        "Internet" -> Lucide.Wifi
        "Electricity" -> Lucide.Zap
        "Entertainment" -> Lucide.Tv
        "Music" -> Lucide.Music2
        "Games" -> Lucide.Gamepad2
        "Fitness" -> Lucide.Dumbbell
        "Garden" -> Lucide.Flower2
        "Plants" -> Lucide.Leaf
        "Charity" -> Lucide.HeartHandshake
        "Business" -> Lucide.Landmark
        "Taxes" -> Lucide.Receipt
        "Other" -> Lucide.NotepadText
        else -> null
    }

internal fun AppState.categoryIconChoice(categoryId: String): String? {
    val key = categoryPinKey(categoryId)
    return categoryIconOverrides
        .split('\u001f')
        .firstNotNullOfOrNull { entry ->
            val delimiter = entry.indexOf('\u001e')
            if (delimiter < 0 || entry.substring(0, delimiter) != key) null else entry.substring(delimiter + 1)
        }
}

internal fun AppState.setCategoryIconChoice(
    categoryId: String,
    choice: String,
) {
    val key = categoryPinKey(categoryId)
    val choices =
        categoryIconOverrides
            .split('\u001f')
            .mapNotNull { entry ->
                val delimiter = entry.indexOf('\u001e')
                if (delimiter < 0) null else entry.substring(0, delimiter) to entry.substring(delimiter + 1)
            }.toMap()
            .toMutableMap()
    if (choice == "Automatic") choices.remove(key) else choices[key] = choice
    categoryIconOverrides = choices.entries.joinToString("\u001f") { (id, icon) -> "$id\u001e$icon" }
}

internal fun AppState.categoryIconFor(
    categoryId: String,
    categoryName: String,
): ImageVector = categoryIconChoice(categoryId)?.let(::categoryIconVector) ?: financeIcon(categoryName) ?: Lucide.Receipt

internal fun AppState.togglePinnedCategory(categoryId: String) {
    val pinned = pinnedCategoryIds.split('\u001f').filter(String::isNotBlank).toMutableSet()
    val key = categoryPinKey(categoryId)
    if (key in pinned || categoryId in pinned) {
        pinned.remove(key)
        pinned.remove(categoryId)
    } else {
        pinned.add(key)
    }
    pinnedCategoryIds = pinned.joinToString("\u001f")
}

internal fun AppState.categoryPinKey(categoryId: String): String = "${selectedBudgetId.ifBlank { "sample" }}:$categoryId"

internal data class AppNavigation(
    val open: (AppRoute) -> Unit,
    val reset: (AppRoute) -> Unit,
    val replaceTop: (AppRoute) -> Unit,
    val newTransaction: (accountId: String?) -> Unit,
    val selectTab: (AppRoute) -> Unit,
    val back: () -> Unit,
    val backToList: () -> Unit,
)

internal data class AppRouteEntry(
    val route: AppRoute,
    val id: Long,
) {
    val stateKey: String
        get() = if (route.isTab()) "tab-${route.name}" else "route-${route.name}-$id"
}

internal val appRouteStackSaver =
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
