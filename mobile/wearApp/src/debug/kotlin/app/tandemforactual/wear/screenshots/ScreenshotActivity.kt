package app.tandemforactual.wear.screenshots

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.tandemforactual.watch.QuickAddQueue
import app.tandemforactual.watch.WatchBill
import app.tandemforactual.watch.WatchCategory
import app.tandemforactual.watch.WatchChoice
import app.tandemforactual.watch.WatchCodec
import app.tandemforactual.watch.WatchState
import app.tandemforactual.watch.WatchSummary
import app.tandemforactual.watch.WatchTransaction
import app.tandemforactual.wear.WatchSurfaces
import app.tandemforactual.wear.data.WatchStore
import app.tandemforactual.wear.ui.TandemWearApp
import kotlinx.coroutines.flow.MutableStateFlow

/** Debug builds only: the watch app with a fictional household summary, for store and README screenshots. */
class ScreenshotActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WatchStore.load(this)
        WatchStore.saveSummary(WatchCodec.encode(DEMO))
        WatchSurfaces.refresh(this)
        val startOnQuickAdd = intent?.getStringExtra(EXTRA_SCREEN) == SCREEN_ADD
        setContent {
            TandemWearApp(
                summaryFlow = MutableStateFlow(DEMO),
                queueFlow = MutableStateFlow(QuickAddQueue()),
                phoneFlow = MutableStateFlow(true),
                startOnQuickAdd = startOnQuickAdd,
                onAdd = { _, _, _ -> false },
                onRefresh = {},
                onInstallOnPhone = {},
            )
        }
    }

    private companion object {
        const val EXTRA_SCREEN = "screen"
        const val SCREEN_ADD = "add"

        val DEMO =
            WatchSummary(
                version = WatchSummary.VERSION,
                state = WatchState.VISIBLE,
                budgetName = "Demo household",
                sentAtEpochMs = 1_795_255_200_000L,
                syncedAtEpochMs = 1_795_255_200_000L,
                toBudgetMinor = 12_500,
                leftToSpendMinor = 84_265,
                categories =
                    listOf(
                        WatchCategory(
                            "groceries",
                            "Groceries",
                            budgetedMinor = 60_000,
                            spentMinor = 41_830,
                            leftMinor = 18_170,
                            recent =
                                listOf(
                                    WatchTransaction("Corner Market", -6_420, "2026-11-19"),
                                    WatchTransaction("Fresh Fields", -3_115, "2026-11-17"),
                                ),
                        ),
                        WatchCategory("dining", "Dining out", budgetedMinor = 20_000, spentMinor = 22_450, leftMinor = -2_450),
                        WatchCategory("fuel", "Fuel", budgetedMinor = 18_000, spentMinor = 9_600, leftMinor = 8_400),
                        WatchCategory("fun", "Fun money", budgetedMinor = 10_000, spentMinor = 3_500, leftMinor = 6_500),
                    ),
                quickCategories = listOf(WatchChoice("phone", "Phone"), WatchChoice("coffee", "Coffee")),
                recentPayees = listOf("Corner Market", "Bean There", "City Transit"),
                accountName = "Everyday checking",
                bills =
                    listOf(
                        WatchBill("Internet", "2026-11-24", -6_000),
                        WatchBill("Phone", "2026-11-28", -4_500),
                        WatchBill("Rent", "2026-12-01", -165_000),
                    ),
            )
    }
}
