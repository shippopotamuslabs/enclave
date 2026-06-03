package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.domain.model.telemetry.UsagePeriod
import com.browntowndev.enclave.domain.model.telemetry.UsageSpendSummary
import com.browntowndev.enclave.domain.port.auth.AuthState
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.domain.usecase.settings.ApplyApiModelMetadataDefaultsUseCase
import com.browntowndev.enclave.feature.settings.usage.UsageSpendUiMapper
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class SettingsUsageSpendUiStateFactoryTest {
    private val factory =
        buildSettingsUiStateFactory(
            localModelAssetUiMapper = LocalModelAssetUiMapper(),
            apiModelAssetUiMapper = ApiModelAssetUiMapper(),
            ttsProviderAssetUiMapper = TtsProviderAssetUiMapper(),
            mediaProviderAssetUiMapper = mockk(relaxed = true),
            apiDiscoveryUiFilter = ApiDiscoveryUiFilter(),
            applyApiModelMetadataDefaultsUseCase = ApplyApiModelMetadataDefaultsUseCase(),
            usageSpendUiMapper = UsageSpendUiMapper(),
        )

    @Test
    fun `TEL_011 zero data settings view renders deterministic empty state`() {
        val state =
            factory.create(
                persistedSettings = SettingsData(),
                localAssets = emptyList(),
                apiAssets = emptyList(),
                ttsAssets = emptyList(),
                mediaAssets = emptyList(),
                defaultModels = emptyList(),
                authState = AuthState.Loading,
                sheetVisibility = SheetVisibilityState(usageSpend = true),
                memories = emptyList(),
                feedbackText = "",
                localModelsState = LocalModelsTransientState(),
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
            )

        assertEquals("$0.000000", state.usageSpend.totalCostLabel)
        assertEquals("No usage recorded", state.usageSpend.emptyMessage)
        assertEquals(0, state.usageSpend.providerRows.size)
        assertNull(state.usageSpend.missingCostTurnsLabel)
    }
}
