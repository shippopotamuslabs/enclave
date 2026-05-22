package com.browntowndev.enclave.feature.chat.service

import com.browntowndev.enclave.domain.model.chat.MessageGenerationState
import com.browntowndev.enclave.domain.model.error.ActiveModelConfigurationDecryptException
import com.browntowndev.enclave.domain.model.inference.ModelType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatInferenceDecryptFailureMapperTest {
    @Test
    fun `maps decrypt exception to failed state with user message`() {
        val cause = ActiveModelConfigurationDecryptException(ModelType.FAST)

        val state = ChatInferenceDecryptFailureMapper.toFailedState(cause, ModelType.FAST)

        assertTrue(state is MessageGenerationState.Failed)
        val failed = state as MessageGenerationState.Failed
        assertTrue(failed.error is ActiveModelConfigurationDecryptException)
        assertEquals(ActiveModelConfigurationDecryptException.USER_MESSAGE, failed.error.message)
        assertEquals(ModelType.FAST, failed.modelType)
    }
}
