package app.tandemforactual.ui.core.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.api.client.TandemApiException

internal fun signOutRevocationNotice(httpStatus: Int?): String =
    if (httpStatus == null) {
        "The server still has this device linked. Sign in again to this server and unlink it in Settings."
    } else {
        "Couldn’t confirm that the server removed this device. Sign in again to this server and check Settings."
    }

internal fun TandemApiException.userFacingMessage(
    fallback: String,
    context: ApiFailureContext = ApiFailureContext.GENERAL,
): String = apiFailureMessage(errorCode, httpStatus, context, fallback)

internal fun budgetApiErrorMessage(
    errorCode: String,
    httpStatus: Int?,
    fallback: String,
): String = apiFailureMessage(errorCode, httpStatus, ApiFailureContext.GENERAL, fallback)
