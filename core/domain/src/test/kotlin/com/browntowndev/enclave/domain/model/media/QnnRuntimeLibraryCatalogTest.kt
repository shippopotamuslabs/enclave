package com.browntowndev.enclave.domain.model.media

import com.browntowndev.enclave.domain.model.device.SnapdragonTier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QnnRuntimeLibraryCatalogTest {
    @Test
    fun `Gen3 includes V73 and V81 skel for SD15 and SDXL packs`() {
        val libraries = QnnRuntimeLibraryCatalog.runtimeLibrariesFor(SnapdragonTier.GEN3_PLUS)

        assertTrue("libQnnHtpV73Skel.so" in libraries)
        assertTrue("libQnnHtpV81Skel.so" in libraries)
        assertTrue("libQnnHtpV79Skel.so" in libraries)
    }

    @Test
    fun `SDXL model runtime uses V81 hexagon family`() {
        val libraries =
            QnnRuntimeLibraryCatalog.runtimeLibrariesForModel(LocalImageGenerationModelId.SDXL_QNN)

        assertTrue("libQnnHtpV81Skel.so" in libraries)
        assertEquals(
            "libQnnHtpV81Skel.so",
            QnnRuntimeLibraryCatalog.primarySkelLibraryFor(LocalImageGenerationModelId.SDXL_QNN),
        )
    }

    @Test
    fun `SD15 QNN model runtime uses V73 hexagon family`() {
        val libraries =
            QnnRuntimeLibraryCatalog.runtimeLibrariesForModel(LocalImageGenerationModelId.SD15_QNN)

        assertTrue("libQnnHtpV73Skel.so" in libraries)
        assertEquals(
            "libQnnHtpV73Skel.so",
            QnnRuntimeLibraryCatalog.primarySkelLibraryFor(LocalImageGenerationModelId.SD15_QNN),
        )
    }
}
