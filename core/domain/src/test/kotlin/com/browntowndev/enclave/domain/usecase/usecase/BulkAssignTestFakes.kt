package com.browntowndev.enclave.domain.usecase.usecase

import com.browntowndev.enclave.domain.model.config.ApiCredentials
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.config.TtsProviderId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.usecase.byok.SaveApiCredentialsUseCase
import com.browntowndev.enclave.domain.usecase.byok.SaveApiModelConfigurationUseCase
import com.browntowndev.enclave.domain.usecase.byok.SetDefaultModelUseCase

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

    override suspend fun invoke(configuration: ApiModelConfiguration): Result<ApiModelConfigurationId> =
        idToReturn
}
