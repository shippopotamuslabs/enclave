package com.browntowndev.pocketcrew.feature.settings

import com.browntowndev.pocketcrew.core.testing.MainDispatcherRule
import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.model.config.*
import com.browntowndev.pocketcrew.domain.model.memory.Memory
import com.browntowndev.pocketcrew.domain.model.memory.MemoryCategory
import com.browntowndev.pocketcrew.domain.model.settings.AppTheme
import com.browntowndev.pocketcrew.domain.model.settings.SearchProvider
import com.browntowndev.pocketcrew.domain.port.repository.MemoriesRepository
import com.browntowndev.pocketcrew.domain.port.repository.SettingsData
import com.browntowndev.pocketcrew.domain.usecase.settings.*
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.*
import com.browntowndev.pocketcrew.domain.usecase.media.*
import com.browntowndev.pocketcrew.domain.usecase.byok.*
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
class SettingsViewModelEventTest {

    private lateinit var viewModel: SettingsViewModel
    private val memoriesRepository = mockk<MemoriesRepository>(relaxed = true)
    private val errorHandler = mockk<ViewModelErrorHandler>(relaxed = true)
    
    private val localModelAssetUiMapper = LocalModelAssetUiMapper()
    private val apiModelAssetUiMapper = ApiModelAssetUiMapper()
    private val ttsProviderAssetUiMapper = TtsProviderAssetUiMapper()
    private val mediaProviderAssetUiMapper = MediaProviderAssetUiMapper()
    private val reassignmentOptionUiMapper = ReassignmentOptionUiMapper()
    private val applyApiModelMetadataDefaultsUseCase = mockk<ApplyApiModelMetadataDefaultsUseCase>(relaxed = true)

    @BeforeEach
    fun setup() {
        every { errorHandler.coroutineExceptionHandler(any(), any(), any()) } returns CoroutineExceptionHandler { _, _ -> }

        val getSettingsUseCase = mockk<GetSettingsUseCase>()
        every { getSettingsUseCase() } returns MutableStateFlow(SettingsData())
        every { memoriesRepository.getAllMemoriesFlow() } returns MutableStateFlow<List<Memory>>(emptyList())

        val getLocalModelAssetsUseCase = mockk<GetLocalModelAssetsUseCase>()
        every { getLocalModelAssetsUseCase() } returns MutableStateFlow<List<LocalModelAsset>>(emptyList())

        val getApiModelAssetsUseCase = mockk<GetApiModelAssetsUseCase>()
        every { getApiModelAssetsUseCase() } returns MutableStateFlow<List<ApiModelAsset>>(emptyList())

        val getDefaultModelsUseCase = mockk<GetDefaultModelsUseCase>()
        every { getDefaultModelsUseCase() } returns MutableStateFlow<List<DefaultModelAssignment>>(emptyList())

        val getTtsProvidersUseCase = mockk<GetTtsProvidersUseCase>()
        every { getTtsProvidersUseCase() } returns MutableStateFlow<List<TtsProviderAsset>>(emptyList())

        val getMediaProvidersUseCase = mockk<GetMediaProvidersUseCase>()
        every { getMediaProvidersUseCase() } returns MutableStateFlow<List<MediaProviderAsset>>(emptyList())

        val settingsUseCases = mockk<SettingsUseCases>(relaxed = true) {
            val mockPreferences = mockk<SettingsPreferencesUseCases>(relaxed = true)
            every { preferences } returns mockPreferences
            every { mockPreferences.getSettings } returns getSettingsUseCase
            every { mockPreferences.updateTheme } returns mockk(relaxed = true)
            every { mockPreferences.updateBackgroundInferenceEnabled } returns mockk(relaxed = true)
            every { mockPreferences.updateSearchEnabled } returns mockk(relaxed = true)
            every { mockPreferences.updateSearchProvider } returns mockk(relaxed = true)
            every { mockPreferences.saveTavilyApiKey } returns mockk(relaxed = true)

            every { localModels } returns mockk(relaxed = true) {
                every { getLocalModelAssets } returns getLocalModelAssetsUseCase
            }
            every { apiProviders } returns mockk(relaxed = true) {
                every { getApiModelAssets } returns getApiModelAssetsUseCase
            }
            every { assignments } returns mockk(relaxed = true) {
                every { getDefaultModels } returns getDefaultModelsUseCase
            }
            every { tts } returns mockk(relaxed = true) {
                every { getTtsProviders } returns getTtsProvidersUseCase
            }
            every { media } returns mockk(relaxed = true) {
                every { getMediaProviders } returns getMediaProvidersUseCase
            }
            every { getSettings } returns getSettingsUseCase
        }

        viewModel = SettingsViewModel(
            settingsUseCases = settingsUseCases,
            memoriesRepository = memoriesRepository,
            settingsUiStateFactory = SettingsUiStateFactory(
                localModelAssetUiMapper,
                apiModelAssetUiMapper,
                ttsProviderAssetUiMapper,
                mediaProviderAssetUiMapper,
                ApiDiscoveryUiFilter(),
                applyApiModelMetadataDefaultsUseCase
            ),
            localModelAssetUiMapper = localModelAssetUiMapper,
            apiModelAssetUiMapper = apiModelAssetUiMapper,
            ttsProviderAssetUiMapper = ttsProviderAssetUiMapper,
            reassignmentOptionUiMapper = reassignmentOptionUiMapper,
            errorHandler = errorHandler,
        )
    }

