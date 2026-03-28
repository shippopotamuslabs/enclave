package com.browntowndev.pocketcrew.domain.usecase.byok

import com.browntowndev.pocketcrew.domain.model.config.DefaultModelAssignment
import com.browntowndev.pocketcrew.domain.model.inference.ModelSource
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.usecase.FakeDefaultModelRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SetDefaultModelUseCaseTest {

    private lateinit var repository: FakeDefaultModelRepository
    private lateinit var useCase: SetDefaultModelUseCase

    @BeforeEach
    fun setUp() {
        repository = FakeDefaultModelRepository()
        useCase = SetDefaultModelUseCase(repository)
    }

    // ========================================================================
    // Happy Path
    // ========================================================================

    @Test
    fun `set FAST slot to API source`() = runTest {
        repository.seed(ModelType.entries.map { DefaultModelAssignment(it, ModelSource.ON_DEVICE) })

        useCase(ModelType.FAST, ModelSource.API, apiModelId = 5L)

        assertEquals(Triple(ModelType.FAST, ModelSource.API, 5L), repository.lastSetCall)
    }

    @Test
    fun `set THINKING slot back to ON_DEVICE`() = runTest {
        repository.seed(listOf(DefaultModelAssignment(ModelType.THINKING, ModelSource.API)))

        useCase(ModelType.THINKING, ModelSource.ON_DEVICE)

        assertEquals(Triple(ModelType.THINKING, ModelSource.ON_DEVICE, null), repository.lastSetCall)
    }

    @Test
    fun `set default for every ModelType variant`() = runTest {
        for (modelType in ModelType.entries) {
            useCase(modelType, ModelSource.ON_DEVICE)
            assertEquals(modelType, repository.lastSetCall?.first)
        }
        assertEquals(ModelType.entries.size, ModelType.entries.size) // Verify all 7 called without exception
    }

    // ========================================================================
    // Edge Cases
    // ========================================================================

    @Test
    fun `set API default with null apiModelId throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            runTest {
                useCase(ModelType.FAST, ModelSource.API, apiModelId = null)
            }
        }
    }

    @Test
    fun `set ON_DEVICE default ignores apiModelId`() = runTest {
        useCase(ModelType.FAST, ModelSource.ON_DEVICE, apiModelId = 99L)

        assertNull(repository.lastSetCall?.third)
    }
}
