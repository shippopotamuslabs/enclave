package com.browntowndev.enclave.feature.inference.media.local

import com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationError
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
                        "MNN probe=$probe. Package libMNN.so and SD1.5 CPU models under ${request.modelDir}.",
                    )
                }
                val outputPath = "${request.outputPathPrefix}.png"
                val started = System.currentTimeMillis()
                val code =
                    nativeRuntime.generate(
                        backend = kind,
                        modelDir = request.modelDir,
                        prompt = request.prompt,
                        negativePrompt = request.negativePrompt,
                        width = request.width,
                        height = request.height,
                        steps = request.steps,
                        cfgScale = request.cfgScale,
                        seed = request.seed,
                        outputPath = outputPath,
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
    }
