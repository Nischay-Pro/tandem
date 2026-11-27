package app.tandemforactual.ui

import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.data.planning.AccountCurrency
import app.tandemforactual.data.planning.BudgetBackupFile
import app.tandemforactual.data.planning.ExchangeRate
import app.tandemforactual.data.planning.JurisdictionPeriod
import app.tandemforactual.data.planning.PeriodKind
import app.tandemforactual.data.regions.Region
import app.tandemforactual.ui.core.session.BackupSecrets
import app.tandemforactual.ui.core.session.PlanningBackupSource
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.planningBackupJson
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.feature.export.BackupEncryption
import app.tandemforactual.ui.feature.export.BackupFlow
import app.tandemforactual.ui.feature.export.ExportRange
import app.tandemforactual.ui.feature.export.exportFileName
import app.tandemforactual.ui.feature.export.exportRangeDates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlanningBackupRestoreTest {
    private val home =
        AppState().apply {
            selectedBudgetId = "b1"
            selectedBudget = "Home"
        }

    private fun flow(
        session: TandemMobileSession,
        scope: CoroutineScope,
        reports: MutableList<Pair<String, InlineMessageKind>>,
    ) = BackupFlow(
        home,
        session,
        scope,
        BackupEncryption(
            BackupSecrets(MemoryCredentialStore(), MemoryCredentialStore()),
            false,
        ),
        { _, _, _ -> },
        {
            reports += it
        },
    )

    private val extras =
        """{"format":"tandem-planning-backup","version":1,"budgetId":"b1","budgetName":"Home","createdOn":"2026-11-20",""" +
            """"records":[{"id":"g1","type":"planning.savings_goal","body":{}}]}"""

    @Test
    fun anEncryptedBackupOpensWithItsPassphraseAndSaysWhatItHolds(): Unit =
        runBlocking {
            val reports = mutableListOf<Pair<String, InlineMessageKind>>()
            coroutineScope {
                val flow = flow(TandemMobileSession(TandemApiClient(), null, { 100L }), this, reports)
                flow.onPicked(BudgetBackupFile.build(TEST_DATABASE, """{"id":"b1"}""", extras, "correct horse"))
                assertTrue(flow.askingPassphrase)
                flow.submit("wrong horse")
                assertEquals("That passphrase doesn’t open this backup.", flow.error)
                flow.submit("correct horse")
                assertFalse(flow.askingPassphrase)
                val pending = assertNotNull(flow.pendingRestore)
                val message = flow.restoreMessage(pending)
                assertTrue(message.startsWith("This backup opens correctly. It holds your Actual budget and Tandem extras (1 item)"))
                assertTrue(message.contains("into “Home”"))
                assertTrue(message.contains("Tandem can’t restore the Actual budget itself yet."))
                flow.cancelRestore()
            }
            assertTrue(reports.isEmpty())
        }

    @Test
    fun aBackupWithOnlyTheBudgetSaysSoInsteadOfRestoring(): Unit =
        runBlocking {
            val reports = mutableListOf<Pair<String, InlineMessageKind>>()
            coroutineScope {
                val flow = flow(TandemMobileSession(TandemApiClient(), null, { 100L }), this, reports)
                flow.onPicked(BudgetBackupFile.build(TEST_DATABASE, """{"id":"b1"}""", null, null))
                assertNull(flow.pendingRestore)
                flow.onPicked("not a backup".encodeToByteArray())
                flow.onPicked(null)
            }
            assertEquals(listOf(InlineMessageKind.INFO, InlineMessageKind.ERROR, InlineMessageKind.ERROR), reports.map { it.second })
            assertTrue(reports.first().first.contains("holds your Actual budget"))
        }

    @Test
    fun restoringOnlyHappensAfterConfirmingAndNothingRestoredIsAnError(): Unit =
        runBlocking {
            val reports = mutableListOf<Pair<String, InlineMessageKind>>()
            coroutineScope {
                val flow = flow(TandemMobileSession(TandemApiClient(), null, { 100L }), this, reports)
                flow.onPicked(extras.encodeToByteArray())
                assertNotNull(flow.pendingRestore)
                assertTrue(reports.isEmpty())
                flow.confirmRestore()
            }
            assertEquals(listOf(InlineMessageKind.ERROR), reports.map { it.second })
        }

    @Test
    fun extrasKeepCurrenciesRatesAndResidencePeriodsThroughARestore(): Unit =
        runBlocking {
            val source = newPlanningTestDatabase()
            val target = newPlanningTestDatabase()
            try {
                val period = JurisdictionPeriod("period-1", PeriodKind.RESIDENCE, Region.CA, "2024-01")
                val first = TandemMobileSession(TandemApiClient(), source, { 100L })
                first.planning.saveAccountCurrency("b1", "acct-1", "EUR")
                first.planning.saveExchangeRate("b1", ExchangeRate("EUR", "USD", "1.08", "2026-11-01"))
                first.planning.saveJurisdictionPeriods("b1", listOf(period))
                val json = planningBackupJson(first.planning.backupRecords("b1")!!, PlanningBackupSource("b1", "Home", "2026-11-20"))
                val file = BudgetBackupFile.build(TEST_DATABASE, """{"id":"b1"}""", json, null)

                val second = TandemMobileSession(TandemApiClient(), target, { 100L })
                val reports = mutableListOf<Pair<String, InlineMessageKind>>()
                coroutineScope {
                    val flow = flow(second, this, reports)
                    flow.onPicked(file)
                    flow.confirmRestore()
                }

                assertEquals(InlineMessageKind.SUCCESS, reports.single().second)
                assertEquals("Restored 3 Tandem extras.", reports.single().first)
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
    fun aBackupFromAnotherBudgetNamesIt(): Unit =
        runBlocking {
            coroutineScope {
                val flow = flow(TandemMobileSession(TandemApiClient(), null, { 100L }), this, mutableListOf())
                flow.onPicked(extras.replace("\"b1\"", "\"b2\"").replace("\"Home\"", "\"Cabin\"").encodeToByteArray())
                val message = flow.restoreMessage(assertNotNull(flow.pendingRestore))
                assertTrue(message.contains("from “Cabin”"))
                assertTrue(message.contains("every device that shares this budget"))
                assertFalse(message.contains("Actual budget itself"))
            }
        }

    @Test
    fun thisMonthExportsTodaysMonth() {
        assertEquals("2026-11-01" to "2026-11-30", exportRangeDates(ExportRange.MONTH, "2026-11-26"))
        assertEquals("2025-01-01" to "2025-12-31", exportRangeDates(ExportRange.LAST_YEAR, "2026-11-26"))
        assertEquals("home-2026-11.csv", exportFileName("Home", ExportRange.MONTH, "2026-11-26"))
        assertEquals("budget-all.csv", exportFileName("", ExportRange.ALL, "2026-11-26"))
    }
}
