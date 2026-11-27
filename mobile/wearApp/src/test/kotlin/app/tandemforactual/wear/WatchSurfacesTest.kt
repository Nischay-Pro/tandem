package app.tandemforactual.wear

import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import app.tandemforactual.watch.HIDDEN_AMOUNT
import app.tandemforactual.watch.WatchBill
import app.tandemforactual.watch.WatchCategory
import app.tandemforactual.watch.WatchState
import app.tandemforactual.watch.WatchSummary
import app.tandemforactual.wear.complications.WatchComplications
import app.tandemforactual.wear.data.Headline
import app.tandemforactual.wear.data.WatchText
import app.tandemforactual.wear.tiles.leftToSpendLayout
import app.tandemforactual.wear.tiles.nextBillLayout
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class WatchSurfacesTest {
    private val context = RuntimeEnvironment.getApplication()
    private val device =
        DeviceParametersBuilders.DeviceParameters
            .Builder()
            .setScreenWidthDp(192)
            .setScreenHeightDp(192)
            .setScreenDensity(2f)
            .setScreenShape(DeviceParametersBuilders.SCREEN_SHAPE_ROUND)
            .setDevicePlatform(DeviceParametersBuilders.DEVICE_PLATFORM_WEAR_OS)
            .build()

    private fun summary(state: WatchState = WatchState.VISIBLE) =
        WatchSummary(
            version = WatchSummary.VERSION,
            state = state,
            budgetName = "Home",
            sentAtEpochMs = 0,
            toBudgetMinor = 7_500,
            leftToSpendMinor = 123_456,
            categories =
                listOf(
                    WatchCategory("food", "Groceries", budgetedMinor = 40_000, spentMinor = 30_000, leftMinor = 10_000),
                    WatchCategory("fun", "Fun", budgetedMinor = 5_000, spentMinor = 7_000, leftMinor = -2_000),
                ),
            bills = listOf(WatchBill("Internet", "2026-12-01", -6_000)),
        )

    @Test
    fun theLeftToSpendTileShowsTheAmountAndAnAddButton() {
        val texts = texts(leftToSpendLayout(context, device, summary()))

        assertTrue(texts.toString(), "$1,234.56" in texts)
        assertTrue(texts.toString(), WatchText.LEFT_TO_SPEND in texts)
        assertTrue(texts.toString(), "+ Add" in texts)
    }

    @Test
    fun theGlanceSplitsWhatIsLeftOverTheDaysLeft() {
        assertEquals("$93.62 a day · 9 days left", WatchText.perDay(summary().copy(perDayMinor = 9_362, daysLeft = 9)))
        assertEquals("$10.00 for today", WatchText.perDay(summary().copy(perDayMinor = 1_000, daysLeft = 1)))
        assertNull(WatchText.perDay(summary(WatchState.AMOUNTS_HIDDEN).copy(perDayMinor = 1_000, daysLeft = 3)))
        assertNull(WatchText.perDay(summary()))
    }

    @Test
    fun theGlanceLeadsWithTheAmountADayUnlessThePhoneSaysOtherwise() {
        val split = summary().copy(perDayMinor = 9_362, daysLeft = 9)
        assertEquals(Headline(9_362, "A day · 9 days left", "$1,234.56 left this month"), WatchText.headline(split))
        assertEquals("Left for today", WatchText.headline(split.copy(daysLeft = 1)).label)
        assertEquals(
            Headline(123_456, WatchText.LEFT_TO_SPEND, "$93.62 a day · 9 days left"),
            WatchText.headline(split.copy(perDayFirst = false)),
        )
        val hidden = summary(WatchState.AMOUNTS_HIDDEN).copy(perDayMinor = 1, daysLeft = 3)
        assertEquals(WatchText.LEFT_TO_SPEND, WatchText.headline(hidden).label)
    }

    @Test
    fun theTileAndComplicationLeadWithTheSameFigureAsTheGlance() {
        val split = summary().copy(perDayMinor = 9_362, daysLeft = 9)
        val tile = texts(leftToSpendLayout(context, device, split))
        assertTrue(tile.toString(), "A day" in tile && "$93.62" in tile)
        val monthTile = texts(leftToSpendLayout(context, device, split.copy(perDayFirst = false)))
        assertTrue(monthTile.toString(), WatchText.LEFT_TO_SPEND in monthTile && "$1,234.56" in monthTile)

        val complication = WatchComplications.leftToSpend(split, null, ComplicationType.SHORT_TEXT, null) as ShortTextComplicationData
        assertEquals("$93", text(complication))
        assertEquals("A day", shown(complication.title))
    }

    @Test
    fun theTileOffersAddOnlyOnceThePhoneAllowsIt() {
        assertFalse("+ Add" in texts(leftToSpendLayout(context, device, null)))
        assertFalse("+ Add" in texts(leftToSpendLayout(context, device, summary().copy(quickAdd = false))))
    }

    @Test
    fun tilesNeverShowHiddenOrLockedAmounts() {
        val hidden = texts(leftToSpendLayout(context, device, summary(WatchState.AMOUNTS_HIDDEN)))
        val locked = texts(leftToSpendLayout(context, device, summary(WatchState.LOCKED)))
        val bill = texts(nextBillLayout(context, device, summary(WatchState.AMOUNTS_HIDDEN)))

        assertTrue(hidden.toString(), HIDDEN_AMOUNT in hidden)
        assertFalse(hidden.toString(), hidden.any { "1,234" in it })
        assertTrue(locked.toString(), WatchText.LOCKED in locked)
        assertFalse(locked.toString(), locked.any { "1,234" in it })
        assertTrue(bill.toString(), "Internet" in bill && HIDDEN_AMOUNT in bill)
        assertTrue(texts(leftToSpendLayout(context, device, null)).contains(WatchText.NO_BUDGET))
    }

    @Test
    fun theNextBillTileNamesTheBill() {
        val texts = texts(nextBillLayout(context, device, summary()))

        assertTrue(texts.toString(), "Internet" in texts && "$60.00" in texts)
    }

    @Test
    fun complicationsFollowTheChosenCategory() {
        val whole = WatchComplications.leftToSpend(summary(), null, ComplicationType.SHORT_TEXT, null) as ShortTextComplicationData
        val overspent = WatchComplications.leftToSpend(summary(), "fun", ComplicationType.SHORT_TEXT, null) as ShortTextComplicationData
        val ranged = WatchComplications.leftToSpend(summary(), null, ComplicationType.RANGED_VALUE, null) as RangedValueComplicationData

        assertEquals("$1234", text(whole))
        assertEquals("−$20", text(overspent))
        assertEquals(10_000f, ranged.value)
        assertEquals(40_000f, ranged.max)
    }

    @Test
    fun complicationsHideAmountsTheWayTheAppDoes() {
        val hidden = WatchComplications.toBudget(summary(WatchState.AMOUNTS_HIDDEN), null) as ShortTextComplicationData
        val locked = WatchComplications.toBudget(summary(WatchState.LOCKED), null) as ShortTextComplicationData

        assertEquals(HIDDEN_AMOUNT, text(hidden))
        assertEquals("Locked", text(locked))
        assertTrue(WatchComplications.toBudget(null, null) is NoDataComplicationData)
    }

    private fun shown(text: ComplicationText?) = text?.getTextAt(context.resources, Instant.now()).toString()

    private fun text(data: ShortTextComplicationData) = data.text.getTextAt(context.resources, Instant.now()).toString()

    private fun texts(element: LayoutElement): List<String> =
        when (element) {
            is LayoutElementBuilders.Text -> listOfNotNull(element.text?.value)
            is LayoutElementBuilders.Box -> element.contents.flatMap(::texts)
            is LayoutElementBuilders.Column -> element.contents.flatMap(::texts)
            is LayoutElementBuilders.Row -> element.contents.flatMap(::texts)
            else -> emptyList()
        }
}
