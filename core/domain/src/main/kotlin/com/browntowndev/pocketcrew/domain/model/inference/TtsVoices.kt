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
        TtsVoice("Puck", "Puck (Gemini)"),
        TtsVoice("Kore", "Kore (Gemini)"),
        TtsVoice("Charon", "Charon (Gemini)"),
        TtsVoice("Aoede", "Aoede (Gemini)"),
        TtsVoice("Fenrir", "Fenrir (Gemini)"),
        TtsVoice("Zephyr", "Zephyr (Gemini)"),
    )

    fun getVoicesForProvider(provider: ApiProvider): List<TtsVoice> = when (provider) {
        ApiProvider.OPENAI -> openAiVoices
        ApiProvider.XAI -> xAiVoices
        ApiProvider.GOOGLE -> googleVoices
        else -> emptyList()
    }
}
