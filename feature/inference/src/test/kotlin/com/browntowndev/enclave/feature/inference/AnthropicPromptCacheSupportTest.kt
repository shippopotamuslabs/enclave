package com.browntowndev.enclave.feature.inference

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnthropicPromptCacheSupportTest {
    @Test
    fun supportsPromptCaching_delegatesToApiProviderModelPolicy() {
        assertTrue(AnthropicPromptCacheSupport.supportsPromptCaching("claude-sonnet-4-20250514"))
        assertTrue(AnthropicPromptCacheSupport.supportsPromptCaching("claude-3-5-sonnet-20241022"))
        assertTrue(AnthropicPromptCacheSupport.supportsPromptCaching("anthropic/claude-haiku-4-5-20251001"))
        assertFalse(AnthropicPromptCacheSupport.supportsPromptCaching("claude-2.1"))
        assertFalse(AnthropicPromptCacheSupport.supportsPromptCaching("gpt-4o"))
        assertFalse(AnthropicPromptCacheSupport.supportsPromptCaching("claude-instant-1.2"))
    }
}
