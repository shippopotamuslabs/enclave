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
    @DisplayName("MPK-013 Settings keeps SDXL diffusion when utility shares the same zip file name")
    fun mpk013_settingsKeepsSdxlDiffusionWhenUtilitySharesZipFileName() {
        val state =
            assembler.create(
                persistedSettings = SettingsData(),
                localAssets =
                    listOf(
                        ModelPackTestFixtures.sdxlDiffusionAsset(),
                        ModelPackTestFixtures.sdxlQnnPackUtilityAsset(),
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
                            pack = ModelPackTestFixtures.sdxlPack(),
                            completeness =
                                ModelPackCompleteness(
                                    packId = ModelPackTestFixtures.SDXL_PACK_ID,
                                    installedCount = 2,
                                    totalCount = 2,
                                    isComplete = true,
                                    members = emptyList(),
                                ),
                            isDiffusionSoftDeleted = false,
                            areUtilityRegistryEntriesAbsent = false,
                            showDownloadAction = false,
                            isEligible = true,
                        ),
                    ),
            )

        assertEquals(1, state.localModelsSheet.models.size)
        assertTrue(
            state.localModelsSheet.models.single().configurations.any {
                it.id.value == ModelPackTestFixtures.SDXL_CONFIG_ID
            },
        )
        assertTrue(state.localModelsSheet.models.single().isImageGenerationCapable)
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

}
