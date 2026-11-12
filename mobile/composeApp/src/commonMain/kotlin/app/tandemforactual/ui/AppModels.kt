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

internal fun categoryCountLabel(count: Int): String = "${count.coerceAtLeast(0)} ${if (count == 1) "category" else "categories"}"

internal fun mainCloudStatusLabel(status: MainCloudStatus): String =
    when (status) {
        MainCloudStatus.OFF -> "Sync off"
        MainCloudStatus.SYNCED -> "Synced"
        MainCloudStatus.SYNCING -> "Syncing"
        MainCloudStatus.ERROR -> "Sync needs attention"
    }

internal fun mainCloudStatusAccessibilityDescription(
    status: MainCloudStatus,
    pendingWriteStatuses: Collection<String>,
): String {
    val conflictCount = pendingWriteStatuses.count { it == "conflict" }
    val uncertainCount = pendingWriteStatuses.count { it == "uncertain" }
    val rejectedCount = pendingWriteStatuses.count { it == "rejected" }
    val reviewCount = conflictCount + uncertainCount + rejectedCount
    if (reviewCount == 0) {
        return when (status) {
            MainCloudStatus.OFF -> "Sync is off"
            MainCloudStatus.SYNCED -> "Everything synced"
            MainCloudStatus.SYNCING -> "Syncing changes"
            MainCloudStatus.ERROR -> "Sync needs attention"
        }
    }

    val reasons =
        buildList {
            if (conflictCount > 0) add("$conflictCount ${pluralizeCloudCount(conflictCount, "conflict", "conflicts")}")
            if (uncertainCount > 0) {
                add("$uncertainCount ${pluralizeCloudCount(uncertainCount, "edit with unknown status", "edits with unknown status")}")
            }
            if (rejectedCount > 0) add("$rejectedCount ${pluralizeCloudCount(rejectedCount, "rejected edit", "rejected edits")}")
        }
    val changeNoun = if (reviewCount == 1) "change" else "changes"
    val verb = if (reviewCount == 1) "needs" else "need"
    return "$reviewCount $changeNoun $verb review: ${reasons.joinToString(", ")}."
}

private fun pluralizeCloudCount(
    count: Int,
    singular: String,
    plural: String,
): String = if (count == 1) singular else plural

internal enum class AppRoute(
    val title: String,
) {
    STARTUP("Tandem"),
    WELCOME("Tandem"),
    SERVER_ADDRESS("Server address"),
    SIGN_IN("Sign in"),
    BUDGET_CHOOSER("Budgets"),
    FIRST_SYNC("Preparing budget"),
    NOTIFICATION_ONBOARDING("Stay in the loop"),
    BUDGET("Budget"),
    CATEGORY_GROUP("Essentials"),
    CATEGORY_DETAIL("Rent"),
    SCHEDULES("Upcoming bills"),
    BUDGET_RULES("Budget rules"),
    ACCOUNTS("Accounts"),
    ACCOUNT_DETAIL("Checking"),
    ACCOUNT_OPTIONS("Account options"),
    ACTIVITY("Activity"),
    REPORTS("Reports"),
    REPORT_DETAIL("Report"),
    ACTIVITY_SEARCH("Search transactions"),
    TRANSACTION_DETAIL("Transaction details"),
    NEW_TRANSACTION("Add transaction"),
    EDIT_TRANSACTION("Edit transaction"),
    CHOOSE_PAYEE("Choose payee"),
    MANAGE_PAYEES("Manage payees"),
    CHOOSE_CATEGORY("Choose category"),
    SYNC_CENTER("Sync center"),
    CONFLICT_INBOX("Needs review"),
    CONFLICT_REVIEW("Review versions"),
    UNCERTAIN_WRITE("Status unknown"),
    SETTINGS("Settings"),
    APPEARANCE("Appearance"),
    BUDGET_PREFERENCES("Budget preferences"),
    SYNC_PREFERENCES("Sync preferences"),
    SERVER_DEVICE("Server and device"),
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
    hasPendingBudgetChanges: Boolean = false,
    budgetChangesWaitingForConnection: Boolean = false,
): MainCloudStatus {
    val hasWriteNeedingReview = pendingWriteStatuses.any { it in setOf("conflict", "uncertain", "rejected") }
    if (hasWriteNeedingReview) return MainCloudStatus.ERROR
    if (isSyncing || persistedSyncStatus == "SYNCING") return MainCloudStatus.SYNCING
    if (budgetChangesWaitingForConnection) return MainCloudStatus.ERROR
    if (!syncEnabled) return if (hasPendingBudgetChanges) MainCloudStatus.ERROR else MainCloudStatus.OFF

    if (hasSyncError || persistedSyncStatus in setOf("FAILED", "NEEDS_FULL_SYNC")) {
        return MainCloudStatus.ERROR
    }
    if (pendingWriteStatuses.any { it in setOf("queued", "sending") } || hasPendingBudgetChanges) return MainCloudStatus.SYNCING
    return if (hasSuccessfulSync) MainCloudStatus.SYNCED else MainCloudStatus.ERROR
}

