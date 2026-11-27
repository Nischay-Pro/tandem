package app.tandemforactual.ui

import app.tandemforactual.api.client.BudgetCatalogItem
import app.tandemforactual.api.client.RestoredBudget
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.api.client.TandemApiException
import app.tandemforactual.data.planning.AccountCurrency
import app.tandemforactual.data.planning.BudgetBackupFile
import app.tandemforactual.data.planning.ExchangeRate
import app.tandemforactual.data.planning.JurisdictionPeriod
import app.tandemforactual.data.planning.OpenedBackup
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
import app.tandemforactual.ui.feature.export.RestoreTarget
import app.tandemforactual.ui.feature.export.describeBackup
import app.tandemforactual.ui.feature.export.exportFileName
import app.tandemforactual.ui.feature.export.exportRangeDates
import app.tandemforactual.ui.feature.export.fileSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
        restoreBudget: suspend (OpenedBackup.Contents, String, String?) -> RestoredBudget = { _, _, _ -> error("Not restoring a budget") },
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
        restoreBudget,
    )

    private suspend fun BackupFlow.settle() {
        while (restoring) delay(5)
    }

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
                assertTrue(flow.openedWithPassphrase)
                assertEquals(RestoreTarget.NEW_BUDGET, flow.restoreTarget)
                assertEquals(
                    "Adds “Home (restored)” to your Actual server and opens it here. “Home” stays as it is.",
                    flow.restoreMessage(pending),
                )
                flow.restoreTarget = RestoreTarget.EXTRAS_ONLY
                assertTrue(flow.restoreMessage(pending).contains("into “Home”"))
                flow.cancelRestore()
                assertNull(flow.pendingRestore)
            }
            assertTrue(reports.isEmpty())
        }

    @Test
    fun aBudgetOnlyBackupOffersANewBudgetAndAFileWithNothingToRestoreSaysSo(): Unit =
        runBlocking {
            val reports = mutableListOf<Pair<String, InlineMessageKind>>()
            coroutineScope {
                val flow = flow(TandemMobileSession(TandemApiClient(), null, { 100L }), this, reports)
                flow.onPicked(BudgetBackupFile.build(TEST_DATABASE, """{"id":"b1"}""", null, null))
                assertEquals(RestoreTarget.NEW_BUDGET, flow.restoreTarget)
                assertFalse(flow.openedWithPassphrase)
                assertEquals("Budget (restored)", flow.restoredName(assertNotNull(flow.pendingRestore)))
                flow.cancelRestore()
                flow.onPicked("""{"format":"tandem-planning-backup","version":1,"records":[]}""".encodeToByteArray())
                assertNull(flow.pendingRestore)
                flow.onPicked("not a backup".encodeToByteArray())
                flow.onPicked(null)
            }
            assertEquals(listOf(InlineMessageKind.INFO, InlineMessageKind.ERROR, InlineMessageKind.ERROR), reports.map { it.second })
            assertTrue(reports.first().first.contains("nothing Tandem can restore"))
        }

    @Test
    fun restoringAsANewBudgetUploadsItBringsTheExtrasAlongAndOpensIt(): Unit =
        runBlocking {
            val database = newPlanningTestDatabase()
            try {
                val reports = mutableListOf<Pair<String, InlineMessageKind>>()
                val uploads = mutableListOf<Pair<String, String?>>()
                val opened = mutableListOf<BudgetCatalogItem>()
                val session = TandemMobileSession(TandemApiClient(), database, { 100L })
                val flow =
                    flow(session, this, reports) { backup, name, password ->
                        assertTrue(backup.database!!.contentEquals(TEST_DATABASE))
                        uploads += name to password
                        RestoredBudget("b9", name, encrypted = false)
                    }
                flow.openBudget = { opened += it }
                flow.onPicked(BudgetBackupFile.build(TEST_DATABASE, """{"id":"b1"}""", extras, null))
                assertTrue(uploads.isEmpty())
                flow.confirmRestore()
                flow.settle()

                assertEquals(listOf<Pair<String, String?>>("Home (restored)" to null), uploads)
                assertEquals(listOf(BudgetCatalogItem("b9", "Home (restored)", false)), opened)
                assertEquals("Restored “Home (restored)” as a new budget." to InlineMessageKind.SUCCESS, reports.single())
                assertEquals(listOf("g1"), session.planning.backupRecords("b9")?.map { it.first })
                assertTrue(session.planning.backupRecords("b1").isNullOrEmpty())
                assertNull(flow.pendingRestore)
            } finally {
                database.close()
            }
        }

    @Test
    fun anEncryptedBudgetAsksForItsPasswordAndAFailedRestoreChangesNothing(): Unit =
        runBlocking {
            val reports = mutableListOf<Pair<String, InlineMessageKind>>()
            val opened = mutableListOf<BudgetCatalogItem>()
            val flow =
                flow(TandemMobileSession(TandemApiClient(), null, { 100L }), this, reports) { _, name, password ->
                    when (password) {
                        null -> throw TandemApiException("budget_password_required")
                        "nope" -> throw TandemApiException("budget_password_incorrect")
                        "offline" -> throw IllegalStateException("unreachable")
                        "expired" -> throw TandemApiException("sign_in_expired")
                        "damaged" -> throw TandemApiException("backup_unreadable")
                        else -> RestoredBudget("b9", name, encrypted = true)
                    }
                }
            flow.openBudget = { opened += it }
            flow.onPicked(BudgetBackupFile.build(TEST_DATABASE, """{"id":"b1","budgetName":"Locked"}""", null, null))
            flow.confirmRestore()
            flow.settle()
            assertTrue(flow.needsBudgetPassword)
            assertEquals("This budget is end-to-end encrypted. Enter the password you set for it in Actual.", flow.restoreError)
            val failures =
                listOf("nope", "offline", "expired", "damaged").map {
                    flow.confirmRestore(it)
                    flow.settle()
                    flow.restoreError
                }
            assertEquals(
                listOf(
                    "That password doesn’t open this budget.",
                    "Couldn’t restore. Nothing changed. Check the connection and try again.",
                    "Sign in to your Actual server, then try again.",
                    "The budget in this backup can’t be read.",
                ),
                failures,
            )
            assertNotNull(flow.pendingRestore)
            assertTrue(opened.isEmpty() && reports.isEmpty())

            flow.confirmRestore("secret")
            flow.settle()
            assertEquals(listOf(BudgetCatalogItem("b9", "Locked (restored)", true)), opened)
            assertFalse(flow.needsBudgetPassword)
            assertNull(flow.pendingRestore)
            flow.confirmRestore()
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
                    flow.restoreTarget = RestoreTarget.EXTRAS_ONLY
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
                val pending = assertNotNull(flow.pendingRestore)
                assertEquals(RestoreTarget.EXTRAS_ONLY, flow.restoreTarget)
                assertEquals("Cabin (restored)", flow.restoredName(pending))
                assertTrue(flow.restoreMessage(pending).contains("every device that shares this budget"))
                assertTrue(describeBackup(pending, "b1").contains("from “Cabin”"))
            }
        }

    @Test
    fun fileSizesReadInKilobytesThenMegabytes() {
        assertEquals("1 KB", fileSize(10))
        assertEquals("348 KB", fileSize(356_352))
        assertEquals("2.5 MB", fileSize(2_621_440))
    }

    @Test
    fun thisMonthExportsTodaysMonth() {
        assertEquals("2026-11-01" to "2026-11-30", exportRangeDates(ExportRange.MONTH, "2026-11-26"))
        assertEquals("2025-01-01" to "2025-12-31", exportRangeDates(ExportRange.LAST_YEAR, "2026-11-26"))
        assertEquals("home-2026-11.csv", exportFileName("Home", ExportRange.MONTH, "2026-11-26"))
        assertEquals("budget-all.csv", exportFileName("", ExportRange.ALL, "2026-11-26"))
    }
}
