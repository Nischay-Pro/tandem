package app.tandemforactual.ui.core.ui

import androidx.compose.ui.graphics.vector.ImageVector
import app.tandemforactual.data.planning.AccountKind
import app.tandemforactual.data.planning.guessAccountKind
import com.composables.icons.lucide.Banknote
import com.composables.icons.lucide.ChartNoAxesCombined
import com.composables.icons.lucide.CreditCard
import com.composables.icons.lucide.HandCoins
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Landmark
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.PiggyBank
import com.composables.icons.lucide.WalletCards

/** The icon for an account's kind, guessed from its name when the kind isn't known. */
internal fun accountTypeIcon(
    accountName: String,
    kind: AccountKind? = null,
): ImageVector = accountKindIcon(kind ?: guessAccountKind(accountName) ?: AccountKind.OTHER)

internal fun accountKindIcon(kind: AccountKind): ImageVector =
    when (kind) {
        AccountKind.BANK -> Lucide.Landmark
        AccountKind.SAVINGS -> Lucide.PiggyBank
        AccountKind.CREDIT_CARD -> Lucide.CreditCard
        AccountKind.CASH -> Lucide.Banknote
        AccountKind.INVESTMENT -> Lucide.ChartNoAxesCombined
        AccountKind.LOAN -> Lucide.HandCoins
        AccountKind.MORTGAGE -> Lucide.House
        AccountKind.OTHER -> Lucide.WalletCards
    }
