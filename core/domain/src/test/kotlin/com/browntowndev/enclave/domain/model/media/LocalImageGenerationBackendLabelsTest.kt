package com.browntowndev.enclave.domain.model.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LocalImageGenerationBackendLabelsTest {
    @Test
    fun `rewrites SD15 NPU suffix to CPU when fallback active`() {
        assertEquals(
            "Stable Diffusion 1.5 (CPU)",
            LocalImageGenerationBackendLabels.sd15DeviceAware("Stable Diffusion 1.5 (NPU)", true),
        )
    }

    @Test
    fun `keeps SD15 NPU suffix when fallback inactive`() {
        assertEquals(
            "Stable Diffusion 1.5 (NPU)",
            LocalImageGenerationBackendLabels.sd15DeviceAware("Stable Diffusion 1.5 (NPU)", false),
        )
    }

    @Test
    fun `leaves SDXL names untouched even when fallback active`() {
        assertEquals(
            "Stable Diffusion XL (NPU)",
            LocalImageGenerationBackendLabels.sd15DeviceAware("Stable Diffusion XL (NPU)", true),
        )
    }

    @Test
    fun `leaves names without suffix untouched`() {
        assertEquals(
            "Stable Diffusion 1.5",
            LocalImageGenerationBackendLabels.sd15DeviceAware("Stable Diffusion 1.5", true),
        )
    }
}
