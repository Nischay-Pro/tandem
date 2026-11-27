package app.tandemforactual.android

import android.content.Context
import android.content.SharedPreferences

/** What each placed widget was set to show, kept on this device only and forgotten when the widget is removed. */
internal object WidgetSettings {
    private const val FILE = "widget_settings"

    fun categories(
        context: Context,
        widgetId: Int,
    ): List<String> =
        preferences(context)
            .getString("categories-$widgetId", null)
            .orEmpty()
            .split(',')
            .filter(String::isNotBlank)

    fun setCategories(
        context: Context,
        widgetId: Int,
        categoryIds: List<String>,
    ) {
        preferences(context).edit().putString("categories-$widgetId", categoryIds.joinToString(",")).apply()
    }

    fun account(
        context: Context,
        widgetId: Int,
    ): String? = preferences(context).getString("account-$widgetId", null)?.takeIf(String::isNotBlank)

    fun accountName(
        context: Context,
        widgetId: Int,
    ): String? = preferences(context).getString("account-name-$widgetId", null)?.takeIf(String::isNotBlank)

    fun setAccount(
        context: Context,
        widgetId: Int,
        accountId: String?,
        accountName: String?,
    ) {
        preferences(context)
            .edit()
            .putString("account-$widgetId", accountId.orEmpty())
            .putString("account-name-$widgetId", accountName.orEmpty())
            .apply()
    }

    fun forget(
        context: Context,
        widgetIds: IntArray,
    ) {
        val editor = preferences(context).edit()
        widgetIds.forEach { editor.remove("categories-$it").remove("account-$it").remove("account-name-$it") }
        editor.apply()
    }

    private fun preferences(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
