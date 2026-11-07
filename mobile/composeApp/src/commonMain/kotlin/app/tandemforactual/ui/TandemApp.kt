@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.tandemforactual.api.client.BudgetCatalogItem
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.api.client.isLocalHttpHost
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.database.SearchableBudgetTransaction
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.ui.components.AnimatedReveal
import app.tandemforactual.ui.components.AnimatedStatusText
import app.tandemforactual.ui.components.Badge
import app.tandemforactual.ui.components.BodyText
import app.tandemforactual.ui.components.CollapsibleSectionCard
import app.tandemforactual.ui.components.DividerLine
import app.tandemforactual.ui.components.EmptyState
import app.tandemforactual.ui.components.InlineMessage
import app.tandemforactual.ui.components.InlineMessageKind
import app.tandemforactual.ui.components.LabelText
import app.tandemforactual.ui.components.ListRow
import app.tandemforactual.ui.components.ListRowOptions
import app.tandemforactual.ui.components.PeriodStepper
import app.tandemforactual.ui.components.PrimaryButton
import app.tandemforactual.ui.components.RowLeading
import app.tandemforactual.ui.components.SecondaryButton
import app.tandemforactual.ui.components.SectionCard
import app.tandemforactual.ui.components.SelectableTile
import app.tandemforactual.ui.components.SurfaceCard
import app.tandemforactual.ui.components.TandemActionButton
import app.tandemforactual.ui.components.TandemActionButtonTone
import app.tandemforactual.ui.components.TandemIconButton
import app.tandemforactual.ui.components.TandemLoadingIndicator
import app.tandemforactual.ui.components.crossfadeTransitions
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.allDrawableResources
import app.tandemforactual.ui.model.LiveAccountSummary
import app.tandemforactual.ui.model.LiveBudgetMonthSummary
import app.tandemforactual.ui.model.LiveCategoryGroupSummary
import app.tandemforactual.ui.model.LiveCategorySummary
import app.tandemforactual.ui.model.LiveScheduleSummary
import app.tandemforactual.ui.model.booleanValue
import app.tandemforactual.ui.model.liveAccountGroupLabel
import app.tandemforactual.ui.model.liveAccountSummaries
import app.tandemforactual.ui.model.liveBudgetMonthSummary
import app.tandemforactual.ui.model.liveScheduleSummaries
import app.tandemforactual.ui.model.longValue
import app.tandemforactual.ui.model.recordBody
import app.tandemforactual.ui.model.recordName
import app.tandemforactual.ui.model.stringValue
import app.tandemforactual.ui.reports.ReportKind
import app.tandemforactual.ui.reports.ReportWindow
import app.tandemforactual.ui.reports.reportMonthLabel
import com.composables.icons.lucide.Baby
import com.composables.icons.lucide.Banknote
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.Bike
import com.composables.icons.lucide.BookOpen
import com.composables.icons.lucide.BriefcaseBusiness
import com.composables.icons.lucide.Building2
import com.composables.icons.lucide.BusFront
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.CarFront
import com.composables.icons.lucide.Carrot
import com.composables.icons.lucide.Cat
import com.composables.icons.lucide.ChartNoAxesCombined
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.CircleDot
import com.composables.icons.lucide.Cloud
import com.composables.icons.lucide.CloudAlert
import com.composables.icons.lucide.CloudCheck
import com.composables.icons.lucide.CloudOff
import com.composables.icons.lucide.CloudSync
import com.composables.icons.lucide.Coffee
import com.composables.icons.lucide.CreditCard
import com.composables.icons.lucide.Database
import com.composables.icons.lucide.Dog
import com.composables.icons.lucide.Droplets
import com.composables.icons.lucide.Dumbbell
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.FingerprintPattern
import com.composables.icons.lucide.Flower2
import com.composables.icons.lucide.FolderKey
import com.composables.icons.lucide.Fuel
import com.composables.icons.lucide.Gamepad2
import com.composables.icons.lucide.Gift
import com.composables.icons.lucide.GraduationCap
import com.composables.icons.lucide.HandCoins
import com.composables.icons.lucide.HeartHandshake
import com.composables.icons.lucide.HeartPulse
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.Landmark
import com.composables.icons.lucide.LayoutDashboard
import com.composables.icons.lucide.Leaf
import com.composables.icons.lucide.LockKeyhole
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minus
import com.composables.icons.lucide.Music2
import com.composables.icons.lucide.NotepadText
import com.composables.icons.lucide.PaintbrushVertical
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.PawPrint
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.PiggyBank
import com.composables.icons.lucide.Pill
import com.composables.icons.lucide.Plane
import com.composables.icons.lucide.PlugZap
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Receipt
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Server
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.Shirt
import com.composables.icons.lucide.ShoppingBag
import com.composables.icons.lucide.ShoppingCart
import com.composables.icons.lucide.Smartphone
import com.composables.icons.lucide.Sofa
import com.composables.icons.lucide.Stethoscope
import com.composables.icons.lucide.SunMoon
import com.composables.icons.lucide.Tags
import com.composables.icons.lucide.TrainFront
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Tv
import com.composables.icons.lucide.Utensils
import com.composables.icons.lucide.WalletCards
import com.composables.icons.lucide.Wallpaper
import com.composables.icons.lucide.Wifi
import com.composables.icons.lucide.Wrench
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
import kotlinx.serialization.json.JsonElement
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
    ACTIVITY_FILTER("Filter and sort"),
    TRANSACTION_DETAIL("Transaction details"),
    NEW_TRANSACTION("Add transaction"),
    EDIT_TRANSACTION("Edit transaction"),
    CHOOSE_ACCOUNT("Choose account"),
    CHOOSE_PAYEE("Choose payee"),
    MANAGE_PAYEES("Manage payees"),
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

