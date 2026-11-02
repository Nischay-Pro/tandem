@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private enum class AppRoute(
    val title: String,
) {
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
}

private data class TransactionItem(
    val payee: String,
    val category: String,
    val dateLabel: String,
    val amountMinorUnits: Long,
    val icon: String,
    val account: String = "Checking",
    val recordId: String? = null,
)

private val previewTransactions =
    listOf(
        TransactionItem("Corner Market", "Groceries", formatShortDate(SampleDate(2026, 5, 14)), -4_218, "G"),
        TransactionItem("Electric Company", "Utilities", formatShortDate(SampleDate(2026, 5, 12)), -12_000, "E"),
        TransactionItem("Acme Corp", "Paycheck", formatShortDate(SampleDate(2026, 5, 10)), 250_000, "A"),
        TransactionItem("Riverside Cafe", "Dining", formatShortDate(SampleDate(2026, 5, 8)), -2_840, "R"),
        TransactionItem("City Transit", "Transport", formatShortDate(SampleDate(2026, 5, 6)), -1_650, "C"),
        TransactionItem("Book Nook", "Personal", formatShortDate(SampleDate(2026, 5, 4)), -2_399, "B"),
    )

private fun liveTransactionItems(session: TandemMobileSession): List<TransactionItem> {
    val accounts = session.accountRecords.mapNotNull { record -> record.recordName()?.let { record.recordId to it } }.toMap()
    val payees = session.payeeRecords.mapNotNull { record -> record.recordName()?.let { record.recordId to it } }.toMap()
    val categories = session.categoryRecords.mapNotNull { record -> record.recordName()?.let { record.recordId to it } }.toMap()
    return session.transactionRecords.mapNotNull { record ->
        val amount = record.amountMinor ?: return@mapNotNull null
        val payeeName = record.payeeId?.let(payees::get)
        TransactionItem(
            payee = payeeName ?: record.payeeId ?: "No payee",
            category = record.categoryId?.let(categories::get) ?: record.categoryId ?: "Uncategorized",
            dateLabel = formatLiveDate(record.recordDate),
            amountMinorUnits = amount,
            icon = (payeeName ?: "•").take(1).uppercase(),
            account = record.accountId?.let(accounts::get) ?: record.accountId ?: "No account",
            recordId = record.recordId,
        )
    }
}

private fun BudgetRecordEntity.recordName(): String? {
    val json = localJson ?: remoteJson ?: return null
    return try {
        Json
            .parseToJsonElement(json)
            .jsonObject["name"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }
}

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

private fun formatPairingCodeInput(value: String): String {
    val compact = value.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }.take(8)
    return if (compact.length > 4) "${compact.take(4)}-${compact.drop(4)}" else compact
}

private class AppState {
    var theme by mutableStateOf(ThemeMode.DARK)
    var accent by mutableStateOf(AccentChoice.TEAL)
    var selectedBudget by mutableStateOf("Household")
    var selectedBudgetId by mutableStateOf("")
    var selectedBudgetEncrypted by mutableStateOf(false)
    var selectedMonth by mutableStateOf(formatMonth(2026, 5))
    var serverUrl by mutableStateOf("https://budget.example.com")
    var pairingCode by mutableStateOf("")
    var encryptedPassword by mutableStateOf("")
    var transactionAmount by mutableStateOf(formatAmount(4_218))
    var transactionAccount by mutableStateOf("Checking")
    var transactionPayee by mutableStateOf("Corner Market")
    var transactionCategory by mutableStateOf("Groceries")
    var transactionDate by mutableStateOf(formatDate(SampleDate(2026, 5, 14)))
    var transactionNotes by mutableStateOf("")
    var selectedTransactionAmountMinor by mutableStateOf<Long?>(null)
    var selectedTransactionIsLive by mutableStateOf(false)
    var conflictChoice by mutableStateOf("")
    var backgroundStyle by mutableStateOf("Soft gradient")
    var homeLayout by mutableStateOf("Comfortable")
    var reduceMotion by mutableStateOf(false)
    var syncOnOpen by mutableStateOf(true)
    var syncOnWifi by mutableStateOf(false)
    var notifications by mutableStateOf(true)
    var faceUnlock by mutableStateOf(false)
    var confirmBeforeDelete by mutableStateOf(true)

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
                        "selectedMonth" to state.selectedMonth,
                        "serverUrl" to state.serverUrl,
                        "transactionAmount" to state.transactionAmount,
                        "transactionAccount" to state.transactionAccount,
                        "transactionPayee" to state.transactionPayee,
                        "transactionCategory" to state.transactionCategory,
                        "transactionDate" to state.transactionDate,
                        "transactionNotes" to state.transactionNotes,
                        "selectedTransactionAmountMinor" to state.selectedTransactionAmountMinor,
                        "selectedTransactionIsLive" to state.selectedTransactionIsLive,
                        "conflictChoice" to state.conflictChoice,
                        "backgroundStyle" to state.backgroundStyle,
                        "homeLayout" to state.homeLayout,
                        "reduceMotion" to state.reduceMotion,
                        "syncOnOpen" to state.syncOnOpen,
                        "syncOnWifi" to state.syncOnWifi,
                        "notifications" to state.notifications,
                        "faceUnlock" to state.faceUnlock,
                        "confirmBeforeDelete" to state.confirmBeforeDelete,
                    )
                },
                restore = { saved ->
                    AppState().apply {
                        theme = ThemeMode.entries.firstOrNull { it.name == saved["theme"] } ?: theme
                        accent = AccentChoice.entries.firstOrNull { it.name == saved["accent"] } ?: accent
                        selectedBudget = saved["selectedBudget"] as? String ?: selectedBudget
                        selectedBudgetId = saved["selectedBudgetId"] as? String ?: selectedBudgetId
                        selectedBudgetEncrypted = saved["selectedBudgetEncrypted"] as? Boolean ?: selectedBudgetEncrypted
                        selectedMonth = saved["selectedMonth"] as? String ?: selectedMonth
                        serverUrl = saved["serverUrl"] as? String ?: serverUrl
                        transactionAmount = saved["transactionAmount"] as? String ?: transactionAmount
                        transactionAccount = saved["transactionAccount"] as? String ?: transactionAccount
                        transactionPayee = saved["transactionPayee"] as? String ?: transactionPayee
                        transactionCategory = saved["transactionCategory"] as? String ?: transactionCategory
                        transactionDate = saved["transactionDate"] as? String ?: transactionDate
                        transactionNotes = saved["transactionNotes"] as? String ?: transactionNotes
                        selectedTransactionAmountMinor = saved["selectedTransactionAmountMinor"] as? Long
                        selectedTransactionIsLive = saved["selectedTransactionIsLive"] as? Boolean ?: selectedTransactionIsLive
                        conflictChoice = saved["conflictChoice"] as? String ?: conflictChoice
                        backgroundStyle = saved["backgroundStyle"] as? String ?: backgroundStyle
                        homeLayout = saved["homeLayout"] as? String ?: homeLayout
                        reduceMotion = saved["reduceMotion"] as? Boolean ?: reduceMotion
                        syncOnOpen = saved["syncOnOpen"] as? Boolean ?: syncOnOpen
                        syncOnWifi = saved["syncOnWifi"] as? Boolean ?: syncOnWifi
                        notifications = saved["notifications"] as? Boolean ?: notifications
                        faceUnlock = saved["faceUnlock"] as? Boolean ?: faceUnlock
                        confirmBeforeDelete = saved["confirmBeforeDelete"] as? Boolean ?: confirmBeforeDelete
                    }
                },
            )
    }
}

