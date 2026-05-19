package com.browntowndev.pocketcrew.domain.usecase.settings

import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.usecase.byok.DeleteApiCredentialsUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.DeleteApiModelConfigurationUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.SetDefaultModelUseCase
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.DeleteLocalModelConfigurationUseCase
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.DeleteLocalModelUseCase
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

            coEvery { deleteLocalModelUseCase(id, replacementId, null) } returns Result.success(Unit)

            val result =
                useCase(
                    target = ModelDeletionTarget.LocalModelAsset(id),
                    modelTypesNeedingReassignment = listOf(ModelType.MAIN),
                    replacementLocalConfigId = replacementId,
                    replacementApiConfigId = null,
                )

            assertTrue(result.isSuccess)
            coVerify { deleteLocalModelUseCase(id, replacementId, null) }
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
