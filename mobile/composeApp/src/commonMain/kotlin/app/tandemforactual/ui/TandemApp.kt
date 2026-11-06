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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.api.client.BudgetCatalogItem
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.allDrawableResources
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.BriefcaseBusiness
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.CarFront
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.CircleDot
import com.composables.icons.lucide.Cloud
import com.composables.icons.lucide.CloudAlert
import com.composables.icons.lucide.CloudCheck
import com.composables.icons.lucide.CloudOff
import com.composables.icons.lucide.CloudSync
import com.composables.icons.lucide.Coffee
import com.composables.icons.lucide.Database
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.FingerprintPattern
import com.composables.icons.lucide.FolderKey
import com.composables.icons.lucide.HeartPulse
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.LayoutDashboard
import com.composables.icons.lucide.LockKeyhole
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.NotepadText
import com.composables.icons.lucide.PaintbrushVertical
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.PawPrint
import com.composables.icons.lucide.Plane
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Receipt
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Server
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.ShoppingCart
import com.composables.icons.lucide.Smartphone
import com.composables.icons.lucide.SunMoon
import com.composables.icons.lucide.Tags
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Utensils
import com.composables.icons.lucide.WalletCards
import com.composables.icons.lucide.Wallpaper
import com.composables.icons.lucide.Wifi
import com.composables.icons.lucide.X
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
    ACCOUNTS("Accounts"),
    ACCOUNT_DETAIL("Checking"),
    ACCOUNT_OPTIONS("Account options"),
    RECONCILIATION("Reconcile account"),
    ACTIVITY("Activity"),
    ACTIVITY_SEARCH("Search transactions"),
    ACTIVITY_FILTER("Filter and sort"),
    TRANSACTION_DETAIL("Transaction details"),
    NEW_TRANSACTION("Add transaction"),
    EDIT_TRANSACTION("Edit transaction"),
    CHOOSE_ACCOUNT("Choose account"),
    CHOOSE_PAYEE("Choose payee"),
    CHOOSE_CATEGORY("Choose category"),
    CHOOSE_TRANSACTION_TYPE("Choose transaction type"),
    CHOOSE_DATE("Choose date"),
    SAVE_RESULT("Transaction status"),
    SYNC_CENTER("Sync center"),
    CONFLICT_INBOX("Review changes"),
    CONFLICT_REVIEW("Review changes"),
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

private val UNSYNCED_TRANSACTION_WRITE_STATUSES = setOf("queued", "sending", "uncertain", "conflict", "rejected")

private fun writeStatusLabel(status: String): String =
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
    val dateIso: String = "",
    val payeeId: String? = null,
    val categoryId: String? = null,
    val clearedState: String? = null,
    val approved: Boolean? = null,
    val matched: Boolean? = null,
    val isTransfer: Boolean = false,
)

private data class MerchantIconNotice(
    val title: String,
    val license: String,
    val registryUrl: String,
    val brandUrl: String,
    val resourceName: String,
    val resource: DrawableResource,
)

private data class MerchantIconMatcherPack(
    private val matcher: MerchantAliasMatcher<DrawableResource>,
) {
    fun iconFor(payee: String): DrawableResource? = matcher.findPayeeIcon(payee)
}

private data class MerchantIconCatalog(
    val notices: List<MerchantIconNotice>,
)

private suspend fun loadMerchantIconMatcherPack(): MerchantIconMatcherPack {
    val index = Res.readBytes("files/merchant-icon-matcher.tsv").decodeToString()
    val resources = Res.allDrawableResources
    val matcher = MerchantAliasMatcher<DrawableResource>()
    val resourcesBySlug = mutableMapOf<String, DrawableResource>()
    val prioritiesBySlug = mutableMapOf<String, Int>()

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
    }

    for ((slug, aliases) in supplementaryMerchantAliases) {
        val resource = resourcesBySlug[slug] ?: continue
        val licensePriority = prioritiesBySlug[slug] ?: continue
        aliases.forEach { alias -> matcher.add(alias, resource, licensePriority, isTitle = false) }
    }

    return MerchantIconMatcherPack(matcher)
}