private data class AppNavigation(
    val open: (AppRoute) -> Unit,
    val selectTab: (AppRoute) -> Unit,
    val back: () -> Unit,
)

private val appRouteStackSaver =
    listSaver<SnapshotStateList<AppRoute>, String>(
        save = { stack -> stack.map { it.name } },
        restore = { saved ->
            mutableStateListOf<AppRoute>().apply {
                saved.mapNotNull { name -> AppRoute.entries.firstOrNull { it.name == name } }.forEach(::add)
                if (isEmpty()) add(AppRoute.WELCOME)
            }
        },
    )

/** Shared preview-first Tandem UI used by the Android app and iOS host. */
@Composable
fun TandemApp(
    startInPreview: Boolean = false,
    database: TandemDatabase? = null,
    nowEpochMillis: () -> Long = { 0L },
    allowInsecureLoopback: Boolean = false,
) {
    val state = rememberSaveable(saver = AppState.Saver) { AppState() }
    val apiClient = remember(allowInsecureLoopback) { TandemApiClient(allowInsecureLoopback) }
    val mobileSession = remember(apiClient, database) { TandemMobileSession(apiClient, database, nowEpochMillis) }
    val coroutineScope = rememberCoroutineScope()
    var syncRetryCounter by remember { mutableStateOf(0) }
    val backStack =
        rememberSaveable(startInPreview, saver = appRouteStackSaver) {
            mutableStateListOf(if (startInPreview) AppRoute.BUDGET else AppRoute.WELCOME)
        }
    val navigation =
        AppNavigation(
            open = { backStack.add(it) },
            selectTab = {
                backStack.clear()
                backStack.add(it)
            },
            back = {
                if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
            },
        )
    val route = backStack.last()

    LaunchedEffect(route, mobileSession.pairedDevice?.deviceId) {
        if (route == AppRoute.BUDGET_CHOOSER && mobileSession.isAuthenticated && mobileSession.budgets.isEmpty()) {
            mobileSession.loadBudgets()
        }
        if (route == AppRoute.BUDGET_CHOOSER && mobileSession.budgets.none { it.budgetId == state.selectedBudgetId }) {
            mobileSession.budgets.firstOrNull()?.let { budget ->
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
    LaunchedEffect(route, state.selectedBudgetId) {
        if (route in setOf(AppRoute.BUDGET, AppRoute.ACCOUNTS, AppRoute.ACTIVITY) && state.selectedBudgetId.isNotBlank()) {
            mobileSession.loadLocalRecords(state.selectedBudgetId)
        }
    }
    val liveTransactions =
        remember(
            mobileSession.transactionRecords,
            mobileSession.accountRecords,
            mobileSession.payeeRecords,
            mobileSession.categoryRecords,
        ) {
            liveTransactionItems(mobileSession)
        }

    val pairDevice = {
        if (!mobileSession.isPairing) {
            coroutineScope.launch {
                mobileSession.pair(state.serverUrl, state.pairingCode, "Tandem mobile")
                if (mobileSession.pairedDevice != null) {
                    state.pairingCode = ""
                    backStack.add(AppRoute.PAIRING_RESULT)
                }
            }
        }
    }
    val selectBudget: (BudgetCatalogItem) -> Unit = { budget ->
        if (state.selectedBudgetId != budget.budgetId) state.encryptedPassword = ""
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
        val selected = mobileSession.budgets.firstOrNull { it.budgetId == state.selectedBudgetId }
        if (selected == null) {
            // Keep a visible message in the chooser when the server catalog is empty or stale.
            coroutineScope.launch {
                mobileSession.loadBudgets()
            }
        } else if (selected.isEncrypted && state.encryptedPassword.isBlank()) {
            backStack.add(AppRoute.UNLOCK_BUDGET)
        } else {
            backStack.add(AppRoute.FIRST_SYNC)
        }
    }

    TandemTheme(state.theme, state.accent) {
        val palette = tandemPalette
        val isDark =
            state.theme == ThemeMode.DARK ||
                state.theme == ThemeMode.PURE_BLACK ||
                (state.theme == ThemeMode.SYSTEM && isSystemInDarkTheme())
        TandemSystemBars(isDark)
        TandemBackHandler(enabled = backStack.size > 1, onBack = navigation.back)
        Column(
            modifier = Modifier.fillMaxSize().background(palette.background).safeDrawingPadding(),
        ) {
            if (route != AppRoute.WELCOME) {
                AppHeader(
                    route = route,
                    state = state,
                    showBack = !route.isTab(),
                    onBack = navigation.back,
                    onSync = { navigation.open(AppRoute.SYNC_CENTER) },
                    onBudget = { navigation.open(AppRoute.BUDGET_CHOOSER) },
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                ScreenContent(
                    route = route,
                    state = state,
                    navigation = navigation,
                    mobileSession = mobileSession,
                    onPairDevice = pairDevice,
                    onSelectBudget = selectBudget,
                    onRefreshBudgets = refreshBudgets,
                    onOpenSelectedBudget = openSelectedBudget,
                    onRetrySync = { syncRetryCounter += 1 },
                    liveTransactions = liveTransactions,
                )
            }
            if (route.isTab()) {
                BottomNavigation(route, navigation.selectTab)
            }
        }
    }
}

private fun AppRoute.isTab(): Boolean =
    this == AppRoute.BUDGET || this == AppRoute.ACCOUNTS || this == AppRoute.ACTIVITY || this == AppRoute.SETTINGS

@Composable
private fun AppHeader(
    route: AppRoute,
    state: AppState,
    showBack: Boolean,
    onBack: () -> Unit,
    onSync: () -> Unit,
    onBudget: () -> Unit,
) {
    val palette = tandemPalette
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
                text = if (route == AppRoute.BUDGET) state.selectedBudget else route.title,
                color = palette.text,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (route == AppRoute.BUDGET) {
                Text("${state.selectedMonth} · Sample budget", color = palette.muted, fontSize = 11.sp)
            }
        }
        if (route.isTab()) {
            Text(
                text = "Sync",
                modifier =
                    Modifier
                        .clip(
                            RoundedCornerShape(999.dp),
                        ).clickable(role = Role.Button, onClickLabel = "Open sync center", onClick = onSync)
                        .background(palette.surface)
                        .padding(horizontal = 11.dp, vertical = 9.dp),
                color = palette.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        } else if (route in listOf(AppRoute.BUDGET_CHOOSER, AppRoute.SERVER_ADDRESS)) {
            Text("Sample data", color = palette.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
                NavigationGlyph(route, active)
                Text(
                    label,
                    color = if (active) palette.accent else palette.muted,
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
    active: Boolean,
) {
    val color = if (active) tandemPalette.accent else tandemPalette.muted
    val surface = tandemPalette.surface
    Canvas(Modifier.size(20.dp).clearAndSetSemantics {}) {
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
    navigation: AppNavigation,
    mobileSession: TandemMobileSession,
    onPairDevice: () -> Unit,
    onSelectBudget: (BudgetCatalogItem) -> Unit,
    onRefreshBudgets: () -> Unit,
    onOpenSelectedBudget: () -> Unit,
    onRetrySync: () -> Unit,
    liveTransactions: List<TransactionItem>,
) {
    when (route) {
        AppRoute.WELCOME -> WelcomeScreen(navigation)
        AppRoute.SERVER_ADDRESS -> ServerAddressScreen(state, navigation)
        AppRoute.PAIRING_OPTIONS -> PairingOptionsScreen(navigation)
        AppRoute.SCAN_QR -> ScanQrScreen(navigation)
        AppRoute.PAIRING_CODE -> PairingCodeScreen(state, mobileSession, navigation, onPairDevice)
        AppRoute.PAIRING_RESULT -> PairingResultScreen(mobileSession, navigation)
        AppRoute.BUDGET_CHOOSER ->
            BudgetChooserScreen(state, mobileSession, navigation, onSelectBudget, onRefreshBudgets, onOpenSelectedBudget)
        AppRoute.UNLOCK_BUDGET -> UnlockBudgetScreen(state, navigation)
        AppRoute.FIRST_SYNC -> FirstSyncScreen(mobileSession, navigation, onRetrySync)
        AppRoute.BUDGET -> BudgetScreen(state, navigation, liveTransactions)
        AppRoute.MONTH_PICKER -> MonthPickerScreen(state, navigation)
        AppRoute.CATEGORY_GROUP -> CategoryGroupScreen(navigation)
        AppRoute.CATEGORY_DETAIL -> CategoryDetailScreen(navigation)
        AppRoute.ACCOUNTS -> AccountsScreen(navigation)
        AppRoute.ACCOUNT_DETAIL -> AccountDetailScreen(navigation)
        AppRoute.ACCOUNT_OPTIONS -> AccountOptionsScreen(navigation)
        AppRoute.RECONCILIATION -> ReconciliationScreen()
        AppRoute.ACTIVITY -> ActivityScreen(state, navigation, liveTransactions)
        AppRoute.ACTIVITY_SEARCH -> ActivitySearchScreen(navigation)
        AppRoute.ACTIVITY_FILTER -> ActivityFilterScreen()
        AppRoute.TRANSACTION_DETAIL -> TransactionDetailScreen(state, navigation)
        AppRoute.NEW_TRANSACTION, AppRoute.EDIT_TRANSACTION -> TransactionFormScreen(route, state, navigation)
        AppRoute.CHOOSE_ACCOUNT ->
            SelectorScreen(listOf("Checking", "Savings", "Credit Card", "Cash"), state.transactionAccount) {
                state.transactionAccount =
                    it
                ; navigation.back()
            }
        AppRoute.CHOOSE_PAYEE ->
            SelectorScreen(
                listOf("Corner Market", "Electric Company", "Acme Corp", "Riverside Cafe", "City Transit"),
                state.transactionPayee,
            ) {
                state.transactionPayee =
                    it
                ; navigation.back()
            }
        AppRoute.CHOOSE_CATEGORY ->
            SelectorScreen(
                listOf("Groceries", "Utilities", "Dining", "Rent", "Transport", "Personal"),
                state.transactionCategory,
            ) {
                state.transactionCategory =
                    it
                ; navigation.back()
            }
        AppRoute.CHOOSE_DATE -> DatePickerScreen(state, navigation)
        AppRoute.SAVE_RESULT -> SaveResultScreen(navigation)
        AppRoute.SYNC_CENTER -> SyncCenterScreen(navigation)
        AppRoute.CONFLICT_INBOX -> ConflictInboxScreen(navigation)
        AppRoute.CONFLICT_REVIEW -> ConflictReviewScreen(state)
        AppRoute.UNCERTAIN_WRITE -> UncertainWriteScreen()
        AppRoute.REJECTED_WRITE -> RejectedWriteScreen()
        AppRoute.SETTINGS -> SettingsScreen(state, navigation)
        AppRoute.APPEARANCE -> AppearanceScreen(state, navigation)
        AppRoute.THEME_MODE -> ThemeModeScreen(state)
        AppRoute.ACCENT_COLOR -> AccentColorScreen(state)
        AppRoute.BACKGROUND_MOTION -> BackgroundScreen(state)
        AppRoute.HOME_LAYOUT -> HomeLayoutScreen(state)
        AppRoute.BUDGET_PREFERENCES -> BudgetPreferencesScreen()
        AppRoute.SYNC_PREFERENCES -> SyncPreferencesScreen(state)
        AppRoute.SERVER_DEVICE -> ServerDeviceScreen(state, mobileSession, navigation)
        AppRoute.SECURITY_PRIVACY -> SecurityScreen(state, mobileSession)
        AppRoute.NOTIFICATIONS -> NotificationsScreen(state)
        AppRoute.LOCAL_DATA -> LocalDataScreen()
        AppRoute.ABOUT -> AboutScreen()
    }
}

@Composable
private fun PageColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
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
private fun LabelText(text: String) {
    Text(text, color = tandemPalette.muted, fontSize = 11.sp, letterSpacing = 0.2.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = tandemPalette
    Surface(
        modifier =
            modifier.then(
                if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier,
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
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(if (enabled) palette.accent else palette.raised)
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (enabled) Color(0xFF081A1B) else palette.muted, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SecondaryButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp)
                .clip(RoundedCornerShape(15.dp))
                .border(1.dp, palette.border, RoundedCornerShape(15.dp))
                .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = palette.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
                    if (isSecret) {
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = secretKeyboardType,
                        )
                    } else {
                        KeyboardOptions.Default
                    },
                visualTransformation = if (isSecret) PasswordVisualTransformation() else VisualTransformation.None,
                textStyle = TextStyle(color = palette.text, fontSize = 15.sp, lineHeight = 20.sp),
                cursorBrush = SolidColor(palette.accent),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(placeholder, color = palette.muted, fontSize = 14.sp)
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
    onClick: (() -> Unit)? = null,
) {
    val palette = tandemPalette
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 62.dp)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(role = Role.Button, onClickLabel = title, onClick = onClick)
                    } else {
                        Modifier
                    },
                ).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(palette.raised),
            contentAlignment = Alignment.Center,
        ) {
            Text(icon, modifier = Modifier.clearAndSetSemantics {}, color = palette.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                title,
                color = palette.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) Text(subtitle, color = palette.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) {
            Text(trailing, color = trailingColor ?: palette.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        } else if (onClick != null) {
            Text("›", modifier = Modifier.clearAndSetSemantics {}, color = palette.muted, fontSize = 20.sp)
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    detail: String? = null,
    action: String? = null,
    onClick: (() -> Unit)? = null,
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
                        if (detail != null) Text(detail, color = palette.muted, fontSize = 11.sp)
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
                    .background(if (checked) palette.accent else palette.raised)
                    .padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.size(18.dp).clip(CircleShape).background(if (checked) Color(0xFF10201F) else palette.muted))
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
                    .border(1.5.dp, if (selected) palette.accent else palette.muted, CircleShape)
                    .padding(5.dp),
            ) {
                if (selected) Box(Modifier.fillMaxSize().clip(CircleShape).background(palette.accent))
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
        Text(text, color = palette.muted, fontSize = 12.sp, lineHeight = 17.sp)
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
    Box(Modifier.clip(RoundedCornerShape(999.dp)).background(color.copy(alpha = 0.13f)).padding(horizontal = 9.dp, vertical = 5.dp)) {
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun Color.withOpacity(alpha: Float) = copy(alpha = alpha)

@Composable
private fun WelcomeScreen(navigation: AppNavigation) {
    val palette = tandemPalette
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        Box(Modifier.size(58.dp).clip(RoundedCornerShape(19.dp)).background(palette.accentSurface), contentAlignment = Alignment.Center) {
            Text("A", color = palette.accent, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        }
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
) {
    PageColumn {
        Heading("Enter the code", "Use the 8-character pairing code shown in server setup.")
        TextFieldCard(
            "Pairing code",
            state.pairingCode,
            { state.pairingCode = formatPairingCodeInput(it) },
            "For example, 4DF7-J1M9",
            isSecret = true,
            secretKeyboardType = KeyboardType.Password,
        )
        BodyText("The code works once and expires after five minutes. It is sent securely to the server only when you pair.")
        mobileSession.pairingError?.let { error ->
            Text(error, color = tandemPalette.warning, fontSize = 13.sp, lineHeight = 19.sp)
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
                    BodyText("The device credential stays in memory for this session and is not included in saved app state.")
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
    PageColumn {
        Heading("Select a budget", "Choose one of the budgets authorized for this device.")
        when {
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
            else -> {
                mobileSession.budgets.forEach { budget ->
                    val detail = if (budget.isEncrypted) "Encrypted Actual budget" else "Available on this server"
                    ChoiceRow(budget.name, detail, selected = state.selectedBudgetId == budget.budgetId) {
                        onSelectBudget(budget)
                    }
                }
            }
        }
        mobileSession.budgetError?.takeIf { mobileSession.budgets.isNotEmpty() }?.let { error ->
            Text(error, color = tandemPalette.warning, fontSize = 12.sp)
        }
        PrimaryButton(
            if (state.selectedBudgetEncrypted && state.encryptedPassword.isBlank()) "Unlock and open" else "Open budget",
            enabled = mobileSession.budgets.isNotEmpty() && !mobileSession.isLoadingBudgets,
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
        when {
            mobileSession.isSyncing -> BodyText("Sync is running. Keep this screen open while the first local copy is prepared.")
            mobileSession.syncComplete ->
                BodyText(
                    "Initial sync complete. ${mobileSession.syncedTransactionCount} transactions are available on this device.",
                )
            mobileSession.syncError != null -> {
                Text(mobileSession.syncError.orEmpty(), color = palette.warning, fontSize = 13.sp, lineHeight = 19.sp)
                SecondaryButton("Retry initial sync", onClick = onRetrySync)
            }
        }
        PrimaryButton("Done", enabled = mobileSession.syncComplete) { navigation.selectTab(AppRoute.BUDGET) }
    }
}

@Composable
private fun BudgetScreen(
    state: AppState,
    navigation: AppNavigation,
    liveTransactions: List<TransactionItem>,
) {
    val palette = tandemPalette
    PageColumn {
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
                Text(formatCurrency(124_000), color = palette.text, fontSize = 34.sp, lineHeight = 39.sp, fontWeight = FontWeight.Bold)
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
        SectionCard("Essentials", "${formatCurrency(232_000)} assigned · 3 categories", formatCurrency(232_000), onClick = {
            navigation.open(AppRoute.CATEGORY_GROUP)
        }) {
            ListRow("H", "Rent", "Due ${formatShortDate(SampleDate(2026, 5, 1))}", formatCurrency(150_000), palette.positive) {
                navigation.open(AppRoute.CATEGORY_DETAIL)
            }
            DividerLine()
            ListRow("G", "Groceries", "Spent ${formatCurrency(18_400)} this month", formatCurrency(50_000), palette.positive) {
                navigation.open(AppRoute.CATEGORY_DETAIL)
            }
            DividerLine()
            ListRow("U", "Utilities", "Spent ${formatCurrency(11_200)} this month", formatCurrency(32_000), palette.positive) {
                navigation.open(AppRoute.CATEGORY_DETAIL)
            }
        }
        SectionCard(
            "Everyday",
            "Flexible spending",
            "${formatCurrency(18_000)} left",
            onClick = { navigation.open(AppRoute.CATEGORY_GROUP) },
        ) {
            ListRow("D", "Dining", "Spent ${formatCurrency(17_000)} this month", formatCurrency(18_000), palette.positive) {
                navigation.open(AppRoute.CATEGORY_DETAIL)
            }
        }
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
                formatCurrency(12_000),
                palette.text,
            )
            DividerLine()
            ListRow("H", "Rent", "Essentials · due ${formatShortDate(SampleDate(2026, 6, 1))}", formatCurrency(150_000), palette.text)
        }
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
            formatSignedCurrency(transaction.amountMinorUnits),
            if (transaction.amountMinorUnits > 0) palette.positive else palette.text,
            onClick,
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
    state.transactionAmount = formatAmount(kotlin.math.abs(transaction.amountMinorUnits))
    state.selectedTransactionAmountMinor = transaction.amountMinorUnits
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
        var selectedYear by remember { mutableStateOf(2026) }
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
        PreviewNote("Months are sample planning periods.")
    }
}

@Composable
private fun CategoryGroupScreen(navigation: AppNavigation) {
    val palette = tandemPalette
    PageColumn {
        SampleBanner()
        Heading("Group overview", "Review the bills and needs planned first.")
        AmountHero("Assigned this month", formatCurrency(232_000), "${formatCurrency(232_000)} available across 3 categories")
        SectionCard("Categories", "3 plans", "Edit group") {
            ListRow(
                "H",
                "Rent",
                "Assigned ${formatCurrency(150_000)} · Available ${formatCurrency(150_000)}",
                formatCurrency(150_000),
                palette.positive,
            ) { navigation.open(AppRoute.CATEGORY_DETAIL) }
            DividerLine()
            ListRow(
                "G",
                "Groceries",
                "Assigned ${formatCurrency(50_000)} · Available ${formatCurrency(50_000)}",
                formatCurrency(50_000),
                palette.positive,
            ) { navigation.open(AppRoute.CATEGORY_DETAIL) }
            DividerLine()
            ListRow(
                "U",
                "Utilities",
                "Assigned ${formatCurrency(32_000)} · Available ${formatCurrency(32_000)}",
                formatCurrency(32_000),
                palette.positive,
            ) { navigation.open(AppRoute.CATEGORY_DETAIL) }
        }
        PreviewNote("Category balances and activity use sample data.")
    }
}

@Composable
private fun CategoryDetailScreen(navigation: AppNavigation) {
    PageColumn {
        SampleBanner()
        Heading("Category details", "Essentials · monthly plan")
        AmountHero("Available", formatCurrency(150_000), "Fully funded for ${formatMonth(2026, 5)}")
        SectionCard("This month", "Assigned ${formatCurrency(150_000)} · Spent ${formatCurrency(0)}") {
            ListRow("↗", "Planned amount", "Monthly target", formatCurrency(150_000))
            DividerLine()
            ListRow("◷", "Next due date", "Scheduled payment", formatShortDate(SampleDate(2026, 6, 1)))
        }
        PrimaryButton("View related activity") { navigation.selectTab(AppRoute.ACTIVITY) }
        PreviewNote("This category uses sample data and is read-only. The app keeps a local budget copy for offline use.")
    }
}

@Composable
private fun AccountsScreen(navigation: AppNavigation) {
    PageColumn {
        SampleBanner()
        Heading("Balances", "Review account balances and recent activity.")
        AmountHero("Total on budget", formatCurrency(348_216), "Across 4 sample accounts")
        SectionCard("Cash accounts", "2 accounts", "") {
            ListRow(
                "C",
                "Checking",
                "Everyday spending",
                formatCurrency(348_216),
                tandemPalette.text,
            ) { navigation.open(AppRoute.ACCOUNT_DETAIL) }
            DividerLine()
            ListRow(
                "S",
                "Savings",
                "Emergency fund",
                formatCurrency(820_000),
                tandemPalette.text,
            ) { navigation.open(AppRoute.ACCOUNT_DETAIL) }
        }
        SectionCard("Credit", "1 account", "") {
            ListRow(
                "V",
                "Travel card",
                "Statement closes ${formatShortDate(SampleDate(2026, 5, 22))}",
                formatCurrency(-64_280),
                tandemPalette.text,
            ) { navigation.open(AppRoute.ACCOUNT_DETAIL) }
        }
        SectionCard("Tracking", "1 account", "") {
            ListRow(
                "H",
                "Car loan",
                "Off budget",
                formatCurrency(-830_000),
                tandemPalette.muted,
            ) { navigation.open(AppRoute.ACCOUNT_DETAIL) }
        }
        SecondaryButton("Account options") { navigation.open(AppRoute.ACCOUNT_OPTIONS) }
    }
}

@Composable
private fun AccountDetailScreen(navigation: AppNavigation) {
    PageColumn {
        SampleBanner()
        AmountHero("Available balance", formatCurrency(348_216), "Checking · Updated ${formatShortDate(SampleDate(2026, 5, 14))}")
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            PrimaryButton("Add transaction", modifier = Modifier.weight(1f)) { navigation.open(AppRoute.NEW_TRANSACTION) }
            SecondaryButton("Reconcile", modifier = Modifier.weight(1f)) { navigation.open(AppRoute.RECONCILIATION) }
        }
        SectionCard("Recent activity", "Checking", "See all", onClick = { navigation.selectTab(AppRoute.ACTIVITY) }) {
            previewTransactions.take(4).forEachIndexed { index, item ->
                if (index > 0) DividerLine()
                TransactionRow(item) { navigation.open(AppRoute.TRANSACTION_DETAIL) }
            }
        }
        SettingRow("•••", "Account options", "Rename, hide, or review details", onClick = { navigation.open(AppRoute.ACCOUNT_OPTIONS) })
    }
}

@Composable
private fun AccountOptionsScreen(navigation: AppNavigation) {
    PageColumn {
        Heading("Checking", "Account options")
        SettingRow("✎", "Edit account details", "Name and notes", onClick = { })
        SettingRow(
            "◷",
            "Reconcile balance",
            "Compare the account against a statement",
            onClick = { navigation.open(AppRoute.RECONCILIATION) },
        )
        SettingRow("⊖", "Hide from budget", "Keep historical activity available", onClick = { })
        PreviewNote(
            "Account changes are not available here. This screen cannot compare a balance with a statement.",
        )
    }
}

@Composable
private fun ReconciliationScreen() {
    PageColumn {
        Heading("Compare balances", "Compare cleared transactions with a statement.")
        TextFieldCard("Statement balance", formatAmount(348_216), {}, formatAmount(0))
        SectionCard("Difference", "Sample calculation") {
            ListRow("=", "Account balance", "Current sample balance", formatCurrency(348_216))
            DividerLine()
            ListRow("Δ", "Out of balance", "No cleared transactions compared", formatCurrency(0), tandemPalette.positive)
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
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Income", "Spending", "Transfers").forEachIndexed { index, filter ->
                Badge(filter, if (index == 0) tandemPalette.accent else tandemPalette.muted)
            }
            Badge("Filter · 2", tandemPalette.accent)
        }
        if (isLiveBudget) {
            SectionCard(state.selectedMonth, "${liveTransactions.size} recent transactions", "Filter", onClick = {
                navigation.open(AppRoute.ACTIVITY_FILTER)
            }) {
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
            BodyText("Live transaction creation and editing are not available yet.")
            PrimaryButton("Add transaction", enabled = false) {}
        } else {
            PreviewNote("Transactions use sample data. Search and filters do not change a live budget.")
            PrimaryButton("Add transaction") { navigation.open(AppRoute.NEW_TRANSACTION) }
        }
    }
}

@Composable
private fun ActivitySearchScreen(navigation: AppNavigation) {
    var search by remember { mutableStateOf("") }
    PageColumn {
        Heading("Find transactions", "Search by payee, note, amount, or category.")
        TextFieldCard("Search", search, { search = it }, "Try “groceries” or “${formatCurrency(4_200)}”")
        SectionCard("Suggested", "Recent sample searches", "") {
            listOf("Groceries", "Electric Company", "Paycheck").forEachIndexed { index, value ->
                if (index > 0) DividerLine()
                ListRow("⌕", value, "Sample results", onClick = { navigation.open(AppRoute.TRANSACTION_DETAIL) })
            }
        }
        SectionCard("Results", "3 matching transactions", "") {
            previewTransactions
                .filter {
                    search.isBlank() ||
                        it.payee.contains(search, ignoreCase = true) ||
                        it.category.contains(search, ignoreCase = true)
                }.forEachIndexed { index, item ->
                    if (index > 0) DividerLine()
                    TransactionRow(item) { navigation.open(AppRoute.TRANSACTION_DETAIL) }
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
    navigation: AppNavigation,
) {
    val isLive = state.selectedTransactionIsLive
    val amount = state.selectedTransactionAmountMinor ?: -4_218L
    PageColumn {
        if (!isLive) SampleBanner() else Badge("Actual · read only", tandemPalette.positive)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(formatCurrency(amount), color = tandemPalette.text, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text(state.transactionPayee, color = tandemPalette.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("${state.transactionDate} · ${state.transactionAccount}", color = tandemPalette.muted, fontSize = 12.sp)
        }
        SectionCard("Transaction", if (isLive) "Stored in this device’s local budget" else "Sample details") {
            ListRow("C", "Account", "Where money moved", state.transactionAccount)
            DividerLine()
            ListRow("G", "Category", "Budget category", state.transactionCategory)
            DividerLine()
            ListRow("◷", "Date", "Posted", state.transactionDate)
            DividerLine()
            ListRow("▤", "Notes", "Optional", if (isLive) "Not available" else "Weekly shop")
        }
        if (isLive) {
            BodyText("Live transaction editing is not available yet. No changes have been sent to Actual.")
        } else {
            PreviewNote("This sample transaction is read-only. Editing it will not change Actual Budget.")
            PrimaryButton("Edit transaction") { navigation.open(AppRoute.EDIT_TRANSACTION) }
        }
        SecondaryButton("Review sync status") { navigation.open(AppRoute.SYNC_CENTER) }
    }
}

@Composable
private fun TransactionFormScreen(
    route: AppRoute,
    state: AppState,
    navigation: AppNavigation,
) {
    val isNew = route == AppRoute.NEW_TRANSACTION
    PageColumn {
        SampleBanner()
        Heading("Transaction details", "Record a change to the budget.")
        TextFieldCard("Amount", state.transactionAmount, { state.transactionAmount = it }, "For example, ${formatAmount(1_250)}")
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
    values: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    PageColumn {
        BodyText("Choose an item for the sample transaction.")
        TextFieldCard("Search", query, { query = it }, "Start typing")
        values.filter { it.contains(query, ignoreCase = true) }.forEach { value ->
            ChoiceRow(value, selected = selected == value, onClick = { onSelect(value) })
        }
        PreviewNote("Selections update only this sample form.")
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
                        Box(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(CircleShape)
                                    .background(if (selected) tandemPalette.accentSurface else Color.Transparent)
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
                                color = if (selected) tandemPalette.accent else tandemPalette.text,
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
private fun SaveResultScreen(navigation: AppNavigation) {
    val palette = tandemPalette
    PageColumn {
        Spacer(Modifier.height(16.dp))
        Badge("Not saved", palette.warning)
        Heading("No change was made", "This sample form cannot save a transaction yet.")
        SectionCard("Transaction draft", "Sample data") {
            ListRow("¤", "Amount", "Corner Market", formatCurrency(-4_218))
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
private fun SyncCenterScreen(navigation: AppNavigation) {
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
private fun ConflictInboxScreen(navigation: AppNavigation) {
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
                    ConflictValueCard("On this device", formatCurrency(1_200), Modifier.weight(1f))
                    ConflictValueCard("In Actual", formatCurrency(1_500), Modifier.weight(1f))
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
private fun ConflictReviewScreen(state: AppState) {
    PageColumn {
        Badge("Conflict · sample data", tandemPalette.warning)
        Heading("Two versions differ", "Review the two sample amounts for Corner Market.")
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            ConflictValueCard("On this device", formatCurrency(1_200), Modifier.weight(1f))
            ConflictValueCard("In Actual", formatCurrency(1_500), Modifier.weight(1f))
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
        PrimaryButton("Keep ${formatCurrency(1_200)} on this device") { state.conflictChoice = "device value ${formatCurrency(1_200)}" }
        SecondaryButton("Keep ${formatCurrency(1_500)} from Actual") { state.conflictChoice = "Actual value ${formatCurrency(1_500)}" }
        PreviewNote(
            "The app would compare that choice with the latest server copy before saving. Nothing changes until the server confirms it.",
        )
    }
}

@Composable
private fun UncertainWriteScreen() {
    PageColumn {
        Badge("Status unknown", tandemPalette.warning)
        Heading("Check before retrying", "The connection ended before this change could be confirmed.")
        SectionCard("Corner Market", "${formatShortDate(SampleDate(2026, 5, 14))} · Groceries") {
            ListRow("¤", "Requested amount", "Local change", formatCurrency(1_200))
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
private fun RejectedWriteScreen() {
    PageColumn {
        Badge("Not applied", tandemPalette.negative)
        Heading("Previous value kept", "This change was not applied in Actual Budget.")
        SectionCard("Sample edit", "Corner Market · ${formatShortDate(SampleDate(2026, 5, 14))}") {
            ListRow("¤", "Requested amount", "Device draft", formatCurrency(1_200))
            DividerLine()
            ListRow("!", "Reason", "New transactions are not available yet", "Unavailable", tandemPalette.warning)
        }
        PreviewNote(
            "The change was not saved. The app keeps the reason and will offer a way to dismiss it.",
        )
        PrimaryButton("Close details") {}
    }
}

@Composable
private fun SettingsScreen(
    state: AppState,
    navigation: AppNavigation,
) {
    PageColumn {
        SampleBanner()
        Heading("Preferences", "Adjust appearance, sync, privacy, and app behavior.")
        SectionCard("Personalize", "Appearance and budget") {
            ListRow("◐", "Appearance", "Theme, accent, and background", state.theme.label) { navigation.open(AppRoute.APPEARANCE) }
            DividerLine()
            ListRow("⌂", "Home screen layout", "Choose how much detail to show", "Comfortable") { navigation.open(AppRoute.HOME_LAYOUT) }
            DividerLine()
            ListRow("¤", "Budget preferences", "Currency and planning choices") { navigation.open(AppRoute.BUDGET_PREFERENCES) }
        }
        SectionCard("Connection", "Sync and paired device") {
            ListRow("↻", "Sync preferences", "Refresh and network behavior", onClick = { navigation.open(AppRoute.SYNC_PREFERENCES) })
            DividerLine()
            ListRow("◎", "Server and paired device", "Connection details and access", onClick = { navigation.open(AppRoute.SERVER_DEVICE) })
            DividerLine()
            ListRow("♧", "Notifications", "Sync reminders and review alerts", onClick = { navigation.open(AppRoute.NOTIFICATIONS) })
        }
        SectionCard("Privacy", "Device data and security") {
            ListRow(
                "⌑",
                "Security and privacy",
                "Credentials and local protection",
                onClick = { navigation.open(AppRoute.SECURITY_PRIVACY) },
            )
            DividerLine()
            ListRow("▣", "Local data and storage", "Cached budget and pending edits", onClick = { navigation.open(AppRoute.LOCAL_DATA) })
        }
        SectionCard("Support", "Help and project details") {
            ListRow("?", "About, help, and licenses", "Version and open-source licenses", onClick = { navigation.open(AppRoute.ABOUT) })
        }
    }
}

@Composable
private fun AppearanceScreen(
    state: AppState,
    navigation: AppNavigation,
) {
    val palette = tandemPalette
    PageColumn {
        Heading("Visual settings", "The same appearance choices work on Android and iOS.")
        SurfaceCard {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LabelText("Live preview")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .weight(
                                1f,
                            ).height(
                                55.dp,
                            ).clip(RoundedCornerShape(13.dp))
                            .background(palette.background)
                            .border(1.dp, palette.border, RoundedCornerShape(13.dp)),
                    )
                    Box(
                        Modifier
                            .weight(
                                1f,
                            ).height(
                                55.dp,
                            ).clip(RoundedCornerShape(13.dp))
                            .background(palette.surface)
                            .border(1.dp, palette.border, RoundedCornerShape(13.dp)),
                    )
                    Box(
                        Modifier
                            .weight(
                                1f,
                            ).height(
                                55.dp,
                            ).clip(RoundedCornerShape(13.dp))
                            .background(palette.accentSurface)
                            .border(1.dp, palette.accent, RoundedCornerShape(13.dp)),
                    )
                }
                Text(formatCurrency(124_000), color = palette.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Available to assign · ${state.selectedMonth}", color = palette.muted, fontSize = 11.sp)
            }
        }
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
        PreviewNote(
            "Appearance choices share the same visual system on Android and iOS. Only system bars, safe areas, and keyboards follow the device.",
        )
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
        Heading("Accent choices", "Choose a highlight color for actions and selected items.")
        AccentChoice.entries.forEach { accent ->
            val palette = tandemPalette
            SurfaceCard(onClick = { state.accent = accent }) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(30.dp).clip(CircleShape).background(accent.color))
                    Column(Modifier.weight(1f)) {
                        Text(accent.label, color = palette.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Preview across buttons, badges, and selection states", color = palette.muted, fontSize = 11.sp)
                    }
                    if (state.accent == accent) Text("✓", color = accent.color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        PreviewNote("The selected accent is shared between the Android and iOS app surfaces.")
    }
}

@Composable
private fun BackgroundScreen(state: AppState) {
    PageColumn {
        Heading("Motion settings", "Choose a background style and reduce animation.")
        SectionCard("Background style", "Sample controls") {
            listOf("Soft gradient", "Solid surface", "Low-motion glow").forEach { style ->
                ChoiceRow(style, selected = state.backgroundStyle == style, onClick = { state.backgroundStyle = style })
            }
        }
        SectionCard("Motion", "Animation should never get in the way") {
            ToggleRow("Reduce motion", "Use still transitions and remove animated backgrounds", state.reduceMotion) {
                state.reduceMotion =
                    it
            }
        }
        PreviewNote("Motion preferences will also respect the operating system’s reduced-motion accessibility setting.")
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
                ListRow("H", "Essentials", "3 categories", formatCurrency(232_000))
                DividerLine()
                ListRow("G", "Groceries", "Available this month", formatCurrency(50_000))
            }
        }
    }
}

@Composable
private fun BudgetPreferencesScreen() {
    val currencyCode = platformCurrencyCode()
    PageColumn {
        Heading("Amount and planning", "Choose how amounts and plans are presented.")
        SectionCard("Number format", "Sample values use $currencyCode") {
            ListRow("¤", "Currency", "Follows the device locale", currencyCode)
            DividerLine()
            ListRow("◷", "Start of week", "Follows the device locale", formatFirstWeekday())
        }
        SectionCard("Planning", "Budget overview") {
            ToggleRow("Show available category amounts", "Keep remaining-to-spend visible on the overview", true, {})
            DividerLine()
            ToggleRow("Show upcoming bills", "Highlight planned payments near their due date", true, {})
            DividerLine()
            ToggleRow("Carry category balances forward", "Use the amount available from prior months", true, {})
        }
        PreviewNote("Preferences are sample controls only and do not alter an Actual budget.")
    }
}

@Composable
private fun SyncPreferencesScreen(state: AppState) {
    PageColumn {
        Heading("Automatic refresh", "Choose when the app should refresh.")
        SectionCard("Automatic refresh", "Local changes stay available offline") {
            ToggleRow("Sync when opening a budget", "Check for server changes when returning to a budget", state.syncOnOpen) {
                state.syncOnOpen =
                    it
            }
            DividerLine()
            ToggleRow("Sync only on Wi-Fi", "Avoid cellular data for automatic refreshes", state.syncOnWifi) { state.syncOnWifi = it }
        }
        SectionCard("Manual controls", "Always available") {
            ListRow("↻", "Sync now", "Read changes and check pending edits", "Sample")
            DividerLine()
            ListRow("◷", "Last sync", "No real sync has run", "Never")
        }
        PreviewNote("These settings are not saved yet. The app checks pending changes before retrying them.")
    }
}

@Composable
private fun ServerDeviceScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
) {
    val device = mobileSession.pairedDevice
    PageColumn {
        Heading("Connection details", "Review the server address and device pairing.")
        SectionCard("Server", if (device != null) "Paired for this app session" else "No device pairing") {
            ListRow("◎", "Server URL", state.serverUrl, if (device != null) "Paired" else "Add") {
                navigation.open(AppRoute.SERVER_ADDRESS)
            }
            DividerLine()
            ListRow(
                "↻",
                "Connection status",
                if (mobileSession.isAuthenticated) "Device credential is active in memory" else "Pair this device to access budgets",
                if (mobileSession.isAuthenticated) "Paired" else "Offline",
                if (mobileSession.isAuthenticated) tandemPalette.positive else tandemPalette.warning,
            )
        }
        SectionCard("This device", if (device != null) "Device pairing is active" else "Pairing is not active") {
            ListRow("▣", "Device name", if (device != null) "Tandem mobile" else "Not paired")
            DividerLine()
            ListRow(
                "⌑",
                "Authorized budget scope",
                if (device != null) {
                    "${device.budgetIds.size} scope${if (device.budgetIds.size == 1) "" else "s"} from the server"
                } else {
                    "No device credential"
                },
            )
            DividerLine()
            ListRow("⌑", "Device credential", "In-memory for this app session", if (device != null) "Active" else "None")
        }
        BodyText("Pairing codes are never saved. The device credential is held in memory only; pair again after the app process restarts.")
        if (device == null) SecondaryButton("Pair this device") { navigation.open(AppRoute.SERVER_ADDRESS) }
    }
}

@Composable
private fun SecurityScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
) {
    PageColumn {
        Heading("Device protection", "Protect financial data and manage device access.")
        SectionCard("Device unlock", "Device pairing is currently held in memory") {
            ToggleRow("Use biometric unlock", "Require this device’s biometrics to reopen a budget", state.faceUnlock) {
                state.faceUnlock =
                    it
            }
            DividerLine()
            ToggleRow(
                "Confirm before deleting a transaction",
                "Show the transaction name and amount before removal",
                state.confirmBeforeDelete,
            ) {
                state.confirmBeforeDelete =
                    it
            }
        }
        SectionCard("Data handling", "Server connection") {
            ListRow(
                "⌑",
                "Device credential",
                "Kept in memory until the app process exits",
                if (mobileSession.isAuthenticated) "Active" else "Not paired",
            )
            DividerLine()
            ListRow("◉", "Budget password", "Used only to open an encrypted budget", "Not stored")
        }
        PreviewNote("Biometric unlock is not wired yet. Device credentials are not persisted between app launches.")
    }
}

@Composable
private fun NotificationsScreen(state: AppState) {
    PageColumn {
        Heading("Alert preferences", "Choose which changes should trigger alerts.")
        SectionCard("Budget activity", "Change these settings at any time") {
            ToggleRow("Sync reminders", "Let you know when this device has been offline", state.notifications) { state.notifications = it }
            DividerLine()
            ToggleRow("Conflict review alerts", "Show a reminder when an edit needs review", state.notifications) {
                state.notifications =
                    it
            }
            DividerLine()
            ToggleRow(
                "Rejected edit alerts",
                "Explain when a server change could not be applied",
                state.notifications,
            ) { state.notifications = it }
        }
        PreviewNote("These sample controls do not request notification permission.")
    }
}

@Composable
private fun LocalDataScreen() {
    PageColumn {
        Heading("Stored data", "See what is cached on this device.")
        AmountHero("Sample storage", "${formatInteger(18)} MB", "Estimated from sample budget data")
        SectionCard("On this device", "No real budget is cached") {
            ListRow("▤", "Budget records", "Sample data only", "0")
            DividerLine()
            ListRow("↑", "Queued changes", "No changes are queued", "0")
            DividerLine()
            ListRow("!", "Conflicts to review", "No live conflict data", "0")
        }
        SectionCard("Storage actions", "") {
            ListRow("↻", "Refresh local budget", "Pull a fresh snapshot", "Unavailable")
            DividerLine()
            ListRow("×", "Clear local data", "Removes cached data from this device", "Unavailable")
        }
        PreviewNote("Never clear pending or uncertain edits without explaining the effect and asking the user to confirm.")
    }
}

@Composable
private fun AboutScreen() {
    PageColumn {
        Heading("About Tandem for Actual Budget", "An unofficial, offline-first mobile client for Actual Budget.")
        SurfaceCard {
            Column(
                Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(tandemPalette.accentSurface),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("A", color = tandemPalette.accent, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                }
                Text("Version 0.1.0 · Sample interface", color = tandemPalette.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Not affiliated with, endorsed by, or supported by Actual Budget.",
                    color = tandemPalette.muted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
        SectionCard("Help and project", "") {
            ListRow("?", "Connection setup", "Pair this device with the server")
            DividerLine()
            ListRow("▣", "Open-source licenses", "AGPL-3.0-or-later")
            DividerLine()
            ListRow("↗", "Server version", "The app checks that this version is supported", "26.10.0")
        }
        PreviewNote("License notices, supported-version details, and help links will appear here.")
    }
}
