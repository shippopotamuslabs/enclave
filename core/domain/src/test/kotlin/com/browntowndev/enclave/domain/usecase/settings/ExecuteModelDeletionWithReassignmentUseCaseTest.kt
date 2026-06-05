package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.download.ModelPackTestFixtures
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.usecase.byok.DeleteApiCredentialsUseCase
import com.browntowndev.enclave.domain.usecase.byok.DeleteApiModelConfigurationUseCase
import com.browntowndev.enclave.domain.usecase.byok.SetDefaultModelUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.DeleteLocalModelConfigurationUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.DeleteLocalModelUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.DeleteModelPackUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class ExecuteModelDeletionWithReassignmentUseCaseTest {
    private val deleteLocalModelUseCase: DeleteLocalModelUseCase = mockk()
    private val deleteLocalModelConfigurationUseCase: DeleteLocalModelConfigurationUseCase = mockk()
    private val deleteApiCredentialsUseCase: DeleteApiCredentialsUseCase = mockk()
    private val deleteApiModelConfigurationUseCase: DeleteApiModelConfigurationUseCase = mockk()
    private val setDefaultModelUseCase: SetDefaultModelUseCase = mockk()
    private val deleteModelPackUseCase: DeleteModelPackUseCase = mockk(relaxed = true)

    private lateinit var useCase: ExecuteModelDeletionWithReassignmentUseCase

    @BeforeEach
    fun setUp() {
        useCase =
            ExecuteModelDeletionWithReassignmentUseCase(
                deleteLocalModelUseCase,
                deleteLocalModelConfigurationUseCase,
                deleteApiCredentialsUseCase,
                deleteApiModelConfigurationUseCase,
                setDefaultModelUseCase,
                deleteModelPackUseCase,
            )
    }

    @Test
    fun `invoke fails if reassignment needed but no replacement provided`() =
        runTest {
            val result =
                useCase(
                    target = ModelDeletionTarget.LocalModelAsset(LocalModelId("test")),
                    modelTypesNeedingReassignment = listOf(ModelType.MAIN),
                    replacementLocalConfigId = null,
                    replacementApiConfigId = null,
                )

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        }

    @Test
    fun `invoke local asset deletion calls deleteLocalModelUseCase`() =
        runTest {
            val id = LocalModelId("test-id")
            val replacementId = LocalModelConfigurationId("replacement-id")

            coEvery {
                deleteLocalModelUseCase(
                    modelId = id,
                    replacementLocalConfigId = replacementId,
                    replacementApiConfigId = null,
                    replacementMediaProviderId = null,
                )
            } returns Result.success(Unit)

            val result =
                useCase(
                    target = ModelDeletionTarget.LocalModelAsset(id),
                    modelTypesNeedingReassignment = listOf(ModelType.MAIN),
                    replacementLocalConfigId = replacementId,
                    replacementApiConfigId = null,
                )

            assertTrue(result.isSuccess)
            coVerify {
                deleteLocalModelUseCase(
                    modelId = id,
                    replacementLocalConfigId = replacementId,
                    replacementApiConfigId = null,
                    replacementMediaProviderId = null,
                )
            }
        }

    @Test
    fun `invoke model pack deletion forwards media provider reassignment`() =
        runTest {
            val mediaId = MediaProviderId("fal-image")
            coEvery {
                deleteModelPackUseCase(
                    packId = ModelPackTestFixtures.PACK_ID,
                    replacementLocalConfigId = null,
                    replacementApiConfigId = null,
                    replacementMediaProviderId = mediaId,
                )
            } returns Result.success(Unit)

            val result =
                useCase(
                    target = ModelDeletionTarget.ModelPack(ModelPackTestFixtures.PACK_ID),
                    modelTypesNeedingReassignment = listOf(ModelType.IMAGE_GENERATION),
                    replacementLocalConfigId = null,
                    replacementApiConfigId = null,
                    replacementMediaProviderId = mediaId,
                )

            assertTrue(result.isSuccess)
            coVerify {
                deleteModelPackUseCase(
                    packId = ModelPackTestFixtures.PACK_ID,
                    replacementLocalConfigId = null,
                    replacementApiConfigId = null,
                    replacementMediaProviderId = mediaId,
                )
            }
        }

    @Test
    fun `invoke local preset deletion calls setDefault and deleteLocalModelConfigurationUseCase`() =
        runTest {
            val id = LocalModelConfigurationId("test-id")
            val replacementId = LocalModelConfigurationId("replacement-id")

            coEvery { setDefaultModelUseCase(ModelType.MAIN, replacementId, null) } returns Unit
            coEvery { deleteLocalModelConfigurationUseCase(id) } returns Result.success(Unit)

            val result =
                useCase(
                    target = ModelDeletionTarget.LocalModelPreset(id),
                    modelTypesNeedingReassignment = listOf(ModelType.MAIN),
                    replacementLocalConfigId = replacementId,
                    replacementApiConfigId = null,
                )

            assertTrue(result.isSuccess)
            coVerify { setDefaultModelUseCase(ModelType.MAIN, replacementId, null) }
            coVerify { deleteLocalModelConfigurationUseCase(id) }
        }

    @Test
    fun `invoke api provider deletion calls deleteApiCredentialsUseCase`() =
        runTest {
            val id = ApiCredentialsId("api-id")
            val replacementId = LocalModelConfigurationId("replacement-id")

            coEvery { deleteApiCredentialsUseCase(id, replacementId, null) } returns Result.success(Unit)

            val result =
                useCase(
                    target = ModelDeletionTarget.ApiProvider(id),
                    modelTypesNeedingReassignment = listOf(ModelType.MAIN),
                    replacementLocalConfigId = replacementId,
                    replacementApiConfigId = null,
                )

            assertTrue(result.isSuccess)
            coVerify { deleteApiCredentialsUseCase(id, replacementId, null) }
        }
}
