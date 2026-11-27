package app.tandemforactual.wear.tiles

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import app.tandemforactual.watch.WatchSummary
import app.tandemforactual.wear.data.WatchStore
import com.google.common.util.concurrent.ListenableFuture

private const val RESOURCES_VERSION = "1"

/** Draws a tile from the summary already on the watch; the phone asks for a redraw whenever it sends a new one. */
abstract class SummaryTileService : TileService() {
    protected abstract fun layout(
        request: RequestBuilders.TileRequest,
        summary: WatchSummary?,
    ): LayoutElement

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val summary = WatchStore.load(this).summary.value
        val tile =
            TileBuilders.Tile
                .Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout(requestParams, summary)))
                .build()
        return done(tile)
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        done(
            ResourceBuilders.Resources
                .Builder()
                .setVersion(RESOURCES_VERSION)
                .build(),
        )

    private fun <T : Any> done(value: T): ListenableFuture<T> =
        CallbackToFutureAdapter.getFuture { completer ->
            completer.set(value)
            "tile"
        }
}

class LeftToSpendTileService : SummaryTileService() {
    override fun layout(
        request: RequestBuilders.TileRequest,
        summary: WatchSummary?,
    ): LayoutElement = leftToSpendLayout(this, request.deviceConfiguration, summary)
}

class NextBillTileService : SummaryTileService() {
    override fun layout(
        request: RequestBuilders.TileRequest,
        summary: WatchSummary?,
    ): LayoutElement = nextBillLayout(this, request.deviceConfiguration, summary)
}
