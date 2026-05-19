package com.browntowndev.pocketcrew.feature.settings

import com.browntowndev.pocketcrew.core.testing.MainDispatcherRule
import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsApiProviderUseCases
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsTtsUseCases
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
            val apiState = MutableStateFlow(ApiProvidersTransientState())
            val currentApiKey = MutableStateFlow("secret")
            val asset = TtsProviderAssetUi(provider = ApiProvider.GOOGLE)

            coordinator.onSelectTtsProviderAsset(this, "tag", ttsState, apiState, currentApiKey, asset)

            assertEquals(asset, ttsState.value.selectedAsset)
            assertEquals("", currentApiKey.value)
        }
}
