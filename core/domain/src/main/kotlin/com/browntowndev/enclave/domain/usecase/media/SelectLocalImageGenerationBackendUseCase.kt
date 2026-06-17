package com.browntowndev.enclave.domain.usecase.media

import com.browntowndev.enclave.domain.model.media.BackendPreference
import com.browntowndev.enclave.domain.model.media.LocalImageGenTriState
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationCapabilities
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelFamily
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId
import javax.inject.Inject

data class LocalImageGenerationBackendSelection(
    val backend: LocalImageGenerationBackendKind,
    val modelId: LocalImageGenerationModelId?,
    val reasons: List<String>,
)

class SelectLocalImageGenerationBackendUseCase
    @Inject
    constructor() {
        operator fun invoke(
            family: LocalImageGenerationModelFamily,
            capabilities: LocalImageGenerationCapabilities,
            preference: BackendPreference = BackendPreference.AUTO,
            requestedModelId: LocalImageGenerationModelId? = null,
        ): LocalImageGenerationBackendSelection {
            if (requestedModelId != null) {
                return selectionForModel(requestedModelId, capabilities)
            }
            return when (family) {
                LocalImageGenerationModelFamily.SD15 -> selectSd15(capabilities, preference)
                LocalImageGenerationModelFamily.SDXL -> selectSdxl(capabilities, preference)
            }
        }

        private fun selectSd15(
            capabilities: LocalImageGenerationCapabilities,
            preference: BackendPreference,
        ): LocalImageGenerationBackendSelection {
            val reasons = mutableListOf<String>()
            when (preference) {
                BackendPreference.QNN_NPU ->
                    if (capabilities.sd15NpuSupported == LocalImageGenTriState.YES) {
                        return LocalImageGenerationBackendSelection(
                            LocalImageGenerationBackendKind.QNN_NPU,
                            LocalImageGenerationModelId.SD15_QNN,
                            listOf("User requested NPU for SD1.5."),
                        )
                    } else {
                        reasons += "NPU requested but SD1.5 NPU not available."
                    }
                BackendPreference.CPU ->
                    if (capabilities.sd15CpuSupported == LocalImageGenTriState.YES) {
                        return LocalImageGenerationBackendSelection(
                            LocalImageGenerationBackendKind.CPU_MNN,
                            LocalImageGenerationModelId.SD15_CPU,
                            listOf("User requested CPU for SD1.5."),
                        )
                    } else {
                        reasons += "CPU requested but SD1.5 CPU runtime not available."
                    }
                BackendPreference.AUTO -> Unit
            }
            if (capabilities.sd15NpuSupported == LocalImageGenTriState.YES) {
                return LocalImageGenerationBackendSelection(
                    LocalImageGenerationBackendKind.QNN_NPU,
                    LocalImageGenerationModelId.SD15_QNN,
                    listOf("SD1.5: prefer Hexagon NPU (QNN)."),
                )
            }
            if (capabilities.sd15CpuSupported == LocalImageGenTriState.YES) {
                return LocalImageGenerationBackendSelection(
                    LocalImageGenerationBackendKind.CPU_MNN,
                    LocalImageGenerationModelId.SD15_CPU,
                    listOf("SD1.5: NPU unavailable; using CPU (MNN) fallback."),
                )
            }
            reasons += "No SD1.5 backend available."
            return LocalImageGenerationBackendSelection(
                LocalImageGenerationBackendKind.NONE,
                null,
                reasons,
            )
        }

        private fun selectSdxl(
            capabilities: LocalImageGenerationCapabilities,
            preference: BackendPreference,
        ): LocalImageGenerationBackendSelection {
            if (preference == BackendPreference.CPU) {
                return LocalImageGenerationBackendSelection(
                    LocalImageGenerationBackendKind.NONE,
                    null,
                    listOf("SDXL CPU fallback is disabled by policy."),
                )
            }
            if (capabilities.sdxlNpuSupported == LocalImageGenTriState.YES) {
                return LocalImageGenerationBackendSelection(
                    LocalImageGenerationBackendKind.QNN_NPU,
                    LocalImageGenerationModelId.SDXL_QNN,
                    listOf("SDXL: NPU-only on supported Snapdragon 8 Gen 3+ class devices."),
                )
            }
            return LocalImageGenerationBackendSelection(
                LocalImageGenerationBackendKind.NONE,
                null,
                listOf("SDXL requires QNN NPU on Snapdragon 8 Gen 3+; not available on this device."),
            )
        }

        private fun selectionForModel(
            modelId: LocalImageGenerationModelId,
            capabilities: LocalImageGenerationCapabilities,
        ): LocalImageGenerationBackendSelection =
            when (modelId) {
                LocalImageGenerationModelId.SD15_QNN -> selectionForAssignedSd15Qnn(capabilities)
                LocalImageGenerationModelId.SD15_CPU -> selectionForAssignedSd15Cpu(capabilities)
                LocalImageGenerationModelId.SDXL_QNN ->
                    selectionForAssignedNpuOnlyModel(
                        modelId = modelId,
                        npuSupported = capabilities.sdxlNpuSupported,
                    )
                LocalImageGenerationModelId.JUGGERNAUT_XL_QNN ->
                    selectionForAssignedNpuOnlyModel(
                        modelId = modelId,
                        npuSupported = capabilities.juggernautXlNpuSupported,
                    )
            }

        private fun selectionForAssignedSd15Qnn(
            capabilities: LocalImageGenerationCapabilities,
        ): LocalImageGenerationBackendSelection {
            if (capabilities.sd15NpuSupported == LocalImageGenTriState.YES) {
                return LocalImageGenerationBackendSelection(
                    LocalImageGenerationBackendKind.QNN_NPU,
                    LocalImageGenerationModelId.SD15_QNN,
                    listOf("Using assigned catalog model SD15_QNN."),
                )
            }
            if (capabilities.sd15CpuSupported == LocalImageGenTriState.YES) {
                val fallback = selectSd15(capabilities, BackendPreference.AUTO)
                return fallback.copy(
                    reasons =
                        listOf(
                            "Assigned SD1.5 NPU unavailable; ${fallback.reasons.firstOrNull() ?: "using CPU fallback."}",
                        ),
                )
            }
            return LocalImageGenerationBackendSelection(
                LocalImageGenerationBackendKind.NONE,
                null,
                listOf("Assigned SD1.5 NPU model is not supported on this device."),
            )
        }

        private fun selectionForAssignedSd15Cpu(
            capabilities: LocalImageGenerationCapabilities,
        ): LocalImageGenerationBackendSelection {
            if (capabilities.sd15CpuSupported == LocalImageGenTriState.YES) {
                return LocalImageGenerationBackendSelection(
                    LocalImageGenerationBackendKind.CPU_MNN,
                    LocalImageGenerationModelId.SD15_CPU,
                    listOf("Using assigned catalog model SD15_CPU."),
                )
            }
            return LocalImageGenerationBackendSelection(
                LocalImageGenerationBackendKind.NONE,
                null,
                listOf("Assigned SD1.5 CPU model is not supported on this device."),
            )
        }

        private fun selectionForAssignedNpuOnlyModel(
            modelId: LocalImageGenerationModelId,
            npuSupported: LocalImageGenTriState,
        ): LocalImageGenerationBackendSelection {
            if (npuSupported != LocalImageGenTriState.YES) {
                return LocalImageGenerationBackendSelection(
                    LocalImageGenerationBackendKind.NONE,
                    null,
                    listOf("Assigned model $modelId is not supported on this device."),
                )
            }
            return LocalImageGenerationBackendSelection(
                LocalImageGenerationBackendKind.QNN_NPU,
                modelId,
                listOf("Using assigned catalog model $modelId."),
            )
        }
    }
