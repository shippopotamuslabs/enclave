package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.usecase.settings.SettingsApiProviderUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsTtsUseCases
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class TtsProvidersCoordinatorTest {
    private val apiProviderUseCases = mockk<SettingsApiProviderUseCases>(relaxed = true)
    private val ttsUseCases = mockk<SettingsTtsUseCases>(relaxed = true)
    private val errorHandler = mockk<ViewModelErrorHandler>()

    private lateinit var coordinator: TtsProvidersCoordinator

    @BeforeEach
    fun setup() {
        every {
            errorHandler.coroutineExceptionHandler(any(), any(), any())
        } returns CoroutineExceptionHandler { _, _ -> }

        coordinator =
            TtsProvidersCoordinator(
                apiProviderUseCases = apiProviderUseCases,
                ttsUseCases = ttsUseCases,
                errorHandler = errorHandler,
            )
    }

    @Test
    fun `onShowTtsProvidersSheet updates sheet visibility`() =
        runTest {
            val ttsState = MutableStateFlow(TtsProvidersTransientState())

            coordinator.onShowTtsProvidersSheet(ttsState, show = true)

            assertTrue(ttsState.value.isSheetOpen)
        }

    @Test
    fun `onStartCreateTtsProviderAsset seeds google draft`() =
        runTest {
            val ttsState = MutableStateFlow(TtsProvidersTransientState())

            coordinator.onStartCreateTtsProviderAsset(ttsState)

            assertEquals(ApiProvider.GOOGLE, ttsState.value.assetDraft?.provider)
        }

    @Test
    fun `onSelectTtsProviderAsset clears api key`() =
        runTest {
            val ttsState = MutableStateFlow(TtsProvidersTransientState())
            val currentApiKey = MutableStateFlow("secret")
            val asset = TtsProviderAssetUi(provider = ApiProvider.GOOGLE)

            coordinator.onSelectTtsProviderAsset(this, "tag", ttsState, currentApiKey, asset)

            assertEquals(asset, ttsState.value.selectedAsset)
            assertEquals("", currentApiKey.value)
        }

    @Test
    fun `onFetchTtsModels for FAL filters out music models and preserves selectedModelId`() =
        runTest {
            val ttsState =
                MutableStateFlow(
                    TtsProvidersTransientState(
                        assetDraft =
                            TtsProviderAssetUi(
                                provider = ApiProvider.FAL,
                                modelName = "fal-ai/elevenlabs/tts/turbo-v2.5",
                                credentialAlias = "alias",
                            ),
                    ),
                )
            val currentApiKey = MutableStateFlow("api-key")

            val discoveredModels =
                listOf(
                    com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel(
                        id = "fal-ai/stable-audio",
                        isAudioGenerationCapable = true,
                    ),
                    com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel(
                        id = "fal-ai/elevenlabs/tts/turbo-v2.5",
                        isAudioGenerationCapable = true,
                    ),
                    com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel(
                        id = "fal-ai/minimax/speech-02-hd",
                        isAudioGenerationCapable = true,
                    ),
                    com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel(
                        id = "fal-ai/ace-step",
                        isAudioGenerationCapable = true,
                    ),
                )

            val mockScope =
                com.browntowndev.enclave.domain.usecase.settings.ApiModelDiscoveryScope(
                    provider = ApiProvider.FAL,
                    baseUrl = "",
                    credentialAlias = "alias",
                )

            io.mockk.coEvery {
                apiProviderUseCases.discoverApiModels(any())
            } returns
                com.browntowndev.enclave.domain.usecase.settings.ApiModelDiscoveryResult(
                    models = discoveredModels,
                    scope = mockScope,
                )

            coordinator.onFetchTtsModels(
                scope = this,
                tag = "tag",
                ttsState = ttsState,
                currentApiKey = currentApiKey,
            )

            // Let coroutine finish executing
            advanceUntilIdle()

            // Verify only TTS candidate models remain (speech/tts), ElevenLabs and music models excluded
            val resultModels = ttsState.value.discoveredApiModels
            assertEquals(1, resultModels.size)
            assertTrue(resultModels.none { it.id == "fal-ai/elevenlabs/tts/turbo-v2.5" })
            assertTrue(resultModels.any { it.id == "fal-ai/minimax/speech-02-hd" })

            // ElevenLabs no longer passes isFalTtsModelCandidate, so selectedModelId is null
            io.mockk.coVerify {
                apiProviderUseCases.discoverApiModels(
                    com.browntowndev.enclave.domain.usecase.settings.ApiModelDiscoveryRequest(
                        provider = ApiProvider.FAL,
                        currentApiKey = "api-key",
                        credentialAlias = "alias",
                        baseUrl = null,
                        selectedModelId = null,
                    ),
                )
            }
        }

    @Test
    fun `onFetchTtsModels for Google filters out non-TTS models and preserves selectedModelId`() =
        runTest {
            val ttsState =
                MutableStateFlow(
                    TtsProvidersTransientState(
                        assetDraft =
                            TtsProviderAssetUi(
                                provider = ApiProvider.GOOGLE,
                                modelName = "gemini-1.5-flash-tts",
                                credentialAlias = "alias",
                            ),
                    ),
                )
            val currentApiKey = MutableStateFlow("api-key")

            val discoveredModels =
                listOf(
                    com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel(
                        id = "gemini-1.5-flash-tts",
                    ),
                    com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel(
                        id = "gemini-1.5-pro",
                    ),
                )

            val mockScope =
                com.browntowndev.enclave.domain.usecase.settings.ApiModelDiscoveryScope(
                    provider = ApiProvider.GOOGLE,
                    baseUrl = "",
                    credentialAlias = "alias",
                )

            io.mockk.coEvery {
                apiProviderUseCases.discoverApiModels(any())
            } returns
                com.browntowndev.enclave.domain.usecase.settings.ApiModelDiscoveryResult(
                    models = discoveredModels,
                    scope = mockScope,
                )

            coordinator.onFetchTtsModels(
                scope = this,
                tag = "tag",
                ttsState = ttsState,
                currentApiKey = currentApiKey,
            )

            // Let coroutine finish executing
            advanceUntilIdle()

            // Verify only TTS candidate model remains
            val resultModels = ttsState.value.discoveredApiModels
            assertEquals(1, resultModels.size)
            assertEquals("gemini-1.5-flash-tts", resultModels.single().id)

            // Verify selectedModelId was passed because it is a valid Google TTS candidate
            io.mockk.coVerify {
                apiProviderUseCases.discoverApiModels(
                    com.browntowndev.enclave.domain.usecase.settings.ApiModelDiscoveryRequest(
                        provider = ApiProvider.GOOGLE,
                        currentApiKey = "api-key",
                        credentialAlias = "alias",
                        baseUrl = null,
                        selectedModelId = "gemini-1.5-flash-tts",
                    ),
                )
            }
        }
}
