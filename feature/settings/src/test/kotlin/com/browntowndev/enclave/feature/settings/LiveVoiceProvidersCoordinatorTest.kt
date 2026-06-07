package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.usecase.settings.SettingsApiProviderUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsLiveVoiceUseCases
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
}