package com.browntowndev.enclave.localimagegen

import android.content.Context
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId
import com.browntowndev.enclave.domain.model.media.QnnRuntimeLibraryCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Low-level native runtime probes and blocking generate calls (invoke off main thread). */
@Singleton
class NativeLocalImageGenRuntime
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        private val qnnRuntimeDir = File(context.filesDir, QNN_RUNTIME_SUBDIR)

        init {
            QnnRuntimeBootstrap.install(context, qnnRuntimeDir)
            LocalImageGenNative.nativeInit(
                nativeLibraryDir = context.applicationInfo.nativeLibraryDir,
                qnnRuntimeDir = qnnRuntimeDir.absolutePath,
            )
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
            QnnRuntimeBootstrap.installLibraries(
                context,
                qnnRuntimeDir,
                QnnRuntimeLibraryCatalog.runtimeLibrariesForModel(modelId),
            )
        }

        fun isQnnRuntimeReadyForModel(modelId: LocalImageGenerationModelId): Boolean {
            val skel = File(qnnRuntimeDir, QnnRuntimeLibraryCatalog.primarySkelLibraryFor(modelId))
            return skel.isFile && skel.length() > 0L
        }

        fun generate(
            backend: LocalImageGenerationBackendKind,
            modelDir: String,
            prompt: String,
            negativePrompt: String?,
            width: Int,
            height: Int,
            steps: Int,
            cfgScale: Float,
            scheduler: String,
            seed: Long,
            outputPath: String,
        ): Int =
            when (backend) {
                LocalImageGenerationBackendKind.QNN_NPU ->
                    LocalImageGenNative.nativeGenerateQnn(
                        modelDir,
                        prompt,
                        negativePrompt,
                        width,
                        height,
                        steps,
                        cfgScale,
                        scheduler,
                        seed,
                        outputPath,
                    )
                LocalImageGenerationBackendKind.CPU_MNN ->
                    LocalImageGenNative.nativeGenerateMnn(
                        modelDir,
                        prompt,
                        negativePrompt,
                        width,
                        height,
                        steps,
                        cfgScale,
                        scheduler,
                        seed,
                        outputPath,
                    )
                LocalImageGenerationBackendKind.GPU_EXPERIMENTAL,
                LocalImageGenerationBackendKind.NONE,
                -> -1
            }

        private companion object {
            const val QNN_RUNTIME_SUBDIR = "local_image_gen_qnn_runtime"
        }
    }
