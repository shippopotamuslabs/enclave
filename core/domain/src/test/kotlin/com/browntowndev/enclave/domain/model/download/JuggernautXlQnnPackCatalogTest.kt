package com.browntowndev.enclave.domain.model.download

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class JuggernautXlQnnPackCatalogTest {
    @Test
    fun `DL-001 defaultVariant returns verified Juggernaut zip metadata`() {
        val variant = JuggernautXlQnnPackCatalog.defaultVariant()
        assertEquals("juggernaut_qnn2.28_8gen3.zip", variant.zipFileName)
        assertEquals("b27231823434c4e4184a61f77401227264a5308c6846c6fc628c37583e76413c", variant.sha256)
        assertEquals(3_747_687_306L, variant.sizeInBytes)
    }

    @Test
    fun `allVariants includes defaultVariant`() {
        val all = JuggernautXlQnnPackCatalog.allVariants()
        assertEquals(1, all.size)
        assertEquals(JuggernautXlQnnPackCatalog.defaultVariant(), all.first())
    }

    @Test
    fun `findBySha256 resolves correctly`() {
        val resolved =
            JuggernautXlQnnPackCatalog.findBySha256(
                "b27231823434c4e4184a61f77401227264a5308c6846c6fc628c37583e76413c",
            )
        assertNotNull(resolved)
        assertEquals("juggernaut_qnn2.28_8gen3.zip", resolved?.zipFileName)

        val invalid = JuggernautXlQnnPackCatalog.findBySha256("invalid_sha")
        assertNull(invalid)
    }
}
