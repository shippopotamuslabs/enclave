package com.browntowndev.pocketcrew.domain.usecase.modelconfig

import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DeleteLocalModelMetadataUseCaseTest {
    private val repository = mockk<LocalModelRepositoryPort>()
    private lateinit var useCase: DeleteLocalModelMetadataUseCase

    @BeforeEach
    fun setup() {
        useCase = DeleteLocalModelMetadataUseCaseImpl(repository)
    }

    @Test
    fun `invoke calls repository deleteLocalModelMetadata`() = runTest {
        val id = 1L
        coEvery { repository.deleteLocalModelMetadata(id) } returns Unit

        val result = useCase(id)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { repository.deleteLocalModelMetadata(id) }
    }
}