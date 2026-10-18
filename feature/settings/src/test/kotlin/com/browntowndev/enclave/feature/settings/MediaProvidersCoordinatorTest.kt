package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.usecase.settings.SettingsApiProviderUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsMediaUseCases
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
class MediaProvidersCoordinatorTest {
    private val apiProviderUseCases = FakeSettingsApiProviderUseCases()
    private val mediaUseCases = FakeSettingsMediaUseCases()
    private val fetchApiProviderModelDetailUseCase =
        com.browntowndev.enclave.core.testing.FakeFetchApiProviderModelDetailUseCase()
    private val errorHandler = FakeViewModelErrorHandler()

    private lateinit var coordinator: MediaProvidersCoordinator

    @BeforeEach
    fun setup() {
        coordinator =
            MediaProvidersCoordinator(
                apiProviderUseCases = apiProviderUseCases,
                mediaUseCases = mediaUseCases,
                fetchApiProviderModelDetailUseCase = fetchApiProviderModelDetailUseCase,
                errorHandler = errorHandler,
            )
    }

    @Test
    fun `onShowMediaProvidersSheet updates sheet visibility`() =
        runTest {
            val mediaState = MutableStateFlow(MediaProvidersTransientState())

            coordinator.onShowMediaProvidersSheet(mediaState, show = true)

            assertTrue(mediaState.value.isSheetOpen)
        }

    @Test
    fun `onStartCreateMediaProviderAsset seeds image draft`() =
        runTest {
            val mediaState = MutableStateFlow(MediaProvidersTransientState())

            coordinator.onStartCreateMediaProviderAsset(mediaState)

            assertEquals(MediaCapability.IMAGE, mediaState.value.assetDraft?.capability)
            assertEquals(ApiProvider.OPENAI, mediaState.value.assetDraft?.provider)
        }

    @Test
    fun `onSelectMediaProviderAsset clears api key`() =
        runTest {
            val mediaState = MutableStateFlow(MediaProvidersTransientState())
            val apiState = MutableStateFlow(ApiProvidersTransientState())
            val currentApiKey = MutableStateFlow("secret")
            val asset =
                MediaProviderAssetUi(
                    provider = ApiProvider.OPENAI,
                    capability = MediaCapability.IMAGE,
                )

            coordinator.onSelectMediaProviderAsset(this, "tag", mediaState, apiState, currentApiKey, asset)

            assertEquals(asset, mediaState.value.selectedAsset)
            assertEquals("", currentApiKey.value)
        }

    @Test
    fun `onSaveMediaProvider reuses selected BYOK credential alias when api key is blank`() =
        runTest {
            val mediaState =
                MutableStateFlow(
                    MediaProvidersTransientState(
                        assetDraft =
                            MediaProviderAssetUi(
                                displayName = "OpenAI Images",
                                provider = ApiProvider.OPENAI,
                                capability = MediaCapability.IMAGE,
                                modelName = "dall-e-3",
                            ),
                        selectedReusableApiCredentialAlias = "openai",
                    ),
                )
            val currentApiKey = MutableStateFlow("")
            val assignmentsUseCases = FakeSettingsAssignmentUseCases()

            coordinator.onSaveMediaProvider(
                scope = this,
                tag = "tag",
                mediaState = mediaState,
                currentApiKey = currentApiKey,
                assignmentsUseCases = assignmentsUseCases,
                onSuccess = {},
            )
            advanceUntilIdle()

            assertEquals(1, mediaUseCases.saveMediaProvider.calls.size)
            assertEquals("openai", mediaUseCases.saveMediaProvider.calls.single().first.credentialAlias)
            assertEquals(null, mediaUseCases.saveMediaProvider.calls.single().second)
        }

