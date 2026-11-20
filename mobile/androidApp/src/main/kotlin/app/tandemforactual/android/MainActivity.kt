package app.tandemforactual.android

import app.tandemforactual.ui.QuickActionEvents
import androidx.core.graphics.drawable.IconCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.content.pm.ShortcutInfoCompat
import app.tandemforactual.ui.SyncNetwork
import app.tandemforactual.ui.NetworkKind
import android.net.NetworkCapabilities
import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.database.openTandemDatabase
import app.tandemforactual.data.database.tandemDatabaseBuilder
import app.tandemforactual.ui.TandemApp
import app.tandemforactual.ui.AuthReturnEvents
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

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

  override fun onCreate(savedInstanceState: Bundle?) {
    val splashScreen: SplashScreen = installSplashScreen()
    val splashDeadline = SystemClock.uptimeMillis() + MAX_SYSTEM_SPLASH_WAIT_MILLIS
    splashScreen.setKeepOnScreenCondition {
      !appStartupComplete && SystemClock.uptimeMillis() < splashDeadline
    }
    super.onCreate(savedInstanceState)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(false)
    intent?.dataString?.let { AuthReturnEvents.publish(it) }
    handleQuickAction(intent)
    publishQuickAddShortcut()
    notificationPermissionEnabled = hasNotificationPermission()
    BudgetSystemNotifications.initialize(applicationContext)
    val database = openTandemDatabase(tandemDatabaseBuilder(applicationContext))
    tandemDatabase = database
    setContent {
      val onBackgroundSyncSettingsChanged: (Boolean, SyncNetwork, String) -> Unit = remember(applicationContext) {
        { enabled, network, budgetId ->
          BackgroundBudgetSyncScheduler.update(applicationContext, enabled, network, budgetId)
        }
      }
      TandemApp(
        deviceName =
          Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME)
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
      )
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    intent.dataString?.let { AuthReturnEvents.publish(it) }
    handleQuickAction(intent)
  }

  private fun handleQuickAction(intent: Intent?) {
    if (intent?.action == ACTION_ADD_TRANSACTION) QuickActionEvents.requestAddTransaction()
  }

  private fun publishQuickAddShortcut() {
    val shortcut =
      ShortcutInfoCompat
        .Builder(this, "add-transaction")
        .setShortLabel("Add transaction")
        .setIcon(IconCompat.createWithResource(this, R.drawable.ic_shortcut_add_transaction))
        .setIntent(Intent(this, MainActivity::class.java).setAction(ACTION_ADD_TRANSACTION))
        .build()
    runCatching { ShortcutManagerCompat.pushDynamicShortcut(this, shortcut) }
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


  private fun appNotificationsEnabled(): Boolean =
    getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() != false

  private fun hasNotificationPermission(): Boolean =
    appNotificationsEnabled() && hasRuntimeNotificationPermission()

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
    if (!networkCallbackRegistered) {
      syncSignalState.onNetworkStarted(connectivityManager.activeNetwork)
      connectivityManager.registerDefaultNetworkCallback(networkCallback)
      networkCallbackRegistered = true
    }
  }

  override fun onStop() {
    appInForeground.value = false
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

internal const val ACTION_ADD_TRANSACTION = "app.tandemforactual.action.ADD_TRANSACTION"