private data class MerchantIconNotice(
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

private data class MerchantIconCatalog(
    val notices: List<MerchantIconNotice>,
)

private suspend fun loadMerchantIconMatcherPack(): MerchantIconMatcherPack {
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

private data class SelectorItem(
    val id: String,
    val label: String,
)

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
internal val LocalRouteLazyListState = staticCompositionLocalOf<LazyListState?> { null }

private fun AppState.isBudgetSectionCollapsed(sectionKey: String): Boolean =
    collapsedBudgetSections.split('\u001f').any { it == sectionKey }

private fun AppState.toggleBudgetSection(sectionKey: String) {
    val collapsed = collapsedBudgetSections.split('\u001f').filter(String::isNotBlank).toMutableSet()
    if (!collapsed.add(sectionKey)) collapsed.remove(sectionKey)
    collapsedBudgetSections = collapsed.joinToString("\u001f")
}

private fun AppState.isCategoryPinned(categoryId: String): Boolean =
    pinnedCategoryIds.split('\u001f').any { it == categoryPinKey(categoryId) || it == categoryId }

private val categoryIconChoices =
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

private fun categoryIconVector(choice: String): ImageVector? =
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

private fun AppState.categoryIconChoice(categoryId: String): String? {
    val key = categoryPinKey(categoryId)
    return categoryIconOverrides
        .split('\u001f')
        .firstNotNullOfOrNull { entry ->
            val delimiter = entry.indexOf('\u001e')
            if (delimiter < 0 || entry.substring(0, delimiter) != key) null else entry.substring(delimiter + 1)
        }
}

private fun AppState.setCategoryIconChoice(
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

private fun AppState.categoryIconFor(
    categoryId: String,
    categoryName: String,
): ImageVector = categoryIconChoice(categoryId)?.let(::categoryIconVector) ?: financeIcon(categoryName) ?: Lucide.Receipt

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
    val reset: (AppRoute) -> Unit,
    val replaceTop: (AppRoute) -> Unit,
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
    allowInsecurePrivateNetwork: Boolean = false,
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
    val reportsDataCache = remember { ReportsDataCache() }
    val activitySortCache = remember { ActivityTransactionSortCache() }
    SideEffect { state.systemReduceMotionEnabled = systemReduceMotion }
    val reduceMotion = state.reduceMotionOverride ?: systemReduceMotion
    val apiClient = remember(allowInsecurePrivateNetwork) { TandemApiClient(allowInsecurePrivateNetwork) }
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
    var backButtonTransitionRunning by remember { mutableStateOf(false) }
    var queuedBackPresses by remember { mutableStateOf(0) }
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
        state.selectedMonthId = currentMonthId()
        state.selectedMonth = currentMonthLabel()
        state.reportsMonthId = state.selectedMonthId
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
    var liveSchedules by remember { mutableStateOf(emptyList<LiveScheduleSummary>()) }
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
            state.selectedTransactionAccountId = ""
            state.selectedTransactionPayeeId = ""
            state.selectedTransactionCategoryId = ""
            state.selectedTransactionDate = ""
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
    lateinit var startBackButtonTransition: () -> Unit
    startBackButtonTransition = {
        if (backStack.size > 1) {
            val outgoingEntry = backStack.last()
            val incomingEntry = backStack.getOrNull(backStack.lastIndex - 1)
            if (incomingEntry == null) {
                popRoute()
            } else {
                backGestureProgress = 0f
                backGestureDirection = 1f
                backGestureOutgoingEntry = outgoingEntry
                backPreviewRoute = incomingEntry
                backButtonTransitionRunning = true
                backGestureCancelJob =
                    coroutineScope.launch {
                        val returnAnimation = Animatable(0f)
                        returnAnimation.animateTo(
                            targetValue = 1f,
                            animationSpec = tandemMotionSpec(reduceMotion, TandemMotion.ROUTE_MILLIS),
                        ) {
                            backGestureProgress = value
                        }
                        backButtonTransitionRunning = false
                        skipNextRouteTransition = true
                        popRoute()
                        backGestureCancelJob = null
                        if (queuedBackPresses > 0) {
                            backButtonTransitionRunning = true
                            coroutineScope.launch {
                                withFrameNanos { }
                                backButtonTransitionRunning = false
                                if (queuedBackPresses > 0 && backStack.size > 1) {
                                    queuedBackPresses -= 1
                                    startBackButtonTransition()
                                } else {
                                    queuedBackPresses = 0
                                }
                            }
                        }
                    }
            }
        }
    }
    val selectTab: (AppRoute) -> Unit = { selectedTab ->
        if (backStack.lastOrNull()?.route != selectedTab) {
            routeTransitionDirection = 0f
            backStack.clear()
            backStack.add(newRouteEntry(AppRoute.BUDGET))
            if (selectedTab != AppRoute.BUDGET) backStack.add(newRouteEntry(selectedTab))
            if (selectedTab == AppRoute.REPORTS) {
                val reportsStateKey = "tab-${AppRoute.REPORTS.name}"
                state.scrollPositions[reportsStateKey] = 0
                state.lazyScrollPositions[reportsStateKey] = 0 to 0
                routeLazyListStates[reportsStateKey]?.let { listState ->
                    listState.requestScrollToItem(0)
                }
            }
        }
    }
    val handleBack: () -> Unit = {
        if (backButtonTransitionRunning) {
            queuedBackPresses += 1
        } else {
            val backAnimationWasRunning = backGestureCancelJob != null
            backGestureCancelJob?.cancel()
            backGestureCancelJob = null
            if (backGestureCommitPending || backAnimationWasRunning) {
                backGestureCommitPending = false
                backGestureProgress = 1f
                skipNextRouteTransition = true
                popRoute()
            } else if (backStack.size > 1) {
                // Button back runs the same route pair and progress transform as predictive back.
                startBackButtonTransition()
            }
        }
    }
    val navigation =
        AppNavigation(
            open = { next ->
                if (next == AppRoute.NEW_TRANSACTION) {
                    newTransaction(null)
                } else {
                    if (next.isReportDetail() && backStack.lastOrNull()?.route != AppRoute.REPORTS) {
                        selectTab(AppRoute.REPORTS)
                    }
                    routeTransitionDirection = 1f
                    backStack.add(newRouteEntry(next))
                }
            },
            reset = { next ->
                routeTransitionDirection = 0f
                backStack.clear()
                backStack.add(newRouteEntry(next))
            },
            replaceTop = { next ->
                routeTransitionDirection = 1f
                if (backStack.isNotEmpty()) backStack.removeAt(backStack.lastIndex)
                backStack.add(newRouteEntry(next))
            },
            newTransaction = newTransaction,
            selectTab = selectTab,
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
    val merchantMatcherNeeded = mobileSession.transactionRecords.isNotEmpty() || mobileSession.accountRecords.isNotEmpty()
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
            skipNextRouteTransition = false
            backGestureCommitPending = false
            if (backPreviewRoute == routeEntry) {
                // Keep the interactive preview in place until AnimatedContent has
                // promoted this same route entry to its foreground destination.
                coroutineScope.launch {
                    withFrameNanos { }
                    if (backPreviewRoute == routeEntry) {
                        backGestureProgress = 0f
                        backGestureOutgoingEntry = null
                        backPreviewRoute = null
                    }
                }
            } else {
                backGestureProgress = 0f
                backGestureOutgoingEntry = null
                backPreviewRoute = null
            }
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
        liveAccounts =
            withContext(Dispatchers.Default) {
                liveAccountSummaries(accountRecords, mobileSession.accountGroupRecords)
            }
    }
    LaunchedEffect(mobileSession.scheduleRecords, mobileSession.payeeRecords, accountRecords) {
        val schedules = mobileSession.scheduleRecords
        val payees = mobileSession.payeeRecords
        liveSchedules =
            withContext(Dispatchers.Default) {
                liveScheduleSummaries(schedules, payees, accountRecords)
            }
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
                    navigation.reset(AppRoute.PAIRING_RESULT)
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
                    isCached -> navigation.reset(AppRoute.BUDGET)
                    selected.isEncrypted && state.encryptedPassword.isBlank() -> navigation.reset(AppRoute.UNLOCK_BUDGET)
                    else -> navigation.reset(AppRoute.FIRST_SYNC)
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
                        accountId = state.newTransactionAccountId,
                        payeeId = state.newTransactionPayeeId.takeIf(String::isNotBlank),
                        categoryId = state.newTransactionCategoryId.takeIf(String::isNotBlank),
                        date = state.transactionDate,
                        originalAmountMinor = originalAmount,
                        displaySettings = state.budgetDisplaySettings,
                        updateDetails = true,
                    )
                if (saved) {
                    val amount = state.parseAmountMinorUnits(state.transactionAmount) ?: kotlin.math.abs(originalAmount)
                    state.selectedTransactionAmountMinor = if (originalAmount < 0) -amount else amount
                    state.selectedTransactionNotes = state.transactionNotes
                    state.selectedTransactionAccountId = state.newTransactionAccountId
                    state.selectedTransactionPayeeId = state.newTransactionPayeeId
                    state.selectedTransactionCategoryId = state.newTransactionCategoryId
                    state.selectedTransactionDate = state.transactionDate
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
                backButtonTransitionRunning = false
                backGestureProgress = progress
                if (backGestureOutgoingEntry == null) {
                    backGestureOutgoingEntry = backStack.lastOrNull()
                }
                if (backPreviewRoute == null) {
                    backPreviewRoute = backStack.getOrNull(backStack.lastIndex - 1)
                }
            } else if (backGestureProgress > 0f) {
                backGestureCommitPending = false
                backButtonTransitionRunning = false
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
                                reportsDataCache = reportsDataCache,
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
                                activitySortCache = activitySortCache,
                                liveAccounts = liveAccounts,
                                liveSchedules = liveSchedules,
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
            val backTargetTab = backPreviewRoute?.route?.takeIf { it.isTab() }
            val tabShellRoute =
                route.takeIf { it.isTab() }
                    ?: backTargetTab
                    ?: backStack.dropLast(1).lastOrNull { it.route.isTab() }?.route
            val stationaryTabShell = tabShellRoute != null
            val shellHeaderRoute = if (backTargetTab != null) backTargetTab else route
            if (tabShellRoute != null) renderHeader(shellHeaderRoute)
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
                            initialState.route.isTab() && targetState.route.isTab() ->
                                fadeIn(tween(TandemMotion.CROSSFADE_MILLIS)) togetherWith
                                    fadeOut(tween(TandemMotion.CROSSFADE_MILLIS)) using null
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
            if (tabShellRoute != null) {
                BottomNavigation(
                    tabShellRoute,
                    navigation.selectTab,
                    visible = route.isTab() || backTargetTab != null,
                )
            }
        }
    }
}

private fun AppRoute.isTab(): Boolean =
    this == AppRoute.BUDGET ||
        this == AppRoute.ACCOUNTS ||
        this == AppRoute.ACTIVITY ||
        this == AppRoute.REPORTS ||
        this == AppRoute.SETTINGS

private fun AppRoute.isReportDetail(): Boolean =
    this == AppRoute.REPORT_SPENDING_BY_CATEGORY ||
        this == AppRoute.REPORT_SPENDING_OVER_TIME ||
        this == AppRoute.REPORT_INCOME_AND_SPENDING ||
        this == AppRoute.REPORT_BUDGETED_AND_SPENT ||
        this == AppRoute.REPORT_NET_WORTH

@Composable
private fun FloatingAction(
    compact: Boolean,
    enabled: Boolean,
    label: String,
    contentDescription: String,
    icon: ImageVector,
    loading: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = tandemPalette
    val actionColor = if (enabled) palette.accentContent else palette.muted
    Row(
        modifier =
            modifier
                .animateContentSize(tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTENT_SIZE_MILLIS))
                .height(56.dp)
                .defaultMinSize(minWidth = 56.dp)
                .clip(if (compact) CircleShape else RoundedCornerShape(TandemShapes.large))
                .background(if (enabled) palette.accent else palette.raised)
                .semantics(mergeDescendants = true) { this.contentDescription = contentDescription }
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
                .padding(horizontal = if (compact) TandemSpacing.large else TandemSpacing.extraLarge),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 0.dp else TandemSpacing.small),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), color = actionColor, strokeWidth = 2.dp)
        } else {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = actionColor)
        }
        AnimatedVisibility(visible = !compact, enter = fadeIn(), exit = fadeOut()) {
            Text(
                label,
                color = actionColor,
                fontSize = TandemTypography.body,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun FloatingTransactionAction(
    compact: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingAction(
        compact = compact,
        enabled = enabled,
        label = "Add transaction",
        contentDescription = if (enabled) "Add transaction" else "Read-only mode is on",
        icon = Lucide.Plus,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun FloatingSyncAction(
    compact: Boolean,
    syncing: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingAction(
        compact = compact,
        enabled = enabled,
        label = "Sync",
        contentDescription = "Sync",
        icon = Lucide.CloudSync,
        loading = syncing,
        onClick = onClick,
        modifier = modifier,
    )
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
            TandemIconButton(
                icon = if (route == AppRoute.NEW_TRANSACTION) Lucide.X else Lucide.ChevronLeft,
                contentDescription = "Back",
                onClick = onBack,
                iconSizeDp = 24,
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
            TandemIconButton(
                icon =
                    when (syncStatus) {
                        MainCloudStatus.OFF -> Lucide.CloudOff
                        MainCloudStatus.ERROR -> Lucide.CloudAlert
                        else -> Lucide.Cloud
                    },
                contentDescription =
                    when (syncStatus) {
                        MainCloudStatus.OFF -> "Sync is off"
                        MainCloudStatus.SYNCED -> "Everything synced"
                        MainCloudStatus.SYNCING -> "Syncing changes"
                        MainCloudStatus.ERROR -> "Sync needs attention"
                    },
                onClickLabel = "Open sync center",
                onClick = onSync,
                iconSizeDp = 25,
                tint = cloudTint,
            )
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
    visible: Boolean = true,
) {
    val palette = tandemPalette
    val items =
        listOf(
            AppRoute.BUDGET to "Budget",
            AppRoute.ACCOUNTS to "Accounts",
            AppRoute.ACTIVITY to "Activity",
            AppRoute.REPORTS to "Reports",
            AppRoute.SETTINGS to "Settings",
        )
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = if (visible) 1f else 0f }
                .background(
                    palette.surface,
                ).padding(
                    start = TandemSpacing.small,
                    end = TandemSpacing.small,
                    top = TandemSpacing.small,
                    bottom = TandemSpacing.small,
                ),
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
                            enabled = visible,
                            role = Role.Tab,
                            onClick = { selectTab(route) },
                        ).semantics(mergeDescendants = true) {
                            if (visible) {
                                contentDescription = label
                                this.selected = active
                            }
                        }.then(if (visible) Modifier else Modifier.clearAndSetSemantics {})
                        .padding(vertical = TandemSpacing.extraSmall),
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
            AppRoute.REPORTS -> {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 250f,
                    useCenter = false,
                    topLeft = Offset(inset * 2, inset * 2),
                    size = Size(size.width - inset * 4, size.height - inset * 4),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color = color,
                    startAngle = 170f,
                    sweepAngle = 72f,
                    useCenter = false,
                    topLeft = Offset(inset * 2, inset * 2),
                    size = Size(size.width - inset * 4, size.height - inset * 4),
                    style = Stroke(stroke, cap = StrokeCap.Round),
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
    reportsDataCache: ReportsDataCache,
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
    activitySortCache: ActivityTransactionSortCache,
    liveAccounts: List<LiveAccountSummary>,
    liveSchedules: List<LiveScheduleSummary>,
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
        AppRoute.BUDGET -> BudgetScreen(state, navigation, mobileSession, liveTransactions, liveSchedules, liveBudgetMonth, onSyncNow)
        AppRoute.MONTH_PICKER -> MonthPickerScreen(state, navigation)
        AppRoute.CATEGORY_GROUP -> CategoryGroupScreen(state, navigation, liveBudgetMonth, mobileSession)
        AppRoute.CATEGORY_DETAIL -> CategoryDetailScreen(state, navigation, liveBudgetMonth, mobileSession)
        AppRoute.SCHEDULES ->
            UpcomingSchedulesScreen(
                state,
                mobileSession,
                liveSchedules,
                liveAccounts,
                mobileSession.payeeRecords,
            )
        AppRoute.MANAGE_PAYEES ->
            ManagePayeesScreen(
                state,
                mobileSession,
                mobileSession.payeeRecords,
                mobileSession.categoryRecords,
                mobileSession.ruleRecords,
            )
        AppRoute.BUDGET_RULES ->
            BudgetRulesScreen(
                mobileSession.ruleRecords,
                mobileSession.accountRecords,
                mobileSession.categoryRecords,
                mobileSession.categoryGroupRecords,
                mobileSession.payeeRecords,
            )
        AppRoute.ACCOUNTS ->
            AccountsScreen(
                state,
                navigation,
                liveAccounts,
                merchantIconMatcher,
                mobileSession,
            )
        AppRoute.ACCOUNT_DETAIL ->
            AccountDetailScreen(state, navigation, liveAccounts, mobileSession, merchantIconMatcher)
        AppRoute.ACCOUNT_OPTIONS -> AccountOptionsScreen(state, liveAccounts, mobileSession, navigation)
        AppRoute.ACTIVITY ->
            ActivityScreen(
                state,
                mobileSession,
                navigation,
                liveTransactions,
                activitySortCache,
                mobileSession.isSyncing,
                onSyncNow,
            )
        AppRoute.REPORTS ->
            ReportsScreen(
                state,
                mobileSession,
                liveAccounts,
                reportsDataCache,
                onOpenReport = { kind -> navigation.open(reportRoute(kind)) },
                onOpenCategoryActivity = { categoryId, categoryName, window ->
                    openReportCategoryActivity(state, navigation, categoryId, categoryName, window)
                },
                onOpenActivity = { navigation.selectTab(AppRoute.ACTIVITY) },
                onAddTransaction = { navigation.newTransaction(null) },
            )
        AppRoute.REPORT_SPENDING_BY_CATEGORY,
        AppRoute.REPORT_SPENDING_OVER_TIME,
        AppRoute.REPORT_INCOME_AND_SPENDING,
        AppRoute.REPORT_BUDGETED_AND_SPENT,
        AppRoute.REPORT_NET_WORTH,
        -> {
            val kind = reportKind(route)
            ReportDetailScreen(
                kind,
                state,
                mobileSession,
                liveAccounts,
                reportsDataCache,
                onOpenCategoryActivity = { categoryId, categoryName, window ->
                    openReportCategoryActivity(state, navigation, categoryId, categoryName, window)
                },
                onEmptyAction =
                    if (state.selectedBudgetId.isBlank()) {
                        { navigation.selectTab(AppRoute.ACTIVITY) }
                    } else {
                        { navigation.newTransaction(null) }
                    },
            )
        }
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
            val scope = rememberCoroutineScope()
            val isLiveBudget = state.selectedBudgetId.isNotBlank()
            val values =
                if (!isLiveBudget) {
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
                if (isLiveBudget) state.newTransactionPayeeId else state.transactionPayee,
                live = isLiveBudget,
                topContent =
                    if (isLiveBudget) {
                        {
                            SurfaceCard {
                                ListRow(
                                    "Manage payees",
                                    leading = RowLeading.Icon(Lucide.Tags),
                                ) { navigation.open(AppRoute.MANAGE_PAYEES) }
                            }
                        }
                    } else {
                        null
                    },
                noMatches =
                    if (isLiveBudget) {
                        { query ->
                            val newName = query.trim()
                            Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                                if (newName.isNotEmpty()) {
                                    SurfaceCard {
                                        ListRow(
                                            if (mobileSession.isPerformingBudgetAction) "Adding payee…" else "Add $newName",
                                            leading = RowLeading.Icon(Lucide.Plus),
                                            onClick = {
                                                if (!mobileSession.isPerformingBudgetAction) {
                                                    scope.launch {
                                                        val payeeId =
                                                            mobileSession.createBudgetPayee(
                                                                state.selectedBudgetId,
                                                                newName,
                                                                state.encryptedPassword.takeIf { it.isNotBlank() },
                                                            )
                                                        if (payeeId != null) {
                                                            state.transactionPayee = newName
                                                            state.newTransactionPayeeId = payeeId
                                                            navigation.back()
                                                        }
                                                    }
                                                }
                                            },
                                        )
                                    }
                                } else {
                                    EmptyState("No matches", modifier = Modifier.padding(TandemSpacing.large))
                                }
                                mobileSession.budgetActionMessage?.let { InlineMessage(it, InlineMessageKind.ERROR) }
                            }
                        }
                    } else {
                        null
                    },
                onSelect = { item ->
                    state.transactionPayee = if (item.label == "No payee") "" else item.label
                    state.newTransactionPayeeId = if (isLiveBudget) item.id else ""
                    navigation.back()
                },
            )
        }
        AppRoute.CHOOSE_CATEGORY -> {
            val scope = rememberCoroutineScope()
            val isLiveBudget = state.selectedBudgetId.isNotBlank()
            val values =
                if (!isLiveBudget) {
                    listOf("Groceries", "Utilities", "Dining", "Rent", "Transport", "Personal")
                        .map { SelectorItem(it, it) }
                } else {
                    listOf(SelectorItem("", "Uncategorized")) +
                        mobileSession.categoryRecords
                            .mapNotNull { record ->
                                record.recordName()?.let { SelectorItem(record.recordId, it) }
                            }.distinctBy { it.id }
                }
            val categoryGroups =
                if (!isLiveBudget) {
                    emptyList()
                } else {
                    mobileSession.categoryGroupRecords.mapNotNull { record ->
                        val body = record.recordBody() ?: return@mapNotNull null
                        val isIncomeGroup = body.booleanValue("is_income") ?: false
                        if (isIncomeGroup != (state.transactionType == "Income") || body.booleanValue("hidden") == true) {
                            return@mapNotNull null
                        }
                        record.recordName()?.let { SelectorItem(record.recordId, it) }
                    }
                }
            var createGroupId by rememberSaveable(state.selectedBudgetId, state.transactionType) { mutableStateOf("") }
            var createGroupMenuExpanded by rememberSaveable(state.selectedBudgetId, state.transactionType) { mutableStateOf(false) }
            LaunchedEffect(categoryGroups) {
                if (categoryGroups.none { it.id == createGroupId }) {
                    createGroupId = categoryGroups.firstOrNull()?.id.orEmpty()
                }
            }
            SelectorScreen(
                values,
                if (state.selectedBudgetId.isBlank()) state.transactionCategory else state.newTransactionCategoryId,
                live = isLiveBudget,
                noMatches =
                    if (isLiveBudget) {
                        { query ->
                            val newName = query.trim()
                            if (newName.isBlank()) {
                                EmptyState("Type a category name", modifier = Modifier.padding(TandemSpacing.large))
                            } else {
                                SurfaceCard {
                                    Column(
                                        Modifier.fillMaxWidth().padding(TandemSpacing.large),
                                        verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                                    ) {
                                        Text(
                                            "Create category",
                                            color = tandemPalette.text,
                                            fontSize = TandemTypography.cardTitle,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        if (categoryGroups.isEmpty()) {
                                            BodyText("Add a ${state.transactionType.lowercase()} category group in Actual first.")
                                        } else {
                                            Box {
                                                val selectedGroup = categoryGroups.firstOrNull { it.id == createGroupId }
                                                ListRow(
                                                    "Category group",
                                                    leading = RowLeading.Icon(Lucide.Tags),
                                                    trailing = selectedGroup?.label ?: "Choose group",
                                                    onClick = { createGroupMenuExpanded = true },
                                                )
                                                DropdownMenu(
                                                    expanded = createGroupMenuExpanded,
                                                    onDismissRequest = { createGroupMenuExpanded = false },
                                                ) {
                                                    categoryGroups.forEach { group ->
                                                        DropdownMenuItem(
                                                            onClick = {
                                                                createGroupId = group.id
                                                                createGroupMenuExpanded = false
                                                            },
                                                        ) {
                                                            Text(
                                                                group.label,
                                                                color = tandemPalette.text,
                                                                fontSize = TandemTypography.compactBody,
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            mobileSession.budgetActionMessage?.let { InlineMessage(it, InlineMessageKind.ERROR) }
                                            TandemActionButton(
                                                if (mobileSession.isPerformingBudgetAction) "Creating…" else "Create \"$newName\"",
                                                TandemActionButtonTone.PRIMARY,
                                                modifier = Modifier.fillMaxWidth(),
                                                enabled =
                                                    !state.readOnlyMode &&
                                                        !mobileSession.isPerformingBudgetAction &&
                                                        createGroupId.isNotBlank() &&
                                                        newName.length <= 100,
                                                loading = mobileSession.isPerformingBudgetAction,
                                            ) {
                                                mobileSession.clearBudgetActionMessage()
                                                scope.launch {
                                                    val categoryId =
                                                        mobileSession.createBudgetCategory(
                                                            state.selectedBudgetId,
                                                            newName,
                                                            createGroupId,
                                                            state.encryptedPassword.takeIf { it.isNotBlank() },
                                                        )
                                                    if (categoryId != null) {
                                                        state.transactionCategory = newName
                                                        state.newTransactionCategoryId = categoryId
                                                        navigation.back()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        null
                    },
            ) { item ->
                state.transactionCategory = if (item.label == "Uncategorized") "" else item.label
                state.newTransactionCategoryId = if (!isLiveBudget) "" else item.id
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
    isRefreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val routeListState = LocalRouteLazyListState.current ?: rememberLazyListState()
    if (onRefresh == null) {
        LazyColumn(
            state = routeListState,
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = TandemSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            content = content,
        )
        return
    }

    val density = LocalDensity.current
    val refreshThresholdPx = with(density) { 72.dp.toPx() }
    var pullDistancePx by remember(routeListState) { mutableStateOf(0f) }
    val latestIsRefreshing by rememberUpdatedState(isRefreshing)
    val latestOnRefresh by rememberUpdatedState(onRefresh)
    val refreshConnection =
        remember(routeListState, refreshThresholdPx) {
            object : NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (source != NestedScrollSource.UserInput) return Offset.Zero
                    val atTop = routeListState.firstVisibleItemIndex == 0 && routeListState.firstVisibleItemScrollOffset == 0
                    if (available.y <= 0f || !atTop || latestIsRefreshing) return Offset.Zero
                    val remainingPull = (refreshThresholdPx * 1.5f - pullDistancePx).coerceAtLeast(0f)
                    val acceptedPull = available.y.coerceAtMost(remainingPull)
                    pullDistancePx += acceptedPull
                    return Offset(x = 0f, y = acceptedPull)
                }

                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (source != NestedScrollSource.UserInput) return Offset.Zero
                    if (available.y >= 0f || pullDistancePx <= 0f) return Offset.Zero
                    val consumedPull = (-available.y).coerceAtMost(pullDistancePx)
                    pullDistancePx -= consumedPull
                    return Offset(x = 0f, y = -consumedPull)
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    releasePull()
                    return Velocity.Zero
                }

                override suspend fun onPostFling(
                    consumed: Velocity,
                    available: Velocity,
                ): Velocity {
                    releasePull()
                    return Velocity.Zero
                }

                private fun releasePull() {
                    val shouldRefresh = pullDistancePx >= refreshThresholdPx && !latestIsRefreshing
                    pullDistancePx = 0f
                    if (shouldRefresh) latestOnRefresh()
                }
            }
        }
    val pullProgress = (pullDistancePx / refreshThresholdPx).coerceIn(0f, 1f)
    val indicatorTarget = if (isRefreshing) 44.dp else with(density) { pullDistancePx.toDp().coerceAtMost(64.dp) }
    val indicatorOffset by
        animateDpAsState(
            targetValue = indicatorTarget,
            animationSpec = spring(),
            label = "pull-to-refresh-offset",
        )
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = routeListState,
            modifier = Modifier.fillMaxSize().nestedScroll(refreshConnection),
            contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = TandemSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            content = content,
        )
        if (isRefreshing || pullDistancePx > 0f) {
            val rotation =
                if (isRefreshing) {
                    val transition = rememberInfiniteTransition(label = "pull-to-refresh-spin")
                    val spin by
                        transition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Restart),
                            label = "pull-to-refresh-rotation",
                        )
                    spin
                } else {
                    pullProgress * 360f
                }
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (indicatorOffset - 18.dp).coerceAtLeast(0.dp))
                    .size(36.dp)
                    .graphicsLayer {
                        val visibleProgress = if (isRefreshing) 1f else (pullDistancePx / with(density) { 20.dp.toPx() }).coerceIn(0.2f, 1f)
                        alpha = visibleProgress
                        scaleX = 0.72f + 0.28f * visibleProgress
                        scaleY = 0.72f + 0.28f * visibleProgress
                    }.clip(CircleShape)
                    .background(tandemPalette.raised)
                    .border(1.dp, tandemPalette.border, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Lucide.RefreshCw,
                    contentDescription = if (isRefreshing) "Refreshing" else "Pull to refresh",
                    modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = rotation },
                    tint = tandemPalette.accent,
                )
            }
        }
    }
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
        else ->
            Brush.verticalGradient(
                colorStops =
                    arrayOf(
                        0f to palette.surface,
                        0.42f to palette.background,
                        1f to palette.background,
                    ),
            )
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
internal fun Heading(
    title: String,
    subtitle: String? = null,
    compactSubtitle: Boolean = false,
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
        if (subtitle != null) {
            Text(
                subtitle,
                color = palette.muted,
                fontSize = if (compactSubtitle) TandemTypography.compactBody else TandemTypography.body,
                lineHeight = TandemTypography.bodyLineHeight,
            )
        }
    }
}

@Composable
private fun MonthSelectorCard(
    month: String,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    SurfaceCard(onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                LabelText("Planning period")
                Spacer(Modifier.height(TandemSpacing.extraSmall))
                Text(month, color = palette.text, fontSize = TandemTypography.action, fontWeight = FontWeight.SemiBold)
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
private fun FieldFrame(
    label: String,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    onClickLabel: String? = null,
    onClick: (() -> Unit)? = null,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(TandemSpacing.medium),
    content: @Composable RowScope.() -> Unit,
) {
    val palette = tandemPalette
    val shape = RoundedCornerShape(TandemShapes.medium)
    Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
        FieldLabel(label)
        Row(
            modifier =
                modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = if (large) 82.dp else 52.dp)
                    .clip(shape)
                    .background(palette.surface)
                    .border(1.dp, palette.border, shape)
                    .then(
                        if (onClick != null) {
                            Modifier.clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
                        } else {
                            Modifier
                        },
                    ).padding(
                        horizontal = if (large) TandemSpacing.screen else TandemSpacing.large,
                        vertical = if (large) TandemSpacing.screen else TandemSpacing.medium,
                    ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = horizontalArrangement,
        ) {
            content()
        }
    }
}

private data class TextFieldOptions(
    val placeholder: String = "",
    val singleLine: Boolean = true,
    val isSecret: Boolean = false,
    val secretKeyboardType: KeyboardType = KeyboardType.Password,
    val keyboardOptions: KeyboardOptions? = null,
    val textAlign: TextAlign = TextAlign.Start,
    val large: Boolean = false,
    val onFocusedChange: ((Boolean) -> Unit)? = null,
)

@Composable
private fun TextFieldCard(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    options: TextFieldOptions = TextFieldOptions(),
) {
    val palette = tandemPalette
    FieldFrame(label, large = options.large) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = options.singleLine,
            keyboardOptions =
                options.keyboardOptions ?: if (options.isSecret) {
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = options.secretKeyboardType,
                    )
                } else {
                    KeyboardOptions.Default
                },
            visualTransformation = if (options.isSecret) PasswordVisualTransformation() else VisualTransformation.None,
            textStyle =
                TextStyle(
                    color = palette.text,
                    fontSize = if (options.large) TandemTypography.amount else TandemTypography.action,
                    lineHeight = if (options.large) TandemTypography.largeInputLineHeight else TandemTypography.bodyLineHeight,
                    fontWeight = if (options.large) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = options.textAlign,
                ),
            cursorBrush = SolidColor(palette.accent),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState -> options.onFocusedChange?.invoke(focusState.isFocused) }
                    .semantics { contentDescription = label },
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty() && options.placeholder.isNotEmpty()) {
                        Text(
                            options.placeholder,
                            modifier = Modifier.fillMaxWidth(),
                            color = palette.muted,
                            fontSize = if (options.large) TandemTypography.keypadNumber else TandemTypography.body,
                            textAlign = options.textAlign,
                        )
                    }
                    innerTextField()
                }
            },
        )
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
    FieldFrame(label, onClickLabel = "$label, $value", onClick = onClick) {
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
        matches("flight", "flights", "airfare", "airport", "plane", "travel") -> Lucide.Plane
        matches("rent", "mortgage", "housing", "home maintenance", "home repair", "landlord") -> Lucide.House
        matches("electric", "utility", "utilities", "power bill", "water bill", "energy", "internet bill") -> Lucide.Zap
        matches("grocery", "groceries", "supermarket", "produce", "market", "household supplies") -> Lucide.Carrot
        matches("clothing", "clothes", "apparel", "garments", "shirts") -> Lucide.Shirt
        matches("shopping", "retail", "department store") -> Lucide.ShoppingCart
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
private fun SettingRowLayout(
    title: String,
    subtitle: String?,
    enabled: Boolean,
    minHeightDp: Int,
    compactTitle: Boolean = false,
    semiboldTitle: Boolean = false,
    interactionModifier: Modifier = Modifier,
    control: @Composable RowScope.() -> Unit,
) {
    val palette = tandemPalette
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = minHeightDp.dp)
                .then(interactionModifier)
                .padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
            Text(
                title,
                color = if (enabled) palette.text else palette.muted,
                fontSize = if (compactTitle) TandemTypography.compactBody else TandemTypography.body,
                fontWeight = if (semiboldTitle) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = palette.muted,
                    fontSize = TandemTypography.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        control()
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
    SettingRowLayout(
        title = title,
        subtitle = subtitle,
        enabled = enabled,
        minHeightDp = 66,
        compactTitle = true,
        interactionModifier = Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
    ) {
        Box(
            Modifier
                .width(42.dp)
                .height(24.dp)
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
    SettingRowLayout(
        title = title,
        subtitle = subtitle,
        enabled = enabled,
        minHeightDp = 64,
        interactionModifier = Modifier.toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange),
    ) {
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
    title: String,
    subtitle: String? = null,
    value: String? = null,
    onClick: () -> Unit,
) {
    ListRow(title, subtitle, value, onClick = onClick)
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
    SelectableTile(
        selected = selected,
        contentDescription = title,
        onSelect = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(TandemShapes.large),
        selectedBackground = palette.surface,
        unselectedBackground = palette.surface,
        selectedBorder = palette.border,
        unselectedBorder = palette.border,
    ) {
        SettingRowLayout(
            title = title,
            subtitle = subtitle,
            enabled = true,
            minHeightDp = 64,
            semiboldTitle = true,
        ) {
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
        TextFieldCard(
            "Server URL",
            state.serverUrl,
            { state.serverUrl = it },
            TextFieldOptions(placeholder = "https://budget.example.com"),
        )
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
    if (hasHttpScheme && isLocalHttpHost(host)) return false
    if (host == "localhost" || host.endsWith(".localhost")) return false
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
            TextFieldOptions(
                placeholder = "For example, 4DF7-J1M9",
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Ascii,
                    ),
                textAlign = TextAlign.Center,
            ),
        )
        InfoBubble("This pairing code expires after five minutes. Tandem doesn’t save it or your budget password.")
        mobileSession.pairingError?.let { error -> InlineMessage(error, InlineMessageKind.ERROR) }
        mobileSession.credentialStorageError?.let { error ->
            InlineMessage(error, InlineMessageKind.ERROR)
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
        mobileSession.budgetError?.let { error -> InlineMessage(error, InlineMessageKind.ERROR) }
        PrimaryButton("Choose a budget", enabled = mobileSession.pairedDevice != null) {
            navigation.reset(AppRoute.BUDGET_CHOOSER)
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
                    InlineMessage(error, InlineMessageKind.ERROR)
                } ?: EmptyState("No budgets available", "This paired device has no budget access.")
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
            InlineMessage(error, InlineMessageKind.ERROR)
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
            TextFieldOptions(placeholder = "Enter password", isSecret = true),
        )
        InfoBubble("Tandem uses this password only to unlock the encrypted budget. It’s cleared after the first sync and never saved.")
        PrimaryButton("Unlock budget", enabled = state.encryptedPassword.isNotBlank()) { navigation.replaceTop(AppRoute.FIRST_SYNC) }
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
    liveSchedules: List<LiveScheduleSummary>,
    liveBudgetMonth: LiveBudgetMonthSummary?,
    onSyncNow: () -> Unit,
) {
    val palette = tandemPalette
    val scope = rememberCoroutineScope()
    var showCopyLastMonthDialog by rememberSaveable(state.selectedBudgetId, state.selectedMonthId) { mutableStateOf(false) }
    var showBudgetTemplatesDialog by rememberSaveable(state.selectedBudgetId, state.selectedMonthId) { mutableStateOf(false) }
    var showNextMonthHoldDialog by rememberSaveable(state.selectedBudgetId, state.selectedMonthId) { mutableStateOf(false) }
    var budgetTemplateNameDraft by rememberSaveable(state.selectedBudgetId) { mutableStateOf("") }
    val isLiveBudget = state.selectedBudgetId.isNotBlank()
    val isTrackingBudget = isLiveBudget && state.budgetDisplaySettings?.budgetType == "tracking"
    val compact = state.homeLayout == "Compact"
    val essentialsFirst = state.homeLayout == "Essentials"
    val sectionSpacing = if (compact) 10.dp else 16.dp
    val groups = if (isLiveBudget) liveBudgetMonth?.groups.orEmpty() else previewCategoryGroups
    val currentBudgetTemplates =
        remember(state.budgetTemplatesJson, state.selectedBudgetId) {
            decodeBudgetTemplates(state.budgetTemplatesJson).filter { it.budgetId == state.selectedBudgetId }
        }
    val currentTemplateAssignments =
        remember(groups) {
            groups.flatMap { group -> group.categories }.map { it.id to it.assignedMinor }
        }
    val recentTransactions =
        remember(isLiveBudget, liveTransactions) {
            if (isLiveBudget) {
                liveTransactions.filterNot(TransactionItem::isSplitParent)
            } else {
                liveTransactions.takeIf { it.isNotEmpty() } ?: previewTransactions
            }
        }
    val conflictCount = if (isLiveBudget) mobileSession.pendingWrites.count { it.status == "conflict" } else 0
    val rejectedCount = if (isLiveBudget) mobileSession.pendingWrites.count { it.status == "rejected" } else 0
    val readyToAssignMinor = if (isLiveBudget) liveBudgetMonth?.toBudgetMinor ?: 0 else 124_000L
    val heldForNextMonthMinor = liveBudgetMonth?.heldForNextMonthMinor?.coerceAtLeast(0) ?: 0L
    var nextMonthHoldAmountDraft by rememberSaveable(state.selectedBudgetId, state.selectedMonthId) {
        mutableStateOf(state.formatAmount(readyToAssignMinor.coerceAtLeast(0)))
    }
    val readyToAssignColor =
        when {
            readyToAssignMinor < 0 -> palette.negative
            readyToAssignMinor > 0 -> palette.positive
            else -> palette.muted
        }
    val summaryKey =
        if (isLiveBudget) {
            "live:${state.selectedBudgetId}:summary:${state.selectedMonthId}"
        } else {
            "summary:${state.selectedMonthId}"
        }
    val displayGroups =
        remember(essentialsFirst, groups) {
            if (essentialsFirst) {
                groups
                    .withIndex()
                    .sortedWith(
                        compareBy<IndexedValue<LiveCategoryGroupSummary>>(
                            { essentialGroupOrder(it.value.name) },
                            { it.index },
                        ),
                    ).map { it.value }
            } else {
                groups
            }
        }
    val pinnedCategories =
        remember(groups, state.pinnedCategoryIds) {
            groups.flatMap { group ->
                group.categories.filter { state.isCategoryPinned(it.id) }.map { group to it }
            }
        }

    PageLazyColumn(
        verticalSpacing = sectionSpacing,
        horizontalPadding = if (compact) 16.dp else 20.dp,
        isRefreshing = isLiveBudget && mobileSession.isSyncing,
        onRefresh = onSyncNow.takeIf { isLiveBudget },
    ) {
        if (!isLiveBudget) {
            item(key = "budget-sample-banner") {
                SampleBanner(
                    if (liveTransactions.isNotEmpty()) {
                        "Categories use sample data; activity is synced"
                    } else {
                        "Categories use sample data"
                    },
                )
            }
        }
        if (isLiveBudget) {
            item(key = "budget-conflict-alert") {
                AnimatedReveal(visible = state.conflictReviewAlerts && conflictCount > 0) {
                    AttentionBanner(
                        "$conflictCount change${if (conflictCount == 1) "" else "s"} need${if (conflictCount == 1) "s" else ""} review",
                        "Choose which version of the edit to keep.",
                        "Review changes",
                    ) { navigation.open(AppRoute.CONFLICT_INBOX) }
                }
            }
            item(key = "budget-rejected-alert") {
                AnimatedReveal(visible = state.rejectedEditAlerts && rejectedCount > 0) {
                    AttentionBanner(
                        "$rejectedCount edit${if (rejectedCount == 1) "" else "s"} not applied",
                        "Review the edit.",
                        "Review edits",
                    ) { navigation.open(AppRoute.REJECTED_WRITE) }
                }
            }
        }
        item(key = "budget-overview-title") {
            Heading(
                "Budget overview",
                if (isTrackingBudget) {
                    "Review activity for the selected month."
                } else if (isLiveBudget) {
                    "Review the synced plan and recent activity for the selected month."
                } else {
                    "Review category balances and spending for the selected month."
                },
                compactSubtitle = true,
            )
        }
        item(key = "budget-month-picker") {
            MonthSelectorCard(state.selectedMonth) { navigation.open(AppRoute.MONTH_PICKER) }
        }
        if (isLiveBudget && !isTrackingBudget && liveBudgetMonth != null) {
            item(key = "budget-copy-last-month") {
                SurfaceCard {
                    Column(Modifier.fillMaxWidth().padding(TandemSpacing.small)) {
                        TandemActionButton(
                            "Copy last month",
                            TandemActionButtonTone.NEUTRAL,
                            modifier = Modifier.fillMaxWidth(),
                            icon = Lucide.CalendarDays,
                            enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
                            onClick = {
                                mobileSession.clearBudgetActionMessage()
                                showCopyLastMonthDialog = true
                            },
                        )
                        Spacer(Modifier.height(TandemSpacing.extraSmall))
                        TandemActionButton(
                            "Plan templates",
                            TandemActionButtonTone.NEUTRAL,
                            modifier = Modifier.fillMaxWidth(),
                            icon = Lucide.CalendarDays,
                            enabled = !mobileSession.isPerformingBudgetAction,
                            onClick = {
                                mobileSession.clearBudgetActionMessage()
                                showBudgetTemplatesDialog = true
                            },
                        )
                    }
                }
            }
        }
        if (isTrackingBudget) {
            item(key = "budget-tracking-mode") {
                SurfaceCard {
                    ListRow(
                        "Tracking budget",
                        "Actual does not assign money to categories in this budget.",
                        leading = RowLeading.Icon(Lucide.ChartNoAxesCombined),
                    )
                }
            }
        }
        if (isLiveBudget && liveBudgetMonth == null && !isTrackingBudget) {
            item(key = "budget-month-empty") {
                SurfaceCard {
                    EmptyState(
                        "No plan for this month",
                        "Sync a month available in Actual, or choose a different planning period.",
                        modifier = Modifier.fillMaxWidth().padding(TandemSpacing.large),
                    )
                }
            }
        } else {
            if (!isTrackingBudget) {
                item(key = summaryKey) {
                    val summaryExpanded = !state.isBudgetSectionCollapsed(summaryKey)
                    Column {
                        CollapsibleSectionCard(
                            "${state.selectedMonth} summary",
                            if (isLiveBudget) "${groups.sumOf { it.categories.size }} categories" else "Ready to assign",
                            state.formatCurrency(readyToAssignMinor),
                            actionColor = readyToAssignColor,
                            expanded = summaryExpanded,
                            onToggleExpanded = { state.toggleBudgetSection(summaryKey) },
                        ) {}
                        AnimatedVisibility(
                            visible = summaryExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            SurfaceCard(Modifier.padding(top = sectionSpacing)) {
                                Column {
                                    ListRow(
                                        if (isLiveBudget) "Ready to assign" else "Available this month",
                                        if (isLiveBudget) state.selectedMonth else "On track",
                                        state.formatCurrency(readyToAssignMinor),
                                        leading = RowLeading.Icon(Lucide.WalletCards),
                                        options = ListRowOptions(trailingColor = readyToAssignColor),
                                    )
                                    if (isLiveBudget) {
                                        DividerLine()
                                        ListRow(
                                            "Hold for next month",
                                            trailing = state.formatCurrency(heldForNextMonthMinor),
                                            leading = RowLeading.Icon(Lucide.CalendarDays),
                                            onClick = {
                                                nextMonthHoldAmountDraft = state.formatAmount(readyToAssignMinor.coerceAtLeast(0))
                                                mobileSession.clearBudgetActionMessage()
                                                showNextMonthHoldDialog = true
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (!isLiveBudget && essentialsFirst) {
                item(key = "sample-upcoming-before-groups") { UpcomingBillsCard(state, navigation) }
            }
            if (pinnedCategories.isNotEmpty()) {
                val pinnedSectionKey = if (isLiveBudget) "live:${state.selectedBudgetId}:pinned" else "sample:pinned"
                val pinnedExpanded = !state.isBudgetSectionCollapsed(pinnedSectionKey)
                item(key = "$pinnedSectionKey:section") {
                    Column {
                        CollapsibleSectionCard(
                            "Pinned",
                            "${pinnedCategories.size} categories",
                            expanded = pinnedExpanded,
                            onToggleExpanded = { state.toggleBudgetSection(pinnedSectionKey) },
                        ) {}
                        AnimatedVisibility(
                            visible = pinnedExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            Column(
                                Modifier.padding(top = sectionSpacing),
                                verticalArrangement = Arrangement.spacedBy(sectionSpacing),
                            ) {
                                pinnedCategories.forEach { (group, category) ->
                                    SurfaceCard {
                                        ListRow(
                                            category.name,
                                            group.name,
                                            state.formatCurrency(category.balanceMinor),
                                            leading = RowLeading.Icon(state.categoryIconFor(category.id, category.name)),
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
                }
            }
            displayGroups.forEach { group ->
                val groupSectionKey =
                    if (isLiveBudget) {
                        "live:${state.selectedBudgetId}:group:${group.id}"
                    } else {
                        "sample:${group.id.removePrefix("sample-")}"
                    }
                val groupExpanded = !state.isBudgetSectionCollapsed(groupSectionKey)
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
                item(key = "$groupSectionKey:section") {
                    Column {
                        CollapsibleSectionCard(
                            group.name,
                            if (isTrackingBudget) {
                                "${group.categories.size} categories"
                            } else {
                                "${state.formatCurrency(group.assignedMinor)} assigned · ${group.categories.size} categories"
                            },
                            state.formatCurrency(group.balanceMinor),
                            expanded = groupExpanded,
                            onToggleExpanded = { state.toggleBudgetSection(groupSectionKey) },
                            onClick = {
                                if (isLiveBudget) {
                                    state.selectedCategoryGroupId = group.id
                                    navigation.open(AppRoute.CATEGORY_GROUP)
                                } else {
                                    openPreviewCategoryGroup(state, navigation, group.id)
                                }
                            },
                        ) {}
                        AnimatedVisibility(
                            visible = groupExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            Column(
                                Modifier.padding(top = sectionSpacing),
                                verticalArrangement = Arrangement.spacedBy(sectionSpacing),
                            ) {
                                displayCategories.forEach { category ->
                                    SurfaceCard {
                                        ListRow(
                                            category.name,
                                            "Spent ${state.formatCurrency(
                                                kotlin.math.abs(category.activityMinor),
                                            )} · ${state.formatCurrency(category.balanceMinor)} available",
                                            state.formatCurrency(category.assignedMinor),
                                            leading = RowLeading.Icon(state.categoryIconFor(category.id, category.name)),
                                            options =
                                                ListRowOptions(
                                                    trailingColor = if (category.balanceMinor >= 0) palette.positive else palette.warning,
                                                    dense = compact,
                                                ),
                                        ) {
                                            if (isLiveBudget) {
                                                state.selectedCategoryGroupId = group.id
                                                state.selectedCategoryId = category.id
                                                navigation.open(AppRoute.CATEGORY_DETAIL)
                                            } else {
                                                openPreviewCategory(state, navigation, group.id, category.id)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (!isLiveBudget && !essentialsFirst) {
                item(key = "sample-upcoming-after-groups") { UpcomingBillsCard(state, navigation) }
            }
            if (isLiveBudget && liveSchedules.isNotEmpty()) {
                item(key = "actual-upcoming-schedules") {
                    LiveUpcomingBillsCard(state, navigation, liveSchedules)
                }
            }
        }
        if (recentTransactions.isNotEmpty()) {
            val recentSectionKey =
                if (isLiveBudget) "live:${state.selectedBudgetId}:recent-activity" else "sample:recent-activity"
            val recentExpanded = !state.isBudgetSectionCollapsed(recentSectionKey)
            item(key = "$recentSectionKey:section") {
                Column {
                    CollapsibleSectionCard(
                        "Recent activity",
                        if (isLiveBudget) "${liveTransactions.size} synced transactions" else "Latest budget changes",
                        "See all",
                        onClick = { navigation.selectTab(AppRoute.ACTIVITY) },
                        expanded = recentExpanded,
                        onToggleExpanded = { state.toggleBudgetSection(recentSectionKey) },
                    ) {}
                    AnimatedVisibility(
                        visible = recentExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Column(
                            Modifier.padding(top = sectionSpacing),
                            verticalArrangement = Arrangement.spacedBy(sectionSpacing),
                        ) {
                            recentTransactions.take(if (isLiveBudget) 3 else 2).forEach { transaction ->
                                SurfaceCard {
                                    TransactionRow(transaction) {
                                        openTransaction(
                                            state,
                                            navigation,
                                            transaction,
                                            isLive = isLiveBudget || liveTransactions.isNotEmpty(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!isLiveBudget) {
            item(key = "sample-sync-summary") {
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
            }
        }
        item(key = "budget-bottom-spacer") { Spacer(Modifier.height(72.dp)) }
    }
    if (showCopyLastMonthDialog) {
        Dialog(
            onDismissRequest = { if (!mobileSession.isPerformingBudgetAction) showCopyLastMonthDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 440.dp).padding(horizontal = TandemSpacing.large)) {
                Column(
                    Modifier.fillMaxWidth().padding(TandemSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        Icon(Lucide.CalendarDays, contentDescription = null, modifier = Modifier.size(24.dp), tint = tandemPalette.accent)
                        Text(
                            "Copy last month?",
                            modifier = Modifier.weight(1f),
                            color = tandemPalette.text,
                            fontSize = TandemTypography.sectionTitle,
                            fontWeight = FontWeight.Bold,
                        )
                        TandemIconButton(
                            Lucide.X,
                            "Close",
                            sizeDp = 40,
                            iconSizeDp = 18,
                            enabled = !mobileSession.isPerformingBudgetAction,
                        ) { showCopyLastMonthDialog = false }
                    }
                    Text(
                        "Replace ${state.selectedMonth} category assignments with the previous month’s amounts.",
                        color = tandemPalette.muted,
                        fontSize = TandemTypography.body,
                    )
                    mobileSession.budgetActionMessage?.let { InlineMessage(it, InlineMessageKind.ERROR) }
                    TandemActionButton(
                        if (mobileSession.isPerformingBudgetAction) "Copying…" else "Copy assignments",
                        TandemActionButtonTone.PRIMARY,
                        modifier = Modifier.fillMaxWidth(),
                        icon = Lucide.Check,
                        enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
                        loading = mobileSession.isPerformingBudgetAction,
                    ) {
                        scope.launch {
                            val copied =
                                mobileSession.copyBudgetMonth(
                                    state.selectedBudgetId,
                                    state.selectedMonthId,
                                    state.encryptedPassword.takeIf { it.isNotBlank() },
                                )
                            if (copied) showCopyLastMonthDialog = false
                        }
                    }
                    TandemActionButton(
                        "Cancel",
                        TandemActionButtonTone.NEUTRAL,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !mobileSession.isPerformingBudgetAction,
                    ) { showCopyLastMonthDialog = false }
                }
            }
        }
    }
    if (showBudgetTemplatesDialog && isLiveBudget && !isTrackingBudget) {
        val canSaveCurrentTemplate = currentTemplateAssignments.isNotEmpty() && currentTemplateAssignments.size <= 500
        Dialog(
            onDismissRequest = { if (!mobileSession.isPerformingBudgetAction) showBudgetTemplatesDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 440.dp).padding(horizontal = TandemSpacing.large)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 680.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(TandemSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        Icon(Lucide.CalendarDays, contentDescription = null, modifier = Modifier.size(24.dp), tint = tandemPalette.accent)
                        Text(
                            "Plan templates",
                            modifier = Modifier.weight(1f),
                            color = tandemPalette.text,
                            fontSize = TandemTypography.sectionTitle,
                            fontWeight = FontWeight.Bold,
                        )
                        TandemIconButton(
                            Lucide.X,
                            "Close",
                            sizeDp = 40,
                            iconSizeDp = 18,
                            enabled = !mobileSession.isPerformingBudgetAction,
                        ) { showBudgetTemplatesDialog = false }
                    }
                    TextFieldCard(
                        "Template name",
                        budgetTemplateNameDraft,
                        { budgetTemplateNameDraft = it.take(60) },
                        TextFieldOptions(placeholder = "e.g. Summer plan"),
                    )
                    TandemActionButton(
                        "Save ${state.selectedMonth} plan",
                        TandemActionButtonTone.PRIMARY,
                        modifier = Modifier.fillMaxWidth(),
                        icon = Lucide.Check,
                        enabled =
                            budgetTemplateNameDraft.isNotBlank() &&
                                canSaveCurrentTemplate &&
                                !mobileSession.isPerformingBudgetAction,
                    ) {
                        val saved =
                            decodeBudgetTemplates(state.budgetTemplatesJson)
                                .filterNot { it.budgetId == state.selectedBudgetId && it.name.equals(budgetTemplateNameDraft.trim(), true) }
                        state.budgetTemplatesJson =
                            encodeBudgetTemplates(
                                saved + BudgetTemplate(state.selectedBudgetId, budgetTemplateNameDraft.trim(), currentTemplateAssignments),
                            )
                        budgetTemplateNameDraft = ""
                    }
                    if (currentTemplateAssignments.size > 500) {
                        InlineMessage("This plan has more than 500 categories and cannot be saved as a template.", InlineMessageKind.INFO)
                    }
                    mobileSession.budgetActionMessage?.let { InlineMessage(it, InlineMessageKind.ERROR) }
                    if (currentBudgetTemplates.isEmpty()) {
                        Text("Saved templates will appear here.", color = tandemPalette.muted, fontSize = TandemTypography.body)
                    } else {
                        currentBudgetTemplates.forEach { template ->
                            SurfaceCard {
                                Column(Modifier.fillMaxWidth().padding(TandemSpacing.small)) {
                                    ListRow(
                                        template.name,
                                        subtitle = "${template.assignments.size} categories",
                                        leading = RowLeading.Icon(Lucide.CalendarDays),
                                    )
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                                        TandemActionButton(
                                            if (mobileSession.isPerformingBudgetAction) "Applying…" else "Apply",
                                            TandemActionButtonTone.PRIMARY,
                                            modifier = Modifier.weight(1f),
                                            icon = Lucide.Check,
                                            enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
                                            loading = mobileSession.isPerformingBudgetAction,
                                        ) {
                                            scope.launch {
                                                val applied =
                                                    mobileSession.applyBudgetTemplate(
                                                        state.selectedBudgetId,
                                                        state.selectedMonthId,
                                                        template.assignments,
                                                        state.encryptedPassword.takeIf { it.isNotBlank() },
                                                    )
                                                if (applied) showBudgetTemplatesDialog = false
                                            }
                                        }
                                        TandemIconButton(
                                            Lucide.Trash2,
                                            "Delete ${template.name}",
                                            sizeDp = 48,
                                            iconSizeDp = 20,
                                            tint = tandemPalette.negative,
                                            containerColor = tandemPalette.raised,
                                            enabled = !mobileSession.isPerformingBudgetAction,
                                        ) {
                                            state.budgetTemplatesJson =
                                                encodeBudgetTemplates(
                                                    decodeBudgetTemplates(state.budgetTemplatesJson) - template,
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
    if (showNextMonthHoldDialog && isLiveBudget && !isTrackingBudget) {
        val parsedHoldAmount = state.parseAmountMinorUnits(nextMonthHoldAmountDraft)
        Dialog(
            onDismissRequest = { if (!mobileSession.isPerformingBudgetAction) showNextMonthHoldDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 440.dp).padding(horizontal = TandemSpacing.large)) {
                Column(
                    Modifier.fillMaxWidth().padding(TandemSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        Icon(Lucide.CalendarDays, contentDescription = null, modifier = Modifier.size(24.dp), tint = tandemPalette.accent)
                        Text(
                            "Hold for next month",
                            modifier = Modifier.weight(1f),
                            color = tandemPalette.text,
                            fontSize = TandemTypography.sectionTitle,
                            fontWeight = FontWeight.Bold,
                        )
                        TandemIconButton(
                            Lucide.X,
                            "Close",
                            sizeDp = 40,
                            iconSizeDp = 18,
                            enabled = !mobileSession.isPerformingBudgetAction,
                        ) { showNextMonthHoldDialog = false }
                    }
                    ListRow("Ready to assign", trailing = state.formatCurrency(readyToAssignMinor))
                    ListRow("Held", trailing = state.formatCurrency(heldForNextMonthMinor))
                    TextFieldCard(
                        "Amount to hold",
                        nextMonthHoldAmountDraft,
                        { nextMonthHoldAmountDraft = it },
                        TextFieldOptions(
                            placeholder = state.formatAmount(0),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            textAlign = TextAlign.Center,
                            large = true,
                        ),
                    )
                    mobileSession.budgetActionMessage?.let { InlineMessage(it, InlineMessageKind.ERROR) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        TandemActionButton(
                            if (heldForNextMonthMinor > 0) "Reset hold" else "Close",
                            TandemActionButtonTone.NEUTRAL,
                            Modifier.weight(1f),
                            enabled =
                                !mobileSession.isPerformingBudgetAction &&
                                    (heldForNextMonthMinor == 0L || !state.readOnlyMode),
                            loading = mobileSession.isPerformingBudgetAction && heldForNextMonthMinor > 0,
                        ) {
                            if (heldForNextMonthMinor > 0) {
                                scope.launch {
                                    val reset =
                                        mobileSession.resetBudgetHold(
                                            state.selectedBudgetId,
                                            state.selectedMonthId,
                                            state.encryptedPassword.takeIf { it.isNotBlank() },
                                        )
                                    if (reset) showNextMonthHoldDialog = false
                                }
                            } else {
                                showNextMonthHoldDialog = false
                            }
                        }
                        TandemActionButton(
                            if (mobileSession.isPerformingBudgetAction) "Holding…" else "Hold amount",
                            TandemActionButtonTone.PRIMARY,
                            Modifier.weight(1f),
                            icon = Lucide.Check,
                            enabled =
                                !state.readOnlyMode &&
                                    !mobileSession.isPerformingBudgetAction &&
                                    parsedHoldAmount != null &&
                                    parsedHoldAmount > 0 &&
                                    parsedHoldAmount <= readyToAssignMinor,
                            loading = mobileSession.isPerformingBudgetAction,
                        ) {
                            scope.launch {
                                val held =
                                    mobileSession.holdBudgetForNextMonth(
                                        state.selectedBudgetId,
                                        state.selectedMonthId,
                                        parsedHoldAmount ?: 0L,
                                        state.encryptedPassword.takeIf { it.isNotBlank() },
                                    )
                                if (held) showNextMonthHoldDialog = false
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun openPreviewCategoryGroup(
    state: AppState,
    navigation: AppNavigation,
    groupId: String,
) {
    state.selectedCategoryGroupId = groupId
    state.selectedCategoryId = ""
    navigation.open(AppRoute.CATEGORY_GROUP)
}

private fun openPreviewCategory(
    state: AppState,
    navigation: AppNavigation,
    groupId: String,
    categoryId: String,
) {
    state.selectedCategoryGroupId = groupId
    state.selectedCategoryId = categoryId
    navigation.open(AppRoute.CATEGORY_DETAIL)
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
    CollapsibleSectionCard(
        "Upcoming",
        "Two bills before the end of the month",
        "View all",
        onClick = { openPreviewCategoryGroup(state, navigation, "sample-essentials") },
        expanded = !state.isBudgetSectionCollapsed("sample:upcoming"),
        onToggleExpanded = { state.toggleBudgetSection("sample:upcoming") },
    ) {
        ListRow(
            "Electric Company",
            "Utilities · due ${formatShortDate(SampleDate(2026, 5, 18))}",
            state.formatCurrency(12_000),
            leading = RowLeading.Icon(financeIcon("Utilities")),
            options = ListRowOptions(trailingColor = palette.text),
        )
        DividerLine()
        ListRow(
            "Rent",
            "Essentials · due ${formatShortDate(SampleDate(2026, 6, 1))}",
            state.formatCurrency(150_000),
            leading = RowLeading.Icon(financeIcon("Rent")),
            options = ListRowOptions(trailingColor = palette.text),
        )
    }
}

@Composable
private fun LiveUpcomingBillsCard(
    state: AppState,
    navigation: AppNavigation,
    schedules: List<LiveScheduleSummary>,
) {
    val sectionKey = "live:${state.selectedBudgetId}:upcoming"
    val expanded = !state.isBudgetSectionCollapsed(sectionKey)
    CollapsibleSectionCard(
        "Upcoming bills",
        "${schedules.size} scheduled",
        "See all",
        expanded = expanded,
        onToggleExpanded = { state.toggleBudgetSection(sectionKey) },
        onClick = { navigation.open(AppRoute.SCHEDULES) },
    ) {
        schedules.take(3).forEachIndexed { index, schedule ->
            if (index > 0) DividerLine()
            ListRow(
                schedule.name,
                listOfNotNull(formatLiveDate(schedule.nextDate), schedule.accountName).joinToString(" · "),
                schedule.amountLabel(state),
                leading = RowLeading.Icon(Lucide.CalendarDays),
            ) { navigation.open(AppRoute.SCHEDULES) }
        }
    }
}

@Composable
private fun UpcomingSchedulesScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    schedules: List<LiveScheduleSummary>,
    accounts: List<LiveAccountSummary>,
    payeeRecords: List<BudgetRecordEntity>,
) {
    var selectedScheduleId by rememberSaveable(state.selectedBudgetId) { mutableStateOf<String?>(null) }
    var dialogMode by rememberSaveable(state.selectedBudgetId, selectedScheduleId) { mutableStateOf("menu") }
    var nameDraft by rememberSaveable(state.selectedBudgetId, selectedScheduleId) {
        mutableStateOf(schedules.firstOrNull { it.id == selectedScheduleId }?.name.orEmpty())
    }
    var amountDraft by rememberSaveable(state.selectedBudgetId, selectedScheduleId) {
        val schedule = schedules.firstOrNull { it.id == selectedScheduleId }
        mutableStateOf(state.formatAmount(schedule?.amountMinor ?: schedule?.amountRangeMinor?.first ?: 0L))
    }
    var amountMaxDraft by rememberSaveable(state.selectedBudgetId, selectedScheduleId) {
        val range = schedules.firstOrNull { it.id == selectedScheduleId }?.amountRangeMinor
        mutableStateOf(range?.second?.let(state::formatAmount).orEmpty())
    }
    var amountRuleDraft by rememberSaveable(state.selectedBudgetId, selectedScheduleId) {
        mutableStateOf(schedules.firstOrNull { it.id == selectedScheduleId }?.amountOp ?: "is")
    }
    var accountIdDraft by rememberSaveable(state.selectedBudgetId, selectedScheduleId) {
        mutableStateOf(schedules.firstOrNull { it.id == selectedScheduleId }?.accountId.orEmpty())
    }
    var payeeIdDraft by rememberSaveable(state.selectedBudgetId, selectedScheduleId) {
        mutableStateOf(schedules.firstOrNull { it.id == selectedScheduleId }?.payeeId.orEmpty())
    }
    val selectedSchedule = schedules.firstOrNull { it.id == selectedScheduleId }
    val dateValue =
        remember(selectedSchedule?.id, selectedSchedule?.dateValueJson) {
            selectedSchedule?.dateValueJson?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() }
        }
    val dateConfig = dateValue as? JsonObject
    var dateDraft by rememberSaveable(state.selectedBudgetId, selectedScheduleId) {
        mutableStateOf(
            dateConfig?.get("start")?.jsonPrimitive?.contentOrNull
                ?: (dateValue as? JsonPrimitive)?.contentOrNull
                ?: selectedSchedule?.nextDate.orEmpty(),
        )
    }
    var frequencyDraft by rememberSaveable(state.selectedBudgetId, selectedScheduleId) {
        mutableStateOf(dateConfig?.get("frequency")?.jsonPrimitive?.contentOrNull ?: "once")
    }
    var intervalDraft by rememberSaveable(state.selectedBudgetId, selectedScheduleId) {
        mutableStateOf(dateConfig?.get("interval")?.jsonPrimitive?.contentOrNull ?: "1")
    }
    var skipWeekendDraft by rememberSaveable(state.selectedBudgetId, selectedScheduleId) {
        mutableStateOf(dateConfig?.get("skipWeekend")?.jsonPrimitive?.booleanOrNull ?: false)
    }
    var endModeDraft by rememberSaveable(state.selectedBudgetId, selectedScheduleId) {
        mutableStateOf(dateConfig?.get("endMode")?.jsonPrimitive?.contentOrNull ?: "never")
    }
    var endOccurrencesDraft by rememberSaveable(state.selectedBudgetId, selectedScheduleId) {
        mutableStateOf(dateConfig?.get("endOccurrences")?.jsonPrimitive?.contentOrNull ?: "12")
    }
    var endDateDraft by rememberSaveable(state.selectedBudgetId, selectedScheduleId) {
        mutableStateOf(dateConfig?.get("endDate")?.jsonPrimitive?.contentOrNull ?: selectedSchedule?.nextDate.orEmpty())
    }
    var datePickerTarget by rememberSaveable(state.selectedBudgetId, selectedScheduleId) { mutableStateOf<String?>(null) }
    var editError by rememberSaveable(state.selectedBudgetId, selectedScheduleId) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val payeeOptions =
        remember(payeeRecords, selectedSchedule?.payeeId) {
            val names =
                payeeRecords
                    .mapNotNull { record ->
                        record.recordName()?.let { record.recordId to it }
                    }.toMap()
                    .toMutableMap()
            selectedSchedule?.payeeId?.let { id -> if (id !in names) names[id] = id }
            names.entries.sortedBy { it.value.lowercase() }.map { it.key to it.value }
        }
    val accountOptions =
        remember(accounts) {
            accounts.filterNot(LiveAccountSummary::closed).sortedBy { it.name.lowercase() }.map { it.id to it.name }
        }
    PageLazyColumn {
        item(key = "upcoming-schedules-heading") { Heading("Upcoming bills") }
        if (schedules.isEmpty()) {
            item(key = "upcoming-schedules-empty") {
                SurfaceCard {
                    EmptyState(
                        "No upcoming bills",
                        "Schedules added in Actual will appear here.",
                        modifier = Modifier.fillMaxWidth().padding(TandemSpacing.large),
                    )
                }
            }
        } else {
            items(schedules.size, key = { index -> schedules[index].id }) { index ->
                val schedule = schedules[index]
                SurfaceCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ListRow(
                            schedule.name,
                            listOfNotNull(formatLiveDate(schedule.nextDate), schedule.accountName).joinToString(" · "),
                            schedule.amountLabel(state),
                            leading = RowLeading.Icon(Lucide.CalendarDays),
                            onClick = {
                                selectedScheduleId = schedule.id
                                dialogMode = "menu"
                                mobileSession.clearBudgetActionMessage()
                            },
                        )
                        TandemIconButton(
                            Lucide.ChevronRight,
                            "Schedule actions",
                            modifier = Modifier.padding(end = TandemSpacing.small),
                            sizeDp = 40,
                            iconSizeDp = 18,
                            containerColor = tandemPalette.raised,
                        ) {
                            selectedScheduleId = schedule.id
                            dialogMode = "menu"
                            mobileSession.clearBudgetActionMessage()
                        }
                    }
                }
            }
        }
    }

    if (selectedSchedule != null) {
        Dialog(
            onDismissRequest = {
                if (!mobileSession.isPerformingBudgetAction) {
                    selectedScheduleId = null
                    dialogMode = "menu"
                }
            },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            SurfaceCard(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .padding(horizontal = TandemSpacing.large),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(TandemSpacing.large)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        Icon(
                            if (dialogMode == "edit") Lucide.Pencil else Lucide.CalendarDays,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = tandemPalette.accent,
                        )
                        Text(
                            when {
                                dialogMode == "delete" -> "Delete schedule?"
                                dialogMode == "edit" -> "Edit schedule"
                                else -> selectedSchedule.name
                            },
                            modifier = Modifier.weight(1f),
                            color = tandemPalette.text,
                            fontSize = TandemTypography.sectionTitle,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TandemIconButton(
                            Lucide.X,
                            "Close",
                            sizeDp = 40,
                            iconSizeDp = 18,
                            containerColor = tandemPalette.raised,
                            enabled = !mobileSession.isPerformingBudgetAction,
                        ) {
                            selectedScheduleId = null
                            dialogMode = "menu"
                        }
                    }
                    when {
                        dialogMode == "delete" -> {
                            TandemActionButton(
                                "Delete",
                                TandemActionButtonTone.DESTRUCTIVE,
                                modifier = Modifier.fillMaxWidth(),
                                icon = Lucide.Trash2,
                                enabled = !mobileSession.isPerformingBudgetAction,
                                loading = mobileSession.isPerformingBudgetAction,
                            ) {
                                scope.launch {
                                    val deleted =
                                        mobileSession.manageBudgetSchedule(
                                            state.selectedBudgetId,
                                            selectedSchedule.id,
                                            "delete",
                                            null,
                                            state.encryptedPassword.takeIf { it.isNotBlank() },
                                        )
                                    if (deleted) selectedScheduleId = null
                                }
                            }
                            TandemActionButton(
                                "Keep schedule",
                                TandemActionButtonTone.NEUTRAL,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !mobileSession.isPerformingBudgetAction,
                            ) { dialogMode = "menu" }
                        }
                        dialogMode == "edit" -> {
                            TextFieldCard("Name", nameDraft, { nameDraft = it })
                            TextFieldCard(
                                "Amount",
                                amountDraft,
                                { amountDraft = it },
                                TextFieldOptions(keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)),
                            )
                            ScheduleChoiceField(
                                "Amount rule",
                                when (amountRuleDraft) {
                                    "isapprox" -> "About this amount"
                                    "isbetween" -> "Between two amounts"
                                    else -> "Exact amount"
                                },
                                listOf("is" to "Exact amount", "isapprox" to "About this amount", "isbetween" to "Between two amounts"),
                            ) { amountRuleDraft = it }
                            if (amountRuleDraft == "isbetween") {
                                TextFieldCard(
                                    "Maximum amount",
                                    amountMaxDraft,
                                    { amountMaxDraft = it },
                                    TextFieldOptions(keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)),
                                )
                            }
                            ScheduleChoiceField(
                                "Account",
                                accountOptions.firstOrNull { it.first == accountIdDraft }?.second ?: "Choose account",
                                accountOptions,
                            ) { accountIdDraft = it }
                            ScheduleChoiceField(
                                "Payee",
                                payeeOptions.firstOrNull { it.first == payeeIdDraft }?.second ?: "Choose payee",
                                payeeOptions,
                            ) { payeeIdDraft = it }
                            ScheduleChoiceField(
                                if (frequencyDraft == "once") "Repeats" else "Repeats every",
                                when (frequencyDraft) {
                                    "daily" -> "Daily"
                                    "weekly" -> "Weekly"
                                    "monthly" -> "Monthly"
                                    "yearly" -> "Yearly"
                                    else -> "One time"
                                },
                                listOf(
                                    "once" to "One time",
                                    "daily" to "Daily",
                                    "weekly" to "Weekly",
                                    "monthly" to "Monthly",
                                    "yearly" to "Yearly",
                                ),
                            ) { frequencyDraft = it }
                            if (frequencyDraft != "once") {
                                TextFieldCard(
                                    "Interval",
                                    intervalDraft,
                                    { intervalDraft = it.filter(Char::isDigit).take(3) },
                                    TextFieldOptions(
                                        placeholder = "1",
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    ),
                                )
                            }
                            FieldFrame(
                                if (frequencyDraft == "once") "Date" else "Start date",
                                onClickLabel = "Choose date",
                                onClick = { datePickerTarget = "start" },
                            ) {
                                Text(
                                    formatLiveDate(dateDraft),
                                    Modifier.weight(1f),
                                    color = tandemPalette.text,
                                    fontSize = TandemTypography.action,
                                )
                                Icon(
                                    Lucide.ChevronDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = tandemPalette.muted,
                                )
                            }
                            if (frequencyDraft != "once") {
                                ToggleRow("Skip weekends", checked = skipWeekendDraft) { skipWeekendDraft = it }
                                ScheduleChoiceField(
                                    "Ends",
                                    when (endModeDraft) {
                                        "after_n_occurrences" -> "After a number of repeats"
                                        "on_date" -> "On a date"
                                        else -> "Never"
                                    },
                                    listOf(
                                        "never" to "Never",
                                        "after_n_occurrences" to "After a number of repeats",
                                        "on_date" to "On a date",
                                    ),
                                ) { endModeDraft = it }
                                if (endModeDraft == "after_n_occurrences") {
                                    TextFieldCard(
                                        "Number of repeats",
                                        endOccurrencesDraft,
                                        { endOccurrencesDraft = it.filter(Char::isDigit).take(6) },
                                        TextFieldOptions(keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)),
                                    )
                                }
                                if (endModeDraft == "on_date") {
                                    FieldFrame(
                                        "End date",
                                        onClickLabel = "Choose end date",
                                        onClick = { datePickerTarget = "end" },
                                    ) {
                                        Text(
                                            formatLiveDate(endDateDraft),
                                            Modifier.weight(1f),
                                            color = tandemPalette.text,
                                            fontSize = TandemTypography.action,
                                        )
                                        Icon(
                                            Lucide.ChevronDown,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = tandemPalette.muted,
                                        )
                                    }
                                }
                            }
                            editError?.let {
                                InlineMessage(it, InlineMessageKind.ERROR)
                            }
                            mobileSession.budgetActionMessage?.let {
                                InlineMessage(it, InlineMessageKind.ERROR)
                            }
                            TandemActionButton(
                                if (mobileSession.isPerformingBudgetAction) {
                                    "Saving…"
                                } else {
                                    "Save schedule"
                                },
                                TandemActionButtonTone.PRIMARY,
                                modifier = Modifier.fillMaxWidth(),
                                enabled =
                                    !mobileSession.isPerformingBudgetAction &&
                                        nameDraft.isNotBlank() &&
                                        accountIdDraft.isNotBlank() &&
                                        payeeIdDraft.isNotBlank(),
                                loading = mobileSession.isPerformingBudgetAction,
                            ) {
                                val amountMinor = state.parseAmountMinorUnits(amountDraft)
                                val amountMaxMinor = state.parseAmountMinorUnits(amountMaxDraft)
                                val interval = intervalDraft.toIntOrNull()
                                val occurrences = endOccurrencesDraft.toIntOrNull()
                                val dateIsValid = parseIsoDate(dateDraft) != null
                                val endDateIsValid = endModeDraft != "on_date" || parseIsoDate(endDateDraft) != null
                                val amountRangeIsInvalid =
                                    amountRuleDraft == "isbetween" &&
                                        amountMinor != null &&
                                        amountMaxMinor != null &&
                                        amountMinor > amountMaxMinor
                                val intervalIsInvalid =
                                    frequencyDraft != "once" && (interval == null || interval !in 1..999)
                                val repeatCountIsInvalid =
                                    frequencyDraft != "once" &&
                                        endModeDraft == "after_n_occurrences" &&
                                        (occurrences == null || occurrences !in 1..100_000)
                                val endBeforeStart = endModeDraft == "on_date" && endDateDraft < dateDraft
                                editError =
                                    when {
                                        amountMinor == null -> "Enter a valid amount."
                                        amountRuleDraft == "isbetween" && amountMaxMinor == null ->
                                            "Enter a valid maximum amount."
                                        amountRangeIsInvalid -> "The minimum must not exceed the maximum."
                                        intervalIsInvalid -> "Repeat interval must be between 1 and 999."
                                        repeatCountIsInvalid -> "Enter a valid number of repeats."
                                        !dateIsValid -> "Choose a valid start date."
                                        !endDateIsValid -> "Choose a valid end date."
                                        endBeforeStart -> "The end date must be on or after the start date."
                                        else -> null
                                    }
                                if (editError == null) {
                                    val editedDate =
                                        if (frequencyDraft == "once") {
                                            JsonPrimitive(dateDraft) as JsonElement
                                        } else {
                                            buildJsonObject {
                                                dateConfig?.forEach { (key, value) ->
                                                    if (key !in
                                                        setOf(
                                                            "frequency",
                                                            "interval",
                                                            "start",
                                                            "skipWeekend",
                                                            "endMode",
                                                            "endOccurrences",
                                                            "endDate",
                                                        )
                                                    ) {
                                                        put(key, value)
                                                    }
                                                }
                                                put("frequency", frequencyDraft)
                                                put("interval", interval ?: 1)
                                                put("start", dateDraft)
                                                put("skipWeekend", skipWeekendDraft)
                                                put("endMode", endModeDraft)
                                                if (endModeDraft == "after_n_occurrences") put("endOccurrences", occurrences ?: 1)
                                                if (endModeDraft == "on_date") put("endDate", endDateDraft)
                                            }
                                        }
                                    val fields =
                                        buildJsonObject {
                                            put("name", nameDraft.trim())
                                            put("accountId", accountIdDraft)
                                            put("payeeId", payeeIdDraft)
                                            put("amountOp", amountRuleDraft)
                                            if (amountRuleDraft == "isbetween") {
                                                put(
                                                    "amountRangeMinor",
                                                    buildJsonObject {
                                                        put("num1", amountMinor ?: 0L)
                                                        put("num2", amountMaxMinor ?: 0L)
                                                    },
                                                )
                                            } else {
                                                put("amountMinor", amountMinor ?: 0L)
                                            }
                                            put("date", editedDate)
                                        }
                                    scope.launch {
                                        val saved =
                                            mobileSession.manageBudgetSchedule(
                                                state.selectedBudgetId,
                                                selectedSchedule.id,
                                                "edit",
                                                null,
                                                state.encryptedPassword.takeIf { it.isNotBlank() },
                                                fields,
                                            )
                                        if (saved) selectedScheduleId = null
                                    }
                                }
                            }
                            TandemActionButton(
                                "Cancel",
                                TandemActionButtonTone.NEUTRAL,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !mobileSession.isPerformingBudgetAction,
                            ) { dialogMode = "menu" }
                        }
                        else -> {
                            mobileSession.budgetActionMessage?.let { InlineMessage(it, InlineMessageKind.ERROR) }
                            TandemActionButton(
                                "Post now",
                                TandemActionButtonTone.PRIMARY,
                                modifier = Modifier.fillMaxWidth(),
                                icon = Lucide.CalendarDays,
                                enabled = !mobileSession.isPerformingBudgetAction,
                                loading = mobileSession.isPerformingBudgetAction,
                            ) {
                                scope.launch {
                                    val posted =
                                        mobileSession.manageBudgetSchedule(
                                            state.selectedBudgetId,
                                            selectedSchedule.id,
                                            "post",
                                            null,
                                            state.encryptedPassword.takeIf { it.isNotBlank() },
                                        )
                                    if (posted) selectedScheduleId = null
                                }
                            }
                            if (selectedSchedule.canSkip) {
                                TandemActionButton(
                                    "Skip next date",
                                    TandemActionButtonTone.NEUTRAL,
                                    modifier = Modifier.fillMaxWidth(),
                                    icon = Lucide.CalendarDays,
                                    enabled = !mobileSession.isPerformingBudgetAction,
                                    loading = mobileSession.isPerformingBudgetAction,
                                ) {
                                    scope.launch {
                                        val skipped =
                                            mobileSession.manageBudgetSchedule(
                                                state.selectedBudgetId,
                                                selectedSchedule.id,
                                                "skip",
                                                null,
                                                state.encryptedPassword.takeIf { it.isNotBlank() },
                                            )
                                        if (skipped) selectedScheduleId = null
                                    }
                                }
                            }
                            TandemActionButton(
                                "Edit schedule",
                                TandemActionButtonTone.NEUTRAL,
                                modifier = Modifier.fillMaxWidth(),
                                icon = Lucide.Pencil,
                                enabled = !mobileSession.isPerformingBudgetAction,
                            ) {
                                mobileSession.clearBudgetActionMessage()
                                dialogMode = "edit"
                            }
                            TandemActionButton(
                                "Delete",
                                TandemActionButtonTone.DESTRUCTIVE,
                                modifier = Modifier.fillMaxWidth(),
                                icon = Lucide.Trash2,
                                enabled = !mobileSession.isPerformingBudgetAction,
                            ) { dialogMode = "delete" }
                        }
                    }
                }
            }
        }
    }
    if (datePickerTarget != null) {
        ScheduleDatePickerDialog(
            initialDate = if (datePickerTarget == "end") endDateDraft else dateDraft,
            onDismiss = { datePickerTarget = null },
            onSelect = { value ->
                if (datePickerTarget == "end") endDateDraft = value else dateDraft = value
                datePickerTarget = null
            },
        )
    }
}

@Composable
private fun ScheduleChoiceField(
    label: String,
    selectedLabel: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var expanded by rememberSaveable(label) { mutableStateOf(false) }
    FieldFrame(label, onClickLabel = "Choose $label", onClick = { expanded = true }) {
        Text(
            selectedLabel,
            Modifier.weight(1f),
            color = tandemPalette.text,
            fontSize = TandemTypography.action,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(Lucide.ChevronDown, contentDescription = null, modifier = Modifier.size(18.dp), tint = tandemPalette.muted)
    }
    if (expanded) {
        Dialog(onDismissRequest = { expanded = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 420.dp).padding(horizontal = TandemSpacing.large)) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(TandemSpacing.medium)) {
                    options.forEach { (id, name) ->
                        ListRow(
                            name,
                            trailing = if (name == selectedLabel) "✓" else null,
                            onClick = {
                                onSelect(id)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleDatePickerDialog(
    initialDate: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val selectedDate = parseIsoDate(initialDate) ?: parseIsoDate(platformTodayIsoDate()) ?: Triple(2026, 1, 1)
    var visibleYear by rememberSaveable(initialDate) { mutableStateOf(selectedDate.first) }
    var visibleMonth by rememberSaveable(initialDate) { mutableStateOf(selectedDate.second) }
    val firstDayOffset = (platformWeekday(visibleYear, visibleMonth, 1) - platformFirstWeekday() + 7) % 7
    val cells = List<Int?>(firstDayOffset) { null } + (1..daysInMonth(visibleYear, visibleMonth)).map { it }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 420.dp).padding(horizontal = TandemSpacing.large)) {
            Column(
                Modifier.fillMaxWidth().padding(TandemSpacing.large),
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TandemIconButton(Lucide.ChevronLeft, "Previous month", sizeDp = 40, iconSizeDp = 18) {
                        if (visibleMonth == 1) {
                            visibleMonth = 12
                            visibleYear -= 1
                        } else {
                            visibleMonth -= 1
                        }
                    }
                    Text(
                        formatMonth(visibleYear, visibleMonth),
                        Modifier.weight(1f),
                        color = tandemPalette.text,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TandemIconButton(Lucide.ChevronRight, "Next month", sizeDp = 40, iconSizeDp = 18) {
                        if (visibleMonth == 12) {
                            visibleMonth = 1
                            visibleYear += 1
                        } else {
                            visibleMonth += 1
                        }
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    (0..6).forEach { column ->
                        val weekday = formatWeekday(visibleYear, visibleMonth, 1 + ((column - firstDayOffset + 7) % 7))
                        Text(
                            weekday.take(1),
                            Modifier.weight(1f),
                            color = tandemPalette.muted,
                            textAlign = TextAlign.Center,
                            fontSize = TandemTypography.caption,
                        )
                    }
                }
                cells.chunked(7).forEach { week ->
                    Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
                        week.forEach { day ->
                            if (day == null) {
                                Spacer(Modifier.weight(1f).height(44.dp))
                            } else {
                                val value = transactionIsoDate(visibleYear, visibleMonth, day)
                                val isSelected = value == initialDate
                                SelectableTile(
                                    selected = isSelected,
                                    contentDescription = displayTransactionDate(value),
                                    onSelect = { onSelect(value) },
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    shape = CircleShape,
                                    selectedBackground = tandemPalette.accentSurface,
                                    unselectedBackground = Color.Transparent,
                                ) {
                                    Text(
                                        day.toString(),
                                        color = if (isSelected) tandemPalette.accent else tandemPalette.text,
                                        fontSize = TandemTypography.label,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                        repeat(7 - week.size) { Spacer(Modifier.weight(1f).height(44.dp)) }
                    }
                }
                TandemActionButton("Cancel", TandemActionButtonTone.NEUTRAL, modifier = Modifier.fillMaxWidth(), onClick = onDismiss)
            }
        }
    }
}

private fun defaultCategoryRulesByPayee(rules: List<BudgetRecordEntity>): Map<String, List<String>> {
    val categoriesByPayee = mutableMapOf<String, MutableSet<String>>()
    rules.forEach { record ->
        if (record.localDeleted || record.remoteDeleted) return@forEach
        val body = record.recordBody() ?: return@forEach
        if (body.booleanValue("tombstone") == true) return@forEach
        val stage = body["stage"]?.jsonPrimitive?.contentOrNull
        if (stage != null && stage != "default") return@forEach
        if (body.stringValue("conditionsOp") != "and") return@forEach
        val conditions = (body["conditions"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
        if (conditions.size != 1) return@forEach
        val condition = conditions.single()
        if (condition.stringValue("field") != "payee" || condition.stringValue("op") != "is") {
            return@forEach
        }
        val payeeId = condition["value"]?.jsonPrimitive?.contentOrNull ?: return@forEach
        val actions = (body["actions"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
        actions.forEach { action ->
            if (action.stringValue("field") == "category" && action.stringValue("op") == "set") {
                val categoryId = action["value"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                categoriesByPayee.getOrPut(payeeId, ::mutableSetOf).add(categoryId)
            }
        }
    }
    return categoriesByPayee.mapValues { (_, categories) -> categories.sorted() }
}

@Composable
private fun ManagePayeesScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    payeeRecords: List<BudgetRecordEntity>,
    categoryRecords: List<BudgetRecordEntity>,
    ruleRecords: List<BudgetRecordEntity>,
) {
    var selectedPayeeId by rememberSaveable(state.selectedBudgetId) { mutableStateOf<String?>(null) }
    var mode by rememberSaveable(state.selectedBudgetId) { mutableStateOf("menu") }
    var nameDraft by rememberSaveable(state.selectedBudgetId, selectedPayeeId) { mutableStateOf("") }
    var mergeTargetId by rememberSaveable(state.selectedBudgetId, selectedPayeeId) { mutableStateOf<String?>(null) }
    var defaultCategoryDraft by rememberSaveable(state.selectedBudgetId, selectedPayeeId) { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val payees =
        remember(payeeRecords) {
            payeeRecords
                .filterNot { record ->
                    val body = record.recordBody()
                    record.localDeleted ||
                        record.remoteDeleted ||
                        body?.booleanValue("tombstone") == true ||
                        body?.stringValue("transfer_acct") != null
                }.mapNotNull { record -> record.recordName()?.let { record.recordId to it } }
                .distinctBy { it.first }
                .sortedBy { it.second.lowercase() }
        }
    val defaultCategories = remember(ruleRecords) { defaultCategoryRulesByPayee(ruleRecords) }
    val categoryOptions =
        remember(categoryRecords) {
            listOf("" to "No default") +
                categoryRecords
                    .filterNot { record ->
                        record.localDeleted ||
                            record.remoteDeleted ||
                            record.recordBody()?.booleanValue("tombstone") == true ||
                            record.recordBody()?.booleanValue("hidden") == true
                    }.mapNotNull { record -> record.recordName()?.let { record.recordId to it } }
                    .sortedBy { it.second.lowercase() }
        }
    val selectedPayee = payees.firstOrNull { it.first == selectedPayeeId }
    val mergeTarget = payees.firstOrNull { it.first == mergeTargetId }
    val selectedDefaultCategories = selectedPayeeId?.let(defaultCategories::get).orEmpty()
    val selectedDefaultCategoryId = selectedDefaultCategories.singleOrNull()
    val selectedDefaultCategoryLabel =
        when {
            selectedDefaultCategories.size > 1 -> "Multiple rules"
            else -> categoryOptions.firstOrNull { it.first == selectedDefaultCategoryId }?.second ?: "No default"
        }

    PageLazyColumn {
        item(key = "manage-payees-heading") { Heading("Payees") }
        if (payees.isEmpty()) {
            item(key = "manage-payees-empty") {
                SurfaceCard {
                    EmptyState(
                        "No payees",
                        "Add a payee when entering a transaction.",
                        modifier = Modifier.fillMaxWidth().padding(TandemSpacing.large),
                    )
                }
            }
        } else {
            items(payees.size, key = { index -> payees[index].first }) { index ->
                val (payeeId, name) = payees[index]
                SurfaceCard {
                    ListRow(
                        name,
                        leading = RowLeading.Icon(Lucide.Tags),
                        onClick = {
                            selectedPayeeId = payeeId
                            nameDraft = name
                            mergeTargetId = null
                            defaultCategoryDraft = defaultCategories[payeeId]?.singleOrNull().orEmpty()
                            mode = "menu"
                            mobileSession.clearBudgetActionMessage()
                        },
                    )
                }
            }
        }
    }

    if (selectedPayee != null) {
        Dialog(
            onDismissRequest = {
                if (!mobileSession.isPerformingBudgetAction) selectedPayeeId = null
            },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            SurfaceCard(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .padding(horizontal = TandemSpacing.large),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 620.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(TandemSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                    ) {
                        Icon(
                            if (mode == "rename") Lucide.Pencil else Lucide.Tags,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = tandemPalette.accent,
                        )
                        Text(
                            when (mode) {
                                "rename" -> "Rename payee"
                                "merge", "confirm-merge" -> "Merge payee"
                                "default-category" -> "Default category"
                                "delete" -> "Delete payee?"
                                else -> selectedPayee.second
                            },
                            modifier = Modifier.weight(1f),
                            color = tandemPalette.text,
                            fontSize = TandemTypography.sectionTitle,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TandemIconButton(
                            Lucide.X,
                            "Close",
                            sizeDp = 40,
                            iconSizeDp = 18,
                            containerColor = tandemPalette.raised,
                            enabled = !mobileSession.isPerformingBudgetAction,
                        ) { selectedPayeeId = null }
                    }
                    when (mode) {
                        "default-category" -> {
                            ScheduleChoiceField(
                                "Category",
                                categoryOptions.firstOrNull { it.first == defaultCategoryDraft }?.second ?: "No default",
                                categoryOptions,
                            ) { defaultCategoryDraft = it }
                            mobileSession.budgetActionMessage?.let {
                                InlineMessage(it, InlineMessageKind.ERROR)
                            }
                            TandemActionButton(
                                if (mobileSession.isPerformingBudgetAction) "Saving…" else "Save",
                                TandemActionButtonTone.PRIMARY,
                                modifier = Modifier.fillMaxWidth(),
                                icon = Lucide.Check,
                                enabled =
                                    !state.readOnlyMode &&
                                        !mobileSession.isPerformingBudgetAction &&
                                        selectedDefaultCategories.size <= 1 &&
                                        defaultCategoryDraft != selectedDefaultCategoryId.orEmpty(),
                                loading = mobileSession.isPerformingBudgetAction,
                            ) {
                                scope.launch {
                                    val saved =
                                        mobileSession.manageBudgetPayee(
                                            state.selectedBudgetId,
                                            selectedPayee.first,
                                            "default-category",
                                            null,
                                            null,
                                            state.encryptedPassword.takeIf { it.isNotBlank() },
                                            defaultCategoryDraft.ifBlank { null },
                                        )
                                    if (saved) selectedPayeeId = null
                                }
                            }
                            TandemActionButton(
                                "Back",
                                TandemActionButtonTone.NEUTRAL,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !mobileSession.isPerformingBudgetAction,
                            ) { mode = "menu" }
                        }
                        "rename" -> {
                            TextFieldCard("Name", nameDraft, { nameDraft = it })
                            mobileSession.budgetActionMessage?.let { InlineMessage(it, InlineMessageKind.ERROR) }
                            TandemActionButton(
                                if (mobileSession.isPerformingBudgetAction) "Saving…" else "Save",
                                TandemActionButtonTone.PRIMARY,
                                modifier = Modifier.fillMaxWidth(),
                                icon = Lucide.Check,
                                enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction && nameDraft.isNotBlank(),
                                loading = mobileSession.isPerformingBudgetAction,
                            ) {
                                scope.launch {
                                    val saved =
                                        mobileSession.manageBudgetPayee(
                                            state.selectedBudgetId,
                                            selectedPayee.first,
                                            "rename",
                                            nameDraft,
                                            null,
                                            state.encryptedPassword.takeIf { it.isNotBlank() },
                                        )
                                    if (saved) selectedPayeeId = null
                                }
                            }
                            TandemActionButton(
                                "Back",
                                TandemActionButtonTone.NEUTRAL,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !mobileSession.isPerformingBudgetAction,
                            ) { mode = "menu" }
                        }
                        "merge" -> {
                            Text("Choose the payee to keep.", color = tandemPalette.muted, fontSize = TandemTypography.body)
                            payees.filterNot { it.first == selectedPayee.first }.forEach { target ->
                                SurfaceCard {
                                    ListRow(
                                        target.second,
                                        leading = RowLeading.Icon(Lucide.Tags),
                                        onClick = {
                                            mergeTargetId = target.first
                                            mode = "confirm-merge"
                                        },
                                    )
                                }
                            }
                            TandemActionButton(
                                "Back",
                                TandemActionButtonTone.NEUTRAL,
                                modifier = Modifier.fillMaxWidth(),
                            ) { mode = "menu" }
                        }
                        "confirm-merge" -> {
                            Text(
                                "Move transactions to ${mergeTarget?.second ?: "the selected payee"}?",
                                color = tandemPalette.muted,
                                fontSize = TandemTypography.body,
                            )
                            mobileSession.budgetActionMessage?.let { InlineMessage(it, InlineMessageKind.ERROR) }
                            TandemActionButton(
                                if (mobileSession.isPerformingBudgetAction) "Merging…" else "Merge payees",
                                TandemActionButtonTone.PRIMARY,
                                modifier = Modifier.fillMaxWidth(),
                                icon = Lucide.Tags,
                                enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction && mergeTarget != null,
                                loading = mobileSession.isPerformingBudgetAction,
                            ) {
                                scope.launch {
                                    val merged =
                                        mobileSession.manageBudgetPayee(
                                            state.selectedBudgetId,
                                            selectedPayee.first,
                                            "merge",
                                            null,
                                            mergeTarget?.first,
                                            state.encryptedPassword.takeIf { it.isNotBlank() },
                                        )
                                    if (merged) selectedPayeeId = null
                                }
                            }
                            TandemActionButton(
                                "Choose another",
                                TandemActionButtonTone.NEUTRAL,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !mobileSession.isPerformingBudgetAction,
                            ) { mode = "merge" }
                        }
                        "delete" -> {
                            mobileSession.budgetActionMessage?.let { InlineMessage(it, InlineMessageKind.ERROR) }
                            TandemActionButton(
                                if (mobileSession.isPerformingBudgetAction) "Deleting…" else "Delete payee",
                                TandemActionButtonTone.DESTRUCTIVE,
                                modifier = Modifier.fillMaxWidth(),
                                icon = Lucide.Trash2,
                                enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
                                loading = mobileSession.isPerformingBudgetAction,
                            ) {
                                scope.launch {
                                    val deleted =
                                        mobileSession.manageBudgetPayee(
                                            state.selectedBudgetId,
                                            selectedPayee.first,
                                            "delete",
                                            null,
                                            null,
                                            state.encryptedPassword.takeIf { it.isNotBlank() },
                                        )
                                    if (deleted) selectedPayeeId = null
                                }
                            }
                            TandemActionButton(
                                "Keep payee",
                                TandemActionButtonTone.NEUTRAL,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !mobileSession.isPerformingBudgetAction,
                            ) { mode = "menu" }
                        }
                        else -> {
                            mobileSession.budgetActionMessage?.let { InlineMessage(it, InlineMessageKind.ERROR) }
                            SurfaceCard {
                                ListRow(
                                    "Default category",
                                    selectedDefaultCategoryLabel,
                                    leading = RowLeading.Icon(Lucide.Tags),
                                    onClick = {
                                        defaultCategoryDraft = selectedDefaultCategoryId.orEmpty()
                                        mobileSession.clearBudgetActionMessage()
                                        mode = "default-category"
                                    },
                                )
                            }
                            TandemActionButton(
                                "Rename",
                                TandemActionButtonTone.NEUTRAL,
                                modifier = Modifier.fillMaxWidth(),
                                icon = Lucide.Pencil,
                                enabled = !state.readOnlyMode,
                            ) {
                                nameDraft = selectedPayee.second
                                mobileSession.clearBudgetActionMessage()
                                mode = "rename"
                            }
                            TandemActionButton(
                                "Merge into another",
                                TandemActionButtonTone.NEUTRAL,
                                modifier = Modifier.fillMaxWidth(),
                                icon = Lucide.Tags,
                                enabled = !state.readOnlyMode && payees.size > 1,
                            ) {
                                mobileSession.clearBudgetActionMessage()
                                mode = "merge"
                            }
                            TandemActionButton(
                                "Delete",
                                TandemActionButtonTone.DESTRUCTIVE,
                                modifier = Modifier.fillMaxWidth(),
                                icon = Lucide.Trash2,
                                enabled = !state.readOnlyMode,
                            ) {
                                mobileSession.clearBudgetActionMessage()
                                mode = "delete"
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetRulesScreen(
    rules: List<BudgetRecordEntity>,
    accountRecords: List<BudgetRecordEntity>,
    categoryRecords: List<BudgetRecordEntity>,
    categoryGroupRecords: List<BudgetRecordEntity>,
    payeeRecords: List<BudgetRecordEntity>,
) {
    val namesByField =
        remember(accountRecords, categoryRecords, categoryGroupRecords, payeeRecords) {
            mapOf(
                "account" to accountRecords.mapNotNull { it.recordName()?.let { name -> it.recordId to name } }.toMap(),
                "category" to categoryRecords.mapNotNull { it.recordName()?.let { name -> it.recordId to name } }.toMap(),
                "category_group" to categoryGroupRecords.mapNotNull { it.recordName()?.let { name -> it.recordId to name } }.toMap(),
                "payee" to payeeRecords.mapNotNull { it.recordName()?.let { name -> it.recordId to name } }.toMap(),
            )
        }
    val activeRules = remember(rules) { rules.filterNot { it.recordBody()?.booleanValue("tombstone") == true } }
    PageLazyColumn {
        item(key = "budget-rules-title") { Heading("Budget rules") }
        if (activeRules.isEmpty()) {
            item(key = "budget-rules-empty") {
                SurfaceCard {
                    EmptyState(
                        "No rules",
                        "Rules created in Actual will appear here.",
                        modifier = Modifier.fillMaxWidth().padding(TandemSpacing.large),
                    )
                }
            }
        } else {
            items(activeRules.size, key = { index -> activeRules[index].recordId }) { index ->
                val body = activeRules[index].recordBody()
                val conditions = (body?.get("conditions") as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
                val actions = (body?.get("actions") as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
                val conditionSummary =
                    conditions
                        .joinToString(" · ") { condition ->
                            val field = condition.stringValue("field")?.replace('_', ' ') ?: "Field"
                            val operation = condition.stringValue("op")?.replace('_', ' ') ?: "matches"
                            val rawValue =
                                condition["value"]
                                    ?.let { value -> (value as? JsonPrimitive)?.contentOrNull ?: value.toString() }
                                    ?.trim('"')
                            val mappedValue =
                                rawValue?.let { value -> namesByField[condition.stringValue("field")]?.get(value) ?: value }
                            listOfNotNull(field, operation, mappedValue).joinToString(" ")
                        }.ifBlank { "Every transaction" }
                val actionSummary =
                    actions
                        .joinToString(" · ") { action ->
                            val field = action.stringValue("field")?.replace('_', ' ')
                            val operation = action.stringValue("op")
                            val rawValue =
                                action["value"]
                                    ?.let { value -> (value as? JsonPrimitive)?.contentOrNull ?: value.toString() }
                                    ?.trim('"')
                            val mappedValue = rawValue?.let { value -> namesByField[field?.replace(' ', '_')]?.get(value) ?: value }
                            when (operation) {
                                "append-notes" -> "Append note"
                                "prepend-notes" -> "Add note"
                                "delete-transaction" -> "Delete transaction"
                                "link-schedule" -> "Link schedule"
                                else -> listOfNotNull(field, mappedValue).joinToString(": ")
                            }
                        }.ifBlank { "No actions" }
                val stage =
                    when (body?.stringValue("stage")) {
                        "pre" -> "Before import"
                        "post" -> "After import"
                        else -> "All stages"
                    }
                SurfaceCard {
                    ListRow(
                        conditionSummary,
                        actionSummary,
                        stage,
                        leading = RowLeading.Icon(Lucide.Tags),
                        options = ListRowOptions(trailingColor = tandemPalette.muted),
                    )
                }
            }
        }
    }
}

private fun LiveScheduleSummary.amountLabel(state: AppState): String =
    when {
        amountMinor != null -> state.formatCurrency(amountMinor)
        amountRangeMinor != null -> "${state.formatCurrency(amountRangeMinor.first)}–${state.formatCurrency(amountRangeMinor.second)}"
        else -> "Variable"
    }

@Composable
private fun TransactionRow(
    transaction: TransactionItem,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
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
            transaction.payee,
            transaction.category,
            formatSignedCurrency(transaction.amountMinorUnits, transaction.displaySettings),
            leading =
                if (selectionMode) {
                    RowLeading.Icon(if (selected) Lucide.Check else Lucide.CircleDot)
                } else {
                    RowLeading.Merchant(transaction.merchantIcon, fallback = transaction.categoryIcon ?: Lucide.Receipt)
                },
            options = ListRowOptions(trailingColor = if (transaction.amountMinorUnits > 0) palette.positive else palette.text),
            onClick = onClick,
            onLongClick = onLongClick,
        )
    }
}

@Composable
private fun TransactionSelectionActions(
    selectedCount: Int,
    allSelected: Boolean,
    onToggleAll: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    SurfaceCard {
        Column(
            Modifier.fillMaxWidth().padding(TandemSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
        ) {
            Text(
                "$selectedCount selected",
                color = tandemPalette.text,
                fontSize = TandemTypography.action,
                fontWeight = FontWeight.SemiBold,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                TandemActionButton(
                    if (allSelected) "Deselect all" else "Select all",
                    TandemActionButtonTone.NEUTRAL,
                    Modifier.weight(1f),
                    icon = if (allSelected) Lucide.CircleDot else Lucide.Check,
                    onClick = onToggleAll,
                )
                TandemActionButton(
                    "Delete",
                    TandemActionButtonTone.DESTRUCTIVE,
                    Modifier.weight(1f),
                    icon = Lucide.Trash2,
                    enabled = selectedCount > 0,
                    onClick = onDelete,
                )
                TandemIconButton(
                    Lucide.X,
                    "Cancel selection",
                    containerColor = tandemPalette.raised,
                    onClick = onCancel,
                )
            }
        }
    }
}

@Composable
private fun TransactionBulkDeleteDialog(
    selectedCount: Int,
    isDeleting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 440.dp).padding(horizontal = 16.dp)) {
            Column(
                Modifier.fillMaxWidth().padding(TandemSpacing.large),
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    Icon(Lucide.Trash2, contentDescription = null, modifier = Modifier.size(22.dp), tint = tandemPalette.negative)
                    Text(
                        if (selectedCount == 1) "Delete this transaction?" else "Delete $selectedCount transactions?",
                        modifier = Modifier.weight(1f),
                        color = tandemPalette.text,
                        fontSize = TandemTypography.cardTitle,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    TandemActionButton(
                        "Cancel",
                        TandemActionButtonTone.NEUTRAL,
                        Modifier.weight(1f),
                        enabled = !isDeleting,
                        onClick = onDismiss,
                    )
                    TandemActionButton(
                        if (isDeleting) "Deleting…" else "Delete",
                        TandemActionButtonTone.DESTRUCTIVE,
                        Modifier.weight(1f),
                        icon = Lucide.Trash2,
                        enabled = !isDeleting,
                        loading = isDeleting,
                        onClick = onConfirm,
                    )
                }
            }
        }
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
    state.transactionDate = transaction.dateIso.ifBlank { transaction.dateLabel }
    state.newTransactionAccountId = transaction.accountId.orEmpty()
    state.newTransactionPayeeId = transaction.payeeId.orEmpty()
    state.newTransactionCategoryId = transaction.categoryId.orEmpty()
    state.selectedTransactionAccountId = transaction.accountId.orEmpty()
    state.selectedTransactionPayeeId = transaction.payeeId.orEmpty()
    state.selectedTransactionCategoryId = transaction.categoryId.orEmpty()
    state.selectedTransactionDate = state.transactionDate
    state.transactionNotes = transaction.notes
    state.transactionAmount = formatAmount(kotlin.math.abs(transaction.amountMinorUnits), state.budgetDisplaySettings)
    state.selectedTransactionAmountMinor = transaction.amountMinorUnits
    state.selectedTransactionNotes = transaction.notes
    state.selectedTransactionRecordId = transaction.recordId.orEmpty()
    state.selectedTransactionIsLive = isLive
    navigation.open(AppRoute.TRANSACTION_DETAIL)
}

private suspend fun deleteTransactionSelection(
    mobileSession: TandemMobileSession,
    budgetId: String,
    transactionIds: Set<String>,
): Boolean {
    for (transactionId in transactionIds) {
        if (!mobileSession.deleteTransaction(budgetId, transactionId)) return false
    }
    return true
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
        PeriodStepper(
            title = selectedYear.toString(),
            previousLabel = "Previous year",
            nextLabel = "Next year",
            onPrevious = { selectedYear -= 1 },
            onNext = { selectedYear += 1 },
        )
        val months = (1..12).map { month -> month to formatShortMonth(selectedYear, month) }
        months.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                row.forEach { (monthNumber, monthLabel) ->
                    val full = formatMonth(selectedYear, monthNumber)
                    val chosen = state.selectedMonth == full
                    SelectableTile(
                        selected = chosen,
                        contentDescription = full,
                        onSelect = {
                            state.selectedMonth = full
                            state.selectedMonthId = "$selectedYear-${monthNumber.toString().padStart(2, '0')}"
                            navigation.back()
                        },
                        modifier = Modifier.weight(1f).height(72.dp),
                        selectedBackground = tandemPalette.accentSurface,
                        unselectedBackground = tandemPalette.surface,
                        selectedBorder = tandemPalette.accent,
                        unselectedBorder = tandemPalette.border,
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
    mobileSession: TandemMobileSession,
) {
    val scope = rememberCoroutineScope()
    val palette = tandemPalette
    val isLiveBudget = state.selectedBudgetId.isNotBlank()
    val isTrackingBudget = isLiveBudget && state.budgetDisplaySettings?.budgetType == "tracking"
    val groups = if (isLiveBudget) liveBudgetMonth?.groups.orEmpty() else previewCategoryGroups
    val group = groups.firstOrNull { it.id == state.selectedCategoryGroupId } ?: groups.firstOrNull()
    val groupIndex = groups.indexOfFirst { it.id == group?.id }
    var showHidden by rememberSaveable(state.selectedBudgetId) { mutableStateOf(false) }
    var showGroupDialog by rememberSaveable(state.selectedBudgetId, group?.id) { mutableStateOf(false) }
    var showCreateGroupDialog by rememberSaveable(state.selectedBudgetId) { mutableStateOf(false) }
    var groupNameDraft by rememberSaveable(state.selectedBudgetId, group?.id) { mutableStateOf(group?.name.orEmpty()) }
    var newGroupName by rememberSaveable(state.selectedBudgetId) { mutableStateOf("") }
    var newGroupIsIncome by rememberSaveable(state.selectedBudgetId) { mutableStateOf(false) }
    val hiddenGroupRecords =
        remember(mobileSession.categoryGroupRecords) {
            mobileSession.categoryGroupRecords.filter { it.recordBody()?.booleanValue("hidden") == true }
        }
    val hiddenCategoryRecords =
        remember(mobileSession.categoryRecords, group?.id) {
            mobileSession.categoryRecords.filter { record ->
                val body = record.recordBody()
                body?.booleanValue("hidden") == true &&
                    (body.stringValue("group") ?: body.stringValue("group_id")) == group?.id
            }
        }
    PageLazyColumn {
        if (!isLiveBudget) {
            item(key = "category-group-sample-banner") { SampleBanner() }
        }
        if (group == null) {
            item(key = "category-group-empty-message") {
                EmptyState(
                    "No category group",
                    "No category group is available for this month.",
                    modifier = Modifier.padding(horizontal = TandemSpacing.large),
                )
            }
        } else {
            item(key = "category-group-title:${group.id}") {
                Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    Heading(group.name, state.selectedMonth)
                    if (isLiveBudget) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                        ) {
                            TandemIconButton(
                                Lucide.Pencil,
                                "Edit category group",
                                enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
                            ) {
                                groupNameDraft = group.name
                                mobileSession.clearBudgetActionMessage()
                                showGroupDialog = true
                            }
                            TandemIconButton(
                                Lucide.Plus,
                                "Add category group",
                                enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
                            ) {
                                newGroupName = ""
                                newGroupIsIncome = group.isIncome
                                mobileSession.clearBudgetActionMessage()
                                showCreateGroupDialog = true
                            }
                            TandemIconButton(
                                Lucide.EyeOff,
                                "Hide category group",
                                enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
                            ) {
                                scope.launch {
                                    mobileSession.manageBudgetCategoryGroup(
                                        state.selectedBudgetId,
                                        group.id,
                                        "hide",
                                        null,
                                        null,
                                        state.encryptedPassword.takeIf { it.isNotBlank() },
                                    )
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            TandemIconButton(
                                Lucide.ChevronUp,
                                "Move category group up",
                                enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction && groupIndex > 0,
                            ) {
                                scope.launch {
                                    mobileSession.manageBudgetCategoryGroup(
                                        state.selectedBudgetId,
                                        group.id,
                                        "move",
                                        null,
                                        groups.getOrNull(groupIndex - 1)?.id,
                                        state.encryptedPassword.takeIf { it.isNotBlank() },
                                    )
                                }
                            }
                            TandemIconButton(
                                Lucide.ChevronDown,
                                "Move category group down",
                                enabled =
                                    !state.readOnlyMode &&
                                        !mobileSession.isPerformingBudgetAction &&
                                        groupIndex >= 0 &&
                                        groupIndex < groups.lastIndex,
                            ) {
                                val targetId = groups.getOrNull(groupIndex + 2)?.id
                                scope.launch {
                                    mobileSession.manageBudgetCategoryGroup(
                                        state.selectedBudgetId,
                                        group.id,
                                        "move",
                                        null,
                                        targetId,
                                        state.encryptedPassword.takeIf { it.isNotBlank() },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item(key = "category-group-summary:${group.id}") {
                AmountHero(
                    if (isTrackingBudget) "Activity this month" else "Assigned this month",
                    state.formatCurrency(if (isTrackingBudget) group.activityMinor else group.assignedMinor),
                    if (isTrackingBudget) {
                        "Across ${group.categories.size} categories"
                    } else {
                        "${state.formatCurrency(group.balanceMinor)} available across ${group.categories.size} categories"
                    },
                )
            }
            item(key = "category-group-list-title:${group.id}") {
                SectionCard("Categories", "${group.categories.size} categories") {}
            }
            items(group.categories, key = { "category-group:${group.id}:${it.id}" }) { category ->
                SurfaceCard(modifier = tandemListItemModifier(tandemReduceMotion)) {
                    ListRow(
                        category.name,
                        if (isTrackingBudget) {
                            "Activity ${state.formatCurrency(category.activityMinor)}"
                        } else {
                            "Spent ${state.formatCurrency(
                                kotlin.math.abs(category.activityMinor),
                            )} · ${state.formatCurrency(category.balanceMinor)} available"
                        },
                        if (isTrackingBudget) null else state.formatCurrency(category.assignedMinor),
                        leading = RowLeading.Icon(state.categoryIconFor(category.id, category.name)),
                        options = ListRowOptions(trailingColor = if (category.balanceMinor >= 0) palette.positive else palette.warning),
                    ) {
                        state.selectedCategoryGroupId = group.id
                        state.selectedCategoryId = category.id
                        navigation.open(AppRoute.CATEGORY_DETAIL)
                    }
                }
            }
            if (isLiveBudget) {
                item(key = "category-group-hidden-toggle:${group.id}") {
                    SecondaryButton(if (showHidden) "Hide hidden items" else "Show hidden items") {
                        showHidden = !showHidden
                    }
                }
                if (showHidden) {
                    items(hiddenCategoryRecords, key = { "hidden-category:${it.recordId}" }) { hidden ->
                        SurfaceCard {
                            ListRow(
                                hidden.recordName() ?: "Hidden category",
                                "Hidden",
                                leading = RowLeading.Icon(Lucide.EyeOff),
                                trailing = "Show",
                                onClick = {
                                    scope.launch {
                                        mobileSession.manageBudgetCategory(
                                            state.selectedBudgetId,
                                            hidden.recordId,
                                            "show",
                                            null,
                                            state.encryptedPassword.takeIf { it.isNotBlank() },
                                        )
                                    }
                                },
                            )
                        }
                    }
                    items(hiddenGroupRecords, key = { "hidden-group:${it.recordId}" }) { hidden ->
                        SurfaceCard {
                            ListRow(
                                hidden.recordName() ?: "Hidden category group",
                                "Category group · hidden",
                                leading = RowLeading.Icon(Lucide.EyeOff),
                                trailing = "Show",
                                onClick = {
                                    scope.launch {
                                        mobileSession.manageBudgetCategoryGroup(
                                            state.selectedBudgetId,
                                            hidden.recordId,
                                            "show",
                                            null,
                                            null,
                                            state.encryptedPassword.takeIf { it.isNotBlank() },
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
    if (showGroupDialog && isLiveBudget && group != null) {
        CategoryGroupNameDialog(
            title = "Edit category group",
            name = groupNameDraft,
            onNameChange = { groupNameDraft = it },
            busy = mobileSession.isPerformingBudgetAction,
            message = mobileSession.budgetActionMessage,
            onDismiss = { showGroupDialog = false },
            onSave = {
                scope.launch {
                    if (mobileSession.manageBudgetCategoryGroup(
                            state.selectedBudgetId,
                            group.id,
                            "rename",
                            groupNameDraft,
                            null,
                            state.encryptedPassword.takeIf { it.isNotBlank() },
                        )
                    ) {
                        showGroupDialog = false
                    }
                }
            },
        )
    }
    if (showCreateGroupDialog && isLiveBudget) {
        CategoryGroupNameDialog(
            title = "Add category group",
            name = newGroupName,
            onNameChange = { newGroupName = it },
            busy = mobileSession.isPerformingBudgetAction,
            message = mobileSession.budgetActionMessage,
            isIncome = newGroupIsIncome,
            onIncomeChange = { newGroupIsIncome = it },
            onDismiss = { showCreateGroupDialog = false },
            onSave = {
                scope.launch {
                    if (mobileSession.createBudgetCategoryGroup(
                            state.selectedBudgetId,
                            newGroupName,
                            newGroupIsIncome,
                            state.encryptedPassword.takeIf { it.isNotBlank() },
                        ) != null
                    ) {
                        showCreateGroupDialog = false
                    }
                }
            },
        )
    }
}

@Composable
private fun CategoryGroupNameDialog(
    title: String,
    name: String,
    onNameChange: (String) -> Unit,
    busy: Boolean,
    message: String?,
    isIncome: Boolean? = null,
    onIncomeChange: ((Boolean) -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 440.dp).padding(horizontal = 16.dp)) {
            Column(Modifier.fillMaxWidth().padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                Text(title, color = tandemPalette.text, fontSize = TandemTypography.sectionTitle, fontWeight = FontWeight.Bold)
                TextFieldCard("Name", name, onNameChange, TextFieldOptions(placeholder = "Essentials"))
                if (isIncome != null && onIncomeChange != null) {
                    ToggleRow("Income group", "For income categories", isIncome, !busy, onIncomeChange)
                }
                message?.let { InlineMessage(it, InlineMessageKind.ERROR) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    TandemActionButton("Cancel", TandemActionButtonTone.NEUTRAL, Modifier.weight(1f), enabled = !busy, onClick = onDismiss)
                    TandemActionButton(
                        "Save",
                        TandemActionButtonTone.PRIMARY,
                        Modifier.weight(1f),
                        icon = Lucide.Check,
                        enabled =
                            name.isNotBlank() && !busy,
                        loading = busy,
                        onClick = onSave,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryDetailScreen(
    state: AppState,
    navigation: AppNavigation,
    liveBudgetMonth: LiveBudgetMonthSummary?,
    mobileSession: TandemMobileSession,
) {
    val scope = rememberCoroutineScope()
    val isLiveBudget = state.selectedBudgetId.isNotBlank()
    val isTrackingBudget = isLiveBudget && state.budgetDisplaySettings?.budgetType == "tracking"
    val category =
        if (isLiveBudget) {
            liveBudgetMonth
                ?.groups
                ?.firstOrNull { it.id == state.selectedCategoryGroupId }
                ?.categories
                ?.firstOrNull { it.id == state.selectedCategoryId }
        } else {
            previewCategoryGroups
                .firstOrNull { it.id == state.selectedCategoryGroupId }
                ?.categories
                ?.firstOrNull { it.id == state.selectedCategoryId }
                ?: previewCategoryGroups.first().categories.first()
        }
    var showEditDialog by rememberSaveable(state.selectedBudgetId, state.selectedCategoryId) { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable(state.selectedBudgetId, state.selectedCategoryId) { mutableStateOf(false) }
    var showAssignedAmountDialog by rememberSaveable(state.selectedBudgetId, state.selectedCategoryId, state.selectedMonthId) {
        mutableStateOf(false)
    }
    var showMoveMoneyDialog by rememberSaveable(state.selectedBudgetId, state.selectedCategoryId, state.selectedMonthId) {
        mutableStateOf(false)
    }
    var moveMoneyFromId by rememberSaveable(state.selectedBudgetId, state.selectedCategoryId, state.selectedMonthId) { mutableStateOf("") }
    var moveMoneyToId by rememberSaveable(state.selectedBudgetId, state.selectedCategoryId, state.selectedMonthId) { mutableStateOf("") }
    var moveMoneyAmountDraft by rememberSaveable(
        state.selectedBudgetId,
        state.selectedCategoryId,
        state.selectedMonthId,
    ) { mutableStateOf("") }
    var moveMoneyFromExpanded by rememberSaveable { mutableStateOf(false) }
    var moveMoneyToExpanded by rememberSaveable { mutableStateOf(false) }
    var assignedAmountDraft by
        rememberSaveable(state.selectedBudgetId, state.selectedCategoryId, state.selectedMonthId) {
            mutableStateOf("")
        }
    var editName by rememberSaveable(state.selectedBudgetId, state.selectedCategoryId) { mutableStateOf("") }
    var iconSearch by rememberSaveable(state.selectedBudgetId, state.selectedCategoryId) { mutableStateOf("") }
    var selectedIconChoice by rememberSaveable(state.selectedBudgetId, state.selectedCategoryId) { mutableStateOf("Automatic") }
    var deleteDestinationId by rememberSaveable(state.selectedBudgetId, state.selectedCategoryId) { mutableStateOf<String?>(null) }
    val currentIncomeType =
        mobileSession.categoryRecords
            .firstOrNull { it.recordId == state.selectedCategoryId }
            ?.recordBody()
            ?.booleanValue("is_income") ?: false
    val deleteDestinations =
        liveBudgetMonth?.groups.orEmpty().flatMap { it.categories }.filter { candidate ->
            candidate.id != state.selectedCategoryId &&
                (
                    mobileSession.categoryRecords
                        .firstOrNull { it.recordId == candidate.id }
                        ?.recordBody()
                        ?.booleanValue("is_income") ?: false
                ) == currentIncomeType
        }
    val moveMoneyCategories =
        remember(liveBudgetMonth, mobileSession.categoryRecords, currentIncomeType) {
            liveBudgetMonth?.groups.orEmpty().flatMap { group ->
                group.categories
                    .filter { candidate ->
                        (
                            mobileSession.categoryRecords
                                .firstOrNull { it.recordId == candidate.id }
                                ?.recordBody()
                                ?.booleanValue("is_income") ?: false
                        ) == currentIncomeType
                    }.map { group.name to it }
            }
        }
    val selectedGroup = liveBudgetMonth?.groups?.firstOrNull { it.id == state.selectedCategoryGroupId }
    val categoryIndex = selectedGroup?.categories?.indexOfFirst { it.id == state.selectedCategoryId } ?: -1
    PageColumn {
        if (!isLiveBudget) SampleBanner()
        if (category == null) {
            Heading("Category details", state.selectedMonth)
            BodyText("This category isn’t in the saved plan for this month.")
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                Column(Modifier.weight(1f)) { Heading(category.name, state.selectedMonth) }
                if (isLiveBudget) {
                    TandemIconButton(
                        Lucide.Pencil,
                        "Edit category",
                        enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
                        tint = tandemPalette.accent,
                    ) {
                        editName = category.name
                        selectedIconChoice =
                            when (state.categoryIconChoice(category.id)) {
                                "Food" -> "Groceries"
                                "Transport" -> "Car"
                                else -> state.categoryIconChoice(category.id) ?: "Automatic"
                            }
                        iconSearch = ""
                        mobileSession.clearBudgetActionMessage()
                        showEditDialog = true
                    }
                }
            }
            AmountHero(
                if (isTrackingBudget) "Activity" else "Available",
                state.formatCurrency(if (isTrackingBudget) category.activityMinor else category.balanceMinor),
                if (isTrackingBudget) "For the selected month" else "${state.formatCurrency(category.assignedMinor)} assigned",
            )
            SectionCard("This month") {
                if (!isTrackingBudget) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .clickable(
                                enabled = isLiveBudget && !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
                                role = Role.Button,
                                onClickLabel = "Edit assigned amount",
                                onClick = {
                                    assignedAmountDraft = state.formatAmount(category.assignedMinor)
                                    mobileSession.clearBudgetActionMessage()
                                    showAssignedAmountDialog = true
                                },
                            ).padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.small),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                    ) {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(TandemShapes.medium)).background(tandemPalette.raised),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Lucide.WalletCards,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = tandemPalette.accent,
                            )
                        }
                        Text("Assigned", modifier = Modifier.weight(1f), color = tandemPalette.text, fontSize = TandemTypography.body)
                        Text(
                            state.formatCurrency(category.assignedMinor),
                            color = tandemPalette.text,
                            fontSize = TandemTypography.compactBody,
                        )
                        if (isLiveBudget) {
                            Icon(
                                Lucide.Pencil,
                                contentDescription = "Edit assigned amount",
                                modifier = Modifier.size(19.dp),
                                tint = if (state.readOnlyMode) tandemPalette.muted else tandemPalette.accent,
                            )
                        }
                    }
                }
                if (!isTrackingBudget) DividerLine()
                ListRow(
                    "Activity",
                    "${state.selectedMonth} · ${state.formatCurrency(category.activityMinor)}",
                    leading = RowLeading.Icon(Lucide.Receipt),
                    onClick = { openCategoryActivity(state, navigation, category.id, category.name) },
                )
                if (isLiveBudget && !isTrackingBudget) {
                    DividerLine()
                    ToggleRow(
                        "Rollover",
                        "Unused money rolls forward",
                        checked = category.carryover,
                        enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
                    ) { enabled ->
                        scope.launch {
                            mobileSession.setBudgetCategoryCarryover(
                                state.selectedBudgetId,
                                category.id,
                                state.selectedMonthId,
                                enabled,
                                state.encryptedPassword.takeIf { it.isNotBlank() },
                            )
                        }
                    }
                }
                if (isLiveBudget && !isTrackingBudget) {
                    DividerLine()
                    ListRow(
                        "Move money",
                        leading = RowLeading.Icon(Lucide.WalletCards),
                        onClick = {
                            moveMoneyFromId = category.id
                            moveMoneyToId =
                                moveMoneyCategories
                                    .firstOrNull { it.second.id != category.id }
                                    ?.second
                                    ?.id
                                    .orEmpty()
                            moveMoneyAmountDraft = ""
                            mobileSession.clearBudgetActionMessage()
                            showMoveMoneyDialog = true
                        },
                    )
                }
                DividerLine()
                ListRow(
                    "Icon",
                    state.categoryIconChoice(category.id) ?: "Automatic",
                    leading = RowLeading.Icon(state.categoryIconFor(category.id, category.name)),
                )
                if (!isTrackingBudget) {
                    DividerLine()
                    ListRow("Available", "Assigned plus activity", state.formatCurrency(category.balanceMinor))
                }
            }
            if (isLiveBudget && selectedGroup != null && categoryIndex >= 0) {
                SectionCard("Category options") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        TandemIconButton(
                            Lucide.ChevronUp,
                            "Move category up",
                            enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction && categoryIndex > 0,
                        ) {
                            scope.launch {
                                mobileSession.manageBudgetCategory(
                                    state.selectedBudgetId,
                                    state.selectedCategoryId,
                                    "move",
                                    selectedGroup.categories.getOrNull(categoryIndex - 1)?.id,
                                    state.encryptedPassword.takeIf { it.isNotBlank() },
                                )
                            }
                        }
                        TandemIconButton(
                            Lucide.ChevronDown,
                            "Move category down",
                            enabled =
                                !state.readOnlyMode &&
                                    !mobileSession.isPerformingBudgetAction &&
                                    categoryIndex < selectedGroup.categories.lastIndex,
                        ) {
                            scope.launch {
                                mobileSession.manageBudgetCategory(
                                    state.selectedBudgetId,
                                    state.selectedCategoryId,
                                    "move",
                                    selectedGroup.categories.getOrNull(categoryIndex + 2)?.id,
                                    state.encryptedPassword.takeIf { it.isNotBlank() },
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        TandemIconButton(
                            Lucide.EyeOff,
                            "Hide category",
                            enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
                            tint = tandemPalette.negative,
                        ) {
                            scope.launch {
                                if (mobileSession.manageBudgetCategory(
                                        state.selectedBudgetId,
                                        state.selectedCategoryId,
                                        "hide",
                                        null,
                                        state.encryptedPassword.takeIf { it.isNotBlank() },
                                    )
                                ) {
                                    navigation.back()
                                }
                            }
                        }
                    }
                }
            }
            if (isLiveBudget) {
                SecondaryButton(
                    if (state.isCategoryPinned(category.id)) "Unpin" else "Pin",
                ) { state.togglePinnedCategory(category.id) }
            }
        }
    }
    if (showAssignedAmountDialog && category != null && isLiveBudget && !isTrackingBudget) {
        val parsedAmount = state.parseAmountMinorUnits(assignedAmountDraft)
        Dialog(
            onDismissRequest = { if (!mobileSession.isPerformingBudgetAction) showAssignedAmountDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 460.dp).padding(horizontal = 16.dp)) {
                Column(
                    Modifier.fillMaxWidth().padding(TandemSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        Icon(Lucide.WalletCards, contentDescription = null, modifier = Modifier.size(24.dp), tint = tandemPalette.accent)
                        Text(
                            "Assigned amount",
                            modifier = Modifier.weight(1f),
                            color = tandemPalette.text,
                            fontSize = TandemTypography.sectionTitle,
                            fontWeight = FontWeight.Bold,
                        )
                        TandemIconButton(
                            Lucide.X,
                            "Close",
                            sizeDp = 40,
                            iconSizeDp = 18,
                            enabled = !mobileSession.isPerformingBudgetAction,
                        ) { showAssignedAmountDialog = false }
                    }
                    Text("${category.name} · ${state.selectedMonth}", color = tandemPalette.muted, fontSize = TandemTypography.caption)
                    TextFieldCard(
                        "Amount",
                        assignedAmountDraft,
                        { assignedAmountDraft = it },
                        TextFieldOptions(
                            placeholder = state.formatAmount(0),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            textAlign = TextAlign.Center,
                            large = true,
                        ),
                    )
                    mobileSession.budgetActionMessage?.let { InlineMessage(it, InlineMessageKind.ERROR) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        TandemActionButton(
                            "Cancel",
                            TandemActionButtonTone.NEUTRAL,
                            Modifier.weight(1f),
                            enabled = !mobileSession.isPerformingBudgetAction,
                        ) { showAssignedAmountDialog = false }
                        TandemActionButton(
                            if (mobileSession.isPerformingBudgetAction) "Saving…" else "Save",
                            TandemActionButtonTone.PRIMARY,
                            Modifier.weight(1f),
                            icon = Lucide.Check,
                            enabled =
                                !state.readOnlyMode && !mobileSession.isPerformingBudgetAction && parsedAmount != null && parsedAmount >= 0,
                            loading = mobileSession.isPerformingBudgetAction,
                        ) {
                            scope.launch {
                                val saved =
                                    mobileSession.setBudgetCategoryAmount(
                                        state.selectedBudgetId,
                                        category.id,
                                        state.selectedMonthId,
                                        parsedAmount ?: 0L,
                                        state.encryptedPassword.takeIf { it.isNotBlank() },
                                    )
                                if (saved) showAssignedAmountDialog = false
                            }
                        }
                    }
                }
            }
        }
    }
    if (showMoveMoneyDialog && category != null && isLiveBudget && !isTrackingBudget) {
        val parsedMoveAmount = state.parseAmountMinorUnits(moveMoneyAmountDraft)
        val fromLabel = moveMoneyCategories.firstOrNull { it.second.id == moveMoneyFromId }?.second?.name ?: "Choose category"
        val toLabel = moveMoneyCategories.firstOrNull { it.second.id == moveMoneyToId }?.second?.name ?: "Choose category"
        Dialog(
            onDismissRequest = { if (!mobileSession.isPerformingBudgetAction) showMoveMoneyDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 480.dp).padding(horizontal = 16.dp)) {
                Column(
                    Modifier.fillMaxWidth().padding(TandemSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        Icon(Lucide.WalletCards, contentDescription = null, modifier = Modifier.size(24.dp), tint = tandemPalette.accent)
                        Text(
                            "Move money",
                            modifier = Modifier.weight(1f),
                            color = tandemPalette.text,
                            fontSize = TandemTypography.sectionTitle,
                            fontWeight = FontWeight.Bold,
                        )
                        TandemIconButton(
                            Lucide.X,
                            "Close",
                            sizeDp = 40,
                            iconSizeDp = 18,
                            enabled = !mobileSession.isPerformingBudgetAction,
                        ) { showMoveMoneyDialog = false }
                    }
                    Box {
                        ListRow("From", trailing = fromLabel, leading = RowLeading.Icon(Lucide.Minus), onClick = {
                            moveMoneyFromExpanded =
                                true
                        })
                        DropdownMenu(expanded = moveMoneyFromExpanded, onDismissRequest = { moveMoneyFromExpanded = false }) {
                            moveMoneyCategories.forEach { (_, candidate) ->
                                DropdownMenuItem(onClick = {
                                    moveMoneyFromId = candidate.id
                                    moveMoneyFromExpanded = false
                                }) {
                                    Text(candidate.name, color = tandemPalette.text, fontSize = TandemTypography.compactBody)
                                }
                            }
                        }
                    }
                    Box {
                        ListRow("To", trailing = toLabel, leading = RowLeading.Icon(Lucide.Plus), onClick = { moveMoneyToExpanded = true })
                        DropdownMenu(expanded = moveMoneyToExpanded, onDismissRequest = { moveMoneyToExpanded = false }) {
                            moveMoneyCategories.filter { it.second.id != moveMoneyFromId }.forEach { (_, candidate) ->
                                DropdownMenuItem(onClick = {
                                    moveMoneyToId = candidate.id
                                    moveMoneyToExpanded = false
                                }) {
                                    Text(candidate.name, color = tandemPalette.text, fontSize = TandemTypography.compactBody)
                                }
                            }
                        }
                    }
                    TextFieldCard(
                        "Amount",
                        moveMoneyAmountDraft,
                        { moveMoneyAmountDraft = it },
                        TextFieldOptions(
                            placeholder = state.formatAmount(0),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            textAlign = TextAlign.Center,
                            large = true,
                        ),
                    )
                    mobileSession.budgetActionMessage?.let { InlineMessage(it, InlineMessageKind.ERROR) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        TandemActionButton(
                            "Cancel",
                            TandemActionButtonTone.NEUTRAL,
                            Modifier.weight(1f),
                            enabled = !mobileSession.isPerformingBudgetAction,
                        ) { showMoveMoneyDialog = false }
                        TandemActionButton(
                            if (mobileSession.isPerformingBudgetAction) "Moving…" else "Move money",
                            TandemActionButtonTone.PRIMARY,
                            Modifier.weight(1f),
                            icon = Lucide.Check,
                            enabled =
                                !state.readOnlyMode &&
                                    !mobileSession.isPerformingBudgetAction &&
                                    parsedMoveAmount != null &&
                                    parsedMoveAmount > 0 &&
                                    moveMoneyFromId.isNotBlank() &&
                                    moveMoneyToId.isNotBlank() &&
                                    moveMoneyFromId != moveMoneyToId,
                            loading = mobileSession.isPerformingBudgetAction,
                        ) {
                            scope.launch {
                                val moved =
                                    mobileSession.moveBudgetMoney(
                                        state.selectedBudgetId,
                                        state.selectedMonthId,
                                        moveMoneyFromId,
                                        moveMoneyToId,
                                        parsedMoveAmount ?: 0L,
                                        state.encryptedPassword.takeIf { it.isNotBlank() },
                                    )
                                if (moved) showMoveMoneyDialog = false
                            }
                        }
                    }
                }
            }
        }
    }
    if (showEditDialog && category != null) {
        Dialog(
            onDismissRequest = { if (!mobileSession.isPerformingBudgetAction) showEditDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            SurfaceCard(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .padding(horizontal = 16.dp)
                    .heightIn(max = 720.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(TandemSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        Box(
                            Modifier.size(42.dp).clip(CircleShape).background(tandemPalette.accentSurface),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Lucide.Pencil, contentDescription = null, modifier = Modifier.size(20.dp), tint = tandemPalette.accent)
                        }
                        Text(
                            "Edit category",
                            modifier = Modifier.weight(1f),
                            color = tandemPalette.text,
                            fontSize = TandemTypography.sectionTitle,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TandemIconButton(
                            Lucide.X,
                            "Close",
                            sizeDp = 40,
                            iconSizeDp = 18,
                            containerColor = tandemPalette.raised,
                            enabled = !mobileSession.isPerformingBudgetAction,
                        ) { showEditDialog = false }
                    }
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                    ) {
                        TextFieldCard("Name", editName, { editName = it }, TextFieldOptions(placeholder = category.name))
                        LabelText("Choose an icon")
                        TextFieldCard(
                            "Find an icon",
                            iconSearch,
                            { iconSearch = it },
                            TextFieldOptions(placeholder = "Search by name"),
                        )
                        val visibleIconChoices = categoryIconChoices.filter { it.contains(iconSearch.trim(), ignoreCase = true) }
                        if (visibleIconChoices.isEmpty()) {
                            BodyText("No icons match that search.")
                        }
                        visibleIconChoices.chunked(4).forEach { rowChoices ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                                rowChoices.forEach { choice ->
                                    val icon = categoryIconVector(choice) ?: financeIcon(category.name) ?: Lucide.Receipt
                                    SelectableTile(
                                        selected = selectedIconChoice == choice,
                                        contentDescription = if (choice == "Automatic") "Automatic icon" else choice,
                                        onSelect = { selectedIconChoice = choice },
                                        modifier = Modifier.weight(1f).height(66.dp),
                                        shape = RoundedCornerShape(TandemShapes.control),
                                        selectedBackground = tandemPalette.accentSurface,
                                        unselectedBackground = tandemPalette.surface,
                                        selectedBorder = tandemPalette.accent,
                                        unselectedBorder = tandemPalette.border,
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall),
                                        ) {
                                            Icon(
                                                icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(23.dp),
                                                tint = tandemPalette.accent,
                                            )
                                            Text(
                                                if (choice == "Automatic") "Auto" else choice,
                                                color = tandemPalette.text,
                                                fontSize = TandemTypography.micro,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                                repeat(4 - rowChoices.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                        mobileSession.budgetActionMessage?.let { InlineMessage(it, InlineMessageKind.ERROR) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        TandemActionButton(
                            "Delete",
                            TandemActionButtonTone.DESTRUCTIVE,
                            modifier = Modifier.weight(1f),
                            icon = Lucide.Trash2,
                            enabled = !mobileSession.isPerformingBudgetAction,
                        ) {
                            showEditDialog = false
                            deleteDestinationId = null
                            mobileSession.clearBudgetActionMessage()
                            showDeleteDialog = true
                        }
                        TandemActionButton(
                            "Cancel",
                            TandemActionButtonTone.NEUTRAL,
                            modifier = Modifier.weight(1f),
                            enabled = !mobileSession.isPerformingBudgetAction,
                        ) { showEditDialog = false }
                        TandemActionButton(
                            if (mobileSession.isPerformingBudgetAction) "Saving…" else "Save",
                            TandemActionButtonTone.PRIMARY,
                            modifier = Modifier.weight(1f),
                            enabled = !mobileSession.isPerformingBudgetAction && editName.isNotBlank(),
                            loading = mobileSession.isPerformingBudgetAction,
                        ) {
                            scope.launch {
                                val renamed =
                                    editName.trim() == category.name ||
                                        mobileSession.renameBudgetCategory(
                                            state.selectedBudgetId,
                                            category.id,
                                            editName,
                                            state.encryptedPassword.takeIf { it.isNotBlank() },
                                        )
                                if (renamed) {
                                    state.setCategoryIconChoice(category.id, selectedIconChoice)
                                    showEditDialog = false
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showDeleteDialog && category != null) {
        Dialog(
            onDismissRequest = { if (!mobileSession.isPerformingBudgetAction) showDeleteDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            SurfaceCard(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .padding(horizontal = 16.dp)
                    .heightIn(max = 720.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(TandemSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        Box(
                            Modifier.size(42.dp).clip(CircleShape).background(tandemPalette.negative.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Lucide.Trash2, contentDescription = null, modifier = Modifier.size(20.dp), tint = tandemPalette.negative)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Delete category",
                                color = tandemPalette.text,
                                fontSize = TandemTypography.sectionTitle,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                category.name,
                                color = tandemPalette.muted,
                                fontSize = TandemTypography.caption,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TandemIconButton(
                            Lucide.X,
                            "Close",
                            sizeDp = 40,
                            iconSizeDp = 18,
                            containerColor = tandemPalette.raised,
                            enabled = !mobileSession.isPerformingBudgetAction,
                        ) { showDeleteDialog = false }
                    }
                    LabelText("Move transactions to")
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                    ) {
                        val destinations: List<Pair<String?, String>> =
                            listOf(null to "Leave unassigned") + deleteDestinations.map { it.id to it.name }
                        destinations.forEach { (destinationId, destinationName) ->
                            val selected = deleteDestinationId == destinationId
                            SelectableTile(
                                selected = selected,
                                contentDescription = "Move to $destinationName",
                                onSelect = { deleteDestinationId = destinationId },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(TandemShapes.control),
                                selectedBackground = tandemPalette.accentSurface,
                                unselectedBackground = tandemPalette.surface,
                                selectedBorder = tandemPalette.accent,
                                unselectedBorder = tandemPalette.border,
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.medium),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                                ) {
                                    Icon(
                                        if (destinationId ==
                                            null
                                        ) {
                                            Lucide.CircleDot
                                        } else {
                                            state.categoryIconFor(destinationId, destinationName)
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(21.dp),
                                        tint = tandemPalette.accent,
                                    )
                                    Text(
                                        destinationName,
                                        modifier = Modifier.weight(1f),
                                        color = tandemPalette.text,
                                        fontSize = TandemTypography.body,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (selected) {
                                        Icon(
                                            Lucide.Check,
                                            contentDescription = "Selected",
                                            modifier = Modifier.size(18.dp),
                                            tint = tandemPalette.accent,
                                        )
                                    }
                                }
                            }
                        }
                        mobileSession.budgetActionMessage?.let { InlineMessage(it, InlineMessageKind.ERROR) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        TandemActionButton(
                            "Cancel",
                            TandemActionButtonTone.NEUTRAL,
                            modifier = Modifier.weight(1f),
                            enabled = !mobileSession.isPerformingBudgetAction,
                        ) { showDeleteDialog = false }
                        TandemActionButton(
                            if (mobileSession.isPerformingBudgetAction) "Deleting…" else "Delete",
                            TandemActionButtonTone.DESTRUCTIVE,
                            modifier = Modifier.weight(1f),
                            icon = Lucide.Trash2,
                            enabled = !mobileSession.isPerformingBudgetAction,
                            loading = mobileSession.isPerformingBudgetAction,
                        ) {
                            scope.launch {
                                val deleted =
                                    mobileSession.deleteBudgetCategory(
                                        state.selectedBudgetId,
                                        category.id,
                                        deleteDestinationId,
                                        state.encryptedPassword.takeIf { it.isNotBlank() },
                                    )
                                if (deleted) {
                                    state.setCategoryIconChoice(category.id, "Automatic")
                                    showDeleteDialog = false
                                    navigation.back()
                                }
                            }
                        }
                    }
                }
            }
        }
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
            ActivitySearchFilter(
                ActivitySearchField.CATEGORY,
                if (state.selectedBudgetId.isBlank()) categoryName else categoryId.ifBlank { categoryName },
                "Category: $categoryName",
            ),
            ActivitySearchFilter(ActivitySearchField.DATE_MONTH, state.selectedMonthId, "Date: ${state.selectedMonth}"),
        )
    navigation.open(AppRoute.ACTIVITY_SEARCH)
}

private fun openGeneralActivitySearch(
    state: AppState,
    navigation: AppNavigation,
) {
    state.activitySearchQuery = ""
    state.activitySearchFilters = emptyList()
    navigation.open(AppRoute.ACTIVITY_SEARCH)
}

private fun openReportCategoryActivity(
    state: AppState,
    navigation: AppNavigation,
    categoryId: String,
    categoryName: String,
    window: ReportWindow,
) {
    state.activitySearchQuery = ""
    val dateFilter =
        when {
            window.allDates ->
                ActivitySearchFilter(
                    ActivitySearchField.DATE_RANGE,
                    "${window.startDate}..${window.throughDate}",
                    "Date: All dates",
                )
            window.monthCount == 1 ->
                ActivitySearchFilter(
                    ActivitySearchField.DATE_MONTH,
                    window.endMonthId,
                    "Date: ${reportMonthLabel(window.endMonthId)}",
                )
            else ->
                ActivitySearchFilter(
                    ActivitySearchField.DATE_RANGE,
                    "${window.startDate}..${window.throughDate}",
                    "Date: ${reportMonthLabel(window.startMonthId)} – ${reportMonthLabel(window.endMonthId)}",
                )
        }
    state.activitySearchFilters =
        listOf(
            ActivitySearchFilter(
                ActivitySearchField.CATEGORY,
                if (state.selectedBudgetId.isBlank()) categoryName else categoryId.ifBlank { categoryName },
                "Category: $categoryName",
            ),
            dateFilter,
        )
    navigation.open(AppRoute.ACTIVITY_SEARCH)
}

private fun reportRoute(kind: ReportKind): AppRoute =
    when (kind) {
        ReportKind.SPENDING_BY_CATEGORY -> AppRoute.REPORT_SPENDING_BY_CATEGORY
        ReportKind.SPENDING_OVER_TIME -> AppRoute.REPORT_SPENDING_OVER_TIME
        ReportKind.INCOME_AND_SPENDING -> AppRoute.REPORT_INCOME_AND_SPENDING
        ReportKind.BUDGETED_AND_SPENT -> AppRoute.REPORT_BUDGETED_AND_SPENT
        ReportKind.NET_WORTH -> AppRoute.REPORT_NET_WORTH
    }

private fun reportKind(route: AppRoute): ReportKind =
    when (route) {
        AppRoute.REPORT_SPENDING_BY_CATEGORY -> ReportKind.SPENDING_BY_CATEGORY
        AppRoute.REPORT_SPENDING_OVER_TIME -> ReportKind.SPENDING_OVER_TIME
        AppRoute.REPORT_INCOME_AND_SPENDING -> ReportKind.INCOME_AND_SPENDING
        AppRoute.REPORT_BUDGETED_AND_SPENT -> ReportKind.BUDGETED_AND_SPENT
        AppRoute.REPORT_NET_WORTH -> ReportKind.NET_WORTH
        else -> error("Route is not a report detail: $route")
    }

private data class AccountListItemUi(
    val id: String,
    val name: String,
    val description: String,
    val balance: String,
    val offBudget: Boolean,
    val closed: Boolean = false,
    val accountType: String? = null,
)

private data class AccountListGroupUi(
    val name: String,
    val total: String,
    val accounts: List<AccountListItemUi>,
)

private data class AccountListUi(
    val groups: List<AccountListGroupUi>,
    val isSample: Boolean,
    val showAccountOptions: Boolean,
)

internal fun accountTypeIcon(
    accountName: String,
    accountType: String? = null,
): ImageVector {
    val name = accountName.lowercase()
    val type = accountType.orEmpty().lowercase()
    return when {
        "savings" in name || "saving" in name -> Lucide.PiggyBank
        "checking" in name || "chequing" in name -> Lucide.Landmark
        listOf("credit", "card", "visa", "mastercard", "amex", "discover").any { it in name } -> Lucide.CreditCard
        listOf("loan", "mortgage", "debt").any { it in name } -> Lucide.HandCoins
        listOf("investment", "brokerage", "401k", "403b", "ira", "retirement", "stocks").any { it in name } ->
            Lucide.ChartNoAxesCombined
        listOf("credit", "card").any { it in type } -> Lucide.CreditCard
        listOf("loan", "debt").any { it in type } -> Lucide.HandCoins
        listOf("tracking", "investment").any { it in type } -> Lucide.ChartNoAxesCombined
        else -> Lucide.WalletCards
    }
}

@Composable
private fun AccountsScreen(
    state: AppState,
    navigation: AppNavigation,
    liveAccounts: List<LiveAccountSummary>,
    merchantIconMatcher: MerchantIconMatcherPack?,
    mobileSession: TandemMobileSession,
) {
    val scope = rememberCoroutineScope()
    var addAccountDialog by rememberSaveable { mutableStateOf(false) }
    var showBankConnectionsDialog by rememberSaveable { mutableStateOf(false) }
    var newAccountName by rememberSaveable { mutableStateOf("") }
    var newAccountOffBudget by rememberSaveable { mutableStateOf(false) }
    var newAccountGroupId by rememberSaveable { mutableStateOf("") }
    var accountGroupMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val accountGroupOptions =
        remember(mobileSession.accountGroupRecords) {
            mobileSession.accountGroupRecords
                .mapNotNull { record -> record.recordName()?.let { record.recordId to it } }
                .sortedBy { it.second.lowercase() }
        }
    val isRefreshing = mobileSession.isSyncing || mobileSession.isPerformingBudgetAction
    val runBankSync: () -> Unit = {
        if (!isRefreshing && !state.readOnlyMode && state.selectedBudgetId.isNotBlank()) {
            scope.launch {
                mobileSession.runBankSync(
                    state.selectedBudgetId,
                    state.encryptedPassword.takeIf { it.isNotBlank() },
                )
            }
        }
    }
    val isLiveBudget = state.selectedBudgetId.isNotBlank()
    val model =
        remember(isLiveBudget, liveAccounts, state.budgetDisplaySettings) {
            if (isLiveBudget) {
                val groupOrder = listOf("On budget", "Off budget", "Closed")
                val groupedAccounts =
                    liveAccounts
                        .groupBy(::liveAccountGroupLabel)
                        .entries
                        .sortedWith(
                            compareBy(
                                { groupOrder.indexOf(it.key).let { index -> if (index < 0) groupOrder.size else index } },
                                { it.key },
                            ),
                        ).map { (groupName, accounts) ->
                            AccountListGroupUi(
                                groupName,
                                state.formatCurrency(accounts.sumOf { it.balanceMinor ?: 0L }),
                                accounts.sortedWith(compareBy({ it.sortOrder ?: Long.MAX_VALUE }, { it.name.lowercase() })).map { account ->
                                    AccountListItemUi(
                                        account.id,
                                        account.name,
                                        when {
                                            account.closed -> "Closed"
                                            account.accountGroupName != null -> account.accountGroupName
                                            account.offBudget -> "Off budget"
                                            else -> "On budget"
                                        },
                                        account.balanceMinor?.let(state::formatCurrency) ?: "Unavailable",
                                        account.offBudget,
                                        account.closed,
                                        account.accountGroupName,
                                    )
                                },
                            )
                        }
                AccountListUi(
                    groupedAccounts,
                    isSample = false,
                    showAccountOptions = false,
                )
            } else {
                val groupedAccounts =
                    previewAccounts.groupBy { it.group }.map { (groupName, accounts) ->
                        AccountListGroupUi(
                            groupName,
                            state.formatCurrency(accounts.sumOf { it.balanceMinor }),
                            accounts.map { account ->
                                AccountListItemUi(
                                    account.id,
                                    account.name,
                                    account.description,
                                    state.formatCurrency(account.balanceMinor),
                                    account.offBudget,
                                    accountType = account.group,
                                )
                            },
                        )
                    }
                AccountListUi(
                    groupedAccounts,
                    isSample = true,
                    showAccountOptions = true,
                )
            }
        }

    PageLazyColumn(
        isRefreshing = isRefreshing,
        onRefresh = runBankSync.takeIf { isLiveBudget && !state.readOnlyMode },
    ) {
        if (model.isSample) item(key = "accounts-preview-banner") { SampleBanner() }
        mobileSession.budgetActionMessage?.let { message ->
            item(key = "account-action-message") { InlineMessage(message, InlineMessageKind.ERROR) }
        }
        if (model.groups.isEmpty()) {
            item(key = "accounts-empty") {
                SurfaceCard {
                    EmptyState(
                        "No accounts yet",
                        if (model.isSample) null else "Sync this budget to load its account list and balances.",
                        modifier = Modifier.fillMaxWidth().padding(TandemSpacing.large),
                    )
                }
            }
        } else {
            model.groups.forEach { group ->
                item(key = "account-group:${group.name}") {
                    Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.small),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                group.name,
                                color = tandemPalette.text,
                                fontSize = TandemTypography.label,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(group.total, color = tandemPalette.muted, fontSize = TandemTypography.caption, maxLines = 1)
                        }
                        SurfaceCard {
                            Column {
                                group.accounts.forEachIndexed { index, account ->
                                    ListRow(
                                        account.name,
                                        trailing = account.balance,
                                        leading =
                                            RowLeading.Merchant(
                                                merchantIconMatcher?.accountIconFor(account.name),
                                                fallback = accountTypeIcon(account.name, account.accountType),
                                            ),
                                        options =
                                            ListRowOptions(
                                                dense = true,
                                                trailingColor =
                                                    if (account.offBudget || account.closed) tandemPalette.muted else tandemPalette.text,
                                            ),
                                        onClick = {
                                            state.selectedAccountId = account.id
                                            navigation.open(AppRoute.ACCOUNT_DETAIL)
                                        },
                                    )
                                    if (index < group.accounts.lastIndex) DividerLine()
                                }
                            }
                        }
                    }
                }
            }
        }
        if (isLiveBudget) {
            item(key = "account-bottom-actions") {
                Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    TandemActionButton(
                        "Add Account",
                        TandemActionButtonTone.PRIMARY,
                        Modifier.fillMaxWidth(),
                        icon = Lucide.Plus,
                        enabled = !isRefreshing && !state.readOnlyMode,
                    ) {
                        newAccountName = ""
                        newAccountOffBudget = false
                        newAccountGroupId = ""
                        mobileSession.clearBudgetActionMessage()
                        addAccountDialog = true
                    }
                    TandemActionButton(
                        "Manage bank connections",
                        TandemActionButtonTone.NEUTRAL,
                        Modifier.fillMaxWidth(),
                        icon = Lucide.Landmark,
                        enabled = !isRefreshing,
                    ) { showBankConnectionsDialog = true }
                }
            }
        }
        if (model.showAccountOptions) {
            item(key = "account-options") {
                SecondaryButton("Account options") { navigation.open(AppRoute.ACCOUNT_OPTIONS) }
            }
        }
    }
    if (addAccountDialog) {
        Dialog(
            onDismissRequest = { if (!mobileSession.isPerformingBudgetAction) addAccountDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            SurfaceCard(
                Modifier.fillMaxWidth().widthIn(max = 480.dp).padding(horizontal = 16.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(TandemSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        Box(
                            Modifier.size(42.dp).clip(CircleShape).background(tandemPalette.accentSurface),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Lucide.WalletCards,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = tandemPalette.accent,
                            )
                        }
                        Text(
                            "Add account",
                            modifier = Modifier.weight(1f),
                            color = tandemPalette.text,
                            fontSize = TandemTypography.sectionTitle,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                        TandemIconButton(
                            Lucide.X,
                            "Close",
                            sizeDp = 40,
                            iconSizeDp = 18,
                            containerColor = tandemPalette.raised,
                            enabled = !mobileSession.isPerformingBudgetAction,
                        ) { addAccountDialog = false }
                    }
                    TextFieldCard("Name", newAccountName, { newAccountName = it }, TextFieldOptions(placeholder = "Checking"))
                    Box {
                        ListRow(
                            "Account group",
                            trailing = accountGroupOptions.firstOrNull { it.first == newAccountGroupId }?.second ?: "No group",
                            leading = RowLeading.Icon(Lucide.FolderKey),
                            onClick = { accountGroupMenuExpanded = true },
                        )
                        DropdownMenu(
                            expanded = accountGroupMenuExpanded,
                            onDismissRequest = { accountGroupMenuExpanded = false },
                        ) {
                            DropdownMenuItem(onClick = {
                                newAccountGroupId = ""
                                accountGroupMenuExpanded = false
                            }) {
                                Text("No group", color = tandemPalette.text, fontSize = TandemTypography.compactBody)
                            }
                            accountGroupOptions.forEach { (groupId, groupName) ->
                                DropdownMenuItem(onClick = {
                                    newAccountGroupId = groupId
                                    accountGroupMenuExpanded = false
                                }) {
                                    Text(groupName, color = tandemPalette.text, fontSize = TandemTypography.compactBody)
                                }
                            }
                        }
                    }
                    SurfaceCard {
                        CheckBoxRow(
                            "Off budget",
                            "Excluded from the budget",
                            checked = newAccountOffBudget,
                            enabled = !mobileSession.isPerformingBudgetAction,
                            onChange = { newAccountOffBudget = it },
                        )
                    }
                    mobileSession.budgetActionMessage?.let { InlineMessage(it, InlineMessageKind.ERROR) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        TandemActionButton(
                            "Cancel",
                            TandemActionButtonTone.NEUTRAL,
                            Modifier.weight(1f),
                            enabled = !mobileSession.isPerformingBudgetAction,
                        ) { addAccountDialog = false }
                        TandemActionButton(
                            if (mobileSession.isPerformingBudgetAction) "Adding…" else "Add account",
                            TandemActionButtonTone.PRIMARY,
                            Modifier.weight(1f),
                            icon = Lucide.Plus,
                            enabled = !mobileSession.isPerformingBudgetAction && newAccountName.isNotBlank(),
                            loading = mobileSession.isPerformingBudgetAction,
                        ) {
                            scope.launch {
                                val created =
                                    mobileSession.createBudgetAccount(
                                        state.selectedBudgetId,
                                        newAccountName,
                                        newAccountOffBudget,
                                        newAccountGroupId.ifBlank { null },
                                        state.encryptedPassword.takeIf { it.isNotBlank() },
                                    )
                                if (created) addAccountDialog = false
                            }
                        }
                    }
                }
            }
        }
    }
    if (showBankConnectionsDialog) {
        Dialog(
            onDismissRequest = { if (!isRefreshing) showBankConnectionsDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 480.dp).padding(horizontal = 16.dp)) {
                Column(
                    Modifier.fillMaxWidth().padding(TandemSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        Icon(Lucide.Landmark, contentDescription = null, modifier = Modifier.size(24.dp), tint = tandemPalette.accent)
                        Text(
                            "Bank connections",
                            modifier = Modifier.weight(1f),
                            color = tandemPalette.text,
                            fontSize = TandemTypography.sectionTitle,
                            fontWeight = FontWeight.Bold,
                        )
                        TandemIconButton(Lucide.X, "Close", sizeDp = 40, iconSizeDp = 18) { showBankConnectionsDialog = false }
                    }
                    InfoBubble("Review, add, or remove bank links in Actual Budget. Tandem syncs the connections configured there.")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        TandemActionButton("Done", TandemActionButtonTone.NEUTRAL, Modifier.weight(1f)) {
                            showBankConnectionsDialog = false
                        }
                        TandemActionButton(
                            "Sync accounts",
                            TandemActionButtonTone.PRIMARY,
                            Modifier.weight(1f),
                            icon = Lucide.RefreshCw,
                            enabled = !isRefreshing && !state.readOnlyMode,
                            loading = isRefreshing,
                        ) {
                            showBankConnectionsDialog = false
                            runBankSync()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountDetailScreen(
    state: AppState,
    navigation: AppNavigation,
    liveAccounts: List<LiveAccountSummary>,
    mobileSession: TandemMobileSession,
    merchantIconMatcher: MerchantIconMatcherPack?,
) {
    val scope = rememberCoroutineScope()
    val isLiveBudget = state.selectedBudgetId.isNotBlank()
    val liveAccount = if (isLiveBudget) liveAccounts.firstOrNull { it.id == state.selectedAccountId } else null
    val previewAccount = if (isLiveBudget) null else previewAccountById(state.selectedAccountId)
    val accountName = liveAccount?.name ?: previewAccount?.name
    var accountActivity by remember(state.selectedBudgetId, liveAccount?.id) { mutableStateOf(emptyList<TransactionItem>()) }
    var clearedAccountBalanceMinor by remember(state.selectedBudgetId, liveAccount?.id) { mutableStateOf<Long?>(null) }
    var showReconcileDialog by rememberSaveable(state.selectedBudgetId, liveAccount?.id) { mutableStateOf(false) }
    var selectedReconciliationIds by remember(state.selectedBudgetId, liveAccount?.id) { mutableStateOf(emptySet<String>()) }
    var statementBalanceText by rememberSaveable(state.selectedBudgetId, liveAccount?.id) { mutableStateOf("") }
    LaunchedEffect(
        state.selectedBudgetId,
        liveAccount?.id,
        mobileSession.accountRecords,
        mobileSession.payeeRecords,
        mobileSession.categoryRecords,
        mobileSession.budgetDisplaySettings,
        merchantIconMatcher,
    ) {
        val accountId = liveAccount?.id ?: return@LaunchedEffect
        val accountActivityFlow =
            mobileSession.observeAccountActivityRecords(state.selectedBudgetId, accountId, limit = 500)
                ?: return@LaunchedEffect
        accountActivityFlow.collect { records ->
            accountActivity =
                withContext(Dispatchers.Default) {
                    buildLiveTransactionItems(
                        records,
                        mobileSession.accountRecords,
                        mobileSession.payeeRecords,
                        mobileSession.categoryRecords,
                        mobileSession.budgetDisplaySettings,
                        merchantIconMatcher,
                    ).filterNot(TransactionItem::isSplitParent)
                }
        }
    }
    LaunchedEffect(state.selectedBudgetId, liveAccount?.id) {
        val accountId = liveAccount?.id ?: return@LaunchedEffect
        val clearedBalanceFlow = mobileSession.observeClearedAccountBalance(state.selectedBudgetId, accountId) ?: return@LaunchedEffect
        clearedBalanceFlow.collect { clearedAccountBalanceMinor = it }
    }
    val shownAccountActivity =
        when {
            liveAccount != null -> accountActivity
            previewAccount != null -> previewTransactions.filter { it.account == previewAccount.name }
            else -> emptyList()
        }
    val offBudget = liveAccount?.offBudget ?: previewAccount?.offBudget ?: false
    val reconciliationCandidates =
        shownAccountActivity.filter { !it.isSplitParent && it.reconciled != true && it.recordId != null }
    val description =
        when {
            liveAccount != null -> if (liveAccount.offBudget) "Off budget" else "On budget"
            previewAccount != null && previewAccount.offBudget -> "Off budget"
            previewAccount != null -> "Updated ${formatShortDate(SampleDate(2026, 5, 14))}"
            else -> ""
        }
    val balance =
        liveAccount?.balanceMinor?.let(state::formatCurrency)
            ?: previewAccount?.balanceMinor?.let(state::formatCurrency)
            ?: "Unavailable"
    PageColumn {
        if (!isLiveBudget) SampleBanner()
        if (accountName == null) {
            Heading("Account details", "This account isn’t saved on this device.")
        } else {
            AmountHero("Available balance", balance, "$accountName · $description")
            if (isLiveBudget && liveAccount != null) {
                TandemActionButton(
                    "Reconcile",
                    TandemActionButtonTone.NEUTRAL,
                    Modifier.fillMaxWidth(),
                    icon = Lucide.Check,
                    enabled =
                        !liveAccount.closed &&
                            clearedAccountBalanceMinor != null &&
                            reconciliationCandidates.isNotEmpty() &&
                            !state.readOnlyMode,
                ) {
                    selectedReconciliationIds = reconciliationCandidates.filter { it.cleared == true }.mapNotNull { it.recordId }.toSet()
                    statementBalanceText = clearedAccountBalanceMinor?.let(state::formatAmount).orEmpty()
                    mobileSession.clearTransactionWriteMessage()
                    showReconcileDialog = true
                }
            }
            PrimaryButton(
                "Add transaction",
                enabled = !offBudget && !state.readOnlyMode,
            ) {
                if (isLiveBudget) {
                    navigation.newTransaction(liveAccount!!.id)
                } else {
                    navigation.open(AppRoute.NEW_TRANSACTION)
                }
            }
            if (shownAccountActivity.isEmpty()) {
                SurfaceCard {
                    EmptyState("No recent activity", modifier = Modifier.padding(TandemSpacing.large))
                }
            } else {
                SectionCard("Recent activity", action = "See all", onClick = { navigation.selectTab(AppRoute.ACTIVITY) }) {
                    shownAccountActivity.take(6).forEachIndexed { index, item ->
                        if (index > 0) DividerLine()
                        TransactionRow(item) { openTransaction(state, navigation, item, isLive = isLiveBudget) }
                    }
                }
            }
            SettingRow(
                "Account options",
                if (isLiveBudget) "Close account" else "$accountName settings",
                onClick = { navigation.open(AppRoute.ACCOUNT_OPTIONS) },
            )
        }
    }
    if (showReconcileDialog && liveAccount != null) {
        val didReconcile = {
            scope.launch {
                if (
                    mobileSession.markTransactionsReconciled(
                        state.selectedBudgetId,
                        liveAccount.id,
                        reconciliationCandidates.mapNotNull { it.recordId }.toSet(),
                        selectedReconciliationIds,
                    )
                ) {
                    showReconcileDialog = false
                }
            }
        }
        AccountReconcileDialog(
            state = state,
            accountName = liveAccount.name,
            clearedBalanceMinor = clearedAccountBalanceMinor,
            transactions = reconciliationCandidates,
            activityTruncated = accountActivity.size >= 500,
            selectedIds = selectedReconciliationIds,
            statementBalance = statementBalanceText,
            busy = mobileSession.isSavingTransaction,
            message = mobileSession.transactionWriteMessage,
            onStatementBalanceChange = { statementBalanceText = it },
            onSelectedChange = { id, selected ->
                selectedReconciliationIds = if (selected) selectedReconciliationIds + id else selectedReconciliationIds - id
            },
            onDismiss = { if (!mobileSession.isSavingTransaction) showReconcileDialog = false },
            onConfirm = { didReconcile() },
        )
    }
}

@Composable
private fun AccountReconcileDialog(
    state: AppState,
    accountName: String,
    clearedBalanceMinor: Long?,
    transactions: List<TransactionItem>,
    activityTruncated: Boolean,
    selectedIds: Set<String>,
    statementBalance: String,
    busy: Boolean,
    message: String?,
    onStatementBalanceChange: (String) -> Unit,
    onSelectedChange: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val statementMinor = state.parseAmountMinorUnits(statementBalance)
    val projectedClearedBalance =
        clearedBalanceMinor?.let { reconciliationClearedBalanceMinor(it, transactions, selectedIds) }
    val differenceMinor =
        if (statementMinor != null && projectedClearedBalance != null) {
            statementMinor - projectedClearedBalance
        } else {
            null
        }
    val balanceMatches = differenceMinor == 0L
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 480.dp).padding(horizontal = 16.dp)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 720.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(TandemSpacing.large),
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                ) {
                    Icon(Lucide.Check, contentDescription = null, modifier = Modifier.size(24.dp), tint = tandemPalette.accent)
                    Text(
                        "Reconcile $accountName",
                        modifier = Modifier.weight(1f),
                        color = tandemPalette.text,
                        fontSize = TandemTypography.sectionTitle,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TandemIconButton(Lucide.X, "Close", sizeDp = 40, iconSizeDp = 18, enabled = !busy, onClick = onDismiss)
                }
                TextFieldCard(
                    "Statement balance",
                    statementBalance,
                    onStatementBalanceChange,
                    TextFieldOptions(
                        placeholder = state.formatAmount(clearedBalanceMinor ?: 0L),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    ),
                )
                SurfaceCard {
                    Column {
                        ListRow("Cleared balance", trailing = projectedClearedBalance?.let(state::formatCurrency) ?: "Unavailable")
                        DividerLine()
                        ListRow(
                            "Difference",
                            trailing = differenceMinor?.let(state::formatCurrency) ?: "Enter a balance",
                            leading = RowLeading.Icon(Lucide.Check),
                            options =
                                ListRowOptions(
                                    trailingColor = if (balanceMatches) tandemPalette.positive else tandemPalette.warning,
                                ),
                        )
                    }
                }
                InlineMessage(
                    if (balanceMatches) {
                        "Cleared transactions match this statement balance."
                    } else {
                        "Adjust the selected transactions until the difference is 0."
                    },
                    if (balanceMatches) InlineMessageKind.SUCCESS else InlineMessageKind.INFO,
                )
                if (activityTruncated) {
                    InlineMessage("Only the latest 500 account transactions are loaded.", InlineMessageKind.INFO)
                }
                SectionCard("Transactions", "Select the items on this statement") {
                    transactions.forEachIndexed { index, transaction ->
                        val id = transaction.recordId ?: return@forEachIndexed
                        CheckBoxRow(
                            transaction.payee,
                            "${transaction.dateLabel} · ${state.formatCurrency(transaction.amountMinorUnits)}",
                            checked = id in selectedIds,
                            enabled = !busy,
                            onChange = { onSelectedChange(id, it) },
                        )
                        if (index < transactions.lastIndex) DividerLine()
                    }
                }
                message?.let { InlineMessage(it, InlineMessageKind.INFO) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    TandemActionButton("Cancel", TandemActionButtonTone.NEUTRAL, Modifier.weight(1f), enabled = !busy, onClick = onDismiss)
                    TandemActionButton(
                        if (busy) "Working…" else "Mark reconciled",
                        TandemActionButtonTone.PRIMARY,
                        Modifier.weight(1f),
                        icon = Lucide.Check,
                        enabled = !busy && balanceMatches && selectedIds.isNotEmpty(),
                        loading = busy,
                        onClick = onConfirm,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountOptionsScreen(
    state: AppState,
    liveAccounts: List<LiveAccountSummary>,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
) {
    val scope = rememberCoroutineScope()
    val isLiveBudget = state.selectedBudgetId.isNotBlank()
    val liveAccount = if (isLiveBudget) liveAccounts.firstOrNull { it.id == state.selectedAccountId } else null
    val previewAccount = if (isLiveBudget) null else previewAccountById(state.selectedAccountId)
    val accountName = liveAccount?.name ?: previewAccount?.name
    val accountBalance =
        liveAccount?.balanceMinor?.let(::formatCurrency)
            ?: previewAccount?.balanceMinor?.let(::formatCurrency)
            ?: "Unavailable"
    val accountScope = if (liveAccount?.offBudget ?: previewAccount?.offBudget ?: false) "Off budget" else "On budget"
    var confirmClose by rememberSaveable(state.selectedAccountId) { mutableStateOf(false) }
    var renameDialog by rememberSaveable(state.selectedAccountId) { mutableStateOf(false) }
    var renameValue by rememberSaveable(state.selectedAccountId) { mutableStateOf(liveAccount?.name.orEmpty()) }
    var transferAccountId by rememberSaveable(state.selectedAccountId) { mutableStateOf("") }
    var transferCategoryId by rememberSaveable(state.selectedAccountId) { mutableStateOf("") }
    val nonZeroBalance = liveAccount?.balanceMinor?.let { it != 0L } ?: false
    val transferAccounts =
        remember(liveAccounts, liveAccount?.id) {
            liveAccounts.filter { it.id != liveAccount?.id && !it.closed }
        }
    val transferCategories =
        remember(mobileSession.categoryRecords) {
            mobileSession.categoryRecords
                .mapNotNull { record -> record.recordName()?.let { SelectorItem(record.recordId, it) } }
                .distinctBy(SelectorItem::id)
        }
    val reorderableAccounts =
        remember(liveAccounts, liveAccount?.id) {
            liveAccount
                ?.let { selected ->
                    liveAccounts
                        .filter { it.closed == selected.closed && it.offBudget == selected.offBudget }
                        .sortedWith(compareBy({ it.sortOrder ?: Long.MAX_VALUE }, { it.name.lowercase() }))
                }.orEmpty()
        }
    val accountOrderIndex = reorderableAccounts.indexOfFirst { it.id == liveAccount?.id }
    val canClose = isLiveBudget && liveAccount != null && !liveAccount.closed && liveAccount.balanceMinor != null && !state.readOnlyMode
    PageColumn {
        if (!isLiveBudget) SampleBanner()
        if (accountName == null) {
            Heading("Account options")
            EmptyState("Account unavailable", "This account isn’t saved on this device.")
        } else {
            Heading(accountName, "Account options")
            SectionCard("Account", "Read-only details") {
                ListRow("Name", "Account name", accountName)
                DividerLine()
                ListRow("Balance", "Current balance", accountBalance)
                DividerLine()
                ListRow("Budget status", "Account scope", accountScope)
            }
            if (isLiveBudget && liveAccount != null) {
                SectionCard(
                    "Position in ${if (liveAccount.closed) {
                        "closed accounts"
                    } else if (liveAccount.offBudget) {
                        "off-budget accounts"
                    } else {
                        "on-budget accounts"
                    }}",
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        TandemIconButton(
                            Lucide.ChevronUp,
                            "Move account up",
                            enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction && accountOrderIndex > 0,
                        ) {
                            scope.launch {
                                mobileSession.moveBudgetAccount(
                                    state.selectedBudgetId,
                                    liveAccount.id,
                                    reorderableAccounts.getOrNull(accountOrderIndex - 1)?.id,
                                    state.encryptedPassword.takeIf { it.isNotBlank() },
                                )
                            }
                        }
                        TandemIconButton(
                            Lucide.ChevronDown,
                            "Move account down",
                            enabled =
                                !state.readOnlyMode &&
                                    !mobileSession.isPerformingBudgetAction &&
                                    accountOrderIndex >= 0 &&
                                    accountOrderIndex < reorderableAccounts.lastIndex,
                        ) {
                            scope.launch {
                                mobileSession.moveBudgetAccount(
                                    state.selectedBudgetId,
                                    liveAccount.id,
                                    reorderableAccounts.getOrNull(accountOrderIndex + 2)?.id,
                                    state.encryptedPassword.takeIf { it.isNotBlank() },
                                )
                            }
                        }
                    }
                }
                SecondaryButton(
                    "Rename account",
                    enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
                    onClick = {
                        renameValue = liveAccount.name
                        mobileSession.clearBudgetActionMessage()
                        renameDialog = true
                    },
                )
                if (liveAccount.closed) {
                    InlineMessage("This account is closed.", InlineMessageKind.INFO)
                    SecondaryButton(
                        "Reopen account",
                        enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
                        onClick = {
                            scope.launch {
                                if (mobileSession.reopenBudgetAccount(
                                        state.selectedBudgetId,
                                        liveAccount.id,
                                        state.encryptedPassword.takeIf { it.isNotBlank() },
                                    )
                                ) {
                                    navigation.selectTab(AppRoute.ACCOUNTS)
                                }
                            }
                        },
                    )
                } else {
                    SecondaryButton(
                        "Close account",
                        enabled = canClose && !mobileSession.isPerformingBudgetAction,
                        onClick = {
                            transferAccountId = ""
                            transferCategoryId = ""
                            mobileSession.clearBudgetActionMessage()
                            confirmClose = true
                        },
                    )
                }
                mobileSession.budgetActionMessage?.let { InlineMessage(it, InlineMessageKind.ERROR) }
            }
        }
    }
    if (renameDialog && liveAccount != null) {
        AlertDialog(
            onDismissRequest = { if (!mobileSession.isPerformingBudgetAction) renameDialog = false },
            title = { Text("Rename account", color = tandemPalette.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    TextFieldCard("Name", renameValue, { renameValue = it }, TextFieldOptions(placeholder = "Account name"))
                    mobileSession.budgetActionMessage?.let { InlineMessage(it, InlineMessageKind.ERROR) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = renameValue.isNotBlank() && !mobileSession.isPerformingBudgetAction,
                    onClick = {
                        scope.launch {
                            if (mobileSession.renameBudgetAccount(
                                    state.selectedBudgetId,
                                    liveAccount.id,
                                    renameValue,
                                    state.encryptedPassword.takeIf { it.isNotBlank() },
                                )
                            ) {
                                renameDialog = false
                            }
                        }
                    },
                ) {
                    Text(if (mobileSession.isPerformingBudgetAction) "Saving…" else "Save", color = tandemPalette.accent)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !mobileSession.isPerformingBudgetAction,
                    onClick = { renameDialog = false },
                ) {
                    Text("Cancel", color = tandemPalette.muted)
                }
            },
            backgroundColor = tandemPalette.surface,
        )
    }
    if (confirmClose && liveAccount != null) {
        AlertDialog(
            onDismissRequest = { if (!mobileSession.isPerformingBudgetAction) confirmClose = false },
            title = { Text("Close ${liveAccount.name}?", color = tandemPalette.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    BodyText("Actual will close this account and unlink its bank connection.")
                    if (nonZeroBalance) {
                        AccountActionDropdown(
                            title = "Move balance to",
                            selectedLabel = transferAccounts.firstOrNull { it.id == transferAccountId }?.name ?: "Choose account",
                            options = transferAccounts.map { it.name to it.id },
                            onSelect = { transferAccountId = it.orEmpty() },
                        )
                        AccountActionDropdown(
                            title = "Category",
                            selectedLabel = transferCategories.firstOrNull { it.id == transferCategoryId }?.label ?: "Uncategorized",
                            options = listOf("Uncategorized" to null) + transferCategories.map { it.label to it.id },
                            onSelect = { transferCategoryId = it.orEmpty() },
                        )
                    }
                    mobileSession.budgetActionMessage?.let { InlineMessage(it, InlineMessageKind.ERROR) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled =
                        !mobileSession.isPerformingBudgetAction &&
                            (!nonZeroBalance || transferAccountId.isNotBlank()),
                    onClick = {
                        scope.launch {
                            val closed =
                                mobileSession.closeBudgetAccount(
                                    state.selectedBudgetId,
                                    liveAccount.id,
                                    transferAccountId.takeIf(String::isNotBlank),
                                    transferCategoryId.takeIf(String::isNotBlank),
                                    state.encryptedPassword.takeIf { it.isNotBlank() },
                                )
                            if (closed) {
                                confirmClose = false
                                navigation.selectTab(AppRoute.ACCOUNTS)
                            }
                        }
                    },
                ) {
                    Text(if (mobileSession.isPerformingBudgetAction) "Closing…" else "Close account", color = tandemPalette.negative)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !mobileSession.isPerformingBudgetAction,
                    onClick = { confirmClose = false },
                ) {
                    Text("Cancel", color = tandemPalette.muted)
                }
            },
            backgroundColor = tandemPalette.surface,
        )
    }
}

@Composable
private fun AccountActionDropdown(
    title: String,
    selectedLabel: String,
    options: List<Pair<String, String?>>,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SurfaceCard(onClick = { expanded = true }) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.medium, vertical = TandemSpacing.small),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall)) {
                    Text(title, color = tandemPalette.muted, fontSize = TandemTypography.caption)
                    Text(selectedLabel, color = tandemPalette.text, fontSize = TandemTypography.body, maxLines = 1)
                }
                Icon(Lucide.ChevronDown, contentDescription = null, modifier = Modifier.size(20.dp), tint = tandemPalette.muted)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (label, value) ->
                DropdownMenuItem(
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                ) {
                    Text(label, color = tandemPalette.text, fontSize = TandemTypography.compactBody)
                }
            }
        }
    }
}

@Composable
private fun ActivityScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    liveTransactions: List<TransactionItem>,
    sortCache: ActivityTransactionSortCache,
    isRefreshing: Boolean,
    onSyncNow: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val isLiveBudget = state.selectedBudgetId.isNotBlank()
    var selectionMode by remember(state.selectedBudgetId) { mutableStateOf(false) }
    var selectedTransactionIds by remember(state.selectedBudgetId) { mutableStateOf<Set<String>>(emptySet()) }
    var confirmBulkDelete by remember(state.selectedBudgetId) { mutableStateOf(false) }
    var isDeletingSelection by remember(state.selectedBudgetId) { mutableStateOf(false) }
    val transactionSource = if (isLiveBudget) liveTransactions else previewTransactions
    val transactions = if (isLiveBudget) transactionSource.filterNot(TransactionItem::isSplitParent) else transactionSource
    val selectedSortOrder = state.activitySortOrder
    val cachedSorted = sortCache.get(state.selectedBudgetId, transactionSource, selectedSortOrder)
    var sortedTransactions by
        remember(state.selectedBudgetId, transactionSource, selectedSortOrder) {
            mutableStateOf(cachedSorted ?: transactions)
        }
    LaunchedEffect(state.selectedBudgetId, transactionSource, selectedSortOrder) {
        sortedTransactions =
            sortCache.get(state.selectedBudgetId, transactionSource, selectedSortOrder)
                ?: withContext(Dispatchers.Default) {
                    sortActivityTransactions(transactions, selectedSortOrder)
                }.also { sortCache.put(state.selectedBudgetId, transactionSource, selectedSortOrder, it) }
    }
    PageLazyColumn(
        isRefreshing = isRefreshing,
        onRefresh = onSyncNow.takeIf { isLiveBudget },
    ) {
        if (!isLiveBudget) item(key = "activity-preview-banner") { SampleBanner() }
        item(key = "activity-search-entry") {
            SurfaceCard(onClick = { openGeneralActivitySearch(state, navigation) }) {
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
        item(key = "activity-sort") {
            ActivitySortControl(state.activitySortOrder) { state.activitySortOrder = it }
        }
        val selectableIds = sortedTransactions.mapNotNull(TransactionItem::recordId).toSet()
        if (selectionMode) {
            item(key = "activity-selection-actions") {
                TransactionSelectionActions(
                    selectedCount = selectedTransactionIds.size,
                    allSelected = selectableIds.isNotEmpty() && selectedTransactionIds.containsAll(selectableIds),
                    onToggleAll = {
                        selectedTransactionIds =
                            if (selectableIds.isNotEmpty() && selectedTransactionIds.containsAll(selectableIds)) {
                                emptySet()
                            } else {
                                selectableIds
                            }
                    },
                    onDelete = { confirmBulkDelete = true },
                    onCancel = {
                        selectionMode = false
                        selectedTransactionIds = emptySet()
                    },
                )
            }
        }
        val recentDescription = if (isLiveBudget) state.activitySortOrder.label else "${transactions.size} sample entries"
        if (transactions.isEmpty()) {
            item(key = "activity-empty") {
                SectionCard("Recent transactions", recentDescription) {
                    EmptyState(
                        if (isLiveBudget) "No transactions yet" else "No sample transactions",
                        if (isLiveBudget) "Sync the budget to refresh." else null,
                        modifier = Modifier.padding(TandemSpacing.large),
                    )
                }
            }
        } else {
            item(key = "activity-recent-title") { SectionCard("Recent transactions", recentDescription) {} }
            itemsIndexed(
                sortedTransactions,
                key = { index, transaction ->
                    val prefix = if (isLiveBudget) "activity" else "sample"
                    "$prefix:${transaction.recordId ?: "$index:${transaction.dateLabel}:${transaction.payee}:${transaction.amountMinorUnits}"}"
                },
            ) { _, transaction ->
                SurfaceCard(modifier = if (isLiveBudget) tandemListItemModifier(tandemReduceMotion) else Modifier) {
                    val transactionId = transaction.recordId
                    TransactionRow(
                        transaction,
                        selectionMode = selectionMode,
                        selected = transactionId != null && transactionId in selectedTransactionIds,
                        onLongClick =
                            if (isLiveBudget && !state.readOnlyMode && transactionId != null) {
                                {
                                    selectionMode = true
                                    selectedTransactionIds = selectedTransactionIds + transactionId
                                }
                            } else {
                                null
                            },
                    ) {
                        if (selectionMode && transactionId != null) {
                            selectedTransactionIds =
                                if (transactionId in selectedTransactionIds) {
                                    selectedTransactionIds - transactionId
                                } else {
                                    selectedTransactionIds + transactionId
                                }
                            if (selectedTransactionIds.isEmpty()) selectionMode = false
                        } else {
                            openTransaction(state, navigation, transaction, isLive = isLiveBudget)
                        }
                    }
                }
            }
        }
        if (!isLiveBudget) item(key = "activity-sample-note") { PreviewNote("Transactions use sample data.") }
    }
    mobileSession.transactionWriteMessage?.takeIf { selectionMode }?.let { InlineMessage(it, InlineMessageKind.ERROR) }
    if (confirmBulkDelete) {
        TransactionBulkDeleteDialog(
            selectedCount = selectedTransactionIds.size,
            isDeleting = isDeletingSelection,
            onDismiss = { confirmBulkDelete = false },
            onConfirm = {
                scope.launch {
                    isDeletingSelection = true
                    val deleted = deleteTransactionSelection(mobileSession, state.selectedBudgetId, selectedTransactionIds)
                    isDeletingSelection = false
                    confirmBulkDelete = false
                    if (deleted) {
                        selectedTransactionIds = emptySet()
                        selectionMode = false
                    }
                }
            },
        )
    }
}

@Composable
private fun ActivitySortControl(
    selected: ActivitySortOrder,
    onSelect: (ActivitySortOrder) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text("Sort: ${selected.label}", color = tandemPalette.text, fontSize = TandemTypography.caption)
            Icon(
                Lucide.ChevronDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp).padding(start = 2.dp),
                tint = tandemPalette.muted,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ActivitySortOrder.entries.forEach { option ->
                DropdownMenuItem(
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                ) {
                    Text(option.label, color = tandemPalette.text, fontSize = TandemTypography.compactBody)
                }
            }
        }
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
        amountMinorUnits = amountMinorUnits,
        cleared = cleared,
        reconciled = reconciled,
        approved = approved,
        matched = matched,
        transfer = isTransfer,
        recordId = recordId,
    )

internal fun buildSearchActivityRecords(
    records: List<SearchableBudgetTransaction>,
    accounts: Map<String, String>,
    payees: Map<String, String>,
    categories: Map<String, String>,
): List<ActivitySearchRecord> =
    records.filterNot(SearchableBudgetTransaction::isParent).mapNotNull { record ->
        val amount = record.amountMinor ?: return@mapNotNull null
        val payee = record.payeeId?.let(payees::get) ?: record.payeeId ?: "No payee"
        val category = record.categoryId?.let(categories::get) ?: record.categoryId ?: "Uncategorized"
        ActivitySearchRecord(
            payee = payee,
            payeeId = record.payeeId,
            account = record.accountId?.let(accounts::get) ?: record.accountId ?: "No account",
            accountId = record.accountId,
            category = category,
            categoryId = record.categoryId,
            notes = record.notes.orEmpty(),
            dateIso = record.recordDate.orEmpty(),
            amountMinorUnits = amount,
            cleared = record.cleared,
            reconciled = record.reconciled,
            approved = record.approved,
            matched = record.matched,
            transfer = record.transfer == true,
            recordId = record.recordId,
        )
    }

private fun ActivitySearchRecord.toTransactionItem(
    displaySettings: BudgetDisplaySettings?,
    merchantIconMatcher: MerchantIconMatcherPack?,
): TransactionItem =
    TransactionItem(
        payee = payee,
        category = category,
        dateLabel = formatLiveDate(dateIso),
        amountMinorUnits = amountMinorUnits,
        icon = payee.take(1).uppercase(),
        categoryIcon = financeIcon(category),
        merchantIcon = merchantIconMatcher?.iconFor(payee),
        account = account,
        accountId = accountId,
        recordId = recordId,
        notes = notes,
        displaySettings = displaySettings,
        dateIso = dateIso,
        payeeId = payeeId,
        categoryId = categoryId,
        cleared = cleared,
        reconciled = reconciled,
        approved = approved,
        matched = matched,
        isTransfer = transfer,
    )

@Composable
private fun ActivitySearchScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    merchantIconMatcher: MerchantIconMatcherPack?,
) {
    val scope = rememberCoroutineScope()
    var search by rememberSaveable { mutableStateOf(state.activitySearchQuery) }
    val savedSearches =
        remember(state.savedActivitySearchesJson, state.selectedBudgetId) {
            decodeSavedActivitySearches(state.savedActivitySearchesJson)
                .filter { it.budgetId == state.selectedBudgetId }
        }
    var showSaveSearchDialog by rememberSaveable { mutableStateOf(false) }
    var savedSearchNameDraft by rememberSaveable { mutableStateOf("") }
    val isLiveBudget = state.selectedBudgetId.isNotBlank()
    var selectionMode by remember(state.selectedBudgetId) { mutableStateOf(false) }
    var selectedTransactionIds by remember(state.selectedBudgetId) { mutableStateOf<Set<String>>(emptySet()) }
    var confirmBulkDelete by remember(state.selectedBudgetId) { mutableStateOf(false) }
    var isDeletingSelection by remember(state.selectedBudgetId) { mutableStateOf(false) }
    val filters = state.activitySearchFilters
    val cachedSearch = state.activitySearchCache?.takeIf { it.budgetId == state.selectedBudgetId }
    val cachedEvaluation =
        state.activitySearchResultCache?.takeIf { cached ->
            cached.budgetId == state.selectedBudgetId &&
                cached.query == search &&
                cached.filters == filters &&
                cached.sourceRecords === cachedSearch?.searchableRecords
        }
    LaunchedEffect(state.selectedBudgetId, mobileSession) {
        if (isLiveBudget) mobileSession.observeActivitySearchRecords(state.selectedBudgetId)
    }
    var searchableRecords by remember(state.selectedBudgetId) {
        mutableStateOf(cachedSearch?.searchableRecords ?: previewTransactions.map(TransactionItem::toActivitySearchRecord))
    }
    var loadedSearchBudgetId by remember(state.selectedBudgetId) {
        mutableStateOf(cachedSearch?.budgetId ?: "")
    }
    var isBuildingSearchRecords by remember(state.selectedBudgetId) { mutableStateOf(isLiveBudget && cachedSearch == null) }
    LaunchedEffect(
        state.selectedBudgetId,
        mobileSession.activitySearchRecordsBudgetId,
        mobileSession.activitySearchRecords,
        mobileSession.accountRecords,
        mobileSession.payeeRecords,
        mobileSession.categoryRecords,
        mobileSession.budgetDisplaySettings,
    ) {
        if (!isLiveBudget) {
            searchableRecords = previewTransactions.map(TransactionItem::toActivitySearchRecord)
            loadedSearchBudgetId = ""
            isBuildingSearchRecords = false
        } else if (mobileSession.activitySearchRecordsBudgetId != state.selectedBudgetId) {
            searchableRecords = emptyList()
            loadedSearchBudgetId = ""
            isBuildingSearchRecords = false
        } else {
            val records = mobileSession.activitySearchRecords
            val accountRecords = mobileSession.accountRecords
            val payeeRecords = mobileSession.payeeRecords
            val categoryRecords = mobileSession.categoryRecords
            val displaySettings = mobileSession.budgetDisplaySettings
            val currentCache = state.activitySearchCache
            if (
                currentCache?.matches(
                    state.selectedBudgetId,
                    records,
                    accountRecords,
                    payeeRecords,
                    categoryRecords,
                    displaySettings,
                ) == true
            ) {
                searchableRecords = currentCache.searchableRecords
                loadedSearchBudgetId = state.selectedBudgetId
                isBuildingSearchRecords = false
            } else {
                isBuildingSearchRecords = true
                val indexed =
                    withContext(Dispatchers.Default) {
                        buildSearchActivityRecords(
                            records = records,
                            accounts =
                                accountRecords
                                    .mapNotNull { record ->
                                        record.recordName()?.let { record.recordId to it }
                                    }.toMap(),
                            payees =
                                payeeRecords
                                    .mapNotNull { record ->
                                        record.recordName()?.let { record.recordId to it }
                                    }.toMap(),
                            categories =
                                categoryRecords
                                    .mapNotNull { record ->
                                        record.recordName()?.let { record.recordId to it }
                                    }.toMap(),
                        )
                    }
                state.activitySearchCache =
                    ActivitySearchCache(
                        state.selectedBudgetId,
                        records,
                        accountRecords,
                        payeeRecords,
                        categoryRecords,
                        displaySettings,
                        indexed,
                    )
                searchableRecords = indexed
                loadedSearchBudgetId = state.selectedBudgetId
                isBuildingSearchRecords = false
            }
        }
    }
    val quickFilters =
        remember(searchableRecords) {
            buildList {
                addAll(recentActivitySearchMonthFilters(searchableRecords))
                if (searchableRecords.any { it.cleared != null }) {
                    add(ActivitySearchFilter(ActivitySearchField.CLEARED, "", "Cleared"))
                    add(ActivitySearchFilter(ActivitySearchField.UNCLEARED, "", "Uncleared"))
                }
                if (searchableRecords.any { it.reconciled != null }) {
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
                searchableRecords
                    .asSequence()
                    .flatMap { it.tagNames.asSequence() }
                    .distinct()
                    .sortedBy(String::lowercase)
                    .take(4)
                    .forEach { tag -> add(ActivitySearchFilter(ActivitySearchField.TAG, tag, "Tag: #$tag")) }
            }
        }
    var suggestions by remember(state.selectedBudgetId) {
        mutableStateOf(cachedEvaluation?.suggestions ?: emptyList())
    }
    var resultIndices by remember(state.selectedBudgetId) {
        mutableStateOf(cachedEvaluation?.indices ?: emptyList())
    }
    var isEvaluatingSearch by remember(state.selectedBudgetId) { mutableStateOf(cachedEvaluation == null) }
    LaunchedEffect(state.selectedBudgetId, search, filters, searchableRecords, quickFilters, state.budgetDisplaySettings) {
        val currentEvaluation = state.activitySearchResultCache
        if (
            currentEvaluation?.budgetId == state.selectedBudgetId &&
            currentEvaluation.query == search &&
            currentEvaluation.filters == filters &&
            currentEvaluation.sourceRecords === searchableRecords
        ) {
            resultIndices = currentEvaluation.indices
            suggestions = currentEvaluation.suggestions
            isEvaluatingSearch = false
            return@LaunchedEffect
        }
        isEvaluatingSearch = true
        if (search.isNotBlank()) delay(150)
        val displaySettings = state.budgetDisplaySettings
        val evaluation =
            withContext(Dispatchers.Default) {
                val preparedSearch = prepareActivitySearch(search, filters, displaySettings?.currencyCode)
                val matchedIndices =
                    if (search.isBlank() && filters.isEmpty()) {
                        searchableRecords.indices.toList()
                    } else {
                        searchableRecords.mapIndexedNotNull { index, record ->
                            index.takeIf { matchesActivitySearch(record, preparedSearch) }
                        }
                    }
                val refinements =
                    if (search.isBlank()) {
                        quickFilters
                    } else {
                        activitySearchSuggestions(search, searchableRecords, displaySettings?.currencyCode) {
                            formatCurrency(it, displaySettings)
                        }
                    }
                matchedIndices to refinements
            }
        state.activitySearchResultCache =
            ActivitySearchResultCache(
                state.selectedBudgetId,
                search,
                filters,
                searchableRecords,
                evaluation.first,
                evaluation.second,
            )
        resultIndices = evaluation.first
        suggestions = evaluation.second
        isEvaluatingSearch = false
    }
    var sortedResultIndices by remember(state.selectedBudgetId) {
        mutableStateOf(cachedEvaluation?.indices ?: emptyList())
    }
    val resultsListState = LocalRouteLazyListState.current ?: rememberLazyListState()
    val visibleTransactionIds =
        remember(sortedResultIndices, searchableRecords) {
            sortedResultIndices.mapNotNull { searchableRecords.getOrNull(it)?.recordId }.toSet()
        }
    LaunchedEffect(search, filters) {
        selectionMode = false
        selectedTransactionIds = emptySet()
    }
    val selectedSortOrder = state.activitySortOrder
    LaunchedEffect(resultIndices, searchableRecords, selectedSortOrder) {
        sortedResultIndices =
            if (selectedSortOrder == ActivitySortOrder.NEWEST_FIRST) {
                resultIndices
            } else {
                withContext(Dispatchers.Default) {
                    sortActivitySearchIndices(resultIndices, searchableRecords, selectedSortOrder)
                }
            }
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
        if (savedSearches.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
            ) {
                savedSearches.forEach { savedSearch ->
                    SurfaceCard(onClick = {
                        search = savedSearch.query
                        state.activitySearchQuery = savedSearch.query
                        state.activitySearchFilters = savedSearch.filters
                    }) {
                        Row(
                            Modifier.widthIn(min = 132.dp, max = 220.dp).padding(start = TandemSpacing.medium),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                        ) {
                            Column(Modifier.weight(1f).padding(vertical = TandemSpacing.small)) {
                                Text(
                                    savedSearch.name,
                                    color = tandemPalette.text,
                                    fontSize = TandemTypography.caption,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${savedSearch.filters.size} filters",
                                    color = tandemPalette.muted,
                                    fontSize = TandemTypography.caption,
                                    maxLines = 1,
                                )
                            }
                            TandemIconButton(
                                Lucide.X,
                                "Remove ${savedSearch.name}",
                                sizeDp = 36,
                                iconSizeDp = 16,
                            ) {
                                state.savedActivitySearchesJson =
                                    encodeSavedActivitySearches(
                                        decodeSavedActivitySearches(state.savedActivitySearchesJson) - savedSearch,
                                    )
                            }
                        }
                    }
                }
            }
        }
        if (search.isNotBlank() || filters.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = {
                        savedSearchNameDraft =
                            search.trim().ifBlank {
                                filters.joinToString(" · ") { it.label }.take(80)
                            }
                        showSaveSearchDialog = true
                    },
                ) {
                    Icon(Lucide.Plus, contentDescription = null, modifier = Modifier.size(18.dp), tint = tandemPalette.accent)
                    Spacer(Modifier.width(TandemSpacing.extraSmall))
                    Text("Save search", color = tandemPalette.accent, fontSize = TandemTypography.caption)
                }
            }
        }
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
                        val isActive = isActivitySearchFilterActive(state.activitySearchFilters, suggestion)
                        ActivitySearchQuickFilter(suggestion, isActive) {
                            state.activitySearchFilters =
                                toggleActivitySearchFilter(state.activitySearchFilters, suggestion)
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
                            state.activitySearchFilters =
                                toggleActivitySearchFilter(state.activitySearchFilters, suggestion)
                            search = ""
                            state.activitySearchQuery = ""
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Results", color = tandemPalette.text, fontSize = TandemTypography.cardTitle, fontWeight = FontWeight.SemiBold)
                Text(formatInteger(resultIndices.size), color = tandemPalette.muted, fontSize = TandemTypography.caption)
            }
            ActivitySortControl(state.activitySortOrder) { state.activitySortOrder = it }
        }
        if (selectionMode) {
            TransactionSelectionActions(
                selectedCount = selectedTransactionIds.size,
                allSelected = visibleTransactionIds.isNotEmpty() && selectedTransactionIds.containsAll(visibleTransactionIds),
                onToggleAll = {
                    selectedTransactionIds =
                        if (visibleTransactionIds.isNotEmpty() && selectedTransactionIds.containsAll(visibleTransactionIds)) {
                            emptySet()
                        } else {
                            visibleTransactionIds
                        }
                },
                onDelete = { confirmBulkDelete = true },
                onCancel = {
                    selectionMode = false
                    selectedTransactionIds = emptySet()
                },
            )
            mobileSession.transactionWriteMessage?.let { InlineMessage(it, InlineMessageKind.ERROR) }
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
        } else if (isEvaluatingSearch && resultIndices.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = tandemPalette.accent)
            }
        } else if (resultIndices.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState(
                    if (filters.isEmpty() &&
                        search.isBlank()
                    ) {
                        "No transactions yet"
                    } else {
                        "No matching transactions"
                    },
                    modifier = Modifier.padding(TandemSpacing.large),
                )
            }
        } else {
            LazyColumn(
                state = resultsListState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
            ) {
                items(
                    sortedResultIndices,
                    key = { index ->
                        val record = searchableRecords.getOrNull(index)
                        "search:${record?.recordId ?: index}"
                    },
                ) { index ->
                    searchableRecords.getOrNull(index)?.let { record ->
                        val transaction =
                            remember(record, state.budgetDisplaySettings, merchantIconMatcher) {
                                record.toTransactionItem(state.budgetDisplaySettings, merchantIconMatcher)
                            }
                        val transactionId = transaction.recordId
                        TransactionRow(
                            transaction,
                            selectionMode = selectionMode,
                            selected = transactionId != null && transactionId in selectedTransactionIds,
                            onLongClick =
                                if (isLiveBudget && !state.readOnlyMode && transactionId != null) {
                                    {
                                        selectionMode = true
                                        selectedTransactionIds = selectedTransactionIds + transactionId
                                    }
                                } else {
                                    null
                                },
                        ) {
                            if (selectionMode && transactionId != null) {
                                selectedTransactionIds =
                                    if (transactionId in selectedTransactionIds) {
                                        selectedTransactionIds - transactionId
                                    } else {
                                        selectedTransactionIds + transactionId
                                    }
                                if (selectedTransactionIds.isEmpty()) selectionMode = false
                            } else {
                                openTransaction(state, navigation, transaction, isLive = isLiveBudget)
                            }
                        }
                    }
                }
            }
        }
    }
    if (showSaveSearchDialog) {
        Dialog(
            onDismissRequest = { showSaveSearchDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 400.dp).padding(horizontal = TandemSpacing.large)) {
                Column(
                    Modifier.fillMaxWidth().padding(TandemSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                ) {
                    Heading("Save search")
                    TextFieldCard(
                        "Name",
                        savedSearchNameDraft,
                        { savedSearchNameDraft = it.take(80) },
                        TextFieldOptions(placeholder = "Name this search"),
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                    ) {
                        TandemActionButton(
                            "Cancel",
                            TandemActionButtonTone.NEUTRAL,
                            modifier = Modifier.weight(1f),
                            onClick = { showSaveSearchDialog = false },
                        )
                        TandemActionButton(
                            "Save",
                            TandemActionButtonTone.PRIMARY,
                            modifier = Modifier.weight(1f),
                            enabled = savedSearchNameDraft.isNotBlank(),
                            onClick = {
                                val entry =
                                    SavedActivitySearch(
                                        budgetId = state.selectedBudgetId,
                                        name = savedSearchNameDraft.trim(),
                                        query = search,
                                        filters = filters,
                                    )
                                val updated =
                                    decodeSavedActivitySearches(state.savedActivitySearchesJson)
                                        .filterNot { it.budgetId == entry.budgetId && it.name.equals(entry.name, ignoreCase = true) } +
                                        entry
                                state.savedActivitySearchesJson = encodeSavedActivitySearches(updated)
                                showSaveSearchDialog = false
                            },
                        )
                    }
                }
            }
        }
    }
    if (confirmBulkDelete) {
        TransactionBulkDeleteDialog(
            selectedCount = selectedTransactionIds.size,
            isDeleting = isDeletingSelection,
            onDismiss = { confirmBulkDelete = false },
            onConfirm = {
                scope.launch {
                    isDeletingSelection = true
                    val deleted = deleteTransactionSelection(mobileSession, state.selectedBudgetId, selectedTransactionIds)
                    isDeletingSelection = false
                    confirmBulkDelete = false
                    if (deleted) {
                        selectedTransactionIds = emptySet()
                        selectionMode = false
                    }
                }
            },
        )
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
                textStyle =
                    TextStyle(
                        color = palette.text,
                        fontSize = TandemTypography.body,
                        lineHeight = TandemTypography.bodyLineHeight,
                        textAlign = TextAlign.Start,
                    ),
                cursorBrush = SolidColor(palette.accent),
                decorationBox = { innerTextField ->
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                "Payee, category or amount",
                                color = palette.muted,
                                fontSize = TandemTypography.body,
                                lineHeight = TandemTypography.bodyLineHeight,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
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
                    ActivitySearchField.DATE_MONTH,
                    ActivitySearchField.DATE_ON,
                    ActivitySearchField.DATE_RANGE,
                    ActivitySearchField.DATE_MONTH_OF_YEAR,
                    ActivitySearchField.DATE_MONTH_DAY,
                    -> Lucide.CalendarDays
                    ActivitySearchField.ACCOUNT -> Lucide.WalletCards
                    ActivitySearchField.CATEGORY, ActivitySearchField.TAG -> Lucide.Tags
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
    isActive: Boolean,
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
        Icon(
            if (isActive) Lucide.Minus else Lucide.Plus,
            contentDescription = if (isActive) "Remove ${filter.label}" else "Add ${filter.label}",
            modifier = Modifier.size(14.dp),
            tint = palette.accent,
        )
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
    val categoryIcon =
        remember(state.categoryIconOverrides, state.transactionCategory, state.newTransactionCategoryId) {
            state.categoryIconFor(state.newTransactionCategoryId, state.transactionCategory)
        }
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
            ListRow("Account", leading = RowLeading.Icon(Lucide.WalletCards), trailing = state.transactionAccount)
            DividerLine()
            ListRow("Category", leading = RowLeading.Icon(Lucide.Tags), trailing = state.transactionCategory)
            DividerLine()
            ListRow("Date", leading = RowLeading.Icon(Lucide.CalendarDays), trailing = state.transactionDate)
            DividerLine()
            ListRow(
                "Notes",
                leading = RowLeading.Icon(Lucide.NotepadText),
                trailing = state.transactionNotes.ifBlank { "No notes" },
            )
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
                        InlineMessage(amountEntryMessage.orEmpty(), InlineMessageKind.ERROR)
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
                        TextFieldOptions(
                            placeholder = "Add a note",
                            singleLine = false,
                            onFocusedChange = { focused -> if (focused) keypadVisible = false },
                        ),
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
        val hasChanges =
            parsedAmount != baselineAmount ||
                state.transactionNotes != originalNotes ||
                state.newTransactionAccountId != state.selectedTransactionAccountId ||
                state.newTransactionPayeeId != state.selectedTransactionPayeeId ||
                state.newTransactionCategoryId != state.selectedTransactionCategoryId ||
                state.transactionDate != state.selectedTransactionDate
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
                TextFieldCard(
                    "Amount",
                    state.transactionAmount,
                    { state.transactionAmount = it },
                    TextFieldOptions(placeholder = state.formatAmount(4_218)),
                )
                TextFieldCard(
                    "Notes",
                    state.transactionNotes,
                    { state.transactionNotes = it },
                    TextFieldOptions(placeholder = "Add a note", singleLine = false),
                )
                SectionCard(title = "") {
                    TransactionPickerRow(state.transactionAccount, "Choose account", Lucide.WalletCards) {
                        navigation.open(AppRoute.CHOOSE_ACCOUNT)
                    }
                    DividerLine()
                    TransactionPickerRow(state.transactionPayee, "Choose payee", Lucide.Receipt) {
                        navigation.open(AppRoute.CHOOSE_PAYEE)
                    }
                    DividerLine()
                    TransactionPickerRow(state.transactionCategory, "Choose category", Lucide.Tags) {
                        navigation.open(AppRoute.CHOOSE_CATEGORY)
                    }
                    DividerLine()
                    TransactionPickerRow(displayTransactionDate(state.transactionDate), "Choose date", Lucide.CalendarDays) {
                        navigation.open(AppRoute.CHOOSE_DATE)
                    }
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
        TextFieldCard(
            "Amount",
            state.transactionAmount,
            { state.transactionAmount = it },
            TextFieldOptions(placeholder = "For example, ${state.formatAmount(1_250)}"),
        )
        ReadOnlyField("Type", state.transactionType, leadingVector = Lucide.Receipt) {
            navigation.open(AppRoute.CHOOSE_TRANSACTION_TYPE)
        }
        ReadOnlyField("Account", state.transactionAccount, "A") { navigation.open(AppRoute.CHOOSE_ACCOUNT) }
        ReadOnlyField("Payee", state.transactionPayee, "P") { navigation.open(AppRoute.CHOOSE_PAYEE) }
        ReadOnlyField("Category", state.transactionCategory, "C") { navigation.open(AppRoute.CHOOSE_CATEGORY) }
        ReadOnlyField("Date", state.transactionDate, "▣") { navigation.open(AppRoute.CHOOSE_DATE) }
        TextFieldCard(
            "Notes",
            state.transactionNotes,
            { state.transactionNotes = it },
            TextFieldOptions(placeholder = "Add a note (optional)", singleLine = false),
        )
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
    topContent: (@Composable () -> Unit)? = null,
    noMatches: (@Composable (String) -> Unit)? = null,
    onSelect: (SelectorItem) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    PageColumn {
        TextFieldCard("Search", query, { query = it }, TextFieldOptions(placeholder = "Start typing"))
        topContent?.invoke()
        val filtered = values.filter { it.label.contains(query, ignoreCase = true) }
        if (filtered.isEmpty()) {
            if (noMatches != null) {
                noMatches(query)
            } else {
                SurfaceCard { EmptyState("No matches", modifier = Modifier.padding(TandemSpacing.large)) }
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
        PeriodStepper(
            title = formatMonth(visibleYear, visibleMonth),
            previousLabel = "Previous month",
            nextLabel = "Next month",
            onPrevious = {
                if (visibleMonth == 1) {
                    visibleMonth = 12
                    visibleYear -= 1
                } else {
                    visibleMonth -= 1
                }
            },
            onNext = {
                if (visibleMonth == 12) {
                    visibleMonth = 1
                    visibleYear += 1
                } else {
                    visibleMonth += 1
                }
            },
        )
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
                        val cellTextColor by
                            animateColorAsState(
                                targetValue = if (selected) tandemPalette.accent else tandemPalette.text,
                                animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
                                label = "date-selection-text",
                            )
                        SelectableTile(
                            selected = selected,
                            contentDescription = displayTransactionDate(isoDate),
                            onSelect = {
                                state.transactionDate = isoDate
                                navigation.back()
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = CircleShape,
                            selectedBackground = tandemPalette.accentSurface,
                            unselectedBackground = Color.Transparent,
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

internal fun currentMonthId(): String = platformTodayIsoDate().take(7).takeIf { it.length == 7 } ?: "2026-05"

internal fun currentMonthLabel(): String {
    val parts = currentMonthId().split('-')
    val year = parts.getOrNull(0)?.toIntOrNull() ?: 2026
    val month = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..12 } ?: 5
    return formatMonth(year, month)
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
            ListRow("Amount", "Corner Market", state.formatCurrency(-4_218))
            DividerLine()
            ListRow("Account and category", "Checking · Groceries")
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
                        EmptyState(
                            "No pending changes",
                            modifier = Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
                        )
                    }
                }
            } else {
                items(attention, key = { "pending:${it.operationId}" }) { operation ->
                    val transaction = liveTransactions.firstOrNull { it.recordId == operation.recordId }
                    SurfaceCard {
                        ListRow(
                            transaction?.payee ?: "Transaction ${operation.recordId.take(8)}",
                            "${writeStatusLabel(operation.status)} · ${transaction?.category ?: "Transaction edit"}",
                            "Review",
                            leading =
                                RowLeading.Icon(
                                    when (operation.status) {
                                        "conflict", "uncertain", "rejected" -> Lucide.CloudAlert
                                        "queued", "sending" -> Lucide.CloudSync
                                        else -> Lucide.Cloud
                                    },
                                ),
                            options =
                                ListRowOptions(
                                    trailingColor =
                                        when (operation.status) {
                                            "conflict", "uncertain", "rejected" -> tandemPalette.warning
                                            else -> tandemPalette.syncing
                                        },
                                ),
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
            ListRow(
                "Needs your review",
                "Corner Market · two versions",
                "1",
                options = ListRowOptions(trailingColor = tandemPalette.warning),
            ) {
                navigation.open(AppRoute.CONFLICT_INBOX)
            }
            DividerLine()
            ListRow(
                "Status unknown",
                "Check before retrying",
                "1",
                options = ListRowOptions(trailingColor = tandemPalette.warning),
            ) { navigation.open(AppRoute.UNCERTAIN_WRITE) }
            DividerLine()
            ListRow(
                "Not applied",
                "New transactions aren’t available yet",
                "1",
                options = ListRowOptions(trailingColor = tandemPalette.negative),
            ) { navigation.open(AppRoute.REJECTED_WRITE) }
        }
        SectionCard("Recent sync activity", "Sample events", "") {
            ListRow("Budget snapshot", "Ready for initial download", "Pending")
            DividerLine()
            ListRow("Local edits", "None in sample data", "0")
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
    val isLiveBudget = state.selectedBudgetId.isNotBlank()
    val conflicts =
        if (isLiveBudget) {
            mobileSession.pendingWrites
                .filter { it.status == "conflict" }
                .map { operation ->
                    val transaction = liveTransactions.firstOrNull { it.recordId == operation.recordId }
                    ConflictInboxItemUi(
                        operation.operationId,
                        transaction?.payee ?: "Transaction ${operation.recordId.take(8)}",
                        "${transaction?.dateLabel ?: "Actual changed"} · ${transaction?.category ?: "Transaction"}",
                    )
                }
        } else {
            listOf(
                ConflictInboxItemUi(
                    "sample-conflict",
                    "Corner Market",
                    "${formatDate(SampleDate(2026, 5, 14))} · Groceries · Checking",
                ),
            )
        }
    PageLazyColumn {
        if (!isLiveBudget) item(key = "conflicts-sample-banner") { SampleBanner() }
        item(key = "conflicts-title") {
            Heading(
                "Changes to review",
                if (isLiveBudget) "Choose whether to keep your edit or the latest Actual version." else "Choose which version to keep.",
            )
        }
        if (conflicts.isEmpty()) {
            item(key = "conflicts-empty") {
                AnimatedAttentionContent(showingAttention = false, animateSizeChanges = true) { _ ->
                    SurfaceCard(modifier = tandemListItemModifier(tandemReduceMotion)) {
                        EmptyState("No changes need review", modifier = Modifier.padding(TandemSpacing.large))
                    }
                }
            }
        } else {
            item(key = "conflicts-section-title") {
                SectionCard("Needs your review", "${conflicts.size} edits") {}
            }
            items(conflicts, key = { "conflict:${it.id}" }) { conflict ->
                SurfaceCard(modifier = tandemListItemModifier(tandemReduceMotion)) {
                    ListRow(
                        conflict.title,
                        conflict.description,
                        "Review",
                        options = ListRowOptions(trailingColor = tandemPalette.warning),
                    ) {
                        state.selectedConflictOperationId = conflict.id
                        navigation.open(AppRoute.CONFLICT_REVIEW)
                    }
                }
            }
        }
        if (isLiveBudget) {
            mobileSession.transactionWriteMessage?.let { message ->
                item(key = "conflicts-write-message") { BodyText(message) }
            }
        }
    }
}

private data class ConflictInboxItemUi(
    val id: String,
    val title: String,
    val description: String,
)

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
    val isLiveBudget = state.selectedBudgetId.isNotBlank()
    val operation =
        if (isLiveBudget) {
            mobileSession.pendingWrites.firstOrNull {
                it.operationId == state.selectedConflictOperationId &&
                    it.status == "conflict"
            }
        } else {
            null
        }
    val record = operation?.let { item -> mobileSession.transactionRecords.firstOrNull { it.recordId == item.recordId } }
    val deviceVersion = operation?.optimisticJson?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
    val actualVersion = record?.remoteJson?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
    val deviceAmount = deviceVersion?.longValue("amount")
    val actualAmount = actualVersion?.longValue("amount")
    val transaction = operation?.let { conflict -> liveTransactions.firstOrNull { it.recordId == conflict.recordId } }
    val deviceAmountText =
        if (isLiveBudget) {
            deviceAmount?.let(::formatSignedCurrency) ?: "Deleted"
        } else {
            state.formatCurrency(1_200)
        }
    val actualAmountText =
        if (isLiveBudget) {
            actualAmount?.let(::formatSignedCurrency) ?: "Deleted"
        } else {
            state.formatCurrency(1_500)
        }
    val accountName = transaction?.account ?: if (isLiveBudget) "Unavailable" else "Checking"
    val categoryName = transaction?.category ?: if (isLiveBudget) "Unavailable" else "Groceries"
    val dateLabel =
        transaction?.dateLabel
            ?: if (isLiveBudget) "Unavailable" else formatShortDate(SampleDate(2026, 5, 14))
    PageColumn {
        if (!isLiveBudget) SampleBanner()
        if (isLiveBudget && operation == null) {
            EmptyState("No change to review", "This change no longer needs attention.")
        } else {
            Heading("Two versions differ")
            Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                ConflictValueCard("Your edit", deviceAmountText, Modifier.weight(1f))
                ConflictValueCard("In Actual", actualAmountText, Modifier.weight(1f))
            }
            SectionCard("Details") {
                ListRow("Account", trailing = accountName)
                DividerLine()
                ListRow("Category", trailing = categoryName)
                DividerLine()
                ListRow("Date", trailing = dateLabel)
            }
            if (isLiveBudget) {
                mobileSession.transactionWriteMessage?.let { BodyText(it) }
                PrimaryButton(
                    "Keep your edit",
                    enabled = !state.readOnlyMode && !mobileSession.isSavingTransaction && record?.remoteDeleted != true,
                ) { onResolveConflict(operation!!.operationId, true) }
                SecondaryButton("Keep Actual", enabled = !state.readOnlyMode && !mobileSession.isSavingTransaction) {
                    onResolveConflict(operation!!.operationId, false)
                }
            } else {
                PrimaryButton("Keep $deviceAmountText") { state.conflictChoice = "Your edit" }
                SecondaryButton("Keep $actualAmountText") { state.conflictChoice = "Actual" }
                if (state.conflictChoice.isNotBlank()) {
                    InfoBubble("Sample choice: ${state.conflictChoice}")
                }
            }
        }
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
            ListRow("Your edit", "Not applied", state.formatCurrency(1_200))
            DividerLine()
            ListRow(
                "Reason",
                "New transactions aren’t available yet",
                "Unavailable",
                options = ListRowOptions(trailingColor = tandemPalette.warning),
            )
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
                "Appearance",
                "Theme, accent, and background",
                state.theme.label,
                leading = RowLeading.Icon(Lucide.Palette),
                onClick = { navigation.open(AppRoute.APPEARANCE) },
            )
            DividerLine()
            ListRow(
                "Home screen layout",
                "Choose how much detail to show",
                state.homeLayout,
                leading = RowLeading.Icon(Lucide.LayoutDashboard),
                onClick = { navigation.open(AppRoute.HOME_LAYOUT) },
            )
            DividerLine()
            ListRow(
                "Budget preferences",
                "Currency and planning choices",
                leading = RowLeading.Icon(Lucide.WalletCards),
                onClick = { navigation.open(AppRoute.BUDGET_PREFERENCES) },
            )
        }
        SectionCard("Connection", "Sync and paired device") {
            ListRow(
                "Sync preferences",
                "Refresh and network behavior",
                leading = RowLeading.Icon(Lucide.CloudSync),
                onClick = { navigation.open(AppRoute.SYNC_PREFERENCES) },
            )
            DividerLine()
            ListRow(
                "Server and paired device",
                "Connection details and access",
                leading = RowLeading.Icon(Lucide.Smartphone),
                onClick = { navigation.open(AppRoute.SERVER_DEVICE) },
            )
            DividerLine()
            ListRow(
                "Notifications",
                "Sync reminders and review alerts",
                leading = RowLeading.Icon(Lucide.Bell),
                onClick = { navigation.open(AppRoute.NOTIFICATIONS) },
            )
        }
        SectionCard("Privacy", "Device data and security") {
            ListRow(
                "Security and privacy",
                "Access and local data",
                leading = RowLeading.Icon(Lucide.ShieldCheck),
                onClick = { navigation.open(AppRoute.SECURITY_PRIVACY) },
            )
            DividerLine()
            ListRow(
                "Local data and storage",
                "Cached budget and pending edits",
                leading = RowLeading.Icon(Lucide.Database),
                onClick = { navigation.open(AppRoute.LOCAL_DATA) },
            )
        }
        SectionCard("Support", "Help and project details") {
            ListRow(
                "Budget rules",
                "Rules from Actual",
                leading = RowLeading.Icon(Lucide.Tags),
                onClick = { navigation.open(AppRoute.BUDGET_RULES) },
            )
            DividerLine()
            ListRow(
                "About, help, and licenses",
                "Version and open-source licenses",
                leading = RowLeading.Icon(Lucide.Info),
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
                "Theme mode",
                "System, light, dark, or pure black",
                state.theme.label,
                leading = RowLeading.Icon(Lucide.SunMoon),
                onClick = { navigation.open(AppRoute.THEME_MODE) },
            )
        }
        SectionCard("Color", "${state.accent.label} accent", "") {
            ListRow(
                "Accent color",
                "Applies to every screen",
                state.accent.label,
                leading = RowLeading.Icon(Lucide.PaintbrushVertical),
                onClick = { navigation.open(AppRoute.ACCENT_COLOR) },
            )
        }
        SectionCard("Background") {
            ListRow(
                "Background and motion",
                "Subtle treatments and reduced motion",
                leading = RowLeading.Icon(Lucide.Wallpaper),
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
            val selected = state.accent == accent
            SelectableTile(
                selected = selected,
                contentDescription = "${accent.label} accent",
                onSelect = { state.accent = accent },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(TandemShapes.large),
                selectedBackground = palette.surface,
                unselectedBackground = palette.surface,
                selectedBorder = palette.border,
                unselectedBorder = palette.border,
            ) {
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
                    if (selected) {
                        Icon(Lucide.Check, contentDescription = null, modifier = Modifier.size(20.dp), tint = palette.accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun BackgroundScreen(state: AppState) {
    PageColumn {
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
                        "Upcoming bills",
                        "Due soon",
                        state.formatCurrency(12_000),
                        leading = RowLeading.Icon(financeIcon("Upcoming bills")),
                        options = ListRowOptions(dense = false),
                    )
                    DividerLine()
                }
                ListRow(
                    "Essentials",
                    "3 categories",
                    state.formatCurrency(232_000),
                    leading = RowLeading.Icon(financeIcon("Essentials")),
                    options = ListRowOptions(dense = state.homeLayout == "Compact"),
                )
                DividerLine()
                ListRow(
                    "Groceries",
                    "Available this month",
                    state.formatCurrency(50_000),
                    leading = RowLeading.Icon(financeIcon("Groceries")),
                    options = ListRowOptions(dense = state.homeLayout == "Compact"),
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
                "Currency",
                if (mobileSession.budgetDisplaySettings != null) "Selected Actual budget" else "Follows the device locale",
                currencyCode,
            )
            DividerLine()
            ListRow("Start of week", "Follows the device locale", formatFirstWeekday())
        }
        SectionCard("Planning") {
            ListRow(
                "Available category amounts",
                "Shown from the synced plan",
                "Available",
                options = ListRowOptions(trailingColor = tandemPalette.positive),
            )
            DividerLine()
            ListRow("Carry balances forward", "Controlled by Actual and shown read-only", "Actual")
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
                "Sync now",
                "Read changes and check pending edits",
                when {
                    mobileSession.isSyncing -> "Syncing"
                    hasSelectedBudget -> "Sync"
                    else -> "Unavailable"
                },
                options = ListRowOptions(animateChanges = true),
                onClick = onSyncNow.takeIf { hasSelectedBudget && !mobileSession.isSyncing },
            )
            DividerLine()
            ListRow(
                "Sync status",
                syncDescription,
                syncStatus,
                options =
                    ListRowOptions(
                        trailingColor =
                            if (mobileSession.syncError !=
                                null
                            ) {
                                tandemPalette.warning
                            } else {
                                null
                            },
                        animateChanges = true,
                    ),
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
                "Server URL",
                state.serverUrl,
                if (mobileSession.isServerReachable) "Connected" else "Disconnected",
                leading = RowLeading.Icon(Lucide.Server),
                options =
                    ListRowOptions(
                        trailingColor = if (mobileSession.isServerReachable) tandemPalette.positive else tandemPalette.negative,
                    ),
                onClick = { navigation.open(AppRoute.SERVER_ADDRESS) },
            )
            DividerLine()
            ListRow(
                "Connection status",
                if (mobileSession.isServerReachable) "Server is reachable" else "Server can’t be reached",
                if (mobileSession.isServerReachable) "Connected" else "Disconnected",
                leading = RowLeading.Icon(Lucide.Wifi),
                options =
                    ListRowOptions(
                        trailingColor = if (mobileSession.isServerReachable) tandemPalette.positive else tandemPalette.negative,
                    ),
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
                "Device name",
                if (device != null) deviceName else "Not paired",
                leading = RowLeading.Icon(Lucide.Smartphone),
            )
            DividerLine()
            ListRow(
                "Budgets this device can open",
                if (device == null) "Not paired" else "${device.budgetIds.size} available",
                leading = RowLeading.Icon(Lucide.FolderKey),
            )
            DividerLine()
            ListRow(
                "Saved pairing",
                if (mobileSession.isCredentialStored) "Protected on this phone" else "Available until the app closes",
                when {
                    mobileSession.isCredentialStored -> "Saved"
                    device != null -> "Active"
                    else -> "None"
                },
                leading = RowLeading.Icon(Lucide.KeyRound),
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
                "Biometric unlock",
                "Not available yet",
                "Unavailable",
                leading = RowLeading.Icon(Lucide.FingerprintPattern),
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
                "Saved pairing",
                if (mobileSession.isCredentialStored) "Protected on this phone" else "Available until the app closes",
                when {
                    mobileSession.isCredentialStored -> "Saved"
                    mobileSession.isAuthenticated -> "Active"
                    else -> "Not paired"
                },
                leading = RowLeading.Icon(Lucide.KeyRound),
            )
            DividerLine()
            ListRow(
                "Budget password",
                if (state.selectedBudgetEncrypted) "Used to unlock this budget" else "Encrypted budgets only",
                if (state.selectedBudgetEncrypted) "Not stored" else "Unavailable",
                leading = RowLeading.Icon(Lucide.LockKeyhole),
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
                "Transactions",
                "Cached transactions",
                formatInteger(mobileSession.cachedTransactionCount),
            )
            DividerLine()
            ListRow("Accounts", "Cached accounts", formatInteger(mobileSession.accountRecords.size))
            DividerLine()
            ListRow(
                "Pending Changes",
                leading = RowLeading.Icon(Lucide.CloudSync),
                trailing = formatInteger(queuedCount),
            )
            DividerLine()
            ListRow(
                "Needs your review",
                "Choose a version",
                formatInteger(conflictCount),
                leading = RowLeading.Icon(Lucide.CloudAlert),
            )
            AnimatedReveal(visible = attentionCount > 0, animateHeight = false) {
                Column {
                    DividerLine()
                    ListRow("Other changes needing attention", "Review locally", formatInteger(attentionCount))
                }
            }
        }
        SectionCard("Storage actions") {
            ListRow(
                "Refresh local budget",
                "Sync this budget",
                when {
                    mobileSession.isSyncing -> "Syncing"
                    hasSelectedBudget -> "Refresh"
                    else -> "Unavailable"
                },
                options = ListRowOptions(animateChanges = true),
                onClick = onSyncNow.takeIf { hasSelectedBudget && !mobileSession.isSyncing },
            )
            DividerLine()
            ListRow("Clear local data", trailing = "Unavailable")
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
                TextFieldCard(
                    "Search",
                    query,
                    { query = it },
                    TextFieldOptions(placeholder = "Name, license, or source"),
                )
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
                    EmptyState("No icons match", "Try another search.")
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
            ListRow("Supported Actual server", "Required by this Tandem release", SUPPORTED_ACTUAL_SERVER_VERSION)
            DividerLine()
            ListRow("Open-source license", "GNU AGPL-3.0-or-later")
        }
        SectionCard("Open-source libraries", "Direct mobile-app dependencies and licenses") {
            ListRow("Kotlin and kotlinx", "Kotlin, Coroutines and Serialization · Apache-2.0")
            DividerLine()
            ListRow("Compose Multiplatform and resources", "Apache-2.0")
            DividerLine()
            ListRow("Ktor", "Apache-2.0")
            DividerLine()
            ListRow("AndroidX Room and SQLite", "Apache-2.0")
            DividerLine()
            ListRow(
                "AndroidX platform libraries",
                "Activity Compose, WorkManager and SplashScreen · Apache-2.0",
            )
            DividerLine()
            ListRow("Compose Icons", "MIT")
            DividerLine()
            ListRow("Lucide icon artwork", "ISC")
            DividerLine()
            ListRow("TheSVG icon package", "Build-time catalog and tooling · MIT")
        }
        SurfaceCard {
            ListRow(
                "View icon licenses and sources",
                leading = RowLeading.Icon(Lucide.Info),
                onClick = { navigation.open(AppRoute.MERCHANT_ICON_LICENSES) },
            )
        }
        AnimatedPreviewNote(merchantIconMatcherLoadError)
    }
}
