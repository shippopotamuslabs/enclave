package com.browntowndev.enclave.domain.model.media

import com.browntowndev.enclave.domain.model.device.SnapdragonTier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QnnRuntimeLibraryCatalogTest {
    @Test
    fun `Gen3 includes V73 V79 and V81 skel for SD15 SDXL and elite tier`() {
        val libraries = QnnRuntimeLibraryCatalog.runtimeLibrariesFor(SnapdragonTier.GEN3_PLUS)

        assertTrue("libQnnHtpV73Skel.so" in libraries)
        assertTrue("libQnnHtpV79Skel.so" in libraries)
        assertTrue("libQnnHtpV81Skel.so" in libraries)
    }

    @Test
    fun `SDXL model runtime uses V79 hexagon family for 8gen3 pack`() {
        val libraries =
            QnnRuntimeLibraryCatalog.runtimeLibrariesForModel(LocalImageGenerationModelId.SDXL_QNN)

        assertTrue("libQnnHtpV79Skel.so" in libraries)
        assertEquals(
            "libQnnHtpV79Skel.so",
            QnnRuntimeLibraryCatalog.primarySkelLibraryFor(LocalImageGenerationModelId.SDXL_QNN),
        )
    }

    @Test
    fun `core libraries include HTP prepare`() {
        assertTrue("libQnnHtpPrepare.so" in QnnRuntimeLibraryCatalog.coreLibraries)
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
