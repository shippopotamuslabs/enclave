package com.browntowndev.enclave.domain.port.inference

interface WhisperInferencePort {
    suspend fun initialize(modelPath: String)

    suspend fun transcribe(samples: FloatArray): String

    suspend fun close()
}
