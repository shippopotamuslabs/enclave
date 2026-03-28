package com.browntowndev.pocketcrew.domain.model.config

import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ApiModelConfigTest {

    @Test
    fun `default values are correct for all tuning fields`() {
        val config = ApiModelConfig(
            displayName = "Test",
            provider = ApiProvider.ANTHROPIC,
            modelId = "test-model",
        )

        assertEquals(4096, config.maxTokens)
        assertEquals(0.7, config.temperature)
        assertEquals(0.95, config.topP)
        assertNull(config.topK)
        assertEquals(0.0, config.frequencyPenalty)
        assertEquals(0.0, config.presencePenalty)
        assertTrue(config.stopSequences.isEmpty())
        assertFalse(config.isVision)
        assertNull(config.baseUrl)
    }
}