internal val UNSYNCED_TRANSACTION_WRITE_STATUSES = setOf("queued", "sending", "uncertain", "conflict", "rejected")

internal fun writeStatusLabel(status: String): String =
    when (status) {
        "queued" -> "Waiting to sync"
        "sending" -> "Syncing"
        "uncertain" -> "Status unknown"
        "conflict" -> "Needs review"
        "rejected" -> "Rejected"
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

internal fun buildLiveTransactionItems(
    records: List<BudgetRecordEntity>,
    accountRecords: List<BudgetRecordEntity>,
    payeeRecords: List<BudgetRecordEntity>,
    categoryRecords: List<BudgetRecordEntity>,
    displaySettings: BudgetDisplaySettings?,
    merchantIconMatcher: MerchantIconMatcherPack?,
    todayIso: String = platformTodayIsoDate(),
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
            dateLabel = formatTransactionListDate(record.recordDate, todayIso),
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
        formatDate(CalendarDate(year, month, day))
    } else {
        value ?: "Undated"
    }
}

internal fun formatTransactionListDate(
    value: String?,
    todayIso: String = platformTodayIsoDate(),
): String {
    val date = parseCalendarDate(value) ?: return formatLiveDate(value)
    val today = parseCalendarDate(todayIso) ?: return formatDate(date)
    return when (calendarDayNumber(today) - calendarDayNumber(date)) {
        0L -> "Today"
        1L -> "Yesterday"
        else -> formatDate(date)
    }
}

private fun parseCalendarDate(value: String?): CalendarDate? {
    val parts = value?.take(10)?.split('-') ?: return null
    if (parts.size != 3) return null
    val year = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val day = parts[2].toIntOrNull() ?: return null
    if (year < 1 || month !in 1..12 || day !in 1..calendarDaysInMonth(year, month)) return null
    return CalendarDate(year, month, day)
}

