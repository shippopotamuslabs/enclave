package com.browntowndev.pocketcrew.domain.port.inference

/**
 * Port for Text-to-Speech (TTS) services.
 */
interface TtsServicePort {
    /**
     * Synthesizes the given [text] into speech using the specified [voice].
     *
     * @param text The text to synthesize.
     * @param voice The identifier for the voice to use.
     * @param modelId The identifier for the model to use (optional, provider-specific).
     * @return A [Result] containing the raw audio bytes (typically MP3) on success.
     */
    suspend fun synthesizeSpeech(text: String, voice: String, modelId: String? = null): Result<ByteArray>
}
