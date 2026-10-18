package com.browntowndev.enclave.app

import com.browntowndev.enclave.domain.port.inference.WhisperInferencePort
import com.shippopotamus.whisper.WhisperEngine

/**
 * Adapter from Enclave's [WhisperInferencePort] to the whisper-android library's
 * [WhisperEngine] (ENC-53). The engine moved to its own repo; this keeps
 * `:core:domain` and every speech consumer unchanged. Default `WhisperParams`
 * (English, no translation, auto thread count) match the library defaults the
 * in-repo engine previously hardcoded.
 */
class WhisperInferenceAdapter(
    private val engine: WhisperEngine,
) : WhisperInferencePort {
    override suspend fun initialize(modelPath: String) {
        engine.load(modelPath)
    }

    override suspend fun transcribe(samples: FloatArray): String = engine.transcribe(samples)

    override suspend fun close() {
        engine.close()
    }
}
