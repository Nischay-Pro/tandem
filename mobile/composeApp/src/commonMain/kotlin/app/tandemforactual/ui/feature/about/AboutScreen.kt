@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.about
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.format.platformAppVersionName
import app.tandemforactual.ui.core.session.LocalCrashReports
import app.tandemforactual.ui.core.session.LocalShareText
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.SUPPORTED_ACTUAL_SERVER_VERSION
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.Badge
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.AnimatedPreviewNote
import app.tandemforactual.ui.design.templates.Heading
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.theme.TandemBrandMark
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemAlloFamily
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Share2
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.Trash2

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
        ConnectedServicesSection()
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
