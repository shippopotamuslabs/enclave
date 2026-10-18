package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.usecase.byok.ClearDefaultModelUseCase
import com.browntowndev.enclave.domain.usecase.byok.GetDefaultModelsUseCase
import com.browntowndev.enclave.domain.usecase.settings.DeleteLiveVoiceProviderUseCase
import com.browntowndev.enclave.domain.usecase.settings.SettingsApiProviderUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsAssignmentUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsLiveVoiceUseCases
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class LiveVoiceProvidersCoordinatorTest {
    private val apiProviderUseCases = FakeSettingsApiProviderUseCases()
    private val liveVoiceUseCases = FakeSettingsLiveVoiceUseCases()
    private val errorHandler = FakeViewModelErrorHandler()

    private lateinit var coordinator: LiveVoiceProvidersCoordinator

    @BeforeEach
    fun setUp() {
        coordinator =
            LiveVoiceProvidersCoordinator(
                apiProviderUseCases = apiProviderUseCases,
                liveVoiceUseCases = liveVoiceUseCases,
                errorHandler = errorHandler,
            )
    }

    @Test
    @DisplayName("LV-SETTINGS-COORD-001: onShowLiveVoiceProvidersSheet sets isSheetOpen")
    fun lvSettingsCoord001_showSheet() =
        runTest {
            val liveVoiceState = MutableStateFlow(LiveVoiceProvidersTransientState())

            coordinator.onShowLiveVoiceProvidersSheet(liveVoiceState, show = true)

            assertTrue(liveVoiceState.value.isSheetOpen)
        }

    @Test
    @DisplayName("LV-SETTINGS-COORD-002: onStartCreateLiveVoiceProviderAsset seeds GOOGLE draft")
    fun lvSettingsCoord002_startCreateSeedsGoogleDraft() =
        runTest {
            val liveVoiceState = MutableStateFlow(LiveVoiceProvidersTransientState())

            coordinator.onStartCreateLiveVoiceProviderAsset(liveVoiceState)

            assertNull(liveVoiceState.value.selectedAsset)
            assertEquals(ApiProvider.GOOGLE, liveVoiceState.value.assetDraft?.provider)
        }

    @Test
    @DisplayName(
        "LV-SETTINGS-COORD-003: deleting assigned LIVE_VOICE default clears slot before provider delete",
    )
    fun lvSettingsCoord003_deleteAssignedDefaultClearsSlotBeforeProvider() =
        runTest {
            val providerId = LiveVoiceProviderId("live-voice-1")
            val operationOrder = mutableListOf<String>()
            val clearDefaultModelUseCase = FakeClearDefaultModelUseCase()
            val deleteLiveVoiceProviderUseCase = liveVoiceUseCases.deleteLiveVoiceProvider
            val assignmentsUseCases =
                FakeSettingsAssignmentUseCases(
                    getDefaultModels =
                        FakeGetDefaultModelsUseCase().apply {
                            flow =
                                flowOf(
                                    listOf(
                                        DefaultModelAssignment(
                                            modelType = ModelType.LIVE_VOICE,
                                            liveVoiceProviderId = providerId,
                                        ),
                                    ),
                                )
                        },
                    clearDefaultModel = clearDefaultModelUseCase,
                )
            clearDefaultModelUseCase.onInvoke = { operationOrder.add("clearDefault") }
            deleteLiveVoiceProviderUseCase.onInvoke = { operationOrder.add("deleteProvider") }

            coordinator.onDeleteLiveVoiceProviderAsset(
                scope = this,
                tag = "tag",
                id = providerId,
                assignmentsUseCases = assignmentsUseCases,
            )
            advanceUntilIdle()

            assertEquals(listOf("clearDefault", "deleteProvider"), operationOrder)
            assertEquals(listOf(ModelType.LIVE_VOICE), clearDefaultModelUseCase.calls)
            assertEquals(listOf(providerId), deleteLiveVoiceProviderUseCase.calls)
        }

    @Test
    @DisplayName("LV-SETTINGS-COORD-004: deleting non-default provider skips clearDefaultModel")
    fun lvSettingsCoord004_deleteNonDefaultProviderSkipsClear() =
        runTest {
            val providerId = LiveVoiceProviderId("live-voice-1")
            val otherDefaultId = LiveVoiceProviderId("live-voice-2")
            val clearDefaultModelUseCase = FakeClearDefaultModelUseCase()
            val deleteLiveVoiceProviderUseCase = liveVoiceUseCases.deleteLiveVoiceProvider
            val assignmentsUseCases =
                FakeSettingsAssignmentUseCases(
                    getDefaultModels =
                        FakeGetDefaultModelsUseCase().apply {
                            flow =
                                flowOf(
                                    listOf(
                                        DefaultModelAssignment(
                                            modelType = ModelType.LIVE_VOICE,
                                            liveVoiceProviderId = otherDefaultId,
                                        ),
                                    ),
                                )
                        },
                    clearDefaultModel = clearDefaultModelUseCase,
                )

            coordinator.onDeleteLiveVoiceProviderAsset(
                scope = this,
                tag = "tag",
                id = providerId,
                assignmentsUseCases = assignmentsUseCases,
            )
            advanceUntilIdle()

            assertTrue(clearDefaultModelUseCase.calls.isEmpty())
            assertEquals(listOf(providerId), deleteLiveVoiceProviderUseCase.calls)
        }
}