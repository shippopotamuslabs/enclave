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

    @Test
    fun variantFor_gen2Uses8gen2Zip() {
        val variant = Sd15QnnPackCatalog.variantFor(SnapdragonTier.GEN2)
        assertEquals("AnythingV5_qnn2.28_8gen2.zip", variant.zipFileName)
        assertEquals(false, variant.isCpuMnn)
    }

    @Test
    fun variantFor_gen1UsesMnnCpuZip() {
        val variant = Sd15QnnPackCatalog.variantFor(SnapdragonTier.GEN1)
        assertEquals("AnythingV5.zip", variant.zipFileName)
        assertEquals(Sd15QnnPackCatalog.HF_CPU_REPO, variant.hfRepo)
        assertEquals(true, variant.isCpuMnn)
    }

    @Test
    fun variantFor_unknownTierUsesMnnCpuZip() {
        val variant = Sd15QnnPackCatalog.variantFor(SnapdragonTier.UNKNOWN)
        assertEquals(true, variant.isCpuMnn)
    }

    @Test
    fun variantForAsset_fallsBackToZipName() {
        val variant = Sd15QnnPackCatalog.variantForAsset("not-a-real-sha", "AnythingV5.zip")
        assertNotNull(variant)
        assertEquals(true, variant?.isCpuMnn)
    }
}