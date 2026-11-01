@file:Suppress("ktlint:standard:function-naming")

package org.actualbudget.companion.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

private enum class AppRoute(
    val title: String,
) {
    WELCOME("Actual Companion"),
    SERVER_ADDRESS("Server address"),
    PAIRING_OPTIONS("Connect a device"),
    SCAN_QR("Scan pairing QR"),
    PAIRING_CODE("Enter pairing code"),
    PAIRING_RESULT("Device pairing"),
    BUDGET_CHOOSER("Your budgets"),
    UNLOCK_BUDGET("Unlock budget"),
    FIRST_SYNC("Preparing your budget"),
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
    CONFLICT_INBOX("Needs your review"),
    CONFLICT_REVIEW("Review conflict"),
    UNCERTAIN_WRITE("Check write status"),
    REJECTED_WRITE("Edit not applied"),
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
    val date: String,
    val amount: String,
    val icon: String,
)

private val previewTransactions =
    listOf(
        TransactionItem("Corner Market", "Groceries", "May 14", "−$42.18", "G"),
        TransactionItem("Electric Company", "Utilities", "May 12", "−$120.00", "E"),
        TransactionItem("Acme Corp", "Paycheck", "May 10", "+$2,500.00", "A"),
        TransactionItem("Riverside Cafe", "Dining", "May 8", "−$28.40", "R"),
        TransactionItem("City Transit", "Transport", "May 6", "−$16.50", "C"),
        TransactionItem("Book Nook", "Personal", "May 4", "−$23.99", "B"),
    )

