package com.browntowndev.enclave.domain.usecase.onboarding

import com.browntowndev.enclave.core.testing.FakeActiveModelProvider
import com.browntowndev.enclave.core.testing.FakeApiModelRepository
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiCredentials
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.onboarding.OnboardingFlowMode
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ResolveOnboardingFlowModeUseCaseTest {
    private lateinit var apiModelRepository: FakeApiModelRepository
    private lateinit var activeModelProvider: FakeActiveModelProvider
    private lateinit var useCase: ResolveOnboardingFlowModeUseCaseImpl

    @BeforeEach
    fun setUp() {
        apiModelRepository = FakeApiModelRepository()
        activeModelProvider = FakeActiveModelProvider()
        useCase =
            ResolveOnboardingFlowModeUseCaseImpl(
                apiModelRepository = apiModelRepository,
                activeModelProvider = activeModelProvider,
            )
    }

    @Test
    @DisplayName("OB-FLOW-01 — No synced data yields FULL flow")
    fun `invoke with no credentials and no slot configs returns FULL`() =
        runTest {
            assertEquals(OnboardingFlowMode.FULL, useCase())
        }

    @Test
    @DisplayName("OB-FLOW-02 — Synced API credentials yield RESTORED flow")
    fun `invoke with credentials returns RESTORED`() =
        runTest {
            apiModelRepository.seedCredentials(
                listOf(
                    ApiCredentials(
                        id = ApiCredentialsId("cred-1"),
                        displayName = "OpenRouter",
                        provider = ApiProvider.OPENROUTER,
                        credentialAlias = "openrouter",
                    ),
                ),
            )

            assertEquals(OnboardingFlowMode.RESTORED, useCase())
        }

    @Test
    @DisplayName("OB-FLOW-03 — Synced default slot configuration yields RESTORED flow")
    fun `invoke with configured text slot returns RESTORED`() =
        runTest {
            activeModelProvider.configurationsByType[ModelType.MAIN] =
                ActiveModelConfiguration(
                    id = ApiModelConfigurationId("cfg-main"),
                    isLocal = false,
                    name = "Synced Main",
                    modelId = "gpt-4",
                    systemPrompt = null,
                    temperature = null,
                    topK = null,
                    topP = null,
                    maxTokens = null,
                    minP = null,
                    repetitionPenalty = null,
                    contextWindow = null,
                    thinkingEnabled = false,
                )

            assertEquals(OnboardingFlowMode.RESTORED, useCase())
        }
}
