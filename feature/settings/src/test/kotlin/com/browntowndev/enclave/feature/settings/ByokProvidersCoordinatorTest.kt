package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.config.ApiCredentials
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelAsset
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.usecase.byok.FetchOpenRouterModelEndpointsUseCase
import com.browntowndev.enclave.domain.usecase.settings.ApiPresetDraft
import com.browntowndev.enclave.domain.usecase.settings.ApiProviderDraft
import com.browntowndev.enclave.domain.usecase.settings.ApiProviderDraftSaveResult
import com.browntowndev.enclave.domain.usecase.settings.ApplyApiModelMetadataDefaultsUseCase
import com.browntowndev.enclave.domain.usecase.settings.SaveApiPresetUseCase
import com.browntowndev.enclave.domain.usecase.settings.SaveApiProviderDraftUseCase
import com.browntowndev.enclave.domain.usecase.settings.SettingsApiProviderUseCases
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class ByokProvidersCoordinatorTest {
    private val apiProviderUseCases = mockk<SettingsApiProviderUseCases>(relaxed = true)
    private val deletionHelper = mockk<SettingsDeletionHelper>(relaxed = true)
    private val errorHandler = mockk<ViewModelErrorHandler>()
    private val loggingPort = mockk<LoggingPort>(relaxed = true)

    private lateinit var coordinator: ByokProvidersCoordinator

    @BeforeEach
    fun setup() {
        every {
            apiProviderUseCases.applyApiModelMetadataDefaults
        } returns ApplyApiModelMetadataDefaultsUseCase()
        every {
            errorHandler.coroutineExceptionHandler(any(), any(), any())
        } returns CoroutineExceptionHandler { _, _ -> }

        coordinator =
            ByokProvidersCoordinator(
                apiProviderUseCases = apiProviderUseCases,
                deletionHelper = deletionHelper,
                apiModelAssetUiMapper = ApiModelAssetUiMapper(),
                errorHandler = errorHandler,
                loggingPort = loggingPort,
            )
    }

    @Test
    fun `onShowByokSheet updates sheet visibility`() =
        runTest {
            val apiState = MutableStateFlow(ApiProvidersTransientState())

            coordinator.onShowByokSheet(apiState, show = true)

            assertTrue(apiState.value.isSheetOpen)
        }

    @Test
    fun `onSelectApiProviderAsset clears key and resets preset`() =
        runTest {
            val apiState =
                MutableStateFlow(
                    ApiProvidersTransientState(
                        presetDraft = ApiModelConfigUi(credentialsId = ApiCredentialsId("existing")),
                    ),
                )
            val currentApiKey = MutableStateFlow("secret")
            val asset =
                ApiModelAssetUi(
                    credentialsId = ApiCredentialsId("cred"),
                    displayName = "OpenAI",
                    provider = ApiProvider.OPENAI,
                    baseUrl = null,
                    credentialAlias = "alias",
                    configurations = emptyList(),
                )

            coordinator.onSelectApiProviderAsset(this, apiState, currentApiKey, asset)

            assertEquals(asset, apiState.value.selectedAsset)
            assertEquals(asset, apiState.value.assetDraft)
            assertNull(apiState.value.presetDraft)
            assertEquals("", currentApiKey.value)
        }

    @Test
    fun `onApiModelConfigFieldChange updates preset draft`() =
        runTest {
            val apiState = MutableStateFlow(ApiProvidersTransientState())
            val config =
                ApiModelConfigUi(
                    credentialsId = ApiCredentialsId("cred"),
                    displayName = "Preset",
                )

            coordinator.onApiModelConfigFieldChange(apiState, config)

            assertEquals(config, apiState.value.presetDraft)
        }

    @Test
    fun `onSaveApiCredentials persists draft clears key and updates selection`() =
        runTest {
            val saveDraft = mockk<SaveApiProviderDraftUseCase>()
            every { apiProviderUseCases.saveApiProviderDraft } returns saveDraft
            val draftSlot = slot<ApiProviderDraft>()
            val persistedAsset =
                ApiModelAsset(
                    credentials =
                        ApiCredentials(
                            id = ApiCredentialsId("saved-cred"),
                            displayName = "OpenAI",
                            provider = ApiProvider.OPENAI,
                            credentialAlias = "openai-alias",
                        ),
                    configurations = emptyList(),
                )
            coEvery { saveDraft(capture(draftSlot)) } returns
                Result.success(
                    ApiProviderDraftSaveResult(persistedAsset = persistedAsset),
                )
            val apiState =
                MutableStateFlow(
                    ApiProvidersTransientState(
                        assetDraft =
                            ApiModelAssetUi(
                                credentialsId = ApiCredentialsId(""),
                                displayName = "OpenAI",
                                provider = ApiProvider.OPENAI,
                                baseUrl = null,
                                credentialAlias = "openai-alias",
                                configurations = emptyList(),
                            ),
                    ),
                )
            val currentApiKey = MutableStateFlow("sk-test")
            var successAsset: ApiModelAssetUi? = null

            coordinator.onSaveApiCredentials(
                scope = this,
                tag = "Settings",
                apiState = apiState,
                currentApiKey = currentApiKey,
                onSuccess = { asset, _ -> successAsset = asset },
                onShowSnackbar = {},
            )
            advanceUntilIdle()

            coVerify(exactly = 1) { saveDraft(any()) }
            assertEquals("sk-test", draftSlot.captured.apiKey)
            assertEquals("", currentApiKey.value)
            assertEquals(ApiCredentialsId("saved-cred"), apiState.value.selectedAsset?.credentialsId)
            assertEquals("OpenAI", apiState.value.assetDraft?.displayName)
            assertEquals(ApiCredentialsId("saved-cred"), successAsset?.credentialsId)
        }

    @Test
    fun `onStartCreateApiModelAsset seeds empty draft`() =
        runTest {
            val apiState = MutableStateFlow(ApiProvidersTransientState(isSheetOpen = true))

            coordinator.onStartCreateApiModelAsset(apiState)

            assertNull(apiState.value.selectedAsset)
            assertEquals(ApiProvider.OPENAI, apiState.value.assetDraft?.provider)
            assertFalse(apiState.value.isDiscoveringApiModels)
        }

    @Test
    fun `onSaveApiModelConfig forwards parameter exclusions to saveApiPreset`() =
        runTest {
            val saveApiPreset = mockk<SaveApiPresetUseCase>()
            every { apiProviderUseCases.saveApiPreset } returns saveApiPreset
            val draftSlot = slot<ApiPresetDraft>()
            coEvery {
                saveApiPreset(
                    provider = any(),
                    parentCredentialsId = any(),
                    defaultReasoningEffort = any(),
                    draft = capture(draftSlot),
                )
            } returns Result.success(ApiModelConfigurationId("preset-1"))

            val credentialsId = ApiCredentialsId("cred-1")
            val apiState =
                MutableStateFlow(
                    ApiProvidersTransientState(
                        assetDraft =
                            ApiModelAssetUi(
                                credentialsId = credentialsId,
                                displayName = "OpenAI",
                                provider = ApiProvider.OPENAI,
                                baseUrl = null,
                                credentialAlias = "openai",
                                configurations = emptyList(),
                            ),
                        presetDraft =
                            ApiModelConfigUi(
                                credentialsId = credentialsId,
                                modelId = "o3-mini",
                                displayName = "Reasoning",
                                maxTokensDisabled = true,
                                temperatureDisabled = true,
                                topPDisabled = true,
                                topKDisabled = false,
                                minPDisabled = false,
                                frequencyPenaltyDisabled = true,
                                presencePenaltyDisabled = false,
                            ),
                    ),
                )

            coordinator.onSaveApiModelConfig(
                scope = this,
                tag = "Settings",
                apiState = apiState,
                onSuccess = {},
            )
            advanceUntilIdle()

            coVerify(exactly = 1) { saveApiPreset(any(), any(), any(), any()) }
            assertEquals(true, draftSlot.captured.maxTokensDisabled)
            assertEquals(true, draftSlot.captured.temperatureDisabled)
            assertEquals(true, draftSlot.captured.topPDisabled)
            assertEquals(false, draftSlot.captured.topKDisabled)
            assertEquals(false, draftSlot.captured.minPDisabled)
            assertEquals(true, draftSlot.captured.frequencyPenaltyDisabled)
            assertEquals(false, draftSlot.captured.presencePenaltyDisabled)
        }

    @Test
    @org.junit.jupiter.api.DisplayName("SCN-UI-STATE-001")
    fun scnUiState001_onShowOpenRouterProvidersSheetUpdatesUiStateOnSuccess() =
        runTest {
            val fetchUseCase = mockk<FetchOpenRouterModelEndpointsUseCase>()
            every { apiProviderUseCases.fetchOpenRouterModelEndpoints } returns fetchUseCase

            val mockEndpoints =
                listOf(
                    com.browntowndev.enclave.domain.model.config.OpenRouterModelEndpoint(
                        providerName = "Together",
                        providerSlug = "together",
                        contextLength = 131072,
                        promptPriceUsdPerMillion = 0.7,
                        completionPriceUsdPerMillion = 0.9,
                        quantization = "fp8",
                        isOperational = true,
                        uptime1d = 99.95,
                        latency30m = 0.25,
                        throughput30m = 45.0,
                    ),
                )

            coEvery {
                fetchUseCase(
                    modelId = "meta-llama/llama-3.3-70b-instruct",
                    currentApiKey = "sk-or-key",
                    credentialAlias = "alias",
                    baseUrl = "https://openrouter.ai",
                )
            } returns mockEndpoints

            val apiState = MutableStateFlow(ApiProvidersTransientState())

            coordinator.onShowOpenRouterProvidersSheet(
                scope = this,
                apiState = apiState,
                show = true,
                modelId = "meta-llama/llama-3.3-70b-instruct",
                currentApiKey = "sk-or-key",
                credentialAlias = "alias",
                baseUrl = "https://openrouter.ai",
            )

            assertTrue(apiState.value.isOpenRouterProvidersSheetOpen)
            assertTrue(apiState.value.isLoadingEndpoints)
            assertFalse(apiState.value.endpointFetchError)

            advanceUntilIdle()

            assertFalse(apiState.value.isLoadingEndpoints)
            assertEquals(mockEndpoints, apiState.value.openRouterEndpoints)
            assertFalse(apiState.value.endpointFetchError)
        }

    @Test
    @org.junit.jupiter.api.DisplayName("SCN-ERROR-001")
    fun scnError001_onShowOpenRouterProvidersSheetEmitsErrorOnFailure() =
        runTest {
            val fetchUseCase = mockk<FetchOpenRouterModelEndpointsUseCase>()
            every { apiProviderUseCases.fetchOpenRouterModelEndpoints } returns fetchUseCase

            coEvery {
                fetchUseCase(
                    modelId = "meta-llama/llama-3.3-70b-instruct",
                    currentApiKey = "sk-or-key",
                    credentialAlias = "alias",
                    baseUrl = "https://openrouter.ai",
                )
            } throws java.io.IOException("HTTP 500")

            val apiState = MutableStateFlow(ApiProvidersTransientState())
            var snackbarMessage: String? = null

            coordinator.onShowOpenRouterProvidersSheet(
                scope = this,
                apiState = apiState,
                show = true,
                modelId = "meta-llama/llama-3.3-70b-instruct",
                currentApiKey = "sk-or-key",
                credentialAlias = "alias",
                baseUrl = "https://openrouter.ai",
                onShowSnackbar = { msg -> snackbarMessage = msg },
            )

            advanceUntilIdle()

            assertFalse(apiState.value.isLoadingEndpoints)
            assertTrue(apiState.value.endpointFetchError)
            assertEquals("Failed to fetch providers", snackbarMessage)
            coVerify(exactly = 1) {
                loggingPort.error(
                    tag = "ByokProvidersCoordinator",
                    message = "Failed to fetch OpenRouter model endpoints",
                    throwable = any<java.io.IOException>()
                )
            }
        }

    @Test
    fun `onShowOpenRouterProvidersSheet with blank modelId does not fetch or open sheet`() =
        runTest {
            val fetchUseCase = mockk<FetchOpenRouterModelEndpointsUseCase>()
            every { apiProviderUseCases.fetchOpenRouterModelEndpoints } returns fetchUseCase

            val apiState = MutableStateFlow(ApiProvidersTransientState())
            var snackbarMessage: String? = null

            coordinator.onShowOpenRouterProvidersSheet(
                scope = this,
                apiState = apiState,
                show = true,
                modelId = "",
                currentApiKey = "sk-or-key",
                credentialAlias = "alias",
                baseUrl = "https://openrouter.ai",
                onShowSnackbar = { msg -> snackbarMessage = msg },
            )

            advanceUntilIdle()

            assertFalse(apiState.value.isOpenRouterProvidersSheetOpen)
            assertFalse(apiState.value.isLoadingEndpoints)
            assertFalse(apiState.value.endpointFetchError)
            assertNull(snackbarMessage)
            coVerify(exactly = 0) {
                fetchUseCase(
                    modelId = any(),
                    currentApiKey = any(),
                    credentialAlias = any(),
                    baseUrl = any(),
                )
            }
        }
}
