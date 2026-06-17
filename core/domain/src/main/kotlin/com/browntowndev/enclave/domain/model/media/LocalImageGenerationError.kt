package com.browntowndev.enclave.domain.model.media

sealed class LocalImageGenerationError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class UnsupportedDevice(
        reasons: List<String>,
    ) : LocalImageGenerationError(
            "This device does not support on-device image generation. ${reasons.joinToString("; ")}",
        )

    class RuntimeNotPackaged(
        backend: LocalImageGenerationBackendKind,
        detail: String,
    ) : LocalImageGenerationError(
            "${backend.name} runtime not available: $detail",
        )

    class ModelNotReady(
        modelId: LocalImageGenerationModelId,
        state: LocalImageGenerationInstallState,
    ) : LocalImageGenerationError(
            "Model $modelId is not ready (state=$state).",
        )

    class GenerationFailed(
        backend: LocalImageGenerationBackendKind,
        detail: String,
        cause: Throwable? = null,
    ) : LocalImageGenerationError(
            "Local image generation failed on ${backend.name}: $detail",
            cause,
        )

    class Cancelled : LocalImageGenerationError("Image generation cancelled.")

    class Busy : LocalImageGenerationError("Another on-device inference task is running.")
}
