package com.browntowndev.enclave.domain.model.inference

/**
 * Supported API providers for BYOK (Bring Your Own Key) configuration.
 */
enum class ApiProvider(
    val displayName: String,
) {
    ANTHROPIC("Anthropic"),
    OPENAI("OpenAI"),
    OPENROUTER("OpenRouter"),
    XAI("xAI"),
    GOOGLE("Google"),
    FAL("fal.ai"),
    ZAI("Z.ai"),
    DEEPSEEK("DeepSeek"),
    ;

    fun defaultBaseUrl(): String? =
        when (this) {
            GOOGLE -> "https://generativelanguage.googleapis.com"
            OPENROUTER -> "https://openrouter.ai/api/v1"
            XAI -> "https://api.x.ai/v1"
            FAL -> "https://queue.fal.run"
            ZAI -> ZaiEndpoint.PUBLIC.baseUrl
            DEEPSEEK -> DeepSeekEndpoint.OFFICIAL.baseUrl
            else -> null
        }

    val supportsChat: Boolean
        get() =
            when (this) {
                FAL -> false
                ANTHROPIC, OPENAI, OPENROUTER, XAI, GOOGLE, ZAI, DEEPSEEK -> true
            }

    /** Model list/detail APIs that include per-million token pricing for telemetry estimates. */
    val exposesDiscoveryModelPricing: Boolean
        get() =
            when (this) {
                OPENROUTER, XAI -> true
                ANTHROPIC, OPENAI, GOOGLE, FAL, ZAI, DEEPSEEK -> false
            }

    val supportsRealtimeVoice: Boolean
        get() =
            when (this) {
                OPENAI, GOOGLE, XAI -> true
                ANTHROPIC, OPENROUTER, FAL, ZAI, DEEPSEEK -> false
            }

    /** Default sampling temperature for a newly created BYOK preset. */
    fun defaultPresetTemperature(): Double =
        when (this) {
            ZAI -> ZAI_RECOMMENDED_PRESET_TEMPERATURE
            else -> GENERIC_PRESET_TEMPERATURE
        }

    companion object {
        const val GENERIC_PRESET_TEMPERATURE: Double = 0.7
        const val ZAI_RECOMMENDED_PRESET_TEMPERATURE: Double = 1.0
    }
}

/**
 * Z.ai offers two OpenAI-compatible endpoints. Both speak the standard Chat Completions protocol
 * and return the same usage shape (`prompt_tokens_details.cached_tokens`,
 * `completion_tokens_details.reasoning_tokens`); they differ only in plan/billing.
 *
 * The OpenAI SDK treats the base URL as a complete prefix, so these `/api/paas/v4` paths resolve
 * correctly (e.g. `…/paas/v4/chat/completions`) without any `/v1` injection.
 *
 * @param displayName Short label for the neo segmented-control picker in the BYOK form.
 * @param baseUrl Full base URL passed verbatim to the OpenAI SDK client.
 */
enum class ZaiEndpoint(
    val displayName: String,
    val baseUrl: String,
) {
    PUBLIC("Public", "https://api.z.ai/api/paas/v4"),
    CODING("Coding Plan", "https://api.z.ai/api/coding/paas/v4"),
    ;

    companion object {
        /** Pre-migration Anthropic-compatible Z.ai URL; maps to [PUBLIC] on load. */
        const val LEGACY_ANTHROPIC_COMPAT_BASE_URL = "https://api.z.ai/api/anthropic"

        /** Returns the preset for a stored base URL, migrating legacy/unknown values to [PUBLIC]. */
        fun resolveEndpoint(baseUrl: String?): ZaiEndpoint =
            when (baseUrl?.trim()?.trimEnd('/')) {
                CODING.baseUrl -> CODING
                PUBLIC.baseUrl,
                LEGACY_ANTHROPIC_COMPAT_BASE_URL,
                null,
                "",
                -> PUBLIC
                else -> PUBLIC
            }

        /** True when [baseUrl] is not one of the two supported preset URLs (needs migration on load). */
        fun storedUrlNeedsMigration(baseUrl: String?): Boolean {
            val trimmed = baseUrl?.trim()?.trimEnd('/') ?: return false
            return trimmed != PUBLIC.baseUrl && trimmed != CODING.baseUrl
        }
    }
}

/**
 * DeepSeek BYOK exposes a single official OpenAI-compatible endpoint.
 * Stored credentials with non-official URLs migrate to [OFFICIAL] on load.
 */
enum class DeepSeekEndpoint(
    val baseUrl: String,
) {
    OFFICIAL("https://api.deepseek.com"),
    ;

    companion object {
        fun resolveEndpoint(@Suppress("UNUSED_PARAMETER") baseUrl: String?): DeepSeekEndpoint = OFFICIAL

        fun storedUrlNeedsMigration(baseUrl: String?): Boolean {
            val trimmed = baseUrl?.trim()?.trimEnd('/') ?: return false
            return trimmed != OFFICIAL.baseUrl
        }
    }
}
