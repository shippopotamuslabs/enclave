package com.browntowndev.enclave.domain.model.media

import com.browntowndev.enclave.domain.model.device.SnapdragonTier
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QnnRuntimeLibraryCatalogTest {
    @Test
    fun `Gen3 includes V73 skel so the 8gen2 pack can load on newer SoCs`() {
        // Gen3+ devices run the 8gen2 (HTP V73) SD pack, so the V73 runtime family must be
        // present at runtime even though the device's native Hexagon version is newer.
        // Without V73 the backend dereferences a null QNN backend and crashes (regression guard).
        val libraries = QnnRuntimeLibraryCatalog.runtimeLibrariesFor(SnapdragonTier.GEN3_PLUS)

        assertTrue("libQnnHtpV73Skel.so" in libraries)
        assertTrue("libQnnHtpV73Stub.so" in libraries)
        assertTrue("libQnnHtpV73.so" in libraries)
        assertTrue("libQnnHtpV79.so" in libraries)
        assertFalse(libraries.any { it.contains("V81") })
    }
}
