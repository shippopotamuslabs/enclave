package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.domain.model.download.ModelPack
import com.browntowndev.enclave.domain.model.download.ModelPackCompleteness
import com.browntowndev.enclave.domain.model.download.ModelPackTestFixtures
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.usecase.modelconfig.ModelPackSettingsRow
import com.browntowndev.enclave.domain.model.telemetry.UsagePeriod
import com.browntowndev.enclave.domain.model.telemetry.UsageSpendSummary
import com.browntowndev.enclave.domain.port.auth.AuthState
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.feature.settings.usage.UsageSpendUiMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ModelPackSettingsUiTest {
    private lateinit var assembler: SettingsUiStateAssembler

    @BeforeEach
    fun setup() {
        assembler =
            SettingsUiStateAssembler(
                localModelAssetUiMapper = LocalModelAssetUiMapper(),
                apiModelAssetUiMapper = ApiModelAssetUiMapper(),
                ttsProviderAssetUiMapper = TtsProviderAssetUiMapper(),
                mediaProviderAssetUiMapper = io.mockk.mockk(relaxed = true),
                apiDiscoveryUiFilter = ApiDiscoveryUiFilter(),
                applyApiModelMetadataDefaultsUseCase =
                    com.browntowndev.enclave.domain.usecase.settings
                        .ApplyApiModelMetadataDefaultsUseCase(),
                usageSpendUiMapper = UsageSpendUiMapper(),
                modelPackUiMapper = ModelPackUiMapper(),
            )
    }

    @Test
    @DisplayName("MPK-011 Settings hides pack member utilities from asset list when pack rows supplied")
    fun mpk011_settingsHidesPackMemberUtilitiesFromAssetList() {
        val state =
            assembler.create(
                persistedSettings = SettingsData(),
                localAssets =
                    listOf(
                        ModelPackTestFixtures.diffusionAsset(),
                        ModelPackTestFixtures.whisperAsset(),
                    ),
                apiAssets = emptyList(),
                ttsAssets = emptyList(),
                mediaAssets = emptyList(),
                defaultModels = emptyList(),
                authState = AuthState.Unauthenticated,
                sheetVisibility = SheetVisibilityState(),
                memories = emptyList(),
                feedbackText = "",
                localModelsState = LocalModelsTransientState(isSheetOpen = true),
                apiState = ApiProvidersTransientState(),
                ttsState = TtsProvidersTransientState(),
                mediaState = MediaProvidersTransientState(),
                searchSkillState = SearchSkillTransientState(),
                assignmentsState = AssignmentDialogTransientState(),
                deletionState = DeletionTransientState(),
                memoriesState = MemoriesTransientState(),
                dataControlsState = DataControlsTransientState(),
                currentTavilyApiKey = "",
                currentExaApiKey = "",
                currentXApiKey = "",
                xSearchState = XSearchTransientState(),
                isReindexing = false,
                reindexProgress = 0f,
                vaultReauthSheet = VaultReauthSheetState.Hidden,
                usageSpendSummary =
                    UsageSpendSummary(
                        period = UsagePeriod.LIFETIME,
                        periodStartUtcMs = null,
                        periodEndUtcMs = null,
                        totalCostUsd = BigDecimal.ZERO,
                        telemetryBackedTurnCount = 0,
                        missingCostTurnCount = 0,
                        providerBreakdown = emptyList(),
                    ),
                modelPackRows =
                    listOf(
                        ModelPackSettingsRow(
                            pack = ModelPackTestFixtures.sd15Pack(),
                            completeness =
                                ModelPackCompleteness(
                                    packId = ModelPackTestFixtures.PACK_ID,
                                    installedCount = 1,
                                    totalCount = 1,
                                    isComplete = true,
                                    members = emptyList(),
                                ),
                            isDiffusionSoftDeleted = false,
                            areUtilityRegistryEntriesAbsent = false,
                            showDownloadAction = false,
                        ),
                    ),
            )

        assertEquals(1, state.localModelsSheet.modelPacks.size)
        assertEquals("1/1", state.localModelsSheet.modelPacks.first().completenessLabel)
        assertEquals(false, state.localModelsSheet.modelPacks.first().showDownloadAction)
        assertTrue(
            state.localModelsSheet.models.any {
                it.remoteFileName == ModelPackTestFixtures.DIFFUSION_FILE && it.isImageGenerationCapable
            },
        )
    }

    @Test
    @DisplayName("MPK-012 Settings omits model packs section when pack rows are empty")
    fun mpk012_settingsOmitsModelPacksSectionWhenEmpty() {
        val state =
            assembler.create(
                persistedSettings = SettingsData(),
                localAssets = listOf(ModelPackTestFixtures.diffusionAsset()),
                apiAssets = emptyList(),
                ttsAssets = emptyList(),
                mediaAssets = emptyList(),
                defaultModels = emptyList(),
                authState = AuthState.Unauthenticated,
                sheetVisibility = SheetVisibilityState(),
                memories = emptyList(),
                feedbackText = "",
                localModelsState = LocalModelsTransientState(isSheetOpen = true),
                apiState = ApiProvidersTransientState(),
                ttsState = TtsProvidersTransientState(),
                mediaState = MediaProvidersTransientState(),
                searchSkillState = SearchSkillTransientState(),
                assignmentsState = AssignmentDialogTransientState(),
                deletionState = DeletionTransientState(),
                memoriesState = MemoriesTransientState(),
                dataControlsState = DataControlsTransientState(),
                currentTavilyApiKey = "",
                currentExaApiKey = "",
                currentXApiKey = "",
                xSearchState = XSearchTransientState(),
                isReindexing = false,
                reindexProgress = 0f,
                vaultReauthSheet = VaultReauthSheetState.Hidden,
                usageSpendSummary =
                    UsageSpendSummary(
                        period = UsagePeriod.LIFETIME,
                        periodStartUtcMs = null,
                        periodEndUtcMs = null,
                        totalCostUsd = BigDecimal.ZERO,
                        telemetryBackedTurnCount = 0,
                        missingCostTurnCount = 0,
                        providerBreakdown = emptyList(),
                    ),
                modelPackRows = emptyList(),
            )

        assertTrue(state.localModelsSheet.modelPacks.isEmpty())
    }

    @Test
    @DisplayName("SET-002 Gen2 hides SDXL from assignment and shows disabled reason")
    fun set002_gen2HidesSdxlFromAssignmentAndShowsDisabledReason() {
        val sdxlPack = ModelPack(
            packId = "sdxl-t2i",
            displayName = "Stable Diffusion XL",
            slot = ModelType.IMAGE_GENERATION,
            members = emptyList(),
        )
        val state =
            assembler.create(
                persistedSettings = SettingsData(),
                localAssets = emptyList(),
                apiAssets = emptyList(),
                ttsAssets = emptyList(),
                mediaAssets = emptyList(),
                defaultModels = emptyList(),
                authState = AuthState.Unauthenticated,
                sheetVisibility = SheetVisibilityState(),
                memories = emptyList(),
                feedbackText = "",
                localModelsState = LocalModelsTransientState(isSheetOpen = true),
                apiState = ApiProvidersTransientState(),
                ttsState = TtsProvidersTransientState(),
                mediaState = MediaProvidersTransientState(),
                searchSkillState = SearchSkillTransientState(),
                assignmentsState = AssignmentDialogTransientState(),
                deletionState = DeletionTransientState(),
                memoriesState = MemoriesTransientState(),
                dataControlsState = DataControlsTransientState(),
                currentTavilyApiKey = "",
                currentExaApiKey = "",
                currentXApiKey = "",
                xSearchState = XSearchTransientState(),
                isReindexing = false,
                reindexProgress = 0f,
                vaultReauthSheet = VaultReauthSheetState.Hidden,
                usageSpendSummary =
                    UsageSpendSummary(
                        period = UsagePeriod.LIFETIME,
                        periodStartUtcMs = null,
                        periodEndUtcMs = null,
                        totalCostUsd = BigDecimal.ZERO,
                        telemetryBackedTurnCount = 0,
                        missingCostTurnCount = 0,
                        providerBreakdown = emptyList(),
                    ),
                modelPackRows =
                    listOf(
                        ModelPackSettingsRow(
                            pack = sdxlPack,
                            completeness =
                                ModelPackCompleteness(
                                    packId = "sdxl-t2i",
                                    installedCount = 0,
                                    totalCount = 2,
                                    isComplete = false,
                                    members = emptyList(),
                                ),
                            isDiffusionSoftDeleted = false,
                            areUtilityRegistryEntriesAbsent = true,
                            showDownloadAction = false,
                            disabledReason = "Requires Snapdragon 8 Gen 3+ with QNN.",
                            isEligible = false,
                        ),
                    ),
            )

        assertEquals(1, state.localModelsSheet.modelPacks.size)
        val uiPack = state.localModelsSheet.modelPacks.first()
        assertEquals("sdxl-t2i", uiPack.packId)
        assertEquals("Requires Snapdragon 8 Gen 3+ with QNN.", uiPack.disabledReason)
        assertEquals(false, uiPack.isEligible)
    }
}
