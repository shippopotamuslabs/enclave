package com.browntowndev.enclave.domain.usecase.auth

import com.browntowndev.enclave.core.testing.FakeApiModelRepository
import com.browntowndev.enclave.core.testing.FakeAuthManager
import com.browntowndev.enclave.core.testing.FakeCleanupRepository
import com.browntowndev.enclave.core.testing.FakeCloudSyncPort
import com.browntowndev.enclave.core.testing.FakeDefaultModelRepository
import com.browntowndev.enclave.core.testing.FakeGpuCapability
import com.browntowndev.enclave.core.testing.FakeLocalImageGenerationCapability
import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
import com.browntowndev.enclave.core.testing.FakeMediaProviderRepository
import com.browntowndev.enclave.core.testing.FakeModelConfigFetcher
import com.browntowndev.enclave.core.testing.FakeModelPackVerifierPort
import com.browntowndev.enclave.core.testing.FakeTtsProviderRepository
import com.browntowndev.enclave.core.testing.FakeUtilityModelFile
import com.browntowndev.enclave.domain.usecase.byok.GetApiModelAssetsUseCaseImpl
import com.browntowndev.enclave.domain.usecase.byok.GetDefaultModelsUseCaseImpl
import com.browntowndev.enclave.domain.usecase.byok.SetDefaultModelUseCaseImpl
import com.browntowndev.enclave.domain.usecase.download.ResolveModelCatalogUseCase
import com.browntowndev.enclave.domain.usecase.download.VerifyModelPackCompletenessUseCase
import com.browntowndev.enclave.domain.usecase.media.GetMediaProvidersUseCase
import com.browntowndev.enclave.domain.usecase.media.LocalStableDiffusionTargetResolver
import com.browntowndev.enclave.domain.usecase.media.SelectLocalImageGenerationBackendUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.GetLocalModelAssetsUseCaseImpl
import com.browntowndev.enclave.domain.usecase.settings.AutoAssignLocalModelsUseCase
import com.browntowndev.enclave.domain.usecase.settings.GetTtsProvidersUseCase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Recording [AutoAssignLocalModelsUseCase] fake (ENC-28): counts invocations, does no assignment work. */
private class FakeAutoAssignLocalModelsUseCase : AutoAssignLocalModelsUseCase(
    getLocalModelAssetsUseCase = GetLocalModelAssetsUseCaseImpl(FakeLocalModelRepository()),
    getDefaultModelsUseCase = GetDefaultModelsUseCaseImpl(FakeDefaultModelRepository()),
    setDefaultModelUseCase =
        SetDefaultModelUseCaseImpl(
            FakeDefaultModelRepository(),
            FakeApiModelRepository(),
            FakeLocalModelRepository(),
            VerifyModelPackCompletenessUseCase(
                FakeModelPackVerifierPort(),
                ResolveModelCatalogUseCase(FakeModelConfigFetcher()),
            ),
            ResolveModelCatalogUseCase(FakeModelConfigFetcher()),
            FakeGpuCapability(),
            FakeLocalImageGenerationCapability(),
            LocalStableDiffusionTargetResolver(
                FakeLocalModelRepository(),
                FakeUtilityModelFile(),
                FakeGpuCapability(),
                FakeLocalImageGenerationCapability(),
                SelectLocalImageGenerationBackendUseCase(),
            ),
        ),
    getApiModelAssetsUseCase = GetApiModelAssetsUseCaseImpl(FakeApiModelRepository()),
    getTtsProvidersUseCase = GetTtsProvidersUseCase(FakeTtsProviderRepository()),
    getMediaProvidersUseCase = GetMediaProvidersUseCase(FakeMediaProviderRepository()),
    capabilityPort = FakeLocalImageGenerationCapability(),
) {
    var invokeCallCount = 0

    override suspend fun invoke() {
        invokeCallCount++
    }
}

class LogoutUseCaseTest {
    private val authManager = FakeAuthManager()
    private val cleanupRepository = FakeCleanupRepository()
    private val cloudSyncPort = FakeCloudSyncPort()
    private val autoAssignLocalModelsUseCase = FakeAutoAssignLocalModelsUseCase()
    private val logoutUseCase =
        LogoutUseCase(
            authManager,
            cleanupRepository,
            cloudSyncPort,
            autoAssignLocalModelsUseCase,
        )

    @Test
    fun `invoke signs out and clears user content while preserving models`() =
        runTest {
            authManager.signOutResult = Result.success(Unit)

            logoutUseCase()

            assertEquals(1, authManager.signOutCallCount)
            assertEquals(1, cloudSyncPort.count("stopListening"))
            assertEquals(1, cleanupRepository.clearUserContentCallCount)
            assertEquals(0, cleanupRepository.clearAllDataCallCount)
            assertEquals(1, autoAssignLocalModelsUseCase.invokeCallCount)
        }

    @Test
    fun `invoke does not clear data on sign out failure`() =
        runTest {
            authManager.signOutResult = Result.failure(Exception("Sign out failed"))

            logoutUseCase()

            assertEquals(1, authManager.signOutCallCount)
            assertEquals(0, cleanupRepository.clearUserContentCallCount)
            assertEquals(0, cleanupRepository.clearAllDataCallCount)
            assertEquals(0, autoAssignLocalModelsUseCase.invokeCallCount)
        }

    @Test
    fun `invoke returns success even if cleanup fails`() =
        runTest {
            authManager.signOutResult = Result.success(Unit)
            cleanupRepository.clearUserContentFailure = Exception("Database error")

            val result = logoutUseCase()

            assertTrue(result.isSuccess)
            assertEquals(1, authManager.signOutCallCount)
            assertEquals(1, cleanupRepository.clearUserContentCallCount)
        }
}
