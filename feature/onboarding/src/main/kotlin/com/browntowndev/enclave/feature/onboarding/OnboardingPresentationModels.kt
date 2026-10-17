package com.browntowndev.enclave.feature.onboarding

import androidx.compose.runtime.Immutable

enum class OnboardingFlowModeUi {
    FULL,
    RESTORED,
}

enum class ApiProviderUi(
    val displayName: String,
    val supportsChat: Boolean = true,
) {
    ANTHROPIC("Anthropic"),
    OPENAI("OpenAI"),
    OPENROUTER("OpenRouter"),
    XAI("xAI"),
    GOOGLE("Google"),
    FAL("fal.ai", supportsChat = false),
    ZAI("Z.ai"),
    DEEPSEEK("DeepSeek"),
}

enum class MediaTypeUi(
    val displayName: String,
) {
    IMAGE("Image"),
    VIDEO("Video"),
    MUSIC("Music"),
}

@Immutable
data class DiscoveredApiModelUi(
    val id: String,
    val name: String? = null,
    val contextWindowTokens: Int? = null,
    val maxOutputTokens: Int? = null,
    val created: Long? = null,
    val promptPrice: Double? = null,
    val completionPrice: Double? = null,
    val cachedPromptPrice: Double? = null,
    val isMultimodal: Boolean? = null,
    val isChatCapable: Boolean = true,
    val isImageGenerationCapable: Boolean = false,
    val isVideoGenerationCapable: Boolean = false,
    val isAudioGenerationCapable: Boolean = false,
    val tasks: Set<String> = emptySet(),
)

@Immutable
data class TtsProviderDraftUi(
    val id: String = "",
    val displayName: String,
    val provider: ApiProviderUi,
    val voiceName: String,
    val modelName: String? = null,
    val baseUrl: String? = null,
    val credentialAlias: String,
    val apiKey: String,
    val sourceCredentialAlias: String? = null,
)

@Immutable
data class MediaProviderDraftUi(
    val id: String = "",
    val displayName: String,
    val provider: ApiProviderUi,
    val modelId: String,
    val capability: MediaTypeUi,
    val baseUrl: String? = null,
    val credentialAlias: String,
    val apiKey: String,
    val sourceCredentialAlias: String? = null,
)

@Immutable
data class TtsVoiceUi(
    val id: String,
    val displayName: String,
)

fun ttsVoicesForProvider(provider: ApiProviderUi): List<TtsVoiceUi> =
    when (provider) {
        ApiProviderUi.OPENAI ->
            listOf(
                TtsVoiceUi("alloy", "Alloy"),
                TtsVoiceUi("echo", "Echo"),
                TtsVoiceUi("fable", "Fable"),
                TtsVoiceUi("onyx", "Onyx"),
                TtsVoiceUi("nova", "Nova"),
                TtsVoiceUi("shimmer", "Shimmer"),
            )
        ApiProviderUi.XAI ->
            listOf(
                TtsVoiceUi("eve", "Eve (Default)"),
                TtsVoiceUi("ara", "Ara (Warm)"),
                TtsVoiceUi("rex", "Rex (Professional)"),
                TtsVoiceUi("sal", "Sal (Smooth)"),
                TtsVoiceUi("leo", "Leo (Authoritative)"),
            )
        ApiProviderUi.GOOGLE ->
            listOf(
                TtsVoiceUi("Puck", "Puck (Gemini)"),
                TtsVoiceUi("Kore", "Kore (Gemini)"),
                TtsVoiceUi("Charon", "Charon (Gemini)"),
                TtsVoiceUi("Aoede", "Aoede (Gemini)"),
                TtsVoiceUi("Fenrir", "Fenrir (Gemini)"),
                TtsVoiceUi("Zephyr", "Zephyr (Gemini)"),
            )
        ApiProviderUi.FAL ->
            listOf(
                TtsVoiceUi("Wise_Woman", "Wise Woman"),
                TtsVoiceUi("Friendly_Person", "Friendly Person"),
                TtsVoiceUi("Inspirational_girl", "Inspirational Girl"),
                TtsVoiceUi("Deep_Voice_Man", "Deep Voice Man"),
            )
        else -> emptyList()
    }
