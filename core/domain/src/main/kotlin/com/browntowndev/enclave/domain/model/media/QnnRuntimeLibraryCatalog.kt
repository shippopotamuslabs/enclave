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
        )

    fun runtimeLibrariesFor(tier: SnapdragonTier): List<String> =
        coreLibraries + hexagonLibrariesFor(tier)

    private fun hexagonLibrariesFor(tier: SnapdragonTier): List<String> =
        when (tier) {
            // Gen3+ devices run the 8gen2 (HTP V73) SD pack — there is no published
            // 8gen3+ archive (see [com.browntowndev.enclave.domain.model.download.Sd15QnnPackVariant]).
            // The runtime skel must match the pack's compiled Hexagon version (V73), not the
            // device's native version, or QNN init dereferences a null backend and crashes.
            // V79 is retained for the device-native arch / forward compatibility.
            SnapdragonTier.GEN3_PLUS ->
                variant(
                    stub = "libQnnHtpV73Stub.so",
                    lib = "libQnnHtpV73.so",
                    skel = "libQnnHtpV73Skel.so",
                ) +
                    variant(
                        stub = "libQnnHtpV79Stub.so",
                        lib = "libQnnHtpV79.so",
                        skel = "libQnnHtpV79Skel.so",
                    )
            SnapdragonTier.GEN2,
            SnapdragonTier.GEN1 ->
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
