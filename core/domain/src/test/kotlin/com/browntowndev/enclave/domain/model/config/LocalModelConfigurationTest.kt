package com.browntowndev.enclave.domain.model.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LocalModelConfigurationTest {
    @Test
    fun `default reasoning budget is twenty five percent of context window`() {
        assertEquals(1024, LocalModelConfiguration.defaultReasoningBudget(4096))
        assertEquals(2048, LocalModelConfiguration.defaultReasoningBudget(8192))
        assertEquals(1, LocalModelConfiguration.defaultReasoningBudget(0))
    }
}
