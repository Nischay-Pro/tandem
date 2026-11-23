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
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.format.formatInteger
import app.tandemforactual.ui.core.state.MerchantIconCatalog
import app.tandemforactual.ui.core.state.MerchantIconNotice
import app.tandemforactual.ui.core.ui.ActivitySearchInput
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.EmptyStateCard
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Package
import org.jetbrains.compose.resources.painterResource

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
        ActivitySearchInput(query, onValueChange = { query = it }, placeholder = "Library, icon, license or source")
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(bottom = TandemSpacing.large),
            verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
        ) {
            libraryItems(libraries)
            item(key = "icons-heading") {
                Text(
                    when {
                        loadError != null -> loadError
                        merchantIconCatalog == null -> "Loading icons…"
                        else -> "Icons · ${formatInteger(filteredNotices.size)} of ${formatInteger(merchantIconCatalog.notices.size)}"
                    },
                    Modifier.padding(top = TandemSpacing.medium),
                    color = tandemPalette.muted,
                    fontSize = TandemTypography.label,
                )
            }
            if (merchantIconCatalog != null && filteredNotices.isEmpty() && libraries.isEmpty()) {
                item(key = "empty") { EmptyStateCard("Nothing matches", "Try another search.") }
            }
            items(filteredNotices, key = { it.resourceName }) { notice ->
                MerchantIconNoticeCard(
                    notice,
                    Modifier.animateItem(
                        fadeInSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.REVEAL_MILLIS),
                        placementSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTENT_SIZE_MILLIS),
                        fadeOutSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.REVEAL_MILLIS),
                    ),
                )
            }
        }
    }
}

/** One bundled merchant icon with its license and sources. */
@Composable
private fun MerchantIconNoticeCard(
    notice: MerchantIconNotice,
    modifier: Modifier,
) {
    SurfaceCard(modifier = modifier) {
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

/** The app's own license and the open-source libraries it uses. */
private fun LazyListScope.libraryItems(libraries: List<OpenSourceLibrary>) {
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
}
