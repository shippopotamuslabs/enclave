package com.browntowndev.enclave.localimagegen

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId
import com.browntowndev.enclave.domain.model.media.QnnRuntimeLibraryCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

data class NativeImageGenGenerateParams(
    val backend: LocalImageGenerationBackendKind,
    val modelDir: String,
    val prompt: String,
    val negativePrompt: String?,
    val width: Int,
    val height: Int,
    val steps: Int,
    val cfgScale: Float,
    val scheduler: String,
    val seed: Long,
    val outputPath: String,
    val mode: Int,
    val denoiseStrength: Float,
    val initImagePath: String?,
    val memoryPolicy: Int,
)

/** Low-level native runtime probes and blocking generate calls (invoke off main thread). */
@Singleton
class NativeLocalImageGenRuntime
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : ComponentCallbacks2 {
        private val qnnRuntime = QnnRuntimeDirResolver.resolve(context)
        private val trimExecutor = Executors.newSingleThreadExecutor()

        init {
            LocalImageGenNative.nativeInit(
                nativeLibraryDir = context.applicationInfo.nativeLibraryDir,
                qnnRuntimeDir = qnnRuntime.directory.absolutePath,
            )
            context.registerComponentCallbacks(this)
        }

        fun probe(backend: LocalImageGenerationBackendKind): Int =
            when (backend) {
                LocalImageGenerationBackendKind.QNN_NPU -> LocalImageGenNative.nativeProbeQnn()
                LocalImageGenerationBackendKind.CPU_MNN -> LocalImageGenNative.nativeProbeMnn()
                LocalImageGenerationBackendKind.GPU_EXPERIMENTAL,
                LocalImageGenerationBackendKind.NONE,
                -> 0
            }

        fun ensureQnnRuntimeForModel(modelId: LocalImageGenerationModelId) {
            if (qnnRuntime.usesJniLibs) {
                return
            }
            QnnRuntimeBootstrap.installLibraries(
                context,
                qnnRuntime.directory,
                QnnRuntimeLibraryCatalog.runtimeLibrariesForModel(modelId),
            )
        }

        fun isQnnRuntimeReadyForModel(modelId: LocalImageGenerationModelId): Boolean {
            val skel =
                File(qnnRuntime.directory, QnnRuntimeLibraryCatalog.primarySkelLibraryFor(modelId))
            return skel.isFile && skel.length() > 0L
        }

        fun cancel() {
            LocalImageGenNative.nativeCancel()
        }

        fun currentGenerationProgress(): Pair<Int, Int>? {
            val values = LocalImageGenNative.nativeGetGenerationProgress() ?: return null
            if (values.size < 2) return null
            val total = values[1]
            if (total <= 0) return null
            return values[0] to total
        }

        fun generate(params: NativeImageGenGenerateParams): Int =
            when (params.backend) {
                LocalImageGenerationBackendKind.QNN_NPU ->
                    LocalImageGenNative.nativeGenerateQnn(
                        params.modelDir,
                        params.prompt,
                        params.negativePrompt,
                        params.width,
                        params.height,
                        params.steps,
                        params.cfgScale,
                        params.scheduler,
                        params.seed,
                        params.outputPath,
                        params.mode,
                        params.denoiseStrength,
                        params.initImagePath,
                        params.memoryPolicy,
                    )
                LocalImageGenerationBackendKind.CPU_MNN ->
                    LocalImageGenNative.nativeGenerateMnn(
                        params.modelDir,
                        params.prompt,
                        params.negativePrompt,
                        params.width,
                        params.height,
                        params.steps,
                        params.cfgScale,
                        params.scheduler,
                        params.seed,
                        params.outputPath,
                        params.mode,
                        params.denoiseStrength,
                        params.initImagePath,
                        params.memoryPolicy,
                    )
                LocalImageGenerationBackendKind.GPU_EXPERIMENTAL,
                LocalImageGenerationBackendKind.NONE,
                -> -1
            }

        override fun onTrimMemory(level: Int) {
            // ComponentCallbacks2 runs on the main thread; native trim must not block on generation.
            trimExecutor.execute {
                LocalImageGenNative.nativeOnTrimMemory(level)
            }
        }

        override fun onConfigurationChanged(newConfig: Configuration) = Unit

        override fun onLowMemory() {
            onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        }

    }
