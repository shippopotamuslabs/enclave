package com.browntowndev.enclave.feature.inference.media.local

import com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationError
import com.browntowndev.enclave.localimagegen.NativeLocalImageGenRuntime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalImageGenerationEngine
    @Inject
    constructor(
        private val qnnBackend: QnnStableDiffusionBackend,
        private val mnnBackend: MnnCpuStableDiffusionBackend,
        private val nativeRuntime: NativeLocalImageGenRuntime,
    ) {
        fun cancel() {
            nativeRuntime.cancel()
            qnnBackend.cancel()
            mnnBackend.cancel()
        }

        suspend fun generate(request: LocalImageGenerationNativeRequest): Result<LocalImageGenerationNativeResult> {
            val backend =
                when (request.backend) {
                    LocalImageGenerationBackendKind.QNN_NPU -> qnnBackend
                    LocalImageGenerationBackendKind.CPU_MNN -> mnnBackend
                    LocalImageGenerationBackendKind.GPU_EXPERIMENTAL,
                    LocalImageGenerationBackendKind.NONE,
                    ->
                        return Result.failure(
                            LocalImageGenerationError.UnsupportedDevice(
                                listOf("Backend ${request.backend} is not available."),
                            ),
                        )
                }
            return backend.generate(request)
        }
    }
