@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.accounts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.tandemforactual.api.client.TandemApiException
import app.tandemforactual.ui.core.importing.statementLedgerRange
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.statementLedger
import app.tandemforactual.ui.core.session.userFacingMessage
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import kotlinx.coroutines.CancellationException

/** Starts a dropped or shared statement, re-parses it when its options change, then reads the account to match it against. */
@Composable
internal fun StatementImportEffects(
    statementImport: StatementImportState,
    state: AppState,
    mobileSession: TandemMobileSession,
    liveAccount: LiveAccountSummary?,
) {
    val pendingStatement = state.pendingStatementFile
    LaunchedEffect(pendingStatement, liveAccount?.id) {
        if (pendingStatement != null && liveAccount != null && !liveAccount.closed && !state.readOnlyMode) {
            state.pendingStatementFile = null
            mobileSession.feedback.clearBudgetActionMessage()
            statementImport.onFilePicked(pendingStatement, state, liveAccount)
        }
    }
    val currencyFractionDigits = mobileSession.budgetDisplaySettings?.currencyFractionDigits
    val numberFormat = mobileSession.budgetDisplaySettings?.numberFormat
    LaunchedEffect(
        statementImport.pickedFile,
        statementImport.dateOrder,
        currencyFractionDigits,
        numberFormat,
        statementImport.csvLayout,
        statementImport.csvMapping,
    ) {
        parseStatement(statementImport, state, liveAccount, currencyFractionDigits, numberFormat)
    }
    LaunchedEffect(statementImport.parseResult, statementImport.ledger) { statementImport.actions = emptyMap() }
    LaunchedEffect(
        statementImport.parseResult,
        statementImport.ledgerRetry,
        state.serverUrl,
        state.selectedBudgetId,
        state.encryptedPassword,
        liveAccount?.id,
    ) {
        loadStatementLedger(statementImport, state, mobileSession, liveAccount)
    }
}

private suspend fun loadStatementLedger(
    statementImport: StatementImportState,
    state: AppState,
    mobileSession: TandemMobileSession,
    liveAccount: LiveAccountSummary?,
) {
    val range = statementImport.parseResult?.transactions?.let(::statementLedgerRange)
    statementImport.ledger = null
    statementImport.ledgerError = null
    if (range == null || liveAccount == null) {
        statementImport.ledgerLoading = false
        return
    }
    statementImport.ledgerLoading = true
    try {
        statementImport.ledger =
            mobileSession.budgetEdits.statementLedger(
                state.selectedBudgetId,
                liveAccount.id,
                range.first,
                range.second,
                state.encryptedPassword.takeIf(String::isNotBlank),
            )
    } catch (error: CancellationException) {
        throw error
    } catch (error: TandemApiException) {
        statementImport.ledgerError = error.userFacingMessage(STATEMENT_IMPORT_CONNECTION_REQUIRED)
    } catch (_: Exception) {
        statementImport.ledgerError = STATEMENT_IMPORT_CONNECTION_REQUIRED
    } finally {
        statementImport.ledgerLoading = false
    }
}
