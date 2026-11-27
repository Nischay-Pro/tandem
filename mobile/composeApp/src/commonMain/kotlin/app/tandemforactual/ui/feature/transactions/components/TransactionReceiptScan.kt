@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.transactions.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import app.tandemforactual.ui.app.HeaderMenuAction
import app.tandemforactual.ui.app.TandemHeaderMenu
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.feedback.AnimatedReveal
import app.tandemforactual.ui.design.feedback.FeedbackMessageOwner
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.tandemPalette
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

/** Scans a receipt into the new transaction, also when one was asked for, shared or dropped before the form opened. */
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
    LaunchedEffect(state.pendingReceiptPhoto) {
        val photo = state.pendingReceiptPhoto ?: return@LaunchedEffect
        state.pendingReceiptPhoto = null
        delay(TandemMotion.ROUTE_MILLIS.toLong())
        onScanRequested()
        scan.receiptScanner.readPhoto(photo)
    }
    return scan
}

/** The "Scan receipt" button with its camera or library menu and the scan result message. */
@Composable
internal fun TransactionReceiptScanButton(
    scan: TransactionReceiptScanState,
    onBeforeScan: () -> Unit,
) {
    val palette = tandemPalette
    Box {
        TandemIconButton(
            Lucide.Scan,
            contentDescription = if (scan.scanningReceipt) "Reading receipt" else "Scan receipt",
            sizeDp = 48,
            enabled = !scan.scanningReceipt,
            tint = if (scan.scanningReceipt) palette.muted else palette.text,
            containerColor = palette.surface,
            borderColor = palette.border,
            shape = RoundedCornerShape(TandemShapes.medium),
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
}

/** What the last receipt scan found, or why it couldn't read the receipt. */
@Composable
internal fun TransactionReceiptScanMessage(scan: TransactionReceiptScanState) {
    AnimatedReveal(visible = scan.receiptMessage != null) {
        scan.receiptMessage?.let { (text, kind) ->
            InlineMessage(text, kind, owner = FeedbackMessageOwner.Action("transaction.receipt"))
        }
    }
}
