@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

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
import app.tandemforactual.ui.components.AnimatedReveal
import app.tandemforactual.ui.components.Badge
import app.tandemforactual.ui.components.BodyText
import app.tandemforactual.ui.components.DividerLine
import app.tandemforactual.ui.components.EmptyState
import app.tandemforactual.ui.components.ListRow
import app.tandemforactual.ui.components.ListRowOptions
import app.tandemforactual.ui.components.RowLeading
import app.tandemforactual.ui.components.SectionCard
import app.tandemforactual.ui.components.SurfaceCard
import app.tandemforactual.ui.components.TandemLoadingIndicator
import app.tandemforactual.ui.platformAppVersionName
import com.composables.icons.lucide.CloudAlert
import com.composables.icons.lucide.CloudSync
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun LocalDataScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    onSyncNow: () -> Unit,
) {
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
                "Pending Changes",
                leading = RowLeading.Icon(Lucide.CloudSync),
                trailing = formatInteger(queuedCount),
            )
            DividerLine()
            ListRow(
                "Needs review",
                "Choose a version",
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
        SectionCard("Storage actions") {
            ListRow(
                "Sync budget",
                "Update this device from Actual",
                when {
                    mobileSession.isSyncing -> "Syncing"
                    hasSelectedBudget -> null
                    else -> "Unavailable"
                },
                options = ListRowOptions(animateChanges = true),
                onClick = onSyncNow.takeIf { hasSelectedBudget && !mobileSession.isSyncing },
            )
        }
        AnimatedPreviewNote(mobileSession.syncError)
        if (mobileSession.pendingWrites.isNotEmpty()) InfoBubble("Pending edits stay on this device.")
    }
}

@Composable
internal fun MerchantIconLicensesScreen(
    merchantIconCatalog: MerchantIconCatalog?,
    loadError: String?,
) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = TandemSpacing.screen, vertical = TandemSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
    ) {
        Heading("Icon licenses and sources")
        when {
            loadError != null -> BodyText(loadError)
            merchantIconCatalog == null -> {
                Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium), verticalAlignment = Alignment.CenterVertically) {
                    TandemLoadingIndicator(
                        modifier = Modifier.size(20.dp),
                        color = tandemPalette.accent,
                        strokeWidth = 2.dp,
                    )
                    BodyText("Loading icons")
                }
            }
            else -> {
                TextFieldCard(
                    "Search",
                    query,
                    { query = it },
                    TextFieldOptions(placeholder = "Name, license, or source"),
                )
                val filteredNotices =
                    remember(merchantIconCatalog, query) {
                        val normalizedQuery = query.trim()
                        merchantIconCatalog.notices.filter { notice ->
                            normalizedQuery.isEmpty() ||
                                notice.title.contains(normalizedQuery, ignoreCase = true) ||
                                notice.license.contains(normalizedQuery, ignoreCase = true) ||
                                notice.registryUrl.contains(normalizedQuery, ignoreCase = true) ||
                                notice.brandUrl.contains(normalizedQuery, ignoreCase = true)
                        }
                    }
                Text(
                    "${filteredNotices.size} of ${merchantIconCatalog.notices.size} icons",
                    color = tandemPalette.muted,
                    fontSize = TandemTypography.label,
                )
                if (filteredNotices.isEmpty()) {
                    EmptyState("No icons match", "Try another search.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                    ) {
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
                Text("Tandem", color = tandemPalette.text, fontSize = TandemTypography.pageTitle, fontWeight = FontWeight.Bold)
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
        SectionCard("Open-source libraries", "Direct mobile-app dependencies and licenses") {
            ListRow("Kotlin and kotlinx", "Kotlin, Coroutines and Serialization · Apache-2.0")
            DividerLine()
            ListRow("Compose Multiplatform and resources", "Apache-2.0")
            DividerLine()
            ListRow("Ktor", "Apache-2.0")
            DividerLine()
            ListRow("AndroidX Room and SQLite", "Apache-2.0")
            DividerLine()
            ListRow(
                "AndroidX platform libraries",
                "Activity Compose, WorkManager and SplashScreen · Apache-2.0",
            )
            DividerLine()
            ListRow("Compose Icons", "MIT")
            DividerLine()
            ListRow("Lucide icon artwork", "ISC")
            DividerLine()
            ListRow("TheSVG icon package", "Build-time catalog and tooling · MIT")
        }
        SurfaceCard {
            ListRow(
                "View icon licenses and sources",
                leading = RowLeading.Icon(Lucide.Info),
                onClick = { navigation.open(AppRoute.MERCHANT_ICON_LICENSES) },
            )
        }
        AnimatedPreviewNote(merchantIconMatcherLoadError)
    }
}
