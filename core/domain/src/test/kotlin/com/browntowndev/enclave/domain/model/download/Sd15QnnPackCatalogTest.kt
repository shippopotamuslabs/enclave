package com.browntowndev.enclave.domain.model.download

import com.browntowndev.enclave.domain.model.device.SnapdragonTier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class Sd15QnnPackCatalogTest {
    @Test
    fun variantFor_gen3Uses8gen2Zip() {
        val variant = Sd15QnnPackCatalog.variantFor(SnapdragonTier.GEN3_PLUS)
        assertEquals("AnythingV5_qnn2.28_8gen2.zip", variant.zipFileName)
    }

    @Test
    fun findBySha256_matchesGen2() {
        val variant = Sd15QnnPackCatalog.findBySha256(Sd15QnnPackCatalog.catalogTemplate.sha256)
        assertNotNull(variant)
    }
}