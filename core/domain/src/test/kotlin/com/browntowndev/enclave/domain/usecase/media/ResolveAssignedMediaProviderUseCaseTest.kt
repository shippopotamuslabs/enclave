package com.browntowndev.enclave.domain.usecase.media

import com.browntowndev.enclave.core.testing.FakeDefaultModelRepository
import com.browntowndev.enclave.core.testing.FakeMediaProviderRepository
import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelType
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ResolveAssignedMediaProviderUseCaseTest {
    private val defaultModelRepository = FakeDefaultModelRepository()
    private val mediaProviderRepository = FakeMediaProviderRepository()
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
            defaultModelRepository.seed(
                DefaultModelAssignment(
                    modelType = ModelType.IMAGE_EDIT,
                    mediaProviderId = providerId,
                ),
            )
            mediaProviderRepository.emitProviders(listOf(provider))

            val result = useCase(ModelType.IMAGE_EDIT)

            assertTrue(result.isSuccess)
            assertEquals(provider, result.getOrThrow())
        }

    @Test
    fun invoke_failsWhenNoAssignmentForModelType() =
        runTest {
            val result = useCase(ModelType.IMAGE_EDIT)

            assertTrue(result.isFailure)
            assertEquals("No image edit provider assigned", result.exceptionOrNull()?.message)
        }

    @Test
    fun invoke_failsWhenAssignedProviderMissing() =
        runTest {
            val providerId = MediaProviderId("missing-provider")
            defaultModelRepository.seed(
                DefaultModelAssignment(
                    modelType = ModelType.IMAGE_GENERATION,
                    mediaProviderId = providerId,
                ),
            )

            val result = useCase(ModelType.IMAGE_GENERATION)

            assertTrue(result.isFailure)
            assertEquals("Assigned media provider not found", result.exceptionOrNull()?.message)
        }
}
