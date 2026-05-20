package com.browntowndev.pocketcrew.feature.settings

import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.DefaultModelAssignment
import com.browntowndev.pocketcrew.domain.model.error.ActiveModelConfigurationDecryptException
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import org.junit.jupiter.api.Assertions.assertEquals
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
}
