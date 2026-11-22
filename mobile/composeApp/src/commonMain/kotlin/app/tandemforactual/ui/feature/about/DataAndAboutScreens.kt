@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.format.formatInteger
import app.tandemforactual.ui.core.format.platformAppVersionName
import app.tandemforactual.ui.core.session.LocalCrashReports
import app.tandemforactual.ui.core.session.LocalShareText
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.MerchantIconCatalog
import app.tandemforactual.ui.core.state.SUPPORTED_ACTUAL_SERVER_VERSION
import app.tandemforactual.ui.core.state.decodeCachedBudgetCatalog
import app.tandemforactual.ui.core.state.encodeCachedBudgetCatalog
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.AnimatedReveal
import app.tandemforactual.ui.design.feedback.Badge
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.sheets.DestructiveConfirmationDialog
import app.tandemforactual.ui.design.templates.AmountHero
import app.tandemforactual.ui.design.templates.AnimatedPreviewNote
import app.tandemforactual.ui.design.templates.Heading
import app.tandemforactual.ui.design.templates.InfoBubble
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemBrandMark
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemAlloFamily
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import app.tandemforactual.ui.feature.auth.LocalDeviceAuthenticator
import app.tandemforactual.ui.feature.auth.LocalEncryptedBudgetVault
import app.tandemforactual.ui.feature.auth.budgetPasswordSecretId
import com.composables.icons.lucide.CloudAlert
import com.composables.icons.lucide.CloudSync
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Package
import com.composables.icons.lucide.Share2
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.Trash2
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun LocalDataScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
) {
    val scope = rememberCoroutineScope()
    val vault = LocalEncryptedBudgetVault.current
    val authenticator = LocalDeviceAuthenticator.current
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    var removeError by remember { mutableStateOf<String?>(null) }
    val hasSelectedBudget = state.selectedBudgetId.isNotBlank()
    val recordCount =
        mobileSession.cachedTransactionCount +
            mobileSession.accountRecords.size +
            mobileSession.payeeRecords.size +
            mobileSession.categoryRecords.size +
            mobileSession.categoryGroupRecords.size +
            mobileSession.budgetMonthIds.size
    val queuedCount = mobileSession.pendingWrites.count { it.status == "queued" || it.status == "sending" }
    val conflictCount = mobileSession.pendingWrites.count { it.status == "conflict" }
    val attentionCount = mobileSession.pendingWrites.count { it.status == "uncertain" || it.status == "rejected" }
    val syncStatus =
        when {
            !hasSelectedBudget -> "No budget selected"
            mobileSession.isSyncing -> "Syncing"
            mobileSession.syncError != null -> "Needs attention"
            mobileSession.localSyncState?.lastSyncedAtEpochMs != null -> "Ready"
            else -> "Not synced"
        }
    PageColumn {
        Heading(
            "Local copy",
            if (hasSelectedBudget) "Cached records and sync status." else "Choose a budget to view the cache.",
        )
        AmountHero(
            "Cached records",
            formatInteger(recordCount),
            if (hasSelectedBudget) "Available offline" else null,
        )
        SectionCard("On this device", syncStatus, animateDetail = true) {
            ListRow(
                "Transactions",
                trailing = formatInteger(mobileSession.cachedTransactionCount),
            )
            DividerLine()
            ListRow("Accounts", trailing = formatInteger(mobileSession.accountRecords.size))
            DividerLine()
            ListRow(
                "Pending changes",
                leading = RowLeading.Icon(Lucide.CloudSync),
                trailing = formatInteger(queuedCount),
            )
            DividerLine()
            ListRow(
                "Needs review",
                "Changes waiting for your decision",
                formatInteger(conflictCount),
                leading = RowLeading.Icon(Lucide.CloudAlert),
            )
            AnimatedReveal(visible = attentionCount > 0, animateHeight = false) {
                Column {
                    DividerLine()
                    ListRow("Other changes needing attention", "Review locally", formatInteger(attentionCount))
                }
            }
        }
        if (hasSelectedBudget) {
            SectionCard("Storage") {
                ListRow(
                    "Remove from this device",
                    "Deletes the copy saved here. Your budget in Actual isn’t changed.",
                    leading = RowLeading.Icon(Lucide.Trash2),
                    onClick = {
                        if (!removing) {
                            removeError = null
                            confirmRemove = true
                        }
                    },
                )
            }
            removeError?.let { InlineMessage(it, InlineMessageKind.ERROR) }
        }
        AnimatedPreviewNote(mobileSession.syncError)
        if (mobileSession.pendingWrites.isNotEmpty()) InfoBubble("Pending edits stay on this device.")
    }
    if (confirmRemove) {
        val budgetName = state.selectedBudget.ifBlank { "this budget" }
        DestructiveConfirmationDialog(
            title = "Remove $budgetName from this device?",
            message =
                "Tandem deletes the copy saved on this device, including any saved password for it. " +
                    "You can download it again from your server. Your budget in Actual isn’t changed.",
            confirmLabel = "Remove",
            dismissLabel = "Cancel",
            enabled = !removing,
            onDismissRequest = { if (!removing) confirmRemove = false },
            onConfirm = {
                removing = true
                scope.launch {
                    val budgetId = state.selectedBudgetId
                    val error = mobileSession.removeBudgetFromDevice(budgetId, vault)
                    removing = false
                    confirmRemove = false
                    if (error != null) {
                        removeError = error
                        return@launch
                    }
                    authenticator?.deleteSecret(budgetPasswordSecretId(budgetId))
                    authenticator?.forgetPasswordCheck(budgetPasswordSecretId(budgetId))
                    state.cachedBudgetCatalogJson =
                        encodeCachedBudgetCatalog(
                            decodeCachedBudgetCatalog(state.cachedBudgetCatalogJson).filterNot {
                                it.budgetId ==
                                    budgetId
                            },
                        )
                    state.encryptedPassword = ""
                    state.selectedBudget = ""
                    state.selectedBudgetId = ""
                    state.selectedBudgetEncrypted = false
                    navigation.reset(AppRoute.BUDGET_CHOOSER)
                }
            },
        )
    }
}

