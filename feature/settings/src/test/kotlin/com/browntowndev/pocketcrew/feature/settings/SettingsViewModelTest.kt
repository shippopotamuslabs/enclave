package com.browntowndev.pocketcrew.feature.settings

import androidx.lifecycle.SavedStateHandle
import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentials
import com.browntowndev.pocketcrew.domain.model.config.ApiModelAsset
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfiguration
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
    private val deleteLocalModelUseCase = mockk<DeleteLocalModelUseCase>(relaxed = true)
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
        every { apiModelRepository.observeAllConfigurations() } returns flowOf(emptyList())
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
    fun `full lifecycle of adding provider and checking presets`() = runTest {
        val credentialsFlow = MutableStateFlow(emptyList<ApiCredentials>())
        val configurationsFlow = MutableStateFlow(emptyList<ApiModelConfiguration>())
        
        every { apiModelRepository.observeAllCredentials() } returns credentialsFlow
        every { apiModelRepository.observeAllConfigurations() } returns configurationsFlow
        
        // 1. Initial State
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.apiModels.size)
        
        // 2. Start adding provider
        val newAssetDraft = ApiModelAssetUi(
            credentialsId = 0, displayName = "P1", provider = ApiProvider.OPENAI, modelId = "m1", baseUrl = null, isVision = false, credentialAlias = "a1", configurations = emptyList()
        )
        viewModel.onSelectApiModelAsset(newAssetDraft)
        viewModel.onApiKeyChange("key")
        
        // Mock save operations with flow updates
        coEvery { apiModelRepository.saveCredentials(any(), any()) } coAnswers {
            val creds = args[0] as ApiCredentials
            val newId = 1L
            credentialsFlow.value = listOf(creds.copy(id = newId))
            newId
        }
        coEvery { apiModelRepository.saveConfiguration(any()) } coAnswers {
            val config = args[0] as ApiModelConfiguration
            val newId = 10L
            configurationsFlow.value = listOf(config.copy(id = newId))
            newId
        }
        
        // 3. Save
        viewModel.onSaveApiCredentials { assetUi, configUi ->
            // Simulate UI logic in ByokConfigureRoute
            viewModel.onSelectApiModelAsset(assetUi)
            viewModel.onSelectApiModelConfig(configUi ?: ApiModelConfigUi())
        }
        testDispatcher.scheduler.advanceUntilIdle()
        
        // 4. Check state after save (immediate)
        assertEquals(1L, viewModel.uiState.value.selectedApiModelAsset?.credentialsId)
        assertEquals(1, viewModel.uiState.value.selectedApiModelAsset?.configurations?.size)
        
        // 5. Navigate back to bottom sheet (simulated)
        viewModel.onShowByokSheet(true) // Opening sheet should clear selection
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.selectedApiModelAsset)
        
        // 6. Click provider in bottom sheet
        viewModel.onSelectApiModelAsset(1L)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Should have 1 preset (refreshed from combined flow)
        assertEquals(1, viewModel.uiState.value.selectedApiModelAsset?.configurations?.size)
        assertEquals("Default Preset", viewModel.uiState.value.selectedApiModelAsset?.configurations?.get(0)?.displayName)
    }

    @Test
    fun `uiState updates when configurations are added to repository`() = runTest {
        val creds = ApiCredentials(id = 1, displayName = "P1", provider = ApiProvider.OPENAI, modelId = "m1", baseUrl = null, isVision = false, credentialAlias = "a1")
        val config = ApiModelConfiguration(id = 1, apiCredentialsId = 1, displayName = "C1")
        
        val credentialsFlow = MutableStateFlow(listOf(creds))
        val configurationsFlow = MutableStateFlow(emptyList<ApiModelConfiguration>())
        
        every { apiModelRepository.observeAllCredentials() } returns credentialsFlow
        every { apiModelRepository.observeAllConfigurations() } returns configurationsFlow
        
        // Refresh VM
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertEquals(1, viewModel.uiState.value.apiModels.size)
        assertEquals(0, viewModel.uiState.value.apiModels[0].configurations.size)
        
        // Emit new configuration
        configurationsFlow.value = listOf(config)
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertEquals(1, viewModel.uiState.value.apiModels[0].configurations.size)
        assertEquals("C1", viewModel.uiState.value.apiModels[0].configurations[0].displayName)
    }

    @Test
    fun `uiState initially reflects empty assets`() = runTest {
        assertEquals(0, viewModel.uiState.value.apiModels.size)
        assertEquals(0, viewModel.uiState.value.localModels.size)
    }

    @Test
    fun `onSaveApiCredentials saves a default preset and returns it when new asset`() = runTest {
        val assetUi = ApiModelAssetUi(
            credentialsId = 0, displayName = "OpenAI", provider = ApiProvider.OPENAI, modelId = "gpt-4", baseUrl = null, isVision = false, credentialAlias = "alias", configurations = emptyList()
        )
        viewModel.onSelectApiModelAsset(assetUi)
        viewModel.onApiKeyChange("sk-test")
        
        coEvery { apiModelRepository.saveCredentials(any(), any()) } returns 123L
        coEvery { apiModelRepository.saveConfiguration(any()) } returns 789L
        
        var savedId: Long = -1L
        var savedConfigUi: ApiModelConfigUi? = null
        viewModel.onSaveApiCredentials { assetUi, configUi -> 
            savedId = assetUi.credentialsId 
            savedConfigUi = configUi
        }
        testDispatcher.scheduler.advanceUntilIdle()
        
        coVerify { apiModelRepository.saveCredentials(any(), "sk-test") }
        coVerify { apiModelRepository.saveConfiguration(match { it.apiCredentialsId == 123L && it.displayName == "Default Preset" }) }
        assertEquals(123L, savedId)
        assertEquals(789L, savedConfigUi?.id)
        assertEquals("Default Preset", savedConfigUi?.displayName)
        assertEquals("", viewModel.currentApiKey.value)
    }

    @Test
    fun `onSaveApiCredentials does not save default preset when editing existing asset`() = runTest {
        val assetUi = ApiModelAssetUi(
            credentialsId = 123L, displayName = "OpenAI", provider = ApiProvider.OPENAI, modelId = "gpt-4", baseUrl = null, isVision = false, credentialAlias = "alias", configurations = emptyList()
        )
        viewModel.onSelectApiModelAsset(assetUi)
        viewModel.onApiKeyChange("sk-test")
        
        coEvery { apiModelRepository.saveCredentials(any(), any()) } returns 123L
        
        var savedId: Long = -1L
        var savedConfigUi: ApiModelConfigUi? = null
        viewModel.onSaveApiCredentials { assetUiResult, configUi -> 
            savedId = assetUiResult.credentialsId
            savedConfigUi = configUi
        }
        testDispatcher.scheduler.advanceUntilIdle()
        
        coVerify { apiModelRepository.saveCredentials(any(), "sk-test") }
        coVerify(exactly = 0) { apiModelRepository.saveConfiguration(any()) }
        assertEquals(123L, savedId)
        assertEquals(null, savedConfigUi)
        assertEquals("", viewModel.currentApiKey.value)
    }

    @Test
    fun `onSelectApiModelAsset(Long) selects correct asset from uiState`() = runTest {
        val creds = ApiCredentials(id = 1, displayName = "P1", provider = ApiProvider.OPENAI, modelId = "m1", baseUrl = null, isVision = false, credentialAlias = "a1")
        
        every { apiModelRepository.observeAllCredentials() } returns flowOf(listOf(creds))
        
        // Refresh VM to pick up assets
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        
        viewModel.onSelectApiModelAsset(1L)
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertEquals(1L, viewModel.uiState.value.selectedApiModelAsset?.credentialsId)
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
    fun `onSaveApiModelConfig calls repository with all fields`() = runTest {
        val assetUi = ApiModelAssetUi(
            credentialsId = 10, displayName = "T", provider = ApiProvider.OPENAI, modelId = "m", baseUrl = null, isVision = false, credentialAlias = "a", configurations = emptyList()
        )
        val configUi = ApiModelConfigUi(
            id = 1, 
            credentialsId = 10, 
            displayName = "New",
            systemPrompt = "You are a helpful assistant",
            minP = 0.1
        )
        
        viewModel.onSelectApiModelAsset(assetUi)
        viewModel.onSelectApiModelConfig(configUi)
        
        coEvery { apiModelRepository.saveConfiguration(any()) } returns 1L
        
        viewModel.onSaveApiModelConfig { }
        testDispatcher.scheduler.advanceUntilIdle()
        
        coVerify { 
            apiModelRepository.saveConfiguration(match { 
                it.apiCredentialsId == 10L && 
                it.displayName == "New" &&
                it.systemPrompt == "You are a helpful assistant" &&
                it.minP == 0.1
            }) 
        }
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
            metadataId = 20, huggingFaceModelName = "hf", remoteFileName = "f", sizeInBytes = 1000, configurations = emptyList()
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
        coEvery { deleteLocalModelUseCase(any(), any(), any()) } returns Result.success(Unit)
        
        viewModel.onDeleteLocalModelAsset(20L)
        testDispatcher.scheduler.advanceUntilIdle()
        
        coVerify { deleteLocalModelUseCase(20L) }
    }

    @Test
    fun `onSelectLocalModelAsset(null) clears selection`() = runTest {
        val assetUi = LocalModelAssetUi(
            metadataId = 20,
            huggingFaceModelName = "hf",
            remoteFileName = "f",
            sizeInBytes = 1000,
            configurations = emptyList()
        )
        viewModel.onSelectLocalModelAsset(assetUi)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(assetUi, viewModel.uiState.value.selectedLocalModelAsset)
        
        viewModel.onSelectLocalModelAsset(null)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.selectedLocalModelAsset)
    }

    @Test
    fun `onShowByokSheet(true) clears selection`() = runTest {
        val assetUi = ApiModelAssetUi(
            credentialsId = 20, displayName = "T", provider = ApiProvider.OPENAI, modelId = "m", baseUrl = null, isVision = false, credentialAlias = "a", configurations = emptyList()
        )
        viewModel.onSelectApiModelAsset(assetUi)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(20L, viewModel.uiState.value.selectedApiModelAsset?.credentialsId)
        
        viewModel.onShowByokSheet(true)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.selectedApiModelAsset)
    }

    @Test
    fun `onShowByokSheet(false) preserves selection`() = runTest {
        val assetUi = ApiModelAssetUi(
            credentialsId = 20, displayName = "T", provider = ApiProvider.OPENAI, modelId = "m", baseUrl = null, isVision = false, credentialAlias = "a", configurations = emptyList()
        )
        viewModel.onSelectApiModelAsset(assetUi)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(20L, viewModel.uiState.value.selectedApiModelAsset?.credentialsId)
        
        viewModel.onShowByokSheet(false)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(20L, viewModel.uiState.value.selectedApiModelAsset?.credentialsId)
    }

    @Test
    fun `onShowModelConfigSheet(false) preserves local selection`() = runTest {
        val assetUi = LocalModelAssetUi(
            metadataId = 20, huggingFaceModelName = "hf", remoteFileName = "f", sizeInBytes = 1000, configurations = emptyList()
        )
        val configUi = LocalModelConfigUi(id = 100, localModelId = 20, displayName = "Config")
        
        viewModel.onSelectLocalModelAsset(assetUi)
        viewModel.onSelectLocalModelConfig(configUi)
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertEquals(20L, viewModel.uiState.value.selectedLocalModelAsset?.metadataId)
        assertEquals(100L, viewModel.uiState.value.selectedLocalModelConfig?.id)
        
        viewModel.onShowModelConfigSheet(false)
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertEquals(20L, viewModel.uiState.value.selectedLocalModelAsset?.metadataId)
        assertEquals(100L, viewModel.uiState.value.selectedLocalModelConfig?.id)
    }

    @Test
    fun `ApiModelConfiguration toUi and onSaveApiModelConfig handle topK default`() = runTest {
        val assetUi = ApiModelAssetUi(
            credentialsId = 10, displayName = "T", provider = ApiProvider.OPENAI, modelId = "m", baseUrl = null, isVision = false, credentialAlias = "a", configurations = emptyList()
        )
        viewModel.onSelectApiModelAsset(assetUi)
        
        // Test mapping from domain model with null topK
        val domainConfig = ApiModelConfiguration(
            id = 1, apiCredentialsId = 10, displayName = "Test", topK = null
        )
        // vm has private toUi, but it's used when assets emit.
        // We can check onApiModelConfigFieldChange.
        
        val configUi = ApiModelConfigUi(id = 1, credentialsId = 10, displayName = "Test", topK = "")
        viewModel.onSelectApiModelConfig(configUi)
        
        coEvery { apiModelRepository.saveConfiguration(any()) } returns 1L
        viewModel.onSaveApiModelConfig {}
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Should use default 40 if blank
        coVerify { apiModelRepository.saveConfiguration(match { it.topK == 40 }) }
    }

    @Test
    fun `onCleanupCustomHeaders filters out blank keys or values`() = runTest {
        val configUi = ApiModelConfigUi(
            id = 1,
            customHeaders = listOf(
                CustomHeaderUi("Valid", "Value"),
                CustomHeaderUi("", "NoKey"),
                CustomHeaderUi("NoValue", ""),
                CustomHeaderUi("  ", "BlankKey"),
                CustomHeaderUi("BlankValue", "  ")
            )
        )
        viewModel.onSelectApiModelConfig(configUi)
        
        viewModel.onCleanupCustomHeaders()
        testDispatcher.scheduler.advanceUntilIdle()
        
        val updatedHeaders = viewModel.uiState.value.selectedApiModelConfig?.customHeaders
        assertEquals(1, updatedHeaders?.size)
        assertEquals("Valid", updatedHeaders?.get(0)?.key)
        assertEquals("Value", updatedHeaders?.get(0)?.value)
    }

    @Test
    fun `onSaveApiModelConfig filters out blank keys or values before saving`() = runTest {
        val assetUi = ApiModelAssetUi(
            credentialsId = 10, displayName = "T", provider = ApiProvider.OPENAI, modelId = "m", baseUrl = null, isVision = false, credentialAlias = "a", configurations = emptyList()
        )
        val configUi = ApiModelConfigUi(
            id = 1,
            credentialsId = 10,
            displayName = "Test",
            customHeaders = listOf(
                CustomHeaderUi("Valid", "Value"),
                CustomHeaderUi("", "NoKey"),
                CustomHeaderUi("NoValue", "")
            )
        )
        
        viewModel.onSelectApiModelAsset(assetUi)
        viewModel.onSelectApiModelConfig(configUi)
        
        coEvery { apiModelRepository.saveConfiguration(any()) } returns 1L
        
        viewModel.onSaveApiModelConfig {}
        testDispatcher.scheduler.advanceUntilIdle()
        
        coVerify { 
            apiModelRepository.saveConfiguration(match { config ->
                config.customHeaders.size == 1 && 
                config.customHeaders["Valid"] == "Value"
            }) 
        }
    }

    @Test
    fun `onDeleteLocalModelAsset shows alert when it is the last model`() = runTest {
        coEvery { deleteLocalModelUseCase.isLastModel(20L) } returns true
        
        viewModel.onDeleteLocalModelAsset(20L)
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value.showCannotDeleteLastModelAlert)
        coVerify(exactly = 0) { deleteLocalModelUseCase(20L, any(), any()) }
    }

    @Test
    fun `onDeleteLocalModelAsset shows reassignment dialog when model is a default`() = runTest {
        val modelId = 20L
        val configId = 100L
        val asset = createFakeLocalModelAsset(id = modelId, configurations = listOf(createFakeLocalModelConfiguration(id = configId, localModelId = modelId)))
        
        coEvery { deleteLocalModelUseCase.isLastModel(modelId) } returns false
        coEvery { deleteLocalModelUseCase.getModelTypesNeedingReassignment(modelId) } returns listOf(ModelType.FAST)
        every { modelRegistry.observeAssets() } returns flowOf(listOf(asset))
        
        // Refresh VM to pick up assets for reassignment options building
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onDeleteLocalModelAsset(modelId)
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertEquals(modelId, viewModel.uiState.value.pendingDeletionModelId)
        assertTrue(viewModel.uiState.value.modelTypesNeedingReassignment.contains(ModelType.FAST))
        coVerify(exactly = 0) { deleteLocalModelUseCase(modelId, any(), any()) }
    }

    @Test
    fun `onDeleteLocalModelAsset filters reassignment options to vision capable models when deleted model is vision capable`() = runTest {
        val deletedModelId = 20L
        val nonVisionAsset = createFakeLocalModelAsset(
            id = 21L,
            configurations = listOf(createFakeLocalModelConfiguration(id = 101L, localModelId = 21L)),
            visionCapable = false
        )
        val visionAsset = createFakeLocalModelAsset(
            id = 22L,
            configurations = listOf(createFakeLocalModelConfiguration(id = 102L, localModelId = 22L)),
            visionCapable = true
        )
        val visionApiAsset = createFakeApiModelAsset(
            credentialsId = 30L,
            configId = 201L,
            isVision = true
        )
        val nonVisionApiAsset = createFakeApiModelAsset(
            credentialsId = 31L,
            configId = 202L,
            isVision = false
        )

        coEvery { deleteLocalModelUseCase.isLastModel(deletedModelId) } returns false
        coEvery { deleteLocalModelUseCase.getModelTypesNeedingReassignment(deletedModelId) } returns listOf(ModelType.VISION)
        every { modelRegistry.observeAssets() } returns flowOf(
            listOf(
                createFakeLocalModelAsset(
                    id = deletedModelId,
                    configurations = listOf(createFakeLocalModelConfiguration(id = 100L, localModelId = deletedModelId)),
                    visionCapable = true
                ),
                nonVisionAsset,
                visionAsset
            )
        )
        every { apiModelRepository.observeAllCredentials() } returns flowOf(
            listOf(visionApiAsset.credentials, nonVisionApiAsset.credentials)
        )
        every { apiModelRepository.observeAllConfigurations() } returns flowOf(
            listOf(visionApiAsset.configurations.single(), nonVisionApiAsset.configurations.single())
        )

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onDeleteLocalModelAsset(deletedModelId)
        testDispatcher.scheduler.advanceUntilIdle()

        val options = viewModel.uiState.value.reassignmentOptions
        assertEquals(2, options.size)
        assertTrue(options.any { it.localModelId == 22L })
        assertTrue(options.any { it.apiCredentialsId == 30L })
        assertFalse(options.any { it.localModelId == 21L })
        assertFalse(options.any { it.apiCredentialsId == 31L })
    }

    @Test
    fun `onConfirmDeletionWithReassignment completes deletion and clears state`() = runTest {
        val modelId = 20L
        val replacementConfigId = 77L
        
        // Set up pending state
        coEvery { deleteLocalModelUseCase.isLastModel(modelId) } returns false
        coEvery { deleteLocalModelUseCase.getModelTypesNeedingReassignment(modelId) } returns listOf(ModelType.FAST)
        viewModel.onDeleteLocalModelAsset(modelId)
        testDispatcher.scheduler.advanceUntilIdle()
        
        coEvery { deleteLocalModelUseCase(modelId, replacementConfigId, null) } returns Result.success(Unit)
        
        viewModel.onConfirmDeletionWithReassignment(replacementLocalConfigId = replacementConfigId, replacementApiConfigId = null)
        testDispatcher.scheduler.advanceUntilIdle()
        
        coVerify { deleteLocalModelUseCase(modelId, replacementConfigId, null) }
        assertEquals(null, viewModel.uiState.value.pendingDeletionModelId)
        assertEquals(emptyList<ModelType>(), viewModel.uiState.value.modelTypesNeedingReassignment)
    }

    @Test
    fun `onDeleteLocalModelConfig shows reassignment dialog when config is a default`() = runTest {
        val configId = 100L
        
        // Mock this config as being a default for FAST
        coEvery { defaultModelRepository.observeDefaults() } returns flowOf(
            listOf(com.browntowndev.pocketcrew.domain.model.config.DefaultModelAssignment(ModelType.FAST, localConfigId = configId, apiConfigId = null))
        )
        // vm uses getDefaultModelsUseCase which uses defaultModelRepository
        
        viewModel.onDeleteLocalModelConfig(configId) {}
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertEquals(configId, viewModel.uiState.value.pendingDeletionConfigId)
        assertTrue(viewModel.uiState.value.modelTypesNeedingReassignment.contains(ModelType.FAST))
    }

    @Test
    fun `onDeleteLocalModelConfig shows empty reassignment options when no compatible vision models exist`() = runTest {
        val deletedModelId = 20L
        val deletedConfigId = 100L
        val deletedAsset = createFakeLocalModelAsset(
            id = deletedModelId,
            configurations = listOf(createFakeLocalModelConfiguration(id = deletedConfigId, localModelId = deletedModelId)),
            visionCapable = true
        )
        val nonVisionAsset = createFakeLocalModelAsset(
            id = 21L,
            configurations = listOf(createFakeLocalModelConfiguration(id = 101L, localModelId = 21L)),
            visionCapable = false
        )
        val nonVisionApiAsset = createFakeApiModelAsset(
            credentialsId = 31L,
            configId = 202L,
            isVision = false
        )

        every { modelRegistry.observeAssets() } returns flowOf(listOf(deletedAsset, nonVisionAsset))
        every { apiModelRepository.observeAllCredentials() } returns flowOf(listOf(nonVisionApiAsset.credentials))
        every { apiModelRepository.observeAllConfigurations() } returns flowOf(listOf(nonVisionApiAsset.configurations.single()))
        every { defaultModelRepository.observeDefaults() } returns flowOf(
            listOf(com.browntowndev.pocketcrew.domain.model.config.DefaultModelAssignment(ModelType.VISION, localConfigId = deletedConfigId, apiConfigId = null))
        )

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onDeleteLocalModelConfig(deletedConfigId) {}
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(deletedConfigId, viewModel.uiState.value.pendingDeletionConfigId)
        assertTrue(viewModel.uiState.value.modelTypesNeedingReassignment.contains(ModelType.VISION))
        assertTrue(viewModel.uiState.value.reassignmentOptions.isEmpty())
    }

    @Test
    fun `onDeleteLocalModelConfig filters to vision compatible options when vision slot needs reassignment`() = runTest {
        val deletedConfigId = 100L
        val deletedAsset = createFakeLocalModelAsset(
            id = 20L,
            configurations = listOf(createFakeLocalModelConfiguration(id = deletedConfigId, localModelId = 20L)),
            visionCapable = false
        )
        val nonVisionAsset = createFakeLocalModelAsset(
            id = 21L,
            configurations = listOf(createFakeLocalModelConfiguration(id = 101L, localModelId = 21L)),
            visionCapable = false
        )
        val visionAsset = createFakeLocalModelAsset(
            id = 22L,
            configurations = listOf(createFakeLocalModelConfiguration(id = 102L, localModelId = 22L)),
            visionCapable = true
        )
        val visionApiAsset = createFakeApiModelAsset(
            credentialsId = 30L,
            configId = 201L,
            isVision = true
        )

        every { modelRegistry.observeAssets() } returns flowOf(listOf(deletedAsset, nonVisionAsset, visionAsset))
        every { apiModelRepository.observeAllCredentials() } returns flowOf(listOf(visionApiAsset.credentials))
        every { apiModelRepository.observeAllConfigurations() } returns flowOf(listOf(visionApiAsset.configurations.single()))
        every { defaultModelRepository.observeDefaults() } returns flowOf(
            listOf(
                com.browntowndev.pocketcrew.domain.model.config.DefaultModelAssignment(
                    ModelType.VISION,
                    localConfigId = deletedConfigId,
                    apiConfigId = null
                )
            )
        )

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onDeleteLocalModelConfig(deletedConfigId) {}
        testDispatcher.scheduler.advanceUntilIdle()

        val options = viewModel.uiState.value.reassignmentOptions
        assertEquals(2, options.size)
        assertTrue(options.any { it.localModelId == 22L })
        assertTrue(options.any { it.apiCredentialsId == 30L })
        assertFalse(options.any { it.localModelId == 21L })
    }

    @Test
    fun `onLocalModelConfigFieldChange should NOT update state when isSystemPreset is true`() = runTest {
        val assetUi = LocalModelAssetUi(
            metadataId = 20, huggingFaceModelName = "hf", remoteFileName = "f", sizeInBytes = 1000, configurations = emptyList()
        )
        val systemConfig = LocalModelConfigUi(id = 1, localModelId = 20, displayName = "System", isSystemPreset = true)
        
        viewModel.onSelectLocalModelAsset(assetUi)
        viewModel.onSelectLocalModelConfig(systemConfig)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Try to change field
        val modifiedConfig = systemConfig.copy(displayName = "Modified")
        viewModel.onLocalModelConfigFieldChange(modifiedConfig)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Should remain as original if read-only
        assertEquals("System", viewModel.uiState.value.selectedLocalModelConfig?.displayName)
    }

    @Test
    fun `onSaveLocalModelConfig should NOT call repository when isSystemPreset is true`() = runTest {
        val assetUi = LocalModelAssetUi(
            metadataId = 20, huggingFaceModelName = "hf", remoteFileName = "f", sizeInBytes = 1000, configurations = emptyList()
        )
        val systemConfig = LocalModelConfigUi(id = 1, localModelId = 20, displayName = "System", isSystemPreset = true)
        
        viewModel.onSelectLocalModelAsset(assetUi)
        viewModel.onSelectLocalModelConfig(systemConfig)
        testDispatcher.scheduler.advanceUntilIdle()
        
        var successCalled = false
        viewModel.onSaveLocalModelConfig { successCalled = true }
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Should NOT call repository and should NOT call success callback
        coVerify(exactly = 0) { modelRegistry.saveConfiguration(any()) }
        assertFalse(successCalled)
    }

    @Test
    fun `onTemperatureChange should NOT update state for system presets`() = runTest {
        val systemConfig = LocalModelConfigUi(id = 1, localModelId = 20, displayName = "System", isSystemPreset = true, temperature = 0.7)
        viewModel.onSelectLocalModelConfig(systemConfig)
        testDispatcher.scheduler.advanceUntilIdle()
        
        viewModel.onTemperatureChange(1.0)
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertEquals(0.7, viewModel.uiState.value.selectedLocalModelConfig?.temperature)
    }

    private fun createFakeLocalModelAsset(
        id: Long,
        configurations: List<com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration>,
        visionCapable: Boolean = false
    ) = com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset(
        metadata = com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata(
            id = id,
            huggingFaceModelName = "hf/$id",
            remoteFileName = "f$id",
            localFileName = "l$id",
            sha256 = "sha$id",
            sizeInBytes = 1000,
            modelFileFormat = com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat.GGUF,
            visionCapable = visionCapable
        ),
        configurations = configurations
    )

    private fun createFakeApiModelAsset(
        credentialsId: Long,
        configId: Long,
        isVision: Boolean
    ) = ApiModelAsset(
        credentials = ApiCredentials(
            id = credentialsId,
            displayName = "API $credentialsId",
            provider = ApiProvider.OPENAI,
            modelId = "model-$credentialsId",
            baseUrl = null,
            isVision = isVision,
            credentialAlias = "alias-$credentialsId"
        ),
        configurations = listOf(
            ApiModelConfiguration(
                id = configId,
                apiCredentialsId = credentialsId,
                displayName = "Preset $configId"
            )
        )
    )

    private fun createFakeLocalModelConfiguration(id: Long, localModelId: Long) = com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration(
        id = id,
        localModelId = localModelId,
        displayName = "Config $id",
        maxTokens = 4096,
        contextWindow = 4096,
        temperature = 0.7,
        topP = 0.95,
        topK = 40,
        minP = 0.0,
        repetitionPenalty = 1.1,
        systemPrompt = ""
    )

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
        deleteLocalModelUseCase = deleteLocalModelUseCase,
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
