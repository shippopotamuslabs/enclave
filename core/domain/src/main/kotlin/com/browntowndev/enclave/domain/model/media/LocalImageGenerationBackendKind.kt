package com.browntowndev.enclave.domain.model.media

enum class LocalImageGenerationBackendKind {
    QNN_NPU,
    CPU_MNN,
    /** Reserved; stable-diffusion.cpp Vulkan is not used in production. */
    GPU_EXPERIMENTAL,
    NONE,
}

enum class BackendPreference {
    AUTO,
    QNN_NPU,
    CPU,
}

enum class LocalImageGenTriState {
    YES,
    NO,
    UNKNOWN,
}