private fun calendarDaysInMonth(
    year: Int,
    month: Int,
): Int =
    when (month) {
        2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

private fun calendarDayNumber(date: CalendarDate): Long {
    val adjustedYear = date.year - if (date.month <= 2) 1 else 0
    val era = adjustedYear / 400
    val yearOfEra = adjustedYear - era * 400
    val adjustedMonth = date.month + if (date.month > 2) -3 else 9
    val dayOfYear = (153 * adjustedMonth + 2) / 5 + date.day - 1
    val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
    return era.toLong() * 146_097 + dayOfEra
}

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

internal fun shouldTriggerAutomaticSyncForRouteEntry(
    previousRoute: AppRoute,
    currentRoute: AppRoute,
): Boolean =
    currentRoute == AppRoute.BUDGET &&
        previousRoute in
        setOf(
            AppRoute.BUDGET,
            AppRoute.BUDGET_CHOOSER,
            AppRoute.STARTUP,
            AppRoute.WELCOME,
        )

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

internal fun AppState.collapseBudgetSections(sectionKeys: Iterable<String>) {
    val collapsed = collapsedBudgetSections.split('\u001f').filter(String::isNotBlank).toMutableSet()
    collapsed.addAll(sectionKeys)
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

internal fun AppState.categoryPinKey(categoryId: String): String = "$selectedBudgetId:$categoryId"

internal data class AppNavigation(
    val open: (AppRoute) -> Unit,
    val openSchedule: (String) -> Boolean = { false },
    val reset: (AppRoute) -> Unit,
    val replaceTop: (AppRoute) -> Unit,
    val newTransaction: (accountId: String?) -> Unit,
    val selectTab: (AppRoute) -> Unit,
    val back: () -> Unit,
    val backToList: () -> Unit,
)

internal enum class BackRequestDisposition {
    START,
    DEFER_UNTIL_ROUTE_TRANSITION_FINISHES,
    QUEUE_AFTER_BACK_TRANSITION,
}

internal fun shouldRenderRouteHeader(
    route: AppRoute,
    hasStationaryTabShell: Boolean,
): Boolean =
    route != AppRoute.NOTIFICATION_ONBOARDING &&
        (hasStationaryTabShell || route == AppRoute.FIRST_SYNC)

internal fun shouldRenderStandaloneRouteHeader(route: AppRoute): Boolean =
    route != AppRoute.NOTIFICATION_ONBOARDING && route != AppRoute.FIRST_SYNC

internal fun backRequestDisposition(
    routeTransitionInputBlocked: Boolean,
    backButtonTransitionRunning: Boolean,
): BackRequestDisposition =
    when {
        backButtonTransitionRunning -> BackRequestDisposition.QUEUE_AFTER_BACK_TRANSITION
        routeTransitionInputBlocked -> BackRequestDisposition.DEFER_UNTIL_ROUTE_TRANSITION_FINISHES
        else -> BackRequestDisposition.START
    }

internal data class AppRouteEntry(
    val route: AppRoute,
    val id: Long,
) {
    val stateKey: String
        get() = if (route.isTab()) "tab-${route.name}" else "route-${route.name}-$id"
}

internal fun routeContentStateKey(
    entry: AppRouteEntry,
    selectedBudgetId: String,
): String =
    if (entry.route == AppRoute.BUDGET && selectedBudgetId.isNotBlank()) {
        "${entry.stateKey}-budget-$selectedBudgetId"
    } else {
        entry.stateKey
    }

internal fun encodeAppTabBackStacks(stacks: Map<AppRoute, List<AppRouteEntry>>): String =
    stacks.entries
        .filter { (tab, _) -> tab.isTab() }
        .joinToString("|") { (tab, entries) ->
            "${tab.name}=" + entries.joinToString(",") { "${it.id}:${it.route.name}" }
        }

internal fun decodeAppTabBackStacks(value: String): Map<AppRoute, List<AppRouteEntry>> =
    value
        .split('|')
        .mapNotNull { encodedStack ->
            val separator = encodedStack.indexOf('=')
            if (separator < 1) {
                null
            } else {
                val tab = AppRoute.entries.firstOrNull { it.name == encodedStack.substring(0, separator) && it.isTab() }
                val entries =
                    encodedStack
                        .substring(separator + 1)
                        .split(',')
                        .mapNotNull { encodedEntry ->
                            val entrySeparator = encodedEntry.indexOf(':')
                            if (entrySeparator < 1) {
                                null
                            } else {
                                val id = encodedEntry.substring(0, entrySeparator).toLongOrNull()
                                val route = AppRoute.entries.firstOrNull { it.name == encodedEntry.substring(entrySeparator + 1) }
                                if (id == null || route == null) null else AppRouteEntry(route, id)
                            }
                        }
                if (tab == null || entries.isEmpty()) null else tab to entries
            }
        }.toMap()

internal data class AppTabSelection(
    val backStack: List<AppRouteEntry>,
    val savedTabBackStacks: String,
)

internal fun selectAppTabNavigation(
    currentBackStack: List<AppRouteEntry>,
    savedTabBackStacks: String,
    selectedTab: AppRoute,
    newRouteEntry: (AppRoute) -> AppRouteEntry,
): AppTabSelection {
    val tabStacks = decodeAppTabBackStacks(savedTabBackStacks).toMutableMap()
    val activeTab = currentBackStack.lastOrNull { it.route.isTab() }?.route
    val nextBackStack =
        if (activeTab == selectedTab) {
            if (currentBackStack.lastOrNull()?.route == selectedTab) {
                currentBackStack
            } else {
                tabStacks.remove(selectedTab)
                buildList {
                    if (selectedTab != AppRoute.BUDGET) add(newRouteEntry(AppRoute.BUDGET))
                    add(newRouteEntry(selectedTab))
                }
            }
        } else {
            activeTab?.let { tabStacks[it] = currentBackStack }
            tabStacks[selectedTab] ?: buildList {
                if (selectedTab != AppRoute.BUDGET) add(newRouteEntry(AppRoute.BUDGET))
                add(newRouteEntry(selectedTab))
            }
        }
    return AppTabSelection(nextBackStack, encodeAppTabBackStacks(tabStacks))
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
