package com.browntowndev.enclave.domain.model.download

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SdxlQnnPackCatalogTest {
    @Test
    fun `defaultVariant returns correct metadata`() {
        val variant = SdxlQnnPackCatalog.defaultVariant()
        assertEquals("realvis_xl_v5_dmd2_qnn2.28_8gen3.zip", variant.zipFileName)
        assertEquals("5250591bc45e58490c2fd3c0e0e7d4d166ba1aa6f443d5ebdb962c95a9e99f83", variant.sha256)
        assertEquals(3_504_485_672L, variant.sizeInBytes)
    }

    @Test
    fun `allVariants includes defaultVariant`() {
        val all = SdxlQnnPackCatalog.allVariants()
        assertEquals(1, all.size)
        assertEquals(SdxlQnnPackCatalog.defaultVariant(), all.first())
    }

    @Test
    fun `findBySha256 resolves correctly`() {
        val resolved = SdxlQnnPackCatalog.findBySha256("5250591bc45e58490c2fd3c0e0e7d4d166ba1aa6f443d5ebdb962c95a9e99f83")
        assertNotNull(resolved)
        assertEquals("realvis_xl_v5_dmd2_qnn2.28_8gen3.zip", resolved?.zipFileName)

        val invalid = SdxlQnnPackCatalog.findBySha256("invalid_sha")
        assertNull(invalid)
    }
}
