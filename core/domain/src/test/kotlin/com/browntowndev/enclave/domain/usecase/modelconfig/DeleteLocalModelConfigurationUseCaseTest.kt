package com.browntowndev.enclave.domain.usecase.modelconfig

import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DeleteLocalModelConfigurationUseCaseTest {
    private val repository = FakeLocalModelRepository()
    private lateinit var useCase: DeleteLocalModelConfigurationUseCase

    @BeforeEach
    fun setup() {
        useCase = DeleteLocalModelConfigurationUseCaseImpl(repository)
    }

    @Test
    fun `invoke deletes specific configuration`() =
        runTest {
            val configId = LocalModelConfigurationId("config-1")

            val result = useCase(configId)

            assertTrue(result.isSuccess)
            assertEquals(listOf(configId), repository.deletedConfigurationIds)
        }
}
