package app.tandemforactual.data.regions.global.bank

import app.tandemforactual.data.regions.Region
import app.tandemforactual.data.regions.br.bank.pluggyai.PluggyAi
import app.tandemforactual.data.regions.eu.bank.enablebanking.EnableBanking
import app.tandemforactual.data.regions.eu.bank.gocardless.GoCardless
import app.tandemforactual.data.regions.nz.bank.akahu.Akahu
import app.tandemforactual.data.regions.us.bank.simplefin.SimpleFin

/** Every bank-sync provider Tandem knows. Register new providers here. */
object BankProviders {
    val all: List<BankProvider> = listOf(SimpleFin, GoCardless, EnableBanking, PluggyAi, Akahu)

    /** Actual treats a link with no recorded source as GoCardless, its original provider. */
    fun forSource(source: String?): BankProvider = all.firstOrNull { it.source == source } ?: GoCardless

    fun forRegion(region: Region): List<BankProvider> = all.filter { region in it.regions }
}