@Composable
internal fun MerchantIconLicensesScreen(
    merchantIconCatalog: MerchantIconCatalog?,
    loadError: String?,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val normalizedQuery = query.trim()

    fun matches(vararg text: String) = normalizedQuery.isEmpty() || text.any { it.contains(normalizedQuery, ignoreCase = true) }
    val libraries = OPEN_SOURCE_LIBRARIES.filter { matches(it.name, it.purpose, it.license) }
    val filteredNotices =
        remember(merchantIconCatalog, normalizedQuery) {
            merchantIconCatalog?.notices.orEmpty().filter { matches(it.title, it.license, it.registryUrl, it.brandUrl) }
        }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = TandemSpacing.screen, vertical = TandemSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
    ) {
        TextFieldCard("Search", query, { query = it }, TextFieldOptions(placeholder = "Library, icon, license or source"))
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(bottom = TandemSpacing.large),
            verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
        ) {
            item(key = "project") {
                SectionCard("Tandem") {
                    ListRow(
                        "Tandem for Actual",
                        "This app",
                        "AGPL-3.0",
                        leading = RowLeading.Icon(Lucide.Info),
                        options = ListRowOptions(showChevron = false),
                    )
                }
            }
            if (libraries.isNotEmpty()) {
                item(key = "libraries") {
                    SectionCard("Open-source libraries", "${libraries.size} used directly by the app") {
                        libraries.forEachIndexed { index, library ->
                            if (index > 0) DividerLine()
                            ListRow(
                                library.name,
                                library.purpose,
                                library.license,
                                leading = RowLeading.Icon(Lucide.Package),
                                options = ListRowOptions(showChevron = false),
                            )
                        }
                    }
                }
            }
            item(key = "icons-heading") {
                Text(
                    when {
                        loadError != null -> loadError
                        merchantIconCatalog == null -> "Loading icons…"
                        else -> "Icons · ${filteredNotices.size} of ${merchantIconCatalog.notices.size}"
                    },
                    Modifier.padding(top = TandemSpacing.medium),
                    color = tandemPalette.muted,
                    fontSize = TandemTypography.label,
                )
            }
            if (merchantIconCatalog != null && filteredNotices.isEmpty() && libraries.isEmpty()) {
                item(key = "empty") { EmptyState("Nothing matches", "Try another search.") }
            }
            items(filteredNotices, key = { it.resourceName }) { notice ->
                SurfaceCard(
                    modifier =
                        Modifier.animateItem(
                            fadeInSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.REVEAL_MILLIS),
                            placementSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTENT_SIZE_MILLIS),
                            fadeOutSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.REVEAL_MILLIS),
                        ),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(TandemSpacing.medium),
                        verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(
                                painter = painterResource(notice.resource),
                                contentDescription = null,
                                modifier = Modifier.size(34.dp),
                                contentScale = ContentScale.Fit,
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall)) {
                                Text(
                                    notice.title,
                                    color = tandemPalette.text,
                                    fontSize = TandemTypography.body,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    notice.license.replace("Fair Use", "fair use", ignoreCase = true),
                                    color = tandemPalette.muted,
                                    fontSize = TandemTypography.label,
                                )
                            }
                        }
                        Text(
                            "Registry source: ${notice.registryUrl}",
                            color = tandemPalette.muted,
                            fontSize = TandemTypography.micro,
                            lineHeight = TandemTypography.tightLineHeight,
                            modifier = Modifier.semantics { contentDescription = "Registry source URL: ${notice.registryUrl}" },
                        )
                        if (notice.brandUrl.isNotBlank()) {
                            Text(
                                "Brand site: ${notice.brandUrl}",
                                color = tandemPalette.muted,
                                fontSize = TandemTypography.micro,
                                lineHeight = TandemTypography.tightLineHeight,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AboutScreen(
    merchantIconMatcherLoadError: String?,
    navigation: AppNavigation,
) {
    PageColumn {
        Heading("About Tandem", "A local-first app for Actual Budget.")
        SurfaceCard {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.extraLarge, vertical = TandemSpacing.extraLarge),
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TandemBrandMark(Modifier.size(84.dp), contentDescription = "Tandem app icon")
                Text(
                    "Tandem",
                    color = tandemPalette.text,
                    fontSize = TandemTypography.pageTitle,
                    fontWeight = FontWeight.Bold,
                    fontFamily = tandemAlloFamily(),
                )
                Badge("Version ${platformAppVersionName()}")
                Text(
                    "Your budget stays on this device and remains available when Tandem is offline.",
                    color = tandemPalette.muted,
                    fontSize = TandemTypography.compactBody,
                    lineHeight = TandemTypography.bodyLineHeight,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Not affiliated with, endorsed by, or supported by Actual Budget.",
                    color = tandemPalette.muted,
                    fontSize = TandemTypography.caption,
                    textAlign = TextAlign.Center,
                )
            }
        }
        SectionCard("Project") {
            ListRow("Supported Actual server", "Required by this Tandem release", SUPPORTED_ACTUAL_SERVER_VERSION)
            DividerLine()
            ListRow("Open-source license", "GNU AGPL-3.0-or-later")
        }
        CrashReportsSection()
        SurfaceCard {
            ListRow(
                "Open-source licenses",
                "Libraries, models and icons",
                leading = RowLeading.Icon(Lucide.Info),
                onClick = { navigation.open(AppRoute.MERCHANT_ICON_LICENSES) },
            )
        }
        AnimatedPreviewNote(merchantIconMatcherLoadError)
    }
}

