package com.browntowndev.enclave.domain.util

import com.browntowndev.enclave.domain.model.settings.SearchProvider
import com.browntowndev.enclave.domain.port.repository.SettingsData
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchSkillAvailabilityTest {
    @Test
    fun `IGTA-16b isWebSearchSkillConfigured false when EXA selected without exa key`() {
        val settings =
            SettingsData(
                searchEnabled = true,
                searchProvider = SearchProvider.EXA,
                exaKeyPresent = false,
                tavilyKeyPresent = true,
            )

        assertFalse(settings.isWebSearchSkillConfigured())
    }

    @Test
    fun `isWebSearchSkillConfigured true when Tavily enabled with key`() {
        val settings =
            SettingsData(
                searchEnabled = true,
                searchProvider = SearchProvider.TAVILY,
                tavilyKeyPresent = true,
            )

        assertTrue(settings.isWebSearchSkillConfigured())
    }

    @Test
    fun `isXSearchSkillConfigured requires key and toggle`() {
        assertFalse(
            SettingsData(xSearchEnabled = true, xApiKeyPresent = false).isXSearchSkillConfigured(),
        )
        assertTrue(
            SettingsData(xSearchEnabled = true, xApiKeyPresent = true).isXSearchSkillConfigured(),
        )
    }
}
