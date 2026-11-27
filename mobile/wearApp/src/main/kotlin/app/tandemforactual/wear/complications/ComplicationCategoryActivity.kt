package app.tandemforactual.wear.complications

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import app.tandemforactual.watch.WatchChoice
import app.tandemforactual.wear.data.WatchStore
import app.tandemforactual.wear.ui.CategoryChoiceScreen
import app.tandemforactual.wear.ui.TandemWearTheme

/** Lets the watch face's editor choose which category a Left to spend complication follows. */
class ComplicationCategoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val complicationId = intent.getIntExtra(ComplicationDataSourceService.EXTRA_CONFIG_COMPLICATION_ID, -1)
        val store = WatchStore.load(this)
        val categories =
            store.summary.value
                ?.categories
                .orEmpty()
        setContent {
            TandemWearTheme {
                AppScaffold {
                    CategoryChoiceScreen(categories.map { WatchChoice(it.id, it.name) }, "Whole budget") { choice ->
                        store.setComplicationCategory(complicationId, choice?.id)
                        ComplicationDataSourceUpdateRequester
                            .create(
                                this@ComplicationCategoryActivity,
                                ComponentName(this@ComplicationCategoryActivity, LeftToSpendComplicationService::class.java),
                            ).requestUpdate(complicationId)
                        setResult(RESULT_OK)
                        finish()
                    }
                }
            }
        }
    }
}
