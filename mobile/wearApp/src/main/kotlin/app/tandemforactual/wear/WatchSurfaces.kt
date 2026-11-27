package app.tandemforactual.wear

import android.content.ComponentName
import android.content.Context
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import app.tandemforactual.wear.complications.LeftToSpendComplicationService
import app.tandemforactual.wear.complications.NextBillComplicationService
import app.tandemforactual.wear.complications.ToBudgetComplicationService
import app.tandemforactual.wear.tiles.LeftToSpendTileService
import app.tandemforactual.wear.tiles.NextBillTileService

/** Asks the system to redraw every tile and complication after the summary changes. */
object WatchSurfaces {
    fun refresh(context: Context) {
        val appContext = context.applicationContext
        val updater = TileService.getUpdater(appContext)
        updater.requestUpdate(LeftToSpendTileService::class.java)
        updater.requestUpdate(NextBillTileService::class.java)
        listOf(
            LeftToSpendComplicationService::class.java,
            ToBudgetComplicationService::class.java,
            NextBillComplicationService::class.java,
        ).forEach {
            ComplicationDataSourceUpdateRequester.create(appContext, ComponentName(appContext, it)).requestUpdateAll()
        }
    }
}
