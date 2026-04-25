package com.browntowndev.pocketcrew.domain.model.inference

/**
 * Static configuration of available TTS voices for supported providers.
 */
object TtsVoices {
    data class TtsVoice(val id: String, val displayName: String)

    val openAiVoices = listOf(
        TtsVoice("alloy", "Alloy"),
        TtsVoice("echo", "Echo"),
        TtsVoice("fable", "Fable"),
        TtsVoice("onyx", "Onyx"),
        TtsVoice("nova", "Nova"),
        TtsVoice("shimmer", "Shimmer")
    )

    val xAiVoices = listOf(
        TtsVoice("eve", "Eve (Default)"),
        TtsVoice("ara", "Ara (Warm)"),
        TtsVoice("rex", "Rex (Professional)"),
        TtsVoice("sal", "Sal (Smooth)"),
        TtsVoice("leo", "Leo (Authoritative)")
    )

    val googleVoices = listOf(
        TtsVoice("en-US:en-US-Neural2-D", "US English (Neural2-D)"),
        TtsVoice("en-US:en-US-Neural2-F", "US English (Neural2-F)"),
        TtsVoice("en-GB:en-GB-Neural2-B", "UK English (Neural2-B)"),
        TtsVoice("en-GB:en-GB-Neural2-C", "UK English (Neural2-C)"),
        TtsVoice("en-AU:en-AU-Neural2-A", "AU English (Neural2-A)"),
        TtsVoice("en-AU:en-AU-Neural2-B", "AU English (Neural2-B)")
    )

    fun getVoicesForProvider(provider: ApiProvider): List<TtsVoice> = when (provider) {
        ApiProvider.OPENAI -> openAiVoices
        ApiProvider.XAI -> xAiVoices
        ApiProvider.GOOGLE -> googleVoices
        else -> emptyList()
    }
}
