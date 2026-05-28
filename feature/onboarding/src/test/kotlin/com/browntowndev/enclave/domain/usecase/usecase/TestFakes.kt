package com.browntowndev.enclave.domain.usecase.usecase

import com.browntowndev.enclave.domain.model.config.ApiCredentials
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.config.TtsProviderId
import com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.usecase.byok.SaveApiCredentialsUseCase
import com.browntowndev.enclave.domain.usecase.byok.SaveApiModelConfigurationUseCase
import com.browntowndev.enclave.domain.usecase.byok.SetDefaultModelUseCase
import com.browntowndev.enclave.domain.usecase.onboarding.BulkAssignApiProviderUseCase
import com.browntowndev.enclave.domain.usecase.settings.ApiModelDiscoveryRequest
import com.browntowndev.enclave.domain.usecase.settings.ApiModelDiscoveryResult
import com.browntowndev.enclave.domain.usecase.settings.ApiModelDiscoveryScope

// ---------------------------------------------------------------------------
// FakeBulkAssignApiProviderUseCase
// ---------------------------------------------------------------------------

class FakeBulkAssignApiProviderUseCase : BulkAssignApiProviderUseCase {
    var callCount: Int = 0
    var resultToReturn: Result<ApiCredentialsId> = Result.success(ApiCredentialsId("fake-creds"))
    val recordedSlots: MutableList<List<ModelType>> = mutableListOf()

    override suspend fun invoke(
        credentials: ApiCredentials,
        apiKey: String,
        modelId: String,
        displayName: String,
        targetSlots: List<ModelType>,
    ): Result<ApiCredentialsId> {
        callCount++
        recordedSlots.add(targetSlots)
        return resultToReturn
    }
}

// ---------------------------------------------------------------------------
// FakeSetDefaultModelUseCase
// ---------------------------------------------------------------------------

class FakeSetDefaultModelUseCase : SetDefaultModelUseCase {
    data class Call(
        val modelType: ModelType,
        val localConfigId: LocalModelConfigurationId?,
        val apiConfigId: ApiModelConfigurationId?,
    )

    val recordedCalls: MutableList<Call> = mutableListOf()

    override suspend fun invoke(
        modelType: ModelType,
        localConfigId: LocalModelConfigurationId?,
        apiConfigId: ApiModelConfigurationId?,
        ttsProviderId: TtsProviderId?,
        mediaProviderId: MediaProviderId?,
    ) {
        recordedCalls.add(Call(modelType, localConfigId, apiConfigId))
    }
}

// ---------------------------------------------------------------------------
// FakeSaveApiCredentialsUseCase
// ---------------------------------------------------------------------------

class FakeSaveApiCredentialsUseCase : SaveApiCredentialsUseCase {
    var idToReturn: ApiCredentialsId = ApiCredentialsId("fake-creds")

    override suspend fun invoke(
        credentials: ApiCredentials,
        apiKey: String,
        sourceCredentialAlias: String?,
    ): ApiCredentialsId = idToReturn
}

// ---------------------------------------------------------------------------
// FakeSaveApiModelConfigurationUseCase
// ---------------------------------------------------------------------------

class FakeSaveApiModelConfigurationUseCase : SaveApiModelConfigurationUseCase {
    var idToReturn: Result<ApiModelConfigurationId> =
        Result.success(ApiModelConfigurationId("fake-config"))

    override suspend fun invoke(
        configuration: com.browntowndev.enclave.domain.model.config.ApiModelConfiguration,
    ): Result<ApiModelConfigurationId> = idToReturn
}

// ---------------------------------------------------------------------------
// FakeDiscoverApiModelsUseCase — wrapper that captures calls, doesn't call super
// ---------------------------------------------------------------------------

/**
 * Test double for [com.browntowndev.enclave.domain.usecase.settings.DiscoverApiModelsUseCase].
 * Cannot subclass because DiscoverApiModelsUseCase is a non-open final class; instead
 * we delegate using a functional contract defined in the ViewModel's constructor. Tests
 * that need to fake discovery inject a [FakeDiscoverApiModelsUseCaseWrapper] via the
 * [com.browntowndev.enclave.feature.onboarding.OnboardingViewModel] constructor that
 * accepts a [DiscoverModelsPort].
 *
 * Defined here for completeness — see [OnboardingViewModelTest] for actual usage pattern.
 */
object FakeDiscoverModelsPort {
    var modelsToReturn: List<DiscoveredApiModel> = emptyList()
    var shouldFail: Boolean = false
    var lastRequest: ApiModelDiscoveryRequest? = null

    fun reset() {
        modelsToReturn = emptyList()
        shouldFail = false
        lastRequest = null
    }

    suspend fun discover(request: ApiModelDiscoveryRequest): ApiModelDiscoveryResult {
        lastRequest = request
        if (shouldFail) throw RuntimeException("Discovery failed")
        return ApiModelDiscoveryResult(
            models = modelsToReturn,
            scope = ApiModelDiscoveryScope(
                provider = request.provider,
                baseUrl = request.baseUrl.orEmpty(),
                credentialAlias = request.credentialAlias,
            ),
        )
    }
}

// ---------------------------------------------------------------------------
// FakeLoggingPort
// ---------------------------------------------------------------------------

class FakeLoggingPort : LoggingPort {
    override fun debug(tag: String, message: String) {}
    override fun info(tag: String, message: String) {}
    override fun warning(tag: String, message: String) {}
    override fun error(tag: String, message: String, throwable: Throwable?) {}
    override fun recordException(tag: String, message: String, throwable: Throwable) {}
}
