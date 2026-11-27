package app.tandemforactual.watch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchCodecTest {
    private fun summary(
        nameLength: Int = 12,
        state: WatchState = WatchState.VISIBLE,
    ): WatchSummary {
        val name = "N".repeat(nameLength)
        return WatchSummary(
            version = WatchSummary.VERSION,
            state = state,
            budgetName = "Household $name",
            sentAtEpochMs = 1_800_000_000_000,
            syncedAtEpochMs = 1_799_999_000_000,
            toBudgetMinor = 12_345,
            leftToSpendMinor = 98_765,
            categories =
                (1..8).map { index ->
                    WatchCategory(
                        id = "category-$index-0000-0000-0000-000000000000",
                        name = "Category $index $name",
                        budgetedMinor = 50_000,
                        spentMinor = 20_000L * index,
                        leftMinor = 50_000 - 20_000L * index,
                        recent = (1..5).map { WatchTransaction("Payee $it $name", -1_234L * it, "2026-11-2$it") },
                    )
                },
            quickCategories = (1..8).map { WatchChoice("recent-$it-0000-0000-0000-000000000000", "Recent $it $name") },
            recentPayees = (1..8).map { "Payee $it $name" },
            accountName = "Everyday $name",
            bills = (1..5).map { WatchBill("Bill $it $name", "2026-12-0$it", -9_900L * it) },
        )
    }

    @Test
    fun aTypicalSummaryKeepsEverythingWithinTheLimit() {
        val typical = summary()
        val bytes = WatchCodec.encode(typical)

        assertTrue(bytes.size <= WatchCodec.MAX_SUMMARY_BYTES, "${bytes.size} bytes")
        val decoded = assertNotNull(WatchCodec.decodeSummary(bytes))
        assertEquals(WatchSummary.MAX_CATEGORIES, decoded.categories.size)
        assertEquals(WatchSummary.MAX_BILLS, decoded.bills.size)
        assertEquals(typical.leftToSpendMinor, decoded.leftToSpendMinor)
        assertEquals("Category 1 ${"N".repeat(12)}", decoded.categories.first().name)
    }

    @Test
    fun longNamesAreTrimmedUntilTheSummaryFits() {
        val bytes = WatchCodec.encode(summary(nameLength = 400))

        assertTrue(bytes.size <= WatchCodec.MAX_SUMMARY_BYTES, "${bytes.size} bytes")
        val decoded = assertNotNull(WatchCodec.decodeSummary(bytes))
        assertEquals(98_765, decoded.leftToSpendMinor)
        assertTrue(decoded.categories.all { it.recent.isEmpty() })
    }

    @Test
    fun aSummaryFromANewerPhoneIsIgnoredButExtraFieldsAreNot() {
        val newer = """{"v":2,"st":"ok","bn":"Home","at":1}""".encodeToByteArray()
        val extra = """{"v":1,"st":"locked","bn":"Home","at":1,"later":true}""".encodeToByteArray()

        assertNull(WatchCodec.decodeSummary(newer))
        assertEquals(WatchState.LOCKED, WatchCodec.decodeSummary(extra)?.state)
        assertNull(WatchCodec.decodeSummary("not json".encodeToByteArray()))
    }

    @Test
    fun quickAddsAndResultsRoundTrip() {
        val entry = WatchQuickAdd("entry-1", -1_250, "category-1", "Corner Café", 42)
        val result = WatchQuickAddResult("entry-1", WatchQuickAddStatus.LOCKED, "Open Tandem to unlock")

        assertEquals(entry, WatchCodec.decodeQuickAdd(WatchCodec.encodeQuickAdd(entry)))
        assertEquals(result, WatchCodec.decodeResult(WatchCodec.encodeResult(result)))
    }
}
