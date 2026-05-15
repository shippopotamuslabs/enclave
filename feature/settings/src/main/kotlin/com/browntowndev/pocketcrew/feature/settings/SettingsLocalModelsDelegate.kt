@file:Suppress("MaxLineLength", "MagicNumber", "UnusedPrivateProperty", "UnusedParameter", "TooManyFunctions", "ComplexCondition", "LargeClass", "CyclomaticComplexMethod", "SwallowedException", "InstanceOfCheckForException", "ThrowsCount", "NestedBlockDepth", "ReturnCount", "LoopWithTooManyJumpStatements", "FunctionNaming", "LongMethod", "MatchingDeclarationName", "UnusedPrivateMember", "LongParameterList")
package com.browntowndev.pocketcrew.feature.settings

import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.usecase.settings.ModelDeletionTarget
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsLocalModelUseCases
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal class SettingsLocalModelsDelegate @Inject constructor(
    private val localModelUseCases: SettingsLocalModelUseCases,
    private val deletionHelper: SettingsDeletionHelper,
    private val localModelAssetUiMapper: LocalModelAssetUiMapper,
    private val errorHandler: ViewModelErrorHandler,
) {
    fun observeActiveLocalModels(
        scope: CoroutineScope,
        localModelAssetsFlow: kotlinx.coroutines.flow.Flow<List<com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset>>,
        localModelsState: MutableStateFlow<LocalModelsTransientState>
    ) {
        scope.launch {
            localModelAssetsFlow.collect { assets ->
                localModelsState.update { it.copy(availableToDownloadModels = assets) }
            }
        }
    }
    fun onShowModelConfigSheet(
        localModelsState: MutableStateFlow<LocalModelsTransientState>,
        show: Boolean
    ) {
        localModelsState.update { it.copy(isSheetOpen = show) }
        if (!show) {
            // Clear sheet navigation state but preserve configDraft so the configure
            // screen retains its state after the sheet hides during navigation.
            // onClearSelectedLocalModel handles final cleanup after the configure screen exits.
            localModelsState.update { it.copy(selectedAsset = null, selectedConfig = null) }
        }
    }

    fun onSelectLocalModelAsset(
        localModelsState: MutableStateFlow<LocalModelsTransientState>,
        asset: LocalModelAssetUi?
    ) {
        localModelsState.update { it.copy(selectedAsset = asset, configDraft = null) }
    }

    fun onSelectLocalModelConfig(
        localModelsState: MutableStateFlow<LocalModelsTransientState>,
        config: LocalModelConfigUi?
    ) {
        localModelsState.update {
            it.copy(
                selectedConfig = config,
                configDraft = config ?: LocalModelConfigUi()
            )
        }
    }

    fun onModelConfigFieldChange(
        localModelsState: MutableStateFlow<LocalModelsTransientState>,
        config: LocalModelConfigUi
    ) {
        localModelsState.update { it.copy(configDraft = config) }
    }

    fun onSaveLocalModelConfig(
        scope: CoroutineScope,
        tag: String,
        localModelsState: MutableStateFlow<LocalModelsTransientState>,
        onSuccess: () -> Unit
    ) {
        val configDraft = localModelsState.value.configDraft ?: return
        val assetId = localModelsState.value.selectedAsset?.metadataId
            ?: configDraft.localModelId.takeIf { it.value.isNotEmpty() }
            ?: return
        scope.launch(
            errorHandler.coroutineExceptionHandler(
                tag,
                "Failed to save local model configuration",
                "Failed to save configuration"
            )
        ) {
            localModelUseCases.saveLocalModelPreset(assetId, configDraft.toDomain()).getOrThrow()
            onSuccess()
        }
    }

    fun onDeleteLocalModelConfig(
        scope: CoroutineScope,
        deletionState: MutableStateFlow<DeletionTransientState>,
        tag: String,
        id: LocalModelConfigurationId,
        onSuccess: () -> Unit
    ) {
        deletionHelper.prepareDeletion(
            scope = scope,
            deletionState = deletionState,
            target = ModelDeletionTarget.LocalModelPreset(id),
            tag = tag,
            failureMessage = "Failed to delete local model configuration",
            userMessage = "Failed to delete configuration",
            onImmediateSuccess = onSuccess,
        )
    }

    fun onDeleteLocalModelAsset(
        scope: CoroutineScope,
        deletionState: MutableStateFlow<DeletionTransientState>,
        tag: String,
        id: com.browntowndev.pocketcrew.domain.model.config.LocalModelId
    ) {
        deletionHelper.prepareDeletion(
            scope = scope,
            deletionState = deletionState,
            target = ModelDeletionTarget.LocalModelAsset(id),
            tag = tag,
            failureMessage = "Failed to delete local model",
            userMessage = "Failed to delete model",
        )
    }

    fun onReDownloadLocalModel(
        scope: CoroutineScope,
        tag: String,
        id: com.browntowndev.pocketcrew.domain.model.config.LocalModelId
    ) {
        scope.launch(errorHandler.coroutineExceptionHandler(tag, "Failed to initiate re-download", "Failed to start download")) {
            localModelUseCases.reDownloadModel(id)
        }
    }

    private fun LocalModelConfigUi.toDomain(): com.browntowndev.pocketcrew.domain.usecase.settings.LocalModelPresetDraft =
        com.browntowndev.pocketcrew.domain.usecase.settings.LocalModelPresetDraft(
            id = id,
            displayName = displayName,
            maxTokens = maxTokens,
            contextWindow = contextWindow,
            temperature = temperature,
            topP = topP,
            topK = topK,
            minP = minP,
            repetitionPenalty = repetitionPenalty,
            thinkingEnabled = thinkingEnabled,
            systemPrompt = systemPrompt,
            isSystemPreset = isSystemPreset,
        )
}
