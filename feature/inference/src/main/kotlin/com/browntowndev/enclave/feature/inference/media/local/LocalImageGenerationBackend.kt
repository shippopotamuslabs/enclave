package com.browntowndev.enclave.feature.inference.media.local

import com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId

data class LocalImageGenerationNativeRequest(
    val backend: LocalImageGenerationBackendKind,
    val modelId: LocalImageGenerationModelId,
    val modelDir: String,
    val prompt: String,
    val negativePrompt: String?,
    val width: Int,
    val height: Int,
    val steps: Int,
    val cfgScale: Float,
    val scheduler: String,
    val seed: Long,
    val outputPathPrefix: String,
)

data class LocalImageGenerationNativeResult(
    val outputPaths: List<String>,
    val width: Int,
    val height: Int,
    val elapsedMillis: Long,
)

interface LocalImageGenerationBackend {
    val kind: LocalImageGenerationBackendKind

    suspend fun generate(request: LocalImageGenerationNativeRequest): Result<LocalImageGenerationNativeResult>
}
