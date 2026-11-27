package app.tandemforactual.wear.complications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import app.tandemforactual.watch.WatchCategory
import app.tandemforactual.watch.WatchState
import app.tandemforactual.watch.WatchSummary
import app.tandemforactual.wear.MainActivity
import app.tandemforactual.wear.data.WatchStore

class LeftToSpendComplicationService : SuspendingComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val store = WatchStore.load(this)
        val categoryId = store.complicationCategory(request.complicationInstanceId)
        return WatchComplications.leftToSpend(store.summary.value, categoryId, request.complicationType, openApp(this))
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData = WatchComplications.leftToSpend(PREVIEW, null, type, null)
}

class ToBudgetComplicationService : SuspendingComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData =
        WatchComplications.toBudget(WatchStore.load(this).summary.value, openApp(this))

    override fun getPreviewData(type: ComplicationType): ComplicationData = WatchComplications.toBudget(PREVIEW, null)
}

private fun openApp(context: Context): PendingIntent =
    PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

private val PREVIEW =
    WatchSummary(
        version = WatchSummary.VERSION,
        state = WatchState.VISIBLE,
        budgetName = "Budget",
        sentAtEpochMs = 0,
        toBudgetMinor = 12_000,
        leftToSpendMinor = 42_000,
        categories = listOf(WatchCategory("preview", "Groceries", budgetedMinor = 50_000, spentMinor = 20_000, leftMinor = 30_000)),
    )
