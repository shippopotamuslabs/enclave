package com.browntowndev.pocketcrew.feature.settings

import androidx.lifecycle.SavedStateHandle
import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.model.config.ApiModelAsset
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentials
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.usecase.byok.*
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.*
import com.browntowndev.pocketcrew.domain.usecase.settings.GetSettingsUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateAllowMemoriesUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateCustomPromptTextUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateCustomizationEnabledUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateHapticPressUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateHapticResponseUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateSelectedPromptOptionUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateThemeUseCase
import com.browntowndev.pocketcrew.domain.model.settings.AppTheme
import com.browntowndev.pocketcrew.domain.port.repository.SettingsData
import com.browntowndev.pocketcrew.domain.model.settings.SystemPromptOption
import com.browntowndev.pocketcrew.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    
    // Repositories
    private val apiModelRepository = mockk<ApiModelRepositoryPort>(relaxed = true)
    private val modelRegistry = mockk<ModelRegistryPort>(relaxed = true)
    private val defaultModelRepository = mockk<DefaultModelRepositoryPort>(relaxed = true)
    private val transactionProvider = mockk<TransactionProvider>(relaxed = true)
    private val getSettingsUseCase = mockk<GetSettingsUseCase>(relaxed = true)
    
    // Other mocks
    private val updateThemeUseCase = mockk<UpdateThemeUseCase>(relaxed = true)
    private val updateHapticPressUseCase = mockk<UpdateHapticPressUseCase>(relaxed = true)
    private val updateHapticResponseUseCase = mockk<UpdateHapticResponseUseCase>(relaxed = true)
    private val updateCustomizationEnabledUseCase = mockk<UpdateCustomizationEnabledUseCase>(relaxed = true)
    private val updateSelectedPromptOptionUseCase = mockk<UpdateSelectedPromptOptionUseCase>(relaxed = true)
    private val updateCustomPromptTextUseCase = mockk<UpdateCustomPromptTextUseCase>(relaxed = true)
    private val updateAllowMemoriesUseCase = mockk<UpdateAllowMemoriesUseCase>(relaxed = true)
    private val errorHandler = mockk<ViewModelErrorHandler>(relaxed = true)

    private lateinit var viewModel: SettingsViewModel

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        coEvery { transactionProvider.runInTransaction<Any>(any()) } coAnswers {
            (args[0] as suspend () -> Any).invoke()
        }

        every { getSettingsUseCase() } returns flowOf(SettingsData(
            theme = AppTheme.SYSTEM,
            hapticPress = true,
            hapticResponse = true,
            customizationEnabled = true,
            selectedPromptOption = SystemPromptOption.CONCISE,
            customPromptText = "",
            allowMemories = true
        ))
        every { modelRegistry.observeAssets() } returns flowOf(emptyList())
        every { apiModelRepository.observeAllCredentials() } returns flowOf(emptyList())
        every { defaultModelRepository.observeDefaults() } returns flowOf(emptyList())
        
        // Mock errorHandler to return a real handler to avoid ClassCastException in coroutines internal code
        every { errorHandler.coroutineExceptionHandler(any(), any(), any()) } returns CoroutineExceptionHandler { _, _ -> }

        viewModel = createViewModel()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uiState initially reflects empty assets`() = runTest {
        assertEquals(0, viewModel.uiState.value.apiModels.size)
        assertEquals(0, viewModel.uiState.value.localModels.size)
    }

    @Test
    fun `onSaveApiCredentials calls repository`() = runTest {
        val assetUi = ApiModelAssetUi(
            credentialsId = 0, displayName = "OpenAI", provider = ApiProvider.OPENAI, modelId = "gpt-4", baseUrl = null, isVision = false, credentialAlias = "alias", configurations = emptyList()
        )
        viewModel.onSelectApiModelAsset(assetUi)
        viewModel.onApiKeyChange("sk-test")
        
        coEvery { apiModelRepository.saveCredentials(any(), any()) } returns 1L
        
        var savedId: Long = -1L
        viewModel.onSaveApiCredentials { id -> savedId = id }
        testDispatcher.scheduler.advanceUntilIdle()
        
        coVerify { apiModelRepository.saveCredentials(any(), "sk-test") }
        assertEquals(1L, savedId)
        assertEquals("", viewModel.currentApiKey.value)
    }

    @Test
    fun `onDeleteApiCredentials calls repository`() = runTest {
        coEvery { apiModelRepository.deleteCredentials(any()) } returns Unit
        
        var successCalled = false
        viewModel.onDeleteApiCredentials(1L) { successCalled = true }
        testDispatcher.scheduler.advanceUntilIdle()
        
        coVerify { apiModelRepository.deleteCredentials(1L) }
        assertTrue(successCalled)
    }

    @Test
    fun `onSetDefaultModel calls repository and hides dialog`() = runTest {
        coEvery { defaultModelRepository.setDefault(any(), any(), any()) } returns Unit
        
        viewModel.onShowAssignmentDialog(true, ModelType.MAIN)
        viewModel.onSetDefaultModel(ModelType.MAIN, localConfigId = 5L, apiConfigId = null)
        testDispatcher.scheduler.advanceUntilIdle()
        
        coVerify { defaultModelRepository.setDefault(ModelType.MAIN, 5L, null) }
        assertEquals(false, viewModel.uiState.value.showAssignmentDialog)
        assertEquals(null, viewModel.uiState.value.editingAssignmentSlot)
    }

    @Test
    fun `onSaveApiModelConfig calls repository`() = runTest {
        val assetUi = ApiModelAssetUi(
            credentialsId = 10, displayName = "T", provider = ApiProvider.OPENAI, modelId = "m", baseUrl = null, isVision = false, credentialAlias = "a", configurations = emptyList()
        )
        val configUi = ApiModelConfigUi(id = 0, credentialsId = 10, displayName = "New")
        
        viewModel.onSelectApiModelAsset(assetUi)
        viewModel.onSelectApiModelConfig(configUi)
        
        coEvery { apiModelRepository.saveConfiguration(any()) } returns 1L
        
        var successCalled = false
        viewModel.onSaveApiModelConfig { successCalled = true }
        testDispatcher.scheduler.advanceUntilIdle()
        
        coVerify { apiModelRepository.saveConfiguration(match { it.apiCredentialsId == 10L && it.displayName == "New" }) }
        assertTrue(successCalled)
    }

    @Test
    fun `onDeleteApiModelConfig calls repository`() = runTest {
        coEvery { apiModelRepository.deleteConfiguration(any()) } returns Unit
        
        var successCalled = false
        viewModel.onDeleteApiModelConfig(5L) { successCalled = true }
        testDispatcher.scheduler.advanceUntilIdle()
        
        coVerify { apiModelRepository.deleteConfiguration(5L) }
        assertTrue(successCalled)
    }

    @Test
    fun `onSaveLocalModelConfig calls repository`() = runTest {
        val assetUi = LocalModelAssetUi(
            metadataId = 20, displayName = "Local", huggingFaceModelName = "hf", remoteFileName = "f", sizeInBytes = 1000, configurations = emptyList()
        )
        val configUi = LocalModelConfigUi(id = 0, localModelId = 20, displayName = "NewLocal")
        
        viewModel.onSelectLocalModelAsset(assetUi)
        viewModel.onSelectLocalModelConfig(configUi)
        
        coEvery { modelRegistry.saveConfiguration(any()) } returns 1L
        
        var successCalled = false
        viewModel.onSaveLocalModelConfig { successCalled = true }
        testDispatcher.scheduler.advanceUntilIdle()
        
        coVerify { modelRegistry.saveConfiguration(match { it.localModelId == 20L && it.displayName == "NewLocal" }) }
        assertTrue(successCalled)
    }

    @Test
    fun `onDeleteLocalModelConfig calls repository`() = runTest {
        coEvery { modelRegistry.deleteConfiguration(any()) } returns Unit
        
        var successCalled = false
        viewModel.onDeleteLocalModelConfig(7L) { successCalled = true }
        testDispatcher.scheduler.advanceUntilIdle()
        
        coVerify { modelRegistry.deleteConfiguration(7L) }
        assertTrue(successCalled)
    }

    @Test
    fun `onDeleteLocalModelAsset calls repository`() = runTest {
        coEvery { modelRegistry.deleteLocalModelMetadata(any()) } returns Unit
        
        viewModel.onDeleteLocalModelAsset(20L)
        testDispatcher.scheduler.advanceUntilIdle()
        
        coVerify { modelRegistry.deleteLocalModelMetadata(20L) }
    }

    private fun createViewModel() = SettingsViewModel(
        savedStateHandle = SavedStateHandle(),
        getSettingsUseCase = getSettingsUseCase,
        updateThemeUseCase = updateThemeUseCase,
        updateHapticPressUseCase = updateHapticPressUseCase,
        updateHapticResponseUseCase = updateHapticResponseUseCase,
        updateCustomizationEnabledUseCase = updateCustomizationEnabledUseCase,
        updateSelectedPromptOptionUseCase = updateSelectedPromptOptionUseCase,
        updateCustomPromptTextUseCase = updateCustomPromptTextUseCase,
        updateAllowMemoriesUseCase = updateAllowMemoriesUseCase,
        getLocalModelAssetsUseCase = GetLocalModelAssetsUseCaseImpl(modelRegistry),
        saveLocalModelConfigurationUseCase = SaveLocalModelConfigurationUseCaseImpl(modelRegistry),
        deleteLocalModelConfigurationUseCase = DeleteLocalModelConfigurationUseCaseImpl(modelRegistry),
        deleteLocalModelMetadataUseCase = DeleteLocalModelMetadataUseCaseImpl(modelRegistry),
        getApiModelAssetsUseCase = GetApiModelAssetsUseCaseImpl(apiModelRepository),
        saveApiCredentialsUseCase = SaveApiCredentialsUseCaseImpl(apiModelRepository),
        deleteApiCredentialsUseCase = DeleteApiCredentialsUseCaseImpl(apiModelRepository, defaultModelRepository, transactionProvider),
        saveApiModelConfigurationUseCase = SaveApiModelConfigurationUseCaseImpl(apiModelRepository),
        deleteApiModelConfigurationUseCase = DeleteApiModelConfigurationUseCaseImpl(apiModelRepository, defaultModelRepository, transactionProvider),
        getDefaultModelsUseCase = GetDefaultModelsUseCaseImpl(defaultModelRepository),
        setDefaultModelUseCase = SetDefaultModelUseCaseImpl(defaultModelRepository),
        errorHandler = errorHandler
    )
}