@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.allo_variable
import app.tandemforactual.ui.generated.resources.inter_variable
import org.jetbrains.compose.resources.Font

@Composable
internal fun tandemInterFamily(): FontFamily {
    val inter = Font(Res.font.inter_variable)
    return remember(inter) { FontFamily(inter) }
}

@Composable
internal fun tandemAlloFamily(): FontFamily {
    val alloRegular = Font(Res.font.allo_variable, FontWeight.Normal)
    val alloSemiBold = Font(Res.font.allo_variable, FontWeight.SemiBold)
    val alloBold = Font(Res.font.allo_variable, FontWeight.Bold)
    return remember(alloRegular, alloSemiBold, alloBold) { FontFamily(alloRegular, alloSemiBold, alloBold) }
}
