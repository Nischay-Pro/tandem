@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.components.Badge
import app.tandemforactual.ui.components.DividerLine
import app.tandemforactual.ui.components.ListRow
import app.tandemforactual.ui.components.PrimaryButton
import app.tandemforactual.ui.components.SecondaryButton
import app.tandemforactual.ui.components.SectionCard

@Composable
internal fun SaveResultScreen(
    state: AppState,
    navigation: AppNavigation,
) {
    val palette = tandemPalette
    PageColumn {
        Spacer(Modifier.height(16.dp))
        Badge("Not saved", palette.warning)
        Heading("No change was made", "This sample form can’t save a transaction yet.")
        SectionCard("Transaction", "Sample data") {
            ListRow("Amount", "Corner Market", state.formatCurrency(-4_218))
            DividerLine()
            ListRow("Account and category", "Checking · Groceries")
        }
        PreviewNote(
            "Actual Budget is unchanged. New transactions aren’t available here yet, and this screen doesn’t send changes.",
        )
        PrimaryButton("Return to activity") { navigation.selectTab(AppRoute.ACTIVITY) }
        SecondaryButton("Open sync center") { navigation.open(AppRoute.SYNC_CENTER) }
    }
}
