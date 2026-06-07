package com.browntowndev.enclave.domain.usecase.media

import com.browntowndev.enclave.domain.model.media.BackendPreference
import com.browntowndev.enclave.domain.model.media.LocalImageGenTriState
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationCapabilities
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelFamily
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SelectLocalImageGenerationBackendUseCaseTest {
    private val useCase = SelectLocalImageGenerationBackendUseCase()

    @Test
    fun `SD15 prefers QNN when NPU available`() {
        val selection =
            useCase(
                family = LocalImageGenerationModelFamily.SD15,
                capabilities = capabilities(sd15Npu = LocalImageGenTriState.YES),
            )
        assertEquals(LocalImageGenerationBackendKind.QNN_NPU, selection.backend)
        assertEquals(LocalImageGenerationModelId.SD15_QNN, selection.modelId)
    }

    @Test
    fun `SD15 falls back to CPU when NPU unavailable`() {
        val selection =
            useCase(
                family = LocalImageGenerationModelFamily.SD15,
                capabilities =
                    capabilities(
                        sd15Npu = LocalImageGenTriState.NO,
                        sd15Cpu = LocalImageGenTriState.YES,
                    ),
            )
        assertEquals(LocalImageGenerationBackendKind.CPU_MNN, selection.backend)
        assertEquals(LocalImageGenerationModelId.SD15_CPU, selection.modelId)
    }

    @Test
    fun `assigned SD15 QNN is rejected when NPU support is unknown and CPU unavailable`() {
        val selection =
            useCase(
                family = LocalImageGenerationModelFamily.SD15,
                capabilities = capabilities(sd15Npu = LocalImageGenTriState.UNKNOWN),
                requestedModelId = LocalImageGenerationModelId.SD15_QNN,
            )

        assertEquals(LocalImageGenerationBackendKind.NONE, selection.backend)
    }

    @Test
    fun `assigned SD15 QNN falls back to CPU when NPU unavailable`() {
        val selection =
            useCase(
                family = LocalImageGenerationModelFamily.SD15,
                capabilities =
                    capabilities(
                        sd15Npu = LocalImageGenTriState.NO,
                        sd15Cpu = LocalImageGenTriState.YES,
                    ),
                requestedModelId = LocalImageGenerationModelId.SD15_QNN,
            )

        assertEquals(LocalImageGenerationBackendKind.CPU_MNN, selection.backend)
        assertEquals(LocalImageGenerationModelId.SD15_CPU, selection.modelId)
    }

    @Test
    fun `capabilities are usable only when a backend is confirmed`() {
        val capabilities =
            capabilities(
                sd15Npu = LocalImageGenTriState.UNKNOWN,
                sd15Cpu = LocalImageGenTriState.NO,
            )

        assertEquals(false, capabilities.isAnyBackendUsable)
    }

    @Test
    fun `SDXL requires NPU on Gen3 class devices`() {
        val selection =
            useCase(
                family = LocalImageGenerationModelFamily.SDXL,
                capabilities = capabilities(sdxlNpu = LocalImageGenTriState.YES),
            )
        assertEquals(LocalImageGenerationBackendKind.QNN_NPU, selection.backend)
        assertEquals(LocalImageGenerationModelId.SDXL_QNN, selection.modelId)
    }

    @Test
    fun `SDXL rejects CPU preference`() {
        val selection =
            useCase(
                family = LocalImageGenerationModelFamily.SDXL,
                capabilities = capabilities(sdxlNpu = LocalImageGenTriState.YES),
                preference = BackendPreference.CPU,
            )
        assertEquals(LocalImageGenerationBackendKind.NONE, selection.backend)
    }

    private fun capabilities(
        sd15Npu: LocalImageGenTriState = LocalImageGenTriState.NO,
        sd15Cpu: LocalImageGenTriState = LocalImageGenTriState.NO,
        sdxlNpu: LocalImageGenTriState = LocalImageGenTriState.NO,
    ): LocalImageGenerationCapabilities =
        LocalImageGenerationCapabilities(
            npuAvailable = if (sd15Npu == LocalImageGenTriState.YES) LocalImageGenTriState.YES else LocalImageGenTriState.NO,
            sd15NpuSupported = sd15Npu,
            sd15CpuSupported = sd15Cpu,
            sdxlNpuSupported = sdxlNpu,
            recommendedBackend = LocalImageGenerationBackendKind.NONE,
            recommendedModelId = null,
            diagnostics = emptyList(),
        )
}
