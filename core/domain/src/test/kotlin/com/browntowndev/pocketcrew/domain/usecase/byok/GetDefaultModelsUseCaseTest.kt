package com.browntowndev.pocketcrew.domain.usecase.byok

import com.browntowndev.pocketcrew.domain.model.config.DefaultModelAssignment
import com.browntowndev.pocketcrew.domain.model.inference.ModelSource
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.usecase.FakeDefaultModelRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GetDefaultModelsUseCaseTest {

    private lateinit var repository: FakeDefaultModelRepository
    private lateinit var useCase: GetDefaultModelsUseCase

    @BeforeEach
    fun setUp() {
        repository = FakeDefaultModelRepository()
        useCase = GetDefaultModelsUseCase(repository)
    }

    @Test
    fun `returns seeded defaults`() = runTest {
        repository.seed(ModelType.entries.map { DefaultModelAssignment(it, ModelSource.ON_DEVICE) })

        val result = useCase().first()

        assertEquals(ModelType.entries.size, result.size)
        result.forEach { assertEquals(ModelSource.ON_DEVICE, it.source) }
    }
}
