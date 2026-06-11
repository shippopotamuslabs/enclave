package com.browntowndev.enclave.domain.model.media

object LocalImageGenerationBackendLabels {
    /**
     * Catalog SD 1.5 labels are authored for the NPU pathway ("Stable Diffusion 1.5 (NPU)").
     * On devices whose Hexagon DSP cannot run the QNN graphs, generation uses the MNN CPU
     * fallback, so the "(NPU)" suffix is rewritten to "(CPU)". Names without the suffix or
     * outside the SD 1.5 family (e.g. SDXL, which has no CPU pathway) pass through unchanged.
     */
    fun sd15DeviceAware(
        name: String,
        sd15CpuFallbackActive: Boolean,
    ): String =
        if (sd15CpuFallbackActive && name.contains("1.5") && name.contains("(NPU)")) {
            name.replace("(NPU)", "(CPU)")
        } else {
            name
        }

    /** True when SD 1.5 routes to the MNN CPU backend instead of the NPU on this device. */
    fun sd15CpuFallbackActive(capabilities: LocalImageGenerationCapabilities): Boolean =
        capabilities.sd15NpuSupported != LocalImageGenTriState.YES &&
            capabilities.sd15CpuSupported == LocalImageGenTriState.YES
}
