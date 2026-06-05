package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.domain.model.download.ModelPackCompleteness
import com.browntowndev.enclave.domain.model.download.ModelPackTestFixtures
import com.browntowndev.enclave.domain.usecase.modelconfig.ModelPackSettingsRow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ModelPackUiMapperTest {
    private val mapper = ModelPackUiMapper()

    @Test
    @DisplayName("MPK-007 Settings shows one pack row with completeness 0/1")
    fun mpk007_settingsShowsPackRowZeroOfOne() {
        val completeness =
            ModelPackCompleteness(
                packId = ModelPackTestFixtures.PACK_ID,
                installedCount = 0,
                totalCount = 1,
                isComplete = false,
                members = emptyList(),
            )
        val row =
            ModelPackSettingsRow(
                pack = ModelPackTestFixtures.sd15Pack(),
                completeness = completeness,
                isDiffusionSoftDeleted = false,
                areUtilityRegistryEntriesAbsent = false,
                showDownloadAction = true,
            )

        val ui = mapper.map(row)

        assertEquals(ModelPackTestFixtures.PACK_ID, ui.packId)
        assertEquals(0, ui.installedCount)
        assertEquals(1, ui.totalCount)
        assertEquals("0/1", mapper.completenessLabel(completeness))
    }

    @Test
    fun mapsDisabledReasonAndIneligibilityFromPackSettingsRow() {
        val row =
            ModelPackSettingsRow(
                pack = ModelPackTestFixtures.sdxlPack(),
                completeness =
                    ModelPackCompleteness(
                        packId = ModelPackTestFixtures.SDXL_PACK_ID,
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
            )

        val ui = mapper.map(row)

        assertEquals("Requires Snapdragon 8 Gen 3+ with QNN.", ui.disabledReason)
        assertEquals(false, ui.isEligible)
    }
}