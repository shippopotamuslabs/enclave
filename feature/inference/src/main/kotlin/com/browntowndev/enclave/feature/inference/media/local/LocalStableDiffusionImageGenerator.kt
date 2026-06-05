package com.browntowndev.enclave.feature.inference.media.local

import android.content.Context
import com.browntowndev.enclave.domain.model.media.ImageGenerationProgress
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.ImageGenerationTarget
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationError
import com.browntowndev.enclave.domain.port.inference.InferenceBusyException
import com.browntowndev.enclave.domain.usecase.inference.InferenceLockManager
import com.browntowndev.enclave.domain.usecase.inference.InferenceType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

data class LocalStableDiffusionGenerationResult(
    val outputFileReferences: List<String>,
    val width: Int,
    val height: Int,
    val elapsedMillis: Long? = null,
    val backend: com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind,
)

/**
 * Runs on-device diffusion via [LocalImageGenerationEngine] (QNN NPU, MNN CPU).
 * Vulkan / stable-diffusion.cpp is not used.
 */
class LocalStableDiffusionImageGenerator
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val engine: LocalImageGenerationEngine,
        private val inferenceLockManager: InferenceLockManager,
        private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) {
        suspend fun generate(
            prompt: String,
            target: ImageGenerationTarget.LocalStableDiffusion,
            settings: ImageGenerationSettings,
            onProgress: (ImageGenerationProgress) -> Unit = {},
        ): Result<LocalStableDiffusionGenerationResult> =
            runCatching {
                onProgress(ImageGenerationProgress.PreparingModel)
                val (width, height) = resolveDimensions(settings)
                val outputDir = File(context.cacheDir, OUTPUT_SUBDIR).apply { mkdirs() }
                val outputPrefix = File(outputDir, "local-${UUID.randomUUID()}").absolutePath

                if (!inferenceLockManager.acquireLock(InferenceType.ON_DEVICE)) {
                    throw LocalImageGenerationError.Busy()
                }
                try {
                    onProgress(ImageGenerationProgress.LoadingModel)
                    val started = System.currentTimeMillis()
                    onProgress(ImageGenerationProgress.Generating(step = 0, totalSteps = STEPS))
                    val nativeRequest =
                        LocalImageGenerationNativeRequest(
                            backend = target.selectedBackend,
                            modelDir = target.modelDir,
                            prompt = prompt,
                            negativePrompt = null,
                            width = width,
                            height = height,
                            steps = STEPS,
                            cfgScale = CFG_SCALE,
                            seed = settings.seed.toLongOrNull() ?: System.nanoTime(),
                            outputPathPrefix = outputPrefix,
                        )
                    val output =
                        withContext(dispatcher) {
                            val result = engine.generate(nativeRequest).getOrThrow()
                            val elapsed = System.currentTimeMillis() - started
                            if (elapsed > SLOW_GENERATION_WARNING_MS) {
                                // Surface slow runs in logs only; do not kill unless user cancels.
                                android.util.Log.w(
                                    TAG,
                                    "Local generation exceeded ${SLOW_GENERATION_WARNING_MS}ms (elapsed=$elapsed)",
                                )
                            }
                            result
                        }
                    onProgress(ImageGenerationProgress.SavingOutput)
                    LocalStableDiffusionGenerationResult(
                        outputFileReferences = output.outputPaths,
                        width = output.width,
                        height = output.height,
                        elapsedMillis = output.elapsedMillis,
                        backend = target.selectedBackend,
                    )
                } finally {
                    inferenceLockManager.releaseLock(InferenceType.ON_DEVICE)
                }
            }.recoverCatching { cause ->
                when (cause) {
                    is kotlinx.coroutines.CancellationException,
                    is InferenceBusyException,
                    is LocalImageGenerationError,
                    -> throw cause
                    else -> throw LocalImageGenerationError.GenerationFailed(
                        target.selectedBackend,
                        GENERATION_FAILED,
                        cause,
                    )
                }
            }

        private fun resolveDimensions(settings: ImageGenerationSettings): Pair<Int, Int> {
            val longestSide =
                settings.imageResolution
                    ?.trim()
                    ?.lowercase()
                    ?.split('x')
                    ?.mapNotNull(String::toIntOrNull)
                    ?.filter { it > 0 }
                    ?.maxOrNull()
                    ?: DEFAULT_DIMENSION
            return resolveAspectRatioDimensions(settings.aspectRatio, longestSide)
        }

        private fun resolveAspectRatioDimensions(
            aspectRatio: com.browntowndev.enclave.domain.model.media.AspectRatio,
            longestSide: Int,
        ): Pair<Int, Int> =
            when (aspectRatio) {
                com.browntowndev.enclave.domain.model.media.AspectRatio.SIXTEEN_NINE ->
                    longestSide to longestSide * 9 / 16
                com.browntowndev.enclave.domain.model.media.AspectRatio.NINE_SIXTEEN ->
                    longestSide * 9 / 16 to longestSide
                else -> longestSide to longestSide
            }

        private companion object {
            const val TAG = "LocalImageGeneration"
            const val CFG_SCALE = 7.5f
            const val STEPS = 20
            const val DEFAULT_DIMENSION = 512
            const val OUTPUT_SUBDIR = "local-image-gen"
            const val GENERATION_FAILED = "Local image generation failed."
            val SLOW_GENERATION_WARNING_MS = 3.minutes.inWholeMilliseconds
        }
    }
