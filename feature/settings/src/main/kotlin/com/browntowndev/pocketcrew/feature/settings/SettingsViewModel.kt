@file:Suppress("MaxLineLength", "MagicNumber", "UnusedPrivateProperty", "UnusedParameter", "TooManyFunctions", "ComplexCondition", "LargeClass", "CyclomaticComplexMethod", "SwallowedException", "InstanceOfCheckForException", "ThrowsCount", "NestedBlockDepth", "ReturnCount", "LoopWithTooManyJumpStatements")
package com.browntowndev.pocketcrew.feature.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browntowndev.pocketcrew.core.data.security.ApiKeyManager
import com.browntowndev.pocketcrew.domain.model.ApiProvider
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.usecase.assignment.AssignmentUseCases
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.ApiModelConfigurationUseCases
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.DefaultModelUseCases
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.GetLocalModelAssetsUseCase
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.LocalModelConfigurationUseCases
import com.browntowndev.pocketcrew.feature.settings.model.MediaProviderAssetUi
import com.browntowndev.pocketcrew.feature.settings.model.SettingsApiState
import com.browntowndev.pocketcrew.feature.settings.model.SettingsMediaState
import com.browntowndev.pocketcrew.feature.settings.model.SettingsTtsState
import com.browntowndev.pocketcrew.feature.settings.model.TtsProviderAssetUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val apiModelConfigurationUseCases: ApiModelConfigurationUseCases,
    private val localModelConfigurationUseCases: LocalModelConfigurationUseCases,
    private val defaultModelUseCases: DefaultModelUseCases,
    private val assignmentUseCases: AssignmentUseCases,
    private val getLocalModelAssetsUseCase: GetLocalModelAssetsUseCase,
    private val apiKeyManager: ApiKeyManager,
    private val apiProvidersDelegate: SettingsApiProvidersDelegate,
    private val localModelsDelegate: SettingsLocalModelsDelegate,
) : ViewModel() {

    private companion object {
        const val TAG = "SettingsViewModel"
        const val SUBSCRIBE_TIMEOUT_MS = 5000L
        
        // Combine indices
        const val INDEX_API_MODELS = 0
        const val INDEX_LOCAL_MODELS = 1
        const val INDEX_DEFAULT_MODELS = 2
    }

    private val _apiState = MutableStateFlow(SettingsApiState())
    val apiState: StateFlow<SettingsApiState> = _apiState.asStateFlow()

    private val _ttsState = MutableStateFlow(SettingsTtsState())
    val ttsState: StateFlow<SettingsTtsState> = _ttsState.asStateFlow()

    private val _mediaState = MutableStateFlow(SettingsMediaState())
    val mediaState: StateFlow<SettingsMediaState> = _mediaState.asStateFlow()

    val localModelsState: StateFlow<SettingsLocalModelsState> = combine(
        apiModelConfigurationUseCases.getApiModelConfigurations(),
        localModelConfigurationUseCases.getLocalModelConfigurations(),
        defaultModelUseCases.getDefaultModels()
    ) { params ->
        val apiModels = params[INDEX_API_MODELS] as List<ApiModelConfiguration>
        val localModels = params[INDEX_LOCAL_MODELS] as List<LocalModelConfiguration>
        val defaultModels = params[INDEX_DEFAULT_MODELS] as Map<String, String?>

        SettingsLocalModelsState(
            apiModelConfigurations = apiModels,
            localModelConfigurations = localModels,
            defaultModels = defaultModels
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS),
        initialValue = SettingsLocalModelsState()
    )

    private val _currentApiKey = MutableStateFlow<String?>(null)
    val currentApiKey: StateFlow<String?> = _currentApiKey.asStateFlow()

    init {
        viewModelScope.launch {
            _currentApiKey.value = apiKeyManager.getApiKey()
        }
    }

    fun onToggleApiProvider(provider: ApiProvider) = apiProvidersDelegate.onToggleApiProvider(_apiState, provider)
    fun onApiKeyChange(apiKey: String) = apiProvidersDelegate.onApiKeyChange(viewModelScope, TAG, _currentApiKey, apiKey)
    fun onShowTtsProvidersSheet(show: Boolean) = apiProvidersDelegate.onShowTtsProvidersSheet(_ttsState, show)
    fun onStartCreateTtsProviderAsset() = apiProvidersDelegate.onStartCreateTtsProviderAsset(_ttsState)
    fun onTtsAssetFieldChange(asset: TtsProviderAssetUi) =
        apiProvidersDelegate.onTtsAssetFieldChange(
            viewModelScope, TAG, _ttsState, _apiState, _currentApiKey,
            assignmentsUseCases = assignmentUseCases, asset = asset
        )

    fun onFetchTtsModels() = apiProvidersDelegate.onFetchTtsModels(viewModelScope, TAG, _ttsState, _apiState, _currentApiKey)
    fun onSelectTtsProviderAsset(asset: TtsProviderAssetUi?) =
        apiProvidersDelegate.onSelectTtsProviderAsset(viewModelScope, TAG, _ttsState, _apiState, _currentApiKey, asset)

    fun onShowMediaProvidersSheet(show: Boolean) = apiProvidersDelegate.onShowMediaProvidersSheet(_mediaState, show)
    fun onStartCreateMediaProviderAsset() = apiProvidersDelegate.onStartCreateMediaProviderAsset(_mediaState)
    
    fun onMediaAssetFieldChange(asset: MediaProviderAssetUi) =
        apiProvidersDelegate.onMediaAssetFieldChange(
            viewModelScope, TAG, _mediaState, _apiState, _currentApiKey,
            assignmentsUseCases = assignmentUseCases, asset = asset
        )

    fun onFetchMediaModels() = apiProvidersDelegate.onFetchMediaModels(viewModelScope, TAG, _mediaState, _currentApiKey)
    
    fun onSelectMediaProviderAsset(asset: MediaProviderAssetUi?) =
        apiProvidersDelegate.onSelectMediaProviderAsset(viewModelScope, TAG, _mediaState, _apiState, _currentApiKey, asset)

    fun onSaveMediaProvider(onSuccess: () -> Unit) =
        apiProvidersDelegate.onSaveMediaProvider(
            viewModelScope, TAG, _mediaState, _currentApiKey,
            assignmentsUseCases = assignmentUseCases, onSuccess = onSuccess
        )

    fun onShowLocalModelsSheet(show: Boolean) = localModelsDelegate.onShowLocalModelsSheet(viewModelScope, getLocalModelAssetsUseCase, show)
    fun onSelectLocalModelAsset(asset: LocalModelAssetUi?) = localModelsDelegate.onSelectLocalModelAsset(viewModelScope, getLocalModelAssetsUseCase, asset)
    fun onDownloadLocalModel(onSuccess: () -> Unit) = localModelsDelegate.onDownloadLocalModel(viewModelScope, TAG, _currentApiKey, onSuccess)
    fun onCancelDownload() = localModelsDelegate.onCancelDownload()
    fun onDeleteLocalModel(asset: LocalModelAssetUi) = localModelsDelegate.onDeleteLocalModel(viewModelScope, asset)
}
