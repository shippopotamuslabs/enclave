package com.browntowndev.pocketcrew.domain.usecase.byok

import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.DefaultModelAssignment
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.usecase.FakeDefaultModelRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SetDefaultModelUseCaseTest {

    private lateinit var repository: FakeDefaultModelRepository
    private lateinit var useCase: SetDefaultModelUseCase

    @BeforeEach
    fun setUp() {
        repository = FakeDefaultModelRepository()
        useCase = SetDefaultModelUseCaseImpl(repository)
    }

    // ========================================================================
    // Happy Path
    // ========================================================================

    @Test
    fun `set FAST slot to API source`() = runTest {
        repository.seed(ModelType.entries.map { DefaultModelAssignment(it, localConfigId = LocalModelConfigurationId("1")) })

        useCase(ModelType.FAST, localConfigId = null, apiConfigId = ApiModelConfigurationId("5"))

        assertEquals(Triple(ModelType.FAST, null, ApiModelConfigurationId("5")), repository.lastSetCall)
    }

    @Test
    fun `set THINKING slot back to ON_DEVICE`() = runTest {
        repository.seed(listOf(DefaultModelAssignment(ModelType.THINKING, apiConfigId = ApiModelConfigurationId("5"))))

        useCase(ModelType.THINKING, localConfigId = LocalModelConfigurationId("1"), apiConfigId = null)

        assertEquals(Triple(ModelType.THINKING, LocalModelConfigurationId("1"), null), repository.lastSetCall)
    }

    @Test
    fun `set default for every ModelType variant`() = runTest {
        for (modelType in ModelType.entries) {
            useCase(modelType, localConfigId = LocalModelConfigurationId("1"), apiConfigId = null)
            assertEquals(modelType, repository.lastSetCall?.first)
        }
    }
}
