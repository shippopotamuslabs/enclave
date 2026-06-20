package com.browntowndev.enclave.domain.model.inference

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ApiProviderTest {
    @Test
    fun `ZAI display name is Z dot ai`() {
        assertEquals("Z.ai", ApiProvider.ZAI.displayName)
    }

    @Test
    fun `ZAI default base url is the public openai-compatible endpoint`() {
        assertEquals("https://api.z.ai/api/paas/v4", ApiProvider.ZAI.defaultBaseUrl())
    }

    @Test
    fun `ZAI supports chat`() {
        assertTrue(ApiProvider.ZAI.supportsChat)
    }

    @Test
    fun `ZAI does not expose discovery model pricing`() {
        // Consistent with Anthropic/OpenAI/Google: Z.ai's model list API does not reliably return
        // per-million token pricing, so telemetry cost stays ESTIMATED from saved pricing only.
        assertFalse(ApiProvider.ZAI.exposesDiscoveryModelPricing)
    }

    @Test
    fun `ZAI does not support realtime voice`() {
        assertFalse(ApiProvider.ZAI.supportsRealtimeVoice)
    }

    @Test
    fun `ZaiEndpoint resolveEndpoint maps presets legacy and unknown urls`() {
        assertEquals(ZaiEndpoint.PUBLIC, ZaiEndpoint.resolveEndpoint("https://api.z.ai/api/paas/v4"))
        assertEquals(ZaiEndpoint.CODING, ZaiEndpoint.resolveEndpoint("https://api.z.ai/api/coding/paas/v4"))
        assertEquals(ZaiEndpoint.PUBLIC, ZaiEndpoint.resolveEndpoint(ZaiEndpoint.LEGACY_ANTHROPIC_COMPAT_BASE_URL))
        assertEquals(ZaiEndpoint.PUBLIC, ZaiEndpoint.resolveEndpoint("https://custom.z.ai/v1"))
        assertEquals(ZaiEndpoint.PUBLIC, ZaiEndpoint.resolveEndpoint(null))
    }

    @Test
    fun `ZaiEndpoint storedUrlNeedsMigration detects non-preset urls`() {
        assertFalse(ZaiEndpoint.storedUrlNeedsMigration("https://api.z.ai/api/paas/v4"))
        assertFalse(ZaiEndpoint.storedUrlNeedsMigration("https://api.z.ai/api/coding/paas/v4"))
        assertTrue(ZaiEndpoint.storedUrlNeedsMigration(ZaiEndpoint.LEGACY_ANTHROPIC_COMPAT_BASE_URL))
        assertTrue(ZaiEndpoint.storedUrlNeedsMigration("https://custom.z.ai/v1"))
        assertFalse(ZaiEndpoint.storedUrlNeedsMigration(null))
    }

    @Test
    fun `ZAI default base url matches public ZaiEndpoint preset`() {
        assertEquals(ZaiEndpoint.PUBLIC.baseUrl, ApiProvider.ZAI.defaultBaseUrl())
    }

    @Test
    fun `ZAI default preset temperature matches provider recommendation`() {
        assertEquals(1.0, ApiProvider.ZAI.defaultPresetTemperature())
    }

    @Test
    fun `non ZAI providers keep generic preset temperature default`() {
        assertEquals(0.7, ApiProvider.OPENAI.defaultPresetTemperature())
    }

    @Test
    fun `SCN-DOMAIN-001 DEEPSEEK display name is DeepSeek`() {
        assertEquals("DeepSeek", ApiProvider.DEEPSEEK.displayName)
    }

    @Test
    fun `SCN-DOMAIN-002 DEEPSEEK default base url is official endpoint`() {
        assertEquals("https://api.deepseek.com", ApiProvider.DEEPSEEK.defaultBaseUrl())
    }

    @Test
    fun `SCN-DOMAIN-003 DEEPSEEK supports chat`() {
        assertTrue(ApiProvider.DEEPSEEK.supportsChat)
    }

    @Test
    fun `SCN-DOMAIN-004 DEEPSEEK does not expose discovery model pricing`() {
        assertFalse(ApiProvider.DEEPSEEK.exposesDiscoveryModelPricing)
    }

    @Test
    fun `SCN-UI-001 provider dropdown includes DeepSeek`() {
        val chatProviders = ApiProvider.entries.filter(ApiProvider::supportsChat)
        assertTrue(chatProviders.contains(ApiProvider.DEEPSEEK))
        assertEquals("DeepSeek", ApiProvider.DEEPSEEK.displayName)
    }

    @Test
    fun `SCN-ONBOARD-001 onboarding chat provider list includes DeepSeek`() {
        val chatProviders = ApiProvider.entries.filter(ApiProvider::supportsChat)
        assertTrue(chatProviders.any { it.displayName == "DeepSeek" })
    }
}
