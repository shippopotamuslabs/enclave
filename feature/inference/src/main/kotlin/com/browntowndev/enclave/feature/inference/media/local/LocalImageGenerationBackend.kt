package com.browntowndev.enclave.feature.inference.media.local

import com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationMemoryPolicy
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelPolicy

enum class LocalImageGenerationMode {
    TXT2IMG,
    IMG2IMG,
}

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
    val mode: LocalImageGenerationMode = LocalImageGenerationMode.TXT2IMG,
    val initImagePath: String? = null,
    val denoiseStrength: Float = LocalImageGenerationModelPolicy.DEFAULT_IMG2IMG_DENOISE_STRENGTH,
    val memoryPolicy: LocalImageGenerationMemoryPolicy = LocalImageGenerationMemoryPolicy.LOW_RAM,
)

data class LocalImageGenerationNativeResult(
    val outputPaths: List<String>,
    val width: Int,
    val height: Int,
    val elapsedMillis: Long,
)

interface LocalImageGenerationBackend {
    val kind: LocalImageGenerationBackendKind

    fun cancel() {}

    suspend fun generate(request: LocalImageGenerationNativeRequest): Result<LocalImageGenerationNativeResult>
}
