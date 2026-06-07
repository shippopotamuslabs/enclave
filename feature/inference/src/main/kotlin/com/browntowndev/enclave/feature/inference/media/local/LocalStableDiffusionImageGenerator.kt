package com.browntowndev.enclave.feature.inference.media.local

import android.app.ActivityManager
import android.content.Context
import com.browntowndev.enclave.domain.model.media.ImageGenerationProgress
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.resolvedImg2ImgDenoiseStrength
import com.browntowndev.enclave.domain.model.media.ImageGenerationTarget
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationError
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationMemoryPolicy
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelPolicy
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationPaths
import com.browntowndev.enclave.domain.port.inference.InferenceBusyException
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
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
        private val studioRepository: StudioRepositoryPort,
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
                val normalized =
                    LocalImageGenerationModelPolicy.normalizeRequest(
                        modelId = target.modelId,
                        settings = settings,
                    )
                val outputDir = File(context.cacheDir, OUTPUT_SUBDIR).apply { mkdirs() }
                val outputPrefix = File(outputDir, "local-${UUID.randomUUID()}").absolutePath

                if (!inferenceLockManager.acquireLock(InferenceType.ON_DEVICE)) {
                    throw LocalImageGenerationError.Busy()
                }
                try {
                    onProgress(ImageGenerationProgress.LoadingModel)
                    val started = System.currentTimeMillis()
                    onProgress(
                        ImageGenerationProgress.Generating(
                            step = 0,
                            totalSteps = normalized.diffusionSteps,
                        ),
                    )
                    val referenceUri = settings.referenceImageUri
                    val mode =
                        if (referenceUri != null) {
                            LocalImageGenerationMode.IMG2IMG
                        } else {
                            LocalImageGenerationMode.TXT2IMG
                        }
                    if (mode == LocalImageGenerationMode.IMG2IMG &&
                        !LocalImageGenerationPaths.hasImg2ImgCapability(java.io.File(target.modelDir))
                    ) {
                        throw LocalImageGenerationError.GenerationFailed(
                            target.selectedBackend,
                            "IMG2IMG_ENCODER_MISSING: model pack lacks vae_encoder.bin or vae_encoder.mnn",
                        )
                    }
                    val initImagePath =
                        referenceUri?.let { uri ->
                            val bytes =
                                studioRepository.readMediaBytes(uri)
                                    ?: throw LocalImageGenerationError.GenerationFailed(
                                        target.selectedBackend,
                                        "Reference image could not be read.",
                                    )
                            val initFile = File(outputDir, "init-${UUID.randomUUID()}.png")
                            initFile.writeBytes(bytes)
                            initFile.absolutePath
                        }
                    val memoryClass =
                        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
                            .memoryClass
                    val memoryPolicy =
                        LocalImageGenerationModelPolicy.memoryPolicyFor(target.modelId, memoryClass)
                    val nativeRequest =
                        LocalImageGenerationNativeRequest(
                            backend = target.selectedBackend,
                            modelId = target.modelId,
                            modelDir = target.modelDir,
                            prompt = prompt,
                            negativePrompt = null,
                            width = normalized.width,
                            height = normalized.height,
                            steps = normalized.diffusionSteps,
                            cfgScale = normalized.cfgScale,
                            scheduler = normalized.scheduler,
                            seed = settings.seed.toLongOrNull() ?: System.nanoTime(),
                            outputPathPrefix = outputPrefix,
                            mode = mode,
                            initImagePath = initImagePath,
                            denoiseStrength =
                                LocalImageGenerationModelPolicy.resolveImg2ImgDenoiseStrength(
                                    target.modelId,
                                    settings,
                                ),
                            memoryPolicy = memoryPolicy,
                        )
                    val output =
                        withContext(dispatcher) {
                            val result = engine.generate(nativeRequest).getOrThrow()
                            val elapsed = System.currentTimeMillis() - started
                            if (elapsed > SLOW_GENERATION_WARNING_MS) {
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
                    is IllegalArgumentException,
                    -> throw cause
                    else -> throw LocalImageGenerationError.GenerationFailed(
                        target.selectedBackend,
                        GENERATION_FAILED,
                        cause,
                    )
                }
            }

        private companion object {
            const val TAG = "LocalImageGeneration"
            const val OUTPUT_SUBDIR = "local-image-gen"
            const val GENERATION_FAILED = "Local image generation failed."
            val SLOW_GENERATION_WARNING_MS = 3.minutes.inWholeMilliseconds
        }
    }