private suspend fun loadMerchantIconCatalog(): MerchantIconCatalog {
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

private fun buildLiveTransactionItems(
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
        val body = record.recordBody()
        val clearedState =
            body?.stringValue("cleared")?.lowercase()
                ?: body?.booleanValue("cleared")?.let { if (it) "cleared" else "uncleared" }
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
            clearedState = clearedState,
            approved = body?.booleanValue("approved"),
            matched = body?.booleanValue("matched"),
            isTransfer =
                body?.stringValue("transfer_id") != null ||
                    body?.stringValue("transferId") != null ||
                    body?.booleanValue("is_transfer") == true ||
                    body?.booleanValue("isTransfer") == true,
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

private fun liveAccountSummaries(accountRecords: List<BudgetRecordEntity>): List<LiveAccountSummary> =
    accountRecords.mapNotNull { record ->
        val body = record.recordBody() ?: return@mapNotNull null
        LiveAccountSummary(
            id = record.recordId,
            name = record.recordName() ?: return@mapNotNull null,
            balanceMinor = body.longValue("balance_current"),
            type = body.stringValue("type"),
            offBudget = body.booleanValue("offbudget") ?: body.booleanValue("offBudget") ?: false,
        )
    }

private fun liveBudgetMonthSummary(
    monthRecord: BudgetRecordEntity?,
    categoryRecords: List<BudgetRecordEntity>,
    categoryGroupRecords: List<BudgetRecordEntity>,
): LiveBudgetMonthSummary? {
    monthRecord ?: return null
    val month = monthRecord.recordBody() ?: return null
    val categoryNames = categoryRecords.associate { it.recordId to (it.recordName() ?: it.recordId) }
    val groupNames = categoryGroupRecords.associate { it.recordId to (it.recordName() ?: it.recordId) }
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

private fun BudgetRecordEntity.recordName(): String? = displayName?.takeIf { it.isNotBlank() }

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
private val LocalRouteLazyListState = staticCompositionLocalOf<LazyListState?> { null }

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

private fun decodeLazyScrollPositions(serialized: String?): Map<String, Pair<Int, Int>> =
    serialized
        ?.split('|')
        ?.mapNotNull { entry ->
            val delimiter = entry.lastIndexOf(':')
            if (delimiter < 1) {
                null
            } else {
                val values = entry.substring(delimiter + 1).split(',', limit = 2)
                val index = values.getOrNull(0)?.toIntOrNull()
                val offset = values.getOrNull(1)?.toIntOrNull()
                if (index == null || offset == null || index < 0 || offset < 0) {
                    null
                } else {
                    entry.substring(0, delimiter) to (index to offset)
                }
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
    var activitySearchQuery by mutableStateOf("")
    var activitySearchFilters by mutableStateOf<List<ActivitySearchFilter>>(emptyList())
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
    var transactionType by mutableStateOf("Spending")
    var transactionNotes by mutableStateOf("")
    var selectedTransactionAmountMinor by mutableStateOf<Long?>(null)
    var selectedTransactionNotes by mutableStateOf("")
    var selectedTransactionIsLive by mutableStateOf(false)
    var conflictChoice by mutableStateOf("")
    var backgroundStyle by mutableStateOf("Solid surface")
    var homeLayout by mutableStateOf("Comfortable")
    var collapsedBudgetSections by mutableStateOf("")
    var pinnedCategoryIds by mutableStateOf("")
    var readOnlyMode by mutableStateOf(false)
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
    val lazyScrollPositions = mutableMapOf<String, Pair<Int, Int>>()

    fun persistentSettings(): Map<String, String> =
        mapOf(
            "theme" to theme.name,
            "accent" to accent.name,
            "backgroundStyle" to backgroundStyle,
            "homeLayout" to homeLayout,
            "collapsedBudgetSections" to collapsedBudgetSections,
            "pinnedCategoryIds" to pinnedCategoryIds,
            "readOnlyMode" to readOnlyMode.toString(),
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
        homeLayout = (saved["homeLayout"] ?: homeLayout).let { if (it == "Essentials first") "Essentials" else it }
        collapsedBudgetSections = saved["collapsedBudgetSections"] ?: collapsedBudgetSections
        pinnedCategoryIds = saved["pinnedCategoryIds"] ?: pinnedCategoryIds
        readOnlyMode = saved["readOnlyMode"]?.toBooleanStrictOrNull() ?: readOnlyMode
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
                        "transactionType" to state.transactionType,
                        "transactionNotes" to state.transactionNotes,
                        "selectedTransactionAmountMinor" to state.selectedTransactionAmountMinor,
                        "selectedTransactionIsLive" to state.selectedTransactionIsLive,
                        "conflictChoice" to state.conflictChoice,
                        "backgroundStyle" to state.backgroundStyle,
                        "homeLayout" to state.homeLayout,
                        "collapsedBudgetSections" to state.collapsedBudgetSections,
                        "pinnedCategoryIds" to state.pinnedCategoryIds,
                        "readOnlyMode" to state.readOnlyMode,
                        "reduceMotionOverride" to state.reduceMotionOverride,
                        "syncOnOpen" to state.syncOnOpen,
                        "backgroundSyncEnabled" to state.backgroundSyncEnabled,
                        "syncOnWifi" to state.syncOnWifi,
                        "syncReminders" to state.syncReminders,
                        "conflictReviewAlerts" to state.conflictReviewAlerts,
                        "rejectedEditAlerts" to state.rejectedEditAlerts,
                        "scrollPositions" to state.scrollPositions.entries.joinToString("|") { "${it.key}:${it.value}" },
                        "lazyScrollPositions" to
                            state.lazyScrollPositions.entries.joinToString("|") { "${it.key}:${it.value.first},${it.value.second}" },
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
                        transactionType = saved["transactionType"] as? String ?: transactionType
                        transactionNotes = saved["transactionNotes"] as? String ?: transactionNotes
                        selectedTransactionAmountMinor = saved["selectedTransactionAmountMinor"] as? Long
                        selectedTransactionIsLive = saved["selectedTransactionIsLive"] as? Boolean ?: selectedTransactionIsLive
                        conflictChoice = saved["conflictChoice"] as? String ?: conflictChoice
                        backgroundStyle = saved["backgroundStyle"] as? String ?: backgroundStyle
                        homeLayout =
                            (saved["homeLayout"] as? String ?: homeLayout).let {
                                if (it == "Essentials first") "Essentials" else it
                            }
                        collapsedBudgetSections = saved["collapsedBudgetSections"] as? String ?: collapsedBudgetSections
                        pinnedCategoryIds = saved["pinnedCategoryIds"] as? String ?: pinnedCategoryIds
                        readOnlyMode = saved["readOnlyMode"] as? Boolean ?: readOnlyMode
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
                        lazyScrollPositions.putAll(decodeLazyScrollPositions(saved["lazyScrollPositions"] as? String))
                    }
                },
            )
    }
}

private fun AppState.isBudgetSectionCollapsed(sectionKey: String): Boolean =
    collapsedBudgetSections.split('\u001f').any { it == sectionKey }

private fun AppState.toggleBudgetSection(sectionKey: String) {
    val collapsed = collapsedBudgetSections.split('\u001f').filter(String::isNotBlank).toMutableSet()
    if (!collapsed.add(sectionKey)) collapsed.remove(sectionKey)
    collapsedBudgetSections = collapsed.joinToString("\u001f")
}

private fun AppState.isCategoryPinned(categoryId: String): Boolean =
    pinnedCategoryIds.split('\u001f').any { it == categoryPinKey(categoryId) || it == categoryId }

private fun AppState.togglePinnedCategory(categoryId: String) {
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

private fun AppState.categoryPinKey(categoryId: String): String = "${selectedBudgetId.ifBlank { "sample" }}:$categoryId"

private data class AppNavigation(
    val open: (AppRoute) -> Unit,
    val newTransaction: (accountId: String?) -> Unit,
    val selectTab: (AppRoute) -> Unit,
    val back: () -> Unit,
    val backToList: () -> Unit,
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
    notificationPermissionEnabled: Boolean? = null,
    onToggleNotificationPermission: ((Boolean) -> Unit)? = null,
    onStartupComplete: (() -> Unit)? = null,
    appResumeEvents: Flow<Unit> = emptyFlow(),
    networkRestoredEvents: Flow<Unit> = emptyFlow(),
) {
    var accessibilitySettingsRevision by remember { mutableStateOf(0) }
    val systemReduceMotion = key(accessibilitySettingsRevision) { SystemReduceMotionEnabled() }
    val state = rememberSaveable(saver = AppState.Saver) { AppState() }
    val saveableStateHolder = rememberSaveableStateHolder()
    val routeScrollStates = remember { mutableMapOf<String, ScrollState>() }
    val routeLazyListStates = remember { mutableMapOf<String, LazyListState>() }
    SideEffect { state.systemReduceMotionEnabled = systemReduceMotion }
    val reduceMotion = state.reduceMotionOverride ?: systemReduceMotion
    val apiClient = remember(allowInsecureLoopback) { TandemApiClient(allowInsecureLoopback) }
    val mobileSession =
        remember(apiClient, database, deviceCredentialStore) {
            TandemMobileSession(apiClient, database, nowEpochMillis, deviceCredentialStore)
        }
    SideEffect { mobileSession.setReadOnlyMode(state.readOnlyMode) }
    val appPreferencesStore = remember(database) { database?.let(::AppPreferencesStore) }
    val coroutineScope = rememberCoroutineScope()
    var merchantIconMatcher by remember { mutableStateOf<MerchantIconMatcherPack?>(null) }
    var merchantIconMatcherLoadError by remember { mutableStateOf<String?>(null) }
    var merchantIconCatalog by remember { mutableStateOf<MerchantIconCatalog?>(null) }
    var merchantIconCatalogLoadError by remember { mutableStateOf<String?>(null) }
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
    LaunchedEffect(state.selectedBudgetId, state.selectedBudgetEncrypted) {
        if (state.selectedBudgetEncrypted) state.backgroundSyncEnabled = false
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
                state.settingsStorageError = "App settings couldn’t be loaded. Retry to load saved preferences."
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
                            "App settings still couldn’t be saved. Check local storage and retry."
                        } else {
                            "App settings couldn’t be saved. Retry to save your changes."
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
    var liveAccounts by remember { mutableStateOf(emptyList<LiveAccountSummary>()) }
    val newTransaction: (String?) -> Unit = { requestedAccountId ->
        if (!state.readOnlyMode) {
            routeTransitionDirection = 1f
            val eligibleAccounts = liveAccounts.filterNot { it.offBudget }
            val defaultAccount =
                if (state.selectedBudgetId.isNotBlank()) {
                    eligibleAccounts.firstOrNull { it.id == requestedAccountId } ?: eligibleAccounts.firstOrNull()
                } else {
                    null
                }
            state.transactionAmount = state.formatAmount(0)
            state.transactionAccount = defaultAccount?.name ?: "Checking"
            state.newTransactionAccountId = defaultAccount?.id.orEmpty()
            state.transactionPayee = ""
            state.newTransactionPayeeId = ""
            state.transactionCategory = ""
            state.newTransactionCategoryId = ""
            state.transactionDate = platformTodayIsoDate()
            state.transactionType = "Spending"
            state.transactionNotes = ""
            state.selectedTransactionRecordId = ""
            state.selectedTransactionAmountMinor = null
            state.selectedTransactionIsLive = false
            backStack.add(newRouteEntry(AppRoute.NEW_TRANSACTION))
        }
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
            backToList = {
                if (backStack.size > 2) {
                    routeTransitionDirection = -1f
                    backStack.removeAt(backStack.lastIndex)
                    backStack.removeAt(backStack.lastIndex)
                } else {
                    handleBack()
                }
            },
        )
    val routeEntry = backStack.last()
    val route = routeEntry.route
    val merchantMatcherNeeded = mobileSession.transactionRecords.isNotEmpty()
    LaunchedEffect(mobileSession, merchantMatcherNeeded) {
        if (merchantIconMatcher == null && merchantIconMatcherLoadError == null && merchantMatcherNeeded) {
            try {
                merchantIconMatcher = withContext(Dispatchers.Default) { loadMerchantIconMatcherPack() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                merchantIconMatcherLoadError = "Couldn’t load merchant icons."
            }
        }
    }
    val merchantCatalogNeeded = route == AppRoute.MERCHANT_ICON_LICENSES
    LaunchedEffect(mobileSession, merchantCatalogNeeded) {
        if (merchantIconCatalog == null && merchantIconCatalogLoadError == null && merchantCatalogNeeded) {
            try {
                merchantIconCatalog = withContext(Dispatchers.Default) { loadMerchantIconCatalog() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                merchantIconCatalogLoadError = "Couldn’t load icon sources."
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
    LaunchedEffect(state.selectedBudgetId, state.selectedMonthId, mobileSession) {
        mobileSession.observeSelectedBudgetMonth(state.selectedBudgetId, state.selectedMonthId)
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
    var liveTransactions by remember { mutableStateOf(emptyList<TransactionItem>()) }
    val transactionRecords = mobileSession.transactionRecords
    val accountRecords = mobileSession.accountRecords
    val payeeRecords = mobileSession.payeeRecords
    val categoryRecords = mobileSession.categoryRecords
    val displaySettings = mobileSession.budgetDisplaySettings
    LaunchedEffect(
        transactionRecords,
        accountRecords,
        payeeRecords,
        categoryRecords,
        displaySettings,
        merchantIconMatcher,
    ) {
        liveTransactions =
            withContext(Dispatchers.Default) {
                buildLiveTransactionItems(
                    transactionRecords,
                    accountRecords,
                    payeeRecords,
                    categoryRecords,
                    displaySettings,
                    merchantIconMatcher,
                )
            }
    }
    LaunchedEffect(accountRecords) {
        liveAccounts = withContext(Dispatchers.Default) { liveAccountSummaries(accountRecords) }
    }
    var liveBudgetMonth by remember { mutableStateOf<LiveBudgetMonthSummary?>(null) }
    LaunchedEffect(
        mobileSession.selectedBudgetMonthRecord,
        mobileSession.categoryRecords,
        mobileSession.categoryGroupRecords,
        state.selectedMonthId,
    ) {
        val monthRecord = mobileSession.selectedBudgetMonthRecord?.takeIf { it.recordId == state.selectedMonthId }
        val monthCategories = mobileSession.categoryRecords
        val monthCategoryGroups = mobileSession.categoryGroupRecords
        liveBudgetMonth = null
        liveBudgetMonth =
            withContext(Dispatchers.Default) {
                liveBudgetMonthSummary(monthRecord, monthCategories, monthCategoryGroups)
            }
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
    val saveTransaction: ((Boolean) -> Unit) -> Unit = { onComplete ->
        val transactionId = state.selectedTransactionRecordId
        val originalAmount = state.selectedTransactionAmountMinor
        if (!state.readOnlyMode && transactionId.isNotBlank() && originalAmount != null && !mobileSession.isSavingTransaction) {
            coroutineScope.launch {
                val saved =
                    mobileSession.updateTransaction(
                        budgetId = state.selectedBudgetId,
                        transactionId = transactionId,
                        amountText = state.transactionAmount,
                        notes = state.transactionNotes,
                        originalAmountMinor = originalAmount,
                        displaySettings = state.budgetDisplaySettings,
                    )
                if (saved) {
                    val amount = state.parseAmountMinorUnits(state.transactionAmount) ?: kotlin.math.abs(originalAmount)
                    state.selectedTransactionAmountMinor = if (originalAmount < 0) -amount else amount
                    state.selectedTransactionNotes = state.transactionNotes
                }
                onComplete(saved)
            }
        } else {
            onComplete(false)
        }
    }
    val deleteTransaction: ((Boolean) -> Unit) -> Unit = { onComplete ->
        val transactionId = state.selectedTransactionRecordId
        if (!state.readOnlyMode && transactionId.isNotBlank() && !mobileSession.isSavingTransaction) {
            coroutineScope.launch {
                onComplete(mobileSession.deleteTransaction(state.selectedBudgetId, transactionId))
            }
        } else {
            onComplete(false)
        }
    }
    val createTransaction: () -> Unit = {
        if (!state.readOnlyMode && !mobileSession.isSavingTransaction) {
            val enteredAmount = state.parseAmountMinorUnits(state.transactionAmount)
            val signedAmountText =
                enteredAmount?.let { amount ->
                    val unsignedAmount = kotlin.math.abs(amount)
                    state.formatAmount(if (state.transactionType == "Income") unsignedAmount else -unsignedAmount)
                } ?: state.transactionAmount
            coroutineScope.launch {
                val created =
                    mobileSession.createTransaction(
                        budgetId = state.selectedBudgetId,
                        accountId = state.newTransactionAccountId,
                        payeeId = state.newTransactionPayeeId.takeIf { it.isNotBlank() },
                        categoryId = state.newTransactionCategoryId.takeIf { it.isNotBlank() },
                        amountText = signedAmountText,
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
    val checkUncertainWriteStatus: (String) -> Unit = { operationId ->
        if (!mobileSession.isSyncing) {
            coroutineScope.launch {
                mobileSession.checkUncertainWriteStatus(
                    state.selectedBudgetId,
                    operationId,
                    state.encryptedPassword.takeIf { it.isNotBlank() },
                )
            }
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
        val mainCloudStatus =
            resolveMainCloudStatus(
                syncEnabled =
                    state.selectedBudgetId.isNotBlank() &&
                        (mobileSession.isAuthenticated || mobileSession.isRestoringStoredSession),
                isSyncing = mobileSession.isSyncing,
                hasSyncError = mobileSession.syncError != null,
                persistedSyncStatus = mobileSession.localSyncState?.status,
                pendingWriteStatuses = mobileSession.pendingWrites.map { it.status },
                hasSuccessfulSync = mobileSession.localSyncState?.lastSyncedAtEpochMs != null,
            )
        val renderHeader: @Composable (AppRoute) -> Unit = { headerRoute ->
            if (headerRoute != AppRoute.WELCOME && headerRoute != AppRoute.STARTUP) {
                AppHeader(
                    route = headerRoute,
                    state = state,
                    showBack = !headerRoute.isTab(),
                    onBack = handleBack,
                    onSync = { navigation.open(AppRoute.SYNC_CENTER) },
                    syncStatus = mainCloudStatus,
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
            val routeLazyListState =
                remember(visibleEntry.stateKey) {
                    routeLazyListStates.getOrPut(visibleEntry.stateKey) {
                        val (index, offset) = state.lazyScrollPositions[visibleEntry.stateKey] ?: (0 to 0)
                        LazyListState(index, offset)
                    }
                }
            LaunchedEffect(visibleEntry.stateKey, routeScrollState) {
                snapshotFlow { routeScrollState.value }.collect { state.scrollPositions[visibleEntry.stateKey] = it }
            }
            LaunchedEffect(visibleEntry.stateKey, routeLazyListState) {
                snapshotFlow {
                    routeLazyListState.firstVisibleItemIndex to routeLazyListState.firstVisibleItemScrollOffset
                }.collect { state.lazyScrollPositions[visibleEntry.stateKey] = it }
            }
            if (saveRouteState) {
                DisposableEffect(visibleEntry.stateKey, routeScrollState, routeLazyListState) {
                    onDispose {
                        state.scrollPositions[visibleEntry.stateKey] = routeScrollState.value
                        state.lazyScrollPositions[visibleEntry.stateKey] =
                            routeLazyListState.firstVisibleItemIndex to routeLazyListState.firstVisibleItemScrollOffset
                        if (!visibleRoute.isTab() && backStack.none { it.stateKey == visibleEntry.stateKey }) {
                            state.scrollPositions.remove(visibleEntry.stateKey)
                            state.lazyScrollPositions.remove(visibleEntry.stateKey)
                            routeLazyListStates.remove(visibleEntry.stateKey)
                        }
                        routeScrollStates.remove(visibleEntry.stateKey)
                    }
                }
            }
            val routeContent: @Composable () -> Unit = {
                CompositionLocalProvider(
                    LocalRouteScrollState provides routeScrollState,
                    LocalRouteLazyListState provides routeLazyListState,
                ) {
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
                                merchantIconMatcher = merchantIconMatcher,
                                merchantIconMatcherLoadError = merchantIconMatcherLoadError,
                                merchantIconCatalog = merchantIconCatalog,
                                merchantIconCatalogLoadError = merchantIconCatalogLoadError,
                                liveTransactions = liveTransactions,
                                liveAccounts = liveAccounts,
                                liveBudgetMonth = liveBudgetMonth,
                                onSyncNow = syncNow,
                                onSaveTransaction = saveTransaction,
                                onDeleteTransaction = deleteTransaction,
                                onCreateTransaction = createTransaction,
                                onResolveConflict = resolveConflict,
                                onDiscardWrite = discardWrite,
                                onRetryUncertainWrite = retryUncertainWrite,
                                onCheckUncertainWriteStatus = checkUncertainWriteStatus,
                                onForgetDevice = { coroutineScope.launch { mobileSession.forgetDevice() } },
                                onForgetDeviceLocally = { coroutineScope.launch { mobileSession.forgetDeviceLocally() } },
                                onRequestNotificationPermission = onRequestNotificationPermission,
                                onOpenNotificationSettings = onOpenNotificationSettings,
                                notificationPermissionEnabled = notificationPermissionEnabled,
                                onToggleNotificationPermission = onToggleNotificationPermission,
                            )
                            if (visibleRoute == AppRoute.BUDGET) {
                                val transactionActionCompact by
                                    remember(routeScrollState, routeLazyListState) {
                                        derivedStateOf {
                                            routeScrollState.value > 72 ||
                                                routeLazyListState.firstVisibleItemIndex > 0 ||
                                                routeLazyListState.firstVisibleItemScrollOffset > 72
                                        }
                                    }
                                FloatingTransactionAction(
                                    compact = transactionActionCompact,
                                    enabled = !state.readOnlyMode,
                                    onClick = {
                                        if (state.selectedBudgetId.isNotBlank()) {
                                            navigation.newTransaction(null)
                                        } else {
                                            navigation.open(AppRoute.NEW_TRANSACTION)
                                        }
                                    },
                                    modifier =
                                        Modifier
                                            .align(
                                                Alignment.BottomEnd,
                                            ).padding(end = TandemSpacing.large, bottom = TandemSpacing.large),
                                )
                            }
                            val pendingSyncOperation =
                                mobileSession.pendingWrites.firstOrNull {
                                    it.operationId == state.selectedConflictOperationId &&
                                        (it.status == "queued" || (it.status == "sending" && mobileSession.isSyncing))
                                }
                            val showSyncAction =
                                (visibleRoute == AppRoute.SYNC_CENTER && state.selectedBudgetId.isNotBlank()) ||
                                    (visibleRoute == AppRoute.UNCERTAIN_WRITE && pendingSyncOperation != null)
                            if (showSyncAction) {
                                val syncActionCompact by
                                    remember(routeScrollState) {
                                        derivedStateOf { routeScrollState.value > 72 }
                                    }
                                val syncActionEnabled =
                                    !mobileSession.isSyncing &&
                                        (visibleRoute != AppRoute.SYNC_CENTER || mobileSession.isAuthenticated)
                                FloatingSyncAction(
                                    compact = syncActionCompact,
                                    syncing = mobileSession.isSyncing,
                                    enabled = syncActionEnabled,
                                    onClick = syncNow,
                                    modifier =
                                        Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(end = TandemSpacing.large, bottom = TandemSpacing.large),
                                )
                            }
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

@Composable
private fun FloatingTransactionAction(
    compact: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = tandemPalette
    Row(
        modifier =
            modifier
                .animateContentSize(tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTENT_SIZE_MILLIS))
                .height(56.dp)
                .defaultMinSize(minWidth = 56.dp)
                .clip(if (compact) CircleShape else RoundedCornerShape(TandemShapes.large))
                .background(if (enabled) palette.accent else palette.raised)
                .semantics(mergeDescendants = true) {
                    contentDescription = if (enabled) "Add transaction" else "Read-only mode is on"
                }.clickable(enabled = enabled, role = Role.Button, onClickLabel = "Add transaction", onClick = onClick)
                .padding(horizontal = if (compact) TandemSpacing.large else TandemSpacing.extraLarge),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 0.dp else TandemSpacing.small),
    ) {
        Icon(
            Lucide.Plus,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = if (enabled) palette.accentContent else palette.muted,
        )
        AnimatedVisibility(visible = !compact, enter = fadeIn(), exit = fadeOut()) {
            Text(
                "Add transaction",
                color = if (enabled) palette.accentContent else palette.muted,
                fontSize = TandemTypography.body,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun FloatingSyncAction(
    compact: Boolean,
    syncing: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = tandemPalette
    Row(
        modifier =
            modifier
                .animateContentSize(tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTENT_SIZE_MILLIS))
                .height(56.dp)
                .defaultMinSize(minWidth = 56.dp)
                .clip(if (compact) CircleShape else RoundedCornerShape(TandemShapes.large))
                .background(if (enabled) palette.accent else palette.raised)
                .semantics(mergeDescendants = true) { contentDescription = "Sync" }
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Sync", onClick = onClick)
                .padding(horizontal = if (compact) TandemSpacing.large else TandemSpacing.extraLarge),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 0.dp else TandemSpacing.small),
    ) {
        if (syncing) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = if (enabled) palette.accentContent else palette.muted,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(Lucide.CloudSync, contentDescription = null, modifier = Modifier.size(24.dp), tint = palette.accentContent)
        }
        AnimatedVisibility(visible = !compact, enter = fadeIn(), exit = fadeOut()) {
            Text(
                "Sync",
                color = if (enabled) palette.accentContent else palette.muted,
                fontSize = TandemTypography.body,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

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
            AppRoute.NEW_TRANSACTION -> ""
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
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            Icon(
                imageVector = if (route == AppRoute.NEW_TRANSACTION) Lucide.X else Lucide.ChevronLeft,
                contentDescription = null,
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .semantics(mergeDescendants = true) { contentDescription = "Back" }
                        .clickable(role = Role.Button, onClickLabel = "Back", onClick = onBack)
                        .padding(TandemSpacing.medium),
                tint = palette.text,
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
            if (headerTitle.isNotBlank()) {
                Text(
                    text = headerTitle,
                    color = palette.text,
                    fontSize = TandemTypography.periodTitle,
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
                        fontSize = TandemTypography.caption,
                    )
                }
            }
        }
        if (route.isTab()) {
            val cloudTint =
                when (syncStatus) {
                    MainCloudStatus.OFF -> palette.muted
                    MainCloudStatus.SYNCED -> palette.positive
                    MainCloudStatus.SYNCING -> palette.syncing
                    MainCloudStatus.ERROR -> palette.negative
                }
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
                    imageVector =
                        when (syncStatus) {
                            MainCloudStatus.OFF -> Lucide.CloudOff
                            MainCloudStatus.ERROR -> Lucide.CloudAlert
                            else -> Lucide.Cloud
                        },
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
                fontSize = TandemTypography.micro,
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
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    palette.surface,
                ).padding(start = TandemSpacing.small, end = TandemSpacing.small, top = TandemSpacing.small, bottom = TandemSpacing.small),
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
                        .clip(RoundedCornerShape(TandemShapes.medium))
                        .selectable(
                            selected = active,
                            role = Role.Tab,
                            onClick = { selectTab(route) },
                        ).semantics(mergeDescendants = true) {
                            contentDescription = label
                            this.selected = active
                        }.padding(vertical = TandemSpacing.extraSmall),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall),
            ) {
                NavigationGlyph(route, tabColor, iconScale)
                Text(
                    label,
                    color = tabColor,
                    fontSize = TandemTypography.micro,
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
            .size(24.dp)
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
    merchantIconMatcher: MerchantIconMatcherPack?,
    merchantIconMatcherLoadError: String?,
    merchantIconCatalog: MerchantIconCatalog?,
    merchantIconCatalogLoadError: String?,
    liveTransactions: List<TransactionItem>,
    liveAccounts: List<LiveAccountSummary>,
    liveBudgetMonth: LiveBudgetMonthSummary?,
    onSyncNow: () -> Unit,
    onSaveTransaction: ((Boolean) -> Unit) -> Unit,
    onDeleteTransaction: ((Boolean) -> Unit) -> Unit,
    onCreateTransaction: () -> Unit,
    onResolveConflict: (String, Boolean) -> Unit,
    onDiscardWrite: (String) -> Unit,
    onRetryUncertainWrite: (String) -> Unit,
    onCheckUncertainWriteStatus: (String) -> Unit,
    onForgetDevice: () -> Unit,
    onForgetDeviceLocally: () -> Unit,
    onRequestNotificationPermission: (() -> Unit)?,
    onOpenNotificationSettings: (() -> Unit)?,
    notificationPermissionEnabled: Boolean?,
    onToggleNotificationPermission: ((Boolean) -> Unit)?,
) {
    when (route) {
        AppRoute.STARTUP -> StartupScreen()
        AppRoute.WELCOME -> WelcomeScreen(navigation)
        AppRoute.SERVER_ADDRESS -> ServerAddressScreen(state, navigation)
        AppRoute.PAIRING_OPTIONS -> PairingOptionsScreen(navigation)
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
        AppRoute.ACTIVITY_SEARCH -> ActivitySearchScreen(state, mobileSession, navigation, merchantIconMatcher)
        AppRoute.ACTIVITY_FILTER -> ActivitySearchScreen(state, mobileSession, navigation, merchantIconMatcher)
        AppRoute.TRANSACTION_DETAIL -> TransactionDetailScreen(state, mobileSession, navigation, merchantIconMatcher)
        AppRoute.NEW_TRANSACTION, AppRoute.EDIT_TRANSACTION ->
            TransactionFormScreen(route, state, navigation, mobileSession, onSaveTransaction, onDeleteTransaction, onCreateTransaction)
        AppRoute.CHOOSE_ACCOUNT -> {
            val values =
                if (state.selectedBudgetId.isBlank()) {
                    listOf("Checking", "Savings", "Credit card", "Cash").map { SelectorItem(it, it) }
                } else {
                    liveAccounts
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
        AppRoute.CHOOSE_TRANSACTION_TYPE ->
            SelectorScreen(
                listOf(SelectorItem("Spending", "Spending"), SelectorItem("Income", "Income")),
                state.transactionType,
                live = state.selectedBudgetId.isNotBlank(),
            ) { item ->
                state.transactionType = item.label
                navigation.back()
            }
        AppRoute.CHOOSE_DATE -> DatePickerScreen(state, navigation)
        AppRoute.SAVE_RESULT -> SaveResultScreen(state, navigation)
        AppRoute.SYNC_CENTER -> SyncCenterScreen(state, mobileSession, navigation, liveTransactions)
        AppRoute.CONFLICT_INBOX -> ConflictInboxScreen(state, mobileSession, navigation, liveTransactions)
        AppRoute.CONFLICT_REVIEW -> ConflictReviewScreen(state, mobileSession, onResolveConflict, liveTransactions)
        AppRoute.UNCERTAIN_WRITE ->
            UncertainWriteScreen(
                state,
                mobileSession,
                navigation,
                onCheckUncertainWriteStatus,
                onRetryUncertainWrite,
                onDiscardWrite,
                liveTransactions,
            )
        AppRoute.REJECTED_WRITE -> RejectedWriteScreen(state, mobileSession, navigation, onDiscardWrite, liveTransactions)
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
            ServerDeviceScreen(state, deviceName, mobileSession, navigation, onForgetDevice, onForgetDeviceLocally)
        AppRoute.SECURITY_PRIVACY -> SecurityScreen(state, mobileSession)
        AppRoute.NOTIFICATIONS ->
            NotificationsScreen(
                state,
                onRequestNotificationPermission,
                onOpenNotificationSettings,
                notificationPermissionEnabled,
                onToggleNotificationPermission,
            )
        AppRoute.LOCAL_DATA -> LocalDataScreen(state, mobileSession, onSyncNow)
        AppRoute.ABOUT -> AboutScreen(merchantIconMatcherLoadError, navigation)
        AppRoute.MERCHANT_ICON_LICENSES -> MerchantIconLicensesScreen(merchantIconCatalog, merchantIconCatalogLoadError)
    }
}

@Composable
private fun PageColumn(
    verticalSpacing: androidx.compose.ui.unit.Dp = TandemSpacing.large,
    horizontalPadding: androidx.compose.ui.unit.Dp = TandemSpacing.screen,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = LocalRouteScrollState.current ?: rememberScrollState()
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = horizontalPadding, vertical = TandemSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        content = content,
    )
}

@Composable
private fun PageLazyColumn(
    verticalSpacing: androidx.compose.ui.unit.Dp = TandemSpacing.large,
    horizontalPadding: androidx.compose.ui.unit.Dp = TandemSpacing.screen,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    val routeListState = LocalRouteLazyListState.current ?: rememberLazyListState()
    LazyColumn(
        state = routeListState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = TandemSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        content = content,
    )
}

private fun LazyItemScope.tandemListItemModifier(reduceMotion: Boolean): Modifier =
    Modifier.animateItem(
        fadeInSpec = tandemMotionSpec(reduceMotion, TandemMotion.REVEAL_MILLIS),
        placementSpec = tandemMotionSpec(reduceMotion, TandemMotion.CONTENT_SIZE_MILLIS),
        fadeOutSpec = tandemMotionSpec(reduceMotion, TandemMotion.REVEAL_MILLIS),
    )

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
private fun SampleBanner(message: String = "Sample data") {
    val palette = tandemPalette
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(TandemShapes.control),
                ).background(palette.accentSurface)
                .padding(horizontal = TandemSpacing.medium, vertical = TandemSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
    ) {
        Text("●", color = palette.accent, fontSize = TandemTypography.indicator)
        Text(message, color = palette.muted, fontSize = TandemTypography.caption)
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
            Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.medium, vertical = TandemSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
        ) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(palette.warning.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("!", color = palette.warning, fontSize = TandemTypography.label, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall)) {
                Text(
                    title,
                    color = palette.text,
                    fontSize = TandemTypography.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    detail,
                    color = palette.muted,
                    fontSize = TandemTypography.micro,
                    lineHeight = TandemTypography.microLineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                action,
                color = palette.accent,
                fontSize = TandemTypography.micro,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(Lucide.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp), tint = palette.muted)
        }
    }
}

@Composable
private fun Heading(
    title: String,
    subtitle: String? = null,
) {
    val palette = tandemPalette
    Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
        Text(
            title,
            color = palette.text,
            fontSize = TandemTypography.pageTitle,
            lineHeight = TandemTypography.headingLineHeight,
            fontWeight = FontWeight.Bold,
        )
        if (subtitle !=
            null
        ) {
            Text(subtitle, color = palette.muted, fontSize = TandemTypography.body, lineHeight = TandemTypography.bodyLineHeight)
        }
    }
}

@Composable
private fun BodyText(text: String) {
    Text(text, color = tandemPalette.muted, fontSize = TandemTypography.body, lineHeight = TandemTypography.bodyLineHeight)
}

@Composable
private fun AnimatedStatusText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = TandemTypography.body,
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
    Text(
        text,
        color = tandemPalette.muted,
        fontSize = TandemTypography.caption,
        letterSpacing = TandemTypography.captionLetterSpacing,
        fontWeight = FontWeight.SemiBold,
    )
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
        shape = RoundedCornerShape(TandemShapes.large),
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
                .clip(RoundedCornerShape(TandemShapes.medium))
                .background(backgroundColor)
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
                .padding(horizontal = TandemSpacing.medium, vertical = TandemSpacing.medium),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.animateContentSize(tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTENT_SIZE_MILLIS)),
            horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedReveal(visible = loading, animateHeight = false) {
                TandemLoadingIndicator(Modifier.size(17.dp), textColor, 2.dp)
            }
            AnimatedStatusText(
                label,
                textColor,
                fontSize = TandemTypography.action,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(TandemShapes.medium))
                .border(1.dp, borderColor, RoundedCornerShape(TandemShapes.medium))
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
                .padding(horizontal = TandemSpacing.medium, vertical = TandemSpacing.medium),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = textColor,
            fontSize = TandemTypography.action,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AccountActionButtons(
    addEnabled: Boolean,
    reconciliationEnabled: Boolean,
    onAddTransaction: () -> Unit,
    onReconcile: () -> Unit,
) {
    val useFullWidthButtons = shouldStackActionButtons(LocalDensity.current.fontScale)
    if (useFullWidthButtons) {
        Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
            PrimaryButton("Add transaction", enabled = addEnabled, onClick = onAddTransaction)
            SecondaryButton("Reconcile", enabled = reconciliationEnabled, onClick = onReconcile)
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
            PrimaryButton("Add transaction", enabled = addEnabled, modifier = Modifier.weight(1f), onClick = onAddTransaction)
            SecondaryButton("Reconcile", enabled = reconciliationEnabled, modifier = Modifier.weight(1f), onClick = onReconcile)
        }
    }
}

@Composable
private fun CompactActionButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    Box(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(TandemShapes.control))
            .border(1.dp, palette.border, RoundedCornerShape(TandemShapes.control))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = TandemSpacing.small, vertical = TandemSpacing.small),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = palette.text,
            fontSize = TandemTypography.label,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FieldLabel(label: String) {
    Text(label, color = tandemPalette.muted, fontSize = TandemTypography.label, fontWeight = FontWeight.Medium)
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
    large: Boolean = false,
    onFocusedChange: ((Boolean) -> Unit)? = null,
) {
    val palette = tandemPalette
    Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
        FieldLabel(label)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = if (large) 82.dp else 52.dp)
                    .clip(RoundedCornerShape(TandemShapes.medium))
                    .background(palette.surface)
                    .border(1.dp, palette.border, RoundedCornerShape(TandemShapes.medium))
                    .padding(
                        horizontal = if (large) TandemSpacing.screen else TandemSpacing.large,
                        vertical = if (large) TandemSpacing.screen else TandemSpacing.medium,
                    ),
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
                textStyle =
                    TextStyle(
                        color = palette.text,
                        fontSize = if (large) TandemTypography.amount else TandemTypography.action,
                        lineHeight = if (large) TandemTypography.largeInputLineHeight else TandemTypography.bodyLineHeight,
                        fontWeight = if (large) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = textAlign,
                    ),
                cursorBrush = SolidColor(palette.accent),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState -> onFocusedChange?.invoke(focusState.isFocused) }
                        .semantics { contentDescription = label },
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(
                                placeholder,
                                modifier = Modifier.fillMaxWidth(),
                                color = palette.muted,
                                fontSize = if (large) TandemTypography.keypadNumber else TandemTypography.body,
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
private fun TransactionAmountField(
    amount: String,
    amountMinorUnits: Long,
    transactionType: String,
    focusRequester: FocusRequester,
    onShowKeypad: () -> Unit,
) {
    val palette = tandemPalette
    val entered = amountMinorUnits != 0L
    val amountColor =
        when {
            !entered -> palette.text
            transactionType == "Income" -> palette.positive
            else -> palette.negative
        }
    val outline = if (entered) amountColor else palette.border
    val fill = if (entered) amountColor.copy(alpha = 0.08f) else palette.surface
    val display = amount.ifBlank { "0.00" }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp)
                .clip(RoundedCornerShape(TandemShapes.large))
                .background(fill)
                .border(1.5.dp, outline, RoundedCornerShape(TandemShapes.large))
                .clickable(role = Role.Button, onClickLabel = "Enter amount", onClick = onShowKeypad)
                .padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = TextFieldValue(display, selection = TextRange(display.length)),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            textStyle =
                TextStyle(
                    color = if (entered) amountColor else palette.muted,
                    fontSize = TandemTypography.amountField,
                    lineHeight = TandemTypography.amountFieldLineHeight,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
            cursorBrush = SolidColor(amountColor),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { if (it.isFocused) onShowKeypad() }
                    .semantics { contentDescription = "Amount, $display" },
            decorationBox = { innerTextField ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { innerTextField() }
            },
        )
    }
}

@Composable
private fun TransactionTypeChip(
    type: String,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    Row(
        modifier =
            Modifier
                .clip(CircleShape)
                .background(palette.raised)
                .clickable(role = Role.Button, onClickLabel = "Choose transaction type", onClick = onClick)
                .padding(horizontal = TandemSpacing.screen, vertical = TandemSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
    ) {
        Icon(Lucide.Receipt, contentDescription = null, modifier = Modifier.size(20.dp), tint = palette.text)
        Text(type, color = palette.text, fontSize = TandemTypography.body, fontWeight = FontWeight.SemiBold)
        Icon(Lucide.ChevronDown, contentDescription = null, modifier = Modifier.size(19.dp), tint = palette.muted)
    }
}

@Composable
private fun TransactionPickerRow(
    value: String,
    placeholder: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    val label = value.ifBlank { placeholder }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 62.dp)
                .clickable(role = Role.Button, onClickLabel = placeholder, onClick = onClick)
                .semantics { contentDescription = "$placeholder: $label" }
                .padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(TandemShapes.control)).background(palette.raised),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = palette.accent)
        }
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = if (value.isBlank()) palette.muted else palette.text,
            fontSize = TandemTypography.body,
            fontWeight = if (value.isBlank()) FontWeight.Normal else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(Lucide.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp), tint = palette.muted)
    }
}

private enum class AmountPadKeyStyle {
    NUMBER,
    OPERATOR,
    EQUALS,
    DONE,
}

@Composable
private fun AmountPadKey(
    label: String,
    modifier: Modifier = Modifier,
    style: AmountPadKeyStyle = AmountPadKeyStyle.NUMBER,
    accessibilityLabel: String = label,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    val background =
        when (style) {
            AmountPadKeyStyle.NUMBER -> Color.Transparent
            AmountPadKeyStyle.DONE -> palette.accent
            else -> palette.raised
        }
    val color =
        when (style) {
            AmountPadKeyStyle.DONE -> palette.accentContent
            AmountPadKeyStyle.NUMBER -> palette.text
            else -> palette.muted
        }
    val shape =
        when (style) {
            AmountPadKeyStyle.NUMBER -> RoundedCornerShape(TandemShapes.roomy)
            AmountPadKeyStyle.OPERATOR -> CircleShape
            else -> RoundedCornerShape(TandemShapes.capsule)
        }
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .padding(TandemSpacing.xxSmall)
                .clip(shape)
                .background(background)
                .semantics(mergeDescendants = true) { contentDescription = accessibilityLabel }
                .clickable(role = Role.Button, onClickLabel = accessibilityLabel, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = color,
            fontSize = if (style == AmountPadKeyStyle.NUMBER) TandemTypography.keypadNumber else TandemTypography.keypadOperator,
            fontWeight = if (style == AmountPadKeyStyle.DONE) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun TransactionAmountKeypad(
    onDigit: (Int) -> Unit,
    onDelete: () -> Unit,
    onOperator: (String) -> Unit,
    onEquals: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = tandemPalette
    Surface(modifier = modifier.fillMaxWidth().height(AMOUNT_KEYPAD_HEIGHT), color = palette.surface) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
            horizontalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
        ) {
            Column(
                Modifier.weight(3.15f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
            ) {
                listOf(listOf(7, 8, 9), listOf(4, 5, 6), listOf(1, 2, 3)).forEach { row ->
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
                        row.forEach { digit ->
                            AmountPadKey(digit.toString(), Modifier.weight(1f)) { onDigit(digit) }
                        }
                    }
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
                    Spacer(Modifier.weight(1f).fillMaxSize())
                    AmountPadKey("0", Modifier.weight(1f)) { onDigit(0) }
                    AmountPadKey("⌫", Modifier.weight(1f), accessibilityLabel = "Delete last digit", onClick = onDelete)
                }
            }
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
            ) {
                listOf(listOf("×", "÷"), listOf("+", "−")).forEach { row ->
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
                        row.forEach { operator ->
                            AmountPadKey(operator, Modifier.weight(1f), AmountPadKeyStyle.OPERATOR) { onOperator(operator) }
                        }
                    }
                }
                AmountPadKey("=", Modifier.weight(1f), AmountPadKeyStyle.EQUALS, "Calculate", onEquals)
                AmountPadKey("Done", Modifier.weight(1f), AmountPadKeyStyle.DONE, onClick = onDone)
            }
        }
    }
}

private val AMOUNT_KEYPAD_HEIGHT = 252.dp

@Composable
private fun FloatingTransactionSave(
    compact: Boolean,
    enabled: Boolean,
    saved: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = tandemPalette
    Row(
        modifier =
            modifier
                .animateContentSize(tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTENT_SIZE_MILLIS))
                .height(56.dp)
                .clip(if (compact) CircleShape else RoundedCornerShape(TandemShapes.large))
                .background(
                    if (saved) {
                        palette.positive
                    } else if (enabled) {
                        palette.accent
                    } else {
                        palette.raised
                    },
                ).semantics(mergeDescendants = true) {
                    contentDescription = if (saved) "Saved on this device" else "Save transaction"
                }.clickable(enabled = enabled && !saved, role = Role.Button, onClickLabel = "Save transaction", onClick = onClick)
                .padding(horizontal = if (compact) TandemSpacing.large else TandemSpacing.extraLarge),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 0.dp else TandemSpacing.small),
    ) {
        Box(
            Modifier
                .size(23.dp)
                .clip(CircleShape)
                .background(if (saved) Color.Transparent else palette.accentContent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Lucide.Check,
                contentDescription = null,
                modifier = Modifier.size(if (saved) 23.dp else 15.dp),
                tint = if (saved) palette.background else palette.accent,
            )
        }
        AnimatedVisibility(visible = !compact, enter = fadeIn(), exit = fadeOut()) {
            Text(
                if (saved) "Saved" else "Save",
                color = if (saved || enabled) palette.accentContent else palette.muted,
                fontSize = TandemTypography.action,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun FloatingTransactionDeleteAction(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = tandemPalette
    Box(
        modifier =
            modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(palette.negative.copy(alpha = if (enabled) 0.15f else 0.08f))
                .border(1.dp, palette.negative.copy(alpha = if (enabled) 0.75f else 0.32f), CircleShape)
                .semantics(mergeDescendants = true) { contentDescription = "Delete transaction" }
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Delete transaction", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Lucide.Trash2,
            contentDescription = null,
            modifier = Modifier.size(23.dp),
            tint = if (enabled) palette.negative else palette.muted,
        )
    }
}

@Composable
private fun ReadOnlyField(
    label: String,
    value: String,
    leading: String = "",
    leadingVector: ImageVector? = null,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
        FieldLabel(label)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(TandemShapes.medium))
                    .background(palette.surface)
                    .border(1.dp, palette.border, RoundedCornerShape(TandemShapes.medium))
                    .clickable(role = Role.Button, onClickLabel = "$label, $value", onClick = onClick)
                    .padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
        ) {
            if (leadingVector != null) {
                Icon(leadingVector, contentDescription = null, modifier = Modifier.size(30.dp), tint = palette.accent)
            } else if (leading.isNotEmpty()) {
                Text(
                    leading,
                    modifier = Modifier.clearAndSetSemantics {},
                    color = palette.accent,
                    fontSize = TandemTypography.action,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(value, color = palette.text, fontSize = TandemTypography.body, modifier = Modifier.weight(1f))
            Icon(
                Lucide.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(22.dp).clearAndSetSemantics {},
                tint = palette.muted,
            )
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
    val resolvedVector = if (resolvedPainter == null) leadingVector ?: fallbackVector ?: financeIcon(title) ?: Lucide.CircleDot else null
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = if (dense) 56.dp else 64.dp)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(role = Role.Button, onClickLabel = title, onClick = onClick)
                    } else {
                        Modifier
                    },
                ).padding(
                    horizontal = TandemSpacing.large,
                    vertical = if (dense) TandemSpacing.extraSmall + TandemSpacing.xxSmall else TandemSpacing.small + TandemSpacing.xxSmall,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(TandemShapes.medium)).background(palette.raised),
            contentAlignment = Alignment.Center,
        ) {
            if (resolvedPainter != null) {
                Image(
                    painter = resolvedPainter,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp).padding(TandemSpacing.xxSmall),
                    contentScale = ContentScale.Fit,
                )
            } else if (resolvedVector != null) {
                Icon(
                    imageVector = resolvedVector,
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                    tint = palette.accent,
                )
            } else {
                Text(
                    icon,
                    modifier = Modifier.clearAndSetSemantics {},
                    color = palette.accent,
                    fontSize = if (dense) TandemTypography.action else TandemTypography.cardTitle,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
            if (animateChanges) {
                AnimatedStatusText(
                    title,
                    palette.text,
                    fontSize = TandemTypography.body,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    title,
                    color = palette.text,
                    fontSize = TandemTypography.body,
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
                        fontSize = TandemTypography.caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        subtitle,
                        color = palette.muted,
                        fontSize = TandemTypography.caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (trailing != null) {
            val effectiveColor = trailingColor ?: palette.muted
            if (animateChanges) {
                AnimatedStatusText(
                    trailing,
                    effectiveColor,
                    fontSize = TandemTypography.compactBody,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    trailing,
                    color = effectiveColor,
                    fontSize = TandemTypography.compactBody,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else if (onClick != null) {
            Icon(
                Lucide.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp).clearAndSetSemantics {},
                tint = palette.muted,
            )
        }
    }
}

internal fun financeIcon(label: String): ImageVector? {
    val words = labelWords(label)

    if (words == listOf("account") || words == listOf("accounts")) return Lucide.WalletCards

    fun matches(vararg phrases: String): Boolean = phrases.any { containsPhrase(words, it) }

    return when {
        matches("biometric", "fingerprint") -> Lucide.FingerprintPattern
        matches("read-only", "read only", "view only") -> Lucide.Eye
        matches("password") -> Lucide.LockKeyhole
        matches("credential", "security key") -> Lucide.KeyRound
        matches("conflict", "attention needed", "needs attention", "review", "reason", "rejected") -> Lucide.CircleAlert
        matches("queued", "pending", "outbox", "changes", "local edits") -> Lucide.CloudSync
        matches("transaction", "transactions", "purchase", "receipt", "payee", "merchant") -> Lucide.Receipt
        matches("local data", "storage", "cached", "records", "snapshot") -> Lucide.Database
        matches("essentials") -> Lucide.House
        matches("upcoming", "due") -> Lucide.CalendarDays
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
        matches("insurance", "coverage", "policy premium", "security", "privacy") -> Lucide.ShieldCheck
        matches("medical", "healthcare", "doctor", "pharmacy", "dental", "hospital") -> Lucide.HeartPulse
        matches("paycheck", "payroll", "salary", "income") -> Lucide.BriefcaseBusiness
        matches(
            "balance",
            "currency",
            "money",
            "amount",
            "number format",
            "assigned",
            "available",
            "ready to assign",
        ) -> Lucide.WalletCards
        matches("date", "calendar", "start of week", "planning period", "month", "due", "posted") -> Lucide.CalendarDays
        matches("sync", "synchronization", "cloud") -> Lucide.CloudSync
        matches("server", "connection", "api") -> Lucide.Server
        matches("device", "phone", "paired device") -> Lucide.Smartphone
        matches("theme", "appearance", "accent", "color", "background", "motion") -> Lucide.Palette
        matches("layout", "home screen") -> Lucide.LayoutDashboard
        matches("storage", "database", "local data", "cache") -> Lucide.Database
        matches("notification", "reminder", "alert") -> Lucide.Bell
        matches("category", "categories", "group", "budget") -> Lucide.Tags
        matches("about", "help", "information", "project", "library", "license") -> Lucide.Info
        matches("notes", "memo") -> Lucide.NotepadText
        matches("payee", "merchant", "transaction", "purchase", "activity", "history") -> Lucide.Receipt
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
    expanded: Boolean? = null,
    onToggleExpanded: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = tandemPalette
    SurfaceCard(onClick = onClick) {
        Column {
            if (title.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
                        Text(
                            title,
                            color = palette.text,
                            fontSize = TandemTypography.body,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (detail != null) {
                            if (animateDetail) {
                                AnimatedStatusText(
                                    detail,
                                    palette.muted,
                                    fontSize = TandemTypography.caption,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            } else {
                                Text(
                                    detail,
                                    color = palette.muted,
                                    fontSize = TandemTypography.caption,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    if (action != null) {
                        Text(
                            action,
                            color = palette.accent,
                            fontSize = TandemTypography.label,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (onToggleExpanded != null) {
                        val arrowRotation by
                            animateFloatAsState(
                                targetValue = if (expanded == false) -90f else 0f,
                                animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTROL_MILLIS),
                                label = "section-chevron-$title",
                            )
                        Icon(
                            imageVector = Lucide.ChevronDown,
                            contentDescription = if (expanded == false) "Expand $title" else "Collapse $title",
                            modifier =
                                Modifier
                                    .padding(start = TandemSpacing.extraSmall)
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .clickable(role = Role.Button, onClick = onToggleExpanded)
                                    .padding(TandemSpacing.small)
                                    .graphicsLayer { rotationZ = arrowRotation },
                            tint = palette.muted,
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.border.copy(alpha = 0.75f)))
            }
            if (onToggleExpanded == null) {
                content()
            } else {
                AnimatedVisibility(
                    visible = expanded != false,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(content = content)
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    val palette = tandemPalette
    val controlColor = if (enabled) palette.accent else palette.muted
    val thumbOffset by
        animateDpAsState(
            targetValue = if (checked) 18.dp else 0.dp,
            animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTROL_MILLIS),
            label = "toggle-thumb-position",
        )
    val trackColor by
        animateColorAsState(
            targetValue = if (checked && enabled) palette.accent else palette.raised,
            animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
            label = "toggle-track-color",
        )
    val thumbColor by
        animateColorAsState(
            targetValue = if (checked && enabled) palette.accentContent else palette.muted,
            animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
            label = "toggle-thumb-color",
        )
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 66.dp)
                .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
                .padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
            Text(
                title,
                color = if (enabled) palette.text else palette.muted,
                fontSize = TandemTypography.compactBody,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = if (enabled) palette.muted else controlColor,
                    fontSize = TandemTypography.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .width(
                        42.dp,
                    ).height(24.dp)
                    .clip(CircleShape)
                    .background(if (enabled) trackColor else palette.raised)
                    .padding(TandemSpacing.extraSmall),
        ) {
            Box(
                Modifier
                    .offset(x = thumbOffset)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(if (enabled) thumbColor else palette.muted),
            )
        }
    }
}

@Composable
private fun CheckBoxRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val palette = tandemPalette
    val accent = if (enabled) palette.accent else palette.muted
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp)
                .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange)
                .padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
            Text(
                title,
                color = if (enabled) palette.text else palette.muted,
                fontSize = TandemTypography.body,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(subtitle, color = palette.muted, fontSize = TandemTypography.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(TandemShapes.small))
                .background(if (checked) accent else Color.Transparent)
                .border(1.5.dp, accent, RoundedCornerShape(TandemShapes.small))
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Lucide.Check, contentDescription = null, modifier = Modifier.size(17.dp), tint = palette.accentContent)
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
            modifier =
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(
                        minHeight = 64.dp,
                    ).padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
                Text(
                    title,
                    color = palette.text,
                    fontSize = TandemTypography.body,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle !=
                    null
                ) {
                    Text(
                        subtitle,
                        color = palette.muted,
                        fontSize = TandemTypography.caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, selectionColor, CircleShape)
                    .padding(TandemSpacing.extraSmall),
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
                .clip(RoundedCornerShape(TandemShapes.medium))
                .background(palette.raised)
                .padding(TandemSpacing.medium),
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
        verticalAlignment = Alignment.Top,
    ) {
        Text("i", color = palette.accent, fontWeight = FontWeight.Bold, fontSize = TandemTypography.body)
        AnimatedStatusText(
            text,
            palette.muted,
            fontSize = TandemTypography.label,
            lineHeight = TandemTypography.compactLineHeight,
            animateSizeChanges = true,
        )
    }
}

@Composable
private fun InfoBubble(text: String) {
    val palette = tandemPalette
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TandemShapes.medium))
            .background(palette.syncing.copy(alpha = 0.14f))
            .padding(horizontal = TandemSpacing.medium, vertical = TandemSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
    ) {
        Icon(Lucide.Info, contentDescription = null, modifier = Modifier.size(20.dp), tint = palette.syncing)
        Text(text, color = palette.text, fontSize = TandemTypography.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.large),
            verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
            horizontalAlignment = Alignment.Start,
        ) {
            LabelText(label)
            Text(
                amount,
                color = palette.text,
                fontSize = TandemTypography.amount,
                lineHeight = TandemTypography.amountLineHeight,
                fontWeight = FontWeight.Bold,
            )
            Text(supporting, color = palette.muted, fontSize = TandemTypography.label)
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
            .clip(RoundedCornerShape(TandemShapes.pill))
            .background(animatedColor.copy(alpha = 0.13f))
            .padding(horizontal = TandemSpacing.small, vertical = TandemSpacing.extraSmall),
    ) {
        AnimatedStatusText(label, color, fontSize = TandemTypography.micro, fontWeight = FontWeight.SemiBold, maxLines = 1)
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
        modifier = Modifier.fillMaxSize().padding(TandemSpacing.extraLarge),
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
                Text("Opening your budget", color = palette.muted, fontSize = TandemTypography.body)
            }
        }
    }
}

@Composable
private fun WelcomeScreen(navigation: AppNavigation) {
    val palette = tandemPalette
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState(),
                ).padding(horizontal = TandemSpacing.extraLarge, vertical = TandemSpacing.extraLarge),
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.large),
    ) {
        Spacer(Modifier.height(14.dp))
        TandemBrandMark(Modifier.size(72.dp))
        Heading("Budget and account overview", "Plan spending, review accounts, and follow recent changes.")
        SurfaceCard {
            Column(Modifier.fillMaxWidth().padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.large)) {
                Text(
                    "Tandem saves a copy on this device. Actual Budget remains the source of truth.",
                    color = palette.text,
                    fontSize = TandemTypography.action,
                    lineHeight = TandemTypography.actionLineHeight,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Spacer(Modifier.weight(1f, fill = false))
        PrimaryButton("Get started") { navigation.open(AppRoute.SERVER_ADDRESS) }
        SecondaryButton("View sample budget") { navigation.selectTab(AppRoute.BUDGET) }
        PreviewNote("Names and amounts are sample data. This screen isn’t connected to a server.")
    }
}

@Composable
private fun ServerAddressScreen(
    state: AppState,
    navigation: AppNavigation,
) {
    PageColumn {
        Heading("Server address")
        TextFieldCard("Server URL", state.serverUrl, { state.serverUrl = it }, "https://budget.example.com")
        InfoBubble("Tandem uses the budget password only to unlock encrypted budgets.")
        if (serverAddressMayBeInsecure(state.serverUrl)) {
            val palette = tandemPalette
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(TandemShapes.medium))
                    .background(palette.warning.copy(alpha = 0.12f))
                    .border(1.dp, palette.warning.copy(alpha = 0.38f), RoundedCornerShape(TandemShapes.medium))
                    .padding(TandemSpacing.large),
                horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Lucide.CircleAlert, contentDescription = null, modifier = Modifier.size(23.dp), tint = palette.warning)
                Text(
                    "This address may not be secure on a public network. Use HTTPS over the internet.",
                    color = palette.text,
                    fontSize = TandemTypography.compactBody,
                    lineHeight = TandemTypography.bodyLineHeight,
                )
            }
        }
        PrimaryButton("Continue", enabled = state.serverUrl.isNotBlank()) { navigation.open(AppRoute.PAIRING_OPTIONS) }
    }
}

internal fun serverAddressMayBeInsecure(value: String): Boolean {
    val address = value.trim().lowercase()
    if (address.isBlank() || address.startsWith("https://")) return false
    val hasHttpScheme = address.startsWith("http://")
    val authority =
        address
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
    val host =
        (if (authority.startsWith("[")) authority.substringAfter('[').substringBefore(']') else authority.substringBefore(':'))
            .removePrefix("www.")
            .trim()
    if (host.isBlank()) return false
    if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local") || host.endsWith(".lan") || host.endsWith(".home")) {
        return false
    }
    val octets = host.split('.').mapNotNull(String::toIntOrNull)
    if (octets.size == 4 && octets.all { it in 0..255 }) {
        val (first, second) = octets
        val isPrivate =
            first == 10 ||
                (first == 172 && second in 16..31) ||
                (first == 192 && second == 168) ||
                first == 127 ||
                (first == 169 && second == 254)
        return hasHttpScheme && !isPrivate
    }
    if (host == "::1" || host.startsWith("fe80:") || host.startsWith("fc") || host.startsWith("fd")) return false
    return hasHttpScheme || host.startsWith("www.") || host.contains('.')
}

@Composable
private fun PairingOptionsScreen(navigation: AppNavigation) {
    PageColumn {
        Heading("Pair this device")
        PrimaryButton("Enter pairing code") { navigation.open(AppRoute.PAIRING_CODE) }
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
        Heading("Enter pairing code")
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
        InfoBubble("This one-time code expires after five minutes. Tandem doesn’t save it or your budget password.")
        mobileSession.pairingError?.let { error ->
            Text(
                error,
                color = tandemPalette.warning,
                fontSize = TandemTypography.compactBody,
                lineHeight = TandemTypography.bodyLineHeight,
            )
        }
        mobileSession.credentialStorageError?.let { error ->
            Text(
                error,
                color = tandemPalette.warning,
                fontSize = TandemTypography.compactBody,
                lineHeight = TandemTypography.bodyLineHeight,
            )
            SecondaryButton(
                if (mobileSession.isRestoringStoredSession) "Checking storage" else "Retry saved pairing",
                enabled = !mobileSession.isRestoringStoredSession,
                onClick = onRetryStoredSession,
            )
        }
        PrimaryButton(
            if (mobileSession.isPairing) "Pairing" else "Pair this device",
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
            Text("✓", color = palette.accent, fontSize = TandemTypography.heroTitle, fontWeight = FontWeight.Bold)
        }
        Heading("Device paired")
        mobileSession.pairedDevice?.let { device ->
            SurfaceCard {
                Column(Modifier.padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
                    LabelText("Device access")
                    BodyText(
                        "This device can open ${device.budgetIds.size} budget${if (device.budgetIds.size == 1) "" else "s"}.",
                    )
                }
            }
            InfoBubble("Pairing codes and budget passwords are never saved.")
        }
        mobileSession.budgetError?.let { error ->
            Text(error, color = palette.warning, fontSize = TandemTypography.compactBody, lineHeight = TandemTypography.bodyLineHeight)
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
                "Choose a budget"
            },
            if (usingCachedCatalog) {
                "Open a budget already saved on this phone. You can sync again when the server is available."
            } else if (hasCachedOnlyBudgets) {
                "Choose a server budget or open one of the saved copies on this phone."
            } else {
                "Choose a budget this device can open."
            },
        )
        when {
            usingCachedCatalog || (mobileSession.budgets.isEmpty() && cachedCatalog.isNotEmpty()) -> {
                BodyText("The server budget list is unavailable. Saved copies stay on this phone.")
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
            mobileSession.isLoadingBudgets -> BodyText("Loading budgets")
            mobileSession.budgets.isEmpty() && mobileSession.isAuthenticated -> {
                mobileSession.budgetError?.let { error ->
                    Text(
                        error,
                        color = tandemPalette.warning,
                        fontSize = TandemTypography.compactBody,
                        lineHeight = TandemTypography.bodyLineHeight,
                    )
                } ?: BodyText("No budgets are available to this paired device.")
                SecondaryButton("Retry loading budgets", onClick = onRefreshBudgets)
            }
            mobileSession.budgets.isEmpty() -> {
                SurfaceCard {
                    Column(Modifier.padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        LabelText("Pair this device first")
                        BodyText("The budget list comes from the server after a device is paired.")
                    }
                }
                SecondaryButton("Pair a device") { navigation.open(AppRoute.SERVER_ADDRESS) }
            }
        }
        mobileSession.budgetError?.takeIf { selectableBudgets.isNotEmpty() }?.let { error ->
            Text(error, color = tandemPalette.warning, fontSize = TandemTypography.label)
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
        Heading("Unlock budget")
        TextFieldCard(
            "Budget password",
            state.encryptedPassword,
            { state.encryptedPassword = it },
            "Enter password",
            isSecret = true,
        )
        InfoBubble("Tandem uses this password only to unlock the encrypted budget. It’s cleared after the first sync and never saved.")
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
        Heading("First sync")
        SurfaceCard {
            Column(Modifier.padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.large)) {
                listOf("Connecting", "Downloading budget", "Saving on this device").forEachIndexed { index, title ->
                    val complete = mobileSession.syncComplete || (!mobileSession.isSyncing && mobileSession.syncError != null && index == 0)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
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
                        if (mobileSession.isSyncing && index == 0 && !complete) {
                            TandemLoadingIndicator(Modifier.size(20.dp), color, 2.dp)
                        } else {
                            Badge(if (complete) "✓" else "${index + 1}", color)
                        }
                        Text(title, color = palette.text, fontSize = TandemTypography.compactBody)
                    }
                }
            }
        }
        val syncMessage =
            when {
                mobileSession.isSyncing && mobileSession.syncError != null ->
                    "Retrying · ${mobileSession.syncError}"
                mobileSession.isSyncing -> "Syncing the budget."
                mobileSession.syncComplete ->
                    "${mobileSession.syncedTransactionCount} transactions saved on this device."
                mobileSession.syncError != null -> mobileSession.syncError.orEmpty()
                else -> "Preparing budget."
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
            fontSize = TandemTypography.compactBody,
            lineHeight = TandemTypography.bodyLineHeight,
            animateSizeChanges = true,
        )
        AnimatedReveal(visible = mobileSession.syncError != null && !mobileSession.isSyncing) {
            Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                SecondaryButton("Retry sync", onClick = onRetrySync)
                if (mobileSession.cachedSnapshotBudgetId == state.selectedBudgetId && mobileSession.hasCachedSnapshot) {
                    BodyText("The last saved copy is still available on this phone.")
                    SecondaryButton("Open saved copy") {
                        selectLatestCachedMonth(state, mobileSession.budgetMonthIds, mobileSession.transactionRecords)
                        navigation.selectTab(AppRoute.BUDGET)
                    }
                }
            }
        }
        PrimaryButton("Done", enabled = mobileSession.syncComplete) {
            selectLatestCachedMonth(state, mobileSession.budgetMonthIds, mobileSession.transactionRecords)
            navigation.selectTab(AppRoute.BUDGET)
        }
    }
}

private fun selectLatestCachedMonth(
    state: AppState,
    budgetMonthIds: List<String>,
    transactionRecords: List<BudgetRecordEntity>,
) {
    val cachedMonthIds =
        budgetMonthIds
            .mapNotNull { monthId ->
                val parts = monthId.split('-')
                if (parts.size != 2) return@mapNotNull null
                val year = parts[0].toIntOrNull() ?: return@mapNotNull null
                val month = parts[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return@mapNotNull null
                if (year !in 1..9999) return@mapNotNull null
                monthId
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
    val essentialsFirst = state.homeLayout == "Essentials"
    if (state.selectedBudgetId.isNotBlank()) {
        PageLazyColumn(verticalSpacing = if (compact) 10.dp else 16.dp, horizontalPadding = if (compact) 16.dp else 20.dp) {
            val conflictCount = mobileSession.pendingWrites.count { it.status == "conflict" }
            item(key = "budget-conflict-alert") {
                AnimatedReveal(visible = state.conflictReviewAlerts && conflictCount > 0) {
                    AttentionBanner(
                        "$conflictCount change${if (conflictCount == 1) "" else "s"} need${if (conflictCount == 1) "s" else ""} review",
                        "Choose which version of the edit to keep.",
                        "Review changes",
                    ) { navigation.open(AppRoute.CONFLICT_INBOX) }
                }
            }
            val rejectedCount = mobileSession.pendingWrites.count { it.status == "rejected" }
            item(key = "budget-rejected-alert") {
                AnimatedReveal(visible = state.rejectedEditAlerts && rejectedCount > 0) {
                    AttentionBanner(
                        "$rejectedCount edit${if (rejectedCount == 1) "" else "s"} not applied",
                        "Review the edit.",
                        "Review edits",
                    ) { navigation.open(AppRoute.REJECTED_WRITE) }
                }
            }
            item(key = "budget-overview-title") {
                Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
                    Text("Budget overview", color = palette.text, fontSize = TandemTypography.pageTitle, fontWeight = FontWeight.Bold)
                    Text(
                        "Review the synced plan and recent activity for the selected month.",
                        color = palette.muted,
                        fontSize = TandemTypography.compactBody,
                    )
                }
            }
            item(key = "budget-month-picker") {
                SurfaceCard(onClick = { navigation.open(AppRoute.MONTH_PICKER) }) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            LabelText("Planning period")
                            Spacer(Modifier.height(4.dp))
                            Text(
                                state.selectedMonth,
                                color = palette.text,
                                fontSize = TandemTypography.action,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Icon(
                            Lucide.ChevronDown,
                            contentDescription = "Choose month",
                            modifier = Modifier.size(22.dp),
                            tint = palette.accent,
                        )
                    }
                }
            }
            if (liveBudgetMonth == null) {
                item(key = "budget-month-empty") {
                    SurfaceCard {
                        Column(
                            Modifier.fillMaxWidth().padding(TandemSpacing.large),
                            verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                        ) {
                            Text("No plan for this month is cached", color = palette.text, fontWeight = FontWeight.SemiBold)
                            BodyText("Sync a month available in Actual, or choose a different planning period.")
                        }
                    }
                }
            } else {
                val summaryKey = "live:${state.selectedBudgetId}:summary:${state.selectedMonthId}"
                item(key = summaryKey) {
                    SectionCard(
                        "${state.selectedMonth} summary",
                        "${liveBudgetMonth.groups.sumOf { it.categories.size }} categories",
                        state.formatCurrency(liveBudgetMonth.toBudgetMinor ?: 0),
                        expanded = !state.isBudgetSectionCollapsed(summaryKey),
                        onToggleExpanded = { state.toggleBudgetSection(summaryKey) },
                    ) {
                        ListRow(
                            "¤",
                            "Ready to assign",
                            state.selectedMonth,
                            state.formatCurrency(liveBudgetMonth.toBudgetMinor ?: 0),
                            leadingVector = Lucide.WalletCards,
                        )
                    }
                }
                val pinnedCategories =
                    liveBudgetMonth.groups.flatMap { group ->
                        group.categories.filter { state.isCategoryPinned(it.id) }.map { group to it }
                    }
                if (pinnedCategories.isNotEmpty()) {
                    val pinnedSectionKey = "live:${state.selectedBudgetId}:pinned"
                    val pinnedExpanded = !state.isBudgetSectionCollapsed(pinnedSectionKey)
                    item(key = "$pinnedSectionKey:header") {
                        SectionCard(
                            "Pinned",
                            "${pinnedCategories.size} categories",
                            expanded = pinnedExpanded,
                            onToggleExpanded = { state.toggleBudgetSection(pinnedSectionKey) },
                        ) {}
                    }
                    items(pinnedCategories, key = { "pinned:${it.second.id}" }) { (group, category) ->
                        AnimatedVisibility(
                            modifier = tandemListItemModifier(tandemReduceMotion),
                            visible = pinnedExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            SurfaceCard {
                                ListRow(
                                    category.name.take(1).uppercase(),
                                    category.name,
                                    group.name,
                                    state.formatCurrency(category.balanceMinor),
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
                    val groupSectionKey = "live:${state.selectedBudgetId}:group:${group.id}"
                    val groupExpanded = !state.isBudgetSectionCollapsed(groupSectionKey)
                    item(key = "$groupSectionKey:header") {
                        SectionCard(
                            group.name,
                            "${state.formatCurrency(group.assignedMinor)} assigned · ${group.categories.size} categories",
                            state.formatCurrency(group.balanceMinor),
                            expanded = groupExpanded,
                            onToggleExpanded = { state.toggleBudgetSection(groupSectionKey) },
                            onClick = {
                                state.selectedCategoryGroupId = group.id
                                navigation.open(AppRoute.CATEGORY_GROUP)
                            },
                        ) {}
                    }
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
                    items(displayCategories, key = { "$groupSectionKey:${it.id}" }) { category ->
                        AnimatedVisibility(
                            modifier = tandemListItemModifier(tandemReduceMotion),
                            visible = groupExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            SurfaceCard {
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
            }
            if (liveTransactions.isNotEmpty()) {
                val recentSectionKey = "live:${state.selectedBudgetId}:recent-activity"
                val recentExpanded = !state.isBudgetSectionCollapsed(recentSectionKey)
                item(key = "$recentSectionKey:header") {
                    SectionCard(
                        "Recent activity",
                        "${liveTransactions.size} synced transactions",
                        "See all",
                        onClick = { navigation.selectTab(AppRoute.ACTIVITY) },
                        expanded = recentExpanded,
                        onToggleExpanded = { state.toggleBudgetSection(recentSectionKey) },
                    ) {}
                }
                items(liveTransactions.take(3), key = { "recent:${it.recordId}" }) { item ->
                    AnimatedVisibility(
                        modifier = tandemListItemModifier(tandemReduceMotion),
                        visible = recentExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        SurfaceCard {
                            TransactionRow(item) { openTransaction(state, navigation, item, isLive = true) }
                        }
                    }
                }
            }
            item(key = "budget-bottom-spacer") { Spacer(Modifier.height(72.dp)) }
        }
        return
    }
    PageColumn(verticalSpacing = if (compact) 10.dp else 16.dp, horizontalPadding = if (compact) 16.dp else 20.dp) {
        SampleBanner(
            if (liveTransactions.isNotEmpty()) {
                "Categories use sample data; activity is synced"
            } else {
                "Categories use sample data"
            },
        )
        Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
            Text("Budget overview", color = palette.text, fontSize = TandemTypography.pageTitle, fontWeight = FontWeight.Bold)
            Text(
                "Review category balances and spending for the selected month.",
                color = palette.muted,
                fontSize = TandemTypography.compactBody,
            )
        }
        SurfaceCard(onClick = { navigation.open(AppRoute.MONTH_PICKER) }) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    LabelText("Planning period")
                    Spacer(Modifier.height(4.dp))
                    Text(state.selectedMonth, color = palette.text, fontSize = TandemTypography.action, fontWeight = FontWeight.SemiBold)
                }
                Icon(Lucide.ChevronDown, contentDescription = "Choose month", modifier = Modifier.size(22.dp), tint = palette.accent)
            }
        }
        val summaryKey = "summary:${state.selectedMonthId}"
        SectionCard(
            "${state.selectedMonth} summary",
            "Ready to assign",
            state.formatCurrency(124_000),
            expanded = !state.isBudgetSectionCollapsed(summaryKey),
            onToggleExpanded = { state.toggleBudgetSection(summaryKey) },
        ) {
            ListRow(
                "¤",
                "Available this month",
                "On track",
                state.formatCurrency(124_000),
                palette.positive,
                leadingVector = Lucide.WalletCards,
            )
        }
        if (essentialsFirst) UpcomingBillsCard(state, navigation)
        SectionCard(
            "Essentials",
            "${state.formatCurrency(232_000)} assigned · 3 categories",
            state.formatCurrency(232_000),
            onClick = { navigation.open(AppRoute.CATEGORY_GROUP) },
            expanded = !state.isBudgetSectionCollapsed("sample:essentials"),
            onToggleExpanded = { state.toggleBudgetSection("sample:essentials") },
        ) {
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
            expanded = !state.isBudgetSectionCollapsed("sample:everyday"),
            onToggleExpanded = { state.toggleBudgetSection("sample:everyday") },
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
        SectionCard(
            "Recent activity",
            "Latest budget changes",
            "See all",
            onClick = { navigation.selectTab(AppRoute.ACTIVITY) },
            expanded = !state.isBudgetSectionCollapsed("sample:recent-activity"),
            onToggleExpanded = { state.toggleBudgetSection("sample:recent-activity") },
        ) {
            (liveTransactions.takeIf { it.isNotEmpty() } ?: previewTransactions).take(2).forEachIndexed { index, item ->
                if (index > 0) DividerLine()
                TransactionRow(item) { openTransaction(state, navigation, item, liveTransactions.isNotEmpty()) }
            }
        }
        SurfaceCard(onClick = { navigation.open(AppRoute.SYNC_CENTER) }) {
            Row(
                Modifier.fillMaxWidth().padding(TandemSpacing.large),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
            ) {
                Badge("✓", palette.positive)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "All changes accounted for",
                        color = palette.text,
                        fontSize = TandemTypography.compactBody,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (liveTransactions.isNotEmpty()) {
                            "${liveTransactions.size} recent Actual transactions cached"
                        } else {
                            "Sample data · nothing has synced"
                        },
                        color = palette.muted,
                        fontSize = TandemTypography.caption,
                    )
                }
                Icon(Lucide.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp), tint = palette.muted)
            }
        }
        Spacer(Modifier.height(72.dp))
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
        expanded = !state.isBudgetSectionCollapsed("sample:upcoming"),
        onToggleExpanded = { state.toggleBudgetSection("sample:upcoming") },
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
            modifier = Modifier.padding(start = TandemSpacing.large, top = TandemSpacing.medium),
            color = palette.muted,
            fontSize = TandemTypography.micro,
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
    state.selectedTransactionNotes = transaction.notes
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
        Heading("Choose a month")
        var selectedYear by remember {
            mutableStateOf(state.selectedMonthId.substringBefore('-').toIntOrNull() ?: 2026)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Lucide.ChevronLeft,
                contentDescription = "Previous year",
                modifier =
                    Modifier
                        .size(44.dp)
                        .semantics { contentDescription = "Previous year" }
                        .clickable(role = Role.Button, onClickLabel = "Previous year") {
                            selectedYear -= 1
                        }.padding(TandemSpacing.medium),
                tint = tandemPalette.text,
            )
            Text(selectedYear.toString(), color = tandemPalette.text, fontSize = TandemTypography.periodTitle, fontWeight = FontWeight.Bold)
            Icon(
                Lucide.ChevronRight,
                contentDescription = "Next year",
                modifier =
                    Modifier
                        .size(44.dp)
                        .semantics { contentDescription = "Next year" }
                        .clickable(role = Role.Button, onClickLabel = "Next year") {
                            selectedYear += 1
                        }.padding(TandemSpacing.medium),
                tint = tandemPalette.text,
            )
        }
        val months = (1..12).map { month -> month to formatShortMonth(selectedYear, month) }
        months.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                row.forEach { (monthNumber, monthLabel) ->
                    val full = formatMonth(selectedYear, monthNumber)
                    val chosen = state.selectedMonth == full
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(72.dp)
                                .clip(RoundedCornerShape(TandemShapes.medium))
                                .background(if (chosen) tandemPalette.accentSurface else tandemPalette.surface)
                                .border(
                                    1.dp,
                                    if (chosen) tandemPalette.accent else tandemPalette.border,
                                    RoundedCornerShape(TandemShapes.medium),
                                ).semantics { contentDescription = full }
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
                            fontSize = TandemTypography.body,
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
        PageLazyColumn {
            if (group == null) {
                item(key = "category-group-empty-title") { Heading("Category group", state.selectedMonth) }
                item(key = "category-group-empty-message") { BodyText("No synced category group is available for this month.") }
            } else {
                item(key = "category-group-title:${group.id}") { Heading(group.name, state.selectedMonth) }
                item(key = "category-group-summary:${group.id}") {
                    AmountHero(
                        "Assigned this month",
                        state.formatCurrency(group.assignedMinor),
                        "${state.formatCurrency(group.balanceMinor)} available across ${group.categories.size} categories",
                    )
                }
                item(key = "category-group-list-title:${group.id}") {
                    SectionCard("Categories", "${group.categories.size} synced categories", "") {}
                }
                items(group.categories, key = { "category-group:${group.id}:${it.id}" }) { category ->
                    SurfaceCard(modifier = tandemListItemModifier(tandemReduceMotion)) {
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
            if (category == null) {
                Heading("Category details", state.selectedMonth)
                BodyText("This category isn’t in the saved plan for this month.")
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
                    ListRow(
                        "",
                        "Activity",
                        "${state.selectedMonth} · ${state.formatCurrency(kotlin.math.abs(category.activityMinor))}",
                        leadingVector = Lucide.Receipt,
                        onClick = { openCategoryActivity(state, navigation, category.id, category.name) },
                    )
                    DividerLine()
                    ListRow("=", "Available", "Assigned plus activity", state.formatCurrency(category.balanceMinor))
                }
                SecondaryButton(
                    if (state.isCategoryPinned(category.id)) "Unpin" else "Pin",
                ) { state.togglePinnedCategory(category.id) }
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
        PrimaryButton("View activity this month") { openCategoryActivity(state, navigation, "Personal", "Personal") }
        PreviewNote("This category uses sample data and is read-only. The app keeps a local budget copy for offline use.")
    }
}

private fun openCategoryActivity(
    state: AppState,
    navigation: AppNavigation,
    categoryId: String,
    categoryName: String,
) {
    state.activitySearchQuery = ""
    state.activitySearchFilters =
        listOf(
            ActivitySearchFilter(ActivitySearchField.CATEGORY, categoryId.ifBlank { categoryName }, "Category: $categoryName"),
            ActivitySearchFilter(ActivitySearchField.DATE_MONTH, state.selectedMonthId, "Date: ${state.selectedMonth}"),
        )
    navigation.open(AppRoute.ACTIVITY_SEARCH)
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
        PageLazyColumn {
            item(key = "accounts-title") { Heading("Balances", "Synced account balances from Actual.") }
            item(key = "accounts-summary") {
                AmountHero("Total on budget", state.formatCurrency(totalOnBudget), "Across ${onBudgetAccounts.size} on-budget accounts")
            }
            if (liveAccounts.isEmpty()) {
                item(key = "accounts-empty") {
                    SurfaceCard {
                        Column(
                            Modifier.fillMaxWidth().padding(TandemSpacing.large),
                            verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                        ) {
                            Text("No accounts are cached", color = tandemPalette.text, fontWeight = FontWeight.SemiBold)
                            BodyText("Sync this budget to load its Actual account list and balances.")
                        }
                    }
                }
            } else {
                groupedAccounts.forEach { (groupName, accounts) ->
                    item(key = "account-group:$groupName") {
                        SectionCard(groupName, "${accounts.size} Actual accounts", "") {}
                    }
                    items(accounts, key = { "account:${it.id}" }) { account ->
                        SurfaceCard(modifier = tandemListItemModifier(tandemReduceMotion)) {
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
            if (account == null) {
                Heading("Account details", "This account isn’t saved on this device.")
            } else {
                AmountHero(
                    "Available balance",
                    account.balanceMinor?.let { state.formatCurrency(it) } ?: "Unavailable",
                    "${account.name} · ${if (account.offBudget) "off budget" else "on budget"}",
                )
                AccountActionButtons(
                    addEnabled = !account.offBudget && !state.readOnlyMode,
                    reconciliationEnabled = false,
                    onAddTransaction = { navigation.newTransaction(account.id) },
                    onReconcile = {},
                )
                if (accountActivity.isEmpty()) {
                    SurfaceCard {
                        Text(
                            "No recent activity for this account.",
                            modifier = Modifier.padding(TandemSpacing.large),
                            color = tandemPalette.muted,
                        )
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
        AccountActionButtons(
            addEnabled = !account.offBudget && !state.readOnlyMode,
            reconciliationEnabled = true,
            onAddTransaction = { navigation.open(AppRoute.NEW_TRANSACTION) },
            onReconcile = { navigation.open(AppRoute.RECONCILIATION) },
        )
        if (accountActivity.isEmpty()) {
            SurfaceCard {
                Text("No recent activity for this account.", modifier = Modifier.padding(TandemSpacing.large), color = tandemPalette.muted)
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
                BodyText("This account isn’t saved on this device.")
            } else {
                SectionCard("Account", "Read-only Actual data") {
                    ListRow("A", "Name", "Account name", account.name)
                    DividerLine()
                    ListRow("¤", "Balance", "Current Actual balance", account.balanceMinor?.let(::formatCurrency) ?: "Unavailable")
                    DividerLine()
                    ListRow("◷", "Budget status", "Account scope", if (account.offBudget) "Off budget" else "On budget")
                }
                PreviewNote("Account names and settings can only be changed in Actual.")
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
            "Account changes and reconciliation aren’t available in this sample view.",
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
            "This sample balance isn’t verified. No statement or transaction will be changed.",
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
    PageLazyColumn {
        if (!isLiveBudget) item(key = "activity-preview-banner") { SampleBanner() }
        item(key = "activity-search-entry") {
            SurfaceCard(onClick = { navigation.open(AppRoute.ACTIVITY_SEARCH) }) {
                Row(
                    Modifier.fillMaxWidth().padding(TandemSpacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                ) {
                    Icon(Lucide.Search, contentDescription = null, modifier = Modifier.size(22.dp), tint = tandemPalette.accent)
                    Text(
                        "Search transactions",
                        color = tandemPalette.muted,
                        fontSize = TandemTypography.compactBody,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Lucide.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp), tint = tandemPalette.muted)
                }
            }
        }
        if (isLiveBudget) {
            if (liveTransactions.isEmpty()) {
                item(key = "activity-empty") {
                    SectionCard("Recent transactions", "Newest first") {
                        BodyText("No transactions are saved on this device yet. Sync the budget to refresh.")
                    }
                }
            } else {
                item(key = "activity-recent-title") { SectionCard("Recent transactions", "Newest first") {} }
                items(
                    liveTransactions,
                    key = { "activity:${it.recordId ?: "${it.dateLabel}:${it.payee}:${it.amountMinorUnits}"}" },
                ) { item ->
                    SurfaceCard(modifier = tandemListItemModifier(tandemReduceMotion)) {
                        TransactionRow(item) { openTransaction(state, navigation, item, isLive = true) }
                    }
                }
            }
        } else {
            item(key = "activity-sample-title") { SectionCard("Recent transactions", "${previewTransactions.size} sample entries") {} }
            items(previewTransactions, key = { "sample:${it.recordId ?: "${it.dateLabel}:${it.payee}:${it.amountMinorUnits}"}" }) { item ->
                SurfaceCard {
                    TransactionRow(item) { openTransaction(state, navigation, item, isLive = false) }
                }
            }
        }
        if (!isLiveBudget) item(key = "activity-sample-note") { PreviewNote("Transactions use sample data.") }
    }
}

private fun TransactionItem.toActivitySearchRecord(): ActivitySearchRecord =
    ActivitySearchRecord(
        payee = payee,
        payeeId = payeeId,
        account = account,
        accountId = accountId,
        category = category,
        categoryId = categoryId,
        notes = notes,
        dateIso = dateIso,
        dateLabel = dateLabel,
        amountMinorUnits = amountMinorUnits,
        amountText = formatSignedCurrency(amountMinorUnits, displaySettings),
        clearedState = clearedState,
        approved = approved,
        matched = matched,
        transfer = isTransfer,
    )

@Composable
private fun ActivitySearchScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    merchantIconMatcher: MerchantIconMatcherPack?,
) {
    var search by rememberSaveable { mutableStateOf(state.activitySearchQuery) }
    val isLiveBudget = state.selectedBudgetId.isNotBlank()
    LaunchedEffect(state.selectedBudgetId, mobileSession) {
        if (isLiveBudget) mobileSession.observeActivitySearchRecords(state.selectedBudgetId)
    }
    var searchTransactions by remember { mutableStateOf(previewTransactions) }
    var searchableRecords by remember { mutableStateOf(previewTransactions.map(TransactionItem::toActivitySearchRecord)) }
    var loadedSearchBudgetId by remember { mutableStateOf("") }
    var isBuildingSearchRecords by remember { mutableStateOf(false) }
    LaunchedEffect(
        state.selectedBudgetId,
        mobileSession.activitySearchRecordsBudgetId,
        mobileSession.activitySearchRecords,
        mobileSession.accountRecords,
        mobileSession.payeeRecords,
        mobileSession.categoryRecords,
        mobileSession.budgetDisplaySettings,
        merchantIconMatcher,
    ) {
        if (!isLiveBudget) {
            searchTransactions = previewTransactions
            searchableRecords = previewTransactions.map(TransactionItem::toActivitySearchRecord)
            loadedSearchBudgetId = ""
            isBuildingSearchRecords = false
        } else if (mobileSession.activitySearchRecordsBudgetId != state.selectedBudgetId) {
            searchTransactions = emptyList()
            searchableRecords = emptyList()
            loadedSearchBudgetId = ""
            isBuildingSearchRecords = false
        } else {
            isBuildingSearchRecords = true
            val records = mobileSession.activitySearchRecords
            val accountRecords = mobileSession.accountRecords
            val payeeRecords = mobileSession.payeeRecords
            val categoryRecords = mobileSession.categoryRecords
            val displaySettings = mobileSession.budgetDisplaySettings
            val items =
                withContext(Dispatchers.Default) {
                    buildLiveTransactionItems(
                        records = records,
                        accountRecords = accountRecords,
                        payeeRecords = payeeRecords,
                        categoryRecords = categoryRecords,
                        displaySettings = displaySettings,
                        merchantIconMatcher = merchantIconMatcher,
                    )
                }
            val indexed = withContext(Dispatchers.Default) { items.map(TransactionItem::toActivitySearchRecord) }
            searchTransactions = items
            searchableRecords = indexed
            loadedSearchBudgetId = state.selectedBudgetId
            isBuildingSearchRecords = false
        }
    }
    val filters = state.activitySearchFilters
    val quickFilters =
        remember(searchableRecords) {
            buildList {
                if (searchableRecords.any { it.clearedState != null }) {
                    add(ActivitySearchFilter(ActivitySearchField.CLEARED, "", "Cleared"))
                    add(ActivitySearchFilter(ActivitySearchField.UNCLEARED, "", "Uncleared"))
                    add(ActivitySearchFilter(ActivitySearchField.RECONCILED, "", "Reconciled"))
                    add(ActivitySearchFilter(ActivitySearchField.UNRECONCILED, "", "Unreconciled"))
                }
                if (searchableRecords.any { it.approved != null }) {
                    add(ActivitySearchFilter(ActivitySearchField.APPROVED, "", "Approved"))
                    add(ActivitySearchFilter(ActivitySearchField.UNAPPROVED, "", "Unapproved"))
                }
                if (searchableRecords.any { it.matched != null }) {
                    add(ActivitySearchFilter(ActivitySearchField.MATCHED, "", "Matched"))
                    add(ActivitySearchFilter(ActivitySearchField.UNMATCHED, "", "Unmatched"))
                }
                if (searchableRecords.any { it.transfer }) add(ActivitySearchFilter(ActivitySearchField.TRANSFER, "", "Transfer"))
            }
        }
    var suggestions by remember { mutableStateOf<List<ActivitySearchFilter>>(emptyList()) }
    var resultIndices by remember { mutableStateOf<List<Int>>(emptyList()) }
    var isEvaluatingSearch by remember { mutableStateOf(true) }
    LaunchedEffect(search, filters, searchableRecords, quickFilters, state.budgetDisplaySettings) {
        isEvaluatingSearch = true
        if (search.isNotBlank()) delay(150)
        val displaySettings = state.budgetDisplaySettings
        val evaluation =
            withContext(Dispatchers.Default) {
                val preparedSearch = prepareActivitySearch(search, filters)
                val matchedIndices =
                    searchableRecords.mapIndexedNotNull { index, record ->
                        index.takeIf { matchesActivitySearch(record, preparedSearch) }
                    }
                val refinements =
                    if (search.isBlank()) {
                        quickFilters
                    } else {
                        activitySearchSuggestions(search, searchableRecords) { formatCurrency(it, displaySettings) }
                    }
                matchedIndices to refinements
            }
        resultIndices = evaluation.first
        suggestions = evaluation.second
        isEvaluatingSearch = false
    }
    val resultTransactions =
        remember(searchTransactions, resultIndices) {
            resultIndices.take(100).mapNotNull(searchTransactions::getOrNull)
        }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = TandemSpacing.screen, vertical = TandemSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
    ) {
        ActivitySearchInput(
            value = search,
            onValueChange = {
                search = it
                state.activitySearchQuery = it
            },
        )
        if (filters.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
            ) {
                filters.forEach { filter ->
                    ActivitySearchFilterChip(filter) { state.activitySearchFilters = filters - filter }
                }
            }
        }
        val shownSuggestions = if (search.isBlank()) quickFilters else suggestions
        if (shownSuggestions.isNotEmpty()) {
            Text(
                if (search.isBlank()) "Filter by" else "Suggestions",
                color = tandemPalette.muted,
                fontSize = TandemTypography.caption,
                fontWeight = FontWeight.SemiBold,
            )
            if (search.isBlank()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                ) {
                    shownSuggestions.take(8).forEach { suggestion ->
                        ActivitySearchQuickFilter(suggestion) {
                            val existing =
                                state.activitySearchFilters.firstOrNull {
                                    it.field == suggestion.field &&
                                        it.value == suggestion.value
                                }
                            state.activitySearchFilters =
                                if (existing != null) {
                                    state.activitySearchFilters - existing
                                } else {
                                    state.activitySearchFilters + suggestion
                                }
                        }
                    }
                }
            } else {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 176.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
                ) {
                    shownSuggestions.take(8).forEach { suggestion ->
                        ActivitySearchSuggestion(suggestion) {
                            val existing =
                                state.activitySearchFilters.firstOrNull {
                                    it.field == suggestion.field &&
                                        it.value == suggestion.value
                                }
                            state.activitySearchFilters =
                                if (existing != null) {
                                    state.activitySearchFilters - existing
                                } else {
                                    state.activitySearchFilters + suggestion
                                }
                            search = ""
                            state.activitySearchQuery = ""
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Results", color = tandemPalette.text, fontSize = TandemTypography.cardTitle, fontWeight = FontWeight.SemiBold)
            Text(
                if (resultIndices.size > 100) "${formatInteger(resultIndices.size)} · showing 100" else formatInteger(resultIndices.size),
                color = tandemPalette.muted,
                fontSize = TandemTypography.caption,
            )
        }
        if (
            isLiveBudget &&
            (
                mobileSession.activitySearchRecordsBudgetId != state.selectedBudgetId ||
                    loadedSearchBudgetId != state.selectedBudgetId ||
                    isBuildingSearchRecords
            )
        ) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = tandemPalette.accent)
            }
        } else if (isEvaluatingSearch && resultTransactions.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = tandemPalette.accent)
            }
        } else if (resultTransactions.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    if (filters.isEmpty() &&
                        search.isBlank()
                    ) {
                        "No transactions are available in this local budget."
                    } else {
                        "No matching transactions."
                    },
                    color = tandemPalette.muted,
                    fontSize = TandemTypography.body,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
            ) {
                items(
                    resultTransactions.take(100),
                    key = { "search:${it.recordId ?: "${it.dateLabel}:${it.payee}:${it.amountMinorUnits}"}" },
                ) { transaction ->
                    TransactionRow(transaction) { openTransaction(state, navigation, transaction, isLive = isLiveBudget) }
                }
            }
        }
    }
}

@Composable
private fun ActivitySearchInput(
    value: String,
    onValueChange: (String) -> Unit,
) {
    val palette = tandemPalette
    SurfaceCard {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(horizontal = TandemSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
        ) {
            Icon(Lucide.Search, contentDescription = null, modifier = Modifier.size(21.dp), tint = palette.muted)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                textStyle = TextStyle(color = palette.text, fontSize = TandemTypography.body),
                cursorBrush = SolidColor(palette.accent),
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                "Payee, category or amount",
                                color = palette.muted,
                                fontSize = TandemTypography.body,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            if (value.isNotEmpty()) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClickLabel = "Clear search", onClick = { onValueChange("") }),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Lucide.X, contentDescription = null, modifier = Modifier.size(20.dp), tint = palette.muted)
                }
            }
        }
    }
}

@Composable
private fun ActivitySearchFilterChip(
    filter: ActivitySearchFilter,
    onRemove: () -> Unit,
) {
    val palette = tandemPalette
    Row(
        Modifier
            .clip(RoundedCornerShape(TandemShapes.pill))
            .background(palette.accentSurface)
            .heightIn(min = 44.dp)
            .clickable(
                role = Role.Button,
                onClickLabel = "Remove ${filter.label}",
                onClick = onRemove,
            ).padding(
                start = TandemSpacing.medium,
                end = TandemSpacing.small,
                top = TandemSpacing.small,
                bottom = TandemSpacing.small,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
    ) {
        Text(
            filter.label,
            color = palette.accent,
            fontSize = TandemTypography.caption,
            maxLines = 1,
        )
        Icon(
            Lucide.X,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = palette.accent,
        )
    }
}

@Composable
private fun ActivitySearchSuggestion(
    suggestion: ActivitySearchFilter,
    onClick: () -> Unit,
) {
    SurfaceCard(onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 42.dp).padding(horizontal = TandemSpacing.medium, vertical = TandemSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
        ) {
            Icon(
                when (suggestion.field) {
                    ActivitySearchField.DATE_MONTH, ActivitySearchField.DATE_ON -> Lucide.CalendarDays
                    ActivitySearchField.ACCOUNT -> Lucide.WalletCards
                    ActivitySearchField.CATEGORY -> Lucide.Tags
                    ActivitySearchField.TRANSFER -> Lucide.Zap
                    ActivitySearchField.CLEARED,
                    ActivitySearchField.RECONCILED,
                    ActivitySearchField.APPROVED,
                    ActivitySearchField.MATCHED,
                    -> Lucide.Check
                    ActivitySearchField.UNCLEARED,
                    ActivitySearchField.UNRECONCILED,
                    ActivitySearchField.UNAPPROVED,
                    ActivitySearchField.UNMATCHED,
                    -> Lucide.CircleDot
                    ActivitySearchField.OUTFLOW_IS, ActivitySearchField.OUTFLOW_AT_LEAST, ActivitySearchField.OUTFLOW_AT_MOST,
                    ActivitySearchField.INFLOW_IS, ActivitySearchField.INFLOW_AT_LEAST, ActivitySearchField.INFLOW_AT_MOST,
                    -> Lucide.Receipt
                    else -> Lucide.Search
                },
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = tandemPalette.accent,
            )
            Text(
                suggestion.label,
                color = tandemPalette.text,
                fontSize = TandemTypography.compactBody,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ActivitySearchQuickFilter(
    filter: ActivitySearchFilter,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    Row(
        Modifier
            .clip(RoundedCornerShape(TandemShapes.pill))
            .background(palette.raised)
            .clickable(role = Role.Button, onClickLabel = "Filter by ${filter.label}", onClick = onClick)
            .padding(horizontal = TandemSpacing.medium, vertical = TandemSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
    ) {
        Icon(Lucide.Plus, contentDescription = null, modifier = Modifier.size(14.dp), tint = palette.accent)
        Text(filter.label, color = palette.text, fontSize = TandemTypography.caption, maxLines = 1)
    }
}

@Composable
private fun TransactionDetailScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    merchantIconMatcher: MerchantIconMatcherPack?,
) {
    val isLive = state.selectedTransactionIsLive
    val amount = state.selectedTransactionAmountMinor ?: -4_218L
    val merchantIcon = remember(merchantIconMatcher, state.transactionPayee) { merchantIconMatcher?.iconFor(state.transactionPayee) }
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
        Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(TandemShapes.medium))
                        .background(tandemPalette.raised),
                    contentAlignment = Alignment.Center,
                ) {
                    if (merchantIcon != null) {
                        Image(
                            painter = painterResource(merchantIcon),
                            contentDescription = null,
                            modifier = Modifier.size(38.dp).padding(TandemSpacing.extraSmall),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Icon(
                            imageVector = categoryIcon,
                            contentDescription = null,
                            modifier = Modifier.size(30.dp),
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
                    fontSize = TandemTypography.amount,
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
            PrimaryButton("Edit transaction", enabled = state.selectedTransactionRecordId.isNotBlank() && !state.readOnlyMode) {
                navigation.open(AppRoute.EDIT_TRANSACTION)
            }
        } else {
            PreviewNote("This sample transaction is read-only. Editing it won’t change Actual Budget.")
            PrimaryButton("Edit transaction", enabled = !state.readOnlyMode) { navigation.open(AppRoute.EDIT_TRANSACTION) }
        }
    }
}

@Composable
private fun TransactionFormScreen(
    route: AppRoute,
    state: AppState,
    navigation: AppNavigation,
    mobileSession: TandemMobileSession,
    onSaveTransaction: ((Boolean) -> Unit) -> Unit,
    onDeleteTransaction: ((Boolean) -> Unit) -> Unit,
    onCreateTransaction: () -> Unit,
) {
    if (state.readOnlyMode) {
        PageColumn {
            Heading("Read-only mode")
            BodyText("Turn it off in Device protection to edit this transaction.")
        }
        return
    }
    val isNew = route == AppRoute.NEW_TRANSACTION
    if (state.selectedBudgetId.isNotBlank()) {
        if (isNew) {
            var keypadVisible by rememberSaveable { mutableStateOf(true) }
            var calculatorAccumulator by rememberSaveable { mutableStateOf<Long?>(null) }
            var calculatorOperator by rememberSaveable { mutableStateOf<String?>(null) }
            var enterNewOperand by rememberSaveable { mutableStateOf(false) }
            var amountEntryMessage by rememberSaveable { mutableStateOf<String?>(null) }
            val amountFocusRequester = remember { FocusRequester() }
            val focusManager = LocalFocusManager.current
            val softwareKeyboard = LocalSoftwareKeyboardController.current
            val routeScrollState = LocalRouteScrollState.current
            val compactSave by remember(routeScrollState) {
                derivedStateOf { (routeScrollState?.value ?: 0) > 72 }
            }
            var amountMinorUnits by
                remember(state.transactionAmount) {
                    mutableStateOf(
                        state
                            .parseAmountMinorUnits(state.transactionAmount)
                            ?.let {
                                (if (it == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(it))
                                    .coerceAtMost(MAX_TRANSACTION_AMOUNT_MINOR_UNITS)
                            }
                            ?: 0L,
                    )
                }

            fun setAmount(minorUnits: Long) {
                val magnitude =
                    (if (minorUnits == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(minorUnits))
                        .coerceAtMost(MAX_TRANSACTION_AMOUNT_MINOR_UNITS)
                amountMinorUnits = magnitude
                state.transactionAmount = state.formatAmount(magnitude)
            }

            fun enterDigit(digit: Int) {
                val current = if (enterNewOperand) 0L else amountMinorUnits
                val exceededLimit = amountDigitExceedsLimit(current, digit)
                setAmount(appendAmountDigit(current, digit))
                amountEntryMessage =
                    if (exceededLimit) {
                        "Largest amount is ${state.formatCurrency(MAX_TRANSACTION_AMOUNT_MINOR_UNITS)}."
                    } else {
                        null
                    }
                enterNewOperand = false
            }

            fun chooseOperator(operator: String) {
                val previous = calculatorAccumulator
                val pending = calculatorOperator
                if (previous != null && pending != null && !enterNewOperand) {
                    val result = calculateAmountMinorUnits(previous, amountMinorUnits, pending)
                    if (result == null) {
                        amountEntryMessage =
                            if (pending == "÷" && amountMinorUnits == 0L) {
                                "Can’t divide by zero."
                            } else {
                                "Largest amount is ${state.formatCurrency(MAX_TRANSACTION_AMOUNT_MINOR_UNITS)}."
                            }
                        return
                    }
                    setAmount(result)
                    calculatorAccumulator = kotlin.math.abs(result)
                } else if (previous == null) {
                    calculatorAccumulator = amountMinorUnits
                }
                amountEntryMessage = null
                calculatorOperator = operator
                enterNewOperand = true
            }

            fun calculate() {
                val previous = calculatorAccumulator
                val operator = calculatorOperator
                if (previous != null && operator != null) {
                    val result =
                        if (enterNewOperand) previous else calculateAmountMinorUnits(previous, amountMinorUnits, operator)
                    if (result == null) {
                        amountEntryMessage =
                            if (operator == "÷" && amountMinorUnits == 0L) {
                                "Can’t divide by zero."
                            } else {
                                "Largest amount is ${state.formatCurrency(MAX_TRANSACTION_AMOUNT_MINOR_UNITS)}."
                            }
                        return
                    }
                    setAmount(result)
                }
                amountEntryMessage = null
                calculatorAccumulator = null
                calculatorOperator = null
                enterNewOperand = true
            }

            LaunchedEffect(keypadVisible) {
                if (keypadVisible) {
                    softwareKeyboard?.hide()
                    amountFocusRequester.requestFocus()
                }
            }

            Box(Modifier.fillMaxSize()) {
                PageColumn(
                    verticalSpacing = 14.dp,
                    modifier = Modifier.imePadding().padding(bottom = if (keypadVisible) AMOUNT_KEYPAD_HEIGHT + 68.dp else 76.dp),
                ) {
                    TransactionAmountField(
                        amount = state.transactionAmount.ifBlank { state.formatAmount(0) },
                        amountMinorUnits = amountMinorUnits,
                        transactionType = state.transactionType,
                        focusRequester = amountFocusRequester,
                        onShowKeypad = { keypadVisible = true },
                    )
                    AnimatedReveal(visible = amountEntryMessage != null) {
                        Text(amountEntryMessage.orEmpty(), color = tandemPalette.warning, fontSize = TandemTypography.compactBody)
                    }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TransactionTypeChip(state.transactionType) {
                            keypadVisible = false
                            softwareKeyboard?.hide()
                            navigation.open(AppRoute.CHOOSE_TRANSACTION_TYPE)
                        }
                    }
                    SectionCard(title = "") {
                        TransactionPickerRow(
                            state.transactionPayee,
                            "Choose payee",
                            Lucide.Receipt,
                        ) {
                            keypadVisible = false
                            softwareKeyboard?.hide()
                            navigation.open(AppRoute.CHOOSE_PAYEE)
                        }
                        DividerLine()
                        TransactionPickerRow(
                            state.transactionCategory,
                            "Choose category",
                            Lucide.Tags,
                        ) {
                            keypadVisible = false
                            softwareKeyboard?.hide()
                            navigation.open(AppRoute.CHOOSE_CATEGORY)
                        }
                        DividerLine()
                        TransactionPickerRow(
                            state.transactionAccount,
                            "Choose account",
                            Lucide.WalletCards,
                        ) {
                            keypadVisible = false
                            softwareKeyboard?.hide()
                            navigation.open(AppRoute.CHOOSE_ACCOUNT)
                        }
                        DividerLine()
                        TransactionPickerRow(
                            displayTransactionDate(state.transactionDate),
                            "Choose date",
                            Lucide.CalendarDays,
                        ) {
                            keypadVisible = false
                            softwareKeyboard?.hide()
                            navigation.open(AppRoute.CHOOSE_DATE)
                        }
                    }
                    TextFieldCard(
                        "Notes",
                        state.transactionNotes,
                        { state.transactionNotes = it },
                        "Add a note",
                        singleLine = false,
                        onFocusedChange = { focused -> if (focused) keypadVisible = false },
                    )
                    AnimatedReveal(visible = mobileSession.transactionWriteMessage != null) {
                        SurfaceCard {
                            Column(Modifier.padding(TandemSpacing.large)) { BodyText(mobileSession.transactionWriteMessage.orEmpty()) }
                        }
                    }
                }
                if (keypadVisible) {
                    TransactionAmountKeypad(
                        onDigit = ::enterDigit,
                        onDelete = {
                            val current = if (enterNewOperand) 0L else amountMinorUnits
                            setAmount(removeAmountDigit(current))
                            amountEntryMessage = null
                            enterNewOperand = false
                        },
                        onOperator = ::chooseOperator,
                        onEquals = ::calculate,
                        onDone = {
                            keypadVisible = false
                            focusManager.clearFocus()
                        },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
                FloatingTransactionSave(
                    compact = compactSave,
                    enabled =
                        !mobileSession.isSavingTransaction &&
                            state.newTransactionAccountId.isNotBlank() &&
                            amountMinorUnits > 0,
                    onClick = onCreateTransaction,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .imePadding()
                            .padding(
                                end = 18.dp,
                                bottom = if (keypadVisible) AMOUNT_KEYPAD_HEIGHT + 12.dp else 12.dp,
                            ),
                )
            }
            return
        }
        var originalAmountMinor by remember(state.selectedTransactionRecordId) { mutableStateOf(state.selectedTransactionAmountMinor) }
        var originalNotes by remember(state.selectedTransactionRecordId) { mutableStateOf(state.selectedTransactionNotes) }
        var savedFeedback by remember(state.selectedTransactionRecordId) { mutableStateOf(false) }
        var confirmDelete by rememberSaveable(state.selectedTransactionRecordId) { mutableStateOf(false) }
        val parsedAmount = state.parseAmountMinorUnits(state.transactionAmount)
        val baselineAmount = originalAmountMinor?.let { kotlin.math.abs(it) }
        val hasChanges = parsedAmount != baselineAmount || state.transactionNotes != originalNotes
        val routeScrollState = LocalRouteScrollState.current
        val compactSave by remember(routeScrollState) {
            derivedStateOf { (routeScrollState?.value ?: 0) > 72 }
        }
        LaunchedEffect(savedFeedback) {
            if (savedFeedback) {
                delay(900)
                savedFeedback = false
            }
        }
        Box(Modifier.fillMaxSize()) {
            PageColumn(modifier = Modifier.padding(bottom = 76.dp)) {
                Heading("Edit transaction")
                TextFieldCard("Amount", state.transactionAmount, { state.transactionAmount = it }, state.formatAmount(4_218))
                TextFieldCard("Notes", state.transactionNotes, { state.transactionNotes = it }, "Add a note", singleLine = false)
                SectionCard("Transaction details") {
                    ListRow("", "Account", state.transactionAccount, leadingVector = Lucide.WalletCards)
                    DividerLine()
                    ListRow("", "Payee", state.transactionPayee, leadingVector = Lucide.Receipt)
                    DividerLine()
                    ListRow("", "Category", state.transactionCategory, leadingVector = Lucide.Tags)
                    DividerLine()
                    ListRow("", "Date", state.transactionDate, leadingVector = Lucide.CalendarDays)
                }
                mobileSession.transactionWriteMessage?.let { message ->
                    SurfaceCard {
                        Column(Modifier.padding(TandemSpacing.large)) { BodyText(message) }
                    }
                }
            }
            Row(
                Modifier.align(Alignment.BottomEnd).imePadding().padding(end = 18.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedVisibility(visible = hasChanges || savedFeedback, enter = fadeIn(), exit = fadeOut()) {
                    FloatingTransactionSave(
                        compact = compactSave || savedFeedback,
                        enabled = hasChanges && !mobileSession.isSavingTransaction && parsedAmount != null,
                        saved = savedFeedback,
                        onClick = {
                            onSaveTransaction { saved ->
                                if (saved) {
                                    originalAmountMinor = state.selectedTransactionAmountMinor
                                    originalNotes = state.selectedTransactionNotes
                                    savedFeedback = true
                                }
                            }
                        },
                    )
                }
                FloatingTransactionDeleteAction(
                    enabled = !mobileSession.isSavingTransaction,
                    onClick = { confirmDelete = true },
                )
            }
        }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { if (!mobileSession.isSavingTransaction) confirmDelete = false },
                title = { Text("Delete transaction?", color = tandemPalette.text) },
                text = { Text("It’ll be removed from this device. The removal will sync later.", color = tandemPalette.muted) },
                confirmButton = {
                    TextButton(
                        enabled = !mobileSession.isSavingTransaction,
                        onClick = {
                            onDeleteTransaction { deleted ->
                                confirmDelete = false
                                if (deleted) navigation.backToList()
                            }
                        },
                    ) {
                        Text("Delete", color = tandemPalette.negative)
                    }
                },
                dismissButton = {
                    TextButton(enabled = !mobileSession.isSavingTransaction, onClick = { confirmDelete = false }) {
                        Text("Keep", color = tandemPalette.muted)
                    }
                },
                backgroundColor = tandemPalette.surface,
            )
        }
        return
    }
    PageColumn {
        SampleBanner()
        Heading("Transaction details", "Record a change to the budget.")
        TextFieldCard("Amount", state.transactionAmount, { state.transactionAmount = it }, "For example, ${state.formatAmount(1_250)}")
        ReadOnlyField("Type", state.transactionType, leadingVector = Lucide.Receipt) {
            navigation.open(AppRoute.CHOOSE_TRANSACTION_TYPE)
        }
        ReadOnlyField("Account", state.transactionAccount, "A") { navigation.open(AppRoute.CHOOSE_ACCOUNT) }
        ReadOnlyField("Payee", state.transactionPayee, "P") { navigation.open(AppRoute.CHOOSE_PAYEE) }
        ReadOnlyField("Category", state.transactionCategory, "C") { navigation.open(AppRoute.CHOOSE_CATEGORY) }
        ReadOnlyField("Date", state.transactionDate, "▣") { navigation.open(AppRoute.CHOOSE_DATE) }
        TextFieldCard("Notes", state.transactionNotes, { state.transactionNotes = it }, "Add a note (optional)", singleLine = false)
        PreviewNote(
            if (isNew) {
                "New transactions aren’t available here yet. This sample form doesn’t save or sync changes."
            } else {
                "This sample form isn’t connected. Changes won’t be saved or synced."
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
        TextFieldCard("Search", query, { query = it }, "Start typing")
        val filtered = values.filter { it.label.contains(query, ignoreCase = true) }
        if (filtered.isEmpty()) {
            SurfaceCard {
                Text("No matches", modifier = Modifier.padding(TandemSpacing.large), color = tandemPalette.muted)
            }
        }
        filtered.forEach { item ->
            ChoiceRow(
                item.label,
                selected = selected == if (live) item.id else item.label,
                onClick = { onSelect(item) },
            )
        }
    }
}

@Composable
private fun DatePickerScreen(
    state: AppState,
    navigation: AppNavigation,
) {
    val selectedDateParts = parseIsoDate(state.transactionDate) ?: parseIsoDate(platformTodayIsoDate()) ?: Triple(2026, 5, 1)
    var visibleYear by remember { mutableStateOf(selectedDateParts.first) }
    var visibleMonth by remember { mutableStateOf(selectedDateParts.second) }
    val firstWeekday = platformFirstWeekday()
    val firstDayOffset = (platformWeekday(visibleYear, visibleMonth, 1) - firstWeekday + 7) % 7
    val daysInVisibleMonth = daysInMonth(visibleYear, visibleMonth)
    val dayCells = List<Int?>(firstDayOffset) { null } + (1..daysInVisibleMonth).map { it }
    PageColumn {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Lucide.ChevronLeft,
                contentDescription = "Previous month",
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClickLabel = "Previous month") {
                            if (visibleMonth == 1) {
                                visibleMonth = 12
                                visibleYear -= 1
                            } else {
                                visibleMonth -= 1
                            }
                        }.padding(TandemSpacing.medium),
                tint = tandemPalette.text,
            )
            Text(
                formatMonth(visibleYear, visibleMonth),
                modifier = Modifier.weight(1f),
                color = tandemPalette.text,
                fontSize = TandemTypography.periodTitle,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Icon(
                Lucide.ChevronRight,
                contentDescription = "Next month",
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClickLabel = "Next month") {
                            if (visibleMonth == 12) {
                                visibleMonth = 1
                                visibleYear += 1
                            } else {
                                visibleMonth += 1
                            }
                        }.padding(TandemSpacing.medium),
                tint = tandemPalette.text,
            )
        }
        Row(Modifier.fillMaxWidth()) {
            (0..6).forEach { column ->
                val representativeDay = 1 + ((column - firstDayOffset + 7) % 7)
                Text(
                    formatWeekday(visibleYear, visibleMonth, representativeDay),
                    modifier = Modifier.weight(1f),
                    color = tandemPalette.muted,
                    textAlign = TextAlign.Center,
                    fontSize = TandemTypography.caption,
                )
            }
        }
        dayCells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
                week.forEach { day ->
                    if (day == null) {
                        Spacer(Modifier.weight(1f).height(44.dp))
                    } else {
                        val isoDate = transactionIsoDate(visibleYear, visibleMonth, day)
                        val selected = state.transactionDate == isoDate
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
                                    .semantics { contentDescription = displayTransactionDate(isoDate) }
                                    .selectable(
                                        selected = selected,
                                        role = Role.RadioButton,
                                        onClick = {
                                            state.transactionDate = isoDate
                                            navigation.back()
                                        },
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                day.toString(),
                                color = cellTextColor,
                                fontSize = TandemTypography.label,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f).height(44.dp)) }
            }
        }
    }
}

private fun parseIsoDate(value: String): Triple<Int, Int, Int>? {
    val parts = value.split('-')
    if (parts.size != 3) return null
    val year = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val day = parts[2].toIntOrNull() ?: return null
    return Triple(year, month, day).takeIf { year > 0 && month in 1..12 && day in 1..daysInMonth(year, month) }
}

private fun daysInMonth(
    year: Int,
    month: Int,
): Int =
    when (month) {
        2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

private fun transactionIsoDate(
    year: Int,
    month: Int,
    day: Int,
): String = "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

private fun displayTransactionDate(value: String): String =
    parseIsoDate(value)?.let { (year, month, day) -> formatDate(SampleDate(year, month, day)) } ?: value

@Composable
private fun SaveResultScreen(
    state: AppState,
    navigation: AppNavigation,
) {
    val palette = tandemPalette
    PageColumn {
        Spacer(Modifier.height(16.dp))
        Badge("Not saved", palette.warning)
        Heading("No change was made", "This sample form can’t save a transaction yet.")
        SectionCard("Transaction", "Sample data") {
            ListRow("¤", "Amount", "Corner Market", state.formatCurrency(-4_218))
            DividerLine()
            ListRow("C", "Account and category", "Checking · Groceries")
        }
        PreviewNote(
            "Actual Budget is unchanged. New transactions aren’t available here yet, and this screen doesn’t send changes.",
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
    liveTransactions: List<TransactionItem>,
) {
    if (state.selectedBudgetId.isNotBlank()) {
        val attention = mobileSession.pendingWrites.filter { it.status in setOf("queued", "conflict", "uncertain", "rejected") }
        PageLazyColumn {
            item(key = "sync-center-title") {
                Heading("Sync")
            }
            item(key = "sync-center-budget-summary") {
                SurfaceCard {
                    Column(
                        Modifier.fillMaxWidth().padding(TandemSpacing.large),
                        verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                    ) {
                        LabelText(state.selectedBudget)
                        BodyText(
                            "${mobileSession.cachedTransactionCount} transactions · ${mobileSession.accountRecords.size} accounts cached",
                        )
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
                            fontSize = TandemTypography.label,
                            lineHeight = TandemTypography.compactLineHeight,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            item(key = "pending-changes-title") {
                SectionCard("Pending Changes", "${attention.size} edits", animateDetail = true) {}
            }
            if (attention.isEmpty()) {
                item(key = "pending-changes-empty") {
                    AnimatedAttentionContent(showingAttention = false) { _ ->
                        Text(
                            "No pending changes.",
                            modifier = Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
                            color = tandemPalette.muted,
                            fontSize = TandemTypography.body,
                            lineHeight = TandemTypography.bodyLineHeight,
                        )
                    }
                }
            } else {
                items(attention, key = { "pending:${it.operationId}" }) { operation ->
                    val transaction = liveTransactions.firstOrNull { it.recordId == operation.recordId }
                    SurfaceCard {
                        ListRow(
                            "",
                            transaction?.payee ?: "Transaction ${operation.recordId.take(8)}",
                            "${writeStatusLabel(operation.status)} · ${transaction?.category ?: "Transaction edit"}",
                            "Review",
                            trailingColor =
                                when (operation.status) {
                                    "conflict", "uncertain", "rejected" -> tandemPalette.warning
                                    else -> tandemPalette.syncing
                                },
                            leadingVector =
                                when (operation.status) {
                                    "conflict", "uncertain", "rejected" -> Lucide.CloudAlert
                                    "queued", "sending" -> Lucide.CloudSync
                                    else -> Lucide.Cloud
                                },
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
            }
            item(key = "sync-write-message") {
                AnimatedReveal(visible = mobileSession.transactionWriteMessage != null) {
                    BodyText(mobileSession.transactionWriteMessage.orEmpty())
                }
            }
            item(key = "sync-center-bottom-spacer") { Spacer(Modifier.height(72.dp)) }
        }
        return
    }
    PageColumn {
        Heading("Connection status", "Review device data, pending edits, and server activity.")
        SurfaceCard {
            Column(Modifier.padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small), verticalAlignment = Alignment.CenterVertically) {
                    Badge("Sample data")
                    Text(
                        "No server connection",
                        color = tandemPalette.text,
                        fontSize = TandemTypography.compactBody,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text("Last sync · sample only", color = tandemPalette.muted, fontSize = TandemTypography.label)
                PreviewNote("No data has been uploaded or downloaded.")
            }
        }
        SectionCard("Attention needed", "Sample recovery states") {
            ListRow("!", "Needs your review", "Corner Market · two versions", "1", tandemPalette.warning) {
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
                "New transactions aren’t available yet",
                "1",
                tandemPalette.negative,
            ) { navigation.open(AppRoute.REJECTED_WRITE) }
        }
        SectionCard("Recent sync activity", "Sample events", "") {
            ListRow("↓", "Budget snapshot", "Ready for initial download", "Pending")
            DividerLine()
            ListRow("↑", "Local edits", "None in sample data", "0")
        }
        SecondaryButton("Review changes") { navigation.open(AppRoute.CONFLICT_INBOX) }
    }
}

@Composable
private fun ConflictInboxScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    liveTransactions: List<TransactionItem>,
) {
    if (state.selectedBudgetId.isNotBlank()) {
        val conflicts = mobileSession.pendingWrites.filter { it.status == "conflict" }
        PageLazyColumn {
            item(key = "conflicts-title") {
                Heading("Changes to review", "Choose whether to keep your edit or the latest Actual version.")
            }
            if (conflicts.isEmpty()) {
                item(key = "conflicts-empty") {
                    AnimatedAttentionContent(showingAttention = false, animateSizeChanges = true) { _ ->
                        SurfaceCard(modifier = tandemListItemModifier(tandemReduceMotion)) {
                            Column(Modifier.padding(TandemSpacing.large)) { BodyText("No changes need review.") }
                        }
                    }
                }
            } else {
                item(key = "conflicts-section-title") {
                    SectionCard("Needs your review", "${conflicts.size} edits") {}
                }
                items(conflicts, key = { "conflict:${it.operationId}" }) { operation ->
                    val transaction = liveTransactions.firstOrNull { it.recordId == operation.recordId }
                    SurfaceCard(modifier = tandemListItemModifier(tandemReduceMotion)) {
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
            }
            mobileSession.transactionWriteMessage?.let { message ->
                item(key = "conflicts-write-message") { BodyText(message) }
            }
        }
        return
    }
    PageColumn {
        SampleBanner()
        Heading("Changes to review", "Choose which version to keep.")
        SurfaceCard(onClick = { navigation.open(AppRoute.CONFLICT_REVIEW) }) {
            Column(Modifier.fillMaxWidth().padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                    Badge("Needs your review", tandemPalette.warning)
                    Text("Corner Market", color = tandemPalette.text, fontSize = TandemTypography.body, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "${formatDate(SampleDate(2026, 5, 14))} · Groceries · Checking",
                    color = tandemPalette.muted,
                    fontSize = TandemTypography.caption,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    ConflictValueCard("Your edit", state.formatCurrency(1_200), Modifier.weight(1f))
                    ConflictValueCard("In Actual", state.formatCurrency(1_500), Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall)) {
                    Text(
                        "Review both versions",
                        color = tandemPalette.accent,
                        fontSize = TandemTypography.label,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Icon(Lucide.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp), tint = tandemPalette.accent)
                }
            }
        }
    }
}

@Composable
private fun ConflictValueCard(
    label: String,
    amount: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(TandemShapes.medium)).background(tandemPalette.raised).padding(TandemSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
    ) {
        Text(label, color = tandemPalette.muted, fontSize = TandemTypography.micro)
        Text(amount, color = tandemPalette.text, fontSize = TandemTypography.cardTitle, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ConflictReviewScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    onResolveConflict: (String, Boolean) -> Unit,
    liveTransactions: List<TransactionItem>,
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
            if (operation == null) {
                Heading("No change to review")
            } else {
                val transaction = liveTransactions.firstOrNull { it.recordId == operation.recordId }
                Heading("Two versions differ")
                Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    ConflictValueCard(
                        "Your edit",
                        deviceAmount?.let(::formatSignedCurrency) ?: "Deleted",
                        Modifier.weight(1f),
                    )
                    ConflictValueCard(
                        "In Actual",
                        actualAmount?.let(::formatSignedCurrency) ?: "Deleted",
                        Modifier.weight(1f),
                    )
                }
                SectionCard("Details") {
                    ListRow("A", "Account", "Actual account", transaction?.account ?: "Unavailable")
                    DividerLine()
                    ListRow("C", "Category", "Actual category", transaction?.category ?: "Unavailable")
                    DividerLine()
                    ListRow("◷", "Date", "Actual date", transaction?.dateLabel ?: "Unavailable")
                }
                mobileSession.transactionWriteMessage?.let { BodyText(it) }
                PrimaryButton(
                    "Keep your edit",
                    enabled = !state.readOnlyMode && !mobileSession.isSavingTransaction && record?.remoteDeleted != true,
                ) { onResolveConflict(operation.operationId, true) }
                SecondaryButton("Keep Actual", enabled = !state.readOnlyMode && !mobileSession.isSavingTransaction) {
                    onResolveConflict(operation.operationId, false)
                }
            }
        }
        return
    }
    PageColumn {
        Heading("Two versions differ", "Review the two sample amounts for Corner Market.")
        Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
            ConflictValueCard("Your edit", state.formatCurrency(1_200), Modifier.weight(1f))
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
        PrimaryButton("Keep ${state.formatCurrency(1_200)}") {
            state.conflictChoice =
                "device value ${state.formatCurrency(1_200)}"
        }
        SecondaryButton("Keep ${state.formatCurrency(1_500)}") {
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
    navigation: AppNavigation,
    onCheckStatus: (String) -> Unit,
    onRetry: (String) -> Unit,
    onDiscard: (String) -> Unit,
    liveTransactions: List<TransactionItem>,
) {
    if (state.selectedBudgetId.isNotBlank()) {
        val pendingOperation =
            if (state.selectedConflictOperationId.isNotBlank()) {
                mobileSession.pendingWrites.firstOrNull { it.operationId == state.selectedConflictOperationId }
            } else {
                mobileSession.pendingWrites.firstOrNull { it.status == "uncertain" }
            }
        val checkedOperation =
            mobileSession.checkedWriteOperation?.takeIf {
                it.operationId == state.selectedConflictOperationId ||
                    (state.selectedConflictOperationId.isBlank() && it.operationId == mobileSession.checkedWriteOperationId)
            }
        val operation = checkedOperation ?: pendingOperation
        PageColumn {
            if (operation == null) {
                Heading("Nothing to check")
                InfoBubble("This edit no longer needs confirmation.")
            } else {
                val transaction = liveTransactions.firstOrNull { it.recordId == operation.recordId }
                val isChecked = mobileSession.checkedWriteOperationId == operation.operationId
                val isNotFound = isChecked && operation.status == "uncertain" && operation.lastErrorCode == "http-404"
                val checkError =
                    mobileSession.uncertainWriteCheckError.takeIf {
                        mobileSession.uncertainWriteCheckErrorOperationId == operation.operationId
                    }
                val heading =
                    when {
                        checkError != null -> "Couldn’t check status"
                        operation.status == "committed" -> "Saved to Actual"
                        operation.status == "conflict" -> "Needs your review"
                        operation.status == "rejected" -> "Change not applied"
                        operation.status == "discarded" -> "Change discarded"
                        operation.status == "queued" -> "Waiting to sync"
                        operation.status == "sending" -> "Syncing"
                        operation.status == "uncertain" -> "Status unknown"
                        isNotFound -> "No server record found"
                        isChecked -> "Status unknown"
                        mobileSession.isSyncing -> "Checking status"
                        else -> "Status unknown"
                    }
                val message =
                    when {
                        checkError != null -> checkError
                        operation.status == "committed" -> "This change is saved in your budget."
                        operation.status == "conflict" -> "Actual changed this transaction. Review both versions."
                        operation.status == "rejected" -> "Actual kept the previous value."
                        operation.status == "discarded" -> "Your edit was removed."
                        operation.status == "queued" -> "Your edit is saved on this device and is waiting to sync."
                        operation.status == "sending" && mobileSession.isSyncing -> "Syncing this edit with Actual."
                        operation.status == "sending" -> "Status unknown. Check before retrying."
                        isNotFound -> "No matching edit is on the server. Retrying sends it once."
                        isChecked -> "Actual hasn’t confirmed whether it saved this edit. Check again before retrying."
                        mobileSession.isSyncing -> "Checking Actual for this edit."
                        else -> "Tandem couldn’t confirm whether Actual saved this edit. Check its status before retrying."
                    }
                Heading(heading)
                SectionCard(
                    transaction?.payee ?: "Transaction",
                    transaction?.dateLabel ?: "Saved on this device",
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.large),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Amount", color = tandemPalette.muted, fontSize = TandemTypography.body)
                        Text(
                            operation.optimisticAmountMinor?.let { state.formatSignedCurrency(it) } ?: "Unavailable",
                            color = tandemPalette.text,
                            fontSize = TandemTypography.cardTitle,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                InfoBubble(message)
                when {
                    checkError != null -> {
                        PrimaryButton(
                            "Check again",
                            enabled = !mobileSession.isSyncing,
                            loading = mobileSession.isSyncing,
                        ) { onCheckStatus(operation.operationId) }
                    }
                    operation.status == "uncertain" && isNotFound -> {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                            PrimaryButton(
                                "Retry edit",
                                enabled = !state.readOnlyMode && !mobileSession.isSyncing,
                                modifier = Modifier.weight(1f),
                            ) { onRetry(operation.operationId) }
                            SecondaryButton(
                                "Discard",
                                modifier = Modifier.weight(1f),
                                enabled = !state.readOnlyMode && !mobileSession.isSyncing,
                            ) { onDiscard(operation.operationId) }
                        }
                    }
                    operation.status == "uncertain" -> {
                        PrimaryButton(
                            if (isChecked) "Check again" else "Check status",
                            enabled = !mobileSession.isSyncing,
                            loading = mobileSession.isSyncing,
                        ) { onCheckStatus(operation.operationId) }
                    }
                    operation.status == "queued" -> Spacer(Modifier.height(72.dp))
                    operation.status == "sending" ->
                        PrimaryButton(
                            if (mobileSession.isSyncing) "Syncing" else "Check status",
                            enabled = !mobileSession.isSyncing,
                            loading = mobileSession.isSyncing,
                        ) {
                            if (!mobileSession.isSyncing) onCheckStatus(operation.operationId)
                        }
                    operation.status == "conflict" ->
                        PrimaryButton("Review changes") {
                            state.selectedConflictOperationId = operation.operationId
                            navigation.open(AppRoute.CONFLICT_REVIEW)
                        }
                    operation.status == "rejected" ->
                        PrimaryButton("Review change") {
                            state.selectedConflictOperationId = operation.operationId
                            navigation.open(AppRoute.REJECTED_WRITE)
                        }
                    else -> PrimaryButton("Done") { navigation.back() }
                }
            }
        }
        return
    }
    PageColumn {
        Heading("Status unknown")
        SectionCard("Corner Market", "${formatShortDate(SampleDate(2026, 5, 14))} · Groceries") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.large),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Amount", color = tandemPalette.muted, fontSize = TandemTypography.body)
                Text(
                    state.formatCurrency(1_200),
                    color = tandemPalette.text,
                    fontSize = TandemTypography.cardTitle,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        InfoBubble("Tandem couldn’t confirm whether Actual saved this edit. Check its status before retrying.")
        PrimaryButton("Check status", enabled = false) {}
    }
}

@Composable
private fun RejectedWriteScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    onDiscard: (String) -> Unit,
    liveTransactions: List<TransactionItem>,
) {
    if (state.selectedBudgetId.isNotBlank()) {
        val operation =
            mobileSession.pendingWrites.firstOrNull {
                it.operationId == state.selectedConflictOperationId && it.status == "rejected"
            } ?: mobileSession.pendingWrites.firstOrNull { it.status == "rejected" }
        PageColumn {
            Badge("Not applied", tandemPalette.negative)
            Heading("Change not applied")
            if (operation == null) {
                BodyText("No unapplied edits need review.")
            } else {
                val transaction = liveTransactions.firstOrNull { it.recordId == operation.recordId }
                SectionCard(transaction?.payee ?: "Transaction", transaction?.dateLabel ?: "Not applied") {
                    ListRow(
                        "¤",
                        "Your edit",
                        "Not applied",
                        operation.optimisticAmountMinor?.let { state.formatSignedCurrency(it) } ?: "Unavailable",
                    )
                }
                PrimaryButton("Dismiss", enabled = !state.readOnlyMode) { onDiscard(operation.operationId) }
            }
        }
        return
    }
    PageColumn {
        Badge("Not applied", tandemPalette.negative)
        Heading("Change not applied")
        SectionCard("Sample edit", "Corner Market · ${formatShortDate(SampleDate(2026, 5, 14))}") {
            ListRow("¤", "Your edit", "Not applied", state.formatCurrency(1_200))
            DividerLine()
            ListRow("!", "Reason", "New transactions aren’t available yet", "Unavailable", tandemPalette.warning)
        }
        PreviewNote(
            "The change wasn’t saved. You can dismiss this message.",
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
            Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                PreviewNote(state.settingsStorageError.orEmpty())
                SecondaryButton("Retry settings storage") { state.settingsStorageRetry += 1 }
            }
        }
        SectionCard("Appearance") {
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
                "Access and local data",
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
        Heading("Visual settings")
        SectionCard("Theme", "${state.theme.label} appearance", "") {
            ListRow(
                "",
                "Theme mode",
                "System, light, dark, or pure black",
                state.theme.label,
                leadingVector = Lucide.SunMoon,
                onClick = { navigation.open(AppRoute.THEME_MODE) },
            )
        }
        SectionCard("Color", "${state.accent.label} accent", "") {
            ListRow(
                "",
                "Accent color",
                "Applies to every screen",
                state.accent.label,
                leadingVector = Lucide.PaintbrushVertical,
                onClick = { navigation.open(AppRoute.ACCENT_COLOR) },
            )
        }
        SectionCard("Background") {
            ListRow(
                "",
                "Background and motion",
                "Subtle treatments and reduced motion",
                leadingVector = Lucide.Wallpaper,
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
                    ThemeMode.LIGHT -> "Light appearance"
                    ThemeMode.DARK -> "The default low-glare Tandem palette"
                    ThemeMode.PURE_BLACK -> "Black background"
                },
                selected = state.theme == mode,
                onClick = { state.theme = mode },
            )
        }
    }
}

@Composable
private fun AccentColorScreen(state: AppState) {
    PageColumn {
        AccentChoice.entries.forEach { accent ->
            val palette = tandemPalette
            SurfaceCard(onClick = { state.accent = accent }) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                ) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(accent.color))
                    Text(
                        accent.label,
                        modifier = Modifier.weight(1f),
                        color = palette.text,
                        fontSize = TandemTypography.body,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (state.accent == accent) {
                        Text("✓", color = palette.accent, fontSize = TandemTypography.cardTitle, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun BackgroundScreen(state: AppState) {
    PageColumn {
        SurfaceCard {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .clip(RoundedCornerShape(TandemShapes.medium))
                    .background(appBackgroundBrush(state.backgroundStyle, tandemPalette)),
                contentAlignment = Alignment.Center,
            ) {
            }
        }
        listOf(
            "Soft gradient" to "A subtle vertical blend behind the cards",
            "Solid surface" to "A single steady canvas color",
            "Low-motion glow" to "A static accent glow behind the content",
        ).forEach { (style, description) ->
            ChoiceRow(style, description, selected = state.backgroundStyle == style, onClick = { state.backgroundStyle = style })
        }
        SectionCard("Motion") {
            ToggleRow(
                "Reduce motion",
                "Fewer animations",
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
    }
}

@Composable
private fun HomeLayoutScreen(state: AppState) {
    PageColumn {
        Heading("Budget layout", "Set the amount of detail shown on the budget page.")
        listOf(
            "Comfortable" to "More detail and context",
            "Compact" to "More categories visible at once",
            "Essentials" to "Keep upcoming bills near the top",
        ).forEach { (label, description) ->
            ChoiceRow(label, description, selected = state.homeLayout == label, onClick = { state.homeLayout = label })
        }
        SurfaceCard {
            Column(Modifier.padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                LabelText("Layout preview")
                Text(
                    when (state.homeLayout) {
                        "Compact" -> "Compact rows and tighter spacing"
                        "Essentials" -> "Upcoming bills and essentials appear first"
                        else -> "More space between categories"
                    },
                    color = tandemPalette.muted,
                    fontSize = TandemTypography.label,
                )
                if (state.homeLayout == "Essentials") {
                    ListRow(
                        "◷",
                        "Upcoming bills",
                        "Due soon",
                        state.formatCurrency(12_000),
                        dense = false,
                        leadingVector = financeIcon("Upcoming bills"),
                    )
                    DividerLine()
                }
                ListRow(
                    "H",
                    "Essentials",
                    "3 categories",
                    state.formatCurrency(232_000),
                    dense = state.homeLayout == "Compact",
                    leadingVector = financeIcon("Essentials"),
                )
                DividerLine()
                ListRow(
                    "G",
                    "Groceries",
                    "Available this month",
                    state.formatCurrency(50_000),
                    dense = state.homeLayout == "Compact",
                    leadingVector = financeIcon("Groceries"),
                )
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
        Heading("Amount and planning")
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
        SectionCard("Planning") {
            ListRow("✓", "Available category amounts", "Shown from the synced plan", "Available", tandemPalette.positive)
            DividerLine()
            ListRow("↻", "Carry balances forward", "Controlled by Actual and shown read-only", "Actual")
        }
        PreviewNote("Tandem displays planning rules set in Actual. To change them, update the rules on the Actual website.")
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
            else -> "Open a budget and sync to save it on this device"
        }
    PageColumn {
        SectionCard("When to sync") {
            ToggleRow("Sync when opening a budget", "Check for server changes when returning to a budget", state.syncOnOpen) {
                state.syncOnOpen =
                    it
            }
            if (backgroundSyncSupported) {
                DividerLine()
                ToggleRow(
                    "Schedule background sync",
                    if (state.selectedBudgetEncrypted) {
                        "Encrypted budgets can’t sync in the background."
                    } else {
                        "Refresh the selected budget about every six hours"
                    },
                    state.backgroundSyncEnabled && !state.selectedBudgetEncrypted,
                    enabled = !state.selectedBudgetEncrypted,
                ) { state.backgroundSyncEnabled = it }
                if (!state.selectedBudgetEncrypted) {
                    DividerLine()
                    ToggleRow(
                        "Use unrestricted networks",
                        "Pauses on metered connections",
                        state.syncOnWifi,
                    ) { state.syncOnWifi = it }
                }
            } else {
                InfoBubble("Background sync isn’t available on this device.")
            }
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
    onForgetDeviceLocally: () -> Unit,
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
                if (mobileSession.isServerReachable) "Connected" else "Disconnected",
                if (mobileSession.isServerReachable) tandemPalette.positive else tandemPalette.negative,
                leadingVector = Lucide.Server,
                onClick = { navigation.open(AppRoute.SERVER_ADDRESS) },
            )
            DividerLine()
            ListRow(
                "",
                "Connection status",
                if (mobileSession.isServerReachable) "Server is reachable" else "Server can’t be reached",
                if (mobileSession.isServerReachable) "Connected" else "Disconnected",
                if (mobileSession.isServerReachable) tandemPalette.positive else tandemPalette.negative,
                leadingVector = Lucide.Wifi,
            )
        }
        SectionCard(
            "This device",
            when {
                device == null -> "No device is paired"
                mobileSession.isCredentialStored -> "Pairing is saved on this phone"
                else -> "Pairing is active until the app closes"
            },
        ) {
            ListRow(
                "",
                "Device name",
                if (device != null) deviceName else "Not paired",
                leadingVector = Lucide.Smartphone,
            )
            DividerLine()
            ListRow(
                "",
                "Budgets this device can open",
                if (device == null) "Not paired" else "${device.budgetIds.size} available",
                leadingVector = Lucide.FolderKey,
            )
            DividerLine()
            ListRow(
                "",
                "Saved pairing",
                if (mobileSession.isCredentialStored) "Protected on this phone" else "Available until the app closes",
                when {
                    mobileSession.isCredentialStored -> "Saved"
                    device != null -> "Active"
                    else -> "None"
                },
                leadingVector = Lucide.KeyRound,
            )
        }
        InfoBubble("Pairing codes and budget passwords are never saved.")
        mobileSession.deviceRevocationError?.let { message ->
            AttentionBanner("Couldn’t remove this device", message, "Retry", onForgetDevice)
            InfoBubble("The server still trusts this phone until its owner removes it there.")
        }
        AnimatedPreviewNote(mobileSession.credentialStorageError)
        if (device == null) {
            SecondaryButton("Pair this device") { navigation.open(AppRoute.SERVER_ADDRESS) }
        } else if (mobileSession.deviceRevocationError != null) {
            SecondaryButton(
                "Forget on this phone only",
                enabled = !mobileSession.isRevokingDevice,
                onClick = onForgetDeviceLocally,
            )
        } else {
            SecondaryButton(
                if (mobileSession.isRevokingDevice) "Removing this device" else "Remove this device",
                enabled = !mobileSession.isRevokingDevice,
                onClick = onForgetDevice,
            )
        }
    }
}

@Composable
private fun SecurityScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
) {
    val hasActiveConnection =
        state.selectedBudgetId.isNotBlank() && mobileSession.isAuthenticated && mobileSession.isServerReachable
    val hasPendingWrites = mobileSession.pendingWrites.isNotEmpty()
    val canEnableReadOnly = hasActiveConnection && !hasPendingWrites
    val canChangeReadOnly = state.readOnlyMode || canEnableReadOnly
    PageColumn {
        Heading("Device protection")
        SectionCard("App access") {
            ListRow(
                "",
                "Biometric unlock",
                "Not available yet",
                "Unavailable",
                leadingVector = Lucide.FingerprintPattern,
            )
            DividerLine()
            CheckBoxRow(
                title = "Read-only mode",
                subtitle =
                    when {
                        canChangeReadOnly -> "Block transaction edits and notes"
                        !mobileSession.isAuthenticated -> "Pair this device first"
                        !mobileSession.isServerReachable -> "Connect to the server first"
                        else -> "Sync pending edits first"
                    },
                checked = state.readOnlyMode,
                enabled = canChangeReadOnly,
                onChange = { state.readOnlyMode = it },
            )
        }
        SectionCard("Access") {
            ListRow(
                "",
                "Saved pairing",
                if (mobileSession.isCredentialStored) "Protected on this phone" else "Available until the app closes",
                when {
                    mobileSession.isCredentialStored -> "Saved"
                    mobileSession.isAuthenticated -> "Active"
                    else -> "Not paired"
                },
                leadingVector = Lucide.KeyRound,
            )
            DividerLine()
            ListRow(
                "",
                "Budget password",
                if (state.selectedBudgetEncrypted) "Used to unlock this budget" else "Encrypted budgets only",
                if (state.selectedBudgetEncrypted) "Not stored" else "Unavailable",
                leadingVector = Lucide.LockKeyhole,
            )
        }
        PreviewNote("Budget passwords aren’t stored.")
        AnimatedPreviewNote(mobileSession.credentialStorageError)
    }
}

@Composable
private fun NotificationsScreen(
    state: AppState,
    onRequestNotificationPermission: (() -> Unit)?,
    onOpenNotificationSettings: (() -> Unit)?,
    notificationPermissionEnabled: Boolean?,
    onToggleNotificationPermission: ((Boolean) -> Unit)?,
) {
    PageColumn {
        SectionCard("") {
            if (notificationPermissionEnabled != null && onToggleNotificationPermission != null) {
                ToggleRow(
                    "Notifications",
                    if (notificationPermissionEnabled) {
                        "On · Disable in notification settings"
                    } else {
                        "Off · Enable in notification settings"
                    },
                    notificationPermissionEnabled,
                    onChange = onToggleNotificationPermission,
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                ) {
                    Text(
                        "Notifications",
                        modifier = Modifier.weight(1f),
                        color = tandemPalette.text,
                        fontSize = TandemTypography.compactBody,
                        fontWeight = FontWeight.Medium,
                    )
                    if (onRequestNotificationPermission != null) {
                        CompactActionButton("Enable", Modifier.weight(1f), onRequestNotificationPermission)
                    }
                    if (onOpenNotificationSettings != null) {
                        CompactActionButton("Settings", Modifier.weight(1f), onOpenNotificationSettings)
                    }
                }
            }
        }
        SectionCard("Alert preferences") {
            ToggleRow(
                "Sync reminders",
                "When background sync fails",
                state.syncReminders,
            ) {
                state.syncReminders = it
            }
            DividerLine()
            ToggleRow(
                "Review alerts",
                "When a change needs review",
                state.conflictReviewAlerts,
            ) {
                state.conflictReviewAlerts =
                    it
            }
            DividerLine()
            ToggleRow(
                "Not applied alerts",
                "When an edit isn’t applied",
                state.rejectedEditAlerts,
            ) {
                state.rejectedEditAlerts = it
            }
        }
        InfoBubble("In-app alerts appear when you open Tandem.")
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
            mobileSession.budgetMonthIds.size
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
            "Local copy",
            if (hasSelectedBudget) "Cached records and sync status." else "Choose a budget to view the cache.",
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
                "Cached transactions",
                formatInteger(mobileSession.cachedTransactionCount),
            )
            DividerLine()
            ListRow("▣", "Accounts", "Cached accounts", formatInteger(mobileSession.accountRecords.size))
            DividerLine()
            ListRow("", "Pending Changes", trailing = formatInteger(queuedCount), leadingVector = Lucide.CloudSync)
            DividerLine()
            ListRow("", "Needs your review", "Choose a version", formatInteger(conflictCount), leadingVector = Lucide.CloudAlert)
            AnimatedReveal(visible = attentionCount > 0, animateHeight = false) {
                Column {
                    DividerLine()
                    ListRow("⚠", "Other changes needing attention", "Review locally", formatInteger(attentionCount))
                }
            }
        }
        SectionCard("Storage actions") {
            ListRow(
                "↻",
                "Refresh local budget",
                "Sync this budget",
                when {
                    mobileSession.isSyncing -> "Syncing"
                    hasSelectedBudget -> "Refresh"
                    else -> "Unavailable"
                },
                onClick = onSyncNow.takeIf { hasSelectedBudget && !mobileSession.isSyncing },
                animateChanges = true,
            )
            DividerLine()
            ListRow("×", "Clear local data", trailing = "Unavailable")
        }
        AnimatedPreviewNote(mobileSession.syncError)
        if (mobileSession.pendingWrites.isNotEmpty()) InfoBubble("Pending edits stay on this device.")
    }
}

@Composable
private fun MerchantIconLicensesScreen(
    merchantIconCatalog: MerchantIconCatalog?,
    loadError: String?,
) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = TandemSpacing.screen, vertical = TandemSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
    ) {
        Heading("Icon licenses and sources")
        when {
            loadError != null -> BodyText(loadError)
            merchantIconCatalog == null -> {
                Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium), verticalAlignment = Alignment.CenterVertically) {
                    TandemLoadingIndicator(
                        modifier = Modifier.size(20.dp),
                        color = tandemPalette.accent,
                        strokeWidth = 2.dp,
                    )
                    BodyText("Loading icons")
                }
            }
            else -> {
                TextFieldCard("Search", query, { query = it }, "Name, license, or source")
                val filteredNotices =
                    remember(merchantIconCatalog, query) {
                        val normalizedQuery = query.trim()
                        merchantIconCatalog.notices.filter { notice ->
                            normalizedQuery.isEmpty() ||
                                notice.title.contains(normalizedQuery, ignoreCase = true) ||
                                notice.license.contains(normalizedQuery, ignoreCase = true) ||
                                notice.registryUrl.contains(normalizedQuery, ignoreCase = true) ||
                                notice.brandUrl.contains(normalizedQuery, ignoreCase = true)
                        }
                    }
                Text(
                    "${filteredNotices.size} of ${merchantIconCatalog.notices.size} icons",
                    color = tandemPalette.muted,
                    fontSize = TandemTypography.label,
                )
                if (filteredNotices.isEmpty()) {
                    BodyText("No icons match that search.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
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
                                    Modifier.fillMaxWidth().padding(TandemSpacing.medium),
                                    verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Image(
                                            painter = painterResource(notice.resource),
                                            contentDescription = null,
                                            modifier = Modifier.size(34.dp),
                                            contentScale = ContentScale.Fit,
                                        )
                                        Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall)) {
                                            Text(
                                                notice.title,
                                                color = tandemPalette.text,
                                                fontSize = TandemTypography.body,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Text(
                                                notice.license.replace("Fair Use", "fair use", ignoreCase = true),
                                                color = tandemPalette.muted,
                                                fontSize = TandemTypography.label,
                                            )
                                        }
                                    }
                                    Text(
                                        "Registry source: ${notice.registryUrl}",
                                        color = tandemPalette.muted,
                                        fontSize = TandemTypography.micro,
                                        lineHeight = TandemTypography.tightLineHeight,
                                        modifier = Modifier.semantics { contentDescription = "Registry source URL: ${notice.registryUrl}" },
                                    )
                                    if (notice.brandUrl.isNotBlank()) {
                                        Text(
                                            "Brand site: ${notice.brandUrl}",
                                            color = tandemPalette.muted,
                                            fontSize = TandemTypography.micro,
                                            lineHeight = TandemTypography.tightLineHeight,
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
    merchantIconMatcherLoadError: String?,
    navigation: AppNavigation,
) {
    PageColumn {
        Heading("About Tandem", "An independent, local-first mobile companion for Actual Budget.")
        SurfaceCard {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.extraLarge, vertical = TandemSpacing.extraLarge),
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TandemBrandMark(Modifier.size(84.dp), contentDescription = "Tandem app icon")
                Text("Tandem", color = tandemPalette.text, fontSize = TandemTypography.pageTitle, fontWeight = FontWeight.Bold)
                Badge("Version 0.1.0")
                Text(
                    "Your budget stays on this device and remains available when the server is offline.",
                    color = tandemPalette.muted,
                    fontSize = TandemTypography.compactBody,
                    lineHeight = TandemTypography.bodyLineHeight,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Not affiliated with, endorsed by, or supported by Actual Budget.",
                    color = tandemPalette.muted,
                    fontSize = TandemTypography.caption,
                    textAlign = TextAlign.Center,
                )
            }
        }
        SectionCard("Project") {
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
        }
        SurfaceCard {
            ListRow(
                "",
                "View icon licenses and sources",
                leadingVector = Lucide.Info,
                onClick = { navigation.open(AppRoute.MERCHANT_ICON_LICENSES) },
            )
        }
        AnimatedPreviewNote(merchantIconMatcherLoadError)
    }
}
