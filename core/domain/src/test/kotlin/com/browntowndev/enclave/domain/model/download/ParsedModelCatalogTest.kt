package com.browntowndev.enclave.domain.model.download

import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.inference.ModelType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ParsedModelCatalogTest {
    @Test
    @DisplayName("MPK-004 Local IMAGE_GENERATION excludes pack utilities from utilityAssets")
    fun mpk004_localImageGenerationExcludesPackUtilities() {
        val catalog = ModelPackTestFixtures.parsedCatalog()
        val slice = filterForDownload(catalog, imageGenerationIsApi = false)

        assertFalse(slice.utilityAssets.any { it.metadata.utilityType == UtilityType.FLUX2_VAE })
        assertFalse(slice.utilityAssets.any { it.metadata.utilityType == UtilityType.QWEN3_TEXT_ENCODER })
        assertEquals(
            ModelPackTestFixtures.DIFFUSION_FILE,
            slice.slotAssignments[ModelType.IMAGE_GENERATION]?.metadata?.localFileName,
        )
    }

    @Test
    @DisplayName("MPK-023 packMemberSha256sForSlot derives from catalog modelPacks")
    fun mpk023_packMemberSha256sDerivedFromCatalog() {
        val catalog = ModelPackTestFixtures.parsedCatalog()
        val shas = catalog.packMemberSha256sForSlot(ModelType.IMAGE_GENERATION)

        assertEquals(ModelPackTestFixtures.TOTAL_MEMBERS, shas.size)
        assertTrue(ModelPackTestFixtures.DIFFUSION_SHA in shas)
    }

    @Test
    @DisplayName("MPK-022 Pack utilities must not appear in standalone utility download list")
    fun mpk022_packUtilitiesExcludedFromStandaloneUtilityList() {
        val catalog = ModelPackTestFixtures.parsedCatalog()
        val slice = filterForDownload(catalog, imageGenerationIsApi = false)
        val utilityTypes = slice.utilityAssets.map { it.metadata.utilityType }

        assertFalse(utilityTypes.contains(UtilityType.FLUX2_VAE))
        assertFalse(utilityTypes.contains(UtilityType.QWEN3_TEXT_ENCODER))
    }
}
