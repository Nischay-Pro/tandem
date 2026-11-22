package app.tandemforactual.ui.core.ui

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.ChartNoAxesCombined
import com.composables.icons.lucide.CreditCard
import com.composables.icons.lucide.HandCoins
import com.composables.icons.lucide.Landmark
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.PiggyBank
import com.composables.icons.lucide.WalletCards

internal fun accountTypeIcon(
    accountName: String,
    accountType: String? = null,
): ImageVector {
    val name = accountName.lowercase()
    val type = accountType.orEmpty().lowercase()
    return when {
        "savings" in name || "saving" in name -> Lucide.PiggyBank
        "checking" in name || "chequing" in name -> Lucide.Landmark
        listOf("credit", "card", "visa", "mastercard", "amex", "discover").any { it in name } -> Lucide.CreditCard
        listOf("loan", "mortgage", "debt").any { it in name } -> Lucide.HandCoins
        listOf("investment", "brokerage", "401k", "403b", "ira", "retirement", "stocks").any { it in name } ->
            Lucide.ChartNoAxesCombined
        "checking" in type || "chequing" in type -> Lucide.Landmark
        "savings" in type || "saving" in type -> Lucide.PiggyBank
        listOf("credit", "card").any { it in type } -> Lucide.CreditCard
        listOf("loan", "debt").any { it in type } -> Lucide.HandCoins
        listOf("tracking", "investment").any { it in type } -> Lucide.ChartNoAxesCombined
        else -> Lucide.WalletCards
    }
}
