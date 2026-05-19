package com.browntowndev.pocketcrew.feature.settings

import com.browntowndev.pocketcrew.core.testing.MainDispatcherRule
import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.usecase.settings.ApplyApiModelMetadataDefaultsUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsApiProviderUseCases
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class ByokProvidersCoordinatorTest {
    private val apiProviderUseCases = mockk<SettingsApiProviderUseCases>(relaxed = true)
    private val deletionHelper = mockk<SettingsDeletionHelper>(relaxed = true)
    private val errorHandler = mockk<ViewModelErrorHandler>()

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
                    modelId = "gpt-4",
                    baseUrl = null,
                    isMultimodal = false,
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
    fun `onStartCreateApiModelAsset seeds empty draft`() =
        runTest {
            val apiState = MutableStateFlow(ApiProvidersTransientState(isSheetOpen = true))

            coordinator.onStartCreateApiModelAsset(apiState)

            assertNull(apiState.value.selectedAsset)
            assertEquals(ApiProvider.OPENAI, apiState.value.assetDraft?.provider)
            assertFalse(apiState.value.isDiscoveringApiModels)
        }
}
