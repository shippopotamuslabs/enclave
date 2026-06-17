package com.browntowndev.enclave.domain.port.media

/**
 * Product gate for on-device image generation (StableDream-style QNN + MNN stack).
 * Vulkan / stable-diffusion.cpp is not used.
 */
const val LOCAL_ON_DEVICE_IMAGE_GENERATION_ENABLED: Boolean = true

const val LOCAL_IMAGE_GENERATION_UNSUPPORTED_MESSAGE =
    "On-device image generation is not available on this device. Use a cloud image provider in Model Configuration."

interface GpuCapabilityPort {
    fun isLocalImageGenerationSupported(): Boolean
}
