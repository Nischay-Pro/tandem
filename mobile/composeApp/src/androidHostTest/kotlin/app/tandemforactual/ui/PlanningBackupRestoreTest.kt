package app.tandemforactual.ui

import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.data.planning.AccountCurrency
import app.tandemforactual.data.planning.ExchangeRate
import app.tandemforactual.data.planning.JurisdictionPeriod
import app.tandemforactual.data.planning.PeriodKind
import app.tandemforactual.data.regions.Region
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.feature.export.BackupContents
import app.tandemforactual.ui.feature.export.BackupFlow
import app.tandemforactual.ui.feature.export.ExportRange
import app.tandemforactual.ui.feature.export.exportFileName
import app.tandemforactual.ui.feature.export.exportRangeDates
import app.tandemforactual.ui.feature.export.readPlanningBackup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlanningBackupRestoreTest {
    @Test
    fun restoringNothingIsAnErrorAndOnlyHappensAfterConfirming(): Unit =
        runBlocking {
            val session = TandemMobileSession(TandemApiClient(), null, { 100L })
            val state = AppState().apply { selectedBudgetId = "b1" }
            val reports = mutableListOf<Pair<String, InlineMessageKind>>()
            coroutineScope {
                val flow = BackupFlow(state, session, this, { _, _, _ -> }, { reports += it })
                flow.startRestore()
                flow.onPicked(
                    """{"format":"tandem-planning-backup","version":1,"records":[{"id":"g1","type":"planning.savings_goal","body":{}}]}""",
                )
                assertNotNull(flow.pendingRestore)
                assertTrue(reports.isEmpty())
                flow.confirmRestore()
                flow.onPicked("""{"format":"tandem-planning-backup","version":1,"records":[]}""")
                assertNull(flow.pendingRestore)
            }
            assertEquals(listOf(InlineMessageKind.ERROR, InlineMessageKind.ERROR), reports.map { it.second })
        }

    @Test
    fun backupsKeepCurrenciesRatesAndResidencePeriodsThroughARestore(): Unit =
        runBlocking {
            val source = newPlanningTestDatabase()
            val target = newPlanningTestDatabase()
            try {
                val period = JurisdictionPeriod("period-1", PeriodKind.RESIDENCE, Region.CA, "2024-01")
                val first = TandemMobileSession(TandemApiClient(), source, { 100L })
                first.planning.saveAccountCurrency("b1", "acct-1", "EUR")
                first.planning.saveExchangeRate("b1", ExchangeRate("EUR", "USD", "1.08", "2026-11-01"))
                first.planning.saveJurisdictionPeriods("b1", listOf(period))
                val state =
                    AppState().apply {
                        selectedBudgetId = "b1"
                        selectedBudget = "Home"
                    }
                var file = ""
                coroutineScope {
                    val flow = BackupFlow(state, first, this, { _, _, contents -> file = contents }, {})
                    flow.startBackup()
                    flow.submit("passphrase1")
                }

                val second = TandemMobileSession(TandemApiClient(), target, { 100L })
                val reports = mutableListOf<Pair<String, InlineMessageKind>>()
                coroutineScope {
                    val flow = BackupFlow(state, second, this, { _, _, _ -> }, { reports += it })
                    flow.startRestore()
                    flow.onPicked(file)
                    flow.submit("passphrase1")
                    val pending = assertNotNull(flow.pendingRestore)
                    assertEquals("b1", pending.budgetId)
                    assertTrue(flow.restoreMessage(pending).contains("“Home”"))
                    flow.confirmRestore()
                }

                assertEquals(InlineMessageKind.SUCCESS, reports.single().second)
                assertEquals(
                    listOf(AccountCurrency.TYPE, ExchangeRate.TYPE, JurisdictionPeriod.TYPE),
                    second.planning.backupRecords("b1")?.map { it.second },
                )
                assertEquals(
                    period.toJson(),
                    second.planning
                        .backupRecords("b1")
                        ?.last()
                        ?.third,
                )
            } finally {
                source.close()
                target.close()
            }
        }

    @Test
    fun confirmationNamesTheBudgetTheBackupCameFrom() {
        val state =
            AppState().apply {
                selectedBudgetId = "b1"
                selectedBudget = "Home"
            }
        val flow =
            BackupFlow(
                state,
                TandemMobileSession(TandemApiClient(), null, { 100L }),
                CoroutineScope(Dispatchers.Unconfined),
                { _, _, _ -> },
                {},
            )
        val backup =
            assertNotNull(
                readPlanningBackup(
                    """{"format":"tandem-planning-backup","version":1,"budgetId":"b2","budgetName":"Cabin",""" +
                        """"createdOn":"2026-11-20","records":[{"id":"r","type":"profile.jurisdiction_period","body":{}}]}""",
                ),
            )
        val message = flow.restoreMessage(backup)
        assertTrue(message.contains("“Cabin”, not this one"))
        assertTrue(message.contains("into “Home”"))
        assertTrue(message.contains("every device that shares this budget"))
        val old = BackupContents.Records(backup.records)
        assertTrue(!flow.restoreMessage(old).contains("not this one"))
    }

    @Test
    fun thisMonthExportsTodaysMonth() {
        assertEquals("2026-11-01" to "2026-11-30", exportRangeDates(ExportRange.MONTH, "2026-11-26"))
        assertEquals("2025-01-01" to "2025-12-31", exportRangeDates(ExportRange.LAST_YEAR, "2026-11-26"))
        assertEquals("home-2026-11.csv", exportFileName("Home", ExportRange.MONTH, "2026-11-26"))
        assertEquals("budget-all.csv", exportFileName("", ExportRange.ALL, "2026-11-26"))
    }
}
