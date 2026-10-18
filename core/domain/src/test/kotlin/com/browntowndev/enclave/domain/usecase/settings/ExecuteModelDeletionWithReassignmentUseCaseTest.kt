package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.core.testing.FakeApiModelRepository
import com.browntowndev.enclave.core.testing.FakeDefaultModelRepository
import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeModelConfigFetcher
import com.browntowndev.enclave.core.testing.FakeModelFileScanner
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.config.TtsProviderId
import com.browntowndev.enclave.domain.model.download.ModelPackTestFixtures
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.usecase.FakeUtilityModelRepository
import com.browntowndev.enclave.domain.usecase.byok.DeleteApiCredentialsUseCase
import com.browntowndev.enclave.domain.usecase.byok.DeleteApiModelConfigurationUseCase
import com.browntowndev.enclave.domain.usecase.byok.SetDefaultModelUseCase
import com.browntowndev.enclave.domain.usecase.download.ResolveModelCatalogUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.DeleteLocalModelConfigurationUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.DeleteLocalModelUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.DeleteModelPackUseCase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/** Recording [DeleteLocalModelUseCase] fake: captures calls, always succeeds. */
private class RecordingDeleteLocalModelUseCase :
    DeleteLocalModelUseCase(
        FakeLocalModelRepository(),
        FakeDefaultModelRepository(),
        FakeModelFileScanner(),
        FakeApiModelRepository(),
        FakeLoggingPort(),
    ) {
    data class Call(
        val modelId: LocalModelId,
        val replacementLocalConfigId: LocalModelConfigurationId?,
        val replacementApiConfigId: ApiModelConfigurationId?,
        val replacementMediaProviderId: MediaProviderId?,
    )

    val calls = mutableListOf<Call>()

    override suspend fun invoke(
        modelId: LocalModelId,
        replacementLocalConfigId: LocalModelConfigurationId?,
        replacementApiConfigId: ApiModelConfigurationId?,
        replacementMediaProviderId: MediaProviderId?,
    ): Result<Unit> {
        calls += Call(modelId, replacementLocalConfigId, replacementApiConfigId, replacementMediaProviderId)
        return Result.success(Unit)
    }
}

/** Recording [DeleteLocalModelConfigurationUseCase] fake: captures calls, always succeeds. */
private class RecordingDeleteLocalModelConfigurationUseCase : DeleteLocalModelConfigurationUseCase {
    val calls = mutableListOf<LocalModelConfigurationId>()

    override suspend fun invoke(configurationId: LocalModelConfigurationId): Result<Unit> {
        calls += configurationId
        return Result.success(Unit)
    }
}

/** Recording [DeleteApiCredentialsUseCase] fake: captures calls, always succeeds. */
private class RecordingDeleteApiCredentialsUseCase : DeleteApiCredentialsUseCase {
    data class Call(
        val id: ApiCredentialsId,
        val replacementLocalConfigId: LocalModelConfigurationId?,
        val replacementApiConfigId: ApiModelConfigurationId?,
    )

    val calls = mutableListOf<Call>()

    override suspend fun invoke(
        id: ApiCredentialsId,
        replacementLocalConfigId: LocalModelConfigurationId?,
        replacementApiConfigId: ApiModelConfigurationId?,
    ): Result<Unit> {
        calls += Call(id, replacementLocalConfigId, replacementApiConfigId)
        return Result.success(Unit)
    }

    override suspend fun getModelTypesNeedingReassignment(id: ApiCredentialsId): List<ModelType> = emptyList()

    override suspend fun isLastModel(id: ApiCredentialsId): Boolean = false
}

/** Recording [DeleteApiModelConfigurationUseCase] fake: captures calls, always succeeds. */
private class RecordingDeleteApiModelConfigurationUseCase : DeleteApiModelConfigurationUseCase {
    data class Call(
        val configurationId: ApiModelConfigurationId,
        val replacementLocalConfigId: LocalModelConfigurationId?,
        val replacementApiConfigId: ApiModelConfigurationId?,
    )

    val calls = mutableListOf<Call>()

    override suspend fun invoke(
        configurationId: ApiModelConfigurationId,
        replacementLocalConfigId: LocalModelConfigurationId?,
        replacementApiConfigId: ApiModelConfigurationId?,
    ): Result<Unit> {
        calls += Call(configurationId, replacementLocalConfigId, replacementApiConfigId)
        return Result.success(Unit)
    }

    override suspend fun getModelTypesNeedingReassignment(configurationId: ApiModelConfigurationId): List<ModelType> = emptyList()
}

/** Recording [SetDefaultModelUseCase] fake: captures every assignment call verbatim. */
private class DeletionRecordingSetDefaultModelUseCase : SetDefaultModelUseCase {
    data class Call(
        val modelType: ModelType,
        val localConfigId: LocalModelConfigurationId?,
        val apiConfigId: ApiModelConfigurationId?,
        val ttsProviderId: TtsProviderId?,
        val mediaProviderId: MediaProviderId?,
        val liveVoiceProviderId: LiveVoiceProviderId?,
    )

    val calls = mutableListOf<Call>()

    override suspend fun invoke(
        modelType: ModelType,
        localConfigId: LocalModelConfigurationId?,
        apiConfigId: ApiModelConfigurationId?,
        ttsProviderId: TtsProviderId?,
        mediaProviderId: MediaProviderId?,
        liveVoiceProviderId: LiveVoiceProviderId?,
    ) {
        calls += Call(modelType, localConfigId, apiConfigId, ttsProviderId, mediaProviderId, liveVoiceProviderId)
    }
}

