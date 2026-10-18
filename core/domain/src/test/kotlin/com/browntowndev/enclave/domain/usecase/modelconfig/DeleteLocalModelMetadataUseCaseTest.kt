package com.browntowndev.enclave.domain.usecase.modelconfig

import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
import com.browntowndev.enclave.domain.model.config.LocalModelId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DeleteLocalModelMetadataUseCaseTest {
    private val repository = FakeLocalModelRepository()
    private lateinit var useCase: DeleteLocalModelMetadataUseCase

    @BeforeEach
    fun setup() {
        useCase = DeleteLocalModelMetadataUseCaseImpl(repository)
    }

    @Test
    fun `invoke calls repository deleteLocalModelMetadata`() =
        runTest {
            val id = LocalModelId("1")

            val result = useCase(id)

            assertTrue(result.isSuccess)
            assertEquals(listOf(id), repository.deletedMetadataIds)
        }
}
