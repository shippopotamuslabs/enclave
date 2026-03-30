package com.browntowndev.pocketcrew.domain.usecase.byok

import com.browntowndev.pocketcrew.domain.model.config.DefaultModelAssignment
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
        repository.seed(ModelType.entries.map { DefaultModelAssignment(it, localConfigId = 1L) })

        useCase(ModelType.FAST, localConfigId = null, apiConfigId = 5L)

        assertEquals(Triple(ModelType.FAST, null, 5L), repository.lastSetCall)
    }

    @Test
    fun `set THINKING slot back to ON_DEVICE`() = runTest {
        repository.seed(listOf(DefaultModelAssignment(ModelType.THINKING, apiConfigId = 5L)))

        useCase(ModelType.THINKING, localConfigId = 1L, apiConfigId = null)

        assertEquals(Triple(ModelType.THINKING, 1L, null), repository.lastSetCall)
    }

    @Test
    fun `set default for every ModelType variant`() = runTest {
        for (modelType in ModelType.entries) {
            useCase(modelType, localConfigId = 1L, apiConfigId = null)
            assertEquals(modelType, repository.lastSetCall?.first)
        }
        assertEquals(ModelType.entries.size, ModelType.entries.size)
    }
}
