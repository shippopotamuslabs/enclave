package com.browntowndev.enclave.domain.usecase.byok

import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.usecase.FakeDefaultModelRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GetDefaultModelsUseCaseTest {
    private lateinit var repository: FakeDefaultModelRepository
    private lateinit var useCase: GetDefaultModelsUseCase

    @BeforeEach
    fun setUp() {
        repository = FakeDefaultModelRepository()
        useCase = GetDefaultModelsUseCaseImpl(repository)
    }

    @Test
    fun `returns seeded defaults`() =
        runTest {
            repository.seed(ModelType.entries.map { DefaultModelAssignment(it, localConfigId = LocalModelConfigurationId("1")) })

            val result = useCase().first()

            assertEquals(ModelType.entries.size, result.size)
            result.forEach { assertNotNull(it.localConfigId) }
        }
}
