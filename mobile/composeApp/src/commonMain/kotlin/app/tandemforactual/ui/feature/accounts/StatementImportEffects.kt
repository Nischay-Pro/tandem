@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.accounts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.tandemforactual.api.client.TandemApiException
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.previewStatementImportTransactions
import app.tandemforactual.ui.core.session.userFacingMessage
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import kotlinx.coroutines.CancellationException

/** Starts a dropped or shared statement, re-parses it when its options change, then loads the server import preview. */
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
    LaunchedEffect(
        statementImport.parseResult,
        statementImport.importPreviewRetry,
        state.serverUrl,
        state.selectedBudgetId,
        state.encryptedPassword,
        liveAccount?.id,
    ) {
        loadStatementImportPreview(statementImport, state, mobileSession, liveAccount)
    }
}

private suspend fun loadStatementImportPreview(
    statementImport: StatementImportState,
    state: AppState,
    mobileSession: TandemMobileSession,
    liveAccount: LiveAccountSummary?,
) {
    val parsed = statementImport.parseResult
    if (parsed == null || liveAccount == null) {
        statementImport.importPreview = null
        statementImport.importPreviewError = null
        statementImport.importPreviewLoading = false
        return
    }
    statementImport.importPreview = null
    statementImport.importPreviewError = null
    statementImport.importPreviewLoading = true
    try {
        statementImport.importPreview =
            mobileSession.budgetEdits.previewStatementImportTransactions(
                state.selectedBudgetId,
                liveAccount.id,
                parsed.transactions.flatMap { listOfNotNull(it.importedId, it.bankId) }.distinct(),
                state.encryptedPassword.takeIf(String::isNotBlank),
            )
    } catch (error: CancellationException) {
        throw error
    } catch (error: TandemApiException) {
        statementImport.importPreviewError = error.userFacingMessage(STATEMENT_IMPORT_CONNECTION_REQUIRED)
    } catch (_: Exception) {
        statementImport.importPreviewError = STATEMENT_IMPORT_CONNECTION_REQUIRED
    } finally {
        statementImport.importPreviewLoading = false
    }
}
