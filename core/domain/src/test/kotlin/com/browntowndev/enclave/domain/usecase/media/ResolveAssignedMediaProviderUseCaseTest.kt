package com.browntowndev.enclave.domain.usecase.media

import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.MediaProviderRepositoryPort
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ResolveAssignedMediaProviderUseCaseTest {
    private val defaultModelRepository = mockk<DefaultModelRepositoryPort>()
    private val mediaProviderRepository = mockk<MediaProviderRepositoryPort>()
    private lateinit var useCase: ResolveAssignedMediaProviderUseCase

    @BeforeEach
    fun setup() {
        useCase =
            ResolveAssignedMediaProviderUseCase(
                defaultModelRepository = defaultModelRepository,
                mediaProviderRepository = mediaProviderRepository,
            )
    }

    @Test
    fun invoke_returnsProviderWhenAssignmentAndProviderExist() =
        runTest {
            val providerId = MediaProviderId("edit-provider")
            val provider =
                MediaProviderAsset(
                    id = providerId,
                    displayName = "Edit",
                    provider = ApiProvider.FAL,
                    capability = MediaCapability.IMAGE,
                    credentialAlias = "fal",
                )
            coEvery { defaultModelRepository.getDefault(ModelType.IMAGE_EDIT) } returns
                DefaultModelAssignment(
                    modelType = ModelType.IMAGE_EDIT,
                    mediaProviderId = providerId,
                )
            coEvery { mediaProviderRepository.getMediaProvider(providerId) } returns provider

            val result = useCase(ModelType.IMAGE_EDIT)

            assertTrue(result.isSuccess)
            assertEquals(provider, result.getOrThrow())
        }

    @Test
    fun invoke_failsWhenNoAssignmentForModelType() =
        runTest {
            coEvery { defaultModelRepository.getDefault(ModelType.IMAGE_EDIT) } returns null

            val result = useCase(ModelType.IMAGE_EDIT)

            assertTrue(result.isFailure)
            assertEquals("No image edit provider assigned", result.exceptionOrNull()?.message)
        }

    @Test
    fun invoke_failsWhenAssignedProviderMissing() =
        runTest {
            val providerId = MediaProviderId("missing-provider")
            coEvery { defaultModelRepository.getDefault(ModelType.IMAGE_GENERATION) } returns
                DefaultModelAssignment(
                    modelType = ModelType.IMAGE_GENERATION,
                    mediaProviderId = providerId,
                )
            coEvery { mediaProviderRepository.getMediaProvider(providerId) } returns null

            val result = useCase(ModelType.IMAGE_GENERATION)

            assertTrue(result.isFailure)
            assertEquals("Assigned media provider not found", result.exceptionOrNull()?.message)
        }
}