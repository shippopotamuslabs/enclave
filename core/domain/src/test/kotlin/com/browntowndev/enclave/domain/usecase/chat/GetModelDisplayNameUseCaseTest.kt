package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.error.ActiveModelConfigurationDecryptException
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GetModelDisplayNameUseCaseTest {
    private val activeModelProvider = mockk<ActiveModelProviderPort>()
    private val useCase = GetModelDisplayNameUseCase(activeModelProvider)

    @Test
    fun `returns empty string when no active configuration`() =
        runTest {
            coEvery { activeModelProvider.getActiveConfiguration(ModelType.FAST) } returns null

            assertEquals("", useCase(ModelType.FAST))
        }

    @Test
    fun `returns decrypt user message instead of propagating exception`() =
        runTest {
            coEvery { activeModelProvider.getActiveConfiguration(ModelType.FAST) } throws
                ActiveModelConfigurationDecryptException(ModelType.FAST)

            assertEquals(ActiveModelConfigurationDecryptException.USER_MESSAGE, useCase(ModelType.FAST))
        }
}
