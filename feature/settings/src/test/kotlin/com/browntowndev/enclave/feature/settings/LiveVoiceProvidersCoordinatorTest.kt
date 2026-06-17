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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
    private val apiProviderUseCases = mockk<SettingsApiProviderUseCases>(relaxed = true)
    private val liveVoiceUseCases = mockk<SettingsLiveVoiceUseCases>(relaxed = true)
    private val errorHandler = mockk<ViewModelErrorHandler>()

    private lateinit var coordinator: LiveVoiceProvidersCoordinator

    @BeforeEach
    fun setUp() {
        every {
            errorHandler.coroutineExceptionHandler(any(), any(), any())
        } returns kotlinx.coroutines.CoroutineExceptionHandler { _, _ -> }

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
            val getDefaultModelsUseCase = mockk<GetDefaultModelsUseCase>()
            val clearDefaultModelUseCase = mockk<ClearDefaultModelUseCase>()
            val deleteLiveVoiceProviderUseCase = mockk<DeleteLiveVoiceProviderUseCase>()
            val assignmentsUseCases = mockk<SettingsAssignmentUseCases>()

            every { assignmentsUseCases.getDefaultModels } returns getDefaultModelsUseCase
            every { assignmentsUseCases.clearDefaultModel } returns clearDefaultModelUseCase
            every { getDefaultModelsUseCase() } returns
                flowOf(
                    listOf(
                        DefaultModelAssignment(
                            modelType = ModelType.LIVE_VOICE,
                            liveVoiceProviderId = providerId,
                        ),
                    ),
                )
            coEvery { clearDefaultModelUseCase(ModelType.LIVE_VOICE) } coAnswers {
                operationOrder.add("clearDefault")
            }
            every { liveVoiceUseCases.deleteLiveVoiceProvider } returns deleteLiveVoiceProviderUseCase
            coEvery { deleteLiveVoiceProviderUseCase(providerId) } coAnswers {
                operationOrder.add("deleteProvider")
            }

            coordinator.onDeleteLiveVoiceProviderAsset(
                scope = this,
                tag = "tag",
                id = providerId,
                assignmentsUseCases = assignmentsUseCases,
            )
            advanceUntilIdle()

            assertEquals(listOf("clearDefault", "deleteProvider"), operationOrder)
            coVerify(exactly = 1) { clearDefaultModelUseCase(ModelType.LIVE_VOICE) }
            coVerify(exactly = 1) { deleteLiveVoiceProviderUseCase(providerId) }
        }

    @Test
    @DisplayName("LV-SETTINGS-COORD-004: deleting non-default provider skips clearDefaultModel")
    fun lvSettingsCoord004_deleteNonDefaultProviderSkipsClear() =
        runTest {
            val providerId = LiveVoiceProviderId("live-voice-1")
            val otherDefaultId = LiveVoiceProviderId("live-voice-2")
            val getDefaultModelsUseCase = mockk<GetDefaultModelsUseCase>()
            val clearDefaultModelUseCase = mockk<ClearDefaultModelUseCase>(relaxed = true)
            val deleteLiveVoiceProviderUseCase = mockk<DeleteLiveVoiceProviderUseCase>(relaxed = true)
            val assignmentsUseCases = mockk<SettingsAssignmentUseCases>()

            every { assignmentsUseCases.getDefaultModels } returns getDefaultModelsUseCase
            every { assignmentsUseCases.clearDefaultModel } returns clearDefaultModelUseCase
            every { getDefaultModelsUseCase() } returns
                flowOf(
                    listOf(
                        DefaultModelAssignment(
                            modelType = ModelType.LIVE_VOICE,
                            liveVoiceProviderId = otherDefaultId,
                        ),
                    ),
                )
            every { liveVoiceUseCases.deleteLiveVoiceProvider } returns deleteLiveVoiceProviderUseCase

            coordinator.onDeleteLiveVoiceProviderAsset(
                scope = this,
                tag = "tag",
                id = providerId,
                assignmentsUseCases = assignmentsUseCases,
            )
            advanceUntilIdle()

            coVerify(exactly = 0) { clearDefaultModelUseCase(any()) }
            coVerify(exactly = 1) { deleteLiveVoiceProviderUseCase(providerId) }
        }
}