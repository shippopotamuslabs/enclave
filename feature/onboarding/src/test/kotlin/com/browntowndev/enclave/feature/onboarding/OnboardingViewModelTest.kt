package com.browntowndev.enclave.feature.onboarding

import com.browntowndev.enclave.domain.usecase.onboarding.CompleteOnboardingUseCase
import com.browntowndev.enclave.domain.usecase.onboarding.ConfirmAgeUseCase
import com.browntowndev.enclave.domain.usecase.onboarding.SetAutomaticErrorTrackingUseCase

import app.cash.turbine.test
import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.config.TtsProviderId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.onboarding.OnboardingFlowMode
import com.browntowndev.enclave.domain.usecase.onboarding.BulkAssignApiProviderUseCase
import com.browntowndev.enclave.domain.usecase.FakeSettingsRepository
import com.browntowndev.enclave.domain.usecase.media.SaveMediaProviderUseCase
import com.browntowndev.enclave.domain.usecase.onboarding.ResolveOnboardingFlowModeUseCase
import com.browntowndev.enclave.domain.usecase.settings.ApiModelDiscoveryResult
import com.browntowndev.enclave.domain.usecase.settings.ApiModelDiscoveryScope
import com.browntowndev.enclave.domain.usecase.settings.DiscoverApiModelsUseCase
import com.browntowndev.enclave.domain.usecase.settings.MediaProviderDraft
import com.browntowndev.enclave.domain.usecase.settings.SaveTtsProviderUseCase
import com.browntowndev.enclave.domain.usecase.settings.TtsProviderDraft
import com.browntowndev.enclave.domain.usecase.usecase.FakeBulkAssignApiProviderUseCase
import com.browntowndev.enclave.domain.usecase.usecase.FakeLoggingPort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class OnboardingViewModelTest {
    private lateinit var fakeSettingsRepository: FakeSettingsRepository
    private lateinit var fakeBulkAssign: FakeBulkAssignApiProviderUseCase
    private lateinit var fakeLogging: FakeLoggingPort

    // Concrete classes that cannot be subclassed — use mockk
    private val mockDiscoverModels: DiscoverApiModelsUseCase = mockk(relaxed = true)
    private val mockSaveTts: SaveTtsProviderUseCase = mockk(relaxed = true)
    private val mockSaveMedia: SaveMediaProviderUseCase = mockk(relaxed = true)
    private val mockResolveFlowMode: ResolveOnboardingFlowModeUseCase = mockk()

    private lateinit var viewModel: OnboardingViewModel

    @BeforeEach
    fun setUp() {
        fakeSettingsRepository = FakeSettingsRepository()
        fakeBulkAssign = FakeBulkAssignApiProviderUseCase()
        fakeLogging = FakeLoggingPort()

        // Default: discover returns empty list
        coEvery { mockDiscoverModels(any()) } returns
            ApiModelDiscoveryResult(
                models = emptyList(),
                scope =
                    ApiModelDiscoveryScope(
                        provider = ApiProvider.OPENROUTER,
                        baseUrl = "",
                        credentialAlias = null,
                    ),
            )
        coEvery { mockSaveTts(any()) } returns Result.success(TtsProviderId("fake-tts"))
        coEvery { mockSaveMedia(any(), any()) } returns MediaProviderId("fake-media")
        coEvery { mockResolveFlowMode() } returns OnboardingFlowMode.FULL

        viewModel =
            OnboardingViewModel(
                confirmAge = ConfirmAgeUseCase(fakeSettingsRepository),
                setAutomaticErrorTracking = SetAutomaticErrorTrackingUseCase(fakeSettingsRepository),
                completeOnboarding = CompleteOnboardingUseCase(fakeSettingsRepository),
                bulkAssignApiProviderUseCase = fakeBulkAssign,
                discoverApiModelsUseCase = mockDiscoverModels,
                saveTtsProviderUseCase = mockSaveTts,
                saveMediaProviderUseCase = mockSaveMedia,
                resolveOnboardingFlowModeUseCase = mockResolveFlowMode,
                loggingPort = fakeLogging,
            )
    }

    private fun createViewModelWithFlowMode(flowMode: OnboardingFlowMode): OnboardingViewModel {
        coEvery { mockResolveFlowMode() } returns flowMode
        return OnboardingViewModel(
            confirmAge = ConfirmAgeUseCase(fakeSettingsRepository),
            setAutomaticErrorTracking = SetAutomaticErrorTrackingUseCase(fakeSettingsRepository),
            completeOnboarding = CompleteOnboardingUseCase(fakeSettingsRepository),
            bulkAssignApiProviderUseCase = fakeBulkAssign,
            discoverApiModelsUseCase = mockDiscoverModels,
            saveTtsProviderUseCase = mockSaveTts,
            saveMediaProviderUseCase = mockSaveMedia,
            resolveOnboardingFlowModeUseCase = mockResolveFlowMode,
            loggingPort = fakeLogging,
        )
    }

    // -------- OB-02 --------

    @Test
    @DisplayName("OB-02 — Step 0 continue disabled when age not confirmed")
    fun `OB-02 step 0 initial state has currentStep 0 and ageConfirmed false`() =
        runTest {
            val state = viewModel.uiState.value

            assertEquals(0, state.currentStep)
            assertFalse(state.ageConfirmed)
        }

    // -------- OB-03 --------

    @Test
    @DisplayName("OB-03 — Step 0 advances to step 1 when age confirmed")
    fun `OB-03 onAgeConfirmedChange true then onNextStep advances to step 1`() =
        runTest {
            advanceUntilIdle()
            viewModel.onAgeConfirmedChange(true)
            viewModel.onTosAcceptedChange(true)
            viewModel.onNextStep()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(1, state.currentStep)
            assertTrue(state.ageConfirmed)
        }

    // -------- OB-04 --------

    @Test
    @DisplayName("OB-04 — Back on step 0 is no-op")
    fun `OB-04 onPreviousStep on step 0 is no-op`() =
        runTest {
            assertEquals(0, viewModel.uiState.value.currentStep)

            viewModel.onPreviousStep()
            advanceUntilIdle()

            assertEquals(0, viewModel.uiState.value.currentStep)
        }

    // -------- OB-05 --------

    @Test
    @DisplayName("OB-05 — Step 1 API path — model discovery populates model list")
    fun `OB-05 discover models populates discoveredModels list`() =
        runTest {
            val expectedModels =
                listOf(
                    DiscoveredApiModel(id = "openrouter/gpt-4o"),
                    DiscoveredApiModel(id = "openrouter/claude-3-haiku"),
                )
            coEvery { mockDiscoverModels(any()) } returns
                ApiModelDiscoveryResult(
                    models = expectedModels,
                    scope =
                        ApiModelDiscoveryScope(
                            provider = ApiProvider.OPENROUTER,
                            baseUrl = "",
                            credentialAlias = null,
                        ),
                )

            // Navigate to step 1
            viewModel.onAgeConfirmedChange(true)
            viewModel.onTosAcceptedChange(true)
            viewModel.onNextStep()
            advanceUntilIdle()

            viewModel.onModelChoiceSelected(ModelChoice.API)
            viewModel.onProviderSelected(ApiProvider.OPENROUTER)
            viewModel.onApiKeyChanged("sk-test-key")
            viewModel.onDiscoverModels()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isDiscoveringModels)
            assertEquals(2, state.discoveredModels.size)
            assertEquals("openrouter/gpt-4o", state.discoveredModels[0].id)
            assertNull(state.discoveryError)
        }

    // -------- OB-06 --------

    @Test
    @DisplayName("OB-06 — Step 1 API path — Continue disabled when apiKey blank")
    fun `OB-06 onNextStep with API choice and blank apiKey does not advance`() =
        runTest {
            // Navigate to step 1
            viewModel.onAgeConfirmedChange(true)
            viewModel.onTosAcceptedChange(true)
            viewModel.onNextStep()
            advanceUntilIdle()

            viewModel.onModelChoiceSelected(ModelChoice.API)
            viewModel.onProviderSelected(ApiProvider.OPENROUTER)
            // apiKey remains ""
            viewModel.onNextStep()
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.currentStep)
        }

    // -------- OB-07 --------

    @Test
    @DisplayName("OB-07 — Step 1 API path — Continue advances when key + model set")
    fun `OB-07 onNextStep with API choice key and model advances to step 2`() =
        runTest {
            // Navigate to step 1
            viewModel.onAgeConfirmedChange(true)
            viewModel.onTosAcceptedChange(true)
            viewModel.onNextStep()
            advanceUntilIdle()

            viewModel.onModelChoiceSelected(ModelChoice.API)
            viewModel.onProviderSelected(ApiProvider.OPENROUTER)
            viewModel.onApiKeyChanged("sk-valid-key")
            viewModel.onModelSelected("openrouter/gpt-4o")
            viewModel.onNextStep()
            advanceUntilIdle()

            assertEquals(2, viewModel.uiState.value.currentStep)
        }

    // -------- OB-08 --------

    @Test
    @DisplayName("OB-08 — Step 1 LOCAL path — Continue skips optional media setup")
    fun `OB-08 selecting LOCAL model choice and calling onNextStep advances to step 3`() =
        runTest {
            // Navigate to step 1
            viewModel.onAgeConfirmedChange(true)
            viewModel.onTosAcceptedChange(true)
            viewModel.onNextStep()
            advanceUntilIdle()

            viewModel.onModelChoiceSelected(ModelChoice.LOCAL)
            viewModel.onNextStep()
            advanceUntilIdle()

            assertEquals(3, viewModel.uiState.value.currentStep)
        }

    @Test
    @DisplayName("OB-08b — Back from LOCAL completion returns to model choice")
    fun `OB-08b onPreviousStep from LOCAL completion returns to step 1`() =
        runTest {
            viewModel.onAgeConfirmedChange(true)
            viewModel.onTosAcceptedChange(true)
            viewModel.onNextStep()
            advanceUntilIdle()
            viewModel.onModelChoiceSelected(ModelChoice.LOCAL)
            viewModel.onNextStep()
            advanceUntilIdle()

            viewModel.onPreviousStep()
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.currentStep)
        }

    // -------- OB-09 --------

    @Test
    @DisplayName("OB-09 — Step 2 — Skip advances to step 3 without saving")
    fun `OB-09 onSkipMedia advances to step 3 and does not call save use cases`() =
        runTest {
            // Navigate to step 2
            viewModel.onAgeConfirmedChange(true)
            viewModel.onTosAcceptedChange(true)
            viewModel.onNextStep()
            advanceUntilIdle()
            viewModel.onModelChoiceSelected(ModelChoice.API)
            viewModel.onProviderSelected(ApiProvider.OPENROUTER)
            viewModel.onApiKeyChanged("sk-valid-key")
            viewModel.onModelSelected("openrouter/gpt-4o")
            viewModel.onNextStep()
            advanceUntilIdle()

            assertEquals(2, viewModel.uiState.value.currentStep)

            viewModel.onSkipMedia()
            advanceUntilIdle()

            assertEquals(3, viewModel.uiState.value.currentStep)
            // Verify no saves were called
            coVerify(exactly = 0) { mockSaveTts(any()) }
            coVerify(exactly = 0) { mockSaveMedia(any(), any()) }
        }

    // -------- OB-10 --------

    @Test
    @DisplayName("OB-10 — Step 3 — Complete (API path) persists flags and emits NavigateToChat")
    fun `OB-10 onCompleteOnboarding API path persists flags and emits NavigateToChat`() =
        runTest {
            fakeBulkAssign.resultToReturn = Result.success(ApiCredentialsId("creds-123"))

            // Navigate to step 3 via API path
            viewModel.onAgeConfirmedChange(true)
            viewModel.onTosAcceptedChange(true)
            viewModel.onNextStep()
            advanceUntilIdle()
            viewModel.onModelChoiceSelected(ModelChoice.API)
            viewModel.onProviderSelected(ApiProvider.OPENROUTER)
            viewModel.onApiKeyChanged("sk-valid-key")
            viewModel.onModelSelected("openrouter/gpt-4o")
            viewModel.onNextStep()
            advanceUntilIdle()
            viewModel.onSkipMedia()
            advanceUntilIdle()

            assertEquals(3, viewModel.uiState.value.currentStep)

            viewModel.events.test {
                viewModel.onCompleteOnboarding()
                advanceUntilIdle()

                assertTrue(fakeSettingsRepository.ageConfirmed)
                assertTrue(fakeSettingsRepository.onboardingCompleted)

                val event = awaitItem() as OnboardingEvent.NavigateToChat
                assertEquals(ModelChoice.API, event.modelChoice)
                val expectedSlots = BulkAssignApiProviderUseCase.TEXT_SLOTS.toSet() + ModelType.VISION
                assertEquals(expectedSlots, event.apiSlots)

                val state = viewModel.uiState.value
                assertFalse(state.isSaving)
                assertNull(state.saveError)

                cancelAndIgnoreRemainingEvents()
            }
        }

    // -------- OB-11 --------

    @Test
    @DisplayName("OB-11 — Step 3 — Complete on LOCAL path skips bulk-assign")
    fun `OB-11 onCompleteOnboarding LOCAL path does not call bulkAssign`() =
        runTest {
            // Navigate to step 3 via LOCAL path
            viewModel.onAgeConfirmedChange(true)
            viewModel.onTosAcceptedChange(true)
            viewModel.onNextStep()
            advanceUntilIdle()
            viewModel.onModelChoiceSelected(ModelChoice.LOCAL)
            viewModel.onNextStep()
            advanceUntilIdle()

            assertEquals(3, viewModel.uiState.value.currentStep)

            viewModel.events.test {
                viewModel.onCompleteOnboarding()
                advanceUntilIdle()

                assertEquals(0, fakeBulkAssign.callCount)
                assertTrue(fakeSettingsRepository.onboardingCompleted)

                val event = awaitItem() as OnboardingEvent.NavigateToChat
                assertEquals(ModelChoice.LOCAL, event.modelChoice)
                assertEquals(emptySet<ModelType>(), event.apiSlots)

                cancelAndIgnoreRemainingEvents()
            }
        }

    // -------- OB-15 --------

    @Test
    @DisplayName("OB-15 — Save failure sets saveError and does NOT emit NavigateToChat")
    fun `OB-15 onCompleteOnboarding API path with failure sets saveError and emits no event`() =
        runTest {
            fakeBulkAssign.resultToReturn = Result.failure(RuntimeException("Network error"))

            // Navigate to step 3 via API path
            viewModel.onAgeConfirmedChange(true)
            viewModel.onTosAcceptedChange(true)
            viewModel.onNextStep()
            advanceUntilIdle()
            viewModel.onModelChoiceSelected(ModelChoice.API)
            viewModel.onProviderSelected(ApiProvider.OPENROUTER)
            viewModel.onApiKeyChanged("sk-valid-key")
            viewModel.onModelSelected("openrouter/gpt-4o")
            viewModel.onNextStep()
            advanceUntilIdle()
            viewModel.onSkipMedia()
            advanceUntilIdle()

            viewModel.events.test {
                viewModel.onCompleteOnboarding()
                advanceUntilIdle()

                val state = viewModel.uiState.value
                assertEquals("Network error", state.saveError)
                assertFalse(state.isSaving)
                assertFalse(fakeSettingsRepository.onboardingCompleted)

                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    // -------- OB-16 --------

    @Test
    @DisplayName("OB-16 — Age-gate checkbox semantics constant equals expected string")
    fun `OB-16 AGE_GATE_CONTENT_DESCRIPTION constant equals expected string`() {
        assertEquals(
            "I confirm I am 18 or older",
            OnboardingViewModel.AGE_GATE_CONTENT_DESCRIPTION,
        )
    }

    // -------- OB-17 --------

    @Test
    @DisplayName("OB-17 — Completing without ageConfirmed=true does NOT set onboardingCompleted")
    fun `OB-17 onCompleteOnboarding without ageConfirmed does not set onboardingCompleted`() =
        runTest {
            // Do NOT call onAgeConfirmedChange(true) — age is never confirmed
            // ageConfirmed stays false throughout; call onCompleteOnboarding() directly
            // (bypassing step validation per spec requirement)

            viewModel.events.test {
                viewModel.onCompleteOnboarding()
                advanceUntilIdle()

                assertFalse(fakeSettingsRepository.onboardingCompleted)
                expectNoEvents()

                val state = viewModel.uiState.value
                assertEquals("Age confirmation is required", state.saveError)

                cancelAndIgnoreRemainingEvents()
            }
        }

    // -------- OB-10b / OB-11b — NavigateToChat carries model choice --------

    @Test
    @DisplayName("OB-10b — API path emits NavigateToChat with ModelChoice.API")
    fun `OB-10b onCompleteOnboarding API path emits NavigateToChat with API model choice`() =
        runTest {
            fakeBulkAssign.resultToReturn = Result.success(ApiCredentialsId("creds-123"))

            viewModel.onAgeConfirmedChange(true)
            viewModel.onTosAcceptedChange(true)
            viewModel.onNextStep()
            advanceUntilIdle()
            viewModel.onModelChoiceSelected(ModelChoice.API)
            viewModel.onProviderSelected(ApiProvider.OPENROUTER)
            viewModel.onApiKeyChanged("sk-valid-key")
            viewModel.onModelSelected("openrouter/gpt-4o")
            viewModel.onNextStep()
            advanceUntilIdle()
            viewModel.onSkipMedia()
            advanceUntilIdle()

            viewModel.events.test {
                viewModel.onCompleteOnboarding()
                advanceUntilIdle()

                val event = awaitItem() as OnboardingEvent.NavigateToChat
                assertEquals(ModelChoice.API, event.modelChoice)
                val expectedSlots = BulkAssignApiProviderUseCase.TEXT_SLOTS.toSet() + ModelType.VISION
                assertEquals(expectedSlots, event.apiSlots)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    @DisplayName("OB-11b — LOCAL path emits NavigateToChat with ModelChoice.LOCAL")
    fun `OB-11b onCompleteOnboarding LOCAL path emits NavigateToChat with LOCAL model choice`() =
        runTest {
            viewModel.onAgeConfirmedChange(true)
            viewModel.onTosAcceptedChange(true)
            viewModel.onNextStep()
            advanceUntilIdle()
            viewModel.onModelChoiceSelected(ModelChoice.LOCAL)
            viewModel.onNextStep()
            advanceUntilIdle()

            viewModel.events.test {
                viewModel.onCompleteOnboarding()
                advanceUntilIdle()

                val event = awaitItem() as OnboardingEvent.NavigateToChat
                assertEquals(ModelChoice.LOCAL, event.modelChoice)
                assertEquals(emptySet<ModelType>(), event.apiSlots)

                cancelAndIgnoreRemainingEvents()
            }
        }

    // -------- OB-DEFERRED-SAVE --------

    @Test
    @DisplayName("OB-DEFERRED-SAVE — TTS and Media are saved only when onboarding completes")
    fun `TTS and Media drafts are deferred and saved on complete`() =
        runTest {
            fakeBulkAssign.resultToReturn = Result.success(ApiCredentialsId("creds-123"))

            // Navigate to step 2
            viewModel.onAgeConfirmedChange(true)
            viewModel.onTosAcceptedChange(true)
            viewModel.onNextStep()
            advanceUntilIdle()
            viewModel.onModelChoiceSelected(ModelChoice.API)
            viewModel.onProviderSelected(ApiProvider.OPENROUTER)
            viewModel.onApiKeyChanged("sk-valid-key")
            viewModel.onModelSelected("openrouter/gpt-4o")
            viewModel.onNextStep()
            advanceUntilIdle()

            // Configure TTS and Media
            val ttsDraft =
                TtsProviderDraft(
                    id = TtsProviderId("tts-id"),
                    displayName = "My TTS",
                    provider = ApiProvider.OPENAI,
                    voiceName = "voice",
                    baseUrl = null,
                    credentialAlias = "openai",
                    apiKey = "tts-key",
                )
            viewModel.onSaveTtsProvider(ttsDraft)

            val mediaDraft =
                MediaProviderDraft(
                    id = MediaProviderId("media-id"),
                    displayName = "My Media",
                    provider = ApiProvider.OPENAI,
                    modelId = "dall-e-3",
                    capability = com.browntowndev.enclave.domain.model.config.MediaCapability.IMAGE,
                    baseUrl = null,
                    credentialAlias = "openai",
                    apiKey = "media-key",
                )
            viewModel.onSaveMediaProvider(mediaDraft)
            advanceUntilIdle()

            // Verify no immediate save
            coVerify(exactly = 0) { mockSaveTts(any()) }
            coVerify(exactly = 0) { mockSaveMedia(any(), any()) }

            // Complete onboarding (step 2 -> step 3 -> complete)
            viewModel.onNextStep()
            advanceUntilIdle()

            viewModel.events.test {
                viewModel.onCompleteOnboarding()
                advanceUntilIdle()

                // Verify deferred saves are performed
                coVerify(exactly = 1) { mockSaveTts(ttsDraft) }
                coVerify(exactly = 1) { mockSaveMedia(match { it.id == mediaDraft.id }, "media-key") }

                assertTrue(fakeSettingsRepository.onboardingCompleted)
                val event = awaitItem() as OnboardingEvent.NavigateToChat
                assertEquals(ModelChoice.API, event.modelChoice)
                val expectedSlots = BulkAssignApiProviderUseCase.TEXT_SLOTS.toSet() +
                    ModelType.VISION + ModelType.IMAGE_GENERATION + ModelType.IMAGE_EDIT
                assertEquals(expectedSlots, event.apiSlots)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // -------- OB-RESTORED --------

    @Test
    @DisplayName("OB-RESTORED-01 — Synced account uses RESTORED flow mode")
    fun `OB-RESTORED-01 init sets RESTORED flow mode when resolver returns RESTORED`() =
        runTest {
            viewModel = createViewModelWithFlowMode(OnboardingFlowMode.RESTORED)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(OnboardingFlowMode.RESTORED, state.flowMode)
            assertEquals(ModelChoice.RESTORED, state.selectedModelChoice)
            assertEquals(0, state.currentStep)
        }

    @Test
    @DisplayName("OB-RESTORED-02 — RESTORED flow completes from welcome and skips bulk-assign")
    fun `OB-RESTORED-02 onNextStep from welcome completes and emits RESTORED`() =
        runTest {
            viewModel = createViewModelWithFlowMode(OnboardingFlowMode.RESTORED)
            advanceUntilIdle()

            viewModel.onAgeConfirmedChange(true)
            viewModel.onTosAcceptedChange(true)

            viewModel.events.test {
                viewModel.onNextStep()
                advanceUntilIdle()

                assertTrue(fakeSettingsRepository.onboardingCompleted)
                assertTrue(fakeSettingsRepository.ageConfirmed)
                val event = awaitItem() as OnboardingEvent.NavigateToChat
                assertEquals(ModelChoice.RESTORED, event.modelChoice)
                assertEquals(emptySet<ModelType>(), event.apiSlots)
                assertEquals(0, fakeBulkAssign.callCount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // -------- OB-TOS --------

    @Test
    @DisplayName("OB-TOS-01 — Step 0 continue disabled when TOS not accepted")
    fun `OB-TOS-01 step 0 with age confirmed but TOS not accepted does not advance`() =
        runTest {
            viewModel.onAgeConfirmedChange(true)
            viewModel.onTosAcceptedChange(false)
            viewModel.onNextStep()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(0, state.currentStep)
        }

    @Test
    @DisplayName("OB-TOS-02 — Step 0 continue disabled when age not confirmed but TOS accepted")
    fun `OB-TOS-02 step 0 with age not confirmed but TOS accepted does not advance`() =
        runTest {
            viewModel.onAgeConfirmedChange(false)
            viewModel.onTosAcceptedChange(true)
            viewModel.onNextStep()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(0, state.currentStep)
        }

    @Test
    @DisplayName("OB-TOS-03 — UI state tracks TOS reviewed and accepted changes")
    fun `OB-TOS-03 uiState reflects tosReviewed and tosAccepted updates`() =
        runTest {
            assertFalse(viewModel.uiState.value.tosReviewed)
            assertFalse(viewModel.uiState.value.tosAccepted)

            viewModel.onTosReviewedChange(true)
            assertTrue(viewModel.uiState.value.tosReviewed)

            viewModel.onTosAcceptedChange(true)
            assertTrue(viewModel.uiState.value.tosAccepted)
        }

    @Test
    @DisplayName("OB-TOS-04 — Completing without tosAccepted=true does NOT set onboardingCompleted")
    fun `OB-TOS-04 onCompleteOnboarding without tosAccepted does not set onboardingCompleted`() =
        runTest {
            viewModel.onAgeConfirmedChange(true)
            viewModel.onTosAcceptedChange(false) // TOS not accepted

            viewModel.events.test {
                viewModel.onCompleteOnboarding()
                advanceUntilIdle()

                assertFalse(fakeSettingsRepository.onboardingCompleted)
                expectNoEvents()

                val state = viewModel.uiState.value
                assertEquals("Terms of Service agreement is required", state.saveError)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    @DisplayName("OB-BASEURL-01 — Base URL updates and resets correctly")
    fun `OB-BASEURL-01 base url updates in state and resets on provider selected`() =
        runTest {
            viewModel.onBaseUrlChanged("https://custom-api.com")
            assertEquals("https://custom-api.com", viewModel.uiState.value.baseUrl)

            viewModel.onProviderSelected(ApiProvider.OPENAI)
            assertEquals("", viewModel.uiState.value.baseUrl)
        }

    @Test
    @DisplayName("OB-BASEURL-02 — Model discovery uses custom base URL if configured")
    fun `OB-BASEURL-02 model discovery passes custom base URL to discovery use case`() =
        runTest {
            // Navigate to step 1
            viewModel.onAgeConfirmedChange(true)
            viewModel.onTosAcceptedChange(true)
            viewModel.onNextStep()
            advanceUntilIdle()

            viewModel.onModelChoiceSelected(ModelChoice.API)
            viewModel.onProviderSelected(ApiProvider.OPENAI)
            viewModel.onApiKeyChanged("sk-test-key")
            viewModel.onBaseUrlChanged("https://my-custom-endpoint.com")
            viewModel.onDiscoverModels()
            advanceUntilIdle()

            coVerify {
                mockDiscoverModels(
                    match {
                        it.provider == ApiProvider.OPENAI &&
                            it.currentApiKey == "sk-test-key" &&
                            it.baseUrl == "https://my-custom-endpoint.com"
                    },
                )
            }
        }

    @Test
    @DisplayName("OB-BASEURL-03 — Onboarding completion passes custom base URL to credentials")
    fun `OB-BASEURL-03 onboarding completion passes custom base URL to credentials`() =
        runTest {
            fakeBulkAssign.resultToReturn = Result.success(ApiCredentialsId("creds-123"))

            // Navigate to step 3 via API path
            viewModel.onAgeConfirmedChange(true)
            viewModel.onTosAcceptedChange(true)
            viewModel.onNextStep()
            advanceUntilIdle()
            viewModel.onModelChoiceSelected(ModelChoice.API)
            viewModel.onProviderSelected(ApiProvider.OPENAI)
            viewModel.onApiKeyChanged("sk-valid-key")
            viewModel.onBaseUrlChanged("https://my-custom-endpoint.com")
            viewModel.onModelSelected("gpt-4o")
            viewModel.onNextStep()
            advanceUntilIdle()
            viewModel.onSkipMedia()
            advanceUntilIdle()

            viewModel.events.test {
                viewModel.onCompleteOnboarding()
                advanceUntilIdle()

                assertEquals(1, fakeBulkAssign.callCount)
                val credentialsPassed = fakeBulkAssign.lastCredentials
                assertEquals("https://my-custom-endpoint.com", credentialsPassed?.baseUrl)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
