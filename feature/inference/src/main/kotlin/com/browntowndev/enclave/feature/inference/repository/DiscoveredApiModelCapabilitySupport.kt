package com.browntowndev.enclave.feature.inference.repository

import com.browntowndev.enclave.domain.model.config.MediaTask
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel

internal fun String.mediaHintMatches(value: String): Boolean = contains(value, ignoreCase = true)

internal fun String.mediaHintMatchesAny(vararg values: String): Boolean = values.any { mediaHintMatches(it) }

@Suppress("CyclomaticComplexMethod")
internal fun DiscoveredApiModel.applyCatalogCapabilities(
    provider: ApiProvider,
    supportedGenerationMethods: List<String> = emptyList(),
    isChatOverride: Boolean? = null,
    isImageGenOverride: Boolean? = null,
    isVideoGenOverride: Boolean? = null,
): DiscoveredApiModel? {
    val isBlacklisted =
        when (provider) {
            ApiProvider.OPENAI -> {
                id.contains("dall-e-2", ignoreCase = true) ||
                    id.startsWith("whisper", ignoreCase = true) ||
                    id.startsWith("tts-", ignoreCase = true) ||
                    id.contains("canary", ignoreCase = true)
            }
            ApiProvider.GOOGLE -> {
                id.contains("imagen-1", ignoreCase = true) ||
                    id.contains("imagen-2", ignoreCase = true) ||
                    id.contains("aqa", ignoreCase = true)
            }
            else -> false
        }
    if (isBlacklisted) return null

    val isImageGen =
        when (provider) {
            ApiProvider.OPENAI -> id.contains("dall-e-3", ignoreCase = true) || id.contains("gpt-image-", ignoreCase = true)
            ApiProvider.GOOGLE -> {
                id.contains("imagen-3", ignoreCase = true) ||
                    id.contains("nano-banana", ignoreCase = true) ||
                    (
                        supportedGenerationMethods.any { it.equals("predict", ignoreCase = true) } &&
                            id.contains("imagen", ignoreCase = true)
                    )
            }
            ApiProvider.XAI -> isImageGenOverride ?: false
            ApiProvider.FAL ->
                supportedGenerationMethods.any { it.mediaHintMatches("image") } ||
                    id.mediaHintMatchesAny("flux", "imagen", "seedream", "recraft", "ideogram", "stable-diffusion")
            else -> false
        }

    val isVideoGen =
        when (provider) {
            ApiProvider.GOOGLE -> {
                id.contains("veo", ignoreCase = true) ||
                    supportedGenerationMethods.any { it.equals("predictLongRunning", ignoreCase = true) }
            }
            ApiProvider.XAI -> isVideoGenOverride ?: false
            ApiProvider.FAL ->
                supportedGenerationMethods.any { it.mediaHintMatches("video") } ||
                    id.mediaHintMatchesAny("video", "kling", "seedance", "wan", "hailuo")
            else -> false
        }

    val isMusicGen =
        when (provider) {
            ApiProvider.GOOGLE -> id.contains("lyria", ignoreCase = true)
            ApiProvider.FAL ->
                supportedGenerationMethods.any { it.mediaHintMatches("audio") || it.mediaHintMatches("music") } ||
                    id.mediaHintMatchesAny("audio", "music", "stable-audio", "ace-step", "tts", "speech", "elevenlabs")
            else -> false
        }

    val isMediaOnly =
        (isImageGen || isVideoGen || isMusicGen) &&
            !id.contains("gemini", ignoreCase = true) &&
            !id.contains("gpt-4", ignoreCase = true)

    val isChat =
        isChatOverride ?: (
            provider.supportsChat &&
                !isMediaOnly &&
                !id.startsWith("whisper", ignoreCase = true) &&
                !id.startsWith("tts-", ignoreCase = true)
        )

    val derivedTasks =
        if (provider != ApiProvider.FAL) {
            val t = mutableSetOf<MediaTask>()
            if (isImageGen) {
                t.add(MediaTask.TEXT_TO_IMAGE)
                t.add(MediaTask.EDIT_IMAGE)
            }
            if (isVideoGen) {
                t.add(MediaTask.TEXT_TO_VIDEO)
                t.add(MediaTask.IMAGE_TO_VIDEO)
            }
            t
        } else {
            tasks
        }

    return copy(
        isChatCapable = isChat,
        isImageGenerationCapable = isImageGen,
        isVideoGenerationCapable = isVideoGen,
        isAudioGenerationCapable = isMusicGen,
        tasks = derivedTasks,
    )
}
