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
        val filtered = filterDownloadModelsResultForApiOnboarding(modelsResult, setOf(ModelType.IMAGE_GENERATION))

        assertEquals(0, filtered.modelsToDownload.size)
        assertTrue(filtered.allModels.isEmpty())
    }

    @Test
    @DisplayName("MPK-025 API onboarding filters pack when catalog SHAs missing")
    fun mpk025_apiOnboardingFiltersPackWhenCatalogShasMissing() {
        val modelsResult =
            packHeavyDownloadResult().copy(imageGenerationPackMemberSha256s = emptySet())
        val filtered = filterDownloadModelsResultForApiOnboarding(modelsResult, setOf(ModelType.IMAGE_GENERATION))

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
        val filtered = filterDownloadModelsResultForApiOnboarding(modelsResult, setOf(ModelType.IMAGE_EDIT))

        assertEquals(0, filtered.modelsToDownload.size)
        assertTrue(filtered.utilityAssets.isEmpty())
    }

    @Test
    @DisplayName("MPK-020 Onboarding must not whitelist pack utilities for API users")
    fun mpk020_onboardingMustNotWhitelistPackUtilitiesForApiUsers() {
        val modelsResult = packHeavyDownloadResult()
        val filtered = filterDownloadModelsResultForApiOnboarding(modelsResult, setOf(ModelType.IMAGE_GENERATION))

        assertEquals(0, filtered.modelsToDownload.size)
        val utilityShas = filtered.utilityAssets.map { it.metadata.sha256 }.toSet()
        assertFalse(ModelPackTestFixtures.packMemberShas.any { it in utilityShas })
    }

    @Test
    @DisplayName("API onboarding keeps local image generation but filters out LLMs")
    fun apiOnboardingKeepsLocalImageGenButFiltersOutLlms() {
        val diffusion = ModelPackTestFixtures.diffusionAsset()
        val llmAsset = diffusion.copy(
            metadata = diffusion.metadata.copy(
                sha256 = "llm-sha",
                localFileName = "llm.gguf",
                displayName = "Llm Model"
            ),
            configurations = listOf(
                com.browntowndev.enclave.domain.model.config.LocalModelConfiguration(
                    localModelId = diffusion.metadata.id,
                    displayName = "Llm Config",
                    systemPrompt = "",
                    maxTokens = 2048,
                    contextWindow = 8192,
                    temperature = 0.7,
                    topP = 0.9,
                    topK = 50,
                    repetitionPenalty = 1.05,
                    defaultAssignments = listOf(ModelType.MAIN)
                )
            ),
        )

        val modelsResult = DownloadModelsResult(
            allModels = mapOf(
                ModelType.IMAGE_GENERATION to diffusion,
                ModelType.MAIN to llmAsset
            ),
            modelsToDownload = listOf(diffusion, llmAsset),
            scanResult = ModelScanResult(listOf(diffusion, llmAsset), emptyMap(), false),
        )

        val filtered = filterDownloadModelsResultForApiOnboarding(
            modelsResult,
            apiSlots = setOf(ModelType.MAIN)
        )

        assertEquals(1, filtered.modelsToDownload.size)
        assertEquals(diffusion.metadata.sha256, filtered.modelsToDownload.first().metadata.sha256)
        // scanResult must also be filtered so the download screen doesn't show the
        // suppressed LLM as QUEUED (which would prompt resume/retry and re-enqueue it).
        assertEquals(1, filtered.scanResult.missingModels.size)
        assertEquals(diffusion.metadata.sha256, filtered.scanResult.missingModels.first().metadata.sha256)
    }

    @Test
    @DisplayName("API onboarding filters scanResult partial downloads and invalid models for suppressed slots")
    fun apiOnboardingFiltersScanResultForSuppressedSlots() {
        val diffusion = ModelPackTestFixtures.diffusionAsset()
        val llmAsset = diffusion.copy(
            metadata = diffusion.metadata.copy(
                sha256 = "llm-sha",
                localFileName = "llm.gguf",
                displayName = "Llm Model"
            ),
            configurations = listOf(
                com.browntowndev.enclave.domain.model.config.LocalModelConfiguration(
                    localModelId = diffusion.metadata.id,
                    displayName = "Llm Config",
                    systemPrompt = "",
                    maxTokens = 2048,
                    contextWindow = 8192,
                    temperature = 0.7,
                    topP = 0.9,
                    topK = 50,
                    repetitionPenalty = 1.05,
                    defaultAssignments = listOf(ModelType.MAIN)
                )
            ),
        )

        val modelsResult = DownloadModelsResult(
            allModels = mapOf(
                ModelType.IMAGE_GENERATION to diffusion,
                ModelType.MAIN to llmAsset
            ),
            modelsToDownload = listOf(diffusion, llmAsset),
            scanResult = ModelScanResult(
                missingModels = listOf(diffusion, llmAsset),
                partialDownloads = mapOf("llm.gguf" to 500L, "stable-diffusion-v1-5-Q4_0.gguf" to 100L),
                allValid = false,
                invalidModels = listOf(llmAsset),
            ),
        )

        val filtered = filterDownloadModelsResultForApiOnboarding(
            modelsResult,
            apiSlots = setOf(ModelType.MAIN)
        )

        // Suppressed LLM partial download entry is dropped; the kept diffusion entry remains.
        assertFalse(filtered.scanResult.partialDownloads.containsKey("llm.gguf"))
        assertTrue(filtered.scanResult.partialDownloads.containsKey("stable-diffusion-v1-5-Q4_0.gguf"))
        // Invalid models for the suppressed slot are dropped.
        assertTrue(filtered.scanResult.invalidModels.none { it.metadata.sha256 == "llm-sha" })
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