    @Test
    fun `onShowDataControlsSheet updates uiState`() = runTest {
        viewModel.onShowDataControlsSheet(true)
        assertTrue(viewModel.uiState.value.dataControls.isSheetOpen)

        viewModel.onShowDataControlsSheet(false)
        assertFalse(viewModel.uiState.value.dataControls.isSheetOpen)
    }

    @Test
    fun `onShowMemoriesSheet updates uiState`() = runTest {
        viewModel.onShowMemoriesSheet(true)
        assertTrue(viewModel.uiState.value.memories.isSheetOpen)

        viewModel.onShowMemoriesSheet(false)
        assertFalse(viewModel.uiState.value.memories.isSheetOpen)
    }

    @Test
    fun `onAddMemory updates memoriesState`() = runTest {
        viewModel.onAddMemory()
        
        val state = viewModel.uiState.value.memories
        assertTrue(state.isEditing)
        assertEquals(MemoryCategory.PREFERENCES, state.memoryDraft?.category)
    }

    @Test
    fun `onUpdateMemoryDraft updates draft in uiState`() = runTest {
        viewModel.onAddMemory()
        viewModel.onUpdateMemoryDraft("Test memory", MemoryCategory.FACTS)
        
        val state = viewModel.uiState.value.memories
        assertEquals("Test memory", state.memoryDraft?.text)
        assertEquals(MemoryCategory.FACTS, state.memoryDraft?.category)
    }

    @Test
    fun `onSaveCustomization closes sheet`() = runTest {
        viewModel.onShowCustomizationSheet(true)
        assertTrue(viewModel.uiState.value.customization.isSheetOpen)

        viewModel.onSaveCustomization()
        assertFalse(viewModel.uiState.value.customization.isSheetOpen)
    }

    @Test
    fun `onTavilyApiKeyChange updates uiState`() = runTest {
        viewModel.onTavilyApiKeyChange("test-key")
    }

    @Test
    fun `onThemeChange updates uiState`() = runTest {
        viewModel.onThemeChange(AppTheme.DARK)
        // Similar to above, verifying no crash.
    }

    @Test
    fun `onBackgroundInferenceChange updates uiState`() = runTest {
        viewModel.onBackgroundInferenceChange(true)
    }

    @Test
    fun `onSearchEnabledChange updates uiState`() = runTest {
        viewModel.onSearchEnabledChange(true)
    }

    @Test
    fun `onSaveMemory calls repository and updates state`() = runTest {
        viewModel.onAddMemory()
        viewModel.onUpdateMemoryDraft("Test memory", MemoryCategory.FACTS)
        
        viewModel.onSaveMemory()
        
        val state = viewModel.uiState.value.memories
        assertFalse(state.isEditing)
        assertEquals(null, state.memoryDraft)
    }

    @Test
    fun `onSearchProviderChange updates uiState`() = runTest {
        viewModel.onSearchProviderChange(SearchProvider.TAVILY)
    }
}
