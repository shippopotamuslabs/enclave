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
        ): LocalImageGenerationBackendSelection {
            val family =
                when (modelId) {
                    LocalImageGenerationModelId.SD15_QNN,
                    LocalImageGenerationModelId.SD15_CPU,
                    -> LocalImageGenerationModelFamily.SD15
                    LocalImageGenerationModelId.SDXL_QNN -> LocalImageGenerationModelFamily.SDXL
                }
            val auto = invoke(family, capabilities, BackendPreference.AUTO, requestedModelId = null)
            if (auto.modelId == modelId) {
                return auto.copy(reasons = auto.reasons + "Matched assigned model $modelId.")
            }
            val backend =
                when (modelId) {
                    LocalImageGenerationModelId.SD15_QNN -> LocalImageGenerationBackendKind.QNN_NPU
                    LocalImageGenerationModelId.SD15_CPU -> LocalImageGenerationBackendKind.CPU_MNN
                    LocalImageGenerationModelId.SDXL_QNN -> LocalImageGenerationBackendKind.QNN_NPU
                }
            val allowed =
                when (modelId) {
                    LocalImageGenerationModelId.SD15_QNN ->
                        capabilities.sd15NpuSupported == LocalImageGenTriState.YES
                    LocalImageGenerationModelId.SD15_CPU ->
                        capabilities.sd15CpuSupported == LocalImageGenTriState.YES
                    LocalImageGenerationModelId.SDXL_QNN ->
                        capabilities.sdxlNpuSupported == LocalImageGenTriState.YES
                }
            if (!allowed) {
                return LocalImageGenerationBackendSelection(
                    LocalImageGenerationBackendKind.NONE,
                    null,
                    listOf("Assigned model $modelId is not supported on this device."),
                )
            }
            return LocalImageGenerationBackendSelection(
                backend,
                modelId,
                listOf("Using assigned catalog model $modelId."),
            )
        }
    }