/** Recording [DeleteModelPackUseCase] fake: captures calls, always succeeds. */
private class RecordingDeleteModelPackUseCase(
    deleteLocalModel: DeleteLocalModelUseCase,
) : DeleteModelPackUseCase(
        deleteLocalModel,
        FakeUtilityModelRepository(),
        FakeModelFileScanner(),
        ResolveModelCatalogUseCase(FakeModelConfigFetcher()),
    ) {
    data class Call(
        val packId: String,
        val replacementLocalConfigId: LocalModelConfigurationId?,
        val replacementApiConfigId: ApiModelConfigurationId?,
        val replacementMediaProviderId: MediaProviderId?,
    )

    val calls = mutableListOf<Call>()

    override suspend fun invoke(
        packId: String,
        replacementLocalConfigId: LocalModelConfigurationId?,
        replacementApiConfigId: ApiModelConfigurationId?,
        replacementMediaProviderId: MediaProviderId?,
    ): Result<Unit> {
        calls += Call(packId, replacementLocalConfigId, replacementApiConfigId, replacementMediaProviderId)
        return Result.success(Unit)
    }
}

class ExecuteModelDeletionWithReassignmentUseCaseTest {
    private val deleteLocalModelUseCase = RecordingDeleteLocalModelUseCase()
    private val deleteLocalModelConfigurationUseCase = RecordingDeleteLocalModelConfigurationUseCase()
    private val deleteApiCredentialsUseCase = RecordingDeleteApiCredentialsUseCase()
    private val deleteApiModelConfigurationUseCase = RecordingDeleteApiModelConfigurationUseCase()
    private val setDefaultModelUseCase = DeletionRecordingSetDefaultModelUseCase()
    private val deleteModelPackUseCase = RecordingDeleteModelPackUseCase(deleteLocalModelUseCase)

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

            val result =
                useCase(
                    target = ModelDeletionTarget.LocalModelAsset(id),
                    modelTypesNeedingReassignment = listOf(ModelType.MAIN),
                    replacementLocalConfigId = replacementId,
                    replacementApiConfigId = null,
                )

            assertTrue(result.isSuccess)
            assertEquals(
                listOf(
                    RecordingDeleteLocalModelUseCase.Call(
                        modelId = id,
                        replacementLocalConfigId = replacementId,
                        replacementApiConfigId = null,
                        replacementMediaProviderId = null,
                    ),
                ),
                deleteLocalModelUseCase.calls,
            )
        }

    @Test
    fun `invoke model pack deletion forwards media provider reassignment`() =
        runTest {
            val mediaId = MediaProviderId("fal-image")

            val result =
                useCase(
                    target = ModelDeletionTarget.ModelPack(ModelPackTestFixtures.PACK_ID),
                    modelTypesNeedingReassignment = listOf(ModelType.IMAGE_GENERATION),
                    replacementLocalConfigId = null,
                    replacementApiConfigId = null,
                    replacementMediaProviderId = mediaId,
                )

            assertTrue(result.isSuccess)
            assertEquals(
                listOf(
                    RecordingDeleteModelPackUseCase.Call(
                        packId = ModelPackTestFixtures.PACK_ID,
                        replacementLocalConfigId = null,
                        replacementApiConfigId = null,
                        replacementMediaProviderId = mediaId,
                    ),
                ),
                deleteModelPackUseCase.calls,
            )
        }

    @Test
    fun `invoke local preset deletion calls setDefault and deleteLocalModelConfigurationUseCase`() =
        runTest {
            val id = LocalModelConfigurationId("test-id")
            val replacementId = LocalModelConfigurationId("replacement-id")

            val result =
                useCase(
                    target = ModelDeletionTarget.LocalModelPreset(id),
                    modelTypesNeedingReassignment = listOf(ModelType.MAIN),
                    replacementLocalConfigId = replacementId,
                    replacementApiConfigId = null,
                )

            assertTrue(result.isSuccess)
            assertEquals(
                listOf(
                    DeletionRecordingSetDefaultModelUseCase.Call(
                        modelType = ModelType.MAIN,
                        localConfigId = replacementId,
                        apiConfigId = null,
                        ttsProviderId = null,
                        mediaProviderId = null,
                        liveVoiceProviderId = null,
                    ),
                ),
                setDefaultModelUseCase.calls,
            )
            assertEquals(listOf(id), deleteLocalModelConfigurationUseCase.calls)
        }

    @Test
    fun `invoke api provider deletion calls deleteApiCredentialsUseCase`() =
        runTest {
            val id = ApiCredentialsId("api-id")
            val replacementId = LocalModelConfigurationId("replacement-id")

            val result =
                useCase(
                    target = ModelDeletionTarget.ApiProvider(id),
                    modelTypesNeedingReassignment = listOf(ModelType.MAIN),
                    replacementLocalConfigId = replacementId,
                    replacementApiConfigId = null,
                )

            assertTrue(result.isSuccess)
            assertEquals(
                listOf(
                    RecordingDeleteApiCredentialsUseCase.Call(
                        id = id,
                        replacementLocalConfigId = replacementId,
                        replacementApiConfigId = null,
                    ),
                ),
                deleteApiCredentialsUseCase.calls,
            )
        }
}