private class AppState {
    var theme by mutableStateOf(ThemeMode.DARK)
    var accent by mutableStateOf(AccentChoice.TEAL)
    var selectedBudget by mutableStateOf("Household")
    var selectedMonth by mutableStateOf("May 2026")
    var serverUrl by mutableStateOf("https://budget.example.com")
    var pairingCode by mutableStateOf("")
    var encryptedPassword by mutableStateOf("")
    var transactionAmount by mutableStateOf("42.18")
    var transactionAccount by mutableStateOf("Checking")
    var transactionPayee by mutableStateOf("Corner Market")
    var transactionCategory by mutableStateOf("Groceries")
    var transactionDate by mutableStateOf("May 14, 2026")
    var transactionNotes by mutableStateOf("")
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
                        "selectedMonth" to state.selectedMonth,
                        "serverUrl" to state.serverUrl,
                        "transactionAmount" to state.transactionAmount,
                        "transactionAccount" to state.transactionAccount,
                        "transactionPayee" to state.transactionPayee,
                        "transactionCategory" to state.transactionCategory,
                        "transactionDate" to state.transactionDate,
                        "transactionNotes" to state.transactionNotes,
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
                        selectedMonth = saved["selectedMonth"] as? String ?: selectedMonth
                        serverUrl = saved["serverUrl"] as? String ?: serverUrl
                        transactionAmount = saved["transactionAmount"] as? String ?: transactionAmount
                        transactionAccount = saved["transactionAccount"] as? String ?: transactionAccount
                        transactionPayee = saved["transactionPayee"] as? String ?: transactionPayee
                        transactionCategory = saved["transactionCategory"] as? String ?: transactionCategory
                        transactionDate = saved["transactionDate"] as? String ?: transactionDate
                        transactionNotes = saved["transactionNotes"] as? String ?: transactionNotes
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

/** Shared preview-first Companion UI used by the Android app and iOS host. */
@Composable
fun CompanionApp(startInPreview: Boolean = false) {
    val state = rememberSaveable(saver = AppState.Saver) { AppState() }
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

    CompanionTheme(state.theme, state.accent) {
        val palette = companionPalette
        val isDark =
            state.theme == ThemeMode.DARK ||
                state.theme == ThemeMode.PURE_BLACK ||
                (state.theme == ThemeMode.SYSTEM && isSystemInDarkTheme())
        CompanionSystemBars(isDark)
        CompanionBackHandler(enabled = backStack.size > 1, onBack = navigation.back)
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
                ScreenContent(route, state, navigation)
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
    val palette = companionPalette
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
                text = "SYNC  ·",
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
        } else if (route in listOf(AppRoute.BUDGET_CHOOSER, AppRoute.SERVER_ADDRESS, AppRoute.SYNC_CENTER)) {
            Text("PREVIEW", color = palette.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun Modifier.wrapContentAlignment(): Modifier = this

@Composable
private fun BottomNavigation(
    selected: AppRoute,
    selectTab: (AppRoute) -> Unit,
) {
    val palette = companionPalette
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
    val color = if (active) companionPalette.accent else companionPalette.muted
    val surface = companionPalette.surface
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
) {
    when (route) {
        AppRoute.WELCOME -> WelcomeScreen(navigation)
        AppRoute.SERVER_ADDRESS -> ServerAddressScreen(state, navigation)
        AppRoute.PAIRING_OPTIONS -> PairingOptionsScreen(navigation)
        AppRoute.SCAN_QR -> ScanQrScreen(navigation)
        AppRoute.PAIRING_CODE -> PairingCodeScreen(state, navigation)
        AppRoute.PAIRING_RESULT -> PairingResultScreen(navigation)
        AppRoute.BUDGET_CHOOSER -> BudgetChooserScreen(state, navigation)
        AppRoute.UNLOCK_BUDGET -> UnlockBudgetScreen(state, navigation)
        AppRoute.FIRST_SYNC -> FirstSyncScreen(navigation)
        AppRoute.BUDGET -> BudgetScreen(state, navigation)
        AppRoute.MONTH_PICKER -> MonthPickerScreen(state, navigation)
        AppRoute.CATEGORY_GROUP -> CategoryGroupScreen(navigation)
        AppRoute.CATEGORY_DETAIL -> CategoryDetailScreen(navigation)
        AppRoute.ACCOUNTS -> AccountsScreen(navigation)
        AppRoute.ACCOUNT_DETAIL -> AccountDetailScreen(navigation)
        AppRoute.ACCOUNT_OPTIONS -> AccountOptionsScreen(navigation)
        AppRoute.RECONCILIATION -> ReconciliationScreen()
        AppRoute.ACTIVITY -> ActivityScreen(navigation)
        AppRoute.ACTIVITY_SEARCH -> ActivitySearchScreen(navigation)
        AppRoute.ACTIVITY_FILTER -> ActivityFilterScreen()
        AppRoute.TRANSACTION_DETAIL -> TransactionDetailScreen(navigation)
        AppRoute.NEW_TRANSACTION, AppRoute.EDIT_TRANSACTION -> TransactionFormScreen(route, state, navigation)
        AppRoute.CHOOSE_ACCOUNT ->
            SelectorScreen("Choose account", listOf("Checking", "Savings", "Credit Card", "Cash"), state.transactionAccount) {
                state.transactionAccount =
                    it
                ; navigation.back()
            }
        AppRoute.CHOOSE_PAYEE ->
            SelectorScreen(
                "Choose payee",
                listOf("Corner Market", "Electric Company", "Acme Corp", "Riverside Cafe", "City Transit"),
                state.transactionPayee,
            ) {
                state.transactionPayee =
                    it
                ; navigation.back()
            }
        AppRoute.CHOOSE_CATEGORY ->
            SelectorScreen(
                "Choose category",
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
        AppRoute.SERVER_DEVICE -> ServerDeviceScreen(state, navigation)
        AppRoute.SECURITY_PRIVACY -> SecurityScreen(state)
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
private fun SampleBanner() {
    val palette = companionPalette
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
        Text("SAMPLE PREVIEW", color = palette.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Text("No live budget is connected", color = palette.muted, fontSize = 11.sp)
    }
}

@Composable
private fun Heading(
    title: String,
    subtitle: String? = null,
) {
    val palette = companionPalette
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, color = palette.text, fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, color = palette.muted, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun BodyText(text: String) {
    Text(text, color = companionPalette.muted, fontSize = 14.sp, lineHeight = 20.sp)
}

@Composable
private fun LabelText(text: String) {
    Text(text.uppercase(), color = companionPalette.muted, fontSize = 10.sp, letterSpacing = 0.9.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = companionPalette
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
    onClick: () -> Unit,
) {
    val palette = companionPalette
    Box(
        modifier =
            Modifier
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
    onClick: () -> Unit,
) {
    val palette = companionPalette
    Box(
        modifier =
            Modifier
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
    Text(label, color = companionPalette.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
    val palette = companionPalette
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
    val palette = companionPalette
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
    val palette = companionPalette
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
    val palette = companionPalette
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
    val palette = companionPalette
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
    val palette = companionPalette
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
    val palette = companionPalette
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
    val palette = companionPalette
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
    Box(Modifier.fillMaxWidth().height(1.dp).background(companionPalette.border.copy(alpha = 0.8f)))
}

@Composable
private fun Badge(
    label: String,
    color: Color = companionPalette.accent,
) {
    Box(Modifier.clip(RoundedCornerShape(999.dp)).background(color.copy(alpha = 0.13f)).padding(horizontal = 9.dp, vertical = 5.dp)) {
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun Color.withOpacity(alpha: Float) = copy(alpha = alpha)

@Composable
private fun WelcomeScreen(navigation: AppNavigation) {
    val palette = companionPalette
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        Box(Modifier.size(58.dp).clip(RoundedCornerShape(19.dp)).background(palette.accentSurface), contentAlignment = Alignment.Center) {
            Text("A", color = palette.accent, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        }
        Heading("Your budget,\nclose at hand", "A thoughtful mobile companion for your Actual Budget.")
        SurfaceCard {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Badge("PRIVATE BY DESIGN")
                    Text("Your server stays in control", color = palette.muted, fontSize = 11.sp)
                }
                Text(
                    "A clear view of every plan, account, and change you make.",
                    color = palette.text,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Badge("OFFLINE READY", palette.positive)
                    Badge("CONFLICT REVIEW", palette.warning)
                }
            }
        }
        Spacer(Modifier.weight(1f, fill = false))
        PrimaryButton("Connect to your server") { navigation.open(AppRoute.SERVER_ADDRESS) }
        SecondaryButton("Explore sample budget") { navigation.selectTab(AppRoute.BUDGET) }
        PreviewNote("Sample preview uses fictional amounts. No server request is sent until connection is implemented.")
    }
}

@Composable
private fun ServerAddressScreen(
    state: AppState,
    navigation: AppNavigation,
) {
    PageColumn {
        Heading("Connect your server", "Enter the Companion server address used for secure device pairing.")
        TextFieldCard("Server URL", state.serverUrl, { state.serverUrl = it }, "https://budget.example.com")
        SurfaceCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LabelText("Before you continue")
                BodyText(
                    "Use the HTTPS address provided by your server administrator. The budget password is only used to unlock an encrypted budget; it is never used to pair this device.",
                )
            }
        }
        PreviewNote("Connection setup is a visual preview. This build does not send your server address anywhere.")
        PrimaryButton("Continue") { navigation.open(AppRoute.PAIRING_OPTIONS) }
    }
}

@Composable
private fun PairingOptionsScreen(navigation: AppNavigation) {
    PageColumn {
        Heading("Pair this device", "Choose a one-time pairing code from the Companion server setup.")
        SettingRow("▦", "Scan pairing QR", "Fastest and easiest", onClick = { navigation.open(AppRoute.SCAN_QR) })
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
    val palette = companionPalette
    PageColumn {
        Heading("Scan the QR code", "Point your camera at the one-time pairing code on the Companion server.")
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
                    Text("Camera preview", color = palette.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "QR scanning will start after camera access is connected.",
                        color = palette.muted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        PrimaryButton("Enter code instead") { navigation.open(AppRoute.PAIRING_CODE) }
        PreviewNote("Camera permission is not requested by this visual prototype.")
    }
}

@Composable
private fun PairingCodeScreen(
    state: AppState,
    navigation: AppNavigation,
) {
    PageColumn {
        Heading("Enter pairing code", "Enter the 8-character one-time pairing code from your server setup.")
        TextFieldCard(
            "Pairing code",
            state.pairingCode,
            { state.pairingCode = it },
            "For example, 4DF7-J1M9",
            singleLine = false,
            isSecret = true,
            secretKeyboardType = KeyboardType.Ascii,
        )
        PreviewNote("Pairing codes work once and expire after five minutes. This preview does not submit it.")
        PrimaryButton("Pair this device") { navigation.open(AppRoute.PAIRING_RESULT) }
    }
}

@Composable
private fun PairingResultScreen(navigation: AppNavigation) {
    val palette = companionPalette
    PageColumn {
        Spacer(Modifier.height(24.dp))
        Box(Modifier.size(60.dp).clip(CircleShape).background(palette.accentSurface), contentAlignment = Alignment.Center) {
            Text("✓", color = palette.accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        Heading("Pairing preview", "Your device is ready to choose a budget.")
        PreviewNote("This prototype has not contacted your server or stored a device credential.")
        PrimaryButton("Choose a budget") { navigation.open(AppRoute.BUDGET_CHOOSER) }
    }
}

@Composable
private fun BudgetChooserScreen(
    state: AppState,
    navigation: AppNavigation,
) {
    PageColumn {
        Heading("Your budgets", "Choose a budget to open on this device.")
        listOf("Household" to "Synced just now", "Travel" to "Synced 2 hours ago").forEach { (name, detail) ->
            ChoiceRow(name, detail, selected = state.selectedBudget == name) { state.selectedBudget = name }
        }
        PreviewNote("Budgets shown here are sample data. Selecting one does not download or unlock a real budget.")
        PrimaryButton("Open budget") { navigation.open(AppRoute.FIRST_SYNC) }
        SecondaryButton("Encrypted budget preview") { navigation.open(AppRoute.UNLOCK_BUDGET) }
    }
}

@Composable
private fun UnlockBudgetScreen(
    state: AppState,
    navigation: AppNavigation,
) {
    PageColumn {
        Heading("Unlock your budget", "This budget is encrypted. Enter the budget password to open it.")
        TextFieldCard(
            "Budget password",
            state.encryptedPassword,
            { state.encryptedPassword = it },
            "Enter to unlock this encrypted budget",
            isSecret = true,
        )
        PreviewNote("A real password must be held only for the encrypted-budget request. This preview never sends or saves it.")
        PrimaryButton("Unlock and continue") { navigation.open(AppRoute.FIRST_SYNC) }
    }
}

@Composable
private fun FirstSyncScreen(navigation: AppNavigation) {
    val palette = companionPalette
    PageColumn {
        Heading("Getting your budget ready", "A local copy keeps your budget available when you are offline.")
        SurfaceCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                listOf("Connecting to Companion", "Reading budget snapshot", "Preparing your device database").forEachIndexed {
                    index,
                    title,
                    ->
                    Row(horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Badge(if (index < 2) "✓" else "3", if (index < 2) palette.positive else palette.accent)
                        Text(title, color = palette.text, fontSize = 13.sp)
                    }
                }
            }
        }
        PreviewNote("No synchronization is running. The steps above are illustrative and use no budget data.")
        PrimaryButton("Continue to sample budget") { navigation.selectTab(AppRoute.BUDGET) }
    }
}

@Composable
private fun BudgetScreen(
    state: AppState,
    navigation: AppNavigation,
) {
    val palette = companionPalette
    PageColumn {
        SampleBanner()
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Good morning, Nischay", color = palette.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("A steady plan makes room for what matters.", color = palette.muted, fontSize = 13.sp)
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
                Text("$1,240.00", color = palette.text, fontSize = 34.sp, lineHeight = 39.sp, fontWeight = FontWeight.Bold)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Available this month", color = palette.muted, fontSize = 12.sp)
                    Badge("ON TRACK", palette.positive)
                }
            }
        }
        SectionCard("Essentials", "$2,320 assigned · 3 categories", "$2,320", onClick = { navigation.open(AppRoute.CATEGORY_GROUP) }) {
            ListRow("H", "Rent", "Due May 1", "$1,500", palette.positive) { navigation.open(AppRoute.CATEGORY_DETAIL) }
            DividerLine()
            ListRow("G", "Groceries", "Spent $184 this month", "$500", palette.positive) { navigation.open(AppRoute.CATEGORY_DETAIL) }
            DividerLine()
            ListRow("U", "Utilities", "Spent $112 this month", "$320", palette.positive) { navigation.open(AppRoute.CATEGORY_DETAIL) }
        }
        SectionCard("Everyday", "Flexible spending", "$180 left", onClick = { navigation.open(AppRoute.CATEGORY_GROUP) }) {
            ListRow("D", "Dining", "Spent $170 this month", "$180", palette.positive) { navigation.open(AppRoute.CATEGORY_DETAIL) }
        }
        SectionCard(
            "Upcoming",
            "Two bills before the end of the month",
            "View all",
            onClick = { navigation.open(AppRoute.CATEGORY_GROUP) },
        ) {
            ListRow("↗", "Electric Company", "Utilities · due May 18", "$120", palette.text)
            DividerLine()
            ListRow("H", "Rent", "Essentials · due Jun 1", "$1,500", palette.text)
        }
        SectionCard("Recent activity", "Your latest budget changes", "See all", onClick = { navigation.selectTab(AppRoute.ACTIVITY) }) {
            previewTransactions.take(2).forEachIndexed { index, item ->
                if (index > 0) DividerLine()
                TransactionRow(item) { navigation.open(AppRoute.TRANSACTION_DETAIL) }
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
                    Text("Sample preview · nothing has synced", color = palette.muted, fontSize = 11.sp)
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
    val palette = companionPalette
    Column {
        Text(transaction.date, modifier = Modifier.padding(start = 14.dp, top = 11.dp), color = palette.muted, fontSize = 10.sp)
        ListRow(
            transaction.icon,
            transaction.payee,
            transaction.category,
            transaction.amount,
            if (transaction.amount.startsWith("+")) palette.positive else palette.text,
            onClick,
        )
    }
}

@Composable
private fun MonthPickerScreen(
    state: AppState,
    navigation: AppNavigation,
) {
    PageColumn {
        Heading("Choose a month", "Review your plan by month.")
        var selectedYear by remember { mutableStateOf("2026") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                "‹",
                modifier =
                    Modifier
                        .semantics { contentDescription = "Previous year" }
                        .clickable(role = Role.Button, onClickLabel = "Previous year") {
                            selectedYear = (selectedYear.toInt() - 1).toString()
                        }.padding(10.dp),
                color = companionPalette.text,
                fontSize = 24.sp,
            )
            Text(selectedYear, color = companionPalette.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(
                "›",
                modifier =
                    Modifier
                        .semantics { contentDescription = "Next year" }
                        .clickable(role = Role.Button, onClickLabel = "Next year") {
                            selectedYear = (selectedYear.toInt() + 1).toString()
                        }.padding(10.dp),
                color = companionPalette.text,
                fontSize = 24.sp,
            )
        }
        val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        months.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                row.forEach { month ->
                    val full = "$month $selectedYear"
                    val chosen = state.selectedMonth == full
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(72.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(if (chosen) companionPalette.accentSurface else companionPalette.surface)
                                .border(1.dp, if (chosen) companionPalette.accent else companionPalette.border, RoundedCornerShape(15.dp))
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
                            month,
                            color = if (chosen) companionPalette.accent else companionPalette.text,
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
    val palette = companionPalette
    PageColumn {
        SampleBanner()
        Heading("Essentials", "The bills and needs you plan around first.")
        AmountHero("Assigned this month", "$2,320.00", "$2,320.00 available across 3 categories")
        SectionCard("Categories", "3 plans", "Edit group") {
            ListRow(
                "H",
                "Rent",
                "Assigned $1,500 · Available $1,500",
                "$1,500",
                palette.positive,
            ) { navigation.open(AppRoute.CATEGORY_DETAIL) }
            DividerLine()
            ListRow(
                "G",
                "Groceries",
                "Assigned $500 · Available $500",
                "$500",
                palette.positive,
            ) { navigation.open(AppRoute.CATEGORY_DETAIL) }
            DividerLine()
            ListRow(
                "U",
                "Utilities",
                "Assigned $320 · Available $320",
                "$320",
                palette.positive,
            ) { navigation.open(AppRoute.CATEGORY_DETAIL) }
        }
        PreviewNote("Category balances and activity here are sample preview data.")
    }
}

@Composable
private fun CategoryDetailScreen(navigation: AppNavigation) {
    PageColumn {
        SampleBanner()
        Heading("Rent", "Essentials · monthly plan")
        AmountHero("Available", "$1,500.00", "Fully funded for May")
        SectionCard("This month", "Assigned $1,500 · Spent $0") {
            ListRow("↗", "Planned amount", "Monthly target", "$1,500")
            DividerLine()
            ListRow("◷", "Next due date", "Scheduled payment", "Jun 1")
        }
        PrimaryButton("View related activity") { navigation.selectTab(AppRoute.ACTIVITY) }
        PreviewNote("Category detail is a read-only sample. The connected app will use the selected budget’s local projection.")
    }
}

@Composable
private fun AccountsScreen(navigation: AppNavigation) {
    PageColumn {
        SampleBanner()
        Heading("Accounts", "A clear picture of where your money lives.")
        AmountHero("Total on budget", "$3,482.16", "Across 4 sample accounts")
        SectionCard("Cash accounts", "2 accounts", "") {
            ListRow("C", "Checking", "Everyday spending", "$3,482.16", companionPalette.text) { navigation.open(AppRoute.ACCOUNT_DETAIL) }
            DividerLine()
            ListRow("S", "Savings", "Emergency fund", "$8,200.00", companionPalette.text) { navigation.open(AppRoute.ACCOUNT_DETAIL) }
        }
        SectionCard("Credit", "1 account", "") {
            ListRow(
                "V",
                "Travel card",
                "Statement closes May 22",
                "−$642.80",
                companionPalette.text,
            ) { navigation.open(AppRoute.ACCOUNT_DETAIL) }
        }
        SectionCard("Tracking", "1 account", "") {
            ListRow("H", "Car loan", "Off budget", "−$8,300.00", companionPalette.muted) { navigation.open(AppRoute.ACCOUNT_DETAIL) }
        }
        SecondaryButton("Account options") { navigation.open(AppRoute.ACCOUNT_OPTIONS) }
    }
}

@Composable
private fun AccountDetailScreen(navigation: AppNavigation) {
    PageColumn {
        SampleBanner()
        AmountHero("Available balance", "$3,482.16", "Checking · Updated May 14")
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            PrimaryButton("Add transaction") { navigation.open(AppRoute.NEW_TRANSACTION) }
            SecondaryButton("Reconcile") { navigation.open(AppRoute.RECONCILIATION) }
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
            "Compare the account with your statement",
            onClick = { navigation.open(AppRoute.RECONCILIATION) },
        )
        SettingRow("⊖", "Hide from budget", "Keep historical activity available", onClick = { })
        PreviewNote(
            "Account changes are not connected in this preview. Reconciliation remains unavailable until the server API supports it.",
        )
    }
}

@Composable
private fun ReconciliationScreen() {
    PageColumn {
        Heading("Reconcile Checking", "Compare your cleared balance with a statement.")
        TextFieldCard("Statement balance", "3,482.16", {}, "$0.00")
        SectionCard("Difference", "Sample calculation") {
            ListRow("=", "Account balance", "Current sample balance", "$3,482.16")
            DividerLine()
            ListRow("Δ", "Out of balance", "No cleared transactions compared", "$0.00", companionPalette.positive)
        }
        PreviewNote(
            "Reconciliation is display-only. Do not treat the sample balance as verified, and no statement or transaction will be changed.",
        )
        PrimaryButton("Reconcile account", enabled = false) {}
    }
}

@Composable
private fun ActivityScreen(navigation: AppNavigation) {
    PageColumn {
        SampleBanner()
        Heading("Activity", "Every change in one searchable timeline.")
        SurfaceCard(onClick = { navigation.open(AppRoute.ACTIVITY_SEARCH) }) {
            Row(
                Modifier.fillMaxWidth().padding(13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("⌕", color = companionPalette.accent, fontSize = 20.sp)
                Text("Search transactions", color = companionPalette.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("⌘ K", color = companionPalette.muted, fontSize = 10.sp)
            }
        }
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Income", "Spending", "Transfers").forEachIndexed { index, filter ->
                Badge(filter, if (index == 0) companionPalette.accent else companionPalette.muted)
            }
            Badge("Filter · 2", companionPalette.accent)
        }
        SectionCard("May 2026", "6 sample transactions", "Filter", onClick = { navigation.open(AppRoute.ACTIVITY_FILTER) }) {
            previewTransactions.forEachIndexed { index, item ->
                if (index > 0) DividerLine()
                TransactionRow(item) { navigation.open(AppRoute.TRANSACTION_DETAIL) }
            }
        }
        PreviewNote("Activity is sample content. Search and filters illustrate the intended page behavior.")
        PrimaryButton("Add transaction") { navigation.open(AppRoute.NEW_TRANSACTION) }
    }
}

@Composable
private fun ActivitySearchScreen(navigation: AppNavigation) {
    var search by remember { mutableStateOf("") }
    PageColumn {
        Heading("Search activity", "Find a payee, note, amount, or category.")
        TextFieldCard("Search", search, { search = it }, "Try “groceries” or “$42”")
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
        Heading("Filter activity", "Narrow the timeline to what you want to review.")
        SectionCard("Transaction type", "Choose one or more") {
            listOf("Income", "Spending", "Transfers").forEachIndexed { index, item -> ToggleRow(item, checked = index != 1, onChange = {}) }
        }
        SectionCard("Date range", "May 1–31, 2026") {
            ListRow("▣", "Start date", "May 1, 2026", onClick = {})
            DividerLine()
            ListRow("▣", "End date", "May 31, 2026", onClick = {})
        }
        SectionCard("Sort order", "Most recent first") {
            ChoiceRow("Newest first", selected = true, onClick = {})
            ChoiceRow("Oldest first", selected = false, onClick = {})
        }
        PreviewNote("Filter state is illustrative and is not saved to a live activity query.")
    }
}

@Composable
private fun TransactionDetailScreen(navigation: AppNavigation) {
    PageColumn {
        SampleBanner()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text("−$42.18", color = companionPalette.text, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text("Corner Market", color = companionPalette.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("May 14, 2026 · Checking", color = companionPalette.muted, fontSize = 12.sp)
        }
        SectionCard("Transaction", "Sample details") {
            ListRow("C", "Account", "Where money moved", "Checking")
            DividerLine()
            ListRow("G", "Category", "Essentials", "Groceries")
            DividerLine()
            ListRow("◷", "Date", "Posted", "May 14, 2026")
            DividerLine()
            ListRow("▤", "Notes", "Optional", "Weekly shop")
        }
        PreviewNote("This is a read-only sample transaction. Editing it here will not change Actual Budget.")
        PrimaryButton("Edit transaction") { navigation.open(AppRoute.EDIT_TRANSACTION) }
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
        Heading(if (isNew) "Add transaction" else "Edit transaction", "Record a change to your budget.")
        TextFieldCard("Amount", state.transactionAmount, { state.transactionAmount = it }, "For example, 12.50")
        ReadOnlyField("Account", state.transactionAccount, "A") { navigation.open(AppRoute.CHOOSE_ACCOUNT) }
        ReadOnlyField("Payee", state.transactionPayee, "P") { navigation.open(AppRoute.CHOOSE_PAYEE) }
        ReadOnlyField("Category", state.transactionCategory, "C") { navigation.open(AppRoute.CHOOSE_CATEGORY) }
        ReadOnlyField("Date", state.transactionDate, "▣") { navigation.open(AppRoute.CHOOSE_DATE) }
        TextFieldCard("Notes", state.transactionNotes, { state.transactionNotes = it }, "Add a note (optional)", singleLine = false)
        PreviewNote(
            if (isNew) {
                "Transaction creation is not supported by the current server API. Saving is disabled from the live budget; this preview will not create or sync a transaction."
            } else {
                "The current server supports transaction updates, but this visual preview has no connected budget and cannot save edits."
            },
        )
        PrimaryButton("Review save status") { navigation.open(AppRoute.SAVE_RESULT) }
    }
}

@Composable
private fun SelectorScreen(
    title: String,
    values: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    PageColumn {
        Heading(title, "Choose an item for this sample transaction.")
        TextFieldCard("Search", query, { query = it }, "Start typing")
        values.filter { it.contains(query, ignoreCase = true) }.forEach { value ->
            ChoiceRow(value, selected = selected == value, onClick = { onSelect(value) })
        }
        PreviewNote("Selections update only the form shown in this prototype.")
    }
}

@Composable
private fun DatePickerScreen(
    state: AppState,
    navigation: AppNavigation,
) {
    val days = (1..31).toList()
    PageColumn {
        Heading("Choose date", "May 2026")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, modifier = Modifier.weight(1f), color = companionPalette.muted, textAlign = TextAlign.Center, fontSize = 11.sp)
            }
        }
        days.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { day ->
                    val label = "May $day, 2026"
                    val selected = state.transactionDate == label
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(CircleShape)
                                .background(if (selected) companionPalette.accentSurface else Color.Transparent)
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
                            day.toString(),
                            color = if (selected) companionPalette.accent else companionPalette.text,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        PreviewNote("The date selector uses sample local dates.")
    }
}

@Composable
private fun SaveResultScreen(navigation: AppNavigation) {
    val palette = companionPalette
    PageColumn {
        Spacer(Modifier.height(16.dp))
        Badge("NOT SAVED", palette.warning)
        Heading("No change was made", "The sample form is ready for a future connected save flow.")
        SectionCard("Transaction draft", "Preview only") {
            ListRow("$", "Amount", "Corner Market", "−$42.18")
            DividerLine()
            ListRow("C", "Account and category", "Checking · Groceries")
        }
        PreviewNote(
            "Actual Budget remains unchanged. The live transaction create endpoint is not supported yet, and this build has not connected to a server.",
        )
        PrimaryButton("Return to activity") { navigation.selectTab(AppRoute.ACTIVITY) }
        SecondaryButton("Open sync center") { navigation.open(AppRoute.SYNC_CENTER) }
    }
}

@Composable
private fun SyncCenterScreen(navigation: AppNavigation) {
    PageColumn {
        SampleBanner()
        Heading("Sync center", "Understand exactly what is on this device and on the server.")
        SurfaceCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Badge("PREVIEW")
                    Text("No server connection", color = companionPalette.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Text("Last sync · sample only", color = companionPalette.muted, fontSize = 12.sp)
                PreviewNote("No data has been uploaded or downloaded.")
            }
        }
        SectionCard("Attention needed", "Example recovery states") {
            ListRow("!", "Conflict to review", "Corner Market · two versions", "1", companionPalette.warning) {
                navigation.open(AppRoute.CONFLICT_INBOX)
            }
            DividerLine()
            ListRow(
                "?",
                "Write status unknown",
                "Check before retrying",
                "1",
                companionPalette.warning,
            ) { navigation.open(AppRoute.UNCERTAIN_WRITE) }
            DividerLine()
            ListRow(
                "×",
                "Edit was rejected",
                "Unsupported action example",
                "1",
                companionPalette.negative,
            ) { navigation.open(AppRoute.REJECTED_WRITE) }
        }
        SectionCard("Recent sync activity", "Preview events", "") {
            ListRow("↓", "Budget snapshot", "Ready for initial download", "Pending")
            DividerLine()
            ListRow("↑", "Local edits", "None in this preview", "0")
        }
        SecondaryButton("Review all conflicts") { navigation.open(AppRoute.CONFLICT_INBOX) }
    }
}

@Composable
private fun ConflictInboxScreen(navigation: AppNavigation) {
    PageColumn {
        SampleBanner()
        Heading("Needs your review", "Choose which version should remain before a change is sent.")
        SurfaceCard(onClick = { navigation.open(AppRoute.CONFLICT_REVIEW) }) {
            Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Badge("1 CONFLICT", companionPalette.warning)
                    Text("Corner Market", color = companionPalette.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Text("May 14, 2026 · Groceries · Checking", color = companionPalette.muted, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ConflictValueCard("On this device", "$12.00", Modifier.weight(1f))
                    ConflictValueCard("In Actual", "$15.00", Modifier.weight(1f))
                }
                Text("Review both versions ›", color = companionPalette.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
        modifier = modifier.clip(RoundedCornerShape(13.dp)).background(companionPalette.raised).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(label, color = companionPalette.muted, fontSize = 10.sp)
        Text(amount, color = companionPalette.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ConflictReviewScreen(state: AppState) {
    PageColumn {
        Badge("CONFLICT · PREVIEW", companionPalette.warning)
        Heading("Two versions differ", "Corner Market · May 14, 2026 · Groceries")
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            ConflictValueCard("On this device", "$12.00", Modifier.weight(1f))
            ConflictValueCard("In Actual", "$15.00", Modifier.weight(1f))
        }
        SectionCard("Other details", "Review before choosing") {
            ListRow("C", "Account", "Same on both versions", "Checking")
            DividerLine()
            ListRow("G", "Category", "Same on both versions", "Groceries")
            DividerLine()
            ListRow("◷", "Date", "Same on both versions", "May 14")
        }
        if (state.conflictChoice.isNotEmpty()) {
            PreviewNote("Preview choice selected: ${state.conflictChoice}. No server update has been sent.")
        }
        PrimaryButton("Keep $12.00 on this device") { state.conflictChoice = "device value $12.00" }
        SecondaryButton("Keep $15.00 from Actual") { state.conflictChoice = "Actual value $15.00" }
        PreviewNote(
            "A real choice creates a new edit against the latest server revision. Nothing changes until that operation is confirmed.",
        )
    }
}

@Composable
private fun UncertainWriteScreen() {
    PageColumn {
        Badge("STATUS UNKNOWN", companionPalette.warning)
        Heading("Check before retrying", "The connection ended before this change could be confirmed.")
        SectionCard("Corner Market", "May 14 · Groceries") {
            ListRow("$", "Requested amount", "Local change", "$12.00")
            DividerLine()
            ListRow("?", "Server result", "Needs an operation status check", "Unknown", companionPalette.warning)
        }
        PreviewNote(
            "Never resend an uncertain write blindly. The live client first checks its operation ID; retry and discard controls will appear only when the server response is safe to act on.",
        )
        PrimaryButton("Check operation status", enabled = false) {}
    }
}

@Composable
private fun RejectedWriteScreen() {
    PageColumn {
        Badge("NOT APPLIED", companionPalette.negative)
        Heading("This edit was not applied", "The latest Actual value remains the source of truth.")
        SectionCard("Sample edit", "Corner Market · May 14") {
            ListRow("$", "Requested amount", "Device draft", "$12.00")
            DividerLine()
            ListRow("!", "Reason", "Creation is not supported by the API", "Unsupported", companionPalette.warning)
        }
        PreviewNote(
            "A rejected edit does not stay over the remote record. The live app will preserve the reason and offer a safe discard action.",
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
        Heading("Settings", "Make the Companion feel right for you.")
        SectionCard("Personalize", "Appearance and budget") {
            ListRow("◐", "Appearance", "Theme, accent, and background", state.theme.label) { navigation.open(AppRoute.APPEARANCE) }
            DividerLine()
            ListRow("⌂", "Home screen layout", "Choose your information density", "Comfortable") { navigation.open(AppRoute.HOME_LAYOUT) }
            DividerLine()
            ListRow("$", "Budget preferences", "Currency and planning choices") { navigation.open(AppRoute.BUDGET_PREFERENCES) }
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
    val palette = companionPalette
    PageColumn {
        Heading("Your appearance", "One Companion design across your devices.")
        SurfaceCard {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LabelText("LIVE PREVIEW")
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
                Text("$1,240.00", color = palette.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Available to assign · ${state.selectedMonth}", color = palette.muted, fontSize = 11.sp)
            }
        }
        SectionCard("Theme", "${state.theme.label} appearance", "") {
            ListRow("◐", "Theme mode", "System, light, dark, or pure black", state.theme.label) { navigation.open(AppRoute.THEME_MODE) }
        }
        SectionCard("Color", "${state.accent.label} accent", "") {
            ListRow("●", "Accent color", "Selected across every app page", state.accent.label) { navigation.open(AppRoute.ACCENT_COLOR) }
        }
        SectionCard("Atmosphere", state.backgroundStyle, "") {
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
        Heading("Theme mode", "Choose how the Companion looks everywhere.")
        ThemeMode.entries.forEach { mode ->
            ChoiceRow(
                mode.label,
                when (mode) {
                    ThemeMode.SYSTEM -> "Follow this device’s light or dark setting"
                    ThemeMode.LIGHT -> "Bright surfaces with the Companion color system"
                    ThemeMode.DARK -> "The default low-glare Companion palette"
                    ThemeMode.PURE_BLACK -> "Black canvas with clear raised cards for OLED screens"
                },
                selected = state.theme == mode,
                onClick = { state.theme = mode },
            )
        }
        PreviewNote("The same selected mode and color tokens are used on Android and iOS. No separate Glass or Material theme is applied.")
    }
}

@Composable
private fun AccentColorScreen(state: AppState) {
    PageColumn {
        Heading("Accent color", "Choose one highlight color for actions and selected items.")
        AccentChoice.entries.forEach { accent ->
            val palette = companionPalette
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
        Heading("Background and motion", "Choose a calm backdrop that keeps money easy to read.")
        SectionCard("Background style", "Preview only") {
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
        Heading("Home screen layout", "Set the amount of detail shown on the budget page.")
        listOf(
            "Comfortable" to "Roomy cards and extra context",
            "Compact" to "More categories visible at once",
            "Essentials first" to "Keep upcoming bills near the top",
        ).forEach { (label, description) ->
            ChoiceRow(label, description, selected = state.homeLayout == label, onClick = { state.homeLayout = label })
        }
        SurfaceCard {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LabelText("LAYOUT PREVIEW")
                ListRow("H", "Essentials", "3 categories", "$2,320")
                DividerLine()
                ListRow("G", "Groceries", "Available this month", "$500")
            }
        }
    }
}

@Composable
private fun BudgetPreferencesScreen() {
    PageColumn {
        Heading("Budget preferences", "Choose how amounts and plans are presented.")
        SectionCard("Number format", "Sample budget uses USD") {
            ListRow("$", "Currency", "United States dollar", "USD")
            DividerLine()
            ListRow("◷", "Start of week", "Monday")
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
        Heading("Sync preferences", "Choose when the connected app should refresh.")
        SectionCard("Automatic refresh", "Local changes stay available offline") {
            ToggleRow("Sync when opening a budget", "Check for server changes when returning to a budget", state.syncOnOpen) {
                state.syncOnOpen =
                    it
            }
            DividerLine()
            ToggleRow("Sync only on Wi-Fi", "Avoid cellular data for automatic refreshes", state.syncOnWifi) { state.syncOnWifi = it }
        }
        SectionCard("Manual controls", "Always available") {
            ListRow("↻", "Sync now", "Read changes and reconcile queued edits", "Preview")
            DividerLine()
            ListRow("◷", "Last sync", "No real sync has run", "Never")
        }
        PreviewNote("These settings are not saved yet. Uncertain financial writes are checked before any retry.")
    }
}

@Composable
private fun ServerDeviceScreen(
    state: AppState,
    navigation: AppNavigation,
) {
    PageColumn {
        Heading("Server and paired device", "Review where this device would connect.")
        SectionCard("Companion server", "Not connected") {
            ListRow("◎", "Server URL", state.serverUrl, "Preview") { navigation.open(AppRoute.SERVER_ADDRESS) }
            DividerLine()
            ListRow("↻", "Connection status", "Sample only", "Offline", companionPalette.warning)
        }
        SectionCard("This device", "Pairing is not active") {
            ListRow("▣", "Device name", "Actual Companion on this device")
            DividerLine()
            ListRow("⌑", "Access scope", "No credential stored")
            DividerLine()
            ListRow("×", "Disconnect device", "Revoke local access", onClick = { navigation.open(AppRoute.SECURITY_PRIVACY) })
        }
        PreviewNote("Server address and pairing code are not saved or sent from this preview.")
    }
}

@Composable
private fun SecurityScreen(state: AppState) {
    PageColumn {
        Heading("Security and privacy", "Protect your budget and keep credentials in your control.")
        SectionCard("Device unlock", "Credentials belong in secure platform storage") {
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
        SectionCard("Data handling", "Companion API") {
            ListRow("⌑", "Device credentials", "Stored in platform secure storage", "Not paired")
            DividerLine()
            ListRow("◉", "Budget password", "Used only for the encrypted budget request", "Not stored")
        }
        PreviewNote("This preview stores no server credential, budget password, or transaction change.")
    }
}

@Composable
private fun NotificationsScreen(state: AppState) {
    PageColumn {
        Heading("Notifications", "Choose when the Companion should ask for your attention.")
        SectionCard("Budget activity", "You can change these at any time") {
            ToggleRow("Sync reminders", "Let you know when this device has been offline", state.notifications) { state.notifications = it }
            DividerLine()
            ToggleRow("Conflict review alerts", "Show a reminder when an edit needs your decision", state.notifications) {
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
        PreviewNote("System notification permission will only be requested when notification settings are connected.")
    }
}

@Composable
private fun LocalDataScreen() {
    PageColumn {
        Heading("Local data and storage", "See what is cached on this device.")
        AmountHero("Sample storage", "18.4 MB", "Estimated from fictional budget data")
        SectionCard("On this device", "No real budget is cached") {
            ListRow("▤", "Budget records", "Sample preview only", "0")
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
        Heading("About Actual Companion", "A community-built mobile companion for Actual Budget.")
        SurfaceCard {
            Column(
                Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(companionPalette.accentSurface),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("A", color = companionPalette.accent, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                }
                Text("Version 0.1.0 · UI preview", color = companionPalette.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Not affiliated with, endorsed by, or supported by Actual Budget.",
                    color = companionPalette.muted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
        SectionCard("Help and project", "") {
            ListRow("?", "Connection setup", "Pair a device with your server")
            DividerLine()
            ListRow("▣", "Open-source licenses", "AGPL-3.0-or-later")
            DividerLine()
            ListRow("↗", "Companion API version", "Compatibility is checked before a budget opens", "26.10.0")
        }
        PreviewNote("The finished app will include complete license notices, supported-version details, and help links.")
    }
}
