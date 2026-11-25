package app.tandemforactual.android

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.database.openTandemDatabase
import app.tandemforactual.data.database.tandemDatabaseBuilder
import app.tandemforactual.ui.app.AuthReturnEvents
import app.tandemforactual.ui.app.QuickActionEvents
import app.tandemforactual.ui.app.ScreenshotScript
import app.tandemforactual.ui.app.TandemApp
import app.tandemforactual.ui.app.WindowHinge
import app.tandemforactual.ui.core.session.BackupSecrets
import app.tandemforactual.ui.core.session.LocalBackupSecrets
import app.tandemforactual.ui.core.session.LocalBankIcons
import app.tandemforactual.ui.core.session.NetworkKind
import app.tandemforactual.ui.core.session.SyncNetwork
import app.tandemforactual.ui.core.session.bankIcons
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private var appStartupComplete = false
    private val deviceAuthenticator by lazy { AndroidDeviceAuthenticator(this) }
    private val deviceCredentialStore by lazy { AndroidDeviceCredentialStore(applicationContext) }
    private val encryptedBudgetVault by lazy { AndroidEncryptedBudgetVault(applicationContext) }
    private var tandemDatabase: TandemDatabase? = null
    private var notificationPermissionEnabled by mutableStateOf(false)
    private var openSettingsIfBlocked = false
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            notificationPermissionEnabled = granted && appNotificationsEnabled()
            val blocked = !granted && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
            if (blocked && openSettingsIfBlocked) openNotificationSettings()
            openSettingsIfBlocked = false
        }
    private val appResumeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val appInForeground = MutableStateFlow(false)
    private val networkRestoredEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val syncSignalState = SyncSignalState<Network>()
    private var networkCallbackRegistered = false
    private val currentNetwork = MutableStateFlow<NetworkKind?>(null)
    private val connectivityManager by lazy {
        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    private val networkCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (syncSignalState.onNetworkAvailable(network)) networkRestoredEvents.tryEmit(Unit)
            }

            override fun onLost(network: Network) {
                syncSignalState.onNetworkLost(network)
                currentNetwork.value = null
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                currentNetwork.value =
                    if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) {
                        NetworkKind.UNMETERED
                    } else {
                        NetworkKind.METERED
                    }
            }
        }

    private val crashReports by lazy { AndroidCrashReports(this) }
    private val windowHinge = MutableStateFlow<WindowHinge?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen: SplashScreen = installSplashScreen()
        val splashDeadline = SystemClock.uptimeMillis() + MAX_SYSTEM_SPLASH_WAIT_MILLIS
        splashScreen.setKeepOnScreenCondition {
            !appStartupComplete && SystemClock.uptimeMillis() < splashDeadline
        }
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(false)
        crashReports.install()
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) { removeStaleSharedFiles() }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@MainActivity).windowLayoutInfo(this@MainActivity).collect { info ->
                    windowHinge.value = info.separatingVerticalHinge()
                }
            }
        }
        handleQuickAction(intent)
        runCatching { ShortcutManagerCompat.removeDynamicShortcuts(this, listOf(LEGACY_QUICK_ADD_SHORTCUT_ID)) }
        notificationPermissionEnabled = hasNotificationPermission()
        BudgetSystemNotifications.initialize(applicationContext)
        AutomaticBackupScheduler.ensureScheduled(applicationContext)
        val database = openTandemDatabase(tandemDatabaseBuilder(applicationContext))
        tandemDatabase = database
        setContent {
            val onBackgroundSyncSettingsChanged: (Boolean, SyncNetwork, String) -> Unit =
                remember(applicationContext) {
                    { enabled, network, budgetId ->
                        BackgroundBudgetSyncScheduler.update(applicationContext, enabled, network, budgetId)
                    }
                }
            val backupSecrets =
                remember {
                    BackupSecrets(
                        AndroidDeviceCredentialStore(applicationContext, BACKUP_PASSPHRASE_STORE),
                        AndroidDeviceCredentialStore(applicationContext, BACKUP_WEBDAV_STORE),
                    )
                }
            val bankIcons =
                remember { bankIcons(applicationContext.filesDir.resolve("bank-icons").path) { System.currentTimeMillis() } }
            CompositionLocalProvider(LocalBackupSecrets provides backupSecrets, LocalBankIcons provides bankIcons) {
                TandemApp(
                    actualBudgetFiles =
                        remember {
                            app.tandemforactual.data.database
                                .AndroidActualBudgetFiles(applicationContext)
                        },
                    planningKeyStore = remember { AndroidDeviceCredentialStore(applicationContext, "planning_keys") },
                    backgroundBudgetKeyStore = remember { AndroidDeviceCredentialStore(applicationContext, BACKGROUND_KEY_STORE) },
                    deviceName =
                        Settings.Global
                            .getString(contentResolver, Settings.Global.DEVICE_NAME)
                            ?.takeIf { it.isNotBlank() } ?: Build.MODEL ?: "Android device",
                    database = database,
                    deviceCredentialStore = deviceCredentialStore,
                    nowEpochMillis = { System.currentTimeMillis() },
                    allowInsecurePrivateNetwork = true,
                    onBackgroundSyncSettingsChanged = onBackgroundSyncSettingsChanged,
                    onRequestNotificationPermission = { requestNotificationPermission() },
                    onOpenNotificationSettings = { openNotificationSettings() },
                    notificationPermissionEnabled = notificationPermissionEnabled,
                    onToggleNotificationPermission = { updateNotificationPermission(it) },
                    onStartupComplete = {
                        appStartupComplete = true
                    },
                    appResumeEvents = appResumeEvents,
                    appInForeground = appInForeground,
                    networkRestoredEvents = networkRestoredEvents,
                    deviceAuthenticator = deviceAuthenticator,
                    encryptedBudgetVault = encryptedBudgetVault,
                    currentNetwork = currentNetwork,
                    crashReports = crashReports,
                    onShareText = { text -> shareText(text) },
                    windowHinge = windowHinge,
                    screenshotScript = screenshotScript(),
                    postLocalAlert = { BudgetSystemNotifications.post(applicationContext, it) },
                )
            }
        }
    }

    /** Exports and receipt photos are shared from the cache; once they're old, nothing still needs to read them. */
    private fun removeStaleSharedFiles() {
        val cutoff = System.currentTimeMillis() - STALE_SHARED_FILE_MILLIS
        listOf("exports", "receipts").forEach { folder ->
            java.io
                .File(cacheDir, folder)
                .listFiles()
                ?.filter { it.lastModified() < cutoff }
                ?.forEach { it.delete() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleQuickAction(intent)
    }

    private fun screenshotScript(): ScreenshotScript? {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return null
        val extras = intent?.extras ?: return null
        return ScreenshotScript.from(extras.keySet().associateWith { extras.getString(it) })
    }

    private fun shareText(text: String) {
        val send =
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "Tandem crash report")
                .putExtra(Intent.EXTRA_TEXT, text)
        val chooser =
            Intent
                .createChooser(send, null)
                .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(this, MainActivity::class.java)))
        startActivity(chooser)
    }

    private fun handleQuickAction(intent: Intent?) {
        when {
            intent?.action == Intent.ACTION_VIEW ->
                intent.dataString?.let { link -> if (!QuickActionEvents.openLink(link)) AuthReturnEvents.publish(link) }
            intent?.action == ACTION_ADD_TRANSACTION -> QuickActionEvents.requestAddTransaction()
            intent?.action == Intent.ACTION_SEND && intent.type == "text/plain" ->
                intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let(QuickActionEvents::shareText)
        }
    }

    private fun requestNotificationPermission() = askForNotifications(openSettingsIfBlocked = false)

    private fun updateNotificationPermission(enabled: Boolean) {
        if (enabled) askForNotifications(openSettingsIfBlocked = true) else openNotificationSettings()
    }

    private fun askForNotifications(openSettingsIfBlocked: Boolean) {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || hasRuntimeNotificationPermission()
        if (granted) {
            notificationPermissionEnabled = appNotificationsEnabled()
            if (!notificationPermissionEnabled && openSettingsIfBlocked) openNotificationSettings()
            return
        }
        this.openSettingsIfBlocked = openSettingsIfBlocked
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun appNotificationsEnabled(): Boolean = getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() != false

    private fun hasNotificationPermission(): Boolean = appNotificationsEnabled() && hasRuntimeNotificationPermission()

    private fun hasRuntimeNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    override fun onResume() {
        super.onResume()
        notificationPermissionEnabled = hasNotificationPermission()
        if (syncSignalState.onActivityResumed()) appResumeEvents.tryEmit(Unit)
    }

    private fun openNotificationSettings() {
        val intent =
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
        startActivity(intent)
    }

    override fun onStart() {
        super.onStart()
        appInForeground.value = true
        AppVisibility.visible = true
        if (!networkCallbackRegistered) {
            syncSignalState.onNetworkStarted(connectivityManager.activeNetwork)
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
            networkCallbackRegistered = true
        }
    }

    override fun onStop() {
        appInForeground.value = false
        AppVisibility.visible = false
        BudgetWidget.refresh(this)
        if (networkCallbackRegistered) {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            networkCallbackRegistered = false
            syncSignalState.onActivityStopped()
        }
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        tandemDatabase?.close()
        tandemDatabase = null
    }
}

private const val MAX_SYSTEM_SPLASH_WAIT_MILLIS = 2_500L

private const val LEGACY_QUICK_ADD_SHORTCUT_ID = "add-transaction"

private const val STALE_SHARED_FILE_MILLIS = 60L * 60 * 1000

internal const val ACTION_ADD_TRANSACTION = "app.tandemforactual.action.ADD_TRANSACTION"

private fun WindowLayoutInfo.separatingVerticalHinge(): WindowHinge? =
    displayFeatures
        .filterIsInstance<FoldingFeature>()
        .firstOrNull { it.isSeparating && it.orientation == FoldingFeature.Orientation.VERTICAL }
        ?.bounds
        ?.let { WindowHinge(it.left.toFloat(), it.right.toFloat()) }
