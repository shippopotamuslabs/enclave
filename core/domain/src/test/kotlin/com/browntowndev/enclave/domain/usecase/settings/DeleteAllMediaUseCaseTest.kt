package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.port.repository.StudioMediaAsset
import com.browntowndev.enclave.domain.usecase.FakeStudioRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("DeleteAllMediaUseCase")
class DeleteAllMediaUseCaseTest {
    private lateinit var studioRepository: FakeStudioRepository
    private lateinit var useCase: DeleteAllMediaUseCase

    @BeforeEach
    fun setup() {
        studioRepository = FakeStudioRepository()
        useCase = DeleteAllMediaUseCase(studioRepository)
    }

    private fun asset(id: String) =
        StudioMediaAsset(
            id = id,
            localUri = "content://$id",
            prompt = "prompt",
            mediaType = "IMAGE",
            createdAt = 1L,
        )

    @Nested
    inner class HappyPaths {
        @Test
        fun `S3 deletes two media assets`() =
            runTest {
                studioRepository.setMedia(listOf(asset("media-1"), asset("media-2")))
                useCase()
                assertEquals(setOf("media-1", "media-2"), studioRepository.deletedMediaIds)
            }

        @Test
        fun `S4 empty studio produces no deletions`() =
            runTest {
                studioRepository.setMedia(emptyList())
                useCase()
                assertEquals(emptySet<String>(), studioRepository.deletedMediaIds)
            }
    }

    @Nested
    inner class Errors {
        @Test
        fun `E2 propagates deleteMedia failures`() =
            runTest {
                studioRepository.setMedia(listOf(asset("m-1")))
                studioRepository.deleteMediaFailure = RuntimeException("permission denied")
                val thrown =
                    assertThrows<RuntimeException> {
                        useCase()
                    }
                assertEquals("permission denied", thrown.message)
            }
    }
}
