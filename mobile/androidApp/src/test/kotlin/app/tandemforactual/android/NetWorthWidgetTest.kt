package app.tandemforactual.android

import app.tandemforactual.data.store.NetWorthWidgetSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetWorthWidgetTest {
    @Test
    fun theDetailLineShowsWhatAccountsHoldAndOwe() {
        val summary = NetWorthWidgetSummary("Home", 210_000, 250_000, 40_000, amountsHidden = false, displaySettings = null)
        val detail = netWorthDetail(summary)
        assertTrue(detail, detail.startsWith("Assets ") && "2,500" in detail && " · Debts " in detail && "400" in detail)
        assertEquals("Assets — · Debts —", netWorthDetail(summary.copy(assetsMinor = null, debtsMinor = null)))
    }
}
