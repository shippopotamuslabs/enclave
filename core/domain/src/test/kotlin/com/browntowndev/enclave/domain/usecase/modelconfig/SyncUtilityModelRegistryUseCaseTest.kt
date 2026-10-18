package com.browntowndev.enclave.domain.usecase.modelconfig

import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.download.DownloadSource
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.domain.usecase.FakeUtilityModelRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SyncUtilityModelRegistryUseCaseTest {
    private val repository = FakeUtilityModelRepository()
    private val logger = FakeLoggingPort()
    private val useCase = SyncUtilityModelRegistryUseCase(repository, logger)

    @Test
    @DisplayName("SCN-DOMAIN-001")
    fun scnDomain001_syncUtilityAssetUpsertsMetadataWithNonEmptyId() =
        runTest {
            val asset =
                LocalModelAsset(
                    metadata =
                        LocalModelMetadata(
                            id = LocalModelId(""),
                            huggingFaceModelName = "ggerganov/whisper.cpp",
                            remoteFileName = "ggml-base.en.bin",
                            localFileName = "ggml-base.en.bin",
                            sha256 = "whisper-sha",
                            sizeInBytes = 4L,
                            modelFileFormat = ModelFileFormat.BIN,
                            source = DownloadSource.HUGGING_FACE,
                            utilityType = UtilityType.WHISPER,
                        ),
                    configurations = emptyList(),
                )

            val id = useCase.invoke(asset)

            assertTrue(id.value.matches(Regex("^[0-9a-f-]{36}$")))
            assertEquals(UtilityType.WHISPER, repository.lastUpserted?.utilityType)
            assertEquals("whisper-sha", repository.lastUpserted?.sha256)
        }

    @Test
    @DisplayName("SCN-DOMAIN-003")
    fun scnDomain003_syncRejectsNonUtilityAsset() =
        runTest {
            val asset =
                LocalModelAsset(
                    metadata =
                        LocalModelMetadata(
                            id = LocalModelId("slot-1"),
                            huggingFaceModelName = "user/repo",
                            remoteFileName = "model.gguf",
                            localFileName = "model.gguf",
                            sha256 = "slot-sha",
                            sizeInBytes = 1000L,
                            modelFileFormat = ModelFileFormat.GGUF,
                            source = DownloadSource.HUGGING_FACE,
                            utilityType = null,
                        ),
                    configurations =
                        listOf(
                            LocalModelConfiguration(
                                id = LocalModelConfigurationId("config-1"),
                                localModelId = LocalModelId("slot-1"),
                                displayName = "Fast",
                                maxTokens = 2048,
                                contextWindow = 4096,
                                temperature = 0.7,
                                topP = 0.95,
                                topK = 40,
                                repetitionPenalty = 1.1,
                                systemPrompt = "",
                                defaultAssignments = listOf(ModelType.FAST),
                            ),
                        ),
                )

            val error =
                assertThrows<Throwable> {
                    useCase.invoke(asset)
                }
            assertTrue(error.message?.contains("Not a utility asset") == true)
        }
}
