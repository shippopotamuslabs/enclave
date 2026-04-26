package com.browntowndev.pocketcrew.domain.port.inference

import com.browntowndev.pocketcrew.domain.model.inference.TtsAudioChunk
import kotlinx.coroutines.flow.Flow

/**
 * Port for streaming Text-to-Speech (TTS) services.
 *
 * Unlike [TtsServicePort] which returns complete audio as a [ByteArray],
 * this interface returns audio incrementally as a [Flow] of [TtsAudioChunk],
 * enabling low-latency playback that starts before the full audio is generated.
 */
interface StreamingTtsServicePort {
    /**
     * Synthesizes the given [text] into speech using the specified [voice],
     * returning audio chunks as they become available.
     *
     * @param text The text to synthesize.
     * @param voice The identifier for the voice to use.
     * @param modelId The identifier for the model to use (optional, provider-specific).
     * @return A [Flow] of [TtsAudioChunk] containing incremental audio data.
     *         Emits [TtsAudioChunk.Data] for each chunk, followed by [TtsAudioChunk.Done]
     *         when complete, or [TtsAudioChunk.Error] if streaming fails.
     */
    fun synthesizeSpeechStreaming(
        text: String,
        voice: String,
        modelId: String? = null
    ): Flow<TtsAudioChunk>
}
