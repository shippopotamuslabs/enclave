package com.browntowndev.enclave.feature.inference.media.local

import com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationError
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId
import com.browntowndev.enclave.domain.model.media.QnnRuntimeLibraryCatalog
import com.browntowndev.enclave.localimagegen.NativeLocalImageGenRuntime
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QnnStableDiffusionBackend
    @Inject
    constructor(
        private val nativeRuntime: NativeLocalImageGenRuntime,
    ) : LocalImageGenerationBackend {
        override val kind: LocalImageGenerationBackendKind = LocalImageGenerationBackendKind.QNN_NPU

        override suspend fun generate(
            request: LocalImageGenerationNativeRequest,
        ): Result<LocalImageGenerationNativeResult> =
            runCatching {
                nativeRuntime.ensureQnnRuntimeForModel(request.modelId)
                val probe = nativeRuntime.probe(kind)
                if (probe < 2) {
                    throw LocalImageGenerationError.RuntimeNotPackaged(
                        kind,
                        "QNN probe=$probe. Package libQnnHtp.so and model under ${request.modelDir}.",
                    )
                }
                val skelName = QnnRuntimeLibraryCatalog.primarySkelLibraryFor(request.modelId)
                if (!nativeRuntime.isQnnRuntimeReadyForModel(request.modelId)) {
                    throw LocalImageGenerationError.RuntimeNotPackaged(
                        kind,
                        "Missing QNN runtime $skelName for ${request.modelId}. " +
                            "Re-sync QNN assets (scripts/sync-qnn-jniLibs.sh) and reinstall the app.",
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
                        scheduler = request.scheduler,
                        seed = request.seed,
                        outputPath = outputPath,
                    )
                if (code != 0) {
                    throw LocalImageGenerationError.GenerationFailed(
                        kind,
                        "Native QNN returned code $code",
                    )
                }
                val file = File(outputPath)
                if (!file.exists()) {
                    throw LocalImageGenerationError.GenerationFailed(kind, "Output file missing after QNN run.")
                }
                LocalImageGenerationNativeResult(
                    outputPaths = listOf(file.absolutePath),
                    width = request.width,
                    height = request.height,
                    elapsedMillis = System.currentTimeMillis() - started,
                )
            }
    }
