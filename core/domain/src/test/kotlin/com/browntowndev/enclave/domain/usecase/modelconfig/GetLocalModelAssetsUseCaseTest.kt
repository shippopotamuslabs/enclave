package com.browntowndev.enclave.domain.usecase.modelconfig

import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.download.DownloadSource
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GetLocalModelAssetsUseCaseTest {
    private val repository = FakeLocalModelRepository()
    private lateinit var useCase: GetLocalModelAssetsUseCase

    @BeforeEach
    fun setup() {
        useCase = GetLocalModelAssetsUseCaseImpl(repository)
    }

    @Test
    fun `invoke returns stream of local model assets`() =
        runTest {
            val assets =
                listOf(
                    LocalModelAsset(
                        metadata =
                            LocalModelMetadata(
                                id = LocalModelId("model-1"),
                                huggingFaceModelName = "test/model",
                                remoteFileName = "model.gguf",
                                localFileName = "model.gguf",
                                sha256 = "abc123",
                                sizeInBytes = 1_000L,
                                modelFileFormat = ModelFileFormat.GGUF,
                                source = DownloadSource.HUGGING_FACE,
                            ),
                        configurations = emptyList(),
                    ),
                )
            repository.setAssets(assets)

            val result = useCase().first()

            assertEquals(1, result.size)
            assertEquals(assets, result)
        }
}
