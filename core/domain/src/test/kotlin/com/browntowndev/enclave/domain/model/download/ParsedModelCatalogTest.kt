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
        val slice = filterForDownload(catalog, suppressImageGenerationSlot = false)

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
        val slice = filterForDownload(catalog, suppressImageGenerationSlot = false)
        val utilityTypes = slice.utilityAssets.map { it.metadata.utilityType }

        assertFalse(utilityTypes.contains(UtilityType.FLUX2_VAE))
        assertFalse(utilityTypes.contains(UtilityType.QWEN3_TEXT_ENCODER))
    }

    @Test
    @DisplayName("DL-004 API IMAGE_GENERATION suppresses SD15 and SDXL but not Juggernaut")
    fun dl004_apiImageGenerationSuppressesGenerationPacksOnly() {
        val catalog = ModelPackTestFixtures.parsedCatalogWithAllThreeImagePacks()
        val slice = filterForDownload(catalog, suppressImageGenerationSlot = true, suppressImageEditSlot = false)

        val diffusionShas = slice.slotAssignments.values.map { it.metadata.sha256 }.toSet()
        val utilityTypes = slice.utilityAssets.map { it.metadata.utilityType }.toSet()

        assertFalse(ModelPackTestFixtures.DIFFUSION_SHA in diffusionShas)
        assertFalse(ModelPackTestFixtures.SDXL_DIFFUSION_SHA in diffusionShas)
        assertTrue(ModelPackTestFixtures.JUGGERNAUT_DIFFUSION_SHA in diffusionShas)
        assertFalse(UtilityType.SD15_QNN_PACK in utilityTypes)
        assertFalse(UtilityType.SDXL_QNN_PACK in utilityTypes)
        assertTrue(UtilityType.JUGGERNAUT_XL_QNN_PACK in utilityTypes)
    }

    @Test
    @DisplayName("DL-014 API IMAGE_EDIT suppresses Juggernaut but not SD15 or SDXL")
    fun dl014_apiImageEditSuppressesJuggernautOnly() {
        val catalog = ModelPackTestFixtures.parsedCatalogWithAllThreeImagePacks()
        val slice = filterForDownload(catalog, suppressImageGenerationSlot = false, suppressImageEditSlot = true)

        val diffusionShas = slice.slotAssignments.values.map { it.metadata.sha256 }.toSet()
        val utilityTypes = slice.utilityAssets.map { it.metadata.utilityType }.toSet()

        assertFalse(ModelPackTestFixtures.JUGGERNAUT_DIFFUSION_SHA in diffusionShas)
        assertFalse(UtilityType.JUGGERNAUT_XL_QNN_PACK in utilityTypes)
        // Slot map holds one IMAGE_GENERATION default; catalog iteration leaves SDXL as the winner.
        assertTrue(ModelPackTestFixtures.SDXL_DIFFUSION_SHA in diffusionShas)
        assertTrue(UtilityType.SD15_QNN_PACK in utilityTypes)
        assertTrue(UtilityType.SDXL_QNN_PACK in utilityTypes)
    }

    @Test
    @DisplayName("DL-015 Diffusion member that loses the slot competition is exposed as pack member asset")
    fun dl015_losingDiffusionMemberExposedAsPackMemberAsset() {
        val catalog = ModelPackTestFixtures.parsedCatalogWithAllThreeImagePacks()
        val slice = filterForDownload(catalog, suppressImageGenerationSlot = false, suppressImageEditSlot = false)

        // SDXL wins the single IMAGE_GENERATION slot; the SD15 diffusion GGUF must still
        // surface for scanning/download or the SD15 pack stays permanently incomplete.
        assertEquals(
            ModelPackTestFixtures.SDXL_DIFFUSION_SHA,
            slice.slotAssignments[ModelType.IMAGE_GENERATION]?.metadata?.sha256,
        )
        val packMemberShas = slice.packMemberAssets.map { it.metadata.sha256 }
        assertTrue(ModelPackTestFixtures.DIFFUSION_SHA in packMemberShas)
        // Slot winners and utility members are not duplicated into the pack-member list.
        assertFalse(ModelPackTestFixtures.SDXL_DIFFUSION_SHA in packMemberShas)
        assertFalse(ModelPackTestFixtures.JUGGERNAUT_DIFFUSION_SHA in packMemberShas)
        assertTrue(slice.packMemberAssets.all { it.metadata.utilityType == null })
    }

    @Test
    @DisplayName("DL-016 Suppressed IMAGE_GENERATION slot excludes its diffusion members from pack member assets")
    fun dl016_suppressedSlotExcludesPackMemberAssets() {
        val catalog = ModelPackTestFixtures.parsedCatalogWithAllThreeImagePacks()
        val slice = filterForDownload(catalog, suppressImageGenerationSlot = true, suppressImageEditSlot = false)

        assertFalse(slice.packMemberAssets.any { it.metadata.sha256 == ModelPackTestFixtures.DIFFUSION_SHA })
    }

    @Test
    @DisplayName("DL-001 Juggernaut pack slot is IMAGE_EDIT")
    fun dl001_juggernautPackSlotIsImageEdit() {
        val catalog = ModelPackTestFixtures.parsedCatalogWithAllThreeImagePacks()
        val juggernautPack = catalog.packById(ModelPackTestFixtures.JUGGERNAUT_PACK_ID)!!

        assertEquals(ModelType.IMAGE_EDIT, juggernautPack.slot)
    }
}
