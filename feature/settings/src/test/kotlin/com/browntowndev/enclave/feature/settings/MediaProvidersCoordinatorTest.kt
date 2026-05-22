package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.usecase.settings.SettingsApiProviderUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsMediaUseCases
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
class MediaProvidersCoordinatorTest {
    private val apiProviderUseCases = mockk<SettingsApiProviderUseCases>(relaxed = true)
    private val mediaUseCases = mockk<SettingsMediaUseCases>(relaxed = true)
    private val errorHandler = mockk<ViewModelErrorHandler>()

    private lateinit var coordinator: MediaProvidersCoordinator

    @BeforeEach
    fun setup() {
        every {
            errorHandler.coroutineExceptionHandler(any(), any(), any())
        } returns CoroutineExceptionHandler { _, _ -> }

        coordinator =
            MediaProvidersCoordinator(
                apiProviderUseCases = apiProviderUseCases,
                mediaUseCases = mediaUseCases,
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
}
