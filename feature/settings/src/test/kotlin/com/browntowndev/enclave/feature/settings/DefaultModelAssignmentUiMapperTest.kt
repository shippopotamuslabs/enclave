package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.error.ActiveModelConfigurationDecryptException
import com.browntowndev.enclave.domain.model.inference.ModelSource
import com.browntowndev.enclave.domain.model.inference.ModelType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DefaultModelAssignmentUiMapperTest {
    @Test
    fun `shows unlock vault message when assignment exists but display name is unreadable`() {
        val assignment =
            DefaultModelAssignment(
                modelType = ModelType.FAST,
                apiConfigId = ApiModelConfigurationId("cfg-1"),
                displayName = null,
            )

        val ui = assignment.toUi()

        assertEquals(ActiveModelConfigurationDecryptException.USER_MESSAGE, ui.currentModelName)
    }

    @Test
    fun `shows none assigned when slot is empty`() {
        val assignment = DefaultModelAssignment(modelType = ModelType.FAST)

        val ui = assignment.toUi()

        assertEquals("None Assigned", ui.currentModelName)
        assertEquals("None Assigned", ui.assignmentRowDisplay.presetName)
        assertNull(ui.assignmentRowDisplay.providerName)
        assertNull(ui.assignmentRowDisplay.modelName)
    }

    @Test
    fun `shows unlock vault message when preset name is unreadable`() {
        val assignment =
            DefaultModelAssignment(
                modelType = ModelType.FAST,
                apiConfigId = ApiModelConfigurationId("cfg-1"),
                displayName = "OpenAI Primary",
                presetName = null,
            )

        val ui = assignment.toUi()

        assertEquals(ActiveModelConfigurationDecryptException.USER_MESSAGE, ui.presetName)
    }

    @Test
    fun `API assignment row shows preset provider and model id`() {
        val display =
            DefaultModelAssignmentUi(
                modelType = ModelType.FAST,
                source = ModelSource.API,
                currentModelName = "OpenRouter",
                displayLabel = "Fast",
                providerName = "OpenRouter",
                presetName = "Default Preset",
                modelId = "stepfun/step-3.7-flash",
            ).assignmentRowDisplay

        assertEquals("Default Preset", display.presetName)
        assertEquals("OpenRouter", display.providerName)
        assertEquals("stepfun/step-3.7-flash", display.modelName)
    }

    @Test
    fun `local assignment row shows preset provider and base model name`() {
        val display =
            DefaultModelAssignmentUi(
                modelType = ModelType.DRAFT_ONE,
                source = ModelSource.ON_DEVICE,
                currentModelName = "Gemma 4 E4B",
                displayLabel = "Draft 1",
                providerName = "Local",
                presetName = "Analytic Drafter",
            ).assignmentRowDisplay

        assertEquals("Analytic Drafter", display.presetName)
        assertEquals("Local", display.providerName)
        assertEquals("Gemma 4 E4B", display.modelName)
    }

    @Test
    fun `media assignment row shows provider display name provider and model id`() {
        val display =
            DefaultModelAssignmentUi(
                modelType = ModelType.IMAGE_GENERATION,
                source = ModelSource.API,
                currentModelName = "OpenAI Images 2",
                displayLabel = "Image Generation",
                providerName = "OpenAI",
                presetName = "dall-e-3",
            ).assignmentRowDisplay

        assertEquals("OpenAI Images 2", display.presetName)
        assertEquals("OpenAI", display.providerName)
        assertEquals("dall-e-3", display.modelName)
    }

    @Test
    fun `TTS assignment row shows provider display name provider and voice`() {
        val display =
            DefaultModelAssignmentUi(
                modelType = ModelType.TTS,
                source = ModelSource.API,
                currentModelName = "Grok TTS",
                displayLabel = "Text-to-Speech",
                providerName = "xAI",
                presetName = "eve",
            ).assignmentRowDisplay

        assertEquals("Grok TTS", display.presetName)
        assertEquals("xAI", display.providerName)
        assertEquals("eve", display.modelName)
    }
}