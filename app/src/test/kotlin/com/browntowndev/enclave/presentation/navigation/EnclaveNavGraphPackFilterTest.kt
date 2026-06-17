package com.browntowndev.enclave.presentation.navigation

import com.browntowndev.enclave.domain.model.download.DownloadModelsResult
import com.browntowndev.enclave.domain.model.download.ModelPackTestFixtures
import com.browntowndev.enclave.domain.model.download.ModelScanResult
import com.browntowndev.enclave.domain.model.inference.ModelType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class EnclaveNavGraphPackFilterTest {
    @Test
    @DisplayName("MPK-006 API onboarding download screen enqueues zero pack files")
    fun mpk006_apiOnboardingEnqueuesZeroPackFiles() {
        val modelsResult = packHeavyDownloadResult()
        val filtered = filterDownloadModelsResultForApiOnboarding(modelsResult)

        assertEquals(0, filtered.modelsToDownload.size)
        assertTrue(filtered.allModels.isEmpty())
    }

    @Test
    @DisplayName("MPK-025 API onboarding filters pack when catalog SHAs missing")
    fun mpk025_apiOnboardingFiltersPackWhenCatalogShasMissing() {
        val modelsResult =
            packHeavyDownloadResult().copy(imageGenerationPackMemberSha256s = emptySet())
        val filtered = filterDownloadModelsResultForApiOnboarding(modelsResult)

        assertEquals(0, filtered.modelsToDownload.size)
        assertTrue(filtered.utilityAssets.isEmpty())
    }

    @Test
    @DisplayName("DL-016 API onboarding excludes IMAGE_EDIT Juggernaut pack members")
    fun dl016_apiOnboardingExcludesImageEditPackMembers() {
        val juggernautDiffusion = ModelPackTestFixtures.juggernautDiffusionAsset()
        val juggernautUtility = ModelPackTestFixtures.juggernautQnnPackUtilityAsset()
        val modelsResult =
            DownloadModelsResult(
                allModels = mapOf(ModelType.IMAGE_EDIT to juggernautDiffusion),
                utilityAssets = listOf(juggernautUtility),
                modelsToDownload = listOf(juggernautDiffusion, juggernautUtility),
                scanResult = ModelScanResult(listOf(juggernautDiffusion), emptyMap(), false),
                imageGenerationPackMemberSha256s = emptySet(),
                imageEditPackMemberSha256s =
                    setOf(
                        juggernautDiffusion.metadata.sha256,
                        juggernautUtility.metadata.sha256,
                    ),
            )
        val filtered = filterDownloadModelsResultForApiOnboarding(modelsResult)

        assertEquals(0, filtered.modelsToDownload.size)
        assertTrue(filtered.utilityAssets.isEmpty())
    }

    @Test
    @DisplayName("MPK-020 Onboarding must not whitelist pack utilities for API users")
    fun mpk020_onboardingMustNotWhitelistPackUtilitiesForApiUsers() {
        val modelsResult = packHeavyDownloadResult()
        val filtered = filterDownloadModelsResultForApiOnboarding(modelsResult)

        assertEquals(0, filtered.modelsToDownload.size)
        val utilityShas = filtered.utilityAssets.map { it.metadata.sha256 }.toSet()
        assertFalse(ModelPackTestFixtures.packMemberShas.any { it in utilityShas })
    }

    private fun packHeavyDownloadResult(): DownloadModelsResult {
        val diffusion = ModelPackTestFixtures.diffusionAsset()
        val vae = ModelPackTestFixtures.vaeAsset()
        val encoder = ModelPackTestFixtures.encoderAsset()
        val packMembers = listOf(diffusion, vae, encoder)
        return DownloadModelsResult(
            allModels = mapOf(ModelType.IMAGE_GENERATION to diffusion),
            utilityAssets = listOf(vae, encoder),
            modelsToDownload = packMembers,
            scanResult = ModelScanResult(packMembers, emptyMap(), false),
            imageGenerationPackMemberSha256s = ModelPackTestFixtures.packMemberShas,
        )
    }
}
