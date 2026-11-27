package app.tandemforactual.android

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.WidgetAccount
import app.tandemforactual.data.store.WidgetCategory
import app.tandemforactual.data.store.widgetAccountChoices
import app.tandemforactual.data.store.widgetCategoryChoices
import app.tandemforactual.ui.feature.widgets.WidgetSetupContent
import app.tandemforactual.ui.feature.widgets.WidgetSetupOption
import app.tandemforactual.ui.feature.widgets.WidgetSetupScreen
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lets someone pick what a newly placed or reconfigured widget shows, from the budget already on this device. */
class WidgetSetupActivity : ComponentActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var preferences by mutableStateOf<Map<String, String>>(emptyMap())
    private var content by mutableStateOf<WidgetSetupContent?>(null)
    private var accounts: List<WidgetAccount> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(RESULT_CANCELED, resultIntent())
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)?.provider
        if (provider?.packageName != packageName) return finish()
        val quickAdd = QuickAddWidget.isQuickAdd(this, widgetId)
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { load(quickAdd) }
            preferences = loaded.first
            content = loaded.second
        }
        setContent {
            content?.let { setup ->
                WidgetSetupScreen(preferences, setup, onClose = ::finish) { chosen -> save(quickAdd, chosen) }
            }
        }
    }

    private suspend fun load(quickAdd: Boolean): Pair<Map<String, String>, WidgetSetupContent> {
        val database = TandemProcess.database(applicationContext)
        val repository = LocalBudgetRepository(database) { System.currentTimeMillis() }
        val saved = runCatching { AppPreferencesStore(database).read() }.getOrDefault(emptyMap())
        if (quickAdd) {
            accounts = runCatching { widgetAccountChoices(repository, saved) }.getOrNull().orEmpty()
            return saved to quickAddContent(accounts, WidgetSettings.account(this, widgetId))
        }
        val categories = runCatching { widgetCategoryChoices(repository, saved, LocalDate.now().toString()) }.getOrNull()
        return saved to categoryContent(categories, WidgetSettings.categories(this, widgetId))
    }

    private fun save(
        quickAdd: Boolean,
        chosen: List<String>,
    ) {
        if (quickAdd) {
            val account = accounts.firstOrNull { it.id == chosen.firstOrNull() }
            WidgetSettings.setAccount(this, widgetId, account?.id, account?.name)
            QuickAddWidget.update(this, widgetId)
        } else {
            WidgetSettings.setCategories(this, widgetId, chosen)
            TandemWidgets.refresh(this)
        }
        setResult(RESULT_OK, resultIntent())
        finish()
    }

    private fun resultIntent(): Intent = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
}

private const val LAST_USED_ACCOUNT = ""

internal fun categoryContent(
    categories: List<WidgetCategory>?,
    chosen: List<String>,
): WidgetSetupContent =
    WidgetSetupContent(
        title = "Left to spend",
        intro = "Pick the categories to show. The widget shows what’s left in each one this month.",
        options = categories.orEmpty().map { WidgetSetupOption(it.id, it.name, it.groupName.ifBlank { "Category" }) },
        emptyMessage =
            if (categories == null) {
                "Open Tandem and open a budget first. Widgets stay empty while the app lock is on or the budget is encrypted."
            } else {
                "This budget has no categories this month yet. Open Tandem to sync it, then try again."
            },
        multiple = true,
        initial = chosen.toSet(),
        confirmLabel = "Save",
    )

internal fun quickAddContent(
    accounts: List<WidgetAccount>,
    chosen: String?,
): WidgetSetupContent =
    WidgetSetupContent(
        title = "Quick add",
        intro = "Pick the account new transactions from this widget start in.",
        options =
            listOf(WidgetSetupOption(LAST_USED_ACCOUNT, "Last used account", "The account you added to most recently")) +
                accounts.map { WidgetSetupOption(it.id, it.name, "Account") },
        emptyMessage = "",
        multiple = false,
        initial = setOf(chosen?.takeIf { id -> accounts.any { it.id == id } } ?: LAST_USED_ACCOUNT),
        confirmLabel = "Save",
    )
