package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ByokPresetDefaultsTest {
    @Test
    fun `freshApiPresetDraft uses ZAI recommended temperature`() {
        val draft = freshApiPresetDraft(ApiProviderUi.ZAI)

        assertEquals(1.0, draft.temperature)
        assertEquals(true, draft.isFreshPreset)
    }

    @Test
    fun `applyProviderPresetDefaultsIfFresh updates unsaved ZAI preset from generic default`() {
        val normalized =
            ApiModelConfigUi(isFreshPreset = true)
                .applyProviderPresetDefaultsIfFresh(ApiProviderUi.ZAI)

        assertEquals(1.0, normalized.temperature)
        assertFalse(normalized.isFreshPreset)
    }

    @Test
    fun `applyProviderPresetDefaultsIfFresh clears fresh flag for auto created default preset`() {
        val normalized =
            ApiModelConfigUi(
                id = ApiModelConfigurationId("preset-1"),
                displayName = "Default Preset",
                modelId = "",
                isFreshPreset = true,
            ).applyProviderPresetDefaultsIfFresh(ApiProviderUi.ZAI)

        assertEquals(1.0, normalized.temperature)
        assertFalse(normalized.isFreshPreset)
    }

    @Test
    fun `applyProviderPresetDefaultsIfFresh preserves customized temperature`() {
        val normalized =
            ApiModelConfigUi(
                temperature = 0.5,
                isFreshPreset = true,
            ).applyProviderPresetDefaultsIfFresh(ApiProviderUi.ZAI)

        assertEquals(0.5, normalized.temperature)
        assertFalse(normalized.isFreshPreset)
    }

    @Test
    fun `applyProviderPresetDefaultsIfFresh preserves user chosen generic temperature on ZAI`() {
        val normalized =
            ApiModelConfigUi(
                temperature = 0.7,
                isFreshPreset = false,
            ).applyProviderPresetDefaultsIfFresh(ApiProviderUi.ZAI)

        assertEquals(0.7, normalized.temperature)
    }

    @Test
    fun `SCN-UI-005 fresh DeepSeek preset uses generic temperature 0 dot 7`() {
        val draft = freshApiPresetDraft(ApiProviderUi.DEEPSEEK)
        assertEquals(0.7, draft.temperature)
        assertEquals(true, draft.isFreshPreset)
    }

    @Test
    fun `applyProviderPresetDefaultsIfFresh preserves saved preset with model selected`() {
        val normalized =
            ApiModelConfigUi(
                id = ApiModelConfigurationId("preset-1"),
                displayName = "Creative",
                modelId = "glm-4.6",
                temperature = 0.7,
            ).applyProviderPresetDefaultsIfFresh(ApiProviderUi.ZAI)

        assertEquals(0.7, normalized.temperature)
    }
}