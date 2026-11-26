@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.about

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import com.composables.icons.lucide.ArrowLeftRight
import com.composables.icons.lucide.Cloud
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Landmark
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Server

internal class ConnectedService(
    val name: String,
    val detail: String,
    val icon: ImageVector,
    val whenContacted: String,
)

internal val connectedServices =
    listOf(
        ConnectedService(
            "Your Actual server",
            "The server you signed in to. Tandem syncs your budget with it, and bank syncing goes through it too.",
            Lucide.Server,
            "Always",
        ),
        ConnectedService(
            "Frankfurter",
            "Exchange rates, only if you turn on Fetch exchange rates. Only currency codes and dates are sent, " +
                "to the public server or your own if you enter one.",
            Lucide.ArrowLeftRight,
            "If turned on",
        ),
        ConnectedService(
            "Bank websites",
            "Bank logos, only if you turn on Show bank logos. Each linked bank can see that your device asked, " +
                "but nothing about your budget.",
            Lucide.Landmark,
            "If turned on",
        ),
        ConnectedService(
            "Your WebDAV server",
            "Copies of automatic backups, only if you set up Upload to WebDAV. They’re encrypted if you set up Encrypt backups.",
            Lucide.Cloud,
            "If set up",
        ),
        ConnectedService(
            "Pages in your browser",
            "Signing in with OpenID, linking a bank and links like Share feedback open in your browser. " +
                "Tandem doesn’t contact those sites itself.",
            Lucide.ExternalLink,
            "When you tap",
        ),
    )

@Composable
internal fun ConnectedServicesSection() {
    SectionCard("Services Tandem connects to", "Nothing else is contacted. There’s no tracking or analytics.") {
        connectedServices.forEachIndexed { index, service ->
            if (index > 0) DividerLine()
            ListRow(
                service.name,
                service.detail,
                service.whenContacted,
                leading = RowLeading.Icon(service.icon),
                options = ListRowOptions(showChevron = false, subtitleMaxLines = 6),
            )
        }
    }
}
