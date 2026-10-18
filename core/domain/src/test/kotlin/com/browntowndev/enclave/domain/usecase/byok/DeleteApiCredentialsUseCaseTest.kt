package com.browntowndev.enclave.domain.usecase.byok

import com.browntowndev.enclave.core.testing.FakeApiModelRepository
import com.browntowndev.enclave.core.testing.FakeDefaultModelRepository
import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
import com.browntowndev.enclave.core.testing.FakeTransactionProvider
import com.browntowndev.enclave.domain.model.config.ApiCredentials
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.download.DownloadSource
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeleteApiCredentialsUseCaseTest {
    private val apiModelRepository = FakeApiModelRepository()
    private val localModelRepository = FakeLocalModelRepository()
    private val defaultModelRepository = FakeDefaultModelRepository()
    private val transactionProvider = FakeTransactionProvider()

    private lateinit var useCase: DeleteApiCredentialsUseCaseImpl

    @BeforeEach
    fun setUp() {
        useCase =
            DeleteApiCredentialsUseCaseImpl(
                apiModelRepository,
                localModelRepository,
                defaultModelRepository,
                transactionProvider,
            )
    }

    @Test
    fun `isLastModel returns true if only one api credentials and no local models`() =
        runTest {
            val id = ApiCredentialsId("api-1")
            // No local assets in the fake by default.
            apiModelRepository.seedCredentials(listOf(credentials(id)))

            val result = useCase.isLastModel(id)

            assertTrue(result)
        }

    @Test
    fun `isLastModel returns false if there are local models`() =
        runTest {
            val id = ApiCredentialsId("api-1")
            localModelRepository.setAssets(listOf(localAsset()))
            apiModelRepository.seedCredentials(listOf(credentials(id)))

            val result = useCase.isLastModel(id)

            assertFalse(result)
        }

    @Test
    fun `invoke fails if it is the last model`() =
        runTest {
            val id = ApiCredentialsId("api-1")
            apiModelRepository.seedCredentials(listOf(credentials(id)))

            val result = useCase(id)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalStateException)
        }

    @Test
    fun `invoke reassigns defaults before deleting`() =
        runTest {
            val id = ApiCredentialsId("api-1")
            val configId = ApiModelConfigurationId("config-1")
            val replacementId = ApiModelConfigurationId("replacement-1")

            localModelRepository.setAssets(listOf(localAsset())) // Not last model
            apiModelRepository.seedCredentials(
                listOf(credentials(id), credentials(ApiCredentialsId("api-2"))),
            )
            apiModelRepository.seedConfigurations(
                listOf(
                    ApiModelConfiguration(
                        id = configId,
                        apiCredentialsId = id,
                        modelId = "gpt-4o",
                        displayName = "Test Config",
                    ),
                ),
            )
            defaultModelRepository.seed(
                DefaultModelAssignment(modelType = ModelType.MAIN, apiConfigId = configId),
            )

            val result = useCase(id, null, replacementId)

            assertTrue(result.isSuccess)
            val reassigned = defaultModelRepository.getDefault(ModelType.MAIN)
            assertEquals(replacementId, reassigned?.apiConfigId)
            assertNull(reassigned?.localConfigId)
            assertEquals(listOf(id), apiModelRepository.deleteCredentialsCalls)
        }

    private fun credentials(id: ApiCredentialsId) =
        ApiCredentials(
            id = id,
            displayName = "Test Provider",
            provider = ApiProvider.OPENAI,
            credentialAlias = "alias-${id.value}",
        )

    private fun localAsset() =
        LocalModelAsset(
            metadata =
                LocalModelMetadata(
                    id = LocalModelId("local-1"),
                    huggingFaceModelName = "test/model",
                    remoteFileName = "model.gguf",
                    localFileName = "model.gguf",
                    sha256 = "abc123",
                    sizeInBytes = 1_000L,
                    modelFileFormat = ModelFileFormat.GGUF,
                    source = DownloadSource.HUGGING_FACE,
                ),
            configurations = emptyList(),
        )
}
