package com.browntowndev.enclave.domain.model.media

import com.browntowndev.enclave.domain.model.device.SnapdragonTier

/**
 * Qualcomm QNN / Hexagon libraries required at runtime for Stable Diffusion QNN backend.
 * APK assets should include only the set for the targeted tier to limit size.
 */
object QnnRuntimeLibraryCatalog {
    val coreLibraries: List<String> =
        listOf(
            "libQnnHtp.so",
            "libQnnSystem.so",
            "libQnnHtpPrepare.so",
        )

    fun runtimeLibrariesFor(tier: SnapdragonTier): List<String> =
        coreLibraries + hexagonLibrariesFor(tier)

    /** Hexagon variant required by a specific on-device image model's compiled QNN graphs. */
    fun runtimeLibrariesForModel(modelId: LocalImageGenerationModelId): List<String> =
        coreLibraries + hexagonLibrariesForModel(modelId)

    /** Primary skeleton library that must exist before launching QNN for [modelId]. */
    fun primarySkelLibraryFor(modelId: LocalImageGenerationModelId): String =
        when (modelId) {
            LocalImageGenerationModelId.SDXL_QNN -> "libQnnHtpV79Skel.so"
            LocalImageGenerationModelId.SD15_QNN,
            LocalImageGenerationModelId.SD15_CPU,
            -> "libQnnHtpV73Skel.so"
        }

    private fun hexagonLibrariesForModel(modelId: LocalImageGenerationModelId): List<String> =
        when (modelId) {
            LocalImageGenerationModelId.SDXL_QNN ->
                variant(
                    stub = "libQnnHtpV79Stub.so",
                    lib = "libQnnHtpV79.so",
                    skel = "libQnnHtpV79Skel.so",
                )
            LocalImageGenerationModelId.SD15_QNN ->
                variant(
                    stub = "libQnnHtpV73Stub.so",
                    lib = "libQnnHtpV73.so",
                    skel = "libQnnHtpV73Skel.so",
                )
            LocalImageGenerationModelId.SD15_CPU -> emptyList()
        }

    private fun hexagonLibrariesFor(tier: SnapdragonTier): List<String> =
        when (tier) {
            // Gen3+ runs SD1.5 from the 8gen2 (V73) pack and SDXL from the 8gen3 (V79) pack.
            SnapdragonTier.GEN3_PLUS ->
                hexagonLibrariesForModel(LocalImageGenerationModelId.SD15_QNN) +
                    hexagonLibrariesForModel(LocalImageGenerationModelId.SDXL_QNN) +
                    variant(
                        stub = "libQnnHtpV81Stub.so",
                        lib = "libQnnHtpV81.so",
                        skel = "libQnnHtpV81Skel.so",
                    )
            SnapdragonTier.GEN2,
            SnapdragonTier.GEN1,
            ->
                variant(
                    stub = "libQnnHtpV73Stub.so",
                    lib = "libQnnHtpV73.so",
                    skel = "libQnnHtpV73Skel.so",
                ) +
                    variant(
                        stub = "libQnnHtpV75Stub.so",
                        lib = "libQnnHtpV75.so",
                        skel = "libQnnHtpV75Skel.so",
                    ) +
                    variant(
                        stub = "libQnnHtpV79Stub.so",
                        lib = "libQnnHtpV79.so",
                        skel = "libQnnHtpV79Skel.so",
                    )
            SnapdragonTier.UNKNOWN ->
                variant(
                    stub = "libQnnHtpV69Stub.so",
                    lib = "libQnnHtpV69.so",
                    skel = "libQnnHtpV69Skel.so",
                ) +
                    variant(
                        stub = "libQnnHtpV73Stub.so",
                        lib = "libQnnHtpV73.so",
                        skel = "libQnnHtpV73Skel.so",
                    )
        }

    private fun variant(
        stub: String,
        lib: String,
        skel: String,
    ): List<String> = listOf(stub, lib, skel)
}
