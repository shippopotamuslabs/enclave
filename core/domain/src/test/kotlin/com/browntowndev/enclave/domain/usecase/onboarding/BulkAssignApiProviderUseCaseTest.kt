package com.browntowndev.enclave.domain.usecase.onboarding

import com.browntowndev.enclave.domain.model.config.ApiCredentials
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.usecase.usecase.FakeSaveApiCredentialsUseCase
import com.browntowndev.enclave.domain.usecase.usecase.FakeSaveApiModelConfigurationUseCase
import com.browntowndev.enclave.domain.usecase.usecase.FakeSetDefaultModelUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BulkAssignApiProviderUseCaseTest {

    private lateinit var fakeSaveCredentials: FakeSaveApiCredentialsUseCase
    private lateinit var fakeSaveConfig: FakeSaveApiModelConfigurationUseCase
    private lateinit var fakeSetDefault: FakeSetDefaultModelUseCase
    private lateinit var useCase: BulkAssignApiProviderUseCaseImpl

    @BeforeEach
    fun setUp() {
        fakeSaveCredentials = FakeSaveApiCredentialsUseCase()
        fakeSaveConfig = FakeSaveApiModelConfigurationUseCase()
        fakeSetDefault = FakeSetDefaultModelUseCase()

        useCase = BulkAssignApiProviderUseCaseImpl(
            saveApiCredentialsUseCase = fakeSaveCredentials,
            saveApiModelConfigurationUseCase = fakeSaveConfig,
            setDefaultModelUseCase = fakeSetDefault,
        )
    }

    // -------- OB-14 --------

    @Test
    @DisplayName("OB-14 — BulkAssignApiProviderUseCase assigns all six TEXT_SLOTS")
    fun `OB-14 invoke assigns credentials to all six TEXT_SLOTS and returns credentialsId`() = runTest {
        fakeSaveCredentials.idToReturn = ApiCredentialsId("creds-abc")
        fakeSaveConfig.idToReturn = Result.success(ApiModelConfigurationId("config-xyz"))

        val credentials = ApiCredentials(
            id = ApiCredentialsId(""),
            displayName = "OpenRouter",
            provider = ApiProvider.OPENROUTER,
            credentialAlias = "openrouter",
        )

        val result = useCase.invoke(
            credentials = credentials,
            apiKey = "sk-key-123",
            modelId = "openrouter/gpt-4o",
            displayName = "OpenRouter Preset",
            targetSlots = BulkAssignApiProviderUseCase.TEXT_SLOTS,
        )

        // Result must be success with the persisted credential id
        assertTrue(result.isSuccess)
        assertEquals(ApiCredentialsId("creds-abc"), result.getOrThrow())

        // SetDefaultModel must have been called exactly 6 times (one per TEXT_SLOT)
        assertEquals(6, fakeSetDefault.recordedCalls.size)

        // Every call must use the config-xyz id and null localConfigId
        val expectedSlots = listOf(
            ModelType.MAIN,
            ModelType.FAST,
            ModelType.THINKING,
            ModelType.DRAFT_ONE,
            ModelType.DRAFT_TWO,
            ModelType.FINAL_SYNTHESIS,
        )
        val recordedModelTypes = fakeSetDefault.recordedCalls.map { it.modelType }
        assertTrue(
            recordedModelTypes.containsAll(expectedSlots),
            "Expected all TEXT_SLOTS to be assigned, but got: $recordedModelTypes",
        )

        for (call in fakeSetDefault.recordedCalls) {
            assertEquals(
                ApiModelConfigurationId("config-xyz"),
                call.apiConfigId,
                "Each recorded call must have apiConfigId = config-xyz, but call for ${call.modelType} had ${call.apiConfigId}",
            )
            assertNull(
                call.localConfigId,
                "Each recorded call must have localConfigId = null, but call for ${call.modelType} had ${call.localConfigId}",
            )
        }
    }
}