    @Test
    fun `onFetchMediaModels for FAL music filters out TTS models and keeps music models`() =
        runTest {
            val mediaState =
                MutableStateFlow(
                    MediaProvidersTransientState(
                        assetDraft =
                            MediaProviderAssetUi(
                                provider = ApiProvider.FAL,
                                capability = MediaCapability.MUSIC,
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

            apiProviderUseCases.discoverApiModels.result =
                com.browntowndev.enclave.domain.usecase.settings.ApiModelDiscoveryResult(
                    models = discoveredModels,
                    scope = mockScope,
                )

            coordinator.onFetchMediaModels(
                scope = this,
                tag = "tag",
                mediaState = mediaState,
                currentApiKey = currentApiKey,
            )

            advanceUntilIdle()

            // Verify only MUSIC candidate models remain (stable-audio, ace-step)
            val resultModels = mediaState.value.discoveredApiModels
            assertEquals(2, resultModels.size)
            assertTrue(resultModels.any { it.id == "fal-ai/stable-audio" })
            assertTrue(resultModels.any { it.id == "fal-ai/ace-step" })
        }

    @Test
    fun `onFetchMediaModels keeps full image catalog when draft already has saved tasks`() =
        runTest {
            val mediaState =
                MutableStateFlow(
                    MediaProvidersTransientState(
                        assetDraft =
                            MediaProviderAssetUi(
                                provider = ApiProvider.FAL,
                                capability = MediaCapability.IMAGE,
                                modelName = "fal-ai/recraft/v3",
                                tasks = setOf(com.browntowndev.enclave.domain.model.config.MediaTask.TEXT_TO_IMAGE),
                                credentialAlias = "alias",
                            ),
                    ),
                )
            val currentApiKey = MutableStateFlow("api-key")
            val discoveredModels =
                listOf(
                    com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel(
                        id = "fal-ai/recraft/v3",
                        isImageGenerationCapable = true,
                        tasks = setOf(com.browntowndev.enclave.domain.model.config.MediaTask.TEXT_TO_IMAGE),
                    ),
                    com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel(
                        id = "fal-ai/flux-pro/kontext",
                        isImageGenerationCapable = true,
                        tasks =
                            setOf(
                                com.browntowndev.enclave.domain.model.config.MediaTask.TEXT_TO_IMAGE,
                                com.browntowndev.enclave.domain.model.config.MediaTask.EDIT_IMAGE,
                            ),
                    ),
                )

            apiProviderUseCases.discoverApiModels.result =
                com.browntowndev.enclave.domain.usecase.settings.ApiModelDiscoveryResult(
                    models = discoveredModels,
                    scope =
                        com.browntowndev.enclave.domain.usecase.settings.ApiModelDiscoveryScope(
                            provider = ApiProvider.FAL,
                            baseUrl = "",
                            credentialAlias = "alias",
                        ),
                )

            coordinator.onFetchMediaModels(
                scope = this,
                tag = "tag",
                mediaState = mediaState,
                currentApiKey = currentApiKey,
            )
            advanceUntilIdle()

            assertEquals(2, mediaState.value.discoveredApiModels.size)
        }

    @Test
    fun `isFalTtsModelCandidate includes speech and tts models but excludes elevenlabs and music`() {
        assertTrue("fal-ai/minimax/speech-02-hd".isFalTtsModelCandidate())
        assertTrue("fal-ai/foo/tts-v1".isFalTtsModelCandidate())

        // ElevenLabs excluded — incompatible voice IDs and request schema
        assertTrue(!"fal-ai/elevenlabs/tts/turbo-v2.5".isFalTtsModelCandidate())
        assertTrue(!"fal-ai/stable-audio".isFalTtsModelCandidate())
        assertTrue(!"fal-ai/ace-step".isFalTtsModelCandidate())
        assertTrue(!"fal-ai/custom-image".isFalTtsModelCandidate())
    }

    @Test
    fun `isFalMusicModelCandidate filters out TTS models and includes stable audio`() {
        assertTrue("fal-ai/stable-audio".isFalMusicModelCandidate())
        assertTrue("fal-ai/ace-step".isFalMusicModelCandidate())
        assertTrue("fal-ai/stable-audio-music".isFalMusicModelCandidate())

        // Excludes
        assertTrue(!"fal-ai/elevenlabs/tts/turbo-v2.5".isFalMusicModelCandidate())
        assertTrue(!"fal-ai/minimax/speech-02-hd".isFalMusicModelCandidate())
        assertTrue(!"fal-ai/custom-image".isFalMusicModelCandidate())
    }

    @Test
    fun `filterMediaModels narrows image discovery by draft tasks when known`() {
        val models =
            listOf(
                com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel(
                    id = "fal-ai/recraft/v3",
                    isImageGenerationCapable = true,
                    tasks = setOf(com.browntowndev.enclave.domain.model.config.MediaTask.TEXT_TO_IMAGE),
                ),
                com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel(
                    id = "fal-ai/flux-pro/kontext",
                    isImageGenerationCapable = true,
                    tasks =
                        setOf(
                            com.browntowndev.enclave.domain.model.config.MediaTask.TEXT_TO_IMAGE,
                            com.browntowndev.enclave.domain.model.config.MediaTask.EDIT_IMAGE,
                        ),
                ),
            )

        val result =
            models.filterMediaModels(
                capability = com.browntowndev.enclave.domain.model.config.MediaCapability.IMAGE,
                provider = com.browntowndev.enclave.domain.model.inference.ApiProvider.FAL,
                requiredTasks = setOf(com.browntowndev.enclave.domain.model.config.MediaTask.EDIT_IMAGE),
            )

        assertEquals(1, result.size)
        assertEquals("fal-ai/flux-pro/kontext", result.single().id)
    }

    @Test
    fun `filterMediaModels FAL MUSIC excludes TTS speech models and includes music models`() {
        val models =
            listOf(
                com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel(
                    id = "fal-ai/stable-audio",
                    isAudioGenerationCapable = true,
                ),
                com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel(
                    id = "fal-ai/ace-step",
                    isAudioGenerationCapable = true,
                ),
                com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel(
                    id = "fal-ai/minimax/speech-02-hd",
                    isAudioGenerationCapable = true,
                ),
                com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel(
                    id = "fal-ai/elevenlabs/tts/turbo-v2.5",
                    isAudioGenerationCapable = true,
                ),
            )

        val result = models.filterMediaModels(MediaCapability.MUSIC, ApiProvider.FAL)

        assertEquals(2, result.size)
        assertTrue(result.any { it.id == "fal-ai/stable-audio" })
        assertTrue(result.any { it.id == "fal-ai/ace-step" })
        assertTrue(result.none { it.id == "fal-ai/minimax/speech-02-hd" })
        assertTrue(result.none { it.id == "fal-ai/elevenlabs/tts/turbo-v2.5" })
    }
}
