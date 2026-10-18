package com.browntowndev.enclave.domain.usecase.modelconfig

import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.download.DownloadSource
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SaveLocalModelMetadataUseCaseTest {
    private val repository = FakeLocalModelRepository()
    private lateinit var useCase: SaveLocalModelMetadataUseCase

    @BeforeEach
    fun setup() {
        useCase = SaveLocalModelMetadataUseCaseImpl(repository)
    }

    @Test
    fun `invoke calls repository saveLocalModelMetadata`() =
        runTest {
            val metadata =
                LocalModelMetadata(
                    id = LocalModelId("1"),
                    huggingFaceModelName = "test/model",
                    remoteFileName = "model.gguf",
                    localFileName = "model.gguf",
                    sha256 = "abc123",
                    sizeInBytes = 1_000L,
                    modelFileFormat = ModelFileFormat.GGUF,
                    source = DownloadSource.HUGGING_FACE,
                )
            val expectedId = LocalModelId("1")

            val result = useCase(metadata)

            assertTrue(result.isSuccess)
            assertEquals(expectedId, result.getOrNull())
            assertEquals(listOf(metadata), repository.savedMetadata)
        }
}
