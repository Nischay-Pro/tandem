@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.transactions.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.app.HeaderMenuAction
import app.tandemforactual.ui.app.TandemHeaderMenu
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.feedback.AnimatedReveal
import app.tandemforactual.ui.design.feedback.FeedbackMessageOwner
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.feature.transactions.receipt.ReceiptScanner
import app.tandemforactual.ui.feature.transactions.receipt.fillNewTransaction
import app.tandemforactual.ui.feature.transactions.receipt.parseReceiptText
import app.tandemforactual.ui.feature.transactions.receipt.rememberReceiptScanner
import com.composables.icons.lucide.Camera
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Scan
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A receipt being read into the new transaction: its progress, preview, menu and result message. */
internal class TransactionReceiptScanState {
    var scanningReceipt by mutableStateOf(false)
    var receiptPreview by mutableStateOf<ImageBitmap?>(null)
    var showReceiptMenu by mutableStateOf(false)
    var receiptMessage by mutableStateOf<Pair<String, InlineMessageKind>?>(null)
    lateinit var receiptScanner: ReceiptScanner

    fun start() {
        if (receiptScanner.canTakePhoto) showReceiptMenu = true else receiptScanner.choosePhoto()
    }
}

/** Scans a receipt into the new transaction, also when one was asked for before the form opened. */
@Composable
internal fun rememberTransactionReceiptScan(
    state: AppState,
    mobileSession: TandemMobileSession,
    onScanRequested: () -> Unit,
): TransactionReceiptScanState {
    val scan = remember { TransactionReceiptScanState() }
    val scanScope = rememberCoroutineScope()
    scan.receiptScanner =
        rememberReceiptScanner(
            onStarted = { preview ->
                scan.scanningReceipt = true
                if (preview != null) scan.receiptPreview = preview
                scan.receiptMessage = null
            },
        ) { result ->
            val lines = result.lines
            if (lines == null) {
                scan.scanningReceipt = false
                scan.receiptMessage = (result.errorMessage ?: "Couldn’t read this receipt.") to InlineMessageKind.ERROR
                return@rememberReceiptScanner
            }
            val receipt = parseReceiptText(lines, platformTodayIsoDate())
            scanScope.launch {
                fillNewTransaction(state, mobileSession, receipt.amountMinor, receipt.merchant, receipt.date)
                scan.scanningReceipt = false
                scan.receiptMessage =
                    if (receipt.isEmpty) {
                        "No total found. Try a flatter, well-lit photo." to InlineMessageKind.WARNING
                    } else {
                        "Filled in from the receipt. Check the details before saving." to InlineMessageKind.SUCCESS
                    }
            }
        }
    LaunchedEffect(state.receiptScanRequested) {
        if (state.receiptScanRequested) {
            state.receiptScanRequested = false
            delay(TandemMotion.ROUTE_MILLIS.toLong())
            onScanRequested()
            scan.start()
        }
    }
    return scan
}

/** The "Scan receipt" button with its camera or library menu and the scan result message. */
@Composable
internal fun TransactionReceiptScanButton(
    scan: TransactionReceiptScanState,
    onBeforeScan: () -> Unit,
) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        TandemActionButton(
            if (scan.scanningReceipt) "Reading receipt…" else "Scan receipt",
            TandemActionButtonTone.NEUTRAL,
            Modifier.widthIn(min = 168.dp),
            icon = Lucide.Scan,
            loading = scan.scanningReceipt,
        ) {
            onBeforeScan()
            scan.start()
        }
        TandemHeaderMenu(expanded = scan.showReceiptMenu, onDismiss = { scan.showReceiptMenu = false }) {
            HeaderMenuAction(Lucide.Camera, "Take a photo") {
                scan.showReceiptMenu = false
                scan.receiptScanner.takePhoto()
            }
            HeaderMenuAction(Lucide.Image, "Choose a photo") {
                scan.showReceiptMenu = false
                scan.receiptScanner.choosePhoto()
            }
        }
    }
    AnimatedReveal(visible = scan.receiptMessage != null) {
        scan.receiptMessage?.let { (text, kind) ->
            InlineMessage(text, kind, owner = FeedbackMessageOwner.Action("transaction.receipt"))
        }
    }
}
