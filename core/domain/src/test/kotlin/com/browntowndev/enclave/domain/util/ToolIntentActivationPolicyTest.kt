package com.browntowndev.enclave.domain.util

import com.browntowndev.enclave.domain.port.inference.SearchIntentClassifierPort
import com.browntowndev.enclave.domain.port.repository.SettingsData
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ToolIntentActivationPolicyTest {
    @Test
    fun `IGTA-08 ToolIntentActivationPolicy skips classifier on pure docgen`() {
        val classifier = mockk<SearchIntentClassifierPort>()
        every { classifier.requiresSearch(any(), any()) } returns true

        val decision =
            ToolIntentActivationPolicy.resolve(
                prompt = "Create a docx report from these notes",
                embedding = floatArrayOf(0.1f, 0.2f),
                settings =
                    SettingsData(
                        searchEnabled = true,
                        tavilyKeyPresent = true,
                        xSearchEnabled = true,
                        xApiKeyPresent = true,
                    ),
                classifier = classifier,
            )

        assertFalse(decision.runWebPreRetrieval)
        assertFalse(decision.activateSearchX)
        verify(exactly = 0) { classifier.requiresSearch(any(), any()) }
    }

    @Test
    fun `IGTA-12 null embedding disables search path`() {
        val classifier = mockk<SearchIntentClassifierPort>(relaxed = true)

        val decision =
            ToolIntentActivationPolicy.resolve(
                prompt = "Latest news headlines",
                embedding = null,
                settings =
                    SettingsData(
                        searchEnabled = true,
                        tavilyKeyPresent = true,
                        xSearchEnabled = true,
                        xApiKeyPresent = true,
                    ),
                classifier = classifier,
            )

        assertFalse(decision.runWebPreRetrieval)
        assertFalse(decision.activateSearchX)
    }

    @Test
    fun `IGTA-07 low classifier probability does not activate X`() {
        val classifier = mockk<SearchIntentClassifierPort>()
        every { classifier.requiresSearch(any(), any()) } returns false

        val decision =
            ToolIntentActivationPolicy.resolve(
                prompt = "Tell me today's weather in Boston",
                embedding = floatArrayOf(0.1f),
                settings =
                    SettingsData(
                        searchEnabled = true,
                        tavilyKeyPresent = true,
                        xSearchEnabled = true,
                        xApiKeyPresent = true,
                    ),
                classifier = classifier,
            )

        assertFalse(decision.activateSearchX)
        assertFalse(decision.runWebPreRetrieval)
    }

    @Test
    fun `MUT-03 classifier not invoked without configured web search skill`() {
        val classifier = mockk<SearchIntentClassifierPort>(relaxed = true)

        val decision =
            ToolIntentActivationPolicy.resolve(
                prompt = "Current weather in Boston",
                embedding = floatArrayOf(0.1f),
                settings =
                    SettingsData(
                        searchEnabled = true,
                        searchProvider = com.browntowndev.enclave.domain.model.settings.SearchProvider.EXA,
                        exaKeyPresent = false,
                        tavilyKeyPresent = false,
                    ),
                classifier = classifier,
            )

        assertFalse(decision.runWebPreRetrieval)
        verify(exactly = 0) { classifier.requiresSearch(any(), any()) }
    }

    @Test
    fun `IGTA-05b combined prompt activates research path when classifier positive`() {
        val classifier = mockk<SearchIntentClassifierPort>()
        every { classifier.requiresSearch(any(), any()) } returns true

        val decision =
            ToolIntentActivationPolicy.resolve(
                prompt = "Research current mortgage rates and write a PDF report",
                embedding = floatArrayOf(0.1f),
                settings =
                    SettingsData(
                        searchEnabled = true,
                        tavilyKeyPresent = true,
                        xSearchEnabled = true,
                        xApiKeyPresent = true,
                    ),
                classifier = classifier,
            )

        assertTrue(decision.activateArtifactTools)
        assertTrue(decision.runWebPreRetrieval)
        assertTrue(decision.activateSearchX)
    }
}