@Composable
private fun CrashReportsSection() {
    val reports = LocalCrashReports.current ?: return
    val share = LocalShareText.current
    var count by remember { mutableStateOf(reports.count()) }
    SectionCard("Crash reports", "Saved only on this device. Nothing is sent unless you share it.") {
        if (count == 0) {
            ListRow("No crash reports", "If Tandem ever crashes, a report is saved here.", leading = RowLeading.Icon(Lucide.ShieldCheck))
        } else {
            ListRow(
                "Share latest crash report",
                if (count == 1) "1 report saved" else "$count reports saved",
                leading = RowLeading.Icon(Lucide.Share2),
                onClick = share?.let { send -> { reports.latest()?.let(send) } },
            )
            DividerLine()
            ListRow(
                "Delete crash reports",
                leading = RowLeading.Icon(Lucide.Trash2),
                onClick = {
                    reports.clear()
                    count = reports.count()
                },
            )
        }
    }
}

internal data class OpenSourceLibrary(
    val name: String,
    val purpose: String,
    val license: String,
)

internal val OPEN_SOURCE_LIBRARIES =
    listOf(
        OpenSourceLibrary("Kotlin", "Language and standard library", "Apache-2.0"),
        OpenSourceLibrary("kotlinx.coroutines", "Background work", "Apache-2.0"),
        OpenSourceLibrary("kotlinx.serialization", "Reading and writing JSON", "Apache-2.0"),
        OpenSourceLibrary("Compose Multiplatform", "Interface on Android and iOS", "Apache-2.0"),
        OpenSourceLibrary("Ktor", "Talking to your Actual server", "Apache-2.0"),
        OpenSourceLibrary("cryptography-kotlin", "Encrypted budgets and planning data", "Apache-2.0"),
        OpenSourceLibrary("SQLCipher", "Encrypted storage on the device", "BSD-3-Clause"),
        OpenSourceLibrary("AndroidX Room and SQLite", "On-device database", "Apache-2.0"),
        OpenSourceLibrary("AndroidX Activity, Fragment and Biometric", "App lock and screens on Android", "Apache-2.0"),
        OpenSourceLibrary("AndroidX WorkManager", "Background sync on Android", "Apache-2.0"),
        OpenSourceLibrary("AndroidX WindowManager", "Foldable and tablet layouts", "Apache-2.0"),
        OpenSourceLibrary("AndroidX SplashScreen and ProfileInstaller", "Startup on Android", "Apache-2.0"),
        OpenSourceLibrary("LiteRT", "Runs receipt scanning on the device", "Apache-2.0"),
        OpenSourceLibrary("PaddleOCR PP-OCRv6", "Receipt text detection and recognition models", "Apache-2.0"),
        OpenSourceLibrary("Compose Icons", "Icon components", "MIT"),
        OpenSourceLibrary("Lucide", "Icon artwork", "ISC"),
        OpenSourceLibrary("TheSVG", "Merchant icon catalog, at build time", "MIT"),
    )
