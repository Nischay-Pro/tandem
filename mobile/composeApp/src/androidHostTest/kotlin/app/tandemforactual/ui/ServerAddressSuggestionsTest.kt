package app.tandemforactual.ui

import app.tandemforactual.ui.feature.onboarding.completeServerAddress
import app.tandemforactual.ui.feature.onboarding.recentServerAddresses
import app.tandemforactual.ui.feature.onboarding.rememberRecentServerAddress
import app.tandemforactual.ui.feature.onboarding.serverAddressSuggestions
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerAddressSuggestionsTest {
    @Test
    fun addressesWithoutASchemeUseHttpsOrHttpForHomeNetworks() {
        assertEquals("https://budget.example.com", completeServerAddress(" budget.example.com/ "))
        assertEquals("http://192.168.1.10:5006", completeServerAddress("192.168.1.10:5006"))
        assertEquals("http://localhost:5006", completeServerAddress("localhost:5006"))
        assertEquals("http://budget.example.com", completeServerAddress("http://budget.example.com"))
        assertEquals("", completeServerAddress("  "))
    }

    @Test
    fun recentServersAreRememberedNewestFirstWithoutDuplicates() {
        var saved = ""
        listOf("https://a.example.com", "https://b.example.com/", "https://A.example.com").forEach {
            saved = rememberRecentServerAddress(saved, it)
        }

        assertEquals(listOf("https://A.example.com", "https://b.example.com"), recentServerAddresses(saved))
    }

    @Test
    fun suggestionsMatchRecentServersAndOfferTheCompletedAddress() {
        val recent = listOf("https://budget.example.com", "http://192.168.1.10:5006")

        assertEquals(recent, serverAddressSuggestions("", recent).map { it.address })
        assertEquals(
            listOf("https://budget.example.com", "https://budget"),
            serverAddressSuggestions("budget", recent).map { it.address },
        )
        assertEquals(listOf("https://budget.example.com"), serverAddressSuggestions("budget.example.com", recent).map { it.address })
        assertEquals(emptyList(), serverAddressSuggestions("https://budget.example.com", recent))
    }
}
