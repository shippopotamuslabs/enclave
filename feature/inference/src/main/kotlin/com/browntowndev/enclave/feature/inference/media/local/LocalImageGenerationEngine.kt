package com.browntowndev.enclave.feature.inference.media.local

import com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationError
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalImageGenerationEngine
    @Inject
    constructor(
        private val qnnBackend: QnnStableDiffusionBackend,
        private val mnnBackend: MnnCpuStableDiffusionBackend,
    ) {
        @Volatile
        private var cancelled: Boolean = false

        fun cancel() {
            cancelled = true
        }

        suspend fun generate(request: LocalImageGenerationNativeRequest): Result<LocalImageGenerationNativeResult> {
            cancelled = false
            if (cancelled) {
                return Result.failure(LocalImageGenerationError.Cancelled())
            }
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
