package com.browntowndev.pocketcrew.domain.usecase.modelconfig

import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SaveLocalModelMetadataUseCaseTest {
    private val repository = mockk<LocalModelRepositoryPort>()
    private lateinit var useCase: SaveLocalModelMetadataUseCase

    @BeforeEach
    fun setup() {
        useCase = SaveLocalModelMetadataUseCaseImpl(repository)
    }

    @Test
    fun `invoke calls repository saveLocalModelMetadata`() = runTest {
        val metadata = mockk<LocalModelMetadata>()
        coEvery { repository.saveLocalModelMetadata(metadata) } returns 1L

        val result = useCase(metadata)

        assertTrue(result.isSuccess)
        assertEquals(1L, result.getOrNull())
        coVerify(exactly = 1) { repository.saveLocalModelMetadata(metadata) }
    }
}