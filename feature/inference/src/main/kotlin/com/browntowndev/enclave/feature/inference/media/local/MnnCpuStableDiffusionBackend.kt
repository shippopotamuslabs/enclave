package com.browntowndev.enclave.feature.inference.media.local

import com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationError
import com.browntowndev.enclave.localimagegen.LocalImageGenConstants
import com.browntowndev.enclave.localimagegen.NativeImageGenGenerateParams
import com.browntowndev.enclave.localimagegen.NativeLocalImageGenRuntime
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MnnCpuStableDiffusionBackend
    @Inject
    constructor(
        private val nativeRuntime: NativeLocalImageGenRuntime,
    ) : LocalImageGenerationBackend {
        override val kind: LocalImageGenerationBackendKind = LocalImageGenerationBackendKind.CPU_MNN

        override suspend fun generate(
            request: LocalImageGenerationNativeRequest,
        ): Result<LocalImageGenerationNativeResult> =
            runCatching {
                val probe = nativeRuntime.probe(kind)
                if (probe < 2) {
                    throw LocalImageGenerationError.RuntimeNotPackaged(
                        kind,
                        "MNN probe=$probe. Package QNN/MNN runtime and models under ${request.modelDir}.",
                    )
                }
                val outputPath = "${request.outputPathPrefix}.png"
                val started = System.currentTimeMillis()
                val code =
                    nativeRuntime.generate(
                        NativeImageGenGenerateParams(
                            backend = kind,
                            modelDir = request.modelDir,
                            prompt = request.prompt,
                            negativePrompt = request.negativePrompt,
                            width = request.width,
                            height = request.height,
                            steps = request.steps,
                            cfgScale = request.cfgScale,
                            scheduler = request.scheduler,
                            seed = request.seed,
                            outputPath = outputPath,
                            mode = request.mode.toNative(),
                            denoiseStrength = request.denoiseStrength,
                            initImagePath = request.initImagePath,
                            memoryPolicy = request.memoryPolicy.toNative(),
                        ),
                    )
                if (code != 0) {
                    throw LocalImageGenerationError.GenerationFailed(
                        kind,
                        "Native MNN returned code $code",
                    )
                }
                val file = File(outputPath)
                if (!file.exists()) {
                    throw LocalImageGenerationError.GenerationFailed(kind, "Output file missing after MNN run.")
                }
                LocalImageGenerationNativeResult(
                    outputPaths = listOf(file.absolutePath),
                    width = request.width,
                    height = request.height,
                    elapsedMillis = System.currentTimeMillis() - started,
                )
            }

        private fun LocalImageGenerationMode.toNative(): Int =
            when (this) {
                LocalImageGenerationMode.TXT2IMG -> LocalImageGenConstants.MODE_TXT2IMG
                LocalImageGenerationMode.IMG2IMG -> LocalImageGenConstants.MODE_IMG2IMG
            }

        private fun com.browntowndev.enclave.domain.model.media.LocalImageGenerationMemoryPolicy.toNative(): Int =
            when (this) {
                com.browntowndev.enclave.domain.model.media.LocalImageGenerationMemoryPolicy.FULL ->
                    LocalImageGenConstants.MEMORY_POLICY_FULL
                com.browntowndev.enclave.domain.model.media.LocalImageGenerationMemoryPolicy.LOW_RAM ->
                    LocalImageGenConstants.MEMORY_POLICY_LOW_RAM
            }
    }
